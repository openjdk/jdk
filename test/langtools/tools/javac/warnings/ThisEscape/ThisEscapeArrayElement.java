/*
 * @test /nodynamiccopyright/
 * @bug 8015831
 * @compile/ref=ThisEscapeArrayElement.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeArrayElement.java
 * @summary Verify 'this' escape detection can follow references embedded as array elements
 */

public class ThisEscapeArrayElement {

    public ThisEscapeArrayElement() {
        final Object[][] array = new Object[][] { { this } };
        ((ThisEscapeArrayElement)array[0][0]).mightLeak();
    }

    public void mightLeak() {
    }
}
