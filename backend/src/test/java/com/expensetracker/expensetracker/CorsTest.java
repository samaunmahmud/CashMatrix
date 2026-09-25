package com.expensetracker.expensetracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.public-url=https://cashmatrix.example.com/",
        "app.cors.extra-origins=https://preview.example.com/some/path, https://other.example.com"
})
@AutoConfigureMockMvc
class CorsTest {

    @Autowired MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions preflightFrom(String origin) throws Exception {
        return mvc.perform(options("/api/auth/login")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST"));
    }

    @Test
    void theConfiguredAppAddressMayCallTheApi() throws Exception {
        preflightFrom("https://cashmatrix.example.com")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://cashmatrix.example.com"));
    }

    @Test
    void extraOriginsAndLocalDevelopmentAreAllowed() throws Exception {
        preflightFrom("https://preview.example.com").andExpect(status().isOk());
        preflightFrom("https://other.example.com").andExpect(status().isOk());
        preflightFrom("http://localhost:5173").andExpect(status().isOk());
    }

    @Test
    void otherSitesAreRefused() throws Exception {
        preflightFrom("https://evil.example.com").andExpect(status().isForbidden());
    }
}
