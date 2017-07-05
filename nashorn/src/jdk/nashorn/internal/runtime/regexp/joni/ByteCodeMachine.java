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
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isFindCondition;
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isFindLongest;
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isFindNotEmpty;
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isNotBol;
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isNotEol;
import static jdk.nashorn.internal.runtime.regexp.joni.Option.isPosixRegion;
import static jdk.nashorn.internal.runtime.regexp.joni.EncodingHelper.isNewLine;

import jdk.nashorn.internal.runtime.regexp.joni.ast.CClassNode;
import jdk.nashorn.internal.runtime.regexp.joni.constants.OPCode;
import jdk.nashorn.internal.runtime.regexp.joni.constants.OPSize;
import jdk.nashorn.internal.runtime.regexp.joni.encoding.IntHolder;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ErrorMessages;
import jdk.nashorn.internal.runtime.regexp.joni.exception.InternalException;

class ByteCodeMachine extends StackMachine {
    private int bestLen;          // return value
    private int s = 0;            // current char

    private int range;            // right range
    private int sprev;
    private int sstart;
    private int sbegin;

    private final int[] code;       // byte code
    private int ip;                 // instruction pointer

    ByteCodeMachine(Regex regex, char[] chars, int p, int end) {
        super(regex, chars, p, end);
        this.code = regex.code;
    }

    private boolean stringCmpIC(int caseFlodFlag, int s1, IntHolder ps2, int mbLen, int textEnd) {

        int s2 = ps2.value;
        int end1 = s1 + mbLen;

        while (s1 < end1) {
            char c1 = Character.toLowerCase(chars[s1++]);
            char c2 = Character.toLowerCase(chars[s2++]);

            if (c1 != c2) {
                return false;
            }
        }
        ps2.value = s2;
        return true;
    }

    private void debugMatchBegin() {
        Config.log.println("match_at: " +
                "str: " + str +
                ", end: " + end +
                ", start: " + this.sstart +
                ", sprev: " + this.sprev);
        Config.log.println("size: " + (end - str) + ", start offset: " + (this.sstart - str));
    }

    private void debugMatchLoop() {
        if (Config.DEBUG_MATCH) {
            Config.log.printf("%4d", (s - str)).print("> \"");
            int q, i;
            for (i=0, q=s; i<7 && q<end && s>=0; i++) {
                if (q < end) Config.log.print(new String(new char[]{chars[q++]}));
            }
            String str = q < end ? "...\"" : "\"";
            q += str.length();
            Config.log.print(str);
            for (i=0; i<20-(q-s);i++) Config.log.print(" ");
            StringBuilder sb = new StringBuilder();
            new ByteCodePrinter(regex).compiledByteCodeToString(sb, ip);
            Config.log.println(sb.toString());
        }
    }

