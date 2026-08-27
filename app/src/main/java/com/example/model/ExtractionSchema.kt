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
    const val ID_RETAIL_SKU = "preset_retail_sku"
    const val ID_ELECTRONICS = "preset_electronics"
    const val ID_WHOLESALE_B2B = "preset_wholesale_b2b"
    const val ID_PHARMACY = "preset_pharmacy"

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

    val RETAIL_SKU = ExtractionSchema(
        id = ID_RETAIL_SKU,
        name = "Retail & Inventory with SKU",
        description = "Extracts product name, SKU / article code, brand, category, selling price, list price, barcode, stock status, and unit.",
        isDefault = false,
        isPreset = true,
        fields = listOf(
            SchemaField(
                id = "f_r_produit",
                name = "Produit",
                label = "Product Name",
                type = SchemaFieldType.STRING,
                description = "Commercial product name or description.",
                required = true
            ),
            SchemaField(
                id = "f_r_sku",
                name = "SKU",
                label = "SKU / Item Code",
                type = SchemaFieldType.STRING,
                description = "SKU, item code, article number, model code, or reference string (e.g. SKU-10492, REF-882)."
            ),
            SchemaField(
                id = "f_r_brand",
                name = "Brand",
                label = "Brand / Maker",
                type = SchemaFieldType.STRING,
                description = "Brand or trademark name."
            ),
            SchemaField(
                id = "f_r_category",
                name = "Category",
                label = "Department / Category",
                type = SchemaFieldType.CATEGORY,
                description = "Inventory category or aisle."
            ),
            SchemaField(
                id = "f_r_prix",
                name = "Prix",
                label = "Selling Price",
                type = SchemaFieldType.PRICE,
                description = "Current selling / retail price numeric value only.",
                required = true
            ),
            SchemaField(
                id = "f_r_prix_normal",
                name = "Prix_Normal",
                label = "List Price / MSRP",
                type = SchemaFieldType.PRICE,
                description = "Regular MSRP / catalogue list price numeric only."
            ),
            SchemaField(
                id = "f_r_barcode",
                name = "Barcode",
                label = "Barcode / EAN",
                type = SchemaFieldType.STRING,
                description = "Barcode numeric digits, EAN-13, or UPC string if present."
            ),
            SchemaField(
                id = "f_r_stock",
                name = "Stock",
                label = "Stock / Qty",
                type = SchemaFieldType.STRING,
                description = "Available quantity, pack size, or stock status."
            ),
            SchemaField(
                id = "f_r_unite",
                name = "unité",
                label = "Unit / Format",
                type = SchemaFieldType.STRING,
                description = "Unit of measure or packaging format."
            )
        )
    )

    val ELECTRONICS_SPECS = ExtractionSchema(
        id = ID_ELECTRONICS,
        name = "Electronics & Tech Specs",
        description = "Extracts device name, brand, model number/SKU, price, discount, key technical specifications, and warranty info.",
        isDefault = false,
        isPreset = true,
        fields = listOf(
            SchemaField(
                id = "f_e_produit",
                name = "Produit",
                label = "Device / Product Name",
                type = SchemaFieldType.STRING,
                description = "Full product name and model title.",
                required = true
            ),
            SchemaField(
                id = "f_e_brand",
                name = "Brand",
                label = "Brand",
                type = SchemaFieldType.STRING,
                description = "Tech brand / manufacturer (e.g. Samsung, Apple, Sony, LG)."
            ),
            SchemaField(
                id = "f_e_sku",
                name = "SKU",
                label = "Model No / SKU",
                type = SchemaFieldType.STRING,
                description = "Exact model number, part number, or SKU code."
            ),
            SchemaField(
                id = "f_e_prix",
                name = "Prix",
                label = "Selling Price",
                type = SchemaFieldType.PRICE,
                description = "Selling price numeric value only.",
                required = true
            ),
            SchemaField(
                id = "f_e_discount",
                name = "Discount",
                label = "Discount / Savings",
                type = SchemaFieldType.STRING,
                description = "Discount percentage or savings amount (e.g. -25%, Save Rs 1,000)."
            ),
            SchemaField(
                id = "f_e_specs",
                name = "Specs",
                label = "Key Specifications",
                type = SchemaFieldType.STRING,
                description = "Key specs such as RAM, Storage, CPU, Display size, Resolution, Connectivity."
            ),
            SchemaField(
                id = "f_e_warranty",
                name = "Warranty",
                label = "Warranty",
                type = SchemaFieldType.STRING,
                description = "Warranty period (e.g. 1 Year, 24 Months, Lifetime)."
            )
        )
    )

    val WHOLESALE_B2B = ExtractionSchema(
        id = ID_WHOLESALE_B2B,
        name = "Wholesale & B2B Price List",
        description = "Extracts item description, item code/SKU, category, wholesale price, MSRP, minimum order quantity (MOQ), and case packaging.",
        isDefault = false,
        isPreset = true,
        fields = listOf(
            SchemaField(
                id = "f_w_produit",
                name = "Produit",
                label = "Item Description",
                type = SchemaFieldType.STRING,
                description = "Item or product description.",
                required = true
            ),
            SchemaField(
                id = "f_w_sku",
                name = "SKU",
                label = "Item Code / SKU",
                type = SchemaFieldType.STRING,
                description = "Internal item code or supplier catalog number."
            ),
            SchemaField(
                id = "f_w_category",
                name = "Category",
                label = "Category",
                type = SchemaFieldType.CATEGORY,
                description = "Product category or line."
            ),
            SchemaField(
                id = "f_w_prix",
                name = "Prix",
                label = "Wholesale Price",
                type = SchemaFieldType.PRICE,
                description = "Wholesale / unit net price numeric value.",
                required = true
            ),
            SchemaField(
                id = "f_w_prix_normal",
                name = "Prix_Normal",
                label = "Retail MSRP",
                type = SchemaFieldType.PRICE,
                description = "Suggested retail / list price."
            ),
            SchemaField(
                id = "f_w_moq",
                name = "Min_Order_Qty",
                label = "Min Order Qty (MOQ)",
                type = SchemaFieldType.STRING,
                description = "Minimum order quantity or minimum order units."
            ),
            SchemaField(
                id = "f_w_case",
                name = "Case_Pack",
                label = "Case / Carton Pack",
                type = SchemaFieldType.STRING,
                description = "Number of units per master carton or bundle."
            )
        )
    )

    val PHARMACY_HEALTH = ExtractionSchema(
        id = ID_PHARMACY,
        name = "Pharmacy & Healthcare",
        description = "Extracts medication/health product name, active ingredient, brand, category, price, promo dates, and packaging format.",
        isDefault = false,
        isPreset = true,
        fields = listOf(
            SchemaField(
                id = "f_p_produit",
                name = "Produit",
                label = "Product / Medicine",
                type = SchemaFieldType.STRING,
                description = "Product trade name or medication title.",
                required = true
            ),
            SchemaField(
                id = "f_p_active",
                name = "Active_Ingredient",
                label = "Active Ingredient / Dosage",
                type = SchemaFieldType.STRING,
                description = "Active chemical compound, dosage, or concentration."
            ),
            SchemaField(
                id = "f_p_brand",
                name = "Brand",
                label = "Laboratory / Brand",
                type = SchemaFieldType.STRING,
                description = "Pharmaceutical laboratory or cosmetics brand."
            ),
            SchemaField(
                id = "f_p_category",
                name = "Category",
                label = "Therapeutic Category",
                type = SchemaFieldType.CATEGORY,
                description = "Department, e.g. Pain Relief, Skincare, Vitamins, Eye Care."
            ),
            SchemaField(
                id = "f_p_prix",
                name = "Prix",
                label = "Selling Price",
                type = SchemaFieldType.PRICE,
                description = "Selling price numeric value.",
                required = true
            ),
            SchemaField(
                id = "f_p_date_promo",
                name = "Date_Promo",
                label = "Valid Period / Expiry",
                type = SchemaFieldType.DATE,
                description = "Promotion validity or expiry notice."
            ),
            SchemaField(
                id = "f_p_unite",
                name = "unité",
                label = "Packaging / Format",
                type = SchemaFieldType.STRING,
                description = "Format e.g. Box of 30 tabs, 100ml Syrup, 50g Gel."
            )
        )
    )

    fun getAllPresets(): List<ExtractionSchema> = listOf(
        SUPERMARKET_FLYER,
        RETAIL_SKU,
        ELECTRONICS_SPECS,
        WHOLESALE_B2B,
        PHARMACY_HEALTH
    )

    fun getDefaultSchema(): ExtractionSchema = SUPERMARKET_FLYER

    fun findPresetById(id: String): ExtractionSchema? {
        return getAllPresets().find { it.id == id }
    }
}
