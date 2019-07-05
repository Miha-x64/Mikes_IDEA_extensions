package net.aquadc.mike.plugin.test

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater
import java.util.concurrent.atomic.AtomicReference

class AtomicAsVolatileKotlin {

    private val normalAtomic = AtomicReference<Any>()
    private val volatileAtomic: AtomicReference<Any>
    init {
        volatileAtomic = AtomicReference()
    }

    @Volatile private var v1: Int = 0
    @Volatile private var v2: Int = 0

    internal fun someFunc() {
        normalAtomic.compareAndSet(Any(), Any())
        volatileAtomic.set(Any())

        normalFU.getAndSet(this, 11)
        atomicFUAsVolatile.get(this)
    }

    companion object {
        private val normalFU = AtomicIntegerFieldUpdater.newUpdater(AtomicAsVolatileKotlin::class.java, "v1")
        private val atomicFUAsVolatile = AtomicIntegerFieldUpdater.newUpdater(AtomicAsVolatileKotlin::class.java, "v2")
    }

}
