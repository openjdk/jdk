/*
 * Copyright (c) 2002-2025, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
/**
 * JLine Terminal API - Core abstractions for terminal operations across different platforms.
 *
 * <p>This package provides a comprehensive API for interacting with terminals in a platform-independent
 * way. It abstracts the differences between various terminal types and operating systems, allowing
 * applications to work consistently across environments.</p>
 *
 * <h2>Key Components</h2>
 *
 * <h3>Terminal Interface</h3>
 * <p>The {@link org.jline.terminal.Terminal} interface is the central abstraction, representing a virtual
 * terminal. It provides methods for:</p>
 * <ul>
 *   <li>Reading input and writing output</li>
 *   <li>Managing terminal attributes</li>
 *   <li>Handling terminal size and cursor positioning</li>
 *   <li>Processing signals (like CTRL+C)</li>
 *   <li>Supporting mouse events</li>
 *   <li>Accessing terminal capabilities</li>
 * </ul>
 *
 * <h3>Terminal Creation</h3>
 * <p>The {@link org.jline.terminal.TerminalBuilder} class provides a fluent API for creating terminal
 * instances with specific configurations. It supports various terminal types and providers, allowing
 * for flexible terminal creation based on the environment and requirements.</p>
 *
 * <h3>Terminal Attributes</h3>
 * <p>The {@link org.jline.terminal.Attributes} class encapsulates terminal settings such as:</p>
 * <ul>
 *   <li>Input flags (e.g., character echoing, canonical mode)</li>
 *   <li>Output flags (e.g., output processing)</li>
 *   <li>Control flags (e.g., baud rate, character size)</li>
 *   <li>Local flags (e.g., echo, canonical processing)</li>
 *   <li>Control characters (e.g., EOF, interrupt, erase)</li>
 * </ul>
 *
 * <h3>Supporting Classes</h3>
 * <ul>
 *   <li>{@link org.jline.terminal.Size} - Represents the dimensions of a terminal (rows and columns)</li>
 *   <li>{@link org.jline.terminal.Cursor} - Represents a cursor position within the terminal</li>
 *   <li>{@link org.jline.terminal.MouseEvent} - Encapsulates mouse events (clicks, movements) in the terminal</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * // Create a terminal
 * Terminal terminal = TerminalBuilder.builder()
 *     .system(true)
 *     .build();
 *
 * // Get terminal size
 * Size size = terminal.getSize();
 * System.out.println("Terminal size: " + size.getColumns() + "x" + size.getRows());
 *
 * // Write to the terminal
 * terminal.writer().println("Hello, JLine Terminal!");
 * terminal.flush();
 *
 * // Read from the terminal
 * int c = terminal.reader().read();
 *
 * // Close the terminal when done
 * terminal.close();
 * </pre>
 *
 * <h2>Terminal Implementations</h2>
 * <p>The actual terminal implementations are provided in the {@code org.jline.terminal.impl} package,
 * with platform-specific implementations available through various provider modules like:</p>
 * <ul>
 *   <li>jline-terminal-ffm - Foreign Function & Memory (Java 22+) based implementation</li>
 *   <li>jline-terminal-jni - JNI-based implementation</li>
 *   <li>jline-terminal-jansi - JANSI-based implementation</li>
 * </ul>
 *
 * <p>The Service Provider Interface (SPI) for terminal implementations is defined in the
 * {@code org.jline.terminal.spi} package.</p>
 *
 * @since 3.0
 */
package org.jline.terminal;
