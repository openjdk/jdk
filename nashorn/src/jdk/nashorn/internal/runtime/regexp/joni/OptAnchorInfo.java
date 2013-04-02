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

final class OptAnchorInfo implements AnchorType {
    int leftAnchor;
    int rightAnchor;

    void clear() {
        leftAnchor = rightAnchor = 0;
    }

    void copy(OptAnchorInfo other) {
        leftAnchor = other.leftAnchor;
        rightAnchor = other.rightAnchor;
    }

    void concat(OptAnchorInfo left, OptAnchorInfo right, int leftLength, int rightLength) {
        leftAnchor = left.leftAnchor;
        if (leftLength == 0) leftAnchor |= right.leftAnchor;

        rightAnchor = right.rightAnchor;
        if (rightLength == 0) rightAnchor |= left.rightAnchor;
    }

    boolean isSet(int anchor) {
        if ((leftAnchor & anchor) != 0) return true;
        return (rightAnchor & anchor) != 0;
    }

    void add(int anchor) {
        if (isLeftAnchor(anchor)) {
            leftAnchor |= anchor;
        } else {
            rightAnchor |= anchor;
        }
    }

    void remove(int anchor) {
        if (isLeftAnchor(anchor)) {
            leftAnchor &= ~anchor;
        } else {
            rightAnchor &= ~anchor;
        }
    }

    void altMerge(OptAnchorInfo other) {
        leftAnchor &= other.leftAnchor;
        rightAnchor &= other.rightAnchor;
    }

    static boolean isLeftAnchor(int anchor) { // make a mask for it ?
        return !(anchor == END_BUF || anchor == SEMI_END_BUF ||
                 anchor == END_LINE || anchor == PREC_READ ||
                 anchor == PREC_READ_NOT);
    }

    static String anchorToString(int anchor) {
        StringBuffer s = new StringBuffer("[");

        if ((anchor & AnchorType.BEGIN_BUF) !=0 ) s.append("begin-buf ");
        if ((anchor & AnchorType.BEGIN_LINE) !=0 ) s.append("begin-line ");
        if ((anchor & AnchorType.BEGIN_POSITION) !=0 ) s.append("begin-pos ");
        if ((anchor & AnchorType.END_BUF) !=0 ) s.append("end-buf ");
        if ((anchor & AnchorType.SEMI_END_BUF) !=0 ) s.append("semi-end-buf ");
        if ((anchor & AnchorType.END_LINE) !=0 ) s.append("end-line ");
        if ((anchor & AnchorType.ANYCHAR_STAR) !=0 ) s.append("anychar-star ");
        if ((anchor & AnchorType.ANYCHAR_STAR_ML) !=0 ) s.append("anychar-star-pl ");
        s.append("]");

        return s.toString();
    }
}
