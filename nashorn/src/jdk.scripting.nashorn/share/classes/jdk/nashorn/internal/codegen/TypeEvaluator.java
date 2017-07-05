/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.runtime.Property.NOT_CONFIGURABLE;
import static jdk.nashorn.internal.runtime.Property.NOT_ENUMERABLE;
import static jdk.nashorn.internal.runtime.Property.NOT_WRITABLE;

import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.Optimistic;
import jdk.nashorn.internal.objects.NativeArray;
import jdk.nashorn.internal.runtime.FindProperty;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Functionality for using a runtime scope to look up value types.
 * Used during recompilation.
 */
final class TypeEvaluator {
    private final Compiler compiler;
    private final ScriptObject runtimeScope;

    TypeEvaluator(final Compiler compiler, final ScriptObject runtimeScope) {
        this.compiler = compiler;
        this.runtimeScope = runtimeScope;
    }

    Type getOptimisticType(final Optimistic node) {
        assert compiler.useOptimisticTypes();

        final int  programPoint = node.getProgramPoint();
        final Type validType    = compiler.getInvalidatedProgramPointType(programPoint);

        if (validType != null) {
            return validType;
        }

        final Type mostOptimisticType = node.getMostOptimisticType();
        final Type evaluatedType      = getEvaluatedType(node);

        if (evaluatedType != null) {
            if (evaluatedType.widerThan(mostOptimisticType)) {
                final Type newValidType = evaluatedType.isObject() || evaluatedType.isBoolean() ? Type.OBJECT : evaluatedType;
                // Update invalidatedProgramPoints so we don't re-evaluate the expression next time. This is a heuristic
                // as we're doing a tradeoff. Re-evaluating expressions on each recompile takes time, but it might
                // notice a widening in the type of the expression and thus prevent an unnecessary deoptimization later.
                // We'll presume though that the types of expressions are mostly stable, so if we evaluated it in one
                // compilation, we'll keep to that and risk a low-probability deoptimization if its type gets widened
                // in the future.
                compiler.addInvalidatedProgramPoint(node.getProgramPoint(), newValidType);
            }
            return evaluatedType;
        }
        return mostOptimisticType;
    }

    private static Type getPropertyType(final ScriptObject sobj, final String name) {
        final FindProperty find = sobj.findProperty(name, true);
        if (find == null) {
            return null;
        }

        final Property property      = find.getProperty();
        final Class<?> propertyClass = property.getCurrentType();
        if (propertyClass == null) {
            // propertyClass == null means its value is Undefined. It is probably not initialized yet, so we won't make
            // a type assumption yet.
            return null;
        } else if (propertyClass.isPrimitive()) {
            return Type.typeFor(propertyClass);
        }

        final ScriptObject owner = find.getOwner();
        if (property.hasGetterFunction(owner)) {
            // Can have side effects, so we can't safely evaluate it; since !propertyClass.isPrimitive(), it's Object.
            return Type.OBJECT;
        }

        // Safely evaluate the property, and return the narrowest type for the actual value (e.g. Type.INT for a boxed
        // integer).
        final Object value = property.getObjectValue(owner, owner);
        if (value == ScriptRuntime.UNDEFINED) {
            return null;
        }
        return Type.typeFor(JSType.unboxedFieldType(value));
    }

    /**
     * Declares a symbol name as belonging to a non-scoped local variable during an on-demand compilation of a single
     * function. This method will add an explicit Undefined binding for the local into the runtime scope if it's
     * otherwise implicitly undefined so that when an expression is evaluated for the name, it won't accidentally find
     * an unrelated value higher up the scope chain. It is only required to call this method when doing an optimistic
     * on-demand compilation.
     * @param symbolName the name of the symbol that is to be declared as being a non-scoped local variable.
     */
    void declareLocalSymbol(final String symbolName) {
        assert
            compiler.useOptimisticTypes() &&
            compiler.isOnDemandCompilation() &&
            runtimeScope != null :
                "useOptimistic=" +
                    compiler.useOptimisticTypes() +
                    " isOnDemand=" +
                    compiler.isOnDemandCompilation() +
                    " scope="+runtimeScope;

        if (runtimeScope.findProperty(symbolName, false) == null) {
            runtimeScope.addOwnProperty(symbolName, NOT_WRITABLE | NOT_ENUMERABLE | NOT_CONFIGURABLE, ScriptRuntime.UNDEFINED);
        }
    }

    private Object evaluateSafely(final Expression expr) {
        if (expr instanceof IdentNode) {
            return runtimeScope == null ? null : evaluatePropertySafely(runtimeScope, ((IdentNode)expr).getName());
        }

        if (expr instanceof AccessNode) {
            final AccessNode accessNode = (AccessNode)expr;
            final Object     base       = evaluateSafely(accessNode.getBase());
            if (!(base instanceof ScriptObject)) {
                return null;
            }
            return evaluatePropertySafely((ScriptObject)base, accessNode.getProperty());
        }

        return null;
    }

    private static Object evaluatePropertySafely(final ScriptObject sobj, final String name) {
        final FindProperty find = sobj.findProperty(name, true);
        if (find == null) {
            return null;
        }
        final Property     property = find.getProperty();
        final ScriptObject owner    = find.getOwner();
        if (property.hasGetterFunction(owner)) {
            // Possible side effects; can't evaluate safely
            return null;
        }
        return property.getObjectValue(owner, owner);
    }


    private Type getEvaluatedType(final Optimistic expr) {
        if (expr instanceof IdentNode) {
            if (runtimeScope == null) {
                return null;
            }
            return getPropertyType(runtimeScope, ((IdentNode)expr).getName());
        }

        if (expr instanceof AccessNode) {
            final AccessNode accessNode = (AccessNode)expr;
            final Object base = evaluateSafely(accessNode.getBase());
            if (!(base instanceof ScriptObject)) {
                return null;
            }
            return getPropertyType((ScriptObject)base, accessNode.getProperty());
        }

        if (expr instanceof IndexNode) {
            final IndexNode indexNode = (IndexNode)expr;
            final Object    base = evaluateSafely(indexNode.getBase());
            if(!(base instanceof NativeArray)) {
                // We only know how to deal with NativeArray. TODO: maybe manage buffers too
                return null;
            }
            // NOTE: optimistic array getters throw UnwarrantedOptimismException based on the type of their underlying
            // array storage, not based on values of individual elements. Thus, a LongArrayData will throw UOE for every
            // optimistic int linkage attempt, even if the long value being returned in the first invocation would be
            // representable as int. That way, we can presume that the array's optimistic type is the most optimistic
            // type for which an element getter has a chance of executing successfully.
            return ((NativeArray)base).getArray().getOptimisticType();
        }

        return null;
    }
}