    @Override
    protected final int matchAt(int range, int sstart, int sprev) {
        this.range = range;
        this.sstart = sstart;
        this.sprev = sprev;

        stk = 0;
        ip = 0;

        if (Config.DEBUG_MATCH) debugMatchBegin();

        init();

        bestLen = -1;
        s = sstart;

        final int[]code = this.code;
        while (true) {
            if (Config.DEBUG_MATCH) debugMatchLoop();

            sbegin = s;
            switch (code[ip++]) {
                case OPCode.END:    if (opEnd()) return finish();                  break;
                case OPCode.EXACT1:                     opExact1();                break;
                case OPCode.EXACT2:                     opExact2();                continue;
                case OPCode.EXACT3:                     opExact3();                continue;
                case OPCode.EXACT4:                     opExact4();                continue;
                case OPCode.EXACT5:                     opExact5();                continue;
                case OPCode.EXACTN:                     opExactN();                continue;

                case OPCode.EXACT1_IC:                  opExact1IC();              break;
                case OPCode.EXACTN_IC:                  opExactNIC();              continue;

                case OPCode.CCLASS:                     opCClass();                break;
                case OPCode.CCLASS_MB:                  opCClassMB();              break;
                case OPCode.CCLASS_MIX:                 opCClassMIX();             break;
                case OPCode.CCLASS_NOT:                 opCClassNot();             break;
                case OPCode.CCLASS_MB_NOT:              opCClassMBNot();           break;
                case OPCode.CCLASS_MIX_NOT:             opCClassMIXNot();          break;
                case OPCode.CCLASS_NODE:                opCClassNode();            break;

                case OPCode.ANYCHAR:                    opAnyChar();               break;
                case OPCode.ANYCHAR_ML:                 opAnyCharML();             break;
                case OPCode.ANYCHAR_STAR:               opAnyCharStar();           break;
                case OPCode.ANYCHAR_ML_STAR:            opAnyCharMLStar();         break;
                case OPCode.ANYCHAR_STAR_PEEK_NEXT:     opAnyCharStarPeekNext();   break;
                case OPCode.ANYCHAR_ML_STAR_PEEK_NEXT:  opAnyCharMLStarPeekNext(); break;

                case OPCode.WORD:                       opWord();                  break;
                case OPCode.NOT_WORD:                   opNotWord();               break;
                case OPCode.WORD_BOUND:                 opWordBound();             continue;
                case OPCode.NOT_WORD_BOUND:             opNotWordBound();          continue;
                case OPCode.WORD_BEGIN:                 opWordBegin();             continue;
                case OPCode.WORD_END:                   opWordEnd();               continue;

                case OPCode.BEGIN_BUF:                  opBeginBuf();              continue;
                case OPCode.END_BUF:                    opEndBuf();                continue;
                case OPCode.BEGIN_LINE:                 opBeginLine();             continue;
                case OPCode.END_LINE:                   opEndLine();               continue;
                case OPCode.SEMI_END_BUF:               opSemiEndBuf();            continue;
                case OPCode.BEGIN_POSITION:             opBeginPosition();         continue;

                case OPCode.MEMORY_START_PUSH:          opMemoryStartPush();       continue;
                case OPCode.MEMORY_START:               opMemoryStart();           continue;
                case OPCode.MEMORY_END_PUSH:            opMemoryEndPush();         continue;
                case OPCode.MEMORY_END:                 opMemoryEnd();             continue;
                case OPCode.MEMORY_END_PUSH_REC:        opMemoryEndPushRec();      continue;
                case OPCode.MEMORY_END_REC:             opMemoryEndRec();          continue;

                case OPCode.BACKREF1:                   opBackRef1();              continue;
                case OPCode.BACKREF2:                   opBackRef2();              continue;
                case OPCode.BACKREFN:                   opBackRefN();              continue;
                case OPCode.BACKREFN_IC:                opBackRefNIC();            continue;
                case OPCode.BACKREF_MULTI:              opBackRefMulti();          continue;
                case OPCode.BACKREF_MULTI_IC:           opBackRefMultiIC();        continue;
                case OPCode.BACKREF_WITH_LEVEL:         opBackRefAtLevel();        continue;

                case OPCode.NULL_CHECK_START:           opNullCheckStart();        continue;
                case OPCode.NULL_CHECK_END:             opNullCheckEnd();          continue;
                case OPCode.NULL_CHECK_END_MEMST:       opNullCheckEndMemST();     continue;
                case OPCode.NULL_CHECK_END_MEMST_PUSH:  opNullCheckEndMemSTPush(); continue;

                case OPCode.JUMP:                       opJump();                  continue;
                case OPCode.PUSH:                       opPush();                  continue;

                case OPCode.POP:                        opPop();                   continue;
                case OPCode.PUSH_OR_JUMP_EXACT1:        opPushOrJumpExact1();      continue;
                case OPCode.PUSH_IF_PEEK_NEXT:          opPushIfPeekNext();        continue;

                case OPCode.REPEAT:                     opRepeat();                continue;
                case OPCode.REPEAT_NG:                  opRepeatNG();              continue;
                case OPCode.REPEAT_INC:                 opRepeatInc();             continue;
                case OPCode.REPEAT_INC_SG:              opRepeatIncSG();           continue;
                case OPCode.REPEAT_INC_NG:              opRepeatIncNG();           continue;
                case OPCode.REPEAT_INC_NG_SG:           opRepeatIncNGSG();         continue;

                case OPCode.PUSH_POS:                   opPushPos();               continue;
                case OPCode.POP_POS:                    opPopPos();                continue;
                case OPCode.PUSH_POS_NOT:               opPushPosNot();            continue;
                case OPCode.FAIL_POS:                   opFailPos();               continue;
                case OPCode.PUSH_STOP_BT:               opPushStopBT();            continue;
                case OPCode.POP_STOP_BT:                opPopStopBT();             continue;

                case OPCode.LOOK_BEHIND:                opLookBehind();            continue;
                case OPCode.PUSH_LOOK_BEHIND_NOT:       opPushLookBehindNot();     continue;
                case OPCode.FAIL_LOOK_BEHIND_NOT:       opFailLookBehindNot();     continue;

                case OPCode.FINISH:
                    return finish();

                case OPCode.FAIL:                       opFail();                  continue;

                default:
                    throw new InternalException(ErrorMessages.ERR_UNDEFINED_BYTECODE);

            } // main switch
        } // main while
    }

