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
package jdk.nashorn.internal.runtime.regexp.joni.ast;

import jdk.nashorn.internal.runtime.regexp.joni.*;
import jdk.nashorn.internal.runtime.regexp.joni.constants.CCSTATE;
import jdk.nashorn.internal.runtime.regexp.joni.constants.CCVALTYPE;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.CharacterType;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.IntHolder;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ErrorMessages;
import jdk.nashorn.internal.runtime.regexp.joni.exception.InternalException;
import jdk.nashorn.internal.runtime.regexp.joni.exception.SyntaxException;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ValueException;

public final class CClassNode extends Node {
    private static final int FLAG_NCCLASS_NOT = 1<<0;
    private static final int FLAG_NCCLASS_SHARE = 1<<1;

    int flags;
    public final BitSet bs = new BitSet();  // conditional creation ?
    public CodeRangeBuffer mbuf;            /* multi-byte info or NULL */

    private int ctype;                      // for hashing purposes

    private final static short AsciiCtypeTable[] = {
            0x4008, 0x4008, 0x4008, 0x4008, 0x4008, 0x4008, 0x4008, 0x4008,
            0x4008, 0x420c, 0x4209, 0x4208, 0x4208, 0x4208, 0x4008, 0x4008,
            0x4008, 0x4008, 0x4008, 0x4008, 0x4008, 0x4008, 0x4008, 0x4008,
            0x4008, 0x4008, 0x4008, 0x4008, 0x4008, 0x4008, 0x4008, 0x4008,
            0x4284, 0x41a0, 0x41a0, 0x41a0, 0x41a0, 0x41a0, 0x41a0, 0x41a0,
            0x41a0, 0x41a0, 0x41a0, 0x41a0, 0x41a0, 0x41a0, 0x41a0, 0x41a0,
            0x78b0, 0x78b0, 0x78b0, 0x78b0, 0x78b0, 0x78b0, 0x78b0, 0x78b0,
            0x78b0, 0x78b0, 0x41a0, 0x41a0, 0x41a0, 0x41a0, 0x41a0, 0x41a0,
            0x41a0, 0x7ca2, 0x7ca2, 0x7ca2, 0x7ca2, 0x7ca2, 0x7ca2, 0x74a2,
            0x74a2, 0x74a2, 0x74a2, 0x74a2, 0x74a2, 0x74a2, 0x74a2, 0x74a2,
            0x74a2, 0x74a2, 0x74a2, 0x74a2, 0x74a2, 0x74a2, 0x74a2, 0x74a2,
            0x74a2, 0x74a2, 0x74a2, 0x41a0, 0x41a0, 0x41a0, 0x41a0, 0x51a0,
            0x41a0, 0x78e2, 0x78e2, 0x78e2, 0x78e2, 0x78e2, 0x78e2, 0x70e2,
            0x70e2, 0x70e2, 0x70e2, 0x70e2, 0x70e2, 0x70e2, 0x70e2, 0x70e2,
            0x70e2, 0x70e2, 0x70e2, 0x70e2, 0x70e2, 0x70e2, 0x70e2, 0x70e2,
            0x70e2, 0x70e2, 0x70e2, 0x41a0, 0x41a0, 0x41a0, 0x41a0, 0x4008,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000
    };

    // node_new_cclass
    public CClassNode() {}

    public void clear() {
        bs.clear();
        flags = 0;
        mbuf = null;
    }

    @Override
    public int getType() {
        return CCLASS;
    }

