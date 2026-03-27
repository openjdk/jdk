/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

import java.io.IOError;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import jdk.internal.org.jline.reader.impl.LineReaderImpl;
import jdk.internal.org.jline.reader.impl.history.DefaultHistory;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.TerminalBuilder;
import jdk.internal.org.jline.utils.Log;

/**
 * A builder for creating and configuring {@link LineReader} instances.
 * <p>
 * This builder provides a fluent API for constructing LineReader objects with
 * various configuration options. It simplifies the process of creating a properly
 * configured LineReader by providing methods for setting all the necessary components
 * and options.
 * <p>
 * Example usage:
 * <pre>
 * LineReader reader = LineReaderBuilder.builder()
 *     .terminal(terminal)
 *     .completer(completer)
 *     .parser(parser)
 *     .highlighter(highlighter)
 *     .variable(LineReader.HISTORY_FILE, historyFile)
 *     .option(LineReader.Option.AUTO_LIST, true)
 *     .build();
 * </pre>
 * <p>
 * If no terminal is provided, the builder will attempt to create a default terminal
 * using {@link TerminalBuilder#terminal()}.
 *
 * @see LineReader
 * @see Terminal
 */
public final class LineReaderBuilder {

    /**
     * Creates a new LineReaderBuilder instance.
     *
     * @return a new LineReaderBuilder
     */
    public static LineReaderBuilder builder() {
        return new LineReaderBuilder();
    }

    Terminal terminal;
    String appName;
    Map<String, Object> variables = new HashMap<>();
    Map<LineReader.Option, Boolean> options = new HashMap<>();
    History history;
    Completer completer;
    History memoryHistory;
    Highlighter highlighter;
    Parser parser;
    Expander expander;
    CompletionMatcher completionMatcher;

    private LineReaderBuilder() {}

    /**
     * Sets the terminal to be used by the LineReader.
     * <p>
     * If not specified, a default terminal will be created when building the LineReader.
     *
     * @param terminal the terminal to use
     * @return this builder
     */
    public LineReaderBuilder terminal(Terminal terminal) {
        this.terminal = terminal;
        return this;
    }

    public LineReaderBuilder appName(String appName) {
        this.appName = appName;
        return this;
    }

    public LineReaderBuilder variables(Map<String, Object> variables) {
        Map<String, Object> old = this.variables;
        this.variables = Objects.requireNonNull(variables);
        this.variables.putAll(old);
        return this;
    }

    public LineReaderBuilder variable(String name, Object value) {
        this.variables.put(name, value);
        return this;
    }

    public LineReaderBuilder option(LineReader.Option option, boolean value) {
        this.options.put(option, value);
        return this;
    }

    public LineReaderBuilder history(History history) {
        this.history = history;
        return this;
    }

    /**
     * Sets the completer to be used for tab completion.
     * <p>
     * The completer provides completion candidates when the user presses the tab key.
     *
     * @param completer the completer to use
     * @return this builder
     * @see Completer
     */
    public LineReaderBuilder completer(Completer completer) {
        this.completer = completer;
        return this;
    }

    /**
     * Sets the highlighter to be used for syntax highlighting.
     * <p>
     * The highlighter applies styling to the input text as the user types.
     *
     * @param highlighter the highlighter to use
     * @return this builder
     * @see Highlighter
     */
    public LineReaderBuilder highlighter(Highlighter highlighter) {
        this.highlighter = highlighter;
        return this;
    }

    /**
     * Sets the parser to be used for parsing command lines.
     * <p>
     * The parser breaks the input line into tokens according to specific syntax rules.
     * It is used during tab completion and when accepting a line of input.
     * <p>
     * This method will log a warning if the provided parser does not support the
     * {@link CompletingParsedLine} interface, as this may cause issues with completion
     * of escaped or quoted words.
     *
     * @param parser the parser to use
     * @return this builder
     * @see Parser
     * @see CompletingParsedLine
     */
    public LineReaderBuilder parser(Parser parser) {
        if (parser != null) {
            try {
                if (!Boolean.getBoolean(LineReader.PROP_SUPPORT_PARSEDLINE)
                        && !(parser.parse("", 0) instanceof CompletingParsedLine)) {
                    Log.warn("The Parser of class " + parser.getClass().getName()
                            + " does not support the CompletingParsedLine interface. "
                            + "Completion with escaped or quoted words won't work correctly.");
                }
            } catch (Throwable t) {
                // Ignore
            }
        }
        this.parser = parser;
        return this;
    }

    public LineReaderBuilder expander(Expander expander) {
        this.expander = expander;
        return this;
    }

    public LineReaderBuilder completionMatcher(CompletionMatcher completionMatcher) {
        this.completionMatcher = completionMatcher;
        return this;
    }

    /**
     * Builds and returns a LineReader instance with the configured options.
     * <p>
     * This method creates a new LineReader with all the components and options that
     * have been set on this builder. If no terminal has been provided, a default
     * terminal will be created.
     * <p>
     * The resulting LineReader will have the following components set:
     * <ul>
     *   <li>Terminal - either the provided terminal or a default one</li>
     *   <li>Application name - either the provided name or the terminal name</li>
     *   <li>History - either the provided history or a new DefaultHistory</li>
     *   <li>Completer, Highlighter, Parser, Expander - if provided</li>
     *   <li>Options - any options that were set using option()</li>
     * </ul>
     *
     * @return a new LineReader instance
     * @throws IOError if there is an error creating the default terminal
     */
    public LineReader build() {
        Terminal terminal = this.terminal;
        if (terminal == null) {
            try {
                terminal = TerminalBuilder.terminal();
            } catch (IOException e) {
                throw new IOError(e);
            }
        }

        String appName = this.appName;
        if (null == appName) {
            appName = terminal.getName();
        }

        LineReaderImpl reader = new LineReaderImpl(terminal, appName, variables);
        if (history != null) {
            reader.setHistory(history);
        } else {
            if (memoryHistory == null) {
                memoryHistory = new DefaultHistory();
            }
            reader.setHistory(memoryHistory);
        }
        if (completer != null) {
            reader.setCompleter(completer);
        }
        if (highlighter != null) {
            reader.setHighlighter(highlighter);
        }
        if (parser != null) {
            reader.setParser(parser);
        }
        if (expander != null) {
            reader.setExpander(expander);
        }
        if (completionMatcher != null) {
            reader.setCompletionMatcher(completionMatcher);
        }
        for (Map.Entry<LineReader.Option, Boolean> e : options.entrySet()) {
            reader.option(e.getKey(), e.getValue());
        }
        return reader;
    }
}
