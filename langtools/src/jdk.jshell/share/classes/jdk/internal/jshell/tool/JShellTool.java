
/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
import jdk.jshell.Diag;
import jdk.jshell.EvalException;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.DeclarationSnippet;
import jdk.jshell.TypeDeclSnippet;
import jdk.jshell.MethodSnippet;
import jdk.jshell.PersistentSnippet;
import jdk.jshell.VarSnippet;
import jdk.jshell.ExpressionSnippet;
import jdk.jshell.Snippet.Status;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.SourceCodeAnalysis.CompletionInfo;
import jdk.jshell.SourceCodeAnalysis.Suggestion;
import jdk.jshell.SnippetEvent;
import jdk.jshell.UnresolvedReferenceException;
import jdk.jshell.Snippet.SubKind;
import jdk.jshell.JShell.Subscription;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Supplier;
import static java.util.stream.Collectors.toList;
import static jdk.jshell.Snippet.SubKind.VAR_VALUE_SUBKIND;

/**
 * Command line REPL tool for Java using the JShell API.
 * @author Robert Field
 */
public class JShellTool {

    private static final Pattern LINEBREAK = Pattern.compile("\\R");
    private static final Pattern HISTORY_ALL_START_FILENAME = Pattern.compile(
            "((?<cmd>(all|history|start))(\\z|\\p{javaWhitespace}+))?(?<filename>.*)");
    private static final String RECORD_SEPARATOR = "\u241E";

    final InputStream cmdin;
    final PrintStream cmdout;
    final PrintStream cmderr;
    final PrintStream console;
    final InputStream userin;
    final PrintStream userout;
    final PrintStream usererr;

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
     */
    public JShellTool(InputStream cmdin, PrintStream cmdout, PrintStream cmderr,
            PrintStream console,
            InputStream userin, PrintStream userout, PrintStream usererr) {
        this.cmdin = cmdin;
        this.cmdout = cmdout;
        this.cmderr = cmderr;
        this.console = console;
        this.userin = userin;
        this.userout = userout;
        this.usererr = usererr;
    }

    private IOContext input = null;
    private boolean regenerateOnDeath = true;
    private boolean live = false;

    SourceCodeAnalysis analysis;
    JShell state = null;
    Subscription shutdownSubscription = null;

    private boolean debug = false;
    private boolean displayPrompt = true;
    public boolean testPrompt = false;
    private Feedback feedback = Feedback.Default;
    private String cmdlineClasspath = null;
    private String cmdlineStartup = null;
    private String editor = null;

    // Commands and snippets which should be replayed
    private List<String> replayableHistory;
    private List<String> replayableHistoryPrevious;

    static final Preferences PREFS = Preferences.userRoot().node("tool/JShell");

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

    void debug(String format, Object... args) {
        if (debug) {
            cmderr.printf(format + "\n", args);
        }
    }

    /**
     * For more verbose feedback modes
     * @param format printf format
     * @param args printf args
     */
    void fluff(String format, Object... args) {
        if (feedback() != Feedback.Off && feedback() != Feedback.Concise) {
            hard(format, args);
        }
    }

    /**
     * For concise feedback mode only
     * @param format printf format
     * @param args printf args
     */
    void concise(String format, Object... args) {
        if (feedback() == Feedback.Concise) {
            hard(format, args);
        }
    }

    /**
     * For all feedback modes -- must show
     * @param format printf format
     * @param args printf args
     */
    void hard(String format, Object... args) {
        cmdout.printf("|  " + format + "\n", args);
    }

