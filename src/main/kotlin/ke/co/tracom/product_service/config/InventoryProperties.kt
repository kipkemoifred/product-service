package ke.co.tracom.product_service.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "inventory")
class InventoryProperties {
    var allowNegative: Boolean = false
    var backOffice: SyncConfig = SyncConfig()
    var supplier: SyncConfig = SyncConfig()

    class SyncConfig {
        var autoSync: Boolean = true
        var endpointUrl: String = "http://localhost:8080/mock-backoffice"
    }
}
