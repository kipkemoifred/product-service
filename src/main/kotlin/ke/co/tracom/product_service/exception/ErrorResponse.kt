package ke.co.tracom.product_service.exception

import java.time.LocalDateTime

data class ErrorResponse(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: Int,
    val error: String,
    val errorCode: String? = null,
    val message: String,
    val path: String? = null,
    val details: Map<String, Any?> = emptyMap()
)