    private boolean opEnd() {
        int n = s - sstart;

        if (n > bestLen) {
            if (Config.USE_FIND_LONGEST_SEARCH_ALL_OF_RANGE) {
                if (isFindLongest(regex.options)) {
                    if (n > msaBestLen) {
                        msaBestLen = n;
                        msaBestS = sstart;
                    } else {
                        // goto end_best_len;
                        return endBestLength();
                    }
                }
            } // USE_FIND_LONGEST_SEARCH_ALL_OF_RANGE

            bestLen = n;
            final Region region = msaRegion;
            if (region != null) {
                // USE_POSIX_REGION_OPTION ... else ...
                region.beg[0] = msaBegin = sstart - str;
                region.end[0] = msaEnd   = s      - str;
                for (int i = 1; i <= regex.numMem; i++) {
                    // opt!
                    if (repeatStk[memEndStk + i] != INVALID_INDEX) {
                        region.beg[i] = bsAt(regex.btMemStart, i) ?
                                        stack[repeatStk[memStartStk + i]].getMemPStr() - str :
                                        repeatStk[memStartStk + i] - str;


                        region.end[i] = bsAt(regex.btMemEnd, i) ?
                                        stack[repeatStk[memEndStk + i]].getMemPStr() :
                                        repeatStk[memEndStk + i] - str;

                    } else {
                        region.beg[i] = region.end[i] = Region.REGION_NOTPOS;
                    }

                }

            } else {
                msaBegin = sstart - str;
                msaEnd   = s      - str;
            }
        } else {
            Region region = msaRegion;
            if (Config.USE_POSIX_API_REGION_OPTION) {
                if (!isPosixRegion(regex.options)) {
                    if (region != null) {
                        region.clear();
                    } else {
                        msaBegin = msaEnd = 0;
                    }
                }
            } else {
                if (region != null) {
                    region.clear();
                } else {
                    msaBegin = msaEnd = 0;
                }
            } // USE_POSIX_REGION_OPTION
        }
        // end_best_len:
        /* default behavior: return first-matching result. */
        return endBestLength();
    }

    private boolean endBestLength() {
        if (isFindCondition(regex.options)) {
            if (isFindNotEmpty(regex.options) && s == sstart) {
                bestLen = -1;
                {opFail(); return false;} /* for retry */
            }
            if (isFindLongest(regex.options) && s < range) {
                {opFail(); return false;} /* for retry */
            }
        }
        // goto finish;
        return true;
    }

    private void opExact1() {
        if (s >= range || code[ip] != chars[s++]) {opFail(); return;}
        //if (s > range) {opFail(); return;}
        ip++;
        sprev = sbegin; // break;
    }

    private void opExact2() {
        if (s + 2 > range) {opFail(); return;}
        if (code[ip] != chars[s]) {opFail(); return;}
        ip++; s++;
        if (code[ip] != chars[s]) {opFail(); return;}
        sprev = s;
        ip++; s++;
    }

    private void opExact3() {
        if (s + 3 > range) {opFail(); return;}
        if (code[ip] != chars[s]) {opFail(); return;}
        ip++; s++;
        if (code[ip] != chars[s]) {opFail(); return;}
        ip++; s++;
        if (code[ip] != chars[s]) {opFail(); return;}
        sprev = s;
        ip++; s++;
    }

    private void opExact4() {
        if (s + 4 > range) {opFail(); return;}
        if (code[ip] != chars[s]) {opFail(); return;}
        ip++; s++;
        if (code[ip] != chars[s]) {opFail(); return;}
        ip++; s++;
        if (code[ip] != chars[s]) {opFail(); return;}
        ip++; s++;
        if (code[ip] != chars[s]) {opFail(); return;}
        sprev = s;
        ip++; s++;
    }

    private void opExact5() {
        if (s + 5 > range) {opFail(); return;}
        if (code[ip] != chars[s]) {opFail(); return;}
        ip++; s++;
        if (code[ip] != chars[s]) {opFail(); return;}
        ip++; s++;
        if (code[ip] != chars[s]) {opFail(); return;}
        ip++; s++;
        if (code[ip] != chars[s]) {opFail(); return;}
        ip++; s++;
        if (code[ip] != chars[s]) {opFail(); return;}
        sprev = s;
        ip++; s++;
    }

    private void opExactN() {
        int tlen = code[ip++];
        if (s + tlen > range) {opFail(); return;}

        if (Config.USE_STRING_TEMPLATES) {
            char[] bs = regex.templates[code[ip++]];
            int ps = code[ip++];

            while (tlen-- > 0) if (bs[ps++] != chars[s++]) {opFail(); return;}

        } else {
            while (tlen-- > 0) if (code[ip++] != chars[s++]) {opFail(); return;}
        }
        sprev = s - 1;
    }

