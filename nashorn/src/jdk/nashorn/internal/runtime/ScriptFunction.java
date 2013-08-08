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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.codegen.CompilerConstants.virtualCallNoLookup;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.lookup.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.lookup.MethodHandleFactory;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.linker.NashornGuards;

/**
 * Runtime representation of a JavaScript function.
 */
public abstract class ScriptFunction extends ScriptObject {

    /** Method handle for prototype getter for this ScriptFunction */
    public static final MethodHandle G$PROTOTYPE = findOwnMH("G$prototype", Object.class, Object.class);

    /** Method handle for prototype setter for this ScriptFunction */
    public static final MethodHandle S$PROTOTYPE = findOwnMH("S$prototype", void.class, Object.class, Object.class);

    /** Method handle for length getter for this ScriptFunction */
    public static final MethodHandle G$LENGTH = findOwnMH("G$length", int.class, Object.class);

    /** Method handle for name getter for this ScriptFunction */
    public static final MethodHandle G$NAME = findOwnMH("G$name", Object.class, Object.class);

    /** Method handle for allocate function for this ScriptFunction */
    static final MethodHandle ALLOCATE = findOwnMH("allocate", Object.class);

    private static final MethodHandle WRAPFILTER = findOwnMH("wrapFilter", Object.class, Object.class);

    /** method handle to scope getter for this ScriptFunction */
    public static final Call GET_SCOPE = virtualCallNoLookup(ScriptFunction.class, "getScope", ScriptObject.class);

    private static final MethodHandle IS_FUNCTION_MH  = findOwnMH("isFunctionMH", boolean.class, Object.class, ScriptFunctionData.class);

    private static final MethodHandle IS_NONSTRICT_FUNCTION = findOwnMH("isNonStrictFunction", boolean.class, Object.class, Object.class, ScriptFunctionData.class);

    private static final MethodHandle ADD_ZEROTH_ELEMENT = findOwnMH("addZerothElement", Object[].class, Object[].class, Object.class);

    /** The parent scope. */
    private final ScriptObject scope;

    private final ScriptFunctionData data;

    /**
     * Constructor
     *
     * @param name          function name
     * @param methodHandle  method handle to function (if specializations are present, assumed to be most generic)
     * @param map           property map
     * @param scope         scope
     * @param specs         specialized version of this function - other method handles
     * @param strict        is this a strict mode function?
     * @param builtin       is this a built in function?
     * @param isConstructor is this a constructor?
     */
    protected ScriptFunction(
            final String name,
            final MethodHandle methodHandle,
            final PropertyMap map,
            final ScriptObject scope,
            final MethodHandle[] specs,
            final boolean strict,
            final boolean builtin,
            final boolean isConstructor) {

        this(new FinalScriptFunctionData(name, methodHandle, specs, strict, builtin, isConstructor), map, scope);
    }

    /**
     * Constructor
     *
     * @param data          static function data
     * @param map           property map
     * @param scope         scope
     */
    protected ScriptFunction(
            final ScriptFunctionData data,
            final PropertyMap map,
            final ScriptObject scope) {

        super(map);

        if (Context.DEBUG) {
            constructorCount++;
        }

        this.data  = data;
        this.scope = scope;
    }

    @Override
    public String getClassName() {
        return "Function";
    }

