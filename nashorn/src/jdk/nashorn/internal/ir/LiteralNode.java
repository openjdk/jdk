/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.ir;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jdk.nashorn.internal.codegen.CompileUnit;
import jdk.nashorn.internal.codegen.types.ArrayType;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.objects.NativeArray;
import jdk.nashorn.internal.parser.Lexer.LexerToken;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Undefined;

/**
 * Literal nodes represent JavaScript values.
 *
 * @param <T> the literal type
 */
@Immutable
public abstract class LiteralNode<T> extends Expression implements PropertyKey {
    /** Literal value */
    protected final T value;

    /** Marker for values that must be computed at runtime */
    public static final Object POSTSET_MARKER = new Object();

    /**
     * Constructor
     *
     * @param token   token
     * @param finish  finish
     * @param value   the value of the literal
     */
    protected LiteralNode(final long token, final int finish, final T value) {
        super(token, finish);
        this.value = value;
    }

    /**
     * Copy constructor
     *
     * @param literalNode source node
     */
    protected LiteralNode(final LiteralNode<T> literalNode) {
        this(literalNode, literalNode.value);
    }

    /**
     * A copy constructor with value change.
     * @param literalNode the original literal node
     * @param newValue new value for this node
     */
    protected LiteralNode(final LiteralNode<T> literalNode, final T newValue) {
        super(literalNode);
        this.value = newValue;
    }

    @Override
    public boolean isAtom() {
        return true;
    }

    /**
     * Check if the literal value is null
     * @return true if literal value is null
     */
    public boolean isNull() {
        return value == null;
    }

    @Override
    public Type getType() {
        return Type.typeFor(value.getClass());
    }

    @Override
    public String getPropertyName() {
        return JSType.toString(getObject());
    }

    /**
     * Fetch boolean value of node.
     *
     * @return boolean value of node.
     */
    public boolean getBoolean() {
        return JSType.toBoolean(value);
    }

    /**
     * Fetch int32 value of node.
     *
     * @return Int32 value of node.
     */
    public int getInt32() {
        return JSType.toInt32(value);
    }

    /**
     * Fetch uint32 value of node.
     *
     * @return uint32 value of node.
     */
    public long getUint32() {
        return JSType.toUint32(value);
    }

    /**
     * Fetch long value of node
     *
     * @return long value of node
     */
    public long getLong() {
        return JSType.toLong(value);
    }

    /**
     * Fetch double value of node.
     *
     * @return double value of node.
     */
    public double getNumber() {
        return JSType.toNumber(value);
    }

    /**
     * Get the array value of the node
     *
     * @return the array value
     */
    public Node[] getArray() {
        assert false : "not an array node";
        return null;
    }

    /**
     * Fetch String value of node.
     *
     * @return String value of node.
     */
    public String getString() {
        return JSType.toString(value);
    }

    /**
     * Fetch Object value of node.
     *
     * @return Object value of node.
     */
    public Object getObject() {
        return value;
    }

    /**
     * Test if the value is a string.
     *
     * @return True if value is a string.
     */
    public boolean isString() {
        return value instanceof String;
    }

    /**
     * Test if tha value is a number
     *
     * @return True if value is a number
     */
    public boolean isNumeric() {
        return value instanceof Number;
    }

    /**
     * Assist in IR navigation.
     *
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterLiteralNode(this)) {
            return visitor.leaveLiteralNode(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else {
            sb.append(value.toString());
        }
    }

    /**
     * Get the literal node value
     * @return the value
     */
    public T getValue() {
        return value;
    }

    /**
     * Create a new null literal
     *
     * @param token   token
     * @param finish  finish
     *
     * @return the new literal node
     */
    public static LiteralNode<Object> newInstance(final long token, final int finish) {
        return new NullLiteralNode(token, finish);
    }

    /**
     * Create a new null literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     *
     * @return the new literal node
     */
    public static LiteralNode<Object> newInstance(final Node parent) {
        return new NullLiteralNode(parent.getToken(), parent.getFinish());
    }

