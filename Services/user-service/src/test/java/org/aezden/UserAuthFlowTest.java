package org.aezden;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserAuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${JWT_SECRET}")
    private String jwtSecret;

    private String registerBody(String email) {
        return """
                {"email":"%s","password":"s3cret-pw","firstName":"Amina","lastName":"El Idrissi","role":"RIDER"}
                """.formatted(email);
    }

    @Test
    void registerCreatesUserAndReturnsAccessToken() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("amina@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("amina@example.com"))
                .andExpect(jsonPath("$.user.role").value("RIDER"));
    }

    @Test
    void duplicateEmailIsRejectedWith409() throws Exception {
        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(registerBody("dup@example.com")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(registerBody("dup@example.com")))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithWrongPasswordIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(registerBody("wrongpw@example.com")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"wrongpw@example.com","password":"not-the-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithCorrectPasswordReturnsJwtWithSubAndRoleClaims() throws Exception {
        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(registerBody("login@example.com")))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@example.com","password":"s3cret-pw"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String token = json.get("accessToken").asText();
        String userId = json.get("user").get("id").asText();

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo(userId);
        assertThat(claims.get("role", String.class)).isEqualTo("RIDER");
        long ttlMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(ttlMillis).isGreaterThan(1000L * 60 * 60); // catches the missing 1000L* factor regression
    }

    @Test
    void meEndpointReturnsUserForForwardedUserIdHeader() throws Exception {
        String response = mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(registerBody("me@example.com")))
                .andReturn().getResponse().getContentAsString();
        String userId = objectMapper.readTree(response).get("user").get("id").asText();

        mockMvc.perform(get("/me").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void responseBodiesNeverContainPasswordField() throws Exception {
        mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content(registerBody("nopass@example.com")))
                .andExpect(jsonPath("$.user.password").doesNotExist());

        mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nopass@example.com","password":"s3cret-pw"}
                                """))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }
}
