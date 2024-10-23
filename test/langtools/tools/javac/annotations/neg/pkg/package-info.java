/*
 * @test /nodynamiccopyright/
 * @bug 8028389
 * @summary javac should output a proper error message when given something
 * like new Object(){} as annotation argument.
 *
 * @compile AnonSubclassOnPkg.java
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 AnonSubclassOnPkg.java
 * @compile/fail/ref=package-info.out -XDrawDiagnostics package-info.java
 */

@AnonSubclassOnPkg(new Object(){})
package pkg;
