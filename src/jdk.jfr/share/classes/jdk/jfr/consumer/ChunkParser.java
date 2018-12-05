/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.consumer;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import jdk.jfr.EventType;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.MetadataDescriptor;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.consumer.ChunkHeader;
import jdk.jfr.internal.consumer.RecordingInput;

/**
 * Parses a chunk.
 *
 */
final class ChunkParser {
    private static final long CONSTANT_POOL_TYPE_ID = 1;
    private final RecordingInput input;
    private final LongMap<Parser> parsers;
    private final ChunkHeader chunkHeader;
    private final long absoluteChunkEnd;
    private final MetadataDescriptor metadata;
    private final LongMap<Type> typeMap;
    private final TimeConverter timeConverter;

    public ChunkParser(RecordingInput input) throws IOException {
        this(new ChunkHeader(input));
    }

    private ChunkParser(ChunkHeader header) throws IOException {
        this.input = header.getInput();
        this.chunkHeader = header;
        this.metadata = header.readMetadata();
        this.absoluteChunkEnd = header.getEnd();
        this.timeConverter = new TimeConverter(chunkHeader, metadata.getGMTOffset());

        ParserFactory factory = new ParserFactory(metadata, timeConverter);
        LongMap<ConstantMap> constantPools = factory.getConstantPools();
        parsers = factory.getParsers();
        typeMap = factory.getTypeMap();

        fillConstantPools(parsers, constantPools);
        constantPools.forEach(ConstantMap::setIsResolving);
        constantPools.forEach(ConstantMap::resolve);
        constantPools.forEach(ConstantMap::setResolved);

        input.position(chunkHeader.getEventStart());
    }

    public RecordedEvent readEvent() throws IOException {
        while (input.position() < absoluteChunkEnd) {
            long pos = input.position();
            int size = input.readInt();
            if (size == 0) {
                throw new IOException("Event can't have zero size");
            }
            long typeId = input.readLong();
            if (typeId > CONSTANT_POOL_TYPE_ID) { // also skips metadata (id=0)
                Parser ep = parsers.get(typeId);
                if (ep instanceof EventParser) {
                    return (RecordedEvent) ep.parse(input);
                }
            }
            input.position(pos + size);
        }
        return null;
    }

    private void fillConstantPools(LongMap<Parser> typeParser, LongMap<ConstantMap> constantPools) throws IOException {
        long nextCP = chunkHeader.getAbsoluteChunkStart();
        long deltaToNext = chunkHeader.getConstantPoolPosition();
        while (deltaToNext != 0) {
            nextCP += deltaToNext;
            input.position(nextCP);
            final long position = nextCP;
            int size = input.readInt(); // size
            long typeId = input.readLong();
            if (typeId != CONSTANT_POOL_TYPE_ID) {
                throw new IOException("Expected check point event (id = 1) at position " + nextCP + ", but found type id = " + typeId);
            }
            input.readLong(); // timestamp
            input.readLong(); // duration
            deltaToNext = input.readLong();
            final long delta = deltaToNext;
            boolean flush = input.readBoolean();
            int poolCount = input.readInt();
            Logger.log(LogTag.JFR_SYSTEM_PARSER, LogLevel.TRACE, () -> {
                return "New constant pool: startPosition=" + position + ", size=" + size + ", deltaToNext=" + delta + ", flush=" + flush + ", poolCount=" + poolCount;
            });

            for (int i = 0; i < poolCount; i++) {
                long id = input.readLong(); // type id
                ConstantMap pool = constantPools.get(id);
                Type type = typeMap.get(id);
                if (pool == null) {
                    Logger.log(LogTag.JFR_SYSTEM_PARSER, LogLevel.INFO, "Found constant pool(" + id + ") that is never used");
                    if (type == null) {
                        throw new IOException("Error parsing constant pool type " + getName(id) + " at position " + input.position() + " at check point between [" + nextCP + ", " + nextCP + size + "]");
                    }
                    pool = new ConstantMap(ObjectFactory.create(type, timeConverter), type.getName());
                    constantPools.put(type.getId(), pool);
                }
                Parser parser = typeParser.get(id);
                if (parser == null) {
                    throw new IOException("Could not find constant pool type with id = " + id);
                }
                try {
                    int count = input.readInt();
                    Logger.log(LogTag.JFR_SYSTEM_PARSER, LogLevel.TRACE, () -> "Constant: " + getName(id) + "[" + count + "]");
                    for (int j = 0; j < count; j++) {
                        long key = input.readLong();
                        Object value = parser.parse(input);
                        pool.put(key, value);
                    }
                } catch (Exception e) {
                    throw new IOException("Error parsing constant pool type " + getName(id) + " at position " + input.position() + " at check point between [" + nextCP + ", " + nextCP + size + "]", e);
                }
            }
            if (input.position() != nextCP + size) {
                throw new IOException("Size of check point event doesn't match content");
            }
        }
    }

    private String getName(long id) {
        Type type = typeMap.get(id);
        return type == null ? ("unknown(" + id + ")") : type.getName();
    }

    public Collection<Type> getTypes() {
        return metadata.getTypes();
    }

    public List<EventType> getEventTypes() {
        return metadata.getEventTypes();
    }

    public boolean isLastChunk() {
        return chunkHeader.isLastChunk();
    }

    public ChunkParser nextChunkParser() throws IOException {
        return new ChunkParser(chunkHeader.nextHeader());
    }
}
