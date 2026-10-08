package ke.co.tracom.product_service.domain.model

import jakarta.persistence.*
import ke.co.tracom.product_service.domain.enums.SyncStatus
import java.time.LocalDateTime

@Entity
@Table(name = "back_office_sync_logs")
class BackOfficeSyncLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "sync_type", nullable = false, length = 100)
    var syncType: String,

    @Column(name = "entity_id", nullable = false, length = 100)
    var entityId: String,

    @Column(nullable = false, length = 2000)
    var payload: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: SyncStatus = SyncStatus.SUCCESS,

    @Column(length = 1000)
    var message: String? = null,

    @Column(name = "synced_at", nullable = false)
    var syncedAt: LocalDateTime = LocalDateTime.now()
) {
    @PrePersist
    fun onPrePersist() {
        syncedAt = LocalDateTime.now()
    }
}
