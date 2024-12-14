/*
 * @test /nodynamiccopyright/
 * @bug 8345944
 * @summary JEP 492: extending local class in a different static context should not be allowed
 * @compile/fail/ref=LocalFreeVarStaticSuper.out -XDrawDiagnostics LocalFreeVarStaticSuper.java
 */

class LocalFreeVarStaticSuper {

    // local class in method
    static void foo(Object there) {
        class Local {
            {
                there.hashCode();
            }

            static {
                class Sub1 extends Local { }
                class Sub2 extends Local {
                    Sub2() { }
                }
                class Sub3 extends Local {
                    Sub3() { super(); }
                }
            }

            static Runnable r = () -> {
                class Sub1 extends Local { }
                class Sub2 extends Local {
                    Sub2() { }
                }
                class Sub3 extends Local {
                    Sub3() { super(); }
                }
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
                class Sub1 extends Local { }
                class Sub2 extends Local {
                    Sub2() { }
                }
                class Sub3 extends Local {
                    Sub3() { super(); }
                }
            }

            static Runnable r = () -> {
                class Sub1 extends Local { }
                class Sub2 extends Local {
                    Sub2() { }
                }
                class Sub3 extends Local {
                    Sub3() { super(); }
                }
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
                    class Sub1 extends Local { }
                    class Sub2 extends Local {
                        Sub2() { }
                    }
                    class Sub3 extends Local {
                        Sub3() { super(); }
                    }
                }

                static Runnable r = () -> {
                    class Sub1 extends Local { }
                    class Sub2 extends Local {
                        Sub2() { }
                    }
                    class Sub3 extends Local {
                        Sub3() { super(); }
                    }
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
                class Sub1 extends Local { }
                class Sub2 extends Local {
                    Sub2() { }
                }
                class Sub3 extends Local {
                    Sub3() { super(); }
                }
            }

            static Runnable r = () -> {
                class Sub1 extends Local { }
                class Sub2 extends Local {
                    Sub2() { }
                }
                class Sub3 extends Local {
                    Sub3() { super(); }
                }
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
                class Sub1 extends Local { }
                class Sub2 extends Local {
                    Sub2() { }
                }
                class Sub3 extends Local {
                    Sub3() { super(); }
                }
            }

            static Runnable r = () -> {
                class Sub1 extends Local { }
                class Sub2 extends Local {
                    Sub2() { }
                }
                class Sub3 extends Local {
                    Sub3() { super(); }
                }
            };
        }
    }
}
