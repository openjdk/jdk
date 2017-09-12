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

import jdk.nashorn.internal.runtime.regexp.joni.ast.CClassNode;

final class ApplyCaseFold {

    // i_apply_case_fold
    public static void apply(final int from, final int to, final Object o) {
        final ApplyCaseFoldArg arg = (ApplyCaseFoldArg)o;

        final ScanEnvironment env = arg.env;
        final CClassNode cc = arg.cc;
        final BitSet bs = cc.bs;

        final boolean inCC = cc.isCodeInCC(from);

        if (Config.CASE_FOLD_IS_APPLIED_INSIDE_NEGATIVE_CCLASS) {
            if ((inCC && !cc.isNot()) || (!inCC && cc.isNot())) {
                if (to >= BitSet.SINGLE_BYTE_SIZE) {
                    cc.addCodeRange(env, to, to);
                } else {
                    /* /(?i:[^A-C])/.match("a") ==> fail. */
                    bs.set(to);
                }
            }
        } else {
            if (inCC) {
                if (to >= BitSet.SINGLE_BYTE_SIZE) {
                    if (cc.isNot()) {
                        cc.clearNotFlag();
                    }
                    cc.addCodeRange(env, to, to);
                } else {
                    if (cc.isNot()) {
                        bs.clear(to);
                    } else {
                        bs.set(to);
                    }
                }
            }
        } // CASE_FOLD_IS_APPLIED_INSIDE_NEGATIVE_CCLASS

    }

    static final ApplyCaseFold INSTANCE = new ApplyCaseFold();
}
