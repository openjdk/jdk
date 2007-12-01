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

import com.sun.org.apache.regexp.internal.RE;
import java.util.Hashtable;

/**
 * A regular expression compiler class.  This class compiles a pattern string into a
 * regular expression program interpretable by the RE evaluator class.  The 'recompile'
 * command line tool uses this compiler to pre-compile regular expressions for use
 * with RE.  For a description of the syntax accepted by RECompiler and what you can
 * do with regular expressions, see the documentation for the RE matcher class.
 *
 * @see RE
 * @see recompile
 *
 * @author <a href="mailto:jonl@muppetlabs.com">Jonathan Locke</a>
 * @author <a href="mailto:gholam@xtra.co.nz">Michael McCallum</a>
 */
public class RECompiler
{
    // The compiled program
    char[] instruction;                                 // The compiled RE 'program' instruction buffer
    int lenInstruction;                                 // The amount of the program buffer currently in use

    // Input state for compiling regular expression
    String pattern;                                     // Input string
    int len;                                            // Length of the pattern string
    int idx;                                            // Current input index into ac
    int parens;                                         // Total number of paren pairs

    // Node flags
    static final int NODE_NORMAL   = 0;                 // No flags (nothing special)
    static final int NODE_NULLABLE = 1;                 // True if node is potentially null
    static final int NODE_TOPLEVEL = 2;                 // True if top level expr

    // Special types of 'escapes'
    static final int ESC_MASK      = 0xffff0;           // Escape complexity mask
    static final int ESC_BACKREF   = 0xfffff;           // Escape is really a backreference
    static final int ESC_COMPLEX   = 0xffffe;           // Escape isn't really a true character
    static final int ESC_CLASS     = 0xffffd;           // Escape represents a whole class of characters

    // {m,n} stacks
    int maxBrackets = 10;                               // Maximum number of bracket pairs
    static final int bracketUnbounded = -1;             // Unbounded value
    int brackets = 0;                                   // Number of bracket sets
    int[] bracketStart = null;                          // Starting point
    int[] bracketEnd = null;                            // Ending point
    int[] bracketMin = null;                            // Minimum number of matches
    int[] bracketOpt = null;                            // Additional optional matches

    // Lookup table for POSIX character class names
    static Hashtable hashPOSIX = new Hashtable();
    static
    {
        hashPOSIX.put("alnum",     new Character(RE.POSIX_CLASS_ALNUM));
        hashPOSIX.put("alpha",     new Character(RE.POSIX_CLASS_ALPHA));
        hashPOSIX.put("blank",     new Character(RE.POSIX_CLASS_BLANK));
        hashPOSIX.put("cntrl",     new Character(RE.POSIX_CLASS_CNTRL));
        hashPOSIX.put("digit",     new Character(RE.POSIX_CLASS_DIGIT));
        hashPOSIX.put("graph",     new Character(RE.POSIX_CLASS_GRAPH));
        hashPOSIX.put("lower",     new Character(RE.POSIX_CLASS_LOWER));
        hashPOSIX.put("print",     new Character(RE.POSIX_CLASS_PRINT));
        hashPOSIX.put("punct",     new Character(RE.POSIX_CLASS_PUNCT));
        hashPOSIX.put("space",     new Character(RE.POSIX_CLASS_SPACE));
        hashPOSIX.put("upper",     new Character(RE.POSIX_CLASS_UPPER));
        hashPOSIX.put("xdigit",    new Character(RE.POSIX_CLASS_XDIGIT));
        hashPOSIX.put("javastart", new Character(RE.POSIX_CLASS_JSTART));
        hashPOSIX.put("javapart",  new Character(RE.POSIX_CLASS_JPART));
    }

    /**
     * Constructor.  Creates (initially empty) storage for a regular expression program.
     */
    public RECompiler()
    {
        // Start off with a generous, yet reasonable, initial size
        instruction = new char[128];
        lenInstruction = 0;
    }

    /**
     * Ensures that n more characters can fit in the program buffer.
     * If n more can't fit, then the size is doubled until it can.
     * @param n Number of additional characters to ensure will fit.
     */
    void ensure(int n)
    {
        // Get current program length
        int curlen = instruction.length;

        // If the current length + n more is too much
        if (lenInstruction + n >= curlen)
        {
            // Double the size of the program array until n more will fit
            while (lenInstruction + n >= curlen)
            {
                curlen *= 2;
            }

            // Allocate new program array and move data into it
            char[] newInstruction = new char[curlen];
            System.arraycopy(instruction, 0, newInstruction, 0, lenInstruction);
            instruction = newInstruction;
        }
    }

    /**
     * Emit a single character into the program stream.
     * @param c Character to add
     */
    void emit(char c)
    {
        // Make room for character
        ensure(1);

        // Add character
        instruction[lenInstruction++] = c;
    }

