package ke.co.tracom.product_service.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Product & Inventory Management Service API")
                    .version("1.0.0")
                    .description(
                        """
                        ### 3.2 Inventory Module & Product Service API Specification
                        
                        This service provides RESTful APIs for managing products and stock quantities within the Portal and integrated systems:
                        - **Product Catalog (AP1)**: Add and manage products with codes/barcodes, UOMs, tax types, discounts, and prices.
                        - **Stock Management (AP2, AP4, AP5)**: Add stock, adjust stock up/down with automatic back-office sync, and remove stock.
                        - **Vehicle Stock (AC1, AC5)**: Query and issue stock loaded onto delivery vehicles; reflects loaded quantities to mobile apps without manual entry.
                        - **Bulk Upload (AC4)**: CSV template download and row-by-row batch import with comprehensive validation and error reporting.
                        - **Supplier Integrations (AC5)**: Automated API integration for stock issues and catalogue sync.
                        - **Audit Trail**: Full movement and transaction history for compliance and inventory tracking.
                        """.trimIndent()
                    )
                    .contact(
                        Contact()
                            .name("Tracom Development Team")
                            .email("support@tracom.co.ke")
                    )
                    .license(
                        License()
                            .name("Apache 2.0")
                            .url("https://www.apache.org/licenses/LICENSE-2.0")
                    )
            )
            .servers(
                listOf(
                    Server().url("/").description("Current Environment Server")
                )
            )
    }
}
