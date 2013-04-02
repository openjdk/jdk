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

import static jdk.nashorn.internal.runtime.regexp.joni.BitStatus.bsAt;
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isCaptureGroup;
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isDontCaptureGroup;

import java.util.HashMap;
import java.util.Iterator;

import jdk.nashorn.internal.runtime.regexp.joni.constants.AnchorType;
import jdk.nashorn.internal.runtime.regexp.joni.constants.RegexState;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ErrorMessages;
import jdk.nashorn.internal.runtime.regexp.joni.exception.InternalException;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ValueException;

public final class Regex implements RegexState {

    int[] code;             /* compiled pattern */
    int codeLength;
    boolean stackNeeded;
    Object[]operands;       /* e.g. shared CClassNode */
    int operandLength;

    int state;              /* normal, searching, compiling */ // remove
    int numMem;             /* used memory(...) num counted from 1 */
    int numRepeat;          /* OP_REPEAT/OP_REPEAT_NG id-counter */
    int numNullCheck;       /* OP_NULL_CHECK_START/END id counter */
    int numCombExpCheck;    /* combination explosion check */
    int numCall;            /* number of subexp call */
    int captureHistory;     /* (?@...) flag (1-31) */
    int btMemStart;         /* need backtrack flag */
    int btMemEnd;           /* need backtrack flag */

    int stackPopLevel;

    int[]repeatRangeLo;
    int[]repeatRangeHi;

    public WarnCallback warnings;
    public MatcherFactory factory;

    int options;
    int userOptions;
    Object userObject;
    //final Syntax syntax;
    final int caseFoldFlag;

    HashMap<String,NameEntry> nameTable;        // named entries

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

    public Regex(CharSequence cs) {
        this(cs.toString());
    }

    public Regex(String str) {
        this(str.toCharArray(), 0, str.length(), 0);
    }

    public Regex(char[] chars) {
        this(chars, 0, chars.length, 0);
    }

    public Regex(char[] chars, int p, int end) {
        this(chars, p, end, 0);
    }

    public Regex(char[] chars, int p, int end, int option) {
        this(chars, p, end, option, Syntax.RUBY, WarnCallback.DEFAULT);
    }

    // onig_new
    public Regex(char[] chars, int p, int end, int option, Syntax syntax) {
        this(chars, p, end, option, Config.ENC_CASE_FOLD_DEFAULT, syntax, WarnCallback.DEFAULT);
    }

    public Regex(char[]chars, int p, int end, int option, WarnCallback warnings) {
        this(chars, p, end, option, Syntax.RUBY, warnings);
    }

    // onig_new
    public Regex(char[] chars, int p, int end, int option, Syntax syntax, WarnCallback warnings) {
        this(chars, p, end, option, Config.ENC_CASE_FOLD_DEFAULT, syntax, warnings);
    }

    // onig_alloc_init
    public Regex(char[] chars, int p, int end, int option, int caseFoldFlag, Syntax syntax, WarnCallback warnings) {

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

        new Analyser(new ScanEnvironment(this, syntax), chars, p, end).compile();

        this.warnings = null;
    }

    public Matcher matcher(char[] chars) {
        return matcher(chars, 0, chars.length);
    }

    public Matcher matcher(char[] chars, int p, int end) {
        return factory.create(this, chars, p, end);
    }

    public int numberOfCaptures() {
        return numMem;
    }

    public int numberOfCaptureHistories() {
        if (Config.USE_CAPTURE_HISTORY) {
            int n = 0;
            for (int i=0; i<=Config.MAX_CAPTURE_HISTORY_GROUP; i++) {
                if (bsAt(captureHistory, i)) n++;
            }
            return n;
        } else {
            return 0;
        }
    }

