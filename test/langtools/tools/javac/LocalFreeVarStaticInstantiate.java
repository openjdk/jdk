/*
 * @test /nodynamiccopyright/
 * @bug 8322882
 * @summary Disallow attempts to access a free variable proxy field from a static method
 * @compile/fail/ref=LocalFreeVarStaticInstantiate.out -XDrawDiagnostics LocalFreeVarStaticInstantiate.java
 */

class LocalFreeVarStaticInstantiate {

    // local class in method
    static void foo(Object there) {
        class Local {
            {
                there.hashCode();
            }

            static {
                new Local();    // can't get there from here
            }

            static Runnable r = () -> {
                new Local();    // can't get there from here
            };
        }
    }

    // local class in lambda
    static Runnable foo = () -> {
        Object there = "";
        class Local {
            {
                there.hashCode();
            }

            static {
                new Local();    // can't get there from here
            }

            static Runnable r = () -> {
                new Local();    // can't get there from here
            };
        }
    };
}
