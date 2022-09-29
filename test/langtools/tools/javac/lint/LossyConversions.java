/*
 * @test /nodynamiccopyright/
 * @bug 8244681
 * @summary Test for -Xlint:lossy-conversions
 *
 * @compile/fail/ref=LossyConversions.out -XDrawDiagnostics -Xmaxwarns 200 -Xlint:lossy-conversions -Werror LossyConversions.java
 */

public class LossyConversions {

    public void lossyConversions() {
        byte a = 0;
        a += 1.0; a -= 2.0; a *= 3.0; a /= 4.0;
        a += 11; a -= 12; a *= 13; a /= 14; //no warnings - within range
        a |= 15; a &= 16; a ^= 17; a %= 18; //no warnings - within range
        a += 21l; a -= 22l; a *= 23l; a /= 24l;
        a |= 25l; a &= 26l; a ^= 27l; a %= 28l;
        a += 1001; a -= 1002; a *= 1003; a /= 1004;
        a |= 1005; a &= 1006; a ^= 1007; a %= 1008;
        a += 100_001; a -= 100_002; a *= 100_003; a /= 100_004;
        a |= 100_005; a &= 100_006; a ^= 100_007; a %= 100_008;
        a += 10_000_000_001l; a -= 10_000_000_002l; a *= 10_000_000_003l; a /= 10_000_000_004l;
        a |= 10_000_000_005l; a &= 10_000_000_006l; a ^= 10_000_000_007l; a %= 10_000_000_008l;

        short b = 0;
        b += 1.0; b -= 2.0; b *= 3.0; b /= 4.0;
        b += 11; b -= 12; b *= 13; b /= 14; //no warnings - within range
        b |= 15; b &= 16; b ^= 17; b %= 18; //no warnings - within range
        b += 21l; b -= 22l; b *= 23l; b /= 24l;
        b |= 25l; b &= 26l; b ^= 27l; b %= 28l;
        b += 1001; b -= 1002; b *= 1003; b /= 1004; //no warnings - within range
        b |= 1005; b &= 1006; b ^= 1007; b %= 1008; //no warnings - within range
        b += 100_001; b -= 100_002; b *= 100_003; b /= 100_004;
        b |= 100_005; b &= 100_006; b ^= 100_007; b %= 100_008;
        b += 10_000_000_001l; b -= 10_000_000_002l; b *= 10_000_000_003l; b /= 10_000_000_004l;
        b |= 10_000_000_005l; b &= 10_000_000_006l; b ^= 10_000_000_007l; b %= 10_000_000_008l;

        int c = 0;
        c += 1.0; c -= 2.0; c *= 3.0; c /= 4.0;
        c += 11; c -= 12; c *= 13; c /= 14; //no warnings
        c |= 15; c &= 16; c ^= 17; c %= 18; //no warnings
        c += 21l; c -= 22l; c *= 23l; c /= 24l;
        c |= 25l; c &= 26l; c ^= 27l; c %= 28l;
        c += 1001; c -= 1002; c *= 1003; c /= 1004; //no warnings
        c |= 1005; c &= 1006; c ^= 1007; c %= 1008; //no warnings
        c += 100_001; c -= 100_002; c *= 100_003; c /= 100_004; //no warnings
        c |= 100_005; c &= 100_006; c ^= 100_007; c %= 100_008; //no warnings
        c += 10_000_000_001l; c -= 10_000_000_002l; c *= 10_000_000_003l; c /= 10_000_000_004l;
        c |= 10_000_000_005l; c &= 10_000_000_006l; c ^= 10_000_000_007l; c %= 10_000_000_008l;

        long d = 0;
        d += 1.0; d -= 2.0; d *= 3.0; d /= 4.0;
        d += 11; d -= 12; d *= 13; d /= 14; //no warnings
        d |= 15; d &= 16; d ^= 17; d %= 18; //no warnings
        d += 21l; d -= 22l; d *= 23l; d /= 24l; //no warnings
        d |= 25l; d &= 26l; d ^= 27l; d %= 28l; //no warnings
        d += 1001; d -= 1002; d *= 1003; d /= 1004; //no warnings
        d |= 1005; d &= 1006; d ^= 1007; d %= 1008; //no warnings
        d += 100_001; d -= 100_002; d *= 100_003; d /= 100_004; //no warnings
        d |= 100_005; d &= 100_006; d ^= 100_007; d %= 100_008; //no warnings
        d += 10_000_000_001l; d -= 10_000_000_002l; d *= 10_000_000_003l; d /= 10_000_000_004l; //no warnings
        d |= 10_000_000_005l; d &= 10_000_000_006l; d ^= 10_000_000_007l; d %= 10_000_000_008l; //no warnings

        float e = 0;
        e += 1.0; e -= 2.0; e *= 3.0; e /= 4.0;

        double f = 1.0;
        f += 1.0; f -= 2.0; f *= 3.0; f /= 4.0; //no warnings

        a += a; a -= a; a *= a; a /= a; //no warnings
        a |= a; a &= a; a ^= a; a %= a; //no warnings
        a += b; a -= b; a *= b; a /= b;
        a |= b; a &= b; a ^= b; a %= b;
        a += c; a -= c; a *= c; a /= c;
        a |= c; a &= c; a ^= c; a %= c;
        a += d; a -= d; a *= d; a /= d;
        a |= d; a &= d; a ^= d; a %= d;
        a += e; a -= e; a *= e; a /= e;
        a += f; a -= f; a *= f; a /= f;

        b += a; b -= a; b *= a; b /= a; //no warnings
        b |= a; b &= a; b ^= a; b %= a; //no warnings
        b += b; b -= b; b *= b; b /= b; //no warnings
        b |= b; b &= b; b ^= b; b %= b; //no warnings
        b += c; b -= c; b *= c; b /= c;
        b |= c; b &= c; b ^= c; b %= c;
        b += d; b -= d; b *= d; b /= d;
        b |= d; b &= d; b ^= d; b %= d;
        b += e; b -= e; b *= e; b /= e;
        b += f; b -= f; b *= f; b /= f;

        c += a; c -= a; c *= a; c /= a; //no warnings
        c |= a; c &= a; c ^= a; c %= a; //no warnings
        c += b; c -= b; c *= b; c /= b; //no warnings
        c |= b; c &= b; c ^= b; c %= b; //no warnings
        c += c; c -= c; c *= c; c /= c; //no warnings
        c |= c; c &= c; c ^= c; c %= c; //no warnings
        c += d; c -= d; c *= d; c /= d;
        c |= d; c &= d; c ^= d; c %= d;
        c += e; c -= e; c *= e; c /= e;
        c += f; c -= f; c *= f; c /= f;

        d += a; d -= a; d *= a; d /= a; //no warnings
        d |= a; d &= a; d ^= a; d %= a; //no warnings
        d += b; d -= b; d *= b; d /= b; //no warnings
        d |= b; d &= b; d ^= b; d %= b; //no warnings
        d += c; d -= c; d *= c; d /= c; //no warnings
        d |= c; d &= c; d ^= c; d %= c; //no warnings
        d += d; d -= d; d *= d; d /= d; //no warnings
        d |= d; d &= d; d ^= d; d %= d; //no warnings
        d += e; d -= e; d *= e; d /= e;
        d += f; d -= f; d *= f; d /= f;

        e += a; e -= a; e *= a; e /= a; //no warnings
        e += b; e -= b; e *= b; e /= b; //no warnings
        e += c; e -= c; e *= c; e /= c; //no warnings
        e += d; e -= d; e *= d; e /= d; //no warnings
        e += e; e -= e; e *= e; e /= e; //no warnings
        e += f; e -= f; e *= f; e /= f;

        f += a; f -= a; f *= a; f /= a; //no warnings
        f += b; f -= b; f *= b; f /= b; //no warnings
        f += c; f -= c; f *= c; f /= c; //no warnings
        f += d; f -= d; f *= d; f /= d; //no warnings
        f += e; f -= e; f *= e; f /= e; //no warnings
        f += f; f -= f; f *= f; f /= f; //no warnings
    }

