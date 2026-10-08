package ke.co.tracom.product_service.repository

import ke.co.tracom.product_service.domain.model.InventoryStock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface InventoryStockRepository : JpaRepository<InventoryStock, Long>, JpaSpecificationExecutor<InventoryStock> {
    fun findByProductId(productId: Long): Optional<InventoryStock>
    fun findByProductProductCode(productCode: String): Optional<InventoryStock>
}
