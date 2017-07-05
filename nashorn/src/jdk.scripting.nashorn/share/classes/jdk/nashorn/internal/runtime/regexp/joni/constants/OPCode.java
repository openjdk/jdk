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

public interface OPCode {
    final int FINISH                        = 0;            /* matching process terminator (no more alternative) */
    final int END                           = 1;            /* pattern code terminator (success end) */

    final int EXACT1                        = 2;            /* single byte, N = 1 */
    final int EXACT2                        = 3;            /* single byte, N = 2 */
    final int EXACT3                        = 4;            /* single byte, N = 3 */
    final int EXACT4                        = 5;            /* single byte, N = 4 */
    final int EXACT5                        = 6;            /* single byte, N = 5 */
    final int EXACTN                        = 7;            /* single byte */

    final int EXACT1_IC                     = 14;           /* single byte, N = 1, ignore case */
    final int EXACTN_IC                     = 15;           /* single byte,        ignore case */

    final int CCLASS                        = 16;
    final int CCLASS_MB                     = 17;
    final int CCLASS_MIX                    = 18;
    final int CCLASS_NOT                    = 19;
    final int CCLASS_MB_NOT                 = 20;
    final int CCLASS_MIX_NOT                = 21;
    final int CCLASS_NODE                   = 22;           /* pointer to CClassNode node */

    final int ANYCHAR                       = 23;           /* "."  */
    final int ANYCHAR_ML                    = 24;           /* "."  multi-line */
    final int ANYCHAR_STAR                  = 25;           /* ".*" */
    final int ANYCHAR_ML_STAR               = 26;           /* ".*" multi-line */
    final int ANYCHAR_STAR_PEEK_NEXT        = 27;
    final int ANYCHAR_ML_STAR_PEEK_NEXT     = 28;

    final int WORD                          = 29;
    final int NOT_WORD                      = 30;
    final int WORD_BOUND                    = 31;
    final int NOT_WORD_BOUND                = 32;
    final int WORD_BEGIN                    = 33;
    final int WORD_END                      = 34;

    final int BEGIN_BUF                     = 35;
    final int END_BUF                       = 36;
    final int BEGIN_LINE                    = 37;
    final int END_LINE                      = 38;
    final int SEMI_END_BUF                  = 39;
    final int BEGIN_POSITION                = 40;

    final int BACKREF1                      = 41;
    final int BACKREF2                      = 42;
    final int BACKREFN                      = 43;
    final int BACKREFN_IC                   = 44;
    final int BACKREF_MULTI                 = 45;
    final int BACKREF_MULTI_IC              = 46;
    final int BACKREF_WITH_LEVEL            = 47;           /* \k<xxx+n>, \k<xxx-n> */

    final int MEMORY_START                  = 48;
    final int MEMORY_START_PUSH             = 49;           /* push back-tracker to stack */
    final int MEMORY_END_PUSH               = 50;           /* push back-tracker to stack */
    final int MEMORY_END_PUSH_REC           = 51;           /* push back-tracker to stack */
    final int MEMORY_END                    = 52;
    final int MEMORY_END_REC                = 53;           /* push marker to stack */

    final int FAIL                          = 54;           /* pop stack and move */
    final int JUMP                          = 55;
    final int PUSH                          = 56;
    final int POP                           = 57;
    final int PUSH_OR_JUMP_EXACT1           = 58;           /* if match exact then push, else jump. */
    final int PUSH_IF_PEEK_NEXT             = 59;           /* if match exact then push, else none. */

    final int REPEAT                        = 60;           /* {n,m} */
    final int REPEAT_NG                     = 61;           /* {n,m}? (non greedy) */
    final int REPEAT_INC                    = 62;
    final int REPEAT_INC_NG                 = 63;           /* non greedy */
    final int REPEAT_INC_SG                 = 64;           /* search and get in stack */
    final int REPEAT_INC_NG_SG              = 65;           /* search and get in stack (non greedy) */

    final int NULL_CHECK_START              = 66;           /* null loop checker start */
    final int NULL_CHECK_END                = 67;           /* null loop checker end   */
    final int NULL_CHECK_END_MEMST          = 68;           /* null loop checker end (with capture status) */
    final int NULL_CHECK_END_MEMST_PUSH     = 69;           /* with capture status and push check-end */

    final int PUSH_POS                      = 70;           /* (?=...)  start */
    final int POP_POS                       = 71;           /* (?=...)  end   */
    final int PUSH_POS_NOT                  = 72;           /* (?!...)  start */
    final int FAIL_POS                      = 73;           /* (?!...)  end   */
    final int PUSH_STOP_BT                  = 74;           /* (?>...)  start */
    final int POP_STOP_BT                   = 75;           /* (?>...)  end   */
    final int LOOK_BEHIND                   = 76;           /* (?<=...) start (no needs end opcode) */
    final int PUSH_LOOK_BEHIND_NOT          = 77;           /* (?<!...) start */
    final int FAIL_LOOK_BEHIND_NOT          = 78;           /* (?<!...) end   */

    final int CALL                          = 79;           /* \g<name> */
    final int RETURN                        = 80;

    final int STATE_CHECK_PUSH              = 81;           /* combination explosion check and push */
    final int STATE_CHECK_PUSH_OR_JUMP      = 82;           /* check ok -> push, else jump  */
    final int STATE_CHECK                   = 83;           /* check only */
    final int STATE_CHECK_ANYCHAR_STAR      = 84;
    final int STATE_CHECK_ANYCHAR_ML_STAR   = 85;

      /* no need: IS_DYNAMIC_OPTION() == 0 */
    final int SET_OPTION_PUSH               = 86;           /* set option and push recover option */
    final int SET_OPTION                    = 87;           /* set option */

}
