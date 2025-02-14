/*
 * @test /nodynamiccopyright/
 * @bug 8349848
 * @summary Test for -Xlint:proprietary
 *
 * @compile/fail/ref=Proprietary.1.out  -XDrawDiagnostics -Werror                     Proprietary.java
 * @compile/ref=Proprietary.2.out       -XDrawDiagnostics -Werror -Xlint:-proprietary Proprietary.java
 */

class Proprietary {
    sun.misc.Unsafe x;      // should get a warning here
    @SuppressWarnings("proprietary")
    sun.misc.Unsafe y;      // should not get a warning here
}
