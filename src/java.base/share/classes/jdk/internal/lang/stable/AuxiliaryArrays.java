package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.Stable;

record AuxillaryArrays<V>(@Stable V[] elements,
                          @Stable int[] states,
                          Object[] mutexes,
                          boolean[] supplyings) {}
