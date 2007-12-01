/*
 * @test  /nodynamiccopyright/
 * @bug 4153038 4785453
 * @summary strictfp may not be used with constructors
 * @author David Stoutamire (dps)
 *
 * @run shell BadConstructorModifiers.sh
 */

public class BadConstructorModifiers {

    strictfp BadConstructorModifiers (double abra) { }

}
