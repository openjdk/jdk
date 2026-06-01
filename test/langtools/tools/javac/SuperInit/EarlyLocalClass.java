/*
 * @test /nodynamiccopyright/
 * @bug 8325805
 * @library /tools/javac/lib
 * @summary Verify local class in early construction context has no outer instance
 * @modules jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 * @enablePreview
 * @compile/fail/ref=EarlyLocalClass.out -XDrawDiagnostics EarlyLocalClass.java
 * @build SuperCallRemover
 * @compile/fail/ref=EarlyLocalClassWarnings.out -Xlint:initialization -Werror -XDrawDiagnostics -processor SuperCallRemover EarlyLocalClass.java
 */
public class EarlyLocalClass {
    EarlyLocalClass() {
        class Local {
            void foo() {
                EarlyLocalClass.this.hashCode();    // this should FAIL
            }
        }
        new Local();                                // this is OK
        super();
        new Local();                                // this is OK
    }
}
