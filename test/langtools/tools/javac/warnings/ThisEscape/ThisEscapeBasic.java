/*
 * @test /nodynamiccopyright/
 * @bug 8015831
 * @compile/ref=ThisEscapeBasic.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeBasic.java
 * @summary Verify basic 'this' escape detection
 */

public class ThisEscapeBasic {

    public ThisEscapeBasic() {
        this.mightLeak();
    }

    public void mightLeak() {
    }
}
