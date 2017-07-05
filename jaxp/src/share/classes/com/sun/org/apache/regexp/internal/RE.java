/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.regexp.internal;

import java.io.Serializable;
import java.util.Vector;

/**
 * RE is an efficient, lightweight regular expression evaluator/matcher
 * class. Regular expressions are pattern descriptions which enable
 * sophisticated matching of strings.  In addition to being able to
 * match a string against a pattern, you can also extract parts of the
 * match.  This is especially useful in text parsing! Details on the
 * syntax of regular expression patterns are given below.
 *
 * <p>
 * To compile a regular expression (RE), you can simply construct an RE
 * matcher object from the string specification of the pattern, like this:
 *
 * <pre>
 *  RE r = new RE("a*b");
 * </pre>
 *
 * <p>
 * Once you have done this, you can call either of the RE.match methods to
 * perform matching on a String.  For example:
 *
 * <pre>
 *  boolean matched = r.match("aaaab");
 * </pre>
 *
 * will cause the boolean matched to be set to true because the
 * pattern "a*b" matches the string "aaaab".
 *
 * <p>
 * If you were interested in the <i>number</i> of a's which matched the
 * first part of our example expression, you could change the expression to
 * "(a*)b".  Then when you compiled the expression and matched it against
 * something like "xaaaab", you would get results like this:
 *
 * <pre>
 *  RE r = new RE("(a*)b");                  // Compile expression
 *  boolean matched = r.match("xaaaab");     // Match against "xaaaab"
 *
 *  String wholeExpr = r.getParen(0);        // wholeExpr will be 'aaaab'
 *  String insideParens = r.getParen(1);     // insideParens will be 'aaaa'
 *
 *  int startWholeExpr = r.getParenStart(0); // startWholeExpr will be index 1
 *  int endWholeExpr = r.getParenEnd(0);     // endWholeExpr will be index 6
 *  int lenWholeExpr = r.getParenLength(0);  // lenWholeExpr will be 5
 *
 *  int startInside = r.getParenStart(1);    // startInside will be index 1
 *  int endInside = r.getParenEnd(1);        // endInside will be index 5
 *  int lenInside = r.getParenLength(1);     // lenInside will be 4
 * </pre>
 *
 * You can also refer to the contents of a parenthesized expression
 * within a regular expression itself.  This is called a
 * 'backreference'.  The first backreference in a regular expression is
 * denoted by \1, the second by \2 and so on.  So the expression:
 *
 * <pre>
 *  ([0-9]+)=\1
 * </pre>
 *
 * will match any string of the form n=n (like 0=0 or 2=2).
 *
 * <p>
 * The full regular expression syntax accepted by RE is described here:
 *
 * <pre>
 *
 *  <b><font face=times roman>Characters</font></b>
 *
 *    <i>unicodeChar</i>   Matches any identical unicode character
 *    \                    Used to quote a meta-character (like '*')
 *    \\                   Matches a single '\' character
 *    \0nnn                Matches a given octal character
 *    \xhh                 Matches a given 8-bit hexadecimal character
 *    \\uhhhh              Matches a given 16-bit hexadecimal character
 *    \t                   Matches an ASCII tab character
 *    \n                   Matches an ASCII newline character
 *    \r                   Matches an ASCII return character
 *    \f                   Matches an ASCII form feed character
 *
 *
 *  <b><font face=times roman>Character Classes</font></b>
 *
 *    [abc]                Simple character class
 *    [a-zA-Z]             Character class with ranges
 *    [^abc]               Negated character class
 * </pre>
 *
 * <b>NOTE:</b> Incomplete ranges will be interpreted as &quot;starts
 * from zero&quot; or &quot;ends with last character&quot;.
 * <br>
 * I.e. [-a] is the same as [\\u0000-a], and [a-] is the same as [a-\\uFFFF],
 * [-] means &quot;all characters&quot;.
 *
 * <pre>
 *
 *  <b><font face=times roman>Standard POSIX Character Classes</font></b>
 *
 *    [:alnum:]            Alphanumeric characters.
 *    [:alpha:]            Alphabetic characters.
 *    [:blank:]            Space and tab characters.
 *    [:cntrl:]            Control characters.
 *    [:digit:]            Numeric characters.
 *    [:graph:]            Characters that are printable and are also visible.
 *                         (A space is printable, but not visible, while an
 *                         `a' is both.)
 *    [:lower:]            Lower-case alphabetic characters.
 *    [:print:]            Printable characters (characters that are not
 *                         control characters.)
 *    [:punct:]            Punctuation characters (characters that are not letter,
 *                         digits, control characters, or space characters).
 *    [:space:]            Space characters (such as space, tab, and formfeed,
 *                         to name a few).
 *    [:upper:]            Upper-case alphabetic characters.
 *    [:xdigit:]           Characters that are hexadecimal digits.
 *
 *
 *  <b><font face=times roman>Non-standard POSIX-style Character Classes</font></b>
 *
 *    [:javastart:]        Start of a Java identifier
 *    [:javapart:]         Part of a Java identifier
 *
 *
 *  <b><font face=times roman>Predefined Classes</font></b>
 *
 *    .         Matches any character other than newline
 *    \w        Matches a "word" character (alphanumeric plus "_")
 *    \W        Matches a non-word character
 *    \s        Matches a whitespace character
 *    \S        Matches a non-whitespace character
 *    \d        Matches a digit character
 *    \D        Matches a non-digit character
 *
 *
 *  <b><font face=times roman>Boundary Matchers</font></b>
 *
 *    ^         Matches only at the beginning of a line
 *    $         Matches only at the end of a line
 *    \b        Matches only at a word boundary
 *    \B        Matches only at a non-word boundary
 *
 *
 *  <b><font face=times roman>Greedy Closures</font></b>
 *
 *    A*        Matches A 0 or more times (greedy)
 *    A+        Matches A 1 or more times (greedy)
 *    A?        Matches A 1 or 0 times (greedy)
 *    A{n}      Matches A exactly n times (greedy)
 *    A{n,}     Matches A at least n times (greedy)
 *    A{n,m}    Matches A at least n but not more than m times (greedy)
 *
 *
 *  <b><font face=times roman>Reluctant Closures</font></b>
 *
 *    A*?       Matches A 0 or more times (reluctant)
 *    A+?       Matches A 1 or more times (reluctant)
 *    A??       Matches A 0 or 1 times (reluctant)
 *
 *
 *  <b><font face=times roman>Logical Operators</font></b>
 *
 *    AB        Matches A followed by B
 *    A|B       Matches either A or B
 *    (A)       Used for subexpression grouping
 *   (?:A)      Used for subexpression clustering (just like grouping but
 *              no backrefs)
 *
 *
 *  <b><font face=times roman>Backreferences</font></b>
 *
 *    \1    Backreference to 1st parenthesized subexpression
 *    \2    Backreference to 2nd parenthesized subexpression
 *    \3    Backreference to 3rd parenthesized subexpression
 *    \4    Backreference to 4th parenthesized subexpression
 *    \5    Backreference to 5th parenthesized subexpression
 *    \6    Backreference to 6th parenthesized subexpression
 *    \7    Backreference to 7th parenthesized subexpression
 *    \8    Backreference to 8th parenthesized subexpression
 *    \9    Backreference to 9th parenthesized subexpression
 * </pre>
 *
 * <p>
 * All closure operators (+, *, ?, {m,n}) are greedy by default, meaning
 * that they match as many elements of the string as possible without
 * causing the overall match to fail.  If you want a closure to be
 * reluctant (non-greedy), you can simply follow it with a '?'.  A
 * reluctant closure will match as few elements of the string as
 * possible when finding matches.  {m,n} closures don't currently
 * support reluctancy.
 *
 * <p>
 * <b><font face="times roman">Line terminators</font></b>
 * <br>
 * A line terminator is a one- or two-character sequence that marks
 * the end of a line of the input character sequence. The following
 * are recognized as line terminators:
 * <ul>
 * <li>A newline (line feed) character ('\n'),</li>
 * <li>A carriage-return character followed immediately by a newline character ("\r\n"),</li>
 * <li>A standalone carriage-return character ('\r'),</li>
 * <li>A next-line character ('\u0085'),</li>
 * <li>A line-separator character ('\u2028'), or</li>
 * <li>A paragraph-separator character ('\u2029).</li>
 * </ul>
 *
 * <p>
 * RE runs programs compiled by the RECompiler class.  But the RE
 * matcher class does not include the actual regular expression compiler
 * for reasons of efficiency.  In fact, if you want to pre-compile one
 * or more regular expressions, the 'recompile' class can be invoked
 * from the command line to produce compiled output like this:
 *
 * <pre>
 *    // Pre-compiled regular expression "a*b"
 *    char[] re1Instructions =
 *    {
 *        0x007c, 0x0000, 0x001a, 0x007c, 0x0000, 0x000d, 0x0041,
 *        0x0001, 0x0004, 0x0061, 0x007c, 0x0000, 0x0003, 0x0047,
 *        0x0000, 0xfff6, 0x007c, 0x0000, 0x0003, 0x004e, 0x0000,
 *        0x0003, 0x0041, 0x0001, 0x0004, 0x0062, 0x0045, 0x0000,
 *        0x0000,
 *    };
 *
 *
 *    REProgram re1 = new REProgram(re1Instructions);
 * </pre>
 *
 * You can then construct a regular expression matcher (RE) object from
 * the pre-compiled expression re1 and thus avoid the overhead of
 * compiling the expression at runtime. If you require more dynamic
 * regular expressions, you can construct a single RECompiler object and
 * re-use it to compile each expression. Similarly, you can change the
 * program run by a given matcher object at any time. However, RE and
 * RECompiler are not threadsafe (for efficiency reasons, and because
 * requiring thread safety in this class is deemed to be a rare
 * requirement), so you will need to construct a separate compiler or
 * matcher object for each thread (unless you do thread synchronization
 * yourself). Once expression compiled into the REProgram object, REProgram
 * can be safely shared across multiple threads and RE objects.
 *
 * <br><p><br>
 *
 * <font color="red">
 * <i>ISSUES:</i>
 *
 * <ul>
 *  <li>com.weusours.util.re is not currently compatible with all
 *      standard POSIX regcomp flags</li>
 *  <li>com.weusours.util.re does not support POSIX equivalence classes
 *      ([=foo=] syntax) (I18N/locale issue)</li>
 *  <li>com.weusours.util.re does not support nested POSIX character
 *      classes (definitely should, but not completely trivial)</li>
 *  <li>com.weusours.util.re Does not support POSIX character collation
 *      concepts ([.foo.] syntax) (I18N/locale issue)</li>
 *  <li>Should there be different matching styles (simple, POSIX, Perl etc?)</li>
 *  <li>Should RE support character iterators (for backwards RE matching!)?</li>
 *  <li>Should RE support reluctant {m,n} closures (does anyone care)?</li>
 *  <li>Not *all* possibilities are considered for greediness when backreferences
 *      are involved (as POSIX suggests should be the case).  The POSIX RE
 *      "(ac*)c*d[ac]*\1", when matched against "acdacaa" should yield a match
 *      of acdacaa where \1 is "a".  This is not the case in this RE package,
 *      and actually Perl doesn't go to this extent either!  Until someone
 *      actually complains about this, I'm not sure it's worth "fixing".
 *      If it ever is fixed, test #137 in RETest.txt should be updated.</li>
 * </ul>
 *
 * </font>
 *
 * @see recompile
 * @see RECompiler
 *
 * @author <a href="mailto:jonl@muppetlabs.com">Jonathan Locke</a>
 * @author <a href="mailto:ts@sch-fer.de">Tobias Sch&auml;fer</a>
 */