    @SuppressWarnings("lossy-conversions")
    public void suppressedLossyConversions() {
        byte a = 0;
        a += 1.0; a -= 2.0; a *= 3.0; a /= 4.0;
        a += 21l; a -= 22l; a *= 23l; a /= 24l;
        a |= 25l; a &= 26l; a ^= 27l; a %= 28l;
        a += 1001; a -= 1002; a *= 1003; a /= 1004;
        a |= 1005; a &= 1006; a ^= 1007; a %= 1008;
        a += 100_001; a -= 100_002; a *= 100_003; a /= 100_004;
        a |= 100_005; a &= 100_006; a ^= 100_007; a %= 100_008;
        a += 10_000_000_001l; a -= 10_000_000_002l; a *= 10_000_000_003l; a /= 10_000_000_004l;
        a |= 10_000_000_005l; a &= 10_000_000_006l; a ^= 10_000_000_007l; a %= 10_000_000_008l;

        short b = 0;
        b += 1.0; b -= 2.0; b *= 3.0; b /= 4.0;
        b += 21l; b -= 22l; b *= 23l; b /= 24l;
        b |= 25l; b &= 26l; b ^= 27l; b %= 28l;
        b += 100_001; b -= 100_002; b *= 100_003; b /= 100_004;
        b |= 100_005; b &= 100_006; b ^= 100_007; b %= 100_008;
        b += 10_000_000_001l; b -= 10_000_000_002l; b *= 10_000_000_003l; b /= 10_000_000_004l;
        b |= 10_000_000_005l; b &= 10_000_000_006l; b ^= 10_000_000_007l; b %= 10_000_000_008l;

        int c = 0;
        c += 1.0; c -= 2.0; c *= 3.0; c /= 4.0;
        c += 21l; c -= 22l; c *= 23l; c /= 24l;
        c |= 25l; c &= 26l; c ^= 27l; c %= 28l;
        c += 10_000_000_001l; c -= 10_000_000_002l; c *= 10_000_000_003l; c /= 10_000_000_004l;
        c |= 10_000_000_005l; c &= 10_000_000_006l; c ^= 10_000_000_007l; c %= 10_000_000_008l;

        long d = 0;
        d += 1.0; d -= 2.0; d *= 3.0; d /= 4.0;

        float e = 0;
        e += 1.0; e -= 2.0; e *= 3.0; e /= 4.0;

        double f = 1.0;

        a += b; a -= b; a *= b; a /= b;
        a |= b; a &= b; a ^= b; a %= b;
        a += c; a -= c; a *= c; a /= c;
        a |= c; a &= c; a ^= c; a %= c;
        a += d; a -= d; a *= d; a /= d;
        a |= d; a &= d; a ^= d; a %= d;
        a += e; a -= e; a *= e; a /= e;
        a += f; a -= f; a *= f; a /= f;

        b += c; b -= c; b *= c; b /= c;
        b |= c; b &= c; b ^= c; b %= c;
        b += d; b -= d; b *= d; b /= d;
        b |= d; b &= d; b ^= d; b %= d;
        b += e; b -= e; b *= e; b /= e;
        b += f; b -= f; b *= f; b /= f;

        c += d; c -= d; c *= d; c /= d;
        c |= d; c &= d; c ^= d; c %= d;
        c += e; c -= e; c *= e; c /= e;
        c += f; c -= f; c *= f; c /= f;

        d += e; d -= e; d *= e; d /= e;
        d += f; d -= f; d *= f; d /= f;

        e += f; e -= f; e *= f; e /= f;
    }
}
