
import java.io.Serializable


class Iii : AbstractList<Nothing>(), Runnable, Cloneable, () -> Unit {

    private fun a() {
        a(1, 1, emptyList(), {}, this)
    }

    override val size: Int
        get() = 0

    override fun get(index: Int): Nothing {
        throw UnsupportedOperationException()
    }

    override fun run() {
    }

    override fun invoke() {
    }

}

fun a(a: Serializable, b: Comparable<*>, c: List<Number>, f: () -> Unit, cl: Cloneable) {

}
