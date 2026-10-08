package ke.co.tracom.product_service.domain.enums

enum class TaxType {
    STANDARD,      // e.g. 16% VAT
    ZERO_RATED,    // 0%
    EXEMPT;        // Exempt from tax

    companion object {
        fun fromString(value: String?): TaxType {
            if (value.isNullOrBlank()) {
                return STANDARD
            }
            return try {
                valueOf(value.trim().uppercase())
            } catch (e: Exception) {
                when (value.trim().lowercase()) {
                    "standard", "vat", "16%" -> STANDARD
                    "zero", "zero_rated", "zero-rated", "0%" -> ZERO_RATED
                    "exempt", "exempted" -> EXEMPT
                    else -> throw IllegalArgumentException("Unknown tax type: $value. Supported: STANDARD, ZERO_RATED, EXEMPT")
                }
            }
        }
    }
}
