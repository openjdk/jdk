/*
 * @test /nodynamiccopyright/
 * @bug 8318160
 * @summary javac does not reject private method reference with type-variable receiver
 * @compile/fail/ref=PrivateMethodReferenceWithTypeVarTest.out -XDrawDiagnostics PrivateMethodReferenceWithTypeVarTest.java
 */

import java.util.function.*;

class PrivateMethodReferenceWithTypeVarTest {
    private String asString() {
        return "bar";
    }

    private String asString2() {
        return "bar";
    }

    static <T extends Test> Function<T, String> m1() {
        return T::asString;
    }

    static <T extends Test> Function<T, String> m2(T t) {
        return t::asString2;
    }
}
