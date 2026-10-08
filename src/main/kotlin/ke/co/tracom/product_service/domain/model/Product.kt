package ke.co.tracom.product_service.domain.model

import jakarta.persistence.*
import ke.co.tracom.product_service.domain.enums.TaxType
import ke.co.tracom.product_service.domain.enums.UnitOfMeasure
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "products",
    indexes = [
        Index(name = "idx_product_code", columnList = "product_code", unique = true),
        Index(name = "idx_product_category", columnList = "category"),
        Index(name = "idx_product_name", columnList = "name")
    ]
)
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 150)
    var name: String,

    @Column(name = "product_code", nullable = false, unique = true, length = 100)
    var productCode: String,

    @Column(length = 1000)
    var description: String? = null,

    @Column(nullable = false, length = 100)
    var category: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 50)
    var unitOfMeasure: UnitOfMeasure = UnitOfMeasure.PIECES,

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    var unitPrice: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type", nullable = false, length = 50)
    var taxType: TaxType = TaxType.STANDARD,

    @Column(name = "has_discount", nullable = false)
    var hasDiscount: Boolean = false,

    @Column(name = "discount_rate", nullable = false, precision = 5, scale = 2)
    var discountRate: BigDecimal = BigDecimal.ZERO,

    @Column(name = "image_url", length = 500)
    var imageUrl: String? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "synced_to_supplier_catalogue", nullable = false)
    var syncedToSupplierCatalogue: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PrePersist
    fun onPrePersist() {
        createdAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }

    @PreUpdate
    fun onPreUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