    /**
     * ECMA 15.3.5.3 [[HasInstance]] (V)
     * Step 3 if "prototype" value is not an Object, throw TypeError
     */
    @Override
    public boolean isInstance(final ScriptObject instance) {
        final Object basePrototype = getTargetFunction().getPrototype();
        if (!(basePrototype instanceof ScriptObject)) {
            throw typeError("prototype.not.an.object", ScriptRuntime.safeToString(getTargetFunction()), ScriptRuntime.safeToString(basePrototype));
        }

        for (ScriptObject proto = instance.getProto(); proto != null; proto = proto.getProto()) {
            if (proto == basePrototype) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the target function for this function. If the function was not created using
     * {@link #makeBoundFunction(Object, Object[])}, its target function is itself. If it is bound, its target function
     * is the target function of the function it was made from (therefore, the target function is always the final,
     * unbound recipient of the calls).
     * @return the target function for this function.
     */
    protected ScriptFunction getTargetFunction() {
        return this;
    }

    boolean isBoundFunction() {
        return getTargetFunction() != this;
    }

    /**
     * Set the arity of this ScriptFunction
     * @param arity arity
     */
    public final void setArity(final int arity) {
        data.setArity(arity);
    }

    /**
     * Is this a ECMAScript 'use strict' function?
     * @return true if function is in strict mode
     */
    public boolean isStrict() {
        return data.isStrict();
    }

    /**
     * Returns true if this is a non-strict, non-built-in function that requires non-primitive this argument
     * according to ECMA 10.4.3.
     * @return true if this argument must be an object
     */
    public boolean needsWrappedThis() {
        return data.needsWrappedThis();
    }

    /**
     * Execute this script function.
     * @param self  Target object.
     * @param arguments  Call arguments.
     * @return ScriptFunction result.
     * @throws Throwable if there is an exception/error with the invocation or thrown from it
     */
    Object invoke(final Object self, final Object... arguments) throws Throwable {
        if (Context.DEBUG) {
            invokes++;
        }
        return data.invoke(this, self, arguments);
    }

    /**
     * Execute this script function as a constructor.
     * @param arguments  Call arguments.
     * @return Newly constructed result.
     * @throws Throwable if there is an exception/error with the invocation or thrown from it
     */
    Object construct(final Object... arguments) throws Throwable {
        return data.construct(this, arguments);
    }

    /**
     * Allocate function. Called from generated {@link ScriptObject} code
     * for allocation as a factory method
     *
     * @return a new instance of the {@link ScriptObject} whose allocator this is
     */
    @SuppressWarnings("unused")
    private Object allocate() {
        if (Context.DEBUG) {
            allocations++;
        }
        assert !isBoundFunction(); // allocate never invoked on bound functions

        final ScriptObject object = data.allocate();

        if (object != null) {
            Object prototype = getPrototype();
            if (prototype instanceof ScriptObject) {
                object.setProto((ScriptObject)prototype);
            }

            if (object.getProto() == null) {
                object.setProto(getObjectPrototype());
            }
        }

        return object;
    }

    /**
     * Return Object.prototype - used by "allocate"
     * @return Object.prototype
     */
    protected abstract ScriptObject getObjectPrototype();

    /**
     * Creates a version of this function bound to a specific "self" and other arguments, as per
     * {@code Function.prototype.bind} functionality in ECMAScript 5.1 section 15.3.4.5.
     * @param self the self to bind to this function. Can be null (in which case, null is bound as this).
     * @param args additional arguments to bind to this function. Can be null or empty to not bind additional arguments.
     * @return a function with the specified self and parameters bound.
     */
    protected ScriptFunction makeBoundFunction(final Object self, final Object[] args) {
        return makeBoundFunction(data.makeBoundFunctionData(this, self, args));
    }

    /**
     * Create a version of this function as in {@link ScriptFunction#makeBoundFunction(Object, Object[])},
     * but using a {@link ScriptFunctionData} for the bound data.
     *
     * @param boundData ScriptFuntionData for the bound function
     * @return a function with the bindings performed according to the given data
     */
    protected abstract ScriptFunction makeBoundFunction(ScriptFunctionData boundData);

    @Override
    public final String safeToString() {
        return toSource();
    }

    @Override
    public String toString() {
        return data.toString();
    }

    /**
     * Get this function as a String containing its source code. If no source code
     * exists in this ScriptFunction, its contents will be displayed as {@code [native code]}
     * @return string representation of this function's source
     */
    public final String toSource() {
        return data.toSource();
    }

    /**
     * Get the prototype object for this function
     * @return prototype
     */
    public abstract Object getPrototype();

    /**
     * Set the prototype object for this function
     * @param prototype new prototype object
     */
    public abstract void setPrototype(Object prototype);

    /**
     * Return the most appropriate invoke handle if there are specializations
     * @param type most specific method type to look for invocation with
     * @param args args for trampoline invocation
     * @return invoke method handle
     */
    private MethodHandle getBestInvoker(final MethodType type, final Object[] args) {
        return data.getBestInvoker(type, args);
    }

    /**
     * Return the most appropriate invoke handle if there are specializations
     * @param type most specific method type to look for invocation with
     * @return invoke method handle
     */
    public MethodHandle getBestInvoker(final MethodType type) {
        return getBestInvoker(type, null);
    }

    /**
     * Return the invoke handle bound to a given ScriptObject self reference.
     * If callee parameter is required result is rebound to this.
     *
     * @param self self reference
     * @return bound invoke handle
     */
    public final MethodHandle getBoundInvokeHandle(final ScriptObject self) {
        return MH.bindTo(bindToCalleeIfNeeded(data.getGenericInvoker()), self);
    }

    /**
     * Bind the method handle to this {@code ScriptFunction} instance if it needs a callee parameter. If this function's
     * method handles don't have a callee parameter, the handle is returned unchanged.
     * @param methodHandle the method handle to potentially bind to this function instance.
     * @return the potentially bound method handle
     */
    private MethodHandle bindToCalleeIfNeeded(final MethodHandle methodHandle) {
        return ScriptFunctionData.needsCallee(methodHandle) ? MH.bindTo(methodHandle, this) : methodHandle;

    }

    /**
     * Get the name for this function
     * @return the name
     */
    public final String getName() {
        return data.getName();
    }


    /**
     * Get the scope for this function
     * @return the scope
     */
    public final ScriptObject getScope() {
        return scope;
    }

    /**
     * Prototype getter for this ScriptFunction - follows the naming convention
     * used by Nasgen and the code generator
     *
     * @param self  self reference
     * @return self's prototype
     */
    public static Object G$prototype(final Object self) {
        return (self instanceof ScriptFunction) ?
            ((ScriptFunction)self).getPrototype() :
            UNDEFINED;
    }

    /**
     * Prototype setter for this ScriptFunction - follows the naming convention
     * used by Nasgen and the code generator
     *
     * @param self  self reference
     * @param prototype prototype to set
     */
    public static void S$prototype(final Object self, final Object prototype) {
        if (self instanceof ScriptFunction) {
            ((ScriptFunction)self).setPrototype(prototype);
        }
    }

    /**
     * Length getter - ECMA 15.3.3.2: Function.length
     * @param self self reference
     * @return length
     */
    public static int G$length(final Object self) {
        if (self instanceof ScriptFunction) {
            return ((ScriptFunction)self).data.getArity();
        }

        return 0;
    }

    /**
     * Name getter - ECMA Function.name
     * @param self self refence
     * @return the name, or undefined if none
     */
    public static Object G$name(final Object self) {
        if (self instanceof ScriptFunction) {
            return ((ScriptFunction)self).getName();
        }

        return UNDEFINED;
    }

    /**
     * Get the prototype for this ScriptFunction
     * @param constructor constructor
     * @return prototype, or null if given constructor is not a ScriptFunction
     */
    public static ScriptObject getPrototype(final Object constructor) {
        if (constructor instanceof ScriptFunction) {
            final Object proto = ((ScriptFunction)constructor).getPrototype();
            if (proto instanceof ScriptObject) {
                return (ScriptObject)proto;
            }
        }

        return null;
    }

    // These counters are updated only in debug mode.
    private static int constructorCount;
    private static int invokes;
    private static int allocations;

    /**
     * @return the constructorCount
     */
    public static int getConstructorCount() {
        return constructorCount;
    }

    /**
     * @return the invokes
     */
    public static int getInvokes() {
        return invokes;
    }

    /**
     * @return the allocations
     */
    public static int getAllocations() {
        return allocations;
    }

    @Override
    protected GuardedInvocation findNewMethod(final CallSiteDescriptor desc) {
        final MethodType type = desc.getMethodType();
        return new GuardedInvocation(pairArguments(data.getBestConstructor(type.changeParameterType(0, ScriptFunction.class), null), type), null, getFunctionGuard(this));
    }

    @SuppressWarnings("unused")
    private static Object wrapFilter(final Object obj) {
        if (obj instanceof ScriptObject || !ScriptFunctionData.isPrimitiveThis(obj)) {
            return obj;
        }
        return ((GlobalObject)Context.getGlobalTrusted()).wrapAsObject(obj);
    }

    /**
     * dyn:call call site signature: (callee, thiz, [args...])
     * generated method signature:   (callee, thiz, [args...])
     *
     * cases:
     * (a) method has callee parameter
     *   (1) for local/scope calls, we just bind thiz and drop the second argument.
     *   (2) for normal this-calls, we have to swap thiz and callee to get matching signatures.
     * (b) method doesn't have callee parameter (builtin functions)
     *   (3) for local/scope calls, bind thiz and drop both callee and thiz.
     *   (4) for normal this-calls, drop callee.
     */
    @Override
    protected GuardedInvocation findCallMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final MethodType type = desc.getMethodType();

        if (request.isCallSiteUnstable()) {
            // (this, callee, args...) => (this, callee, args[])
            final MethodHandle collector = MH.asCollector(ScriptRuntime.APPLY.methodHandle(), Object[].class,
                    type.parameterCount() - 2);

            // If call site is statically typed to take a ScriptFunction, we don't need a guard, otherwise we need a
            // generic "is this a ScriptFunction?" guard.
            return new GuardedInvocation(collector, ScriptFunction.class.isAssignableFrom(desc.getMethodType().parameterType(0))
                    ? null : NashornGuards.getScriptFunctionGuard());
        }

        MethodHandle boundHandle;
        MethodHandle guard = null;

        final boolean scopeCall = NashornCallSiteDescriptor.isScope(desc);

        if (data.needsCallee()) {
            final MethodHandle callHandle = getBestInvoker(type, request.getArguments());
            if (scopeCall) {
                // Make a handle that drops the passed "this" argument and substitutes either Global or Undefined
                // (callee, this, args...) => (callee, args...)
                boundHandle = MH.insertArguments(callHandle, 1, needsWrappedThis() ? Context.getGlobalTrusted() : ScriptRuntime.UNDEFINED);
                // (callee, args...) => (callee, [this], args...)
                boundHandle = MH.dropArguments(boundHandle, 1, Object.class);

            } else {
                // It's already (callee, this, args...), just what we need
                boundHandle = callHandle;
            }
        } else {
            final MethodHandle callHandle = getBestInvoker(type.dropParameterTypes(0, 1), request.getArguments());
            if (scopeCall) {
                // Make a handle that drops the passed "this" argument and substitutes either Global or Undefined
                // (this, args...) => (args...)
                boundHandle = MH.bindTo(callHandle, needsWrappedThis() ? Context.getGlobalTrusted() : ScriptRuntime.UNDEFINED);
                // (args...) => ([callee], [this], args...)
                boundHandle = MH.dropArguments(boundHandle, 0, Object.class, Object.class);
            } else {
                // (this, args...) => ([callee], this, args...)
                boundHandle = MH.dropArguments(callHandle, 0, Object.class);
            }
        }

        // For non-strict functions, check whether this-object is primitive type.
        // If so add a to-object-wrapper argument filter.
        // Else install a guard that will trigger a relink when the argument becomes primitive.
        if (!scopeCall && needsWrappedThis()) {
            if (ScriptFunctionData.isPrimitiveThis(request.getArguments()[1])) {
                boundHandle = MH.filterArguments(boundHandle, 1, WRAPFILTER);
            } else {
                guard = getNonStrictFunctionGuard(this);
            }
        }

        boundHandle = pairArguments(boundHandle, type);

        return new GuardedInvocation(boundHandle, guard == null ? getFunctionGuard(this) : guard);
   }

    /**
     * Used for noSuchMethod/noSuchProperty and JSAdapter hooks.
     *
     * These don't want a callee parameter, so bind that. Name binding is optional.
     */
    MethodHandle getCallMethodHandle(final MethodType type, final String bindName) {
        return pairArguments(bindToNameIfNeeded(bindToCalleeIfNeeded(getBestInvoker(type, null)), bindName), type);
    }

    private static MethodHandle bindToNameIfNeeded(final MethodHandle methodHandle, final String bindName) {
        if (bindName == null) {
            return methodHandle;
        } else {
            // if it is vararg method, we need to extend argument array with
            // a new zeroth element that is set to bindName value.
            final MethodType methodType = methodHandle.type();
            final int parameterCount = methodType.parameterCount();
            final boolean isVarArg = parameterCount > 0 && methodType.parameterType(parameterCount - 1).isArray();

            if (isVarArg) {
                return MH.filterArguments(methodHandle, 1, MH.insertArguments(ADD_ZEROTH_ELEMENT, 1, bindName));
            } else {
                return MH.insertArguments(methodHandle, 1, bindName);
            }
        }
    }

    /**
     * Get the guard that checks if a {@link ScriptFunction} is equal to
     * a known ScriptFunction, using reference comparison
     *
     * @param function The ScriptFunction to check against. This will be bound to the guard method handle
     *
     * @return method handle for guard
     */
    private static MethodHandle getFunctionGuard(final ScriptFunction function) {
        assert function.data != null;
        return MH.insertArguments(IS_FUNCTION_MH, 1, function.data);
    }

    /**
     * Get a guard that checks if a {@link ScriptFunction} is equal to
     * a known ScriptFunction using reference comparison, and whether the type of
     * the second argument (this-object) is not a JavaScript primitive type.
     *
     * @param function The ScriptFunction to check against. This will be bound to the guard method handle
     *
     * @return method handle for guard
     */
    private static MethodHandle getNonStrictFunctionGuard(final ScriptFunction function) {
        assert function.data != null;
        return MH.insertArguments(IS_NONSTRICT_FUNCTION, 2, function.data);
    }

    @SuppressWarnings("unused")
    private static boolean isFunctionMH(final Object self, final ScriptFunctionData data) {
        return self instanceof ScriptFunction && ((ScriptFunction)self).data == data;
    }

    @SuppressWarnings("unused")
    private static boolean isNonStrictFunction(final Object self, final Object arg, final ScriptFunctionData data) {
        return self instanceof ScriptFunction && ((ScriptFunction)self).data == data && arg instanceof ScriptObject;
    }

    @SuppressWarnings("unused")
    private static Object[] addZerothElement(final Object[] args, final Object value) {
        // extends input array with by adding new zeroth element
        final Object[] src = (args == null)? ScriptRuntime.EMPTY_ARRAY : args;
        final Object[] result = new Object[src.length + 1];
        System.arraycopy(src, 0, result, 1, src.length);
        result[0] = value;
        return result;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        final Class<?>   own = ScriptFunction.class;
        final MethodType mt  = MH.type(rtype, types);
        try {
            return MH.findStatic(MethodHandles.lookup(), own, name, mt);
        } catch (final MethodHandleFactory.LookupException e) {
            return MH.findVirtual(MethodHandles.lookup(), own, name, mt);
        }
    }
}

