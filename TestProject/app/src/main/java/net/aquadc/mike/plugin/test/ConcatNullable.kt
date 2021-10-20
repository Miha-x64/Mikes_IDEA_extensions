package net.aquadc.mike.plugin.test

@Suppress("unused")
fun concatNullable() {
    val ok = "" + "" + 1 + Any()
    val left = null + ""
    val right = "" + null
    val within = "" + null + 1

    var peq1: String? = null
    peq1 += ""
    peq1 += 1
    peq1 += null

    var peq2 = ""
    peq2 += ""
    peq2 += 1
    peq2 += null

    "".plus("")
    null.plus("")
    "".plus(null)
    "".apply { plus("") }
    null.apply { plus("") }
    "".apply { plus(null) }


    buildString {
        append("").append(peq1).append(null as Any?).append(1).append(Any())
    }
    if (peq1 != null) {
        buildString {
            append(peq1)
        }
        peq1.apply {
            plus("")
        }
    }

    val dontTrigger = ArrayList<Any?>()
    dontTrigger += Any() as Any?
    dontTrigger + Any() as Any?
    dontTrigger.plus(Any() as Any?)
}