    /**
     * Inserts a node with a given opcode and opdata at insertAt.  The node relative next
     * pointer is initialized to 0.
     * @param opcode Opcode for new node
     * @param opdata Opdata for new node (only the low 16 bits are currently used)
     * @param insertAt Index at which to insert the new node in the program
     */
    void nodeInsert(char opcode, int opdata, int insertAt)
    {
        // Make room for a new node
        ensure(RE.nodeSize);

        // Move everything from insertAt to the end down nodeSize elements
        System.arraycopy(instruction, insertAt, instruction, insertAt + RE.nodeSize, lenInstruction - insertAt);
        instruction[insertAt + RE.offsetOpcode] = opcode;
        instruction[insertAt + RE.offsetOpdata] = (char)opdata;
        instruction[insertAt + RE.offsetNext] = 0;
        lenInstruction += RE.nodeSize;
    }

    /**
     * Appends a node to the end of a node chain
     * @param node Start of node chain to traverse
     * @param pointTo Node to have the tail of the chain point to
     */
    void setNextOfEnd(int node, int pointTo)
    {
        // Traverse the chain until the next offset is 0
        int next = instruction[node + RE.offsetNext];
        // while the 'node' is not the last in the chain
        // and the 'node' is not the last in the program.
        while ( next != 0 && node < lenInstruction )
        {
            // if the node we are supposed to point to is in the chain then
            // point to the end of the program instead.
            // Michael McCallum <gholam@xtra.co.nz>
            // FIXME: // This is a _hack_ to stop infinite programs.
            // I believe that the implementation of the reluctant matches is wrong but
            // have not worked out a better way yet.
            if ( node == pointTo ) {
              pointTo = lenInstruction;
            }
            node += next;
            next = instruction[node + RE.offsetNext];
        }
        // if we have reached the end of the program then dont set the pointTo.
        // im not sure if this will break any thing but passes all the tests.
        if ( node < lenInstruction ) {
            // Point the last node in the chain to pointTo.
            instruction[node + RE.offsetNext] = (char)(short)(pointTo - node);
        }
    }

    /**
     * Adds a new node
     * @param opcode Opcode for node
     * @param opdata Opdata for node (only the low 16 bits are currently used)
     * @return Index of new node in program
     */
    int node(char opcode, int opdata)
    {
        // Make room for a new node
        ensure(RE.nodeSize);

        // Add new node at end
        instruction[lenInstruction + RE.offsetOpcode] = opcode;
        instruction[lenInstruction + RE.offsetOpdata] = (char)opdata;
        instruction[lenInstruction + RE.offsetNext] = 0;
        lenInstruction += RE.nodeSize;

        // Return index of new node
        return lenInstruction - RE.nodeSize;
    }


    /**
     * Throws a new internal error exception
     * @exception Error Thrown in the event of an internal error.
     */
    void internalError() throws Error
    {
        throw new Error("Internal error!");
    }

    /**
     * Throws a new syntax error exception
     * @exception RESyntaxException Thrown if the regular expression has invalid syntax.
     */
    void syntaxError(String s) throws RESyntaxException
    {
        throw new RESyntaxException(s);
    }

    /**
     * Allocate storage for brackets only as needed
     */
    void allocBrackets()
    {
        // Allocate bracket stacks if not already done
        if (bracketStart == null)
        {
            // Allocate storage
            bracketStart = new int[maxBrackets];
            bracketEnd   = new int[maxBrackets];
            bracketMin   = new int[maxBrackets];
            bracketOpt   = new int[maxBrackets];

            // Initialize to invalid values
            for (int i = 0; i < maxBrackets; i++)
            {
                bracketStart[i] = bracketEnd[i] = bracketMin[i] = bracketOpt[i] = -1;
            }
        }
    }

    /** Enlarge storage for brackets only as needed. */
    synchronized void reallocBrackets() {
        // trick the tricky
        if (bracketStart == null) {
            allocBrackets();
        }

        int new_size = maxBrackets * 2;
        int[] new_bS = new int[new_size];
        int[] new_bE = new int[new_size];
        int[] new_bM = new int[new_size];
        int[] new_bO = new int[new_size];
        // Initialize to invalid values
        for (int i=brackets; i<new_size; i++) {
            new_bS[i] = new_bE[i] = new_bM[i] = new_bO[i] = -1;
        }
        System.arraycopy(bracketStart,0, new_bS,0, brackets);
        System.arraycopy(bracketEnd,0,   new_bE,0, brackets);
        System.arraycopy(bracketMin,0,   new_bM,0, brackets);
        System.arraycopy(bracketOpt,0,   new_bO,0, brackets);
        bracketStart = new_bS;
        bracketEnd   = new_bE;
        bracketMin   = new_bM;
        bracketOpt   = new_bO;
        maxBrackets  = new_size;
    }

