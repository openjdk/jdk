/*
 * @test /nodynamiccopyright/
 * @bug 8194743
 * @compile/ref=ThisEscapeRecursion.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeRecursion.java
 * @summary Verify 'this' escape detection properly handles leaks via recursive methods
 */

public class ThisEscapeRecursion {

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
