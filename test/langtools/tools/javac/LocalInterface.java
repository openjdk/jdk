/**
 * @test  /nodynamiccopyright/
 * @bug 8242478
 * @summary test for local interfaces
 * @compile/fail/ref=LocalInterface.out -XDrawDiagnostics LocalInterface.java
 * @compile --enable-preview -source ${jdk.version} LocalInterface.java
 */
class LocalInterface {
    void m() {
        interface I {}
    }
}

