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

import static jdk.nashorn.internal.lookup.Lookup.MH;
import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.nashorn.internal.runtime.linker.JavaAdapterFactory;

/**
 * A container for data needed to instantiate a specific {@link ScriptFunction} at runtime.
 * Instances of this class are created during codegen and stored in script classes'
 * constants array to reduce function instantiation overhead during runtime.
 */
public abstract class ScriptFunctionData {

    /** Name of the function or "" for anonynous functions */
    protected final String name;

    /** All versions of this function that have been generated to code */
    protected final CompiledFunctions code;

    private int arity;

    private final boolean isStrict;

    private final boolean isBuiltin;

    private final boolean isConstructor;

    private static final MethodHandle NEWFILTER     = findOwnMH("newFilter", Object.class, Object.class, Object.class);
    private static final MethodHandle BIND_VAR_ARGS = findOwnMH("bindVarArgs", Object[].class, Object[].class, Object[].class);

    /**
     * Constructor
     *
     * @param name          script function name
     * @param arity         arity
     * @param isStrict      is the function strict
     * @param isBuiltin     is the function built in
     * @param isConstructor is the function a constructor
     */
    ScriptFunctionData(final String name, final int arity, final boolean isStrict, final boolean isBuiltin, final boolean isConstructor) {
        this.name          = name;
        this.arity         = arity;
        this.code          = new CompiledFunctions();
        this.isStrict      = isStrict;
        this.isBuiltin     = isBuiltin;
        this.isConstructor = isConstructor;
    }

    final int getArity() {
        return arity;
    }

    /**
     * Used from e.g. Native*$Constructors as an explicit call. TODO - make arity immutable and final
     * @param arity new arity
     */
    void setArity(final int arity) {
        this.arity = arity;
    }

    CompiledFunction bind(final CompiledFunction originalInv, final ScriptFunction fn, final Object self, final Object[] args) {
        final MethodHandle boundInvoker = bindInvokeHandle(originalInv.getInvoker(), fn, self, args);

        //TODO the boundinvoker.type() could actually be more specific here
        if (isConstructor()) {
            ensureConstructor(originalInv);
            return new CompiledFunction(boundInvoker.type(), boundInvoker, bindConstructHandle(originalInv.getConstructor(), fn, args));
        }

        return new CompiledFunction(boundInvoker.type(), boundInvoker);
    }

    /**
     * Is this a ScriptFunction generated with strict semantics?
     * @return true if strict, false otherwise
     */
    public boolean isStrict() {
        return isStrict;
    }

    boolean isBuiltin() {
        return isBuiltin;
    }

    boolean isConstructor() {
        return isConstructor;
    }

    boolean needsCallee() {
        // we don't know if we need a callee or not unless we are generated
        ensureCodeGenerated();
        return code.needsCallee();
    }

    /**
     * Returns true if this is a non-strict, non-built-in function that requires non-primitive this argument
     * according to ECMA 10.4.3.
     * @return true if this argument must be an object
     */
    boolean needsWrappedThis() {
        return !isStrict && !isBuiltin;
    }

    String toSource() {
        return "function " + (name == null ? "" : name) + "() { [native code] }";
    }

    String getName() {
        return name;
    }

    /**
     * Get this function as a String containing its source code. If no source code
     * exists in this ScriptFunction, its contents will be displayed as {@code [native code]}
     *
     * @return string representation of this function
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("name='").
                append(name.isEmpty() ? "<anonymous>" : name).
                append("' ").
                append(code.size()).
                append(" invokers=").
                append(code);

        return sb.toString();
    }

    /**
     * Pick the best invoker, i.e. the one version of this method with as narrow and specific
     * types as possible. If the call site arguments are objects, but boxed primitives we can
     * also try to get a primitive version of the method and do an unboxing filter, but then
     * we need to insert a guard that checks the argument is really always a boxed primitive
     * and not suddenly a "real" object
     *
     * @param callSiteType callsite type
     * @param args         arguments at callsite on first trampoline invocation
     * @return method handle to best invoker
     */
    MethodHandle getBestInvoker(final MethodType callSiteType, final Object[] args) {
        return getBest(callSiteType).getInvoker();
    }

