/*
 * @test /nodynamiccopyright/
 * @bug 8015831
 * @compile/ref=ThisEscapeComplex.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeComplex.java
 * @summary Verify 'this' escape detection can follow references through various Java code structures
 */

import java.util.function.Supplier;

public class ThisEscapeComplex {

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
