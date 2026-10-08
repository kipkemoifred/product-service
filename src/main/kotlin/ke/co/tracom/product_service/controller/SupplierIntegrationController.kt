package ke.co.tracom.product_service.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import ke.co.tracom.product_service.dto.AddStockRequest
import ke.co.tracom.product_service.dto.ApiResponse
import ke.co.tracom.product_service.dto.IssueToVehicleRequest
import ke.co.tracom.product_service.dto.StockOperationResponse
import ke.co.tracom.product_service.service.InventoryService
import ke.co.tracom.product_service.service.VehicleStockService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Supplier Integration", description = "Endpoints for supplier APIs and client system automated stock synchronization (AC5)")
@RestController
@RequestMapping("/api/inventory/supplier")
class SupplierIntegrationController(
    private val vehicleStockService: VehicleStockService,
    private val inventoryService: InventoryService
) {

    /**
     * AC5:
     * Given a supplier with API integration, when stock is issued to a vehicle,
     * then the mobile app reflects the loaded quantities without manual entry.
     */
    @Operation(
        summary = "Supplier API vehicle stock issue (AC5)",
        description = "Automated integration endpoint for suppliers to issue loaded quantities directly to a delivery vehicle. Instantly reflected in mobile app."
    )
    @ApiResponses(
        value = [
            SwaggerResponse(responseCode = "200", description = "Stock successfully loaded onto vehicle from supplier system"),
            SwaggerResponse(responseCode = "400", description = "Invalid stock quantity (EX-03) or insufficient stock (EX-04)"),
            SwaggerResponse(responseCode = "404", description = "Product not found (EX-02)")
        ]
    )
    @PostMapping("/stock-issue")
    fun issueStockFromSupplier(
        @RequestBody request: IssueToVehicleRequest
    ): ResponseEntity<ApiResponse<StockOperationResponse>> {
        val result = vehicleStockService.issueStockToVehicle(
            request.copy(
                performedBy = request.performedBy ?: "SUPPLIER_API_INTEGRATION",
                notes = request.notes ?: "Automated supplier API vehicle stock issue"
            )
        )
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Supplier stock issued successfully to vehicle ${request.vehicleId}. Mobile app reflects loaded quantities immediately.",
                data = result
            )
        )
    }

    /**
     * API integration to pull / push stock directly from client system.
     */
    @Operation(
        summary = "Supplier / Client ERP stock sync",
        description = "Automated endpoint to push/pull inventory stock adjustments directly from external client ERP or WMS."
    )
    @ApiResponses(
        value = [
            SwaggerResponse(responseCode = "200", description = "Stock successfully synced from client system"),
            SwaggerResponse(responseCode = "400", description = "Invalid quantity or missing product (EX-03, EX-06)"),
            SwaggerResponse(responseCode = "404", description = "Product not found (EX-02)")
        ]
    )
    @PostMapping("/stock-sync")
    fun syncStockFromSupplier(
        @RequestBody request: AddStockRequest
    ): ResponseEntity<ApiResponse<StockOperationResponse>> {
        val result = inventoryService.addStock(
            request.copy(
                performedBy = request.performedBy ?: "CLIENT_API_INTEGRATION",
                reason = request.reason ?: "Automated API stock sync from client system"
            )
        )
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Stock synchronized from client system API successfully",
                data = result
            )
        )
    }
}
