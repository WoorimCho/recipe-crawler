package com.example.recipecrawler;

import com.example.recipecrawler.catalogue.CatalogueGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogueGatewayTest {

    private MockRestServiceServer ingredientServer;
    private MockRestServiceServer recipeServer;
    private CatalogueGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder ing = RestClient.builder();
        RestClient.Builder rec = RestClient.builder();
        ingredientServer = MockRestServiceServer.bindTo(ing).build();
        recipeServer = MockRestServiceServer.bindTo(rec).build();
        gateway = new CatalogueGateway(ing.build(), rec.build());
    }

    @Test
    void findIngredientIdMatchesCaseInsensitively() {
        ingredientServer.expect(requestTo("/api/ingredients?name=Flour&size=25"))
                .andRespond(withSuccess("""
                        {"content":[{"id":7,"name":"flour"},{"id":8,"name":"bread flour"}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.findIngredientId("Flour")).isEqualTo(7L);
        ingredientServer.verify();
    }

    @Test
    void resolveCreatesWhenMissing() {
        ingredientServer.expect(requestTo("/api/ingredients?name=nutmeg&size=25"))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));
        ingredientServer.expect(requestTo("/api/ingredients")).andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.name").value("nutmeg"))
                .andRespond(withSuccess("{\"id\":42,\"name\":\"nutmeg\"}", MediaType.APPLICATION_JSON));

        CatalogueGateway.Resolved r = gateway.resolveIngredientId("nutmeg");
        assertThat(r.ingredientId()).isEqualTo(42L);
        assertThat(r.created()).isTrue();
        ingredientServer.verify();
    }

    @Test
    void createRecipeOmitsAmountWhenThereIsNoUnit() {
        recipeServer.expect(requestTo("/api/recipes")).andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.name").value("Cake"))
                .andExpect(jsonPath("$.tags[0]").value("source:imported"))
                .andExpect(jsonPath("$.ingredients[0].amount").value(2.0))
                .andExpect(jsonPath("$.ingredients[0].unit").value("cup"))
                .andExpect(jsonPath("$.ingredients[1].amount").doesNotExist())   // "2 large eggs" — no unit
                .andExpect(jsonPath("$.ingredients[1].unit").doesNotExist())
                .andExpect(jsonPath("$.ingredients[1].quantity").value("2 large eggs"))
                .andExpect(jsonPath("$.steps[0].position").value(1))
                .andRespond(withSuccess("{\"id\":99}", MediaType.APPLICATION_JSON));

        long id = gateway.createRecipe("Cake", "import:test",
                List.of(new CatalogueGateway.Line(1, "2 cups flour", 2.0, "cup"),
                        new CatalogueGateway.Line(2, "2 large eggs", 2.0, null)),
                List.of("Mix it."));

        assertThat(id).isEqualTo(99L);
        recipeServer.verify();
    }

    @Test
    void createRecipePropagatesADownstream400() {
        recipeServer.expect(requestTo("/api/recipes"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        gateway.createRecipe("x", "y", List.of(), List.of()))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
    }
}
