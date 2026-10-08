package ke.co.tracom.product_service.domain.enums

enum class UnitOfMeasure {
    LITRES,
    KGS,
    PACKETS,
    PIECES,
    BOXES,
    UNITS,
    CARTONS,
    BOTTLES;

    companion object {
        fun fromString(value: String?): UnitOfMeasure {
            if (value.isNullOrBlank()) {
                throw IllegalArgumentException("Unit of measure cannot be blank")
            }
            val trimmed = value.trim()
            return try {
                valueOf(trimmed.uppercase())
            } catch (e: Exception) {
                // handle common aliases
                when (trimmed.lowercase()) {
                    "litre", "litres", "l", "ltr" -> LITRES
                    "kg", "kgs", "kilogram", "kilograms" -> KGS
                    "packet", "packets", "pkt", "pkts" -> PACKETS
                    "piece", "pieces", "pc", "pcs" -> PIECES
                    "box", "boxes" -> BOXES
                    "unit", "units" -> UNITS
                    "carton", "cartons" -> CARTONS
                    "bottle", "bottles" -> BOTTLES
                    else -> throw IllegalArgumentException("Unknown unit of measure: $value. Supported: ${values().joinToString()}")
                }
            }
        }
    }
}
