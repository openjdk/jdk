
import java.awt.*;
import java.awt.geom.*;
import java.math.*;
import java.util.*;

/*
 * @test
 * @bug 8176501
 * @summary This tests thousands of shapes and makes sure a high-precision bounding box fits inside the
 * results of Path2D.getBounds(PathIterator)
 * @run main GetBounds2DPrecisionTest
 */
public class GetBounds2DPrecisionTest {

    public static void main(String[] args) {
        String msg1 = testSmallCubics();
        if (msg1 != null) {
            System.out.println("testSmallCubics: "+msg1);
        } else {
            System.out.println("testSmallCubics: passed");
        }

        if (msg1 != null)
            throw new RuntimeException("One or more tests failed; see System.out output for details.");
    }

    /**
     * @return a String describing the failure, or null if this test passed.
     */
    private static String testSmallCubics() {
        int failureCtr = 0;
        for(int a = 0; a < 1000; a++) {
            CubicCurve2D cubicCurve2D = createSmallCubic(a);
            if (!test(a, cubicCurve2D, getHorizontalEdges(cubicCurve2D)))
                failureCtr++;
        }
        if (failureCtr > 0)
            return failureCtr+" tests failed; see System.out for details";
        return null;
    }

    private static CubicCurve2D createSmallCubic(int trial) {
        Random random = new Random(trial);

        double cx1 = random.nextDouble() * 10 - 5;
        double cy1 = random.nextDouble();
        double cx2 = random.nextDouble() * 10 - 5;
        double cy2 = random.nextDouble();

        return new CubicCurve2D.Double(0, 0, cx1, cy1, cx2, cy2, 0, 1);
    }

    /**
     * This returns true if the shape's getBounds2D() method returns a bounding box whose
     * left & right edges matches or exceeds the horizontalEdges arguments.
     */
    private static boolean test(int trial, Shape shape, BigDecimal[] horizontalEdges) {
        Rectangle2D bounds_doublePrecision = shape.getBounds2D();

        Rectangle2D bounds_bigDecimalPrecision = new Rectangle2D.Double(
                horizontalEdges[0].doubleValue(),
                bounds_doublePrecision.getY(),
                horizontalEdges[1].subtract(horizontalEdges[0]).doubleValue(),
                bounds_doublePrecision.getHeight() );

        boolean pass = true;
        if (bounds_doublePrecision.getMinX() > bounds_bigDecimalPrecision.getMinX()) {
            pass = false;
            String x1a = toUniformString(bounds_bigDecimalPrecision.getX());
            String x1b = toComparisonString(x1a, toUniformString(bounds_doublePrecision.getX()));
            System.out.println("Left expected:\t"+x1a);
            System.out.println("Left observed:\t"+x1b);
        }

        if (bounds_doublePrecision.getMaxX() < bounds_bigDecimalPrecision.getMaxX()) {
            pass = false;
            String x2a = toUniformString(bounds_bigDecimalPrecision.getMaxX());
            String x2b = toComparisonString(x2a, toUniformString(bounds_doublePrecision.getMaxX()));
            System.out.println("Right expected:\t"+x2a);
            System.out.println("Right observed:\t"+x2b);
        }
        if (!pass)
            System.out.println("\ttrial "+trial +" failed ("+toString(shape)+")");
        return pass;
    }

    /**
     * Return the left and right edges in high precision
     */
    private static BigDecimal[] getHorizontalEdges(CubicCurve2D curve) {
        double cx1 = curve.getCtrlX1();
        double cx2 = curve.getCtrlX2();

        BigDecimal[] coeff = new BigDecimal[4];
        BigDecimal[] deriv_coeff = new BigDecimal[3];
        BigDecimal[] tExtrema = new BigDecimal[2];

//        coeff[3] = -lastX + 3.0 * coords[0] - 3.0 * coords[2] + coords[4];
//        coeff[2] = 3.0 * lastX - 6.0 * coords[0] + 3.0 * coords[2];
//        coeff[1] = -3.0 * lastX + 3.0 * coords[0];
//        coeff[0] = lastX;

        coeff[3] = new BigDecimal(3).multiply(new BigDecimal(cx1)).add( new BigDecimal(-3).multiply(new BigDecimal(cx2)) );
        coeff[2] = new BigDecimal(-6).multiply(new BigDecimal(cx1)).add(new BigDecimal(3).multiply(new BigDecimal(cx2)));
        coeff[1] = new BigDecimal(3).multiply(new BigDecimal(cx1));
        coeff[0] = BigDecimal.ZERO;

        deriv_coeff[0] = coeff[1];
        deriv_coeff[1] = new BigDecimal(2.0).multiply( coeff[2] );
        deriv_coeff[2] = new BigDecimal(3.0).multiply( coeff[3] );

        int tExtremaCount = solveQuadratic(deriv_coeff, tExtrema);

        BigDecimal leftX = BigDecimal.ZERO;
        BigDecimal rightX = BigDecimal.ZERO;

        for (int i = 0; i < tExtremaCount; i++) {
            BigDecimal t = tExtrema[i];
            if (t.compareTo( BigDecimal.ZERO ) > 0 && t.compareTo(BigDecimal.ONE) < 0) {
                BigDecimal x = coeff[0].add( t.multiply(coeff[1].add(t.multiply(coeff[2].add(t.multiply(coeff[3]))))) );
                if (x.compareTo(leftX) < 0) leftX = x;
                if (x.compareTo(rightX) > 0) rightX = x;
            }
        }
        return new BigDecimal[] { leftX, rightX };
    }

