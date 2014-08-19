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
package jdk.nashorn.internal.runtime.regexp.joni.constants;

public interface NodeType {
    /* node type */
    final int  STR        = 0;
    final int  CCLASS     = 1;
    final int  CTYPE      = 2;
    final int  CANY       = 3;
    final int  BREF       = 4;
    final int  QTFR       = 5;
    final int  ENCLOSE    = 6;
    final int  ANCHOR     = 7;
    final int  LIST       = 8;
    final int  ALT        = 9;
    final int  CALL       = 10;

    final int BIT_STR        = 1 << STR;
    final int BIT_CCLASS     = 1 << CCLASS;
    final int BIT_CTYPE      = 1 << CTYPE;
    final int BIT_CANY       = 1 << CANY;
    final int BIT_BREF       = 1 << BREF;
    final int BIT_QTFR       = 1 << QTFR;
    final int BIT_ENCLOSE    = 1 << ENCLOSE;
    final int BIT_ANCHOR     = 1 << ANCHOR;
    final int BIT_LIST       = 1 << LIST;
    final int BIT_ALT        = 1 << ALT;
    final int BIT_CALL       = 1 << CALL;

    /* allowed node types in look-behind */
    final int ALLOWED_IN_LB = ( BIT_LIST |
                                BIT_ALT |
                                BIT_STR |
                                BIT_CCLASS |
                                BIT_CTYPE |
                                BIT_CANY |
                                BIT_ANCHOR |
                                BIT_ENCLOSE |
                                BIT_QTFR |
                                BIT_CALL );

    final int SIMPLE =        ( BIT_STR |
                                BIT_CCLASS |
                                BIT_CTYPE |
                                BIT_CANY |
                                BIT_BREF);

}
