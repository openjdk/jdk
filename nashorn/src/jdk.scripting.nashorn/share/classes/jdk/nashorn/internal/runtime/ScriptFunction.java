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
import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.util.Collections;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.support.Guards;
import jdk.nashorn.internal.codegen.ApplySpecialization;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.objects.NativeFunction;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;

/**
 * Runtime representation of a JavaScript function.
 */
public abstract class ScriptFunction extends ScriptObject {

    /** Method handle for prototype getter for this ScriptFunction */
    public static final MethodHandle G$PROTOTYPE = findOwnMH_S("G$prototype", Object.class, Object.class);

    /** Method handle for prototype setter for this ScriptFunction */
    public static final MethodHandle S$PROTOTYPE = findOwnMH_S("S$prototype", void.class, Object.class, Object.class);

    /** Method handle for length getter for this ScriptFunction */
    public static final MethodHandle G$LENGTH = findOwnMH_S("G$length", int.class, Object.class);

    /** Method handle for name getter for this ScriptFunction */
    public static final MethodHandle G$NAME = findOwnMH_S("G$name", Object.class, Object.class);

    /** Method handle used for implementing sync() in mozilla_compat */
    public static final MethodHandle INVOKE_SYNC = findOwnMH_S("invokeSync", Object.class, ScriptFunction.class, Object.class, Object.class, Object[].class);

    /** Method handle for allocate function for this ScriptFunction */
    static final MethodHandle ALLOCATE = findOwnMH_V("allocate", Object.class);

    private static final MethodHandle WRAPFILTER = findOwnMH_S("wrapFilter", Object.class, Object.class);

    private static final MethodHandle SCRIPTFUNCTION_GLOBALFILTER = findOwnMH_S("globalFilter", Object.class, Object.class);

    /** method handle to scope getter for this ScriptFunction */
    public static final Call GET_SCOPE = virtualCallNoLookup(ScriptFunction.class, "getScope", ScriptObject.class);

    private static final MethodHandle IS_FUNCTION_MH  = findOwnMH_S("isFunctionMH", boolean.class, Object.class, ScriptFunctionData.class);

    private static final MethodHandle IS_APPLY_FUNCTION  = findOwnMH_S("isApplyFunction", boolean.class, boolean.class, Object.class, Object.class);

    private static final MethodHandle IS_NONSTRICT_FUNCTION = findOwnMH_S("isNonStrictFunction", boolean.class, Object.class, Object.class, ScriptFunctionData.class);

    private static final MethodHandle ADD_ZEROTH_ELEMENT = findOwnMH_S("addZerothElement", Object[].class, Object[].class, Object.class);

    private static final MethodHandle WRAP_THIS = MH.findStatic(MethodHandles.lookup(), ScriptFunctionData.class, "wrapThis", MH.type(Object.class, Object.class));

    /** The parent scope. */
    private final ScriptObject scope;

    private final ScriptFunctionData data;

    /** The property map used for newly allocated object when function is used as constructor. */
    protected PropertyMap allocatorMap;

