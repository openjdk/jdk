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

import jdk.nashorn.internal.runtime.regexp.joni.constants.AnchorType;
import jdk.nashorn.internal.runtime.regexp.joni.constants.RegexState;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ErrorMessages;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ValueException;

public final class Regex implements RegexState {

    int[] code;             /* compiled pattern */
    int codeLength;
    boolean stackNeeded;
    Object[] operands;       /* e.g. shared CClassNode */
    int operandLength;

    int numMem;             /* used memory(...) num counted from 1 */
    int numRepeat;          /* OP_REPEAT/OP_REPEAT_NG id-counter */
    int numNullCheck;       /* OP_NULL_CHECK_START/END id counter */
    int captureHistory;     /* (?@...) flag (1-31) */
    int btMemStart;         /* need backtrack flag */
    int btMemEnd;           /* need backtrack flag */

    int stackPopLevel;

    int[] repeatRangeLo;
    int[] repeatRangeHi;

    WarnCallback warnings;
    MatcherFactory factory;
    protected Analyser analyser;

    int options;
    final int caseFoldFlag;

    /* optimization info (string search, char-map and anchors) */
    SearchAlgorithm searchAlgorithm;        /* optimize flag */
    int thresholdLength;                    /* search str-length for apply optimize */
    int anchor;                             /* BEGIN_BUF, BEGIN_POS, (SEMI_)END_BUF */
    int anchorDmin;                         /* (SEMI_)END_BUF anchor distance */
    int anchorDmax;                         /* (SEMI_)END_BUF anchor distance */
    int subAnchor;                          /* start-anchor for exact or map */

    char[] exact;
    int exactP;
    int exactEnd;

    byte[] map;                              /* used as BM skip or char-map */
    int[] intMap;                            /* BM skip for exact_len > 255 */
    int[] intMapBackward;                    /* BM skip for backward search */
    int dMin;                               /* min-distance of exact or map */
    int dMax;                               /* max-distance of exact or map */

    char[][] templates;
    int templateNum;

    public Regex(final CharSequence cs) {
        this(cs.toString());
    }

    public Regex(final String str) {
        this(str.toCharArray(), 0, str.length(), 0);
    }

    public Regex(final char[] chars) {
        this(chars, 0, chars.length, 0);
    }

    public Regex(final char[] chars, final int p, final int end) {
        this(chars, p, end, 0);
    }

    public Regex(final char[] chars, final int p, final int end, final int option) {
        this(chars, p, end, option, Syntax.RUBY, WarnCallback.DEFAULT);
    }

    // onig_new
    public Regex(final char[] chars, final int p, final int end, final int option, final Syntax syntax) {
        this(chars, p, end, option, Config.ENC_CASE_FOLD_DEFAULT, syntax, WarnCallback.DEFAULT);
    }

    public Regex(final char[]chars, final int p, final int end, final int option, final WarnCallback warnings) {
        this(chars, p, end, option, Syntax.RUBY, warnings);
    }

    // onig_new
    public Regex(final char[] chars, final int p, final int end, final int option, final Syntax syntax, final WarnCallback warnings) {
        this(chars, p, end, option, Config.ENC_CASE_FOLD_DEFAULT, syntax, warnings);
    }

    // onig_alloc_init
    public Regex(final char[] chars, final int p, final int end, int option, final int caseFoldFlag, final Syntax syntax, final WarnCallback warnings) {

        if ((option & (Option.DONT_CAPTURE_GROUP | Option.CAPTURE_GROUP)) ==
            (Option.DONT_CAPTURE_GROUP | Option.CAPTURE_GROUP)) {
            throw new ValueException(ErrorMessages.ERR_INVALID_COMBINATION_OF_OPTIONS);
        }

        if ((option & Option.NEGATE_SINGLELINE) != 0) {
            option |= syntax.options;
            option &= ~Option.SINGLELINE;
        } else {
            option |= syntax.options;
        }

        this.options = option;
        this.caseFoldFlag = caseFoldFlag;
        this.warnings = warnings;

        this.analyser = new Analyser(new ScanEnvironment(this, syntax), chars, p, end);
        this.analyser.compile();

        this.warnings = null;
    }

    public synchronized MatcherFactory compile() {
        if (factory == null && analyser != null) {
            new ArrayCompiler(analyser).compile();
            analyser = null; // only do this once
        }
        assert factory != null;
        return factory;
    }

    public Matcher matcher(final char[] chars) {
        return matcher(chars, 0, chars.length);
    }

    public Matcher matcher(final char[] chars, final int p, final int end) {
        MatcherFactory matcherFactory = factory;
        if (matcherFactory == null) {
            matcherFactory = compile();
        }
        return matcherFactory.create(this, chars, p, end);
    }

