/*
 * @test /nodynamiccopyright/
 * @bug 8316971
 * @summary Smoke test for restricted method call warnings
 * @compile/fail/ref=RestrictedMethods.out -Xlint:restricted -Werror -XDrawDiagnostics --enable-preview --source ${jdk.version} RestrictedMethods.java
 * @compile -Werror --enable-preview --source ${jdk.version} RestrictedMethods.java
 */

import java.lang.foreign.MemorySegment;

class RestrictedMethods {

    MemorySegment warn = MemorySegment.NULL.reinterpret(10);
    @SuppressWarnings("restricted")
    MemorySegment suppressed = MemorySegment.NULL.reinterpret(10);

    void testWarn() {
        MemorySegment.NULL.reinterpret(10); // warning here
    }

    @SuppressWarnings("restricted")
    void testSuppressed() {
        MemorySegment.NULL.reinterpret(10); // no warning here
    }

    @SuppressWarnings("restricted")
    static class Nested {
        MemorySegment suppressedNested = MemorySegment.NULL.reinterpret(10);

        void testSuppressedNested() {
            MemorySegment.NULL.reinterpret(10); // no warning here
        }
    }
}
