/*
 * @test  /nodynamiccopyright/
 * @bug 4279339 6969184
 * @summary Verify that an anonymous class can contain a static method.
 * @author maddox
 *
 * @compile AnonStaticMember_2.java
 */

class AnonStaticMember_2 {
    Object x = new Object() {
        static void test() {}
    };
}
