package ke.co.tracom.product_service.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import ke.co.tracom.product_service.exception.UnauthorizedInventoryAccessException
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class InventorySecurityInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val uri = request.requestURI
        if (!uri.startsWith("/api/inventory") && !uri.startsWith("/api/products")) {
            return true
        }

        // Allow public template download or swagger/health checks
        if (uri.endsWith("/template/csv")) {
            return true
        }

        val userRole = request.getHeader("X-User-Role")
        val permissions = request.getHeader("X-User-Permissions")
        val isDenied = request.getHeader("X-Denied-Access")?.toBoolean() ?: false

        if (isDenied || userRole.equals("UNAUTHORIZED", ignoreCase = true) || userRole.equals("GUEST", ignoreCase = true)) {
            throw UnauthorizedInventoryAccessException(
                "EX-01: Unauthorized Inventory Access: User does not have permission to access the Inventory module or perform the selected operation",
                mapOf("userRole" to (userRole ?: "GUEST"), "path" to uri)
            )
        }

        if (permissions != null && !permissions.contains("INVENTORY", ignoreCase = true)) {
            throw UnauthorizedInventoryAccessException(
                "EX-01: Unauthorized Inventory Access: User does not have permission to access the Inventory module or perform the selected operation",
                mapOf("permissions" to permissions, "path" to uri)
            )
        }

        return true
    }
}
