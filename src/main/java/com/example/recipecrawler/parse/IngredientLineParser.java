package com.example.recipecrawler.parse;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a free-text ingredient line ("2 1/2 cups all-purpose flour, sifted")
 * into {@code amount} + {@code unit} + {@code name} + {@code note}.
 *
 * <p>Best-effort: an unmatched line still yields a name (the leftover text), just
 * with a null amount / unit — RecipeCatalogue keeps the original text as the
 * line's {@code quantity} regardless.
 */
@Component
public class IngredientLineParser {

    /** Unicode fraction glyphs → value. */
    private static final Map<String, Double> GLYPH_FRACTIONS = Map.ofEntries(
            Map.entry("¼", 0.25), Map.entry("½", 0.5), Map.entry("¾", 0.75),
            Map.entry("⅓", 1.0 / 3), Map.entry("⅔", 2.0 / 3),
            Map.entry("⅛", 0.125), Map.entry("⅜", 0.375), Map.entry("⅝", 0.625), Map.entry("⅞", 0.875),
            Map.entry("⅕", 0.2), Map.entry("⅖", 0.4), Map.entry("⅗", 0.6), Map.entry("⅘", 0.8),
            Map.entry("⅙", 1.0 / 6), Map.entry("⅚", 5.0 / 6));

    /** Unit words we recognise → the token RecipeCatalogue's {@code Unit.parse} accepts. */
    private static final Map<String, String> UNIT_WORDS = Map.ofEntries(
            Map.entry("teaspoon", "tsp"), Map.entry("teaspoons", "tsp"), Map.entry("tsp", "tsp"),
            Map.entry("tablespoon", "tbsp"), Map.entry("tablespoons", "tbsp"), Map.entry("tbsp", "tbsp"),
            Map.entry("tbsps", "tbsp"), Map.entry("tbs", "tbsp"), Map.entry("tbl", "tbsp"),
            Map.entry("cup", "cup"), Map.entry("cups", "cup"),
            Map.entry("gram", "g"), Map.entry("grams", "g"), Map.entry("g", "g"), Map.entry("gr", "g"),
            Map.entry("kilogram", "kg"), Map.entry("kilograms", "kg"), Map.entry("kg", "kg"),
            Map.entry("milligram", "mg"), Map.entry("milligrams", "mg"), Map.entry("mg", "mg"),
            Map.entry("millilitre", "ml"), Map.entry("millilitres", "ml"),
            Map.entry("milliliter", "ml"), Map.entry("milliliters", "ml"), Map.entry("ml", "ml"),
            Map.entry("litre", "l"), Map.entry("litres", "l"), Map.entry("liter", "l"), Map.entry("liters", "l"),
            Map.entry("ounce", "oz"), Map.entry("ounces", "oz"), Map.entry("oz", "oz"),
            Map.entry("pound", "lb"), Map.entry("pounds", "lb"), Map.entry("lb", "lb"), Map.entry("lbs", "lb"),
            Map.entry("pinch", "pinch"), Map.entry("pinches", "pinch"),
            Map.entry("clove", "clove"), Map.entry("cloves", "clove"),
            Map.entry("slice", "slice"), Map.entry("slices", "slice"),
            Map.entry("piece", "piece"), Map.entry("pieces", "piece"));

    /** Two-word units, checked before single words. */
    private static final Map<String, String> UNIT_PHRASES = Map.of(
            "fluid ounce", "fl_oz", "fluid ounces", "fl_oz");

    private static final Set<String> DROP_LEADING = Set.of("of", "the");

    private static final List<Pattern> TRAILING_NOTES = List.of(
            Pattern.compile("(?i)[,;]?\\s*(to taste|as needed|as required|for serving|for garnish|"
                    + "for dusting|for greasing|plus more.*|or to taste|divided|optional)\\s*$"));

    private static final Pattern LEADING_QTY = Pattern.compile(
            "^\\s*(?<qty>"
                    + "\\d+(?:[.,]\\d+)?\\s*[-–—]\\s*\\d+(?:[.,]\\d+)?"   // range 2-3
                    + "|\\d+\\s+\\d+/\\d+"                                 // mixed 1 1/2
                    + "|\\d+/\\d+"                                         // fraction 1/2
                    + "|\\d+(?:[.,]\\d+)?"                                 // decimal 2 / 2.5
                    + ")\\s*");

    private static final Pattern GLYPH_QTY =
            Pattern.compile("^\\s*(?<whole>\\d+)?\\s*(?<glyph>[¼½¾⅓⅔⅛⅜⅝⅞⅕⅖⅗⅘⅙⅚])\\s*");

    private static final Pattern PARENS = Pattern.compile("\\s*\\(([^)]*)\\)");

