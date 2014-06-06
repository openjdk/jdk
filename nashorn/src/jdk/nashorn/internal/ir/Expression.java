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

import java.util.function.Function;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.runtime.UnwarrantedOptimismException;

/**
 * Common superclass for all expression nodes. Expression nodes can have
 * an associated symbol as well as a type.
 *
 */
public abstract class Expression extends Node {
    static final String OPT_IDENTIFIER = "%";

    private static final Function<Symbol, Type> UNKNOWN_LOCALS = new Function<Symbol, Type>() {
        @Override
        public Type apply(final Symbol t) {
            return null;
        }
    };

    Expression(final long token, final int start, final int finish) {
        super(token, start, finish);
    }

    Expression(final long token, final int finish) {
        super(token, finish);
    }

    Expression(final Expression expr) {
        super(expr);
    }

    /**
     * Returns the type of the expression.
     *
     * @return the type of the expression.
     */
    public final Type getType() {
        return getType(UNKNOWN_LOCALS);
    }

    /**
     * Returns the type of the expression under the specified symbol-to-type mapping. By default delegates to
     * {@link #getType()} but expressions whose type depends on their subexpressions' types and expressions whose type
     * depends on symbol type ({@link IdentNode}) will have a special implementation.
     * @param localVariableTypes a mapping from symbols to their types, used for type calculation.
     * @return the type of the expression under the specified symbol-to-type mapping.
     */
    public abstract Type getType(final Function<Symbol, Type> localVariableTypes);

    /**
     * Returns {@code true} if this expression depends exclusively on state that is constant
     * or local to the currently running function and thus inaccessible to other functions.
     * This implies that a local expression must not call any other functions (neither directly
     * nor implicitly through a getter, setter, or object-to-primitive type conversion).
     *
     * @return true if this expression does not depend on state shared with other functions.
     */
    public boolean isLocal() {
        return false;
    }

    /**
     * Is this a self modifying assignment?
     * @return true if self modifying, e.g. a++, or a*= 17
     */
    public boolean isSelfModifying() {
        return false;
    }

    /**
     * Returns widest operation type of this operation.
     *
     * @return the widest type for this operation
     */
    public Type getWidestOperationType() {
        return Type.OBJECT;
    }

    /**
     * Returns true if the type of this expression is narrower than its widest operation type (thus, it is
     * optimistically typed).
     * @return true if this expression is optimistically typed.
     */
    public final boolean isOptimistic() {
        return getType().narrowerThan(getWidestOperationType());
    }

    void optimisticTypeToString(final StringBuilder sb) {
        optimisticTypeToString(sb, isOptimistic());
    }

    void optimisticTypeToString(final StringBuilder sb, final boolean optimistic) {
        sb.append('{');
        final Type type = getType();
        final String desc = type == Type.UNDEFINED ? "U" : type.getDescriptor();

        sb.append(desc.charAt(desc.length() - 1) == ';' ? "O" : desc);
        if (isOptimistic() && optimistic) {
            sb.append(OPT_IDENTIFIER);
            final int pp = ((Optimistic)this).getProgramPoint();
            if (UnwarrantedOptimismException.isValid(pp)) {
                sb.append('_').append(pp);
            }
        }
        sb.append('}');
    }

    /**
     * Returns true if the runtime value of this expression is always false when converted to boolean as per ECMAScript
     * ToBoolean conversion. Used in control flow calculations.
     * @return true if this expression's runtime value converted to boolean is always false.
     */
    public boolean isAlwaysFalse() {
        return false;
    }

    /**
     * Returns true if the runtime value of this expression is always true when converted to boolean as per ECMAScript
     * ToBoolean conversion. Used in control flow calculations.
     * @return true if this expression's runtime value converted to boolean is always true.
     */
    public boolean isAlwaysTrue() {
        return false;
    }

    /**
     * Returns true if the expression is not null and {@link #isAlwaysFalse()}.
     * @param test a test expression used as a predicate of a branch or a loop.
     * @return true if the expression is not null and {@link #isAlwaysFalse()}.
     */
    public static boolean isAlwaysFalse(final Expression test) {
        return test != null && test.isAlwaysFalse();
    }


    /**
     * Returns true if the expression is null or {@link #isAlwaysTrue()}. Null is considered to be always true as a
     * for loop with no test is equivalent to a for loop with always-true test.
     * @param test a test expression used as a predicate of a branch or a loop.
     * @return true if the expression is null or {@link #isAlwaysFalse()}.
     */
    public static boolean isAlwaysTrue(final Expression test) {
        return test == null || test.isAlwaysTrue();
    }
}
