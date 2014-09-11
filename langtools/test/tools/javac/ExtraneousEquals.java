/*
 * @test /nodynamiccopyright/
 * @bug 5019614
 * @summary variance prototype syntax leftover
 *
 * @compile/fail/ref=ExtraneousEquals.out -XDrawDiagnostics ExtraneousEquals.java
 */

public class ExtraneousEquals {
  int[] foo = new int[=] { 1, 2, 3 };
}