    /**
     * Super class for primitive (side-effect free) literals.
     *
     * @param <T> the literal type
     */
    public static class PrimitiveLiteralNode<T> extends LiteralNode<T> {
        private PrimitiveLiteralNode(final long token, final int finish, final T value) {
            super(token, finish, value);
        }

        private PrimitiveLiteralNode(final PrimitiveLiteralNode<T> literalNode) {
            super(literalNode);
        }

        /**
         * Check if the literal value is boolean true
         * @return true if literal value is boolean true
         */
        public boolean isTrue() {
            return JSType.toBoolean(value);
        }
    }

    @Immutable
    private static final class BooleanLiteralNode extends PrimitiveLiteralNode<Boolean> {

        private BooleanLiteralNode(final long token, final int finish, final boolean value) {
            super(Token.recast(token, value ? TokenType.TRUE : TokenType.FALSE), finish, value);
        }

        private BooleanLiteralNode(final BooleanLiteralNode literalNode) {
            super(literalNode);
        }

        @Override
        public boolean isTrue() {
            return value;
        }

        @Override
        public Type getType() {
            return Type.BOOLEAN;
        }

        @Override
        public Type getWidestOperationType() {
            return Type.BOOLEAN;
        }
    }

    /**
     * Create a new boolean literal
     *
     * @param token   token
     * @param finish  finish
     * @param value   true or false
     *
     * @return the new literal node
     */
    public static LiteralNode<Boolean> newInstance(final long token, final int finish, final boolean value) {
        return new BooleanLiteralNode(token, finish, value);
    }

    /**
     * Create a new boolean literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     * @param value  true or false
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent, final boolean value) {
        return new BooleanLiteralNode(parent.getToken(), parent.getFinish(), value);
    }

    @Immutable
    private static final class NumberLiteralNode extends PrimitiveLiteralNode<Number> {

        private final Type type = numberGetType(value);

        private NumberLiteralNode(final long token, final int finish, final Number value) {
            super(Token.recast(token, TokenType.DECIMAL), finish, value);
        }

        private NumberLiteralNode(final NumberLiteralNode literalNode) {
            super(literalNode);
        }

        private static Type numberGetType(final Number number) {
            if (number instanceof Integer) {
                return Type.INT;
            } else if (number instanceof Long) {
                return Type.LONG;
            } else if (number instanceof Double) {
                return Type.NUMBER;
            } else {
                assert false;
            }

            return null;
        }

        @Override
        public Type getType() {
            return type;
        }

        @Override
        public Type getWidestOperationType() {
            return getType();
        }

    }
    /**
     * Create a new number literal
     *
     * @param token   token
     * @param finish  finish
     * @param value   literal value
     *
     * @return the new literal node
     */
    public static LiteralNode<Number> newInstance(final long token, final int finish, final Number value) {
        return new NumberLiteralNode(token, finish, value);
    }

    /**
     * Create a new number literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     * @param value  literal value
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent, final Number value) {
        return new NumberLiteralNode(parent.getToken(), parent.getFinish(), value);
    }

    private static class UndefinedLiteralNode extends PrimitiveLiteralNode<Undefined> {
        private UndefinedLiteralNode(final long token, final int finish) {
            super(Token.recast(token, TokenType.OBJECT), finish, ScriptRuntime.UNDEFINED);
        }

        private UndefinedLiteralNode(final UndefinedLiteralNode literalNode) {
            super(literalNode);
        }
    }

    /**
     * Create a new undefined literal
     *
     * @param token   token
     * @param finish  finish
     * @param value   undefined value, passed only for polymorphisism discrimination
     *
     * @return the new literal node
     */
    public static LiteralNode<Undefined> newInstance(final long token, final int finish, final Undefined value) {
        return new UndefinedLiteralNode(token, finish);
    }

