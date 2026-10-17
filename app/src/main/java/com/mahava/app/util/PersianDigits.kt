package com.mahava.app.util

object PersianDigits {
    private val western = charArrayOf('0','1','2','3','4','5','6','7','8','9')
    private val persian = charArrayOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')

    fun toPersian(input: String): String {
        val out = StringBuilder(input.length)
        for ((i, c) in input.withIndex()) {
            val idx = western.indexOf(c)
            // A decimal point between two digits is the Persian decimal separator «٫».
            val decimal = c == '.' && i > 0 && i < input.length - 1 && input[i - 1].isDigit() && input[i + 1].isDigit()
            out.append(if (idx >= 0) persian[idx] else if (decimal) '٫' else c)
        }
        return out.toString()
    }

    fun toPersian(n: Int): String = toPersian(n.toString())
    fun toPersian(n: Long): String = toPersian(n.toString())
}
