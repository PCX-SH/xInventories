package sh.pcx.xinventories.internal.model

/**
 * Comparison operators for placeholder conditions.
 */
enum class ComparisonOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    LESS_THAN,
    GREATER_OR_EQUAL,
    LESS_OR_EQUAL,
    CONTAINS;

    /**
     * Evaluates the comparison between two string values.
     * Attempts numeric comparison first, falls back to string comparison.
     */
    fun evaluate(actual: String, expected: String): Boolean {
        // Try numeric comparison first
        val actualNum = actual.toDoubleOrNull()
        val expectedNum = expected.toDoubleOrNull()

        if (actualNum != null && expectedNum != null) {
            return when (this) {
                EQUALS -> actualNum == expectedNum
                NOT_EQUALS -> actualNum != expectedNum
                GREATER_THAN -> actualNum > expectedNum
                LESS_THAN -> actualNum < expectedNum
                GREATER_OR_EQUAL -> actualNum >= expectedNum
                LESS_OR_EQUAL -> actualNum <= expectedNum
                CONTAINS -> actual.contains(expected, ignoreCase = true)
            }
        }

        // Fall back to string comparison
        return when (this) {
            EQUALS -> actual.equals(expected, ignoreCase = true)
            NOT_EQUALS -> !actual.equals(expected, ignoreCase = true)
            GREATER_THAN -> actual.compareTo(expected, ignoreCase = true) > 0
            LESS_THAN -> actual.compareTo(expected, ignoreCase = true) < 0
            GREATER_OR_EQUAL -> actual.compareTo(expected, ignoreCase = true) >= 0
            LESS_OR_EQUAL -> actual.compareTo(expected, ignoreCase = true) <= 0
            CONTAINS -> actual.contains(expected, ignoreCase = true)
        }
    }

    companion object {
        /**
         * Parses an operator from string.
         * Supports both enum names and symbols (==, !=, >, <, >=, <=).
         */
        fun fromString(value: String): ComparisonOperator? {
            return when (value.trim().uppercase()) {
                "EQUALS", "=", "==" -> EQUALS
                "NOT_EQUALS", "!=", "<>" -> NOT_EQUALS
                "GREATER_THAN", ">" -> GREATER_THAN
                "LESS_THAN", "<" -> LESS_THAN
                "GREATER_OR_EQUAL", ">=" -> GREATER_OR_EQUAL
                "LESS_OR_EQUAL", "<=" -> LESS_OR_EQUAL
                "CONTAINS" -> CONTAINS
                else -> try {
                    valueOf(value.trim().uppercase())
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }
    }
}
