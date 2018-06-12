/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.console;

//import java.awt.*;
//import java.awt.datatransfer.Clipboard;
//import java.awt.datatransfer.DataFlavor;
//import java.awt.datatransfer.Transferable;
//import java.awt.datatransfer.UnsupportedFlavorException;
//import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.System;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.ResourceBundle;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.internal.jline.DefaultTerminal2;
import jdk.internal.jline.Terminal;
import jdk.internal.jline.Terminal2;
import jdk.internal.jline.TerminalFactory;
import jdk.internal.jline.UnixTerminal;
import jdk.internal.jline.console.completer.CandidateListCompletionHandler;
import jdk.internal.jline.console.completer.Completer;
import jdk.internal.jline.console.completer.CompletionHandler;
import jdk.internal.jline.console.history.History;
import jdk.internal.jline.console.history.MemoryHistory;
import jdk.internal.jline.internal.Ansi;
import jdk.internal.jline.internal.Configuration;
import jdk.internal.jline.internal.Curses;
import jdk.internal.jline.internal.InputStreamReader;
import jdk.internal.jline.internal.Log;
import jdk.internal.jline.internal.NonBlockingInputStream;
import jdk.internal.jline.internal.Nullable;
import jdk.internal.jline.internal.TerminalLineSettings;
import jdk.internal.jline.internal.Urls;

import static jdk.internal.jline.internal.Preconditions.checkNotNull;

/**
 * A reader for console applications. It supports custom tab-completion,
 * saveable command history, and command line editing. On some platforms,
 * platform-specific commands will need to be issued before the reader will
 * function properly. See {@link jline.Terminal#init} for convenience
 * methods for issuing platform-specific setup commands.
 *
 * @author <a href="mailto:mwp1@cornell.edu">Marc Prud'hommeaux</a>
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 */
public class ConsoleReader implements Closeable
{
    public static final String JLINE_NOBELL = "jline.nobell";

    public static final String JLINE_ESC_TIMEOUT = "jline.esc.timeout";

    public static final String JLINE_INPUTRC = "jline.inputrc";

    public static final String INPUT_RC = ".inputrc";

    public static final String DEFAULT_INPUT_RC = "/etc/inputrc";

    public static final String JLINE_EXPAND_EVENTS = "jline.expandevents";

    public static final char BACKSPACE = '\b';

    public static final char RESET_LINE = '\r';

    public static final char KEYBOARD_BELL = '\07';

    public static final char NULL_MASK = 0;

    public static final int TAB_WIDTH = 8;

    private static final ResourceBundle
        resources = ResourceBundle.getBundle(CandidateListCompletionHandler.class.getName());

    private static final int ESCAPE = 27;
    private static final int READ_EXPIRED = -2;

    private final Terminal2 terminal;

    private final Writer out;

    private final CursorBuffer buf = new CursorBuffer();
    private boolean cursorOk;

    private String prompt;
    private int    promptLen;

    private boolean expandEvents = Configuration.getBoolean(JLINE_EXPAND_EVENTS, true);

    private boolean bellEnabled = !Configuration.getBoolean(JLINE_NOBELL, true);

    private boolean handleUserInterrupt = false;

    private boolean handleLitteralNext = true;

    private Character mask;

    private Character echoCharacter;

    private CursorBuffer originalBuffer = null;

    private StringBuffer searchTerm = null;

    private String previousSearchTerm = "";

    private int searchIndex = -1;

    private int parenBlinkTimeout = 500;

    // Reading buffers
    private final StringBuilder opBuffer = new StringBuilder();
    private final Stack<Character> pushBackChar = new Stack<Character>();

    /*
     * The reader and the nonBlockingInput go hand-in-hand.  The reader wraps
     * the nonBlockingInput, but we have to retain a handle to it so that
     * we can shut down its blocking read thread when we go away.
     */
    private NonBlockingInputStream in;
    private long                   escapeTimeout;
    private Reader                 reader;

    /**
     * Last character searched for with a vi character search
     */
    private char  charSearchChar = 0;           // Character to search for
    private char  charSearchLastInvokeChar = 0; // Most recent invocation key
    private char  charSearchFirstInvokeChar = 0;// First character that invoked

    /**
     * The vi yank buffer
     */
    private String yankBuffer = "";

    private KillRing killRing = new KillRing();

    private String encoding;

    private boolean quotedInsert;

    private boolean recording;

    private String macro = "";

    private String appName;

    private URL inputrcUrl;

    private ConsoleKeys consoleKeys;

    private String commentBegin = null;

    private boolean skipLF = false;

    /**
     * Set to true if the reader should attempt to detect copy-n-paste. The
     * effect of this that an attempt is made to detect if tab is quickly
     * followed by another character, then it is assumed that the tab was
     * a literal tab as part of a copy-and-paste operation and is inserted as
     * such.
     */
    private boolean copyPasteDetection = false;

    /*
     * Current internal state of the line reader
     */
    private State   state = State.NORMAL;

    /**
     * Possible states in which the current readline operation may be in.
     */
    private static enum State {
        /**
         * The user is just typing away
         */
        NORMAL,
        /**
         * In the middle of a emacs seach
         */
        SEARCH,
        FORWARD_SEARCH,
        /**
         * VI "yank-to" operation ("y" during move mode)
         */
        VI_YANK_TO,
        /**
         * VI "delete-to" operation ("d" during move mode)
         */
        VI_DELETE_TO,
        /**
         * VI "change-to" operation ("c" during move mode)
         */
        VI_CHANGE_TO
    }

    public ConsoleReader() throws IOException {
        this(null, new FileInputStream(FileDescriptor.in), System.out, null);
    }

    public ConsoleReader(final InputStream in, final OutputStream out) throws IOException {
        this(null, in, out, null);
    }

    public ConsoleReader(final InputStream in, final OutputStream out, final Terminal term) throws IOException {
        this(null, in, out, term);
    }

    public ConsoleReader(final @Nullable String appName, final InputStream in, final OutputStream out, final @Nullable Terminal term) throws IOException {
        this(appName, in, out, term, null);
    }

    public ConsoleReader(final @Nullable String appName, final InputStream in, final OutputStream out, final @Nullable Terminal term, final @Nullable String encoding)
        throws IOException
    {
        this.appName = appName != null ? appName : "JLine";
        this.encoding = encoding != null ? encoding : Configuration.getEncoding();
        Terminal terminal = term != null ? term : TerminalFactory.get();
        this.terminal = terminal instanceof Terminal2 ? (Terminal2) terminal : new DefaultTerminal2(terminal);
        String outEncoding = terminal.getOutputEncoding() != null? terminal.getOutputEncoding() : this.encoding;
        this.out = new OutputStreamWriter(terminal.wrapOutIfNeeded(out), outEncoding);
        setInput( in );

        this.inputrcUrl = getInputRc();

        consoleKeys = new ConsoleKeys(this.appName, inputrcUrl);

        if (terminal instanceof UnixTerminal
                && TerminalLineSettings.DEFAULT_TTY.equals(((UnixTerminal) terminal).getSettings().getTtyDevice())
                && Configuration.getBoolean("jline.sigcont", false)) {
            setupSigCont();
        }
    }

