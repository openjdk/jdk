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

        sb.append("[" + OPCode.OpCodeNames[code[bp]]);
        int argType = OPCode.OpCodeArgTypes[code[bp]];
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
