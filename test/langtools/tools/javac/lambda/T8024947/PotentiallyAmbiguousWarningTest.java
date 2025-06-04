/*
 * @test /nodynamiccopyright/
 * @bug 8024947 8026369
 * @summary javac should issue the potentially ambiguous overload warning only
 * where the problem appears
 * @compile/fail/ref=PotentiallyAmbiguousWarningTest.out -XDrawDiagnostics -Werror -Xlint:overloads PotentiallyAmbiguousWarningTest.java
 */

import java.util.function.*;

public interface PotentiallyAmbiguousWarningTest {

    //a warning should be fired
    interface I1 {
        void foo(Consumer<Integer> c);
        void foo(IntConsumer c);
    }

    //a warning should be fired
    class C1 {
        void foo(Consumer<Integer> c) { }
        void foo(IntConsumer c) { }
    }

    interface I2 {
        void foo(Consumer<Integer> c);
    }

    //a warning should be fired, J1 is provoking the issue
    interface J1 extends I2 {
        void foo(IntConsumer c);
    }

    //no warning here, the issue is introduced in I1
    interface I3 extends I1 {}

    //no warning here, the issue is introduced in I1. I4 is just overriding an existing method
    interface I4 extends I1 {
        void foo(IntConsumer c);
    }

    class C2 {
        void foo(Consumer<Integer> c) { }
    }

    //a warning should be fired, D1 is provoking the issue
    class D1 extends C2 {
        void foo(IntConsumer c) { }
    }

    //a warning should be fired, C3 is provoking the issue
    class C3 implements I2 {
        public void foo(Consumer<Integer> c) { }
        public void foo(IntConsumer c) { }
    }

    //no warning here, the issue is introduced in C1
    class C4 extends C1 {}

    //no warning here, the issue is introduced in C1. C5 is just overriding an existing method
    class C5 extends C1 {
        void foo(IntConsumer c) {}
    }

    interface I5<T> {
        void foo(T c);
    }

    //a warning should be fired, J2 is provoking the issue
    interface J2 extends I5<IntConsumer> {
        void foo(Consumer<Integer> c);
    }

// The test cases below are from JDK-8026369

    interface I6 {
        void foo(Consumer<Integer> c);
    }

    interface I7 {
        void foo(IntConsumer c);
    }

    //a warning should be fired, I8 is provoking the issue
    interface I8 extends I6, I7 { }

    //no warning here, the issue is introduced in I8
    interface I9 extends I8 { }

    //no warning here
    interface I10<T> {
        void foo(Consumer<Integer> c);
        void foo(T c);
    }

    //a warning should be fired, I11 is provoking the issue
    interface I11 extends I10<IntConsumer> { }

    // No warning should be fired here
    interface I12<T> extends Consumer<T>, IntSupplier {
        // A warning should be fired here
        interface OfInt extends I12<Integer>, IntConsumer {
            @Override
            void accept(int value);
            default void accept(Integer i) { }
        }
        @Override
        default int getAsInt() { return 0; }
    }

    // No warning should be fired here
    abstract static class C6<T> implements I12.OfInt { }

    default <U> Object foo() {
        // No warning should be fired here
        return new C6<U>() {
            @Override
            public void accept(int value) { }
        };
    }

    // Overrides should not trigger warnings
    interface I13 extends I8 {
        @Override
        void foo(Consumer<Integer> c);
        @Override
        void foo(IntConsumer c);
    }
    interface I14 extends I8 {
        @Override
        void foo(IntConsumer c);
    }

    // Verify we can suppress warnings at the class level
    @SuppressWarnings("overloads")
    interface I15 extends I8 { }        // would normally trigger a warning

    // Verify we can suppress warnings at the method level
    interface I16 extends I2 {
        @SuppressWarnings("overloads")
        void foo(IntConsumer c);        // would normally trigger a warning
    }
}
