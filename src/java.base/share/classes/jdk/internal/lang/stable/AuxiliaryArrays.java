package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.Stable;

import static jdk.internal.lang.stable.StableUtil.*;

public record AuxiliaryArrays(@Stable int[] states,
                              Object[] mutexes,
                              boolean[] supplyings) { // Todo: make this array more dense

    public int state(int index) {
        return states[index];
    }

    public static AuxiliaryArrays create(int size) {
        return new AuxiliaryArrays(new int[size], new Object[size], new boolean[size]);
    }

    byte stateVolatile(int index) {
        return UNSAFE.getByteVolatile(states, StableUtil.intOffset(index));
    }

    boolean supplying(int index) {
        return supplyings[index];
    }

    void supplying(int index, boolean supplying) {
        supplyings[index] = supplying;
    }

    void putState(int index, int newValue) {
        // This prevents `this.element[index]` to be seen
        // before `this.status[index]` is seen
        freeze();
        UNSAFE.putIntVolatile(states, StableUtil.intOffset(index), newValue);
    }

    Object acquireMutex(int index) {
        Object mutex = UNSAFE.getReferenceVolatile(mutexes, StableUtil.objectOffset(index));
        if (mutex == null) {
            mutex = caeMutex(index);
        }
        return mutex;
    }

    Object caeMutex(int index) {
        final var created = new Object();
        final var witness = UNSAFE.compareAndExchangeReference(mutexes, objectOffset(index), null, created);
        return witness == null ? created : witness;
    }

    void putMutex(int index, Object value) {
        UNSAFE.putReferenceVolatile(mutexes, objectOffset(index), value);
    }

}