    /**
     * Create a new null literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     * @param value  undefined value
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent, final Undefined value) {
        return new UndefinedLiteralNode(parent.getToken(), parent.getFinish());
    }

    @Immutable
    private static class StringLiteralNode extends PrimitiveLiteralNode<String> {
        private StringLiteralNode(final long token, final int finish, final String value) {
            super(Token.recast(token, TokenType.STRING), finish, value);
        }

        private StringLiteralNode(final StringLiteralNode literalNode) {
            super(literalNode);
        }

        @Override
        public void toString(final StringBuilder sb) {
            sb.append('\"');
            sb.append(value);
            sb.append('\"');
        }
    }

    /**
     * Create a new string literal
     *
     * @param token   token
     * @param finish  finish
     * @param value   string value
     *
     * @return the new literal node
     */
    public static LiteralNode<String> newInstance(final long token, final int finish, final String value) {
        return new StringLiteralNode(token, finish, value);
    }

    /**
     * Create a new String literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     * @param value  string value
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent, final String value) {
        return new StringLiteralNode(parent.getToken(), parent.getFinish(), value);
    }

    @Immutable
    private static class LexerTokenLiteralNode extends LiteralNode<LexerToken> {
        private LexerTokenLiteralNode(final long token, final int finish, final LexerToken value) {
            super(Token.recast(token, TokenType.STRING), finish, value); //TODO is string the correct token type here?
        }

        private LexerTokenLiteralNode(final LexerTokenLiteralNode literalNode) {
            super(literalNode);
        }

        @Override
        public Type getType() {
            return Type.OBJECT;
        }

        @Override
        public void toString(final StringBuilder sb) {
            sb.append(value.toString());
        }
    }

    /**
     * Create a new literal node for a lexer token
     *
     * @param token   token
     * @param finish  finish
     * @param value   lexer token value
     *
     * @return the new literal node
     */
    public static LiteralNode<LexerToken> newInstance(final long token, final int finish, final LexerToken value) {
        return new LexerTokenLiteralNode(token, finish, value);
    }

    /**
     * Create a new lexer token literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     * @param value  lexer token
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent, final LexerToken value) {
        return new LexerTokenLiteralNode(parent.getToken(), parent.getFinish(), value);
    }

    /**
     * Get the constant value for an object, or {@link #POSTSET_MARKER} if the value can't be statically computed.
     *
     * @param object a node or value object
     * @return the constant value or {@code POSTSET_MARKER}
     */
    public static Object objectAsConstant(final Object object) {
        if (object == null) {
            return null;
        } else if (object instanceof Number || object instanceof String || object instanceof Boolean) {
            return object;
        } else if (object instanceof LiteralNode) {
            return objectAsConstant(((LiteralNode<?>)object).getValue());
        }

        return POSTSET_MARKER;
    }

    private static final class NullLiteralNode extends PrimitiveLiteralNode<Object> {

        private NullLiteralNode(final long token, final int finish) {
            super(Token.recast(token, TokenType.OBJECT), finish, null);
        }

        @Override
        public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
            if (visitor.enterLiteralNode(this)) {
                return visitor.leaveLiteralNode(this);
            }

            return this;
        }

        @Override
        public Type getType() {
            return Type.OBJECT;
        }

