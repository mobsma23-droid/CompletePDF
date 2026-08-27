package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class ProductItem(
    val Produit: String = "",
    val Brand: String = "",
    val Category: String = "",
    val Prix: String = "",
    val Prix_Normal: String = "",
    val Date_Promo: String = "",
    val unité: String = "",
    val SKU: String = "",
    val Barcode: String = "",
    val customFields: Map<String, String> = emptyMap()
) {
    /**
     * Cleans price string so only numbers and standard decimal point remain.
     * e.g., "Rs 1,250.50" -> "1250.50", "45.00 $" -> "45.00"
     */
    fun cleanPrice(rawPrice: String = Prix): String {
        if (rawPrice.isBlank()) return ""
        
        // Remove currency symbols, characters, spaces, except digits and dot or comma
        val normalized = rawPrice.replace(",", ".")
        val digitsAndDotOnly = normalized.filter { it.isDigit() || it == '.' }
        
        // Handle multiple decimal points if present
        val parts = digitsAndDotOnly.split(".")
        return when {
            parts.isEmpty() -> ""
            parts.size == 1 -> parts[0].ifBlank { "" }
            else -> {
                val integerPart = parts[0]
                val decimalPart = parts.drop(1).joinToString("")
                if (integerPart.isBlank() && decimalPart.isBlank()) ""
                else "${integerPart.ifBlank { "0" }}.$decimalPart"
            }
        }
    }

    fun getFieldValue(key: String): String {
        return when (key.lowercase()) {
            "produit", "product", "name", "item", "product name" -> Produit
            "brand", "marque", "manufacturer" -> Brand
            "category", "categorie", "section", "department" -> Category
            "prix", "price", "promo_price", "prix promo", "promo price", "selling price" -> Prix
            "prix_normal", "prix normal", "normal_price", "regular_price", "regular price", "msrp", "list price" -> Prix_Normal
            "date_promo", "date promo", "promo_date", "validity", "promo validity date", "valid period" -> Date_Promo
            "unité", "unite", "unit", "size", "unit / size", "format" -> unité
            "sku", "item_code", "article_number", "model", "model no / sku", "item code / sku" -> SKU
            "barcode", "ean", "upc", "barcode / ean" -> Barcode
            else -> customFields[key]
                ?: customFields.entries.find { it.key.equals(key, ignoreCase = true) }?.value
                ?: ""
        }
    }

    fun sanitized(): ProductItem {
        return copy(
            Produit = Produit.trim().ifBlank { "N/A" },
            Brand = Brand.trim().ifBlank { "" },
            Category = Category.trim().ifBlank { "General" },
            Prix = cleanPrice(Prix).ifBlank { "0.00" },
            Prix_Normal = cleanPrice(Prix_Normal),
            Date_Promo = Date_Promo.trim(),
            unité = unité.trim().ifBlank { "piece" },
            SKU = SKU.trim(),
            Barcode = Barcode.trim(),
            customFields = customFields.mapValues { it.value.trim() }
        )
    }
}
