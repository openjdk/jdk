/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.jshell.tool;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import jdk.internal.jshell.debug.InternalDebugControl;
import jdk.internal.jshell.tool.IOContext.InputInterruptedException;
import jdk.jshell.DeclarationSnippet;
import jdk.jshell.Diag;
import jdk.jshell.EvalException;
import jdk.jshell.ExpressionSnippet;
import jdk.jshell.ImportSnippet;
import jdk.jshell.JShell;
import jdk.jshell.JShell.Subscription;
import jdk.jshell.MethodSnippet;
import jdk.jshell.PersistentSnippet;
import jdk.jshell.Snippet;
import jdk.jshell.Snippet.Status;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.SourceCodeAnalysis.CompletionInfo;
import jdk.jshell.SourceCodeAnalysis.Suggestion;
import jdk.jshell.TypeDeclSnippet;
import jdk.jshell.UnresolvedReferenceException;
import jdk.jshell.VarSnippet;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Supplier;
import jdk.internal.jshell.tool.Feedback.FormatAction;
import jdk.internal.jshell.tool.Feedback.FormatCase;
import jdk.internal.jshell.tool.Feedback.FormatErrors;
import jdk.internal.jshell.tool.Feedback.FormatResolve;
import jdk.internal.jshell.tool.Feedback.FormatUnresolved;
import jdk.internal.jshell.tool.Feedback.FormatWhen;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static jdk.jshell.Snippet.SubKind.VAR_VALUE_SUBKIND;

/**
 * Command line REPL tool for Java using the JShell API.
 * @author Robert Field
 */
public class JShellTool implements MessageHandler {

    private static final String LINE_SEP = System.getProperty("line.separator");
    private static final Pattern LINEBREAK = Pattern.compile("\\R");
    private static final Pattern HISTORY_ALL_START_FILENAME = Pattern.compile(
            "((?<cmd>(all|history|start))(\\z|\\p{javaWhitespace}+))?(?<filename>.*)");
    private static final String RECORD_SEPARATOR = "\u241E";
    private static final String RB_NAME_PREFIX  = "jdk.internal.jshell.tool.resources";
    private static final String VERSION_RB_NAME = RB_NAME_PREFIX + ".version";
    private static final String L10N_RB_NAME    = RB_NAME_PREFIX + ".l10n";

    final InputStream cmdin;
    final PrintStream cmdout;
    final PrintStream cmderr;
    final PrintStream console;
    final InputStream userin;
    final PrintStream userout;
    final PrintStream usererr;
    final Preferences prefs;
    final Locale locale;

    final Feedback feedback = new Feedback();

    /**
     * The constructor for the tool (used by tool launch via main and by test
     * harnesses to capture ins and outs.
     * @param cmdin command line input -- snippets and commands
     * @param cmdout command line output, feedback including errors
     * @param cmderr start-up errors and debugging info
     * @param console console control interaction
     * @param userin code execution input (not yet functional)
     * @param userout code execution output  -- System.out.printf("hi")
     * @param usererr code execution error stream  -- System.err.printf("Oops")
     * @param prefs preferences to use
     * @param locale locale to use
     */
    public JShellTool(InputStream cmdin, PrintStream cmdout, PrintStream cmderr,
            PrintStream console,
            InputStream userin, PrintStream userout, PrintStream usererr,
            Preferences prefs, Locale locale) {
        this.cmdin = cmdin;
        this.cmdout = cmdout;
        this.cmderr = cmderr;
        this.console = console;
        this.userin = userin;
        this.userout = userout;
        this.usererr = usererr;
        this.prefs = prefs;
        this.locale = locale;
    }

    private ResourceBundle versionRB = null;
    private ResourceBundle outputRB  = null;

    private IOContext input = null;
    private boolean regenerateOnDeath = true;
    private boolean live = false;
    private boolean feedbackInitialized = false;
    private String initialMode = null;
    private List<String> remoteVMOptions = new ArrayList<>();

    SourceCodeAnalysis analysis;
    JShell state = null;
    Subscription shutdownSubscription = null;

    private boolean debug = false;
    public boolean testPrompt = false;
    private String cmdlineClasspath = null;
    private String cmdlineStartup = null;
    private String[] editor = null;

    // Commands and snippets which should be replayed
    private List<String> replayableHistory;
    private List<String> replayableHistoryPrevious;

    static final String STARTUP_KEY = "STARTUP";
    static final String REPLAY_RESTORE_KEY = "REPLAY_RESTORE";

    static final String DEFAULT_STARTUP =
            "\n" +
            "import java.util.*;\n" +
            "import java.io.*;\n" +
            "import java.math.*;\n" +
            "import java.net.*;\n" +
            "import java.util.concurrent.*;\n" +
            "import java.util.prefs.*;\n" +
            "import java.util.regex.*;\n" +
            "void printf(String format, Object... args) { System.out.printf(format, args); }\n";

    // Tool id (tid) mapping: the three name spaces
    NameSpace mainNamespace;
    NameSpace startNamespace;
    NameSpace errorNamespace;

    // Tool id (tid) mapping: the current name spaces
    NameSpace currentNameSpace;

    Map<Snippet,SnippetInfo> mapSnippet;

    /**
     * Is the input/output currently interactive
     *
     * @return true if console
     */
    boolean interactive() {
        return input != null && input.interactiveOutput();
    }

    void debug(String format, Object... args) {
        if (debug) {
            cmderr.printf(format + "\n", args);
        }
    }

    /**
     * Base output for command output -- no pre- or post-fix
     *
     * @param printf format
     * @param printf args
     */
    void rawout(String format, Object... args) {
        cmdout.printf(format, args);
    }

    /**
     * Must show command output
     *
     * @param format printf format
     * @param args printf args
     */
    void hard(String format, Object... args) {
        rawout(feedback.getPre() + format + feedback.getPost(), args);
    }

    /**
     * Error command output
     *
     * @param format printf format
     * @param args printf args
     */
    void error(String format, Object... args) {
        rawout(feedback.getErrorPre() + format + feedback.getErrorPost(), args);
    }

    /**
     * Optional output
     *
     * @param format printf format
     * @param args printf args
     */
    @Override
    public void fluff(String format, Object... args) {
        if (feedback.shouldDisplayCommandFluff() && interactive()) {
            hard(format, args);
        }
    }

    /**
     * Optional output -- with embedded per- and post-fix
     *
     * @param format printf format
     * @param args printf args
     */
    void fluffRaw(String format, Object... args) {
        if (feedback.shouldDisplayCommandFluff() && interactive()) {
            rawout(format, args);
        }
    }

    /**
     * Print using resource bundle look-up and adding prefix and postfix
     *
     * @param key the resource key
     */
    String getResourceString(String key) {
        if (outputRB == null) {
            try {
                outputRB = ResourceBundle.getBundle(L10N_RB_NAME, locale);
            } catch (MissingResourceException mre) {
                error("Cannot find ResourceBundle: %s for locale: %s", L10N_RB_NAME, locale);
                return "";
            }
        }
        String s;
        try {
            s = outputRB.getString(key);
        } catch (MissingResourceException mre) {
            error("Missing resource: %s in %s", key, L10N_RB_NAME);
            return "";
        }
        return s;
    }

    /**
     * Add prefixing to embedded newlines in a string, leading with the normal
     * prefix
     *
     * @param s the string to prefix
     */
    String prefix(String s) {
        return prefix(s, feedback.getPre());
    }

