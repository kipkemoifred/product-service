package ke.co.tracom.product_service.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import ke.co.tracom.product_service.domain.enums.MovementType
import ke.co.tracom.product_service.dto.ApiResponse
import ke.co.tracom.product_service.dto.StockMovementResponse
import ke.co.tracom.product_service.service.StockAuditService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Stock Audit Trail", description = "Endpoints for querying historical inventory movements and audit logs")
@RestController
@RequestMapping("/api/inventory/audit-trail")
class StockAuditController(
    private val stockAuditService: StockAuditService
) {

    /**
     * Post-Condition:
     * The system shall record a stock movement/audit trail for all stock additions,
     * removals, adjustments, and vehicle movements.
     */
    @Operation(
        summary = "Query inventory audit trail movements",
        description = "Retrieves immutable audit records for all stock transactions (additions, removals, adjustments, and vehicle issues) with before/after quantities and audit reasons."
    )
    @ApiResponses(
        value = [
            SwaggerResponse(responseCode = "200", description = "Audit trail records retrieved successfully")
        ]
    )
    @GetMapping
    fun getAuditTrail(
        @Parameter(description = "Filter by product ID") @RequestParam(required = false) productId: Long?,
        @Parameter(description = "Filter by product code") @RequestParam(required = false) productCode: String?,
        @Parameter(description = "Filter by movement type (ADDITION, ADJUSTMENT, REMOVAL, ISSUE_TO_VEHICLE, etc.)") @RequestParam(required = false) movementType: MovementType?,
        @Parameter(description = "Filter by vehicle ID") @RequestParam(required = false) vehicleId: String?
    ): ResponseEntity<ApiResponse<List<StockMovementResponse>>> {
        val movements = stockAuditService.getMovements(productId, productCode, movementType, vehicleId)
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Audit trail movements retrieved successfully",
                data = movements
            )
        )
    }
}
