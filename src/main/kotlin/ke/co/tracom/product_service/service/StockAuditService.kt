package ke.co.tracom.product_service.service

import jakarta.persistence.criteria.Predicate
import ke.co.tracom.product_service.domain.enums.MovementType
import ke.co.tracom.product_service.domain.model.Product
import ke.co.tracom.product_service.domain.model.StockMovement
import ke.co.tracom.product_service.dto.StockMovementResponse
import ke.co.tracom.product_service.repository.StockMovementRepository
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class StockAuditService(
    private val movementRepository: StockMovementRepository
) {
    @Transactional
    fun recordMovement(
        product: Product,
        movementType: MovementType,
        quantityChanged: BigDecimal,
        previousQuantity: BigDecimal,
        newQuantity: BigDecimal,
        reason: String,
        referenceNumber: String? = null,
        vehicleId: String? = null,
        performedBy: String = "PORTAL_USER",
        syncedToBackOffice: Boolean = true
    ): StockMovement {
        val movement = StockMovement(
            product = product,
            movementType = movementType,
            quantityChanged = quantityChanged,
            previousQuantity = previousQuantity,
            newQuantity = newQuantity,
            reason = reason,
            referenceNumber = referenceNumber,
            vehicleId = vehicleId,
            performedBy = performedBy,
            syncedToBackOffice = syncedToBackOffice
        )
        return movementRepository.save(movement)
    }

    @Transactional(readOnly = true)
    fun getMovements(
        productId: Long? = null,
        productCode: String? = null,
        movementType: MovementType? = null,
        vehicleId: String? = null
    ): List<StockMovementResponse> {
        val cleanCode = if (productCode.isNullOrBlank()) null else productCode.trim()
        val cleanVehicleId = if (vehicleId.isNullOrBlank()) null else vehicleId.trim()

        val spec = Specification<StockMovement> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (productId != null) {
                predicates.add(cb.equal(root.get<Product>("product").get<Long>("id"), productId))
            }
            if (cleanCode != null) {
                predicates.add(cb.equal(root.get<Product>("product").get<String>("productCode"), cleanCode))
            }
            if (movementType != null) {
                predicates.add(cb.equal(root.get<MovementType>("movementType"), movementType))
            }
            if (cleanVehicleId != null) {
                predicates.add(cb.equal(root.get<String>("vehicleId"), cleanVehicleId))
            }

            if (predicates.isEmpty()) cb.conjunction() else cb.and(*predicates.toTypedArray())
        }

        return movementRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"))
            .map { it.toResponse() }
    }

    private fun StockMovement.toResponse(): StockMovementResponse {
        return StockMovementResponse(
            id = this.id!!,
            productId = this.product.id!!,
            productCode = this.product.productCode,
            productName = this.product.name,
            movementType = this.movementType.name,
            quantityChanged = this.quantityChanged,
            previousQuantity = this.previousQuantity,
            newQuantity = this.newQuantity,
            reason = this.reason,
            referenceNumber = this.referenceNumber,
            vehicleId = this.vehicleId,
            performedBy = this.performedBy,
            syncedToBackOffice = this.syncedToBackOffice,
            createdAt = this.createdAt
        )
    }
}
