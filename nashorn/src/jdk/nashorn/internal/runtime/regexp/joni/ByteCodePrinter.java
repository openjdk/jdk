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

import jdk.nashorn.internal.runtime.regexp.joni.ast.CClassNode;
import jdk.nashorn.internal.runtime.regexp.joni.constants.Arguments;
import jdk.nashorn.internal.runtime.regexp.joni.constants.OPCode;
import jdk.nashorn.internal.runtime.regexp.joni.constants.OPSize;
import jdk.nashorn.internal.runtime.regexp.joni.exception.InternalException;

class ByteCodePrinter {
    final int[]code;
    final int codeLength;
    final char[][] templates;

    Object[]operands;
    int operantCount;
    WarnCallback warnings;

    private final static String OpCodeNames[] = new String[] {
            "finish", /*OP_FINISH*/
            "end", /*OP_END*/
            "exact1", /*OP_EXACT1*/
            "exact2", /*OP_EXACT2*/
            "exact3", /*OP_EXACT3*/
            "exact4", /*OP_EXACT4*/
            "exact5", /*OP_EXACT5*/
            "exactn", /*OP_EXACTN*/
            "exactmb2-n1", /*OP_EXACTMB2N1*/
            "exactmb2-n2", /*OP_EXACTMB2N2*/
            "exactmb2-n3", /*OP_EXACTMB2N3*/
            "exactmb2-n", /*OP_EXACTMB2N*/
            "exactmb3n", /*OP_EXACTMB3N*/
            "exactmbn", /*OP_EXACTMBN*/
            "exact1-ic", /*OP_EXACT1_IC*/
            "exactn-ic", /*OP_EXACTN_IC*/
            "cclass", /*OP_CCLASS*/
            "cclass-mb", /*OP_CCLASS_MB*/
            "cclass-mix", /*OP_CCLASS_MIX*/
            "cclass-not", /*OP_CCLASS_NOT*/
            "cclass-mb-not", /*OP_CCLASS_MB_NOT*/
            "cclass-mix-not", /*OP_CCLASS_MIX_NOT*/
            "cclass-node", /*OP_CCLASS_NODE*/
            "anychar", /*OP_ANYCHAR*/
            "anychar-ml", /*OP_ANYCHAR_ML*/
            "anychar*", /*OP_ANYCHAR_STAR*/
            "anychar-ml*", /*OP_ANYCHAR_ML_STAR*/
            "anychar*-peek-next", /*OP_ANYCHAR_STAR_PEEK_NEXT*/
            "anychar-ml*-peek-next", /*OP_ANYCHAR_ML_STAR_PEEK_NEXT*/
            "word", /*OP_WORD*/
            "not-word", /*OP_NOT_WORD*/
            "word-bound", /*OP_WORD_BOUND*/
            "not-word-bound", /*OP_NOT_WORD_BOUND*/
            "word-begin", /*OP_WORD_BEGIN*/
            "word-end", /*OP_WORD_END*/
            "begin-buf", /*OP_BEGIN_BUF*/
            "end-buf", /*OP_END_BUF*/
            "begin-line", /*OP_BEGIN_LINE*/
            "end-line", /*OP_END_LINE*/
            "semi-end-buf", /*OP_SEMI_END_BUF*/
            "begin-position", /*OP_BEGIN_POSITION*/
            "backref1", /*OP_BACKREF1*/
            "backref2", /*OP_BACKREF2*/
            "backrefn", /*OP_BACKREFN*/
            "backrefn-ic", /*OP_BACKREFN_IC*/
            "backref_multi", /*OP_BACKREF_MULTI*/
            "backref_multi-ic", /*OP_BACKREF_MULTI_IC*/
            "backref_at_level", /*OP_BACKREF_AT_LEVEL*/
            "mem-start", /*OP_MEMORY_START*/
            "mem-start-push", /*OP_MEMORY_START_PUSH*/
            "mem-end-push", /*OP_MEMORY_END_PUSH*/
            "mem-end-push-rec", /*OP_MEMORY_END_PUSH_REC*/
            "mem-end", /*OP_MEMORY_END*/
            "mem-end-rec", /*OP_MEMORY_END_REC*/
            "fail", /*OP_FAIL*/
            "jump", /*OP_JUMP*/
            "push", /*OP_PUSH*/
            "pop", /*OP_POP*/
            "push-or-jump-e1", /*OP_PUSH_OR_JUMP_EXACT1*/
            "push-if-peek-next", /*OP_PUSH_IF_PEEK_NEXT*/
            "repeat", /*OP_REPEAT*/
            "repeat-ng", /*OP_REPEAT_NG*/
            "repeat-inc", /*OP_REPEAT_INC*/
            "repeat-inc-ng", /*OP_REPEAT_INC_NG*/
            "repeat-inc-sg", /*OP_REPEAT_INC_SG*/
            "repeat-inc-ng-sg", /*OP_REPEAT_INC_NG_SG*/
            "null-check-start", /*OP_NULL_CHECK_START*/
            "null-check-end", /*OP_NULL_CHECK_END*/
            "null-check-end-memst", /*OP_NULL_CHECK_END_MEMST*/
            "null-check-end-memst-push", /*OP_NULL_CHECK_END_MEMST_PUSH*/
            "push-pos", /*OP_PUSH_POS*/
            "pop-pos", /*OP_POP_POS*/
            "push-pos-not", /*OP_PUSH_POS_NOT*/
            "fail-pos", /*OP_FAIL_POS*/
            "push-stop-bt", /*OP_PUSH_STOP_BT*/
            "pop-stop-bt", /*OP_POP_STOP_BT*/
            "look-behind", /*OP_LOOK_BEHIND*/
            "push-look-behind-not", /*OP_PUSH_LOOK_BEHIND_NOT*/
            "fail-look-behind-not", /*OP_FAIL_LOOK_BEHIND_NOT*/
            "call", /*OP_CALL*/
            "return", /*OP_RETURN*/
            "state-check-push", /*OP_STATE_CHECK_PUSH*/
            "state-check-push-or-jump", /*OP_STATE_CHECK_PUSH_OR_JUMP*/
            "state-check", /*OP_STATE_CHECK*/
            "state-check-anychar*", /*OP_STATE_CHECK_ANYCHAR_STAR*/
            "state-check-anychar-ml*", /*OP_STATE_CHECK_ANYCHAR_ML_STAR*/
            "set-option-push", /*OP_SET_OPTION_PUSH*/
            "set-option", /*OP_SET_OPTION*/

            // single byte versions
            "anychar-sb", /*OP_ANYCHAR*/
            "anychar-ml-sb", /*OP_ANYCHAR_ML*/
            "anychar*-sb", /*OP_ANYCHAR_STAR*/
            "anychar-ml*-sb", /*OP_ANYCHAR_ML_STAR*/
            "anychar*-peek-next-sb", /*OP_ANYCHAR_STAR_PEEK_NEXT*/
            "anychar-ml*-peek-next-sb", /*OP_ANYCHAR_ML_STAR_PEEK_NEXT*/
            "state-check-anychar*-sb", /*OP_STATE_CHECK_ANYCHAR_STAR*/
            "state-check-anychar-ml*-sb", /*OP_STATE_CHECK_ANYCHAR_ML_STAR*/

            "cclass-sb", /*OP_CCLASS*/
            "cclass-not-sb", /*OP_CCLASS_NOT*/

            "word-sb", /*OP_WORD*/
            "not-word-sb", /*OP_NOT_WORD*/
            "word-bound-sb", /*OP_WORD_BOUND*/
            "not-word-bound-sb", /*OP_NOT_WORD_BOUND*/
            "word-begin-sb", /*OP_WORD_BEGIN*/
            "word-end-sb", /*OP_WORD_END*/

            "look-behind-sb", /*OP_LOOK_BEHIND*/

            "exact1-ic-sb", /*OP_EXACT1_IC*/
            "exactn-ic-sb", /*OP_EXACTN_IC*/

    };

