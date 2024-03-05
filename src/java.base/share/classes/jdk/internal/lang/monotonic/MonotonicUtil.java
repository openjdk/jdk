package jdk.internal.lang.monotonic;

import jdk.internal.misc.Unsafe;

final class MonotonicUtil {

    private MonotonicUtil() {
    }

    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static UnsupportedOperationException uoe() {
        return new UnsupportedOperationException();
    }

    interface ThrowingFunction<T, R> {
        R apply(T t) throws Throwable;
    }
}
