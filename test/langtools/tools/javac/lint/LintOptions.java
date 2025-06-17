/*
 * @test /nodynamiccopyright/
 * @bug 8359596
 * @summary Verify behavior when both "-Xlint:options" and "-Xlint:-options" are given
 * @compile/fail/ref=LintOptions.out -Werror -XDrawDiagnostics -source 21 -target 21                                LintOptions.java
 * @compile/fail/ref=LintOptions.out -Werror -XDrawDiagnostics -source 21 -target 21 -Xlint:options                 LintOptions.java
 * @compile                          -Werror -XDrawDiagnostics -source 21 -target 21                -Xlint:-options LintOptions.java
 * @compile                          -Werror -XDrawDiagnostics -source 21 -target 21 -Xlint:options -Xlint:-options LintOptions.java
 */
class LintOptions {
}
