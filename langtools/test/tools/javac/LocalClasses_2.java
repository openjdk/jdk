/*
 * @test  /nodynamiccopyright/
 * @bug 4074421 4277278 4785453
 * @summary Verify that a local class cannot be redefined within its scope.
 * @author William Maddox (maddox)
 *
 * @run shell LocalClasses_2.sh
 */

class LocalClasses_2 {

    void foo() {
        class Local { }
        {
            class Local { }                     // ERROR
        }
    }

    void bar() {

        class Local { }

        class Baz {
            void quux() {
                class Local { }                 // OK
            }
        }

        class Quux {
            void baz() {
                class Random {
                    void quem() {
                        class Local { }         // OK
                    }
                }
            }
        }
    }
}
