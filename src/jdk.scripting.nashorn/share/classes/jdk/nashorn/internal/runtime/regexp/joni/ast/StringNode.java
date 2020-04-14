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
package jdk.nashorn.internal.runtime.regexp.joni.ast;

import jdk.nashorn.internal.runtime.regexp.joni.EncodingHelper;
import jdk.nashorn.internal.runtime.regexp.joni.constants.StringType;

@SuppressWarnings("javadoc")
public final class StringNode extends Node implements StringType {

    private static final int NODE_STR_MARGIN = 16;
    private static final int NODE_STR_BUF_SIZE = 24;

    public char[] chars;
    public int p;
    public int end;

    public int flag;

    public StringNode() {
        this(NODE_STR_BUF_SIZE);
    }

    private StringNode(int size) {
        this.chars = new char[size];
        this.p = 0;
        this.end = 0;
    }

    public StringNode(final char[] chars, final int p, final int end) {
        this.chars = chars;
        this.p = p;
        this.end = end;
        setShared();
    }

    public StringNode(final char c) {
        this();
        chars[end++] = c;
    }

    /**
     * Create a new empty StringNode.
     */
    public static StringNode createEmpty() {
        return new StringNode(0);
    }

    /* Ensure there is ahead bytes available in node's buffer
     * (assumes that the node is not shared)
     */
    public void ensure(final int ahead) {
        final int len = (end - p) + ahead;
        if (len >= chars.length) {
            final char[] tmp = new char[len + NODE_STR_MARGIN];
            System.arraycopy(chars, p, tmp, 0, end - p);
            chars = tmp;
        }
    }

    /* COW and/or ensure there is ahead bytes available in node's buffer
     */
    private void modifyEnsure(final int ahead) {
        if (isShared()) {
            final int len = (end - p) + ahead;
            final char[] tmp = new char[len + NODE_STR_MARGIN];
            System.arraycopy(chars, p, tmp, 0, end - p);
            chars = tmp;
            end -= p;
            p = 0;
            clearShared();
        } else {
            ensure(ahead);
        }
    }

    @Override
    public int getType() {
        return STR;
    }

    @Override
    public String getName() {
        return "String";
    }

    @Override
    public String toString(final int level) {
        final StringBuilder value = new StringBuilder();
        value.append("\n  bytes: '");
        for (int i=p; i<end; i++) {
            if (chars[i] >= 0x20 && chars[i] < 0x7f) {
                value.append(chars[i]);
            } else {
                value.append(String.format("[0x%04x]", (int)chars[i]));
            }
        }
        value.append("'");
        return value.toString();
    }

    public int length() {
        return end - p;
    }

    public StringNode splitLastChar() {
        StringNode n = null;

        if (end > p) {
            final int prev = EncodingHelper.prevCharHead(p, end);
            if (prev != -1 && prev > p) { /* can be splitted. */
                n = new StringNode(chars, prev, end);
                if (isRaw()) n.setRaw();
                end = prev;
            }
        }
        return n;
    }

    public boolean canBeSplit() {
        return end > p && 1 < (end - p);
    }

    public void set(final char[] chars, final int p, final int end) {
        this.chars = chars;
        this.p = p;
        this.end = end;
        setShared();
    }

    public void cat(final char[] cat, final int catP, final int catEnd) {
        final int len = catEnd - catP;
        modifyEnsure(len);
        System.arraycopy(cat, catP, chars, end, len);
        end += len;
    }

    public void cat(final char c) {
        modifyEnsure(1);
        chars[end++] = c;
    }

    public void catCode(final int code) {
        cat((char)code);
    }

    public void clear() {
        if (chars.length > NODE_STR_BUF_SIZE) chars = new char[NODE_STR_BUF_SIZE];
        flag = 0;
        p = end = 0;
    }

    public void setRaw() {
        flag |= NSTR_RAW;
    }

    public void clearRaw() {
        flag &= ~NSTR_RAW;
    }

    public boolean isRaw() {
        return (flag & NSTR_RAW) != 0;
    }

    public void setAmbig() {
        flag |= NSTR_AMBIG;
    }

    public void clearAmbig() {
        flag &= ~NSTR_AMBIG;
    }

    public boolean isAmbig() {
        return (flag & NSTR_AMBIG) != 0;
    }

    public void setDontGetOptInfo() {
        flag |= NSTR_DONT_GET_OPT_INFO;
    }

    public void clearDontGetOptInfo() {
        flag &= ~NSTR_DONT_GET_OPT_INFO;
    }

    public boolean isDontGetOptInfo() {
        return (flag & NSTR_DONT_GET_OPT_INFO) != 0;
    }

    public void setShared() {
        flag |= NSTR_SHARED;
    }

    public void clearShared() {
        flag &= ~NSTR_SHARED;
    }

    public boolean isShared() {
        return (flag & NSTR_SHARED) != 0;
    }
}
