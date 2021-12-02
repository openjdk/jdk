/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=DeepNestingSuppression.out -XDrawDiagnostics -Xlint:serial DeepNestingSuppression.java
 */

import java.io.Serializable;

/*
 * Verify suppressing serial warnings works through several levels of
 * nested types.
 */
class DeepNestingSuppression {

    @SuppressWarnings("serial")
    static class SuppressedOuter {
        static class Intermediate {
            static class Inner implements Serializable {
                // warning for int rather than long svuid
                private static final int serialVersionUID = 42;
            }
        }
    }

    static class Outer {
        static class Intermediate {
            static class Inner implements Serializable {
                // warning for int rather than long svuid
                private static final int serialVersionUID = 42;
            }
        }
    }
}
