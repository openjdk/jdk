/*
 * @test  /nodynamiccopyright/
 * @bug 4279339 6969184
 * @summary Verify that an anonymous class cannot contain a static method.
 * @author maddox
 *
 * @run compile/fail/ref=AnonStaticMember_2.out -XDrawDiagnostics AnonStaticMember_2.java
 */

class AnonStaticMember_2 {
    Object x = new Object() {
        static void test() {}
    };
}
