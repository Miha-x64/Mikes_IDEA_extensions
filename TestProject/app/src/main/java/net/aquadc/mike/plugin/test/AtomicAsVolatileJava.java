package net.aquadc.mike.plugin.test;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;

public class AtomicAsVolatileJava {

    private final AtomicReference<Object> normalAtomic = new AtomicReference<>();
    private final AtomicReference<Object> volatileAtomic = new AtomicReference<>();

    private volatile int v1;
    private volatile int v2;

    void someFunc() {
        normalAtomic.compareAndSet(new Object(), new Object());
        volatileAtomic.set(new Object());

        normalFU.getAndSet(this, 11);
        volatileAsAtomicFU.get(this);
    }

    private static final AtomicIntegerFieldUpdater<AtomicAsVolatileJava> normalFU =
            AtomicIntegerFieldUpdater.newUpdater(AtomicAsVolatileJava.class, "v1");

    private static final AtomicIntegerFieldUpdater<AtomicAsVolatileJava> volatileAsAtomicFU =
            AtomicIntegerFieldUpdater.newUpdater(AtomicAsVolatileJava.class, "v2");

}