    private void opExact1IC() {
        if (s >= range || code[ip] != Character.toLowerCase(chars[s++])) {opFail(); return;}
        ip++;
        sprev = sbegin; // break;
    }

    private void opExactNIC() {
        int tlen = code[ip++];
        if (s + tlen > range) {opFail(); return;}

        if (Config.USE_STRING_TEMPLATES) {
            char[] bs = regex.templates[code[ip++]];
            int ps = code[ip++];

            while (tlen-- > 0) if (bs[ps++] != Character.toLowerCase(chars[s++])) {opFail(); return;}
        } else {

            while (tlen-- > 0) if (code[ip++] != Character.toLowerCase(chars[s++])) {opFail(); return;}
        }
        sprev = s - 1;
    }

    private boolean isInBitSet() {
        int c = chars[s];
        return (c <= 0xff && (code[ip + (c >>> BitSet.ROOM_SHIFT)] & (1 << c)) != 0);
    }

    private void opCClass() {
        if (s >= range || !isInBitSet()) {opFail(); return;}
        ip += BitSet.BITSET_SIZE;
        s++;
        sprev = sbegin; // break;
    }

    private boolean isInClassMB() {
        int tlen = code[ip++];
        if (s >= range) return false;
        int ss = s;
        s++;
        int c = chars[ss];
        if (!EncodingHelper.isInCodeRange(code, ip, c)) return false;
        ip += tlen;
        return true;
    }

    private void opCClassMB() {
        // beyond string check
        if (s >= range || chars[s] <= 0xff) {opFail(); return;}
        if (!isInClassMB()) {opFail(); return;} // not!!!
        sprev = sbegin; // break;
    }

    private void opCClassMIX() {
        if (s >= range) {opFail(); return;}
        if (chars[s] > 0xff) {
            ip += BitSet.BITSET_SIZE;
            if (!isInClassMB()) {opFail(); return;}
        } else {
            if (!isInBitSet()) {opFail(); return;}
            ip += BitSet.BITSET_SIZE;
            int tlen = code[ip++]; // by code range length
            ip += tlen;
            s++;
        }
        sprev = sbegin; // break;
    }

    private void opCClassNot() {
        if (s >= range || isInBitSet()) {opFail(); return;}
        ip += BitSet.BITSET_SIZE;
        s++;
        sprev = sbegin; // break;
    }

    private boolean isNotInClassMB() {
        int tlen = code[ip++];

        if (!(s + 1 <= range)) {
            if (s >= range) return false;
            s = end;
            ip += tlen;
            return true;
        }

        int ss = s;
        s++;
        int c = chars[ss];

        if (EncodingHelper.isInCodeRange(code, ip, c)) return false;
        ip += tlen;
        return true;
    }

    private void opCClassMBNot() {
        if (s >= range) {opFail(); return;}
        if (chars[s] <= 0xff) {
            s++;
            int tlen = code[ip++];
            ip += tlen;
            sprev = sbegin; // break;
            return;
        }
        if (!isNotInClassMB()) {opFail(); return;}
        sprev = sbegin; // break;
    }

    private void opCClassMIXNot() {
        if (s >= range) {opFail(); return;}
        if (chars[s] > 0xff) {
            ip += BitSet.BITSET_SIZE;
            if (!isNotInClassMB()) {opFail(); return;}
        } else {
            if (isInBitSet()) {opFail(); return;}
            ip += BitSet.BITSET_SIZE;
            int tlen = code[ip++];
            ip += tlen;
            s++;
        }
        sprev = sbegin; // break;
    }

    private void opCClassNode() {
        if (s >= range) {opFail(); return;}
        CClassNode cc = (CClassNode)regex.operands[code[ip++]];
        int ss = s;
        s++;
        int c = chars[ss];
        if (!cc.isCodeInCCLength(c)) {opFail(); return;}
        sprev = sbegin; // break;
    }

    private void opAnyChar() {
        if (s >= range) {opFail(); return;}
        if (isNewLine(chars[s])) {opFail(); return;}
        s++;
        sprev = sbegin; // break;
    }

    private void opAnyCharML() {
        if (s >= range) {opFail(); return;}
        s++;
        sprev = sbegin; // break;
    }

    private void opAnyCharStar() {
        final char[] chars = this.chars;
        while (s < range) {
            pushAlt(ip, s, sprev);
            if (isNewLine(chars, s, end)) {opFail(); return;}
            sprev = s;
            s++;
        }
        sprev = sbegin; // break;
    }

    private void opAnyCharMLStar() {
        while (s < range) {
            pushAlt(ip, s, sprev);
            sprev = s;
            s++;
        }
        sprev = sbegin; // break;
    }

