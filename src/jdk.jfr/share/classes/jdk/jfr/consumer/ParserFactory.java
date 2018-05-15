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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jdk.jfr.EventType;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.internal.MetadataDescriptor;
import jdk.jfr.internal.PrivateAccess;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.consumer.RecordingInput;

/**
 * Class that create parsers suitable for reading events and constant pools
 */
final class ParserFactory {
    private final LongMap<Parser> parsers = new LongMap<>();
    private final TimeConverter timeConverter;
    private final LongMap<Type> types = new LongMap<>();
    private final LongMap<ConstantMap> constantPools;

    public ParserFactory(MetadataDescriptor metadata, TimeConverter timeConverter) throws IOException {
        this.constantPools = new LongMap<>();
        this.timeConverter = timeConverter;
        for (Type t : metadata.getTypes()) {
            types.put(t.getId(), t);
        }
        for (Type t : types) {
            if (!t.getFields().isEmpty()) { // Avoid primitives
                CompositeParser cp = createCompositeParser(t);
                if (t.isSimpleType()) { // Reduce to nested parser
                   parsers.put(t.getId(), cp.parsers[0]);
                }

            }
        }
        // Override event types with event parsers
        for (EventType t : metadata.getEventTypes()) {
            parsers.put(t.getId(), createEventParser(t));
        }
    }

    public LongMap<Parser> getParsers() {
        return parsers;
    }

    public LongMap<ConstantMap> getConstantPools() {
        return constantPools;
    }

    public LongMap<Type> getTypeMap() {
        return types;
    }

    private EventParser createEventParser(EventType eventType) throws IOException {
        List<Parser> parsers = new ArrayList<Parser>();
        for (ValueDescriptor f : eventType.getFields()) {
            parsers.add(createParser(f));
        }
        return new EventParser(timeConverter, eventType, parsers.toArray(new Parser[0]));
    }

    private Parser createParser(ValueDescriptor v) throws IOException {
        boolean constantPool = PrivateAccess.getInstance().isConstantPool(v);
        if (v.isArray()) {
            Type valueType = PrivateAccess.getInstance().getType(v);
            ValueDescriptor element = PrivateAccess.getInstance().newValueDescriptor(v.getName(), valueType, v.getAnnotationElements(), 0, constantPool, null);
            return new ArrayParser(createParser(element));
        }
        long id = v.getTypeId();
        Type type = types.get(id);
        if (type == null) {
            throw new IOException("Type '" + v.getTypeName() + "' is not defined");
        }
        if (constantPool) {
            ConstantMap pool = constantPools.get(id);
            if (pool == null) {
                pool = new ConstantMap(ObjectFactory.create(type, timeConverter), type.getName());
                constantPools.put(id, pool);
            }
            return new ConstantMapValueParser(pool);
        }
        Parser parser = parsers.get(id);
        if (parser == null) {
            if (!v.getFields().isEmpty()) {
                return createCompositeParser(type);
            } else {
                return registerParserType(type, createPrimitiveParser(type));
            }
        }
        return parser;
    }

    private Parser createPrimitiveParser(Type type) throws IOException {
        switch (type.getName()) {
        case "int":
            return new IntegerParser();
        case "long":
            return new LongParser();
        case "float":
            return new FloatParser();
        case "double":
            return new DoubleParser();
        case "char":
            return new CharacterParser();
        case "boolean":
            return new BooleanParser();
        case "short":
            return new ShortParser();
        case "byte":
            return new ByteParser();
        case "java.lang.String":
            ConstantMap pool = new ConstantMap(ObjectFactory.create(type, timeConverter), type.getName());
            constantPools.put(type.getId(), pool);
            return new StringParser(pool);
        default:
            throw new IOException("Unknown primitive type " + type.getName());
        }
    }

    private Parser registerParserType(Type t, Parser parser) {
        Parser p = parsers.get(t.getId());
        // check if parser exists (known type)
        if (p != null) {
            return p;
        }
        parsers.put(t.getId(), parser);
        return parser;
    }

    private CompositeParser createCompositeParser(Type type) throws IOException {
        List<ValueDescriptor> vds = type.getFields();
        Parser[] parsers = new Parser[vds.size()];
        CompositeParser composite = new CompositeParser(parsers);
        // need to pre-register so recursive types can be handled
        registerParserType(type, composite);

        int index = 0;
        for (ValueDescriptor vd : vds) {
            parsers[index++] = createParser(vd);
        }
        return composite;
    }

    private static final class BooleanParser extends Parser {
        @Override
        public Object parse(RecordingInput input) throws IOException {
            return input.readBoolean() ? Boolean.TRUE : Boolean.FALSE;
        }
    }

    private static final class ByteParser extends Parser {
        @Override
        public Object parse(RecordingInput input) throws IOException {
            return Byte.valueOf(input.readByte());
        }
    }

    private static final class LongParser extends Parser {
        @Override
        public Object parse(RecordingInput input) throws IOException {
            return Long.valueOf(input.readLong());
        }
    }

    private static final class IntegerParser extends Parser {
        @Override
        public Object parse(RecordingInput input) throws IOException {
            return Integer.valueOf(input.readInt());
        }
    }

    private static final class ShortParser extends Parser {
        @Override
        public Object parse(RecordingInput input) throws IOException {
            return Short.valueOf(input.readShort());
        }
    }

    private static final class CharacterParser extends Parser {
        @Override
        public Object parse(RecordingInput input) throws IOException {
            return Character.valueOf(input.readChar());
        }
    }

    private static final class FloatParser extends Parser {
        @Override
        public Object parse(RecordingInput input) throws IOException {
            return Float.valueOf(input.readFloat());
        }
    }

    private static final class DoubleParser extends Parser {
        @Override
        public Object parse(RecordingInput input) throws IOException {
            return Double.valueOf(input.readDouble());
        }
    }

    private static final class StringParser extends Parser {
        private final ConstantMap stringConstantMap;
        private String last;

        StringParser(ConstantMap stringConstantMap) {
            this.stringConstantMap = stringConstantMap;
        }

        @Override
        public Object parse(RecordingInput input) throws IOException {
            String s = parseEncodedString(input);
            if (!Objects.equals(s, last)) {
                last = s;
            }
            return last;
        }

        private String parseEncodedString(RecordingInput input) throws IOException {
            byte encoding = input.readByte();
            if (encoding == RecordingInput.STRING_ENCODING_CONSTANT_POOL) {
                long id = input.readLong();
                return (String) stringConstantMap.get(id);
            } else {
                return input.readEncodedString(encoding);
            }
        }
    }

    private final static class ArrayParser extends Parser {
        private final Parser elementParser;

        public ArrayParser(Parser elementParser) {
            this.elementParser = elementParser;
        }

        @Override
        public Object parse(RecordingInput input) throws IOException {
            final int size = input.readInt();
            final Object[] array = new Object[size];
            for (int i = 0; i < size; i++) {
                array[i] = elementParser.parse(input);
            }
            return array;
        }
    }

    private final static class CompositeParser extends Parser {
        private final Parser[] parsers;

        public CompositeParser(Parser[] valueParsers) {
            this.parsers = valueParsers;
        }

        @Override
        public Object parse(RecordingInput input) throws IOException {
            final Object[] values = new Object[parsers.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = parsers[i].parse(input);
            }
            return values;
        }
    }

    private static final class ConstantMapValueParser extends Parser {
        private final ConstantMap pool;

        ConstantMapValueParser(ConstantMap pool) {
            this.pool = pool;
        }

        @Override
        public Object parse(RecordingInput input) throws IOException {
            return pool.get(input.readLong());
        }
    }
}
