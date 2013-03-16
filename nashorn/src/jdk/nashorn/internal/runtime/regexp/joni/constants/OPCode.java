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

import jdk.nashorn.internal.runtime.regexp.joni.Config;

public interface OPCode {
    final int FINISH                        = 0;            /* matching process terminator (no more alternative) */
    final int END                           = 1;            /* pattern code terminator (success end) */

    final int EXACT1                        = 2;            /* single byte, N = 1 */
    final int EXACT2                        = 3;            /* single byte, N = 2 */
    final int EXACT3                        = 4;            /* single byte, N = 3 */
    final int EXACT4                        = 5;            /* single byte, N = 4 */
    final int EXACT5                        = 6;            /* single byte, N = 5 */
    final int EXACTN                        = 7;            /* single byte */
    final int EXACTMB2N1                    = 8;            /* mb-length = 2 N = 1 */
    final int EXACTMB2N2                    = 9;            /* mb-length = 2 N = 2 */
    final int EXACTMB2N3                    = 10;           /* mb-length = 2 N = 3 */
    final int EXACTMB2N                     = 11;           /* mb-length = 2 */
    final int EXACTMB3N                     = 12;           /* mb-length = 3 */
    final int EXACTMBN                      = 13;           /* other length */

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

    // single byte versions
    final int ANYCHAR_SB                    = 88;           /* "."  */
    final int ANYCHAR_ML_SB                 = 89;           /* "."  multi-line */
    final int ANYCHAR_STAR_SB               = 90;           /* ".*" */
    final int ANYCHAR_ML_STAR_SB            = 91;           /* ".*" multi-line */
    final int ANYCHAR_STAR_PEEK_NEXT_SB     = 92;
    final int ANYCHAR_ML_STAR_PEEK_NEXT_SB  = 93;
    final int STATE_CHECK_ANYCHAR_STAR_SB   = 94;
    final int STATE_CHECK_ANYCHAR_ML_STAR_SB= 95;

    final int CCLASS_SB                     = 96;
    final int CCLASS_NOT_SB                 = 97;
    final int WORD_SB                       = 98;
    final int NOT_WORD_SB                   = 99;
    final int WORD_BOUND_SB                 = 100;
    final int NOT_WORD_BOUND_SB             = 101;
    final int WORD_BEGIN_SB                 = 102;
    final int WORD_END_SB                   = 103;

    final int LOOK_BEHIND_SB                = 104;

    final int EXACT1_IC_SB                  = 105;           /* single byte, N = 1, ignore case */
    final int EXACTN_IC_SB                  = 106;           /* single byte,        ignore case */