    private void opAnyCharStarPeekNext() {
        final char c = (char)code[ip];
        final char[] chars = this.chars;

        while (s < range) {
            char b = chars[s];
            if (c == b) pushAlt(ip + 1, s, sprev);
            if (isNewLine(b)) {opFail(); return;}
            sprev = s;
            s++;
        }
        ip++;
        sprev = sbegin; // break;
    }

    private void opAnyCharMLStarPeekNext() {
        final char c = (char)code[ip];
        final char[] chars = this.chars;

        while (s < range) {
            if (c == chars[s]) pushAlt(ip + 1, s, sprev);
            sprev = s;
            s++;
        }
        ip++;
        sprev = sbegin; // break;
    }

    private void opWord() {
        if (s >= range || !EncodingHelper.isWord(chars[s])) {opFail(); return;}
        s++;
        sprev = sbegin; // break;
    }

    private void opNotWord() {
        if (s >= range || EncodingHelper.isWord(chars[s])) {opFail(); return;}
        s++;
        sprev = sbegin; // break;
    }

    private void opWordBound() {
        if (s == str) {
            if (s >= range || !EncodingHelper.isWord(chars[s])) {opFail(); return;}
        } else if (s == end) {
            if (sprev >= end || !EncodingHelper.isWord(chars[sprev])) {opFail(); return;}
        } else {
            if (EncodingHelper.isWord(chars[s]) == EncodingHelper.isWord(chars[sprev])) {opFail(); return;}
        }
    }

    private void opNotWordBound() {
        if (s == str) {
            if (s < range && EncodingHelper.isWord(chars[s])) {opFail(); return;}
        } else if (s == end) {
            if (sprev < end && EncodingHelper.isWord(chars[sprev])) {opFail(); return;}
        } else {
            if (EncodingHelper.isWord(chars[s]) != EncodingHelper.isWord(chars[sprev])) {opFail(); return;}
        }
    }

    private void opWordBegin() {
        if (s < range && EncodingHelper.isWord(chars[s])) {
            if (s == str || !EncodingHelper.isWord(chars[sprev])) return;
        }
        opFail();
    }

    private void opWordEnd() {
        if (s != str && EncodingHelper.isWord(chars[sprev])) {
            if (s == end || !EncodingHelper.isWord(chars[s])) return;
        }
        opFail();
    }

    private void opBeginBuf() {
        if (s != str) opFail();
    }

    private void opEndBuf() {
        if (s != end) opFail();
    }

    private void opBeginLine() {
        if (s == str) {
            if (isNotBol(msaOptions)) opFail();
            return;
        } else if (isNewLine(chars, sprev, end) && s != end) {
            return;
        }
        opFail();
    }

    private void opEndLine()  {
        if (s == end) {
            if (Config.USE_NEWLINE_AT_END_OF_STRING_HAS_EMPTY_LINE) {
                if (str == end || !isNewLine(chars, sprev, end)) {
                    if (isNotEol(msaOptions)) opFail();
                }
                return;
            } else {
                if (isNotEol(msaOptions)) opFail();
                return;
            }
        } else if (isNewLine(chars, s, end)) {
            return;
        }
        opFail();
    }

    private void opSemiEndBuf() {
        if (s == end) {
            if (Config.USE_NEWLINE_AT_END_OF_STRING_HAS_EMPTY_LINE) {
                if (str == end || !isNewLine(chars, sprev, end)) {
                    if (isNotEol(msaOptions)) opFail();
                }
                return;
            } else {
                if (isNotEol(msaOptions)) opFail();
                return;
            }
        } else if (isNewLine(chars, s, end) && s + 1 == end) {
            return;
        }
        opFail();
    }

    private void opBeginPosition() {
        if (s != msaStart) opFail();
    }

    private void opMemoryStartPush() {
        int mem = code[ip++];
        pushMemStart(mem, s);
    }

    private void opMemoryStart() {
        int mem = code[ip++];
        repeatStk[memStartStk + mem] = s;
    }

    private void opMemoryEndPush() {
        int mem = code[ip++];
        pushMemEnd(mem, s);
    }

    private void opMemoryEnd() {
        int mem = code[ip++];
        repeatStk[memEndStk + mem] = s;
    }

    private void opMemoryEndPushRec() {
        int mem = code[ip++];
        int stkp = getMemStart(mem); /* should be before push mem-end. */
        pushMemEnd(mem, s);
        repeatStk[memStartStk + mem] = stkp;
    }

