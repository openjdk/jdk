/*
 * @test  /nodynamiccopyright/
 * @bug 4063740 6969184
 * @summary Interfaces can also be declared in inner classes.
 * @author turnidge
 *
 * @compile InterfaceInInner.java
 */
class InterfaceInInner {
    InterfaceInInner() {
        class foo {
            interface A {
            }
        }
    }
}
