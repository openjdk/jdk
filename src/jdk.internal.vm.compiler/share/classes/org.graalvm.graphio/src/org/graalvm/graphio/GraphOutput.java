/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Instance of output to dump informations about a compiler compilations.
 *
 * @param <G> the type of graph this instance handles
 * @param <M> the type of methods this instance handles
 * @since 19.0 a {@link WritableByteChannel} is implemented
 */
public final class GraphOutput<G, M> implements Closeable, WritableByteChannel {
    private final GraphProtocol<G, ?, ?, ?, ?, M, ?, ?, ?, ?> printer;

    /**
     * Name of stream attribute to identify the VM execution, allows to join different GraphOutput
     * streams. The value should be the same for all related {@link GraphOutput}s.
     *
     * @since 20.2.0
     */
    public static final String ATTR_VM_ID = "vm.uuid";

    private GraphOutput(GraphProtocol<G, ?, ?, ?, ?, M, ?, ?, ?, ?> p) {
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
     * Checks if the {@link GraphOutput} is open.
     *
     * @return true if the {@link GraphOutput} is open.
     * @since 19.0
     */
    @Override
    public boolean isOpen() {
        return printer.isOpen();
    }

    /**
     * Writes raw bytes into {@link GraphOutput}.
     *
     * @param src the bytes to write
     * @return the number of bytes written, possibly zero
     * @throws IOException in case of IO error
     * @since 19.0
     */
    @Override
    public int write(ByteBuffer src) throws IOException {
        return printer.write(src);
    }

    /**
     * Builder to configure and create an instance of {@link GraphOutput}.
     *
     * @param <G> the type of the (root element of) graph
     * @param <N> the type of the nodes
     * @param <M> the type of the methods
     */
    public static final class Builder<G, N, M> {
        private static final int DEFAULT_MAJOR_VERSION = 7;
        private static final int DEFAULT_MINOR_VERSION = 0;

        private final GraphStructure<G, N, ?, ?> structure;
        private ElementsAndLocations<M, ?, ?> elementsAndLocations;

        private GraphTypes types = DefaultGraphTypes.DEFAULT;
        private GraphBlocks<G, ?, N> blocks = DefaultGraphBlocks.empty();

        /**
         * The major version. Negative values mean automatically assigned version, implied by
         * Builder functions.
         */
        private int major = 0;
        private int minor = 0;
        private boolean explicitVersionSet;
        private boolean embeddedGraphOutput;
        private Map<String, Object> properties;

        Builder(GraphStructure<G, N, ?, ?> structure) {
            this.structure = structure;
        }

        /**
         * Chooses which version of the protocol to use. The default version is <code>7.0</code>
         * (when the {@link GraphOutput} & co. classes were introduced). The default can be changed
         * to other known versions manually by calling this method.
         * <p>
         * Note: the the default version is 7.0 since version 20.2. Previous versions used default
         * version 4.0
         *
         * @param majorVersion by default 7, newer version may be known
         * @param minorVersion usually 0
         * @return this builder
         * @since 0.28
         */
        public Builder<G, N, M> protocolVersion(int majorVersion, int minorVersion) {
            assert majorVersion >= 1 : "Major must be positive";
            assert minorVersion >= 0 : "Minor must not be negative";

            if (!(explicitVersionSet ||
                            (majorVersion == 0) ||
                            (majorVersion > major) ||
                            ((majorVersion == major) && (minorVersion >= minor)))) {
                throw new IllegalArgumentException("Cannot downgrade from minimum required version " + (-major) + "." + minor);
            }
            this.major = majorVersion;
            this.minor = minorVersion;
            explicitVersionSet = true;
            return this;
        }

        /**
         * Asserts a specific version of the protocol. If not specified explicitly, upgrades the
         * protocol version.
         *
         * @param reqMajor The required major version
         * @param reqMinor the required minor version
         */
        private void requireVersion(int reqMajor, int reqMinor) {
            assert reqMajor >= 1 : "Major must be positive";
            assert reqMinor >= 0 : "Minor must not be negative";
            if (explicitVersionSet) {
                if (major < reqMajor || (major == reqMajor && minor < reqMinor)) {
                    throw new IllegalStateException("Feature unsupported in version " + major + "." + minor);
                }
            } else {
                if (major < reqMajor) {
                    major = reqMajor;
                    minor = reqMinor;
                } else if (major == reqMajor) {
                    minor = Math.max(minor, reqMinor);
                }
            }
        }

        /**
         * Sets {@link GraphOutput} as embedded. The embedded {@link GraphOutput} shares
         * {@link WritableByteChannel channel} with another already open non parent
         * {@link GraphOutput}. The embedded {@link GraphOutput} flushes data after each
         * {@link GraphOutput#print print}, {@link GraphOutput#beginGroup beginGroup} and
         * {@link GraphOutput#endGroup endGroup} call.
         *
         * @param embedded if {@code true} the builder creates an embedded {@link GraphOutput}
         * @return this builder
         * @since 19.0
         */
        public Builder<G, N, M> embedded(boolean embedded) {
            this.embeddedGraphOutput = embedded;
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
        public <E, P> Builder<G, N, E> elements(GraphElements<E, ?, ?, P> graphElements) {
            StackLocations<E, P> loc = new StackLocations<>(graphElements);
            return elementsAndLocations(graphElements, loc);
        }

        /**
         * Associates implementation of graph elements and an advanced way to interpret their
         * locations.
         *
         * @param graphElements the elements implementation
         * @param graphLocations the locations for the elements
         * @return this builder
         * @since 0.33 GraalVM 0.33
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <E, P> Builder<G, N, E> elementsAndLocations(GraphElements<E, ?, ?, P> graphElements, GraphLocations<E, P, ?> graphLocations) {
            ElementsAndLocations both = new ElementsAndLocations<>(graphElements, graphLocations);
            this.elementsAndLocations = both;
            return (Builder<G, N, E>) this;
        }

        /**
         * Attaches metadata to the dump. The method may be called more times, subsequent calls will
         * overwrite previous values of matching keys.
         *
         * @param name key name
         * @param value value for the key
         * @return this builder
         * @since 20.1.0
         */
        public Builder<G, N, M> attr(String name, Object value) {
            requireVersion(7, 0);
            if (properties == null) {
                properties = new HashMap<>(5);
            }
            properties.put(name, value);
            return this;
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
            return buildImpl(elementsAndLocations, channel);
        }

        /**
         * Support for nesting heterogenous graphs. The newly created output uses all the interfaces
         * currently associated with this builder, but shares with {@code parent} the output
         * {@code channel}, internal constant pool and {@link #protocolVersion(int, int) protocol
         * version}.
         * <p>
         * Both GraphOutput (the {@code parent} and the returned one) has to be used in
         * synchronization - e.g. only one
         * {@link GraphOutput#beginGroup(java.lang.Object, java.lang.String, java.lang.String, java.lang.Object, int, java.util.Map)
         * begin}, {@link GraphOutput#endGroup() end} of group or
         * {@link GraphOutput#print(java.lang.Object, java.util.Map, int, java.lang.String, java.lang.Object...)
         * printing} can be on at a given moment.
         *
         * @param parent the output to inherit {@code channel} and protocol version from
         * @return new output sharing {@code channel} and other internals with {@code parent}
         */
        public GraphOutput<G, M> build(GraphOutput<?, ?> parent) {
            return buildImpl(elementsAndLocations, parent);
        }

        private <L, P> GraphOutput<G, M> buildImpl(ElementsAndLocations<M, L, P> e, WritableByteChannel channel) throws IOException {
            int m = major;
            int n = minor;
            if (m == 0) {
                m = DEFAULT_MAJOR_VERSION;
                n = DEFAULT_MINOR_VERSION;
            }
            // @formatter:off
            ProtocolImpl<G, N, ?, ?, ?, M, ?, ?, ?, ?> p = new ProtocolImpl<>(
                m, n, embeddedGraphOutput, structure, types, blocks,
                e == null ? null : e.elements,
                e == null ? null : e.locations, channel
            );
            // @formatter:on
            if (properties != null) {
                p.startDocument(properties);
            }
            return new GraphOutput<>(p);
        }

        private <L, P> GraphOutput<G, M> buildImpl(ElementsAndLocations<M, L, P> e, GraphOutput<?, ?> parent) {
            // @formatter:off
            ProtocolImpl<G, N, ?, ?, ?, M, ?, ?, ?, ?> p = new ProtocolImpl<>(
                parent.printer, structure, types, blocks,
                e == null ? null : e.elements,
                e == null ? null : e.locations
            );
            // @formatter:on
            return new GraphOutput<>(p);
        }
    }

    private static final class ElementsAndLocations<M, P, L> {
        final GraphElements<M, ?, ?, P> elements;
        final GraphLocations<M, P, L> locations;

        ElementsAndLocations(GraphElements<M, ?, ?, P> elements, GraphLocations<M, P, L> locations) {
            elements.getClass();
            locations.getClass();
            this.elements = elements;
            this.locations = locations;
        }
    }

    private static final class StackLocations<M, P> implements GraphLocations<M, P, StackTraceElement> {
        private final GraphElements<M, ?, ?, P> graphElements;

        StackLocations(GraphElements<M, ?, ?, P> graphElements) {
            this.graphElements = graphElements;
        }

        @Override
        public Iterable<StackTraceElement> methodLocation(M method, int bci, P pos) {
            StackTraceElement ste = this.graphElements.methodStackTraceElement(method, bci, pos);
            return Collections.singleton(ste);
        }

        @Override
        public URI locationURI(StackTraceElement location) {
            String path = location.getFileName();
            try {
                return path == null ? null : new URI(null, null, path, null);
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException(ex);
            }
        }

        @Override
        public int locationLineNumber(StackTraceElement location) {
            return location.getLineNumber();
        }

        @Override
        public String locationLanguage(StackTraceElement location) {
            return "Java";
        }

        @Override
        public int locationOffsetStart(StackTraceElement location) {
            return -1;
        }

        @Override
        public int locationOffsetEnd(StackTraceElement location) {
            return -1;
        }
    }
}
