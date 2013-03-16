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

public class CaptureTreeNode {


    int group;
    int beg;
    int end;
    // int allocated;
    int numChildren;
    CaptureTreeNode[]children;

    CaptureTreeNode() {
        beg = Region.REGION_NOTPOS;
        end = Region.REGION_NOTPOS;
        group = -1;
    }

    static final int HISTORY_TREE_INIT_ALLOC_SIZE = 8;
    void addChild(CaptureTreeNode child) {
        if (children == null) {
            children = new CaptureTreeNode[HISTORY_TREE_INIT_ALLOC_SIZE];
        } else if (numChildren >= children.length) {
            CaptureTreeNode[]tmp = new CaptureTreeNode[children.length << 1];
            System.arraycopy(children, 0, tmp, 0, children.length);
            children = tmp;
        }

        children[numChildren] = child;
        numChildren++;
    }

    void clear() {
        for (int i=0; i<numChildren; i++) {
            children[i] = null; // ???
        }
        numChildren = 0;
        beg = end = Region.REGION_NOTPOS;
        group = -1;
    }

    CaptureTreeNode cloneTree() {
        CaptureTreeNode clone = new CaptureTreeNode();
        clone.beg = beg;
        clone.end = end;

        for (int i=0; i<numChildren; i++) {
            CaptureTreeNode child = children[i].cloneTree();
            clone.addChild(child);
        }
        return clone;
    }


}