public class RE implements Serializable
{
    /**
     * Specifies normal, case-sensitive matching behaviour.
     */
    public static final int MATCH_NORMAL          = 0x0000;

    /**
     * Flag to indicate that matching should be case-independent (folded)
     */
    public static final int MATCH_CASEINDEPENDENT = 0x0001;

    /**
     * Newlines should match as BOL/EOL (^ and $)
     */
    public static final int MATCH_MULTILINE       = 0x0002;

    /**
     * Consider all input a single body of text - newlines are matched by .
     */
    public static final int MATCH_SINGLELINE      = 0x0004;

    /************************************************
     *                                              *
     * The format of a node in a program is:        *
     *                                              *
     * [ OPCODE ] [ OPDATA ] [ OPNEXT ] [ OPERAND ] *
     *                                              *
     * char OPCODE - instruction                    *
     * char OPDATA - modifying data                 *
     * char OPNEXT - next node (relative offset)    *
     *                                              *
     ************************************************/

                 //   Opcode              Char       Opdata/Operand  Meaning
                 //   ----------          ---------- --------------- --------------------------------------------------
    static final char OP_END              = 'E';  //                 end of program
    static final char OP_BOL              = '^';  //                 match only if at beginning of line
    static final char OP_EOL              = '$';  //                 match only if at end of line
    static final char OP_ANY              = '.';  //                 match any single character except newline
    static final char OP_ANYOF            = '[';  // count/ranges    match any char in the list of ranges
    static final char OP_BRANCH           = '|';  // node            match this alternative or the next one
    static final char OP_ATOM             = 'A';  // length/string   length of string followed by string itself
    static final char OP_STAR             = '*';  // node            kleene closure
    static final char OP_PLUS             = '+';  // node            positive closure
    static final char OP_MAYBE            = '?';  // node            optional closure
    static final char OP_ESCAPE           = '\\'; // escape          special escape code char class (escape is E_* code)
    static final char OP_OPEN             = '(';  // number          nth opening paren
    static final char OP_OPEN_CLUSTER     = '<';  //                 opening cluster
    static final char OP_CLOSE            = ')';  // number          nth closing paren
    static final char OP_CLOSE_CLUSTER    = '>';  //                 closing cluster
    static final char OP_BACKREF          = '#';  // number          reference nth already matched parenthesized string
    static final char OP_GOTO             = 'G';  //                 nothing but a (back-)pointer
    static final char OP_NOTHING          = 'N';  //                 match null string such as in '(a|)'
    static final char OP_RELUCTANTSTAR    = '8';  // none/expr       reluctant '*' (mnemonic for char is unshifted '*')
    static final char OP_RELUCTANTPLUS    = '=';  // none/expr       reluctant '+' (mnemonic for char is unshifted '+')
    static final char OP_RELUCTANTMAYBE   = '/';  // none/expr       reluctant '?' (mnemonic for char is unshifted '?')
    static final char OP_POSIXCLASS       = 'P';  // classid         one of the posix character classes

