
import java.io.Serializable


class Iii : AbstractList<Nothing>(), Runnable, Cloneable {

    private fun a() {
        a(1, 1, emptyList(), {}, this)
    }

    override val size: Int
        get() = 0

    override fun get(index: Int): Nothing {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun run() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

fun a(a: Serializable, b: Comparable<*>, c: List<Number>, f: () -> Unit, cl: Cloneable) {

}