    public WarnCallback getWarnings() {
        return warnings;
    }

    public int numberOfCaptures() {
        return numMem;
    }

    /* set skip map for Boyer-Moor search */
    void setupBMSkipMap() {
        final char[] chars = exact;
        final int p = exactP;
        final int end = exactEnd;
        final int len = end - p;

        if (len < Config.CHAR_TABLE_SIZE) {
            // map/skip
            if (map == null) map = new byte[Config.CHAR_TABLE_SIZE];

            for (int i=0; i<Config.CHAR_TABLE_SIZE; i++) map[i] = (byte)len;
            for (int i=0; i<len-1; i++) map[chars[p + i] & 0xff] = (byte)(len - 1 -i); // oxff ??
        } else {
            if (intMap == null) intMap = new int[Config.CHAR_TABLE_SIZE];

            for (int i=0; i<len-1; i++) intMap[chars[p + i] & 0xff] = len - 1 - i; // oxff ??
        }
    }

    void setExactInfo(final OptExactInfo e) {
        if (e.length == 0) return;

        // shall we copy that ?
        exact = e.chars;
        exactP = 0;
        exactEnd = e.length;

        if (e.ignoreCase) {
            searchAlgorithm = new SearchAlgorithm.SLOW_IC(this);
        } else {
            if (e.length >= 2) {
                setupBMSkipMap();
                searchAlgorithm = SearchAlgorithm.BM;
            } else {
                searchAlgorithm = SearchAlgorithm.SLOW;
            }
        }

        dMin = e.mmd.min;
        dMax = e.mmd.max;

        if (dMin != MinMaxLen.INFINITE_DISTANCE) {
            thresholdLength = dMin + (exactEnd - exactP);
        }
    }

    void setOptimizeMapInfo(final OptMapInfo m) {
        map = m.map;

        searchAlgorithm = SearchAlgorithm.MAP;
        dMin = m.mmd.min;
        dMax = m.mmd.max;

        if (dMin != MinMaxLen.INFINITE_DISTANCE) {
            thresholdLength = dMin + 1;
        }
    }

    void setSubAnchor(final OptAnchorInfo anc) {
        subAnchor |= anc.leftAnchor & AnchorType.BEGIN_LINE;
        subAnchor |= anc.rightAnchor & AnchorType.END_LINE;
    }

    void clearOptimizeInfo() {
        searchAlgorithm = SearchAlgorithm.NONE;
        anchor = 0;
        anchorDmax = 0;
        anchorDmin = 0;
        subAnchor = 0;

        exact = null;
        exactP = exactEnd = 0;
    }

    public String optimizeInfoToString() {
        final StringBuilder s = new StringBuilder();
        s.append("optimize: ").append(searchAlgorithm.getName()).append("\n");
        s.append("  anchor:     ").append(OptAnchorInfo.anchorToString(anchor));

        if ((anchor & AnchorType.END_BUF_MASK) != 0) {
            s.append(MinMaxLen.distanceRangeToString(anchorDmin, anchorDmax));
        }

        s.append("\n");

        if (searchAlgorithm != SearchAlgorithm.NONE) {
            s.append("  sub anchor: ").append(OptAnchorInfo.anchorToString(subAnchor)).append("\n");
        }

        s.append("dmin: ").append(dMin).append(" dmax: ").append(dMax).append("\n");
        s.append("threshold length: ").append(thresholdLength).append("\n");

        if (exact != null) {
            s.append("exact: [").append(exact, exactP, exactEnd - exactP).append("]: length: ").append(exactEnd - exactP).append("\n");
        } else if (searchAlgorithm == SearchAlgorithm.MAP) {
            int n=0;
            for (int i=0; i<Config.CHAR_TABLE_SIZE; i++) if (map[i] != 0) n++;

            s.append("map: n = ").append(n).append("\n");
            if (n > 0) {
                int c=0;
                s.append("[");
                for (int i=0; i<Config.CHAR_TABLE_SIZE; i++) {
                    if (map[i] != 0) {
                        if (c > 0) {
                            s.append(", ");
                        }
                        c++;
                        // TODO if (enc.isPrint(i)
                        s.append((char)i);
                    }
                }
                s.append("]\n");
            }
        }

        return s.toString();
    }

    public int getOptions() {
        return options;
    }

    public String dumpTree() {
        return analyser == null ? null : analyser.root.toString();
    }

    public String dumpByteCode() {
        compile();
        return new ByteCodePrinter(this).byteCodeListToString();
    }

}