    // Escape codes
    static final char E_ALNUM             = 'w';  // Alphanumeric
    static final char E_NALNUM            = 'W';  // Non-alphanumeric
    static final char E_BOUND             = 'b';  // Word boundary
    static final char E_NBOUND            = 'B';  // Non-word boundary
    static final char E_SPACE             = 's';  // Whitespace
    static final char E_NSPACE            = 'S';  // Non-whitespace
    static final char E_DIGIT             = 'd';  // Digit
    static final char E_NDIGIT            = 'D';  // Non-digit

    // Posix character classes
    static final char POSIX_CLASS_ALNUM   = 'w';  // Alphanumerics
    static final char POSIX_CLASS_ALPHA   = 'a';  // Alphabetics
    static final char POSIX_CLASS_BLANK   = 'b';  // Blanks
    static final char POSIX_CLASS_CNTRL   = 'c';  // Control characters
    static final char POSIX_CLASS_DIGIT   = 'd';  // Digits
    static final char POSIX_CLASS_GRAPH   = 'g';  // Graphic characters
    static final char POSIX_CLASS_LOWER   = 'l';  // Lowercase characters
    static final char POSIX_CLASS_PRINT   = 'p';  // Printable characters
    static final char POSIX_CLASS_PUNCT   = '!';  // Punctuation
    static final char POSIX_CLASS_SPACE   = 's';  // Spaces
    static final char POSIX_CLASS_UPPER   = 'u';  // Uppercase characters
    static final char POSIX_CLASS_XDIGIT  = 'x';  // Hexadecimal digits
    static final char POSIX_CLASS_JSTART  = 'j';  // Java identifier start
    static final char POSIX_CLASS_JPART   = 'k';  // Java identifier part

    // Limits
    static final int maxNode  = 65536;            // Maximum number of nodes in a program
    static final int MAX_PAREN = 16;              // Number of paren pairs (only 9 can be backrefs)

    // Node layout constants
    static final int offsetOpcode = 0;            // Opcode offset (first character)
    static final int offsetOpdata = 1;            // Opdata offset (second char)
    static final int offsetNext   = 2;            // Next index offset (third char)
    static final int nodeSize     = 3;            // Node size (in chars)

    // State of current program
    REProgram program;                            // Compiled regular expression 'program'
    transient CharacterIterator search;           // The string being matched against
    int matchFlags;                               // Match behaviour flags
    int maxParen = MAX_PAREN;

    // Parenthesized subexpressions
    transient int parenCount;                     // Number of subexpressions matched (num open parens + 1)
    transient int start0;                         // Cache of start[0]
    transient int end0;                           // Cache of start[0]
    transient int start1;                         // Cache of start[1]
    transient int end1;                           // Cache of start[1]
    transient int start2;                         // Cache of start[2]
    transient int end2;                           // Cache of start[2]
    transient int[] startn;                       // Lazy-alloced array of sub-expression starts
    transient int[] endn;                         // Lazy-alloced array of sub-expression ends

    // Backreferences
    transient int[] startBackref;                 // Lazy-alloced array of backref starts
    transient int[] endBackref;                   // Lazy-alloced array of backref ends

    /**
     * Constructs a regular expression matcher from a String by compiling it
     * using a new instance of RECompiler.  If you will be compiling many
     * expressions, you may prefer to use a single RECompiler object instead.
     *
     * @param pattern The regular expression pattern to compile.
     * @exception RESyntaxException Thrown if the regular expression has invalid syntax.
     * @see RECompiler
     * @see recompile
     */
    public RE(String pattern) throws RESyntaxException
    {
        this(pattern, MATCH_NORMAL);
    }

    /**
     * Constructs a regular expression matcher from a String by compiling it
     * using a new instance of RECompiler.  If you will be compiling many
     * expressions, you may prefer to use a single RECompiler object instead.
     *
     * @param pattern The regular expression pattern to compile.
     * @param matchFlags The matching style
     * @exception RESyntaxException Thrown if the regular expression has invalid syntax.
     * @see RECompiler
     * @see recompile
     */
    public RE(String pattern, int matchFlags) throws RESyntaxException
    {
        this(new RECompiler().compile(pattern));
        setMatchFlags(matchFlags);
    }

