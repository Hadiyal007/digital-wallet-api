package com.wallet.digital_wallet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures the OpenAPI / Swagger UI documentation.
 *
 * Two key things this does beyond the defaults:
 *
 * 1. Registers a "Bearer Authentication" security scheme so the Swagger UI
 *    shows an "Authorize" button. After pasting your JWT token there, every
 *    "Try it out" request automatically includes Authorization: Bearer <token>.
 *    Without this, every protected endpoint in Swagger would return 401.
 *
 * 2. Adds project metadata (title, description, version, contact) so the
 *    Swagger page looks polished when shown to a recruiter.
 *
 * Access: http://localhost:8080/swagger-ui.html
 * Raw spec: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI digitalWalletOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Digital Wallet API")
                        .description("""
                                A production-grade Digital Wallet REST API built with Spring Boot.
                                
                                **Features:**
                                - JWT-based stateless authentication
                                - Wallet credit, debit, and transfer operations
                                - Optimistic locking for concurrent transfer safety
                                - Paginated transaction history
                                - Saved beneficiaries management
                                - Role-based access control (USER / ADMIN)
                                - Audit logging on all money-moving operations
                                
                                **How to authenticate:**
                                1. Call `POST /api/auth/login` with your credentials
                                2. Copy the `token` from the response
                                3. Click the **Authorize** button above and paste: `<your-token>`
                                4. All subsequent requests will include the JWT automatically
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Harshil Hadiyal")
                                .email("hadiyal@example.com"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))

                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Dev Server")
                ))

                // Register Bearer JWT as the security scheme
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token (without 'Bearer ' prefix)")))

                // Apply Bearer auth as the global default requirement —
                // every endpoint shows the lock icon and requires auth.
                // Public endpoints override this with @SecurityRequirements({})
                .addSecurityItem(new SecurityRequirement()
                        .addList(SECURITY_SCHEME_NAME));
    }
}