    /**
     * Add prefixing to embedded newlines in a string
     *
     * @param s the string to prefix
     * @param leading the string to prepend
     */
    String prefix(String s, String leading) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return leading
                + s.substring(0, s.length() - 1).replaceAll("\\R", System.getProperty("line.separator") + feedback.getPre())
                + s.substring(s.length() - 1, s.length());
    }

    /**
     * Print using resource bundle look-up and adding prefix and postfix
     *
     * @param key the resource key
     */
    void hardrb(String key) {
        String s = prefix(getResourceString(key));
        cmdout.println(s);
    }

    /**
     * Format using resource bundle look-up using MessageFormat
     *
     * @param key the resource key
     * @param args
     */
    String messageFormat(String key, Object... args) {
        String rs = getResourceString(key);
        return MessageFormat.format(rs, args);
    }

    /**
     * Print using resource bundle look-up, MessageFormat, and add prefix and
     * postfix
     *
     * @param key the resource key
     * @param args
     */
    void hardmsg(String key, Object... args) {
        cmdout.println(prefix(messageFormat(key, args)));
    }

    /**
     * Print error using resource bundle look-up, MessageFormat, and add prefix
     * and postfix
     *
     * @param key the resource key
     * @param args
     */
    @Override
    public void errormsg(String key, Object... args) {
        cmdout.println(prefix(messageFormat(key, args), feedback.getErrorPre()));
    }

    /**
     * Print command-line error using resource bundle look-up, MessageFormat
     *
     * @param key the resource key
     * @param args
     */
    void startmsg(String key, Object... args) {
        cmderr.println(prefix(messageFormat(key, args), ""));
    }

    /**
     * Print (fluff) using resource bundle look-up, MessageFormat, and add
     * prefix and postfix
     *
     * @param key the resource key
     * @param args
     */
    @Override
    public void fluffmsg(String key, Object... args) {
        if (feedback.shouldDisplayCommandFluff() && interactive()) {
            hardmsg(key, args);
        }
    }

    <T> void hardPairs(Stream<T> stream, Function<T, String> a, Function<T, String> b) {
        Map<String, String> a2b = stream.collect(toMap(a, b,
                (m1, m2) -> m1,
                () -> new LinkedHashMap<>()));
        int aLen = 0;
        for (String av : a2b.keySet()) {
            aLen = Math.max(aLen, av.length());
        }
        String format = "   %-" + aLen + "s -- %s";
        String indentedNewLine = LINE_SEP + feedback.getPre()
                + String.format("   %-" + (aLen + 4) + "s", "");
        for (Entry<String, String> e : a2b.entrySet()) {
            hard(format, e.getKey(), e.getValue().replaceAll("\n", indentedNewLine));
        }
    }

    /**
     * Trim whitespace off end of string
     *
     * @param s
     * @return
     */
    static String trimEnd(String s) {
        int last = s.length() - 1;
        int i = last;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) {
            --i;
        }
        if (i != last) {
            return s.substring(0, i + 1);
        } else {
            return s;
        }
    }

    /**
     * Normal start entry point
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        new JShellTool(System.in, System.out, System.err, System.out,
                 new ByteArrayInputStream(new byte[0]), System.out, System.err,
                 Preferences.userRoot().node("tool/JShell"),
                 Locale.getDefault())
                .start(args);
    }

    public void start(String[] args) throws Exception {
        List<String> loadList = processCommandArgs(args);
        if (loadList == null) {
            // Abort
            return;
        }
        try (IOContext in = new ConsoleIOContext(this, cmdin, console)) {
            start(in, loadList);
        }
    }

    private void start(IOContext in, List<String> loadList) {
        resetState(); // Initialize

        // Read replay history from last jshell session into previous history
        String prevReplay = prefs.get(REPLAY_RESTORE_KEY, null);
        if (prevReplay != null) {
            replayableHistoryPrevious = Arrays.asList(prevReplay.split(RECORD_SEPARATOR));
        }

        for (String loadFile : loadList) {
            runFile(loadFile, "jshell");
        }

        if (regenerateOnDeath) {
            hardmsg("jshell.msg.welcome", version());
        }

        try {
            while (regenerateOnDeath) {
                if (!live) {
                    resetState();
                }
                run(in);
            }
        } finally {
            closeState();
        }
    }

    /**
     * Process the command line arguments.
     * Set options.
     * @param args the command line arguments
     * @return the list of files to be loaded
     */
    private List<String> processCommandArgs(String[] args) {
        List<String> loadList = new ArrayList<>();
        Iterator<String> ai = Arrays.asList(args).iterator();
        while (ai.hasNext()) {
            String arg = ai.next();
            if (arg.startsWith("-")) {
                switch (arg) {
                    case "-classpath":
                    case "-cp":
                        if (cmdlineClasspath != null) {
                            startmsg("jshell.err.opt.classpath.conflict");
                            return null;
                        }
                        if (ai.hasNext()) {
                            cmdlineClasspath = ai.next();
                        } else {
                            startmsg("jshell.err.opt.classpath.arg");
                            return null;
                        }
                        break;
                    case "-help":
                        printUsage();
                        return null;
                    case "-version":
                        cmdout.printf("jshell %s\n", version());
                        return null;
                    case "-fullversion":
                        cmdout.printf("jshell %s\n", fullVersion());
                        return null;
                    case "-feedback":
                        if (ai.hasNext()) {
                            initialMode = ai.next();
                        } else {
                            startmsg("jshell.err.opt.feedback.arg");
                            return null;
                        }
                        break;
                    case "-q":
                        initialMode = "concise";
                        break;
                    case "-qq":
                        initialMode = "silent";
                        break;
                    case "-v":
                        initialMode = "verbose";
                        break;
                    case "-startup":
                        if (cmdlineStartup != null) {
                            startmsg("jshell.err.opt.startup.conflict");
                            return null;
                        }
                        cmdlineStartup = readFile(ai.hasNext()? ai.next() : null, "'-startup'");
                        if (cmdlineStartup == null) {
                            return null;
                        }
                        break;
                    case "-nostartup":
                        if (cmdlineStartup != null && !cmdlineStartup.isEmpty()) {
                            startmsg("jshell.err.opt.startup.conflict");
                            return null;
                        }
                        cmdlineStartup = "";
                        break;
                    default:
                        if (arg.startsWith("-R")) {
                            remoteVMOptions.add(arg.substring(2));
                            break;
                        }
                        startmsg("jshell.err.opt.unknown", arg);
                        printUsage();
                        return null;
                }
            } else {
                loadList.add(arg);
            }
        }
        return loadList;
    }

    private void printUsage() {
        cmdout.print(getResourceString("help.usage"));
    }

    private void resetState() {
        closeState();

        // Initialize tool id mapping
        mainNamespace = new NameSpace("main", "");
        startNamespace = new NameSpace("start", "s");
        errorNamespace = new NameSpace("error", "e");
        mapSnippet = new LinkedHashMap<>();
        currentNameSpace = startNamespace;

        // Reset the replayable history, saving the old for restore
        replayableHistoryPrevious = replayableHistory;
        replayableHistory = new ArrayList<>();

        state = JShell.builder()
                .in(userin)
                .out(userout)
                .err(usererr)
                .tempVariableNameGenerator(()-> "$" + currentNameSpace.tidNext())
                .idGenerator((sn, i) -> (currentNameSpace == startNamespace || state.status(sn).isActive)
                        ? currentNameSpace.tid(sn)
                        : errorNamespace.tid(sn))
                .remoteVMOptions(remoteVMOptions.toArray(new String[remoteVMOptions.size()]))
                .build();
        shutdownSubscription = state.onShutdown((JShell deadState) -> {
            if (deadState == state) {
                hardmsg("jshell.msg.terminated");
                live = false;
            }
        });
        analysis = state.sourceCodeAnalysis();
        live = true;
        if (!feedbackInitialized) {
            startUpRun(getResourceString("startup.feedback"));
            feedbackInitialized = true;
        }

        if (cmdlineClasspath != null) {
            state.addToClasspath(cmdlineClasspath);
        }

        String start;
        if (cmdlineStartup == null) {
            start = prefs.get(STARTUP_KEY, "<nada>");
            if (start.equals("<nada>")) {
                start = DEFAULT_STARTUP;
                prefs.put(STARTUP_KEY, DEFAULT_STARTUP);
            }
        } else {
            start = cmdlineStartup;
        }
        startUpRun(start);
        if (initialMode != null) {
            MessageHandler mh = new MessageHandler() {
                @Override
                public void fluff(String format, Object... args) {
                }

                @Override
                public void fluffmsg(String messageKey, Object... args) {
                }

                @Override
                public void errormsg(String messageKey, Object... args) {
                    startmsg(messageKey, args);
                }
            };
            if (!feedback.setFeedback(mh, new ArgTokenizer("-feedback ", initialMode))) {
                regenerateOnDeath = false;
            }
            initialMode = null;
        }
        currentNameSpace = mainNamespace;
    }
    //where
    private void startUpRun(String start) {
        try (IOContext suin = new FileScannerIOContext(new StringReader(start))) {
            run(suin);
        } catch (Exception ex) {
            hardmsg("jshell.err.startup.unexpected.exception", ex);
        }
    }

    private void closeState() {
        live = false;
        JShell oldState = state;
        if (oldState != null) {
            oldState.unsubscribe(shutdownSubscription); // No notification
            oldState.close();
        }
    }

    /**
     * Main loop
     * @param in the line input/editing context
     */
    private void run(IOContext in) {
        IOContext oldInput = input;
        input = in;
        try {
            String incomplete = "";
            while (live) {
                String prompt;
                if (currentNameSpace == mainNamespace) {
                    prompt = testPrompt
                                    ? incomplete.isEmpty()
                                            ? "\u0005" //ENQ
                                            : "\u0006" //ACK
                                    : incomplete.isEmpty()
                                            ? feedback.getPrompt(currentNameSpace.tidNext())
                                            : feedback.getContinuationPrompt(currentNameSpace.tidNext())
                    ;
                } else {
                    prompt = "";
                }
                String raw;
                try {
                    raw = in.readLine(prompt, incomplete);
                } catch (InputInterruptedException ex) {
                    //input interrupted - clearing current state
                    incomplete = "";
                    continue;
                }
                if (raw == null) {
                    //EOF
                    if (in.interactiveOutput()) {
                        // End after user ctrl-D
                        regenerateOnDeath = false;
                    }
                    break;
                }
                String trimmed = trimEnd(raw);
                if (!trimmed.isEmpty()) {
                    String line = incomplete + trimmed;

                    // No commands in the middle of unprocessed source
                    if (incomplete.isEmpty() && line.startsWith("/") && !line.startsWith("//") && !line.startsWith("/*")) {
                        processCommand(line.trim());
                    } else {
                        incomplete = processSourceCatchingReset(line);
                    }
                }
            }
        } catch (IOException ex) {
            errormsg("jshell.err.unexpected.exception", ex);
        } finally {
            input = oldInput;
        }
    }

    private void addToReplayHistory(String s) {
        if (currentNameSpace == mainNamespace) {
            replayableHistory.add(s);
        }
    }

    private String processSourceCatchingReset(String src) {
        try {
            input.beforeUserCode();
            return processSource(src);
        } catch (IllegalStateException ex) {
            hard("Resetting...");
            live = false; // Make double sure
            return "";
        } finally {
            input.afterUserCode();
        }
    }

    private void processCommand(String cmd) {
        if (cmd.startsWith("/-")) {
            try {
                //handle "/-[number]"
                cmdUseHistoryEntry(Integer.parseInt(cmd.substring(1)));
                return ;
            } catch (NumberFormatException ex) {
                //ignore
            }
        }
        String arg = "";
        int idx = cmd.indexOf(' ');
        if (idx > 0) {
            arg = cmd.substring(idx + 1).trim();
            cmd = cmd.substring(0, idx);
        }
        Command[] candidates = findCommand(cmd, c -> c.kind.isRealCommand);
        switch (candidates.length) {
            case 0:
                if (!rerunHistoryEntryById(cmd.substring(1))) {
                    errormsg("jshell.err.no.such.command.or.snippet.id", cmd);
                    fluffmsg("jshell.msg.help.for.help");
                }   break;
            case 1:
                Command command = candidates[0];
                // If comand was successful and is of a replayable kind, add it the replayable history
                if (command.run.apply(arg) && command.kind == CommandKind.REPLAY) {
                    addToReplayHistory((command.command + " " + arg).trim());
                }   break;
            default:
                errormsg("jshell.err.command.ambiguous", cmd,
                        Arrays.stream(candidates).map(c -> c.command).collect(Collectors.joining(", ")));
                fluffmsg("jshell.msg.help.for.help");
                break;
        }
    }

    private Command[] findCommand(String cmd, Predicate<Command> filter) {
        Command exact = commands.get(cmd);
        if (exact != null)
            return new Command[] {exact};

        return commands.values()
                       .stream()
                       .filter(filter)
                       .filter(command -> command.command.startsWith(cmd))
                       .toArray(size -> new Command[size]);
    }

    private static Path toPathResolvingUserHome(String pathString) {
        if (pathString.replace(File.separatorChar, '/').startsWith("~/"))
            return Paths.get(System.getProperty("user.home"), pathString.substring(2));
        else
            return Paths.get(pathString);
    }

    static final class Command {
        public final String command;
        public final String helpKey;
        public final Function<String,Boolean> run;
        public final CompletionProvider completions;
        public final CommandKind kind;

        // NORMAL Commands
        public Command(String command, Function<String,Boolean> run, CompletionProvider completions) {
            this(command, run, completions, CommandKind.NORMAL);
        }

        // Special kinds of Commands
        public Command(String command, Function<String,Boolean> run, CompletionProvider completions, CommandKind kind) {
            this(command, "help." + command.substring(1),
                    run, completions, kind);
        }

        // Documentation pseudo-commands
        public Command(String command, String helpKey, CommandKind kind) {
            this(command, helpKey,
                    arg -> { throw new IllegalStateException(); },
                    EMPTY_COMPLETION_PROVIDER,
                    kind);
        }

        public Command(String command, String helpKey, Function<String,Boolean> run, CompletionProvider completions, CommandKind kind) {
            this.command = command;
            this.helpKey = helpKey;
            this.run = run;
            this.completions = completions;
            this.kind = kind;
        }

    }

    interface CompletionProvider {
        List<Suggestion> completionSuggestions(String input, int cursor, int[] anchor);
    }

    enum CommandKind {
        NORMAL(true, true, true),
        REPLAY(true, true, true),
        HIDDEN(true, false, false),
        HELP_ONLY(false, true, false),
        HELP_SUBJECT(false, false, false);

        final boolean isRealCommand;
        final boolean showInHelp;
        final boolean shouldSuggestCompletions;
        private CommandKind(boolean isRealCommand, boolean showInHelp, boolean shouldSuggestCompletions) {
            this.isRealCommand = isRealCommand;
            this.showInHelp = showInHelp;
            this.shouldSuggestCompletions = shouldSuggestCompletions;
        }
    }

    static final class FixedCompletionProvider implements CompletionProvider {

        private final String[] alternatives;

        public FixedCompletionProvider(String... alternatives) {
            this.alternatives = alternatives;
        }

        @Override
        public List<Suggestion> completionSuggestions(String input, int cursor, int[] anchor) {
            List<Suggestion> result = new ArrayList<>();

            for (String alternative : alternatives) {
                if (alternative.startsWith(input)) {
                    result.add(new Suggestion(alternative, false));
                }
            }

            anchor[0] = 0;

            return result;
        }

    }

    private static final CompletionProvider EMPTY_COMPLETION_PROVIDER = new FixedCompletionProvider();
    private static final CompletionProvider KEYWORD_COMPLETION_PROVIDER = new FixedCompletionProvider("all ", "start ", "history ");
    private static final CompletionProvider RELOAD_OPTIONS_COMPLETION_PROVIDER = new FixedCompletionProvider("restore", "quiet");
    private static final CompletionProvider FILE_COMPLETION_PROVIDER = fileCompletions(p -> true);
    private final Map<String, Command> commands = new LinkedHashMap<>();
    private void registerCommand(Command cmd) {
        commands.put(cmd.command, cmd);
    }
    private static CompletionProvider fileCompletions(Predicate<Path> accept) {
        return (code, cursor, anchor) -> {
            int lastSlash = code.lastIndexOf('/');
            String path = code.substring(0, lastSlash + 1);
            String prefix = lastSlash != (-1) ? code.substring(lastSlash + 1) : code;
            Path current = toPathResolvingUserHome(path);
            List<Suggestion> result = new ArrayList<>();
            try (Stream<Path> dir = Files.list(current)) {
                dir.filter(f -> accept.test(f) && f.getFileName().toString().startsWith(prefix))
                   .map(f -> new Suggestion(f.getFileName() + (Files.isDirectory(f) ? "/" : ""), false))
                   .forEach(result::add);
            } catch (IOException ex) {
                //ignore...
            }
            if (path.isEmpty()) {
                StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false)
                             .filter(root -> accept.test(root) && root.toString().startsWith(prefix))
                             .map(root -> new Suggestion(root.toString(), false))
                             .forEach(result::add);
            }
            anchor[0] = path.length();
            return result;
        };
    }

    private static CompletionProvider classPathCompletion() {
        return fileCompletions(p -> Files.isDirectory(p) ||
                                    p.getFileName().toString().endsWith(".zip") ||
                                    p.getFileName().toString().endsWith(".jar"));
    }

    private CompletionProvider editCompletion() {
        return (prefix, cursor, anchor) -> {
            anchor[0] = 0;
            return state.snippets()
                        .stream()
                        .flatMap(k -> (k instanceof DeclarationSnippet)
                                ? Stream.of(String.valueOf(k.id()), ((DeclarationSnippet) k).name())
                                : Stream.of(String.valueOf(k.id())))
                        .filter(k -> k.startsWith(prefix))
                        .map(k -> new Suggestion(k, false))
                        .collect(Collectors.toList());
        };
    }

    private CompletionProvider editKeywordCompletion() {
        return (code, cursor, anchor) -> {
            List<Suggestion> result = new ArrayList<>();
            result.addAll(KEYWORD_COMPLETION_PROVIDER.completionSuggestions(code, cursor, anchor));
            result.addAll(editCompletion().completionSuggestions(code, cursor, anchor));
            return result;
        };
    }

    private static CompletionProvider saveCompletion() {
        return (code, cursor, anchor) -> {
            List<Suggestion> result = new ArrayList<>();
            int space = code.indexOf(' ');
            if (space == (-1)) {
                result.addAll(KEYWORD_COMPLETION_PROVIDER.completionSuggestions(code, cursor, anchor));
            }
            result.addAll(FILE_COMPLETION_PROVIDER.completionSuggestions(code.substring(space + 1), cursor - space - 1, anchor));
            anchor[0] += space + 1;
            return result;
        };
    }

    private static CompletionProvider reloadCompletion() {
        return (code, cursor, anchor) -> {
            List<Suggestion> result = new ArrayList<>();
            int pastSpace = code.indexOf(' ') + 1; // zero if no space
            result.addAll(RELOAD_OPTIONS_COMPLETION_PROVIDER.completionSuggestions(code.substring(pastSpace), cursor - pastSpace, anchor));
            anchor[0] += pastSpace;
            return result;
        };
    }

    // Table of commands -- with command forms, argument kinds, helpKey message, implementation, ...

    {
        registerCommand(new Command("/list",
                arg -> cmdList(arg),
                editKeywordCompletion()));
        registerCommand(new Command("/edit",
                arg -> cmdEdit(arg),
                editCompletion()));
        registerCommand(new Command("/drop",
                arg -> cmdDrop(arg),
                editCompletion(),
                CommandKind.REPLAY));
        registerCommand(new Command("/save",
                arg -> cmdSave(arg),
                saveCompletion()));
        registerCommand(new Command("/open",
                arg -> cmdOpen(arg),
                FILE_COMPLETION_PROVIDER));
        registerCommand(new Command("/vars",
                arg -> cmdVars(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/methods",
                arg -> cmdMethods(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/classes",
                arg -> cmdClasses(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/imports",
                arg -> cmdImports(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/exit",
                arg -> cmdExit(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/reset",
                arg -> cmdReset(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/reload",
                arg -> cmdReload(arg),
                reloadCompletion()));
        registerCommand(new Command("/classpath",
                arg -> cmdClasspath(arg),
                classPathCompletion(),
                CommandKind.REPLAY));
        registerCommand(new Command("/history",
                arg -> cmdHistory(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/debug",
                arg -> cmdDebug(arg),
                EMPTY_COMPLETION_PROVIDER,
                CommandKind.HIDDEN));
        registerCommand(new Command("/help",
                arg -> cmdHelp(arg),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/set",
                arg -> cmdSet(arg),
                new FixedCompletionProvider("format", "feedback", "prompt", "newmode", "start", "editor")));
        registerCommand(new Command("/?",
                "help.quest",
                arg -> cmdHelp(arg),
                EMPTY_COMPLETION_PROVIDER,
                CommandKind.NORMAL));
        registerCommand(new Command("/!",
                "help.bang",
                arg -> cmdUseHistoryEntry(-1),
                EMPTY_COMPLETION_PROVIDER,
                CommandKind.NORMAL));

        // Documentation pseudo-commands
        registerCommand(new Command("/<id>",
                "help.id",
                CommandKind.HELP_ONLY));
        registerCommand(new Command("/-<n>",
                "help.previous",
                CommandKind.HELP_ONLY));
        registerCommand(new Command("intro",
                "help.intro",
                CommandKind.HELP_SUBJECT));
        registerCommand(new Command("shortcuts",
                "help.shortcuts",
                CommandKind.HELP_SUBJECT));
    }

    public List<Suggestion> commandCompletionSuggestions(String code, int cursor, int[] anchor) {
        String prefix = code.substring(0, cursor);
        int space = prefix.indexOf(' ');
        Stream<Suggestion> result;

        if (space == (-1)) {
            result = commands.values()
                             .stream()
                             .distinct()
                             .filter(cmd -> cmd.kind.shouldSuggestCompletions)
                             .map(cmd -> cmd.command)
                             .filter(key -> key.startsWith(prefix))
                             .map(key -> new Suggestion(key + " ", false));
            anchor[0] = 0;
        } else {
            String arg = prefix.substring(space + 1);
            String cmd = prefix.substring(0, space);
            Command[] candidates = findCommand(cmd, c -> true);
            if (candidates.length == 1) {
                result = candidates[0].completions.completionSuggestions(arg, cursor - space, anchor).stream();
                anchor[0] += space + 1;
            } else {
                result = Stream.empty();
            }
        }

        return result.sorted((s1, s2) -> s1.continuation.compareTo(s2.continuation))
                     .collect(Collectors.toList());
    }

    public String commandDocumentation(String code, int cursor) {
        code = code.substring(0, cursor);
        int space = code.indexOf(' ');

        if (space != (-1)) {
            String cmd = code.substring(0, space);
            Command command = commands.get(cmd);
            if (command != null) {
                return getResourceString(command.helpKey + ".summary");
            }
        }

        return null;
    }

    // --- Command implementations ---

    private static final String[] SET_SUBCOMMANDS = new String[]{
        "format", "feedback", "newmode", "prompt", "editor", "start"};

    final boolean cmdSet(String arg) {
        ArgTokenizer at = new ArgTokenizer("/set ", arg.trim());
        String which = setSubCommand(at);
        if (which == null) {
            return false;
        }
        switch (which) {
            case "format":
                return feedback.setFormat(this, at);
            case "feedback":
                return feedback.setFeedback(this, at);
            case "newmode":
                return feedback.setNewMode(this, at);
            case "prompt":
                return feedback.setPrompt(this, at);
            case "editor": {
                String prog = at.next();
                if (prog == null) {
                    errormsg("jshell.err.set.editor.arg");
                    return false;
                } else {
                    List<String> ed = new ArrayList<>();
                    ed.add(prog);
                    String n;
                    while ((n = at.next()) != null) {
                        ed.add(n);
                    }
                    editor = ed.toArray(new String[ed.size()]);
                    fluffmsg("jshell.msg.set.editor.set", prog);
                    return true;
                }
            }
            case "start": {
                String init = readFile(at.next(), "/set start");
                if (init == null) {
                    return false;
                } else {
                    prefs.put(STARTUP_KEY, init);
                    return true;
                }
            }
            default:
                errormsg("jshell.err.arg", "/set", at.val());
                return false;
        }
    }

    boolean printSetHelp(ArgTokenizer at) {
        String which = setSubCommand(at);
        if (which == null) {
            return false;
        }
        hardrb("help.set." + which);
        return true;
    }

    String setSubCommand(ArgTokenizer at) {
        String[] matches = at.next(SET_SUBCOMMANDS);
        if (matches == null) {
            errormsg("jshell.err.set.arg");
            return null;
        }
        if (matches.length == 0) {
            errormsg("jshell.err.arg", "/set", at.val());
            fluffmsg("jshell.msg.use.one.of", Arrays.stream(SET_SUBCOMMANDS)
                    .collect(Collectors.joining(", "))
            );
            return null;
        }
        if (matches.length > 1) {
            errormsg("jshell.err.set.ambiguous", at.val());
            fluffmsg("jshell.msg.use.one.of", Arrays.stream(matches)
                    .collect(Collectors.joining(", "))
            );
            return null;
        }
        return matches[0];
    }

    boolean cmdClasspath(String arg) {
        if (arg.isEmpty()) {
            errormsg("jshell.err.classpath.arg");
            return false;
        } else {
            state.addToClasspath(toPathResolvingUserHome(arg).toString());
            fluffmsg("jshell.msg.classpath", arg);
            return true;
        }
    }

    boolean cmdDebug(String arg) {
        if (arg.isEmpty()) {
            debug = !debug;
            InternalDebugControl.setDebugFlags(state, debug ? InternalDebugControl.DBG_GEN : 0);
            fluff("Debugging %s", debug ? "on" : "off");
        } else {
            int flags = 0;
            for (char ch : arg.toCharArray()) {
                switch (ch) {
                    case '0':
                        flags = 0;
                        debug = false;
                        fluff("Debugging off");
                        break;
                    case 'r':
                        debug = true;
                        fluff("REPL tool debugging on");
                        break;
                    case 'g':
                        flags |= InternalDebugControl.DBG_GEN;
                        fluff("General debugging on");
                        break;
                    case 'f':
                        flags |= InternalDebugControl.DBG_FMGR;
                        fluff("File manager debugging on");
                        break;
                    case 'c':
                        flags |= InternalDebugControl.DBG_COMPA;
                        fluff("Completion analysis debugging on");
                        break;
                    case 'd':
                        flags |= InternalDebugControl.DBG_DEP;
                        fluff("Dependency debugging on");
                        break;
                    case 'e':
                        flags |= InternalDebugControl.DBG_EVNT;
                        fluff("Event debugging on");
                        break;
                    default:
                        hard("Unknown debugging option: %c", ch);
                        fluff("Use: 0 r g f c d");
                        return false;
                }
            }
            InternalDebugControl.setDebugFlags(state, flags);
        }
        return true;
    }

    private boolean cmdExit() {
        regenerateOnDeath = false;
        live = false;
        if (!replayableHistory.isEmpty()) {
            // Prevent history overflow by calculating what will fit, starting
            // with must recent
            int sepLen = RECORD_SEPARATOR.length();
            int length = 0;
            int first = replayableHistory.size();
            while(length < Preferences.MAX_VALUE_LENGTH && --first >= 0) {
                length += replayableHistory.get(first).length() + sepLen;
            }
            String hist = replayableHistory
                    .subList(first + 1, replayableHistory.size())
                    .stream()
                    .reduce( (a, b) -> a + RECORD_SEPARATOR + b)
                    .get();
            prefs.put(REPLAY_RESTORE_KEY, hist);
        }
        fluffmsg("jshell.msg.goodbye");
        return true;
    }

    boolean cmdHelp(String arg) {
        ArgTokenizer at = new ArgTokenizer(arg);
        String subject = at.next();
        if (subject != null) {
            Command[] matches = commands.values().stream()
                    .filter(c -> c.command.startsWith(subject))
                    .toArray(size -> new Command[size]);
            at.mark();
            String sub = at.next();
            if (sub != null && matches.length == 1 && matches[0].command.equals("/set")) {
                at.rewind();
                return printSetHelp(at);
            }
            if (matches.length > 0) {
                for (Command c : matches) {
                    hard("");
                    hard("%s", c.command);
                    hard("");
                    hardrb(c.helpKey);
                }
                return true;
            } else {
                errormsg("jshell.err.help.arg", arg);
            }
        }
        hardmsg("jshell.msg.help.begin");
        hardPairs(commands.values().stream()
                .filter(cmd -> cmd.kind.showInHelp),
                cmd -> cmd.command + " " + getResourceString(cmd.helpKey + ".args"),
                cmd -> getResourceString(cmd.helpKey + ".summary")
        );
        hardmsg("jshell.msg.help.subject");
        hardPairs(commands.values().stream()
                .filter(cmd -> cmd.kind == CommandKind.HELP_SUBJECT),
                cmd -> cmd.command,
                cmd -> getResourceString(cmd.helpKey + ".summary")
        );
        return true;
    }

    private boolean cmdHistory() {
        cmdout.println();
        for (String s : input.currentSessionHistory()) {
            // No number prefix, confusing with snippet ids
            cmdout.printf("%s\n", s);
        }
        return true;
    }

    /**
     * Avoid parameterized varargs possible heap pollution warning.
     */
    private interface SnippetPredicate extends Predicate<Snippet> { }

    /**
     * Apply filters to a stream until one that is non-empty is found.
     * Adapted from Stuart Marks
     *
     * @param supplier Supply the Snippet stream to filter
     * @param filters Filters to attempt
     * @return The non-empty filtered Stream, or null
     */
    private static Stream<Snippet> nonEmptyStream(Supplier<Stream<Snippet>> supplier,
            SnippetPredicate... filters) {
        for (SnippetPredicate filt : filters) {
            Iterator<Snippet> iterator = supplier.get().filter(filt).iterator();
            if (iterator.hasNext()) {
                return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false);
            }
        }
        return null;
    }

    private boolean inStartUp(Snippet sn) {
        return mapSnippet.get(sn).space == startNamespace;
    }

    private boolean isActive(Snippet sn) {
        return state.status(sn).isActive;
    }

    private boolean mainActive(Snippet sn) {
        return !inStartUp(sn) && isActive(sn);
    }

    private boolean matchingDeclaration(Snippet sn, String name) {
        return sn instanceof DeclarationSnippet
                && ((DeclarationSnippet) sn).name().equals(name);
    }

    /**
     * Convert a user argument to a Stream of snippets referenced by that argument
     * (or lack of argument).
     *
     * @param arg The user's argument to the command, maybe be the empty string
     * @return a Stream of referenced snippets or null if no matches to specific arg
     */
    private Stream<Snippet> argToSnippets(String arg, boolean allowAll) {
        List<Snippet> snippets = state.snippets();
        if (allowAll && arg.equals("all")) {
            // all snippets including start-up, failed, and overwritten
            return snippets.stream();
        } else if (allowAll && arg.equals("start")) {
            // start-up snippets
            return snippets.stream()
                    .filter(this::inStartUp);
        } else if (arg.isEmpty()) {
            // Default is all active user snippets
            return snippets.stream()
                    .filter(this::mainActive);
        } else {
            Stream<Snippet> result =
                    nonEmptyStream(
                            () -> snippets.stream(),
                            // look for active user declarations matching the name
                            sn -> isActive(sn) && matchingDeclaration(sn, arg),
                            // else, look for any declarations matching the name
                            sn -> matchingDeclaration(sn, arg),
                            // else, look for an id of this name
                            sn -> sn.id().equals(arg)
                    );
            return result;
        }
    }

    private boolean cmdDrop(String arg) {
        if (arg.isEmpty()) {
            errormsg("jshell.err.drop.arg");
            return false;
        }
        Stream<Snippet> stream = argToSnippets(arg, false);
        if (stream == null) {
            errormsg("jshell.err.def.or.id.not.found", arg);
            fluffmsg("jshell.msg.see.classes.etc");
            return false;
        }
        List<Snippet> snippets = stream
                .filter(sn -> state.status(sn).isActive && sn instanceof PersistentSnippet)
                .collect(toList());
        if (snippets.isEmpty()) {
            errormsg("jshell.err.drop.not.active");
            return false;
        }
        if (snippets.size() > 1) {
            errormsg("jshell.err.drop.ambiguous");
            fluffmsg("jshell.msg.use.one.of", snippets.stream()
                    .map(sn -> String.format("\n%4s : %s", sn.id(), sn.source().replace("\n", "\n       ")))
                    .collect(Collectors.joining(", "))
            );
            return false;
        }
        PersistentSnippet psn = (PersistentSnippet) snippets.get(0);
        state.drop(psn).forEach(this::handleEvent);
        return true;
    }

    private boolean cmdEdit(String arg) {
        Stream<Snippet> stream = argToSnippets(arg, true);
        if (stream == null) {
            errormsg("jshell.err.def.or.id.not.found", arg);
            fluffmsg("jshell.msg.see.classes.etc");
            return false;
        }
        Set<String> srcSet = new LinkedHashSet<>();
        stream.forEachOrdered(sn -> {
            String src = sn.source();
            switch (sn.subKind()) {
                case VAR_VALUE_SUBKIND:
                    break;
                case ASSIGNMENT_SUBKIND:
                case OTHER_EXPRESSION_SUBKIND:
                case TEMP_VAR_EXPRESSION_SUBKIND:
                    if (!src.endsWith(";")) {
                        src = src + ";";
                    }
                    srcSet.add(src);
                    break;
                default:
                    srcSet.add(src);
                    break;
            }
        });
        StringBuilder sb = new StringBuilder();
        for (String s : srcSet) {
            sb.append(s);
            sb.append('\n');
        }
        String src = sb.toString();
        Consumer<String> saveHandler = new SaveHandler(src, srcSet);
        Consumer<String> errorHandler = s -> hard("Edit Error: %s", s);
        if (editor == null) {
            try {
                EditPad.edit(errorHandler, src, saveHandler);
            } catch (RuntimeException ex) {
                errormsg("jshell.err.cant.launch.editor", ex);
                fluffmsg("jshell.msg.try.set.editor");
                return false;
            }
        } else {
            ExternalEditor.edit(editor, errorHandler, src, saveHandler, input);
        }
        return true;
    }
    //where
    // receives editor requests to save
    private class SaveHandler implements Consumer<String> {

        String src;
        Set<String> currSrcs;

        SaveHandler(String src, Set<String> ss) {
            this.src = src;
            this.currSrcs = ss;
        }

        @Override
        public void accept(String s) {
            if (!s.equals(src)) { // quick check first
                src = s;
                try {
                    Set<String> nextSrcs = new LinkedHashSet<>();
                    boolean failed = false;
                    while (true) {
                        CompletionInfo an = analysis.analyzeCompletion(s);
                        if (!an.completeness.isComplete) {
                            break;
                        }
                        String tsrc = trimNewlines(an.source);
                        if (!failed && !currSrcs.contains(tsrc)) {
                            failed = processCompleteSource(tsrc);
                        }
                        nextSrcs.add(tsrc);
                        if (an.remaining.isEmpty()) {
                            break;
                        }
                        s = an.remaining;
                    }
                    currSrcs = nextSrcs;
                } catch (IllegalStateException ex) {
                    hardmsg("jshell.msg.resetting");
                    resetState();
                    currSrcs = new LinkedHashSet<>(); // re-process everything
                }
            }
        }

        private String trimNewlines(String s) {
            int b = 0;
            while (b < s.length() && s.charAt(b) == '\n') {
                ++b;
            }
            int e = s.length() -1;
            while (e >= 0 && s.charAt(e) == '\n') {
                --e;
            }
            return s.substring(b, e + 1);
        }
    }

    private boolean cmdList(String arg) {
        if (arg.equals("history")) {
            return cmdHistory();
        }
        Stream<Snippet> stream = argToSnippets(arg, true);
        if (stream == null) {
            errormsg("jshell.err.def.or.id.not.found", arg);
            // Check if there are any definitions at all
            if (argToSnippets("", false).iterator().hasNext()) {
                fluffmsg("jshell.msg.try.list.without.args");
            } else {
                hardmsg("jshell.msg.no.active");
            }
            return false;
        }

        // prevent double newline on empty list
        boolean[] hasOutput = new boolean[1];
        stream.forEachOrdered(sn -> {
            if (!hasOutput[0]) {
                cmdout.println();
                hasOutput[0] = true;
            }
            cmdout.printf("%4s : %s\n", sn.id(), sn.source().replace("\n", "\n       "));
        });
        return true;
    }

    private boolean cmdOpen(String filename) {
        return runFile(filename, "/open");
    }

    private boolean runFile(String filename, String context) {
        if (!filename.isEmpty()) {
            try {
                run(new FileScannerIOContext(toPathResolvingUserHome(filename).toString()));
                return true;
            } catch (FileNotFoundException e) {
                errormsg("jshell.err.file.not.found", context, filename, e.getMessage());
            } catch (Exception e) {
                errormsg("jshell.err.file.exception", context, filename, e);
            }
        } else {
            errormsg("jshell.err.file.filename", context);
        }
        return false;
    }

    /**
     * Read an external file. Error messages accessed via keyPrefix
     *
     * @param filename file to access or null
     * @param context printable non-natural language context for errors
     * @return contents of file as string
     */
    String readFile(String filename, String context) {
        if (filename != null) {
            try {
                byte[] encoded = Files.readAllBytes(Paths.get(filename));
                return new String(encoded);
            } catch (AccessDeniedException e) {
                errormsg("jshell.err.file.not.accessible", context, filename, e.getMessage());
            } catch (NoSuchFileException e) {
                errormsg("jshell.err.file.not.found", context, filename);
            } catch (Exception e) {
                errormsg("jshell.err.file.exception", context, filename, e);
            }
        } else {
            errormsg("jshell.err.file.filename", context);
        }
        return null;

    }

    private boolean cmdReset() {
        live = false;
        fluffmsg("jshell.msg.resetting.state");
        return true;
    }

    private boolean cmdReload(String arg) {
        Iterable<String> history = replayableHistory;
        boolean echo = true;
        if (arg.length() > 0) {
            if ("restore".startsWith(arg)) {
                if (replayableHistoryPrevious == null) {
                    errormsg("jshell.err.reload.no.previous");
                    return false;
                }
                history = replayableHistoryPrevious;
            } else if ("quiet".startsWith(arg)) {
                echo = false;
            } else {
                errormsg("jshell.err.arg", "/reload", arg);
                return false;
            }
        }
        fluffmsg(history == replayableHistoryPrevious
                        ? "jshell.err.reload.restarting.previous.state"
                        : "jshell.err.reload.restarting.state");
        resetState();
        run(new ReloadIOContext(history,
                echo? cmdout : null));
        return true;
    }

    private boolean cmdSave(String arg_filename) {
        Matcher mat = HISTORY_ALL_START_FILENAME.matcher(arg_filename);
        if (!mat.find()) {
            errormsg("jshell.err.arg", arg_filename);
            return false;
        }
        boolean useHistory = false;
        String saveAll = "";
        boolean saveStart = false;
        String cmd = mat.group("cmd");
        if (cmd != null) switch (cmd) {
            case "all":
                saveAll = "all";
                break;
            case "history":
                useHistory = true;
                break;
            case "start":
                saveStart = true;
                break;
        }
        String filename = mat.group("filename");
        if (filename == null ||filename.isEmpty()) {
            errormsg("jshell.err.file.filename", "/save");
            return false;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(toPathResolvingUserHome(filename),
                Charset.defaultCharset(),
                CREATE, TRUNCATE_EXISTING, WRITE)) {
            if (useHistory) {
                for (String s : input.currentSessionHistory()) {
                    writer.write(s);
                    writer.write("\n");
                }
            } else if (saveStart) {
                writer.append(DEFAULT_STARTUP);
            } else {
                Stream<Snippet> stream = argToSnippets(saveAll, true);
                if (stream != null) {
                    for (Snippet sn : stream.collect(toList())) {
                        writer.write(sn.source());
                        writer.write("\n");
                    }
                }
            }
        } catch (FileNotFoundException e) {
            errormsg("jshell.err.file.not.found", "/save", filename, e.getMessage());
            return false;
        } catch (Exception e) {
            errormsg("jshell.err.file.exception", "/save", filename, e);
            return false;
        }
        return true;
    }

    private boolean cmdVars() {
        for (VarSnippet vk : state.variables()) {
            String val = state.status(vk) == Status.VALID
                    ? state.varValue(vk)
                    : "jshell.msg.vars.not.active";
            hard("  %s %s = %s", vk.typeName(), vk.name(), val);
        }
        return true;
    }

    private boolean cmdMethods() {
        for (MethodSnippet mk : state.methods()) {
            hard("  %s %s", mk.name(), mk.signature());
        }
        return true;
    }

    private boolean cmdClasses() {
        for (TypeDeclSnippet ck : state.types()) {
            String kind;
            switch (ck.subKind()) {
                case INTERFACE_SUBKIND:
                    kind = "interface";
                    break;
                case CLASS_SUBKIND:
                    kind = "class";
                    break;
                case ENUM_SUBKIND:
                    kind = "enum";
                    break;
                case ANNOTATION_TYPE_SUBKIND:
                    kind = "@interface";
                    break;
                default:
                    assert false : "Wrong kind" + ck.subKind();
                    kind = "class";
                    break;
            }
            hard("  %s %s", kind, ck.name());
        }
        return true;
    }

    private boolean cmdImports() {
        state.imports().forEach(ik -> {
            hard("  import %s%s", ik.isStatic() ? "static " : "", ik.fullname());
        });
        return true;
    }

    private boolean cmdUseHistoryEntry(int index) {
        List<Snippet> keys = state.snippets();
        if (index < 0)
            index += keys.size();
        else
            index--;
        if (index >= 0 && index < keys.size()) {
            rerunSnippet(keys.get(index));
        } else {
            errormsg("jshell.err.out.of.range");
            return false;
        }
        return true;
    }

    private boolean rerunHistoryEntryById(String id) {
        Optional<Snippet> snippet = state.snippets().stream()
            .filter(s -> s.id().equals(id))
            .findFirst();
        return snippet.map(s -> {
            rerunSnippet(s);
            return true;
        }).orElse(false);
    }

    private void rerunSnippet(Snippet snippet) {
        String source = snippet.source();
        cmdout.printf("%s\n", source);
        input.replaceLastHistoryEntry(source);
        processSourceCatchingReset(source);
    }

    /**
     * Filter diagnostics for only errors (no warnings, ...)
     * @param diagnostics input list
     * @return filtered list
     */
    List<Diag> errorsOnly(List<Diag> diagnostics) {
        return diagnostics.stream()
                .filter(d -> d.isError())
                .collect(toList());
    }

    void displayDiagnostics(String source, Diag diag, List<String> toDisplay) {
        for (String line : diag.getMessage(null).split("\\r?\\n")) { // TODO: Internationalize
            if (!line.trim().startsWith("location:")) {
                toDisplay.add(line);
            }
        }

        int pstart = (int) diag.getStartPosition();
        int pend = (int) diag.getEndPosition();
        Matcher m = LINEBREAK.matcher(source);
        int pstartl = 0;
        int pendl = -2;
        while (m.find(pstartl)) {
            pendl = m.start();
            if (pendl >= pstart) {
                break;
            } else {
                pstartl = m.end();
            }
        }
        if (pendl < pstart) {
            pendl = source.length();
        }
        toDisplay.add(source.substring(pstartl, pendl));

        StringBuilder sb = new StringBuilder();
        int start = pstart - pstartl;
        for (int i = 0; i < start; ++i) {
            sb.append(' ');
        }
        sb.append('^');
        boolean multiline = pend > pendl;
        int end = (multiline ? pendl : pend) - pstartl - 1;
        if (end > start) {
            for (int i = start + 1; i < end; ++i) {
                sb.append('-');
            }
            if (multiline) {
                sb.append("-...");
            } else {
                sb.append('^');
            }
        }
        toDisplay.add(sb.toString());

        debug("printDiagnostics start-pos = %d ==> %d -- wrap = %s", diag.getStartPosition(), start, this);
        debug("Code: %s", diag.getCode());
        debug("Pos: %d (%d - %d)", diag.getPosition(),
                diag.getStartPosition(), diag.getEndPosition());
    }

    private String processSource(String srcInput) throws IllegalStateException {
        while (true) {
            CompletionInfo an = analysis.analyzeCompletion(srcInput);
            if (!an.completeness.isComplete) {
                return an.remaining;
            }
            boolean failed = processCompleteSource(an.source);
            if (failed || an.remaining.isEmpty()) {
                return "";
            }
            srcInput = an.remaining;
        }
    }
    //where
    private boolean processCompleteSource(String source) throws IllegalStateException {
        debug("Compiling: %s", source);
        boolean failed = false;
        boolean isActive = false;
        List<SnippetEvent> events = state.eval(source);
        for (SnippetEvent e : events) {
            // Report the event, recording failure
            failed |= handleEvent(e);

            // If any main snippet is active, this should be replayable
            // also ignore var value queries
            isActive |= e.causeSnippet() == null &&
                    e.status().isActive &&
                    e.snippet().subKind() != VAR_VALUE_SUBKIND;
        }
        // If this is an active snippet and it didn't cause the backend to die,
        // add it to the replayable history
        if (isActive && live) {
            addToReplayHistory(source);
        }

        return failed;
    }

    // Handle incoming snippet events -- return true on failure
    private boolean handleEvent(SnippetEvent ste) {
        Snippet sn = ste.snippet();
        if (sn == null) {
            debug("Event with null key: %s", ste);
            return false;
        }
        List<Diag> diagnostics = state.diagnostics(sn);
        String source = sn.source();
        if (ste.causeSnippet() == null) {
            // main event
            for (Diag d : diagnostics) {
                hardmsg(d.isError()? "jshell.msg.error" : "jshell.msg.warning");
                List<String> disp = new ArrayList<>();
                displayDiagnostics(source, d, disp);
                disp.stream()
                        .forEach(l -> hard(l));
            }

            if (ste.status() != Status.REJECTED) {
                if (ste.exception() != null) {
                    if (ste.exception() instanceof EvalException) {
                        printEvalException((EvalException) ste.exception());
                        return true;
                    } else if (ste.exception() instanceof UnresolvedReferenceException) {
                        printUnresolvedException((UnresolvedReferenceException) ste.exception());
                    } else {
                        hard("Unexpected execution exception: %s", ste.exception());
                        return true;
                    }
                } else {
                    new DisplayEvent(ste, false, ste.value(), diagnostics).displayDeclarationAndValue();
                }
            } else {
                if (diagnostics.isEmpty()) {
                    errormsg("jshell.err.failed");
                }
                return true;
            }
        } else {
            // Update
            if (sn instanceof DeclarationSnippet) {
                List<Diag> other = errorsOnly(diagnostics);

                // display update information
                new DisplayEvent(ste, true, ste.value(), other).displayDeclarationAndValue();
            }
        }
        return false;
    }
    //where
    void printStackTrace(StackTraceElement[] stes) {
        for (StackTraceElement ste : stes) {
            StringBuilder sb = new StringBuilder();
            String cn = ste.getClassName();
            if (!cn.isEmpty()) {
                int dot = cn.lastIndexOf('.');
                if (dot > 0) {
                    sb.append(cn.substring(dot + 1));
                } else {
                    sb.append(cn);
                }
                sb.append(".");
            }
            if (!ste.getMethodName().isEmpty()) {
                sb.append(ste.getMethodName());
                sb.append(" ");
            }
            String fileName = ste.getFileName();
            int lineNumber = ste.getLineNumber();
            String loc = ste.isNativeMethod()
                    ? getResourceString("jshell.msg.native.method")
                    : fileName == null
                            ? getResourceString("jshell.msg.unknown.source")
                            : lineNumber >= 0
                                    ? fileName + ":" + lineNumber
                                    : fileName;
            hard("      at %s(%s)", sb, loc);

        }
    }
    //where
    void printUnresolvedException(UnresolvedReferenceException ex) {
        DeclarationSnippet corralled =  ex.getSnippet();
        List<Diag> otherErrors = errorsOnly(state.diagnostics(corralled));
        new DisplayEvent(corralled, state.status(corralled), FormatAction.USED, true, null, otherErrors)
                .displayDeclarationAndValue();
    }
    //where
    void printEvalException(EvalException ex) {
        if (ex.getMessage() == null) {
            hard("%s thrown", ex.getExceptionClassName());
        } else {
            hard("%s thrown: %s", ex.getExceptionClassName(), ex.getMessage());
        }
        printStackTrace(ex.getStackTrace());
    }

    private FormatAction toAction(Status status, Status previousStatus, boolean isSignatureChange) {
        FormatAction act;
        switch (status) {
            case VALID:
            case RECOVERABLE_DEFINED:
            case RECOVERABLE_NOT_DEFINED:
                if (previousStatus.isActive) {
                    act = isSignatureChange
                            ? FormatAction.REPLACED
                            : FormatAction.MODIFIED;
                } else {
                    act = FormatAction.ADDED;
                }
                break;
            case OVERWRITTEN:
                act = FormatAction.OVERWROTE;
                break;
            case DROPPED:
                act = FormatAction.DROPPED;
                break;
            case REJECTED:
            case NONEXISTENT:
            default:
                // Should not occur
                error("Unexpected status: " + previousStatus.toString() + "=>" + status.toString());
                act = FormatAction.DROPPED;
        }
        return act;
    }

    class DisplayEvent {
        private final Snippet sn;
        private final FormatAction action;
        private final boolean update;
        private final String value;
        private final List<String> errorLines;
        private final FormatResolve resolution;
        private final String unresolved;
        private final FormatUnresolved unrcnt;
        private final FormatErrors errcnt;

        DisplayEvent(SnippetEvent ste, boolean update, String value, List<Diag> errors) {
            this(ste.snippet(), ste.status(), toAction(ste.status(), ste.previousStatus(), ste.isSignatureChange()), update, value, errors);
        }

        DisplayEvent(Snippet sn, Status status, FormatAction action, boolean update, String value, List<Diag> errors) {
            this.sn = sn;
            this.action = action;
            this.update = update;
            this.value = value;
            this.errorLines = new ArrayList<>();
            for (Diag d : errors) {
                displayDiagnostics(sn.source(), d, errorLines);
            }
            int unresolvedCount;
            if (sn instanceof DeclarationSnippet && (status == Status.RECOVERABLE_DEFINED || status == Status.RECOVERABLE_NOT_DEFINED)) {
                resolution = (status == Status.RECOVERABLE_NOT_DEFINED)
                        ? FormatResolve.NOTDEFINED
                        : FormatResolve.DEFINED;
                unresolved = unresolved((DeclarationSnippet) sn);
                unresolvedCount = state.unresolvedDependencies((DeclarationSnippet) sn).size();
            } else {
                resolution = FormatResolve.OK;
                unresolved = "";
                unresolvedCount = 0;
            }
            unrcnt = unresolvedCount == 0
                    ? FormatUnresolved.UNRESOLVED0
                    : unresolvedCount == 1
                        ? FormatUnresolved.UNRESOLVED1
                        : FormatUnresolved.UNRESOLVED2;
            errcnt = errors.isEmpty()
                    ? FormatErrors.ERROR0
                    : errors.size() == 1
                        ? FormatErrors.ERROR1
                        : FormatErrors.ERROR2;
        }

        private String unresolved(DeclarationSnippet key) {
            List<String> unr = state.unresolvedDependencies(key);
            StringBuilder sb = new StringBuilder();
            int fromLast = unr.size();
            if (fromLast > 0) {
                sb.append(" ");
            }
            for (String u : unr) {
                --fromLast;
                sb.append(u);
                switch (fromLast) {
                    // No suffix
                    case 0:
                        break;
                    case 1:
                        sb.append(", and ");
                        break;
                    default:
                        sb.append(", ");
                        break;
                }
            }
            return sb.toString();
        }

        private void custom(FormatCase fcase, String name) {
            custom(fcase, name, null);
        }

        private void custom(FormatCase fcase, String name, String type) {
            String display = feedback.format(fcase, action, (update ? FormatWhen.UPDATE : FormatWhen.PRIMARY),
                    resolution, unrcnt, errcnt,
                    name, type, value, unresolved, errorLines);
            if (interactive()) {
                cmdout.print(display);
            }
        }

        @SuppressWarnings("fallthrough")
        private void displayDeclarationAndValue() {
            switch (sn.subKind()) {
                case CLASS_SUBKIND:
                    custom(FormatCase.CLASS, ((TypeDeclSnippet) sn).name());
                    break;
                case INTERFACE_SUBKIND:
                    custom(FormatCase.INTERFACE, ((TypeDeclSnippet) sn).name());
                    break;
                case ENUM_SUBKIND:
                    custom(FormatCase.ENUM, ((TypeDeclSnippet) sn).name());
                    break;
                case ANNOTATION_TYPE_SUBKIND:
                    custom(FormatCase.ANNOTATION, ((TypeDeclSnippet) sn).name());
                    break;
                case METHOD_SUBKIND:
                    custom(FormatCase.METHOD, ((MethodSnippet) sn).name(), ((MethodSnippet) sn).parameterTypes());
                    break;
                case VAR_DECLARATION_SUBKIND: {
                    VarSnippet vk = (VarSnippet) sn;
                    custom(FormatCase.VARDECL, vk.name(), vk.typeName());
                    break;
                }
                case VAR_DECLARATION_WITH_INITIALIZER_SUBKIND: {
                    VarSnippet vk = (VarSnippet) sn;
                    custom(FormatCase.VARINIT, vk.name(), vk.typeName());
                    break;
                }
                case TEMP_VAR_EXPRESSION_SUBKIND: {
                    VarSnippet vk = (VarSnippet) sn;
                    custom(FormatCase.EXPRESSION, vk.name(), vk.typeName());
                    break;
                }
                case OTHER_EXPRESSION_SUBKIND:
                    error("Unexpected expression form -- value is: %s", (value));
                    break;
                case VAR_VALUE_SUBKIND: {
                    ExpressionSnippet ek = (ExpressionSnippet) sn;
                    custom(FormatCase.VARVALUE, ek.name(), ek.typeName());
                    break;
                }
                case ASSIGNMENT_SUBKIND: {
                    ExpressionSnippet ek = (ExpressionSnippet) sn;
                    custom(FormatCase.ASSIGNMENT, ek.name(), ek.typeName());
                    break;
                }
                case SINGLE_TYPE_IMPORT_SUBKIND:
                case TYPE_IMPORT_ON_DEMAND_SUBKIND:
                case SINGLE_STATIC_IMPORT_SUBKIND:
                case STATIC_IMPORT_ON_DEMAND_SUBKIND:
                    custom(FormatCase.IMPORT, ((ImportSnippet) sn).name());
                    break;
                case STATEMENT_SUBKIND:
                    custom(FormatCase.STATEMENT, null);
                    break;
            }
        }
    }

    /** The current version number as a string.
     */
    String version() {
        return version("release");  // mm.nn.oo[-milestone]
    }

    /** The current full version number as a string.
     */
    String fullVersion() {
        return version("full"); // mm.mm.oo[-milestone]-build
    }

    private String version(String key) {
        if (versionRB == null) {
            try {
                versionRB = ResourceBundle.getBundle(VERSION_RB_NAME, locale);
            } catch (MissingResourceException e) {
                return "(version info not available)";
            }
        }
        try {
            return versionRB.getString(key);
        }
        catch (MissingResourceException e) {
            return "(version info not available)";
        }
    }

    class NameSpace {
        final String spaceName;
        final String prefix;
        private int nextNum;

        NameSpace(String spaceName, String prefix) {
            this.spaceName = spaceName;
            this.prefix = prefix;
            this.nextNum = 1;
        }

        String tid(Snippet sn) {
            String tid = prefix + nextNum++;
            mapSnippet.put(sn, new SnippetInfo(sn, this, tid));
            return tid;
        }

        String tidNext() {
            return prefix + nextNum;
        }
    }

    static class SnippetInfo {
        final Snippet snippet;
        final NameSpace space;
        final String tid;

        SnippetInfo(Snippet snippet, NameSpace space, String tid) {
            this.snippet = snippet;
            this.space = space;
            this.tid = tid;
        }
    }
}

abstract class NonInteractiveIOContext extends IOContext {

    @Override
    public boolean interactiveOutput() {
        return false;
    }

    @Override
    public Iterable<String> currentSessionHistory() {
        return Collections.emptyList();
    }

    @Override
    public boolean terminalEditorRunning() {
        return false;
    }

    @Override
    public void suspend() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void beforeUserCode() {
    }

    @Override
    public void afterUserCode() {
    }

    @Override
    public void replaceLastHistoryEntry(String source) {
    }
}

class ScannerIOContext extends NonInteractiveIOContext {
    private final Scanner scannerIn;

    ScannerIOContext(Scanner scannerIn) {
        this.scannerIn = scannerIn;
    }

    @Override
    public String readLine(String prompt, String prefix) {
        if (scannerIn.hasNextLine()) {
            return scannerIn.nextLine();
        } else {
            return null;
        }
    }

    @Override
    public void close() {
        scannerIn.close();
    }
}

class FileScannerIOContext extends ScannerIOContext {

    FileScannerIOContext(String fn) throws FileNotFoundException {
        this(new FileReader(fn));
    }

    FileScannerIOContext(Reader rdr) throws FileNotFoundException {
        super(new Scanner(rdr));
    }
}

class ReloadIOContext extends NonInteractiveIOContext {
    private final Iterator<String> it;
    private final PrintStream echoStream;

    ReloadIOContext(Iterable<String> history, PrintStream echoStream) {
        this.it = history.iterator();
        this.echoStream = echoStream;
    }

    @Override
    public String readLine(String prompt, String prefix) {
        String s = it.hasNext()
                ? it.next()
                : null;
        if (echoStream != null && s != null) {
            String p = "-: ";
            String p2 = "\n   ";
            echoStream.printf("%s%s\n", p, s.replace("\n", p2));
        }
        return s;
    }

    @Override
    public void close() {
    }
}
