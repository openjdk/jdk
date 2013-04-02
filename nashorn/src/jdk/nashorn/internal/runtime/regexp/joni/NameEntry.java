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

public final class NameEntry {
    static final int INIT_NAME_BACKREFS_ALLOC_NUM = 8;

    public final char[] name;
    public final int nameP;
    public final int nameEnd;

    int backNum;
    int backRef1;
    int backRefs[];

    public NameEntry(char[] chars, int p, int end) {
        name = chars;
        nameP = p;
        nameEnd = end;
    }

    public int[] getBackRefs() {
        switch (backNum) {
        case 0:
            return new int[]{};
        case 1:
            return new int[]{backRef1};
        default:
            int[]result = new int[backNum];
            System.arraycopy(backRefs, 0, result, 0, backNum);
            return result;
        }
    }

    private void alloc() {
        backRefs = new int[INIT_NAME_BACKREFS_ALLOC_NUM];
    }

    private void ensureSize() {
        if (backNum > backRefs.length) {
            int[]tmp = new int[backRefs.length << 1];
            System.arraycopy(backRefs, 0, tmp, 0, backRefs.length);
            backRefs = tmp;
        }
    }

    public void addBackref(int backRef) {
        backNum++;

        switch (backNum) {
            case 1:
                backRef1 = backRef;
                break;
            case 2:
                alloc();
                backRefs[0] = backRef1;
                backRefs[1] = backRef;
                break;
            default:
                ensureSize();
                backRefs[backNum - 1] = backRef;
        }
    }

    public String toString() {
        StringBuilder buff = new StringBuilder(new String(name, nameP, nameEnd - nameP) + " ");
        if (backNum == 0) {
            buff.append("-");
        } else if (backNum == 1){
            buff.append(backRef1);
        } else {
            for (int i=0; i<backNum; i++){
                if (i > 0) buff.append(", ");
                buff.append(backRefs[i]);
            }
        }
        return buff.toString();
    }

}