    private void opMemoryEndRec() {
        int mem = code[ip++];
        repeatStk[memEndStk + mem] = s;
        int stkp = getMemStart(mem);

        if (BitStatus.bsAt(regex.btMemStart, mem)) {
            repeatStk[memStartStk + mem] = stkp;
        } else {
            repeatStk[memStartStk + mem] = stack[stkp].getMemPStr();
        }

        pushMemEndMark(mem);
    }

    private boolean backrefInvalid(int mem) {
        return repeatStk[memEndStk + mem] == INVALID_INDEX || repeatStk[memStartStk + mem] == INVALID_INDEX;
    }

    private int backrefStart(int mem) {
        return bsAt(regex.btMemStart, mem) ? stack[repeatStk[memStartStk + mem]].getMemPStr() : repeatStk[memStartStk + mem];
    }

    private int backrefEnd(int mem) {
        return bsAt(regex.btMemEnd, mem) ? stack[repeatStk[memEndStk + mem]].getMemPStr() : repeatStk[memEndStk + mem];
    }

    private void backref(int mem) {
        /* if you want to remove following line,
        you should check in parse and compile time. (numMem) */
        if (mem > regex.numMem || backrefInvalid(mem)) {opFail(); return;}

        int pstart = backrefStart(mem);
        int pend = backrefEnd(mem);

        int n = pend - pstart;
        if (s + n > range) {opFail(); return;}
        sprev = s;

        // STRING_CMP
        while(n-- > 0) if (chars[pstart++] != chars[s++]) {opFail(); return;}

        // beyond string check
        if (sprev < range) {
            while (sprev + 1 < s) sprev++;
        }
    }

    private void opBackRef1() {
        backref(1);
    }

    private void opBackRef2() {
        backref(2);
    }

    private void opBackRefN() {
        backref(code[ip++]);
    }

    private void opBackRefNIC() {
        int mem = code[ip++];
        /* if you want to remove following line,
        you should check in parse and compile time. (numMem) */
        if (mem > regex.numMem || backrefInvalid(mem)) {opFail(); return;}

        int pstart = backrefStart(mem);
        int pend = backrefEnd(mem);

        int n = pend - pstart;
        if (s + n > range) {opFail(); return;}
        sprev = s;

        value = s;
        if (!stringCmpIC(regex.caseFoldFlag, pstart, this, n, end)) {opFail(); return;}
        s = value;

        // if (sprev < chars.length)
        while (sprev + 1 < s) sprev++;
    }

    private void opBackRefMulti() {
        int tlen = code[ip++];

        int i;
        loop:for (i=0; i<tlen; i++) {
            int mem = code[ip++];
            if (backrefInvalid(mem)) continue;

            int pstart = backrefStart(mem);
            int pend = backrefEnd(mem);

            int n = pend - pstart;
            if (s + n > range) {opFail(); return;}

            sprev = s;
            int swork = s;

            while (n-- > 0) {
                if (chars[pstart++] != chars[swork++]) continue loop;
            }

            s = swork;

            // beyond string check
            if (sprev < range) {
                while (sprev + 1 < s) sprev++;
            }

            ip += tlen - i  - 1; // * SIZE_MEMNUM (1)
            break; /* success */
        }
        if (i == tlen) {opFail(); return;}
    }

    private void opBackRefMultiIC() {
        int tlen = code[ip++];

        int i;
        loop:for (i=0; i<tlen; i++) {
            int mem = code[ip++];
            if (backrefInvalid(mem)) continue;

            int pstart = backrefStart(mem);
            int pend = backrefEnd(mem);

            int n = pend - pstart;
            if (s + n > range) {opFail(); return;}

            sprev = s;

            value = s;
            if (!stringCmpIC(regex.caseFoldFlag, pstart, this, n, end)) continue loop; // STRING_CMP_VALUE_IC
            s = value;

            // if (sprev < chars.length)
            while (sprev + 1 < s) sprev++;

            ip += tlen - i  - 1; // * SIZE_MEMNUM (1)
            break;  /* success */
        }
        if (i == tlen) {opFail(); return;}
    }

    private boolean memIsInMemp(int mem, int num, int memp) {
        for (int i=0; i<num; i++) {
            int m = code[memp++];
            if (mem == m) return true;
        }
        return false;
    }

