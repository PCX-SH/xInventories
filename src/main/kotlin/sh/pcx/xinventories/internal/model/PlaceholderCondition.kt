package sh.pcx.xinventories.internal.model

/**
 * Represents a PlaceholderAPI condition for group matching.
 */
data class PlaceholderCondition(
    /**
     * The placeholder to evaluate (e.g., "%player_level%").
     */
    val placeholder: String,

    /**
     * The comparison operator.
     */
    val operator: ComparisonOperator,

    /**
     * The expected value to compare against.
     */
    val value: String
) {
    /**
     * Evaluates this condition against an actual placeholder value.
     *
     * @param actualValue The resolved placeholder value
     * @return true if the condition is satisfied
     */
    fun evaluate(actualValue: String): Boolean {
        return operator.evaluate(actualValue, value)
    }

    /**
     * Returns a human-readable description of this condition.
     */
    fun toDisplayString(): String {
        val operatorSymbol = when (operator) {
            ComparisonOperator.EQUALS -> "="
            ComparisonOperator.NOT_EQUALS -> "!="
            ComparisonOperator.GREATER_THAN -> ">"
            ComparisonOperator.LESS_THAN -> "<"
            ComparisonOperator.GREATER_OR_EQUAL -> ">="
            ComparisonOperator.LESS_OR_EQUAL -> "<="
            ComparisonOperator.CONTAINS -> "contains"
        }
        return "$placeholder $operatorSymbol $value"
    }

    companion object {
        /**
         * Creates a PlaceholderCondition from configuration values.
         *
         * @param placeholder The placeholder string
         * @param operatorStr The operator as a string
         * @param value The comparison value
         * @return PlaceholderCondition or null if operator is invalid
         */
        fun fromConfig(placeholder: String, operatorStr: String, value: String): PlaceholderCondition? {
            val operator = ComparisonOperator.fromString(operatorStr) ?: return null
            return PlaceholderCondition(placeholder, operator, value)
        }

        /**
         * Parses a placeholder condition from a single string expression.
         * Supports formats like: "%player_level% >= 50"
         *
         * @param expression The condition expression
         * @return PlaceholderCondition or null if parsing fails
         */
        fun parse(expression: String): PlaceholderCondition? {
            val operators = listOf(">=", "<=", "!=", "<>", "==", "=", ">", "<", " contains ")

            for (op in operators) {
                val index = expression.indexOf(op, ignoreCase = true)
                if (index > 0) {
                    val placeholder = expression.substring(0, index).trim()
                    val value = expression.substring(index + op.length).trim()
                    val operator = ComparisonOperator.fromString(op.trim()) ?: continue

                    if (placeholder.isNotEmpty() && value.isNotEmpty()) {
                        return PlaceholderCondition(placeholder, operator, value)
                    }
                }
            }

            return null
        }
    }
}
