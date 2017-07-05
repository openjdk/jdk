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

import java.lang.invoke.MethodType;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.Optimistic;
import jdk.nashorn.internal.objects.ArrayBufferView;
import jdk.nashorn.internal.objects.NativeArray;
import jdk.nashorn.internal.runtime.FindProperty;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Functionality for using a runtime scope to look up value types.
 * Used during recompilation.
 */
final class TypeEvaluator {
    /**
     * Type signature for invocation of functions without parameters: we must pass (callee, this) of type
     * (ScriptFunction, Object) respectively. We also use Object as the return type (we must pass something,
     * but it'll be ignored; it can't be void, though).
     */
    private static final MethodType EMPTY_INVOCATION_TYPE = MethodType.methodType(Object.class, ScriptFunction.class, Object.class);

    private final Compiler compiler;
    private final ScriptObject runtimeScope;

    TypeEvaluator(final Compiler compiler, final ScriptObject runtimeScope) {
        this.compiler = compiler;
        this.runtimeScope = runtimeScope;
    }

    /**
     * Returns true if the expression can be safely evaluated, and its value is an object known to always use
     * String as the type of its property names retrieved through
     * {@link ScriptRuntime#toPropertyIterator(Object)}. It is used to avoid optimistic assumptions about its
     * property name types.
     * @param expr the expression to test
     * @return true if the expression can be safely evaluated, and its value is an object known to always use
     * String as the type of its property iterators.
     */
    boolean hasStringPropertyIterator(final Expression expr) {
        return evaluateSafely(expr) instanceof ScriptObject;
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
        final Class<?> propertyClass = property.getType();
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
        final Object value = property.needsDeclaration() ? ScriptRuntime.UNDEFINED : property.getObjectValue(owner, owner);
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
        } else if (expr instanceof AccessNode) {
            final AccessNode accessNode = (AccessNode)expr;
            final Object base = evaluateSafely(accessNode.getBase());
            if (!(base instanceof ScriptObject)) {
                return null;
            }
            return getPropertyType((ScriptObject)base, accessNode.getProperty());
        } else if (expr instanceof IndexNode) {
            final IndexNode indexNode = (IndexNode)expr;
            final Object    base = evaluateSafely(indexNode.getBase());
            if(base instanceof NativeArray || base instanceof ArrayBufferView) {
                // NOTE: optimistic array getters throw UnwarrantedOptimismException based on the type of their
                // underlying array storage, not based on values of individual elements. Thus, a LongArrayData will
                // throw UOE for every optimistic int linkage attempt, even if the long value being returned in the
                // first invocation would be representable as int. That way, we can presume that the array's optimistic
                // type is the most optimistic type for which an element getter has a chance of executing successfully.
                return ((ScriptObject)base).getArray().getOptimisticType();
            }
        } else if (expr instanceof CallNode) {
            // Currently, we'll only try to guess the return type of immediately invoked function expressions with no
            // parameters, that is (function() { ... })(). We could do better, but these are all heuristics and we can
            // gradually introduce them as needed. An easy one would be to do the same for .call(this) idiom.
            final CallNode callExpr = (CallNode)expr;
            final Expression fnExpr = callExpr.getFunction();
            // Skip evaluation if running with eager compilation as we may violate constraints in RecompilableScriptFunctionData
            if (fnExpr instanceof FunctionNode && compiler.getContext().getEnv()._lazy_compilation) {
                final FunctionNode fn = (FunctionNode)fnExpr;
                if (callExpr.getArgs().isEmpty()) {
                    final RecompilableScriptFunctionData data = compiler.getScriptFunctionData(fn.getId());
                    if (data != null) {
                        final Type returnType = Type.typeFor(data.getReturnType(EMPTY_INVOCATION_TYPE, runtimeScope));
                        if (returnType == Type.BOOLEAN) {
                            // We don't have optimistic booleans. In fact, optimistic call sites getting back boolean
                            // currently deoptimize all the way to Object.
                            return Type.OBJECT;
                        }
                        assert returnType == Type.INT || returnType == Type.NUMBER || returnType == Type.OBJECT;
                        return returnType;
                    }
                }
            }
        }

        return null;
    }
}
