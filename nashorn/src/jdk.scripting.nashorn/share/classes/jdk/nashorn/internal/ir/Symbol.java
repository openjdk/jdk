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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.Debug;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * Symbol is a symbolic address for a value ("variable" if you wish). Identifiers in JavaScript source, as well as
 * certain synthetic variables created by the compiler are represented by Symbol objects. Symbols can address either
 * local variable slots in bytecode ("slotted symbol"), or properties in scope objects ("scoped symbol"). A symbol can
 * also end up being defined but then not used during symbol assignment calculations; such symbol will be neither
 * scoped, nor slotted; it represents a dead variable (it might be written to, but is never read). Finally, a symbol can
 * be both slotted and in scope. This special case can only occur with bytecode method parameters. They all come in as
 * slotted, but if they are used by a nested function (or eval) then they will be copied into the scope object, and used
 * from there onwards. Two further special cases are parameters stored in {@code NativeArguments} objects and parameters
 * stored in {@code Object[]} parameter to variable-arity functions. Those use the {@code #getFieldIndex()} property to
 * refer to their location.
 */

public final class Symbol implements Comparable<Symbol>, Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    /** Is this Global */
    public static final int IS_GLOBAL   = 1;
    /** Is this a variable */
    public static final int IS_VAR      = 2;
    /** Is this a parameter */
    public static final int IS_PARAM    = 3;
    /** Mask for kind flags */
    public static final int KINDMASK = (1 << 2) - 1; // Kinds are represented by lower two bits

    /** Is this symbol in scope */
    public static final int IS_SCOPE                = 1 <<  2;
    /** Is this a this symbol */
    public static final int IS_THIS                 = 1 <<  3;
    /** Is this a let */
    public static final int IS_LET                  = 1 <<  4;
    /** Is this a const */
    public static final int IS_CONST                = 1 <<  5;
    /** Is this an internal symbol, never represented explicitly in source code */
    public static final int IS_INTERNAL             = 1 <<  6;
    /** Is this a function self-reference symbol */
    public static final int IS_FUNCTION_SELF        = 1 <<  7;
    /** Is this a function declaration? */
    public static final int IS_FUNCTION_DECLARATION = 1 <<  8;
    /** Is this a program level symbol? */
    public static final int IS_PROGRAM_LEVEL        = 1 <<  9;
    /** Are this symbols' values stored in local variable slots? */
    public static final int HAS_SLOT                = 1 << 10;
    /** Is this symbol known to store an int value ? */
    public static final int HAS_INT_VALUE           = 1 << 11;
    /** Is this symbol known to store a long value ? */
    public static final int HAS_LONG_VALUE          = 1 << 12;
    /** Is this symbol known to store a double value ? */
    public static final int HAS_DOUBLE_VALUE        = 1 << 13;
    /** Is this symbol known to store an object value ? */
    public static final int HAS_OBJECT_VALUE        = 1 << 14;
    /** Is this symbol seen a declaration? Used for block scoped LET and CONST symbols only. */
    public static final int HAS_BEEN_DECLARED       = 1 << 15;

    /** Null or name identifying symbol. */
    private final String name;

    /** Symbol flags. */
    private int flags;

    /** First bytecode method local variable slot for storing the value(s) of this variable. -1 indicates the variable
     * is not stored in local variable slots or it is not yet known. */
    private transient int firstSlot = -1;

    /** Field number in scope or property; array index in varargs when not using arguments object. */
    private transient int fieldIndex = -1;

    /** Number of times this symbol is used in code */
    private int useCount;

    /** Debugging option - dump info and stack trace when symbols with given names are manipulated */
    private static final Set<String> TRACE_SYMBOLS;
    private static final Set<String> TRACE_SYMBOLS_STACKTRACE;

    static {
        final String stacktrace = Options.getStringProperty("nashorn.compiler.symbol.stacktrace", null);
        final String trace;
        if (stacktrace != null) {
            trace = stacktrace; //stacktrace always implies trace as well
            TRACE_SYMBOLS_STACKTRACE = new HashSet<>();
            for (final StringTokenizer st = new StringTokenizer(stacktrace, ","); st.hasMoreTokens(); ) {
                TRACE_SYMBOLS_STACKTRACE.add(st.nextToken());
            }
        } else {
            trace = Options.getStringProperty("nashorn.compiler.symbol.trace", null);
            TRACE_SYMBOLS_STACKTRACE = null;
        }

        if (trace != null) {
            TRACE_SYMBOLS = new HashSet<>();
            for (final StringTokenizer st = new StringTokenizer(trace, ","); st.hasMoreTokens(); ) {
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
     */
    public Symbol(final String name, final int flags) {
        this.name       = name;
        this.flags      = flags;
        if(shouldTrace()) {
            trace("CREATE SYMBOL " + name);
        }
    }

    @Override
    public Symbol clone() {
        try {
            return (Symbol)super.clone();
        } catch (final CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
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
     * Debugging .
     *
     * @param stream Stream to print to.
     */

    void print(final PrintWriter stream) {
        final StringBuilder sb = new StringBuilder();

        sb.append(align(name, 20)).
            append(": ").
            append(", ").
            append(align(firstSlot == -1 ? "none" : "" + firstSlot, 10));

        switch (flags & KINDMASK) {
        case IS_GLOBAL:
            sb.append(" global");
            break;
        case IS_VAR:
            if (isConst()) {
                sb.append(" const");
            } else if (isLet()) {
                sb.append(" let");
            } else {
                sb.append(" var");
            }
            break;
        case IS_PARAM:
            sb.append(" param");
            break;
        default:
            break;
        }

        if (isScope()) {
            sb.append(" scope");
        }

        if (isInternal()) {
            sb.append(" internal");
        }

        if (isThis()) {
            sb.append(" this");
        }

        if (isProgramLevel()) {
            sb.append(" program");
        }

        sb.append('\n');

        stream.print(sb.toString());
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
     * @return the symbol
     */
    public Symbol setNeedsSlot(final boolean needsSlot) {
        if(needsSlot) {
            assert !isScope();
            flags |= HAS_SLOT;
        } else {
            flags &= ~HAS_SLOT;
        }
        return this;
    }

    /**
     * Return the number of slots required for the symbol.
     *
     * @return Number of slots.
     */
    public int slotCount() {
        return ((flags & HAS_INT_VALUE)    == 0 ? 0 : 1) +
               ((flags & HAS_LONG_VALUE)   == 0 ? 0 : 2) +
               ((flags & HAS_DOUBLE_VALUE) == 0 ? 0 : 2) +
               ((flags & HAS_OBJECT_VALUE) == 0 ? 0 : 1);
    }

    private boolean isSlotted() {
        return firstSlot != -1 && ((flags & HAS_SLOT) != 0);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append(name).
            append(' ');

        if (hasSlot()) {
            sb.append(' ').
                append('(').
                append("slot=").
                append(firstSlot).append(' ');
            if((flags & HAS_INT_VALUE) != 0) { sb.append('I'); }
            if((flags & HAS_LONG_VALUE) != 0) { sb.append('J'); }
            if((flags & HAS_DOUBLE_VALUE) != 0) { sb.append('D'); }
            if((flags & HAS_OBJECT_VALUE) != 0) { sb.append('O'); }
            sb.append(')');
        }

        if (isScope()) {
            if(isGlobal()) {
                sb.append(" G");
            } else {
                sb.append(" S");
            }
        }

        return sb.toString();
    }

    @Override
    public int compareTo(final Symbol other) {
        return name.compareTo(other.name);
    }

    /**
     * Does this symbol have an allocated bytecode slot? Note that having an allocated bytecode slot doesn't necessarily
     * mean the symbol's value will be stored in it. Namely, a function parameter can have a bytecode slot, but if it is
     * in scope, then the bytecode slot will not be used. See {@link #isBytecodeLocal()}.
     *
     * @return true if this symbol has a local bytecode slot
     */
    public boolean hasSlot() {
        return (flags & HAS_SLOT) != 0;
    }

    /**
     * Is this symbol a local variable stored in bytecode local variable slots? This is true for a slotted variable that
     * is not in scope. (E.g. a parameter that is in scope is slotted, but it will not be a local variable).
     * @return true if this symbol is using bytecode local slots for its storage.
     */
    public boolean isBytecodeLocal() {
        return hasSlot() && !isScope();
    }

    /**
     * Returns true if this symbol is dead (it is a local variable that is statically proven to never be read in any type).
     * @return true if this symbol is dead
     */
    public boolean isDead() {
        return (flags & (HAS_SLOT | IS_SCOPE)) == 0;
    }

    /**
     * Check if this is a symbol in scope. Scope symbols cannot, for obvious reasons
     * be stored in byte code slots on the local frame
     *
     * @return true if this is scoped
     */
    public boolean isScope() {
        assert (flags & KINDMASK) != IS_GLOBAL || (flags & IS_SCOPE) == IS_SCOPE : "global without scope flag";
        return (flags & IS_SCOPE) != 0;
    }

    /**
     * Check if this symbol is a function declaration
     * @return true if a function declaration
     */
    public boolean isFunctionDeclaration() {
        return (flags & IS_FUNCTION_DECLARATION) != 0;
    }

    /**
     * Flag this symbol as scope as described in {@link Symbol#isScope()}
     * @return the symbol
     */
    public Symbol setIsScope() {
        if (!isScope()) {
            if(shouldTrace()) {
                trace("SET IS SCOPE");
            }
            flags |= IS_SCOPE;
            if(!isParam()) {
                flags &= ~HAS_SLOT;
            }
        }
        return this;
    }

    /**
     * Mark this symbol as a function declaration.
     */
    public void setIsFunctionDeclaration() {
        if (!isFunctionDeclaration()) {
            if(shouldTrace()) {
                trace("SET IS FUNCTION DECLARATION");
            }
            flags |= IS_FUNCTION_DECLARATION;
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
     * Check if this is a program (script) level definition
     * @return true if program level
     */
    public boolean isProgramLevel() {
        return (flags & IS_PROGRAM_LEVEL) != 0;
    }

    /**
     * Check if this symbol is a constant
     * @return true if a constant
     */
    public boolean isConst() {
        return (flags & IS_CONST) != 0;
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
        return (flags & IS_LET) != 0;
    }

    /**
     * Flag this symbol as a function's self-referencing symbol.
     * @return true if this symbol as a function's self-referencing symbol.
     */
    public boolean isFunctionSelf() {
        return (flags & IS_FUNCTION_SELF) != 0;
    }

    /**
     * Is this a block scoped symbol
     * @return true if block scoped
     */
    public boolean isBlockScoped() {
        return isLet() || isConst();
    }

    /**
     * Has this symbol been declared
     * @return true if declared
     */
    public boolean hasBeenDeclared() {
        return (flags & HAS_BEEN_DECLARED) != 0;
    }

    /**
     * Mark this symbol as declared
     */
    public void setHasBeenDeclared() {
        if (!hasBeenDeclared()) {
            flags |= HAS_BEEN_DECLARED;
        }
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
     * @return the symbol
     */
    public Symbol setFieldIndex(final int fieldIndex) {
        if (this.fieldIndex != fieldIndex) {
            this.fieldIndex = fieldIndex;
        }
        return this;
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
     * @return the symbol
     */
    public Symbol setFlags(final int flags) {
        if (this.flags != flags) {
            this.flags = flags;
        }
        return this;
    }

    /**
     * Set a single symbol flag
     * @param flag flag to set
     * @return the symbol
     */
    public Symbol setFlag(final int flag) {
        if ((this.flags & flag) == 0) {
            this.flags |= flag;
        }
        return this;
    }

    /**
     * Clears a single symbol flag
     * @param flag flag to set
     * @return the symbol
     */
    public Symbol clearFlag(final int flag) {
        if ((this.flags & flag) != 0) {
            this.flags &= ~flag;
        }
        return this;
    }

    /**
     * Get the name of this symbol
     * @return symbol name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the index of the first bytecode slot for this symbol
     * @return byte code slot
     */
    public int getFirstSlot() {
        assert isSlotted();
        return firstSlot;
    }

    /**
     * Get the index of the bytecode slot for this symbol for storing a value of the specified type.
     * @param type the requested type
     * @return byte code slot
     */
    public int getSlot(final Type type) {
        assert isSlotted();
        int typeSlot = firstSlot;
        if(type.isBoolean() || type.isInteger()) {
            assert (flags & HAS_INT_VALUE) != 0;
            return typeSlot;
        }
        typeSlot += ((flags & HAS_INT_VALUE) == 0 ? 0 : 1);
        if(type.isLong()) {
            assert (flags & HAS_LONG_VALUE) != 0;
            return typeSlot;
        }
        typeSlot += ((flags & HAS_LONG_VALUE) == 0 ? 0 : 2);
        if(type.isNumber()) {
            assert (flags & HAS_DOUBLE_VALUE) != 0;
            return typeSlot;
        }
        assert type.isObject();
        assert (flags & HAS_OBJECT_VALUE) != 0 : name;
        return typeSlot + ((flags & HAS_DOUBLE_VALUE) == 0 ? 0 : 2);
    }

    /**
     * Returns true if this symbol has a local variable slot for storing a value of specific type.
     * @param type the type
     * @return true if this symbol has a local variable slot for storing a value of specific type.
     */
    public boolean hasSlotFor(final Type type) {
        if(type.isBoolean() || type.isInteger()) {
            return (flags & HAS_INT_VALUE) != 0;
        } else if(type.isLong()) {
            return (flags & HAS_LONG_VALUE) != 0;
        } else if(type.isNumber()) {
            return (flags & HAS_DOUBLE_VALUE) != 0;
        }
        assert type.isObject();
        return (flags & HAS_OBJECT_VALUE) != 0;
    }

    /**
     * Marks this symbol as having a local variable slot for storing a value of specific type.
     * @param type the type
     */
    public void setHasSlotFor(final Type type) {
        if(type.isBoolean() || type.isInteger()) {
            setFlag(HAS_INT_VALUE);
        } else if(type.isLong()) {
            setFlag(HAS_LONG_VALUE);
        } else if(type.isNumber()) {
            setFlag(HAS_DOUBLE_VALUE);
        } else {
            assert type.isObject();
            setFlag(HAS_OBJECT_VALUE);
        }
    }

    /**
     * Increase the symbol's use count by one.
     */
    public void increaseUseCount() {
        if (isScope()) { // Avoid dirtying a cache line; we only need the use count for scoped symbols
            useCount++;
        }
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
     * @param  firstSlot valid bytecode slot
     * @return the symbol
     */
    public Symbol setFirstSlot(final int firstSlot) {
        assert firstSlot >= 0 && firstSlot <= 65535;
        if (firstSlot != this.firstSlot) {
            if(shouldTrace()) {
                trace("SET SLOT " + firstSlot);
            }
            this.firstSlot = firstSlot;
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
     * @param  lc     lexical context
     * @param  symbol symbol
     * @return the symbol
     */
    public static Symbol setSymbolIsScope(final LexicalContext lc, final Symbol symbol) {
        symbol.setIsScope();
        if (!symbol.isGlobal()) {
            lc.setBlockNeedsScope(lc.getDefiningBlock(symbol));
        }
        return symbol;
    }

    private boolean shouldTrace() {
        return TRACE_SYMBOLS != null && (TRACE_SYMBOLS.isEmpty() || TRACE_SYMBOLS.contains(name));
    }

    private void trace(final String desc) {
        Context.err(Debug.id(this) + " SYMBOL: '" + name + "' " + desc);
        if (TRACE_SYMBOLS_STACKTRACE != null && (TRACE_SYMBOLS_STACKTRACE.isEmpty() || TRACE_SYMBOLS_STACKTRACE.contains(name))) {
            new Throwable().printStackTrace(Context.getCurrentErr());
        }
    }

    private void readObject(final ObjectInputStream in) throws ClassNotFoundException, IOException {
        in.defaultReadObject();
        firstSlot = -1;
        fieldIndex = -1;
    }
}