    @Override
    public String getName() {
        return "Character Class";
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CClassNode)) return false;
        CClassNode cc = (CClassNode)other;
        return ctype == cc.ctype && isNot() == cc.isNot();
    }

    @Override
    public int hashCode() {
        if (Config.USE_SHARED_CCLASS_TABLE) {
            int hash = 0;
            hash += ctype;
            if (isNot()) hash++;
            return hash + (hash >> 5);
        } else {
            return super.hashCode();
        }
    }

    @Override
    public String toString(int level) {
        StringBuilder value = new StringBuilder();
        value.append("\n  flags: " + flagsToString());
        value.append("\n  bs: " + pad(bs, level + 1));
        value.append("\n  mbuf: " + pad(mbuf, level + 1));

        return value.toString();
    }

    public String flagsToString() {
        StringBuilder flags = new StringBuilder();
        if (isNot()) flags.append("NOT ");
        if (isShare()) flags.append("SHARE ");
        return flags.toString();
    }

    public boolean isEmpty() {
        return mbuf == null && bs.isEmpty();
    }

    public void addCodeRangeToBuf(int from, int to) {
        mbuf = CodeRangeBuffer.addCodeRangeToBuff(mbuf, from, to);
    }

    public void addCodeRange(ScanEnvironment env, int from, int to) {
        mbuf = CodeRangeBuffer.addCodeRange(mbuf, env, from, to);
    }

    public void addAllMultiByteRange() {
        mbuf = CodeRangeBuffer.addAllMultiByteRange(mbuf);
    }

    public void clearNotFlag() {
        if (isNot()) {
            bs.invert();

            mbuf = CodeRangeBuffer.notCodeRangeBuff(mbuf);
            clearNot();
        }
    }

    // and_cclass
    public void and(CClassNode other) {
        boolean not1 = isNot();
        BitSet bsr1 = bs;
        CodeRangeBuffer buf1 = mbuf;
        boolean not2 = other.isNot();
        BitSet bsr2 = other.bs;
        CodeRangeBuffer buf2 = other.mbuf;

        if (not1) {
            BitSet bs1 = new BitSet();
            bsr1.invertTo(bs1);
            bsr1 = bs1;
        }

        if (not2) {
            BitSet bs2 = new BitSet();
            bsr2.invertTo(bs2);
            bsr2 = bs2;
        }

        bsr1.and(bsr2);

        if (bsr1 != bs) {
            bs.copy(bsr1);
            bsr1 = bs;
        }

        if (not1) {
            bs.invert();
        }

        CodeRangeBuffer pbuf = null;

        if (not1 && not2) {
            pbuf = CodeRangeBuffer.orCodeRangeBuff(buf1, false, buf2, false);
        } else {
            pbuf = CodeRangeBuffer.andCodeRangeBuff(buf1, not1, buf2, not2);

            if (not1) {
                pbuf = CodeRangeBuffer.notCodeRangeBuff(pbuf);
            }
        }
        mbuf = pbuf;

    }

    // or_cclass
    public void or(CClassNode other) {
        boolean not1 = isNot();
        BitSet bsr1 = bs;
        CodeRangeBuffer buf1 = mbuf;
        boolean not2 = other.isNot();
        BitSet bsr2 = other.bs;
        CodeRangeBuffer buf2 = other.mbuf;

        if (not1) {
            BitSet bs1 = new BitSet();
            bsr1.invertTo(bs1);
            bsr1 = bs1;
        }

        if (not2) {
            BitSet bs2 = new BitSet();
            bsr2.invertTo(bs2);
            bsr2 = bs2;
        }

        bsr1.or(bsr2);

        if (bsr1 != bs) {
            bs.copy(bsr1);
            bsr1 = bs;
        }

        if (not1) {
            bs.invert();
        }

        CodeRangeBuffer pbuf = null;
        if (not1 && not2) {
            pbuf = CodeRangeBuffer.andCodeRangeBuff(buf1, false, buf2, false);
        } else {
            pbuf = CodeRangeBuffer.orCodeRangeBuff(buf1, not1, buf2, not2);
            if (not1) {
                pbuf = CodeRangeBuffer.notCodeRangeBuff(pbuf);
            }
        }
        mbuf = pbuf;
    }

    // add_ctype_to_cc_by_range // Encoding out!
    public void addCTypeByRange(int ctype, boolean not, int sbOut, int mbr[]) {
        int n = mbr[0];

        if (!not) {
            for (int i=0; i<n; i++) {
                for (int j=mbr[i * 2 + 1]; j<=mbr[i * 2 + 2]; j++) {
                    if (j >= sbOut) {
                        if (Config.VANILLA) {
                            if (j == mbr[i * 2 + 2]) {
                                i++;
                            } else if (j > mbr[i * 2 + 1]) {
                                addCodeRangeToBuf(j, mbr[i * 2 + 2]);
                                i++;
                            }
                        } else {
                            if (j >= mbr[i * 2 + 1]) {
                                addCodeRangeToBuf(j, mbr[i * 2 + 2]);
                                i++;
                            }
                        }
                        // !goto sb_end!, remove duplication!
                        for (; i<n; i++) {
                            addCodeRangeToBuf(mbr[2 * i + 1], mbr[2 * i + 2]);
                        }
                        return;
                    }
                    bs.set(j);
                }
            }
            // !sb_end:!
            for (int i=0; i<n; i++) {
                addCodeRangeToBuf(mbr[2 * i + 1], mbr[2 * i + 2]);
            }

        } else {
            int prev = 0;

            for (int i=0; i<n; i++) {
                for (int j=prev; j < mbr[2 * i + 1]; j++) {
                    if (j >= sbOut) {
                        // !goto sb_end2!, remove duplication
                        prev = sbOut;
                        for (i=0; i<n; i++) {
                            if (prev < mbr[2 * i + 1]) addCodeRangeToBuf(prev, mbr[i * 2 + 1] - 1);
                            prev = mbr[i * 2 + 2] + 1;
                        }
                        if (prev < 0x7fffffff/*!!!*/) addCodeRangeToBuf(prev, 0x7fffffff);
                        return;
                    }
                    bs.set(j);
                }
                prev = mbr[2 * i + 2] + 1;
            }

            for (int j=prev; j<sbOut; j++) {
                bs.set(j);
            }

            // !sb_end2:!
            prev = sbOut;
            for (int i=0; i<n; i++) {
                if (prev < mbr[2 * i + 1]) addCodeRangeToBuf(prev, mbr[i * 2 + 1] - 1);
                prev = mbr[i * 2 + 2] + 1;
            }
            if (prev < 0x7fffffff/*!!!*/) addCodeRangeToBuf(prev, 0x7fffffff);
        }
    }

    public void addCType(int ctype, boolean not, ScanEnvironment env, IntHolder sbOut) {
        if (Config.NON_UNICODE_SDW) {
            switch(ctype) {
            case CharacterType.D:
            case CharacterType.S:
            case CharacterType.W:
                ctype ^= CharacterType.SPECIAL_MASK;

                if (env.syntax == Syntax.JAVASCRIPT && ctype == CharacterType.SPACE) {
                    // \s in JavaScript includes unicode characters.
                    break;
                }

                if (not) {
                    for (int c = 0; c < BitSet.SINGLE_BYTE_SIZE; c++) {
                        // if (!ASCIIEncoding.INSTANCE.isCodeCType(c, ctype)) bs.set(c);
                        if ((AsciiCtypeTable[c] & (1 << ctype)) == 0) bs.set(c);
                    }
                    addAllMultiByteRange();
                } else {
                    for (int c = 0; c < BitSet.SINGLE_BYTE_SIZE; c++) {
                        // if (ASCIIEncoding.INSTANCE.isCodeCType(c, ctype)) bs.set(c);
                        if ((AsciiCtypeTable[c] & (1 << ctype)) != 0) bs.set(c);
                    }
                }
                return;
            }
        }

        int[] ranges = EncodingHelper.ctypeCodeRange(ctype, sbOut);
        if (ranges != null) {
            addCTypeByRange(ctype, not, sbOut.value, ranges);
            return;
        }

        switch(ctype) {
        case CharacterType.ALPHA:
        case CharacterType.BLANK:
        case CharacterType.CNTRL:
        case CharacterType.DIGIT:
        case CharacterType.LOWER:
        case CharacterType.PUNCT:
        case CharacterType.SPACE:
        case CharacterType.UPPER:
        case CharacterType.XDIGIT:
        case CharacterType.ASCII:
        case CharacterType.ALNUM:
            if (not) {
                for (int c=0; c<BitSet.SINGLE_BYTE_SIZE; c++) {
                    if (!EncodingHelper.isCodeCType(c, ctype)) bs.set(c);
                }
                addAllMultiByteRange();
            } else {
                for (int c=0; c<BitSet.SINGLE_BYTE_SIZE; c++) {
                    if (EncodingHelper.isCodeCType(c, ctype)) bs.set(c);
                }
            }
            break;

        case CharacterType.GRAPH:
        case CharacterType.PRINT:
            if (not) {
                for (int c=0; c<BitSet.SINGLE_BYTE_SIZE; c++) {
                    if (!EncodingHelper.isCodeCType(c, ctype)) bs.set(c);
                }
            } else {
                for (int c=0; c<BitSet.SINGLE_BYTE_SIZE; c++) {
                    if (EncodingHelper.isCodeCType(c, ctype)) bs.set(c);
                }
                addAllMultiByteRange();
            }
            break;

        case CharacterType.WORD:
            if (!not) {
                for (int c=0; c<BitSet.SINGLE_BYTE_SIZE; c++) {
                    if (EncodingHelper.isWord(c)) bs.set(c);
                }

                addAllMultiByteRange();
            } else {
                for (int c=0; c<BitSet.SINGLE_BYTE_SIZE; c++) {
                    if (!EncodingHelper.isWord(c)) bs.set(c);
                }
            }
            break;

        default:
            throw new InternalException(ErrorMessages.ERR_PARSER_BUG);
        } // switch
    }

    public static final class CCStateArg {
        public int v;
        public int vs;
        public boolean vsIsRaw;
        public boolean vIsRaw;
        public CCVALTYPE inType;
        public CCVALTYPE type;
        public CCSTATE state;
    }

    public void nextStateClass(CCStateArg arg, ScanEnvironment env) {
        if (arg.state == CCSTATE.RANGE) throw new SyntaxException(ErrorMessages.ERR_CHAR_CLASS_VALUE_AT_END_OF_RANGE);

        if (arg.state == CCSTATE.VALUE && arg.type != CCVALTYPE.CLASS) {
            if (arg.type == CCVALTYPE.SB) {
                bs.set(arg.vs);
            } else if (arg.type == CCVALTYPE.CODE_POINT) {
                addCodeRange(env, arg.vs, arg.vs);
            }
        }
        arg.state = CCSTATE.VALUE;
        arg.type = CCVALTYPE.CLASS;
    }

    public void nextStateValue(CCStateArg arg, ScanEnvironment env) {

        switch(arg.state) {
        case VALUE:
            if (arg.type == CCVALTYPE.SB) {
                if (arg.vs > 0xff) throw new ValueException(ErrorMessages.ERR_INVALID_CODE_POINT_VALUE);
                bs.set(arg.vs);
            } else if (arg.type == CCVALTYPE.CODE_POINT) {
                addCodeRange(env, arg.vs, arg.vs);
            }
            break;

        case RANGE:
            if (arg.inType == arg.type) {
                if (arg.inType == CCVALTYPE.SB) {
                    if (arg.vs > 0xff || arg.v > 0xff) throw new ValueException(ErrorMessages.ERR_INVALID_CODE_POINT_VALUE);

                    if (arg.vs > arg.v) {
                        if (env.syntax.allowEmptyRangeInCC()) {
                            // goto ccs_range_end
                            arg.state = CCSTATE.COMPLETE;
                            break;
                        } else {
                            throw new ValueException(ErrorMessages.ERR_EMPTY_RANGE_IN_CHAR_CLASS);
                        }
                    }
                    bs.setRange(arg.vs, arg.v);
                } else {
                    addCodeRange(env, arg.vs, arg.v);
                }
            } else {
                if (arg.vs > arg.v) {
                    if (env.syntax.allowEmptyRangeInCC()) {
                        // goto ccs_range_end
                        arg.state = CCSTATE.COMPLETE;
                        break;
                    } else {
                        throw new ValueException(ErrorMessages.ERR_EMPTY_RANGE_IN_CHAR_CLASS);
                    }
                }
                bs.setRange(arg.vs, arg.v < 0xff ? arg.v : 0xff);
                addCodeRange(env, arg.vs, arg.v);
            }
            // ccs_range_end:
            arg.state = CCSTATE.COMPLETE;
            break;

        case COMPLETE:
        case START:
            arg.state = CCSTATE.VALUE;
            break;

        default:
            break;

        } // switch

        arg.vsIsRaw = arg.vIsRaw;
        arg.vs = arg.v;
        arg.type = arg.inType;
    }

    // onig_is_code_in_cc_len
    public boolean isCodeInCCLength(int code) {
        boolean found;

        if (code > 0xff) {
            found = mbuf != null && mbuf.isInCodeRange(code);
        } else {
            found = bs.at(code);
        }

        if (isNot()) {
            return !found;
        } else {
            return found;
        }
    }

    // onig_is_code_in_cc
    public boolean isCodeInCC(int code) {
         return isCodeInCCLength(code);
    }

    public void setNot() {
        flags |= FLAG_NCCLASS_NOT;
    }

    public void clearNot() {
        flags &= ~FLAG_NCCLASS_NOT;
    }

    public boolean isNot() {
        return (flags & FLAG_NCCLASS_NOT) != 0;
    }

    public void setShare() {
        flags |= FLAG_NCCLASS_SHARE;
    }

    public void clearShare() {
        flags &= ~FLAG_NCCLASS_SHARE;
    }

    public boolean isShare() {
        return (flags & FLAG_NCCLASS_SHARE) != 0;
    }

}
