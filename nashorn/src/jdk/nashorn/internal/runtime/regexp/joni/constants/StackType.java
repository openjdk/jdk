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
package jdk.nashorn.internal.runtime.regexp.joni.constants;

public interface StackType {
    /** stack **/
    final int INVALID_STACK_INDEX           = -1;

    /* stack type */
    /* used by normal-POP */
    final int ALT                           = 0x0001;
    final int LOOK_BEHIND_NOT               = 0x0002;
    final int POS_NOT                       = 0x0003;
    /* handled by normal-POP */
    final int MEM_START                     = 0x0100;
    final int MEM_END                       = 0x8200;
    final int REPEAT_INC                    = 0x0300;
    final int STATE_CHECK_MARK              = 0x1000;
    /* avoided by normal-POP */
    final int NULL_CHECK_START              = 0x3000;
    final int NULL_CHECK_END                = 0x5000;  /* for recursive call */
    final int MEM_END_MARK                  = 0x8400;
    final int POS                           = 0x0500;  /* used when POP-POS */
    final int STOP_BT                       = 0x0600;  /* mark for "(?>...)" */
    final int REPEAT                        = 0x0700;
    final int CALL_FRAME                    = 0x0800;
    final int RETURN                        = 0x0900;
    final int VOID                          = 0x0a00;  /* for fill a blank */

    /* stack type check mask */
    final int MASK_POP_USED                 = 0x00ff;
    final int MASK_TO_VOID_TARGET           = 0x10ff;
    final int MASK_MEM_END_OR_MARK          = 0x8000;  /* MEM_END or MEM_END_MARK */
}
