package ke.co.tracom.product_service.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class StockMovementResponse(
    val id: Long,
    val productId: Long,
    val productCode: String,
    val productName: String,
    val movementType: String,
    val quantityChanged: BigDecimal,
    val previousQuantity: BigDecimal,
    val newQuantity: BigDecimal,
    val reason: String,
    val referenceNumber: String?,
    val vehicleId: String?,
    val performedBy: String,
    val syncedToBackOffice: Boolean,
    val createdAt: LocalDateTime
)
