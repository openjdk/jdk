package jdk.internal.foreign.support;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.NoSuchElementException;

public final class DefaultNativeLookupUtil {

    private DefaultNativeLookupUtil() {}

    private static final MethodHandle INT_IS_ONE;

    static {
        try {
            INT_IS_ONE = MethodHandles.lookup().findStatic(DefaultNativeLookupUtil.class, "intIsOne", MethodType.methodType(boolean.class, int.class));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static MethodHandle downcallOfVoid(String name, MemoryLayout... types) {
        return Linker.nativeLinker().downcallHandle(
                lookup(name),
                FunctionDescriptor.ofVoid(types));
    }

    public static MethodHandle downcall(String name, MemoryLayout resLayout, MemoryLayout... types) {
        return Linker.nativeLinker().downcallHandle(
                lookup(name),
                FunctionDescriptor.of(resLayout, types));
    }

    public static MethodHandle intIsOne(MethodHandle original) {
        return MethodHandles.filterReturnValue(original, INT_IS_ONE);
    }

    public static boolean intIsOne(int returnValue) {
        return returnValue == 1;
    }

    private static MemorySegment lookup(String name) {
        var address = Linker.nativeLinker()
                .defaultLookup()
                .find(name);

        // Avoid using a capturing lambda
        if (address.isPresent()) {
            return address.get();
        }
        throw new NoSuchElementException("Unable to find a native symbol named '" + name + "'");
    }

}
