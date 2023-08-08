package jdk.internal.foreign;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class DefaultNativeLookupUtil {

    private static final MethodHandle INT_IS_ONE;

    static {
        try {
            INT_IS_ONE = MethodHandles.lookup().findStatic(DefaultNativeLookupUtil.class, "intIsOne", MethodType.methodType(boolean.class, int.class));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DefaultNativeLookupUtil() {}

    public static MethodHandle downcallOfVoid(String name, MemoryLayout... types) {
        return Linker.nativeLinker().downcallHandle(
                Linker.nativeLinker().defaultLookup().find(name).orElseThrow(),
                FunctionDescriptor.ofVoid(types));
    }

    public static MethodHandle downcall(String name, MemoryLayout resLayout, MemoryLayout... types) {
        return Linker.nativeLinker().downcallHandle(
                Linker.nativeLinker().defaultLookup().find(name).orElseThrow(),
                FunctionDescriptor.of(resLayout, types));
    }

    public static MethodHandle intIsOne(MethodHandle original) {
        return MethodHandles.filterReturnValue(original, INT_IS_ONE);
    }

    public static boolean intIsOne(int returnValue) {
        return returnValue == 1;
    }


}
