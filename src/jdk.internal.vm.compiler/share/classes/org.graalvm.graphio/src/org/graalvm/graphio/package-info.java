
/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * Send your graphs to <b>IGV</b> via a socket or a file. This package allows one to easily encode
 * any graph-like data structure and send it for visualization to <em>OracleLab's Ideal Graph
 * Visualizer</em> tool. Assuming you already have your own data structure that contains
 * <b>nodes</b> and <b>edges</b> among them, creating a {@link org.graalvm.graphio.GraphOutput}
 * specialized for your data is a matter of implementing a single interface:
 *
 * {@link org.graalvm.graphio.GraphJavadocSnippets#acmeGraphStructure}
 *
 * The {@link org.graalvm.graphio.GraphStructure} interface defines the set of operations that are
 * needed by the <em>graph protocol</em> to encode a graph into the <b>IGV</b> expected format. The
 * graph structure is implemented as a so called
 * <a href="http://wiki.apidesign.org/wiki/Singletonizer">singletonizer</a> API pattern: there is no
 * need to change your data structures or implement some special interfaces - everything needed is
 * provided by implementing the {@link org.graalvm.graphio.GraphStructure} operations.
 * <p>
 * The next step is to turn this graph structure into an instance of
 * {@link org.graalvm.graphio.GraphOutput}. To do so use the associated
 * {@link org.graalvm.graphio.GraphOutput.Builder builder} just like shown in the following method:
 *
 * {@link org.graalvm.graphio.GraphJavadocSnippets#buildOutput}
 *
 * Now you are ready to dump your graph into <b>IGV</b>. Where to obtain the right channel? One
 * option is to create a {@link java.nio.channels.FileChannel} and dump the data into a file
 * (preferrably with <code>.bgv</code> extension). The other is to open a socket to port
 * <code>4445</code> (the default port <b>IGV</b> listens to) and dump the data there. Here is an
 * example:
 *
 * {@link org.graalvm.graphio.GraphJavadocSnippets#dump}
 *
 * Call the {@code dump} method with pointer to file {@code diamond.bgv} and then you can open the
 * file in <b>IGV</b>. The result will look like this:
 * <p>
 * <img src="doc-files/diamond.png">
 * <p>
 * You can verify the behavior directly in the <b>IGV</b> by downloading
 * <a href="doc-files/diamond.bgv">diamond.bgv</a> file generated from the above diamond structure
 * graph.
 * <p>
 * The primary <b>IGV</b> focus is on graphs used by Graal compiler. As such they aren't plain
 * graphs, but contain various compiler oriented attributes:
 * <ul>
 * <li>{@linkplain org.graalvm.graphio.GraphBlocks code blocks} information</li>
 * <li>{@linkplain org.graalvm.graphio.GraphElements method and fields} information</li>
 * <li>Advanced support for {@linkplain org.graalvm.graphio.GraphTypes recognizing types}</li>
 * </ul>
 * all these additional interfaces ({@link org.graalvm.graphio.GraphBlocks},
 * {@link org.graalvm.graphio.GraphElements} and {@link org.graalvm.graphio.GraphTypes}) are
 * optional - they don't have to be provided. As such they can be specified via
 * {@link org.graalvm.graphio.GraphOutput.Builder} instance methods, which may, but need not be
 * called at all. Here is an example:
 *
 * {@link org.graalvm.graphio.GraphJavadocSnippets#buildAll}
 *
 * All these interfaces follow the
 * <a href="http://wiki.apidesign.org/wiki/Singletonizer">singletonizer</a> API pattern again - e.g.
 * no need to change your existing data structures, just implement the operations provided by the
 * interfaces you pass into the builder. By combining these interfaces together you can get as rich,
 * colorful, source linked graphs as Graal compiler produces to describe its optimizations.
 */


package org.graalvm.graphio;