    MethodHandle getBestInvoker(final MethodType callSiteType) {
        return getBestInvoker(callSiteType, null);
    }

    MethodHandle getBestConstructor(final MethodType callSiteType, final Object[] args) {
        if (!isConstructor()) {
            throw typeError("not.a.constructor", toSource());
        }
        ensureCodeGenerated();

        final CompiledFunction best = getBest(callSiteType);
        ensureConstructor(best);
        return best.getConstructor();
    }

    MethodHandle getBestConstructor(final MethodType callSiteType) {
        return getBestConstructor(callSiteType, null);
    }

    /**
     * Subclass responsibility. If we can have lazy code generation, this is a hook to ensure that
     * code exists before performing an operation.
     */
    protected void ensureCodeGenerated() {
        //empty
    }

    /**
     * Return a generic Object/Object invoker for this method. It will ensure code
     * is generated, get the most generic of all versions of this function and adapt it
     * to Objects.
     *
     * TODO this is only public because {@link JavaAdapterFactory} can't supply us with
     * a MethodType that we can use for lookup due to boostrapping problems. Can be fixed
     *
     * @return generic invoker of this script function
     */
    public final MethodHandle getGenericInvoker() {
        ensureCodeGenerated();
        return code.generic().getInvoker();
    }

    final MethodHandle getGenericConstructor() {
        ensureCodeGenerated();
        ensureConstructor(code.generic());
        return code.generic().getConstructor();
    }

    private CompiledFunction getBest(final MethodType callSiteType) {
        ensureCodeGenerated();
        return code.best(callSiteType);
    }

    /**
     * Allocates an object using this function's allocator.
     * @return the object allocated using this function's allocator, or null if the function doesn't have an allocator.
     */
    ScriptObject allocate() {
        return null;
    }

    /**
     * This method is used to create the immutable portion of a bound function.
     * See {@link ScriptFunction#makeBoundFunction(Object, Object[])}
     *
     * @param fn the original function being bound
     * @param self this reference to bind. Can be null.
     * @param args additional arguments to bind. Can be null.
     */
    ScriptFunctionData makeBoundFunctionData(final ScriptFunction fn, final Object self, final Object[] args) {
        ensureCodeGenerated();

        final Object[] allArgs = args == null ? ScriptRuntime.EMPTY_ARRAY : args;
        final int length = args == null ? 0 : args.length;

        CompiledFunctions boundList = new CompiledFunctions();
        if (code.size() == 1) {
            // only one variant - bind that
            boundList.add(bind(code.first(), fn, self, allArgs));
        } else {
            // There are specialized versions. Get the most generic one.
            // This is to avoid ambiguous overloaded versions of bound and
            // specialized variants and choosing wrong overload.
            final MethodHandle genInvoker = getGenericInvoker();
            final CompiledFunction inv = new CompiledFunction(genInvoker.type(), genInvoker, getGenericConstructor());
            boundList.add(bind(inv, fn, self, allArgs));
        }

        ScriptFunctionData boundData = new FinalScriptFunctionData(name, arity == -1 ? -1 : Math.max(0, arity - length), boundList, isStrict(), isBuiltin(), isConstructor());
        return boundData;
    }

