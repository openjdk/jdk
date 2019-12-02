/*
 * @test /nodynamiccopyright/
 * @bug 8231827
 * @summary Basic pattern bindings scope test
 * @compile/fail/ref=DuplicateBindingTest.out -XDrawDiagnostics --enable-preview -source ${jdk.version} DuplicateBindingTest.java
 */

public class DuplicateBindingTest {

    int f;

    public static void main(String[] args) {

        if (args != null) {
            int s;
            if (args[0] instanceof String s) { // NOT OK. Redef same scope.
            }
            if (args[0] instanceof String f) { // OK to redef field.
            }
        }
    }
}
