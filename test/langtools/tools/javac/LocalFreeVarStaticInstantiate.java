/*
 * @test /nodynamiccopyright/
 * @bug 8322882
 * @summary Disallow attempts to access a free variable proxy field from a static method
 * @compile/fail/ref=LocalFreeVarStaticInstantiate.out -XDrawDiagnostics LocalFreeVarStaticInstantiate.java
 */

class LocalFreeVarStaticInstantiate {

    static void foo(Object there) {
        class Local {
            {
                there.hashCode();
            }
            static {
                new Local();    // can't get there from here
            }
        }
    }
}
