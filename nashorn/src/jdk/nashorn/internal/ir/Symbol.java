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

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import jdk.nashorn.internal.codegen.types.Range;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.Debug;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Maps a name to specific data.
 */

public final class Symbol implements Comparable<Symbol> {
    /** Symbol kinds. Kind ordered by precedence. */
    public static final int IS_TEMP     = 1;
    /** Is this Global */
    public static final int IS_GLOBAL   = 2;
    /** Is this a variable */
    public static final int IS_VAR      = 3;
    /** Is this a parameter */
    public static final int IS_PARAM    = 4;
    /** Is this a constant */
    public static final int IS_CONSTANT = 5;
    /** Mask for kind flags */
    public static final int KINDMASK = (1 << 3) - 1; // Kinds are represented by lower three bits

    /** Is this scope */
    public static final int IS_SCOPE             = 1 <<  4;
    /** Is this a this symbol */
    public static final int IS_THIS              = 1 <<  5;
    /** Can this symbol ever be undefined */
    public static final int CAN_BE_UNDEFINED     = 1 <<  6;
    /** Is this symbol always defined? */
    public static final int IS_ALWAYS_DEFINED    = 1 <<  8;
    /** Can this symbol ever have primitive types */
    public static final int CAN_BE_PRIMITIVE     = 1 <<  9;
    /** Is this a let */
    public static final int IS_LET               = 1 << 10;
    /** Is this an internal symbol, never represented explicitly in source code */
    public static final int IS_INTERNAL          = 1 << 11;
    /** Is this a function self-reference symbol */
    public static final int IS_FUNCTION_SELF     = 1 << 12;
    /** Is this a specialized param? */
    public static final int IS_SPECIALIZED_PARAM = 1 << 13;
    /** Is this symbol a shared temporary? */
    public static final int IS_SHARED            = 1 << 14;

    /** Null or name identifying symbol. */
    private final String name;

    /** Symbol flags. */
    private int flags;

    /** Type of symbol. */
    private Type type;

    /** Local variable slot. -1 indicates external property. */
    private int slot;

    /** Field number in scope or property; array index in varargs when not using arguments object. */
    private int fieldIndex;

    /** Number of times this symbol is used in code */
    private int useCount;

    /** Range for symbol */
    private Range range;

    /** Debugging option - dump info and stack trace when symbols with given names are manipulated */
    private static final Set<String> TRACE_SYMBOLS;
    private static final Set<String> TRACE_SYMBOLS_STACKTRACE;

    static {
        final String stacktrace = Options.getStringProperty("nashorn.compiler.symbol.stacktrace", null);
        final String trace;
        if (stacktrace != null) {
            trace = stacktrace; //stacktrace always implies trace as well
            TRACE_SYMBOLS_STACKTRACE = new HashSet<>();
            for (StringTokenizer st = new StringTokenizer(stacktrace, ","); st.hasMoreTokens(); ) {
                TRACE_SYMBOLS_STACKTRACE.add(st.nextToken());
            }
        } else {
            trace = Options.getStringProperty("nashorn.compiler.symbol.trace", null);
            TRACE_SYMBOLS_STACKTRACE = null;
        }

        if (trace != null) {
            TRACE_SYMBOLS = new HashSet<>();
            for (StringTokenizer st = new StringTokenizer(trace, ","); st.hasMoreTokens(); ) {
                TRACE_SYMBOLS.add(st.nextToken());
            }
        } else {
            TRACE_SYMBOLS = null;
        }
    }

    /**
     * Constructor
     *
     * @param name  name of symbol
     * @param flags symbol flags
     * @param type  type of this symbol
     * @param slot  bytecode slot for this symbol
     */
    protected Symbol(final String name, final int flags, final Type type, final int slot) {
        this.name       = name;
        this.flags      = flags;
        this.type       = type;
        this.slot       = slot;
        this.fieldIndex = -1;
        this.range      = Range.createUnknownRange();
        trace("CREATE SYMBOL");
    }

    /**
     * Constructor
     *
     * @param name  name of symbol
     * @param flags symbol flags
     */
    public Symbol(final String name, final int flags) {
        this(name, flags, Type.UNKNOWN, -1);
    }

    /**
     * Constructor
     *
     * @param name  name of symbol
     * @param flags symbol flags
     * @param type  type of this symbol
     */
    public Symbol(final String name, final int flags, final Type type) {
        this(name, flags, type, -1);
    }

    private Symbol(final Symbol base, final String name, final int flags) {
        this.flags = flags;
        this.name  = name;

        this.fieldIndex = base.fieldIndex;
        this.slot       = base.slot;
        this.type       = base.type;
        this.useCount   = base.useCount;
        this.range      = base.range;
    }