    private final static int OpCodeArgTypes[] = new int[] {
            Arguments.NON, /*OP_FINISH*/
            Arguments.NON, /*OP_END*/
            Arguments.SPECIAL, /*OP_EXACT1*/
            Arguments.SPECIAL, /*OP_EXACT2*/
            Arguments.SPECIAL, /*OP_EXACT3*/
            Arguments.SPECIAL, /*OP_EXACT4*/
            Arguments.SPECIAL, /*OP_EXACT5*/
            Arguments.SPECIAL, /*OP_EXACTN*/
            Arguments.SPECIAL, /*OP_EXACTMB2N1*/
            Arguments.SPECIAL, /*OP_EXACTMB2N2*/
            Arguments.SPECIAL, /*OP_EXACTMB2N3*/
            Arguments.SPECIAL, /*OP_EXACTMB2N*/
            Arguments.SPECIAL, /*OP_EXACTMB3N*/
            Arguments.SPECIAL, /*OP_EXACTMBN*/
            Arguments.SPECIAL, /*OP_EXACT1_IC*/
            Arguments.SPECIAL, /*OP_EXACTN_IC*/
            Arguments.SPECIAL, /*OP_CCLASS*/
            Arguments.SPECIAL, /*OP_CCLASS_MB*/
            Arguments.SPECIAL, /*OP_CCLASS_MIX*/
            Arguments.SPECIAL, /*OP_CCLASS_NOT*/
            Arguments.SPECIAL, /*OP_CCLASS_MB_NOT*/
            Arguments.SPECIAL, /*OP_CCLASS_MIX_NOT*/
            Arguments.SPECIAL, /*OP_CCLASS_NODE*/
            Arguments.NON, /*OP_ANYCHAR*/
            Arguments.NON, /*OP_ANYCHAR_ML*/
            Arguments.NON, /*OP_ANYCHAR_STAR*/
            Arguments.NON, /*OP_ANYCHAR_ML_STAR*/
            Arguments.SPECIAL, /*OP_ANYCHAR_STAR_PEEK_NEXT*/
            Arguments.SPECIAL, /*OP_ANYCHAR_ML_STAR_PEEK_NEXT*/
            Arguments.NON, /*OP_WORD*/
            Arguments.NON, /*OP_NOT_WORD*/
            Arguments.NON, /*OP_WORD_BOUND*/
            Arguments.NON, /*OP_NOT_WORD_BOUND*/
            Arguments.NON, /*OP_WORD_BEGIN*/
            Arguments.NON, /*OP_WORD_END*/
            Arguments.NON, /*OP_BEGIN_BUF*/
            Arguments.NON, /*OP_END_BUF*/
            Arguments.NON, /*OP_BEGIN_LINE*/
            Arguments.NON, /*OP_END_LINE*/
            Arguments.NON, /*OP_SEMI_END_BUF*/
            Arguments.NON, /*OP_BEGIN_POSITION*/
            Arguments.NON, /*OP_BACKREF1*/
            Arguments.NON, /*OP_BACKREF2*/
            Arguments.MEMNUM, /*OP_BACKREFN*/
            Arguments.SPECIAL, /*OP_BACKREFN_IC*/
            Arguments.SPECIAL, /*OP_BACKREF_MULTI*/
            Arguments.SPECIAL, /*OP_BACKREF_MULTI_IC*/
            Arguments.SPECIAL, /*OP_BACKREF_AT_LEVEL*/
            Arguments.MEMNUM, /*OP_MEMORY_START*/
            Arguments.MEMNUM, /*OP_MEMORY_START_PUSH*/
            Arguments.MEMNUM, /*OP_MEMORY_END_PUSH*/
            Arguments.MEMNUM, /*OP_MEMORY_END_PUSH_REC*/
            Arguments.MEMNUM, /*OP_MEMORY_END*/
            Arguments.MEMNUM, /*OP_MEMORY_END_REC*/
            Arguments.NON, /*OP_FAIL*/
            Arguments.RELADDR, /*OP_JUMP*/
            Arguments.RELADDR, /*OP_PUSH*/
            Arguments.NON, /*OP_POP*/
            Arguments.SPECIAL, /*OP_PUSH_OR_JUMP_EXACT1*/
            Arguments.SPECIAL, /*OP_PUSH_IF_PEEK_NEXT*/
            Arguments.SPECIAL, /*OP_REPEAT*/
            Arguments.SPECIAL, /*OP_REPEAT_NG*/
            Arguments.MEMNUM, /*OP_REPEAT_INC*/
            Arguments.MEMNUM, /*OP_REPEAT_INC_NG*/
            Arguments.MEMNUM, /*OP_REPEAT_INC_SG*/
            Arguments.MEMNUM, /*OP_REPEAT_INC_NG_SG*/
            Arguments.MEMNUM, /*OP_NULL_CHECK_START*/
            Arguments.MEMNUM, /*OP_NULL_CHECK_END*/
            Arguments.MEMNUM, /*OP_NULL_CHECK_END_MEMST*/
            Arguments.MEMNUM, /*OP_NULL_CHECK_END_MEMST_PUSH*/
            Arguments.NON, /*OP_PUSH_POS*/
            Arguments.NON, /*OP_POP_POS*/
            Arguments.RELADDR, /*OP_PUSH_POS_NOT*/
            Arguments.NON, /*OP_FAIL_POS*/
            Arguments.NON, /*OP_PUSH_STOP_BT*/
            Arguments.NON, /*OP_POP_STOP_BT*/
            Arguments.SPECIAL, /*OP_LOOK_BEHIND*/
            Arguments.SPECIAL, /*OP_PUSH_LOOK_BEHIND_NOT*/
            Arguments.NON, /*OP_FAIL_LOOK_BEHIND_NOT*/
            Arguments.ABSADDR, /*OP_CALL*/
            Arguments.NON, /*OP_RETURN*/
            Arguments.SPECIAL, /*OP_STATE_CHECK_PUSH*/
            Arguments.SPECIAL, /*OP_STATE_CHECK_PUSH_OR_JUMP*/
            Arguments.STATE_CHECK, /*OP_STATE_CHECK*/
            Arguments.STATE_CHECK, /*OP_STATE_CHECK_ANYCHAR_STAR*/
            Arguments.STATE_CHECK, /*OP_STATE_CHECK_ANYCHAR_ML_STAR*/
            Arguments.OPTION, /*OP_SET_OPTION_PUSH*/
            Arguments.OPTION, /*OP_SET_OPTION*/

            // single byte versions
            Arguments.NON, /*OP_ANYCHAR*/
            Arguments.NON, /*OP_ANYCHAR_ML*/
            Arguments.NON, /*OP_ANYCHAR_STAR*/
            Arguments.NON, /*OP_ANYCHAR_ML_STAR*/
            Arguments.SPECIAL, /*OP_ANYCHAR_STAR_PEEK_NEXT*/
            Arguments.SPECIAL, /*OP_ANYCHAR_ML_STAR_PEEK_NEXT*/
            Arguments.STATE_CHECK, /*OP_STATE_CHECK_ANYCHAR_STAR*/
            Arguments.STATE_CHECK, /*OP_STATE_CHECK_ANYCHAR_ML_STAR*/

            Arguments.SPECIAL, /*OP_CCLASS*/
            Arguments.SPECIAL, /*OP_CCLASS_NOT*/

            Arguments.NON, /*OP_WORD*/
            Arguments.NON, /*OP_NOT_WORD*/
            Arguments.NON, /*OP_WORD_BOUND*/
            Arguments.NON, /*OP_NOT_WORD_BOUND*/
            Arguments.NON, /*OP_WORD_BEGIN*/
            Arguments.NON, /*OP_WORD_END*/

            Arguments.SPECIAL, /*OP_LOOK_BEHIND*/

            Arguments.SPECIAL, /*OP_EXACT1_IC*/
            Arguments.SPECIAL, /*OP_EXACTN_IC*/
    };

