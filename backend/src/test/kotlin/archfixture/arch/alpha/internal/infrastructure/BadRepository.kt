package archfixture.arch.alpha.internal.infrastructure

import com.soruhavuzu.shared.AppendOnly
import org.springframework.data.repository.CrudRepository
import java.util.UUID

/** KASITLI İHLAL (A5): append-only repository CrudRepository'dir ve silme metodu sunar. */
@AppendOnly
interface BadRepository : CrudRepository<BadRow, UUID> {
    fun deleteOlderThan(days: Int)
}

data class BadRow(
    val id: UUID,
)
