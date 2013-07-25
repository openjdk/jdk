/**
 * @test /nodynamiccopyright/
 * @bug 8020586
 * @summary Warnings in the imports section should be attributed to the correct source file
 * @clean Auxiliary ImplicitCompilation
 * @compile/ref=ImplicitCompilation.out -XDrawDiagnostics -Xlint:deprecation -sourcepath . ImplicitCompilation.java
 * @clean Auxiliary ImplicitCompilation
 * @compile/ref=ExplicitCompilation.out -XDrawDiagnostics -Xlint:deprecation ImplicitCompilation.java Auxiliary.java
 */

public class ImplicitCompilation {
    private Auxiliary a;
}