    public ByteCodePrinter(Regex regex) {
        code = regex.code;
        codeLength = regex.codeLength;
        operands = regex.operands;
        operantCount = regex.operandLength;

        templates = regex.templates;
        warnings = regex.warnings;
    }

    public String byteCodeListToString() {
        return compiledByteCodeListToString();
    }

    private void pString(StringBuilder sb, int len, int s) {
        sb.append(":");
        while (len-- > 0) sb.append(new String(new byte[]{(byte)code[s++]}));
    }

    private void pStringFromTemplate(StringBuilder sb, int len, byte[]tm, int idx) {
        sb.append(":T:");
        while (len-- > 0) sb.append(new String(new byte[]{tm[idx++]}));
    }

    private void pLenString(StringBuilder sb, int len, int mbLen, int s) {
        int x = len * mbLen;
        sb.append(":" + len + ":");
        while (x-- > 0) sb.append(new String(new byte[]{(byte)code[s++]}));
    }

    private void pLenStringFromTemplate(StringBuilder sb, int len, int mbLen, char[] tm, int idx) {
        int x = len * mbLen;
        sb.append(":T:" + len + ":");
        while (x-- > 0) sb.append(new String(new byte[]{(byte)tm[idx++]}));
    }

    public int compiledByteCodeToString(StringBuilder sb, int bp) {
        int len, n, mem, addr, scn, cod;
        BitSet bs;
        CClassNode cc;
        int tm, idx;

        sb.append("[" + OpCodeNames[code[bp]]);
        int argType = OpCodeArgTypes[code[bp]];
        int ip = bp;
        if (argType != Arguments.SPECIAL) {
            bp++;
            switch (argType) {
            case Arguments.NON:
                break;

            case Arguments.RELADDR:
                sb.append(":(" + code[bp] + ")");
                bp += OPSize.RELADDR;
                break;

            case Arguments.ABSADDR:
                sb.append(":(" + code[bp] + ")");
                bp += OPSize.ABSADDR;
                break;

            case Arguments.LENGTH:
                sb.append(":" + code[bp]);
                bp += OPSize.LENGTH;
                break;

            case Arguments.MEMNUM:
                sb.append(":" + code[bp]);
                bp += OPSize.MEMNUM;
                break;

            case Arguments.OPTION:
                sb.append(":" + code[bp]);
                bp += OPSize.OPTION;
                break;

            case Arguments.STATE_CHECK:
                sb.append(":" + code[bp]);
                bp += OPSize.STATE_CHECK;
                break;
            }
        } else {
            switch (code[bp++]) {
            case OPCode.EXACT1:
            case OPCode.ANYCHAR_STAR_PEEK_NEXT:
            case OPCode.ANYCHAR_ML_STAR_PEEK_NEXT:
            case OPCode.ANYCHAR_STAR_PEEK_NEXT_SB:
            case OPCode.ANYCHAR_ML_STAR_PEEK_NEXT_SB:
                pString(sb, 1, bp++);
                break;

            case OPCode.EXACT2:
                pString(sb, 2, bp);
                bp += 2;
                break;

            case OPCode.EXACT3:
                pString(sb, 3, bp);
                bp += 3;
                break;

            case OPCode.EXACT4:
                pString(sb, 4, bp);
                bp += 4;
                break;

            case OPCode.EXACT5:
                pString(sb, 5, bp);
                bp += 5;
                break;

            case OPCode.EXACTN:
                len = code[bp];
                bp += OPSize.LENGTH;
                if (Config.USE_STRING_TEMPLATES) {
                    tm = code[bp];
                    bp += OPSize.INDEX;
                    idx = code[bp];
                    bp += OPSize.INDEX;
                    pLenStringFromTemplate(sb, len, 1, templates[tm], idx);
                } else {
                    pLenString(sb, len, 1, bp);
                    bp += len;
                }
                break;

            case OPCode.EXACTMB2N1:
                pString(sb, 2, bp);
                bp += 2;
                break;

            case OPCode.EXACTMB2N2:
                pString(sb, 4, bp);
                bp += 4;
                break;

            case OPCode.EXACTMB2N3:
                pString(sb, 6, bp);
                bp += 6;
                break;

            case OPCode.EXACTMB2N:
                len = code[bp];
                bp += OPSize.LENGTH;
                if (Config.USE_STRING_TEMPLATES) {
                    tm = code[bp];
                    bp += OPSize.INDEX;
                    idx = code[bp];
                    bp += OPSize.INDEX;
                    pLenStringFromTemplate(sb, len, 2, templates[tm], idx);
                } else {
                    pLenString(sb, len, 2, bp);
                    bp += len * 2;
                }
                break;

            case OPCode.EXACTMB3N:
                len = code[bp];
                bp += OPSize.LENGTH;
                if (Config.USE_STRING_TEMPLATES) {
                    tm = code[bp];
                    bp += OPSize.INDEX;
                    idx = code[bp];
                    bp += OPSize.INDEX;
                    pLenStringFromTemplate(sb, len, 3, templates[tm], idx);
                } else {
                    pLenString(sb, len, 3, bp);
                    bp += len * 3;
                }
                break;

            case OPCode.EXACTMBN:
                int mbLen = code[bp];
                bp += OPSize.LENGTH;
                len = code[bp];
                bp += OPSize.LENGTH;
                n = len * mbLen;

                if (Config.USE_STRING_TEMPLATES) {
                    tm = code[bp];
                    bp += OPSize.INDEX;
                    idx = code[bp];
                    bp += OPSize.INDEX;
                    sb.append(":T:" + mbLen + ":" + len + ":");

                    while (n-- > 0) sb.append(new String(new char[]{templates[tm][idx++]}));
                } else {
                    sb.append(":" + mbLen + ":" + len + ":");

                    while (n-- > 0) sb.append(new String(new byte[]{(byte)code[bp++]}));
                }

                break;

            case OPCode.EXACT1_IC:
            case OPCode.EXACT1_IC_SB:
                final int MAX_CHAR_LENGTH = 6;
                byte[]bytes = new byte[MAX_CHAR_LENGTH];
                for (int i = 0; bp + i < code.length && i < MAX_CHAR_LENGTH; i++) bytes[i] = (byte)code[bp + i];
                pString(sb, 1, bp);
                bp++;
                break;

            case OPCode.EXACTN_IC:
            case OPCode.EXACTN_IC_SB:
                len = code[bp];
                bp += OPSize.LENGTH;
                if (Config.USE_STRING_TEMPLATES) {
                    tm = code[bp];
                    bp += OPSize.INDEX;
                    idx = code[bp];
                    bp += OPSize.INDEX;
                    pLenStringFromTemplate(sb, len, 1, templates[tm], idx);
                } else {
                    pLenString(sb, len, 1, bp);
                    bp += len;
                }
                break;

            case OPCode.CCLASS:
            case OPCode.CCLASS_SB:
                bs = new BitSet();
                System.arraycopy(code, bp, bs.bits, 0, BitSet.BITSET_SIZE);
                n = bs.numOn();
                bp += BitSet.BITSET_SIZE;
                sb.append(":" + n);
                break;

            case OPCode.CCLASS_NOT:
            case OPCode.CCLASS_NOT_SB:
                bs = new BitSet();
                System.arraycopy(code, bp, bs.bits, 0, BitSet.BITSET_SIZE);
                n = bs.numOn();
                bp += BitSet.BITSET_SIZE;
                sb.append(":" + n);
                break;

            case OPCode.CCLASS_MB:
            case OPCode.CCLASS_MB_NOT:
                len = code[bp];
                bp += OPSize.LENGTH;
                cod = code[bp];
                //bp += OPSize.CODE_POINT;
                bp += len;
                sb.append(":" + cod + ":" + len);
                break;

            case OPCode.CCLASS_MIX:
            case OPCode.CCLASS_MIX_NOT:
                bs = new BitSet();
                System.arraycopy(code, bp, bs.bits, 0, BitSet.BITSET_SIZE);
                n = bs.numOn();
                bp += BitSet.BITSET_SIZE;
                len = code[bp];
                bp += OPSize.LENGTH;
                cod = code[bp];
                //bp += OPSize.CODE_POINT;
                bp += len;
                sb.append(":" + n + ":" + cod + ":" + len);
                break;

            case OPCode.CCLASS_NODE:
                cc = (CClassNode)operands[code[bp]];
                bp += OPSize.POINTER;
                n = cc.bs.numOn();
                sb.append(":" + cc + ":" + n);
                break;

            case OPCode.BACKREFN_IC:
                mem = code[bp];
                bp += OPSize.MEMNUM;
                sb.append(":" + mem);
                break;

            case OPCode.BACKREF_MULTI_IC:
            case OPCode.BACKREF_MULTI:
                sb.append(" ");
                len = code[bp];
                bp += OPSize.LENGTH;
                for (int i=0; i<len; i++) {
                    mem = code[bp];
                    bp += OPSize.MEMNUM;
                    if (i > 0) sb.append(", ");
                    sb.append(mem);
                }
                break;

            case OPCode.BACKREF_WITH_LEVEL: {
                int option = code[bp];
                bp += OPSize.OPTION;
                sb.append(":" + option);
                int level = code[bp];
                bp += OPSize.LENGTH;
                sb.append(":" + level);
                sb.append(" ");
                len = code[bp];
                bp += OPSize.LENGTH;
                for (int i=0; i<len; i++) {
                    mem = code[bp];
                    bp += OPSize.MEMNUM;
                    if (i > 0) sb.append(", ");
                    sb.append(mem);
                }
                break;
            }

            case OPCode.REPEAT:
            case OPCode.REPEAT_NG:
                mem = code[bp];
                bp += OPSize.MEMNUM;
                addr = code[bp];
                bp += OPSize.RELADDR;
                sb.append(":" + mem + ":" + addr);
                break;

            case OPCode.PUSH_OR_JUMP_EXACT1:
            case OPCode.PUSH_IF_PEEK_NEXT:
                addr = code[bp];
                bp += OPSize.RELADDR;
                sb.append(":(" + addr + ")");
                pString(sb, 1, bp);
                bp++;
                break;

            case OPCode.LOOK_BEHIND:
            case OPCode.LOOK_BEHIND_SB:
                len = code[bp];
                bp += OPSize.LENGTH;
                sb.append(":" + len);
                break;

            case OPCode.PUSH_LOOK_BEHIND_NOT:
                addr = code[bp];
                bp += OPSize.RELADDR;
                len = code[bp];
                bp += OPSize.LENGTH;
                sb.append(":" + len + ":(" + addr + ")");
                break;

            case OPCode.STATE_CHECK_PUSH:
            case OPCode.STATE_CHECK_PUSH_OR_JUMP:
                scn = code[bp];
                bp += OPSize.STATE_CHECK_NUM;
                addr = code[bp];
                bp += OPSize.RELADDR;
                sb.append(":" + scn + ":(" + addr + ")");
                break;

            default:
                throw new InternalException("undefined code: " + code[--bp]);
            }
        }

        sb.append("]");

        // @opcode_address(opcode_size)
        if (Config.DEBUG_COMPILE_BYTE_CODE_INFO) sb.append("@" + ip + "(" + (bp - ip) + ")");

        return bp;
    }

    private String compiledByteCodeListToString() {
        StringBuilder sb = new StringBuilder();
        sb.append("code length: " + codeLength + "\n");

        int ncode = 0;
        int bp = 0;
        int end = codeLength;

        while (bp < end) {
            ncode++;

            if (bp > 0) sb.append(ncode % 5 == 0 ? "\n" : " ");

            bp = compiledByteCodeToString(sb, bp);
        }
        sb.append("\n");
        return sb.toString();
    }
}
