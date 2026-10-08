package ke.co.tracom.product_service.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import ke.co.tracom.product_service.dto.ApiResponse
import ke.co.tracom.product_service.dto.BulkUploadResult
import ke.co.tracom.product_service.service.BulkUploadService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@Tag(name = "Bulk Inventory Upload", description = "Endpoints for CSV template download and bulk inventory file upload (AC4)")
@RestController
@RequestMapping("/api/inventory")
class BulkUploadController(
    private val bulkUploadService: BulkUploadService
) {

    /**
     * AC4: Published CSV template download endpoint.
     */
    @Operation(
        summary = "Download published inventory CSV template (AC4)",
        description = "Provides the official CSV file template with headers and sample records for bulk inventory uploading."
    )
    @ApiResponses(
        value = [
            SwaggerResponse(responseCode = "200", description = "CSV template returned successfully")
        ]
    )
    @GetMapping("/template/csv")
    fun downloadCsvTemplate(): ResponseEntity<String> {
        val template = bulkUploadService.getCsvTemplate()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventory_template.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(template)
    }

    /**
     * AC4: Upload inventory file in CSV format via multipart form-data.
     */
    @Operation(
        summary = "Upload inventory CSV file (AC4)",
        description = "Uploads a CSV file multipart form. Valid rows are loaded, and rejected rows are reported with line number and reasons."
    )
    @ApiResponses(
        value = [
            SwaggerResponse(responseCode = "200", description = "File processed with detailed successful/rejected breakdown")
        ]
    )
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadInventoryFile(
        @Parameter(description = "CSV file adhering to published template") @RequestParam("file") file: MultipartFile,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<BulkUploadResult>> {
        val performedBy = httpRequest.getHeader("X-User-Id") ?: "PORTAL_USER"
        val result = bulkUploadService.processCsvUpload(file.inputStream, performedBy)
        return ResponseEntity.ok(
            ApiResponse(
                success = result.rejectedRows == 0,
                message = "Processed ${result.totalRows} rows: ${result.successfulRows} loaded, ${result.rejectedRows} rejected",
                data = result
            )
        )
    }

    /**
     * AC4: Upload inventory file using raw CSV text content in body.
     */
    @Operation(
        summary = "Upload inventory raw CSV text (AC4)",
        description = "Uploads inventory data as raw CSV text in the HTTP body. Valid rows are loaded and rejected rows reported."
    )
    @ApiResponses(
        value = [
            SwaggerResponse(responseCode = "200", description = "CSV text processed with row-by-row validation report")
        ]
    )
    @PostMapping("/upload-csv", consumes = [MediaType.TEXT_PLAIN_VALUE, "text/csv", MediaType.APPLICATION_JSON_VALUE])
    fun uploadCsvText(
        @Parameter(description = "Raw CSV text content") @RequestBody csvContent: String,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<BulkUploadResult>> {
        val performedBy = httpRequest.getHeader("X-User-Id") ?: "PORTAL_USER"
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(StandardCharsets.UTF_8))
        val result = bulkUploadService.processCsvUpload(inputStream, performedBy)
        return ResponseEntity.ok(
            ApiResponse(
                success = result.rejectedRows == 0,
                message = "Processed ${result.totalRows} rows: ${result.successfulRows} loaded, ${result.rejectedRows} rejected",
                data = result
            )
        )
    }
}
