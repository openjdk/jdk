/*
 * @test /nodynamiccopyright/
 * @bug 8194743
 * @compile/ref=ThisEscapeOuterThis.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeOuterThis.java
 * @summary Verify 'this' escape detection handles leaks via outer 'this'
 */

public class ThisEscapeOuterThis {

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
}
