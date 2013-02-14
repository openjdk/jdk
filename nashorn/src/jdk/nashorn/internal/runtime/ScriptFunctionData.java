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

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;
import static jdk.nashorn.internal.runtime.linker.Lookup.MH;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;

/**
 * A container for data needed to instantiate a specific {@link ScriptFunction} at runtime.
 * Instances of this class are created during codegen and stored in script classes'
 * constants array to reduce function instantiation overhead during runtime.
 */
public final class ScriptFunctionData {
    private static final MethodHandle BIND_VAR_ARGS = findOwnMH("bindVarArgs", Object[].class, Object[].class, Object[].class);
    private static final MethodHandle NEWFILTER     = findOwnMH("newFilter", Object.class, Object.class, Object.class);

    // per-function object flags
    private static final int IS_STRICT      = 0b0000_0001;
    private static final int IS_BUILTIN     = 0b0000_0010;
    private static final int HAS_CALLEE     = 0b0000_0100;
    private static final int IS_VARARGS     = 0b0000_1000;
    private static final int IS_CONSTRUCTOR = 0b0001_0000;

    /** Name of the function or "" */
    private final String name;
    /** Source of this function, or null */
    private final Source source;
    /** Map for new instance constructor */
    private PropertyMap  allocatorMap;
    /** Start position and length in source */
    private final long   token;
    /** Number of expected arguments, either taken from FunctionNode or calculated from method handle signature*/
    private int          arity;
    private final int    flags;

    /** Reference to code for this method. */
    private MethodHandle invoker;
    /** Reference to code for this method when called to create "new" object. This must always be populated with a
     * result of calling {@link #composeConstructor(MethodHandle)} on the value of the {@link #invoker} field. */
    private MethodHandle constructor;
    /** Constructor to create a new instance. */
    private MethodHandle allocator;
    /** Generic invoker to used in {@link ScriptFunction#invoke(Object, Object...)}. */
    private MethodHandle genericInvoker;
    /** Specializations - see @SpecializedFunction */
    private MethodHandle[] invokeSpecializations;
    /** Specializations - see @SpecializedFunction. Same restrictions as for {@link #constructor} apply; only populate
     * with method handles returned from {@link #composeConstructor(MethodHandle)}. */
    private MethodHandle[] constructSpecializations;

    /**
     * Constructor
     * @param fn the function node
     * @param allocatorMap the allocator property map
     */
    public ScriptFunctionData(final FunctionNode fn, final PropertyMap allocatorMap) {

        final long firstToken = fn.getFirstToken();
        final long lastToken  = fn.getLastToken();
        final int  position   = Token.descPosition(firstToken);
        final int  length     = Token.descPosition(lastToken) - position + Token.descLength(lastToken);

        this.name         = fn.isAnonymous() ? "" : fn.getIdent().getName();
        this.source       = fn.getSource();
        this.allocatorMap = allocatorMap;
        this.token        = Token.toDesc(TokenType.FUNCTION, position, length);
        this.arity        = fn.getParameters().size();
        this.flags        = makeFlags(fn.needsCallee(), fn.isVarArg(), fn.isStrictMode(), false, true);
    }

    /**
     * Constructor
     *
     * @param name the function name
     * @param methodHandle the method handle
     * @param specs array of specialized method handles
     * @param strict strict flag
     * @param builtin builtin flag
     * @param isConstructor constructor flags
     */
    public ScriptFunctionData(final String name, final MethodHandle methodHandle, final MethodHandle[] specs, final boolean strict, final boolean builtin, final boolean isConstructor) {
        this(name, null, 0L, methodHandle, specs, strict, builtin, isConstructor);
    }

