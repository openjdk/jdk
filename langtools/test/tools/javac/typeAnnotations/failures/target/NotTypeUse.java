/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary test invalid location of TypeUse
 * @author Mahmood Ali
 * @compile/fail/ref=NotTypeUse.out -XDrawDiagnostics -source 1.7 NotTypeUse.java
 */

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

class VoidMethod {
  @A void test() { }
}

@Target(ElementType.TYPE)
@interface A { }