    /**
     * Match bracket {m,n} expression put results in bracket member variables
     * @exception RESyntaxException Thrown if the regular expression has invalid syntax.
     */
    void bracket() throws RESyntaxException
    {
        // Current character must be a '{'
        if (idx >= len || pattern.charAt(idx++) != '{')
        {
            internalError();
        }

        // Next char must be a digit
        if (idx >= len || !Character.isDigit(pattern.charAt(idx)))
        {
            syntaxError("Expected digit");
        }

        // Get min ('m' of {m,n}) number
        StringBuffer number = new StringBuffer();
        while (idx < len && Character.isDigit(pattern.charAt(idx)))
        {
            number.append(pattern.charAt(idx++));
        }
        try
        {
            bracketMin[brackets] = Integer.parseInt(number.toString());
        }
        catch (NumberFormatException e)
        {
            syntaxError("Expected valid number");
        }

        // If out of input, fail
        if (idx >= len)
        {
            syntaxError("Expected comma or right bracket");
        }

        // If end of expr, optional limit is 0
        if (pattern.charAt(idx) == '}')
        {
            idx++;
            bracketOpt[brackets] = 0;
            return;
        }

        // Must have at least {m,} and maybe {m,n}.
        if (idx >= len || pattern.charAt(idx++) != ',')
        {
            syntaxError("Expected comma");
        }

        // If out of input, fail
        if (idx >= len)
        {
            syntaxError("Expected comma or right bracket");
        }

        // If {m,} max is unlimited
        if (pattern.charAt(idx) == '}')
        {
            idx++;
            bracketOpt[brackets] = bracketUnbounded;
            return;
        }

        // Next char must be a digit
        if (idx >= len || !Character.isDigit(pattern.charAt(idx)))
        {
            syntaxError("Expected digit");
        }

        // Get max number
        number.setLength(0);
        while (idx < len && Character.isDigit(pattern.charAt(idx)))
        {
            number.append(pattern.charAt(idx++));
        }
        try
        {
            bracketOpt[brackets] = Integer.parseInt(number.toString()) - bracketMin[brackets];
        }
        catch (NumberFormatException e)
        {
            syntaxError("Expected valid number");
        }

        // Optional repetitions must be >= 0
        if (bracketOpt[brackets] < 0)
        {
            syntaxError("Bad range");
        }

        // Must have close brace
        if (idx >= len || pattern.charAt(idx++) != '}')
        {
            syntaxError("Missing close brace");
        }
    }

    /**
     * Match an escape sequence.  Handles quoted chars and octal escapes as well
     * as normal escape characters.  Always advances the input stream by the
     * right amount. This code "understands" the subtle difference between an
     * octal escape and a backref.  You can access the type of ESC_CLASS or
     * ESC_COMPLEX or ESC_BACKREF by looking at pattern[idx - 1].
     * @return ESC_* code or character if simple escape
     * @exception RESyntaxException Thrown if the regular expression has invalid syntax.
     */
    int escape() throws RESyntaxException
    {
        // "Shouldn't" happen
        if (pattern.charAt(idx) != '\\')
        {
            internalError();
        }

        // Escape shouldn't occur as last character in string!
        if (idx + 1 == len)
        {
            syntaxError("Escape terminates string");
        }

        // Switch on character after backslash
        idx += 2;
        char escapeChar = pattern.charAt(idx - 1);
        switch (escapeChar)
        {
            case RE.E_BOUND:
            case RE.E_NBOUND:
                return ESC_COMPLEX;

            case RE.E_ALNUM:
            case RE.E_NALNUM:
            case RE.E_SPACE:
            case RE.E_NSPACE:
            case RE.E_DIGIT:
            case RE.E_NDIGIT:
                return ESC_CLASS;

            case 'u':
            case 'x':
                {
                    // Exact required hex digits for escape type
                    int hexDigits = (escapeChar == 'u' ? 4 : 2);

                    // Parse up to hexDigits characters from input
                    int val = 0;
                    for ( ; idx < len && hexDigits-- > 0; idx++)
                    {
                        // Get char
                        char c = pattern.charAt(idx);

                        // If it's a hexadecimal digit (0-9)
                        if (c >= '0' && c <= '9')
                        {
                            // Compute new value
                            val = (val << 4) + c - '0';
                        }
                        else
                        {
                            // If it's a hexadecimal letter (a-f)
                            c = Character.toLowerCase(c);
                            if (c >= 'a' && c <= 'f')
                            {
                                // Compute new value
                                val = (val << 4) + (c - 'a') + 10;
                            }
                            else
                            {
                                // If it's not a valid digit or hex letter, the escape must be invalid
                                // because hexDigits of input have not been absorbed yet.
                                syntaxError("Expected " + hexDigits + " hexadecimal digits after \\" + escapeChar);
                            }
                        }
                    }
                    return val;
                }

            case 't':
                return '\t';

            case 'n':
                return '\n';

            case 'r':
                return '\r';

            case 'f':
                return '\f';

            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':

                // An octal escape starts with a 0 or has two digits in a row
                if ((idx < len && Character.isDigit(pattern.charAt(idx))) || escapeChar == '0')
                {
                    // Handle \nnn octal escapes
                    int val = escapeChar - '0';
                    if (idx < len && Character.isDigit(pattern.charAt(idx)))
                    {
                        val = ((val << 3) + (pattern.charAt(idx++) - '0'));
                        if (idx < len && Character.isDigit(pattern.charAt(idx)))
                        {
                            val = ((val << 3) + (pattern.charAt(idx++) - '0'));
                        }
                    }
                    return val;
                }

                // It's actually a backreference (\[1-9]), not an escape
                return ESC_BACKREF;

            default:

                // Simple quoting of a character
                return escapeChar;
        }
    }

