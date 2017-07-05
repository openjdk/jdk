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

final class BitStatus {
    public static final int BIT_STATUS_BITS_NUM = 4 * 8;

    public static int bsClear() {
        return 0;
    }

    public static int bsAll() {
        return -1;
    }

    public static boolean bsAt(final int stats, final int n) {
        return (n < BIT_STATUS_BITS_NUM ? stats & (1 << n) : (stats & 1)) != 0;
    }

    public static int bsOnAt(int stats, final int n) {
        if (n < BIT_STATUS_BITS_NUM) {
            stats |= (1 << n);
        } else {
            stats |= 1;
        }
        return stats;
    }

    public static int bsOnOff(int v, final int f, final boolean negative) {
        if (negative) {
            v &= ~f;
        } else {
            v |= f;
        }
        return v;
    }
}