    private ScriptFunctionData(final String name, final Source source, final long token, final MethodHandle methodHandle, final MethodHandle[] specs, final boolean strict, final boolean builtin, final boolean isConstructor) {
        this.name   = name;
        this.source = source;
        this.token  = token;

        final boolean isVarArg = isVarArg(methodHandle);
        final boolean needsCallee = needsCallee(methodHandle);

        this.flags = makeFlags(needsCallee, isVarArg, strict, builtin, isConstructor);
        int lArity = isVarArg ? -1 : methodHandle.type().parameterCount() - 1; //drop the self param for arity

        if (needsCallee && !isVarArg) {
            lArity--;
        }

        if (isConstructor(methodHandle)) {
            assert isConstructor;
            if (!isVarArg) {
                lArity--;    // drop the boolean flag for arity
            }
            /*
             * We insert a boolean argument to tell if the method was invoked as constructor or not if the method
             * handle's first argument is boolean.
             */
            this.invoker     = MH.insertArguments(methodHandle, 0, false);
            this.constructor = composeConstructor(MH.insertArguments(methodHandle, 0, true));

            if (specs != null) {
                this.invokeSpecializations    = new MethodHandle[specs.length];
                this.constructSpecializations = new MethodHandle[specs.length];
                for (int i = 0; i < specs.length; i++) {
                    this.invokeSpecializations[i]    = MH.insertArguments(specs[i], 0, false);
                    this.constructSpecializations[i] = composeConstructor(MH.insertArguments(specs[i], 0, true));
                }
            }
        } else {
            this.invoker                  = methodHandle;
            this.constructor              = null; // delay composition of the constructor
            this.invokeSpecializations    = specs;
            this.constructSpecializations = null; // delay composition of the constructors
        }
        this.arity = lArity;
    }

    /**
     * Get the arity of the function.
     * @return the arity
     */
    int getArity() {
        return arity;
    }

    /**
     * Set the arity of the function.
     * @param arity the arity
     */
    void setArity(int arity) {
        this.arity = arity;
    }

    /**
     * Get the function name.
     * @return function name
     */
    String getName() {
        return name;
    }

    /**
     * Get this function as a String containing its source code. If no source code
     * exists in this ScriptFunction, its contents will be displayed as {@code [native code]}
     * @return string representation of this function's source
     */
    String toSource() {
        if (source != null && token != 0) {
            return source.getString(Token.descPosition(token), Token.descLength(token));
        }

        return "function " + (name == null ? "" : name) + "() { [native code] }";
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append(super.toString())
                .append(" [ ")
                .append(invoker)
                .append(", ")
                .append((name == null || name.isEmpty()) ? "<anonymous>" : name);

        if (source != null) {
            sb.append(" @ ")
                    .append(source.getName())
                    .append(':')
                    .append(source.getLine(Token.descPosition(token)));
        }
        sb.append(" ]");

        return sb.toString();
    }

    /**
     * Returns true if the function needs a callee argument.
     * @return the needsCallee flag
     */
    boolean needsCallee() {
        return (flags & HAS_CALLEE) != 0;
    }

    /**
     * Returns true if this is a strict-mode function.
     * @return the strict flag
     */
    public boolean isStrict() {
        return (flags & IS_STRICT) != 0;
    }

    /**
     * Returns true if this is a built-in function.
     * @return the built-in flag
     */
    private boolean isBuiltin() {
        return (flags & IS_BUILTIN) != 0;
    }

    /**
     * Returns true if this function can be used as a constructor.
     * @return the constructor flag
     */
    private boolean isConstructor() {
        return (flags & IS_CONSTRUCTOR) != 0;
    }

    /**
     * Returns true if this is a var-arg function.
     * @return the var-arg flag
     */
    private boolean isVarArg() {
        return (flags & IS_VARARGS) != 0;
    }

    /**
     * Returns true if this is a non-strict, non-built-in function that requires non-primitive this argument
     * according to ECMA 10.4.3.
     * @return true if this argument must be an object
     */
    boolean needsWrappedThis() {
        return (flags & (IS_STRICT | IS_BUILTIN)) == 0;
    }

    /**
     * Get the method handle used to invoke this function.
     * @return the invoke handle
     */
    MethodHandle getInvoker() {
        return invoker;
    }

    MethodHandle getBestInvoker(final MethodType type) {
        return SpecializedMethodChooser.candidateWithLowestWeight(type, invoker, invokeSpecializations);
    }

    /**
     * Get the method handle used to invoke this function as a constructor.
     * @return the constructor handle
     */
    private MethodHandle getConstructor() {
        if (constructor == null) {
            constructor = composeConstructor(invoker);
        }

        return constructor;
    }

