/*
 * @test  /nodynamiccopyright/
 * @bug 4063740 6969184
 * @summary Interfaces may only be declared in top level classes.
 * @author turnidge
 *
 * @compile/fail/ref=InterfaceInInner.out -XDrawDiagnostics InterfaceInInner.java
 */
class InterfaceInInner {
    InterfaceInInner() {
        class foo {
            interface A {
            }
        }
    }
}
