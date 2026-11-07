package com.dhalam.ap.data

import io.objectbox.BoxStore

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

    fun find(stableKey: String): SecurityFact? {
        val query = facts.query(SecurityFact_.stableKey.equal(stableKey)).build()
        return try {
            query.findUnique()
        } finally {
            query.close()
        }
    }
}