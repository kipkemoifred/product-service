package ke.co.tracom.product_service

import tools.jackson.databind.ObjectMapper
import ke.co.tracom.product_service.domain.enums.TaxType
import ke.co.tracom.product_service.domain.enums.UnitOfMeasure
import ke.co.tracom.product_service.dto.*
import ke.co.tracom.product_service.repository.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class InventoryIntegrationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var inventoryStockRepository: InventoryStockRepository

    @Autowired
    private lateinit var vehicleStockRepository: VehicleStockRepository

    @Autowired
    private lateinit var stockMovementRepository: StockMovementRepository

    @Autowired
    private lateinit var backOfficeSyncLogRepository: BackOfficeSyncLogRepository

    @Autowired
    private lateinit var flyway: Flyway

    @BeforeEach
    fun cleanDatabase() {
        stockMovementRepository.deleteAll()
        vehicleStockRepository.deleteAll()
        inventoryStockRepository.deleteAll()
        backOfficeSyncLogRepository.deleteAll()
        productRepository.deleteAll()
    }

    // =========================================================================
    // Acceptance Criteria 1
    // Given stock loaded on the vehicle, when the user opens Inventory -> Stock,
    // then each product shows available quantity, ordered quantity and quantity available for sale.
    // =========================================================================
    @Test
    fun `AC1 - Stock loaded on vehicle shows available, ordered, and quantity available for sale`() {
        // 1. Create a product with 100 units initial stock
        val createProductReq = CreateProductRequest(
            name = "Fresh Milk 500ml",
            productCode = "MILK-500",
            category = "Dairy",
            unitOfMeasure = "PACKETS",
            unitPrice = BigDecimal("65.00"),
            taxType = "ZERO_RATED",
            initialStock = BigDecimal("100.00")
        )
        mockMvc.perform(
            post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProductReq))
        ).andExpect(status().isCreated)

        // 2. Issue 40 units of stock to vehicle "KBZ-101A"
        val issueReq = IssueToVehicleRequest(
            vehicleId = "KBZ-101A",
            productCode = "MILK-500",
            quantity = BigDecimal("40.00"),
            deductFromWarehouse = true
        )
        mockMvc.perform(
            post("/api/inventory/vehicles/issue-stock")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(issueReq))
        ).andExpect(status().isOk)

        // Simulate 10 units ordered on the vehicle
        val vs = vehicleStockRepository.findByVehicleIdAndProductProductCode("KBZ-101A", "MILK-500").get()
        vs.orderedQuantity = BigDecimal("10.00")
        vehicleStockRepository.save(vs)

        // 3. User opens Inventory -> Stock with vehicleId filter
        mockMvc.perform(
            get("/api/inventory/stock")
                .param("vehicleId", "KBZ-101A")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].productCode").value("MILK-500"))
            .andExpect(jsonPath("$.data[0].vehicleId").value("KBZ-101A"))
            .andExpect(jsonPath("$.data[0].availableQuantity").value(40.00))
            .andExpect(jsonPath("$.data[0].orderedQuantity").value(10.00))
            .andExpect(jsonPath("$.data[0].quantityAvailableForSale").value(30.00))

        // Also verify warehouse inventory reflects the deduction
        mockMvc.perform(get("/api/inventory/stock"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].productCode").value("MILK-500"))
            .andExpect(jsonPath("$.data[0].availableQuantity").value(60.00))
            .andExpect(jsonPath("$.data[0].orderedQuantity").value(0.00))
            .andExpect(jsonPath("$.data[0].quantityAvailableForSale").value(60.00))
    }

    // =========================================================================
    // Acceptance Criteria 2
    // Given a product, when the user adjusts the quantity up or down and confirms,
    // then inventory is updated by exactly that amount and the change is synced to the back office.
    // =========================================================================
    @Test
    fun `AC2 - Adjust quantity up and down updates inventory exactly and syncs to back office`() {
        // Setup product with 50 units
        val createProductReq = CreateProductRequest(
            name = "White Bread 400g",
            productCode = "BREAD-400",
            category = "Bakery",
            unitOfMeasure = "PACKETS",
            unitPrice = BigDecimal("60.00"),
            initialStock = BigDecimal("50.00")
        )
        mockMvc.perform(
            post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProductReq))
        ).andExpect(status().isCreated)

        // 1. Adjust up by +20
        val adjustUpReq = AdjustStockRequest(
            productCode = "BREAD-400",
            adjustmentQuantity = BigDecimal("20.00"),
            reason = "Stock recount surplus from morning delivery audit"
        )
        mockMvc.perform(
            post("/api/inventory/stock/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adjustUpReq))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.previousQuantity").value(50.00))
            .andExpect(jsonPath("$.data.newQuantity").value(70.00))
            .andExpect(jsonPath("$.data.syncedToBackOffice").value(true))

        // 2. Adjust down by -15
        val adjustDownReq = AdjustStockRequest(
            productCode = "BREAD-400",
            adjustmentQuantity = BigDecimal("-15.00"),
            reason = "Damaged items write-off during shelf audit"
        )
        mockMvc.perform(
            post("/api/inventory/stock/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adjustDownReq))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.previousQuantity").value(70.00))
            .andExpect(jsonPath("$.data.newQuantity").value(55.00))
            .andExpect(jsonPath("$.data.syncedToBackOffice").value(true))

        // Verify back office sync logs contain both adjustments
        val syncLogs = backOfficeSyncLogRepository.findAll()
        val adjustLogs = syncLogs.filter { it.syncType == "STOCK_ADJUSTMENT_SYNC" }
        assertEquals(2, adjustLogs.size)
        assertTrue(adjustLogs.all { it.status.name == "SUCCESS" })
    }

    // =========================================================================
    // Acceptance Criteria 3
    // Given Add Product, when the user enters name, barcode/code, unit price,
    // tax type, discount flag/rate and description and saves,
    // then the product is available for sale and synced to the supplier's catalogue.
    // =========================================================================
    @Test
    fun `AC3 - Add product saves product, makes it available for sale and syncs to supplier catalogue`() {
        val createProductReq = CreateProductRequest(
            name = "Mineral Water 1L",
            productCode = "WATER-1000",
            description = "Natural mineral drinking water 1000ml bottle",
            category = "Beverages",
            unitOfMeasure = "BOTTLES",
            unitPrice = BigDecimal("50.00"),
            taxType = "STANDARD",
            hasDiscount = true,
            discountRate = BigDecimal("10.00"),
            initialStock = BigDecimal("200.00")
        )

        mockMvc.perform(
            post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProductReq))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Mineral Water 1L"))
            .andExpect(jsonPath("$.data.productCode").value("WATER-1000"))
            .andExpect(jsonPath("$.data.unitPrice").value(50.00))
            .andExpect(jsonPath("$.data.taxType").value("STANDARD"))
            .andExpect(jsonPath("$.data.hasDiscount").value(true))
            .andExpect(jsonPath("$.data.discountRate").value(10.00))
            .andExpect(jsonPath("$.data.active").value(true))
            .andExpect(jsonPath("$.data.syncedToSupplierCatalogue").value(true))

        // Verify database state
        val product = productRepository.findByProductCode("WATER-1000").get()
        assertTrue(product.active, "Product must be active and available for sale")
        assertTrue(product.syncedToSupplierCatalogue, "Product must be marked as synced to supplier catalogue")

        // Verify supplier catalogue sync log exists
        val supplierLogs = backOfficeSyncLogRepository.findAll().filter { it.syncType == "SUPPLIER_CATALOGUE_SYNC" }
        assertEquals(1, supplierLogs.size)
        assertEquals("WATER-1000", supplierLogs[0].entityId)
    }

    // =========================================================================
    // Acceptance Criteria 4
    // Given an inventory file in the published CSV/Excel template,
    // when it is uploaded, then valid rows are loaded and rejected rows are reported with reasons.
    // =========================================================================
    @Test
    fun `AC4 - Bulk upload CSV loads valid rows and reports rejected rows with reasons`() {
        // First verify published template download endpoint
        mockMvc.perform(get("/api/inventory/template/csv"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("text/csv; charset=UTF-8"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Product Name,Product Code")))

        // Prepare mixed CSV with 2 valid products, 1 existing product stock increment, and 3 invalid rows:
        // - Row 1 (Header)
        // - Row 2: Valid new product (FLOUR-2KG)
        // - Row 3: Missing name (EX-06)
        // - Row 4: Negative unit price (EX-03)
        // - Row 5: Valid new product (SUGAR-1KG)
        // - Row 6: Duplicate code within file (FLOUR-2KG) (EX-05)
        val csvContent = """
            Product Name,Product Code,Category,Unit Of Measure,Unit Price,Tax Type,Discount (Y/N),Discount Rate,Description,Initial Stock
            Maize Flour 2kg,FLOUR-2KG,Grains,PACKETS,150.00,ZERO_RATED,N,0,Premium sifted maize flour,50
            ,INVALID-CODE,Dairy,PACKETS,80.00,STANDARD,N,0,Missing product name,10
            Juice 500ml,JUICE-500,Beverages,BOTTLES,-45.00,STANDARD,N,0,Negative price,20
            White Sugar 1kg,SUGAR-1KG,Sweeteners,PACKETS,180.00,ZERO_RATED,N,0,Refined white sugar,100
            Maize Flour Duplicate,FLOUR-2KG,Grains,PACKETS,155.00,ZERO_RATED,N,0,Duplicate product code,30
        """.trimIndent()

        mockMvc.perform(
            post("/api/inventory/upload-csv")
                .contentType(MediaType.TEXT_PLAIN)
                .content(csvContent)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalRows").value(5))
            .andExpect(jsonPath("$.data.successfulRows").value(2))
            .andExpect(jsonPath("$.data.rejectedRows").value(3))
            // Check that errors report row numbers and specific reasons
            .andExpect(jsonPath("$.data.errors[0].rowNumber").value(3))
            .andExpect(jsonPath("$.data.errors[0].reason").value(org.hamcrest.Matchers.containsString("EX-06")))
            .andExpect(jsonPath("$.data.errors[1].rowNumber").value(4))
            .andExpect(jsonPath("$.data.errors[1].reason").value(org.hamcrest.Matchers.containsString("EX-03")))
            .andExpect(jsonPath("$.data.errors[2].rowNumber").value(6))
            .andExpect(jsonPath("$.data.errors[2].reason").value(org.hamcrest.Matchers.containsString("EX-05")))

        // Verify valid products were actually loaded into database
        assertTrue(productRepository.findByProductCode("FLOUR-2KG").isPresent)
        assertTrue(productRepository.findByProductCode("SUGAR-1KG").isPresent)
    }

    // =========================================================================
    // Acceptance Criteria 5
    // Given a supplier with API integration, when stock is issued to a vehicle,
    // then the mobile app reflects the loaded quantities without manual entry.
    // =========================================================================
    @Test
    fun `AC5 - Supplier API stock issue to vehicle is immediately reflected in mobile app`() {
        // 1. Create product in catalogue
        val createProductReq = CreateProductRequest(
            name = "Soda 500ml",
            productCode = "SODA-500",
            category = "Beverages",
            unitOfMeasure = "BOTTLES",
            unitPrice = BigDecimal("50.00"),
            initialStock = BigDecimal("500.00")
        )
        mockMvc.perform(
            post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProductReq))
        ).andExpect(status().isCreated)

        // 2. Supplier system calls API integration endpoint to issue 120 units to vehicle "TRUCK-44"
        val supplierIssueReq = IssueToVehicleRequest(
            vehicleId = "TRUCK-44",
            productCode = "SODA-500",
            quantity = BigDecimal("120.00"),
            deductFromWarehouse = true,
            referenceNumber = "SUPPLIER-MANIFEST-987"
        )
        mockMvc.perform(
            post("/api/inventory/supplier/stock-issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(supplierIssueReq))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.operation").value("ISSUE_TO_VEHICLE"))
            .andExpect(jsonPath("$.data.vehicleId").value("TRUCK-44"))
            .andExpect(jsonPath("$.data.newQuantity").value(120.00))

        // 3. Mobile app queries vehicle stock without manual entry
        mockMvc.perform(get("/api/inventory/vehicles/TRUCK-44/stock"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].vehicleId").value("TRUCK-44"))
            .andExpect(jsonPath("$.data[0].productCode").value("SODA-500"))
            .andExpect(jsonPath("$.data[0].availableQuantity").value(120.00))
            .andExpect(jsonPath("$.data[0].orderedQuantity").value(0.00))
            .andExpect(jsonPath("$.data[0].quantityAvailableForSale").value(120.00))
    }

    // =========================================================================
    // AP2: Add Stock
    // =========================================================================
    @Test
    fun `AP2 - Add stock increases available stock and records audit movement`() {
        val createProductReq = CreateProductRequest(
            name = "Soap Bar 200g",
            productCode = "SOAP-200",
            category = "Hygiene",
            unitOfMeasure = "PIECES",
            unitPrice = BigDecimal("70.00"),
            initialStock = BigDecimal("30.00")
        )
        mockMvc.perform(
            post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProductReq))
        ).andExpect(status().isCreated)

        // Add 50 more units
        val addReq = AddStockRequest(
            productCode = "SOAP-200",
            quantity = BigDecimal("50.00"),
            referenceNumber = "PO-10023",
            reason = "Supplier delivery replenishment"
        )
        mockMvc.perform(
            post("/api/inventory/stock/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addReq))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.previousQuantity").value(30.00))
            .andExpect(jsonPath("$.data.newQuantity").value(80.00))
            .andExpect(jsonPath("$.data.operation").value("ADD_STOCK"))

        // Check audit trail
        mockMvc.perform(get("/api/inventory/audit-trail").param("productCode", "SOAP-200"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].movementType").value("ADDITION"))
            .andExpect(jsonPath("$.data[0].quantityChanged").value(50.00))
    }

    // =========================================================================
    // AP5: Remove Stock
    // =========================================================================
    @Test
    fun `AP5 - Remove stock deducts quantity and records removal reason`() {
        val createProductReq = CreateProductRequest(
            name = "Eggs 30-Tray",
            productCode = "EGGS-30",
            category = "Poultry",
            unitOfMeasure = "BOXES",
            unitPrice = BigDecimal("450.00"),
            initialStock = BigDecimal("20.00")
        )
        mockMvc.perform(
            post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProductReq))
        ).andExpect(status().isCreated)

        // Remove 5 units due to damage
        val removeReq = RemoveStockRequest(
            productCode = "EGGS-30",
            quantity = BigDecimal("5.00"),
            reason = "Damaged during warehouse handling",
            referenceNumber = "DISPOSAL-01"
        )
        mockMvc.perform(
            post("/api/inventory/stock/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(removeReq))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.previousQuantity").value(20.00))
            .andExpect(jsonPath("$.data.newQuantity").value(15.00))
            .andExpect(jsonPath("$.data.operation").value("REMOVE_STOCK"))
    }

    // =========================================================================
    // EX-01: Unauthorized Inventory Access
    // =========================================================================
    @Test
    fun `EX-01 - Unauthorized access is rejected with 403 and error message`() {
        mockMvc.perform(
            get("/api/inventory/stock")
                .header("X-User-Role", "UNAUTHORIZED")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value("EX-01"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("EX-01: Unauthorized Inventory Access")))
    }

    // =========================================================================
    // EX-02: Product Not Found
    // =========================================================================
    @Test
    fun `EX-02 - Stock operation on nonexistent product is rejected with 404`() {
        val addReq = AddStockRequest(
            productCode = "NON-EXISTENT-CODE",
            quantity = BigDecimal("10.00")
        )
        mockMvc.perform(
            post("/api/inventory/stock/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addReq))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value("EX-02"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("EX-02: Product Not Found")))
    }

    // =========================================================================
    // EX-03: Invalid Stock Quantity
    // =========================================================================
    @Test
    fun `EX-03 - Zero or negative quantity is rejected with 400`() {
        val createProductReq = CreateProductRequest(
            name = "Biscuits 100g",
            productCode = "BISC-100",
            category = "Snacks",
            unitOfMeasure = "PACKETS",
            unitPrice = BigDecimal("30.00"),
            initialStock = BigDecimal("10.00")
        )
        mockMvc.perform(
            post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProductReq))
        ).andExpect(status().isCreated)

        // Attempt add with zero quantity
        val addZeroReq = AddStockRequest(
            productCode = "BISC-100",
            quantity = BigDecimal.ZERO
        )
        mockMvc.perform(
            post("/api/inventory/stock/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addZeroReq))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("EX-03"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("EX-03: Invalid Stock Quantity")))

        // Attempt add with negative quantity
        val addNegReq = AddStockRequest(
            productCode = "BISC-100",
            quantity = BigDecimal("-5.00")
        )
        mockMvc.perform(
            post("/api/inventory/stock/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(addNegReq))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("EX-03"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("EX-03: Invalid Stock Quantity")))
    }

    // =========================================================================
    // EX-04: Insufficient Stock
    // =========================================================================
    @Test
    fun `EX-04 - Removing more than available stock is rejected and prevents negative inventory`() {
        val createProductReq = CreateProductRequest(
            name = "Cooking Oil 500ml",
            productCode = "OIL-500",
            category = "Groceries",
            unitOfMeasure = "BOTTLES",
            unitPrice = BigDecimal("120.00"),
            initialStock = BigDecimal("10.00")
        )
        mockMvc.perform(
            post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProductReq))
        ).andExpect(status().isCreated)

        // Attempt to remove 25 units when only 10 available
        val removeReq = RemoveStockRequest(
            productCode = "OIL-500",
            quantity = BigDecimal("25.00"),
            reason = "Bulk order removal"
        )
        mockMvc.perform(
            post("/api/inventory/stock/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(removeReq))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("EX-04"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("EX-04: Insufficient Stock")))

        // Verify balance was NOT modified
        val stock = inventoryStockRepository.findByProductProductCode("OIL-500").get()
        assertEquals(BigDecimal("10.00"), stock.availableQuantity)
    }

    // =========================================================================
    // EX-05: Duplicate Product
    // =========================================================================
    @Test
    fun `EX-05 - Attempting to create duplicate product code is rejected with 409`() {
        val req1 = CreateProductRequest(
            name = "Product A",
            productCode = "DUP-001",
            category = "General",
            unitOfMeasure = "PIECES",
            unitPrice = BigDecimal("10.00")
        )
        mockMvc.perform(
            post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1))
        ).andExpect(status().isCreated)

        val req2 = CreateProductRequest(
            name = "Product B With Same Code",
            productCode = "DUP-001",
            category = "General",
            unitOfMeasure = "PIECES",
            unitPrice = BigDecimal("15.00")
        )
        mockMvc.perform(
            post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value("EX-05"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("EX-05: Duplicate Product")))
    }

    // =========================================================================
    // EX-06: Missing Mandatory Information
    // =========================================================================
    @Test
    fun `EX-06 - Missing mandatory information prevents submission and reports missing fields`() {
        // Attempt create product with blank code and blank name
        val invalidCreate = CreateProductRequest(
            name = "",
            productCode = "",
            category = "Category",
            unitOfMeasure = "PACKETS",
            unitPrice = BigDecimal("10.00")
        )
        mockMvc.perform(
            post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidCreate))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("EX-06"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("EX-06: Missing Mandatory Information")))

        // Attempt remove stock without reason
        val removeWithoutReason = RemoveStockRequest(
            productCode = "ANY-CODE",
            quantity = BigDecimal("5.00"),
            reason = "" // blank reason
        )
        mockMvc.perform(
            post("/api/inventory/stock/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(removeWithoutReason))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("EX-06"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("EX-06: Missing Mandatory Information")))
    }

    // =========================================================================
    // Swagger / OpenAPI Verification
    // =========================================================================
    @Test
    fun `Swagger - OpenAPI v3 docs and Swagger UI are accessible and document inventory APIs`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.info.title").value("Product & Inventory Management Service API"))
            .andExpect(jsonPath("$.paths['/api/inventory/stock']").exists())
            .andExpect(jsonPath("$.paths['/api/inventory/stock/add']").exists())
            .andExpect(jsonPath("$.paths['/api/inventory/stock/adjust']").exists())
            .andExpect(jsonPath("$.paths['/api/inventory/stock/remove']").exists())
            .andExpect(jsonPath("$.paths['/api/inventory/products']").exists())
            .andExpect(jsonPath("$.paths['/api/inventory/vehicles/{vehicleId}/stock']").exists())
            .andExpect(jsonPath("$.paths['/api/inventory/supplier/stock-issue']").exists())
            .andExpect(jsonPath("$.paths['/api/inventory/template/csv']").exists())
            .andExpect(jsonPath("$.paths['/api/inventory/audit-trail']").exists())

        // Verify Swagger UI endpoint is reachable without authorization block
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk)
    }

    // =========================================================================
    // Dynamic Query & Null Parameter Filtering Verification
    // (Verifies fix for lower(bytea) does not exist when parameters are null)
    // =========================================================================
    @Test
    fun `Query - GET api inventory products with active=true only runs without lower bytea error`() {
        val prod1 = CreateProductRequest(
            name = "Premium Tea 500g",
            productCode = "TEA-500",
            category = "Beverages",
            unitOfMeasure = "PACKETS",
            unitPrice = BigDecimal("150.00"),
            initialStock = BigDecimal("50.00")
        )
        val prod2 = CreateProductRequest(
            name = "Coffee Beans 1kg",
            productCode = "COF-1000",
            category = "Beverages",
            unitOfMeasure = "KGS",
            unitPrice = BigDecimal("800.00"),
            initialStock = BigDecimal("20.00")
        )
        mockMvc.perform(post("/api/inventory/products").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(prod1))).andExpect(status().isCreated)
        mockMvc.perform(post("/api/inventory/products").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(prod2))).andExpect(status().isCreated)

        // Exact endpoint from user report: curl -X 'GET' 'http://localhost:8080/api/inventory/products?active=true'
        mockMvc.perform(
            get("/api/inventory/products")
                .param("active", "true")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].productCode").value("COF-1000"))
            .andExpect(jsonPath("$.data[1].productCode").value("TEA-500"))

        // With search only
        mockMvc.perform(
            get("/api/inventory/products")
                .param("search", "tea")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].productCode").value("TEA-500"))

        // With category only
        mockMvc.perform(
            get("/api/inventory/products")
                .param("category", "Beverages")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))

        // Stock summary with null params
        mockMvc.perform(get("/api/inventory/stock"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))

        // Stock movements audit with null params
        mockMvc.perform(get("/api/inventory/audit-trail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
    }

    // =========================================================================
    // Flyway Database Migrations Verification
    // =========================================================================
    @Test
    fun `Flyway - Migrations are successfully applied and verified`() {
        val info = flyway.info()
        val applied = info.applied()
        assertTrue(applied.isNotEmpty(), "Flyway migrations must have been applied")
        assertEquals(2, applied.size, "Expected 2 applied Flyway migrations")
        assertEquals("4", applied[0].version.version)
        assertEquals("create inventory tables", applied[0].description)
        assertEquals("5", applied[1].version.version)
        assertEquals("seed sample inventory data", applied[1].description)
        assertEquals("5", info.current().version.version)
    }
}