    /**
     * Trim whitespace off end of string
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
                 new ByteArrayInputStream(new byte[0]), System.out, System.err)
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
        String prevReplay = PREFS.get(REPLAY_RESTORE_KEY, null);
        if (prevReplay != null) {
            replayableHistoryPrevious = Arrays.asList(prevReplay.split(RECORD_SEPARATOR));
        }

        for (String loadFile : loadList) {
            cmdOpen(loadFile);
        }

        if (regenerateOnDeath) {
            fluff("Welcome to JShell -- Version %s", version());
            fluff("Type /help for help");
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
                            cmderr.printf("Conflicting -classpath option.\n");
                            return null;
                        }
                        if (ai.hasNext()) {
                            cmdlineClasspath = ai.next();
                        } else {
                            cmderr.printf("Argument to -classpath missing.\n");
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
                    case "-startup":
                        if (cmdlineStartup != null) {
                            cmderr.printf("Conflicting -startup or -nostartup option.\n");
                            return null;
                        }
                        if (ai.hasNext()) {
                            String filename = ai.next();
                            try {
                                byte[] encoded = Files.readAllBytes(Paths.get(filename));
                                cmdlineStartup = new String(encoded);
                            } catch (AccessDeniedException e) {
                                hard("File '%s' for start-up is not accessible.", filename);
                            } catch (NoSuchFileException e) {
                                hard("File '%s' for start-up is not found.", filename);
                            } catch (Exception e) {
                                hard("Exception while reading start-up file: %s", e);
                            }
                        } else {
                            cmderr.printf("Argument to -startup missing.\n");
                            return null;
                        }
                        break;
                    case "-nostartup":
                        if (cmdlineStartup != null && !cmdlineStartup.isEmpty()) {
                            cmderr.printf("Conflicting -startup option.\n");
                            return null;
                        }
                        cmdlineStartup = "";
                        break;
                    default:
                        cmderr.printf("Unknown option: %s\n", arg);
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
        cmdout.printf("Usage:   jshell <options> <load files>\n");
        cmdout.printf("where possible options include:\n");
        cmdout.printf("  -classpath <path>          Specify where to find user class files\n");
        cmdout.printf("  -cp <path>                 Specify where to find user class files\n");
        cmdout.printf("  -startup <file>            One run replacement for the start-up definitions\n");
        cmdout.printf("  -nostartup                 Do not run the start-up definitions\n");
        cmdout.printf("  -help                      Print a synopsis of standard options\n");
        cmdout.printf("  -version                   Version information\n");
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
                .build();
        analysis = state.sourceCodeAnalysis();
        shutdownSubscription = state.onShutdown((JShell deadState) -> {
            if (deadState == state) {
                hard("State engine terminated.");
                hard("Restore definitions with: /reload restore");
                live = false;
            }
        });
        live = true;

        if (cmdlineClasspath != null) {
            state.addToClasspath(cmdlineClasspath);
        }

        String start;
        if (cmdlineStartup == null) {
            start = PREFS.get(STARTUP_KEY, "<nada>");
            if (start.equals("<nada>")) {
                start = DEFAULT_STARTUP;
                PREFS.put(STARTUP_KEY, DEFAULT_STARTUP);
            }
        } else {
            start = cmdlineStartup;
        }
        try (IOContext suin = new FileScannerIOContext(new StringReader(start))) {
            run(suin);
        } catch (Exception ex) {
            hard("Unexpected exception reading start-up: %s\n", ex);
        }
        currentNameSpace = mainNamespace;
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
                if (displayPrompt) {
                    prompt = testPrompt
                                    ? incomplete.isEmpty()
                                            ? "\u0005" //ENQ
                                            : "\u0006" //ACK
                                    : incomplete.isEmpty()
                                            ? feedback() == Feedback.Concise
                                                    ? "-> "
                                                    : "\n-> "
                                            : ">> "
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
            hard("Unexpected exception: %s\n", ex);
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
        Command[] candidates = findCommand(cmd, c -> c.kind != CommandKind.HELP_ONLY);
        if (candidates.length == 0) {
            if (!rerunHistoryEntryById(cmd.substring(1))) {
                hard("No such command or snippet id: %s", cmd);
                fluff("Type /help for help.");
            }
        } else if (candidates.length == 1) {
            Command command = candidates[0];

            // If comand was successful and is of a replayable kind, add it the replayable history
            if (command.run.apply(arg) && command.kind == CommandKind.REPLAY) {
                addToReplayHistory((command.command + " " + arg).trim());
            }
        } else {
            hard("Command: %s is ambiguous: %s", cmd, Arrays.stream(candidates).map(c -> c.command).collect(Collectors.joining(", ")));
            fluff("Type /help for help.");
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
        public final String params;
        public final String description;
        public final Function<String,Boolean> run;
        public final CompletionProvider completions;
        public final CommandKind kind;

        public Command(String command, String params, String description, Function<String,Boolean> run, CompletionProvider completions) {
            this(command, params, description, run, completions, CommandKind.NORMAL);
        }

        public Command(String command, String params, String description, Function<String,Boolean> run, CompletionProvider completions, CommandKind kind) {
            this.command = command;
            this.params = params;
            this.description = description;
            this.run = run;
            this.completions = completions;
            this.kind = kind;
        }

    }

    interface CompletionProvider {
        List<Suggestion> completionSuggestions(String input, int cursor, int[] anchor);
    }

    enum CommandKind {
        NORMAL,
        REPLAY,
        HIDDEN,
        HELP_ONLY;
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

    // Table of commands -- with command forms, argument kinds, help message, implementation, ...

    {
        registerCommand(new Command("/list", "[all|start|history|<name or id>]", "list the source you have typed",
                                    arg -> cmdList(arg),
                                    editKeywordCompletion()));
        registerCommand(new Command("/seteditor", "<executable>", "set the external editor command to use",
                                    arg -> cmdSetEditor(arg),
                                    EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/edit", "<name or id>", "edit a source entry referenced by name or id",
                                    arg -> cmdEdit(arg),
                                    editCompletion()));
        registerCommand(new Command("/drop", "<name or id>", "delete a source entry referenced by name or id",
                                    arg -> cmdDrop(arg),
                                    editCompletion(),
                                    CommandKind.REPLAY));
        registerCommand(new Command("/save", "[all|history|start] <file>", "save: <none> - current source;\n" +
                                                                           "      all - source including overwritten, failed, and start-up code;\n" +
                                                                           "      history - editing history;\n" +
                                                                           "      start - default start-up definitions",
                                    arg -> cmdSave(arg),
                                    saveCompletion()));
        registerCommand(new Command("/open", "<file>", "open a file as source input",
                                    arg -> cmdOpen(arg),
                                    FILE_COMPLETION_PROVIDER));
        registerCommand(new Command("/vars", null, "list the declared variables and their values",
                                    arg -> cmdVars(),
                                    EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/methods", null, "list the declared methods and their signatures",
                                    arg -> cmdMethods(),
                                    EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/classes", null, "list the declared classes",
                                    arg -> cmdClasses(),
                                    EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/imports", null, "list the imported items",
                                    arg -> cmdImports(),
                                    EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/exit", null, "exit the REPL",
                                    arg -> cmdExit(),
                                    EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/reset", null, "reset everything in the REPL",
                                    arg -> cmdReset(),
                                    EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/reload", "[restore] [quiet]", "reset and replay relevant history -- current or previous (restore)",
                                    arg -> cmdReload(arg),
                                    reloadCompletion()));
        registerCommand(new Command("/feedback", "<level>", "feedback information: off, concise, normal, verbose, default, or ?",
                                    arg -> cmdFeedback(arg),
                                    new FixedCompletionProvider("off", "concise", "normal", "verbose", "default", "?")));
        registerCommand(new Command("/prompt", null, "toggle display of a prompt",
                                    arg -> cmdPrompt(),
                                    EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/classpath", "<path>", "add a path to the classpath",
                                    arg -> cmdClasspath(arg),
                                    classPathCompletion(),
                                    CommandKind.REPLAY));
        registerCommand(new Command("/history", null, "history of what you have typed",
                                    arg -> cmdHistory(),
                                    EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/setstart", "<file>", "read file and set as the new start-up definitions",
                                    arg -> cmdSetStart(arg),
                                    FILE_COMPLETION_PROVIDER));
        registerCommand(new Command("/debug", "", "toggle debugging of the REPL",
                                    arg -> cmdDebug(arg),
                                    EMPTY_COMPLETION_PROVIDER,
                                    CommandKind.HIDDEN));
        registerCommand(new Command("/help", "", "this help message",
                                    arg -> cmdHelp(),
                                    EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/?", "", "this help message",
                                    arg -> cmdHelp(),
                                    EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/!", "", "re-run last snippet",
                                    arg -> cmdUseHistoryEntry(-1),
                                    EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/<id>", "", "re-run snippet by id",
                                    arg -> { throw new IllegalStateException(); },
                                    EMPTY_COMPLETION_PROVIDER,
                                    CommandKind.HELP_ONLY));
        registerCommand(new Command("/-<n>", "", "re-run n-th previous snippet",
                                    arg -> { throw new IllegalStateException(); },
                                    EMPTY_COMPLETION_PROVIDER,
                                    CommandKind.HELP_ONLY));
    }

    public List<Suggestion> commandCompletionSuggestions(String code, int cursor, int[] anchor) {
        String prefix = code.substring(0, cursor);
        int space = prefix.indexOf(' ');
        Stream<Suggestion> result;

        if (space == (-1)) {
            result = commands.values()
                             .stream()
                             .distinct()
                             .filter(cmd -> cmd.kind != CommandKind.HIDDEN && cmd.kind != CommandKind.HELP_ONLY)
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
                return command.description;
            }
        }

        return null;
    }

    // --- Command implementations ---

    boolean cmdSetEditor(String arg) {
        if (arg.isEmpty()) {
            hard("/seteditor requires a path argument");
            return false;
        } else {
            editor = arg;
            fluff("Editor set to: %s", arg);
            return true;
        }
    }

    boolean cmdClasspath(String arg) {
        if (arg.isEmpty()) {
            hard("/classpath requires a path argument");
            return false;
        } else {
            state.addToClasspath(toPathResolvingUserHome(arg).toString());
            fluff("Path %s added to classpath", arg);
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
            PREFS.put(REPLAY_RESTORE_KEY, replayableHistory.stream().reduce(
                    (a, b) -> a + RECORD_SEPARATOR + b).get());
        }
        fluff("Goodbye\n");
        return true;
    }

    private boolean cmdFeedback(String arg) {
        switch (arg) {
            case "":
            case "d":
            case "default":
                feedback = Feedback.Default;
                break;
            case "o":
            case "off":
                feedback = Feedback.Off;
                break;
            case "c":
            case "concise":
                feedback = Feedback.Concise;
                break;
            case "n":
            case "normal":
                feedback = Feedback.Normal;
                break;
            case "v":
            case "verbose":
                feedback = Feedback.Verbose;
                break;
            default:
                hard("Follow /feedback with of the following:");
                hard("  off       (errors and critical output only)");
                hard("  concise");
                hard("  normal");
                hard("  verbose");
                hard("  default");
                hard("You may also use just the first letter, for example: /f c");
                hard("In interactive mode 'default' is the same as 'normal', from a file it is the same as 'off'");
                return false;
        }
        fluff("Feedback mode: %s", feedback.name().toLowerCase());
        return true;
    }

    boolean cmdHelp() {
        int synopsisLen = 0;
        Map<String, String> synopsis2Description = new LinkedHashMap<>();
        for (Command cmd : new LinkedHashSet<>(commands.values())) {
            if (cmd.kind == CommandKind.HIDDEN)
                continue;
            StringBuilder synopsis = new StringBuilder();
            synopsis.append(cmd.command);
            if (cmd.params != null)
                synopsis.append(" ").append(cmd.params);
            synopsis2Description.put(synopsis.toString(), cmd.description);
            synopsisLen = Math.max(synopsisLen, synopsis.length());
        }
        cmdout.println("Type a Java language expression, statement, or declaration.");
        cmdout.println("Or type one of the following commands:\n");
        for (Entry<String, String> e : synopsis2Description.entrySet()) {
            cmdout.print(String.format("%-" + synopsisLen + "s", e.getKey()));
            cmdout.print(" -- ");
            String indentedNewLine = System.getProperty("line.separator") +
                                     String.format("%-" + (synopsisLen + 4) + "s", "");
            cmdout.println(e.getValue().replace("\n", indentedNewLine));
        }
        cmdout.println();
        cmdout.println("Supported shortcuts include:");
        cmdout.println("<tab>       -- show possible completions for the current text");
        cmdout.println("Shift-<tab> -- for current method or constructor invocation, show a synopsis of the method/constructor");
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

    private boolean mainActive(Snippet sn) {
        return notInStartUp(sn) && state.status(sn).isActive;
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
        } else if (arg.isEmpty()) {
            // Default is all active user snippets
            return snippets.stream()
                    .filter(this::mainActive);
        } else {
            Stream<Snippet> result =
                    nonEmptyStream(
                            () -> snippets.stream(),
                            // look for active user declarations matching the name
                            sn -> mainActive(sn) && matchingDeclaration(sn, arg),
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
            hard("In the /drop argument, please specify an import, variable, method, or class to drop.");
            hard("Specify by id or name. Use /list to see ids. Use /reset to reset all state.");
            return false;
        }
        Stream<Snippet> stream = argToSnippets(arg, false);
        if (stream == null) {
            hard("No definition or id named %s found.  See /classes, /methods, /vars, or /list", arg);
            return false;
        }
        List<Snippet> snippets = stream
                .filter(sn -> state.status(sn).isActive && sn instanceof PersistentSnippet)
                .collect(toList());
        if (snippets.isEmpty()) {
            hard("The argument did not specify an active import, variable, method, or class to drop.");
            return false;
        }
        if (snippets.size() > 1) {
            hard("The argument references more than one import, variable, method, or class.");
            hard("Try again with one of the ids below:");
            for (Snippet sn : snippets) {
                cmdout.printf("%4s : %s\n", sn.id(), sn.source().replace("\n", "\n       "));
            }
            return false;
        }
        PersistentSnippet psn = (PersistentSnippet) snippets.get(0);
        state.drop(psn).forEach(this::handleEvent);
        return true;
    }

    private boolean cmdEdit(String arg) {
        Stream<Snippet> stream = argToSnippets(arg, true);
        if (stream == null) {
            hard("No definition or id named %s found.  See /classes, /methods, /vars, or /list", arg);
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
            EditPad.edit(errorHandler, src, saveHandler);
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
                    hard("Resetting...");
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
            // Check if there are any definitions at all
            if (argToSnippets("", false).iterator().hasNext()) {
                hard("No definition or id named %s found.  Try /list without arguments.", arg);
            } else {
                hard("No definition or id named %s found.  There are no active definitions.", arg);
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
        if (filename.isEmpty()) {
            hard("The /open command requires a filename argument.");
            return false;
        } else {
            try {
                run(new FileScannerIOContext(toPathResolvingUserHome(filename).toString()));
            } catch (FileNotFoundException e) {
                hard("File '%s' is not found: %s", filename, e.getMessage());
                return false;
            } catch (Exception e) {
                hard("Exception while reading file: %s", e);
                return false;
            }
        }
        return true;
    }

    private boolean cmdPrompt() {
        displayPrompt = !displayPrompt;
        fluff("Prompt will %sdisplay. Use /prompt to toggle.", displayPrompt ? "" : "NOT ");
        concise("Prompt: %s", displayPrompt ? "on" : "off");
        return true;
    }

    private boolean cmdReset() {
        live = false;
        fluff("Resetting state.");
        return true;
    }

    private boolean cmdReload(String arg) {
        Iterable<String> history = replayableHistory;
        boolean echo = true;
        if (arg.length() > 0) {
            if ("restore".startsWith(arg)) {
                if (replayableHistoryPrevious == null) {
                    hard("No previous history to restore\n", arg);
                    return false;
                }
                history = replayableHistoryPrevious;
            } else if ("quiet".startsWith(arg)) {
                echo = false;
            } else {
                hard("Invalid argument to reload command: %s\nUse 'restore', 'quiet', or no argument\n", arg);
                return false;
            }
        }
        fluff("Restarting and restoring %s.",
                history == replayableHistoryPrevious
                        ? "from previous state"
                        : "state");
        resetState();
        run(new ReloadIOContext(history,
                echo? cmdout : null));
        return true;
    }

    private boolean cmdSave(String arg_filename) {
        Matcher mat = HISTORY_ALL_START_FILENAME.matcher(arg_filename);
        if (!mat.find()) {
            hard("Malformed argument to the /save command: %s", arg_filename);
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
            hard("The /save command requires a filename argument.");
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
            hard("File '%s' for save is not accessible: %s", filename, e.getMessage());
            return false;
        } catch (Exception e) {
            hard("Exception while saving: %s", e);
            return false;
        }
        return true;
    }

    private boolean cmdSetStart(String filename) {
        if (filename.isEmpty()) {
            hard("The /setstart command requires a filename argument.");
        } else {
            try {
                byte[] encoded = Files.readAllBytes(toPathResolvingUserHome(filename));
                String init = new String(encoded);
                PREFS.put(STARTUP_KEY, init);
            } catch (AccessDeniedException e) {
                hard("File '%s' for /setstart is not accessible.", filename);
                return false;
            } catch (NoSuchFileException e) {
                hard("File '%s' for /setstart is not found.", filename);
                return false;
            } catch (Exception e) {
                hard("Exception while reading start set file: %s", e);
                return false;
            }
        }
        return true;
    }

    private boolean cmdVars() {
        for (VarSnippet vk : state.variables()) {
            String val = state.status(vk) == Status.VALID
                    ? state.varValue(vk)
                    : "(not-active)";
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
            hard("Cannot find snippet %d", index + 1);
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

    void printDiagnostics(String source, List<Diag> diagnostics, boolean embed) {
        String padding = embed? "    " : "";
        for (Diag diag : diagnostics) {
            //assert diag.getSource().equals(source);

            if (!embed) {
                if (diag.isError()) {
                    hard("Error:");
                } else {
                    hard("Warning:");
                }
            }

            for (String line : diag.getMessage(null).split("\\r?\\n")) {
                if (!line.trim().startsWith("location:")) {
                    hard("%s%s", padding, line);
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
            fluff("%s%s", padding, source.substring(pstartl, pendl));

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
            fluff("%s%s", padding, sb.toString());

            debug("printDiagnostics start-pos = %d ==> %d -- wrap = %s", diag.getStartPosition(), start, this);
            debug("Code: %s", diag.getCode());
            debug("Pos: %d (%d - %d)", diag.getPosition(),
                    diag.getStartPosition(), diag.getEndPosition());
        }
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
            printDiagnostics(source, diagnostics, false);
            if (ste.status().isActive) {
                if (ste.exception() != null) {
                    if (ste.exception() instanceof EvalException) {
                        printEvalException((EvalException) ste.exception());
                        return true;
                    } else if (ste.exception() instanceof UnresolvedReferenceException) {
                        printUnresolved((UnresolvedReferenceException) ste.exception());
                    } else {
                        hard("Unexpected execution exception: %s", ste.exception());
                        return true;
                    }
                } else {
                    displayDeclarationAndValue(ste, false, ste.value());
                }
            } else if (ste.status() == Status.REJECTED) {
                if (diagnostics.isEmpty()) {
                    hard("Failed.");
                }
                return true;
            }
        } else if (ste.status() == Status.REJECTED) {
            //TODO -- I don't believe updates can cause failures any more
            hard("Caused failure of dependent %s --", ((DeclarationSnippet) sn).name());
            printDiagnostics(source, diagnostics, true);
        } else {
            // Update
            SubKind subkind = sn.subKind();
            if (sn instanceof DeclarationSnippet
                    && (feedback() == Feedback.Verbose
                    || ste.status() == Status.OVERWRITTEN
                    || subkind == SubKind.VAR_DECLARATION_SUBKIND
                    || subkind == SubKind.VAR_DECLARATION_WITH_INITIALIZER_SUBKIND)) {
                // Under the conditions, display update information
                displayDeclarationAndValue(ste, true, null);
                List<Diag> other = errorsOnly(diagnostics);
                if (other.size() > 0) {
                    printDiagnostics(source, other, true);
                }
            }
        }
        return false;
    }

    @SuppressWarnings("fallthrough")
    private void displayDeclarationAndValue(SnippetEvent ste, boolean update, String value) {
        Snippet key = ste.snippet();
        String declared;
        Status status = ste.status();
        switch (status) {
            case VALID:
            case RECOVERABLE_DEFINED:
            case RECOVERABLE_NOT_DEFINED:
                if (ste.previousStatus().isActive) {
                    declared = ste.isSignatureChange()
                        ? "Replaced"
                        : "Modified";
                } else {
                    declared = "Added";
                }
                break;
            case OVERWRITTEN:
                declared = "Overwrote";
                break;
            case DROPPED:
                declared = "Dropped";
                break;
            case REJECTED:
                declared = "Rejected";
                break;
            case NONEXISTENT:
            default:
                // Should not occur
                declared = ste.previousStatus().toString() + "=>" + status.toString();
        }
        if (update) {
            declared = "  Update " + declared.toLowerCase();
        }
        String however;
        if (key instanceof DeclarationSnippet && (status == Status.RECOVERABLE_DEFINED || status == Status.RECOVERABLE_NOT_DEFINED)) {
            String cannotUntil = (status == Status.RECOVERABLE_NOT_DEFINED)
                    ? " cannot be referenced until"
                    : " cannot be invoked until";
            however = (update? " which" : ", however, it") + cannotUntil + unresolved((DeclarationSnippet) key);
        } else {
            however = "";
        }
        switch (key.subKind()) {
            case CLASS_SUBKIND:
                fluff("%s class %s%s", declared, ((TypeDeclSnippet) key).name(), however);
                break;
            case INTERFACE_SUBKIND:
                fluff("%s interface %s%s", declared, ((TypeDeclSnippet) key).name(), however);
                break;
            case ENUM_SUBKIND:
                fluff("%s enum %s%s", declared, ((TypeDeclSnippet) key).name(), however);
                break;
            case ANNOTATION_TYPE_SUBKIND:
                fluff("%s annotation interface %s%s", declared, ((TypeDeclSnippet) key).name(), however);
                break;
            case METHOD_SUBKIND:
                fluff("%s method %s(%s)%s", declared, ((MethodSnippet) key).name(),
                        ((MethodSnippet) key).parameterTypes(), however);
                break;
            case VAR_DECLARATION_SUBKIND:
                if (!update) {
                    VarSnippet vk = (VarSnippet) key;
                    if (status == Status.RECOVERABLE_NOT_DEFINED) {
                        fluff("%s variable %s%s", declared, vk.name(), however);
                    } else {
                        fluff("%s variable %s of type %s%s", declared, vk.name(), vk.typeName(), however);
                    }
                    break;
                }
            // Fall through
            case VAR_DECLARATION_WITH_INITIALIZER_SUBKIND: {
                VarSnippet vk = (VarSnippet) key;
                if (status == Status.RECOVERABLE_NOT_DEFINED) {
                    if (!update) {
                        fluff("%s variable %s%s", declared, vk.name(), however);
                        break;
                    }
                } else if (update) {
                    if (ste.isSignatureChange()) {
                        hard("%s variable %s, reset to null", declared, vk.name());
                    }
                } else {
                    fluff("%s variable %s of type %s with initial value %s",
                            declared, vk.name(), vk.typeName(), value);
                    concise("%s : %s", vk.name(), value);
                }
                break;
            }
            case TEMP_VAR_EXPRESSION_SUBKIND: {
                VarSnippet vk = (VarSnippet) key;
                if (update) {
                    hard("%s temporary variable %s, reset to null", declared, vk.name());
                 } else {
                    fluff("Expression value is: %s", (value));
                    fluff("  assigned to temporary variable %s of type %s", vk.name(), vk.typeName());
                    concise("%s : %s", vk.name(), value);
                }
                break;
            }
            case OTHER_EXPRESSION_SUBKIND:
                fluff("Expression value is: %s", (value));
                break;
            case VAR_VALUE_SUBKIND: {
                ExpressionSnippet ek = (ExpressionSnippet) key;
                fluff("Variable %s of type %s has value %s", ek.name(), ek.typeName(), (value));
                concise("%s : %s", ek.name(), value);
                break;
            }
            case ASSIGNMENT_SUBKIND: {
                ExpressionSnippet ek = (ExpressionSnippet) key;
                fluff("Variable %s has been assigned the value %s", ek.name(), (value));
                concise("%s : %s", ek.name(), value);
                break;
            }
        }
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
                    ? "Native Method"
                    : fileName == null
                            ? "Unknown Source"
                            : lineNumber >= 0
                                    ? fileName + ":" + lineNumber
                                    : fileName;
            hard("      at %s(%s)", sb, loc);

        }
    }
    //where
    void printUnresolved(UnresolvedReferenceException ex) {
        MethodSnippet corralled =  ex.getMethodSnippet();
        List<Diag> otherErrors = errorsOnly(state.diagnostics(corralled));
        StringBuilder sb = new StringBuilder();
        if (otherErrors.size() > 0) {
            if (state.unresolvedDependencies(corralled).size() > 0) {
                sb.append(" and");
            }
            if (otherErrors.size() == 1) {
                sb.append(" this error is addressed --");
            } else {
                sb.append(" these errors are addressed --");
            }
        } else {
            sb.append(".");
        }

        hard("Attempted to call %s which cannot be invoked until%s", corralled.name(),
                unresolved(corralled), sb.toString());
        if (otherErrors.size() > 0) {
            printDiagnostics(corralled.source(), otherErrors, true);
        }
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
    //where
    String unresolved(DeclarationSnippet key) {
        List<String> unr = state.unresolvedDependencies(key);
        StringBuilder sb = new StringBuilder();
        int fromLast = unr.size();
        if (fromLast > 0) {
            sb.append(" ");
        }
        for (String u : unr) {
            --fromLast;
            sb.append(u);
            if (fromLast == 0) {
                // No suffix
            } else if (fromLast == 1) {
                sb.append(", and ");
            } else {
                sb.append(", ");
            }
        }
        switch (unr.size()) {
            case 0:
                break;
            case 1:
                sb.append(" is declared");
                break;
            default:
                sb.append(" are declared");
                break;
        }
        return sb.toString();
    }

    enum Feedback {
        Default,
        Off,
        Concise,
        Normal,
        Verbose
    }

    Feedback feedback() {
        if (feedback == Feedback.Default) {
            return input == null || input.interactiveOutput() ? Feedback.Normal : Feedback.Off;
        }
        return feedback;
    }

    boolean notInStartUp(Snippet sn) {
        return mapSnippet.get(sn).space != startNamespace;
    }

    /** The current version number as a string.
     */
    static String version() {
        return version("release");  // mm.nn.oo[-milestone]
    }

    /** The current full version number as a string.
     */
    static String fullVersion() {
        return version("full"); // mm.mm.oo[-milestone]-build
    }

    private static final String versionRBName = "jdk.internal.jshell.tool.resources.version";
    private static ResourceBundle versionRB;

    private static String version(String key) {
        if (versionRB == null) {
            try {
                versionRB = ResourceBundle.getBundle(versionRBName);
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
