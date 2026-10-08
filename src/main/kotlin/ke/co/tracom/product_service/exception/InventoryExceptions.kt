package ke.co.tracom.product_service.exception

open class InventoryException(
    val errorCode: String,
    override val message: String,
    val details: Map<String, Any?> = emptyMap()
) : RuntimeException(message)

/**
 * EX-01: Unauthorized Inventory Access: If the user does not have permission to access the Inventory module
 * or perform the selected operation, the system shall deny the request and display an appropriate authorization message.
 */
class UnauthorizedInventoryAccessException(
    message: String = "EX-01: Unauthorized Inventory Access: User does not have permission to access the Inventory module or perform the selected operation",
    details: Map<String, Any?> = emptyMap()
) : InventoryException("EX-01", message, details)

/**
 * EX-02: Product Not Found: If the selected product does not exist in the inventory catalogue when performing
 * a stock operation, the system shall notify the user and shall not modify the inventory balance.
 */
class ProductNotFoundException(
    message: String = "EX-02: Product Not Found: Product does not exist in the inventory catalogue",
    details: Map<String, Any?> = emptyMap()
) : InventoryException("EX-02", message, details)

/**
 * EX-03: Invalid Stock Quantity: If the user enters an invalid quantity, including zero, a negative value,
 * or an incorrectly formatted value, the system shall reject the request and display a validation message.
 */
class InvalidStockQuantityException(
    message: String = "EX-03: Invalid Stock Quantity: Quantity must be a valid positive number greater than zero",
    details: Map<String, Any?> = emptyMap()
) : InventoryException("EX-03", message, details)

/**
 * EX-04: Insufficient Stock: If the quantity being removed exceeds the available stock, the system shall
 * reject the stock removal request and shall not allow the inventory balance to become negative unless
 * negative inventory is explicitly configured.
 */
class InsufficientStockException(
    message: String = "EX-04: Insufficient Stock: Quantity exceeds available stock",
    details: Map<String, Any?> = emptyMap()
) : InventoryException("EX-04", message, details)

/**
 * EX-05: Duplicate Product: If the user attempts to add a product using a product code that already exists,
 * the system shall reject the product creation request and notify the user that the product already exists.
 */
class DuplicateProductException(
    message: String = "EX-05: Duplicate Product: Product code already exists in the catalogue",
    details: Map<String, Any?> = emptyMap()
) : InventoryException("EX-05", message, details)

/**
 * EX-06: Missing Mandatory Information: If required product, stock or adjustment information is not provided,
 * the system shall prevent submission and identify the missing information.
 */
class MissingMandatoryInformationException(
    message: String = "EX-06: Missing Mandatory Information: Required information was not provided",
    details: Map<String, Any?> = emptyMap()
) : InventoryException("EX-06", message, details)