    /**
     * Return the left and right edges in high precision
     */
    private static BigDecimal[] getHorizontalEdges(QuadCurve2D curve) {
        double cx = curve.getCtrlX();

        BigDecimal[] coeff = new BigDecimal[3];
        BigDecimal[] deriv_coeff = new BigDecimal[2];

        BigDecimal dx21 = new BigDecimal(cx).subtract(new BigDecimal(curve.getX1()));
        coeff[2] = new BigDecimal(curve.getX2()).subtract(new BigDecimal(cx)).subtract(dx21);  // A = P3 - P0 - 2 P2
        coeff[1] = new BigDecimal(2.0).multiply(dx21);                      // B = 2 (P2 - P1)
        coeff[0] = new BigDecimal(curve.getX1());                           // C = P1

        deriv_coeff[0] = coeff[1];
        deriv_coeff[1] = new BigDecimal(2.0).multiply( coeff[2] );

        BigDecimal leftX = BigDecimal.ZERO;
        BigDecimal rightX = BigDecimal.ZERO;

        if (!deriv_coeff[1].equals(BigDecimal.ZERO)) {
            BigDecimal t = deriv_coeff[0].negate().divide(deriv_coeff[1], RoundingMode.HALF_EVEN);

            if (t.compareTo( BigDecimal.ZERO ) > 0 && t.compareTo(BigDecimal.ONE) < 0) {
                BigDecimal x = coeff[0].add( t.multiply(coeff[1].add(t.multiply(coeff[2]))) );
                if (x.compareTo(leftX) < 0) leftX = x;
                if (x.compareTo(rightX) > 0) rightX = x;
            }
        }

        return new BigDecimal[] { leftX, rightX };
    }

    /**
     * Convert a shape into SVG-ish notation for debugging/readability.
     */
    private static String toString(Shape shape) {
        StringBuilder returnValue = new StringBuilder();
        PathIterator pi = shape.getPathIterator(null);
        double[] coords = new double[6];
        while(!pi.isDone()) {
            int k = pi.currentSegment(coords);
            if (k == PathIterator.SEG_MOVETO) {
                returnValue.append("m "+coords[0]+" "+coords[1]+" ");
            } else if (k == PathIterator.SEG_LINETO) {
                returnValue.append("l "+coords[0]+" "+coords[1]+" ");
            } else if (k == PathIterator.SEG_QUADTO) {
                returnValue.append("q "+coords[0]+" "+coords[1]+" "+coords[2]+" "+coords[3]+" ");
            } else if (k == PathIterator.SEG_CUBICTO) {
                returnValue.append("c "+coords[0]+" "+coords[1]+" "+coords[2]+" "+coords[3]+" "+coords[4]+" "+coords[5]+" ");
            } else if (k == PathIterator.SEG_CLOSE) {
                returnValue.append("z");
            }
            pi.next();
        }
        return returnValue.toString();
    }

    private static String toUniformString(double value) {
        BigDecimal decimal = new BigDecimal(value);
        int DIGIT_COUNT = 40;
        String str = decimal.toPlainString();
        if (str.length() >= DIGIT_COUNT) {
            str = str.substring(0,DIGIT_COUNT-1)+"...";
        }
        while(str.length() < DIGIT_COUNT) {
            str = str + " ";
        }
        return str;
    }

    private static String toComparisonString(String target, String observed) {
        for(int a = 0; a<target.length(); a++) {
            char ch1 = target.charAt(a);
            char ch2 = observed.charAt(a);
            if (ch1 != ch2) {
                return observed.substring(0,a) + createCircleDigit(ch2)+observed.substring(a+1);
            }
        }
        return observed;
    }

    /**
     * Convert a digit 0-9 into a "circle digit". Really we just want any unobtrusive way to
     * highlight a character.
     */
    private static char createCircleDigit(char ch) {
        if (ch >= '1' && ch <='9')
            return (char)( ch - '1' + '\u2460');
        if (ch == '0')
            return '\u24ea';
        return ch;
    }

    private static int solveQuadratic(BigDecimal[] eqn, BigDecimal[] res) {
        BigDecimal a = eqn[2];
        BigDecimal b = eqn[1];
        BigDecimal c = eqn[0];
        int roots = 0;
        if (a.equals(BigDecimal.ZERO)) {
            // The quadratic parabola has degenerated to a line.
            if (b.equals(BigDecimal.ZERO)) {
                // The line has degenerated to a constant.
                return -1;
            }
            res[roots++] = c.negate().divide(b);
        } else {
            // From Numerical Recipes, 5.6, Quadratic and Cubic Equations
            BigDecimal d = b.multiply(b).add(new BigDecimal(-4.0).multiply(a).multiply(c));
            if (d.compareTo(BigDecimal.ZERO) < 0) {
                // If d < 0.0, then there are no roots
                return 0;
            }
            d = d.sqrt(MathContext.DECIMAL128);
            // For accuracy, calculate one root using:
            //     (-b +/- d) / 2a
            // and the other using:
            //     2c / (-b +/- d)
            // Choose the sign of the +/- so that b+d gets larger in magnitude
            if (b.compareTo(BigDecimal.ZERO) < 0) {
                d = d.negate();
            }
            BigDecimal q = b.add(d).divide(new BigDecimal(-2.0));
            q = q.setScale(40, RoundingMode.HALF_EVEN);

            // We already tested a for being 0 above
            res[roots++] = q.divide(a, RoundingMode.HALF_EVEN);
            if (!q.equals(BigDecimal.ZERO)) {
                c = c.setScale(40, RoundingMode.HALF_EVEN);
                res[roots++] = c.divide(q, RoundingMode.HALF_EVEN);
            }
        }
        return roots;
    }
}
