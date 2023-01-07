/*
 * @test /nodynamiccopyright/
 * @bug 8015831
 * @compile/ref=ThisEscapeFields.out -Xlint:this-escape -XDrawDiagnostics ThisEscapeFields.java
 * @summary Verify 'this' escape detection in field initializers
 */

public class ThisEscapeFields {

    private final int field1 = this.mightLeak1();

    private final int field2 = this.mightLeak2();

    public int mightLeak1() {
        return 123;
    }

    public int mightLeak2() {
        return 456;
    }
}
