/*
 * @test /nodynamiccopyright/
 * @bug 8015831
 * @compile/ref=ThisEscape.out -Xlint:this-escape -XDrawDiagnostics ThisEscape.java
 * @summary Verify 'this' escape detection
 */

import java.util.function.*;

public class ThisEscape {

    // Verify 'this' escape detection can follow references embedded as array elements
    public static class ThisEscapeArrayElement {

        public ThisEscapeArrayElement() {
            final Object[][] array = new Object[][] { { this } };
            ((ThisEscapeArrayElement)array[0][0]).mightLeak();
        }

        public void mightLeak() {
        }
    }

    // Verify basic 'this' escape detection
    public static class ThisEscapeBasic {

        public ThisEscapeBasic() {
            this.mightLeak();
        }

        public void mightLeak() {
        }
    }

    // Verify 'this' escape detection can follow references through various Java code structures
    public static class ThisEscapeComplex {

        public ThisEscapeComplex() {
            this.method1().mightLeak();
        }

        public void mightLeak() {
        }

        private ThisEscapeComplex method1() {
            while (true) {
                do {
                    for (ThisEscapeComplex x = this.method2(); new Object().hashCode() < 10; ) {
                        for (int y : new int[] { 123, 456 }) {
                            return x;
                        }
                    }
                } while (true);
            }
        }

        private ThisEscapeComplex method2() {
            switch (new Object().hashCode()) {
            case 1:
            case 2:
            case 3:
                return null;
            default:
                return this.method3();
            }
        }

        private ThisEscapeComplex method3() {
            return switch (new Object().hashCode()) {
                case 1, 2, 3 -> this.method4();
                default -> null;
            };
        }

        private ThisEscapeComplex method4() {
            return ThisEscapeComplex.this.method5();
        }

        private ThisEscapeComplex method5() {
            final ThisEscapeComplex foo = this.method6();
            return foo;
        }

        private ThisEscapeComplex method6() {
            synchronized (new Object()) {
                return this.method7();
            }
        }

        private ThisEscapeComplex method7() {
            ThisEscapeComplex x = null;
            ThisEscapeComplex y = this.method8();
            if (new Object().hashCode() == 3)
                return x;
            else
                return y;
        }

        private ThisEscapeComplex method8() {
            return (ThisEscapeComplex)(Object)this.method9();
        }

        private ThisEscapeComplex method9() {
            return new Object().hashCode() == 3 ? this : null;
        }
    }

    // Verify pruning of 'this' escape warnings for various constructors
    public static class ThisEscapeCtors {

        // This constructor should NOT generate a warning because it would be a
        // duplicate of the warning already generated for ThisEscapeCtors(short).
        public ThisEscapeCtors(char x) {
            this((short)x);
        }

        // This constructor should generate a warning because it invokes leaky this()
        // and is accessible to subclasses.
        public ThisEscapeCtors(short x) {
            this();
        }

        // This constructor should generate a warning because it invokes leaky this()
        // and is accessible to subclasses.
        public ThisEscapeCtors(int x) {
            this();
        }

        // This constructor should NOT generate a warning because it is not accessbile
        // to subclasses. However, other constructors do invoke it, and that should cause
        // them to generate an indirect warning.
        private ThisEscapeCtors() {
            this.mightLeak();
        }

        public void mightLeak() {
        }
    }

    // Verify 'this' escape detection in field initializers
    public static class ThisEscapeFields {

        private final int field1 = this.mightLeak1();

        private final int field2 = this.mightLeak2();

        public int mightLeak1() {
            return 123;
        }

        public int mightLeak2() {
            return 456;
        }
    }

    // Verify 'this' escape detection properly handles lambdas
    public static class ThisEscapeLambda {

        public ThisEscapeLambda() {
            Runnable r = () -> {
                this.mightLeak();
            };
            System.out.println(r);
        }

        public void mightLeak() {
        }
    }

    // Verify 'this' escape detection properly handles loop convergence
    public static class ThisEscapeLoop {

