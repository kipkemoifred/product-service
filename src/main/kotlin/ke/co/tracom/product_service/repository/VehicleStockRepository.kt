package ke.co.tracom.product_service.repository

import ke.co.tracom.product_service.domain.model.VehicleStock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface VehicleStockRepository : JpaRepository<VehicleStock, Long>, JpaSpecificationExecutor<VehicleStock> {
    fun findByVehicleId(vehicleId: String): List<VehicleStock>
    fun findByVehicleIdAndProductId(vehicleId: String, productId: Long): Optional<VehicleStock>
    fun findByVehicleIdAndProductProductCode(vehicleId: String, productCode: String): Optional<VehicleStock>
}
