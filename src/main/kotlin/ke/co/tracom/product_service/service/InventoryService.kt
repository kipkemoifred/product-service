package ke.co.tracom.product_service.service

import jakarta.persistence.criteria.Predicate
import ke.co.tracom.product_service.config.InventoryProperties
import ke.co.tracom.product_service.domain.enums.MovementType
import ke.co.tracom.product_service.domain.model.InventoryStock
import ke.co.tracom.product_service.domain.model.Product
import ke.co.tracom.product_service.domain.model.VehicleStock
import ke.co.tracom.product_service.dto.*
import ke.co.tracom.product_service.exception.InsufficientStockException
import ke.co.tracom.product_service.exception.InvalidStockQuantityException
import ke.co.tracom.product_service.exception.MissingMandatoryInformationException
import ke.co.tracom.product_service.exception.ProductNotFoundException
import ke.co.tracom.product_service.repository.InventoryStockRepository
import ke.co.tracom.product_service.repository.VehicleStockRepository
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class InventoryService(
    private val inventoryStockRepository: InventoryStockRepository,
    private val vehicleStockRepository: VehicleStockRepository,
    private val productService: ProductService,
    private val stockAuditService: StockAuditService,
    private val backOfficeSyncService: BackOfficeSyncService,
    private val properties: InventoryProperties
) {

    /**
     * AC1: Given stock loaded on the vehicle, when the user opens Inventory -> Stock,
     * then each product shows available quantity, ordered quantity and quantity available for sale.
     * Also supports warehouse inventory view when vehicleId is not specified.
     */
    @Transactional(readOnly = true)
    fun getStockSummary(
        search: String? = null,
        category: String? = null,
        vehicleId: String? = null
    ): List<StockSummaryResponse> {
        val cleanSearch = if (search.isNullOrBlank()) null else search.trim()
        val cleanCat = if (category.isNullOrBlank()) null else category.trim()

        if (!vehicleId.isNullOrBlank()) {
            val vId = vehicleId.trim()
            val spec = Specification<VehicleStock> { root, _, cb ->
                val predicates = mutableListOf<Predicate>()
                predicates.add(cb.equal(root.get<String>("vehicleId"), vId))

                val productPath = root.get<Product>("product")
                if (cleanSearch != null) {
                    val term = "%${cleanSearch.lowercase()}%"
                    val namePred = cb.like(cb.lower(productPath.get("name")), term)
                    val codePred = cb.like(cb.lower(productPath.get("productCode")), term)
                    predicates.add(cb.or(namePred, codePred))
                }

                if (cleanCat != null) {
                    predicates.add(cb.equal(cb.lower(productPath.get("category")), cleanCat.lowercase()))
                }

                cb.and(*predicates.toTypedArray())
            }

            val vehicleStocks = vehicleStockRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "product.name"))
            return vehicleStocks.map { vs ->
                StockSummaryResponse(
                    productId = vs.product.id!!,
                    productCode = vs.product.productCode,
                    productName = vs.product.name,
                    category = vs.product.category,
                    unitOfMeasure = vs.product.unitOfMeasure.name,
                    unitPrice = vs.product.unitPrice,
                    availableQuantity = vs.availableQuantity,
                    orderedQuantity = vs.orderedQuantity,
                    quantityAvailableForSale = vs.quantityAvailableForSale,
                    vehicleId = vs.vehicleId,
                    lastUpdated = vs.lastUpdated
                )
            }
        }

        val spec = Specification<InventoryStock> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()
            val productPath = root.get<Product>("product")

            if (cleanSearch != null) {
                val term = "%${cleanSearch.lowercase()}%"
                val namePred = cb.like(cb.lower(productPath.get("name")), term)
                val codePred = cb.like(cb.lower(productPath.get("productCode")), term)
                predicates.add(cb.or(namePred, codePred))
            }

            if (cleanCat != null) {
                predicates.add(cb.equal(cb.lower(productPath.get("category")), cleanCat.lowercase()))
            }

            if (predicates.isEmpty()) cb.conjunction() else cb.and(*predicates.toTypedArray())
        }

        val stocks = inventoryStockRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "product.name"))
        return stocks.map { s ->
            StockSummaryResponse(
                productId = s.product.id!!,
                productCode = s.product.productCode,
                productName = s.product.name,
                category = s.product.category,
                unitOfMeasure = s.product.unitOfMeasure.name,
                unitPrice = s.product.unitPrice,
                availableQuantity = s.availableQuantity,
                orderedQuantity = s.orderedQuantity,
                quantityAvailableForSale = s.quantityAvailableForSale,
                vehicleId = null,
                lastUpdated = s.lastUpdated
            )
        }
    }

    /**
     * AP2: Add Stock (AP2: Add, Reduce or Remove)
     * Validates quantity (> 0), increases available stock, records movement, displays updated balance.
     */
    @Transactional
    fun addStock(request: AddStockRequest): StockOperationResponse {
        val qty = request.quantity ?: throw MissingMandatoryInformationException(
            "EX-06: Missing Mandatory Information: Stock quantity is required"
        )

        // EX-03: Invalid Stock Quantity
        if (qty <= BigDecimal.ZERO) {
            throw InvalidStockQuantityException(
                "EX-03: Invalid Stock Quantity: Quantity must be greater than zero. Received: $qty",
                mapOf("quantity" to qty)
            )
        }

        val product = resolveProduct(request.productId, request.productCode)
        val reason = request.reason?.takeIf { it.isNotBlank() } ?: "Stock Addition"
        val vehicleId = request.vehicleId?.takeIf { it.isNotBlank() }

        return if (vehicleId != null) {
            val vehicleStock = vehicleStockRepository.findByVehicleIdAndProductId(vehicleId, product.id!!)
                .orElseGet {
                    VehicleStock(
                        vehicleId = vehicleId,
                        product = product,
                        availableQuantity = BigDecimal.ZERO,
                        orderedQuantity = BigDecimal.ZERO
                    )
                }

            val prevQty = vehicleStock.availableQuantity
            val newQty = prevQty.add(qty)
            vehicleStock.availableQuantity = newQty
            val saved = vehicleStockRepository.save(vehicleStock)

            val movement = stockAuditService.recordMovement(
                product = product,
                movementType = MovementType.ADDITION,
                quantityChanged = qty,
                previousQuantity = prevQty,
                newQuantity = newQty,
                reason = reason,
                referenceNumber = request.referenceNumber,
                vehicleId = vehicleId,
                performedBy = request.performedBy ?: "PORTAL_USER",
                syncedToBackOffice = true
            )

            backOfficeSyncService.syncStockAdjustmentToBackOffice(
                productCode = product.productCode,
                previousQuantity = prevQty,
                newQuantity = newQty,
                adjustmentDelta = qty,
                reason = reason,
                vehicleId = vehicleId
            )

            StockOperationResponse(
                productId = product.id!!,
                productCode = product.productCode,
                productName = product.name,
                operation = "ADD_STOCK",
                previousQuantity = prevQty,
                newQuantity = newQty,
                orderedQuantity = saved.orderedQuantity,
                quantityAvailableForSale = saved.quantityAvailableForSale,
                vehicleId = vehicleId,
                syncedToBackOffice = true,
                movementId = movement.id,
                message = "Successfully added $qty units of ${product.name} to vehicle $vehicleId. New available balance: $newQty"
            )
        } else {
            val stock = getOrCreateWarehouseStock(product)
            val prevQty = stock.availableQuantity
            val newQty = prevQty.add(qty)
            stock.availableQuantity = newQty
            val saved = inventoryStockRepository.save(stock)

            val movement = stockAuditService.recordMovement(
                product = product,
                movementType = MovementType.ADDITION,
                quantityChanged = qty,
                previousQuantity = prevQty,
                newQuantity = newQty,
                reason = reason,
                referenceNumber = request.referenceNumber,
                performedBy = request.performedBy ?: "PORTAL_USER",
                syncedToBackOffice = true
            )

            backOfficeSyncService.syncStockAdjustmentToBackOffice(
                productCode = product.productCode,
                previousQuantity = prevQty,
                newQuantity = newQty,
                adjustmentDelta = qty,
                reason = reason
            )

            StockOperationResponse(
                productId = product.id!!,
                productCode = product.productCode,
                productName = product.name,
                operation = "ADD_STOCK",
                previousQuantity = prevQty,
                newQuantity = newQty,
                orderedQuantity = saved.orderedQuantity,
                quantityAvailableForSale = saved.quantityAvailableForSale,
                vehicleId = null,
                syncedToBackOffice = true,
                movementId = movement.id,
                message = "Successfully added $qty units of ${product.name}. New available balance: $newQty"
            )
        }
    }

    /**
     * AC2 & AP4: Adjust Stock
     * Given a product, when the user adjusts the quantity up or down and confirms,
     * then inventory is updated by exactly that amount and the change is synced to the back office.
     * Records previous quantity, new quantity, and adjustment reason.
     */
    @Transactional
    fun adjustStock(request: AdjustStockRequest): StockOperationResponse {
        val product = resolveProduct(request.productId, request.productCode)
        val reason = request.reason?.trim()
        if (reason.isNullOrBlank()) {
            throw MissingMandatoryInformationException(
                "EX-06: Missing Mandatory Information: Adjustment reason is mandatory"
            )
        }

        if (request.adjustmentQuantity == null && request.newQuantity == null) {
            throw MissingMandatoryInformationException(
                "EX-06: Missing Mandatory Information: Either adjustmentQuantity (delta) or newQuantity must be provided"
            )
        }

        val vehicleId = request.vehicleId?.takeIf { it.isNotBlank() }

        return if (vehicleId != null) {
            val vehicleStock = vehicleStockRepository.findByVehicleIdAndProductId(vehicleId, product.id!!)
                .orElseGet {
                    VehicleStock(
                        vehicleId = vehicleId,
                        product = product,
                        availableQuantity = BigDecimal.ZERO,
                        orderedQuantity = BigDecimal.ZERO
                    )
                }

            val prevQty = vehicleStock.availableQuantity
            val (newQty, delta) = calculateAdjustment(prevQty, request.adjustmentQuantity, request.newQuantity)

            if (!properties.allowNegative && newQty < BigDecimal.ZERO) {
                throw InsufficientStockException(
                    "EX-04: Insufficient Stock: Resulting vehicle stock quantity ($newQty) cannot be negative",
                    mapOf("previousQuantity" to prevQty, "attemptedNewQuantity" to newQty, "vehicleId" to vehicleId)
                )
            }

            vehicleStock.availableQuantity = newQty
            val saved = vehicleStockRepository.save(vehicleStock)

            val movement = stockAuditService.recordMovement(
                product = product,
                movementType = MovementType.ADJUSTMENT,
                quantityChanged = delta,
                previousQuantity = prevQty,
                newQuantity = newQty,
                reason = reason,
                referenceNumber = request.referenceNumber,
                vehicleId = vehicleId,
                performedBy = request.performedBy ?: "PORTAL_USER",
                syncedToBackOffice = true
            )

            // AC2: Synced to the back office
            val synced = backOfficeSyncService.syncStockAdjustmentToBackOffice(
                productCode = product.productCode,
                previousQuantity = prevQty,
                newQuantity = newQty,
                adjustmentDelta = delta,
                reason = reason,
                vehicleId = vehicleId
            )

            StockOperationResponse(
                productId = product.id!!,
                productCode = product.productCode,
                productName = product.name,
                operation = "ADJUST_STOCK",
                previousQuantity = prevQty,
                newQuantity = newQty,
                orderedQuantity = saved.orderedQuantity,
                quantityAvailableForSale = saved.quantityAvailableForSale,
                vehicleId = vehicleId,
                syncedToBackOffice = synced,
                movementId = movement.id,
                message = "Successfully adjusted stock for ${product.name} on vehicle $vehicleId. Updated balance: $newQty (change: $delta)"
            )
        } else {
            val stock = getOrCreateWarehouseStock(product)
            val prevQty = stock.availableQuantity
            val (newQty, delta) = calculateAdjustment(prevQty, request.adjustmentQuantity, request.newQuantity)

            if (!properties.allowNegative && newQty < BigDecimal.ZERO) {
                throw InsufficientStockException(
                    "EX-04: Insufficient Stock: Resulting stock balance ($newQty) cannot be negative",
                    mapOf("previousQuantity" to prevQty, "attemptedNewQuantity" to newQty)
                )
            }

            stock.availableQuantity = newQty
            val saved = inventoryStockRepository.save(stock)

            val movement = stockAuditService.recordMovement(
                product = product,
                movementType = MovementType.ADJUSTMENT,
                quantityChanged = delta,
                previousQuantity = prevQty,
                newQuantity = newQty,
                reason = reason,
                referenceNumber = request.referenceNumber,
                performedBy = request.performedBy ?: "PORTAL_USER",
                syncedToBackOffice = true
            )

            // AC2: Synced to the back office
            val synced = backOfficeSyncService.syncStockAdjustmentToBackOffice(
                productCode = product.productCode,
                previousQuantity = prevQty,
                newQuantity = newQty,
                adjustmentDelta = delta,
                reason = reason
            )

            StockOperationResponse(
                productId = product.id!!,
                productCode = product.productCode,
                productName = product.name,
                operation = "ADJUST_STOCK",
                previousQuantity = prevQty,
                newQuantity = newQty,
                orderedQuantity = saved.orderedQuantity,
                quantityAvailableForSale = saved.quantityAvailableForSale,
                vehicleId = null,
                syncedToBackOffice = synced,
                movementId = movement.id,
                message = "Successfully adjusted stock for ${product.name}. Updated balance: $newQty (change: $delta)"
            )
        }
    }

    private fun calculateAdjustment(
        prevQty: BigDecimal,
        adjustmentDelta: BigDecimal?,
        newQuantity: BigDecimal?
    ): Pair<BigDecimal, BigDecimal> {
        return if (adjustmentDelta != null) {
            val target = prevQty.add(adjustmentDelta)
            Pair(target, adjustmentDelta)
        } else if (newQuantity != null) {
            if (newQuantity < BigDecimal.ZERO && !properties.allowNegative) {
                throw InvalidStockQuantityException("EX-03: Invalid Stock Quantity: Stock quantity cannot be negative")
            }
            val delta = newQuantity.subtract(prevQty)
            Pair(newQuantity, delta)
        } else {
            throw MissingMandatoryInformationException("EX-06: Missing Mandatory Information: Missing adjustment quantity")
        }
    }

    /**
     * AP5: Remove Stock
     * Validates sufficient stock available (EX-04), deducts quantity from available stock,
     * records removal and updated balance.
     */
    @Transactional
    fun removeStock(request: RemoveStockRequest): StockOperationResponse {
        val qty = request.quantity ?: throw MissingMandatoryInformationException(
            "EX-06: Missing Mandatory Information: Removal quantity is required"
        )

        // EX-03: Invalid Stock Quantity
        if (qty <= BigDecimal.ZERO) {
            throw InvalidStockQuantityException(
                "EX-03: Invalid Stock Quantity: Removal quantity must be greater than zero. Received: $qty",
                mapOf("quantity" to qty)
            )
        }

        // EX-06: Reason is mandatory for removal
        val reason = request.reason?.trim()
        if (reason.isNullOrBlank()) {
            throw MissingMandatoryInformationException(
                "EX-06: Missing Mandatory Information: Reason for removal is mandatory"
            )
        }

        val product = resolveProduct(request.productId, request.productCode)
        val vehicleId = request.vehicleId?.takeIf { it.isNotBlank() }

        return if (vehicleId != null) {
            val vehicleStock = vehicleStockRepository.findByVehicleIdAndProductId(vehicleId, product.id!!)
                .orElseThrow {
                    ProductNotFoundException(
                        "EX-02: Product Not Found: Product ${product.productCode} has no stock on vehicle $vehicleId"
                    )
                }

            val prevQty = vehicleStock.availableQuantity
            // EX-04: Insufficient Stock check
            if (!properties.allowNegative && prevQty < qty) {
                throw InsufficientStockException(
                    "EX-04: Insufficient Stock: Requested removal of $qty exceeds vehicle available stock of $prevQty",
                    mapOf("availableStock" to prevQty, "requestedRemoval" to qty, "vehicleId" to vehicleId)
                )
            }

            val newQty = prevQty.subtract(qty)
            vehicleStock.availableQuantity = newQty
            val saved = vehicleStockRepository.save(vehicleStock)

            val movement = stockAuditService.recordMovement(
                product = product,
                movementType = MovementType.REMOVAL,
                quantityChanged = qty.negate(),
                previousQuantity = prevQty,
                newQuantity = newQty,
                reason = reason,
                referenceNumber = request.referenceNumber,
                vehicleId = vehicleId,
                performedBy = request.performedBy ?: "PORTAL_USER",
                syncedToBackOffice = true
            )

            backOfficeSyncService.syncStockAdjustmentToBackOffice(
                productCode = product.productCode,
                previousQuantity = prevQty,
                newQuantity = newQty,
                adjustmentDelta = qty.negate(),
                reason = reason,
                vehicleId = vehicleId
            )

            StockOperationResponse(
                productId = product.id!!,
                productCode = product.productCode,
                productName = product.name,
                operation = "REMOVE_STOCK",
                previousQuantity = prevQty,
                newQuantity = newQty,
                orderedQuantity = saved.orderedQuantity,
                quantityAvailableForSale = saved.quantityAvailableForSale,
                vehicleId = vehicleId,
                syncedToBackOffice = true,
                movementId = movement.id,
                message = "Successfully removed $qty units of ${product.name} from vehicle $vehicleId. Updated balance: $newQty"
            )
        } else {
            val stock = getOrCreateWarehouseStock(product)
            val prevQty = stock.availableQuantity

            // EX-04: Insufficient Stock check
            if (!properties.allowNegative && prevQty < qty) {
                throw InsufficientStockException(
                    "EX-04: Insufficient Stock: Requested removal of $qty exceeds available stock of $prevQty",
                    mapOf("availableStock" to prevQty, "requestedRemoval" to qty)
                )
            }

            val newQty = prevQty.subtract(qty)
            stock.availableQuantity = newQty
            val saved = inventoryStockRepository.save(stock)

            val movement = stockAuditService.recordMovement(
                product = product,
                movementType = MovementType.REMOVAL,
                quantityChanged = qty.negate(),
                previousQuantity = prevQty,
                newQuantity = newQty,
                reason = reason,
                referenceNumber = request.referenceNumber,
                performedBy = request.performedBy ?: "PORTAL_USER",
                syncedToBackOffice = true
            )

            backOfficeSyncService.syncStockAdjustmentToBackOffice(
                productCode = product.productCode,
                previousQuantity = prevQty,
                newQuantity = newQty,
                adjustmentDelta = qty.negate(),
                reason = reason
            )

            StockOperationResponse(
                productId = product.id!!,
                productCode = product.productCode,
                productName = product.name,
                operation = "REMOVE_STOCK",
                previousQuantity = prevQty,
                newQuantity = newQty,
                orderedQuantity = saved.orderedQuantity,
                quantityAvailableForSale = saved.quantityAvailableForSale,
                vehicleId = null,
                syncedToBackOffice = true,
                movementId = movement.id,
                message = "Successfully removed $qty units of ${product.name}. Updated balance: $newQty"
            )
        }
    }

    private fun resolveProduct(productId: Long?, productCode: String?): Product {
        return when {
            productId != null -> productService.findProductEntityById(productId)
            !productCode.isNullOrBlank() -> productService.findProductEntityByCode(productCode)
            else -> throw MissingMandatoryInformationException(
                "EX-06: Missing Mandatory Information: Product identifier (productId or productCode) is required"
            )
        }
    }

    private fun getOrCreateWarehouseStock(product: Product): InventoryStock {
        return inventoryStockRepository.findByProductId(product.id!!)
            .orElseGet {
                val stock = InventoryStock(
                    product = product,
                    availableQuantity = BigDecimal.ZERO,
                    orderedQuantity = BigDecimal.ZERO
                )
                inventoryStockRepository.save(stock)
            }
    }
}
