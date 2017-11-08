/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.graphio;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.Map;

/**
 * Instance of output to dump informations about a compiler compilations.
 *
 * @param <G> the type of graph this instance handles
 * @param <M> the type of methods this instance handles
 */
public final class GraphOutput<G, M> implements Closeable {
    private final GraphProtocol<G, ?, ?, ?, ?, M, ?, ?, ?> printer;

    private GraphOutput(GraphProtocol<G, ?, ?, ?, ?, M, ?, ?, ?> p) {
        this.printer = p;
    }

    /**
     * Creates new builder to configure a future instance of {@link GraphOutput}.
     *
     * @param <G> the type of the graph
     * @param <N> the type of the nodes
     * @param <C> the type of the node classes
     * @param <P> the type of the ports
     *
     * @param structure description of the structure of the graph
     * @return the builder to configure
     */
    public static <G, N, C, P> Builder<G, N, ?> newBuilder(GraphStructure<G, N, C, P> structure) {
        return new Builder<>(structure);
    }

    /**
     * Begins a compilation group.
     *
     * @param forGraph
     * @param name
     * @param shortName
     * @param method
     * @param bci
     * @param properties
     * @throws IOException
     */
    public void beginGroup(G forGraph, String name, String shortName, M method, int bci, Map<? extends Object, ? extends Object> properties) throws IOException {
        printer.beginGroup(forGraph, name, shortName, method, bci, properties);
    }

    /**
     * Prints a single graph.
     *
     * @param graph
     * @param properties
     * @param id
     * @param format
     * @param args
     * @throws IOException
     */
    public void print(G graph, Map<? extends Object, ? extends Object> properties, int id, String format, Object... args) throws IOException {
        printer.print(graph, properties, id, format, args);
    }

    /**
     * Ends compilation group.
     *
     * @throws IOException
     */
    public void endGroup() throws IOException {
        printer.endGroup();
    }

    /**
     * Closes the output. Closes allocated resources and associated output channel.
     */
    @Override
    public void close() {
        printer.close();
    }

    /**
     * Builder to configure and create an instance of {@link GraphOutput}.
     *
     * @param <G> the type of the (root element of) graph
     * @param <N> the type of the nodes
     * @param <M> the type of the methods
     */
    public static final class Builder<G, N, M> {
        private final GraphStructure<G, N, ?, ?> structure;
        private GraphElements<M, ?, ?, ?> elements = null;
        private GraphTypes types = DefaultGraphTypes.DEFAULT;
        private GraphBlocks<G, ?, N> blocks = DefaultGraphBlocks.empty();
        private int major = 4;
        private int minor = 0;

        Builder(GraphStructure<G, N, ?, ?> structure) {
            this.structure = structure;
        }

        /**
         * Chooses which version of the protocol to use. The default version is <code>4.0</code>
         * (when the {@link GraphOutput} & co. classes were introduced). The default can be changed
         * to other known versions manually by calling this method.
         *
         * @param majorVersion by default 4, newer version may be known
         * @param minorVersion usually 0
         * @return this builder
         * @since 0.28
         */
        public Builder<G, N, M> protocolVersion(int majorVersion, int minorVersion) {
            this.major = majorVersion;
            this.minor = minorVersion;
            return this;
        }

        /**
         * Associates different implementation of types.
         *
         * @param graphTypes implementation of types and enum recognition
         * @return this builder
         */
        public Builder<G, N, M> types(GraphTypes graphTypes) {
            this.types = graphTypes;
            return this;
        }

        /**
         * Associates implementation of blocks.
         *
         * @param graphBlocks the blocks implementation
         * @return this builder
         */
        public Builder<G, N, M> blocks(GraphBlocks<G, ?, N> graphBlocks) {
            this.blocks = graphBlocks;
            return this;
        }

        /**
         * Associates implementation of graph elements.
         *
         * @param graphElements the elements implementation
         * @return this builder
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <E> Builder<G, N, E> elements(GraphElements<E, ?, ?, ?> graphElements) {
            this.elements = (GraphElements) graphElements;
            return (Builder<G, N, E>) this;
        }

        /**
         * Creates new {@link GraphOutput} to output to provided channel. The output will use
         * interfaces currently associated with this builder.
         *
         * @param channel the channel to output to
         * @return new graph output
         * @throws IOException if something goes wrong when writing to the channel
         */
        public GraphOutput<G, M> build(WritableByteChannel channel) throws IOException {
            ProtocolImpl<G, N, ?, ?, ?, M, ?, ?, ?> p = new ProtocolImpl<>(major, minor, structure, types, blocks, elements, channel);
            return new GraphOutput<>(p);
        }

        /**
         * Support for nesting heterogenous graphs. The newly created output uses all the interfaces
         * currently associated with this builder, but shares with {@code parent} the output
         * {@code channel}, internal constant pool and {@link #protocolVersion(int, int) protocol
         * version}.
         * <p>
         * Both GraphOutput (the {@code parent} and the returned one) has to be used in
         * synchronization - e.g. only one
         * {@link #beginGroup(java.lang.Object, java.lang.String, java.lang.String, java.lang.Object, int, java.util.Map)
         * begin}, {@link #endGroup() end} of group or
         * {@link #print(java.lang.Object, java.util.Map, int, java.lang.String, java.lang.Object...)
         * printing} can be on at a given moment.
         *
         * @param parent the output to inherit {@code channel} and protocol version from
         * @return new output sharing {@code channel} and other internals with {@code parent}
         */
        public GraphOutput<G, M> build(GraphOutput<?, ?> parent) {
            ProtocolImpl<G, N, ?, ?, ?, M, ?, ?, ?> p = new ProtocolImpl<>(parent.printer, structure, types, blocks, elements);
            return new GraphOutput<>(p);
        }
    }
}
