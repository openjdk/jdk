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

final class OptExactInfo {
    static final int OPT_EXACT_MAXLEN = 24;

    final MinMaxLen mmd = new MinMaxLen();
    final OptAnchorInfo anchor = new OptAnchorInfo();

    boolean reachEnd;
    boolean ignoreCase;

    final char chars[] = new char[OPT_EXACT_MAXLEN];
    int length;

    boolean isFull() {
        return length >= OPT_EXACT_MAXLEN;
    }

    void clear() {
        mmd.clear();
        anchor.clear();

        reachEnd = false;
        ignoreCase = false;
        length = 0;
    }

    void copy(final OptExactInfo other) {
        mmd.copy(other.mmd);
        anchor.copy(other.anchor);
        reachEnd = other.reachEnd;
        ignoreCase = other.ignoreCase;
        length = other.length;

        System.arraycopy(other.chars, 0, chars, 0, OPT_EXACT_MAXLEN);
    }

    void concat(final OptExactInfo other) {
        if (!ignoreCase && other.ignoreCase) {
            if (length >= other.length) return; /* avoid */
            ignoreCase = true;
        }

        int p = 0; // add->s;
        final int end = p + other.length;

        int i;
        for (i = length; p < end;) {
            if (i + 1 > OPT_EXACT_MAXLEN) break;
            chars[i++] = other.chars[p++];
        }

        length = i;
        reachEnd = (p == end ? other.reachEnd : false);

        final OptAnchorInfo tmp = new OptAnchorInfo();
        tmp.concat(anchor, other.anchor, 1, 1);
        if (!other.reachEnd) tmp.rightAnchor = 0;
        anchor.copy(tmp);
    }

    // ?? raw is not used here
    void concatStr(final char[] lchars, int p, final int end, final boolean raw) {
        int i;
        for (i = length; p < end && i < OPT_EXACT_MAXLEN;) {
            if (i + 1 > OPT_EXACT_MAXLEN) break;
            chars[i++] = lchars[p++];
        }

        length = i;
    }

    void altMerge(final OptExactInfo other, final OptEnvironment env) {
        if (other.length == 0 || length == 0) {
            clear();
            return;
        }

        if (!mmd.equal(other.mmd)) {
            clear();
            return;
        }

        int i;
        for (i = 0; i < length && i < other.length; i++) {
            if (chars[i] != other.chars[i]) break;
        }

        if (!other.reachEnd || i<other.length || i<length) reachEnd = false;

        length = i;
        ignoreCase |= other.ignoreCase;

        anchor.altMerge(other.anchor);

        if (!reachEnd) anchor.rightAnchor = 0;
    }


    void select(final OptExactInfo alt) {
        int v1 = length;
        int v2 = alt.length;

        if (v2 == 0) {
            return;
        } else if (v1 == 0) {
            copy(alt);
            return;
        } else if (v1 <= 2 && v2 <= 2) {
            /* ByteValTable[x] is big value --> low price */
            v2 = OptMapInfo.positionValue(chars[0] & 0xff);
            v1 = OptMapInfo.positionValue(alt.chars[0] & 0xff);

            if (length > 1) v1 += 5;
            if (alt.length > 1) v2 += 5;
        }

        if (!ignoreCase) v1 *= 2;
        if (!alt.ignoreCase) v2 *= 2;

        if (mmd.compareDistanceValue(alt.mmd, v1, v2) > 0) copy(alt);
    }

    // comp_opt_exact_or_map_info
    private static final int COMP_EM_BASE   = 20;
    int compare(final OptMapInfo m) {
        if (m.value <= 0) return -1;

        final int ve = COMP_EM_BASE * length * (ignoreCase ? 1 : 2);
        final int vm = COMP_EM_BASE * 5 * 2 / m.value;

        return mmd.compareDistanceValue(m.mmd, ve, vm);
    }
}