    public final String OpCodeNames[] = Config.DEBUG_COMPILE ? new String[] {
        "finish", /*OP_FINISH*/
        "end", /*OP_END*/
        "exact1", /*OP_EXACT1*/
        "exact2", /*OP_EXACT2*/
        "exact3", /*OP_EXACT3*/
        "exact4", /*OP_EXACT4*/
        "exact5", /*OP_EXACT5*/
        "exactn", /*OP_EXACTN*/
        "exactmb2-n1", /*OP_EXACTMB2N1*/
        "exactmb2-n2", /*OP_EXACTMB2N2*/
        "exactmb2-n3", /*OP_EXACTMB2N3*/
        "exactmb2-n", /*OP_EXACTMB2N*/
        "exactmb3n", /*OP_EXACTMB3N*/
        "exactmbn", /*OP_EXACTMBN*/
        "exact1-ic", /*OP_EXACT1_IC*/
        "exactn-ic", /*OP_EXACTN_IC*/
        "cclass", /*OP_CCLASS*/
        "cclass-mb", /*OP_CCLASS_MB*/
        "cclass-mix", /*OP_CCLASS_MIX*/
        "cclass-not", /*OP_CCLASS_NOT*/
        "cclass-mb-not", /*OP_CCLASS_MB_NOT*/
        "cclass-mix-not", /*OP_CCLASS_MIX_NOT*/
        "cclass-node", /*OP_CCLASS_NODE*/
        "anychar", /*OP_ANYCHAR*/
        "anychar-ml", /*OP_ANYCHAR_ML*/
        "anychar*", /*OP_ANYCHAR_STAR*/
        "anychar-ml*", /*OP_ANYCHAR_ML_STAR*/
        "anychar*-peek-next", /*OP_ANYCHAR_STAR_PEEK_NEXT*/
        "anychar-ml*-peek-next", /*OP_ANYCHAR_ML_STAR_PEEK_NEXT*/
        "word", /*OP_WORD*/
        "not-word", /*OP_NOT_WORD*/
        "word-bound", /*OP_WORD_BOUND*/
        "not-word-bound", /*OP_NOT_WORD_BOUND*/
        "word-begin", /*OP_WORD_BEGIN*/
        "word-end", /*OP_WORD_END*/
        "begin-buf", /*OP_BEGIN_BUF*/
        "end-buf", /*OP_END_BUF*/
        "begin-line", /*OP_BEGIN_LINE*/
        "end-line", /*OP_END_LINE*/
        "semi-end-buf", /*OP_SEMI_END_BUF*/
        "begin-position", /*OP_BEGIN_POSITION*/
        "backref1", /*OP_BACKREF1*/
        "backref2", /*OP_BACKREF2*/
        "backrefn", /*OP_BACKREFN*/
        "backrefn-ic", /*OP_BACKREFN_IC*/
        "backref_multi", /*OP_BACKREF_MULTI*/
        "backref_multi-ic", /*OP_BACKREF_MULTI_IC*/
        "backref_at_level", /*OP_BACKREF_AT_LEVEL*/
        "mem-start", /*OP_MEMORY_START*/
        "mem-start-push", /*OP_MEMORY_START_PUSH*/
        "mem-end-push", /*OP_MEMORY_END_PUSH*/
        "mem-end-push-rec", /*OP_MEMORY_END_PUSH_REC*/
        "mem-end", /*OP_MEMORY_END*/
        "mem-end-rec", /*OP_MEMORY_END_REC*/
        "fail", /*OP_FAIL*/
        "jump", /*OP_JUMP*/
        "push", /*OP_PUSH*/
        "pop", /*OP_POP*/
        "push-or-jump-e1", /*OP_PUSH_OR_JUMP_EXACT1*/
        "push-if-peek-next", /*OP_PUSH_IF_PEEK_NEXT*/
        "repeat", /*OP_REPEAT*/
        "repeat-ng", /*OP_REPEAT_NG*/
        "repeat-inc", /*OP_REPEAT_INC*/
        "repeat-inc-ng", /*OP_REPEAT_INC_NG*/
        "repeat-inc-sg", /*OP_REPEAT_INC_SG*/
        "repeat-inc-ng-sg", /*OP_REPEAT_INC_NG_SG*/
        "null-check-start", /*OP_NULL_CHECK_START*/
        "null-check-end", /*OP_NULL_CHECK_END*/
        "null-check-end-memst", /*OP_NULL_CHECK_END_MEMST*/
        "null-check-end-memst-push", /*OP_NULL_CHECK_END_MEMST_PUSH*/
        "push-pos", /*OP_PUSH_POS*/
        "pop-pos", /*OP_POP_POS*/
        "push-pos-not", /*OP_PUSH_POS_NOT*/
        "fail-pos", /*OP_FAIL_POS*/
        "push-stop-bt", /*OP_PUSH_STOP_BT*/
        "pop-stop-bt", /*OP_POP_STOP_BT*/
        "look-behind", /*OP_LOOK_BEHIND*/
        "push-look-behind-not", /*OP_PUSH_LOOK_BEHIND_NOT*/
        "fail-look-behind-not", /*OP_FAIL_LOOK_BEHIND_NOT*/
        "call", /*OP_CALL*/
        "return", /*OP_RETURN*/
        "state-check-push", /*OP_STATE_CHECK_PUSH*/
        "state-check-push-or-jump", /*OP_STATE_CHECK_PUSH_OR_JUMP*/
        "state-check", /*OP_STATE_CHECK*/
        "state-check-anychar*", /*OP_STATE_CHECK_ANYCHAR_STAR*/
        "state-check-anychar-ml*", /*OP_STATE_CHECK_ANYCHAR_ML_STAR*/
        "set-option-push", /*OP_SET_OPTION_PUSH*/
        "set-option", /*OP_SET_OPTION*/

        // single byte versions
        "anychar-sb", /*OP_ANYCHAR*/
        "anychar-ml-sb", /*OP_ANYCHAR_ML*/
        "anychar*-sb", /*OP_ANYCHAR_STAR*/
        "anychar-ml*-sb", /*OP_ANYCHAR_ML_STAR*/
        "anychar*-peek-next-sb", /*OP_ANYCHAR_STAR_PEEK_NEXT*/
        "anychar-ml*-peek-next-sb", /*OP_ANYCHAR_ML_STAR_PEEK_NEXT*/
        "state-check-anychar*-sb", /*OP_STATE_CHECK_ANYCHAR_STAR*/
        "state-check-anychar-ml*-sb", /*OP_STATE_CHECK_ANYCHAR_ML_STAR*/

        "cclass-sb", /*OP_CCLASS*/
        "cclass-not-sb", /*OP_CCLASS_NOT*/

        "word-sb", /*OP_WORD*/
        "not-word-sb", /*OP_NOT_WORD*/
        "word-bound-sb", /*OP_WORD_BOUND*/
        "not-word-bound-sb", /*OP_NOT_WORD_BOUND*/
        "word-begin-sb", /*OP_WORD_BEGIN*/
        "word-end-sb", /*OP_WORD_END*/

        "look-behind-sb", /*OP_LOOK_BEHIND*/

        "exact1-ic-sb", /*OP_EXACT1_IC*/
        "exactn-ic-sb", /*OP_EXACTN_IC*/

    } : null;

