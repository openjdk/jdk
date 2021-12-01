/**
 * @test /nodynamiccopyright/
 * @bug 8278078
 * @summary error: cannot reference super before supertype constructor has been called
 * @run compile EclosingSuperTest.java
 */
public class EnclosingSuperTest {
    class InnerClass extends Exception {
        InnerClass() {
            super(EnclosingSuperTest.super.toString());
        }
    }
}
