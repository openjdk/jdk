/*
 * @test /nodynamiccopyright/
 * @bug 8015831
 * @compile/ref=ThisEscapeUnqualified.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeUnqualified.java
 * @summary Verify proper 'this' escape interpretation of unqualified non-static method invocations.
 */

public class ThisEscapeUnqualified {

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
