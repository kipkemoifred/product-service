package ke.co.tracom.product_service.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class StockSummaryResponse(
    val productId: Long,
    val productCode: String,
    val productName: String,
    val category: String,
    val unitOfMeasure: String,
    val unitPrice: BigDecimal,
    val availableQuantity: BigDecimal,
    val orderedQuantity: BigDecimal,
    val quantityAvailableForSale: BigDecimal,
    val vehicleId: String? = null,
    val lastUpdated: LocalDateTime
)

data class AddStockRequest(
    val productId: Long? = null,
    val productCode: String? = null,
    val quantity: BigDecimal? = null,
    val referenceNumber: String? = null,
    val reason: String? = "Stock Receipt / Addition",
    val vehicleId: String? = null,
    val performedBy: String? = "PORTAL_USER"
)

data class AdjustStockRequest(
    val productId: Long? = null,
    val productCode: String? = null,
    /**
     * Delta adjustment (e.g. +10 or -5)
     */
    val adjustmentQuantity: BigDecimal? = null,
    /**
     * Absolute target new quantity (optional alternative to delta)
     */
    val newQuantity: BigDecimal? = null,
    val reason: String? = null,
    val referenceNumber: String? = null,
    val vehicleId: String? = null,
    val performedBy: String? = "PORTAL_USER"
)

data class RemoveStockRequest(
    val productId: Long? = null,
    val productCode: String? = null,
    val quantity: BigDecimal? = null,
    val reason: String? = null,
    val referenceNumber: String? = null,
    val vehicleId: String? = null,
    val performedBy: String? = "PORTAL_USER"
)

data class StockOperationResponse(
    val productId: Long,
    val productCode: String,
    val productName: String,
    val operation: String,
    val previousQuantity: BigDecimal,
    val newQuantity: BigDecimal,
    val orderedQuantity: BigDecimal,
    val quantityAvailableForSale: BigDecimal,
    val vehicleId: String? = null,
    val syncedToBackOffice: Boolean = true,
    val movementId: Long? = null,
    val message: String
)
