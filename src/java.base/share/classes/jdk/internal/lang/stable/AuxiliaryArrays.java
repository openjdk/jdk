package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.Stable;

record AuxiliaryArrays<V>(@Stable int[] states,
                          Object[] mutexes,
                          boolean[] supplyings) { // Todo: make this array more dense

    public AuxiliaryArrays(int size) {
        this(new int[size], new Object[size], new boolean[size]);
    }

    public int state(int index) {
        return states[index];
    }

}
