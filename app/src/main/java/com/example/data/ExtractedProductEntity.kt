package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "extracted_products")
data class ExtractedProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: String,
    val sourceFileName: String,
    val produit: String,
    val brand: String,
    val category: String,
    val prix: String,
    val prixNormal: String = "",
    val datePromo: String = "",
    val unite: String,
    val sku: String = "",
    val barcode: String = "",
    val customFieldsJson: String = "{}",
    val timestampMs: Long = System.currentTimeMillis()
)