    private static String align(final String string, final int max) {
        final StringBuilder sb = new StringBuilder();
        sb.append(string.substring(0, Math.min(string.length(), max)));

        while (sb.length() < max) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Return the type for this symbol. Normally, if there is no type override,
     * this is where any type for any node is stored. If the node has a TypeOverride,
     * it may override this, e.g. when asking for a scoped field as a double
     *
     * @return symbol type
     */
    public final Type getSymbolType() {
        return type;
    }

    /**
     * Debugging .
     *
     * @param stream Stream to print to.
     */

    void print(final PrintWriter stream) {
        final String printName = align(name, 20);
        final String printType = align(type.toString(), 10);
        final String printSlot = align(slot == -1 ? "none" : "" + slot, 10);
        String printFlags = "";

        switch (flags & KINDMASK) {
        case IS_TEMP:
            printFlags = "temp " + printFlags;
            break;
        case IS_GLOBAL:
            printFlags = "global " + printFlags;
            break;
        case IS_VAR:
            printFlags = "var " + printFlags;
            break;
        case IS_PARAM:
            printFlags = "param " + printFlags;
            break;
        case IS_CONSTANT:
            printFlags = "CONSTANT " + printFlags;
            break;
        default:
            break;
        }

        if (isScope()) {
            printFlags += "scope ";
        }

        if (isInternal()) {
            printFlags += "internal ";
        }

        if (isLet()) {
            printFlags += "let ";
        }

        if (isThis()) {
            printFlags += "this ";
        }

        if (!canBeUndefined()) {
            printFlags += "always_def ";
        }

        if (canBePrimitive()) {
            printFlags += "can_be_prim ";
        }

        stream.print(printName + ": " + printType + ", " + printSlot + ", " + printFlags);
        stream.println();
    }

    /**
     * Compare the the symbol kind with another.
     *
     * @param other Other symbol's flags.
     * @return True if symbol has less kind.
     */
    public boolean less(final int other) {
        return (flags & KINDMASK) < (other & KINDMASK);
    }

    /**
     * Allocate a slot for this symbol.
     *
     * @param needsSlot True if symbol needs a slot.
     */
    public void setNeedsSlot(final boolean needsSlot) {
        setSlot(needsSlot ? 0 : -1);
    }

    /**
     * Return the number of slots required for the symbol.
     *
     * @return Number of slots.
     */
    public int slotCount() {
        return type.isCategory2() ? 2 : 1;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append(name).
            append(' ').
            append('(').
            append(getSymbolType().getTypeClass().getSimpleName()).
            append(')');

        if (hasSlot()) {
            sb.append(' ').
                append('(').
                append("slot=").
                append(slot).
                append(')');
        }

        if (isScope()) {
            if(isGlobal()) {
                sb.append(" G");
            } else {
                sb.append(" S");
            }
        }

        if (canBePrimitive()) {
            sb.append(" P?");
        }

        return sb.toString();
    }

    @Override
    public int compareTo(final Symbol other) {
        return name.compareTo(other.name);
    }

    /**
     * Does this symbol have an allocated bytecode slot. If not, it is scope
     * and must be loaded from memory upon access
     *
     * @return true if this symbol has a local bytecode slot
     */
    public boolean hasSlot() {
        return slot >= 0;
    }

    /**
     * Check if this is a temporary symbol
     * @return true if temporary
     */
    public boolean isTemp() {
        return (flags & KINDMASK) == IS_TEMP;
    }

    /**
     * Check if this is a symbol in scope. Scope symbols cannot, for obvious reasons
     * be stored in byte code slots on the local frame
     *
     * @return true if this is scoped
     */
    public boolean isScope() {
        assert ((flags & KINDMASK) != IS_GLOBAL) || ((flags & IS_SCOPE) == IS_SCOPE) : "global without scope flag";
        return (flags & IS_SCOPE) == IS_SCOPE;
    }

    /**
     * Returns true if this symbol is a temporary that is being shared across expressions.
     * @return true if this symbol is a temporary that is being shared across expressions.
     */
    public boolean isShared() {
        return (flags & IS_SHARED) == IS_SHARED;
    }

    /**
     * Creates an unshared copy of a symbol. The symbol must be currently shared.
     * @param newName the name for the new symbol.
     * @return a new, unshared symbol.
     */
    public Symbol createUnshared(final String newName) {
        assert isShared();
        return new Symbol(this, newName, flags & ~IS_SHARED);
    }

    /**
     * Flag this symbol as scope as described in {@link Symbol#isScope()}
     */
    /**
     * Flag this symbol as scope as described in {@link Symbol#isScope()}
     */
     public void setIsScope() {
        if (!isScope()) {
            trace("SET IS SCOPE");
            assert !isShared();
            flags |= IS_SCOPE;
        }
    }

     /**
      * Mark this symbol as one being shared by multiple expressions. The symbol must be a temporary.
      */
     public void setIsShared() {
         if (!isShared()) {
             assert isTemp();
             trace("SET IS SHARED");
             flags |= IS_SHARED;
         }
     }


    /**
     * Check if this symbol is a variable
     * @return true if variable
     */
    public boolean isVar() {
        return (flags & KINDMASK) == IS_VAR;
    }

    /**
     * Check if this symbol is a global (undeclared) variable
     * @return true if global
     */
    public boolean isGlobal() {
        return (flags & KINDMASK) == IS_GLOBAL;
    }

    /**
     * Check if this symbol is a function parameter
     * @return true if parameter
     */
    public boolean isParam() {
        return (flags & KINDMASK) == IS_PARAM;
    }

    /**
     * Check if this symbol is always defined, which overrides all canBeUndefined tags
     * @return true if always defined
     */
    public boolean isAlwaysDefined() {
        return isParam() || (flags & IS_ALWAYS_DEFINED) == IS_ALWAYS_DEFINED;
    }

    /**
     * Get the range for this symbol
     * @return range for symbol
     */
    public Range getRange() {
        return range;
    }

    /**
     * Set the range for this symbol
     * @param range range
     */
    public void setRange(final Range range) {
        this.range = range;
    }

    /**
     * Check if this symbol is a function parameter of known
     * narrowest type
     * @return true if parameter
     */
    public boolean isSpecializedParam() {
        return (flags & IS_SPECIALIZED_PARAM) == IS_SPECIALIZED_PARAM;
    }

    /**
     * Check whether this symbol ever has primitive assignments. Conservative
     * @return true if primitive assignments exist
     */
    public boolean canBePrimitive() {
        return (flags & CAN_BE_PRIMITIVE) == CAN_BE_PRIMITIVE;
    }

    /**
     * Check if this symbol can ever be undefined
     * @return true if can be undefined
     */
    public boolean canBeUndefined() {
        return (flags & CAN_BE_UNDEFINED) == CAN_BE_UNDEFINED;
    }

    /**
     * Flag this symbol as potentially undefined in parts of the program
     */
    public void setCanBeUndefined() {
        assert type.isObject() : type;
        if (isAlwaysDefined()) {
            return;
        } else if (!canBeUndefined()) {
            assert !isShared();
            flags |= CAN_BE_UNDEFINED;
        }
    }

    /**
     * Flag this symbol as potentially primitive
     * @param type the primitive type it occurs with, currently unused but can be used for width guesses
     */
    public void setCanBePrimitive(final Type type) {
        if(!canBePrimitive()) {
            assert !isShared();
            flags |= CAN_BE_PRIMITIVE;
        }
    }

    /**
     * Check if this symbol is a constant
     * @return true if a constant
     */
    public boolean isConstant() {
        return (flags & KINDMASK) == IS_CONSTANT;
    }

    /**
     * Check if this is an internal symbol, without an explicit JavaScript source
     * code equivalent
     * @return true if internal
     */
    public boolean isInternal() {
        return (flags & IS_INTERNAL) != 0;
    }

    /**
     * Check if this symbol represents {@code this}
     * @return true if this
     */
    public boolean isThis() {
        return (flags & IS_THIS) != 0;
    }

    /**
     * Check if this symbol is a let
     * @return true if let
     */
    public boolean isLet() {
        return (flags & IS_LET) == IS_LET;
    }

    /**
     * Flag this symbol as a let
     */
    public void setIsLet() {
        if(!isLet()) {
            assert !isShared();
            flags |= IS_LET;
        }
    }

    /**
     * Flag this symbol as a function's self-referencing symbol.
     * @return true if this symbol as a function's self-referencing symbol.
     */
    public boolean isFunctionSelf() {
        return (flags & IS_FUNCTION_SELF) == IS_FUNCTION_SELF;
    }

    /**
     * Get the index of the field used to store this symbol, should it be an AccessorProperty
     * and get allocated in a JO-prefixed ScriptObject subclass.
     *
     * @return field index
     */
    public int getFieldIndex() {
        assert fieldIndex != -1 : "fieldIndex must be initialized " + fieldIndex;
        return fieldIndex;
    }

    /**
     * Set the index of the field used to store this symbol, should it be an AccessorProperty
     * and get allocated in a JO-prefixed ScriptObject subclass.
     *
     * @param fieldIndex field index - a positive integer
     */
    public void setFieldIndex(final int fieldIndex) {
        if(this.fieldIndex != fieldIndex) {
            assert !isShared();
            this.fieldIndex = fieldIndex;
        }
    }

    /**
     * Get the symbol flags
     * @return flags
     */
    public int getFlags() {
        return flags;
    }

    /**
     * Set the symbol flags
     * @param flags flags
     */
    public void setFlags(final int flags) {
        if(this.flags != flags) {
            assert !isShared();
            this.flags = flags;
        }
    }

    /**
     * Get the name of this symbol
     * @return symbol name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the byte code slot for this symbol
     * @return byte code slot, or -1 if no slot allocated/possible
     */
    public int getSlot() {
        return slot;
    }

    /**
     * Increase the symbol's use count by one.
     */
    public void increaseUseCount() {
        useCount++;
    }

    /**
     * Get the symbol's use count
     * @return the number of times the symbol is used in code.
     */
    public int getUseCount() {
        return useCount;
    }

    /**
     * Set the bytecode slot for this symbol
     * @param slot valid bytecode slot, or -1 if not available
     */
    public void setSlot(final int slot) {
        if (slot != this.slot) {
            assert !isShared();
            trace("SET SLOT " + slot);
            this.slot = slot;
        }
    }

    /**
     * Assign a specific subclass of Object to the symbol
     *
     * @param type  the type
     */
    public void setType(final Class<?> type) {
        assert !type.isPrimitive() && !Number.class.isAssignableFrom(type) : "Class<?> types can only be subclasses of object";
        setType(Type.typeFor(type));
    }

    /**
     * Assign a type to the symbol
     *
     * @param type the type
     */
    public void setType(final Type type) {
        setTypeOverride(Type.widest(this.type, type));
    }

    /**
     * Returns true if calling {@link #setType(Type)} on this symbol would effectively change its type.
     * @param newType the new type to test for
     * @return true if setting this symbols type to a new value would effectively change its type.
     */
    public boolean wouldChangeType(final Type newType) {
        return Type.widest(this.type, newType) != this.type;
    }

    /**
     * Only use this if you know about an existing type
     * constraint - otherwise a type can only be
     * widened
     *
     * @param type  the type
     */
    public void setTypeOverride(final Type type) {
        final Type old = this.type;
        if (old != type) {
            assert !isShared();
            trace("TYPE CHANGE: " + old + "=>" + type + " == " + type);
            this.type = type;
        }
    }

    /**
     * Sets the type of the symbol to the specified type. If the type would be changed, but this symbol is a shared
     * temporary, it will instead return a different temporary symbol of the requested type from the passed temporary
     * symbols. That way, it never mutates the type of a shared temporary.
     * @param type the new type for the symbol
     * @param ts a holder of temporary symbols
     * @return either this symbol, or a different symbol if this symbol is a shared temporary and it type would have to
     * be changed.
     */
    public Symbol setTypeOverrideShared(final Type type, final TemporarySymbols ts) {
        if(getSymbolType() != type) {
            if(isShared()) {
                assert !hasSlot();
                return ts.getTypedTemporarySymbol(type);
            }
            setTypeOverride(type);
        }
        return this;
    }

    /**
     * From a lexical context, set this symbol as needing scope, which
     * will set flags for the defining block that will be written when
     * block is popped from the lexical context stack, used by codegen
     * when flags need to be tagged, but block is in the
     * middle of evaluation and cannot be modified.
     *
     * @param lc     lexical context
     * @param symbol symbol
     */
    public static void setSymbolIsScope(final LexicalContext lc, final Symbol symbol) {
        symbol.setIsScope();
        if (!symbol.isGlobal()) {
            lc.setFlag(lc.getDefiningBlock(symbol), Block.NEEDS_SCOPE);
        }
    }

    private void trace(final String desc) {
        if (TRACE_SYMBOLS != null && (TRACE_SYMBOLS.isEmpty() || TRACE_SYMBOLS.contains(name))) {
            Context.err(Debug.id(this) + " SYMBOL: '" + name + "' " + desc);
            if (TRACE_SYMBOLS_STACKTRACE != null && (TRACE_SYMBOLS_STACKTRACE.isEmpty() || TRACE_SYMBOLS_STACKTRACE.contains(name))) {
                new Throwable().printStackTrace(Context.getCurrentErr());
            }
        }
    }
}
