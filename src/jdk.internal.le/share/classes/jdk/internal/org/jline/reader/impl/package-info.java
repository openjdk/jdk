/*
 * Copyright (c) 2002-2025, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
/**
 * JLine 3 Reader Implementation Package.
 * <p>
 * This package provides the core implementations of the interfaces defined in the
 * {@link org.jline.reader} package. These implementations form the foundation of
 * JLine's line editing and command processing capabilities.
 * <p>
 * Key implementation classes include:
 * <ul>
 *   <li>{@link org.jline.reader.impl.LineReaderImpl} - The main implementation of the
 *       {@link org.jline.reader.LineReader} interface, providing line editing, history
 *       navigation, completion, and other interactive features</li>
 *   <li>{@link org.jline.reader.impl.DefaultParser} - Implementation of the
 *       {@link org.jline.reader.Parser} interface for tokenizing command lines</li>
 *   <li>{@link org.jline.reader.impl.DefaultHighlighter} - Implementation of the
 *       {@link org.jline.reader.Highlighter} interface for syntax highlighting</li>
 *   <li>{@link org.jline.reader.impl.DefaultExpander} - Implementation of the
 *       {@link org.jline.reader.Expander} interface for expanding history references and variables</li>
 *   <li>{@link org.jline.reader.impl.CompletionMatcherImpl} - Implementation of the
 *       {@link org.jline.reader.CompletionMatcher} interface for matching completion candidates</li>
 *   <li>{@link org.jline.reader.impl.BufferImpl} - Implementation of the
 *       {@link org.jline.reader.Buffer} interface for managing the line buffer</li>
 * </ul>
 * <p>
 * This package also contains utility classes that support the main implementations:
 * <ul>
 *   <li>{@link org.jline.reader.impl.ReaderUtils} - Utility methods for LineReader implementations</li>
 *   <li>{@link org.jline.reader.impl.UndoTree} - Provides undo/redo functionality</li>
 *   <li>{@link org.jline.reader.impl.KillRing} - Implements a kill ring for cut/paste operations</li>
 *   <li>{@link org.jline.reader.impl.InputRC} - Handles inputrc configuration files</li>
 * </ul>
 * <p>
 * Most users will not need to interact directly with these implementation classes,
 * but instead should use the {@link org.jline.reader.LineReaderBuilder} to create
 * properly configured instances.
 *
 * @since 3.0
 * @see org.jline.reader
 * @see org.jline.reader.LineReaderBuilder
 */
package org.jline.reader.impl;
