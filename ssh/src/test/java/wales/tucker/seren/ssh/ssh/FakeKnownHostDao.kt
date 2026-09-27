package wales.tucker.seren.ssh.ssh

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import wales.tucker.seren.ssh.data.KnownHost
import wales.tucker.seren.ssh.data.KnownHostDao

class FakeKnownHostDao : KnownHostDao {
    val entries = MutableStateFlow<List<KnownHost>>(emptyList())

    override fun observeAll(): Flow<List<KnownHost>> = entries
    override fun findByHost(host: String): List<KnownHost> = entries.value.filter { it.host == host }
    override fun insert(knownHost: KnownHost): Long {
        entries.value = entries.value.filterNot { it.host == knownHost.host && it.keyType == knownHost.keyType } + knownHost
        return entries.value.size.toLong()
    }

    override fun remove(host: String, keyType: String) {
        entries.value = entries.value.filterNot { it.host == host && it.keyType == keyType }
    }

    override suspend fun delete(knownHost: KnownHost) {
        entries.value = entries.value - knownHost
    }
}