    String nameTableToString() {
        StringBuilder sb = new StringBuilder();

        if (nameTable != null) {
            sb.append("name table\n");
            for (NameEntry ne : nameTable.values()) {
                sb.append("  " + ne + "\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    NameEntry nameFind(char[] name, int nameP, int nameEnd) {
        if (nameTable != null) return nameTable.get(new String(name, nameP, nameEnd - nameP));
        return null;
    }

    void renumberNameTable(int[]map) {
        if (nameTable != null) {
            for (NameEntry e : nameTable.values()) {
                if (e.backNum > 1) {
                    for (int i=0; i<e.backNum; i++) {
                        e.backRefs[i] = map[e.backRefs[i]];
                    }
                } else if (e.backNum == 1) {
                    e.backRef1 = map[e.backRef1];
                }
            }
        }
    }

    public int numberOfNames() {
        return nameTable == null ? 0 : nameTable.size();
    }

    void nameAdd(char[] name, int nameP, int nameEnd, int backRef, Syntax syntax) {
        if (nameEnd - nameP <= 0) throw new ValueException(ErrorMessages.ERR_EMPTY_GROUP_NAME);

        NameEntry e = null;
        if (nameTable == null) {
            nameTable = new HashMap<String,NameEntry>(); // 13, oni defaults to 5
        } else {
            e = nameFind(name, nameP, nameEnd);
        }

        if (e == null) {
            // dup the name here as oni does ?, what for ? (it has to manage it, we don't)
            e = new NameEntry(name, nameP, nameEnd);
            nameTable.put(new String(name, nameP, nameEnd - nameP), e);
        } else if (e.backNum >= 1 && !syntax.allowMultiplexDefinitionName()) {
            throw new ValueException(ErrorMessages.ERR_MULTIPLEX_DEFINED_NAME, new String(name, nameP, nameEnd - nameP));
        }

        e.addBackref(backRef);
    }

    NameEntry nameToGroupNumbers(char[] name, int nameP, int nameEnd) {
        return nameFind(name, nameP, nameEnd);
    }

    public int nameToBackrefNumber(char[] name, int nameP, int nameEnd, Region region) {
        NameEntry e = nameToGroupNumbers(name, nameP, nameEnd);
        if (e == null) throw new ValueException(ErrorMessages.ERR_UNDEFINED_NAME_REFERENCE,
                                                new String(name, nameP, nameEnd - nameP));

        switch(e.backNum) {
        case 0:
            throw new InternalException(ErrorMessages.ERR_PARSER_BUG);
        case 1:
            return e.backRef1;
        default:
            if (region != null) {
                for (int i = e.backNum - 1; i >= 0; i--) {
                    if (region.beg[e.backRefs[i]] != Region.REGION_NOTPOS) return e.backRefs[i];
                }
            }
            return e.backRefs[e.backNum - 1];
        }
    }

    public Iterator<NameEntry> namedBackrefIterator() {
        return nameTable.values().iterator();
    }

    public boolean noNameGroupIsActive(Syntax syntax) {
        if (isDontCaptureGroup(options)) return false;

        if (Config.USE_NAMED_GROUP) {
            if (numberOfNames() > 0 && syntax.captureOnlyNamedGroup() && !isCaptureGroup(options)) return false;
        }
        return true;
    }

    /* set skip map for Boyer-Moor search */
    void setupBMSkipMap() {
        char[] chars = exact;
        int p = exactP;
        int end = exactEnd;
        int len = end - p;

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

    void setExactInfo(OptExactInfo e) {
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

    void setOptimizeMapInfo(OptMapInfo m) {
        map = m.map;

        searchAlgorithm = SearchAlgorithm.MAP;
        dMin = m.mmd.min;
        dMax = m.mmd.max;

        if (dMin != MinMaxLen.INFINITE_DISTANCE) {
            thresholdLength = dMin + 1;
        }
    }

    void setSubAnchor(OptAnchorInfo anc) {
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

    public String encStringToString(byte[]bytes, int p, int end) {
        StringBuilder sb = new StringBuilder("\nPATTERN: /");

        while (p < end) {
            sb.append(new String(new byte[]{bytes[p]}));
            p++;
        }
        return sb.append("/").toString();
    }

    public String optimizeInfoToString() {
        String s = "";
        s += "optimize: " + searchAlgorithm.getName() + "\n";
        s += "  anchor:     " + OptAnchorInfo.anchorToString(anchor);

        if ((anchor & AnchorType.END_BUF_MASK) != 0) {
            s += MinMaxLen.distanceRangeToString(anchorDmin, anchorDmax);
        }

        s += "\n";

        if (searchAlgorithm != SearchAlgorithm.NONE) {
            s += "  sub anchor: " + OptAnchorInfo.anchorToString(subAnchor) + "\n";
        }

        s += "dmin: " + dMin + " dmax: " + dMax + "\n";
        s += "threshold length: " + thresholdLength + "\n";

        if (exact != null) {
            s += "exact: [" + new String(exact, exactP, exactEnd - exactP) + "]: length: " + (exactEnd - exactP) + "\n";
        } else if (searchAlgorithm == SearchAlgorithm.MAP) {
            int n=0;
            for (int i=0; i<Config.CHAR_TABLE_SIZE; i++) if (map[i] != 0) n++;

            s += "map: n = " + n + "\n";
            if (n > 0) {
                int c=0;
                s += "[";
                for (int i=0; i<Config.CHAR_TABLE_SIZE; i++) {
                    if (map[i] != 0) {
                        if (c > 0) s += ", ";
                        c++;
                        // TODO if (enc.isPrint(i)
                        s += ((char)i);
                    }
                }
                s += "]\n";
            }
        }

        return s;
    }

    public int getOptions() {
        return options;
    }

    public void setUserOptions(int options) {
        this.userOptions = options;
    }

    public int getUserOptions() {
        return userOptions;
    }

    public void setUserObject(Object object) {
        this.userObject = object;
    }

    public Object getUserObject() {
        return userObject;
    }
}
