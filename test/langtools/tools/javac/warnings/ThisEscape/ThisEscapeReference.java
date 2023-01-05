/*
 * @test /nodynamiccopyright/
 * @bug 8194743
 * @compile/ref=ThisEscapeReference.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeReference.java
 * @summary Verify proper handling of 'this' escape warnings from method references
 */

import java.util.function.*;

public class ThisEscapeReference {

// Test 1 - ReferenceKind.SUPER

    public static class Test1 {
        public void mightLeak() {
        }
    }

    public static class Test1b extends Test1 {
        public Test1b() {
            new Thread(super::mightLeak);   // this is a leak
        }
    }

    public static class Test1c extends Test1 {
        public Test1c() {
            new Thread(super::notify);      // this is not a leak
        }
    }

// Test 2 - ReferenceKind.BOUND

    public static class Test2 {

        public Test2() {
            new Thread(this::mightLeak);    // this is a leak
        }

        public Test2(int x) {
            final Test2 foo = new Test2();
            new Thread(foo::mightLeak);     // this is not a leak
        }

        public Test2(char x) {
            new Thread(this::noLeak);       // this is not a leak
        }

        public void mightLeak() {
        }

        private void noLeak() {
        }
    }

// Test 3 - ReferenceKind.IMPLICIT_INNER

    public static class Test3 {

        public Test3() {
            new Thread(Inner1::new);        // this is a leak
        }

        public Test3(int x) {
            new Thread(Inner2::new);        // this is not a leak
        }

        public void mightLeak() {
        }

        public class Inner1 {
            public Inner1() {
                Test3.this.mightLeak();
            }
        }

        public class Inner2 {
            public Inner2() {
                new Test3().mightLeak();
            }
        }
    }

// Test 4 - ReferenceKind.UNBOUND, STATIC, TOPLEVEL, ARRAY_CTOR

    public static class Test4 {

        // ReferenceKind.UNBOUND
        public Test4() {
            Test4.bar(Test4::sameHashCode);
        }

        // ReferenceKind.STATIC
        public Test4(int x) {
            new Thread(Test4::noLeak);      // this is not a leak
        }

        // ReferenceKind.ARRAY_CTOR
        public Test4(char x) {
            Test4.foo(String[]::new);       // this is not a leak
        }

        // ReferenceKind.TOPLEVEL
        public Test4(short x) {
            Test4.foo(Test4::new);          // this is not a leak
        }

        public static void noLeak() {
        }

        public static void foo(IntFunction<?> x) {
            x.hashCode();
        }

        public static void bar(BiPredicate<Test4, Object> x) {
            x.hashCode();
        }

        public boolean sameHashCode(Object obj) {
            return obj.hashCode() == this.hashCode();
        }
    }
}
