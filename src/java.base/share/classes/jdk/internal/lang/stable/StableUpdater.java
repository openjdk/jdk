package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.util.Architecture;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Stable field updaters.
 * <p>
 * The provided {@code underlying} function must not recurse or the result of the
 * operation is unspecified.
 */
public final class StableUpdater {

    private StableUpdater() {}

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    @CallerSensitive
    public static <T> ToIntFunction<T> ofInt(Class<T> holderType,
                                             String fieldName,
                                             ToIntFunction<? super T> underlying,
                                             int zeroReplacement) {
        Objects.requireNonNull(holderType);
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(underlying);

        final long offset = offset(holderType, fieldName, int.class, Reflection.getCallerClass());
        return new StableIntUpdater<>(holderType, offset, underlying, zeroReplacement);
    }

    // Only to be used by classes that are used "early" in the init sequence.
    public static <T> ToIntFunction<T> ofIntRaw(Class<T> holderType,
                                                long offset,
                                                ToIntFunction<? super T> underlying,
                                                int zeroReplacement) {
        Objects.requireNonNull(holderType);
        if (offset < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(underlying);
        return new StableIntUpdater<>(holderType, offset, underlying, zeroReplacement);
    }

    @CallerSensitive
    public static <T> ToLongFunction<T> ofLong(Class<T> holderType,
                                               String fieldName,
                                               ToLongFunction<? super T> underlying,
                                               long zeroReplacement) {
        Objects.requireNonNull(holderType);
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(underlying);

        final long offset = offset(holderType, fieldName, long.class, Reflection.getCallerClass());
        if (Architecture.is64bit()) {
            return new StableLongUpdater<>(holderType, offset, underlying, zeroReplacement);
        } else {
            return new TearingStableLongUpdater<>(holderType, offset, underlying, zeroReplacement);
        }
    }

    private record StableIntUpdater<T>(Class<T> holderType,
                                      long offset,
                                      ToIntFunction<? super T> underlying,
                                      int zeroReplacement) implements ToIntFunction<T> {

        @ForceInline
        @Override
        public int applyAsInt(T t) {
            checkType(holderType, t);
            // Plain semantics suffice here as we are not dealing with a reference (for
            // a reference, the internal state initialization can be reordered with
            // other store ops). JLS (24) 17.4 states that 64-bit fields tear under
            // plain memory semantics. But, `int` is 32-bit.
            int v = UNSAFE.getInt(t, offset);
            if (v == 0) {
                // StableUtil.preventReentry(t);
                synchronized (t) {
                    v = UNSAFE.getIntAcquire(t, offset);
                    if (v == 0) {
                        v = underlying.applyAsInt(t);
                        if (v == 0) {
                            v = zeroReplacement;
                        }
                        UNSAFE.putIntRelease(t, offset, v);
                    }
                }
            }
            return v;
        }
    }

    private record StableLongUpdater<T>(Class<T> holderType,
                                        long offset,
                                        ToLongFunction<? super T> underlying,
                                        long zeroReplacement) implements ToLongFunction<T> {

        @ForceInline
        @Override
        public long applyAsLong(T t) {
            checkType(holderType, t);
            // Plain semantics suffice here as we are not dealing with a reference (for
            // a reference, the internal state initialization can be reordered with
            // other store ops). JLS (24) 17.4 states that 64-bit fields tear under
            // plain memory semantics. But, the VM is 64-bit.
            long v = UNSAFE.getLong(t, offset);
            if (v == 0) {
                // StableUtil.preventReentry(t);
                synchronized (t) {
                    v = UNSAFE.getLongAcquire(t, offset);
                    if (v == 0) {
                        v = underlying.applyAsLong(t);
                        if (v == 0) {
                            v = zeroReplacement;
                        }
                        UNSAFE.putLongRelease(t, offset, v);
                    }
                }
            }
            return v;
        }
    }

    private record TearingStableLongUpdater<T>(Class<T> holderType,
                                               long offset,
                                               ToLongFunction<? super T> underlying,
                                               long zeroReplacement) implements ToLongFunction<T> {

        @ForceInline
        @Override
        public long applyAsLong(T t) {
            checkType(holderType, t);
            // Plain semantics suffice here as we are not dealing with a reference (for
            // a reference, the internal state initialization can be reordered with
            // other store ops). JLS (24) 17.4 states that 64-bit fields tear under
            // plain memory semantics and this VM is not 64-bit.
            long v = UNSAFE.getLongOpaque(t, offset);
            if (v == 0) {
                // StableUtil.preventReentry(t);
                synchronized (t) {
                    v = UNSAFE.getLongAcquire(t, offset);
                    if (v == 0) {
                        v = underlying.applyAsLong(t);
                        if (v == 0) {
                            v = zeroReplacement;
                        }
                        UNSAFE.putLongRelease(t, offset, v);
                    }
                }
            }
            return v;
        }
    }

    // Static support functions

    @ForceInline
    private static void checkType(Class<?> holderType, Object t) {
        if (!holderType.isInstance(t)) {
            throw new IllegalArgumentException("The provided t is not an instance of " + holderType);
        }
    }

    private static long offset(Class<?> holderType,
                               String fieldName,
                               Class<?> fieldType,
                               Class<?> caller) {
        final Field field;
        try {
            field = findField(holderType, fieldName);
            int modifiers = field.getModifiers();
            if (Modifier.isFinal(modifiers)) {
                throw illegalField("non final fields", field);
            }
            sun.reflect.misc.ReflectUtil.ensureMemberAccess(
                    caller, holderType, null, modifiers);
            if (field.getType() != fieldType) {
                throw illegalField("fields of type '" + fieldType + "'", field);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
        return UNSAFE.objectFieldOffset(field);
    }

    private static IllegalArgumentException illegalField(String msg, Field field) {
        return new IllegalArgumentException("Only " + msg + " are supported. The provided field is '" + field + "'");
    }

    private static Field findField(Class<?> holderType, String fieldName) throws NoSuchFieldException {
        if (holderType.equals(Object.class)) {
            throw new NoSuchFieldException("'" + fieldName + "' in '" + holderType + "'");
        }
        final Field[] fields = holderType.getDeclaredFields();
        for (Field f : fields) {
            if (f.getName().equals(fieldName)) {
                return f;
            }
        }
        return findField(holderType.getSuperclass(), fieldName);
    }

}
