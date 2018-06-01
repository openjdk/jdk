/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.console.completer;

import jdk.internal.jline.console.ConsoleReader;
import jdk.internal.jline.console.CursorBuffer;
import jdk.internal.jline.internal.Ansi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * A {@link CompletionHandler} that deals with multiple distinct completions
 * by outputting the complete list of possibilities to the console. This
 * mimics the behavior of the
 * <a href="http://www.gnu.org/directory/readline.html">readline</a> library.
 *
 * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.3
 */
public class CandidateListCompletionHandler
    implements CompletionHandler
{
    private boolean printSpaceAfterFullCompletion = true;
    private boolean stripAnsi;

    public boolean getPrintSpaceAfterFullCompletion() {
        return printSpaceAfterFullCompletion;
    }

    public void setPrintSpaceAfterFullCompletion(boolean printSpaceAfterFullCompletion) {
        this.printSpaceAfterFullCompletion = printSpaceAfterFullCompletion;
    }

    public boolean isStripAnsi() {
        return stripAnsi;
    }

    public void setStripAnsi(boolean stripAnsi) {
        this.stripAnsi = stripAnsi;
    }

    // TODO: handle quotes and escaped quotes && enable automatic escaping of whitespace

    public boolean complete(final ConsoleReader reader, final List<CharSequence> candidates, final int pos) throws
        IOException
    {
        CursorBuffer buf = reader.getCursorBuffer();

        // if there is only one completion, then fill in the buffer
        if (candidates.size() == 1) {
            String value = Ansi.stripAnsi(candidates.get(0).toString());

            if (buf.cursor == buf.buffer.length()
                    && printSpaceAfterFullCompletion
                    && !value.endsWith(" ")) {
                value += " ";
            }

            // fail if the only candidate is the same as the current buffer
            if (value.equals(buf.toString())) {
                return false;
            }

            setBuffer(reader, value, pos);

            return true;
        }
        else if (candidates.size() > 1) {
            String value = getUnambiguousCompletions(candidates);
            setBuffer(reader, value, pos);
        }

        printCandidates(reader, candidates);

        // redraw the current console buffer
        reader.drawLine();

        return true;
    }

    public static void setBuffer(final ConsoleReader reader, final CharSequence value, final int offset) throws
        IOException
    {
        while ((reader.getCursorBuffer().cursor > offset) && reader.backspace()) {
            // empty
        }

        reader.putString(value);
        reader.setCursorPosition(offset + value.length());
    }

    /**
     * Print out the candidates. If the size of the candidates is greater than the
     * {@link ConsoleReader#getAutoprintThreshold}, they prompt with a warning.
     *
     * @param candidates the list of candidates to print
     */
    public static void printCandidates(final ConsoleReader reader, Collection<CharSequence> candidates) throws
        IOException
    {
        Set<CharSequence> distinct = new HashSet<CharSequence>(candidates);

        if (distinct.size() > reader.getAutoprintThreshold()) {
            //noinspection StringConcatenation
            reader.println();
            reader.print(Messages.DISPLAY_CANDIDATES.format(distinct.size()));
            reader.flush();

            int c;

            String noOpt = Messages.DISPLAY_CANDIDATES_NO.format();
            String yesOpt = Messages.DISPLAY_CANDIDATES_YES.format();
            char[] allowed = {yesOpt.charAt(0), noOpt.charAt(0)};

            while ((c = reader.readCharacter(allowed)) != -1) {
                String tmp = new String(new char[]{(char) c});

                if (noOpt.startsWith(tmp)) {
                    reader.println();
                    return;
                }
                else if (yesOpt.startsWith(tmp)) {
                    break;
                }
                else {
                    reader.beep();
                }
            }
        }

        // copy the values and make them distinct, without otherwise affecting the ordering. Only do it if the sizes differ.
        if (distinct.size() != candidates.size()) {
            Collection<CharSequence> copy = new ArrayList<CharSequence>();

            for (CharSequence next : candidates) {
                if (!copy.contains(next)) {
                    copy.add(next);
                }
            }

            candidates = copy;
        }

        reader.println();
        reader.printColumns(candidates);
    }

    /**
     * Returns a root that matches all the {@link String} elements of the specified {@link List},
     * or null if there are no commonalities. For example, if the list contains
     * <i>foobar</i>, <i>foobaz</i>, <i>foobuz</i>, the method will return <i>foob</i>.
     */
    private String getUnambiguousCompletions(final List<CharSequence> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0).toString();
        }

        // convert to an array for speed
        String first = null;
        String[] strings = new String[candidates.size() - 1];
        for (int i = 0; i < candidates.size(); i++) {
            String str = candidates.get(i).toString();
            if (stripAnsi) {
                str = Ansi.stripAnsi(str);
            }
            if (first == null) {
                first = str;
            } else {
                strings[i - 1] = str;
            }
        }

        StringBuilder candidate = new StringBuilder();

        for (int i = 0; i < first.length(); i++) {
            if (startsWith(first.substring(0, i + 1), strings)) {
                candidate.append(first.charAt(i));
            }
            else {
                break;
            }
        }

        return candidate.toString();
    }

    /**
     * @return true is all the elements of <i>candidates</i> start with <i>starts</i>
     */
    private static boolean startsWith(final String starts, final String[] candidates) {
        for (String candidate : candidates) {
            if (!candidate.toLowerCase().startsWith(starts.toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    private static enum Messages
    {
        DISPLAY_CANDIDATES,
        DISPLAY_CANDIDATES_YES,
        DISPLAY_CANDIDATES_NO,;

        private static final
        ResourceBundle
            bundle =
            ResourceBundle.getBundle(CandidateListCompletionHandler.class.getName(), Locale.getDefault());

        public String format(final Object... args) {
            if (bundle == null)
                return "";
            else
                return String.format(bundle.getString(name()), args);
        }
    }
}
