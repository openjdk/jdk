/**
 * @test  /nodynamiccopyright/
 * @bug 4222327 4785453
 * @summary Interface names for classes in the same scope should not
 * cause the compiler to crash.
 *
 * @run shell NameCollision.sh
 */

// The test fails if the compiler crashes.

public class NameCollision {
    class Runnable implements Runnable { } // ERROR
}
