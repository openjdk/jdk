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

import jdk.nashorn.internal.runtime.regexp.joni.constants.TokenType;

final class Token {
    TokenType type;
    boolean escaped;
    int base;               /* is number: 8, 16 (used in [....]) */
    int backP;

    // union fields
    private int INT1, INT2, INT3, INT4, INT5;
    private int []INTA1;

    // union accessors
    int getC() {
        return INT1;
    }
    void setC(int c) {
        INT1 = c;
    }

    int getCode() {
        return INT1;
    }
    void setCode(int code) {
        INT1 = code;
    }

    int getAnchor() {
        return INT1;
    }
    void setAnchor(int anchor) {
        INT1 = anchor;
    }

    int getSubtype() {
        return INT1;
    }
    void setSubtype(int subtype) {
        INT1 = subtype;
    }

    // repeat union member
    int getRepeatLower() {
        return INT1;
    }
    void setRepeatLower(int lower) {
        INT1 = lower;
    }

    int getRepeatUpper() {
        return INT2;
    }
    void setRepeatUpper(int upper) {
        INT2 = upper;
    }

    boolean getRepeatGreedy() {
        return INT3 != 0;
    }
    void setRepeatGreedy(boolean greedy) {
        INT3 = greedy ? 1 : 0;
    }

    boolean getRepeatPossessive() {
        return INT4 != 0;
    }
    void setRepeatPossessive(boolean possessive) {
        INT4 = possessive ? 1 : 0;
    }

    // backref union member
    int getBackrefNum() {
        return INT1;
    }
    void setBackrefNum(int num) {
        INT1 = num;
    }

    int getBackrefRef1() {
        return INT2;
    }
    void setBackrefRef1(int ref1) {
        INT2 = ref1;
    }

    int[]getBackrefRefs() {
        return INTA1;
    }
    void setBackrefRefs(int[]refs) {
        INTA1 = refs;
    }

    boolean getBackrefByName() {
        return INT3 != 0;
    }
    void setBackrefByName(boolean byName) {
        INT3 = byName ? 1 : 0;
    }

    // USE_BACKREF_AT_LEVEL
    boolean getBackrefExistLevel() {
        return INT4 != 0;
    }
    void setBackrefExistLevel(boolean existLevel) {
        INT4 = existLevel ? 1 : 0;
    }

    int getBackrefLevel() {
        return INT5;
    }
    void setBackrefLevel(int level) {
        INT5 = level;
    }

    // call union member
    int getCallNameP() {
        return INT1;
    }
    void setCallNameP(int nameP) {
        INT1 = nameP;
    }

    int getCallNameEnd() {
        return INT2;
    }
    void setCallNameEnd(int nameEnd) {
        INT2 = nameEnd;
    }

    int getCallGNum() {
        return INT3;
    }
    void setCallGNum(int gnum) {
        INT3 = gnum;
    }

    // prop union member
    int getPropCType() {
        return INT1;
    }
    void setPropCType(int ctype) {
        INT1 = ctype;
    }

    boolean getPropNot() {
        return INT2 != 0;
    }
    void setPropNot(boolean not) {
        INT2 = not ? 1 : 0;
    }
}