    /**
     * Compose a constructor given a primordial constructor handle.
     *
     * @param ctor primordial constructor handle
     * @return the composed constructor
     */
    protected MethodHandle composeConstructor(final MethodHandle ctor) {
        // If it was (callee, this, args...), permute it to (this, callee, args...). We're doing this because having
        // "this" in the first argument position is what allows the elegant folded composition of
        // (newFilter x constructor x allocator) further down below in the code. Also, ensure the composite constructor
        // always returns Object.
        final boolean needsCallee = needsCallee(ctor);
        MethodHandle composedCtor = needsCallee ? swapCalleeAndThis(ctor) : ctor;

        composedCtor = changeReturnTypeToObject(composedCtor);

        final MethodType ctorType = composedCtor.type();

        // Construct a dropping type list for NEWFILTER, but don't include constructor "this" into it, so it's actually
        // captured as "allocation" parameter of NEWFILTER after we fold the constructor into it.
        // (this, [callee, ]args...) => ([callee, ]args...)
        final Class<?>[] ctorArgs = ctorType.dropParameterTypes(0, 1).parameterArray();

        // Fold constructor into newFilter that replaces the return value from the constructor with the originally
        // allocated value when the originally allocated value is a primitive.
        // (result, this, [callee, ]args...) x (this, [callee, ]args...) => (this, [callee, ]args...)
        composedCtor = MH.foldArguments(MH.dropArguments(NEWFILTER, 2, ctorArgs), composedCtor);

        // allocate() takes a ScriptFunction and returns a newly allocated ScriptObject...
        if (needsCallee) {
            // ...we either fold it into the previous composition, if we need both the ScriptFunction callee object and
            // the newly allocated object in the arguments, so (this, callee, args...) x (callee) => (callee, args...),
            // or...
            return MH.foldArguments(composedCtor, ScriptFunction.ALLOCATE);
        }

        // ...replace the ScriptFunction argument with the newly allocated object, if it doesn't need the callee
        // (this, args...) filter (callee) => (callee, args...)
        return MH.filterArguments(composedCtor, 0, ScriptFunction.ALLOCATE);
    }

    /**
     * If this function's method handles need a callee parameter, swap the order of first two arguments for the passed
     * method handle. If this function's method handles don't need a callee parameter, returns the original method
     * handle unchanged.
     *
     * @param mh a method handle with order of arguments {@code (callee, this, args...)}
     *
     * @return a method handle with order of arguments {@code (this, callee, args...)}
     */
    private static MethodHandle swapCalleeAndThis(final MethodHandle mh) {
        final MethodType type = mh.type();
        assert type.parameterType(0) == ScriptFunction.class : type;
        assert type.parameterType(1) == Object.class : type;
        final MethodType newType = type.changeParameterType(0, Object.class).changeParameterType(1, ScriptFunction.class);
        final int[] reorder = new int[type.parameterCount()];
        reorder[0] = 1;
        assert reorder[1] == 0;
        for (int i = 2; i < reorder.length; ++i) {
            reorder[i] = i;
        }
        return MethodHandles.permuteArguments(mh, newType, reorder);
    }

