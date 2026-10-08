package ke.co.tracom.product_service.repository

import ke.co.tracom.product_service.domain.model.BackOfficeSyncLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BackOfficeSyncLogRepository : JpaRepository<BackOfficeSyncLog, Long> {
    fun findByEntityIdOrderBySyncedAtDesc(entityId: String): List<BackOfficeSyncLog>
    fun findAllByOrderBySyncedAtDesc(): List<BackOfficeSyncLog>
}
