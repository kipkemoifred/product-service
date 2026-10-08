package ke.co.tracom.product_service.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDateTime

data class CreateProductRequest(
    @field:NotBlank(message = "Product name is required")
    val name: String,

    @field:NotBlank(message = "Product code (barcode/code) is required")
    val productCode: String,

    val description: String? = null,

    @field:NotBlank(message = "Product category is required")
    val category: String,

    @field:NotBlank(message = "Unit of measure is required (e.g. LITRES, KGS, PACKETS, PIECES)")
    val unitOfMeasure: String,

    @field:NotNull(message = "Unit price is required")
    @field:DecimalMin(value = "0.01", message = "Unit price must be greater than zero")
    val unitPrice: BigDecimal,

    val taxType: String? = "STANDARD",

    val hasDiscount: Boolean = false,

    val discountRate: BigDecimal = BigDecimal.ZERO,

    val imageUrl: String? = null,

    val initialStock: BigDecimal? = BigDecimal.ZERO
)

data class UpdateProductRequest(
    val name: String? = null,
    val description: String? = null,
    val category: String? = null,
    val unitOfMeasure: String? = null,
    val unitPrice: BigDecimal? = null,
    val taxType: String? = null,
    val hasDiscount: Boolean? = null,
    val discountRate: BigDecimal? = null,
    val imageUrl: String? = null,
    val active: Boolean? = null
)

data class ProductResponse(
    val id: Long,
    val name: String,
    val productCode: String,
    val description: String?,
    val category: String,
    val unitOfMeasure: String,
    val unitPrice: BigDecimal,
    val taxType: String,
    val hasDiscount: Boolean,
    val discountRate: BigDecimal,
    val imageUrl: String?,
    val active: Boolean,
    val syncedToSupplierCatalogue: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
