package com.example.recipecrawler.fetch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsTxtTest {

    private static RobotsTxt.Rules parse(String body) {
        return RobotsTxt.Rules.parse(body, "recipe-crawler/0.1");
    }

    @Test
    void starGroupDisallow() {
        RobotsTxt.Rules r = parse("""
                User-agent: *
                Disallow: /private/
                Disallow: /tmp
                Allow: /private/ok
                """);
        assertThat(r.allows("/recipes/cake")).isTrue();
        assertThat(r.allows("/private/secret")).isFalse();
        assertThat(r.allows("/private/ok/page")).isTrue();     // Allow is longer at the match point
        assertThat(r.allows("/tmpfile")).isFalse();            // prefix match, not segment
    }

    @Test
    void ourOwnGroupWinsOverStar() {
        RobotsTxt.Rules r = parse("""
                User-agent: *
                Disallow: /

                User-agent: recipe-crawler
                Disallow: /admin
                """);
        assertThat(r.allows("/anything")).isTrue();            // the '*' Disallow: / is ignored for us
        assertThat(r.allows("/admin/x")).isFalse();
    }

    @Test
    void wildcardAndAnchor() {
        RobotsTxt.Rules r = parse("""
                User-agent: *
                Disallow: /*.pdf$
                Disallow: /search
                """);
        assertThat(r.allows("/files/a.pdf")).isFalse();
        assertThat(r.allows("/files/a.pdf?x=1")).isTrue();     // anchored — query means it's not the end
        assertThat(r.allows("/search?q=cake")).isFalse();
    }

    @Test
    void emptyOrNoGroupsMeansAllowAll() {
        assertThat(parse("").allows("/x")).isTrue();
        assertThat(parse("User-agent: googlebot\nDisallow: /\n").allows("/x")).isTrue();
    }

    @Test
    void allowAllVsDenyAllConstants() {
        assertThat(RobotsTxt.Rules.ALLOW_ALL.allows("/whatever")).isTrue();
        assertThat(RobotsTxt.Rules.DENY_ALL.allows("/")).isFalse();
        assertThat(RobotsTxt.Rules.DENY_ALL.allows("/whatever")).isFalse();
    }
}
