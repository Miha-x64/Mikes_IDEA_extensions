package net.aquadc.mike.plugin.test;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;

public class AtomicAsVolatileJava {

    private final AtomicReference<Object> normalAtomic = new AtomicReference<>();
    private final AtomicReference<Object> volatileAtomic = new AtomicReference<>();
    private final AtomicReference<Object> complicatedNormalAtomic = new AtomicReference<>();
    private final AtomicReference<Object> complicatedVolatileAtomic = new AtomicReference<>();

    private volatile int v1;
    private volatile int v2;

    void someFunc() {
        normalAtomic.compareAndSet(new Object(), new Object());
        volatileAtomic.set(new Object());

        normalFU.getAndSet(this, 11);
        atomicAsVolatileFU.get(this);

        atomicFunc(complicatedNormalAtomic);
        volatileFunc(complicatedVolatileAtomic);
    }

    private void atomicFunc(AtomicReference<Object> ref) {
        ref.compareAndSet(new Object(), new Object());
    }

    private void volatileFunc(AtomicReference<Object> ref) {
        ref.get();
    }

    private static final AtomicIntegerFieldUpdater<AtomicAsVolatileJava> normalFU =
            AtomicIntegerFieldUpdater.newUpdater(AtomicAsVolatileJava.class, "v1");

    private static final AtomicIntegerFieldUpdater<AtomicAsVolatileJava> atomicAsVolatileFU =
            AtomicIntegerFieldUpdater.newUpdater(AtomicAsVolatileJava.class, "v2");

}
