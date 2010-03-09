/*
 * @test
 * @bug 6918065
 * @summary Test for passing NaN as alpha
 *          should throw IllegalArgumentException
 */

import java.awt.*;

public class TestAlphaCompositeForNaN {
  public static void main(String[] args) {
    try {
      AlphaComposite a = AlphaComposite.getInstance(AlphaComposite.DST, Float.NaN);
      System.out.println("Failed");
      throw new RuntimeException(a + " failed to throw IllegalArgumentException for alpha = " + Float.NaN);
    }
    catch (IllegalArgumentException ie) {
      System.out.println("Passed");
      System.out.println("Caught " + ie);
    }
  }
}
