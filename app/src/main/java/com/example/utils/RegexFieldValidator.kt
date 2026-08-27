package com.example.utils

import com.example.model.FieldValidationResult
import com.example.model.FieldValidationRule
import com.example.model.ProductItem
import com.example.model.ValidationAction

object RegexFieldValidator {

    data class ValidationRunResult(
        val product: ProductItem?,
        val warnings: List<String>
    )

    /**
     * Validates and optionally cleans/filters a ProductItem based on custom regex validation rules.
     * Returns null product if an active FILTER_EXCLUDE rule fails.
     */
    fun validateProduct(
        item: ProductItem,
        rules: List<FieldValidationRule>
    ): ValidationRunResult {
        val activeRules = rules.filter { it.isEnabled }
        if (activeRules.isEmpty()) {
            return ValidationRunResult(item, emptyList())
        }

        var currentItem = item
        val warnings = mutableListOf<String>()
        var shouldExclude = false

        for (rule in activeRules) {
            val fieldValue = when (rule.fieldName.lowercase()) {
                "produit", "product", "name" -> currentItem.Produit
                "prix", "price" -> currentItem.Prix
                "prix_normal", "regular_price", "old_price" -> currentItem.Prix_Normal
                "brand", "marque" -> currentItem.Brand
                "category", "catégorie", "section" -> currentItem.Category
                "date_promo", "promo_date", "date" -> currentItem.Date_Promo
                "unité", "unite", "unit" -> currentItem.unité
                else -> ""
            }

            // If field is empty and rule is optional, skip unless required
            val isMatched = rule.matches(fieldValue)

            if (!isMatched) {
                when (rule.actionOnMismatch) {
                    ValidationAction.FILTER_EXCLUDE -> {
                        shouldExclude = true
                        warnings.add("[Excluded] ${rule.fieldName} \"$fieldValue\" failed rule: ${rule.description}")
                        break
                    }
                    ValidationAction.FLAG_WARNING -> {
                        warnings.add("${rule.fieldName} \"$fieldValue\" does not match pattern: ${rule.pattern}")
                    }
                    ValidationAction.SET_EMPTY -> {
                        warnings.add("Cleared invalid ${rule.fieldName} \"$fieldValue\" (failed pattern ${rule.pattern})")
                        currentItem = when (rule.fieldName.lowercase()) {
                            "produit", "product", "name" -> currentItem.copy(Produit = "")
                            "prix", "price" -> currentItem.copy(Prix = "")
                            "prix_normal", "regular_price", "old_price" -> currentItem.copy(Prix_Normal = "")
                            "brand", "marque" -> currentItem.copy(Brand = "")
                            "category", "catégorie", "section" -> currentItem.copy(Category = "")
                            "date_promo", "promo_date", "date" -> currentItem.copy(Date_Promo = "")
                            "unité", "unite", "unit" -> currentItem.copy(unité = "")
                            else -> currentItem
                        }
                    }
                }
            }
        }

        return if (shouldExclude) {
            ValidationRunResult(null, warnings)
        } else {
            ValidationRunResult(currentItem, warnings)
        }
    }

    /**
     * Batch processes extracted items through validation rules.
     */
    fun processExtractedProducts(
        products: List<ProductItem>,
        rules: List<FieldValidationRule>
    ): Pair<List<ProductItem>, List<String>> {
        val validatedProducts = mutableListOf<ProductItem>()
        val allWarnings = mutableListOf<String>()

        for (product in products) {
            val (validProduct, warnings) = validateProduct(product, rules)
            if (validProduct != null && validProduct.Produit.isNotBlank()) {
                validatedProducts.add(validProduct)
            }
            allWarnings.addAll(warnings)
        }

        return Pair(validatedProducts, allWarnings)
    }
}
