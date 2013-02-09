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
import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.objects.annotations.SpecializedConstructor;
import jdk.nashorn.internal.objects.annotations.SpecializedFunction;
import jdk.nashorn.internal.runtime.linker.MethodHandleFactory;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;
import jdk.nashorn.internal.runtime.linker.NashornGuards;
import jdk.nashorn.internal.runtime.options.Options;
import org.dynalang.dynalink.CallSiteDescriptor;
import org.dynalang.dynalink.linker.GuardedInvocation;
import org.dynalang.dynalink.linker.LinkRequest;

/**
 * Runtime representation of a JavaScript function.
 */
public abstract class ScriptFunction extends ScriptObject {

    /** Method handle for prototype getter for this ScriptFunction */
    public static final MethodHandle G$PROTOTYPE  = findOwnMH("G$prototype",  Object.class, Object.class);

    /** Method handle for prototype setter for this ScriptFunction */
    public static final MethodHandle S$PROTOTYPE  = findOwnMH("S$prototype",  void.class, Object.class, Object.class);

    /** Method handle for length getter for this ScriptFunction */
    public static final MethodHandle G$LENGTH     = findOwnMH("G$length",     int.class, Object.class);

    /** Method handle for name getter for this ScriptFunction */
    public static final MethodHandle G$NAME       = findOwnMH("G$name",       Object.class, Object.class);

    /** Method handle for allocate function for this ScriptFunction */
    public static final MethodHandle ALLOCATE     = findOwnMH("allocate", Object.class);

    private static final MethodHandle NEWFILTER = findOwnMH("newFilter", Object.class, Object.class, Object.class);

    private static final MethodHandle WRAPFILTER = findOwnMH("wrapFilter", Object.class, Object.class);

    /** method handle to scope getter for this ScriptFunction */
    public static final Call GET_SCOPE = virtualCallNoLookup(ScriptFunction.class, "getScope", ScriptObject.class);

    /** Should specialized function and specialized constructors for the builtin be used if available? */
    private static final boolean DISABLE_SPECIALIZATION = Options.getBooleanProperty("nashorn.scriptfunction.specialization.disable");

    private final ScriptFunctionData data;

    /** Reference to constructor prototype. */
    protected Object prototype;

    /** The parent scope. */
    private final ScriptObject scope;

