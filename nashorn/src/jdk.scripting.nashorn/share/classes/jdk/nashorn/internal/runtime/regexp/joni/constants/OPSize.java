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

public interface OPSize {

    // this might be helpful for potential byte[] migration
    final int OPCODE                = 1;
    final int RELADDR               = 1;
    final int ABSADDR               = 1;
    final int LENGTH                = 1;
    final int MEMNUM                = 1;
    final int STATE_CHECK_NUM       = 1;
    final int REPEATNUM             = 1;
    final int OPTION                = 1;
    final int CODE_POINT            = 1;
    final int POINTER               = 1;
    final int INDEX                 = 1;

    /* op-code + arg size */

    final int ANYCHAR_STAR                  = OPCODE;
    final int ANYCHAR_STAR_PEEK_NEXT        = (OPCODE + 1);
    final int JUMP                          = (OPCODE + RELADDR);
    final int PUSH                          = (OPCODE + RELADDR);
    final int POP                           = OPCODE;
    final int PUSH_OR_JUMP_EXACT1           = (OPCODE + RELADDR + 1);
    final int PUSH_IF_PEEK_NEXT             = (OPCODE + RELADDR + 1);
    final int REPEAT_INC                    = (OPCODE + MEMNUM);
    final int REPEAT_INC_NG                 = (OPCODE + MEMNUM);
    final int PUSH_POS                      = OPCODE;
    final int PUSH_POS_NOT                  = (OPCODE + RELADDR);
    final int POP_POS                       = OPCODE;
    final int FAIL_POS                      = OPCODE;
    final int SET_OPTION                    = (OPCODE + OPTION);
    final int SET_OPTION_PUSH               = (OPCODE + OPTION);
    final int FAIL                          = OPCODE;
    final int MEMORY_START                  = (OPCODE + MEMNUM);
    final int MEMORY_START_PUSH             = (OPCODE + MEMNUM);
    final int MEMORY_END_PUSH               = (OPCODE + MEMNUM);
    final int MEMORY_END_PUSH_REC           = (OPCODE + MEMNUM);
    final int MEMORY_END                    = (OPCODE + MEMNUM);
    final int MEMORY_END_REC                = (OPCODE + MEMNUM);
    final int PUSH_STOP_BT                  = OPCODE;
    final int POP_STOP_BT                   = OPCODE;
    final int NULL_CHECK_START              = (OPCODE + MEMNUM);
    final int NULL_CHECK_END                = (OPCODE + MEMNUM);
    final int LOOK_BEHIND                   = (OPCODE + LENGTH);
    final int PUSH_LOOK_BEHIND_NOT          = (OPCODE + RELADDR + LENGTH);
    final int FAIL_LOOK_BEHIND_NOT          = OPCODE;
    final int CALL                          = (OPCODE + ABSADDR);
    final int RETURN                        = OPCODE;

    // #ifdef USE_COMBINATION_EXPLOSION_CHECK
    final int STATE_CHECK                   = (OPCODE + STATE_CHECK_NUM);
    final int STATE_CHECK_PUSH              = (OPCODE + STATE_CHECK_NUM + RELADDR);
    final int STATE_CHECK_PUSH_OR_JUMP      = (OPCODE + STATE_CHECK_NUM + RELADDR);
    final int STATE_CHECK_ANYCHAR_STAR      = (OPCODE + STATE_CHECK_NUM);
}
