/*
 * Copyright (c) 2002-2025, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
/**
 * JLine 3 Reader Package - Core components for building interactive command-line interfaces.
 * <p>
 * This package provides the fundamental interfaces and classes for creating interactive
 * command-line applications with features such as:
 * <ul>
 *   <li>Line editing with customizable key bindings</li>
 *   <li>Command history navigation</li>
 *   <li>Tab completion with pluggable completion strategies</li>
 *   <li>Customizable syntax highlighting</li>
 *   <li>Password masking</li>
 *   <li>Custom prompt rendering</li>
 *   <li>Command parsing and tokenization</li>
 * </ul>
 * <p>
 * The main entry point is the {@link org.jline.reader.LineReader} interface, which can be
 * instantiated using the {@link org.jline.reader.LineReaderBuilder}. The LineReader provides
 * methods to read input from the user with various customization options.
 * <p>
 * Key components in this package include:
 * <ul>
 *   <li>{@link org.jline.reader.LineReader} - The main interface for reading lines from the console</li>
 *   <li>{@link org.jline.reader.LineReaderBuilder} - Builder for creating LineReader instances</li>
 *   <li>{@link org.jline.reader.Parser} - Interface for parsing command lines into tokens</li>
 *   <li>{@link org.jline.reader.Completer} - Interface for providing tab-completion candidates</li>
 *   <li>{@link org.jline.reader.Highlighter} - Interface for syntax highlighting</li>
 *   <li>{@link org.jline.reader.History} - Interface for command history management</li>
 * </ul>
 *
 * @since 3.0
 */
package jdk.internal.org.jline.reader;
