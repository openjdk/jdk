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

@SuppressWarnings("javadoc")
public interface AnchorType {
    final int BEGIN_BUF         = (1<<0);
    final int BEGIN_LINE        = (1<<1);
    final int BEGIN_POSITION    = (1<<2);
    final int END_BUF           = (1<<3);
    final int SEMI_END_BUF      = (1<<4);
    final int END_LINE          = (1<<5);

    final int WORD_BOUND        = (1<<6);
    final int NOT_WORD_BOUND    = (1<<7);
    final int WORD_BEGIN        = (1<<8);
    final int WORD_END          = (1<<9);
    final int PREC_READ         = (1<<10);
    final int PREC_READ_NOT     = (1<<11);
    final int LOOK_BEHIND       = (1<<12);
    final int LOOK_BEHIND_NOT   = (1<<13);

    final int ANYCHAR_STAR      = (1<<14);   /* ".*" optimize info */
    final int ANYCHAR_STAR_ML   = (1<<15);   /* ".*" optimize info (multi-line) */

    final int ANYCHAR_STAR_MASK = (ANYCHAR_STAR | ANYCHAR_STAR_ML);
    final int END_BUF_MASK      = (END_BUF | SEMI_END_BUF);

    final int ALLOWED_IN_LB =     ( LOOK_BEHIND |
                                    BEGIN_LINE |
                                    END_LINE |
                                    BEGIN_BUF |
                                    BEGIN_POSITION );

    final int ALLOWED_IN_LB_NOT = ( LOOK_BEHIND |
                                    LOOK_BEHIND_NOT |
                                    BEGIN_LINE |
                                    END_LINE |
                                    BEGIN_BUF |
                                    BEGIN_POSITION );

}
