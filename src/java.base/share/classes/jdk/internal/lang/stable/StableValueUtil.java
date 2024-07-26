package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

public final class StableValueUtil {

    private StableValueUtil() {}

    // Unsafe allows StableValue to be used early in the boot sequence
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Used to indicate a holder value is `null` (see field `value` below)
    // A wrapper method `nullSentinel()` is used for generic type conversion.
    static final Object NULL_SENTINEL = new Object();

    // Wraps `null` values into a sentinel value
    @ForceInline
    static <T> T wrap(T t) {
        return (t == null) ? nullSentinel() : t;
    }

    // Unwraps null sentinel values into `null`
    @ForceInline
    public static <T> T unwrap(T t) {
        return t != nullSentinel() ? t : null;
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    static <T> T nullSentinel() {
        return (T) NULL_SENTINEL;
    }

    static <T> String render(T t) {
        return (t == null) ? ".unset" : "[" + unwrap(t) + "]";
    }

    @ForceInline
    static boolean safelyPublish(Object o, long offset, Object value) {

        // Prevents reordering of store operations with other store operations.
        // This means any stores made to a field prior to this point cannot be
        // reordered with the following CAS operation of the reference to the field.

        // In other words, if a loader (using plain memory semantics) can first observe
        // a holder reference, any field updates in the holder reference made prior to
        // this fence are guaranteed to be seen.
        // See https://gee.cs.oswego.edu/dl/html/j9mm.html "Mixed Modes and Specializations",
        // Doug Lea, 2018
        UNSAFE.storeStoreFence();

        // This upholds the invariant, a `@Stable` field is written to at most once.
        return UNSAFE.compareAndSetReference(o, offset, null, wrap(value));
    }

}
