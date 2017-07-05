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

import jdk.nashorn.internal.runtime.regexp.joni.encoding.IntHolder;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ErrorMessages;
import jdk.nashorn.internal.runtime.regexp.joni.exception.InternalException;
import jdk.nashorn.internal.runtime.regexp.joni.exception.SyntaxException;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ValueException;

abstract class ScannerSupport extends IntHolder implements ErrorMessages {

    protected final char[] chars;       // pattern
    protected int p;                    // current scanner position
    protected int stop;                 // pattern end (mutable)
    private int lastFetched;            // last fetched value for unfetch support
    protected int c;                    // current code point

    private final int begin;            // pattern begin position for reset() support
    private final int end;              // pattern end position for reset() support
    protected int _p;                   // used by mark()/restore() to mark positions

    private final static int INT_SIGN_BIT = 1 << 31;

    protected ScannerSupport(char[] chars, int p, int end) {
        this.chars = chars;
        this.begin = p;
        this.end = end;

        reset();
    }

    protected int getBegin() {
        return begin;
    }

    protected int getEnd() {
        return end;
    }

    protected final int scanUnsignedNumber() {
        int last = c;
        int num = 0; // long ???
        while(left()) {
            fetch();
            if (Character.isDigit(c)) {
                int onum = num;
                num = num * 10 + EncodingHelper.digitVal(c);
                if (((onum ^ num) & INT_SIGN_BIT) != 0) return -1;
            } else {
                unfetch();
                break;
            }
        }
        c = last;
        return num;
    }

    protected final int scanUnsignedHexadecimalNumber(int maxLength) {
        int last = c;
        int num = 0;
        while(left() && maxLength-- != 0) {
            fetch();
            if (EncodingHelper.isXDigit(c)) {
                int onum = num;
                int val = EncodingHelper.xdigitVal(c);
                num = (num << 4) + val;
                if (((onum ^ num) & INT_SIGN_BIT) != 0) return -1;
            } else {
                unfetch();
                break;
            }
        }
        c = last;
        return num;
    }

    protected final int scanUnsignedOctalNumber(int maxLength) {
        int last = c;
        int num = 0;
        while(left() && maxLength-- != 0) {
            fetch();
            if (Character.isDigit(c) && c < '8') {
                int onum = num;
                int val = EncodingHelper.odigitVal(c);
                num = (num << 3) + val;
                if (((onum ^ num) & INT_SIGN_BIT) != 0) return -1;
            } else {
                unfetch();
                break;
            }
        }
        c = last;
        return num;
    }

    protected final void reset() {
        p = begin;
        stop = end;
    }

    protected final void mark() {
        _p = p;
    }

    protected final void restore() {
        p = _p;
    }

    protected final void inc() {
        lastFetched = p;
        p++;
    }

    protected final void fetch() {
        lastFetched = p;
        c = chars[p++];
    }

    protected int fetchTo() {
        lastFetched = p;
        return chars[p++];
    }

    protected final void unfetch() {
        p = lastFetched;
    }

    protected final int peek() {
        return p < stop ? chars[p] : 0;
    }

    protected final boolean peekIs(int c) {
        return peek() == c;
    }

    protected final boolean left() {
        return p < stop;
    }

}