    /**
     * Compile a character class
     * @return Index of class node
     * @exception RESyntaxException Thrown if the regular expression has invalid syntax.
     */
    int characterClass() throws RESyntaxException
    {
        // Check for bad calling or empty class
        if (pattern.charAt(idx) != '[')
        {
            internalError();
        }

        // Check for unterminated or empty class
        if ((idx + 1) >= len || pattern.charAt(++idx) == ']')
        {
            syntaxError("Empty or unterminated class");
        }

        // Check for POSIX character class
        if (idx < len && pattern.charAt(idx) == ':')
        {
            // Skip colon
            idx++;

            // POSIX character classes are denoted with lowercase ASCII strings
            int idxStart = idx;
            while (idx < len && pattern.charAt(idx) >= 'a' && pattern.charAt(idx) <= 'z')
            {
                idx++;
            }

            // Should be a ":]" to terminate the POSIX character class
            if ((idx + 1) < len && pattern.charAt(idx) == ':' && pattern.charAt(idx + 1) == ']')
            {
                // Get character class
                String charClass = pattern.substring(idxStart, idx);

                // Select the POSIX class id
                Character i = (Character)hashPOSIX.get(charClass);
                if (i != null)
                {
                    // Move past colon and right bracket
                    idx += 2;

                    // Return new POSIX character class node
                    return node(RE.OP_POSIXCLASS, i.charValue());
                }
                syntaxError("Invalid POSIX character class '" + charClass + "'");
            }
            syntaxError("Invalid POSIX character class syntax");
        }

        // Try to build a class.  Create OP_ANYOF node
        int ret = node(RE.OP_ANYOF, 0);

        // Parse class declaration
        char CHAR_INVALID = Character.MAX_VALUE;
        char last = CHAR_INVALID;
        char simpleChar = 0;
        boolean include = true;
        boolean definingRange = false;
        int idxFirst = idx;
        char rangeStart = Character.MIN_VALUE;
        char rangeEnd;
        RERange range = new RERange();
        while (idx < len && pattern.charAt(idx) != ']')
        {

            switchOnCharacter:

            // Switch on character
            switch (pattern.charAt(idx))
            {
                case '^':
                    include = !include;
                    if (idx == idxFirst)
                    {
                        range.include(Character.MIN_VALUE, Character.MAX_VALUE, true);
                    }
                    idx++;
                    continue;

                case '\\':
                {
                    // Escape always advances the stream
                    int c;
                    switch (c = escape ())
                    {
                        case ESC_COMPLEX:
                        case ESC_BACKREF:

                            // Word boundaries and backrefs not allowed in a character class!
                            syntaxError("Bad character class");

                        case ESC_CLASS:

                            // Classes can't be an endpoint of a range
                            if (definingRange)
                            {
                                syntaxError("Bad character class");
                            }

                            // Handle specific type of class (some are ok)
                            switch (pattern.charAt(idx - 1))
                            {
                                case RE.E_NSPACE:
                                case RE.E_NDIGIT:
                                case RE.E_NALNUM:
                                    syntaxError("Bad character class");

                                case RE.E_SPACE:
                                    range.include('\t', include);
                                    range.include('\r', include);
                                    range.include('\f', include);
                                    range.include('\n', include);
                                    range.include('\b', include);
                                    range.include(' ', include);
                                    break;

                                case RE.E_ALNUM:
                                    range.include('a', 'z', include);
                                    range.include('A', 'Z', include);
                                    range.include('_', include);

                                    // Fall through!

                                case RE.E_DIGIT:
                                    range.include('0', '9', include);
                                    break;
                            }

                            // Make last char invalid (can't be a range start)
                            last = CHAR_INVALID;
                            break;

                        default:

                            // Escape is simple so treat as a simple char
                            simpleChar = (char) c;
                            break switchOnCharacter;
                    }
                }
                continue;

                case '-':

                    // Start a range if one isn't already started
                    if (definingRange)
                    {
                        syntaxError("Bad class range");
                    }
                    definingRange = true;

                    // If no last character, start of range is 0
                    rangeStart = (last == CHAR_INVALID ? 0 : last);

                    // Premature end of range. define up to Character.MAX_VALUE
                    if ((idx + 1) < len && pattern.charAt(++idx) == ']')
                    {
                        simpleChar = Character.MAX_VALUE;
                        break;
                    }
                    continue;

                default:
                    simpleChar = pattern.charAt(idx++);
                    break;
            }

            // Handle simple character simpleChar
            if (definingRange)
            {
                // if we are defining a range make it now
                rangeEnd = simpleChar;

                // Actually create a range if the range is ok
                if (rangeStart >= rangeEnd)
                {
                    syntaxError("Bad character class");
                }
                range.include(rangeStart, rangeEnd, include);

                // We are done defining the range
                last = CHAR_INVALID;
                definingRange = false;
            }
            else
            {
                // If simple character and not start of range, include it
                if (idx >= len || pattern.charAt(idx) != '-')
                {
                    range.include(simpleChar, include);
                }
                last = simpleChar;
            }
        }

        // Shouldn't be out of input
        if (idx == len)
        {
            syntaxError("Unterminated character class");
        }

        // Absorb the ']' end of class marker
        idx++;

        // Emit character class definition
        instruction[ret + RE.offsetOpdata] = (char)range.num;
        for (int i = 0; i < range.num; i++)
        {
            emit((char)range.minRange[i]);
            emit((char)range.maxRange[i]);
        }
        return ret;
    }