    public final int OpCodeArgTypes[] = Config.DEBUG_COMPILE ? new int[] {
        Arguments.NON, /*OP_FINISH*/
        Arguments.NON, /*OP_END*/
        Arguments.SPECIAL, /*OP_EXACT1*/
        Arguments.SPECIAL, /*OP_EXACT2*/
        Arguments.SPECIAL, /*OP_EXACT3*/
        Arguments.SPECIAL, /*OP_EXACT4*/
        Arguments.SPECIAL, /*OP_EXACT5*/
        Arguments.SPECIAL, /*OP_EXACTN*/
        Arguments.SPECIAL, /*OP_EXACTMB2N1*/
        Arguments.SPECIAL, /*OP_EXACTMB2N2*/
        Arguments.SPECIAL, /*OP_EXACTMB2N3*/
        Arguments.SPECIAL, /*OP_EXACTMB2N*/
        Arguments.SPECIAL, /*OP_EXACTMB3N*/
        Arguments.SPECIAL, /*OP_EXACTMBN*/
        Arguments.SPECIAL, /*OP_EXACT1_IC*/
        Arguments.SPECIAL, /*OP_EXACTN_IC*/
        Arguments.SPECIAL, /*OP_CCLASS*/
        Arguments.SPECIAL, /*OP_CCLASS_MB*/
        Arguments.SPECIAL, /*OP_CCLASS_MIX*/
        Arguments.SPECIAL, /*OP_CCLASS_NOT*/
        Arguments.SPECIAL, /*OP_CCLASS_MB_NOT*/
        Arguments.SPECIAL, /*OP_CCLASS_MIX_NOT*/
        Arguments.SPECIAL, /*OP_CCLASS_NODE*/
        Arguments.NON, /*OP_ANYCHAR*/
        Arguments.NON, /*OP_ANYCHAR_ML*/
        Arguments.NON, /*OP_ANYCHAR_STAR*/
        Arguments.NON, /*OP_ANYCHAR_ML_STAR*/
        Arguments.SPECIAL, /*OP_ANYCHAR_STAR_PEEK_NEXT*/
        Arguments.SPECIAL, /*OP_ANYCHAR_ML_STAR_PEEK_NEXT*/
        Arguments.NON, /*OP_WORD*/
        Arguments.NON, /*OP_NOT_WORD*/
        Arguments.NON, /*OP_WORD_BOUND*/
        Arguments.NON, /*OP_NOT_WORD_BOUND*/
        Arguments.NON, /*OP_WORD_BEGIN*/
        Arguments.NON, /*OP_WORD_END*/
        Arguments.NON, /*OP_BEGIN_BUF*/
        Arguments.NON, /*OP_END_BUF*/
        Arguments.NON, /*OP_BEGIN_LINE*/
        Arguments.NON, /*OP_END_LINE*/
        Arguments.NON, /*OP_SEMI_END_BUF*/
        Arguments.NON, /*OP_BEGIN_POSITION*/
        Arguments.NON, /*OP_BACKREF1*/
        Arguments.NON, /*OP_BACKREF2*/
        Arguments.MEMNUM, /*OP_BACKREFN*/
        Arguments.SPECIAL, /*OP_BACKREFN_IC*/
        Arguments.SPECIAL, /*OP_BACKREF_MULTI*/
        Arguments.SPECIAL, /*OP_BACKREF_MULTI_IC*/
        Arguments.SPECIAL, /*OP_BACKREF_AT_LEVEL*/
        Arguments.MEMNUM, /*OP_MEMORY_START*/
        Arguments.MEMNUM, /*OP_MEMORY_START_PUSH*/
        Arguments.MEMNUM, /*OP_MEMORY_END_PUSH*/
        Arguments.MEMNUM, /*OP_MEMORY_END_PUSH_REC*/
        Arguments.MEMNUM, /*OP_MEMORY_END*/
        Arguments.MEMNUM, /*OP_MEMORY_END_REC*/
        Arguments.NON, /*OP_FAIL*/
        Arguments.RELADDR, /*OP_JUMP*/
        Arguments.RELADDR, /*OP_PUSH*/
        Arguments.NON, /*OP_POP*/
        Arguments.SPECIAL, /*OP_PUSH_OR_JUMP_EXACT1*/
        Arguments.SPECIAL, /*OP_PUSH_IF_PEEK_NEXT*/
        Arguments.SPECIAL, /*OP_REPEAT*/
        Arguments.SPECIAL, /*OP_REPEAT_NG*/
        Arguments.MEMNUM, /*OP_REPEAT_INC*/
        Arguments.MEMNUM, /*OP_REPEAT_INC_NG*/
        Arguments.MEMNUM, /*OP_REPEAT_INC_SG*/
        Arguments.MEMNUM, /*OP_REPEAT_INC_NG_SG*/
        Arguments.MEMNUM, /*OP_NULL_CHECK_START*/
        Arguments.MEMNUM, /*OP_NULL_CHECK_END*/
        Arguments.MEMNUM, /*OP_NULL_CHECK_END_MEMST*/
        Arguments.MEMNUM, /*OP_NULL_CHECK_END_MEMST_PUSH*/
        Arguments.NON, /*OP_PUSH_POS*/
        Arguments.NON, /*OP_POP_POS*/
        Arguments.RELADDR, /*OP_PUSH_POS_NOT*/
        Arguments.NON, /*OP_FAIL_POS*/
        Arguments.NON, /*OP_PUSH_STOP_BT*/
        Arguments.NON, /*OP_POP_STOP_BT*/
        Arguments.SPECIAL, /*OP_LOOK_BEHIND*/
        Arguments.SPECIAL, /*OP_PUSH_LOOK_BEHIND_NOT*/
        Arguments.NON, /*OP_FAIL_LOOK_BEHIND_NOT*/
        Arguments.ABSADDR, /*OP_CALL*/
        Arguments.NON, /*OP_RETURN*/
        Arguments.SPECIAL, /*OP_STATE_CHECK_PUSH*/
        Arguments.SPECIAL, /*OP_STATE_CHECK_PUSH_OR_JUMP*/
        Arguments.STATE_CHECK, /*OP_STATE_CHECK*/
        Arguments.STATE_CHECK, /*OP_STATE_CHECK_ANYCHAR_STAR*/
        Arguments.STATE_CHECK, /*OP_STATE_CHECK_ANYCHAR_ML_STAR*/
        Arguments.OPTION, /*OP_SET_OPTION_PUSH*/
        Arguments.OPTION, /*OP_SET_OPTION*/

        // single byte versions
        Arguments.NON, /*OP_ANYCHAR*/
        Arguments.NON, /*OP_ANYCHAR_ML*/
        Arguments.NON, /*OP_ANYCHAR_STAR*/
        Arguments.NON, /*OP_ANYCHAR_ML_STAR*/
        Arguments.SPECIAL, /*OP_ANYCHAR_STAR_PEEK_NEXT*/
        Arguments.SPECIAL, /*OP_ANYCHAR_ML_STAR_PEEK_NEXT*/
        Arguments.STATE_CHECK, /*OP_STATE_CHECK_ANYCHAR_STAR*/
        Arguments.STATE_CHECK, /*OP_STATE_CHECK_ANYCHAR_ML_STAR*/

        Arguments.SPECIAL, /*OP_CCLASS*/
        Arguments.SPECIAL, /*OP_CCLASS_NOT*/

        Arguments.NON, /*OP_WORD*/
        Arguments.NON, /*OP_NOT_WORD*/
        Arguments.NON, /*OP_WORD_BOUND*/
        Arguments.NON, /*OP_NOT_WORD_BOUND*/
        Arguments.NON, /*OP_WORD_BEGIN*/
        Arguments.NON, /*OP_WORD_END*/

        Arguments.SPECIAL, /*OP_LOOK_BEHIND*/

        Arguments.SPECIAL, /*OP_EXACT1_IC*/
        Arguments.SPECIAL, /*OP_EXACTN_IC*/
    } : null;
}
