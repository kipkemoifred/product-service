package ke.co.tracom.product_service.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import ke.co.tracom.product_service.dto.ApiResponse
import ke.co.tracom.product_service.dto.CreateProductRequest
import ke.co.tracom.product_service.dto.ProductResponse
import ke.co.tracom.product_service.dto.UpdateProductRequest
import ke.co.tracom.product_service.service.ProductService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Product Catalog", description = "Endpoints for managing products, catalog search, and AP1 product creation")
@RestController
@RequestMapping("/api/inventory/products")
class ProductController(
    private val productService: ProductService
) {

    /**
     * AP1: Add Product
     * Given Add Product, when the user enters name, barcode/code, unit price, tax type,
     * discount flag/rate and description and saves, then the product is available for sale
     * and synced to the supplier's catalogue.
     */
    @Operation(
        summary = "Add a new product (AP1)",
        description = "Creates a product, validates mandatory fields and duplicate codes, activates it for sale, initializes stock, and syncs to supplier catalogue."
    )
    @ApiResponses(
        value = [
            SwaggerResponse(responseCode = "201", description = "Product created successfully and synced to supplier"),
            SwaggerResponse(responseCode = "400", description = "Missing mandatory fields (EX-06) or invalid unit price/stock (EX-03)"),
            SwaggerResponse(responseCode = "409", description = "Product code already exists (EX-05)")
        ]
    )
    @PostMapping
    fun createProduct(
        @Valid @RequestBody request: CreateProductRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<ProductResponse>> {
        val performedBy = httpRequest.getHeader("X-User-Id") ?: "PORTAL_USER"
        val response = productService.createProduct(request, performedBy)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse(
                success = true,
                message = "Product created successfully, available for sale and synced to supplier's catalogue",
                data = response
            )
        )
    }

    @Operation(
        summary = "Search and filter products",
        description = "Search products by name or code, filter by category and active status."
    )
    @GetMapping
    fun searchProducts(
        @Parameter(description = "Search term matching name or code") @RequestParam(required = false) search: String?,
        @Parameter(description = "Filter by product category") @RequestParam(required = false) category: String?,
        @Parameter(description = "Filter by active status") @RequestParam(required = false, defaultValue = "true") active: Boolean?
    ): ResponseEntity<ApiResponse<List<ProductResponse>>> {
        val products = productService.searchProducts(search, category, active)
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Products retrieved successfully",
                data = products
            )
        )
    }

    @Operation(summary = "Get product by ID", description = "Retrieves product details by internal ID.")
    @GetMapping("/{id}")
    fun getProductById(
        @Parameter(description = "Product internal ID") @PathVariable id: Long
    ): ResponseEntity<ApiResponse<ProductResponse>> {
        val product = productService.getProductById(id)
        return ResponseEntity.ok(ApiResponse(success = true, message = "Product found", data = product))
    }

    @Operation(summary = "Get product by product code/barcode", description = "Retrieves product details by product code.")
    @GetMapping("/code/{productCode}")
    fun getProductByCode(
        @Parameter(description = "Product code or barcode") @PathVariable productCode: String
    ): ResponseEntity<ApiResponse<ProductResponse>> {
        val product = productService.getProductByCode(productCode)
        return ResponseEntity.ok(ApiResponse(success = true, message = "Product found", data = product))
    }

    @Operation(summary = "Update product details", description = "Updates editable product fields and syncs updates to supplier.")
    @PutMapping("/{id}")
    fun updateProduct(
        @Parameter(description = "Product internal ID") @PathVariable id: Long,
        @RequestBody request: UpdateProductRequest
    ): ResponseEntity<ApiResponse<ProductResponse>> {
        val updated = productService.updateProduct(id, request)
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Product updated successfully",
                data = updated
            )
        )
    }
}