    /**
     * Absorb an atomic character string.  This method is a little tricky because
     * it can un-include the last character of string if a closure operator follows.
     * This is correct because *+? have higher precedence than concatentation (thus
     * ABC* means AB(C*) and NOT (ABC)*).
     * @return Index of new atom node
     * @exception RESyntaxException Thrown if the regular expression has invalid syntax.
     */
    int atom() throws RESyntaxException
    {
        // Create a string node
        int ret = node(RE.OP_ATOM, 0);

        // Length of atom
        int lenAtom = 0;

        // Loop while we've got input

        atomLoop:

        while (idx < len)
        {
            // Is there a next char?
            if ((idx + 1) < len)
            {
                char c = pattern.charAt(idx + 1);

                // If the next 'char' is an escape, look past the whole escape
                if (pattern.charAt(idx) == '\\')
                {
                    int idxEscape = idx;
                    escape();
                    if (idx < len)
                    {
                        c = pattern.charAt(idx);
                    }
                    idx = idxEscape;
                }

                // Switch on next char
                switch (c)
                {
                    case '{':
                    case '?':
                    case '*':
                    case '+':

                        // If the next character is a closure operator and our atom is non-empty, the
                        // current character should bind to the closure operator rather than the atom
                        if (lenAtom != 0)
                        {
                            break atomLoop;
                        }
                }
            }

            // Switch on current char
            switch (pattern.charAt(idx))
            {
                case ']':
                case '^':
                case '$':
                case '.':
                case '[':
                case '(':
                case ')':
                case '|':
                    break atomLoop;

                case '{':
                case '?':
                case '*':
                case '+':

                    // We should have an atom by now
                    if (lenAtom == 0)
                    {
                        // No atom before closure
                        syntaxError("Missing operand to closure");
                    }
                    break atomLoop;

                case '\\':

                    {
                        // Get the escaped character (advances input automatically)
                        int idxBeforeEscape = idx;
                        int c = escape();

                        // Check if it's a simple escape (as opposed to, say, a backreference)
                        if ((c & ESC_MASK) == ESC_MASK)
                        {
                            // Not a simple escape, so backup to where we were before the escape.
                            idx = idxBeforeEscape;
                            break atomLoop;
                        }

                        // Add escaped char to atom
                        emit((char) c);
                        lenAtom++;
                    }
                    break;

                default:

                    // Add normal character to atom
                    emit(pattern.charAt(idx++));
                    lenAtom++;
                    break;
            }
        }

        // This "shouldn't" happen
        if (lenAtom == 0)
        {
            internalError();
        }

        // Emit the atom length into the program
        instruction[ret + RE.offsetOpdata] = (char)lenAtom;
        return ret;
    }

    /**
     * Match a terminal node.
     * @param flags Flags
     * @return Index of terminal node (closeable)
     * @exception RESyntaxException Thrown if the regular expression has invalid syntax.
     */
    int terminal(int[] flags) throws RESyntaxException
    {
        switch (pattern.charAt(idx))
        {
        case RE.OP_EOL:
        case RE.OP_BOL:
        case RE.OP_ANY:
            return node(pattern.charAt(idx++), 0);

        case '[':
            return characterClass();

        case '(':
            return expr(flags);

        case ')':
            syntaxError("Unexpected close paren");

        case '|':
            internalError();

        case ']':
            syntaxError("Mismatched class");

        case 0:
            syntaxError("Unexpected end of input");

        case '?':
        case '+':
        case '{':
        case '*':
            syntaxError("Missing operand to closure");

        case '\\':
            {
                // Don't forget, escape() advances the input stream!
                int idxBeforeEscape = idx;

                // Switch on escaped character
                switch (escape())
                {
                    case ESC_CLASS:
                    case ESC_COMPLEX:
                        flags[0] &= ~NODE_NULLABLE;
                        return node(RE.OP_ESCAPE, pattern.charAt(idx - 1));

                    case ESC_BACKREF:
                        {
                            char backreference = (char)(pattern.charAt(idx - 1) - '0');
                            if (parens <= backreference)
                            {
                                syntaxError("Bad backreference");
                            }
                            flags[0] |= NODE_NULLABLE;
                            return node(RE.OP_BACKREF, backreference);
                        }

                    default:

                        // We had a simple escape and we want to have it end up in
                        // an atom, so we back up and fall though to the default handling
                        idx = idxBeforeEscape;
                        flags[0] &= ~NODE_NULLABLE;
                        break;
                }
            }
        }

        // Everything above either fails or returns.
        // If it wasn't one of the above, it must be the start of an atom.
        flags[0] &= ~NODE_NULLABLE;
        return atom();
    }

