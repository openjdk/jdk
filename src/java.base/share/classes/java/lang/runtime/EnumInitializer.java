package java.lang.runtime;

import jdk.internal.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A class for initializing enums.
 */
public final class EnumInitializer {

    /**
     * A method for initializing enums.
     *
     * @param enumClassLU the lookup
     */
    public static void initializeEnumClass(MethodHandles.Lookup enumClassLU) {
        if (!enumClassLU.hasFullPrivilegeAccess()) {
            throw new IllegalArgumentException();
        }
        @SuppressWarnings("rawtypes")
        Class<? extends Enum> ec = enumClassLU.lookupClass().asSubclass(Enum.class);
        Unsafe unsafe = Unsafe.getUnsafe();
        try {
            // TODO - guarantee the creator method name doesn't clash, and pass in the name of the generated method
            Method enumMemberCreator = ec.getDeclaredMethod("enumMemberCreator$", int.class);
            enumMemberCreator.setAccessible(true);
            int ordinal = 0;
            for (Field f : ec.getDeclaredFields()) {  //order significant here
                if (f.isEnumConstant()) {
                    Object e = enumMemberCreator.invoke(null, ordinal++);
                    long offset = unsafe.staticFieldOffset(f);
                    assert unsafe.getReference(enumClassLU.lookupClass(), offset) == null; //caller resp.
                    unsafe.putReference(enumClassLU.lookupClass(), offset, e);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new LinkageError(e.getMessage(), e);
        }
    }

    private EnumInitializer() {
    }
}