    /**
     * Convert this argument for non-strict functions according to ES 10.4.3
     *
     * @param thiz the this argument
     *
     * @return the converted this object
     */
    private Object convertThisObject(final Object thiz) {
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

    static boolean isPrimitiveThis(final Object obj) {
        return obj instanceof String || obj instanceof ConsString ||
               obj instanceof Number || obj instanceof Boolean;
    }

    /**
     * Creates an invoker method handle for a bound function.
     *
     * @param targetFn the function being bound
     * @param originalInvoker an original invoker method handle for the function. This can be its generic invoker or
     * any of its specializations.
     * @param self the "this" value being bound
     * @param args additional arguments being bound
     *
     * @return a bound invoker method handle that will bind the self value and the specified arguments. The resulting
     * invoker never needs a callee; if the original invoker needed it, it will be bound to {@code fn}. The resulting
     * invoker still takes an initial {@code this} parameter, but it is always dropped and the bound {@code self} passed
     * to the original invoker on invocation.
     */
    private MethodHandle bindInvokeHandle(final MethodHandle originalInvoker, final ScriptFunction targetFn, final Object self, final Object[] args) {
        // Is the target already bound? If it is, we won't bother binding either callee or self as they're already bound
        // in the target and will be ignored anyway.
        final boolean isTargetBound = targetFn.isBoundFunction();

        final boolean needsCallee = needsCallee(originalInvoker);
        assert needsCallee == needsCallee() : "callee contract violation 2";
        assert !(isTargetBound && needsCallee); // already bound functions don't need a callee

        final Object boundSelf = isTargetBound ? null : convertThisObject(self);
        final MethodHandle boundInvoker;

        if (isVarArg(originalInvoker)) {
            // First, bind callee and this without arguments
            final MethodHandle noArgBoundInvoker;

            if (isTargetBound) {
                // Don't bind either callee or this
                noArgBoundInvoker = originalInvoker;
            } else if (needsCallee) {
                // Bind callee and this
                noArgBoundInvoker = MH.insertArguments(originalInvoker, 0, targetFn, boundSelf);
            } else {
                // Only bind this
                noArgBoundInvoker = MH.bindTo(originalInvoker, boundSelf);
            }
            // Now bind arguments
            if (args.length > 0) {
                boundInvoker = varArgBinder(noArgBoundInvoker, args);
            } else {
                boundInvoker = noArgBoundInvoker;
            }
        } else {
            // If target is already bound, insert additional bound arguments after "this" argument, at position 1.
            final int argInsertPos = isTargetBound ? 1 : 0;
            final Object[] boundArgs = new Object[Math.min(originalInvoker.type().parameterCount() - argInsertPos, args.length + (isTargetBound ? 0 : (needsCallee  ? 2 : 1)))];
            int next = 0;
            if (!isTargetBound) {
                if (needsCallee) {
                    boundArgs[next++] = targetFn;
                }
                boundArgs[next++] = boundSelf;
            }
            // If more bound args were specified than the function can take, we'll just drop those.
            System.arraycopy(args, 0, boundArgs, next, boundArgs.length - next);
            // If target is already bound, insert additional bound arguments after "this" argument, at position 1;
            // "this" will get dropped anyway by the target invoker. We previously asserted that already bound functions
            // don't take a callee parameter, so we can know that the signature is (this[, args...]) therefore args
            // start at position 1. If the function is not bound, we start inserting arguments at position 0.
            boundInvoker = MH.insertArguments(originalInvoker, argInsertPos, boundArgs);
        }

        if (isTargetBound) {
            return boundInvoker;
        }

        // If the target is not already bound, add a dropArguments that'll throw away the passed this
        return MH.dropArguments(boundInvoker, 0, Object.class);
    }

    /**
     * Creates a constructor method handle for a bound function using the passed constructor handle.
     *
     * @param originalConstructor the constructor handle to bind. It must be a composed constructor.
     * @param fn the function being bound
     * @param args arguments being bound
     *
     * @return a bound constructor method handle that will bind the specified arguments. The resulting constructor never
     * needs a callee; if the original constructor needed it, it will be bound to {@code fn}. The resulting constructor
     * still takes an initial {@code this} parameter and passes it to the underlying original constructor. Finally, if
     * this script function data object has no constructor handle, null is returned.
     */
    private static MethodHandle bindConstructHandle(final MethodHandle originalConstructor, final ScriptFunction fn, final Object[] args) {
        assert originalConstructor != null;

        // If target function is already bound, don't bother binding the callee.
        final MethodHandle calleeBoundConstructor = fn.isBoundFunction() ? originalConstructor :
            MH.dropArguments(MH.bindTo(originalConstructor, fn), 0, ScriptFunction.class);

        if (args.length == 0) {
            return calleeBoundConstructor;
        }

        if (isVarArg(calleeBoundConstructor)) {
            return varArgBinder(calleeBoundConstructor, args);
        }

        final Object[] boundArgs;

        final int maxArgCount = calleeBoundConstructor.type().parameterCount() - 1;
        if (args.length <= maxArgCount) {
            boundArgs = args;
        } else {
            boundArgs = new Object[maxArgCount];
            System.arraycopy(args, 0, boundArgs, 0, maxArgCount);
        }

        return MH.insertArguments(calleeBoundConstructor, 1, boundArgs);
    }

    /**
     * Execute this script function.
     *
     * @param self  Target object.
     * @param arguments  Call arguments.
     * @return ScriptFunction result.
     *
     * @throws Throwable if there is an exception/error with the invocation or thrown from it
     */
    Object invoke(final ScriptFunction fn, final Object self, final Object... arguments) throws Throwable {
        final MethodHandle mh  = getGenericInvoker();
        final Object   selfObj = convertThisObject(self);
        final Object[] args    = arguments == null ? ScriptRuntime.EMPTY_ARRAY : arguments;

        if (isVarArg(mh)) {
            if (needsCallee(mh)) {
                return mh.invokeExact(fn, selfObj, args);
            }
            return mh.invokeExact(selfObj, args);
        }

        final int paramCount = mh.type().parameterCount();
        if (needsCallee(mh)) {
            switch (paramCount) {
            case 2:
                return mh.invokeExact(fn, selfObj);
            case 3:
                return mh.invokeExact(fn, selfObj, getArg(args, 0));
            case 4:
                return mh.invokeExact(fn, selfObj, getArg(args, 0), getArg(args, 1));
            case 5:
                return mh.invokeExact(fn, selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2));
            case 6:
                return mh.invokeExact(fn, selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3));
            case 7:
                return mh.invokeExact(fn, selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4));
            case 8:
                return mh.invokeExact(fn, selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4), getArg(args, 5));
            default:
                return mh.invokeWithArguments(withArguments(fn, selfObj, paramCount, args));
            }
        }

        switch (paramCount) {
        case 1:
            return mh.invokeExact(selfObj);
        case 2:
            return mh.invokeExact(selfObj, getArg(args, 0));
        case 3:
            return mh.invokeExact(selfObj, getArg(args, 0), getArg(args, 1));
        case 4:
            return mh.invokeExact(selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2));
        case 5:
            return mh.invokeExact(selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3));
        case 6:
            return mh.invokeExact(selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4));
        case 7:
            return mh.invokeExact(selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4), getArg(args, 5));
        default:
            return mh.invokeWithArguments(withArguments(null, selfObj, paramCount, args));
        }
    }

    Object construct(final ScriptFunction fn, final Object... arguments) throws Throwable {
        final MethodHandle mh   = getGenericConstructor();
        final Object[]     args = arguments == null ? ScriptRuntime.EMPTY_ARRAY : arguments;

        if (isVarArg(mh)) {
            if (needsCallee(mh)) {
                return mh.invokeExact(fn, args);
            }
            return mh.invokeExact(args);
        }

        final int paramCount = mh.type().parameterCount();
        if (needsCallee(mh)) {
            switch (paramCount) {
            case 1:
                return mh.invokeExact(fn);
            case 2:
                return mh.invokeExact(fn, getArg(args, 0));
            case 3:
                return mh.invokeExact(fn, getArg(args, 0), getArg(args, 1));
            case 4:
                return mh.invokeExact(fn, getArg(args, 0), getArg(args, 1), getArg(args, 2));
            case 5:
                return mh.invokeExact(fn, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3));
            case 6:
                return mh.invokeExact(fn, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4));
            case 7:
                return mh.invokeExact(fn, getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4), getArg(args, 5));
            default:
                return mh.invokeWithArguments(withArguments(fn, paramCount, args));
            }
        }

        switch (paramCount) {
        case 0:
            return mh.invokeExact();
        case 1:
            return mh.invokeExact(getArg(args, 0));
        case 2:
            return mh.invokeExact(getArg(args, 0), getArg(args, 1));
        case 3:
            return mh.invokeExact(getArg(args, 0), getArg(args, 1), getArg(args, 2));
        case 4:
            return mh.invokeExact(getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3));
        case 5:
            return mh.invokeExact(getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4));
        case 6:
            return mh.invokeExact(getArg(args, 0), getArg(args, 1), getArg(args, 2), getArg(args, 3), getArg(args, 4), getArg(args, 5));
        default:
            return mh.invokeWithArguments(withArguments(null, paramCount, args));
        }
    }

    private static Object getArg(final Object[] args, final int i) {
        return i < args.length ? args[i] : UNDEFINED;
    }

    private static Object[] withArguments(final ScriptFunction fn, final int argCount, final Object[] args) {
        final Object[] finalArgs = new Object[argCount];

        int nextArg = 0;
        if (fn != null) {
            //needs callee
            finalArgs[nextArg++] = fn;
        }

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

    private static Object[] withArguments(final ScriptFunction fn, final Object self, final int argCount, final Object[] args) {
        final Object[] finalArgs = new Object[argCount];

        int nextArg = 0;
        if (fn != null) {
            //needs callee
            finalArgs[nextArg++] = fn;
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
     * Takes a variable-arity method and binds a variable number of arguments in it. The returned method will filter the
     * vararg array and pass a different array that prepends the bound arguments in front of the arguments passed on
     * invocation
     *
     * @param mh the handle
     * @param args the bound arguments
     *
     * @return the bound method handle
     */
    private static MethodHandle varArgBinder(final MethodHandle mh, final Object[] args) {
        assert args != null;
        assert args.length > 0;
        return MH.filterArguments(mh, mh.type().parameterCount() - 1, MH.bindTo(BIND_VAR_ARGS, args));
    }

    /**
     * Adapts the method handle so its return type is {@code Object}. If the handle's return type is already
     * {@code Object}, the handle is returned unchanged.
     *
     * @param mh the handle to adapt
     * @return the adapted handle
     */
    private static MethodHandle changeReturnTypeToObject(final MethodHandle mh) {
        final MethodType type = mh.type();
        return (type.returnType() == Object.class) ? mh : MH.asType(mh, type.changeReturnType(Object.class));
    }

    private void ensureConstructor(final CompiledFunction inv) {
        if (!inv.hasConstructor()) {
            inv.setConstructor(composeConstructor(inv.getInvoker()));
        }
    }

    /**
     * Heuristic to figure out if the method handle has a callee argument. If it's type is
     * {@code (ScriptFunction, ...)}, then we'll assume it has a callee argument. We need this as
     * the constructor above is not passed this information, and can't just blindly assume it's false
     * (notably, it's being invoked for creation of new scripts, and scripts have scopes, therefore
     * they also always receive a callee).
     *
     * @param mh the examined method handle
     *
     * @return true if the method handle expects a callee, false otherwise
     */
    protected static boolean needsCallee(final MethodHandle mh) {
        final MethodType type = mh.type();
        return (type.parameterCount() > 0 && type.parameterType(0) == ScriptFunction.class);
    }

    /**
     * Check if a javascript function methodhandle is a vararg handle
     *
     * @param mh method handle to check
     *
     * @return true if vararg
     */
    protected static boolean isVarArg(final MethodHandle mh) {
        final MethodType type = mh.type();
        return type.parameterType(type.parameterCount() - 1).isArray();
    }

    @SuppressWarnings("unused")
    private static Object[] bindVarArgs(final Object[] array1, final Object[] array2) {
        if (array2 == null) {
            // Must clone it, as we can't allow the receiving method to alter the array
            return array1.clone();
        }

        final int l2 = array2.length;
        if (l2 == 0) {
            return array1.clone();
        }

        final int l1 = array1.length;
        final Object[] concat = new Object[l1 + l2];
        System.arraycopy(array1, 0, concat, 0, l1);
        System.arraycopy(array2, 0, concat, l1, l2);

        return concat;
    }

    @SuppressWarnings("unused")
    private static Object newFilter(final Object result, final Object allocation) {
        return (result instanceof ScriptObject || !JSType.isPrimitive(result))? result : allocation;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), ScriptFunctionData.class, name, MH.type(rtype, types));
    }
}
