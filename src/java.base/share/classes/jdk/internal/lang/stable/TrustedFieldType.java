package jdk.internal.lang.stable;

import jdk.internal.lang.StableArray;
import jdk.internal.lang.StableArray2D;
import jdk.internal.lang.StableArray3D;
import jdk.internal.lang.StableValue;

/**
 * This sealed marker interface signals fields that are _declared_ as a class that
 * implements this interface is "trusted" and therefore somewhat protected from being
 * modified via {@linkplain sun.misc.Unsafe} and {@linkplain java.lang.reflect.Field#setAccessible(boolean)}
 * operations.
 */
public sealed interface TrustedFieldType
        permits StableArray,
        StableArray2D,
        StableArray3D,
        StableValue { }
