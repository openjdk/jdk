/*
 * @test /nodynamiccopyright/
 * @bug 8015831
 * @compile/ref=ThisEscapeThrown.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeThrown.java
 * @summary Verify 'this' escape detection from a thrown 'this'
 */

public class ThisEscapeThrown extends RuntimeException {

    public ThisEscapeThrown(Object obj) {
        if (obj == null)
            throw this;
    }
}