    MethodHandle getBestConstructor(MethodType descType) {
        if (!isConstructor()) {
            typeError("not.a.constructor", toSource());
        }
        return SpecializedMethodChooser.candidateWithLowestWeight(descType, getConstructor(), getConstructSpecializations());
    }

    private MethodHandle composeConstructor(MethodHandle ctor) {
        // If it was (callee, this, args...), permute it to (this, callee, args...). We're doing this because having
        // "this" in the first argument position is what allows the elegant folded composition of
        // (newFilter x constructor x allocator) further down below in the code. Also, ensure the composite constructor
        // always returns Object.
        MethodHandle composedCtor = changeReturnTypeToObject(swapCalleeAndThis(ctor));

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
        if (needsCallee()) {
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
     * Get an adapted version of the invoker handle that only uses {@code Object} as parameter and return types.
     * @return the generic invoke handle
     */
    private MethodHandle getGenericInvoker() {
        if (genericInvoker == null) {
            assert invoker != null : "invoker is null";
            genericInvoker = makeGenericMethod(invoker);
        }
        return genericInvoker;
    }

    /**
     * Execute this script function.
     * @param self  Target object.
     * @param arguments  Call arguments.
     * @return ScriptFunction result.
     * @throws Throwable if there is an exception/error with the invocation or thrown from it
     */
    Object invoke(final ScriptFunction fn, final Object self, final Object... arguments) throws Throwable {
        final MethodHandle genInvoker = getGenericInvoker();
        final Object selfObj = convertThisObject(self);
        final Object[] args = arguments == null ? ScriptRuntime.EMPTY_ARRAY : arguments;

        if (isVarArg()) {
            if (needsCallee()) {
                return genInvoker.invokeExact(fn, selfObj, args);
            }
            return genInvoker.invokeExact(selfObj, args);
        }

        final int paramCount = genInvoker.type().parameterCount();
        if (needsCallee()) {
            switch (paramCount) {
            case 2:
                return genInvoker.invokeExact(fn, selfObj);
            case 3:
                return genInvoker.invokeExact(fn, selfObj, getArg(args, 0));
            case 4:
                return genInvoker.invokeExact(fn, selfObj, getArg(args, 0), getArg(args, 1));
            case 5:
                return genInvoker.invokeExact(fn, selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2));
            default:
                return genInvoker.invokeWithArguments(withArguments(fn, selfObj, paramCount, args));
            }
        }

        switch (paramCount) {
        case 1:
            return genInvoker.invokeExact(selfObj);
        case 2:
            return genInvoker.invokeExact(selfObj, getArg(args, 0));
        case 3:
            return genInvoker.invokeExact(selfObj, getArg(args, 0), getArg(args, 1));
        case 4:
            return genInvoker.invokeExact(selfObj, getArg(args, 0), getArg(args, 1), getArg(args, 2));
        default:
            return genInvoker.invokeWithArguments(withArguments(null, selfObj, paramCount, args));
        }
    }

    private static Object getArg(final Object[] args, final int i) {
        return i < args.length ? args[i] : UNDEFINED;
    }

    private Object[] withArguments(final ScriptFunction fn, final Object self, final int argCount, final Object[] args) {
        final Object[] finalArgs = new Object[argCount];

        int nextArg = 0;
        if (needsCallee()) {
            assert fn != null;
            finalArgs[nextArg++] = fn;
        } else {
            assert fn == null;
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
     * Get the specialized construct handles for this function.
     * @return array of specialized construct handles
     */
    private MethodHandle[] getConstructSpecializations() {
        if(constructSpecializations == null && invokeSpecializations != null) {
            final MethodHandle[] ctors = new MethodHandle[invokeSpecializations.length];
            for(int i = 0; i < ctors.length; ++i) {
                ctors[i] = composeConstructor(invokeSpecializations[i]);
            }
            constructSpecializations = ctors;
        }
        return constructSpecializations;
    }

    /**
     * Set the method handles for this function.
     * @param invoker the invoker handle
     * @param allocator the allocator handle
     */
    public void setMethodHandles(final MethodHandle invoker, final MethodHandle allocator) {
        // We can't make method handle fields final because they're not available during codegen
        // and they're set when first called, so we enforce set-once here.
        if (this.invoker == null) {
            this.invoker     = invoker;
            this.constructor = null; // delay constructor composition
            this.allocator   = allocator;
        }
    }

    /**
     * Used by the trampoline. Must not be any wider than package
     * private
     * @param invoker new invoker
     */
    void resetInvoker(final MethodHandle invoker) {
        this.invoker     = invoker;
        this.constructor = null; //delay constructor composition
    }

    /**
     * Allocates an object using this function's allocator.
     * @return the object allocated using this function's allocator, or null if the function doesn't have an allocator.
     */
    ScriptObject allocate() {
        if (allocator == null) {
            return null;
        }

        try {
            return (ScriptObject)allocator.invokeExact(allocatorMap);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
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
        final Object[] allArgs = args == null ? ScriptRuntime.EMPTY_ARRAY : args;

        final boolean isConstructor = isConstructor();
        // Note that the new ScriptFunctionData's method handle will not need a callee regardless of whether the
        // original did.
        final ScriptFunctionData boundData = new ScriptFunctionData(name, source, token,
                bindInvokeHandle(invoker, fn, self, allArgs), bindInvokeSpecializations(fn, self, allArgs), isStrict(), isBuiltin(), isConstructor);
        if(isConstructor) {
            // Can't just rely on bound invoke as a basis for constructor, as it ignores the passed "this" in favor of the
            // bound "this"; constructor on the other hand must see the actual "this" received from the allocator.

            // Binding a function will force constructor composition in getConstructor(); not really any way around that
            // as it's the composed constructor that has to be bound to the function.
            boundData.constructor = bindConstructHandle(getConstructor(), fn, allArgs);
            boundData.constructSpecializations = bindConstructorSpecializations(fn, allArgs);
        }
        assert boundData.allocator == null;
        final int thisArity = getArity();
        if(thisArity != -1) {
            boundData.setArity(Math.max(0, thisArity - args.length));
        } else {
            assert boundData.getArity() == -1;
        }
        return boundData;
    }

    /**
     * Convert this argument for non-strict functions according to ES 10.4.3
     *
     * @param thiz the this argument
     *
     * @return the converted this object
     */
    Object convertThisObject(final Object thiz) {
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

    static boolean isPrimitiveThis(Object obj) {
        return obj instanceof String || obj instanceof ConsString ||
               obj instanceof Number || obj instanceof Boolean;
    }

    /**
     * Creates an invoker method handle for a bound function.
     * @param targetFn the function being bound
     * @param originalInvoker an original invoker method handle for the function. This can be its generic invoker or
     * any of its specializations.
     * @param self the "this" value being bound
     * @param args additional arguments being bound
     * @return a bound invoker method handle that will bind the self value and the specified arguments. The resulting
     * invoker never needs a callee; if the original invoker needed it, it will be bound to {@code fn}. The resulting
     * invoker still takes an initial {@code this} parameter, but it is always dropped and the bound {@code self} passed
     * to the original invoker on invocation.
     */
    private MethodHandle bindInvokeHandle(final MethodHandle originalInvoker, final ScriptFunction targetFn, final Object self, final Object[] args) {
        // Is the target already bound? If it is, we won't bother binding either callee or self as they're already bound
        // in the target and will be ignored anyway.
        final boolean isTargetBound = targetFn.isBoundFunction();
        assert !(isTargetBound && needsCallee()); // already bound functions don't need a callee
        final Object boundSelf = isTargetBound ? null : convertThisObject(self);
        final MethodHandle boundInvoker;
        if(isVarArg(originalInvoker)) {
            // First, bind callee and this without arguments
            final MethodHandle noArgBoundInvoker;
            if(isTargetBound) {
                // Don't bind either callee or this
                noArgBoundInvoker = originalInvoker;
            } else if(needsCallee()) {
                // Bind callee and this
                noArgBoundInvoker = MH.insertArguments(originalInvoker, 0, targetFn, boundSelf);
            } else {
                // Only bind this
                noArgBoundInvoker = MH.bindTo(originalInvoker, boundSelf);
            }
            // Now bind arguments
            if(args.length > 0) {
                boundInvoker = varArgBinder(noArgBoundInvoker, args);
            } else {
                boundInvoker = noArgBoundInvoker;
            }
        } else {
            final Object[] boundArgs = new Object[Math.min(originalInvoker.type().parameterCount(),
                    args.length + (isTargetBound ? 0 : (needsCallee() ? 2 : 1)))];
            int next = 0;
            if(!isTargetBound) {
                if(needsCallee()) {
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
            boundInvoker = MH.insertArguments(originalInvoker, isTargetBound ? 1 : 0, boundArgs);
        }
        if(isTargetBound) {
            return boundInvoker;
        }
        // If the target is not already bound, add a dropArguments that'll throw away the passed this
        return MH.dropArguments(boundInvoker, 0, Object.class);
    }

    private MethodHandle[] bindInvokeSpecializations(final ScriptFunction fn, final Object self, final Object[] args) {
        if(invokeSpecializations == null) {
            return null;
        }
        final MethodHandle[] boundSpecializations = new MethodHandle[invokeSpecializations.length];
        for(int i = 0; i < invokeSpecializations.length; ++i) {
            boundSpecializations[i] = bindInvokeHandle(invokeSpecializations[i], fn, self, args);
        }
        return boundSpecializations;
    }

    /**
     * Creates a constructor method handle for a bound function using the passed constructor handle.
     * @param originalConstructor the constructor handle to bind. It must be a composed constructor.
     * @param fn the function being bound
     * @param args arguments being bound
     * @return a bound constructor method handle that will bind the specified arguments. The resulting constructor never
     * needs a callee; if the original constructor needed it, it will be bound to {@code fn}. The resulting constructor
     * still takes an initial {@code this} parameter and passes it to the underlying original constructor. Finally, if
     * this script function data object has no constructor handle, null is returned.
     */
    private static MethodHandle bindConstructHandle(final MethodHandle originalConstructor, final ScriptFunction fn, final Object[] args) {
        if(originalConstructor == null) {
            return null;
        }

        // If target function is already bound, don't bother binding the callee.
        final MethodHandle calleeBoundConstructor = fn.isBoundFunction() ? originalConstructor :
            MH.dropArguments(MH.bindTo(originalConstructor, fn), 0, ScriptFunction.class);
        if(args.length == 0) {
            return calleeBoundConstructor;
        }

        if(isVarArg(calleeBoundConstructor)) {
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

    private MethodHandle[] bindConstructorSpecializations(final ScriptFunction fn, final Object[] args) {
        final MethodHandle[] ctorSpecs = getConstructSpecializations();
        if(ctorSpecs == null) {
            return null;
        }
        final MethodHandle[] boundSpecializations = new MethodHandle[ctorSpecs.length];
        for(int i = 0; i < ctorSpecs.length; ++i) {
            boundSpecializations[i] = bindConstructHandle(ctorSpecs[i], fn, args);
        }
        return boundSpecializations;
    }

    /**
     * Takes a variable-arity method and binds a variable number of arguments in it. The returned method will filter the
     * vararg array and pass a different array that prepends the bound arguments in front of the arguments passed on
     * invocation
     * @param mh the handle
     * @param args the bound arguments
     * @return the bound method handle
     */
    private static MethodHandle varArgBinder(final MethodHandle mh, final Object[] args) {
        assert args != null;
        assert args.length > 0;
        return MH.filterArguments(mh, mh.type().parameterCount() - 1, MH.bindTo(BIND_VAR_ARGS, args));
    }

    /**
     * Convert boolean flags to int.
     * @param needsCallee needs-callee flag
     * @param isVarArg var-arg flag
     * @param isStrict strict flag
     * @param isBuiltin builtin flag
     * @return int flags
     */
    private static int makeFlags(final boolean needsCallee, final boolean isVarArg, final boolean isStrict, final boolean isBuiltin, final boolean isConstructor) {
        int flags = 0;
        if (needsCallee) {
            flags |= HAS_CALLEE;
        }
        if (isVarArg) {
            flags |= IS_VARARGS;
        }
        if (isStrict) {
            flags |= IS_STRICT;
        }
        if (isBuiltin) {
            flags |= IS_BUILTIN;
        }
        if (isConstructor) {
            flags |= IS_CONSTRUCTOR;
        }

        return flags;
    }

    /**
     * Test if a methodHandle refers to a constructor.
     * @param methodHandle MethodHandle to test.
     * @return True if method is a constructor.
     */
    private static boolean isConstructor(final MethodHandle methodHandle) {
        return methodHandle.type().parameterCount() >= 1 && methodHandle.type().parameterType(0) == boolean.class;
    }

    /**
     * Heuristic to figure out if the method handle has a callee argument. If it's type is either
     * {@code (boolean, Object, ScriptFunction, ...)} or {@code (Object, ScriptFunction, ...)}, then we'll assume it has
     * a callee argument. We need this as the constructor above is not passed this information, and can't just blindly
     * assume it's false (notably, it's being invoked for creation of new scripts, and scripts have scopes, therefore
     * they also always receive a callee.
     * @param methodHandle the examined method handle
     * @return true if the method handle expects a callee, false otherwise
     */
    private static boolean needsCallee(MethodHandle methodHandle) {
        final MethodType type = methodHandle.type();
        final int len = type.parameterCount();
        if(len == 0) {
            return false;
        }
        if(type.parameterType(0) == boolean.class) {
            return len > 1 && type.parameterType(1) == ScriptFunction.class;
        }
        return type.parameterType(0) == ScriptFunction.class;
    }

    private static boolean isVarArg(MethodHandle methodHandle) {
        final MethodType type = methodHandle.type();
        return type.parameterType(type.parameterCount() - 1).isArray();
    }

    /**
     * Takes a method handle, and returns a potentially different method handle that can be used in
     * {@link ScriptFunction#invoke(Object, Object...)} or {@link ScriptFunction#construct(Object, Object...)}.
     * The returned method handle will be sure to return {@code Object}, and will have all its parameters turned into
     * {@code Object} as well, except for the following ones:
     * <ul>
     *   <li>a last parameter of type {@code Object[]} which is used for vararg functions,</li>
     *   <li>the first argument, which is forced to be {@link ScriptFunction}, in case the function receives itself
     *   (callee) as an argument.</li>
     * </ul>
     *
     * @param handle the original method handle
     * @return the new handle, conforming to the rules above.
     */
    private MethodHandle makeGenericMethod(final MethodHandle handle) {
        final MethodType type = handle.type();
        MethodType newType = type.generic();
        if (isVarArg()) {
            newType = newType.changeParameterType(type.parameterCount() - 1, Object[].class);
        }
        if (needsCallee()) {
            newType = newType.changeParameterType(0, ScriptFunction.class);
        }
        return type.equals(newType) ? handle : handle.asType(newType);
    }

    /**
     * Adapts the method handle so its return type is {@code Object}. If the handle's return type is already
     * {@code Object}, the handle is returned unchanged.
     * @param mh the handle to adapt
     * @return the adapted handle
     */
    private static MethodHandle changeReturnTypeToObject(final MethodHandle mh) {
        return MH.asType(mh, mh.type().changeReturnType(Object.class));
    }


    /**
     * If this function's method handles need a callee parameter, swap the order of first two arguments for the passed
     * method handle. If this function's method handles don't need a callee parameter, returns the original method
     * handle unchanged.
     * @param mh a method handle with order of arguments {@code (callee, this, args...)}
     * @return a method handle with order of arguments {@code (this, callee, args...)}
     */
    private MethodHandle swapCalleeAndThis(final MethodHandle mh) {
        if (!needsCallee()) {
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
    private static Object[] bindVarArgs(final Object[] array1, final Object[] array2) {
        if(array2 == null) {
            // Must clone it, as we can't allow the receiving method to alter the array
            return array1.clone();
        }
        final int l2 = array2.length;
        if(l2 == 0) {
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
