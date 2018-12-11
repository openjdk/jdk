/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.tools.jjs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jdk.internal.org.jline.reader.Candidate;
import jdk.internal.org.jline.reader.CompletingParsedLine;
import jdk.internal.org.jline.reader.EOFError;
import jdk.internal.org.jline.reader.History;
import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.reader.LineReader.Option;
import jdk.internal.org.jline.reader.LineReaderBuilder;
import jdk.internal.org.jline.reader.Parser;
import jdk.internal.org.jline.reader.Parser.ParseContext;
import jdk.internal.org.jline.reader.Widget;
import jdk.internal.org.jline.reader.impl.LineReaderImpl;
import jdk.internal.org.jline.reader.impl.completer.ArgumentCompleter.ArgumentLine;
import jdk.internal.org.jline.terminal.Attributes.LocalFlag;
import jdk.internal.org.jline.terminal.Terminal;

class Console implements AutoCloseable {
    private static final String DOCUMENTATION_SHORTCUT = "\033\133\132"; //Shift-TAB
    private final LineReader in;
    private final File historyFile;

    Console(final InputStream cmdin, final PrintStream cmdout, final File historyFile,
            final NashornCompleter completer, final Function<String, String> docHelper) throws IOException {
        this.historyFile = historyFile;

        Parser parser = (line, cursor, context) -> {
            if (context == ParseContext.COMPLETE) {
                List<Candidate> candidates = new ArrayList<>();
                int anchor = completer.complete(line, cursor, candidates);
                anchor = Math.min(anchor, line.length());
                return new CompletionLine(line.substring(anchor), cursor, candidates);
            } else if (!completer.isComplete(line)) {
                throw new EOFError(cursor, cursor, line);
            }
            return new ArgumentLine(line, cursor);
        };
        in = LineReaderBuilder.builder()
                              .option(Option.DISABLE_EVENT_EXPANSION, true)
                              .completer((in, line, candidates) -> candidates.addAll(((CompletionLine) line).candidates))
                              .parser(parser)
                              .build();
        if (historyFile.exists()) {
            StringBuilder line = new StringBuilder();
            for (String h : Files.readAllLines(historyFile.toPath())) {
                if (line.length() > 0) {
                    line.append("\n");
                }
                line.append(h);
                try {
                    parser.parse(line.toString(), line.length());
                    in.getHistory().add(line.toString());
                    line.delete(0, line.length());
                } catch (EOFError e) {
                    //continue;
                }
            }
            if (line.length() > 0) {
                in.getHistory().add(line.toString());
            }
        }
        Runtime.getRuntime().addShutdownHook(new Thread((Runnable)this::saveHistory));
        bind(DOCUMENTATION_SHORTCUT, ()->showDocumentation(docHelper));
    }

    String readLine(final String prompt, final String continuationPrompt) throws IOException {
        in.setVariable(LineReader.SECONDARY_PROMPT_PATTERN, continuationPrompt);
        return in.readLine(prompt);
    }

    String readUserLine(final String prompt) throws IOException {
        Parser prevParser = in.getParser();

        try {
            ((LineReaderImpl) in).setParser((line, cursor, context) -> new ArgumentLine(line, cursor));
            return in.readLine(prompt);
        } finally {
            ((LineReaderImpl) in).setParser(prevParser);
        }
    }

    @Override
    public void close() {
        saveHistory();
    }

    private void saveHistory() {
        try (Writer out = Files.newBufferedWriter(historyFile.toPath())) {
            String lineSeparator = System.getProperty("line.separator");

            out.write(StreamSupport.stream(getHistory().spliterator(), false)
                                   .map(e -> e.line())
                                   .collect(Collectors.joining(lineSeparator)));
        } catch (final IOException exp) {}
    }

    History getHistory() {
        return in.getHistory();
    }

    boolean terminalEditorRunning() {
        Terminal terminal = in.getTerminal();
        return !terminal.getAttributes().getLocalFlag(LocalFlag.ICANON);
    }

    void suspend() {
    }

    void resume() {
    }

    private void bind(String shortcut, Widget action) {
        in.getKeyMaps().get(LineReader.MAIN).bind(action, shortcut);
    }

    private boolean showDocumentation(final Function<String, String> docHelper) {
        final String buffer = in.getBuffer().toString();
        final int cursor = in.getBuffer().cursor();
        final String doc = docHelper.apply(buffer.substring(0, cursor));
        if (doc != null) {
            in.getTerminal().writer().println();
            in.printAbove(doc);
            return true;
        } else {
            return false;
        }
    }

    private static final class CompletionLine extends ArgumentLine implements CompletingParsedLine {
        public final List<Candidate> candidates;

        public CompletionLine(String word, int cursor, List<Candidate> candidates) {
            super(word, cursor);
            this.candidates = candidates;
        }

        public CharSequence escape(CharSequence candidate, boolean complete) {
            return candidate;
        }

        public int rawWordCursor() {
            return word().length();
        }

        public int rawWordLength() {
            return word().length();
        }
    }
}
