/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jshell.SourceCodeAnalysis.Documentation;
import jdk.jshell.SourceCodeAnalysis.QualifiedNames;
import jdk.jshell.SourceCodeAnalysis.Suggestion;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.shellsupport.doc.JavadocFormatter;
import jdk.internal.jline.NoInterruptUnixTerminal;
import jdk.internal.jline.Terminal;
import jdk.internal.jline.TerminalFactory;
import jdk.internal.jline.TerminalSupport;
import jdk.internal.jline.WindowsTerminal;
import jdk.internal.jline.console.ConsoleReader;
import jdk.internal.jline.console.KeyMap;
import jdk.internal.jline.console.Operation;
import jdk.internal.jline.console.UserInterruptException;
import jdk.internal.jline.console.history.History;
import jdk.internal.jline.console.history.MemoryHistory;
import jdk.internal.jline.extra.EditingHistory;
import jdk.internal.jline.internal.NonBlockingInputStream;
import jdk.internal.jshell.tool.StopDetectingInputStream.State;
import jdk.internal.misc.Signal;
import jdk.internal.misc.Signal.Handler;

class ConsoleIOContext extends IOContext {

    private static final String HISTORY_LINE_PREFIX = "HISTORY_LINE_";

    final JShellTool repl;
    final StopDetectingInputStream input;
    final ConsoleReader in;
    final EditingHistory history;
    final MemoryHistory userInputHistory = new MemoryHistory();

    String prefix = "";