    /**
     * Construct a matcher for a pre-compiled regular expression from program
     * (bytecode) data.  Permits special flags to be passed in to modify matching
     * behaviour.
     *
     * @param program Compiled regular expression program (see RECompiler and/or recompile)
     * @param matchFlags One or more of the RE match behaviour flags (RE.MATCH_*):
     *
     * <pre>
     *   MATCH_NORMAL              // Normal (case-sensitive) matching
     *   MATCH_CASEINDEPENDENT     // Case folded comparisons
     *   MATCH_MULTILINE           // Newline matches as BOL/EOL
     * </pre>
     *
     * @see RECompiler
     * @see REProgram
     * @see recompile
     */
    public RE(REProgram program, int matchFlags)
    {
        setProgram(program);
        setMatchFlags(matchFlags);
    }

    /**
     * Construct a matcher for a pre-compiled regular expression from program
     * (bytecode) data.
     *
     * @param program Compiled regular expression program
     * @see RECompiler
     * @see recompile
     */
    public RE(REProgram program)
    {
        this(program, MATCH_NORMAL);
    }

    /**
     * Constructs a regular expression matcher with no initial program.
     * This is likely to be an uncommon practice, but is still supported.
     */
    public RE()
    {
        this((REProgram)null, MATCH_NORMAL);
    }

