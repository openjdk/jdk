/*
 * @test /nodynamiccopyright/
 * @bug 8334756
 * @summary javac crashes on call to non-existent generic method with explicit annotated type arg
 * @compile/fail/ref=CrashOnNonExistingMethodTest.out -XDrawDiagnostics -XDdev CrashOnNonExistingMethodTest.java
 */

import static java.lang.annotation.ElementType.TYPE_USE;
import java.lang.annotation.Target;

class CrashOnNonExistingMethodTest {
    @Target(TYPE_USE)
    @interface Nullable {}

    static <T extends @Nullable Object> T identity(T t) {
        return t;
    }

    static void test() {
        CrashOnNonExistingMethodTest.<@Nullable Object>nonNullIdentity(null);
    }
}
