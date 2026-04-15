/*
 * @test /nodynamiccopyright/
 * @bug 8349847
 *
 * @compile                             -XDrawDiagnostics -Xlint:none                         WerrorLint.java
 * @compile                             -XDrawDiagnostics -Xlint:none     -Werror             WerrorLint.java
 * @compile                             -XDrawDiagnostics -Xlint:none     -Werror:empty       WerrorLint.java
 * @compile                             -XDrawDiagnostics -Xlint:none     -Werror:strictfp    WerrorLint.java
 * @compile/ref=WerrorLint.w2.out       -XDrawDiagnostics -Xlint:all                          WerrorLint.java
 * @compile/fail/ref=WerrorLint.e2.out  -XDrawDiagnostics -Xlint:all      -Werror             WerrorLint.java
 * @compile/fail/ref=WerrorLint.e2.out  -XDrawDiagnostics -Xlint:all      -Werror:empty       WerrorLint.java
 * @compile/fail/ref=WerrorLint.e2.out  -XDrawDiagnostics -Xlint:all      -Werror:strictfp    WerrorLint.java
 * @compile/ref=WerrorLint.w1.out       -XDrawDiagnostics                                     WerrorLint.java
 * @compile/fail/ref=WerrorLint.e1.out  -XDrawDiagnostics                 -Werror             WerrorLint.java
 * @compile/ref=WerrorLint.w1.out       -XDrawDiagnostics                 -Werror:empty       WerrorLint.java
 * @compile/fail/ref=WerrorLint.e1.out  -XDrawDiagnostics                 -Werror:strictfp    WerrorLint.java
 */

class WerrorLint {
    strictfp void m() {             // [strictfp] - this category is enabled by default
        if (hashCode() == 1) ;      // [empty]    - this category is disabled by default
    }
}
