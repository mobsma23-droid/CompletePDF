package com.example.model

import kotlinx.serialization.Serializable

@Serializable
enum class ValidationAction {
    FLAG_WARNING,   // Keep product but flag validation warning
    FILTER_EXCLUDE, // Exclude product item from dataset
    SET_EMPTY       // Clear out invalid field content
}

@Serializable
data class FieldValidationRule(
    val id: String,
    val fieldName: String, // "Produit", "Prix", "Prix_Normal", "Brand", "Category", "Date_Promo", "unité"
    val pattern: String,
    val description: String,
    val isEnabled: Boolean = true,
    val actionOnMismatch: ValidationAction = ValidationAction.FLAG_WARNING,
    val isCustom: Boolean = true
) {
    fun matches(input: String): Boolean {
        if (pattern.isBlank()) return true
        return try {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            regex.containsMatchIn(input) || regex.matches(input)
        } catch (e: Exception) {
            true
        }
    }
}

data class FieldValidationResult(
    val isValid: Boolean,
    val failedRules: List<FieldValidationRule> = emptyList(),
    val warnings: List<String> = emptyList()
)

object DefaultValidationRules {
    fun getDefaultRules(): List<FieldValidationRule> = listOf(
        FieldValidationRule(
            id = "rule_prod_name_len",
            fieldName = "Produit",
            pattern = "^.{2,}$",
            description = "Product name must contain at least 2 characters",
            isEnabled = true,
            actionOnMismatch = ValidationAction.FLAG_WARNING,
            isCustom = false
        ),
        FieldValidationRule(
            id = "rule_price_numeric",
            fieldName = "Prix",
            pattern = "^\\d+(\\.\\d{1,2})?$",
            description = "Price must be a valid positive decimal number (e.g. 19.99 or 45)",
            isEnabled = true,
            actionOnMismatch = ValidationAction.FLAG_WARNING,
            isCustom = false
        ),
        FieldValidationRule(
            id = "rule_normal_price_numeric",
            fieldName = "Prix_Normal",
            pattern = "^(\\d+(\\.\\d{1,2})?)?$",
            description = "Regular price must be numeric when present",
            isEnabled = true,
            actionOnMismatch = ValidationAction.FLAG_WARNING,
            isCustom = false
        ),
        FieldValidationRule(
            id = "rule_brand_chars",
            fieldName = "Brand",
            pattern = "^[a-zA-Z0-9\\s&'./-]*$",
            description = "Brand name contains valid characters",
            isEnabled = true,
            actionOnMismatch = ValidationAction.FLAG_WARNING,
            isCustom = false
        ),
        FieldValidationRule(
            id = "rule_unit_format",
            fieldName = "unité",
            pattern = "^[a-zA-Z0-9\\s./%xX-]*$",
            description = "Unit size format (e.g., 1L, 500g, pack, piece)",
            isEnabled = true,
            actionOnMismatch = ValidationAction.FLAG_WARNING,
            isCustom = false
        )
    )
}
