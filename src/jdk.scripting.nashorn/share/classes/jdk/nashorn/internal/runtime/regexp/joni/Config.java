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

import java.io.PrintStream;

@SuppressWarnings("javadoc")
public interface Config {
    final int CHAR_TABLE_SIZE = 256;

    /* from jcodings */
    final boolean VANILLA = false;
    final int INTERNAL_ENC_CASE_FOLD_MULTI_CHAR = (1<<30);
    final int ENC_CASE_FOLD_MIN = INTERNAL_ENC_CASE_FOLD_MULTI_CHAR;
    final int ENC_CASE_FOLD_DEFAULT = ENC_CASE_FOLD_MIN;

    final boolean USE_MONOMANIAC_CHECK_CAPTURES_IN_ENDLESS_REPEAT = true; /* /(?:()|())*\2/ */
    final boolean USE_NEWLINE_AT_END_OF_STRING_HAS_EMPTY_LINE = true;     /* /\n$/ =~ "\n" */
    final boolean USE_WARNING_REDUNDANT_NESTED_REPEAT_OPERATOR = false;

    final boolean CASE_FOLD_IS_APPLIED_INSIDE_NEGATIVE_CCLASS = true;

    final boolean USE_MATCH_RANGE_MUST_BE_INSIDE_OF_SPECIFIED_RANGE = false;
    final boolean USE_VARIABLE_META_CHARS = true;
    final boolean USE_WORD_BEGIN_END = true;                                /* "\<": word-begin, "\>": word-end */
    final boolean USE_POSIX_API_REGION_OPTION = false;                           /* needed for POSIX API support */
    final boolean USE_FIND_LONGEST_SEARCH_ALL_OF_RANGE = true;

    final int NREGION                   = 10;
    final int MAX_BACKREF_NUM           = 1000;
    final int MAX_CAPTURE_GROUP_NUM     = 0x8000;
    final int MAX_REPEAT_NUM            = 100000;
    final int MAX_MULTI_BYTE_RANGES_NUM = 10000;


    final boolean USE_WARN = true;

    // internal config
    final boolean USE_PARSE_TREE_NODE_RECYCLE       = true;
    final boolean USE_OP_PUSH_OR_JUMP_EXACT         = true;
    final boolean USE_SHARED_CCLASS_TABLE           = false;
    final boolean USE_QTFR_PEEK_NEXT                = true;

    final int INIT_MATCH_STACK_SIZE                 = 64;
    final int DEFAULT_MATCH_STACK_LIMIT_SIZE        = 0;        /* unlimited */
    final int NUMBER_OF_POOLED_STACKS               = 4;



    final boolean DONT_OPTIMIZE                     = false;

    final boolean USE_STRING_TEMPLATES              = true; // use embedded string templates in Regex object as byte arrays instead of compiling them into int bytecode array

    final boolean NON_UNICODE_SDW                   = true;


    final PrintStream log = System.out;
    final PrintStream err = System.err;

    final boolean DEBUG_ALL                         = false;

    final boolean DEBUG                             = DEBUG_ALL;
    final boolean DEBUG_PARSE_TREE                  = DEBUG_ALL;
    final boolean DEBUG_PARSE_TREE_RAW              = true;
    final boolean DEBUG_COMPILE                     = DEBUG_ALL;
    final boolean DEBUG_COMPILE_BYTE_CODE_INFO      = DEBUG_ALL;
    final boolean DEBUG_SEARCH                      = DEBUG_ALL;
    final boolean DEBUG_MATCH                       = DEBUG_ALL;
}
