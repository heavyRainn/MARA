package com.care.voice.data.repository

import androidx.room.*
import com.care.voice.data.history.MemoryFactEntity
import com.care.voice.data.history.MemoryFactSourceEntity
import com.care.voice.data.history.MemoryTombstoneEntity

@Dao
interface MemoryFactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: MemoryFactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: MemoryFactSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstone(tombstone: MemoryTombstoneEntity)

    @Query("""
        SELECT * FROM memory_facts
        WHERE status = 'ACTIVE'
        AND subject_type = :subjectType
        AND type = :type
        AND fact_key = :key
        AND (:subjectRelation IS NULL OR subject_relation = :subjectRelation)
    """)
    suspend fun findActiveByKey(
        subjectType: String,
        subjectRelation: String?,
        type: String,
        key: String
    ): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE status = 'ACTIVE'")
    suspend fun findAllActive(): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MemoryFactEntity?

    @Query("SELECT * FROM memory_fact_sources WHERE memory_fact_id = :factId")
    suspend fun getSources(factId: String): List<MemoryFactSourceEntity>

    @Query("""
        SELECT COUNT(*) FROM memory_tombstones
        WHERE subject_type = :subjectType AND type = :type AND tombstone_key = :key
        AND (:valueHash IS NULL OR value_hash = :valueHash OR value_hash IS NULL)
    """)
    suspend fun countTombstone(
        subjectType: String,
        type: String,
        key: String,
        valueHash: String?
    ): Int

    @Transaction
    suspend fun applyMutationTransaction(
        insertFacts: List<MemoryFactEntity>,
        updateFacts: List<MemoryFactEntity>,
        sources: List<MemoryFactSourceEntity>,
        tombstones: List<MemoryTombstoneEntity>
    ) {
        updateFacts.forEach { insertFact(it) }
        insertFacts.forEach { insertFact(it) }
        sources.forEach { insertSource(it) }
        tombstones.forEach { insertTombstone(it) }
    }
}