    // USE_BACKREF_AT_LEVEL // (s) and (end) implicit
    private boolean backrefMatchAtNestedLevel(boolean ignoreCase, int caseFoldFlag,
                                              int nest, int memNum, int memp) {
        int pend = -1;
        int level = 0;
        int k = stk - 1;

        while (k >= 0) {
            StackEntry e = stack[k];

            if (e.type == CALL_FRAME) {
                level--;
            } else if (e.type == RETURN) {
                level++;
            } else if (level == nest) {
                if (e.type == MEM_START) {
                    if (memIsInMemp(e.getMemNum(), memNum, memp)) {
                        int pstart = e.getMemPStr();
                        if (pend != -1) {
                            if (pend - pstart > end - s) return false; /* or goto next_mem; */
                            int p = pstart;

                            value = s;
                            if (ignoreCase) {
                                if (!stringCmpIC(caseFoldFlag, pstart, this, pend - pstart, end)) {
                                    return false; /* or goto next_mem; */
                                }
                            } else {
                                while (p < pend) {
                                    if (chars[p++] != chars[value++]) return false; /* or goto next_mem; */
                                }
                            }
                            s = value;

                            return true;
                        }
                    }
                } else if (e.type == MEM_END) {
                    if (memIsInMemp(e.getMemNum(), memNum, memp)) {
                        pend = e.getMemPStr();
                    }
                }
            }
            k--;
        }
        return false;
    }

    private void opBackRefAtLevel() {
        int ic      = code[ip++];
        int level   = code[ip++];
        int tlen    = code[ip++];

        sprev = s;
        if (backrefMatchAtNestedLevel(ic != 0, regex.caseFoldFlag, level, tlen, ip)) { // (s) and (end) implicit
            while (sprev + 1 < s) sprev++;
            ip += tlen; // * SIZE_MEMNUM
        } else {
            {opFail(); return;}
        }
    }

    /* no need: IS_DYNAMIC_OPTION() == 0 */
    private void opSetOptionPush() {
        // option = code[ip++]; // final for now
        pushAlt(ip, s, sprev);
        ip += OPSize.SET_OPTION + OPSize.FAIL;
    }

    private void opSetOption() {
        // option = code[ip++]; // final for now
    }

    private void opNullCheckStart() {
        int mem = code[ip++];
        pushNullCheckStart(mem, s);
    }

    private void nullCheckFound() {
        // null_check_found:
        /* empty loop founded, skip next instruction */
        switch(code[ip++]) {
        case OPCode.JUMP:
        case OPCode.PUSH:
            ip++;       // p += SIZE_RELADDR;
            break;
        case OPCode.REPEAT_INC:
        case OPCode.REPEAT_INC_NG:
        case OPCode.REPEAT_INC_SG:
        case OPCode.REPEAT_INC_NG_SG:
            ip++;        // p += SIZE_MEMNUM;
            break;
        default:
            throw new InternalException(ErrorMessages.ERR_UNEXPECTED_BYTECODE);
        } // switch
    }

    private void opNullCheckEnd() {
        int mem = code[ip++];
        int isNull = nullCheck(mem, s); /* mem: null check id */

        if (isNull != 0) {
            if (Config.DEBUG_MATCH) {
                Config.log.println("NULL_CHECK_END: skip  id:" + mem + ", s:" + s);
            }

            nullCheckFound();
        }
    }

    // USE_INFINITE_REPEAT_MONOMANIAC_MEM_STATUS_CHECK
    private void opNullCheckEndMemST() {
        int mem = code[ip++];   /* mem: null check id */
        int isNull = nullCheckMemSt(mem, s);

        if (isNull != 0) {
            if (Config.DEBUG_MATCH) {
                Config.log.println("NULL_CHECK_END_MEMST: skip  id:" + mem + ", s:" + s);
            }

            if (isNull == -1) {opFail(); return;}
            nullCheckFound();
        }
    }

    // USE_SUBEXP_CALL
    private void opNullCheckEndMemSTPush() {
        int mem = code[ip++];   /* mem: null check id */

        int isNull;
        if (Config.USE_MONOMANIAC_CHECK_CAPTURES_IN_ENDLESS_REPEAT) {
            isNull = nullCheckMemStRec(mem, s);
        } else {
            isNull = nullCheckRec(mem, s);
        }

        if (isNull != 0) {
            if (Config.DEBUG_MATCH) {
                Config.log.println("NULL_CHECK_END_MEMST_PUSH: skip  id:" + mem + ", s:" + s);
            }

            if (isNull == -1) {opFail(); return;}
            nullCheckFound();
        } else {
            pushNullCheckEnd(mem);
        }
    }

    private void opJump() {
        ip += code[ip] + 1;
    }

    private void opPush() {
        int addr = code[ip++];
        pushAlt(ip + addr, s, sprev);
    }

    private void opPop() {
        popOne();
    }

    private void opPushOrJumpExact1() {
        int addr = code[ip++];
        // beyond string check
        if (s < range && code[ip] == chars[s]) {
            ip++;
            pushAlt(ip + addr, s, sprev);
            return;
        }
        ip += addr + 1;
    }

