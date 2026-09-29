/*
 * @test /nodynamiccopyright/
 * @bug 8334466
 * @summary Ambiguous method call with generics may cause FunctionDescriptorLookupError
 * @compile/fail/ref=CrashWithFunctionDescriptorLookupErrorTest.out -XDrawDiagnostics CrashWithFunctionDescriptorLookupErrorTest.java
 */

import java.util.List;

class CrashWithFunctionDescriptorLookupErrorTest {
    void m() {
        List<X> list = List.of(new X());
        test(list.get(0));
    }

    void test(A<?> a) { }
    void test(B<?> b) { }

    interface A<T extends A<T>> { T a(); }
    interface B<T extends B<T>> { T b(); }
    class X implements A<X>, B<X> {
        public X a() { return null; }
        public X b() { return null; }
    }
}
