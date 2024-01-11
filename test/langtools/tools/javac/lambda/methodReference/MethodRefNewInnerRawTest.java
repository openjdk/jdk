/*
 * @test /nodynamiccopyright/
 * @bug 8029633
 * @summary Raw inner class constructor ref should not perform diamond inference
 * @compile/fail/ref=MethodRefNewInnerRawTest.out -Werror -Xlint:unchecked -XDrawDiagnostics MethodRefNewInnerRawTest.java
 */

import java.util.function.*;

class MethodRefNewInnerRawTest<T> {
    class Inner1 {}
    class Inner2<U> {}

    Supplier<MethodRefNewInnerRawTest.Inner1> s1 = MethodRefNewInnerRawTest.Inner1::new;
    Supplier<MethodRefNewInnerRawTest.Inner2> s2 = MethodRefNewInnerRawTest.Inner2::new;
    Supplier<MethodRefNewInnerRawTest<T>.Inner1> s3 = MethodRefNewInnerRawTest.Inner1::new;
    Supplier<MethodRefNewInnerRawTest<T>.Inner2<String>> s4 = MethodRefNewInnerRawTest.Inner2::new;

    static class Outer {
        class Inner3<U> {}

        Supplier<Outer.Inner3<String>> s5 = Outer.Inner3::new;
    }
}
