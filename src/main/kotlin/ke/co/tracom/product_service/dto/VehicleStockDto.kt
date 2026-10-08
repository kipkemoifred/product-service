package ke.co.tracom.product_service.dto

import java.math.BigDecimal

data class IssueToVehicleRequest(
    val vehicleId: String,
    val productId: Long? = null,
    val productCode: String? = null,
    val quantity: BigDecimal,
    val deductFromWarehouse: Boolean = true,
    val referenceNumber: String? = null,
    val notes: String? = "Stock issued to vehicle",
    val performedBy: String? = "PORTAL_USER"
)

data class VehicleStockResponse(
    val vehicleId: String,
    val productId: Long,
    val productCode: String,
    val productName: String,
    val availableQuantity: BigDecimal,
    val orderedQuantity: BigDecimal,
    val quantityAvailableForSale: BigDecimal,
    val lastUpdated: java.time.LocalDateTime
)