    private void setupSigCont() {
        // Check that sun.misc.SignalHandler and sun.misc.Signal exists
        try {
            Class<?> signalClass = Class.forName("sun.misc.Signal");
            Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");
            // Implement signal handler
            Object signalHandler = Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{signalHandlerClass}, new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            // only method we are proxying is handle()
                            terminal.init();
                            try {
                                drawLine();
                                flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return null;
                        }
                    });
            // Register the signal handler, this code is equivalent to:
            // Signal.handle(new Signal("CONT"), signalHandler);
            signalClass.getMethod("handle", signalClass, signalHandlerClass).invoke(null, signalClass.getConstructor(String.class).newInstance("CONT"), signalHandler);
        } catch (ClassNotFoundException cnfe) {
            // sun.misc Signal handler classes don't exist
        } catch (Exception e) {
            // Ignore this one too, if the above failed, the signal API is incompatible with what we're expecting
        }
    }

    /**
     * Retrieve the URL for the inputrc configuration file in effect. Intended
     * use is for instantiating ConsoleKeys, to read inputrc variables.
     */
    public static URL getInputRc() throws IOException {
        String path = Configuration.getString(JLINE_INPUTRC);
        if (path == null) {
            File f = new File(Configuration.getUserHome(), INPUT_RC);
            if (!f.exists()) {
                f = new File(DEFAULT_INPUT_RC);
            }
            return f.toURI().toURL();
        } else {
            return Urls.create(path);
        }
    }

    public KeyMap getKeys() {
        return consoleKeys.getKeys();
    }

    void setInput(final InputStream in) throws IOException {
        this.escapeTimeout = Configuration.getLong(JLINE_ESC_TIMEOUT, 100);
        boolean nonBlockingEnabled =
               escapeTimeout > 0L
            && terminal.isSupported()
            && in != null;

        /*
         * If we had a non-blocking thread already going, then shut it down
         * and start a new one.
         */
        if (this.in != null) {
            this.in.shutdown();
        }

        final InputStream wrapped = terminal.wrapInIfNeeded( in );

        this.in = new NonBlockingInputStream(wrapped, nonBlockingEnabled);
        this.reader = new InputStreamReader( this.in, encoding );
    }

    /**
     * Shuts the console reader down.  This method should be called when you
     * have completed using the reader as it shuts down and cleans up resources
     * that would otherwise be "leaked".
     */
    @Override
    public void close() {
        if (in != null) {
            in.shutdown();
        }
    }

    /**
     * Shuts the console reader down.  The same as {@link #close()}.
     * @deprecated Use {@link #close()} instead.
     */
    @Deprecated
    public void shutdown() {
        this.close();
    }

    /**
     * Shuts down the ConsoleReader if the JVM attempts to clean it up.
     */
    @Override
    @SuppressWarnings("deprecation")
    protected void finalize() throws Throwable {
        try {
            close();
        }
        finally {
            super.finalize();
        }
    }

    public InputStream getInput() {
        return in;
    }

    public Writer getOutput() {
        return out;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public CursorBuffer getCursorBuffer() {
        return buf;
    }

    public void setExpandEvents(final boolean expand) {
        this.expandEvents = expand;
    }

    public boolean getExpandEvents() {
        return expandEvents;
    }

    /**
     * Enables or disables copy and paste detection. The effect of enabling this
     * this setting is that when a tab is received immediately followed by another
     * character, the tab will not be treated as a completion, but as a tab literal.
     * @param onoff true if detection is enabled
     */
    public void setCopyPasteDetection(final boolean onoff) {
        copyPasteDetection = onoff;
    }

    /**
     * @return true if copy and paste detection is enabled.
     */
    public boolean isCopyPasteDetectionEnabled() {
        return copyPasteDetection;
    }

    /**
     * Set whether the console bell is enabled.
     *
     * @param enabled true if enabled; false otherwise
     * @since 2.7
     */
    public void setBellEnabled(boolean enabled) {
        this.bellEnabled = enabled;
    }

    /**
     * Get whether the console bell is enabled
     *
     * @return true if enabled; false otherwise
     * @since 2.7
     */
    public boolean getBellEnabled() {
        return bellEnabled;
    }

    /**
     * Set whether user interrupts (ctrl-C) are handled by having JLine
     * throw {@link UserInterruptException} from {@link #readLine}.
     * Otherwise, the JVM will handle {@code SIGINT} as normal, which
     * usually causes it to exit. The default is {@code false}.
     *
     * @since 2.10
     */
    public void setHandleUserInterrupt(boolean enabled)
    {
        this.handleUserInterrupt = enabled;
    }

    /**
     * Get whether user interrupt handling is enabled
     *
     * @return true if enabled; false otherwise
     * @since 2.10
     */
    public boolean getHandleUserInterrupt()
    {
        return handleUserInterrupt;
    }

    /**
     * Set wether literal next are handled by JLine.
     *
     * @since 2.13
     */
    public void setHandleLitteralNext(boolean handleLitteralNext) {
        this.handleLitteralNext = handleLitteralNext;
    }

    /**
     * Get wether literal next are handled by JLine.
     *
     * @since 2.13
     */
    public boolean getHandleLitteralNext() {
        return handleLitteralNext;
    }

    /**
     * Sets the string that will be used to start a comment when the
     * insert-comment key is struck.
     * @param commentBegin The begin comment string.
     * @since 2.7
     */
    public void setCommentBegin(String commentBegin) {
        this.commentBegin = commentBegin;
    }

    /**
     * @return the string that will be used to start a comment when the
     * insert-comment key is struck.
     * @since 2.7
     */
    public String getCommentBegin() {
        String str = commentBegin;

        if (str == null) {
            str = consoleKeys.getVariable("comment-begin");
            if (str == null) {
                str = "#";
            }
        }
        return str;
    }

    public void setPrompt(final String prompt) {
        this.prompt = prompt;
        this.promptLen = (prompt == null) ? 0 : wcwidth(Ansi.stripAnsi(lastLine(prompt)), 0);
    }

    public String getPrompt() {
        return prompt;
    }

    /**
     * Set the echo character. For example, to have "*" entered when a password is typed:
     * <pre>
     * myConsoleReader.setEchoCharacter(new Character('*'));
     * </pre>
     * Setting the character to <code>null</code> will restore normal character echoing.<p/>
     * Setting the character to <code>Character.valueOf(0)</code> will cause nothing to be echoed.
     *
     * @param c the character to echo to the console in place of the typed character.
     */
    public void setEchoCharacter(final Character c) {
        this.echoCharacter = c;
    }

    /**
     * Returns the echo character.
     */
    public Character getEchoCharacter() {
        return echoCharacter;
    }

    /**
     * Erase the current line.
     *
     * @return false if we failed (e.g., the buffer was empty)
     */
    protected final boolean resetLine() throws IOException {
        if (buf.cursor == 0) {
            return false;
        }

        StringBuilder killed = new StringBuilder();

        while (buf.cursor > 0) {
            char c = buf.current();
            if (c == 0) {
                break;
            }

            killed.append(c);
            backspace();
        }

        String copy = killed.reverse().toString();
        killRing.addBackwards(copy);

        return true;
    }

    int wcwidth(CharSequence str, int pos) {
        return wcwidth(str, 0, str.length(), pos);
    }

    int wcwidth(CharSequence str, int start, int end, int pos) {
        int cur = pos;
        for (int i = start; i < end;) {
            int ucs;
            char c1 = str.charAt(i++);
            if (!Character.isHighSurrogate(c1) || i >= end) {
                ucs = c1;
            } else {
                char c2 = str.charAt(i);
                if (Character.isLowSurrogate(c2)) {
                    i++;
                    ucs = Character.toCodePoint(c1, c2);
                } else {
                    ucs = c1;
                }
            }
            cur += wcwidth(ucs, cur);
        }
        return cur - pos;
    }

    int wcwidth(int ucs, int pos) {
        if (ucs == '\t') {
            return nextTabStop(pos);
        } else if (ucs < 32) {
            return 2;
        } else  {
            int w = WCWidth.wcwidth(ucs);
            return w > 0 ? w : 0;
        }
    }

    int nextTabStop(int pos) {
        int tabWidth = TAB_WIDTH;
        int width = getTerminal().getWidth();
        int mod = (pos + tabWidth - 1) % tabWidth;
        int npos = pos + tabWidth - mod;
        return npos < width ? npos - pos : width - pos;
    }

    int getCursorPosition() {
        return promptLen + wcwidth(buf.buffer, 0, buf.cursor, promptLen);
    }

    /**
     * Returns the text after the last '\n'.
     * prompt is returned if no '\n' characters are present.
     * null is returned if prompt is null.
     */
    private static String lastLine(String str) {
        if (str == null) return "";
        int last = str.lastIndexOf("\n");

        if (last >= 0) {
            return str.substring(last + 1, str.length());
        }

        return str;
    }

    /**
     * Move the cursor position to the specified absolute index.
     */
    public boolean setCursorPosition(final int position) throws IOException {
        if (position == buf.cursor) {
            return true;
        }

        return moveCursor(position - buf.cursor) != 0;
    }

    /**
     * Set the current buffer's content to the specified {@link String}. The
     * visual console will be modified to show the current buffer.
     *
     * @param buffer the new contents of the buffer.
     */
    private void setBuffer(final String buffer) throws IOException {
        // don't bother modifying it if it is unchanged
        if (buffer.equals(buf.buffer.toString())) {
            return;
        }

        // obtain the difference between the current buffer and the new one
        int sameIndex = 0;

        for (int i = 0, l1 = buffer.length(), l2 = buf.buffer.length(); (i < l1)
            && (i < l2); i++) {
            if (buffer.charAt(i) == buf.buffer.charAt(i)) {
                sameIndex++;
            }
            else {
                break;
            }
        }

        int diff = buf.cursor - sameIndex;
        if (diff < 0) { // we can't backspace here so try from the end of the buffer
            moveToEnd();
            diff = buf.buffer.length() - sameIndex;
        }

        backspace(diff); // go back for the differences
        killLine(); // clear to the end of the line
        buf.buffer.setLength(sameIndex); // the new length
        putString(buffer.substring(sameIndex)); // append the differences
    }

    private void setBuffer(final CharSequence buffer) throws IOException {
        setBuffer(String.valueOf(buffer));
    }

    private void setBufferKeepPos(final String buffer) throws IOException {
        int pos = buf.cursor;
        setBuffer(buffer);
        setCursorPosition(pos);
    }

    private void setBufferKeepPos(final CharSequence buffer) throws IOException {
        setBufferKeepPos(String.valueOf(buffer));
    }

    /**
     * Output put the prompt + the current buffer
     */
    public void drawLine() throws IOException {
        String prompt = getPrompt();
        if (prompt != null) {
            rawPrint(prompt);
        }

        fmtPrint(buf.buffer, 0, buf.cursor, promptLen);

        // force drawBuffer to check for weird wrap (after clear screen)
        drawBuffer();
    }

    /**
     * Clear the line and redraw it.
     */
    public void redrawLine() throws IOException {
        tputs("carriage_return");
        drawLine();
    }

    /**
     * Clear the buffer and add its contents to the history.
     *
     * @return the former contents of the buffer.
     */
    final String finishBuffer() throws IOException { // FIXME: Package protected because used by tests
        String str = buf.buffer.toString();
        String historyLine = str;

        if (expandEvents) {
            try {
                str = expandEvents(str);
                // all post-expansion occurrences of '!' must have been escaped, so re-add escape to each
                historyLine = str.replace("!", "\\!");
                // only leading '^' results in expansion, so only re-add escape for that case
                historyLine = historyLine.replaceAll("^\\^", "\\\\^");
            } catch(IllegalArgumentException e) {
                Log.error("Could not expand event", e);
                beep();
                buf.clear();
                str = "";
            }
        }

        // we only add it to the history if the buffer is not empty
        // and if mask is null, since having a mask typically means
        // the string was a password. We clear the mask after this call
        if (str.length() > 0) {
            if (mask == null && isHistoryEnabled()) {
                history.add(historyLine);
            }
            else {
                mask = null;
            }
        }

        history.moveToEnd();

        buf.buffer.setLength(0);
        buf.cursor = 0;

        return str;
    }

    /**
     * Expand event designator such as !!, !#, !3, etc...
     * See http://www.gnu.org/software/bash/manual/html_node/Event-Designators.html
     */
    @SuppressWarnings("fallthrough")
    protected String expandEvents(String str) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\\':
                    // any '\!' should be considered an expansion escape, so skip expansion and strip the escape character
                    // a leading '\^' should be considered an expansion escape, so skip expansion and strip the escape character
                    // otherwise, add the escape
                    if (i + 1 < str.length()) {
                        char nextChar = str.charAt(i+1);
                        if (nextChar == '!' || (nextChar == '^' && i == 0)) {
                            c = nextChar;
                            i++;
                        }
                    }
                    sb.append(c);
                    break;
                case '!':
                    if (i + 1 < str.length()) {
                        c = str.charAt(++i);
                        boolean neg = false;
                        String rep = null;
                        int i1, idx;
                        switch (c) {
                            case '!':
                                if (history.size() == 0) {
                                    throw new IllegalArgumentException("!!: event not found");
                                }
                                rep = history.get(history.index() - 1).toString();
                                break;
                            case '#':
                                sb.append(sb.toString());
                                break;
                            case '?':
                                i1 = str.indexOf('?', i + 1);
                                if (i1 < 0) {
                                    i1 = str.length();
                                }
                                String sc = str.substring(i + 1, i1);
                                i = i1;
                                idx = searchBackwards(sc);
                                if (idx < 0) {
                                    throw new IllegalArgumentException("!?" + sc + ": event not found");
                                } else {
                                    rep = history.get(idx).toString();
                                }
                                break;
                            case '$':
                                if (history.size() == 0) {
                                    throw new IllegalArgumentException("!$: event not found");
                                }
                                String previous = history.get(history.index() - 1).toString().trim();
                                int lastSpace = previous.lastIndexOf(' ');
                                if(lastSpace != -1) {
                                    rep = previous.substring(lastSpace+1);
                                } else {
                                    rep = previous;
                                }
                                break;
                            case ' ':
                            case '\t':
                                sb.append('!');
                                sb.append(c);
                                break;
                            case '-':
                                neg = true;
                                i++;
                                // fall through
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
                                i1 = i;
                                for (; i < str.length(); i++) {
                                    c = str.charAt(i);
                                    if (c < '0' || c > '9') {
                                        break;
                                    }
                                }
                                idx = 0;
                                try {
                                    idx = Integer.parseInt(str.substring(i1, i));
                                } catch (NumberFormatException e) {
                                    throw new IllegalArgumentException((neg ? "!-" : "!") + str.substring(i1, i) + ": event not found");
                                }
                                if (neg) {
                                    if (idx > 0 && idx <= history.size()) {
                                        rep = (history.get(history.index() - idx)).toString();
                                    } else {
                                        throw new IllegalArgumentException((neg ? "!-" : "!") + str.substring(i1, i) + ": event not found");
                                    }
                                } else {
                                    if (idx > history.index() - history.size() && idx <= history.index()) {
                                        rep = (history.get(idx - 1)).toString();
                                    } else {
                                        throw new IllegalArgumentException((neg ? "!-" : "!") + str.substring(i1, i) + ": event not found");
                                    }
                                }
                                break;
                            default:
                                String ss = str.substring(i);
                                i = str.length();
                                idx = searchBackwards(ss, history.index(), true);
                                if (idx < 0) {
                                    throw new IllegalArgumentException("!" + ss + ": event not found");
                                } else {
                                    rep = history.get(idx).toString();
                                }
                                break;
                        }
                        if (rep != null) {
                            sb.append(rep);
                        }
                    } else {
                        sb.append(c);
                    }
                    break;
                case '^':
                    if (i == 0) {
                        int i1 = str.indexOf('^', i + 1);
                        int i2 = str.indexOf('^', i1 + 1);
                        if (i2 < 0) {
                            i2 = str.length();
                        }
                        if (i1 > 0 && i2 > 0) {
                            String s1 = str.substring(i + 1, i1);
                            String s2 = str.substring(i1 + 1, i2);
                            String s = history.get(history.index() - 1).toString().replace(s1, s2);
                            sb.append(s);
                            i = i2 + 1;
                            break;
                        }
                    }
                    sb.append(c);
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        String result = sb.toString();
        if (!str.equals(result)) {
            fmtPrint(result, getCursorPosition());
            println();
            flush();
        }
        return result;

    }

    /**
     * Write out the specified string to the buffer and the output stream.
     */
    public void putString(final CharSequence str) throws IOException {
        int pos = getCursorPosition();
        buf.write(str);
        if (mask == null) {
            // no masking
            fmtPrint(str, pos);
        } else if (mask == NULL_MASK) {
            // don't print anything
        } else {
            rawPrint(mask, str.length());
        }
        drawBuffer();
    }

    /**
     * Redraw the rest of the buffer from the cursor onwards. This is necessary
     * for inserting text into the buffer.
     *
     * @param clear the number of characters to clear after the end of the buffer
     */
    private void drawBuffer(final int clear) throws IOException {
        // debug ("drawBuffer: " + clear);
        int nbChars = buf.length() - buf.cursor;
        if (buf.cursor != buf.length() || clear != 0) {
            if (mask != null) {
                if (mask != NULL_MASK) {
                    rawPrint(mask, nbChars);
                } else {
                    nbChars = 0;
                }
            } else {
                fmtPrint(buf.buffer, buf.cursor, buf.length());
            }
        }
        int cursorPos = promptLen + wcwidth(buf.buffer, 0, buf.length(), promptLen);
        if (terminal.hasWeirdWrap() && !cursorOk) {
            int width = terminal.getWidth();
            // best guess on whether the cursor is in that weird location...
            // Need to do this without calling ansi cursor location methods
            // otherwise it breaks paste of wrapped lines in xterm.
            if (cursorPos > 0 && (cursorPos % width == 0)) {
                // the following workaround is reverse-engineered from looking
                // at what bash sent to the terminal in the same situation
                rawPrint(' '); // move cursor to next line by printing dummy space
                tputs("carriage_return"); // CR / not newline.
            }
            cursorOk = true;
        }
        clearAhead(clear, cursorPos);
        back(nbChars);
    }

    /**
     * Redraw the rest of the buffer from the cursor onwards. This is necessary
     * for inserting text into the buffer.
     */
    private void drawBuffer() throws IOException {
        drawBuffer(0);
    }

    /**
     * Clear ahead the specified number of characters without moving the cursor.
     *
     * @param num the number of characters to clear
     * @param pos the current screen cursor position
     */
    private void clearAhead(int num, final int pos) throws IOException {
        if (num == 0) return;

        int width = terminal.getWidth();
        // Use kill line
        if (terminal.getStringCapability("clr_eol") != null) {
            int cur = pos;
            int c0 = cur % width;
            // Erase end of current line
            int nb = Math.min(num, width - c0);
            tputs("clr_eol");
            num -= nb;
            // Loop
            while (num > 0) {
                // Move to beginning of next line
                int prev = cur;
                cur = cur - cur % width + width;
                moveCursorFromTo(prev, cur);
                // Erase
                nb = Math.min(num, width);
                tputs("clr_eol");
                num -= nb;
            }
            moveCursorFromTo(cur, pos);
        }
        // Terminal does not wrap on the right margin
        else if (!terminal.getBooleanCapability("auto_right_margin")) {
            int cur = pos;
            int c0 = cur % width;
            // Erase end of current line
            int nb = Math.min(num, width - c0);
            rawPrint(' ', nb);
            num -= nb;
            cur += nb;
            // Loop
            while (num > 0) {
                // Move to beginning of next line
                moveCursorFromTo(cur, ++cur);
                // Erase
                nb = Math.min(num, width);
                rawPrint(' ', nb);
                num -= nb;
                cur += nb;
            }
            moveCursorFromTo(cur, pos);
        }
        // Simple erasure
        else {
            rawPrint(' ', num);
            moveCursorFromTo(pos + num, pos);
        }
    }

    /**
     * Move the visual cursor backward without modifying the buffer cursor.
     */
    protected void back(final int num) throws IOException {
        if (num == 0) return;
        int i0 = promptLen + wcwidth(buf.buffer, 0, buf.cursor, promptLen);
        int i1 = i0 + ((mask != null) ? num : wcwidth(buf.buffer, buf.cursor, buf.cursor + num, i0));
        moveCursorFromTo(i1, i0);
    }

    /**
     * Flush the console output stream. This is important for printout out single characters (like a backspace or
     * keyboard) that we want the console to handle immediately.
     */
    public void flush() throws IOException {
        out.flush();
    }

    private int backspaceAll() throws IOException {
        return backspace(Integer.MAX_VALUE);
    }

    /**
     * Issue <em>num</em> backspaces.
     *
     * @return the number of characters backed up
     */
    private int backspace(final int num) throws IOException {
        if (buf.cursor == 0) {
            return 0;
        }

        int count = - moveCursor(-num);
        int clear = wcwidth(buf.buffer, buf.cursor, buf.cursor + count, getCursorPosition());
        buf.buffer.delete(buf.cursor, buf.cursor + count);

        drawBuffer(clear);
        return count;
    }

    /**
     * Issue a backspace.
     *
     * @return true if successful
     */
    public boolean backspace() throws IOException {
        return backspace(1) == 1;
    }

    protected boolean moveToEnd() throws IOException {
        if (buf.cursor == buf.length()) {
            return true;
        }
        return moveCursor(buf.length() - buf.cursor) > 0;
    }

    /**
     * Delete the character at the current position and redraw the remainder of the buffer.
     */
    private boolean deleteCurrentCharacter() throws IOException {
        if (buf.length() == 0 || buf.cursor == buf.length()) {
            return false;
        }

        buf.buffer.deleteCharAt(buf.cursor);
        drawBuffer(1);
        return true;
    }

    /**
     * This method is calling while doing a delete-to ("d"), change-to ("c"),
     * or yank-to ("y") and it filters out only those movement operations
     * that are allowable during those operations. Any operation that isn't
     * allow drops you back into movement mode.
     *
     * @param op The incoming operation to remap
     * @return The remaped operation
     */
    private Operation viDeleteChangeYankToRemap (Operation op) {
        switch (op) {
            case VI_EOF_MAYBE:
            case ABORT:
            case BACKWARD_CHAR:
            case FORWARD_CHAR:
            case END_OF_LINE:
            case VI_MATCH:
            case VI_BEGINNING_OF_LINE_OR_ARG_DIGIT:
            case VI_ARG_DIGIT:
            case VI_PREV_WORD:
            case VI_END_WORD:
            case VI_CHAR_SEARCH:
            case VI_NEXT_WORD:
            case VI_FIRST_PRINT:
            case VI_GOTO_MARK:
            case VI_COLUMN:
            case VI_DELETE_TO:
            case VI_YANK_TO:
            case VI_CHANGE_TO:
                return op;

            default:
                return Operation.VI_MOVEMENT_MODE;
        }
    }

    /**
     * Deletes the previous character from the cursor position
     * @param count number of times to do it.
     * @return true if it was done.
     */
    private boolean viRubout(int count) throws IOException {
        boolean ok = true;
        for (int i = 0; ok && i < count; i++) {
            ok = backspace();
        }
        return ok;
    }

    /**
     * Deletes the character you are sitting on and sucks the rest of
     * the line in from the right.
     * @param count Number of times to perform the operation.
     * @return true if its works, false if it didn't
     */
    private boolean viDelete(int count) throws IOException {
        boolean ok = true;
        for (int i = 0; ok && i < count; i++) {
            ok = deleteCurrentCharacter();
        }
        return ok;
    }

    /**
     * Switches the case of the current character from upper to lower
     * or lower to upper as necessary and advances the cursor one
     * position to the right.
     * @param count The number of times to repeat
     * @return true if it completed successfully, false if not all
     *   case changes could be completed.
     */
    private boolean viChangeCase(int count) throws IOException {
        boolean ok = true;
        for (int i = 0; ok && i < count; i++) {

            ok = buf.cursor < buf.buffer.length ();
            if (ok) {
                char ch = buf.buffer.charAt(buf.cursor);
                if (Character.isUpperCase(ch)) {
                    ch = Character.toLowerCase(ch);
                }
                else if (Character.isLowerCase(ch)) {
                    ch = Character.toUpperCase(ch);
                }
                buf.buffer.setCharAt(buf.cursor, ch);
                drawBuffer(1);
                moveCursor(1);
            }
        }
        return ok;
    }

    /**
     * Implements the vi change character command (in move-mode "r"
     * followed by the character to change to).
     * @param count Number of times to perform the action
     * @param c The character to change to
     * @return Whether or not there were problems encountered
     */
    private boolean viChangeChar(int count, int c) throws IOException {
        // EOF, ESC, or CTRL-C aborts.
        if (c < 0 || c == '\033' || c == '\003') {
            return true;
        }

        boolean ok = true;
        for (int i = 0; ok && i < count; i++) {
            ok = buf.cursor < buf.buffer.length ();
            if (ok) {
                buf.buffer.setCharAt(buf.cursor, (char) c);
                drawBuffer(1);
                if (i < (count-1)) {
                    moveCursor(1);
                }
            }
        }
        return ok;
    }

    /**
     * This is a close facsimile of the actual vi previous word logic. In
     * actual vi words are determined by boundaries of identity characterse.
     * This logic is a bit more simple and simply looks at white space or
     * digits or characters.  It should be revised at some point.
     *
     * @param count number of iterations
     * @return true if the move was successful, false otherwise
     */
    private boolean viPreviousWord(int count) throws IOException {
        boolean ok = true;
        if (buf.cursor == 0) {
            return false;
        }

        int pos = buf.cursor - 1;
        for (int i = 0; pos > 0 && i < count; i++) {
            // If we are on white space, then move back.
            while (pos > 0 && isWhitespace(buf.buffer.charAt(pos))) {
                --pos;
            }

            while (pos > 0 && !isDelimiter(buf.buffer.charAt(pos-1))) {
                --pos;
            }

            if (pos > 0 && i < (count-1)) {
                --pos;
            }
        }
        setCursorPosition(pos);
        return ok;
    }

    /**
     * Performs the vi "delete-to" action, deleting characters between a given
     * span of the input line.
     * @param startPos The start position
     * @param endPos The end position.
     * @param isChange If true, then the delete is part of a change operationg
     *    (e.g. "c$" is change-to-end-of line, so we first must delete to end
     *    of line to start the change
     * @return true if it succeeded, false otherwise
     */
    private boolean viDeleteTo(int startPos, int endPos, boolean isChange) throws IOException {
        if (startPos == endPos) {
            return true;
        }

        if (endPos < startPos) {
            int tmp = endPos;
            endPos = startPos;
            startPos = tmp;
        }

        setCursorPosition(startPos);
        buf.cursor = startPos;
        buf.buffer.delete(startPos, endPos);
        drawBuffer(endPos - startPos);

        // If we are doing a delete operation (e.g. "d$") then don't leave the
        // cursor dangling off the end. In reality the "isChange" flag is silly
        // what is really happening is that if we are in "move-mode" then the
        // cursor can't be moved off the end of the line, but in "edit-mode" it
        // is ok, but I have no easy way of knowing which mode we are in.
        if (! isChange && startPos > 0 && startPos == buf.length()) {
            moveCursor(-1);
        }
        return true;
    }

    /**
     * Implement the "vi" yank-to operation.  This operation allows you
     * to yank the contents of the current line based upon a move operation,
     * for exaple "yw" yanks the current word, "3yw" yanks 3 words, etc.
     *
     * @param startPos The starting position from which to yank
     * @param endPos The ending position to which to yank
     * @return true if the yank succeeded
     */
    private boolean viYankTo(int startPos, int endPos) throws IOException {
        int cursorPos = startPos;

        if (endPos < startPos) {
            int tmp = endPos;
            endPos = startPos;
            startPos = tmp;
        }

        if (startPos == endPos) {
            yankBuffer = "";
            return true;
        }

        yankBuffer = buf.buffer.substring(startPos, endPos);

        /*
         * It was a movement command that moved the cursor to find the
         * end position, so put the cursor back where it started.
         */
        setCursorPosition(cursorPos);
        return true;
    }

    /**
     * Pasts the yank buffer to the right of the current cursor position
     * and moves the cursor to the end of the pasted region.
     *
     * @param count Number of times to perform the operation.
     * @return true if it worked, false otherwise
     */
    private boolean viPut(int count) throws IOException {
        if (yankBuffer.length () == 0) {
            return true;
        }
        if (buf.cursor < buf.buffer.length ()) {
            moveCursor(1);
        }
        for (int i = 0; i < count; i++) {
            putString(yankBuffer);
        }
        moveCursor(-1);
        return true;
    }

    /**
     * Searches forward of the current position for a character and moves
     * the cursor onto it.
     * @param count Number of times to repeat the process.
     * @param ch The character to search for
     * @return true if the char was found, false otherwise
     */
    private boolean viCharSearch(int count, int invokeChar, int ch) throws IOException {
        if (ch < 0 || invokeChar < 0) {
            return false;
        }

        char    searchChar = (char)ch;
        boolean isForward;
        boolean stopBefore;

        /*
         * The character stuff turns out to be hairy. Here is how it works:
         *   f - search forward for ch
         *   F - search backward for ch
         *   t - search forward for ch, but stop just before the match
         *   T - search backward for ch, but stop just after the match
         *   ; - After [fFtT;], repeat the last search, after ',' reverse it
         *   , - After [fFtT;], reverse the last search, after ',' repeat it
         */
        if (invokeChar == ';' || invokeChar == ',') {
            // No recent search done? Then bail
            if (charSearchChar == 0) {
                return false;
            }

            // Reverse direction if switching between ',' and ';'
            if (charSearchLastInvokeChar == ';' || charSearchLastInvokeChar == ',') {
                if (charSearchLastInvokeChar != invokeChar) {
                    charSearchFirstInvokeChar = switchCase(charSearchFirstInvokeChar);
                }
            }
            else {
                if (invokeChar == ',') {
                    charSearchFirstInvokeChar = switchCase(charSearchFirstInvokeChar);
                }
            }

            searchChar = charSearchChar;
        }
        else {
            charSearchChar            = searchChar;
            charSearchFirstInvokeChar = (char) invokeChar;
        }

        charSearchLastInvokeChar = (char)invokeChar;

        isForward = Character.isLowerCase(charSearchFirstInvokeChar);
        stopBefore = (Character.toLowerCase(charSearchFirstInvokeChar) == 't');

        boolean ok = false;

        if (isForward) {
            while (count-- > 0) {
                int pos = buf.cursor + 1;
                while (pos < buf.buffer.length()) {
                    if (buf.buffer.charAt(pos) == searchChar) {
                        setCursorPosition(pos);
                        ok = true;
                        break;
                    }
                    ++pos;
                }
            }

            if (ok) {
                if (stopBefore)
                    moveCursor(-1);

                /*
                 * When in yank-to, move-to, del-to state we actually want to
                 * go to the character after the one we landed on to make sure
                 * that the character we ended up on is included in the
                 * operation
                 */
                if (isInViMoveOperationState()) {
                    moveCursor(1);
                }
            }
        }
        else {
            while (count-- > 0) {
                int pos = buf.cursor - 1;
                while (pos >= 0) {
                    if (buf.buffer.charAt(pos) == searchChar) {
                        setCursorPosition(pos);
                        ok = true;
                        break;
                    }
                    --pos;
                }
            }

            if (ok && stopBefore)
                moveCursor(1);
        }

        return ok;
    }

    private static char switchCase(char ch) {
        if (Character.isUpperCase(ch)) {
            return Character.toLowerCase(ch);
        }
        return Character.toUpperCase(ch);
    }

    /**
     * @return true if line reader is in the middle of doing a change-to
     *   delete-to or yank-to.
     */
    private final boolean isInViMoveOperationState() {
        return state == State.VI_CHANGE_TO
            || state == State.VI_DELETE_TO
            || state == State.VI_YANK_TO;
    }

    /**
     * This is a close facsimile of the actual vi next word logic.
     * As with viPreviousWord() this probably needs to be improved
     * at some point.
     *
     * @param count number of iterations
     * @return true if the move was successful, false otherwise
     */
    private boolean viNextWord(int count) throws IOException {
        int pos = buf.cursor;
        int end = buf.buffer.length();

        for (int i = 0; pos < end && i < count; i++) {
            // Skip over letter/digits
            while (pos < end && !isDelimiter(buf.buffer.charAt(pos))) {
                ++pos;
            }

            /*
             * Don't you love special cases? During delete-to and yank-to
             * operations the word movement is normal. However, during a
             * change-to, the trailing spaces behind the last word are
             * left in tact.
             */
            if (i < (count-1) || !(state == State.VI_CHANGE_TO)) {
                while (pos < end && isDelimiter(buf.buffer.charAt(pos))) {
                    ++pos;
                }
            }
        }

        setCursorPosition(pos);
        return true;
    }

    /**
     * Implements a close facsimile of the vi end-of-word movement.
     * If the character is on white space, it takes you to the end
     * of the next word.  If it is on the last character of a word
     * it takes you to the next of the next word.  Any other character
     * of a word, takes you to the end of the current word.
     *
     * @param count Number of times to repeat the action
     * @return true if it worked.
     */
    private boolean viEndWord(int count) throws IOException {
        int pos = buf.cursor;
        int end = buf.buffer.length();

        for (int i = 0; pos < end && i < count; i++) {
            if (pos < (end-1)
                    && !isDelimiter(buf.buffer.charAt(pos))
                    && isDelimiter(buf.buffer.charAt (pos+1))) {
                ++pos;
            }

            // If we are on white space, then move back.
            while (pos < end && isDelimiter(buf.buffer.charAt(pos))) {
                ++pos;
            }

            while (pos < (end-1) && !isDelimiter(buf.buffer.charAt(pos+1))) {
                ++pos;
            }
        }
        setCursorPosition(pos);
        return true;
    }

    private boolean previousWord() throws IOException {
        while (isDelimiter(buf.current()) && (moveCursor(-1) != 0)) {
            // nothing
        }

        while (!isDelimiter(buf.current()) && (moveCursor(-1) != 0)) {
            // nothing
        }

        return true;
    }

    private boolean nextWord() throws IOException {
        while (isDelimiter(buf.nextChar()) && (moveCursor(1) != 0)) {
            // nothing
        }

        while (!isDelimiter(buf.nextChar()) && (moveCursor(1) != 0)) {
            // nothing
        }

        return true;
    }

    /**
     * Deletes to the beginning of the word that the cursor is sitting on.
     * If the cursor is on white-space, it deletes that and to the beginning
     * of the word before it.  If the user is not on a word or whitespace
     * it deletes up to the end of the previous word.
     *
     * @param count Number of times to perform the operation
     * @return true if it worked, false if you tried to delete too many words
     */
    private boolean unixWordRubout(int count) throws IOException {
        boolean success = true;
        StringBuilder killed = new StringBuilder();

        for (; count > 0; --count) {
            if (buf.cursor == 0) {
                success = false;
                break;
            }

            while (isWhitespace(buf.current())) {
                char c = buf.current();
                if (c == 0) {
                    break;
                }

                killed.append(c);
                backspace();
            }

            while (!isWhitespace(buf.current())) {
                char c = buf.current();
                if (c == 0) {
                    break;
                }

                killed.append(c);
                backspace();
            }
        }

        String copy = killed.reverse().toString();
        killRing.addBackwards(copy);

        return success;
    }

    private String insertComment(boolean isViMode) throws IOException {
        String comment = this.getCommentBegin();
        setCursorPosition(0);
        putString(comment);
        if (isViMode) {
            consoleKeys.setKeyMap(KeyMap.VI_INSERT);
        }
        return accept();
    }

    /**
     * Implements vi search ("/" or "?").
     */
    @SuppressWarnings("fallthrough")
    private int viSearch(char searchChar) throws IOException {
        boolean isForward = (searchChar == '/');

        /*
         * This is a little gross, I'm sure there is a more appropriate way
         * of saving and restoring state.
         */
        CursorBuffer origBuffer = buf.copy();

        // Clear the contents of the current line and
        setCursorPosition (0);
        killLine();

        // Our new "prompt" is the character that got us into search mode.
        putString(Character.toString(searchChar));
        flush();

        boolean isAborted = false;
        boolean isComplete = false;

        /*
         * Readline doesn't seem to do any special character map handling
         * here, so I think we are safe.
         */
        int ch = -1;
        while (!isAborted && !isComplete && (ch = readCharacter()) != -1) {
            switch (ch) {
                case '\033':  // ESC
                    /*
                     * The ESC behavior doesn't appear to be readline behavior,
                     * but it is a little tweak of my own. I like it.
                     */
                    isAborted = true;
                    break;
                case '\010':  // Backspace
                case '\177':  // Delete
                    backspace();
                    /*
                     * Backspacing through the "prompt" aborts the search.
                     */
                    if (buf.cursor == 0) {
                        isAborted = true;
                    }
                    break;
                case '\012': // NL
                case '\015': // CR
                    isComplete = true;
                    break;
                default:
                    putString(Character.toString((char) ch));
            }

            flush();
        }

        // If we aborted, then put ourself at the end of the original buffer.
        if (ch == -1 || isAborted) {
            setCursorPosition(0);
            killLine();
            putString(origBuffer.buffer);
            setCursorPosition(origBuffer.cursor);
            return -1;
        }

        /*
         * The first character of the buffer was the search character itself
         * so we discard it.
         */
        String searchTerm = buf.buffer.substring(1);
        int idx = -1;

        /*
         * The semantics of the history thing is gross when you want to
         * explicitly iterate over entries (without an iterator) as size()
         * returns the actual number of entries in the list but get()
         * doesn't work the way you think.
         */
        int end   = history.index();
        int start = (end <= history.size()) ? 0 : end - history.size();

        if (isForward) {
            for (int i = start; i < end; i++) {
                if (history.get(i).toString().contains(searchTerm)) {
                    idx = i;
                    break;
                }
            }
        }
        else {
            for (int i = end-1; i >= start; i--) {
                if (history.get(i).toString().contains(searchTerm)) {
                    idx = i;
                    break;
                }
            }
        }

        /*
         * No match? Then restore what we were working on, but make sure
         * the cursor is at the beginning of the line.
         */
        if (idx == -1) {
            setCursorPosition(0);
            killLine();
            putString(origBuffer.buffer);
            setCursorPosition(0);
            return -1;
        }

        /*
         * Show the match.
         */
        setCursorPosition(0);
        killLine();
        putString(history.get(idx));
        setCursorPosition(0);
        flush();

        /*
         * While searching really only the "n" and "N" keys are interpreted
         * as movement, any other key is treated as if you are editing the
         * line with it, so we return it back up to the caller for interpretation.
         */
        isComplete = false;
        while (!isComplete && (ch = readCharacter()) != -1) {
            boolean forward = isForward;
            switch (ch) {
                case 'p': case 'P':
                    forward = !isForward;
                    // Fallthru
                case 'n': case 'N':
                    boolean isMatch = false;
                    if (forward) {
                        for (int i = idx+1; !isMatch && i < end; i++) {
                            if (history.get(i).toString().contains(searchTerm)) {
                                idx = i;
                                isMatch = true;
                            }
                        }
                    }
                    else {
                        for (int i = idx - 1; !isMatch && i >= start; i--) {
                            if (history.get(i).toString().contains(searchTerm)) {
                                idx = i;
                                isMatch = true;
                            }
                        }
                    }
                    if (isMatch) {
                        setCursorPosition(0);
                        killLine();
                        putString(history.get(idx));
                        setCursorPosition(0);
                    }
                    break;
                default:
                    isComplete = true;
            }
            flush();
        }

        /*
         * Complete?
         */
        return ch;
    }

    public void setParenBlinkTimeout(int timeout) {
        parenBlinkTimeout = timeout;
    }

    private void insertClose(String s) throws IOException {
        putString(s);
        int closePosition = buf.cursor;

        moveCursor(-1);
        viMatch();


        if (in.isNonBlockingEnabled()) {
            in.peek(parenBlinkTimeout);
        }

        setCursorPosition(closePosition);
        flush();
    }

    /**
     * Implements vi style bracket matching ("%" command). The matching
     * bracket for the current bracket type that you are sitting on is matched.
     * The logic works like so:
     * @return true if it worked, false if the cursor was not on a bracket
     *   character or if there was no matching bracket.
     */
    private boolean viMatch() throws IOException {
        int pos        = buf.cursor;

        if (pos == buf.length()) {
            return false;
        }

        int type       = getBracketType(buf.buffer.charAt (pos));
        int move       = (type < 0) ? -1 : 1;
        int count      = 1;

        if (type == 0)
            return false;

        while (count > 0) {
            pos += move;

            // Fell off the start or end.
            if (pos < 0 || pos >= buf.buffer.length ()) {
                return false;
            }

            int curType = getBracketType(buf.buffer.charAt (pos));
            if (curType == type) {
                ++count;
            }
            else if (curType == -type) {
                --count;
            }
        }

        /*
         * Slight adjustment for delete-to, yank-to, change-to to ensure
         * that the matching paren is consumed
         */
        if (move > 0 && isInViMoveOperationState())
            ++pos;

        setCursorPosition(pos);
        flush();
        return true;
    }

    /**
     * Given a character determines what type of bracket it is (paren,
     * square, curly, or none).
     * @param ch The character to check
     * @return 1 is square, 2 curly, 3 parent, or zero for none.  The value
     *   will be negated if it is the closing form of the bracket.
     */
    private static int getBracketType (char ch) {
        switch (ch) {
            case '[': return  1;
            case ']': return -1;
            case '{': return  2;
            case '}': return -2;
            case '(': return  3;
            case ')': return -3;
            default:
                return 0;
        }
    }

    private boolean deletePreviousWord() throws IOException {
        StringBuilder killed = new StringBuilder();
        char c;

        while (isDelimiter((c = buf.current()))) {
            if (c == 0) {
                break;
            }

            killed.append(c);
            backspace();
        }

        while (!isDelimiter((c = buf.current()))) {
            if (c == 0) {
                break;
            }

            killed.append(c);
            backspace();
        }

        String copy = killed.reverse().toString();
        killRing.addBackwards(copy);
        return true;
    }

    private boolean deleteNextWord() throws IOException {
        StringBuilder killed = new StringBuilder();
        char c;

        while (isDelimiter((c = buf.nextChar()))) {
            if (c == 0) {
                break;
            }
            killed.append(c);
            delete();
        }

        while (!isDelimiter((c = buf.nextChar()))) {
            if (c == 0) {
                break;
            }
            killed.append(c);
            delete();
        }

        String copy = killed.toString();
        killRing.add(copy);

        return true;
    }

    private boolean capitalizeWord() throws IOException {
        boolean first = true;
        int i = 1;
        char c;
        while (buf.cursor + i  - 1< buf.length() && !isDelimiter((c = buf.buffer.charAt(buf.cursor + i - 1)))) {
            buf.buffer.setCharAt(buf.cursor + i - 1, first ? Character.toUpperCase(c) : Character.toLowerCase(c));
            first = false;
            i++;
        }
        drawBuffer();
        moveCursor(i - 1);
        return true;
    }

    private boolean upCaseWord() throws IOException {
        int i = 1;
        char c;
        while (buf.cursor + i - 1 < buf.length() && !isDelimiter((c = buf.buffer.charAt(buf.cursor + i - 1)))) {
            buf.buffer.setCharAt(buf.cursor + i - 1, Character.toUpperCase(c));
            i++;
        }
        drawBuffer();
        moveCursor(i - 1);
        return true;
    }

    private boolean downCaseWord() throws IOException {
        int i = 1;
        char c;
        while (buf.cursor + i - 1 < buf.length() && !isDelimiter((c = buf.buffer.charAt(buf.cursor + i - 1)))) {
            buf.buffer.setCharAt(buf.cursor + i - 1, Character.toLowerCase(c));
            i++;
        }
        drawBuffer();
        moveCursor(i - 1);
        return true;
    }

    /**
     * Performs character transpose. The character prior to the cursor and the
     * character under the cursor are swapped and the cursor is advanced one
     * character unless you are already at the end of the line.
     *
     * @param count The number of times to perform the transpose
     * @return true if the operation succeeded, false otherwise (e.g. transpose
     *   cannot happen at the beginning of the line).
     */
    private boolean transposeChars(int count) throws IOException {
        for (; count > 0; --count) {
            if (buf.cursor == 0 || buf.cursor == buf.buffer.length()) {
                return false;
            }

            int first  = buf.cursor-1;
            int second = buf.cursor;

            char tmp = buf.buffer.charAt (first);
            buf.buffer.setCharAt(first, buf.buffer.charAt(second));
            buf.buffer.setCharAt(second, tmp);

            // This could be done more efficiently by only re-drawing at the end.
            moveInternal(-1);
            drawBuffer();
            moveInternal(2);
        }

        return true;
    }

    public boolean isKeyMap(String name) {
        // Current keymap.
        KeyMap map = consoleKeys.getKeys();
        KeyMap mapByName = consoleKeys.getKeyMaps().get(name);

        if (mapByName == null)
            return false;

        /*
         * This may not be safe to do, but there doesn't appear to be a
         * clean way to find this information out.
         */
        return map == mapByName;
    }


    /**
     * The equivalent of hitting &lt;RET&gt;.  The line is considered
     * complete and is returned.
     *
     * @return The completed line of text.
     */
    public String accept() throws IOException {
        moveToEnd();
        println(); // output newline
        flush();
        return finishBuffer();
    }

    private void abort() throws IOException {
        beep();
        buf.clear();
        println();
        redrawLine();
    }

    /**
     * Move the cursor <i>where</i> characters.
     *
     * @param num   If less than 0, move abs(<i>where</i>) to the left, otherwise move <i>where</i> to the right.
     * @return      The number of spaces we moved
     */
    public int moveCursor(final int num) throws IOException {
        int where = num;

        if ((buf.cursor == 0) && (where <= 0)) {
            return 0;
        }

        if ((buf.cursor == buf.buffer.length()) && (where >= 0)) {
            return 0;
        }

        if ((buf.cursor + where) < 0) {
            where = -buf.cursor;
        }
        else if ((buf.cursor + where) > buf.buffer.length()) {
            where = buf.buffer.length() - buf.cursor;
        }

        moveInternal(where);

        return where;
    }

    /**
     * Move the cursor <i>where</i> characters, without checking the current buffer.
     *
     * @param where the number of characters to move to the right or left.
     */
    private void moveInternal(final int where) throws IOException {
        // debug ("move cursor " + where + " ("
        // + buf.cursor + " => " + (buf.cursor + where) + ")");
        buf.cursor += where;

        int i0;
        int i1;
        if (mask == null) {
            if (where < 0) {
                i1 = promptLen + wcwidth(buf.buffer, 0, buf.cursor, promptLen);
                i0 = i1 + wcwidth(buf.buffer, buf.cursor, buf.cursor - where, i1);
            } else {
                i0 = promptLen + wcwidth(buf.buffer, 0, buf.cursor - where, promptLen);
                i1 = i0 + wcwidth(buf.buffer, buf.cursor - where, buf.cursor, i0);
            }
        } else if (mask != NULL_MASK) {
            i1 = promptLen + buf.cursor;
            i0 = i1 - where;
        } else {
            return;
        }
        moveCursorFromTo(i0, i1);
    }

    private void moveCursorFromTo(int i0, int i1) throws IOException {
        if (i0 == i1) return;
        int width = getTerminal().getWidth();
        int l0 = i0 / width;
        int c0 = i0 % width;
        int l1 = i1 / width;
        int c1 = i1 % width;
        if (l0 == l1 + 1) {
            if (!tputs("cursor_up")) {
                tputs("parm_up_cursor", 1);
            }
        } else if (l0 > l1) {
            if (!tputs("parm_up_cursor", l0 - l1)) {
                for (int i = l1; i < l0; i++) {
                    tputs("cursor_up");
                }
            }
        } else if (l0 < l1) {
            tputs("carriage_return");
            rawPrint('\n', l1 - l0);
            c0 = 0;
        }
        if (c0 == c1 - 1) {
            tputs("cursor_right");
        } else if (c0 == c1 + 1) {
            tputs("cursor_left");
        } else if (c0 < c1) {
            if (!tputs("parm_right_cursor", c1 - c0)) {
                for (int i = c0; i < c1; i++) {
                    tputs("cursor_right");
                }
            }
        } else if (c0 > c1) {
            if (!tputs("parm_left_cursor", c0 - c1)) {
                for (int i = c1; i < c0; i++) {
                    tputs("cursor_left");
                }
            }
        }
        cursorOk = true;
    }

    /**
     * Read a character from the console.
     *
     * @return the character, or -1 if an EOF is received.
     */
    public int readCharacter() throws IOException {
      return readCharacter(false);
    }

    /**
     * Read a character from the console.  If boolean parameter is "true", it will check whether the keystroke was an "alt-" key combination, and
     * if so add 1000 to the value returned.  Better way...?
     *
     * @return the character, or -1 if an EOF is received.
     */
    public int readCharacter(boolean checkForAltKeyCombo) throws IOException {
        int c = reader.read();
        if (c >= 0) {
            Log.trace("Keystroke: ", c);
            // clear any echo characters
            if (terminal.isSupported()) {
                clearEcho(c);
            }
            if (c == ESCAPE && checkForAltKeyCombo && in.peek(escapeTimeout) >= 32) {
              /* When ESC is encountered and there is a pending
               * character in the pushback queue, then it seems to be
               * an Alt-[key] combination.  Is this true, cross-platform?
               * It's working for me on Debian GNU/Linux at the moment anyway.
               * I removed the "isNonBlockingEnabled" check, though it was
               * in the similar code in "readLine(String prompt, final Character mask)" (way down),
               * as I am not sure / didn't look up what it's about, and things are working so far w/o it.
               */
              int next = reader.read();
              // with research, there's probably a much cleaner way to do this, but, this is now it flags an Alt key combination for now:
              next = next + 1000;
              return next;
            }
        }
        return c;
    }

    /**
     * Clear the echoed characters for the specified character code.
     */
    private int clearEcho(final int c) throws IOException {
        // if the terminal is not echoing, then ignore
        if (!terminal.isEchoEnabled()) {
            return 0;
        }

        // otherwise, clear
        int pos = getCursorPosition();
        int num = wcwidth(c, pos);
        moveCursorFromTo(pos + num, pos);
        drawBuffer(num);

        return num;
    }

    public int readCharacter(final char... allowed) throws IOException {
      return readCharacter(false, allowed);
    }

    public int readCharacter(boolean checkForAltKeyCombo, final char... allowed) throws IOException {
        // if we restrict to a limited set and the current character is not in the set, then try again.
        char c;

        Arrays.sort(allowed); // always need to sort before binarySearch

        while (Arrays.binarySearch(allowed, c = (char) readCharacter(checkForAltKeyCombo)) < 0) {
            // nothing
        }

        return c;
    }

    /**
     * Read from the input stream and decode an operation from the key map.
     *
     * The input stream will be read character by character until a matching
     * binding can be found.  Characters that can't possibly be matched to
     * any binding will be discarded.
     *
     * @param keys the KeyMap to use for decoding the input stream
     * @return the decoded binding or <code>null</code> if the end of
     *         stream has been reached
     */
    public Object readBinding(KeyMap keys) throws IOException {
        Object o;
        opBuffer.setLength(0);
        do {
            int c = pushBackChar.isEmpty() ? readCharacter() : pushBackChar.pop();
            if (c == -1) {
                return null;
            }
            opBuffer.appendCodePoint(c);

            if (recording) {
                macro += new String(Character.toChars(c));
            }

            if (quotedInsert) {
                o = Operation.SELF_INSERT;
                quotedInsert = false;
            } else {
                o = keys.getBound(opBuffer);
            }

            /*
             * The kill ring keeps record of whether or not the
             * previous command was a yank or a kill. We reset
             * that state here if needed.
             */
            if (!recording && !(o instanceof KeyMap)) {
                if (o != Operation.YANK_POP && o != Operation.YANK) {
                    killRing.resetLastYank();
                }
                if (o != Operation.KILL_LINE && o != Operation.KILL_WHOLE_LINE
                        && o != Operation.BACKWARD_KILL_WORD && o != Operation.KILL_WORD
                        && o != Operation.UNIX_LINE_DISCARD && o != Operation.UNIX_WORD_RUBOUT) {
                    killRing.resetLastKill();
                }
            }

            if (o == Operation.DO_LOWERCASE_VERSION) {
                opBuffer.setLength(opBuffer.length() - 1);
                opBuffer.append(Character.toLowerCase((char) c));
                o = keys.getBound(opBuffer);
            }

            /*
             * A KeyMap indicates that the key that was struck has a
             * number of keys that can follow it as indicated in the
             * map. This is used primarily for Emacs style ESC-META-x
             * lookups. Since more keys must follow, go back to waiting
             * for the next key.
             */
            if (o instanceof KeyMap) {
                /*
                 * The ESC key (#27) is special in that it is ambiguous until
                 * you know what is coming next.  The ESC could be a literal
                 * escape, like the user entering vi-move mode, or it could
                 * be part of a terminal control sequence.  The following
                 * logic attempts to disambiguate things in the same
                 * fashion as regular vi or readline.
                 *
                 * When ESC is encountered and there is no other pending
                 * character in the pushback queue, then attempt to peek
                 * into the input stream (if the feature is enabled) for
                 * 150ms. If nothing else is coming, then assume it is
                 * not a terminal control sequence, but a raw escape.
                 */
                if (c == ESCAPE
                        && pushBackChar.isEmpty()
                        && in.isNonBlockingEnabled()
                        && in.peek(escapeTimeout) == READ_EXPIRED) {
                    o = ((KeyMap) o).getAnotherKey();
                    if (o == null || o instanceof KeyMap) {
                        continue;
                    }
                    opBuffer.setLength(0);
                } else {
                    continue;
                }
            }

            /*
             * If we didn't find a binding for the key and there is
             * more than one character accumulated then start checking
             * the largest span of characters from the beginning to
             * see if there is a binding for them.
             *
             * For example if our buffer has ESC,CTRL-M,C the getBound()
             * called previously indicated that there is no binding for
             * this sequence, so this then checks ESC,CTRL-M, and failing
             * that, just ESC. Each keystroke that is pealed off the end
             * during these tests is stuffed onto the pushback buffer so
             * they won't be lost.
             *
             * If there is no binding found, then we go back to waiting for
             * input.
             */
            while (o == null && opBuffer.length() > 0) {
                c = opBuffer.charAt(opBuffer.length() - 1);
                opBuffer.setLength(opBuffer.length() - 1);
                Object o2 = keys.getBound(opBuffer);
                if (o2 instanceof KeyMap) {
                    o = ((KeyMap) o2).getAnotherKey();
                    if (o == null) {
                        continue;
                    } else {
                        pushBackChar.push((char) c);
                    }
                }
            }

        } while (o == null || o instanceof KeyMap);

        return o;
    }

    public String getLastBinding() {
        return opBuffer.toString();
    }

    //
    // Key Bindings
    //

    public static final String JLINE_COMPLETION_THRESHOLD = "jline.completion.threshold";

    //
    // Line Reading
    //

    /**
     * Read the next line and return the contents of the buffer.
     */
    public String readLine() throws IOException {
        return readLine((String) null);
    }

    /**
     * Read the next line with the specified character mask. If null, then
     * characters will be echoed. If 0, then no characters will be echoed.
     */
    public String readLine(final Character mask) throws IOException {
        return readLine(null, mask);
    }

    public String readLine(final String prompt) throws IOException {
        return readLine(prompt, null);
    }

    /**
     * Read a line from the <i>in</i> {@link InputStream}, and return the line
     * (without any trailing newlines).
     *
     * @param prompt    The prompt to issue to the console, may be null.
     * @return          A line that is read from the terminal, or null if there was null input (e.g., <i>CTRL-D</i>
     *                  was pressed).
     */
    public String readLine(String prompt, final Character mask) throws IOException {
        return readLine(prompt, mask, null);
    }

    /**
     * Sets the current keymap by name. Supported keymaps are "emacs",
     * "vi-insert", "vi-move".
     * @param name The name of the keymap to switch to
     * @return true if the keymap was set, or false if the keymap is
     *    not recognized.
     */
    public boolean setKeyMap(String name) {
        return consoleKeys.setKeyMap(name);
    }

    /**
     * Returns the name of the current key mapping.
     * @return the name of the key mapping. This will be the canonical name
     *   of the current mode of the key map and may not reflect the name that
     *   was used with {@link #setKeyMap(String)}.
     */
    public String getKeyMap() {
        return consoleKeys.getKeys().getName();
    }

    /**
     * Read a line from the <i>in</i> {@link InputStream}, and return the line
     * (without any trailing newlines).
     *
     * @param prompt    The prompt to issue to the console, may be null.
     * @return          A line that is read from the terminal, or null if there was null input (e.g., <i>CTRL-D</i>
     *                  was pressed).
     */
    public String readLine(String prompt, final Character mask, String buffer) throws IOException {
        // prompt may be null
        // mask may be null
        // buffer may be null

        /*
         * This is the accumulator for VI-mode repeat count. That is, while in
         * move mode, if you type 30x it will delete 30 characters. This is
         * where the "30" is accumulated until the command is struck.
         */
        int repeatCount = 0;

        // FIXME: This blows, each call to readLine will reset the console's state which doesn't seem very nice.
        this.mask = mask != null ? mask : this.echoCharacter;
        if (prompt != null) {
            setPrompt(prompt);
        }
        else {
            prompt = getPrompt();
        }

        try {
            if (buffer != null) {
                buf.write(buffer);
            }

            if (!terminal.isSupported()) {
                beforeReadLine(prompt, mask);
            }

            if (buffer != null && buffer.length() > 0
                    || prompt != null && prompt.length() > 0) {
                drawLine();
                out.flush();
            }

            if (terminal.isAnsiSupported() && System.console() != null) {
                //detect the prompt length by reading the cursor position from the terminal
                //the real prompt length could differ from the simple prompt length due to
                //use of escape sequences:
                out.write("\033[6n");
                out.flush();
                StringBuilder input = new StringBuilder();
                while (true) {
                    int read;
                    while ((read = in.read()) != 'R') {
                        input.appendCodePoint(read);
                    }
                    input.appendCodePoint(read);
                    Matcher m = CURSOR_COLUMN_PATTERN.matcher(input);
                    if (m.matches()) {
                        promptLen = Integer.parseInt(m.group("column")) - 1;
                        String prefix = m.group("prefix");
                        List<Character> chars = new ArrayList<>();
                        for (int i = prefix.length() - 1; i >= 0; i--) {
                            chars.add(prefix.charAt(i));
                        }
                        pushBackChar.addAll(0, chars);
                        break;
                    }
                }
            }

            // if the terminal is unsupported, just use plain-java reading
            if (!terminal.isSupported()) {
                return readLineSimple();
            }

            if (handleUserInterrupt) {
                terminal.disableInterruptCharacter();
            }
            if (handleLitteralNext && (terminal instanceof UnixTerminal)) {
                ((UnixTerminal) terminal).disableLitteralNextCharacter();
            }

            String originalPrompt = this.prompt;

            state = State.NORMAL;

            boolean success = true;

            while (true) {

                Object o = readBinding(getKeys());
                if (o == null) {
                    return null;
                }
                int c = 0;
                if (opBuffer.length() > 0) {
                    c = opBuffer.codePointBefore(opBuffer.length());
                }
                Log.trace("Binding: ", o);


                // Handle macros
                if (o instanceof String) {
                    String macro = (String) o;
                    for (int i = 0; i < macro.length(); i++) {
                        pushBackChar.push(macro.charAt(macro.length() - 1 - i));
                    }
                    opBuffer.setLength(0);
                    continue;
                }

                // Handle custom callbacks
                //original code:
//                if (o instanceof ActionListener) {
//                    ((ActionListener) o).actionPerformed(null);
//                    sb.setLength( 0 );
//                    continue;
//                }
                //using reflection to avoid dependency on java.desktop:
                try {
                    Class<?> actionListener =
                            Class.forName("java.awt.event.ActionListener", false, ClassLoader.getSystemClassLoader());
                    Class<?> actionEvent =
                            Class.forName("java.awt.event.ActionEvent", false, ClassLoader.getSystemClassLoader());
                    if (actionListener.isAssignableFrom(o.getClass())) {
                        Method actionPerformed =
                                actionListener.getMethod("actionPerformed", actionEvent);
                        try {
                            actionPerformed.invoke(o, (Object) null);
                        } catch (InvocationTargetException ex ) {
                            Log.error("Exception while running registered action", ex);
                        }
                        opBuffer.setLength(0);
                        continue;
                    }
                } catch (ReflectiveOperationException ex) {
                    //ignore
                }

                if (o instanceof Runnable) {
                    ((Runnable) o).run();
                    opBuffer.setLength(0);
                    continue;
                }

                CursorBuffer oldBuf = new CursorBuffer();
                oldBuf.buffer.append(buf.buffer);
                oldBuf.cursor = buf.cursor;

                // Search mode.
                //
                // Note that we have to do this first, because if there is a command
                // not linked to a search command, we leave the search mode and fall
                // through to the normal state.
                if (state == State.SEARCH || state == State.FORWARD_SEARCH) {
                    int cursorDest = -1;
                    // TODO: check the isearch-terminators variable terminating the search
                    switch ( ((Operation) o )) {
                        case ABORT:
                            state = State.NORMAL;
                            buf.clear();
                            buf.write(originalBuffer.buffer);
                            buf.cursor = originalBuffer.cursor;
                            break;

                        case REVERSE_SEARCH_HISTORY:
                            state = State.SEARCH;
                            if (searchTerm.length() == 0) {
                                searchTerm.append(previousSearchTerm);
                            }

                            if (searchIndex > 0) {
                                searchIndex = searchBackwards(searchTerm.toString(), searchIndex);
                            }
                            break;

                        case FORWARD_SEARCH_HISTORY:
                            state = State.FORWARD_SEARCH;
                            if (searchTerm.length() == 0) {
                                searchTerm.append(previousSearchTerm);
                            }

                            if (searchIndex > -1 && searchIndex < history.size() - 1) {
                                searchIndex = searchForwards(searchTerm.toString(), searchIndex);
                            }
                            break;

                        case BACKWARD_DELETE_CHAR:
                            if (searchTerm.length() > 0) {
                                searchTerm.deleteCharAt(searchTerm.length() - 1);
                                if (state == State.SEARCH) {
                                    searchIndex = searchBackwards(searchTerm.toString());
                                } else {
                                    searchIndex = searchForwards(searchTerm.toString());
                                }
                            }
                            break;

                        case SELF_INSERT:
                            searchTerm.appendCodePoint(c);
                            if (state == State.SEARCH) {
                                searchIndex = searchBackwards(searchTerm.toString());
                            } else {
                                searchIndex = searchForwards(searchTerm.toString());
                            }
                            break;

                        default:
                            // Set buffer and cursor position to the found string.
                            if (searchIndex != -1) {
                                history.moveTo(searchIndex);
                                // set cursor position to the found string
                                cursorDest = history.current().toString().indexOf(searchTerm.toString());
                            }
                            if (o != Operation.ACCEPT_LINE) {
                                o = null;
                            }
                            state = State.NORMAL;
                            break;
                    }

                    // if we're still in search mode, print the search status
                    if (state == State.SEARCH || state == State.FORWARD_SEARCH) {
                        if (searchTerm.length() == 0) {
                            if (state == State.SEARCH) {
                                printSearchStatus("", "");
                            } else {
                                printForwardSearchStatus("", "");
                            }
                            searchIndex = -1;
                        } else {
                            if (searchIndex == -1) {
                                beep();
                                printSearchStatus(searchTerm.toString(), "");
                            } else if (state == State.SEARCH) {
                                printSearchStatus(searchTerm.toString(), history.get(searchIndex).toString());
                            } else {
                                printForwardSearchStatus(searchTerm.toString(), history.get(searchIndex).toString());
                            }
                        }
                    }
                    // otherwise, restore the line
                    else {
                        restoreLine(originalPrompt, cursorDest);
                    }
                }
                if (state != State.SEARCH && state != State.FORWARD_SEARCH) {
                    /*
                     * If this is still false at the end of the switch, then
                     * we reset our repeatCount to 0.
                     */
                    boolean isArgDigit = false;

                    /*
                     * Every command that can be repeated a specified number
                     * of times, needs to know how many times to repeat, so
                     * we figure that out here.
                     */
                    int count = (repeatCount == 0) ? 1 : repeatCount;

                    /*
                     * Default success to true. You only need to explicitly
                     * set it if something goes wrong.
                     */
                    success = true;

                    if (o instanceof Operation) {
                        Operation op = (Operation)o;
                        /*
                         * Current location of the cursor (prior to the operation).
                         * These are used by vi *-to operation (e.g. delete-to)
                         * so we know where we came from.
                         */
                        int     cursorStart = buf.cursor;
                        State   origState   = state;

                        /*
                         * If we are on a "vi" movement based operation, then we
                         * need to restrict the sets of inputs pretty heavily.
                         */
                        if (state == State.VI_CHANGE_TO
                            || state == State.VI_YANK_TO
                            || state == State.VI_DELETE_TO) {

                            op = viDeleteChangeYankToRemap(op);
                        }

                        switch ( op ) {
                            case COMPLETE: // tab
                                // There is an annoyance with tab completion in that
                                // sometimes the user is actually pasting input in that
                                // has physical tabs in it.  This attempts to look at how
                                // quickly a character follows the tab, if the character
                                // follows *immediately*, we assume it is a tab literal.
                                boolean isTabLiteral = false;
                                if (copyPasteDetection
                                    && c == 9
                                    && (!pushBackChar.isEmpty()
                                        || (in.isNonBlockingEnabled() && in.peek(escapeTimeout) != -2))) {
                                    isTabLiteral = true;
                                }

                                if (! isTabLiteral) {
                                    success = complete();
                                }
                                else {
                                    putString(opBuffer);
                                }
                                break;

                            case POSSIBLE_COMPLETIONS:
                                printCompletionCandidates();
                                break;

                            case BEGINNING_OF_LINE:
                                success = setCursorPosition(0);
                                break;

                            case YANK:
                                success = yank();
                                break;

                            case YANK_POP:
                                success = yankPop();
                                break;

                            case KILL_LINE: // CTRL-K
                                success = killLine();
                                break;

                            case KILL_WHOLE_LINE:
                                success = setCursorPosition(0) && killLine();
                                break;

                            case CLEAR_SCREEN: // CTRL-L
                                success = clearScreen();
                                redrawLine();
                                break;

                            case OVERWRITE_MODE:
                                buf.setOverTyping(!buf.isOverTyping());
                                break;

                            case SELF_INSERT:
                                putString(opBuffer);
                                break;

                            case ACCEPT_LINE:
                                return accept();

                            case ABORT:
                                if (searchTerm == null) {
                                    abort();
                                }
                                break;

                            case INTERRUPT:
                                if (handleUserInterrupt) {
                                    println();
                                    flush();
                                    String partialLine = buf.buffer.toString();
                                    buf.clear();
                                    history.moveToEnd();
                                    throw new UserInterruptException(partialLine);
                                }
                                break;

                            /*
                             * VI_MOVE_ACCEPT_LINE is the result of an ENTER
                             * while in move mode. This is the same as a normal
                             * ACCEPT_LINE, except that we need to enter
                             * insert mode as well.
                             */
                            case VI_MOVE_ACCEPT_LINE:
                                consoleKeys.setKeyMap(KeyMap.VI_INSERT);
                                return accept();

                            case BACKWARD_WORD:
                                success = previousWord();
                                break;

                            case FORWARD_WORD:
                                success = nextWord();
                                break;

                            case PREVIOUS_HISTORY:
                                success = moveHistory(false);
                                break;

                            /*
                             * According to bash/readline move through history
                             * in "vi" mode will move the cursor to the
                             * start of the line. If there is no previous
                             * history, then the cursor doesn't move.
                             */
                            case VI_PREVIOUS_HISTORY:
                                success = moveHistory(false, count)
                                    && setCursorPosition(0);
                                break;

                            case NEXT_HISTORY:
                                success = moveHistory(true);
                                break;

                            /*
                             * According to bash/readline move through history
                             * in "vi" mode will move the cursor to the
                             * start of the line. If there is no next history,
                             * then the cursor doesn't move.
                             */
                            case VI_NEXT_HISTORY:
                                success = moveHistory(true, count)
                                    && setCursorPosition(0);
                                break;

                            case BACKWARD_DELETE_CHAR: // backspace
                                success = backspace();
                                break;

                            case EXIT_OR_DELETE_CHAR:
                                if (buf.buffer.length() == 0) {
                                    return null;
                                }
                                success = deleteCurrentCharacter();
                                break;

                            case DELETE_CHAR: // delete
                                success = deleteCurrentCharacter();
                                break;

                            case BACKWARD_CHAR:
                                success = moveCursor(-(count)) != 0;
                                break;

                            case FORWARD_CHAR:
                                success = moveCursor(count) != 0;
                                break;

                            case UNIX_LINE_DISCARD:
                                success = resetLine();
                                break;

                            case UNIX_WORD_RUBOUT:
                                success = unixWordRubout(count);
                                break;

                            case BACKWARD_KILL_WORD:
                                success = deletePreviousWord();
                                break;

                            case KILL_WORD:
                                success = deleteNextWord();
                                break;

                            case BEGINNING_OF_HISTORY:
                                success = history.moveToFirst();
                                if (success) {
                                    setBuffer(history.current());
                                }
                                break;

                            case END_OF_HISTORY:
                                success = history.moveToLast();
                                if (success) {
                                    setBuffer(history.current());
                                }
                                break;

                            case HISTORY_SEARCH_BACKWARD:
                                searchTerm = new StringBuffer(buf.upToCursor());
                                searchIndex = searchBackwards(searchTerm.toString(), history.index(), true);

                                if (searchIndex == -1) {
                                    beep();
                                } else {
                                    // Maintain cursor position while searching.
                                    success = history.moveTo(searchIndex);
                                    if (success) {
                                        setBufferKeepPos(history.current());
                                    }
                                }
                                break;

                            case HISTORY_SEARCH_FORWARD:
                                searchTerm = new StringBuffer(buf.upToCursor());
                                int index = history.index() + 1;

                                if (index == history.size()) {
                                    history.moveToEnd();
                                    setBufferKeepPos(searchTerm.toString());
                                } else if (index < history.size()) {
                                    searchIndex = searchForwards(searchTerm.toString(), index, true);
                                    if (searchIndex == -1) {
                                        beep();
                                    } else {
                                        // Maintain cursor position while searching.
                                        success = history.moveTo(searchIndex);
                                        if (success) {
                                            setBufferKeepPos(history.current());
                                        }
                                    }
                                }
                                break;

                            case REVERSE_SEARCH_HISTORY:
                                originalBuffer = new CursorBuffer();
                                originalBuffer.write(buf.buffer);
                                originalBuffer.cursor = buf.cursor;
                                if (searchTerm != null) {
                                    previousSearchTerm = searchTerm.toString();
                                }
                                searchTerm = new StringBuffer(buf.buffer);
                                state = State.SEARCH;
                                if (searchTerm.length() > 0) {
                                    searchIndex = searchBackwards(searchTerm.toString());
                                    if (searchIndex == -1) {
                                        beep();
                                    }
                                    printSearchStatus(searchTerm.toString(),
                                            searchIndex > -1 ? history.get(searchIndex).toString() : "");
                                } else {
                                    searchIndex = -1;
                                    printSearchStatus("", "");
                                }
                                break;

                            case FORWARD_SEARCH_HISTORY:
                                originalBuffer = new CursorBuffer();
                                originalBuffer.write(buf.buffer);
                                originalBuffer.cursor = buf.cursor;
                                if (searchTerm != null) {
                                    previousSearchTerm = searchTerm.toString();
                                }
                                searchTerm = new StringBuffer(buf.buffer);
                                state = State.FORWARD_SEARCH;
                                if (searchTerm.length() > 0) {
                                    searchIndex = searchForwards(searchTerm.toString());
                                    if (searchIndex == -1) {
                                        beep();
                                    }
                                    printForwardSearchStatus(searchTerm.toString(),
                                            searchIndex > -1 ? history.get(searchIndex).toString() : "");
                                } else {
                                    searchIndex = -1;
                                    printForwardSearchStatus("", "");
                                }
                                break;

                            case CAPITALIZE_WORD:
                                success = capitalizeWord();
                                break;

                            case UPCASE_WORD:
                                success = upCaseWord();
                                break;

                            case DOWNCASE_WORD:
                                success = downCaseWord();
                                break;

                            case END_OF_LINE:
                                success = moveToEnd();
                                break;

                            case TAB_INSERT:
                                putString( "\t" );
                                break;

                            case RE_READ_INIT_FILE:
                                consoleKeys.loadKeys(appName, inputrcUrl);
                                break;

                            case START_KBD_MACRO:
                                recording = true;
                                break;

                            case END_KBD_MACRO:
                                recording = false;
                                macro = macro.substring(0, macro.length() - opBuffer.length());
                                break;

                            case CALL_LAST_KBD_MACRO:
                                for (int i = 0; i < macro.length(); i++) {
                                    pushBackChar.push(macro.charAt(macro.length() - 1 - i));
                                }
                                opBuffer.setLength(0);
                                break;

                            case VI_EDITING_MODE:
                                consoleKeys.setKeyMap(KeyMap.VI_INSERT);
                                break;

                            case VI_MOVEMENT_MODE:
                                /*
                                 * If we are re-entering move mode from an
                                 * aborted yank-to, delete-to, change-to then
                                 * don't move the cursor back. The cursor is
                                 * only move on an expclit entry to movement
                                 * mode.
                                 */
                                if (state == State.NORMAL) {
                                    moveCursor(-1);
                                }
                                consoleKeys.setKeyMap(KeyMap.VI_MOVE);
                                break;

                            case VI_INSERTION_MODE:
                                consoleKeys.setKeyMap(KeyMap.VI_INSERT);
                                break;

                            case VI_APPEND_MODE:
                                moveCursor(1);
                                consoleKeys.setKeyMap(KeyMap.VI_INSERT);
                                break;

                            case VI_APPEND_EOL:
                                success = moveToEnd();
                                consoleKeys.setKeyMap(KeyMap.VI_INSERT);
                                break;

                            /*
                             * Handler for CTRL-D. Attempts to follow readline
                             * behavior. If the line is empty, then it is an EOF
                             * otherwise it is as if the user hit enter.
                             */
                            case VI_EOF_MAYBE:
                                if (buf.buffer.length() == 0) {
                                    return null;
                                }
                                return accept();

                            case TRANSPOSE_CHARS:
                                success = transposeChars(count);
                                break;

                            case INSERT_COMMENT:
                                return insertComment (false);

                            case INSERT_CLOSE_CURLY:
                                insertClose("}");
                                break;

                            case INSERT_CLOSE_PAREN:
                                insertClose(")");
                                break;

                            case INSERT_CLOSE_SQUARE:
                                insertClose("]");
                                break;

                            case VI_INSERT_COMMENT:
                                return insertComment (true);

                            case VI_MATCH:
                                success = viMatch ();
                                break;

                            case VI_SEARCH:
                                int lastChar = viSearch(opBuffer.charAt(0));
                                if (lastChar != -1) {
                                    pushBackChar.push((char)lastChar);
                                }
                                break;

                            case VI_ARG_DIGIT:
                                repeatCount = (repeatCount * 10) + opBuffer.charAt(0) - '0';
                                isArgDigit = true;
                                break;

                            case VI_BEGINNING_OF_LINE_OR_ARG_DIGIT:
                                if (repeatCount > 0) {
                                    repeatCount = (repeatCount * 10) + opBuffer.charAt(0) - '0';
                                    isArgDigit = true;
                                }
                                else {
                                    success = setCursorPosition(0);
                                }
                                break;

                            case VI_FIRST_PRINT:
                                success = setCursorPosition(0) && viNextWord(1);
                                break;

                            case VI_PREV_WORD:
                                success = viPreviousWord(count);
                                break;

                            case VI_NEXT_WORD:
                                success = viNextWord(count);
                                break;

                            case VI_END_WORD:
                                success = viEndWord(count);
                                break;

                            case VI_INSERT_BEG:
                                success = setCursorPosition(0);
                                consoleKeys.setKeyMap(KeyMap.VI_INSERT);
                                break;

                            case VI_RUBOUT:
                                success = viRubout(count);
                                break;

                            case VI_DELETE:
                                success = viDelete(count);
                                break;

                            case VI_DELETE_TO:
                                /*
                                 * This is a weird special case. In vi
                                 * "dd" deletes the current line. So if we
                                 * get a delete-to, followed by a delete-to,
                                 * we delete the line.
                                 */
                                if (state == State.VI_DELETE_TO) {
                                    success = setCursorPosition(0) && killLine();
                                    state = origState = State.NORMAL;
                                }
                                else {
                                    state = State.VI_DELETE_TO;
                                }
                                break;

                            case VI_YANK_TO:
                                // Similar to delete-to, a "yy" yanks the whole line.
                                if (state == State.VI_YANK_TO) {
                                    yankBuffer = buf.buffer.toString();
                                    state = origState = State.NORMAL;
                                }
                                else {
                                    state = State.VI_YANK_TO;
                                }
                                break;

                            case VI_CHANGE_TO:
                                if (state == State.VI_CHANGE_TO) {
                                    success = setCursorPosition(0) && killLine();
                                    state = origState = State.NORMAL;
                                    consoleKeys.setKeyMap(KeyMap.VI_INSERT);
                                }
                                else {
                                    state = State.VI_CHANGE_TO;
                                }
                                break;

                            case VI_KILL_WHOLE_LINE:
                                success = setCursorPosition(0) && killLine();
                                consoleKeys.setKeyMap(KeyMap.VI_INSERT);
                                break;

                            case VI_PUT:
                                success = viPut(count);
                                break;

                            case VI_CHAR_SEARCH: {
                                 // ';' and ',' don't need another character. They indicate repeat next or repeat prev.
                                int searchChar = (c != ';' && c != ',')
                                    ? (pushBackChar.isEmpty()
                                        ? readCharacter()
                                        : pushBackChar.pop ())
                                    : 0;

                                    success = viCharSearch(count, c, searchChar);
                                }
                                break;

                            case VI_CHANGE_CASE:
                                success = viChangeCase(count);
                                break;

                            case VI_CHANGE_CHAR:
                                success = viChangeChar(count,
                                    pushBackChar.isEmpty()
                                        ? readCharacter()
                                        : pushBackChar.pop());
                                break;

                            case VI_DELETE_TO_EOL:
                                success = viDeleteTo(buf.cursor, buf.buffer.length(), false);
                                break;

                            case VI_CHANGE_TO_EOL:
                                success = viDeleteTo(buf.cursor, buf.buffer.length(), true);
                                consoleKeys.setKeyMap(KeyMap.VI_INSERT);
                                break;

                            case EMACS_EDITING_MODE:
                                consoleKeys.setKeyMap(KeyMap.EMACS);
                                break;

                            case QUIT:
                                getCursorBuffer().clear();
                                return accept();

                            case QUOTED_INSERT:
                                quotedInsert = true;
                                break;

                            case PASTE_FROM_CLIPBOARD:
//                                paste();
                                break;

                            default:
                                break;
                        }

                        /*
                         * If we were in a yank-to, delete-to, move-to
                         * when this operation started, then fall back to
                         */
                        if (origState != State.NORMAL) {
                            if (origState == State.VI_DELETE_TO) {
                                success = viDeleteTo(cursorStart, buf.cursor, false);
                            }
                            else if (origState == State.VI_CHANGE_TO) {
                                success = viDeleteTo(cursorStart, buf.cursor, true);
                                consoleKeys.setKeyMap(KeyMap.VI_INSERT);
                            }
                            else if (origState == State.VI_YANK_TO) {
                                success = viYankTo(cursorStart, buf.cursor);
                            }
                            state = State.NORMAL;
                        }

                        /*
                         * Another subtly. The check for the NORMAL state is
                         * to ensure that we do not clear out the repeat
                         * count when in delete-to, yank-to, or move-to modes.
                         */
                        if (state == State.NORMAL && !isArgDigit) {
                            /*
                             * If the operation performed wasn't a vi argument
                             * digit, then clear out the current repeatCount;
                             */
                            repeatCount = 0;
                        }

                        if (state != State.SEARCH && state != State.FORWARD_SEARCH) {
                            originalBuffer = null;
                            previousSearchTerm = "";
                            searchTerm = null;
                            searchIndex = -1;
                        }
                    }
                }
                if (!success) {
                    beep();
                }
                opBuffer.setLength(0);

                flush();
            }
        }
        finally {
            if (!terminal.isSupported()) {
                afterReadLine();
            }
            if (handleUserInterrupt) {
                terminal.enableInterruptCharacter();
            }
        }
    }
    //where:
        private Pattern CURSOR_COLUMN_PATTERN =
                Pattern.compile("(?<prefix>.*)\033\\[[0-9]+;(?<column>[0-9]+)R", Pattern.DOTALL);

    /**
     * Read a line for unsupported terminals.
     */
    private String readLineSimple() throws IOException {

        if (skipLF) {
            skipLF = false;

            int i = readCharacter();

            if (i == -1 || i == '\r') {
                return finishBuffer();
            } else if (i == '\n') {
                // ignore
            } else {
                buf.buffer.append((char) i);
            }
        }

        while (true) {
            int i = readCharacter();

            if (i == -1 && buf.buffer.length() == 0) {
              return null;
            }

            if (i == -1 || i == '\n') {
                return finishBuffer();
            } else if (i == '\r') {
                skipLF = true;
                return finishBuffer();
            } else {
                buf.buffer.append((char) i);
            }
        }
    }

    //
    // Completion
    //

    private final List<Completer> completers = new LinkedList<Completer>();

    private CompletionHandler completionHandler = new CandidateListCompletionHandler();

    /**
     * Add the specified {@link jline.console.completer.Completer} to the list of handlers for tab-completion.
     *
     * @param completer the {@link jline.console.completer.Completer} to add
     * @return true if it was successfully added
     */
    public boolean addCompleter(final Completer completer) {
        return completers.add(completer);
    }

    /**
     * Remove the specified {@link jline.console.completer.Completer} from the list of handlers for tab-completion.
     *
     * @param completer     The {@link Completer} to remove
     * @return              True if it was successfully removed
     */
    public boolean removeCompleter(final Completer completer) {
        return completers.remove(completer);
    }

    /**
     * Returns an unmodifiable list of all the completers.
     */
    public Collection<Completer> getCompleters() {
        return Collections.unmodifiableList(completers);
    }

    public void setCompletionHandler(final CompletionHandler handler) {
        this.completionHandler = checkNotNull(handler);
    }

    public CompletionHandler getCompletionHandler() {
        return this.completionHandler;
    }

    /**
     * Use the completers to modify the buffer with the appropriate completions.
     *
     * @return true if successful
     */
    protected boolean complete() throws IOException {
        // debug ("tab for (" + buf + ")");
        if (completers.size() == 0) {
            return false;
        }

        List<CharSequence> candidates = new LinkedList<CharSequence>();
        String bufstr = buf.buffer.toString();
        int cursor = buf.cursor;

        int position = -1;

        for (Completer comp : completers) {
            if ((position = comp.complete(bufstr, cursor, candidates)) != -1) {
                break;
            }
        }

        return candidates.size() != 0 && getCompletionHandler().complete(this, candidates, position);
    }

    protected void printCompletionCandidates() throws IOException {
        // debug ("tab for (" + buf + ")");
        if (completers.size() == 0) {
            return;
        }

        List<CharSequence> candidates = new LinkedList<CharSequence>();
        String bufstr = buf.buffer.toString();
        int cursor = buf.cursor;

        for (Completer comp : completers) {
            if (comp.complete(bufstr, cursor, candidates) != -1) {
                break;
            }
        }
        CandidateListCompletionHandler.printCandidates(this, candidates);
        drawLine();
    }

    /**
     * The number of tab-completion candidates above which a warning will be
     * prompted before showing all the candidates.
     */
    private int autoprintThreshold = Configuration.getInteger(JLINE_COMPLETION_THRESHOLD, 100); // same default as bash

    /**
     * @param threshold the number of candidates to print without issuing a warning.
     */
    public void setAutoprintThreshold(final int threshold) {
        this.autoprintThreshold = threshold;
    }

    /**
     * @return the number of candidates to print without issuing a warning.
     */
    public int getAutoprintThreshold() {
        return autoprintThreshold;
    }

    private boolean paginationEnabled;

    /**
     * Whether to use pagination when the number of rows of candidates exceeds the height of the terminal.
     */
    public void setPaginationEnabled(final boolean enabled) {
        this.paginationEnabled = enabled;
    }

    /**
     * Whether to use pagination when the number of rows of candidates exceeds the height of the terminal.
     */
    public boolean isPaginationEnabled() {
        return paginationEnabled;
    }

    //
    // History
    //

    private History history = new MemoryHistory();

    public void setHistory(final History history) {
        this.history = history;
    }

    public History getHistory() {
        return history;
    }

    private boolean historyEnabled = true;

    /**
     * Whether or not to add new commands to the history buffer.
     */
    public void setHistoryEnabled(final boolean enabled) {
        this.historyEnabled = enabled;
    }

    /**
     * Whether or not to add new commands to the history buffer.
     */
    public boolean isHistoryEnabled() {
        return historyEnabled;
    }

    /**
     * Used in "vi" mode for argumented history move, to move a specific
     * number of history entries forward or back.
     *
     * @param next If true, move forward
     * @param count The number of entries to move
     * @return true if the move was successful
     */
    private boolean moveHistory(final boolean next, int count) throws IOException {
        boolean ok = true;
        for (int i = 0; i < count && (ok = moveHistory(next)); i++) {
            /* empty */
        }
        return ok;
    }

    /**
     * Move up or down the history tree.
     */
    private boolean moveHistory(final boolean next) throws IOException {
        if (next && !history.next()) {
            return false;
        }
        else if (!next && !history.previous()) {
            return false;
        }

        setBuffer(history.current());

        return true;
    }

    //
    // Printing
    //

    /**
     * Output the specified characters to the output stream without manipulating the current buffer.
     */
    private int fmtPrint(final CharSequence buff, int cursorPos) throws IOException {
        return fmtPrint(buff, 0, buff.length(), cursorPos);
    }

    private int fmtPrint(final CharSequence buff, int start, int end) throws IOException {
        return fmtPrint(buff, start, end, getCursorPosition());
    }

    private int fmtPrint(final CharSequence buff, int start, int end, int cursorPos) throws IOException {
        checkNotNull(buff);
        for (int i = start; i < end; i++) {
            char c = buff.charAt(i);
            if (c == '\t') {
                int nb = nextTabStop(cursorPos);
                cursorPos += nb;
                while (nb-- > 0) {
                    out.write(' ');
                }
            } else if (c < 32) {
                out.write('^');
                out.write((char) (c + '@'));
                cursorPos += 2;
            } else {
                int w = WCWidth.wcwidth(c);
                if (w > 0) {
                    out.write(c);
                    cursorPos += w;
                }
            }
        }
        cursorOk = false;
        return cursorPos;
    }

    /**
     * Output the specified string to the output stream (but not the buffer).
     */
    public void print(final CharSequence s) throws IOException {
        rawPrint(s.toString());
    }

    public void println(final CharSequence s) throws IOException {
        print(s);
        println();
    }

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    /**
     * Output a platform-dependent newline.
     */
    public void println() throws IOException {
        rawPrint(LINE_SEPARATOR);
    }

    /**
     * Raw output printing
     */
    final void rawPrint(final int c) throws IOException {
        out.write(c);
        cursorOk = false;
    }

    final void rawPrint(final String str) throws IOException {
        out.write(str);
        cursorOk = false;
    }

    private void rawPrint(final char c, final int num) throws IOException {
        for (int i = 0; i < num; i++) {
            rawPrint(c);
        }
    }

    private void rawPrintln(final String s) throws IOException {
        rawPrint(s);
        println();
    }


    //
    // Actions
    //

    /**
     * Issue a delete.
     *
     * @return true if successful
     */
    public boolean delete() throws IOException {
        if (buf.cursor == buf.buffer.length()) {
          return false;
        }

        buf.buffer.delete(buf.cursor, buf.cursor + 1);
        drawBuffer(1);

        return true;
    }

    /**
     * Kill the buffer ahead of the current cursor position.
     *
     * @return true if successful
     */
    public boolean killLine() throws IOException {
        int cp = buf.cursor;
        int len = buf.buffer.length();

        if (cp >= len) {
            return false;
        }

        int num = len - cp;
        int pos = getCursorPosition();
        int width = wcwidth(buf.buffer, cp, len, pos);
        clearAhead(width, pos);

        char[] killed = new char[num];
        buf.buffer.getChars(cp, (cp + num), killed, 0);
        buf.buffer.delete(cp, (cp + num));

        String copy = new String(killed);
        killRing.add(copy);

        return true;
    }

    public boolean yank() throws IOException {
        String yanked = killRing.yank();

        if (yanked == null) {
            return false;
        }
        putString(yanked);
        return true;
    }

    public boolean yankPop() throws IOException {
        if (!killRing.lastYank()) {
            return false;
        }
        String current = killRing.yank();
        if (current == null) {
            // This shouldn't happen.
            return false;
        }
        backspace(current.length());
        String yanked = killRing.yankPop();
        if (yanked == null) {
            // This shouldn't happen.
            return false;
        }

        putString(yanked);
        return true;
    }

    /**
     * Clear the screen by issuing the ANSI "clear screen" code.
     */
    public boolean clearScreen() throws IOException {
        if (!tputs("clear_screen")) {
            println();
        }
        return true;
    }

    /**
     * Issue an audible keyboard bell.
     */
    public void beep() throws IOException {
        if (bellEnabled) {
            if (tputs("bell")) {
                // need to flush so the console actually beeps
                flush();
            }
        }
    }

    //disabled to avoid dependency on java.desktop:
