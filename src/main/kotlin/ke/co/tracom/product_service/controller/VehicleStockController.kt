package ke.co.tracom.product_service.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import ke.co.tracom.product_service.dto.ApiResponse
import ke.co.tracom.product_service.dto.IssueToVehicleRequest
import ke.co.tracom.product_service.dto.StockOperationResponse
import ke.co.tracom.product_service.dto.VehicleStockResponse
import ke.co.tracom.product_service.service.VehicleStockService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Vehicle Stock", description = "Endpoints for monitoring and issuing stock loaded onto delivery vehicles (AC1, AC5)")
@RestController
@RequestMapping("/api/inventory/vehicles")
class VehicleStockController(
    private val vehicleStockService: VehicleStockService
) {

    /**
     * AC1 & AC5:
     * Mobile app / portal queries vehicle stock. Reflects loaded quantities without manual entry.
     */
    @Operation(
        summary = "Get loaded stock for vehicle (AC1, AC5)",
        description = "Returns all products loaded on the specified vehicle along with available quantity, ordered quantity, and quantity available for sale."
    )
    @ApiResponses(
        value = [
            SwaggerResponse(responseCode = "200", description = "Vehicle stock records retrieved successfully"),
            SwaggerResponse(responseCode = "400", description = "Missing vehicle ID (EX-06)")
        ]
    )
    @GetMapping("/{vehicleId}/stock")
    fun getVehicleStock(
        @Parameter(description = "Vehicle registration or identifier") @PathVariable vehicleId: String,
        @Parameter(description = "Optional search query matching product name or code") @RequestParam(required = false) search: String?
    ): ResponseEntity<ApiResponse<List<VehicleStockResponse>>> {
        val stock = vehicleStockService.getVehicleStock(vehicleId, search)
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Vehicle stock records retrieved successfully",
                data = stock
            )
        )
    }

    /**
     * Issue stock to vehicle:
     * Increases vehicle loaded quantity, deducts warehouse inventory if specified,
     * records audit trail and syncs to back office.
     */
    @Operation(
        summary = "Issue stock to delivery vehicle",
        description = "Transfers stock from warehouse to vehicle, updates balances, records ISSUE_TO_VEHICLE audit trail, and syncs to back office."
    )
    @ApiResponses(
        value = [
            SwaggerResponse(responseCode = "200", description = "Stock successfully issued to vehicle"),
            SwaggerResponse(responseCode = "400", description = "Insufficient warehouse stock (EX-04) or invalid quantity (EX-03)"),
            SwaggerResponse(responseCode = "404", description = "Product not found (EX-02)")
        ]
    )
    @PostMapping("/issue-stock")
    fun issueStockToVehicle(
        @RequestBody request: IssueToVehicleRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<StockOperationResponse>> {
        val performedBy = request.performedBy ?: httpRequest.getHeader("X-User-Id") ?: "PORTAL_USER"
        val response = vehicleStockService.issueStockToVehicle(request.copy(performedBy = performedBy))
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = response.message,
                data = response
            )
        )
    }
}
