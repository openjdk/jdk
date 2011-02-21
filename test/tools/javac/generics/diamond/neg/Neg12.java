/*
 * @test /nodynamiccopyright/
 * @bug 7020043
 *
 * @summary  Project Coin: diamond allowed on non-generic type
 * @author R&eacute;mi Forax
 * @compile/fail/ref=Neg12.out Neg12.java -XDrawDiagnostics
 *
 */

class DiamondRaw {
   public static void main(String[] args) {
     String s = new String<>("foo");
   }
}
