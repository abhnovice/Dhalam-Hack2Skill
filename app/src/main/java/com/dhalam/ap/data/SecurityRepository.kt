package com.dhalam.ap.data

import io.objectbox.BoxStore
import com.dhalam.ap.SecurityObservation

class SecurityRepository(private val store: BoxStore) {
    private val facts = store.boxFor(SecurityFact::class.java)

    fun save(fact: SecurityFact) {
        require(fact.stableKey.isNotBlank())

        store.runInTx {
            val existing = find(fact.stableKey)
            if (existing == null || fact.observedAt >= existing.observedAt) {
                facts.put(fact.copy(id = existing?.id ?: 0L))
            }
        }
    }

    /** Atomically replace each successfully observed category, retaining IDs for unchanged keys. */
    fun saveObservation(observation: SecurityObservation) {
        val batches = observation.factBatches()
        val states = store.boxFor(SecurityScanState::class.java)
        store.runInTx {
            batches.forEach { batch ->
                val query = states.query(SecurityScanState_.kind.equal(batch.kind)).build()
                val previous = try { query.findUnique() } finally { query.close() }
                if (previous != null && previous.observedAt >= observation.observedAt) return@forEach
                val oldQuery = facts.query(SecurityFact_.kind.equal(batch.kind)).build()
                val existing = try { oldQuery.find() } finally { oldQuery.close() }
                val byKey = existing.associateBy { it.stableKey }
                val keys = batch.facts.map { it.stableKey }.toSet()
                facts.put(batch.facts.map { it.copy(id = byKey[it.stableKey]?.id ?: 0L) })
                facts.removeByIds(existing.filter { it.stableKey !in keys }.map { it.id })
                states.put(SecurityScanState(previous?.id ?: 0L, batch.kind, observation.observedAt))
            }
        }
    }

    fun all(): List<SecurityFact> = facts.all

    fun count(): Long = facts.count()

    fun find(stableKey: String): SecurityFact? {
        val query = facts.query(SecurityFact_.stableKey.equal(stableKey)).build()
        return try {
            query.findUnique()
        } finally {
            query.close()
        }
    }
}