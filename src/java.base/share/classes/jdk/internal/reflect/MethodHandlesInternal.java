package jdk.internal.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

/**
 * This class contains a number of static factories for certain VarHandle/MethodHandle types
 * <p>
 * The methods are intended to be called from a static context:
 * {@snippet lang=java
 * static MethodHandle BAR_HANDLE =
 *         MethodHandlesInternal.findVirtualOrThrow(MethodHandles.lookup(),
 *                 Foo.class,"bar",MethodType.methodType(int.class));
 * }
 */
public final class MethodHandlesInternal {

    private MethodHandlesInternal() {}

    public static VarHandle findVarHandleOrThrow(MethodHandles.Lookup lookup, Class<?> recv, String name, Class<?> type) {
        try {
            return lookup.findVarHandle(recv, name, type);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static MethodHandle findVirtualOrThrow(MethodHandles.Lookup lookup, Class<?> refc, String name, MethodType type) {
        try {
            return lookup.findVirtual(refc, name, type);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
