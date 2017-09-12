/*
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package jdk.nashorn.internal.runtime.regexp.joni;

final class MinMaxLen {
    int min; /* min byte length */
    int max; /* max byte length */

    MinMaxLen() {
    }

    MinMaxLen(final int min, final int max) {
        this.min = min;
        this.max = max;
    }

    /* 1000 / (min-max-dist + 1) */
    private static final short distValues[] = {
        1000,  500,  333,  250,  200,  167,  143,  125,  111,  100,
          91,   83,   77,   71,   67,   63,   59,   56,   53,   50,
          48,   45,   43,   42,   40,   38,   37,   36,   34,   33,
          32,   31,   30,   29,   29,   28,   27,   26,   26,   25,
          24,   24,   23,   23,   22,   22,   21,   21,   20,   20,
          20,   19,   19,   19,   18,   18,   18,   17,   17,   17,
          16,   16,   16,   16,   15,   15,   15,   15,   14,   14,
          14,   14,   14,   14,   13,   13,   13,   13,   13,   13,
          12,   12,   12,   12,   12,   12,   11,   11,   11,   11,
          11,   11,   11,   11,   11,   10,   10,   10,   10,   10
    };

    int distanceValue() {
        if (max == INFINITE_DISTANCE) {
            return 0;
        }
        final int d = max - min;
        /* return dist_vals[d] * 16 / (mm->min + 12); */
        return d < distValues.length ? distValues[d] : 1;
    }

    int compareDistanceValue(final MinMaxLen other, final int v1p, final int v2p) {
        int v1 = v1p, v2 = v2p;

        if (v2 <= 0) {
            return -1;
        }
        if (v1 <= 0) {
            return 1;
        }

        v1 *= distanceValue();
        v2 *= other.distanceValue();

        if (v2 > v1) {
            return 1;
        }
        if (v2 < v1) {
            return -1;
        }

        if (other.min < min) {
            return 1;
        }
        if (other.min > min) {
            return -1;
        }
        return 0;
    }

    boolean equal(final MinMaxLen other) {
        return min == other.min && max == other.max;
    }

    void set(final int min, final int max) {
        this.min = min;
        this.max = max;
    }

    void clear() {
        min = max = 0;
    }

    void copy(final MinMaxLen other) {
        min = other.min;
        max = other.max;
    }

    void add(final MinMaxLen other) {
        min = distanceAdd(min, other.min);
        max = distanceAdd(max, other.max);
    }

    void addLength(final int len) {
        min = distanceAdd(min, len);
        max = distanceAdd(max, len);
    }

    void altMerge(final MinMaxLen other) {
        if (min > other.min) {
            min = other.min;
        }
        if (max < other.max) {
            max = other.max;
        }
    }

    static final int INFINITE_DISTANCE = 0x7FFFFFFF;
    static int distanceAdd(final int d1, final int d2) {
        if (d1 == INFINITE_DISTANCE || d2 == INFINITE_DISTANCE) {
            return INFINITE_DISTANCE;
        }
        if (d1 <= INFINITE_DISTANCE - d2) {
            return d1 + d2;
        }
        return INFINITE_DISTANCE;
    }

    static int distanceMultiply(final int d, final int m) {
        if (m == 0) {
            return 0;
        }
        if (d < INFINITE_DISTANCE / m) {
            return d * m;
        }
        return INFINITE_DISTANCE;
    }

    static String distanceRangeToString(final int a, final int b) {
        String s = "";
        if (a == INFINITE_DISTANCE) {
            s += "inf";
        } else {
            s += "(" + a + ")";
        }

        s += "-";

        if (b == INFINITE_DISTANCE) {
            s += "inf";
        } else {
            s += "(" + b + ")";
        }
        return s;
    }
}
