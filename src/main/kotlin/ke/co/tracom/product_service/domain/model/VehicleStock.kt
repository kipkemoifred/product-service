package ke.co.tracom.product_service.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "vehicle_stocks",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_vehicle_product", columnNames = ["vehicle_id", "product_id"])
    ],
    indexes = [
        Index(name = "idx_vehicle_id", columnList = "vehicle_id")
    ]
)
class VehicleStock(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "vehicle_id", nullable = false, length = 100)
    var vehicleId: String,

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    var product: Product,

    @Column(name = "available_quantity", nullable = false, precision = 12, scale = 2)
    var availableQuantity: BigDecimal = BigDecimal.ZERO,

    @Column(name = "ordered_quantity", nullable = false, precision = 12, scale = 2)
    var orderedQuantity: BigDecimal = BigDecimal.ZERO,

    @Column(name = "last_updated", nullable = false)
    var lastUpdated: LocalDateTime = LocalDateTime.now()
) {
    val quantityAvailableForSale: BigDecimal
        get() {
            val diff = availableQuantity.subtract(orderedQuantity)
            return if (diff < BigDecimal.ZERO) BigDecimal.ZERO else diff
        }

    @PrePersist
    @PreUpdate
    fun onUpdate() {
        lastUpdated = LocalDateTime.now()
    }
}
