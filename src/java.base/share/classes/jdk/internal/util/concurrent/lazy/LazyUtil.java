package jdk.internal.util.concurrent.lazy;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.lazy.LazyReference;

public final class LazyUtil {
    private LazyUtil() {
    }
/*
    // Atomically aquires (sets) the provided flag and returns if it was not previously set
    // (using volatile semantics)
    private boolean acquire(int flag) {
        int previousFlag = (int) FLAGS_HANDLE.getAndBitwiseOr(this, flag);
        return (previousFlag & flag) == 0;
    }

    // Atomically releases (clears) the provided flag and returns if it was previously set
    // (using volatile semantics)
    private boolean release(int flag) {
        int previousFlag = (int) FLAGS_HANDLE.getAndBitwiseAnd(this, ~flag);
        return (previousFlag & flag) != 0;
    }

    // Atomically obtains (gets) the provided flag (using volatile semantics)
    private boolean get(int flag) {
        int flags = (int) FLAGS_HANDLE.getVolatile(this);
        return (flags & flag) != 0;
    }*/

    public static VarHandle varHandle(MethodHandles.Lookup lookup,
                                      String fieldName,
                                      Class<?> fieldType) {
        try {
            return lookup
                    .findVarHandle(LazyReference.class, fieldName, fieldType);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
