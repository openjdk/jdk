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
package jdk.nashorn.internal.runtime.regexp.joni.encoding;

@SuppressWarnings("javadoc")
public interface CharacterType {

    final int NEWLINE   = 0;
    final int ALPHA     = 1;
    final int BLANK     = 2;
    final int CNTRL     = 3;
    final int DIGIT     = 4;
    final int GRAPH     = 5;
    final int LOWER     = 6;
    final int PRINT     = 7;
    final int PUNCT     = 8;
    final int SPACE     = 9;
    final int UPPER     = 10;
    final int XDIGIT    = 11;
    final int WORD      = 12;
    final int ALNUM     = 13;      /* alpha || digit */
    final int ASCII     = 14;

    final int SPECIAL_MASK = 256;
    final int S = SPECIAL_MASK | SPACE;
    final int D = SPECIAL_MASK | DIGIT;
    final int W = SPECIAL_MASK | WORD;

    final int LETTER_MASK = (1 << Character.UPPERCASE_LETTER)
                          | (1 << Character.LOWERCASE_LETTER)
                          | (1 << Character.TITLECASE_LETTER)
                          | (1 << Character.MODIFIER_LETTER)
                          | (1 << Character.OTHER_LETTER);
    final int ALPHA_MASK = LETTER_MASK
                          | (1 << Character.COMBINING_SPACING_MARK)
                          | (1 << Character.NON_SPACING_MARK)
                          | (1 << Character.ENCLOSING_MARK);
    final int ALNUM_MASK = ALPHA_MASK
                          | (1 << Character.DECIMAL_DIGIT_NUMBER);
    final int WORD_MASK = ALNUM_MASK
                          | (1 << Character.CONNECTOR_PUNCTUATION);
    final int PUNCT_MASK =  (1 << Character.CONNECTOR_PUNCTUATION)
                          | (1 << Character.DASH_PUNCTUATION)
                          | (1 << Character.END_PUNCTUATION)
                          | (1 << Character.FINAL_QUOTE_PUNCTUATION)
                          | (1 << Character.INITIAL_QUOTE_PUNCTUATION)
                          | (1 << Character.OTHER_PUNCTUATION)
                          | (1 << Character.START_PUNCTUATION);
    final int CNTRL_MASK =  (1 << Character.CONTROL)
                          | (1 << Character.FORMAT)
                          | (1 << Character.PRIVATE_USE)
                          | (1 << Character.SURROGATE);
    final int SPACE_MASK =  (1 << Character.SPACE_SEPARATOR)
                          | (1 << Character.LINE_SEPARATOR)        // 0x2028
                          | (1 << Character.PARAGRAPH_SEPARATOR);  // 0x2029
    final int GRAPH_MASK = SPACE_MASK
                          | (1 << Character.CONTROL)
                          | (1 << Character.SURROGATE);
    final int PRINT_MASK =  (1 << Character.CONTROL)
                          | (1 << Character.SURROGATE);


}