    /**
     * Converts a 'simplified' regular expression to a full regular expression
     *
     * @param pattern The pattern to convert
     * @return The full regular expression
     */
    public static String simplePatternToFullRegularExpression(String pattern)
    {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < pattern.length(); i++)
        {
            char c = pattern.charAt(i);
            switch (c)
            {
                case '*':
                    buf.append(".*");
                    break;

                case '.':
                case '[':
                case ']':
                case '\\':
                case '+':
                case '?':
                case '{':
                case '}':
                case '$':
                case '^':
                case '|':
                case '(':
                case ')':
                    buf.append('\\');
                default:
                    buf.append(c);
                    break;
            }
        }
        return buf.toString();
    }

    /**
     * Sets match behaviour flags which alter the way RE does matching.
     * @param matchFlags One or more of the RE match behaviour flags (RE.MATCH_*):
     *
     * <pre>
     *   MATCH_NORMAL              // Normal (case-sensitive) matching
     *   MATCH_CASEINDEPENDENT     // Case folded comparisons
     *   MATCH_MULTILINE           // Newline matches as BOL/EOL
     * </pre>
     */
    public void setMatchFlags(int matchFlags)
    {
        this.matchFlags = matchFlags;
    }

    /**
     * Returns the current match behaviour flags.
     * @return Current match behaviour flags (RE.MATCH_*).
     *
     * <pre>
     *   MATCH_NORMAL              // Normal (case-sensitive) matching
     *   MATCH_CASEINDEPENDENT     // Case folded comparisons
     *   MATCH_MULTILINE           // Newline matches as BOL/EOL
     * </pre>
     *
     * @see #setMatchFlags
     */
    public int getMatchFlags()
    {
        return matchFlags;
    }

    /**
     * Sets the current regular expression program used by this matcher object.
     *
     * @param program Regular expression program compiled by RECompiler.
     * @see RECompiler
     * @see REProgram
     * @see recompile
     */
    public void setProgram(REProgram program)
    {
        this.program = program;
        if (program != null && program.maxParens != -1) {
            this.maxParen = program.maxParens;
        } else {
            this.maxParen = MAX_PAREN;
        }
    }

    /**
     * Returns the current regular expression program in use by this matcher object.
     *
     * @return Regular expression program
     * @see #setProgram
     */
    public REProgram getProgram()
    {
        return program;
    }

    /**
     * Returns the number of parenthesized subexpressions available after a successful match.
     *
     * @return Number of available parenthesized subexpressions
     */
    public int getParenCount()
    {
        return parenCount;
    }

    /**
     * Gets the contents of a parenthesized subexpression after a successful match.
     *
     * @param which Nesting level of subexpression
     * @return String
     */
    public String getParen(int which)
    {
        int start;
        if (which < parenCount && (start = getParenStart(which)) >= 0)
        {
            return search.substring(start, getParenEnd(which));
        }
        return null;
    }

    /**
     * Returns the start index of a given paren level.
     *
     * @param which Nesting level of subexpression
     * @return String index
     */
    public final int getParenStart(int which)
    {
        if (which < parenCount)
        {
            switch (which)
            {
                case 0:
                    return start0;

                case 1:
                    return start1;

                case 2:
                    return start2;

                default:
                    if (startn == null)
                    {
                        allocParens();
                    }
                    return startn[which];
            }
        }
        return -1;
    }

    /**
     * Returns the end index of a given paren level.
     *
     * @param which Nesting level of subexpression
     * @return String index
     */
    public final int getParenEnd(int which)
    {
        if (which < parenCount)
        {
            switch (which)
            {
                case 0:
                    return end0;

                case 1:
                    return end1;

                case 2:
                    return end2;

                default:
                    if (endn == null)
                    {
                        allocParens();
                    }
                    return endn[which];
            }
        }
        return -1;
    }

    /**
     * Returns the length of a given paren level.
     *
     * @param which Nesting level of subexpression
     * @return Number of characters in the parenthesized subexpression
     */
    public final int getParenLength(int which)
    {
        if (which < parenCount)
        {
            return getParenEnd(which) - getParenStart(which);
        }
        return -1;
    }

    /**
     * Sets the start of a paren level
     *
     * @param which Which paren level
     * @param i Index in input array
     */
    protected final void setParenStart(int which, int i)
    {
        if (which < parenCount)
        {
            switch (which)
            {
                case 0:
                    start0 = i;
                    break;

                case 1:
                    start1 = i;
                    break;

                case 2:
                    start2 = i;
                    break;

                default:
                    if (startn == null)
                    {
                        allocParens();
                    }
                    startn[which] = i;
                    break;
            }
        }
    }

    /**
     * Sets the end of a paren level
     *
     * @param which Which paren level
     * @param i Index in input array
     */
    protected final void setParenEnd(int which, int i)
    {
        if (which < parenCount)
        {
            switch (which)
            {
                case 0:
                    end0 = i;
                    break;

                case 1:
                    end1 = i;
                    break;

                case 2:
                    end2 = i;
                    break;

                default:
                    if (endn == null)
                    {
                        allocParens();
                    }
                    endn[which] = i;
                    break;
            }
        }
    }

    /**
     * Throws an Error representing an internal error condition probably resulting
     * from a bug in the regular expression compiler (or possibly data corruption).
     * In practice, this should be very rare.
     *
     * @param s Error description
     */
    protected void internalError(String s) throws Error
    {
        throw new Error("RE internal error: " + s);
    }

    /**
     * Performs lazy allocation of subexpression arrays
     */
    private final void allocParens()
    {
        // Allocate arrays for subexpressions
        startn = new int[maxParen];
        endn = new int[maxParen];

        // Set sub-expression pointers to invalid values
        for (int i = 0; i < maxParen; i++)
        {
            startn[i] = -1;
            endn[i] = -1;
        }
    }

    /**
     * Try to match a string against a subset of nodes in the program
     *
     * @param firstNode Node to start at in program
     * @param lastNode  Last valid node (used for matching a subexpression without
     *                  matching the rest of the program as well).
     * @param idxStart  Starting position in character array
     * @return Final input array index if match succeeded.  -1 if not.
     */
    protected int matchNodes(int firstNode, int lastNode, int idxStart)
    {
        // Our current place in the string
        int idx = idxStart;

        // Loop while node is valid
        int next, opcode, opdata;
        int idxNew;
        char[] instruction = program.instruction;
        for (int node = firstNode; node < lastNode; )
        {
            opcode = instruction[node + offsetOpcode];
            next   = node + (short)instruction[node + offsetNext];
            opdata = instruction[node + offsetOpdata];

            switch (opcode)
            {
                case OP_RELUCTANTMAYBE:
                    {
                        int once = 0;
                        do
                        {
                            // Try to match the rest without using the reluctant subexpr
                            if ((idxNew = matchNodes(next, maxNode, idx)) != -1)
                            {
                                return idxNew;
                            }
                        }
                        while ((once++ == 0) && (idx = matchNodes(node + nodeSize, next, idx)) != -1);
                        return -1;
                    }

                case OP_RELUCTANTPLUS:
                    while ((idx = matchNodes(node + nodeSize, next, idx)) != -1)
                    {
                        // Try to match the rest without using the reluctant subexpr
                        if ((idxNew = matchNodes(next, maxNode, idx)) != -1)
                        {
                            return idxNew;
                        }
                    }
                    return -1;

                case OP_RELUCTANTSTAR:
                    do
                    {
                        // Try to match the rest without using the reluctant subexpr
                        if ((idxNew = matchNodes(next, maxNode, idx)) != -1)
                        {
                            return idxNew;
                        }
                    }
                    while ((idx = matchNodes(node + nodeSize, next, idx)) != -1);
                    return -1;

                case OP_OPEN:

                    // Match subexpression
                    if ((program.flags & REProgram.OPT_HASBACKREFS) != 0)
                    {
                        startBackref[opdata] = idx;
                    }
                    if ((idxNew = matchNodes(next, maxNode, idx)) != -1)
                    {
                        // Increase valid paren count
                        if ((opdata + 1) > parenCount)
                        {
                            parenCount = opdata + 1;
                        }

                        // Don't set paren if already set later on
                        if (getParenStart(opdata) == -1)
                        {
                            setParenStart(opdata, idx);
                        }
                    }
                    return idxNew;

                case OP_CLOSE:

                    // Done matching subexpression
                    if ((program.flags & REProgram.OPT_HASBACKREFS) != 0)
                    {
                        endBackref[opdata] = idx;
                    }
                    if ((idxNew = matchNodes(next, maxNode, idx)) != -1)
                    {
                        // Increase valid paren count
                        if ((opdata + 1) > parenCount)
                        {
                            parenCount = opdata + 1;
                        }

                        // Don't set paren if already set later on
                        if (getParenEnd(opdata) == -1)
                        {
                            setParenEnd(opdata, idx);
                        }
                    }
                    return idxNew;

                case OP_OPEN_CLUSTER:
                case OP_CLOSE_CLUSTER:
                    // starting or ending the matching of a subexpression which has no backref.
                    return matchNodes( next, maxNode, idx );

                case OP_BACKREF:
                    {
                        // Get the start and end of the backref
                        int s = startBackref[opdata];
                        int e = endBackref[opdata];

                        // We don't know the backref yet
                        if (s == -1 || e == -1)
                        {
                            return -1;
                        }

                        // The backref is empty size
                        if (s == e)
                        {
                            break;
                        }

                        // Get the length of the backref
                        int l = e - s;

                        // If there's not enough input left, give up.
                        if (search.isEnd(idx + l - 1))
                        {
                            return -1;
                        }

                        // Case fold the backref?
                        final boolean caseFold =
                            ((matchFlags & MATCH_CASEINDEPENDENT) != 0);
                        // Compare backref to input
                        for (int i = 0; i < l; i++)
                        {
                            if (compareChars(search.charAt(idx++), search.charAt(s + i), caseFold) != 0)
                            {
                                return -1;
                            }
                        }
                    }
                    break;

                case OP_BOL:

                    // Fail if we're not at the start of the string
                    if (idx != 0)
                    {
                        // If we're multiline matching, we could still be at the start of a line
                        if ((matchFlags & MATCH_MULTILINE) == MATCH_MULTILINE)
                        {
                            // If not at start of line, give up
                            if (idx <= 0 || !isNewline(idx - 1)) {
                                return -1;
                            } else {
                                break;
                            }
                        }
                        return -1;
                    }
                    break;

                case OP_EOL:

                    // If we're not at the end of string
                    if (!search.isEnd(0) && !search.isEnd(idx))
                    {
                        // If we're multi-line matching
                        if ((matchFlags & MATCH_MULTILINE) == MATCH_MULTILINE)
                        {
                            // Give up if we're not at the end of a line
                            if (!isNewline(idx)) {
                                return -1;
                            } else {
                                break;
                            }
                        }
                        return -1;
                    }
                    break;

                case OP_ESCAPE:

                    // Which escape?
                    switch (opdata)
                    {
                        // Word boundary match
                        case E_NBOUND:
                        case E_BOUND:
                            {
                                char cLast = ((idx == 0) ? '\n' : search.charAt(idx - 1));
                                char cNext = ((search.isEnd(idx)) ? '\n' : search.charAt(idx));
                                if ((Character.isLetterOrDigit(cLast) == Character.isLetterOrDigit(cNext)) == (opdata == E_BOUND))
                                {
                                    return -1;
                                }
                            }
                            break;

                        // Alpha-numeric, digit, space, javaLetter, javaLetterOrDigit
                        case E_ALNUM:
                        case E_NALNUM:
                        case E_DIGIT:
                        case E_NDIGIT:
                        case E_SPACE:
                        case E_NSPACE:

                            // Give up if out of input
                            if (search.isEnd(idx))
                            {
                                return -1;
                            }

                            char c = search.charAt(idx);

                            // Switch on escape
                            switch (opdata)
                            {
                                case E_ALNUM:
                                case E_NALNUM:
                                    if (!((Character.isLetterOrDigit(c) || c == '_') == (opdata == E_ALNUM)))
                                    {
                                        return -1;
                                    }
                                    break;

                                case E_DIGIT:
                                case E_NDIGIT:
                                    if (!(Character.isDigit(c) == (opdata == E_DIGIT)))
                                    {
                                        return -1;
                                    }
                                    break;

                                case E_SPACE:
                                case E_NSPACE:
                                    if (!(Character.isWhitespace(c) == (opdata == E_SPACE)))
                                    {
                                        return -1;
                                    }
                                    break;
                            }
                            idx++;
                            break;

                        default:
                            internalError("Unrecognized escape '" + opdata + "'");
                    }
                    break;

                case OP_ANY:

                    if ((matchFlags & MATCH_SINGLELINE) == MATCH_SINGLELINE) {
                        // Match anything
                        if (search.isEnd(idx))
                        {
                            return -1;
                        }
                    }
                    else
                    {
                        // Match anything but a newline
                        if (search.isEnd(idx) || isNewline(idx))
                        {
                            return -1;
                        }
                    }
                    idx++;
                    break;

                case OP_ATOM:
                    {
                        // Match an atom value
                        if (search.isEnd(idx))
                        {
                            return -1;
                        }

                        // Get length of atom and starting index
                        int lenAtom = opdata;
                        int startAtom = node + nodeSize;

                        // Give up if not enough input remains to have a match
                        if (search.isEnd(lenAtom + idx - 1))
                        {
                            return -1;
                        }

                        // Match atom differently depending on casefolding flag
                        final boolean caseFold =
                            ((matchFlags & MATCH_CASEINDEPENDENT) != 0);

                        for (int i = 0; i < lenAtom; i++)
                        {
                            if (compareChars(search.charAt(idx++), instruction[startAtom + i], caseFold) != 0)
                            {
                                return -1;
                            }
                        }
                    }
                    break;

                case OP_POSIXCLASS:
                    {
                        // Out of input?
                        if (search.isEnd(idx))
                        {
                            return -1;
                        }

                        switch (opdata)
                        {
                            case POSIX_CLASS_ALNUM:
                                if (!Character.isLetterOrDigit(search.charAt(idx)))
                                {
                                    return -1;
                                }
                                break;

                            case POSIX_CLASS_ALPHA:
                                if (!Character.isLetter(search.charAt(idx)))
                                {
                                    return -1;
                                }
                                break;

                            case POSIX_CLASS_DIGIT:
                                if (!Character.isDigit(search.charAt(idx)))
                                {
                                    return -1;
                                }
                                break;

                            case POSIX_CLASS_BLANK: // JWL - bugbug: is this right??
                                if (!Character.isSpaceChar(search.charAt(idx)))
                                {
                                    return -1;
                                }
                                break;

                            case POSIX_CLASS_SPACE:
                                if (!Character.isWhitespace(search.charAt(idx)))
                                {
                                    return -1;
                                }
                                break;

                            case POSIX_CLASS_CNTRL:
                                if (Character.getType(search.charAt(idx)) != Character.CONTROL)
                                {
                                    return -1;
                                }
                                break;

                            case POSIX_CLASS_GRAPH: // JWL - bugbug???
                                switch (Character.getType(search.charAt(idx)))
                                {
                                    case Character.MATH_SYMBOL:
                                    case Character.CURRENCY_SYMBOL:
                                    case Character.MODIFIER_SYMBOL:
                                    case Character.OTHER_SYMBOL:
                                        break;

                                    default:
                                        return -1;
                                }
                                break;

                            case POSIX_CLASS_LOWER:
                                if (Character.getType(search.charAt(idx)) != Character.LOWERCASE_LETTER)
                                {
                                    return -1;
                                }
                                break;

                            case POSIX_CLASS_UPPER:
                                if (Character.getType(search.charAt(idx)) != Character.UPPERCASE_LETTER)
                                {
                                    return -1;
                                }
                                break;

                            case POSIX_CLASS_PRINT:
                                if (Character.getType(search.charAt(idx)) == Character.CONTROL)
                                {
                                    return -1;
                                }
                                break;

                            case POSIX_CLASS_PUNCT:
                            {
                                int type = Character.getType(search.charAt(idx));
                                switch(type)
                                {
                                    case Character.DASH_PUNCTUATION:
                                    case Character.START_PUNCTUATION:
                                    case Character.END_PUNCTUATION:
                                    case Character.CONNECTOR_PUNCTUATION:
                                    case Character.OTHER_PUNCTUATION:
                                        break;

                                    default:
                                        return -1;
                                }
                            }
                            break;

                            case POSIX_CLASS_XDIGIT: // JWL - bugbug??
                            {
                                boolean isXDigit = ((search.charAt(idx) >= '0' && search.charAt(idx) <= '9') ||
                                                    (search.charAt(idx) >= 'a' && search.charAt(idx) <= 'f') ||
                                                    (search.charAt(idx) >= 'A' && search.charAt(idx) <= 'F'));
                                if (!isXDigit)
                                {
                                    return -1;
                                }
                            }
                            break;

                            case POSIX_CLASS_JSTART:
                                if (!Character.isJavaIdentifierStart(search.charAt(idx)))
                                {
                                    return -1;
                                }
                                break;

                            case POSIX_CLASS_JPART:
                                if (!Character.isJavaIdentifierPart(search.charAt(idx)))
                                {
                                    return -1;
                                }
                                break;

                            default:
                                internalError("Bad posix class");
                                break;
                        }

                        // Matched.
                        idx++;
                    }
                    break;

                case OP_ANYOF:
                    {
                        // Out of input?
                        if (search.isEnd(idx))
                        {
                            return -1;
                        }

                        // Get character to match against character class and maybe casefold
                        char c = search.charAt(idx);
                        boolean caseFold = (matchFlags & MATCH_CASEINDEPENDENT) != 0;
                        // Loop through character class checking our match character
                        int idxRange = node + nodeSize;
                        int idxEnd = idxRange + (opdata * 2);
                        boolean match = false;
                        for (int i = idxRange; !match && i < idxEnd; )
                        {
                            // Get start, end and match characters
                            char s = instruction[i++];
                            char e = instruction[i++];

                            match = ((compareChars(c, s, caseFold) >= 0)
                                     && (compareChars(c, e, caseFold) <= 0));
                        }

                        // Fail if we didn't match the character class
                        if (!match)
                        {
                            return -1;
                        }
                        idx++;
                    }
                    break;

                case OP_BRANCH:
                {
                    // Check for choices
                    if (instruction[next + offsetOpcode] != OP_BRANCH)
                    {
                        // If there aren't any other choices, just evaluate this branch.
                        node += nodeSize;
                        continue;
                    }

                    // Try all available branches
                    short nextBranch;
                    do
                    {
                        // Try matching the branch against the string
                        if ((idxNew = matchNodes(node + nodeSize, maxNode, idx)) != -1)
                        {
                            return idxNew;
                        }

                        // Go to next branch (if any)
                        nextBranch = (short)instruction[node + offsetNext];
                        node += nextBranch;
                    }
                    while (nextBranch != 0 && (instruction[node + offsetOpcode] == OP_BRANCH));

                    // Failed to match any branch!
                    return -1;
                }

                case OP_NOTHING:
                case OP_GOTO:

                    // Just advance to the next node without doing anything
                    break;

                case OP_END:

                    // Match has succeeded!
                    setParenEnd(0, idx);
                    return idx;

                default:

                    // Corrupt program
                    internalError("Invalid opcode '" + opcode + "'");
            }

            // Advance to the next node in the program
            node = next;
        }

        // We "should" never end up here
        internalError("Corrupt program");
        return -1;
    }

    /**
     * Match the current regular expression program against the current
     * input string, starting at index i of the input string.  This method
     * is only meant for internal use.
     *
     * @param i The input string index to start matching at
     * @return True if the input matched the expression
     */
    protected boolean matchAt(int i)
    {
        // Initialize start pointer, paren cache and paren count
        start0 = -1;
        end0   = -1;
        start1 = -1;
        end1   = -1;
        start2 = -1;
        end2   = -1;
        startn = null;
        endn   = null;
        parenCount = 1;
        setParenStart(0, i);

        // Allocate backref arrays (unless optimizations indicate otherwise)
        if ((program.flags & REProgram.OPT_HASBACKREFS) != 0)
        {
            startBackref = new int[maxParen];
            endBackref = new int[maxParen];
        }

        // Match against string
        int idx;
        if ((idx = matchNodes(0, maxNode, i)) != -1)
        {
            setParenEnd(0, idx);
            return true;
        }

        // Didn't match
        parenCount = 0;
        return false;
    }

    /**
     * Matches the current regular expression program against a character array,
     * starting at a given index.
     *
     * @param search String to match against
     * @param i Index to start searching at
     * @return True if string matched
     */
    public boolean match(String search, int i)
    {
        return match(new StringCharacterIterator(search), i);
    }

    /**
     * Matches the current regular expression program against a character array,
     * starting at a given index.
     *
     * @param search String to match against
     * @param i Index to start searching at
     * @return True if string matched
     */
    public boolean match(CharacterIterator search, int i)
    {
        // There is no compiled program to search with!
        if (program == null)
        {
            // This should be uncommon enough to be an error case rather
            // than an exception (which would have to be handled everywhere)
            internalError("No RE program to run!");
        }

        // Save string to search
        this.search = search;

        // Can we optimize the search by looking for a prefix string?
        if (program.prefix == null)
        {
            // Unprefixed matching must try for a match at each character
            for ( ;! search.isEnd(i - 1); i++)
            {
                // Try a match at index i
                if (matchAt(i))
                {
                    return true;
                }
            }
            return false;
        }
        else
        {
            // Prefix-anchored matching is possible
            boolean caseIndependent = (matchFlags & MATCH_CASEINDEPENDENT) != 0;
            char[] prefix = program.prefix;
            for ( ; !search.isEnd(i + prefix.length - 1); i++)
            {
                int j = i;
                int k = 0;

                boolean match;
                do {
                    // If there's a mismatch of any character in the prefix, give up
                    match = (compareChars(search.charAt(j++), prefix[k++], caseIndependent) == 0);
                } while (match && k < prefix.length);

                // See if the whole prefix string matched
                if (k == prefix.length)
                {
                    // We matched the full prefix at firstChar, so try it
                    if (matchAt(i))
                    {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Matches the current regular expression program against a String.
     *
     * @param search String to match against
     * @return True if string matched
     */
    public boolean match(String search)
    {
        return match(search, 0);
    }

    /**
     * Splits a string into an array of strings on regular expression boundaries.
     * This function works the same way as the Perl function of the same name.
     * Given a regular expression of "[ab]+" and a string to split of
     * "xyzzyababbayyzabbbab123", the result would be the array of Strings
     * "[xyzzy, yyz, 123]".
     *
     * <p>Please note that the first string in the resulting array may be an empty
     * string. This happens when the very first character of input string is
     * matched by the pattern.
     *
     * @param s String to split on this regular exression
     * @return Array of strings
     */
    public String[] split(String s)
    {
        // Create new vector
        Vector v = new Vector();

        // Start at position 0 and search the whole string
        int pos = 0;
        int len = s.length();

        // Try a match at each position
        while (pos < len && match(s, pos))
        {
            // Get start of match
            int start = getParenStart(0);

            // Get end of match
            int newpos = getParenEnd(0);

            // Check if no progress was made
            if (newpos == pos)
            {
                v.addElement(s.substring(pos, start + 1));
                newpos++;
            }
            else
            {
                v.addElement(s.substring(pos, start));
            }

            // Move to new position
            pos = newpos;
        }

        // Push remainder if it's not empty
        String remainder = s.substring(pos);
        if (remainder.length() != 0)
        {
            v.addElement(remainder);
        }

        // Return vector as an array of strings
        String[] ret = new String[v.size()];
        v.copyInto(ret);
        return ret;
    }

    /**
     * Flag bit that indicates that subst should replace all occurrences of this
     * regular expression.
     */
    public static final int REPLACE_ALL            = 0x0000;

    /**
     * Flag bit that indicates that subst should only replace the first occurrence
     * of this regular expression.
     */
    public static final int REPLACE_FIRSTONLY      = 0x0001;

    /**
     * Flag bit that indicates that subst should replace backreferences
     */
    public static final int REPLACE_BACKREFERENCES = 0x0002;

    /**
     * Substitutes a string for this regular expression in another string.
     * This method works like the Perl function of the same name.
     * Given a regular expression of "a*b", a String to substituteIn of
     * "aaaabfooaaabgarplyaaabwackyb" and the substitution String "-", the
     * resulting String returned by subst would be "-foo-garply-wacky-".
     *
     * @param substituteIn String to substitute within
     * @param substitution String to substitute for all matches of this regular expression.
     * @return The string substituteIn with zero or more occurrences of the current
     * regular expression replaced with the substitution String (if this regular
     * expression object doesn't match at any position, the original String is returned
     * unchanged).
     */
    public String subst(String substituteIn, String substitution)
    {
        return subst(substituteIn, substitution, REPLACE_ALL);
    }

    /**
     * Substitutes a string for this regular expression in another string.
     * This method works like the Perl function of the same name.
     * Given a regular expression of "a*b", a String to substituteIn of
     * "aaaabfooaaabgarplyaaabwackyb" and the substitution String "-", the
     * resulting String returned by subst would be "-foo-garply-wacky-".
     * <p>
     * It is also possible to reference the contents of a parenthesized expression
     * with $0, $1, ... $9. A regular expression of "http://[\\.\\w\\-\\?/~_@&=%]+",
     * a String to substituteIn of "visit us: http://www.apache.org!" and the
     * substitution String "&lt;a href=\"$0\"&gt;$0&lt;/a&gt;", the resulting String
     * returned by subst would be
     * "visit us: &lt;a href=\"http://www.apache.org\"&gt;http://www.apache.org&lt;/a&gt;!".
     * <p>
     * <i>Note:</i> $0 represents the whole match.
     *
     * @param substituteIn String to substitute within
     * @param substitution String to substitute for matches of this regular expression
     * @param flags One or more bitwise flags from REPLACE_*.  If the REPLACE_FIRSTONLY
     * flag bit is set, only the first occurrence of this regular expression is replaced.
     * If the bit is not set (REPLACE_ALL), all occurrences of this pattern will be
     * replaced. If the flag REPLACE_BACKREFERENCES is set, all backreferences will
     * be processed.
     * @return The string substituteIn with zero or more occurrences of the current
     * regular expression replaced with the substitution String (if this regular
     * expression object doesn't match at any position, the original String is returned
     * unchanged).
     */
    public String subst(String substituteIn, String substitution, int flags)
    {
        // String to return
        StringBuffer ret = new StringBuffer();

        // Start at position 0 and search the whole string
        int pos = 0;
        int len = substituteIn.length();

        // Try a match at each position
        while (pos < len && match(substituteIn, pos))
        {
            // Append string before match
            ret.append(substituteIn.substring(pos, getParenStart(0)));

            if ((flags & REPLACE_BACKREFERENCES) != 0)
            {
                // Process backreferences
                int lCurrentPosition = 0;
                int lLastPosition = -2;
                int lLength = substitution.length();
                boolean bAddedPrefix = false;

                while ((lCurrentPosition = substitution.indexOf("$", lCurrentPosition)) >= 0)
                {
                    if ((lCurrentPosition == 0 || substitution.charAt(lCurrentPosition - 1) != '\\')
                        && lCurrentPosition+1 < lLength)
                    {
                        char c = substitution.charAt(lCurrentPosition + 1);
                        if (c >= '0' && c <= '9')
                        {
                            if (bAddedPrefix == false)
                            {
                                // Append everything between the beginning of the
                                // substitution string and the current $ sign
                                ret.append(substitution.substring(0, lCurrentPosition));
                                bAddedPrefix = true;
                            }
                            else
                            {
                                // Append everything between the last and the current $ sign
                                ret.append(substitution.substring(lLastPosition + 2, lCurrentPosition));
                            }

                            // Append the parenthesized expression
                            // Note: if a parenthesized expression of the requested
                            // index is not available "null" is added to the string
                            ret.append(getParen(c - '0'));
                            lLastPosition = lCurrentPosition;
                        }
                    }

                    // Move forward, skipping past match
                    lCurrentPosition++;
                }

                // Append everything after the last $ sign
                ret.append(substitution.substring(lLastPosition + 2, lLength));
            }
            else
            {
                // Append substitution without processing backreferences
                ret.append(substitution);
            }

            // Move forward, skipping past match
            int newpos = getParenEnd(0);

            // We always want to make progress!
            if (newpos == pos)
            {
                newpos++;
            }

            // Try new position
            pos = newpos;

            // Break out if we're only supposed to replace one occurrence
            if ((flags & REPLACE_FIRSTONLY) != 0)
            {
                break;
            }
        }

        // If there's remaining input, append it
        if (pos < len)
        {
            ret.append(substituteIn.substring(pos));
        }

        // Return string buffer as string
        return ret.toString();
    }

    /**
     * Returns an array of Strings, whose toString representation matches a regular
     * expression. This method works like the Perl function of the same name.  Given
     * a regular expression of "a*b" and an array of String objects of [foo, aab, zzz,
     * aaaab], the array of Strings returned by grep would be [aab, aaaab].
     *
     * @param search Array of Objects to search
     * @return Array of Strings whose toString() value matches this regular expression.
     */
    public String[] grep(Object[] search)
    {
        // Create new vector to hold return items
        Vector v = new Vector();

        // Traverse array of objects
        for (int i = 0; i < search.length; i++)
        {
            // Get next object as a string
            String s = search[i].toString();

            // If it matches this regexp, add it to the list
            if (match(s))
            {
                v.addElement(s);
            }
        }

        // Return vector as an array of strings
        String[] ret = new String[v.size()];
        v.copyInto(ret);
        return ret;
    }

    /**
     * @return true if character at i-th position in the <code>search</code> string is a newline
     */
    private boolean isNewline(int i)
    {
        char nextChar = search.charAt(i);

        if (nextChar == '\n' || nextChar == '\r' || nextChar == '\u0085'
            || nextChar == '\u2028' || nextChar == '\u2029')
        {
            return true;
        }

        return false;
    }

    /**
     * Compares two characters.
     *
     * @param c1 first character to compare.
     * @param c2 second character to compare.
     * @param caseIndependent whether comparision is case insensitive or not.
     * @return negative, 0, or positive integer as the first character
     *         less than, equal to, or greater then the second.
     */
    private int compareChars(char c1, char c2, boolean caseIndependent)
    {
        if (caseIndependent)
        {
            c1 = Character.toLowerCase(c1);
            c2 = Character.toLowerCase(c2);
        }
        return ((int)c1 - (int)c2);
    }
}
