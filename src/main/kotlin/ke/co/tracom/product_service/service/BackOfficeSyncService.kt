package ke.co.tracom.product_service.service

import ke.co.tracom.product_service.config.InventoryProperties
import ke.co.tracom.product_service.domain.enums.SyncStatus
import ke.co.tracom.product_service.domain.model.BackOfficeSyncLog
import ke.co.tracom.product_service.domain.model.Product
import ke.co.tracom.product_service.repository.BackOfficeSyncLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class BackOfficeSyncService(
    private val syncLogRepository: BackOfficeSyncLogRepository,
    private val properties: InventoryProperties
) {
    private val logger = LoggerFactory.getLogger(BackOfficeSyncService::class.java)

    @Transactional
    fun syncProductToSupplierCatalogue(product: Product): Boolean {
        if (!properties.supplier.autoSync) {
            logger.info("Supplier sync disabled by configuration for product {}", product.productCode)
            return false
        }

        val payload = """
            {
              "productCode": "${product.productCode}",
              "name": "${product.name}",
              "category": "${product.category}",
              "unitPrice": ${product.unitPrice},
              "unitOfMeasure": "${product.unitOfMeasure}",
              "taxType": "${product.taxType}",
              "hasDiscount": ${product.hasDiscount},
              "discountRate": ${product.discountRate}
            }
        """.trimIndent()

        val log = BackOfficeSyncLog(
            syncType = "SUPPLIER_CATALOGUE_SYNC",
            entityId = product.productCode,
            payload = payload,
            status = SyncStatus.SUCCESS,
            message = "Product successfully synced to supplier's catalogue"
        )
        syncLogRepository.save(log)
        logger.info("Product {} synced to supplier catalogue successfully", product.productCode)
        return true
    }

    @Transactional
    fun syncStockAdjustmentToBackOffice(
        productCode: String,
        previousQuantity: BigDecimal,
        newQuantity: BigDecimal,
        adjustmentDelta: BigDecimal,
        reason: String,
        vehicleId: String? = null
    ): Boolean {
        if (!properties.backOffice.autoSync) {
            logger.info("Back-office sync disabled by configuration for stock adjustment {}", productCode)
            return false
        }

        val payload = """
            {
              "productCode": "$productCode",
              "vehicleId": ${vehicleId?.let { "\"$it\"" } ?: "null"},
              "previousQuantity": $previousQuantity,
              "newQuantity": $newQuantity,
              "adjustmentDelta": $adjustmentDelta,
              "reason": "$reason"
            }
        """.trimIndent()

        val log = BackOfficeSyncLog(
            syncType = "STOCK_ADJUSTMENT_SYNC",
            entityId = productCode,
            payload = payload,
            status = SyncStatus.SUCCESS,
            message = "Stock adjustment change successfully synced to the back office"
        )
        syncLogRepository.save(log)
        logger.info("Stock adjustment for product {} synced to back office successfully", productCode)
        return true
    }
}