    public Parsed parse(String rawLine) {
        String original = rawLine == null ? "" : rawLine.trim();
        String s = original.replaceAll("\\s+", " ");
        if (s.isEmpty()) {
            return new Parsed(original, null, null, "", null);
        }

        // 1. lift out a parenthetical -> note
        String note = null;
        Matcher pm = PARENS.matcher(s);
        if (pm.find()) {
            note = pm.group(1).trim();
            s = pm.replaceAll(" ").replaceAll("\\s+", " ").trim();
        }

        // 2. trailing "to taste" / "for serving" / ... -> note
        for (Pattern p : TRAILING_NOTES) {
            Matcher tm = p.matcher(s);
            if (tm.find()) {
                note = append(note, tm.group(1).trim());
                s = s.substring(0, tm.start()).trim();
            }
        }

        // 3. leading amount
        Double amount = null;
        Matcher gm = GLYPH_QTY.matcher(s);
        Matcher qm = LEADING_QTY.matcher(s);
        if (gm.find()) {
            double whole = gm.group("whole") != null ? Double.parseDouble(gm.group("whole")) : 0;
            amount = whole + GLYPH_FRACTIONS.getOrDefault(gm.group("glyph"), 0.0);
            s = s.substring(gm.end());
        } else if (qm.find()) {
            amount = parseQty(qm.group("qty"));
            s = s.substring(qm.end());
        } else {
            Matcher am = Pattern.compile("(?i)^(a|an)\\s+(.*)$").matcher(s);
            if (am.matches() && firstUnit(am.group(2)) != null) {
                amount = 1.0;
                s = am.group(2);
            }
        }

        // 4. optional unit word
        String unit = null;
        String[] u = firstUnit(s);
        if (u != null) {
            unit = u[0];
            s = u[1];
        }

        // 5. remainder is the name; a trailing ", detail" is a note
        String name = s;
        int comma = name.indexOf(',');
        if (comma >= 0) {
            note = append(note, name.substring(comma + 1).trim());
            name = name.substring(0, comma);
        }
        return new Parsed(original, amount, unit, cleanName(name), blankToNull(note));
    }

    private static double parseQty(String qty) {
        String q = qty.trim().replace(",", ".").replaceAll("\\s*[-–—]\\s*", "-");
        if (q.matches("\\d+(?:\\.\\d+)?-\\d+(?:\\.\\d+)?")) {
            String[] p = q.split("-");
            return (Double.parseDouble(p[0]) + Double.parseDouble(p[1])) / 2.0;
        }
        if (q.matches("\\d+ \\d+/\\d+")) {
            String[] p = q.split(" ");
            return Double.parseDouble(p[0]) + frac(p[1]);
        }
        if (q.contains("/")) {
            return frac(q);
        }
        return Double.parseDouble(q);
    }

    private static double frac(String f) {
        String[] p = f.split("/");
        return Double.parseDouble(p[0]) / Double.parseDouble(p[1]);
    }

    /** {@code [token, remainder]} if {@code s} starts with a unit word, else null. */
    static String[] firstUnit(String s) {
        String trimmed = s.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] words = trimmed.split("\\s+");
        if (words.length >= 2) {
            String two = (words[0] + " " + words[1]).toLowerCase(Locale.ROOT);
            if (UNIT_PHRASES.containsKey(two)) {
                int cut = words[0].length() + 1 + words[1].length();
                return new String[]{UNIT_PHRASES.get(two), trimmed.substring(cut).trim()};
            }
        }
        String first = words[0].replaceAll("\\.+$", "");
        String token = UNIT_WORDS.get(first);                              // case-sensitive (T vs t)
        if (token == null) {
            token = UNIT_WORDS.get(first.toLowerCase(Locale.ROOT));
        }
        if ("t".equals(first)) {
            token = "tsp";
        } else if ("T".equals(first)) {
            token = "tbsp";
        }
        if (token != null) {
            return new String[]{token, trimmed.substring(words[0].length()).trim()};
        }
        return null;
    }

    private static String cleanName(String name) {
        String n = name.trim().replaceAll("^[-–—•*\\s]+", "").replaceAll("[.,;:\\s]+$", "");
        String[] w = n.split("\\s+", 2);
        if (w.length == 2 && DROP_LEADING.contains(w[0].toLowerCase(Locale.ROOT))) {
            n = w[1].trim();
        }
        return n.toLowerCase(Locale.ROOT);
    }

    private static String append(String note, String more) {
        if (more == null || more.isBlank()) {
            return note;
        }
        return note == null || note.isBlank() ? more : note + "; " + more;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** amount / unit are null when the line had none; name is never null (may be ""). */
    public record Parsed(String raw, Double amount, String unit, String name, String note) {
    }
}
