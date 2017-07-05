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
 * THE SOFTWARE IS PROVIDED "AS IS".toCharArray(), WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package jdk.nashorn.internal.runtime.regexp.joni.encoding;

import jdk.nashorn.internal.runtime.regexp.joni.exception.ErrorMessages;
import jdk.nashorn.internal.runtime.regexp.joni.exception.JOniException;

import java.util.HashMap;

public class PosixBracket {

    public static final char[][] PBSNamesLower = {
            "alnum".toCharArray(),
            "alpha".toCharArray(),
            "blank".toCharArray(),
            "cntrl".toCharArray(),
            "digit".toCharArray(),
            "graph".toCharArray(),
            "lower".toCharArray(),
            "print".toCharArray(),
            "punct".toCharArray(),
            "space".toCharArray(),
            "upper".toCharArray(),
            "xdigit".toCharArray(),
            "ascii".toCharArray(),
            "word".toCharArray()
    };

    public static final int PBSValues[] = {
            CharacterType.ALNUM,
            CharacterType.ALPHA,
            CharacterType.BLANK,
            CharacterType.CNTRL,
            CharacterType.DIGIT,
            CharacterType.GRAPH,
            CharacterType.LOWER,
            CharacterType.PRINT,
            CharacterType.PUNCT,
            CharacterType.SPACE,
            CharacterType.UPPER,
            CharacterType.XDIGIT,
            CharacterType.ASCII,
            CharacterType.WORD,
    };

    public static int propertyNameToCType(String name) {
        name = name.toLowerCase();
        if (!PBSTableUpper.containsKey(name)) {
            throw new JOniException(ErrorMessages.ERR_INVALID_CHAR_PROPERTY_NAME.replaceAll("%n", name));
        }
        return PBSTableUpper.get(name);
    }

    private static final HashMap<String,Integer> PBSTableUpper = new HashMap<String,Integer>();

    static {
        for (int i=0; i<PBSValues.length; i++) PBSTableUpper.put(new String(PBSNamesLower[i]), PBSValues[i]);
    }

}