    /**
     * Constructor
     *
     * @param name          function name
     * @param methodHandle  method handle to function (if specializations are present, assumed to be most generic)
     * @param map           property map
     * @param scope         scope
     * @param specs         specialized version of this function - other method handles
     * @param flags         {@link ScriptFunctionData} flags
     */
    protected ScriptFunction(
            final String name,
            final MethodHandle methodHandle,
            final PropertyMap map,
            final ScriptObject scope,
            final MethodHandle[] specs,
            final int flags) {

        this(new FinalScriptFunctionData(name, methodHandle, specs, flags), map, scope);
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
        this.allocatorMap = data.getAllocatorMap();
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

    private static boolean needsWrappedThis(final Object fn) {
        return fn instanceof ScriptFunction ? ((ScriptFunction)fn).needsWrappedThis() : false;
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

        final ScriptObject object = data.allocate(allocatorMap);

        if (object != null) {
            final Object prototype = getPrototype();
            if (prototype instanceof ScriptObject) {
                object.setInitialProto((ScriptObject)prototype);
            }

            if (object.getProto() == null) {
                object.setInitialProto(getObjectPrototype());
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
     * Create a function that invokes this function synchronized on {@code sync} or the self object
     * of the invocation.
     * @param sync the Object to synchronize on, or undefined
     * @return synchronized function
     */
   public abstract ScriptFunction makeSynchronizedFunction(Object sync);

    /**
     * Return the invoke handle bound to a given ScriptObject self reference.
     * If callee parameter is required result is rebound to this.
     *
     * @param self self reference
     * @return bound invoke handle
     */
    public final MethodHandle getBoundInvokeHandle(final Object self) {
        return MH.bindTo(bindToCalleeIfNeeded(data.getGenericInvoker(scope)), self);
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
        return self instanceof ScriptFunction ?
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
    public static ScriptObject getPrototype(final ScriptFunction constructor) {
        if (constructor != null) {
            final Object proto = constructor.getPrototype();
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
    protected GuardedInvocation findNewMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final MethodType type = desc.getMethodType();
        assert desc.getMethodType().returnType() == Object.class && !NashornCallSiteDescriptor.isOptimistic(desc);
        final CompiledFunction cf = data.getBestConstructor(type, scope);
        final GuardedInvocation bestCtorInv = cf.createConstructorInvocation();
        //TODO - ClassCastException
        return new GuardedInvocation(pairArguments(bestCtorInv.getInvocation(), type), getFunctionGuard(this, cf.getFlags()), bestCtorInv.getSwitchPoints(), null);
    }

    @SuppressWarnings("unused")
    private static Object wrapFilter(final Object obj) {
        if (obj instanceof ScriptObject || !ScriptFunctionData.isPrimitiveThis(obj)) {
            return obj;
        }
        return Context.getGlobal().wrapAsObject(obj);
    }


    @SuppressWarnings("unused")
    private static Object globalFilter(final Object object) {
        // replace whatever we get with the current global object
        return Context.getGlobal();
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
     *
     * @return guarded invocation for call
     */
    @Override
    protected GuardedInvocation findCallMethod(final CallSiteDescriptor desc, final LinkRequest request) {
        final MethodType type = desc.getMethodType();

        final String  name       = getName();
        final boolean isUnstable = request.isCallSiteUnstable();
        final boolean scopeCall  = NashornCallSiteDescriptor.isScope(desc);
        final boolean isCall     = !scopeCall && data.isBuiltin() && "call".equals(name);
        final boolean isApply    = !scopeCall && data.isBuiltin() && "apply".equals(name);

        final boolean isApplyOrCall = isCall | isApply;

        if (isUnstable && !isApplyOrCall) {
            //megamorphic - replace call with apply
            final MethodHandle handle;
            //ensure that the callsite is vararg so apply can consume it
            if (type.parameterCount() == 3 && type.parameterType(2) == Object[].class) {
                // Vararg call site
                handle = ScriptRuntime.APPLY.methodHandle();
            } else {
                // (callee, this, args...) => (callee, this, args[])
                handle = MH.asCollector(ScriptRuntime.APPLY.methodHandle(), Object[].class, type.parameterCount() - 2);
            }

            // If call site is statically typed to take a ScriptFunction, we don't need a guard, otherwise we need a
            // generic "is this a ScriptFunction?" guard.
            return new GuardedInvocation(
                    handle,
                    null,
                    (SwitchPoint)null,
                    ClassCastException.class);
        }

        MethodHandle boundHandle;
        MethodHandle guard = null;

        // Special handling of Function.apply and Function.call. Note we must be invoking
        if (isApplyOrCall && !isUnstable) {
            final Object[] args = request.getArguments();
            if (Bootstrap.isCallable(args[1])) {
                return createApplyOrCallCall(isApply, desc, request, args);
            }
        } //else just fall through and link as ordinary function or unstable apply

        final int programPoint = NashornCallSiteDescriptor.isOptimistic(desc) ? NashornCallSiteDescriptor.getProgramPoint(desc) : INVALID_PROGRAM_POINT;
        final CompiledFunction cf = data.getBestInvoker(type, scope);
        final GuardedInvocation bestInvoker = cf.createFunctionInvocation(type.returnType(), programPoint);
        final MethodHandle callHandle = bestInvoker.getInvocation();

        if (data.needsCallee()) {
            if (scopeCall && needsWrappedThis()) {
                // (callee, this, args...) => (callee, [this], args...)
                boundHandle = MH.filterArguments(callHandle, 1, SCRIPTFUNCTION_GLOBALFILTER);
            } else {
                // It's already (callee, this, args...), just what we need
                boundHandle = callHandle;
            }
        } else if (data.isBuiltin() && "extend".equals(data.getName())) {
            // NOTE: the only built-in named "extend" is NativeJava.extend. As a special-case we're binding the
            // current lookup as its "this" so it can do security-sensitive creation of adapter classes.
            boundHandle = MH.dropArguments(MH.bindTo(callHandle, desc.getLookup()), 0, type.parameterType(0), type.parameterType(1));
        } else if (scopeCall && needsWrappedThis()) {
            // Make a handle that drops the passed "this" argument and substitutes either Global or Undefined
            // (this, args...) => ([this], args...)
            boundHandle = MH.filterArguments(callHandle, 0, SCRIPTFUNCTION_GLOBALFILTER);
            // ([this], args...) => ([callee], [this], args...)
            boundHandle = MH.dropArguments(boundHandle, 0, type.parameterType(0));
        } else {
            // (this, args...) => ([callee], this, args...)
            boundHandle = MH.dropArguments(callHandle, 0, type.parameterType(0));
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

        return new GuardedInvocation(boundHandle, guard == null ? getFunctionGuard(this, cf.getFlags()) : guard, bestInvoker.getSwitchPoints(), null);
    }

    private GuardedInvocation createApplyOrCallCall(final boolean isApply, final CallSiteDescriptor desc, final LinkRequest request, final Object[] args) {
        final MethodType descType = desc.getMethodType();
        final int paramCount = descType.parameterCount();
        if(descType.parameterType(paramCount - 1).isArray()) {
            // This is vararg invocation of apply or call. This can normally only happen when we do a recursive
            // invocation of createApplyOrCallCall (because we're doing apply-of-apply). In this case, create delegate
            // linkage by unpacking the vararg invocation and use pairArguments to introduce the necessary spreader.
            return createVarArgApplyOrCallCall(isApply, desc, request, args);
        }

        final boolean passesThis = paramCount > 2;
        final boolean passesArgs = paramCount > 3;
        final int realArgCount = passesArgs ? paramCount - 3 : 0;

        final Object appliedFn = args[1];
        final boolean appliedFnNeedsWrappedThis = needsWrappedThis(appliedFn);

        //box call back to apply
        CallSiteDescriptor appliedDesc = desc;
        final SwitchPoint applyToCallSwitchPoint = Global.instance().getChangeCallback("apply");
        //enough to change the proto switchPoint here

        final boolean isApplyToCall = NashornCallSiteDescriptor.isApplyToCall(desc);
        final boolean isFailedApplyToCall = isApplyToCall && applyToCallSwitchPoint.hasBeenInvalidated();

        // R(apply|call, ...) => R(...)
        MethodType appliedType = descType.dropParameterTypes(0, 1);
        if (!passesThis) {
            // R() => R(this)
            appliedType = appliedType.insertParameterTypes(1, Object.class);
        } else if (appliedFnNeedsWrappedThis) {
            appliedType = appliedType.changeParameterType(1, Object.class);
        }

        if (isApply || isFailedApplyToCall) {
            if (passesArgs) {
                // R(this, args) => R(this, Object[])
                appliedType = appliedType.changeParameterType(2, Object[].class);
                // drop any extraneous arguments for the apply fail case
                if (isFailedApplyToCall) {
                    appliedType = appliedType.dropParameterTypes(3, paramCount - 1);
                }
            } else {
                // R(this) => R(this, Object[])
                appliedType = appliedType.insertParameterTypes(2, Object[].class);
            }
        }

        appliedDesc = appliedDesc.changeMethodType(appliedType);

        // Create the same arguments for the delegate linking request that would be passed in an actual apply'd invocation
        final Object[] appliedArgs = new Object[isApply ? 3 : appliedType.parameterCount()];
        appliedArgs[0] = appliedFn;
        appliedArgs[1] = passesThis ? appliedFnNeedsWrappedThis ? ScriptFunctionData.wrapThis(args[2]) : args[2] : ScriptRuntime.UNDEFINED;
        if (isApply && !isFailedApplyToCall) {
            appliedArgs[2] = passesArgs ? NativeFunction.toApplyArgs(args[3]) : ScriptRuntime.EMPTY_ARRAY;
        } else {
            if (passesArgs) {
                if (isFailedApplyToCall) {
                    final Object[] tmp = new Object[args.length - 3];
                    System.arraycopy(args, 3, tmp, 0, tmp.length);
                    appliedArgs[2] = NativeFunction.toApplyArgs(tmp);
                } else {
                    assert !isApply;
                    System.arraycopy(args, 3, appliedArgs, 2, args.length - 3);
                }
            } else if (isFailedApplyToCall) {
                appliedArgs[2] = ScriptRuntime.EMPTY_ARRAY;
            }
        }

        // Ask the linker machinery for an invocation of the target function
        final LinkRequest appliedRequest = request.replaceArguments(appliedDesc, appliedArgs);
        GuardedInvocation appliedInvocation;
        try {
            appliedInvocation = Bootstrap.getLinkerServices().getGuardedInvocation(appliedRequest);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        assert appliedRequest != null; // Bootstrap.isCallable() returned true for args[1], so it must produce a linkage.

        final Class<?> applyFnType = descType.parameterType(0);
        MethodHandle inv = appliedInvocation.getInvocation(); //method handle from apply invocation. the applied function invocation

        if (isApply && !isFailedApplyToCall) {
            if (passesArgs) {
                // Make sure that the passed argArray is converted to Object[] the same way NativeFunction.apply() would do it.
                inv = MH.filterArguments(inv, 2, NativeFunction.TO_APPLY_ARGS);
            } else {
                // If the original call site doesn't pass argArray, pass in an empty array
                inv = MH.insertArguments(inv, 2, (Object)ScriptRuntime.EMPTY_ARRAY);
            }
        }

        if (isApplyToCall) {
            if (isFailedApplyToCall) {
                //take the real arguments that were passed to a call and force them into the apply instead
                Context.getContextTrusted().getLogger(ApplySpecialization.class).info("Collection arguments to revert call to apply in " + appliedFn);
                inv = MH.asCollector(inv, Object[].class, realArgCount);
            } else {
                appliedInvocation = appliedInvocation.addSwitchPoint(applyToCallSwitchPoint);
            }
        }

        if (!passesThis) {
            // If the original call site doesn't pass in a thisArg, pass in Global/undefined as needed
            inv = bindImplicitThis(appliedFn, inv);
        } else if (appliedFnNeedsWrappedThis) {
            // target function needs a wrapped this, so make sure we filter for that
            inv = MH.filterArguments(inv, 1, WRAP_THIS);
        }
        inv = MH.dropArguments(inv, 0, applyFnType);

        MethodHandle guard = appliedInvocation.getGuard();
        // If the guard checks the value of "this" but we aren't passing thisArg, insert the default one
        if (!passesThis && guard.type().parameterCount() > 1) {
            guard = bindImplicitThis(appliedFn, guard);
        }
        final MethodType guardType = guard.type();

        // We need to account for the dropped (apply|call) function argument.
        guard = MH.dropArguments(guard, 0, descType.parameterType(0));
        // Take the "isApplyFunction" guard, and bind it to this function.
        MethodHandle applyFnGuard = MH.insertArguments(IS_APPLY_FUNCTION, 2, this);
        // Adapt the guard to receive all the arguments that the original guard does.
        applyFnGuard = MH.dropArguments(applyFnGuard, 2, guardType.parameterArray());
        // Fold the original function guard into our apply guard.
        guard = MH.foldArguments(applyFnGuard, guard);

        return appliedInvocation.replaceMethods(inv, guard);
    }

    /*
     * This method is used for linking nested apply. Specialized apply and call linking will create a variable arity
     * call site for an apply call; when createApplyOrCallCall sees a linking request for apply or call with
     * Nashorn-style variable arity call site (last argument type is Object[]) it'll delegate to this method.
     * This method converts the link request from a vararg to a non-vararg one (unpacks the array), then delegates back
     * to createApplyOrCallCall (with which it is thus mutually recursive), and adds appropriate argument spreaders to
     * invocation and the guard of whatever createApplyOrCallCall returned to adapt it back into a variable arity
     * invocation. It basically reduces the problem of vararg call site linking of apply and call back to the (already
     * solved by createApplyOrCallCall) non-vararg call site linking.
     */
    private GuardedInvocation createVarArgApplyOrCallCall(final boolean isApply, final CallSiteDescriptor desc,
            final LinkRequest request, final Object[] args) {
        final MethodType descType = desc.getMethodType();
        final int paramCount = descType.parameterCount();
        final Object[] varArgs = (Object[])args[paramCount - 1];
        // -1 'cause we're not passing the vararg array itself
        final int copiedArgCount = args.length - 1;
        final int varArgCount = varArgs.length;

        // Spread arguments for the delegate createApplyOrCallCall invocation.
        final Object[] spreadArgs = new Object[copiedArgCount + varArgCount];
        System.arraycopy(args, 0, spreadArgs, 0, copiedArgCount);
        System.arraycopy(varArgs, 0, spreadArgs, copiedArgCount, varArgCount);

        // Spread call site descriptor for the delegate createApplyOrCallCall invocation. We drop vararg array and
        // replace it with a list of Object.class.
        final MethodType spreadType = descType.dropParameterTypes(paramCount - 1, paramCount).appendParameterTypes(
                Collections.<Class<?>>nCopies(varArgCount, Object.class));
        final CallSiteDescriptor spreadDesc = desc.changeMethodType(spreadType);

        // Delegate back to createApplyOrCallCall with the spread (that is, reverted to non-vararg) request/
        final LinkRequest spreadRequest = request.replaceArguments(spreadDesc, spreadArgs);
        final GuardedInvocation spreadInvocation = createApplyOrCallCall(isApply, spreadDesc, spreadRequest, spreadArgs);

        // Add spreader combinators to returned invocation and guard.
        return spreadInvocation.replaceMethods(
                // Use standard ScriptObject.pairArguments on the invocation
                pairArguments(spreadInvocation.getInvocation(), descType),
                // Use our specialized spreadGuardArguments on the guard (see below).
                spreadGuardArguments(spreadInvocation.getGuard(), descType));
    }

    private static MethodHandle spreadGuardArguments(final MethodHandle guard, final MethodType descType) {
        final MethodType guardType = guard.type();
        final int guardParamCount = guardType.parameterCount();
        final int descParamCount = descType.parameterCount();
        final int spreadCount = guardParamCount - descParamCount + 1;
        if (spreadCount <= 0) {
            // Guard doesn't dip into the varargs
            return guard;
        }

        final MethodHandle arrayConvertingGuard;
        // If the last parameter type of the guard is an array, then it is already itself a guard for a vararg apply
        // invocation. We must filter the last argument with toApplyArgs otherwise deeper levels of nesting will fail
        // with ClassCastException of NativeArray to Object[].
        if(guardType.parameterType(guardParamCount - 1).isArray()) {
            arrayConvertingGuard = MH.filterArguments(guard, guardParamCount - 1, NativeFunction.TO_APPLY_ARGS);
        } else {
            arrayConvertingGuard = guard;
        }

        return ScriptObject.adaptHandleToVarArgCallSite(arrayConvertingGuard, descParamCount);
    }

    private static MethodHandle bindImplicitThis(final Object fn, final MethodHandle mh) {
         final MethodHandle bound;
         if(fn instanceof ScriptFunction && ((ScriptFunction)fn).needsWrappedThis()) {
             bound = MH.filterArguments(mh, 1, SCRIPTFUNCTION_GLOBALFILTER);
         } else {
             bound = mh;
         }
         return MH.insertArguments(bound, 1, ScriptRuntime.UNDEFINED);
     }

    /**
     * Used for noSuchMethod/noSuchProperty and JSAdapter hooks.
     *
     * These don't want a callee parameter, so bind that. Name binding is optional.
     */
    MethodHandle getCallMethodHandle(final MethodType type, final String bindName) {
        return pairArguments(bindToNameIfNeeded(bindToCalleeIfNeeded(data.getGenericInvoker(scope)), bindName), type);
    }

    private static MethodHandle bindToNameIfNeeded(final MethodHandle methodHandle, final String bindName) {
        if (bindName == null) {
            return methodHandle;
        }

        // if it is vararg method, we need to extend argument array with
        // a new zeroth element that is set to bindName value.
        final MethodType methodType = methodHandle.type();
        final int parameterCount = methodType.parameterCount();
        final boolean isVarArg = parameterCount > 0 && methodType.parameterType(parameterCount - 1).isArray();

        if (isVarArg) {
            return MH.filterArguments(methodHandle, 1, MH.insertArguments(ADD_ZEROTH_ELEMENT, 1, bindName));
        }
        return MH.insertArguments(methodHandle, 1, bindName);
    }

    /**
     * Get the guard that checks if a {@link ScriptFunction} is equal to
     * a known ScriptFunction, using reference comparison
     *
     * @param function The ScriptFunction to check against. This will be bound to the guard method handle
     *
     * @return method handle for guard
     */
    private static MethodHandle getFunctionGuard(final ScriptFunction function, final int flags) {
        assert function.data != null;
        // Built-in functions have a 1-1 correspondence to their ScriptFunctionData, so we can use a cheaper identity
        // comparison for them.
        if (function.data.isBuiltin()) {
            return Guards.getIdentityGuard(function);
        }
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
    private static boolean isApplyFunction(final boolean appliedFnCondition, final Object self, final Object expectedSelf) {
        // NOTE: we're using self == expectedSelf as we're only using this with built-in functions apply() and call()
        return appliedFnCondition && self == expectedSelf;
    }

    @SuppressWarnings("unused")
    private static Object[] addZerothElement(final Object[] args, final Object value) {
        // extends input array with by adding new zeroth element
        final Object[] src = args == null? ScriptRuntime.EMPTY_ARRAY : args;
        final Object[] result = new Object[src.length + 1];
        System.arraycopy(src, 0, result, 1, src.length);
        result[0] = value;
        return result;
    }

    @SuppressWarnings("unused")
    private static Object invokeSync(final ScriptFunction func, final Object sync, final Object self, final Object... args)
            throws Throwable {
        final Object syncObj = sync == UNDEFINED ? self : sync;
        synchronized (syncObj) {
            return func.invoke(self, args);
        }
    }

    private static MethodHandle findOwnMH_S(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), ScriptFunction.class, name, MH.type(rtype, types));
    }

    private static MethodHandle findOwnMH_V(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findVirtual(MethodHandles.lookup(), ScriptFunction.class, name, MH.type(rtype, types));
    }
}

