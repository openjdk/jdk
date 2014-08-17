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

public final class Region {
    static final int REGION_NOTPOS = -1;

    public final int numRegs;
    public final int[]beg;
    public final int[]end;

    public Region(final int num) {
        this.numRegs = num;
        this.beg = new int[num];
        this.end = new int[num];
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Region: \n");
        for (int i=0; i<beg.length; i++) sb.append(" " + i + ": (" + beg[i] + "-" + end[i] + ")");
        return sb.toString();
    }

    void clear() {
        for (int i=0; i<beg.length; i++) {
            beg[i] = end[i] = REGION_NOTPOS;
        }
    }
}