        public ThisEscapeLoop() {
            ThisEscapeLoop ref1 = this;
            ThisEscapeLoop ref2 = null;
            ThisEscapeLoop ref3 = null;
            ThisEscapeLoop ref4 = null;
            for (int i = 0; i < 100; i++) {
                ref4 = ref3;
                ref3 = ref2;
                ref2 = ref1;
                if (ref4 != null)
                    ref4.mightLeak();
            }
        }

        public void mightLeak() {
        }
    }

    // Verify 'this' escape detection handles leaks via outer 'this'
    public static class ThisEscapeOuterThis {

        public ThisEscapeOuterThis() {
            new InnerClass();
        }

        public void mightLeak() {
        }

        public class InnerClass {

            InnerClass() {
                ThisEscapeOuterThis.this.mightLeak();
            }
        }

        // No leak here because class 'Local' cannot be externally extended
        public static void method1() {
            class Local {
                Local() {
                    this.wontLeak();
                }
                void wontLeak() {
                }
            }
        }
    }

    // Verify 'this' escape detection handles leaks via passing 'this' as a parameter
    public static class ThisEscapeParameter {

        public ThisEscapeParameter() {
            ThisEscapeParameter.method(this);
        }

        public static void method(Object obj) {
            obj.hashCode();
        }
    }

    // Verify 'this' escape detection properly handles leaks via recursive methods
    public static class ThisEscapeRecursion {

        public ThisEscapeRecursion() {
            this.noLeak(0);         // no leak here
            this.mightLeak();       // possible leak here
        }

        public final void noLeak(int depth) {
            if (depth < 10)
                this.noLeak(depth - 1);
        }

        public void mightLeak() {
        }
    }

    // Verify proper handling of 'this' escape warnings from method references
    public static class ThisEscapeReference {

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

    // Verify 'this' escape detection properly handles leaks via method return values
    public static class ThisEscapeReturnValue {

        public ThisEscapeReturnValue() {
            final Object rval = ThisEscapeReturnValue.method(this);
            ((ThisEscapeReturnValue)rval).mightLeak();
        }

        public static Object method(Object obj) {
            return obj;
        }

        public void mightLeak() {
        }
    }

    // Verify 'this' escape detection from a thrown 'this'
    public static class ThisEscapeThrown extends RuntimeException {

        public ThisEscapeThrown(Object obj) {
            if (obj == null)
                throw this;
        }
    }

    // Verify proper 'this' escape interpretation of unqualified non-static method invocations
    public static class ThisEscapeUnqualified {

        // This class has a leak
        public static class Example1 {

            public Example1() {
                new Inner();
            }

            public final class Inner {
                public Inner() {
                    mightLeak();    // refers to Example1.mightLeak()
                }
            }

            public void mightLeak() {
            }
        }

        // This class does NOT have a leak
        public static class Example2 {

            public Example2() {
                new Inner();
            }

            public final class Inner {
                public Inner() {
                    mightLeak();    // refers to Inner.mightLeak()
                }

                public void mightLeak() {
                }
            }

            public void mightLeak() {
            }
        }
    }

    // Verify 'this' escape detection handles leaks via switch expression yields
    public static class ThisEscapeYield {

        public ThisEscapeYield(int x) {
            ThisEscapeYield y = switch (x) {
                case 3:
                    if (x > 17)
                        yield this;
                    else
                        yield null;
                default:
                    yield null;
            };
            if (y != null)
                y.mightLeak();
        }

        public void mightLeak() {
        }
    }

    // Verify 'this' escape warnings can be properly suppressed on constructors
    public static class ThisEscapeSuppressCtor {

        private final int x = this.mightLeak();

        @SuppressWarnings("this-escape")
        public ThisEscapeSuppressCtor() {
            this.mightLeak();
        }

        public int mightLeak() {
            return 0;
        }
    }

    // Verify 'this' escape warnings can be properly suppressed on fields
    public static class ThisEscapeSuppressField {

        @SuppressWarnings("this-escape")
        private final int x = this.mightLeak();

        public ThisEscapeSuppressField() {
            this.mightLeak();
        }

        public int mightLeak() {
            return 0;
        }
    }

    // Verify 'this' escape warnings can be properly suppressed on classes
    public static class ThisEscapeSuppressClass {

        @SuppressWarnings("this-escape")
        private final int x = this.mightLeak();

