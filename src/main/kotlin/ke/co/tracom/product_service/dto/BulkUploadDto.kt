package ke.co.tracom.product_service.dto

data class BulkUploadResult(
    val totalRows: Int,
    val successfulRows: Int,
    val rejectedRows: Int,
    val successfulItems: List<Map<String, Any?>> = emptyList(),
    val errors: List<BulkUploadError> = emptyList()
)

data class BulkUploadError(
    val rowNumber: Int,
    val data: String,
    val reason: String
)