    /**
     * Compile a possibly closured terminal
     * @param flags Flags passed by reference
     * @return Index of closured node
     * @exception RESyntaxException Thrown if the regular expression has invalid syntax.
     */
    int closure(int[] flags) throws RESyntaxException
    {
        // Before terminal
        int idxBeforeTerminal = idx;

        // Values to pass by reference to terminal()
        int[] terminalFlags = { NODE_NORMAL };

        // Get terminal symbol
        int ret = terminal(terminalFlags);

        // Or in flags from terminal symbol
        flags[0] |= terminalFlags[0];

        // Advance input, set NODE_NULLABLE flag and do sanity checks
        if (idx >= len)
        {
            return ret;
        }
        boolean greedy = true;
        char closureType = pattern.charAt(idx);
        switch (closureType)
        {
            case '?':
            case '*':

                // The current node can be null
                flags[0] |= NODE_NULLABLE;

            case '+':

                // Eat closure character
                idx++;

            case '{':

                // Don't allow blantant stupidity
                int opcode = instruction[ret + RE.offsetOpcode];
                if (opcode == RE.OP_BOL || opcode == RE.OP_EOL)
                {
                    syntaxError("Bad closure operand");
                }
                if ((terminalFlags[0] & NODE_NULLABLE) != 0)
                {
                    syntaxError("Closure operand can't be nullable");
                }
                break;
        }

        // If the next character is a '?', make the closure non-greedy (reluctant)
        if (idx < len && pattern.charAt(idx) == '?')
        {
            idx++;
            greedy = false;
        }

        if (greedy)
        {
            // Actually do the closure now
            switch (closureType)
            {
                case '{':
                {
                    // We look for our bracket in the list
                    boolean found = false;
                    int i;
                    allocBrackets();
                    for (i = 0; i < brackets; i++)
                    {
                        if (bracketStart[i] == idx)
                        {
                            found = true;
                            break;
                        }
                    }

                    // If its not in the list we parse the {m,n}
                    if (!found)
                    {
                        if (brackets >= maxBrackets)
                        {
                            reallocBrackets();
                        }
                        bracketStart[brackets] = idx;
                        bracket();
                        bracketEnd[brackets] = idx;
                        i = brackets++;
                    }

                    // Process min first
                    if (bracketMin[i]-- > 0)
                    {
                        if (bracketMin[i] > 0 || bracketOpt[i] != 0) {
                            // Rewind stream and run it through again - more matchers coming
                            for (int j = 0; j < brackets; j++) {
                                if (j != i && bracketStart[j] < idx
                                    && bracketStart[j] >= idxBeforeTerminal)
                                {
                                    brackets--;
                                    bracketStart[j] = bracketStart[brackets];
                                    bracketEnd[j] = bracketEnd[brackets];
                                    bracketMin[j] = bracketMin[brackets];
                                    bracketOpt[j] = bracketOpt[brackets];
                                }
                            }

                            idx = idxBeforeTerminal;
                        } else {
                            // Bug #1030: No optinal matches - no need to rewind
                            idx = bracketEnd[i];
                        }
                        break;
                    }

                    // Do the right thing for maximum ({m,})
                    if (bracketOpt[i] == bracketUnbounded)
                    {
                        // Drop through now and closure expression.
                        // We are done with the {m,} expr, so skip rest
                        closureType = '*';
                        bracketOpt[i] = 0;
                        idx = bracketEnd[i];
                    }
                    else
                        if (bracketOpt[i]-- > 0)
                        {
                            if (bracketOpt[i] > 0)
                            {
                                // More optional matchers - 'play it again sam!'
                                idx = idxBeforeTerminal;
                            } else {
                                // Bug #1030: We are done - this one is last and optional
                                idx = bracketEnd[i];
                            }
                            // Drop through to optionally close
                            closureType = '?';
                        }
                        else
                        {
                            // Rollback terminal - neither min nor opt matchers present
                            lenInstruction = ret;
                            node(RE.OP_NOTHING, 0);

                            // We are done. skip the rest of {m,n} expr
                            idx = bracketEnd[i];
                            break;
                        }
                }

                // Fall through!

                case '?':
                case '*':

                    if (!greedy)
                    {
                        break;
                    }

                    if (closureType == '?')
                    {
                        // X? is compiled as (X|)
                        nodeInsert(RE.OP_BRANCH, 0, ret);                 // branch before X
                        setNextOfEnd(ret, node (RE.OP_BRANCH, 0));        // inserted branch to option
                        int nothing = node (RE.OP_NOTHING, 0);            // which is OP_NOTHING
                        setNextOfEnd(ret, nothing);                       // point (second) branch to OP_NOTHING
                        setNextOfEnd(ret + RE.nodeSize, nothing);         // point the end of X to OP_NOTHING node
                    }

                    if (closureType == '*')
                    {
                        // X* is compiled as (X{gotoX}|)
                        nodeInsert(RE.OP_BRANCH, 0, ret);                         // branch before X
                        setNextOfEnd(ret + RE.nodeSize, node(RE.OP_BRANCH, 0));   // end of X points to an option
                        setNextOfEnd(ret + RE.nodeSize, node(RE.OP_GOTO, 0));     // to goto
                        setNextOfEnd(ret + RE.nodeSize, ret);                     // the start again
                        setNextOfEnd(ret, node(RE.OP_BRANCH, 0));                 // the other option is
                        setNextOfEnd(ret, node(RE.OP_NOTHING, 0));                // OP_NOTHING
                    }
                    break;

                case '+':
                {
                    // X+ is compiled as X({gotoX}|)
                    int branch;
                    branch = node(RE.OP_BRANCH, 0);                   // a new branch
                    setNextOfEnd(ret, branch);                        // is added to the end of X
                    setNextOfEnd(node(RE.OP_GOTO, 0), ret);           // one option is to go back to the start
                    setNextOfEnd(branch, node(RE.OP_BRANCH, 0));      // the other option
                    setNextOfEnd(ret, node(RE.OP_NOTHING, 0));        // is OP_NOTHING
                }
                break;
            }
        }
        else
        {
            // Add end after closured subexpr
            setNextOfEnd(ret, node(RE.OP_END, 0));

            // Actually do the closure now
            switch (closureType)
            {
                case '?':
                    nodeInsert(RE.OP_RELUCTANTMAYBE, 0, ret);
                    break;

                case '*':
                    nodeInsert(RE.OP_RELUCTANTSTAR, 0, ret);
                    break;

                case '+':
                    nodeInsert(RE.OP_RELUCTANTPLUS, 0, ret);
                    break;
            }

            // Point to the expr after the closure
            setNextOfEnd(ret, lenInstruction);
        }
        return ret;
    }

