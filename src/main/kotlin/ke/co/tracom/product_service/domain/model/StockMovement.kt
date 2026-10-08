package ke.co.tracom.product_service.domain.model

import jakarta.persistence.*
import ke.co.tracom.product_service.domain.enums.MovementType
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "stock_movements",
    indexes = [
        Index(name = "idx_movement_product", columnList = "product_id"),
        Index(name = "idx_movement_created_at", columnList = "created_at"),
        Index(name = "idx_movement_vehicle", columnList = "vehicle_id")
    ]
)
class StockMovement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    var product: Product,

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 50)
    var movementType: MovementType,

    @Column(name = "quantity_changed", nullable = false, precision = 12, scale = 2)
    var quantityChanged: BigDecimal,

    @Column(name = "previous_quantity", nullable = false, precision = 12, scale = 2)
    var previousQuantity: BigDecimal,

    @Column(name = "new_quantity", nullable = false, precision = 12, scale = 2)
    var newQuantity: BigDecimal,

    @Column(nullable = false, length = 500)
    var reason: String,

    @Column(name = "reference_number", length = 100)
    var referenceNumber: String? = null,

    @Column(name = "vehicle_id", length = 100)
    var vehicleId: String? = null,

    @Column(name = "performed_by", nullable = false, length = 100)
    var performedBy: String = "PORTAL_USER",

    @Column(name = "synced_to_back_office", nullable = false)
    var syncedToBackOffice: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    @PrePersist
    fun onPrePersist() {
        createdAt = LocalDateTime.now()
    }
}
