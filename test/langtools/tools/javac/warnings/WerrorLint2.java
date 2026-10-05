/*
 * @test /nodynamiccopyright/
 * @bug 8380971
 *
 * @compile/fail/ref=WerrorLint2.fail1.out  -XDrawDiagnostics -Xlint:all                 -Werror:all        WerrorLint2.java
 * @compile/ref=WerrorLint2.warn1.out       -XDrawDiagnostics -Xlint:all                 -Werror:all,-empty WerrorLint2.java
 * @compile/ref=WerrorLint2.warn1.out       -XDrawDiagnostics -Xlint:all                 -Werror:-empty     WerrorLint2.java
 * @compile/fail/ref=WerrorLint2.fail1.out  -XDrawDiagnostics -Xlint:all -XDfind=diamond -Werror:all        WerrorLint2.java
 * @compile/fail/ref=WerrorLint2.fail2.out  -XDrawDiagnostics -Xlint:all -XDfind=diamond -Werror:all,-empty WerrorLint2.java
 * @compile/ref=WerrorLint2.warn2.out       -XDrawDiagnostics -Xlint:all -XDfind=diamond -Werror:-empty     WerrorLint2.java
 */

class WerrorLint2 {
    ThreadLocal<Void> t;
    void m() {
        if (hashCode() == 1) ;       // warning: [empty] empty statement after if
        t = new ThreadLocal<Void>(); // warning: Redundant type arguments in new expression (use diamond operator instead).
    }
}
