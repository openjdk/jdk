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

import jdk.nashorn.internal.runtime.regexp.joni.ScanEnvironment;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ErrorMessages;
import jdk.nashorn.internal.runtime.regexp.joni.exception.ValueException;

public final class BackRefNode extends StateNode {
    //private static int NODE_BACKREFS_SIZE = 6;

    //int state;
    public int backNum;
    public int back[];

    public int nestLevel;

    public BackRefNode(int backNum, int[]backRefs, boolean byName, ScanEnvironment env) {
        this.backNum = backNum;
        if (byName) setNameRef();

        for (int i=0; i<backNum; i++) {
            if (backRefs[i] <= env.numMem && env.memNodes[backRefs[i]] == null) {
                setRecursion(); /* /...(\1).../ */
                break;
            }
        }

        back = new int[backNum];
        System.arraycopy(backRefs, 0, back, 0, backNum); // shall we really dup it ???
    }

    // #ifdef USE_BACKREF_AT_LEVEL
    public BackRefNode(int backNum, int[]backRefs, boolean byName, boolean existLevel, int nestLevel, ScanEnvironment env) {
        this(backNum, backRefs, byName, env);

        if (existLevel) {
            //state |= NST_NEST_LEVEL;
            setNestLevel();
            this.nestLevel = nestLevel;
        }
    }

    @Override
    public int getType() {
        return BREF;
    }

    @Override
    public String getName() {
        return "Back Ref";
    }

    @Override
    public String toString(int level) {
        StringBuilder value = new StringBuilder(super.toString(level));
        value.append("\n  backNum: " + backNum);
        String backs = "";
        for (int i=0; i<back.length; i++) backs += back[i] + ", ";
        value.append("\n  back: " + backs);
        value.append("\n  nextLevel: " + nestLevel);
        return value.toString();
    }

    public void renumber(int[]map) {
        if (!isNameRef()) throw new ValueException(ErrorMessages.ERR_NUMBERED_BACKREF_OR_CALL_NOT_ALLOWED);

        int oldNum = backNum;

        int pos = 0;
        for (int i=0; i<oldNum; i++) {
            int n = map[back[i]];
            if (n > 0) {
                back[pos] = n;
                pos++;
            }
        }
        backNum = pos;
    }

}
