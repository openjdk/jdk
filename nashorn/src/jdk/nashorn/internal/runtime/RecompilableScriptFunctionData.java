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

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import jdk.internal.dynalink.support.NameCodec;
import jdk.nashorn.internal.codegen.Compiler;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.codegen.FunctionSignature;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.scripts.JS;

/**
 * This is a subclass that represents a script function that may be regenerated,
 * for example with specialization based on call site types, or lazily generated.
 * The common denominator is that it can get new invokers during its lifespan,
 * unlike {@code FinalScriptFunctionData}
 */
public final class RecompilableScriptFunctionData extends ScriptFunctionData implements Serializable {

    /** FunctionNode with the code for this ScriptFunction */
    private transient FunctionNode functionNode;

    /** Source from which FunctionNode was parsed. */
    private transient Source source;

    /** The line number where this function begins. */
    private final int lineNumber;

    /** Allows us to retrieve the method handle for this function once the code is compiled */
    private MethodLocator methodLocator;

    /** Token of this function within the source. */
    private final long token;

    /** Allocator map from makeMap() */
    private final PropertyMap allocatorMap;

    /** Code installer used for all further recompilation/specialization of this ScriptFunction */
    private transient CodeInstaller<ScriptEnvironment> installer;

    /** Name of class where allocator function resides */
    private final String allocatorClassName;

    /** lazily generated allocator */
    private transient MethodHandle allocator;

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /**
     * Used for specialization based on runtime arguments. Whenever we specialize on
     * callsite parameter types at runtime, we need to use a parameter type guard to
     * ensure that the specialized version of the script function continues to be
     * applicable for a particular callsite.
     */
    private static final MethodHandle PARAM_TYPE_GUARD = findOwnMH("paramTypeGuard", boolean.class, Type[].class,  Object[].class);

    /**
     * It is usually a good gamble whever we detect a runtime callsite with a double
     * (or java.lang.Number instance) to specialize the parameter to an integer, if the
     * parameter in question can be represented as one. The double typically only exists
     * because the compiler doesn't know any better than "a number type" and conservatively
     * picks doubles when it can't prove that an integer addition wouldn't overflow.
     */
    private static final MethodHandle ENSURE_INT = findOwnMH("ensureInt", int.class, Object.class);

    private static final long serialVersionUID = 4914839316174633726L;

    /**
     * Constructor - public as scripts use it
     *
     * @param functionNode       functionNode that represents this function code
     * @param installer          installer for code regeneration versions of this function
     * @param allocatorClassName name of our allocator class, will be looked up dynamically if used as a constructor
     * @param allocatorMap       allocator map to seed instances with, when constructing
     */
    public RecompilableScriptFunctionData(final FunctionNode functionNode, final CodeInstaller<ScriptEnvironment> installer, final String allocatorClassName, final PropertyMap allocatorMap) {
        super(functionName(functionNode),
              functionNode.getParameters().size(),
              getFlags(functionNode));
        this.functionNode       = functionNode;
        this.source             = functionNode.getSource();
        this.lineNumber         = functionNode.getLineNumber();
        this.token              = tokenFor(functionNode);
        this.installer          = installer;
        this.allocatorClassName = allocatorClassName;
        this.allocatorMap       = allocatorMap;
        if (!functionNode.isLazy()) {
            methodLocator = new MethodLocator(functionNode);
        }
    }

    @Override
    String toSource() {
        if (source != null && token != 0) {
            return source.getString(Token.descPosition(token), Token.descLength(token));
        }

        return "function " + (name == null ? "" : name) + "() { [native code] }";
    }

