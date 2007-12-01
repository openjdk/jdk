/* @test  /nodynamiccopyright/
   @bug 4087127 4785453
   @author dps
   @summary method: instance access through types is not allowed

   @run shell NonStaticFieldExpr2.sh
*/

class NonStaticFieldExpr2 {

  public int x;

  void foo () {
     int z = NonStaticFieldExpr2.x;     // SHOULD BE ERROR
  }
}
