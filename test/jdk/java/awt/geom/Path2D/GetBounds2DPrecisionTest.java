
import java.awt.*;
import java.awt.geom.*;
import java.math.*;
import java.util.*;

/*
 * @test
 * @bug 8176501
 * @summary This is not a test. This is an exploratory task to empirically
 *          identify how to expand a rectangle to comfortably fit just outside
 *          (and never inside) a precise bounding box.
 */

public class GetBounds2DPrecisionTest {

    /**
     * This iterates through a million random CubicCurve2D and identifies the
     * marginMultiplier constant needed to consistently expand the bounding box
     * so it slightly exceeds a precise bounding box. The precise bounding box
     * follows the same algorithm, but it uses BigDecimals to have several more
     * digits of accuracy.
     * <p>
     * This currently suffers from a design flaw: the multiplier is applied to the
     * ulp of the x or y value in question. So the size of that ulp varies based
     * on how close x or y is to zero. This results in the multiplier being extremely
     * large to compensate.
     * </p>
     */
    public static void main(String[] args) {
        Random random = new Random(0);
        for(int a = 0; a < 1000000; a++) {
            test(a, random);
        }
        System.out.println("Final multiplier: " + marginMultiplier);
    }

    static double marginMultiplier = 1;

    private static void test(int trial, Random random) {
        double cx1 = random.nextDouble() * 10 - 5;
        double cy1 = random.nextDouble();
        double cx2 = random.nextDouble() * 10 - 5;
        double cy2 = random.nextDouble();

        CubicCurve2D curve = new CubicCurve2D.Double(0, 0, cx1, cy1, cx2, cy2, 0, 1);

        // The incoming data from a PathIterator is always represented by doubles, so that needs
        // to be where we start. (That is: if there's machine error already baked into those
        // doubles, then that's not something we can control for or accommodate.)

        // ... but everything that follows can, technically be calculated in really high precision:

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

        Result result = getResult(curve, leftX, rightX);
        if (result == Result.PASSING)
            return;

        System.out.println("Examining (trial #"+trial+"), "+result+", "+toString(curve));

        String leftStr = toUniformString(leftX);
        String rightStr = toUniformString(rightX);
        if (result == Result.FAIL_BOTH) {
            System.out.println("Exp:\t" + leftStr + "\t" + rightStr);
        } else if (result == Result.FAIL_LEFT) {
            System.out.println("Exp:\t" + leftStr);
        } else if (result == Result.FAIL_RIGHT) {
            System.out.println("Exp:\t" + rightStr);
        }

        double v = marginMultiplier;
        marginMultiplier = 0;
        Rectangle2D bounds = getBounds2D(curve.getPathIterator(null));
        marginMultiplier = v;
        String leftStr2 = toComparisonString(new BigDecimal(bounds.getMinX()), leftStr);
        String rightStr2 = toComparisonString(new BigDecimal(bounds.getMaxX()), rightStr);
        if (result == Result.FAIL_BOTH) {
            System.out.println("Orig:\t"+leftStr2+"\t"+rightStr2);
        } else if (result == Result.FAIL_LEFT) {
            System.out.println("Orig:\t"+leftStr2);
        } else if (result == Result.FAIL_RIGHT) {
            System.out.println("Orig:\t"+rightStr2);
        }

        bounds = getBounds2D(curve.getPathIterator(null));
        leftStr2 = toComparisonString(new BigDecimal(bounds.getMinX()), leftStr);
        rightStr2 = toComparisonString(new BigDecimal(bounds.getMaxX()), rightStr);
        if (result == Result.FAIL_BOTH) {
            System.out.println("Was:\t"+leftStr2+"\t"+rightStr2);
        } else if (result == Result.FAIL_LEFT) {
            System.out.println("Was:\t"+leftStr2);
        } else if (result == Result.FAIL_RIGHT) {
            System.out.println("Was:\t"+rightStr2);
        }

        double minMargin = marginMultiplier;
        double maxMargin = marginMultiplier * 1000;

        marginMultiplier = maxMargin;
        while(getResult(curve, leftX, rightX) != Result.PASSING) {
            minMargin = maxMargin;
            maxMargin = maxMargin * 1000;
            marginMultiplier = maxMargin;
        }

        int ctr = 0;
        while(true) {
            double newMargin = (maxMargin + minMargin) / 2;
            if (newMargin == maxMargin || newMargin == minMargin || ctr > 1000) {
                bounds = getBounds2D(curve.getPathIterator(null));
                leftStr2 = toComparisonString(new BigDecimal(bounds.getMinX()), leftStr);
                rightStr2 = toComparisonString(new BigDecimal(bounds.getMaxX()), rightStr);

                if (result == Result.FAIL_BOTH) {
                    System.out.println("Now:\t"+leftStr2+"\t"+rightStr2);
                } else if (result == Result.FAIL_LEFT) {
                    System.out.println("Now:\t"+leftStr2);
                } else if (result == Result.FAIL_RIGHT) {
                    System.out.println("Now:\t"+rightStr2);
                }

                System.out.println("New marginMultiplier = "+marginMultiplier);
                return;
            }
            marginMultiplier= newMargin;
            if (getResult(curve, leftX, rightX)==Result.PASSING) {
                maxMargin = marginMultiplier;
            } else {
                minMargin = marginMultiplier;
            }
            ctr++;
        }
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

    private static String toUniformString(BigDecimal decimal) {
        int DIGIT_COUNT = 40;
        String str = decimal.toPlainString();
        if (str.length() >= DIGIT_COUNT) {
            str = str.substring(0,DIGIT_COUNT-1)+"…";
        }
        while(str.length() < DIGIT_COUNT) {
            str = str + " ";
        }
        return str;
    }

    private static String toComparisonString(BigDecimal target, String compareAgainst) {
        String str = toUniformString(target);
        for(int a = 0; a<str.length(); a++) {
            char ch1 = str.charAt(a);
            char ch2 = compareAgainst.charAt(a);
            if (ch1 != ch2) {
                return str.substring(0,a) + createCircleDigit(ch1)+str.substring(a+1);
            }
        }
        return str;
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

    enum Result {
        PASSING, FAIL_LEFT, FAIL_RIGHT, FAIL_BOTH;
    }

    /**
     * Check to see if getBounds2D(..) is as big or larger than the precise bounds. If the left or right
     * edge comes in too small then this returns a failing Result.
     */
    private static Result getResult(CubicCurve2D curve, BigDecimal preciseLeft, BigDecimal preciseRight) {
        Rectangle2D r = getBounds2D(curve.getPathIterator(null));

        BigDecimal observedLeftX = new BigDecimal(r.getMinX());
        BigDecimal observedRightX = new BigDecimal(r.getMaxX());

        boolean badLeft = observedLeftX.compareTo(preciseLeft) > 0;
        boolean badRight = observedRightX.compareTo(preciseRight) < 0;
        if (badLeft && badRight)
            return Result.FAIL_BOTH;
        if (badLeft)
            return Result.FAIL_LEFT;
        if (badRight)
            return Result.FAIL_RIGHT;
        return Result.PASSING;
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
            // We already tested a for being 0 above
            res[roots++] = q.divide(a, RoundingMode.HALF_EVEN);
            if (!q.equals(BigDecimal.ZERO)) {
                res[roots++] = c.divide(q, RoundingMode.HALF_EVEN);
            }
        }
        return roots;
    }

    /**
     * This is an adaptation of the existing Path2D.getBounds2D(PathIterator) draft that
     * expands bounding box by <code>double margin = marginMultiplier * Math.ulp(v);</code>
     */
    public static Rectangle2D getBounds2D(final PathIterator pi) {
        // define x and y parametric coefficients where:
        // x(t) = x_coeff[0] + x_coeff[1] * t + x_coeff[2] * t^2 + x_coeff[3] * t^3
        final double[] coeff = new double[4];

        // define the derivative's coefficients
        final double[] deriv_coeff = new double[3];

        final double[] coords = new double[6];
        final double[] tExtrema = new double[2];
        boolean isDefined = false;
        double leftX = 0.0;
        double rightX = 0.0;
        double topY = 0.0;
        double bottomY = 0.0;
        double lastX = 0.0;
        double lastY = 0.0;

        for (; !pi.isDone(); pi.next()) {
            int type = pi.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    if (!isDefined) {
                        isDefined = true;
                        leftX = rightX = coords[0];
                        topY = bottomY = coords[1];
                    } else {
                        if (coords[0] < leftX) leftX = coords[0];
                        if (coords[0] > rightX) rightX = coords[0];
                        if (coords[1] < topY) topY = coords[1];
                        if (coords[1] > bottomY) bottomY = coords[1];
                    }
                    lastX = coords[0];
                    lastY = coords[1];
                    break;
                case PathIterator.SEG_LINETO:
                    if (coords[0] < leftX) leftX = coords[0];
                    if (coords[0] > rightX) rightX = coords[0];
                    if (coords[1] < topY) topY = coords[1];
                    if (coords[1] > bottomY) bottomY = coords[1];
                    lastX = coords[0];
                    lastY = coords[1];
                    break;
                case PathIterator.SEG_QUADTO:
                    if (coords[2] < leftX) leftX = coords[2];
                    if (coords[2] > rightX) rightX = coords[2];
                    if (coords[3] < topY) topY = coords[3];
                    if (coords[3] > bottomY) bottomY = coords[3];

                    if (coords[0] < leftX || coords[0] > rightX) {
                        final double dx21 = (coords[0] - lastX);
                        coeff[2] = (coords[2] - coords[0]) - dx21;  // A = P3 - P0 - 2 P2
                        coeff[1] = 2.0 * dx21;                      // B = 2 (P2 - P1)
                        coeff[0] = lastX;                           // C = P1

                        coeff[2] = lastX - 2.0 * coords[0] + coords[2];
                        coeff[1] = -2.0 * lastX + 2.0 * coords[0];
                        coeff[0] = lastX;

                        deriv_coeff[0] = coeff[1];
                        deriv_coeff[1] = 2.0 * coeff[2];

                        double t = -deriv_coeff[0] / deriv_coeff[1];
                        if (t > 0.0 && t < 1.0) {
                            double x = coeff[0] + t * (coeff[1] + t * coeff[2]);
                            double margin = marginMultiplier * Math.ulp(x);
                            if (x - margin < leftX) leftX = x - margin;
                            if (x + margin> rightX) rightX = x + margin;
                        }
                    }
                    if (coords[1] < topY || coords[1] > bottomY) {
                        final double dy21 = (coords[1] - lastY);
                        coeff[2] = (coords[3] - coords[1]) - dy21;
                        coeff[1] = 2.0 * dy21;
                        coeff[0] = lastY;

                        deriv_coeff[0] = coeff[1];
                        deriv_coeff[1] = 2.0 * coeff[2];

                        double t = -deriv_coeff[0] / deriv_coeff[1];
                        if (t > 0.0 && t < 1.0) {
                            double y = coeff[0] + t * (coeff[1] + t * coeff[2]);
                            double margin = marginMultiplier * Math.ulp(y);
                            if (y - margin < topY) topY = y - margin;
                            if (y + margin > bottomY) bottomY = y + margin;
                        }
                    }
                    lastX = coords[2];
                    lastY = coords[3];
                    break;
                case PathIterator.SEG_CUBICTO:
                    if (coords[4] < leftX) leftX = coords[4];
                    if (coords[4] > rightX) rightX = coords[4];
                    if (coords[5] < topY) topY = coords[5];
                    if (coords[5] > bottomY) bottomY = coords[5];

                    if (coords[0] < leftX || coords[0] > rightX || coords[2] < leftX || coords[2] > rightX) {
                        final double dx32 = 3.0 * (coords[2] - coords[0]);
                        final double dx21 = 3.0 * (coords[0] - lastX);
                        coeff[3] = (coords[4] - lastX) - dx32;  // A = P3 - P0 - 3 (P2 - P1) = (P3 - P0) + 3 (P1 - P2)
                        coeff[2] = (dx32 - dx21);               // B = 3 (P2 - P1) - 3(P1 - P0) = 3 (P2 + P0) - 6 P1
                        coeff[1] = dx21;                        // C = 3 (P1 - P0)
                        coeff[0] = lastX;                       // D = P0

                        deriv_coeff[0] = coeff[1];
                        deriv_coeff[1] = 2.0 * coeff[2];
                        deriv_coeff[2] = 3.0 * coeff[3];

                        int tExtremaCount = QuadCurve2D.solveQuadratic(deriv_coeff, tExtrema);
                        for (int i = 0; i < tExtremaCount; i++) {
                            double t = tExtrema[i];
                            if (t > 0.0 && t < 1.0) {
                                double x = coeff[0] + t * (coeff[1] + t * (coeff[2] + t * coeff[3]));
                                double margin = marginMultiplier * Math.ulp(x);
                                if (x - margin < leftX) leftX = x - margin;
                                if (x + margin > rightX) rightX = x + margin;
                            }
                        }
                    }
                    if (coords[1] < topY || coords[1] > bottomY || coords[3] < topY || coords[3] > bottomY) {
                        final double dy32 = 3.0 * (coords[3] - coords[1]);
                        final double dy21 = 3.0 * (coords[1] - lastY);
                        coeff[3] = (coords[5] - lastY) - dy32;
                        coeff[2] = (dy32 - dy21);
                        coeff[1] = dy21;
                        coeff[0] = lastY;

                        deriv_coeff[0] = coeff[1];
                        deriv_coeff[1] = 2.0 * coeff[2];
                        deriv_coeff[2] = 3.0 * coeff[3];

                        int tExtremaCount = QuadCurve2D.solveQuadratic(deriv_coeff, tExtrema);
                        for (int i = 0; i < tExtremaCount; i++) {
                            double t = tExtrema[i];
                            if (t > 0.0 && t < 1.0) {
                                double y = coeff[0] + t * (coeff[1] + t * (coeff[2] + t * coeff[3]));
                                double margin = marginMultiplier * Math.ulp(y);
                                if (y - margin < topY) topY = y - margin;
                                if (y + margin > bottomY) bottomY = y + margin;
                            }
                        }
                    }
                    lastX = coords[4];
                    lastY = coords[5];
                    break;
                case PathIterator.SEG_CLOSE:
                default:
                    continue;
            }
        }
        if (isDefined) {
            return new Rectangle2D.Double(leftX, topY, rightX - leftX, bottomY - topY);
        }

        // there's room to debate what should happen here, but historically we return a zeroed
        // out rectangle here. So for backwards compatibility let's keep doing that:
        return new Rectangle2D.Double();
    }
}