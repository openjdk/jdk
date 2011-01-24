/* @test /nodynamiccopyright/
 * @bug 6999438
 * @summary remove support for exotic identifiers from JDK 7
 * @compile/fail/ref=T6999438.out -XDrawDiagnostics -source 7 T6999438.java
 */

class Test {
    int #"not supported";
}
