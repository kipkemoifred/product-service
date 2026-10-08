package ke.co.tracom.product_service.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import ke.co.tracom.product_service.dto.*
import ke.co.tracom.product_service.service.InventoryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Inventory Management", description = "Endpoints for viewing stock balances (AC1), adding stock (AP2), adjusting stock (AC2, AP4), and removing stock (AP5)")
@RestController
@RequestMapping("/api/inventory")
class InventoryController(
    private val inventoryService: InventoryService
) {

    /**
     * AC1 & Normal Flow:
     * Given stock loaded on the vehicle (or warehouse), when the user opens Inventory -> Stock,
     * then each product shows available quantity, ordered quantity and quantity available for sale.
     */
    @Operation(
        summary = "View available stock (AC1)",
        description = "Displays stock records with available quantity, ordered quantity, and quantity available for sale. Supports searching and filtering by category or vehicleId."
    )
    @ApiResponses(
        value = [
            SwaggerResponse(responseCode = "200", description = "Stock records retrieved successfully"),
            SwaggerResponse(responseCode = "403", description = "Unauthorized inventory access (EX-01)")
        ]
    )
    @GetMapping("/stock")
    fun getStockSummary(
        @Parameter(description = "Search term by product name or code") @RequestParam(required = false) search: String?,
        @Parameter(description = "Filter by product category") @RequestParam(required = false) category: String?,
        @Parameter(description = "Vehicle ID to view vehicle-specific loaded stock") @RequestParam(required = false) vehicleId: String?
    ): ResponseEntity<ApiResponse<List<StockSummaryResponse>>> {
        val summary = inventoryService.getStockSummary(search, category, vehicleId)
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Inventory stock records retrieved successfully",
                data = summary
            )
        )
    }

    /**
     * AP2: Add Stock
     * User selects Add Stock, enters product and quantity.
     * System validates quantity, increases available stock, records addition, and displays updated balance.
     */
    @Operation(
        summary = "Add stock (AP2)",
        description = "Adds stock to warehouse or vehicle, validates quantity (> 0), updates balance, records audit movement, and syncs to back office."
    )
    @ApiResponses(
        value = [
            SwaggerResponse(responseCode = "200", description = "Stock added successfully and balance updated"),
            SwaggerResponse(responseCode = "400", description = "Invalid stock quantity (EX-03) or missing product info (EX-06)"),
            SwaggerResponse(responseCode = "404", description = "Product not found (EX-02)")
        ]
    )
    @PostMapping("/stock/add")
    fun addStock(
        @RequestBody request: AddStockRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<StockOperationResponse>> {
        val performedBy = request.performedBy ?: httpRequest.getHeader("X-User-Id") ?: "PORTAL_USER"
        val response = inventoryService.addStock(request.copy(performedBy = performedBy))
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = response.message,
                data = response
            )
        )
    }

    /**
     * AC2 & AP4: Adjust Stock
     * Given a product, when the user adjusts the quantity up or down and confirms,
     * then inventory is updated by exactly that amount and the change is synced to the back office.
     * System records previous quantity, new quantity, and adjustment reason.
     */
    @Operation(
        summary = "Adjust stock quantity up or down (AC2, AP4)",
        description = "Adjusts quantity up/down by specified delta or sets target stock. Records previous/new quantity and mandatory reason, and syncs change to back office."
    )
    @ApiResponses(
        value = [
            SwaggerResponse(responseCode = "200", description = "Stock adjusted successfully and synced to back office"),
            SwaggerResponse(responseCode = "400", description = "Missing reason (EX-06) or insufficient stock for negative adjustment (EX-04)"),
            SwaggerResponse(responseCode = "404", description = "Product not found (EX-02)")
        ]
    )
    @PostMapping("/stock/adjust")
    fun adjustStock(
        @RequestBody request: AdjustStockRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<StockOperationResponse>> {
        val performedBy = request.performedBy ?: httpRequest.getHeader("X-User-Id") ?: "PORTAL_USER"
        val response = inventoryService.adjustStock(request.copy(performedBy = performedBy))
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = response.message,
                data = response
            )
        )
    }

    /**
     * AP5: Remove Stock
     * User selects Remove Stock, enters quantity and reason.
     * System validates sufficient stock is available, deducts quantity, records removal and updated balance.
     */
    @Operation(
        summary = "Remove stock (AP5)",
        description = "Removes stock from warehouse or vehicle. Validates sufficient available stock (EX-04) and mandatory removal reason (EX-06)."
    )
    @ApiResponses(
        value = [
            SwaggerResponse(responseCode = "200", description = "Stock removed successfully"),
            SwaggerResponse(responseCode = "400", description = "Insufficient stock (EX-04) or missing reason/quantity (EX-06)"),
            SwaggerResponse(responseCode = "404", description = "Product not found (EX-02)")
        ]
    )
    @PostMapping("/stock/remove")
    fun removeStock(
        @RequestBody request: RemoveStockRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<StockOperationResponse>> {
        val performedBy = request.performedBy ?: httpRequest.getHeader("X-User-Id") ?: "PORTAL_USER"
        val response = inventoryService.removeStock(request.copy(performedBy = performedBy))
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = response.message,
                data = response
            )
        )
    }
}
