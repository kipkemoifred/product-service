package ke.co.tracom.product_service.service

import ke.co.tracom.product_service.domain.enums.TaxType
import ke.co.tracom.product_service.domain.enums.UnitOfMeasure
import ke.co.tracom.product_service.dto.AddStockRequest
import ke.co.tracom.product_service.dto.BulkUploadError
import ke.co.tracom.product_service.dto.BulkUploadResult
import ke.co.tracom.product_service.dto.CreateProductRequest
import ke.co.tracom.product_service.repository.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigDecimal
import java.nio.charset.StandardCharsets

@Service
class BulkUploadService(
    private val productService: ProductService,
    private val inventoryService: InventoryService,
    private val productRepository: ProductRepository
) {
    private val logger = LoggerFactory.getLogger(BulkUploadService::class.java)

    companion object {
        const val CSV_HEADER = "Product Name,Product Code,Category,Unit Of Measure,Unit Price,Tax Type,Discount (Y/N),Discount Rate,Description,Initial Stock"
    }

    fun getCsvTemplate(): String {
        return buildString {
            appendLine(CSV_HEADER)
            appendLine("Fresh Milk 500ml,MILK-500,Dairy,PACKETS,65.00,ZERO_RATED,N,0,Fresh whole milk 500ml,100")
            appendLine("White Bread 400g,BREAD-400,Bakery,PACKETS,60.00,ZERO_RATED,N,0,Sliced sandwich bread,150")
            appendLine("Cooking Oil 1L,OIL-1000,Groceries,LITRES,240.00,STANDARD,Y,5.0,Pure vegetable cooking oil,80")
            appendLine("Mineral Water 500ml,WATER-500,Beverages,BOTTLES,35.00,STANDARD,N,0,Natural mineral water,300")
        }
    }

    fun processCsvUpload(inputStream: InputStream, performedBy: String = "PORTAL_USER"): BulkUploadResult {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val lines = reader.readLines().filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return BulkUploadResult(0, 0, 0, emptyList(), listOf(BulkUploadError(0, "", "Empty CSV file provided")))
        }

        var headerRowIndex = -1
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.contains("Product Name", ignoreCase = true) && line.contains("Product Code", ignoreCase = true)) {
                headerRowIndex = i
                break
            }
        }

        val dataLines = if (headerRowIndex >= 0) lines.drop(headerRowIndex + 1) else lines
        val seenCodesInFile = mutableSetOf<String>()

        val successfulItems = mutableListOf<Map<String, Any?>>()
        val errors = mutableListOf<BulkUploadError>()

        for ((index, rawLine) in dataLines.withIndex()) {
            val rowNumber = if (headerRowIndex >= 0) headerRowIndex + 2 + index else index + 1
            val trimmedLine = rawLine.trim()
            if (trimmedLine.isBlank()) continue

            val tokens = parseCsvLine(trimmedLine)

            if (tokens.size < 5) {
                errors.add(
                    BulkUploadError(
                        rowNumber = rowNumber,
                        data = trimmedLine,
                        reason = "EX-06: Missing Mandatory Information: Insufficient columns. Minimum required: Name, Product Code, Category, Unit Of Measure, Unit Price"
                    )
                )
                continue
            }

            val name = tokens.getOrNull(0)?.trim() ?: ""
            val productCode = tokens.getOrNull(1)?.trim() ?: ""
            val category = tokens.getOrNull(2)?.trim() ?: ""
            val uomStr = tokens.getOrNull(3)?.trim() ?: ""
            val priceStr = tokens.getOrNull(4)?.trim() ?: ""
            val taxStr = tokens.getOrNull(5)?.trim()
            val discountStr = tokens.getOrNull(6)?.trim()
            val discountRateStr = tokens.getOrNull(7)?.trim()
            val description = tokens.getOrNull(8)?.trim()
            val initialStockStr = tokens.getOrNull(9)?.trim()

            // Validate mandatory fields
            val missing = mutableListOf<String>()
            if (name.isBlank()) missing.add("Product Name")
            if (productCode.isBlank()) missing.add("Product Code")
            if (category.isBlank()) missing.add("Category")
            if (uomStr.isBlank()) missing.add("Unit Of Measure")
            if (priceStr.isBlank()) missing.add("Unit Price")

            if (missing.isNotEmpty()) {
                errors.add(
                    BulkUploadError(
                        rowNumber = rowNumber,
                        data = trimmedLine,
                        reason = "EX-06: Missing Mandatory Information: Missing fields: ${missing.joinToString(", ")}"
                    )
                )
                continue
            }

            // Check duplicate in file
            if (seenCodesInFile.contains(productCode.uppercase())) {
                errors.add(
                    BulkUploadError(
                        rowNumber = rowNumber,
                        data = trimmedLine,
                        reason = "EX-05: Duplicate Product: Duplicate product code '$productCode' in uploaded file"
                    )
                )
                continue
            }
            seenCodesInFile.add(productCode.uppercase())

            // Validate Unit Price
            val unitPrice = try {
                val p = BigDecimal(priceStr)
                if (p <= BigDecimal.ZERO) {
                    errors.add(
                        BulkUploadError(
                            rowNumber = rowNumber,
                            data = trimmedLine,
                            reason = "EX-03: Invalid Stock Quantity: Unit price must be greater than zero. Received: $priceStr"
                        )
                    )
                    continue
                }
                p
            } catch (e: Exception) {
                errors.add(
                    BulkUploadError(
                        rowNumber = rowNumber,
                        data = trimmedLine,
                        reason = "EX-03: Invalid Stock Quantity: Invalid unit price format '$priceStr'"
                    )
                )
                continue
            }

            // Validate Unit of Measure
            val uom = try {
                UnitOfMeasure.fromString(uomStr)
            } catch (e: Exception) {
                errors.add(
                    BulkUploadError(
                        rowNumber = rowNumber,
                        data = trimmedLine,
                        reason = "EX-06: Missing Mandatory Information: Invalid unit of measure '$uomStr'"
                    )
                )
                continue
            }

            // Validate Initial Stock
            val initialStock = if (!initialStockStr.isNullOrBlank()) {
                try {
                    val s = BigDecimal(initialStockStr)
                    if (s < BigDecimal.ZERO) {
                        errors.add(
                            BulkUploadError(
                                rowNumber = rowNumber,
                                data = trimmedLine,
                                reason = "EX-03: Invalid Stock Quantity: Initial stock cannot be negative. Received: $initialStockStr"
                            )
                        )
                        continue
                    }
                    s
                } catch (e: Exception) {
                    errors.add(
                        BulkUploadError(
                            rowNumber = rowNumber,
                            data = trimmedLine,
                            reason = "EX-03: Invalid Stock Quantity: Invalid stock format '$initialStockStr'"
                        )
                    )
                    continue
                }
            } else {
                BigDecimal.ZERO
            }

            val hasDiscount = discountStr?.equals("Y", ignoreCase = true) == true ||
                    discountStr?.equals("YES", ignoreCase = true) == true ||
                    discountStr?.equals("TRUE", ignoreCase = true) == true

            val discountRate = if (hasDiscount && !discountRateStr.isNullOrBlank()) {
                try {
                    BigDecimal(discountRateStr)
                } catch (e: Exception) {
                    BigDecimal.ZERO
                }
            } else BigDecimal.ZERO

            // Check if product already exists in DB
            val existingProduct = productRepository.findByProductCode(productCode)
            if (existingProduct.isPresent) {
                // If it already exists, either add stock if initialStock > 0 or report duplicate code
                if (initialStock > BigDecimal.ZERO) {
                    try {
                        val addRes = inventoryService.addStock(
                            AddStockRequest(
                                productCode = productCode,
                                quantity = initialStock,
                                reason = "Bulk Upload Stock Addition (Row $rowNumber)",
                                performedBy = performedBy
                            )
                        )
                        successfulItems.add(
                            mapOf(
                                "rowNumber" to rowNumber,
                                "productCode" to productCode,
                                "status" to "STOCK_ADDED",
                                "message" to "Existing product stock increased by $initialStock. New balance: ${addRes.newQuantity}"
                            )
                        )
                    } catch (e: Exception) {
                        errors.add(BulkUploadError(rowNumber, trimmedLine, e.message ?: "Failed to add stock"))
                    }
                } else {
                    errors.add(
                        BulkUploadError(
                            rowNumber = rowNumber,
                            data = trimmedLine,
                            reason = "EX-05: Duplicate Product: Product code '$productCode' already exists in catalogue"
                        )
                    )
                }
                continue
            }

            // Create new product
            try {
                val createReq = CreateProductRequest(
                    name = name,
                    productCode = productCode,
                    category = category,
                    unitOfMeasure = uom.name,
                    unitPrice = unitPrice,
                    taxType = taxStr?.ifBlank { "STANDARD" } ?: "STANDARD",
                    hasDiscount = hasDiscount,
                    discountRate = discountRate,
                    description = description,
                    initialStock = initialStock
                )
                val created = productService.createProduct(createReq, performedBy)
                successfulItems.add(
                    mapOf(
                        "rowNumber" to rowNumber,
                        "productCode" to created.productCode,
                        "productName" to created.name,
                        "status" to "CREATED",
                        "initialStock" to initialStock
                    )
                )
            } catch (e: Exception) {
                logger.error("Failed to process row $rowNumber: ${e.message}", e)
                errors.add(BulkUploadError(rowNumber, trimmedLine, e.message ?: "Failed to create product"))
            }
        }

        return BulkUploadResult(
            totalRows = dataLines.size,
            successfulRows = successfulItems.size,
            rejectedRows = errors.size,
            successfulItems = successfulItems,
            errors = errors
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString().trim())
        return result
    }
}