    /**
     * Compile one branch of an or operator (implements concatenation)
     * @param flags Flags passed by reference
     * @return Pointer to branch node
     * @exception RESyntaxException Thrown if the regular expression has invalid syntax.
     */
    int branch(int[] flags) throws RESyntaxException
    {
        // Get each possibly closured piece and concat
        int node;
        int ret = node(RE.OP_BRANCH, 0);
        int chain = -1;
        int[] closureFlags = new int[1];
        boolean nullable = true;
        while (idx < len && pattern.charAt(idx) != '|' && pattern.charAt(idx) != ')')
        {
            // Get new node
            closureFlags[0] = NODE_NORMAL;
            node = closure(closureFlags);
            if (closureFlags[0] == NODE_NORMAL)
            {
                nullable = false;
            }

            // If there's a chain, append to the end
            if (chain != -1)
            {
                setNextOfEnd(chain, node);
            }

            // Chain starts at current
            chain = node;
        }

        // If we don't run loop, make a nothing node
        if (chain == -1)
        {
            node(RE.OP_NOTHING, 0);
        }

        // Set nullable flag for this branch
        if (nullable)
        {
            flags[0] |= NODE_NULLABLE;
        }
        return ret;
    }

    /**
     * Compile an expression with possible parens around it.  Paren matching
     * is done at this level so we can tie the branch tails together.
     * @param flags Flag value passed by reference
     * @return Node index of expression in instruction array
     * @exception RESyntaxException Thrown if the regular expression has invalid syntax.
     */
    int expr(int[] flags) throws RESyntaxException
    {
        // Create open paren node unless we were called from the top level (which has no parens)
        int paren = -1;
        int ret = -1;
        int closeParens = parens;
        if ((flags[0] & NODE_TOPLEVEL) == 0 && pattern.charAt(idx) == '(')
        {
            // if its a cluster ( rather than a proper subexpression ie with backrefs )
            if ( idx + 2 < len && pattern.charAt( idx + 1 ) == '?' && pattern.charAt( idx + 2 ) == ':' )
            {
                paren = 2;
                idx += 3;
                ret = node( RE.OP_OPEN_CLUSTER, 0 );
            }
            else
            {
                paren = 1;
                idx++;
                ret = node(RE.OP_OPEN, parens++);
            }
        }
        flags[0] &= ~NODE_TOPLEVEL;

        // Create a branch node
        int branch = branch(flags);
        if (ret == -1)
        {
            ret = branch;
        }
        else
        {
            setNextOfEnd(ret, branch);
        }

        // Loop through branches
        while (idx < len && pattern.charAt(idx) == '|')
        {
            idx++;
            branch = branch(flags);
            setNextOfEnd(ret, branch);
        }

        // Create an ending node (either a close paren or an OP_END)
        int end;
        if ( paren > 0 )
        {
            if (idx < len && pattern.charAt(idx) == ')')
            {
                idx++;
            }
            else
            {
                syntaxError("Missing close paren");
            }
            if ( paren == 1 )
            {
                end = node(RE.OP_CLOSE, closeParens);
            }
            else
            {
                end = node( RE.OP_CLOSE_CLUSTER, 0 );
            }
        }
        else
        {
            end = node(RE.OP_END, 0);
        }

        // Append the ending node to the ret nodelist
        setNextOfEnd(ret, end);

        // Hook the ends of each branch to the end node
        int currentNode = ret;
        int nextNodeOffset = instruction[ currentNode + RE.offsetNext ];
        // while the next node o
        while ( nextNodeOffset != 0 && currentNode < lenInstruction )
        {
            // If branch, make the end of the branch's operand chain point to the end node.
            if ( instruction[ currentNode + RE.offsetOpcode ] == RE.OP_BRANCH )
            {
                setNextOfEnd( currentNode + RE.nodeSize, end );
            }
            nextNodeOffset = instruction[ currentNode + RE.offsetNext ];
            currentNode += nextNodeOffset;
        }

        // Return the node list
        return ret;
    }

