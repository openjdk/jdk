/*
 * Copyright (c) 2002-2025, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
/**
 * JLine 3 Completer Implementations.
 * <p>
 * This package provides various implementations of the {@link org.jline.reader.Completer}
 * interface for different completion scenarios. These completers can be used individually
 * or combined to create sophisticated tab completion behavior.
 * <p>
 * Key completer implementations include:
 * <ul>
 *   <li>{@link org.jline.reader.impl.completer.ArgumentCompleter} - Completes commands based on argument position</li>
 *   <li>{@link org.jline.builtins.Completers.FileNameCompleter} - Completes file and directory names</li>
 *   <li>{@link org.jline.reader.impl.completer.StringsCompleter} - Completes from a predefined set of strings</li>
 *   <li>{@link org.jline.reader.impl.completer.SystemCompleter} - Aggregates multiple completers for different commands</li>
 *   <li>{@link org.jline.reader.impl.completer.AggregateCompleter} - Combines multiple completers</li>
 *   <li>{@link org.jline.reader.impl.completer.NullCompleter} - A no-op completer that provides no completions</li>
 * </ul>
 * <p>
 * These completers can be registered with a {@link org.jline.reader.LineReader} using
 * the {@link org.jline.reader.LineReaderBuilder#completer(org.jline.reader.Completer)}
 * method.
 *
 * @since 3.0
 * @see org.jline.reader.Completer
 * @see org.jline.reader.LineReaderBuilder#completer(org.jline.reader.Completer)
 */
package jdk.internal.org.jline.reader.impl.completer;
