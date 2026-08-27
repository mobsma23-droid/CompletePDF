package com.example.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class SchemaFieldType(val displayName: String) {
    STRING("Text / String"),
    PRICE("Price (Numeric)"),
    NUMBER("Number / Integer"),
    DATE("Date / Period"),
    CATEGORY("Category / Tag"),
    BOOLEAN("Boolean (Yes/No)")
}

@Serializable
data class SchemaField(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // JSON key e.g. "Produit", "SKU", "Prix", "Barcode"
    val label: String, // Friendly UI display label e.g. "Product Name", "SKU Code"
    val type: SchemaFieldType = SchemaFieldType.STRING,
    val description: String = "", // Prompt instruction for Gemini extraction
    val required: Boolean = false,
    val defaultValue: String = ""
)

@Serializable
data class ExtractionSchema(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val isDefault: Boolean = false,
    val isPreset: Boolean = false,
    val fields: List<SchemaField> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis()
) {
    val fieldNames: List<String>
        get() = fields.map { it.name }

    val fieldLabels: List<String>
        get() = fields.map { it.label.ifBlank { it.name } }
}

object DefaultSchemas {
    const val ID_SUPERMARKET = "preset_supermarket"

    val SUPERMARKET_FLYER = ExtractionSchema(
        id = ID_SUPERMARKET,
        name = "Supermarket & Grocery Flyer",
        description = "Standard flyer schema extracting product name, brand, category, promotional price, regular price, promo date, and unit.",
        isDefault = true,
        isPreset = true,
        fields = listOf(
            SchemaField(
                id = "f_produit",
                name = "Produit",
                label = "Product Name",
                type = SchemaFieldType.STRING,
                description = "The full commercial product name.",
                required = true
            ),
            SchemaField(
                id = "f_brand",
                name = "Brand",
                label = "Brand / Manufacturer",
                type = SchemaFieldType.STRING,
                description = "Brand or manufacturer name. Empty string if not found."
            ),
            SchemaField(
                id = "f_category",
                name = "Category",
                label = "Category / Section",
                type = SchemaFieldType.CATEGORY,
                description = "Product category, department, or section header."
            ),
            SchemaField(
                id = "f_prix",
                name = "Prix",
                label = "Promo Price",
                type = SchemaFieldType.PRICE,
                description = "Current / promotional selling price numeric value ONLY (strip currency symbols and use dot decimal).",
                required = true
            ),
            SchemaField(
                id = "f_prix_normal",
                name = "Prix_Normal",
                label = "Regular Price",
                type = SchemaFieldType.PRICE,
                description = "Original / non-promotional price if crossed out or shown."
            ),
            SchemaField(
                id = "f_date_promo",
                name = "Date_Promo",
                label = "Promo Validity Date",
                type = SchemaFieldType.DATE,
                description = "Validity date range of the promo shown on header or badge."
            ),
            SchemaField(
                id = "f_unite",
                name = "unité",
                label = "Unit / Size",
                type = SchemaFieldType.STRING,
                description = "Unit of measurement or packaging size (e.g., 1 kg, 500 ml, piece, 1L, pack)."
            )
        )
    )

    fun getAllPresets(): List<ExtractionSchema> = listOf(
        SUPERMARKET_FLYER
    )

    fun getDefaultSchema(): ExtractionSchema = SUPERMARKET_FLYER

    fun findPresetById(id: String): ExtractionSchema? {
        return getAllPresets().find { it.id == id }
    }
}
