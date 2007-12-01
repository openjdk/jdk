/* @test  /nodynamiccopyright/
   @bug 4087127 4785453
   @author dps
   @summary class: instance access through types is not allowed

   @run shell NonStaticFieldExpr3.sh
*/

class NonStaticFieldExpr3 {
  public int x;
}

class Subclass extends NonStaticFieldExpr3 {
  int a = NonStaticFieldExpr3.x;      // SHOULD BE ERROR
}
