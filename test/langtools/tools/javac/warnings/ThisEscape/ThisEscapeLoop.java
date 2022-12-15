/*
 * @test /nodynamiccopyright/
 * @bug 8194743
 * @compile/ref=ThisEscapeLoop.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeLoop.java
 * @summary Verify 'this' escape detection properly handles loop convergence
 */

public class ThisEscapeLoop {

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
