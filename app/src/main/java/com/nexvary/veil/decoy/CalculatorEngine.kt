package com.nexvary.veil.decoy

object CalculatorEngine {
    fun calculate(expression: String): Double {
        require(expression.length <= 200)
        val s = expression.filterNot { it.isWhitespace() }
        var pos=0
        fun number(): Double {
            val start=pos
            if (pos<s.length && (s[pos]=='-' || s[pos]=='+')) pos++
            while(pos<s.length && (s[pos].isDigit() || s[pos]=='.')) pos++
            require(pos>start)
            return s.substring(start,pos).toDouble()
        }
        fun term(): Double {
            var value=number()
            while(pos<s.length && (s[pos]=='*' || s[pos]=='/')) {
                val op=s[pos++]; val n=number(); value=if(op=='*') value*n else value/n
            }
            return value
        }
        var value=term()
        while(pos<s.length && (s[pos]=='+' || s[pos]=='-')) {
            val op=s[pos++]; val n=term(); value=if(op=='+') value+n else value-n
        }
        require(pos==s.length && value.isFinite())
        return value
    }
}
