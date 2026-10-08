package ke.co.tracom.product_service.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "inventory_stocks")
class InventoryStock(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    var product: Product,

    @Column(name = "available_quantity", nullable = false, precision = 12, scale = 2)
    var availableQuantity: BigDecimal = BigDecimal.ZERO,

    @Column(name = "ordered_quantity", nullable = false, precision = 12, scale = 2)
    var orderedQuantity: BigDecimal = BigDecimal.ZERO,

    @Column(name = "reorder_threshold", nullable = false, precision = 12, scale = 2)
    var reorderThreshold: BigDecimal = BigDecimal.ZERO,

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