        @SuppressWarnings("this-escape")
        public ThisEscapeSuppressClass() {
            this.mightLeak();
        }

        public int mightLeak() {
            return 0;
        }
    }

    // Verify 'this' escape detection doesn't generate certain false positives
    public static class ThisEscapeNoEscapes {

        public ThisEscapeNoEscapes() {
            this.noLeak1();                             // invoked method is private
            this.noLeak2();                             // invoked method is final
            ThisEscapeNoEscapes.noLeak3();              // invoked method is static
            this.noLeak4(this);                         // parameter is 'this' but it's not leaked
            this.noLeak5(new ThisEscapeNoEscapes(0));   // parameter is not 'this', so no leak
            this.noLeak6(null, this, null);             // method leaks 1st and 3rd parameters only
            this.noLeak7();                             // method does complicated stuff but doesn't leak
            Runnable r1 = () -> {                       // lambda does not leak 'this'
                if (System.out == System.err)
                    throw new RuntimeException();
            };
            System.out.println(r1);                     // lambda does not leak 'this'
            Runnable r2 = () -> {                       // lambda leaks 'this' but is never used
                this.mightLeak1();
            };
            Runnable r3 = this::mightLeak1;             // reference leaks 'this' but is never used
        }

        public ThisEscapeNoEscapes(int x) {
        }

        public void mightLeak1() {
        }

        private void noLeak1() {
        }

        public final void noLeak2() {
        }

        public static void noLeak3() {
        }

        public static void noLeak4(ThisEscapeNoEscapes param) {
            param.noLeak1();
            param.noLeak2();
        }

        public final void noLeak5(ThisEscapeNoEscapes param) {
            param.mightLeak1();
        }

        public final void noLeak6(ThisEscapeNoEscapes param1,
            ThisEscapeNoEscapes param2, ThisEscapeNoEscapes param3) {
            if (param1 != null)
                param1.mightLeak1();
            if (param2 != null)
                param2.noLeak2();
            if (param3 != null)
                param3.mightLeak1();
        }

        public final void noLeak7() {
            ((ThisEscapeNoEscapes)(Object)this).noLeak2();
            final ThisEscapeNoEscapes obj1 = switch (new Object().hashCode()) {
                case 1, 2, 3 -> null;
                default -> new ThisEscapeNoEscapes(0);
            };
            obj1.mightLeak1();
        }

    // PrivateClass

        private static class PrivateClass {

            PrivateClass() {
                this.cantLeak();                    // method is inside a private class
            }

            public void cantLeak() {
            }
        }

    // FinalClass

        public static final class FinalClass extends ThisEscapeNoEscapes {

            public FinalClass() {
                this.mightLeak1();                  // class and therefore method is final
            }
        }

        public static void main(String[] args) {
            new ThisEscapeNoEscapes();
        }
    }

    // Verify 'this' escape detection doesn't warn for sealed classes with local permits
    public static sealed class ThisEscapeSealed permits ThisEscapeSealed.Sub1, ThisEscapeSealed.Sub2 {

        public ThisEscapeSealed() {
            this.mightLeak();
        }

        public void mightLeak() {
        }

        public static final class Sub1 extends ThisEscapeSealed {
        }

        public static final class Sub2 extends ThisEscapeSealed {
        }
    }

    // Verify no assertion error occurs (JDK-8317336)
    public static class ThisEscapeAssertionError {
        public ThisEscapeAssertionError() {
            System.out.println((Supplier<Object>)() -> this);
        }
    }

    // Verify no assertion error occurs (JDK-8317336)
    public static class ThisEscapeAssertionError2 {
        public ThisEscapeAssertionError2() {
            ThisEscapeAssertionError2[] array = new ThisEscapeAssertionError2[] { this };
            for (Object obj : array)
                ;
        }
    }

    // Verify no infinite recursion loop occurs (JDK-8317818)
    public static class ThisEscapeRecursionExplosion {
        private Object obj;
        public ThisEscapeRecursionExplosion() {
            getObject();
        }
        private Object getObject() {
            if (this.obj == null) {
                this.obj = new Object();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
                getObject().hashCode();
            }
            return this.obj;
        }
    }
}
