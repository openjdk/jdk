/*
 * @test  /nodynamiccopyright/
 * @bug 8328649
 * @summary Verify local classes in constructor prologues don't have enclosing instances
 * @compile/fail/ref=LocalClassCtorPrologue.out -XDrawDiagnostics LocalClassCtorPrologue.java
 * @enablePreview
 */

class LocalClassCtorPrologue {

    int x;

    LocalClassCtorPrologue() {
        class Local {
            {
                x++;                // this should fail
            }
        }
        super();
    }

    public class Inner {
        public Inner() {
            class Local {
                {
                    x++;            // this should work
                }
            };
            super();
        }
    }
}
