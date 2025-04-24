/*
 * @test /nodynamiccopyright/
 * @bug 8322882 8345953
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

    // local class in switch
    static Object bar = switch (foo) {
        case Runnable r -> {
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
            yield r;
        }
    };

    // local class in instance init
    {
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
    }

    // local class in static init
    static {
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
    }
}