    private void opPushIfPeekNext() {
        int addr = code[ip++];
        // beyond string check
        if (s < range && code[ip] == chars[s]) {
            ip++;
            pushAlt(ip + addr, s, sprev);
            return;
        }
        ip++;
    }

    private void opRepeat() {
        int mem = code[ip++];   /* mem: OP_REPEAT ID */
        int addr= code[ip++];

        // ensure1();
        repeatStk[mem] = stk;
        pushRepeat(mem, ip);

        if (regex.repeatRangeLo[mem] == 0) { // lower
            pushAlt(ip + addr, s, sprev);
        }
    }

    private void opRepeatNG() {
        int mem = code[ip++];   /* mem: OP_REPEAT ID */
        int addr= code[ip++];

        // ensure1();
        repeatStk[mem] = stk;
        pushRepeat(mem, ip);

        if (regex.repeatRangeLo[mem] == 0) {
            pushAlt(ip, s, sprev);
            ip += addr;
        }
    }

    private void repeatInc(int mem, int si) {
        StackEntry e = stack[si];

        e.increaseRepeatCount();

        if (e.getRepeatCount() >= regex.repeatRangeHi[mem]) {
            /* end of repeat. Nothing to do. */
        } else if (e.getRepeatCount() >= regex.repeatRangeLo[mem]) {
            pushAlt(ip, s, sprev);
            ip = e.getRepeatPCode(); /* Don't use stkp after PUSH. */
        } else {
            ip = e.getRepeatPCode();
        }
        pushRepeatInc(si);
    }

    private void opRepeatInc() {
        int mem = code[ip++];   /* mem: OP_REPEAT ID */
        int si = repeatStk[mem];
        repeatInc(mem, si);
    }

    private void opRepeatIncSG() {
        int mem = code[ip++];   /* mem: OP_REPEAT ID */
        int si = getRepeat(mem);
        repeatInc(mem, si);
    }

    private void repeatIncNG(int mem, int si) {
        StackEntry e = stack[si];

        e.increaseRepeatCount();

        if (e.getRepeatCount() < regex.repeatRangeHi[mem]) {
            if (e.getRepeatCount() >= regex.repeatRangeLo[mem]) {
                int pcode = e.getRepeatPCode();
                pushRepeatInc(si);
                pushAlt(pcode, s, sprev);
            } else {
                ip = e.getRepeatPCode();
                pushRepeatInc(si);
            }
        } else if (e.getRepeatCount() == regex.repeatRangeHi[mem]) {
            pushRepeatInc(si);
        }
    }

    private void opRepeatIncNG() {
        int mem = code[ip++];
        int si = repeatStk[mem];
        repeatIncNG(mem, si);
    }

    private void opRepeatIncNGSG() {
        int mem = code[ip++];
        int si = getRepeat(mem);
        repeatIncNG(mem, si);
    }

    private void opPushPos() {
        pushPos(s, sprev);
    }

    private void opPopPos() {
        StackEntry e = stack[posEnd()];
        s    = e.getStatePStr();
        sprev= e.getStatePStrPrev();
    }

    private void opPushPosNot() {
        int addr = code[ip++];
        pushPosNot(ip + addr, s, sprev);
    }

    private void opFailPos() {
        popTilPosNot();
        opFail();
    }

    private void opPushStopBT() {
        pushStopBT();
    }

    private void opPopStopBT() {
        stopBtEnd();
    }

    private void opLookBehind() {
        int tlen = code[ip++];
        s = EncodingHelper.stepBack(str, s, tlen);
        if (s == -1) {opFail(); return;}
        sprev = EncodingHelper.prevCharHead(str, s);
    }

    private void opLookBehindSb() {
        int tlen = code[ip++];
        s -= tlen;
        if (s < str) {opFail(); return;}
        sprev = s == str ? -1 : s - 1;
    }

    private void opPushLookBehindNot() {
        int addr = code[ip++];
        int tlen = code[ip++];
        int q = EncodingHelper.stepBack(str, s, tlen);
        if (q == -1) {
            /* too short case -> success. ex. /(?<!XXX)a/.match("a")
            If you want to change to fail, replace following line. */
            ip += addr;
            // return FAIL;
        } else {
            pushLookBehindNot(ip + addr, s, sprev);
            s = q;
            sprev = EncodingHelper.prevCharHead(str, s);
        }
    }

    private void opFailLookBehindNot() {
        popTilLookBehindNot();
        opFail();
    }

    private void opFail() {
        if (stack == null) {
            ip = regex.codeLength - 1;
            return;
        }


        StackEntry e = pop();
        ip    = e.getStatePCode();
        s     = e.getStatePStr();
        sprev = e.getStatePStrPrev();
    }

    private int finish() {
        return bestLen;
    }
}