    public void setCodeAndSource(final Map<String, Class<?>> code, final Source source) {
        this.source = source;
        if (methodLocator != null) {
            methodLocator.setClass(code.get(methodLocator.getClassName()));
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        if (source != null) {
            sb.append(source.getName()).append(':').append(lineNumber).append(' ');
        }

        return sb.toString() + super.toString();
    }

    private static String functionName(final FunctionNode fn) {
        if (fn.isAnonymous()) {
            return "";
        } else {
            final FunctionNode.Kind kind = fn.getKind();
            if (kind == FunctionNode.Kind.GETTER || kind == FunctionNode.Kind.SETTER) {
                final String name = NameCodec.decode(fn.getIdent().getName());
                return name.substring(4); // 4 is "get " or "set "
            } else {
                return fn.getIdent().getName();
            }
        }
    }

    private static long tokenFor(final FunctionNode fn) {
        final int  position   = Token.descPosition(fn.getFirstToken());
        final int  length     = Token.descPosition(fn.getLastToken()) - position + Token.descLength(fn.getLastToken());

        return Token.toDesc(TokenType.FUNCTION, position, length);
    }

    private static int getFlags(final FunctionNode functionNode) {
        int flags = IS_CONSTRUCTOR;
        if (functionNode.isStrict()) {
            flags |= IS_STRICT;
        }
        if (functionNode.needsCallee()) {
            flags |= NEEDS_CALLEE;
        }
        if (functionNode.usesThis() || functionNode.hasEval()) {
            flags |= USES_THIS;
        }
        return flags;
    }

    @Override
    ScriptObject allocate(final PropertyMap map) {
        try {
            ensureHasAllocator(); //if allocatorClass name is set to null (e.g. for bound functions) we don't even try
            return allocator == null ? null : (ScriptObject)allocator.invokeExact(map);
        } catch (final RuntimeException | Error e) {
            throw e;
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void ensureHasAllocator() throws ClassNotFoundException {
        if (allocator == null && allocatorClassName != null) {
            this.allocator = MH.findStatic(LOOKUP, Context.forStructureClass(allocatorClassName), CompilerConstants.ALLOCATE.symbolName(), MH.type(ScriptObject.class, PropertyMap.class));
        }
    }

    @Override
    PropertyMap getAllocatorMap() {
        return allocatorMap;
    }


    @Override
    protected void ensureCompiled() {
        if (functionNode != null && functionNode.isLazy()) {
            Compiler.LOG.info("Trampoline hit: need to do lazy compilation of '", functionNode.getName(), "'");
            final Compiler compiler = new Compiler(installer);
            functionNode = compiler.compile(functionNode);
            assert !functionNode.isLazy();
            compiler.install(functionNode);
            methodLocator = new MethodLocator(functionNode);
            flags = getFlags(functionNode);
        }

        if (functionNode != null) {
            methodLocator.setClass(functionNode.getCompileUnit().getCode());
        }
    }

    @Override
    protected synchronized void ensureCodeGenerated() {
        if (!code.isEmpty()) {
            return; // nothing to do, we have code, at least some.
        }

        ensureCompiled();

        /*
         * We can't get to this program point unless we have bytecode, either from
         * eager compilation or from running a lazy compile on the lines above
         */

        assert functionNode == null || functionNode.hasState(CompilationState.EMITTED) :
                    functionNode.getName() + " " + functionNode.getState() + " " + Debug.id(functionNode);

        // code exists - look it up and add it into the automatically sorted invoker list
        addCode(functionNode);

        if (functionNode != null && !functionNode.canSpecialize()) {
            // allow GC to claim IR stuff that is not needed anymore
            functionNode = null;
            installer = null;
        }
    }

    private MethodHandle addCode(final FunctionNode fn) {
        return addCode(fn, null, null, null);
    }

    private MethodHandle addCode(final FunctionNode fn, final MethodType runtimeType, final MethodHandle guard, final MethodHandle fallback) {
        assert methodLocator != null;
        MethodHandle target = methodLocator.getMethodHandle();
        final MethodType targetType = methodLocator.getMethodType();

        /*
         * For any integer argument. a double that is representable as an integer is OK.
         * otherwise the guard would have failed. in that case introduce a filter that
         * casts the double to an integer, which we know will preserve all precision.
         */
        for (int i = 0; i < targetType.parameterCount(); i++) {
            if (targetType.parameterType(i) == int.class) {
                //representable as int
                target = MH.filterArguments(target, i, ENSURE_INT);
            }
        }

        MethodHandle mh = target;
        if (guard != null) {
            mh = MH.guardWithTest(MH.asCollector(guard, Object[].class, target.type().parameterCount()), MH.asType(target, fallback.type()), fallback);
        }

        final CompiledFunction cf = new CompiledFunction(runtimeType == null ? targetType : runtimeType, mh);
        code.add(cf);

        return cf.getInvoker();
    }

    private static Type runtimeType(final Object arg) {
        if (arg == null) {
            return Type.OBJECT;
        }

        final Class<?> clazz = arg.getClass();
        assert !clazz.isPrimitive() : "always boxed";
        if (clazz == Double.class) {
            return JSType.isRepresentableAsInt((double)arg) ? Type.INT : Type.NUMBER;
        } else if (clazz == Integer.class) {
            return Type.INT;
        } else if (clazz == Long.class) {
            return Type.LONG;
        } else if (clazz == String.class) {
            return Type.STRING;
        }
        return Type.OBJECT;
    }

    private static boolean canCoerce(final Object arg, final Type type) {
        Type argType = runtimeType(arg);
        if (Type.widest(argType, type) == type || arg == ScriptRuntime.UNDEFINED) {
            return true;
        }
        System.err.println(arg + " does not fit in "+ argType + " " + type + " " + arg.getClass());
        new Throwable().printStackTrace();
        return false;
    }

    @SuppressWarnings("unused")
    private static boolean paramTypeGuard(final Type[] paramTypes, final Object... args) {
        final int length = args.length;
        assert args.length >= paramTypes.length;

        //i==start, skip the this, callee params etc
        int start = args.length - paramTypes.length;
        for (int i = start; i < args.length; i++) {
            final Object arg = args[i];
            if (!canCoerce(arg, paramTypes[i - start])) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unused")
    private static int ensureInt(final Object arg) {
        if (arg instanceof Number) {
            return ((Number)arg).intValue();
        } else if (arg instanceof Undefined) {
            return 0;
        }
        throw new AssertionError(arg);
    }

    /**
     * Given the runtime callsite args, compute a method type that is equivalent to what
     * was passed - this is typically a lot more specific that what the compiler has been
     * able to deduce
     * @param callSiteType callsite type for the compiled callsite target
     * @param args runtime arguments to the compiled callsite target
     * @return adjusted method type, narrowed as to conform to runtime callsite type instead
     */
    private static MethodType runtimeType(final MethodType callSiteType, final Object[] args) {
        if (args == null) {
            //for example bound, or otherwise runtime arguments to callsite unavailable, then
            //do not change the type
            return callSiteType;
        }
        final Class<?>[] paramTypes = new Class<?>[callSiteType.parameterCount()];
        final int        start      = args.length - callSiteType.parameterCount();
        for (int i = start; i < args.length; i++) {
            paramTypes[i - start] = runtimeType(args[i]).getTypeClass();
        }
        return MH.type(callSiteType.returnType(), paramTypes);
    }

    private static ArrayList<Type> runtimeType(final MethodType mt) {
        final ArrayList<Type> type = new ArrayList<>();
        for (int i = 0; i < mt.parameterCount(); i++) {
            type.add(Type.typeFor(mt.parameterType(i)));
        }
        return type;
    }

    @Override
    synchronized MethodHandle getBestInvoker(final MethodType callSiteType, final Object[] args) {
        final MethodType runtimeType = runtimeType(callSiteType, args);
        assert runtimeType.parameterCount() == callSiteType.parameterCount();

        final MethodHandle mh = super.getBestInvoker(runtimeType, args);

        /*
         * Not all functions can be specialized, for example, if we deemed memory
         * footprint too large to store a parse snapshot, or if it is meaningless
         * to do so, such as e.g. for runScript
         */
        if (functionNode == null || !functionNode.canSpecialize()) {
            return mh;
        }

        /*
         * Check if best invoker is equally specific or more specific than runtime
         * type. In that case, we don't need further specialization, but can use
         * whatever we have already. We know that it will match callSiteType, or it
         * would not have been returned from getBestInvoker
         */
        if (!code.isLessSpecificThan(runtimeType)) {
            return mh;
        }

        int i;
        final FunctionNode snapshot = functionNode.getSnapshot();
        assert snapshot != null;

        /*
         * Create a list of the arg types that the compiler knows about
         * typically, the runtime args are a lot more specific, and we should aggressively
         * try to use those whenever possible
         * We WILL try to make an aggressive guess as possible, and add guards if needed.
         * For example, if the compiler can deduce that we have a number type, but the runtime
         * passes and int, we might still want to keep it an int, and the gamble to
         * check that whatever is passed is int representable usually pays off
         * If the compiler only knows that a parameter is an "Object", it is still worth
         * it to try to specialize it by looking at the runtime arg.
         */
        final LinkedList<Type> compileTimeArgs = new LinkedList<>();
        for (i = callSiteType.parameterCount() - 1; i >= 0 && compileTimeArgs.size() < snapshot.getParameters().size(); i--) {
            compileTimeArgs.addFirst(Type.typeFor(callSiteType.parameterType(i)));
        }

        /*
         * The classes known at compile time are a safe to generate as primitives without parameter guards
         * But the classes known at runtime (if more specific than compile time types) are safe to generate as primitives
         * IFF there are parameter guards
         */
        MethodHandle guard = null;
        final ArrayList<Type> runtimeParamTypes = runtimeType(runtimeType);
        while (runtimeParamTypes.size() > functionNode.getParameters().size()) {
            runtimeParamTypes.remove(0);
        }
        for (i = 0; i < compileTimeArgs.size(); i++) {
            final Type rparam = Type.typeFor(runtimeType.parameterType(i));
            final Type cparam = compileTimeArgs.get(i);

            if (cparam.isObject() && !rparam.isObject()) {
                //check that the runtime object is still coercible to the runtime type, because compiler can't prove it's always primitive
                if (guard == null) {
                    guard = MH.insertArguments(PARAM_TYPE_GUARD, 0, (Object)runtimeParamTypes.toArray(new Type[runtimeParamTypes.size()]));
                }
            }
        }

        Compiler.LOG.info("Callsite specialized ", name, " runtimeType=", runtimeType, " parameters=", snapshot.getParameters(), " args=", Arrays.asList(args));

        assert snapshot != functionNode;

        final Compiler compiler = new Compiler(installer);

        final FunctionNode compiledSnapshot = compiler.compile(
            snapshot.setHints(
                null,
                new Compiler.Hints(runtimeParamTypes.toArray(new Type[runtimeParamTypes.size()]))));

        /*
         * No matter how narrow your types were, they can never be narrower than Attr during recompile made them. I.e. you
         * can put an int into the function here, if you see it as a runtime type, but if the function uses a multiplication
         * on it, it will still need to be a double. At least until we have overflow checks. Similarly, if an int is
         * passed but it is used as a string, it makes no sense to make the parameter narrower than Object. At least until
         * the "different types for one symbol in difference places" work is done
         */
        compiler.install(compiledSnapshot);

        return addCode(compiledSnapshot, runtimeType, guard, mh);
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), RecompilableScriptFunctionData.class, name, MH.type(rtype, types));
    }

    /**
     * Helper class that allows us to retrieve the method handle for this function once it has been generated.
     */
    private static class MethodLocator implements Serializable {
        private transient Class<?> clazz;
        private final String className;
        private final String methodName;
        private final MethodType methodType;

        private static final long serialVersionUID = -5420835725902966692L;

        MethodLocator(final FunctionNode functionNode) {
            this.className  = functionNode.getCompileUnit().getUnitClassName();
            this.methodName = functionNode.getName();
            this.methodType = new FunctionSignature(functionNode).getMethodType();

            assert className != null;
            assert methodName != null;
        }

        void setClass(final Class<?> clazz) {
            if (!JS.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException();
            }
            this.clazz = clazz;
        }

        String getClassName() {
            return className;
        }

        MethodType getMethodType() {
            return methodType;
        }

        MethodHandle getMethodHandle() {
            return MH.findStatic(LOOKUP, clazz, methodName, methodType);
        }
    }

}

