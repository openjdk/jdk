/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary test invalid location of TypeUse
 * @author Mahmood Ali
 * @compile/fail/ref=Constructor.out -XDrawDiagnostics -source 1.7 Constructor.java
 */

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

class Constructor {
  @A Constructor() { }
}

@Target(ElementType.TYPE_USE)
@interface A { }
