package ke.co.tracom.product_service.repository

import ke.co.tracom.product_service.domain.model.StockMovement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

@Repository
interface StockMovementRepository : JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {
    fun findByProductIdOrderByCreatedAtDesc(productId: Long): List<StockMovement>
    fun findByVehicleIdOrderByCreatedAtDesc(vehicleId: String): List<StockMovement>
    fun findAllByOrderByCreatedAtDesc(): List<StockMovement>
}
