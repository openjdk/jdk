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

public final class NodeOptInfo {
    final MinMaxLen length = new  MinMaxLen();
    final OptAnchorInfo anchor = new OptAnchorInfo();
    final OptExactInfo exb = new OptExactInfo();            /* boundary */
    final OptExactInfo exm = new OptExactInfo();            /* middle */
    final OptExactInfo expr = new OptExactInfo();           /* prec read (?=...) */
    final OptMapInfo map = new OptMapInfo();                /* boundary */

    public void setBoundNode(final MinMaxLen mmd) {
        exb.mmd.copy(mmd);
        expr.mmd.copy(mmd);
        map.mmd.copy(mmd);
    }

    public void clear() {
        length.clear();
        anchor.clear();
        exb.clear();
        exm.clear();
        expr.clear();
        map.clear();
    }

    public void copy(final NodeOptInfo other) {
        length.copy(other.length);
        anchor.copy(other.anchor);
        exb.copy(other.exb);
        exm.copy(other.exm);
        expr.copy(other.expr);
        map.copy(other.map);
    }

    public void concatLeftNode(final NodeOptInfo other) {
        final OptAnchorInfo tanchor = new OptAnchorInfo(); // remove it somehow ?
        tanchor.concat(anchor, other.anchor, length.max, other.length.max);
        anchor.copy(tanchor);

        if (other.exb.length > 0 && length.max == 0) {
            tanchor.concat(anchor, other.exb.anchor, length.max, other.length.max);
            other.exb.anchor.copy(tanchor);
        }

        if (other.map.value > 0 && length.max == 0) {
            if (other.map.mmd.max == 0) {
                other.map.anchor.leftAnchor |= anchor.leftAnchor;
            }
        }

        final boolean exbReach = exb.reachEnd;
        final boolean exmReach = exm.reachEnd;

        if (other.length.max != 0) {
            exb.reachEnd = exm.reachEnd = false;
        }

        if (other.exb.length > 0) {
            if (exbReach) {
                exb.concat(other.exb);
                other.exb.clear();
            } else if (exmReach) {
                exm.concat(other.exb);
                other.exb.clear();
            }
        }

        exm.select(other.exb);
        exm.select(other.exm);

        if (expr.length > 0) {
            if (other.length.max > 0) {
                // TODO: make sure it is not an Oniguruma bug (casting unsigned int to int for arithmetic comparison)
                int otherLengthMax = other.length.max;
                if (otherLengthMax == MinMaxLen.INFINITE_DISTANCE) otherLengthMax = -1;
                if (expr.length > otherLengthMax) expr.length = otherLengthMax;
                if (expr.mmd.max == 0) {
                    exb.select(expr);
                } else {
                    exm.select(expr);
                }
            }
        } else if (other.expr.length > 0) {
            expr.copy(other.expr);
        }

        map.select(other.map);
        length.add(other.length);
    }

    public void altMerge(final NodeOptInfo other, final OptEnvironment env) {
        anchor.altMerge(other.anchor);
        exb.altMerge(other.exb, env);
        exm.altMerge(other.exm, env);
        expr.altMerge(other.expr, env);
        map.altMerge(other.map);
        length.altMerge(other.length);
    }

    public void setBound(final MinMaxLen mmd) {
        exb.mmd.copy(mmd);
        expr.mmd.copy(mmd);
        map.mmd.copy(mmd);
    }

}