//    /**
//     * Paste the contents of the clipboard into the console buffer
//     *
//     * @return true if clipboard contents pasted
//     */
//    public boolean paste() throws IOException {
//        Clipboard clipboard;
//        try { // May throw ugly exception on system without X
//            clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
//        }
//        catch (Exception e) {
//            return false;
//        }
//
//        if (clipboard == null) {
//            return false;
//        }
//
//        Transferable transferable = clipboard.getContents(null);
//
//        if (transferable == null) {
//            return false;
//        }
//
//        try {
//            @SuppressWarnings("deprecation")
//            Object content = transferable.getTransferData(DataFlavor.plainTextFlavor);
//
//            // This fix was suggested in bug #1060649 at
//            // http://sourceforge.net/tracker/index.php?func=detail&aid=1060649&group_id=64033&atid=506056
//            // to get around the deprecated DataFlavor.plainTextFlavor, but it
//            // raises a UnsupportedFlavorException on Mac OS X
//
//            if (content == null) {
//                try {
//                    content = new DataFlavor().getReaderForText(transferable);
//                }
//                catch (Exception e) {
//                    // ignore
//                }
//            }
//
//            if (content == null) {
//                return false;
//            }
//
//            String value;
//
//            if (content instanceof Reader) {
//                // TODO: we might want instead connect to the input stream
//                // so we can interpret individual lines
//                value = "";
//                String line;
//
//                BufferedReader read = new BufferedReader((Reader) content);
//                while ((line = read.readLine()) != null) {
//                    if (value.length() > 0) {
//                        value += "\n";
//                    }
//
//                    value += line;
//                }
//            }
//            else {
//                value = content.toString();
//            }
//
//            if (value == null) {
//                return true;
//            }
//
//            putString(value);
//
//            return true;
//        }
//        catch (UnsupportedFlavorException e) {
//            Log.error("Paste failed: ", e);
//
//            return false;
//        }
//    }

    //disabled to avoid dependency on java.desktop:
