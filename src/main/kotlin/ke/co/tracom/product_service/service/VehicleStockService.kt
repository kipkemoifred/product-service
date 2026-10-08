package ke.co.tracom.product_service.service

import ke.co.tracom.product_service.config.InventoryProperties
import ke.co.tracom.product_service.domain.enums.MovementType
import ke.co.tracom.product_service.domain.model.VehicleStock
import ke.co.tracom.product_service.dto.IssueToVehicleRequest
import ke.co.tracom.product_service.dto.StockOperationResponse
import ke.co.tracom.product_service.dto.VehicleStockResponse
import ke.co.tracom.product_service.exception.InsufficientStockException
import ke.co.tracom.product_service.exception.InvalidStockQuantityException
import ke.co.tracom.product_service.exception.MissingMandatoryInformationException
import ke.co.tracom.product_service.repository.InventoryStockRepository
import ke.co.tracom.product_service.repository.VehicleStockRepository
import jakarta.persistence.criteria.Predicate
import ke.co.tracom.product_service.domain.model.Product
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class VehicleStockService(
    private val vehicleStockRepository: VehicleStockRepository,
    private val inventoryStockRepository: InventoryStockRepository,
    private val productService: ProductService,
    private val stockAuditService: StockAuditService,
    private val backOfficeSyncService: BackOfficeSyncService,
    private val properties: InventoryProperties
) {

    @Transactional(readOnly = true)
    fun getVehicleStock(vehicleId: String, search: String? = null): List<VehicleStockResponse> {
        if (vehicleId.isBlank()) {
            throw MissingMandatoryInformationException("EX-06: Missing Mandatory Information: vehicleId is required")
        }
        val cleanSearch = if (search.isNullOrBlank()) null else search.trim()
        val vId = vehicleId.trim()

        val spec = Specification<VehicleStock> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            predicates.add(cb.equal(root.get<String>("vehicleId"), vId))

            if (cleanSearch != null) {
                val term = "%${cleanSearch.lowercase()}%"
                val productPath = root.get<Product>("product")
                val namePred = cb.like(cb.lower(productPath.get("name")), term)
                val codePred = cb.like(cb.lower(productPath.get("productCode")), term)
                predicates.add(cb.or(namePred, codePred))
            }

            cb.and(*predicates.toTypedArray())
        }

        return vehicleStockRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "product.name"))
            .map { it.toResponse() }
    }

    @Transactional
    fun issueStockToVehicle(request: IssueToVehicleRequest): StockOperationResponse {
        if (request.vehicleId.isBlank()) {
            throw MissingMandatoryInformationException("EX-06: Missing Mandatory Information: Vehicle ID is required")
        }
        if (request.quantity <= BigDecimal.ZERO) {
            throw InvalidStockQuantityException("EX-03: Invalid Stock Quantity: Quantity must be greater than zero")
        }

        val product = when {
            request.productId != null -> productService.findProductEntityById(request.productId)
            !request.productCode.isNullOrBlank() -> productService.findProductEntityByCode(request.productCode)
            else -> throw MissingMandatoryInformationException("EX-06: Missing Mandatory Information: Either productId or productCode must be provided")
        }

        // Deduct from warehouse inventory if requested
        if (request.deductFromWarehouse) {
            val warehouseStock = inventoryStockRepository.findByProductId(product.id!!)
                .orElseThrow {
                    ke.co.tracom.product_service.exception.ProductNotFoundException(
                        "EX-02: Product Not Found: Stock record for product ${product.productCode} does not exist"
                    )
                }

            if (!properties.allowNegative && warehouseStock.availableQuantity < request.quantity) {
                throw InsufficientStockException(
                    "EX-04: Insufficient Stock: Warehouse available stock (${warehouseStock.availableQuantity}) is insufficient to issue ${request.quantity} units to vehicle ${request.vehicleId}",
                    mapOf(
                        "availableQuantity" to warehouseStock.availableQuantity,
                        "requestedQuantity" to request.quantity,
                        "productCode" to product.productCode,
                        "vehicleId" to request.vehicleId
                    )
                )
            }

            val previousWarehouseQty = warehouseStock.availableQuantity
            val newWarehouseQty = warehouseStock.availableQuantity.subtract(request.quantity)
            warehouseStock.availableQuantity = newWarehouseQty
            inventoryStockRepository.save(warehouseStock)

            stockAuditService.recordMovement(
                product = product,
                movementType = MovementType.ISSUE_TO_VEHICLE,
                quantityChanged = request.quantity.negate(),
                previousQuantity = previousWarehouseQty,
                newQuantity = newWarehouseQty,
                reason = "Transfer to vehicle ${request.vehicleId}: ${request.notes ?: ""}".trim(),
                referenceNumber = request.referenceNumber,
                vehicleId = request.vehicleId,
                performedBy = request.performedBy ?: "PORTAL_USER",
                syncedToBackOffice = true
            )
        }

        // Add to vehicle stock
        val vehicleStock = vehicleStockRepository.findByVehicleIdAndProductId(request.vehicleId.trim(), product.id!!)
            .orElseGet {
                VehicleStock(
                    vehicleId = request.vehicleId.trim(),
                    product = product,
                    availableQuantity = BigDecimal.ZERO,
                    orderedQuantity = BigDecimal.ZERO
                )
            }

        val previousVehicleQty = vehicleStock.availableQuantity
        val newVehicleQty = vehicleStock.availableQuantity.add(request.quantity)
        vehicleStock.availableQuantity = newVehicleQty
        val savedVehicleStock = vehicleStockRepository.save(vehicleStock)

        val movement = stockAuditService.recordMovement(
            product = product,
            movementType = MovementType.ISSUE_TO_VEHICLE,
            quantityChanged = request.quantity,
            previousQuantity = previousVehicleQty,
            newQuantity = newVehicleQty,
            reason = "Loaded onto vehicle ${request.vehicleId}: ${request.notes ?: ""}".trim(),
            referenceNumber = request.referenceNumber,
            vehicleId = request.vehicleId,
            performedBy = request.performedBy ?: "PORTAL_USER",
            syncedToBackOffice = true
        )

        // Sync with back-office
        backOfficeSyncService.syncStockAdjustmentToBackOffice(
            productCode = product.productCode,
            previousQuantity = previousVehicleQty,
            newQuantity = newVehicleQty,
            adjustmentDelta = request.quantity,
            reason = "Stock issued to vehicle ${request.vehicleId}",
            vehicleId = request.vehicleId
        )

        return StockOperationResponse(
            productId = product.id!!,
            productCode = product.productCode,
            productName = product.name,
            operation = "ISSUE_TO_VEHICLE",
            previousQuantity = previousVehicleQty,
            newQuantity = newVehicleQty,
            orderedQuantity = savedVehicleStock.orderedQuantity,
            quantityAvailableForSale = savedVehicleStock.quantityAvailableForSale,
            vehicleId = request.vehicleId,
            syncedToBackOffice = true,
            movementId = movement.id,
            message = "Successfully issued ${request.quantity} units of ${product.name} to vehicle ${request.vehicleId}"
        )
    }

    private fun VehicleStock.toResponse(): VehicleStockResponse {
        return VehicleStockResponse(
            vehicleId = this.vehicleId,
            productId = this.product.id!!,
            productCode = this.product.productCode,
            productName = this.product.name,
            availableQuantity = this.availableQuantity,
            orderedQuantity = this.orderedQuantity,
            quantityAvailableForSale = this.quantityAvailableForSale,
            lastUpdated = this.lastUpdated
        )
    }
}
