/*
 * @test /nodynamiccopyright/
 * @bug 8325805
 * @summary Permit non-superclass instance field assignments before this/super in constructors
 * @compile/fail/ref=DA_DUConstructors.out -XDrawDiagnostics DA_DUConstructors.java
 */

public class DA_DUConstructors {
    // identity
    class C1 {
        final int x;
        final int y = x + 1;
        C1() {
            x = 12;
            super();
        }
    }

    class C2 {
        final int x;
        C2() {
            this(x = 3); // error
        }
        C2(int i) {
            x = 4;
        }
    }

    class C3 {
        C3(int i) {}
    }
    class C4 extends C3 {
        final int x;
        C4() {
            super(x = 3); // ok
        }
    }

    class C5 {
        final int x;
        final int y = x + 1; // x is not DA
        C5() {
            x = 12; super();
        }
        C5(int i) {
            /* no prologue */
            x = i;
        }
    }
}