//    /**
//     * Adding a triggered Action allows to give another curse of action if a character passed the pre-processing.
//     * <p/>
//     * Say you want to close the application if the user enter q.
//     * addTriggerAction('q', new ActionListener(){ System.exit(0); }); would do the trick.
//     */
//    public void addTriggeredAction(final char c, final ActionListener listener) {
//        getKeys().bind(Character.toString(c), listener);
//    }

    //
    // Formatted Output
    //

    /**
     * Output the specified {@link Collection} in proper columns.
     */
    public void printColumns(final Collection<? extends CharSequence> items) throws IOException {
        if (items == null || items.isEmpty()) {
            return;
        }

        int width = getTerminal().getWidth();
        int height = getTerminal().getHeight();

        int maxWidth = 0;
        for (CharSequence item : items) {
            // we use 0 here, as we don't really support tabulations inside candidates
            int len = wcwidth(Ansi.stripAnsi(item.toString()), 0);
            maxWidth = Math.max(maxWidth, len);
        }
        maxWidth = maxWidth + 3;
        Log.debug("Max width: ", maxWidth);

        int showLines;
        if (isPaginationEnabled()) {
            showLines = height - 1; // page limit
        }
        else {
            showLines = Integer.MAX_VALUE;
        }

        StringBuilder buff = new StringBuilder();
        int realLength = 0;
        for (CharSequence item : items) {
            if ((realLength + maxWidth) > width) {
                rawPrintln(buff.toString());
                buff.setLength(0);
                realLength = 0;

                if (--showLines == 0) {
                    // Overflow
                    print(resources.getString("DISPLAY_MORE"));
                    flush();
                    int c = readCharacter();
                    if (c == '\r' || c == '\n') {
                        // one step forward
                        showLines = 1;
                    }
                    else if (c != 'q') {
                        // page forward
                        showLines = height - 1;
                    }

                    tputs("carriage_return");
                    if (c == 'q') {
                        // cancel
                        break;
                    }
                }
            }

            // NOTE: toString() is important here due to AnsiString being retarded
            buff.append(item.toString());
            int strippedItemLength = wcwidth(Ansi.stripAnsi(item.toString()), 0);
            for (int i = 0; i < (maxWidth - strippedItemLength); i++) {
                buff.append(' ');
            }
            realLength += maxWidth;
        }

        if (buff.length() > 0) {
            rawPrintln(buff.toString());
        }
    }

    //
    // Non-supported Terminal Support
    //

    private Thread maskThread;

    private void beforeReadLine(final String prompt, final Character mask) {
        if (mask != null && maskThread == null) {
            final String fullPrompt = "\r" + prompt
                + "                 "
                + "                 "
                + "                 "
                + "\r" + prompt;

            maskThread = new Thread()
            {
                public void run() {
                    while (!interrupted()) {
                        try {
                            Writer out = getOutput();
                            out.write(fullPrompt);
                            out.flush();
                            sleep(3);
                        }
                        catch (IOException e) {
                            return;
                        }
                        catch (InterruptedException e) {
                            return;
                        }
                    }
                }
            };

            maskThread.setPriority(Thread.MAX_PRIORITY);
            maskThread.setDaemon(true);
            maskThread.start();
        }
    }

    private void afterReadLine() {
        if (maskThread != null && maskThread.isAlive()) {
            maskThread.interrupt();
        }

        maskThread = null;
    }

    /**
     * Erases the current line with the existing prompt, then redraws the line
     * with the provided prompt and buffer
     * @param prompt
     *            the new prompt
     * @param buffer
     *            the buffer to be drawn
     * @param cursorDest
     *            where you want the cursor set when the line has been drawn.
     *            -1 for end of line.
     * */
    public void resetPromptLine(String prompt, String buffer, int cursorDest) throws IOException {
        // move cursor to end of line
        moveToEnd();

        // backspace all text, including prompt
        buf.buffer.append(this.prompt);
        int promptLength = 0;
        if (this.prompt != null) {
            promptLength = this.prompt.length();
        }

        buf.cursor += promptLength;
        setPrompt("");
        backspaceAll();

        setPrompt(prompt);
        redrawLine();
        setBuffer(buffer);

        // move cursor to destination (-1 will move to end of line)
        if (cursorDest < 0) cursorDest = buffer.length();
        setCursorPosition(cursorDest);

        flush();
    }

    public void printSearchStatus(String searchTerm, String match) throws IOException {
        printSearchStatus(searchTerm, match, "(reverse-i-search)`");
    }

    public void printForwardSearchStatus(String searchTerm, String match) throws IOException {
        printSearchStatus(searchTerm, match, "(i-search)`");
    }

    private void printSearchStatus(String searchTerm, String match, String searchLabel) throws IOException {
        String prompt = searchLabel + searchTerm + "': ";
        int cursorDest = match.indexOf(searchTerm);
        resetPromptLine(prompt, match, cursorDest);
    }

    public void restoreLine(String originalPrompt, int cursorDest) throws IOException {
        // TODO move cursor to matched string
        String prompt = lastLine(originalPrompt);
        String buffer = buf.buffer.toString();
        resetPromptLine(prompt, buffer, cursorDest);
    }

    //
    // History search
    //
    /**
     * Search backward in history from a given position.
     *
     * @param searchTerm substring to search for.
     * @param startIndex the index from which on to search
     * @return index where this substring has been found, or -1 else.
     */
    public int searchBackwards(String searchTerm, int startIndex) {
        return searchBackwards(searchTerm, startIndex, false);
    }

    /**
     * Search backwards in history from the current position.
     *
     * @param searchTerm substring to search for.
     * @return index where the substring has been found, or -1 else.
     */
    public int searchBackwards(String searchTerm) {
        return searchBackwards(searchTerm, history.index());
    }


    public int searchBackwards(String searchTerm, int startIndex, boolean startsWith) {
        ListIterator<History.Entry> it = history.entries(startIndex);
        while (it.hasPrevious()) {
            History.Entry e = it.previous();
            if (startsWith) {
                if (e.value().toString().startsWith(searchTerm)) {
                    return e.index();
                }
            } else {
                if (e.value().toString().contains(searchTerm)) {
                    return e.index();
                }
            }
        }
        return -1;
    }

    /**
     * Search forward in history from a given position.
     *
     * @param searchTerm substring to search for.
     * @param startIndex the index from which on to search
     * @return index where this substring has been found, or -1 else.
     */
    public int searchForwards(String searchTerm, int startIndex) {
        return searchForwards(searchTerm, startIndex, false);
    }
    /**
     * Search forwards in history from the current position.
     *
     * @param searchTerm substring to search for.
     * @return index where the substring has been found, or -1 else.
     */
    public int searchForwards(String searchTerm) {
        return searchForwards(searchTerm, history.index());
    }

    public int searchForwards(String searchTerm, int startIndex, boolean startsWith) {
        if (startIndex >= history.size()) {
            startIndex = history.size() - 1;
        }

        ListIterator<History.Entry> it = history.entries(startIndex);

        if (searchIndex != -1 && it.hasNext()) {
            it.next();
        }

        while (it.hasNext()) {
            History.Entry e = it.next();
            if (startsWith) {
                if (e.value().toString().startsWith(searchTerm)) {
                    return e.index();
                }
            } else {
                if (e.value().toString().contains(searchTerm)) {
                    return e.index();
                }
            }
        }
        return -1;
    }

    //
    // Helpers
    //

    /**
     * Checks to see if the specified character is a delimiter. We consider a
     * character a delimiter if it is anything but a letter or digit.
     *
     * @param c     The character to test
     * @return      True if it is a delimiter
     */
    private static boolean isDelimiter(final char c) {
        return !Character.isLetterOrDigit(c);
    }

    /**
     * Checks to see if a character is a whitespace character. Currently
     * this delegates to {@link Character#isWhitespace(char)}, however
     * eventually it should be hooked up so that the definition of whitespace
     * can be configured, as readline does.
     *
     * @param c The character to check
     * @return true if the character is a whitespace
     */
    private static boolean isWhitespace(final char c) {
        return Character.isWhitespace (c);
    }

    private boolean tputs(String cap, Object... params) throws IOException {
        String str = terminal.getStringCapability(cap);
        if (str == null) {
            return false;
        }
        Curses.tputs(out, str, params);
        return true;
    }

}
