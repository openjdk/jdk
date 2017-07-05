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

import java.util.Set;

import jdk.nashorn.internal.runtime.regexp.joni.UnsetAddrList;
import jdk.nashorn.internal.runtime.regexp.joni.WarnCallback;

public final class CallNode extends StateNode {
    public char[] name;
    public int nameP;
    public int nameEnd;

    public int groupNum;
    public Node target;             // is it an EncloseNode always ?
    public UnsetAddrList unsetAddrList;

    public CallNode(char[] name, int nameP, int nameEnd, int gnum) {
        this.name = name;
        this.nameP = nameP;
        this.nameEnd = nameEnd;
        this.groupNum = gnum; /* call by number if gnum != 0 */
    }

    @Override
    public int getType() {
        return CALL;
    }

    @Override
    protected void setChild(Node newChild) {
        target = newChild;
    }

    @Override
    protected Node getChild() {
        return target;
    }

    public void setTarget(Node tgt) {
        target = tgt;
        tgt.parent = this;
    }

    @Override
    public String getName() {
        return "Call";
    }

    @Override
    public void verifyTree(Set<Node> set, WarnCallback warnings) {
        if (target == null || target.parent == this)
            warnings.warn(this.getAddressName() + " doesn't point to a target or the target has been stolen");
        // do not recurse here
    }

    @Override
    public String toString(int level) {
        StringBuilder value = new StringBuilder(super.toString(level));
        value.append("\n  name: " + new String(name, nameP, nameEnd - nameP));
        value.append("\n  groupNum: " + groupNum);
        value.append("\n  target: " + pad(target.getAddressName(), level + 1));
        value.append("\n  unsetAddrList: " + pad(unsetAddrList, level + 1));

        return value.toString();
    }

}