    ConsoleIOContext(JShellTool repl, InputStream cmdin, PrintStream cmdout) throws Exception {
        this.repl = repl;
        this.input = new StopDetectingInputStream(() -> repl.stop(), ex -> repl.hard("Error on input: %s", ex));
        Terminal term;
        if (System.getProperty("test.jdk") != null) {
            term = new TestTerminal(input);
        } else if (System.getProperty("os.name").toLowerCase(Locale.US).contains(TerminalFactory.WINDOWS)) {
            term = new JShellWindowsTerminal(input);
        } else {
            term = new JShellUnixTerminal(input);
        }
        term.init();
        List<CompletionTask> completionTODO = new ArrayList<>();
        in = new ConsoleReader(cmdin, cmdout, term) {
            @Override public KeyMap getKeys() {
                return new CheckCompletionKeyMap(super.getKeys(), completionTODO);
            }
            @Override
            protected boolean complete() throws IOException {
                return ConsoleIOContext.this.complete(completionTODO);
            }
        };
        in.setExpandEvents(false);
        in.setHandleUserInterrupt(true);
        List<String> persistenHistory = Stream.of(repl.prefs.keys())
                                              .filter(key -> key.startsWith(HISTORY_LINE_PREFIX))
                                              .sorted()
                                              .map(key -> repl.prefs.get(key))
                                              .collect(Collectors.toList());
        in.setHistory(history = new EditingHistory(in, persistenHistory) {
            @Override protected boolean isComplete(CharSequence input) {
                return repl.analysis.analyzeCompletion(input.toString()).completeness().isComplete();
            }
        });
        in.setBellEnabled(true);
        in.setCopyPasteDetection(true);
        bind(FIXES_SHORTCUT, (Runnable) () -> fixes());
        try {
            Signal.handle(new Signal("CONT"), new Handler() {
                @Override public void handle(Signal sig) {
                    try {
                        in.getTerminal().reset();
                        in.redrawLine();
                        in.flush();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
        } catch (IllegalArgumentException ignored) {
            //the CONT signal does not exist on this platform
        }
    }

    @Override
    public String readLine(String prompt, String prefix) throws IOException, InputInterruptedException {
        this.prefix = prefix;
        try {
            return in.readLine(prompt);
        } catch (UserInterruptException ex) {
            throw (InputInterruptedException) new InputInterruptedException().initCause(ex);
        }
    }

    @Override
    public boolean interactiveOutput() {
        return true;
    }

    @Override
    public Iterable<String> currentSessionHistory() {
        return history.currentSessionEntries();
    }

    @Override
    public void close() throws IOException {
        //save history:
        for (String key : repl.prefs.keys()) {
            if (key.startsWith(HISTORY_LINE_PREFIX)) {
                repl.prefs.remove(key);
            }
        }
        Collection<? extends String> savedHistory = history.save();
        if (!savedHistory.isEmpty()) {
            int len = (int) Math.ceil(Math.log10(savedHistory.size()+1));
            String format = HISTORY_LINE_PREFIX + "%0" + len + "d";
            int index = 0;
            for (String historyLine : savedHistory) {
                repl.prefs.put(String.format(format, index++), historyLine);
            }
        }
        repl.prefs.flush();
        in.shutdown();
        try {
            in.getTerminal().restore();
        } catch (Exception ex) {
            throw new IOException(ex);
        }
        input.shutdown();
    }

    private void bind(String shortcut, Object action) {
        KeyMap km = in.getKeys();
        for (int i = 0; i < shortcut.length(); i++) {
            Object value = km.getBound(Character.toString(shortcut.charAt(i)));
            if (value instanceof KeyMap) {
                km = (KeyMap) value;
            } else {
                km.bind(shortcut.substring(i), action);
            }
        }
    }

    private static final String FIXES_SHORTCUT = "\033\133\132"; //Shift-TAB

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final String LINE_SEPARATORS2 = LINE_SEPARATOR + LINE_SEPARATOR;

    @SuppressWarnings("fallthrough")
    private boolean complete(List<CompletionTask> todo) {
        //The completion has multiple states (invoked by subsequent presses of <tab>).
        //On the first invocation in a given sequence, all steps are precomputed
        //and placed into the todo list. The todo list is then followed on both the first
        //and subsequent <tab> presses:
        try {
            String text = in.getCursorBuffer().toString();
            int cursor = in.getCursorBuffer().cursor;
            if (todo.isEmpty()) {
                int[] anchor = new int[] {-1};
                List<Suggestion> suggestions;
                List<String> doc;
                boolean command = prefix.isEmpty() && text.trim().startsWith("/");
                if (command) {
                    suggestions = repl.commandCompletionSuggestions(text, cursor, anchor);
                    doc = repl.commandDocumentation(text, cursor, true);
                } else {
                    int prefixLength = prefix.length();
                    suggestions = repl.analysis.completionSuggestions(prefix + text, cursor + prefixLength, anchor);
                    anchor[0] -= prefixLength;
                    doc = repl.analysis.documentation(prefix + text, cursor + prefix.length(), false)
                                       .stream()
                                       .map(Documentation::signature)
                                       .collect(Collectors.toList());
                }
                long smartCount = suggestions.stream().filter(Suggestion::matchesType).count();
                boolean hasSmart = smartCount > 0 && smartCount <= in.getAutoprintThreshold();
                boolean hasBoth = hasSmart &&
                                  suggestions.stream()
                                             .map(s -> s.matchesType())
                                             .distinct()
                                             .count() == 2;
                boolean tooManyItems = suggestions.size() > in.getAutoprintThreshold();
                CompletionTask ordinaryCompletion = new OrdinaryCompletionTask(suggestions, anchor[0], !command && !doc.isEmpty(), hasSmart);
                CompletionTask allCompletion = new AllSuggestionsCompletionTask(suggestions, anchor[0]);

                //the main decission tree:
                if (command) {
                    CompletionTask shortDocumentation = new CommandSynopsisTask(doc);
                    CompletionTask fullDocumentation = new CommandFullDocumentationTask(todo);

                    if (!doc.isEmpty()) {
                        if (tooManyItems) {
                            todo.add(new NoopCompletionTask());
                            todo.add(allCompletion);
                        } else {
                            todo.add(ordinaryCompletion);
                        }
                        todo.add(shortDocumentation);
                        todo.add(fullDocumentation);
                    } else {
                        todo.add(new NoSuchCommandCompletionTask());
                    }
                } else {
                    if (doc.isEmpty()) {
                        if (hasSmart) {
                            todo.add(ordinaryCompletion);
                        } else if (tooManyItems) {
                            todo.add(new NoopCompletionTask());
                        }
                        if (!hasSmart || hasBoth) {
                            todo.add(allCompletion);
                        }
                    } else {
                        CompletionTask shortDocumentation = new ExpressionSignaturesTask(doc);
                        CompletionTask fullDocumentation = new ExpressionJavadocTask(todo);

                        if (hasSmart) {
                            todo.add(ordinaryCompletion);
                        }
                        todo.add(shortDocumentation);
                        if (!hasSmart || hasBoth) {
                            todo.add(allCompletion);
                        }
                        if (tooManyItems) {
                            todo.add(todo.size() - 1, fullDocumentation);
                        } else {
                            todo.add(fullDocumentation);
                        }
                    }
                }
            }

            boolean success = false;
            boolean repaint = true;

            OUTER: while (!todo.isEmpty()) {
                CompletionTask.Result result = todo.remove(0).perform(text, cursor);

                switch (result) {
                    case CONTINUE:
                        break;
                    case SKIP_NOREPAINT:
                        repaint = false;
                    case SKIP:
                        todo.clear();
                        //intentional fall-through
                    case FINISH:
                        success = true;
                        //intentional fall-through
                    case NO_DATA:
                        if (!todo.isEmpty()) {
                            in.println();
                            in.println(todo.get(0).description());
                        }
                        break OUTER;
                }
            }

            if (repaint) {
                in.redrawLine();
                in.flush();
            }

            return success;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private CompletionTask.Result doPrintFullDocumentation(List<CompletionTask> todo, List<String> doc, boolean command) {
        if (doc != null && !doc.isEmpty()) {
            Terminal term = in.getTerminal();
            int pageHeight = term.getHeight() - NEEDED_LINES;
            List<CompletionTask> thisTODO = new ArrayList<>();

            for (Iterator<String> docIt = doc.iterator(); docIt.hasNext(); ) {
                String currentDoc = docIt.next();
                String[] lines = currentDoc.split("\n");
                int firstLine = 0;

                while (firstLine < lines.length) {
                    boolean first = firstLine == 0;
                    String[] thisPageLines =
                            Arrays.copyOfRange(lines,
                                               firstLine,
                                               Math.min(firstLine + pageHeight, lines.length));

                    thisTODO.add(new CompletionTask() {
                        @Override
                        public String description() {
                            String key =  !first ? "jshell.console.see.next.page"
                                                 : command ? "jshell.console.see.next.command.doc"
                                                           : "jshell.console.see.next.javadoc";

                            return repl.getResourceString(key);
                        }

                        @Override
                        public Result perform(String text, int cursor) throws IOException {
                            in.println();
                            for (String line : thisPageLines) {
                                in.println(line);
                            }
                            return Result.FINISH;
                        }
                    });

                    firstLine += pageHeight;
                }
            }

            todo.addAll(0, thisTODO);

            return CompletionTask.Result.CONTINUE;
        }

        return CompletionTask.Result.FINISH;
    }
    //where:
        private static final int NEEDED_LINES = 4;

    private static String commonPrefix(String str1, String str2) {
        for (int i = 0; i < str2.length(); i++) {
            if (!str1.startsWith(str2.substring(0, i + 1))) {
                return str2.substring(0, i);
            }
        }

        return str2;
    }

    private interface CompletionTask {
        public String description();
        public Result perform(String text, int cursor) throws IOException;

        enum Result {
            NO_DATA,
            CONTINUE,
            FINISH,
            SKIP,
            SKIP_NOREPAINT;
        }
    }

    private final class NoopCompletionTask implements CompletionTask {

        @Override
        public String description() {
            throw new UnsupportedOperationException("Should not get here.");
        }

        @Override
        public Result perform(String text, int cursor) throws IOException {
            return Result.FINISH;
        }

    }

    private final class NoSuchCommandCompletionTask implements CompletionTask {

        @Override
        public String description() {
            throw new UnsupportedOperationException("Should not get here.");
        }

        @Override
        public Result perform(String text, int cursor) throws IOException {
            in.println();
            in.println(repl.getResourceString("jshell.console.no.such.command"));
            in.println();
            return Result.SKIP;
        }

    }

    private final class OrdinaryCompletionTask implements CompletionTask {
        private final List<Suggestion> suggestions;
        private final int anchor;
        private final boolean cont;
        private final boolean smart;

        public OrdinaryCompletionTask(List<Suggestion> suggestions,
                                      int anchor,
                                      boolean cont,
                                      boolean smart) {
            this.suggestions = suggestions;
            this.anchor = anchor;
            this.cont = cont;
            this.smart = smart;
        }

        @Override
        public String description() {
            throw new UnsupportedOperationException("Should not get here.");
        }

        @Override
        public Result perform(String text, int cursor) throws IOException {
            List<CharSequence> toShow;

            if (smart) {
                toShow =
                    suggestions.stream()
                               .filter(Suggestion::matchesType)
                               .map(Suggestion::continuation)
                               .distinct()
                               .collect(Collectors.toList());
            } else {
                toShow =
                    suggestions.stream()
                               .map(Suggestion::continuation)
                               .distinct()
                               .collect(Collectors.toList());
            }

            if (toShow.isEmpty()) {
                return Result.CONTINUE;
            }

            Optional<String> prefix =
                    suggestions.stream()
                               .map(Suggestion::continuation)
                               .reduce(ConsoleIOContext::commonPrefix);

            String prefixStr = prefix.orElse("").substring(cursor - anchor);
            in.putString(prefixStr);

            boolean showItems = toShow.size() > 1 || smart;

            if (showItems) {
                in.println();
                in.printColumns(toShow);
            }

            if (!prefixStr.isEmpty())
                return showItems ? Result.SKIP : Result.SKIP_NOREPAINT;

            return cont ? Result.CONTINUE : Result.FINISH;
        }

    }

    private final class AllSuggestionsCompletionTask implements CompletionTask {
        private final List<Suggestion> suggestions;
        private final int anchor;

        public AllSuggestionsCompletionTask(List<Suggestion> suggestions,
                                            int anchor) {
            this.suggestions = suggestions;
            this.anchor = anchor;
        }

        @Override
        public String description() {
            if (suggestions.size() <= in.getAutoprintThreshold()) {
                return repl.getResourceString("jshell.console.completion.all.completions");
            } else {
                return repl.messageFormat("jshell.console.completion.all.completions.number", suggestions.size());
            }
        }

        @Override
        public Result perform(String text, int cursor) throws IOException {
            List<String> candidates =
                    suggestions.stream()
                               .map(Suggestion::continuation)
                               .distinct()
                               .collect(Collectors.toList());

            Optional<String> prefix =
                    candidates.stream()
                              .reduce(ConsoleIOContext::commonPrefix);

            String prefixStr = prefix.map(str -> str.substring(cursor - anchor)).orElse("");
            in.putString(prefixStr);
            if (candidates.size() > 1) {
                in.println();
                in.printColumns(candidates);
            }
            return suggestions.isEmpty() ? Result.NO_DATA : Result.FINISH;
        }

    }

    private final class CommandSynopsisTask implements CompletionTask {

        private final List<String> synopsis;

        public CommandSynopsisTask(List<String> synposis) {
            this.synopsis = synposis;
        }

        @Override
        public String description() {
            return repl.getResourceString("jshell.console.see.synopsis");
        }

        @Override
        public Result perform(String text, int cursor) throws IOException {
            try {
                in.println();
                in.println(synopsis.stream()
                                   .map(l -> l.replaceAll("\n", LINE_SEPARATOR))
                                   .collect(Collectors.joining(LINE_SEPARATORS2)));
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            return Result.FINISH;
        }

    }

    private final class CommandFullDocumentationTask implements CompletionTask {

        private final List<CompletionTask> todo;

        public CommandFullDocumentationTask(List<CompletionTask> todo) {
            this.todo = todo;
        }

        @Override
        public String description() {
            return repl.getResourceString("jshell.console.see.full.documentation");
        }

        @Override
        public Result perform(String text, int cursor) throws IOException {
            List<String> fullDoc = repl.commandDocumentation(text, cursor, false);
            return doPrintFullDocumentation(todo, fullDoc, true);
        }

    }

    private final class ExpressionSignaturesTask implements CompletionTask {

        private final List<String> doc;

        public ExpressionSignaturesTask(List<String> doc) {
            this.doc = doc;
        }

        @Override
        public String description() {
            throw new UnsupportedOperationException("Should not get here.");
        }

        @Override
        public Result perform(String text, int cursor) throws IOException {
            in.println();
            in.println(repl.getResourceString("jshell.console.completion.current.signatures"));
            in.println(doc.stream().collect(Collectors.joining(LINE_SEPARATOR)));
            return Result.FINISH;
        }

    }

    private final class ExpressionJavadocTask implements CompletionTask {

        private final List<CompletionTask> todo;

        public ExpressionJavadocTask(List<CompletionTask> todo) {
            this.todo = todo;
        }

        @Override
        public String description() {
            return repl.getResourceString("jshell.console.see.documentation");
        }

        @Override
        public Result perform(String text, int cursor) throws IOException {
            //schedule showing javadoc:
            Terminal term = in.getTerminal();
            JavadocFormatter formatter = new JavadocFormatter(term.getWidth(),
                                                              term.isAnsiSupported());
            Function<Documentation, String> convertor = d -> formatter.formatJavadoc(d.signature(), d.javadoc()) +
                             (d.javadoc() == null ? repl.messageFormat("jshell.console.no.javadoc")
                                                  : "");
            List<String> doc = repl.analysis.documentation(prefix + text, cursor + prefix.length(), true)
                                            .stream()
                                            .map(convertor)
                                            .collect(Collectors.toList());
            return doPrintFullDocumentation(todo, doc, false);
        }

    }

    @Override
    public boolean terminalEditorRunning() {
        Terminal terminal = in.getTerminal();
        if (terminal instanceof SuspendableTerminal)
            return ((SuspendableTerminal) terminal).isRaw();
        return false;
    }

    @Override
    public void suspend() {
        Terminal terminal = in.getTerminal();
        if (terminal instanceof SuspendableTerminal)
            ((SuspendableTerminal) terminal).suspend();
    }

    @Override
    public void resume() {
        Terminal terminal = in.getTerminal();
        if (terminal instanceof SuspendableTerminal)
            ((SuspendableTerminal) terminal).resume();
    }

    @Override
    public void beforeUserCode() {
        synchronized (this) {
            inputBytes = null;
        }
        input.setState(State.BUFFER);
    }

    @Override
    public void afterUserCode() {
        input.setState(State.WAIT);
    }

    @Override
    public void replaceLastHistoryEntry(String source) {
        history.fullHistoryReplace(source);
    }

    private static final long ESCAPE_TIMEOUT = 100;

    private void fixes() {
        try {
            int c = in.readCharacter();

            if (c == (-1)) {
                return ;
            }

            for (FixComputer computer : FIX_COMPUTERS) {
                if (computer.shortcut == c) {
                    fixes(computer);
                    return ;
                }
            }

            readOutRemainingEscape(c);

            in.beep();
            in.println();
            in.println(repl.getResourceString("jshell.fix.wrong.shortcut"));
            in.redrawLine();
            in.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void readOutRemainingEscape(int c) throws IOException {
        if (c == '\033') {
            //escape, consume waiting input:
            InputStream inp = in.getInput();

            if (inp instanceof NonBlockingInputStream) {
                NonBlockingInputStream nbis = (NonBlockingInputStream) inp;

                while (nbis.isNonBlockingEnabled() && nbis.peek(ESCAPE_TIMEOUT) > 0) {
                    in.readCharacter();
                }
            }
        }
    }

    //compute possible options/Fixes based on the selected FixComputer, present them to the user,
    //and perform the selected one:
    private void fixes(FixComputer computer) {
        String input = prefix + in.getCursorBuffer().toString();
        int cursor = prefix.length() + in.getCursorBuffer().cursor;
        FixResult candidates = computer.compute(repl, input, cursor);

        try {
            final boolean printError = candidates.error != null && !candidates.error.isEmpty();
            if (printError) {
                in.println(candidates.error);
            }
            if (candidates.fixes.isEmpty()) {
                in.beep();
                if (printError) {
                    in.redrawLine();
                    in.flush();
                }
            } else if (candidates.fixes.size() == 1 && !computer.showMenu) {
                if (printError) {
                    in.redrawLine();
                    in.flush();
                }
                candidates.fixes.get(0).perform(in);
            } else {
                List<Fix> fixes = new ArrayList<>(candidates.fixes);
                fixes.add(0, new Fix() {
                    @Override
                    public String displayName() {
                        return repl.messageFormat("jshell.console.do.nothing");
                    }

                    @Override
                    public void perform(ConsoleReader in) throws IOException {
                        in.redrawLine();
                    }
                });

                Map<Character, Fix> char2Fix = new HashMap<>();
                in.println();
                for (int i = 0; i < fixes.size(); i++) {
                    Fix fix = fixes.get(i);
                    char2Fix.put((char) ('0' + i), fix);
                    in.println("" + i + ": " + fixes.get(i).displayName());
                }
                in.print(repl.messageFormat("jshell.console.choice"));
                in.flush();
                int read;

                read = in.readCharacter();

                Fix fix = char2Fix.get((char) read);

                if (fix == null) {
                    in.beep();
                    fix = fixes.get(0);
                }

                in.println();

                fix.perform(in);

                in.flush();
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private byte[] inputBytes;
    private int inputBytesPointer;

    @Override
    public synchronized int readUserInput() throws IOException {
        while (inputBytes == null || inputBytes.length <= inputBytesPointer) {
            boolean prevHandleUserInterrupt = in.getHandleUserInterrupt();
            History prevHistory = in.getHistory();

            try {
                input.setState(State.WAIT);
                in.setHandleUserInterrupt(true);
                in.setHistory(userInputHistory);
                inputBytes = (in.readLine("") + System.getProperty("line.separator")).getBytes();
                inputBytesPointer = 0;
            } catch (UserInterruptException ex) {
                throw new InterruptedIOException();
            } finally {
                in.setHistory(prevHistory);
                in.setHandleUserInterrupt(prevHandleUserInterrupt);
                input.setState(State.BUFFER);
            }
        }
        return inputBytes[inputBytesPointer++];
    }

    /**
     * A possible action which the user can choose to perform.
     */
    public interface Fix {
        /**
         * A name that should be shown to the user.
         */
        public String displayName();
        /**
         * Perform the given action.
         */
        public void perform(ConsoleReader in) throws IOException;
    }

    /**
     * A factory for {@link Fix}es.
     */
    public abstract static class FixComputer {
        private final char shortcut;
        private final boolean showMenu;

        /**
         * Construct a new FixComputer. {@code shortcut} defines the key which should trigger this FixComputer.
         * If {@code showMenu} is {@code false}, and this computer returns exactly one {@code Fix},
         * no options will be show to the user, and the given {@code Fix} will be performed.
         */
        public FixComputer(char shortcut, boolean showMenu) {
            this.shortcut = shortcut;
            this.showMenu = showMenu;
        }

        /**
         * Compute possible actions for the given code.
         */
        public abstract FixResult compute(JShellTool repl, String code, int cursor);
    }

    /**
     * A list of {@code Fix}es with a possible error that should be shown to the user.
     */
    public static class FixResult {
        public final List<Fix> fixes;
        public final String error;

        public FixResult(List<Fix> fixes, String error) {
            this.fixes = fixes;
            this.error = error;
        }
    }

    private static final FixComputer[] FIX_COMPUTERS = new FixComputer[] {
        new FixComputer('v', false) { //compute "Introduce variable" Fix:
            private void performToVar(ConsoleReader in, String type) throws IOException {
                in.redrawLine();
                in.setCursorPosition(0);
                in.putString(type + "  = ");
                in.setCursorPosition(in.getCursorBuffer().cursor - 3);
                in.flush();
            }

            @Override
            public FixResult compute(JShellTool repl, String code, int cursor) {
                String type = repl.analysis.analyzeType(code, cursor);
                if (type == null) {
                    return new FixResult(Collections.emptyList(), null);
                }
                List<Fix> fixes = new ArrayList<>();
                fixes.add(new Fix() {
                    @Override
                    public String displayName() {
                        return repl.messageFormat("jshell.console.create.variable");
                    }

                    @Override
                    public void perform(ConsoleReader in) throws IOException {
                        performToVar(in, type);
                    }
                });
                int idx = type.lastIndexOf(".");
                if (idx > 0) {
                    String stype = type.substring(idx + 1);
                    QualifiedNames res = repl.analysis.listQualifiedNames(stype, stype.length());
                    if (res.isUpToDate() && res.getNames().contains(type)
                            && !res.isResolvable()) {
                        fixes.add(new Fix() {
                            @Override
                            public String displayName() {
                                return "import: " + type + ". " +
                                        repl.messageFormat("jshell.console.create.variable");
                            }

                            @Override
                            public void perform(ConsoleReader in) throws IOException {
                                repl.processCompleteSource("import " + type + ";");
                                in.println("Imported: " + type);
                                performToVar(in, stype);
                            }
                        });
                    }
                }
                return new FixResult(fixes, null);
            }
        },
        new FixComputer('i', true) { //compute "Add import" Fixes:
            @Override
            public FixResult compute(JShellTool repl, String code, int cursor) {
                QualifiedNames res = repl.analysis.listQualifiedNames(code, cursor);
                List<Fix> fixes = new ArrayList<>();
                for (String fqn : res.getNames()) {
                    fixes.add(new Fix() {
                        @Override
                        public String displayName() {
                            return "import: " + fqn;
                        }

                        @Override
                        public void perform(ConsoleReader in) throws IOException {
                            repl.processCompleteSource("import " + fqn + ";");
                            in.println("Imported: " + fqn);
                            in.redrawLine();
                        }
                    });
                }
                if (res.isResolvable()) {
                    return new FixResult(Collections.emptyList(),
                            repl.messageFormat("jshell.console.resolvable"));
                } else {
                    String error = "";
                    if (fixes.isEmpty()) {
                        error = repl.messageFormat("jshell.console.no.candidate");
                    }
                    if (!res.isUpToDate()) {
                        error += repl.messageFormat("jshell.console.incomplete");
                    }
                    return new FixResult(fixes, error);
                }
            }
        }
    };

    private static final class JShellUnixTerminal extends NoInterruptUnixTerminal implements SuspendableTerminal {

        private final StopDetectingInputStream input;

        public JShellUnixTerminal(StopDetectingInputStream input) throws Exception {
            this.input = input;
        }

        public boolean isRaw() {
            try {
                return getSettings().get("-a").contains("-icanon");
            } catch (IOException | InterruptedException ex) {
                return false;
            }
        }

        @Override
        public InputStream wrapInIfNeeded(InputStream in) throws IOException {
            return input.setInputStream(super.wrapInIfNeeded(in));
        }

        @Override
        public void disableInterruptCharacter() {
        }

        @Override
        public void enableInterruptCharacter() {
        }

        @Override
        public void suspend() {
            try {
                getSettings().restore();
                super.disableInterruptCharacter();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public void resume() {
            try {
                init();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

    }

    private static final class JShellWindowsTerminal extends WindowsTerminal implements SuspendableTerminal {

        private final StopDetectingInputStream input;

        public JShellWindowsTerminal(StopDetectingInputStream input) throws Exception {
            this.input = input;
        }

        @Override
        public void init() throws Exception {
            super.init();
            setAnsiSupported(false);
        }

        @Override
        public InputStream wrapInIfNeeded(InputStream in) throws IOException {
            return input.setInputStream(super.wrapInIfNeeded(in));
        }

        @Override
        public void suspend() {
            try {
                restore();
                setConsoleMode(getConsoleMode() & ~ConsoleMode.ENABLE_PROCESSED_INPUT.code);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public void resume() {
            try {
                restore();
                init();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public boolean isRaw() {
            return (getConsoleMode() & ConsoleMode.ENABLE_LINE_INPUT.code) == 0;
        }

    }

    private static final class TestTerminal extends TerminalSupport {

        private final StopDetectingInputStream input;

        public TestTerminal(StopDetectingInputStream input) throws Exception {
            super(true);
            setAnsiSupported(false);
            setEchoEnabled(false);
            this.input = input;
        }

        @Override
        public InputStream wrapInIfNeeded(InputStream in) throws IOException {
            return input.setInputStream(super.wrapInIfNeeded(in));
        }

    }

    private interface SuspendableTerminal {
        public void suspend();
        public void resume();
        public boolean isRaw();
    }

    private static final class CheckCompletionKeyMap extends KeyMap {

        private final KeyMap del;
        private final List<CompletionTask> completionTODO;

        public CheckCompletionKeyMap(KeyMap del, List<CompletionTask> completionTODO) {
            super(del.getName(), del.isViKeyMap());
            this.del = del;
            this.completionTODO = completionTODO;
        }

        @Override
        public void bind(CharSequence keySeq, Object function) {
            del.bind(keySeq, function);
        }

        @Override
        public void bindIfNotBound(CharSequence keySeq, Object function) {
            del.bindIfNotBound(keySeq, function);
        }

        @Override
        public void from(KeyMap other) {
            del.from(other);
        }

        @Override
        public Object getAnotherKey() {
            return del.getAnotherKey();
        }

        @Override
        public Object getBound(CharSequence keySeq) {
            Object res = del.getBound(keySeq);

            if (res != Operation.COMPLETE) {
                completionTODO.clear();
            }

            return res;
        }

        @Override
        public void setBlinkMatchingParen(boolean on) {
            del.setBlinkMatchingParen(on);
        }

        @Override
        public String toString() {
            return "check: " + del.toString();
        }
    }
    }
