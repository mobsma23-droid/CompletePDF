package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM processed_files ORDER BY timestampMs DESC")
    fun getAllFiles(): Flow<List<ProcessedFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(file: ProcessedFileEntity)

    @Query("DELETE FROM processed_files WHERE id = :fileId")
    suspend fun deleteById(fileId: String)

    @Query("DELETE FROM processed_files")
    suspend fun deleteAll()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM extracted_products ORDER BY id DESC")
    fun getAllProducts(): Flow<List<ExtractedProductEntity>>

    @Query("SELECT * FROM extracted_products WHERE fileId = :fileId")
    suspend fun getProductsForFile(fileId: String): List<ExtractedProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ExtractedProductEntity>)

    @Query("DELETE FROM extracted_products WHERE fileId = :fileId")
    suspend fun deleteForFile(fileId: String)

    @Query("DELETE FROM extracted_products")
    suspend fun deleteAll()
}
