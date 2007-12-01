/* @test  /nodynamiccopyright/
   @bug 4087127 4785453
   @author dps
   @summary field: instance access through types is not allowed

   @run shell NonStaticFieldExpr1.sh
*/
class NonStaticFieldExpr1 {
  public int x;
  int y = NonStaticFieldExpr1.x;                // SHOULD BE ERROR
}
