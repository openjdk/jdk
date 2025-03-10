/*
 * @test /nodynamiccopyright/
 * @bug 8325805
 * @summary Verify local class in early construction context has no outer instance
 * @compile/fail/ref=EarlyLocalClass.out -XDrawDiagnostics EarlyLocalClass.java
 * @enablePreview
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
