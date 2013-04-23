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

final class OptMapInfo {

    final MinMaxLen mmd = new MinMaxLen();          /* info position */
    final OptAnchorInfo anchor = new OptAnchorInfo();

    int value;                                      /* weighted value */
    final byte map[] = new byte[Config.CHAR_TABLE_SIZE];

    void clear() {
        mmd.clear();
        anchor.clear();
        value = 0;
        for (int i=0; i<map.length; i++) map[i] = 0;
    }

    void copy(OptMapInfo other) {
        mmd.copy(other.mmd);
        anchor.copy(other.anchor);
        value = other.value;
        //for(int i=0; i<map.length; i++) map[i] = other.map[i];
        System.arraycopy(other.map, 0, map, 0, other.map.length);
    }

    void addChar(int c) {
        int c_ = c & 0xff;
        if (map[c_] == 0) {
            map[c_] = 1;
            value += positionValue(c_);
        }
    }

    void addCharAmb(char[] chars, int p, int end, int caseFoldFlag) {
        addChar(chars[p]);

        caseFoldFlag &= ~Config.INTERNAL_ENC_CASE_FOLD_MULTI_CHAR;
        char[]items = EncodingHelper.caseFoldCodesByString(caseFoldFlag, chars[p]);

        for (int i=0; i<items.length; i++) {
            addChar(items[i]);
        }
    }

    // select_opt_map_info
    private static final int z = 1<<15; /* 32768: something big value */
    void select(OptMapInfo alt) {
        if (alt.value == 0) return;
        if (value == 0) {
            copy(alt);
            return;
        }

        int v1 = z / value;
        int v2 = z /alt.value;

        if (mmd.compareDistanceValue(alt.mmd, v1, v2) > 0) copy(alt);
    }

    // alt_merge_opt_map_info
    void altMerge(OptMapInfo other) {
        /* if (! is_equal_mml(&to->mmd, &add->mmd)) return ; */
        if (value == 0) return;
        if (other.value == 0 || mmd.max < other.mmd.max) {
            clear();
            return;
        }

        mmd.altMerge(other.mmd);

        int val = 0;
        for (int i=0; i<Config.CHAR_TABLE_SIZE; i++) {
            if (other.map[i] != 0) map[i] = 1;
            if (map[i] != 0) val += positionValue(i);
        }

        value = val;
        anchor.altMerge(other.anchor);
    }

    static final short ByteValTable[] = {
        5,  1,  1,  1,  1,  1,  1,  1,  1, 10, 10,  1,  1, 10,  1,  1,
        1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,  1,
       12,  4,  7,  4,  4,  4,  4,  4,  4,  5,  5,  5,  5,  5,  5,  5,
        6,  6,  6,  6,  6,  6,  6,  6,  6,  6,  5,  5,  5,  5,  5,  5,
        5,  6,  6,  6,  6,  7,  6,  6,  6,  6,  6,  6,  6,  6,  6,  6,
        6,  6,  6,  6,  6,  6,  6,  6,  6,  6,  6,  5,  6,  5,  5,  5,
        5,  6,  6,  6,  6,  7,  6,  6,  6,  6,  6,  6,  6,  6,  6,  6,
        6,  6,  6,  6,  6,  6,  6,  6,  6,  6,  6,  5,  5,  5,  5,  1
     };

    // map_position_value
    static int positionValue(int i) {
        if (i < ByteValTable.length) {
            return ByteValTable[i];
        } else {
            return 4; /* Take it easy. */
        }
    }

}
