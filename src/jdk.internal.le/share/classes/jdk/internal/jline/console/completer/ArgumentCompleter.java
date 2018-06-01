/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.console.completer;

import jdk.internal.jline.internal.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static jdk.internal.jline.internal.Preconditions.checkNotNull;

/**
 * A {@link Completer} implementation that invokes a child completer using the appropriate <i>separator</i> argument.
 * This can be used instead of the individual completers having to know about argument parsing semantics.
 *
 * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.3
 */
public class ArgumentCompleter
    implements Completer
{
    private final ArgumentDelimiter delimiter;

    private final List<Completer> completers = new ArrayList<Completer>();

    private boolean strict = true;

    /**
     * Create a new completer with the specified argument delimiter.
     *
     * @param delimiter     The delimiter for parsing arguments
     * @param completers    The embedded completers
     */
    public ArgumentCompleter(final ArgumentDelimiter delimiter, final Collection<Completer> completers) {
        this.delimiter = checkNotNull(delimiter);
        checkNotNull(completers);
        this.completers.addAll(completers);
    }

    /**
     * Create a new completer with the specified argument delimiter.
     *
     * @param delimiter     The delimiter for parsing arguments
     * @param completers    The embedded completers
     */
    public ArgumentCompleter(final ArgumentDelimiter delimiter, final Completer... completers) {
        this(delimiter, Arrays.asList(completers));
    }

    /**
     * Create a new completer with the default {@link WhitespaceArgumentDelimiter}.
     *
     * @param completers    The embedded completers
     */
    public ArgumentCompleter(final Completer... completers) {
        this(new WhitespaceArgumentDelimiter(), completers);
    }

    /**
     * Create a new completer with the default {@link WhitespaceArgumentDelimiter}.
     *
     * @param completers    The embedded completers
     */
    public ArgumentCompleter(final List<Completer> completers) {
        this(new WhitespaceArgumentDelimiter(), completers);
    }

    /**
     * If true, a completion at argument index N will only succeed
     * if all the completions from 0-(N-1) also succeed.
     */
    public void setStrict(final boolean strict) {
        this.strict = strict;
    }

    /**
     * Returns whether a completion at argument index N will success
     * if all the completions from arguments 0-(N-1) also succeed.
     *
     * @return  True if strict.
     * @since 2.3
     */
    public boolean isStrict() {
        return this.strict;
    }

    /**
     * @since 2.3
     */
    public ArgumentDelimiter getDelimiter() {
        return delimiter;
    }

    /**
     * @since 2.3
     */
    public List<Completer> getCompleters() {
        return completers;
    }

    public int complete(final String buffer, final int cursor, final List<CharSequence> candidates) {
        // buffer can be null
        checkNotNull(candidates);

        ArgumentDelimiter delim = getDelimiter();
        ArgumentList list = delim.delimit(buffer, cursor);
        int argpos = list.getArgumentPosition();
        int argIndex = list.getCursorArgumentIndex();

        if (argIndex < 0) {
            return -1;
        }

        List<Completer> completers = getCompleters();
        Completer completer;

        // if we are beyond the end of the completers, just use the last one
        if (argIndex >= completers.size()) {
            completer = completers.get(completers.size() - 1);
        }
        else {
            completer = completers.get(argIndex);
        }

        // ensure that all the previous completers are successful before allowing this completer to pass (only if strict).
        for (int i = 0; isStrict() && (i < argIndex); i++) {
            Completer sub = completers.get(i >= completers.size() ? (completers.size() - 1) : i);
            String[] args = list.getArguments();
            String arg = (args == null || i >= args.length) ? "" : args[i];

            List<CharSequence> subCandidates = new LinkedList<CharSequence>();

            if (sub.complete(arg, arg.length(), subCandidates) == -1) {
                return -1;
            }

            if (!subCandidates.contains(arg)) {
                return -1;
            }
        }

        int ret = completer.complete(list.getCursorArgument(), argpos, candidates);

        if (ret == -1) {
            return -1;
        }

        int pos = ret + list.getBufferPosition() - argpos;

        // Special case: when completing in the middle of a line, and the area under the cursor is a delimiter,
        // then trim any delimiters from the candidates, since we do not need to have an extra delimiter.
        //
        // E.g., if we have a completion for "foo", and we enter "f bar" into the buffer, and move to after the "f"
        // and hit TAB, we want "foo bar" instead of "foo  bar".

        if ((cursor != buffer.length()) && delim.isDelimiter(buffer, cursor)) {
            for (int i = 0; i < candidates.size(); i++) {
                CharSequence val = candidates.get(i);

                while (val.length() > 0 && delim.isDelimiter(val, val.length() - 1)) {
                    val = val.subSequence(0, val.length() - 1);
                }

                candidates.set(i, val);
            }
        }

        Log.trace("Completing ", buffer, " (pos=", cursor, ") with: ", candidates, ": offset=", pos);

        return pos;
    }

    /**
     * The {@link ArgumentCompleter.ArgumentDelimiter} allows custom breaking up of a {@link String} into individual
     * arguments in order to dispatch the arguments to the nested {@link Completer}.
     *
     * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
     */
    public static interface ArgumentDelimiter
    {
        /**
         * Break the specified buffer into individual tokens that can be completed on their own.
         *
         * @param buffer    The buffer to split
         * @param pos       The current position of the cursor in the buffer
         * @return          The tokens
         */
        ArgumentList delimit(CharSequence buffer, int pos);

        /**
         * Returns true if the specified character is a whitespace parameter.
         *
         * @param buffer    The complete command buffer
         * @param pos       The index of the character in the buffer
         * @return          True if the character should be a delimiter
         */
        boolean isDelimiter(CharSequence buffer, int pos);
    }

    /**
     * Abstract implementation of a delimiter that uses the {@link #isDelimiter} method to determine if a particular
     * character should be used as a delimiter.
     *
     * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
     */
    public abstract static class AbstractArgumentDelimiter
        implements ArgumentDelimiter
    {
        private char[] quoteChars = {'\'', '"'};

        private char[] escapeChars = {'\\'};

        public void setQuoteChars(final char[] chars) {
            this.quoteChars = chars;
        }

        public char[] getQuoteChars() {
            return this.quoteChars;
        }

        public void setEscapeChars(final char[] chars) {
            this.escapeChars = chars;
        }

        public char[] getEscapeChars() {
            return this.escapeChars;
        }

        public ArgumentList delimit(final CharSequence buffer, final int cursor) {
            List<String> args = new LinkedList<String>();
            StringBuilder arg = new StringBuilder();
            int argpos = -1;
            int bindex = -1;
            int quoteStart = -1;

            for (int i = 0; (buffer != null) && (i < buffer.length()); i++) {
                // once we reach the cursor, set the
                // position of the selected index
                if (i == cursor) {
                    bindex = args.size();
                    // the position in the current argument is just the
                    // length of the current argument
                    argpos = arg.length();
                }

                if (quoteStart < 0 && isQuoteChar(buffer, i)) {
                    // Start a quote block
                    quoteStart = i;
                } else if (quoteStart >= 0) {
                    // In a quote block
                    if (buffer.charAt(quoteStart) == buffer.charAt(i) && !isEscaped(buffer, i)) {
                        // End the block; arg could be empty, but that's fine
                        args.add(arg.toString());
                        arg.setLength(0);
                        quoteStart = -1;
                    } else if (!isEscapeChar(buffer, i)) {
                        // Take the next character
                        arg.append(buffer.charAt(i));
                    }
                } else {
                    // Not in a quote block
                    if (isDelimiter(buffer, i)) {
                        if (arg.length() > 0) {
                            args.add(arg.toString());
                            arg.setLength(0); // reset the arg
                        }
                    } else if (!isEscapeChar(buffer, i)) {
                        arg.append(buffer.charAt(i));
                    }
                }
            }

            if (cursor == buffer.length()) {
                bindex = args.size();
                // the position in the current argument is just the
                // length of the current argument
                argpos = arg.length();
            }
            if (arg.length() > 0) {
                args.add(arg.toString());
            }

            return new ArgumentList(args.toArray(new String[args.size()]), bindex, argpos, cursor);
        }

        /**
         * Returns true if the specified character is a whitespace parameter. Check to ensure that the character is not
         * escaped by any of {@link #getQuoteChars}, and is not escaped by ant of the {@link #getEscapeChars}, and
         * returns true from {@link #isDelimiterChar}.
         *
         * @param buffer    The complete command buffer
         * @param pos       The index of the character in the buffer
         * @return          True if the character should be a delimiter
         */
        public boolean isDelimiter(final CharSequence buffer, final int pos) {
            return !isQuoted(buffer, pos) && !isEscaped(buffer, pos) && isDelimiterChar(buffer, pos);
        }

        public boolean isQuoted(final CharSequence buffer, final int pos) {
            return false;
        }

        public boolean isQuoteChar(final CharSequence buffer, final int pos) {
            if (pos < 0) {
                return false;
            }

            for (int i = 0; (quoteChars != null) && (i < quoteChars.length); i++) {
                if (buffer.charAt(pos) == quoteChars[i]) {
                    return !isEscaped(buffer, pos);
                }
            }

            return false;
        }

        /**
         * Check if this character is a valid escape char (i.e. one that has not been escaped)
         */
        public boolean isEscapeChar(final CharSequence buffer, final int pos) {
            if (pos < 0) {
                return false;
            }

            for (int i = 0; (escapeChars != null) && (i < escapeChars.length); i++) {
                if (buffer.charAt(pos) == escapeChars[i]) {
                    return !isEscaped(buffer, pos); // escape escape
                }
            }

            return false;
        }

        /**
         * Check if a character is escaped (i.e. if the previous character is an escape)
         *
         * @param buffer
         *          the buffer to check in
         * @param pos
         *          the position of the character to check
         * @return true if the character at the specified position in the given buffer is an escape character and the character immediately preceding it is not an
         *         escape character.
         */
        public boolean isEscaped(final CharSequence buffer, final int pos) {
            if (pos <= 0) {
                return false;
            }

            return isEscapeChar(buffer, pos - 1);
        }

        /**
         * Returns true if the character at the specified position if a delimiter. This method will only be called if
         * the character is not enclosed in any of the {@link #getQuoteChars}, and is not escaped by ant of the
         * {@link #getEscapeChars}. To perform escaping manually, override {@link #isDelimiter} instead.
         */
        public abstract boolean isDelimiterChar(CharSequence buffer, int pos);
    }

    /**
     * {@link ArgumentCompleter.ArgumentDelimiter} implementation that counts all whitespace (as reported by
     * {@link Character#isWhitespace}) as being a delimiter.
     *
     * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
     */
    public static class WhitespaceArgumentDelimiter
        extends AbstractArgumentDelimiter
    {
        /**
         * The character is a delimiter if it is whitespace, and the
         * preceding character is not an escape character.
         */
        @Override
        public boolean isDelimiterChar(final CharSequence buffer, final int pos) {
            return Character.isWhitespace(buffer.charAt(pos));
        }
    }

    /**
     * The result of a delimited buffer.
     *
     * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
     */
    public static class ArgumentList
    {
        private String[] arguments;

        private int cursorArgumentIndex;

        private int argumentPosition;

        private int bufferPosition;

        /**
         * @param arguments             The array of tokens
         * @param cursorArgumentIndex   The token index of the cursor
         * @param argumentPosition      The position of the cursor in the current token
         * @param bufferPosition        The position of the cursor in the whole buffer
         */
        public ArgumentList(final String[] arguments, final int cursorArgumentIndex, final int argumentPosition, final int bufferPosition) {
            this.arguments = checkNotNull(arguments);
            this.cursorArgumentIndex = cursorArgumentIndex;
            this.argumentPosition = argumentPosition;
            this.bufferPosition = bufferPosition;
        }

        public void setCursorArgumentIndex(final int i) {
            this.cursorArgumentIndex = i;
        }

        public int getCursorArgumentIndex() {
            return this.cursorArgumentIndex;
        }

        public String getCursorArgument() {
            if ((cursorArgumentIndex < 0) || (cursorArgumentIndex >= arguments.length)) {
                return null;
            }

            return arguments[cursorArgumentIndex];
        }

        public void setArgumentPosition(final int pos) {
            this.argumentPosition = pos;
        }

        public int getArgumentPosition() {
            return this.argumentPosition;
        }

        public void setArguments(final String[] arguments) {
            this.arguments = arguments;
        }

        public String[] getArguments() {
            return this.arguments;
        }

        public void setBufferPosition(final int pos) {
            this.bufferPosition = pos;
        }

        public int getBufferPosition() {
            return this.bufferPosition;
        }
    }
}
