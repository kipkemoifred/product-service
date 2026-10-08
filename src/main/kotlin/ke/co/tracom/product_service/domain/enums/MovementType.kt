package ke.co.tracom.product_service.domain.enums

enum class MovementType {
    INITIAL_STOCK,
    ADDITION,
    ADJUSTMENT,
    REMOVAL,
    ISSUE_TO_VEHICLE,
    VEHICLE_RETURN,
    BULK_UPLOAD,
    SUPPLIER_SYNC
}
