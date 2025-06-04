/**
 * @test  /nodynamiccopyright/
 * @bug 8242478 8246774
 * @summary test for local interfaces
 * @compile/fail/ref=LocalInterface.out -XDrawDiagnostics --release 15 LocalInterface.java
 * @compile LocalInterface.java
 */
class LocalInterface {
    void m() {
        interface I {}
    }
}

