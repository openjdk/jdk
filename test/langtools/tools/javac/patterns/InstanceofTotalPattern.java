/*
 * @test /nodynamiccopyright/
 * @summary Verify behavior of total patterns in instanceof
 * @compile/fail/ref=InstanceofTotalPattern-15.out --release 15 -XDrawDiagnostics InstanceofTotalPattern.java
 * @compile/fail/ref=InstanceofTotalPattern-16.out --release 16 -XDrawDiagnostics InstanceofTotalPattern.java
 * @compile/fail/ref=InstanceofTotalPattern-20.out --release 20 -XDrawDiagnostics InstanceofTotalPattern.java
 * @compile InstanceofTotalPattern.java
 * @run main InstanceofTotalPattern
 */

public class InstanceofTotalPattern {

    public static void main(String[] args) {
        new InstanceofTotalPattern().totalTest();
    }

    void totalTest() {
        String str = "";
        if (!(str instanceof String s1)) {
            throw new AssertionError();
        }
        str = null;
        if (str instanceof String s2) {
            throw new AssertionError();
        }
    }

}
