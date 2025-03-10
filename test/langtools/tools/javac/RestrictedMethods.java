/*
 * @test /nodynamiccopyright/
 * @bug 8316971
 * @summary Smoke test for restricted method call warnings
 * @compile/fail/ref=RestrictedMethods.out -Xlint:restricted -Werror -XDrawDiagnostics RestrictedMethods.java
 * @compile/fail/ref=RestrictedMethods.out --release ${jdk.version} -Xlint:restricted -Werror -XDrawDiagnostics RestrictedMethods.java
 * @compile -Werror RestrictedMethods.java
 */

import java.lang.foreign.MemorySegment;
import java.util.function.Function;

class RestrictedMethods {

    MemorySegment warn = MemorySegment.NULL.reinterpret(10); // warning here
    @SuppressWarnings("restricted")
    MemorySegment suppressed = MemorySegment.NULL.reinterpret(10); // no warning here

    Function<Integer, MemorySegment> warn_ref = MemorySegment.NULL::reinterpret; // warning here

    @SuppressWarnings("restricted")
    Function<Integer, MemorySegment> suppressed_ref = MemorySegment.NULL::reinterpret; // no warning here

    void testWarn() {
        MemorySegment.NULL.reinterpret(10); // warning here
    }

    @SuppressWarnings("restricted")
    void testSuppressed() {
        MemorySegment.NULL.reinterpret(10); // no warning here
    }

    Function<Integer, MemorySegment> testWarnRef() {
        return MemorySegment.NULL::reinterpret; // warning here
    }

    @SuppressWarnings("restricted")
    Function<Integer, MemorySegment> testSuppressedRef() {
        return MemorySegment.NULL::reinterpret; // no warning here
    }

    @SuppressWarnings("restricted")
    static class Nested {
        MemorySegment suppressedNested = MemorySegment.NULL.reinterpret(10); // no warning here

        Function<Integer, MemorySegment> suppressedNested_ref = MemorySegment.NULL::reinterpret; // no warning here

        void testSuppressedNested() {
            MemorySegment.NULL.reinterpret(10); // no warning here
        }

        Function<Integer, MemorySegment> testSuppressedNestedRef() {
            return MemorySegment.NULL::reinterpret; // no warning here
        }
    }
}
