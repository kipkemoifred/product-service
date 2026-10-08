package ke.co.tracom.product_service.dto

data class ApiResponse<T>(
    val success: Boolean = true,
    val message: String,
    val data: T? = null
)
