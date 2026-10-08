package ke.co.tracom.product_service.service

import ke.co.tracom.product_service.domain.enums.MovementType
import ke.co.tracom.product_service.domain.enums.TaxType
import ke.co.tracom.product_service.domain.enums.UnitOfMeasure
import ke.co.tracom.product_service.domain.model.InventoryStock
import ke.co.tracom.product_service.domain.model.Product
import ke.co.tracom.product_service.dto.CreateProductRequest
import ke.co.tracom.product_service.dto.ProductResponse
import ke.co.tracom.product_service.dto.UpdateProductRequest
import ke.co.tracom.product_service.exception.DuplicateProductException
import ke.co.tracom.product_service.exception.InvalidStockQuantityException
import ke.co.tracom.product_service.exception.MissingMandatoryInformationException
import ke.co.tracom.product_service.exception.ProductNotFoundException
import ke.co.tracom.product_service.repository.InventoryStockRepository
import ke.co.tracom.product_service.repository.ProductRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val inventoryStockRepository: InventoryStockRepository,
    private val backOfficeSyncService: BackOfficeSyncService,
    private val stockAuditService: StockAuditService
) {

    @Transactional
    fun createProduct(request: CreateProductRequest, performedBy: String = "PORTAL_USER"): ProductResponse {
        // EX-06: Missing Mandatory Information validation
        validateProductRequest(request)

        // EX-05: Duplicate Product validation
        if (productRepository.existsByProductCode(request.productCode.trim())) {
            throw DuplicateProductException(
                "EX-05: Duplicate Product: Product with code '${request.productCode.trim()}' already exists",
                mapOf("productCode" to request.productCode.trim())
            )
        }

        // Parse enums
        val uom = try {
            UnitOfMeasure.fromString(request.unitOfMeasure)
        } catch (e: Exception) {
            throw MissingMandatoryInformationException(
                "EX-06: Missing Mandatory Information: Invalid unit of measure '${request.unitOfMeasure}'. Supported: ${UnitOfMeasure.values().joinToString()}",
                mapOf("unitOfMeasure" to request.unitOfMeasure)
            )
        }

        val tax = TaxType.fromString(request.taxType)

        val product = Product(
            name = request.name.trim(),
            productCode = request.productCode.trim(),
            description = request.description?.trim(),
            category = request.category.trim(),
            unitOfMeasure = uom,
            unitPrice = request.unitPrice,
            taxType = tax,
            hasDiscount = request.hasDiscount,
            discountRate = if (request.hasDiscount) request.discountRate else BigDecimal.ZERO,
            imageUrl = request.imageUrl?.trim(),
            active = true,
            syncedToSupplierCatalogue = false
        )

        val savedProduct = productRepository.save(product)

        // Initialize inventory stock balance
        val initialStockQty = request.initialStock ?: BigDecimal.ZERO
        val inventoryStock = InventoryStock(
            product = savedProduct,
            availableQuantity = initialStockQty,
            orderedQuantity = BigDecimal.ZERO,
            reorderThreshold = BigDecimal.ZERO
        )
        inventoryStockRepository.save(inventoryStock)

        // Record initial stock movement if quantity > 0
        if (initialStockQty > BigDecimal.ZERO) {
            stockAuditService.recordMovement(
                product = savedProduct,
                movementType = MovementType.INITIAL_STOCK,
                quantityChanged = initialStockQty,
                previousQuantity = BigDecimal.ZERO,
                newQuantity = initialStockQty,
                reason = "Initial stock allocation upon product creation",
                performedBy = performedBy,
                syncedToBackOffice = true
            )
        }

        // AC3: Sync product to supplier's catalogue
        val synced = backOfficeSyncService.syncProductToSupplierCatalogue(savedProduct)
        savedProduct.syncedToSupplierCatalogue = synced
        productRepository.save(savedProduct)

        return savedProduct.toResponse()
    }

    private fun validateProductRequest(request: CreateProductRequest) {
        val missingFields = mutableListOf<String>()
        if (request.name.isBlank()) missingFields.add("name")
        if (request.productCode.isBlank()) missingFields.add("productCode")
        if (request.category.isBlank()) missingFields.add("category")
        if (request.unitOfMeasure.isBlank()) missingFields.add("unitOfMeasure")

        if (missingFields.isNotEmpty()) {
            throw MissingMandatoryInformationException(
                "EX-06: Missing Mandatory Information: The following required fields are missing: ${missingFields.joinToString(", ")}",
                mapOf("missingFields" to missingFields)
            )
        }

        // EX-03: Invalid unit price / stock validation
        if (request.unitPrice <= BigDecimal.ZERO) {
            throw InvalidStockQuantityException(
                "EX-03: Invalid Stock Quantity: Unit price must be greater than zero",
                mapOf("unitPrice" to request.unitPrice)
            )
        }

        if (request.initialStock != null && request.initialStock < BigDecimal.ZERO) {
            throw InvalidStockQuantityException(
                "EX-03: Invalid Stock Quantity: Initial stock cannot be negative",
                mapOf("initialStock" to request.initialStock)
            )
        }
    }

    @Transactional(readOnly = true)
    fun getProductById(id: Long): ProductResponse {
        return findProductEntityById(id).toResponse()
    }

    @Transactional(readOnly = true)
    fun getProductByCode(productCode: String): ProductResponse {
        return findProductEntityByCode(productCode).toResponse()
    }

    @Transactional(readOnly = true)
    fun findProductEntityById(id: Long): Product {
        return productRepository.findById(id).orElseThrow {
            ProductNotFoundException(
                "EX-02: Product Not Found: Product with ID $id does not exist in the inventory catalogue",
                mapOf("productId" to id)
            )
        }
    }

    @Transactional(readOnly = true)
    fun findProductEntityByCode(productCode: String): Product {
        return productRepository.findByProductCode(productCode.trim()).orElseThrow {
            ProductNotFoundException(
                "EX-02: Product Not Found: Product with code '$productCode' does not exist in the inventory catalogue",
                mapOf("productCode" to productCode)
            )
        }
    }

    @Transactional(readOnly = true)
    fun searchProducts(search: String?, category: String?, active: Boolean? = true): List<ProductResponse> {
        val spec = Specification<Product> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (!search.isNullOrBlank()) {
                val term = "%${search.trim().lowercase()}%"
                val namePred = cb.like(cb.lower(root.get("name")), term)
                val codePred = cb.like(cb.lower(root.get("productCode")), term)
                predicates.add(cb.or(namePred, codePred))
            }

            if (!category.isNullOrBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().lowercase()))
            }

            if (active != null) {
                predicates.add(cb.equal(root.get<Boolean>("active"), active))
            }

            if (predicates.isEmpty()) cb.conjunction() else cb.and(*predicates.toTypedArray())
        }

        return productRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "name")).map { it.toResponse() }
    }

    @Transactional
    fun updateProduct(id: Long, request: UpdateProductRequest): ProductResponse {
        val product = findProductEntityById(id)
        request.name?.let { if (it.isNotBlank()) product.name = it.trim() }
        request.description?.let { product.description = it.trim() }
        request.category?.let { if (it.isNotBlank()) product.category = it.trim() }
        request.unitOfMeasure?.let { if (it.isNotBlank()) product.unitOfMeasure = UnitOfMeasure.fromString(it) }
        request.unitPrice?.let {
            if (it <= BigDecimal.ZERO) throw InvalidStockQuantityException("EX-03: Invalid Stock Quantity: Unit price must be greater than zero")
            product.unitPrice = it
        }
        request.taxType?.let { product.taxType = TaxType.fromString(it) }
        request.hasDiscount?.let { product.hasDiscount = it }
        request.discountRate?.let { product.discountRate = it }
        request.imageUrl?.let { product.imageUrl = it.trim() }
        request.active?.let { product.active = it }

        val updated = productRepository.save(product)
        backOfficeSyncService.syncProductToSupplierCatalogue(updated)
        return updated.toResponse()
    }

    fun Product.toResponse(): ProductResponse {
        return ProductResponse(
            id = this.id!!,
            name = this.name,
            productCode = this.productCode,
            description = this.description,
            category = this.category,
            unitOfMeasure = this.unitOfMeasure.name,
            unitPrice = this.unitPrice,
            taxType = this.taxType.name,
            hasDiscount = this.hasDiscount,
            discountRate = this.discountRate,
            imageUrl = this.imageUrl,
            active = this.active,
            syncedToSupplierCatalogue = this.syncedToSupplierCatalogue,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}
