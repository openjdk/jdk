/*
 * @test /nodynamiccopyright/
 * @bug 8024809
 * @summary javac, some lambda programs are rejected by flow analysis
 * @compile SelfInitializerInLambdaTestb.java
 * @run main SelfInitializerInLambdaTestb
 *
 * NOTE: Bug 8024809 has been obsoleted by bug 8043179,
 * so this test is now expected to compile successfully.
 */

public class SelfInitializerInLambdaTestb {

    final Runnable r1;

    final Runnable r2 = ()-> System.out.println(r1);

    SelfInitializerInLambdaTestb() {
        r1 = ()->System.out.println(r1);
    }

    public static void main(String[] args) {
        new SelfInitializerInLambdaTestb();
    }
}