        @Override
        public Type getWidestOperationType() {
            return Type.OBJECT;
        }
    }

    /**
     * Array literal node class.
     */
    public static final class ArrayLiteralNode extends LiteralNode<Expression[]> {

        /** Array element type. */
        private Type elementType;

        /** Preset constant array. */
        private Object presets;

        /** Indices of array elements requiring computed post sets. */
        private int[] postsets;

        private List<ArrayUnit> units;

        /**
         * An ArrayUnit is a range in an ArrayLiteral. ArrayLiterals can
         * be split if they are too large, for bytecode generation reasons
         */
        public static class ArrayUnit {
            /** Compile unit associated with the postsets range. */
            private final CompileUnit compileUnit;

            /** postsets range associated with the unit (hi not inclusive). */
            private final int lo, hi;

            /**
             * Constructor
             * @param compileUnit compile unit
             * @param lo lowest array index in unit
             * @param hi highest array index in unit + 1
             */
            public ArrayUnit(final CompileUnit compileUnit, final int lo, final int hi) {
                this.compileUnit = compileUnit;
                this.lo   = lo;
                this.hi   = hi;
            }

            /**
             * Get the high index position of the ArrayUnit (non inclusive)
             * @return high index position
             */
            public int getHi() {
                return hi;
            }

            /**
             * Get the low index position of the ArrayUnit (inclusive)
             * @return low index position
             */
            public int getLo() {
                return lo;
            }

            /**
             * The array compile unit
             * @return array compile unit
             */
            public CompileUnit getCompileUnit() {
                return compileUnit;
            }
        }

        /**
         * Constructor
         *
         * @param token   token
         * @param finish  finish
         * @param value   array literal value, a Node array
         */
        protected ArrayLiteralNode(final long token, final int finish, final Expression[] value) {
            super(Token.recast(token, TokenType.ARRAY), finish, value);
            this.elementType = Type.UNKNOWN;
        }

        /**
         * Copy constructor
         * @param node source array literal node
         */
        private ArrayLiteralNode(final ArrayLiteralNode node, final Expression[] value) {
            super(node, value);
            this.elementType = node.elementType;
            this.presets     = node.presets;
            this.postsets    = node.postsets;
            this.units       = node.units;
        }

        /**
         * Compute things like widest element type needed. Internal use from compiler only
         */
        public void analyze() {
            elementType = Type.INT;
            analyzeElements();

            if (elementType.isInteger()) {
                presetIntArray();
            } else if (elementType.isLong()) {
                presetLongArray();
            } else if (elementType.isNumeric()) {
                presetNumberArray();
            } else {
                presetObjectArray();
            }
        }

        private void presetIntArray() {
            final int[] array = new int[value.length];
            final int[] computed = new int[value.length];
            int nComputed = 0;

            for (int i = 0; i < value.length; i++) {
                final Object element = objectAsConstant(value[i]);

                if (element instanceof Number) {
                    array[i] = ((Number)element).intValue();
                } else {
                    computed[nComputed++] = i;
                }
            }

            presets = array;
            postsets = Arrays.copyOf(computed, nComputed);
        }

        private void presetLongArray() {
            final long[] array = new long[value.length];
            final int[] computed = new int[value.length];
            int nComputed = 0;

            for (int i = 0; i < value.length; i++) {
                final Object element = objectAsConstant(value[i]);

                if (element instanceof Number) {
                    array[i] = ((Number)element).longValue();
                } else {
                    computed[nComputed++] = i;
                }
            }

            presets = array;
            postsets = Arrays.copyOf(computed, nComputed);
        }

        private void presetNumberArray() {
            final double[] array = new double[value.length];
            final int[] computed = new int[value.length];
            int nComputed = 0;

            for (int i = 0; i < value.length; i++) {
                final Object element = objectAsConstant(value[i]);

                if (element instanceof Number) {
                    array[i] = ((Number)element).doubleValue();
                } else {
                    computed[nComputed++] = i;
                }
            }

            presets = array;
            postsets = Arrays.copyOf(computed, nComputed);
        }

        private void presetObjectArray() {
            final Object[] array = new Object[value.length];
            final int[] computed = new int[value.length];
            int nComputed = 0;

            for (int i = 0; i < value.length; i++) {
                final Node node = value[i];

                if (node == null) {
                    computed[nComputed++] = i;
                } else {
                    final Object element = objectAsConstant(node);

                    if (element != POSTSET_MARKER) {
                        array[i] = element;
                    } else {
                        computed[nComputed++] = i;
                    }
                }
            }

            presets = array;
            postsets = Arrays.copyOf(computed, nComputed);
        }

        private void analyzeElements() {
            for (final Expression node : value) {
                if (node == null) {
                    elementType = elementType.widest(Type.OBJECT); //no way to represent undefined as number
                    break;
                }

                assert node.getSymbol() != null; //don't run this on unresolved nodes or you are in trouble
                Type symbolType = node.getSymbol().getSymbolType();
                if (symbolType.isUnknown()) {
                    symbolType = Type.OBJECT;
                }

                if (symbolType.isBoolean()) {
                    elementType = elementType.widest(Type.OBJECT);
                    break;
                }

                elementType = elementType.widest(symbolType);

                if (elementType.isObject()) {
                    break;
                }
            }
        }

        @Override
        public Node[] getArray() {
            return value;
        }

        public ArrayType getArrayType() {
            if (elementType.isInteger()) {
                return Type.INT_ARRAY;
            } else if (elementType.isLong()) {
                return Type.LONG_ARRAY;
            } else if (elementType.isNumeric()) {
                return Type.NUMBER_ARRAY;
            } else {
                return Type.OBJECT_ARRAY;
            }
        }

        @Override
        public Type getType() {
            return Type.typeFor(NativeArray.class);
        }

        /**
         * Get the element type of this array literal
         * @return element type
         */
        public Type getElementType() {
            return elementType;
        }

        /**
         * Get indices of arrays containing computed post sets. post sets
         * are things like non literals e.g. "x+y" instead of i or 17
         * @return post set indices
         */
        public int[] getPostsets() {
            return postsets;
        }

        /**
         * Get presets constant array
         * @return presets array, always returns an array type
         */
        public Object getPresets() {
            return presets;
        }

        /**
         * Get the array units that make up this ArrayLiteral
         * @see ArrayUnit
         * @return list of array units
         */
        public List<ArrayUnit> getUnits() {
            return units == null ? null : Collections.unmodifiableList(units);
        }

        /**
         * Set the ArrayUnits that make up this ArrayLiteral
         * @see ArrayUnit
         * @param units list of array units
         */
        public void setUnits(final List<ArrayUnit> units) {
            this.units = units;
        }

        @Override
        public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
            if (visitor.enterLiteralNode(this)) {
                final List<Expression> oldValue = Arrays.asList(value);
                final List<Expression> newValue = Node.accept(visitor, Expression.class, oldValue);
                return visitor.leaveLiteralNode(oldValue != newValue ? setValue(newValue) : this);
            }
            return this;
        }

        private ArrayLiteralNode setValue(final List<Expression> value) {
            return new ArrayLiteralNode(this, value.toArray(new Expression[value.size()]));
        }

        @Override
        public void toString(final StringBuilder sb) {
            sb.append('[');
            boolean first = true;
            for (final Node node : value) {
                if (!first) {
                    sb.append(',');
                    sb.append(' ');
                }
                if (node == null) {
                    sb.append("undefined");
                } else {
                    node.toString(sb);
                }
                first = false;
            }
            sb.append(']');
        }
    }

    /**
     * Create a new array literal of Nodes from a list of Node values
     *
     * @param token   token
     * @param finish  finish
     * @param value   literal value list
     *
     * @return the new literal node
     */
    public static LiteralNode<Expression[]> newInstance(final long token, final int finish, final List<Expression> value) {
        return new ArrayLiteralNode(token, finish, value.toArray(new Expression[value.size()]));
    }


    /**
     * Create a new array literal based on a parent node (source, token, finish)
     *
     * @param parent parent node
     * @param value  literal value list
     *
     * @return the new literal node
     */
    public static LiteralNode<?> newInstance(final Node parent, final List<Expression> value) {
        return new ArrayLiteralNode(parent.getToken(), parent.getFinish(), value.toArray(new Expression[value.size()]));
    }

    /**
     * Create a new array literal of Nodes
     *
     * @param token   token
     * @param finish  finish
     * @param value   literal value array
     *
     * @return the new literal node
     */
    public static LiteralNode<Expression[]> newInstance(final long token, final int finish, final Expression[] value) {
        return new ArrayLiteralNode(token, finish, value);
    }
}
