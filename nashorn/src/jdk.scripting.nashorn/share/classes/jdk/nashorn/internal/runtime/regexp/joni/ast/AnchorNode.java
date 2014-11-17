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

import jdk.nashorn.internal.runtime.regexp.joni.constants.AnchorType;

@SuppressWarnings("javadoc")
public final class AnchorNode extends Node implements AnchorType {
    public int type;
    public Node target;
    public int charLength;

    public AnchorNode(final int type) {
        this.type = type;
        charLength = -1;
    }

    @Override
    public int getType() {
        return ANCHOR;
    }

    @Override
    protected void setChild(final Node newChild) {
        target = newChild;
    }

    @Override
    protected Node getChild() {
        return target;
    }

    public void setTarget(final Node tgt) {
        target = tgt;
        tgt.parent = this;
    }

    @Override
    public String getName() {
        return "Anchor";
    }

    @Override
    public String toString(final int level) {
        final StringBuilder value = new StringBuilder();
        value.append("\n  type: " + typeToString());
        value.append("\n  target: " + pad(target, level + 1));
        return value.toString();
    }

    public String typeToString() {
        final StringBuilder sb = new StringBuilder();
        if (isType(BEGIN_BUF)) {
            sb.append("BEGIN_BUF ");
        }
        if (isType(BEGIN_LINE)) {
            sb.append("BEGIN_LINE ");
        }
        if (isType(BEGIN_POSITION)) {
            sb.append("BEGIN_POSITION ");
        }
        if (isType(END_BUF)) {
            sb.append("END_BUF ");
        }
        if (isType(SEMI_END_BUF)) {
            sb.append("SEMI_END_BUF ");
        }
        if (isType(END_LINE)) {
            sb.append("END_LINE ");
        }
        if (isType(WORD_BOUND)) {
            sb.append("WORD_BOUND ");
        }
        if (isType(NOT_WORD_BOUND)) {
            sb.append("NOT_WORD_BOUND ");
        }
        if (isType(WORD_BEGIN)) {
            sb.append("WORD_BEGIN ");
        }
        if (isType(WORD_END)) {
            sb.append("WORD_END ");
        }
        if (isType(PREC_READ)) {
            sb.append("PREC_READ ");
        }
        if (isType(PREC_READ_NOT)) {
            sb.append("PREC_READ_NOT ");
        }
        if (isType(LOOK_BEHIND)) {
            sb.append("LOOK_BEHIND ");
        }
        if (isType(LOOK_BEHIND_NOT)) {
            sb.append("LOOK_BEHIND_NOT ");
        }
        if (isType(ANYCHAR_STAR)) {
            sb.append("ANYCHAR_STAR ");
        }
        if (isType(ANYCHAR_STAR_ML)) {
            sb.append("ANYCHAR_STAR_ML ");
        }
        return sb.toString();
    }

    private boolean isType(final int t) {
        return (this.type & t) != 0;
    }

}
