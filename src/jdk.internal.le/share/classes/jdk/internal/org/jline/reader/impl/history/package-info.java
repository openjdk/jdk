/*
 * Copyright (c) 2002-2025, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
/**
 * JLine 3 History Implementation Package.
 * <p>
 * This package provides implementations of the {@link org.jline.reader.History} interface
 * for managing command history in interactive applications. Command history allows users
 * to recall, edit, and reuse previously entered commands.
 * <p>
 * Key features of the history implementation include:
 * <ul>
 *   <li>Persistent storage of command history in files</li>
 *   <li>Configurable history size limits for memory and file storage</li>
 *   <li>Support for timestamped history entries</li>
 *   <li>Filtering of history entries based on patterns</li>
 *   <li>Duplicate entry handling (ignore, reduce blanks, etc.)</li>
 *   <li>Navigation through history (previous/next, first/last, etc.)</li>
 * </ul>
 * <p>
 * The main implementation class is {@link org.jline.reader.impl.history.DefaultHistory},
 * which provides a file-backed history implementation with various configuration options.
 *
 * @since 3.0
 * @see org.jline.reader.History
 * @see org.jline.reader.impl.history.DefaultHistory
 */
package jdk.internal.org.jline.reader.impl.history;
