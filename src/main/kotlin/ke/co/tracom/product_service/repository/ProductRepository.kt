package ke.co.tracom.product_service.repository

import ke.co.tracom.product_service.domain.model.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ProductRepository : JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    fun findByProductCode(productCode: String): Optional<Product>
    fun existsByProductCode(productCode: String): Boolean
}