    /**
     * Constructor
     *
     * @param name         function name
     * @param methodHandle method handle to function (if specializations are present, assumed to be most generic)
     * @param map          property map
     * @param scope        scope
     * @param specs        specialized version of this function - other method handles
     *
     */
    protected ScriptFunction(
            final String name,
            final MethodHandle methodHandle,
            final PropertyMap map,
            final ScriptObject scope,
            final MethodHandle[] specs,
            final boolean strict,
            final boolean builtin) {

        this (new ScriptFunctionData(name, methodHandle, specs, strict, builtin), map, scope);
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

        this.data = data;
        this.scope  = scope;
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
        if (!(prototype instanceof ScriptObject)) {
            typeError("prototype.not.an.object", ScriptRuntime.safeToString(this), ScriptRuntime.safeToString(prototype));
        }

        for (ScriptObject proto = instance.getProto(); proto != null; proto = proto.getProto()) {
            if (proto == prototype) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the arity of this ScriptFunction
     * @return arity
     */
    public final int getArity() {
        return data.getArity();
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
     * Is this a ECMAScript built-in function (like parseInt, Array.isArray) ?
     * @return true if built-in
     */
    public boolean isBuiltin() {
        return data.isBuiltin();
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
    public Object invoke(final Object self, final Object... arguments) throws Throwable {
        if (Context.DEBUG) {
            invokes++;
        }

        final MethodHandle invoker = data.getGenericInvoker();
        final Object selfObj = convertThisObject(self);
        final Object[] args = arguments == null ? ScriptRuntime.EMPTY_ARRAY : arguments;

        if (data.isVarArg()) {
            if (data.needsCallee()) {
                return invoker.invokeExact(this, selfObj, args);
            }
            return invoker.invokeExact(selfObj, args);
        }

        final int paramCount = invoker.type().parameterCount();
        if (data.needsCallee()) {
            switch (paramCount) {
            case 2:
                return invoker.invokeExact(this, selfObj);
            case 3:
                return invoker.invokeExact(this, selfObj, getArg(args, 0));
            case 4:
                return invoker.invokeExact(this, selfObj, getArg(args, 0), getArg(args, 1));
            case 5:
                return invoker.invokeExact(this, selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2));
            default:
                return invoker.invokeWithArguments(withArguments(selfObj, paramCount, args));
            }
        }

        switch (paramCount) {
        case 1:
            return invoker.invokeExact(selfObj);
        case 2:
            return invoker.invokeExact(selfObj, getArg(args, 0));
        case 3:
            return invoker.invokeExact(selfObj, getArg(args, 0), getArg(args, 1));
        case 4:
            return invoker.invokeExact(selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2));
        default:
            return invoker.invokeWithArguments(withArguments(selfObj, paramCount, args));
        }
    }

    private static Object getArg(final Object[] args, final int i) {
        return i < args.length ? args[i] : UNDEFINED;
    }

    /**
     * Construct new object using this constructor.
     * @param self  Target object.
     * @param args  Call arguments.
     * @return ScriptFunction result.
     * @throws Throwable if there is an exception/error with the constructor invocation or thrown from it
     */
    public Object construct(final Object self, final Object... args) throws Throwable {
        if (data.getConstructor() == null) {
            typeError("not.a.constructor", ScriptRuntime.safeToString(this));
        }

        final MethodHandle constructor = data.getGenericConstructor();
        if (data.isVarArg()) {
            if (data.needsCallee()) {
                return constructor.invokeExact(this, self, args);
            }
            return constructor.invokeExact(self, args);
        }

        final int paramCount = constructor.type().parameterCount();
        if (data.needsCallee()) {
            switch (paramCount) {
            case 2:
                return constructor.invokeExact(this, self);
            case 3:
                return constructor.invokeExact(this, self, getArg(args, 0));
            case 4:
                return constructor.invokeExact(this, self, getArg(args, 0), getArg(args, 1));
            case 5:
                return constructor.invokeExact(this, self, getArg(args, 0), getArg(args, 1), getArg(args, 2));
            default:
                return constructor.invokeWithArguments(withArguments(self, args));
            }
        }

        switch(paramCount) {
        case 1:
            return constructor.invokeExact(self);
        case 2:
            return constructor.invokeExact(self, getArg(args, 0));
        case 3:
            return constructor.invokeExact(self, getArg(args, 0), getArg(args, 1));
        case 4:
            return constructor.invokeExact(self, getArg(args, 0), getArg(args, 1), getArg(args, 2));
        default:
            return constructor.invokeWithArguments(withArguments(self, args));
        }
    }

    private Object[] withArguments(final Object self, final Object[] args) {
        return withArguments(self, args.length + (data.needsCallee() ? 2 : 1), args);
    }

    private Object[] withArguments(final Object self, final int argCount, final Object[] args) {
        final Object[] finalArgs = new Object[argCount];

        int nextArg = 0;
        if (data.needsCallee()) {
            finalArgs[nextArg++] = this;
        }
        finalArgs[nextArg++] = self;

        // Don't add more args that there is argCount in the handle (including self and callee).
        for (int i = 0; i < args.length && nextArg < argCount;) {
            finalArgs[nextArg++] = args[i++];
        }

        // If we have fewer args than argCount, pad with undefined.
        while (nextArg < argCount) {
            finalArgs[nextArg++] = UNDEFINED;
        }

        return finalArgs;
    }

    /**
     * Allocate function. Called from generated {@link ScriptObject} code
     * for allocation as a factory method
     *
     * @return a new instance of the {@link ScriptObject} whose allocator this is
     */
    public Object allocate() {
        if (Context.DEBUG) {
            allocations++;
        }

        if (getConstructHandle() == null) {
            typeError("not.a.constructor", ScriptRuntime.safeToString(this));
        }

        ScriptObject object = null;

        if (data.getAllocator() != null) {
            try {
                object = (ScriptObject)data.getAllocator().invokeExact(data.getAllocatorMap());
            } catch (final RuntimeException | Error e) {
                throw e;
            } catch (final Throwable t) {
                throw new RuntimeException(t);
            }
        }

        if (object != null) {
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
     * Creates a version of this function bound to a specific "self" and other argumentss
     * @param self the self to bind the function to
     * @param args other arguments (beside self) to bind the function to
     * @return the bound function
     */
    public abstract ScriptFunction makeBoundFunction(Object self, Object[] args);


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
    public final Object getPrototype() {
        return prototype;
    }

    /**
     * Set the prototype object for this function
     * @param prototype new prototype object
     * @return the prototype parameter
     */
    public final Object setPrototype(final Object prototype) {
        this.prototype = prototype;
        return prototype;
    }

    private static int weigh(final MethodType t) {
        int weight = Type.typeFor(t.returnType()).getWeight();
        for (final Class<?> paramType : t.parameterArray()) {
            final int pweight = Type.typeFor(paramType).getWeight();
            weight += pweight;
        }
        return weight;
    }

    private static boolean typeCompatible(final MethodType desc, final MethodType spec) {
        //spec must fit in desc
        final Class<?>[] dparray = desc.parameterArray();
        final Class<?>[] sparray = spec.parameterArray();

        if (dparray.length != sparray.length) {
            return false;
        }

        for (int i = 0; i < dparray.length; i++) {
            final Type dp = Type.typeFor(dparray[i]);
            final Type sp = Type.typeFor(sparray[i]);

            if (dp.isBoolean()) {
                return false; //don't specialize on booleans, we have the "true" vs int 1 ambiguity in resolution
            }

            //specialization arguments must be at least as wide as dp, if not wider
            if (Type.widest(dp, sp) != sp) {
                //e.g. specialization takes double and callsite says "object". reject.
                //but if specialization says double and callsite says "int" or "long" or "double", that's fine
                return false;
            }
        }

        return true; // anything goes for return type, take the convenient one and it will be upcasted thru dynalink magic.
    }

    private MethodHandle candidateWithLowestWeight(final MethodType descType, final MethodHandle initialCandidate, final MethodHandle[] specs) {
        if (DISABLE_SPECIALIZATION || specs == null) {
            return initialCandidate;
        }

        int          minimumWeight = Integer.MAX_VALUE;
        MethodHandle candidate     = initialCandidate;

        for (final MethodHandle spec : specs) {
            final MethodType specType = spec.type();

            if (!typeCompatible(descType, specType)) {
                continue;
            }

            //return type is ok. we want a wider or equal one for our callsite.
            final int specWeight = weigh(specType);
            if (specWeight < minimumWeight) {
                candidate = spec;
                minimumWeight = specWeight;
            }
        }

        if (DISABLE_SPECIALIZATION && candidate != initialCandidate) {
            Context.err("### Specializing builtin " + getName() + " -> " + candidate + "?");
        }

        return candidate;
    }

    /**
     * Return the most appropriate invoke handle if there are specializations
     * @param type most specific method type to look for invocation with
     * @return invoke method handle
     */
    public final MethodHandle getBestSpecializedInvokeHandle(final MethodType type) {
        return candidateWithLowestWeight(type, getInvokeHandle(), data.getInvokeSpecializations());
    }

    /**
     * Get the invoke handle - the most generic (and if no specializations are in place, only) invocation
     * method handle for this ScriptFunction
     * @see SpecializedFunction
     * @return invokeHandle
     */
    public final MethodHandle getInvokeHandle() {
        return data.getInvoker();
    }

    /**
     * Return the invoke handle bound to a given ScriptObject self reference.
     * If callee parameter is required result is rebound to this.
     *
     * @param self self reference
     * @return bound invoke handle
     */
    public final MethodHandle getBoundInvokeHandle(final ScriptObject self) {
        return MH.bindTo(bindToCalleeIfNeeded(getInvokeHandle()), self);
    }

    /**
     * Bind the method handle to this {@code ScriptFunction} instance if it needs a callee parameter. If this function's
     * method handles don't have a callee parameter, the handle is returned unchanged.
     * @param methodHandle the method handle to potentially bind to this function instance.
     * @return the potentially bound method handle
     */
    private MethodHandle bindToCalleeIfNeeded(final MethodHandle methodHandle) {
        return data.needsCallee() ? MH.bindTo(methodHandle, this) : methodHandle;
    }

    /**
     * Get the construct handle - the most generic (and if no specializations are in place, only) constructor
     * method handle for this ScriptFunction
     * @see SpecializedConstructor
     * @param type type for wanted constructor
     * @return construct handle
     */
    private final MethodHandle getConstructHandle(final MethodType type) {
        return candidateWithLowestWeight(type, getConstructHandle(), data.getConstructSpecializations());
    }

    /**
     * Get a method handle to the constructor for this function
     * @return constructor handle
     */
    public final MethodHandle getConstructHandle() {
        return data.getConstructor();
    }

    /**
     * Set a method handle to the constructor for this function.
     * @param constructHandle constructor handle. Can be null to prevent the function from being used as a constructor.
     */
    public final void setConstructHandle(final MethodHandle constructHandle) {
        data.setConstructor(constructHandle);
    }

    /**
     * Get the name for this function
     * @return the name
     */
    public final String getName() {
        return data.getName();
    }

    /**
     * Does this script function need to be compiled. This determined by
     * null checking invokeHandle
     *
     * @return true if this needs compilation
     */
    public final boolean needsCompilation() {
        return data.getInvoker() == null;
    }

    /**
     * Get token for this function
     * @return token
     */
    public final long getToken() {
        return data.getToken();
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
            return ((ScriptFunction)self).getArity();
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
        // Call site type is (callee, args...) - dyn:new doesn't specify a "this", it's our job to allocate it
        final MethodType type = desc.getMethodType();

        // Constructor arguments are either (callee, this, args...) or (this, args...), depending on whether it needs a
        // callee or not.
        MethodHandle constructor = getConstructHandle(type);

        if (constructor == null) {
            typeError("not.a.constructor", ScriptRuntime.safeToString(this));
            return null;
        }

        // If it was (callee, this, args...), permute it to (this, callee, args...). We're doing this because having
        // "this" in the first argument position is what allows the elegant folded composition of
        // (newFilter x constructor x allocator) further down below in the code.
        constructor = swapCalleeAndThis(constructor);

        final MethodType ctorType = constructor.type();
        // Drop constructor "this", so it's also captured as "allocation" parameter of newFilter after we fold the
        // constructor into newFilter.
        // (this, [callee, ]args...) => ([callee, ]args...)
        final Class<?>[] ctorArgs = ctorType.dropParameterTypes(0, 1).parameterArray();
        // Fold constructor into newFilter that replaces the return value from the constructor with the originally
        // allocated value when the originally allocated value is a primitive.
        // (result, this, [callee, ]args...) x (this, [callee, ]args...) => (this, [callee, ]args...)
        MethodHandle handle = MH.foldArguments(MH.dropArguments(NEWFILTER, 2, ctorArgs), constructor);

        // allocate() takes a ScriptFunction and returns a newly allocated ScriptObject...
        if (data.needsCallee()) {
            // ...we either fold it into the previous composition, if we need both the ScriptFunction callee object and
            // the newly allocated object in the arguments, so (this, callee, args...) x (callee) => (callee, args...),
            // or...
            handle = MH.foldArguments(handle, ALLOCATE);
        } else {
            // ...replace the ScriptFunction argument with the newly allocated object, if it doesn't need the callee
            // (this, args...) filter (callee) => (callee, args...)
            handle = MH.filterArguments(handle, 0, ALLOCATE);
        }

        final MethodHandle filterIn = MH.asType(pairArguments(handle, type), type);
        return new GuardedInvocation(filterIn, null, NashornGuards.getFunctionGuard(this));
    }

    /**
     * If this function's method handles need a callee parameter, swap the order of first two arguments for the passed
     * method handle. If this function's method handles don't need a callee parameter, returns the original method
     * handle unchanged.
     * @param mh a method handle with order of arguments {@code (callee, this, args...)}
     * @return a method handle with order of arguments {@code (this, callee, args...)}
     */
    private MethodHandle swapCalleeAndThis(final MethodHandle mh) {
        if (!data.needsCallee()) {
            return mh;
        }
        final MethodType type = mh.type();
        assert type.parameterType(0) == ScriptFunction.class;
        assert type.parameterType(1) == Object.class;
        final MethodType newType = type.changeParameterType(0, Object.class).changeParameterType(1, ScriptFunction.class);
        final int[] reorder = new int[type.parameterCount()];
        reorder[0] = 1;
        assert reorder[1] == 0;
        for (int i = 2; i < reorder.length; ++i) {
            reorder[i] = i;
        }
        return MethodHandles.permuteArguments(mh, newType, reorder);
    }

    @SuppressWarnings("unused")
    private static Object newFilter(final Object result, final Object allocation) {
        return (result instanceof ScriptObject || !JSType.isPrimitive(result))? result : allocation;
    }

    @SuppressWarnings("unused")
    private static Object wrapFilter(final Object obj) {
        if (obj instanceof ScriptObject || !isPrimitiveThis(obj)) {
            return obj;
        }
        return ((GlobalObject) Context.getGlobalTrusted()).wrapAsObject(obj);
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

        if (data.needsCallee()) {
            final MethodHandle callHandle = getBestSpecializedInvokeHandle(type);

            if (NashornCallSiteDescriptor.isScope(desc)) {
                // Make a handle that drops the passed "this" argument and substitutes either Global or Undefined
                // (callee, this, args...) => (callee, args...)
                boundHandle = MH.insertArguments(callHandle, 1, needsWrappedThis() ? Context.getGlobalTrusted() : ScriptRuntime.UNDEFINED);
                // (callee, args...) => (callee, [this], args...)
                boundHandle = MH.dropArguments(boundHandle, 1, Object.class);
            } else {
                // It's already (callee, this, args...), just what we need
                boundHandle = callHandle;

                // For non-strict functions, check whether this-object is primitive type.
                // If so add a to-object-wrapper argument filter.
                // Else install a guard that will trigger a relink when the argument becomes primitive.
                if (needsWrappedThis()) {
                    if (isPrimitiveThis(request.getArguments()[1])) {
                        boundHandle = MH.filterArguments(boundHandle, 1, WRAPFILTER);
                    } else {
                        guard = NashornGuards.getNonStrictFunctionGuard(this);
                    }
                }
            }
        } else {
            final MethodHandle callHandle = getBestSpecializedInvokeHandle(type.dropParameterTypes(0, 1));

            if (NashornCallSiteDescriptor.isScope(desc)) {
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

        boundHandle = pairArguments(boundHandle, type);
        return new GuardedInvocation(boundHandle, guard == null ? NashornGuards.getFunctionGuard(this) : guard);
   }

    /**
     * Used for noSuchMethod/noSuchProperty and JSAdapter hooks.
     *
     * These don't want a callee parameter, so bind that. Name binding is optional.
     */
    MethodHandle getCallMethodHandle(final MethodType type, final String bindName) {
        return pairArguments(bindToNameIfNeeded(bindToCalleeIfNeeded(getBestSpecializedInvokeHandle(type)), bindName), type);
    }

    private static MethodHandle bindToNameIfNeeded(final MethodHandle methodHandle, final String bindName) {
        return bindName == null ? methodHandle : MH.insertArguments(methodHandle, 1, bindName);
    }

    /**
     * Convert this argument for non-strict functions according to ES 10.4.3
     *
     * @param thiz the this argument
     *
     * @return the converted this object
     */
    protected Object convertThisObject(final Object thiz) {
        if (!(thiz instanceof ScriptObject) && needsWrappedThis()) {
            if (JSType.nullOrUndefined(thiz)) {
                return Context.getGlobalTrusted();
            }

            if (isPrimitiveThis(thiz)) {
                return ((GlobalObject)Context.getGlobalTrusted()).wrapAsObject(thiz);
            }
        }

        return thiz;
    }

    private static boolean isPrimitiveThis(Object obj) {
        return obj instanceof String || obj instanceof ConsString ||
               obj instanceof Number || obj instanceof Boolean;
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

