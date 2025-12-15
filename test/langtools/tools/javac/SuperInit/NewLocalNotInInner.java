/*
 * @test /nodynamiccopyright/
 * @bug 8373570
 * @summary Javac stack overflow on method-local class with nested record referring to enclosing type
 * @compile/fail/ref=NewLocalNotInInner.out -XDrawDiagnostics NewLocalNotInInner.java
 */
class NewLocalNotInInner {
    void m() {
        class Local {
            static class Foo {
                void m() {
                    new Local();
                }
            }
        }
    }

    void m_anon() {
        class Local {
            static class Foo {
                void m() {
                    new Local() { };
                }
            }
        }
    }

    void m_record() {
        class Local {
            record Foo() {
                void m() {
                    new Local();
                }
            }
        }
    }

    void sup() {
        class Local {
            static class Foo {
                void m() {
                    class Sub extends Local { };
                    new Sub();
                }
            }
        }
    }
}