    /**
     * Compiles a regular expression pattern into a program runnable by the pattern
     * matcher class 'RE'.
     * @param pattern Regular expression pattern to compile (see RECompiler class
     * for details).
     * @return A compiled regular expression program.
     * @exception RESyntaxException Thrown if the regular expression has invalid syntax.
     * @see RECompiler
     * @see RE
     */
    public REProgram compile(String pattern) throws RESyntaxException
    {
        // Initialize variables for compilation
        this.pattern = pattern;                         // Save pattern in instance variable
        len = pattern.length();                         // Precompute pattern length for speed
        idx = 0;                                        // Set parsing index to the first character
        lenInstruction = 0;                             // Set emitted instruction count to zero
        parens = 1;                                     // Set paren level to 1 (the implicit outer parens)
        brackets = 0;                                   // No bracketed closures yet

        // Initialize pass by reference flags value
        int[] flags = { NODE_TOPLEVEL };

        // Parse expression
        expr(flags);

        // Should be at end of input
        if (idx != len)
        {
            if (pattern.charAt(idx) == ')')
            {
                syntaxError("Unmatched close paren");
            }
            syntaxError("Unexpected input remains");
        }

        // Return the result
        char[] ins = new char[lenInstruction];
        System.arraycopy(instruction, 0, ins, 0, lenInstruction);
        return new REProgram(parens, ins);
    }

    /**
     * Local, nested class for maintaining character ranges for character classes.
     */
    class RERange
    {
        int size = 16;                      // Capacity of current range arrays
        int[] minRange = new int[size];     // Range minima
        int[] maxRange = new int[size];     // Range maxima
        int num = 0;                        // Number of range array elements in use

        /**
         * Deletes the range at a given index from the range lists
         * @param index Index of range to delete from minRange and maxRange arrays.
         */
        void delete(int index)
        {
            // Return if no elements left or index is out of range
            if (num == 0 || index >= num)
            {
                return;
            }

            // Move elements down
            while (++index < num)
            {
                if (index - 1 >= 0)
                {
                    minRange[index-1] = minRange[index];
                    maxRange[index-1] = maxRange[index];
                }
            }

            // One less element now
            num--;
        }

        /**
         * Merges a range into the range list, coalescing ranges if possible.
         * @param min Minimum end of range
         * @param max Maximum end of range
         */
        void merge(int min, int max)
        {
            // Loop through ranges
            for (int i = 0; i < num; i++)
            {
                // Min-max is subsumed by minRange[i]-maxRange[i]
                if (min >= minRange[i] && max <= maxRange[i])
                {
                    return;
                }

                // Min-max subsumes minRange[i]-maxRange[i]
                else if (min <= minRange[i] && max >= maxRange[i])
                {
                    delete(i);
                    merge(min, max);
                    return;
                }

                // Min is in the range, but max is outside
                else if (min >= minRange[i] && min <= maxRange[i])
                {
                    delete(i);
                    min = minRange[i];
                    merge(min, max);
                    return;
                }

                // Max is in the range, but min is outside
                else if (max >= minRange[i] && max <= maxRange[i])
                {
                    delete(i);
                    max = maxRange[i];
                    merge(min, max);
                    return;
                }
            }

            // Must not overlap any other ranges
            if (num >= size)
            {
                size *= 2;
                int[] newMin = new int[size];
                int[] newMax = new int[size];
                System.arraycopy(minRange, 0, newMin, 0, num);
                System.arraycopy(maxRange, 0, newMax, 0, num);
                minRange = newMin;
                maxRange = newMax;
            }
            minRange[num] = min;
            maxRange[num] = max;
            num++;
        }

        /**
         * Removes a range by deleting or shrinking all other ranges
         * @param min Minimum end of range
         * @param max Maximum end of range
         */
        void remove(int min, int max)
        {
            // Loop through ranges
            for (int i = 0; i < num; i++)
            {
                // minRange[i]-maxRange[i] is subsumed by min-max
                if (minRange[i] >= min && maxRange[i] <= max)
                {
                    delete(i);
                    i--;
                    return;
                }

                // min-max is subsumed by minRange[i]-maxRange[i]
                else if (min >= minRange[i] && max <= maxRange[i])
                {
                    int minr = minRange[i];
                    int maxr = maxRange[i];
                    delete(i);
                    if (minr < min)
                    {
                        merge(minr, min - 1);
                    }
                    if (max < maxr)
                    {
                        merge(max + 1, maxr);
                    }
                    return;
                }

                // minRange is in the range, but maxRange is outside
                else if (minRange[i] >= min && minRange[i] <= max)
                {
                    minRange[i] = max + 1;
                    return;
                }

                // maxRange is in the range, but minRange is outside
                else if (maxRange[i] >= min && maxRange[i] <= max)
                {
                    maxRange[i] = min - 1;
                    return;
                }
            }
        }

        /**
         * Includes (or excludes) the range from min to max, inclusive.
         * @param min Minimum end of range
         * @param max Maximum end of range
         * @param include True if range should be included.  False otherwise.
         */
        void include(int min, int max, boolean include)
        {
            if (include)
            {
                merge(min, max);
            }
            else
            {
                remove(min, max);
            }
        }

        /**
         * Includes a range with the same min and max
         * @param minmax Minimum and maximum end of range (inclusive)
         * @param include True if range should be included.  False otherwise.
         */
        void include(char minmax, boolean include)
        {
            include(minmax, minmax, include);
        }
    }
}
