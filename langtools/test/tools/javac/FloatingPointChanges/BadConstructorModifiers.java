/*
 * @test  /nodynamiccopyright/
 * @bug 4153038 4785453
 * @summary strictfp may not be used with constructors
 * @author David Stoutamire (dps)
 *
 * @compile/fail/ref=BadConstructorModifiers.out -XDrawDiagnostics -XDstdout BadConstructorModifiers.java
 */

public class BadConstructorModifiers {

    strictfp BadConstructorModifiers (double abra) { }

}
