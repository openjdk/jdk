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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedList;

import jdk.nashorn.internal.codegen.Compiler;
import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.codegen.FunctionSignature;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;

/**
 * This is a subclass that represents a script function that may be regenerated,
 * for example with specialization based on call site types, or lazily generated.
 * The common denominator is that it can get new invokers during its lifespan,
 * unlike {@link FinalScriptFunctionData}
 */
public final class RecompilableScriptFunctionData extends ScriptFunctionData {

    private FunctionNode functionNode;
    private final PropertyMap  allocatorMap;
    private final CodeInstaller<ScriptEnvironment> installer;
    private final String allocatorClassName;

    /** lazily generated allocator */
    private MethodHandle allocator;

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /**
     * Constructor - public as scripts use it
     *
     * @param functionNode       functionNode that represents this function code
     * @param installer          installer for code regeneration versions of this function
     * @param allocatorClassName name of our allocator class, will be looked up dynamically if used as a constructor
     * @param allocatorMap       allocator map to seed instances with, when constructing
     */
    public RecompilableScriptFunctionData(final FunctionNode functionNode, final CodeInstaller<ScriptEnvironment> installer, final String allocatorClassName, final PropertyMap allocatorMap) {
        super(functionNode.isAnonymous() ?
                "" :
                functionNode.getIdent().getName(),
              functionNode.getParameters().size(),
              functionNode.isStrict(),
              false,
              true);

        this.functionNode       = functionNode;
        this.installer          = installer;
        this.allocatorClassName = allocatorClassName;
        this.allocatorMap       = allocatorMap;
    }

    @Override
    String toSource() {
        final Source source = functionNode.getSource();
        final long   token  = tokenFor(functionNode);

        if (source != null && token != 0) {
            return source.getString(Token.descPosition(token), Token.descLength(token));
        }

        return "function " + (name == null ? "" : name) + "() { [native code] }";
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        final Source source = functionNode.getSource();
        final long   token  = tokenFor(functionNode);

        if (source != null) {
            sb.append(source.getName())
                .append(':')
                .append(source.getLine(Token.descPosition(token)))
                .append(' ');
        }

        return sb.toString() + super.toString();
    }

    private static long tokenFor(final FunctionNode fn) {
        final int  position   = Token.descPosition(fn.getFirstToken());
        final int  length     = Token.descPosition(fn.getLastToken()) - position + Token.descLength(fn.getLastToken());

        return Token.toDesc(TokenType.FUNCTION, position, length);
    }

    @Override
    ScriptObject allocate() {
        try {
            ensureHasAllocator(); //if allocatorClass name is set to null (e.g. for bound functions) we don't even try
            return allocator == null ? null : (ScriptObject)allocator.invokeExact(allocatorMap);
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
    protected void ensureCodeGenerated() {
         if (!code.isEmpty()) {
             return; // nothing to do, we have code, at least some.
         }

         // check if function node is lazy, need to compile it.
         // note that currently function cloning is not working completely, which
         // means that the compiler will mutate the function node it has been given
         // once it has been compiled, it cannot be recompiled. This means that
         // lazy compilation works (not compiled yet) but e.g. specializations won't
         // until the copy-on-write changes for IR are in, making cloning meaningless.
         // therefore, currently method specialization is disabled. TODO

         if (functionNode.isLazy()) {
             Compiler.LOG.info("Trampoline hit: need to do lazy compilation of '", functionNode.getName(), "'");
             final Compiler compiler = new Compiler(installer);
             functionNode = compiler.compile(functionNode);
             assert !functionNode.isLazy();
             compiler.install(functionNode);

             // we don't need to update any flags - varArgs and needsCallee are instrincic
             // in the function world we need to get a destination node from the compile instead
             // and replace it with our function node. TODO
         }

         // we can't get here unless we have bytecode, either from eager compilation or from
         // running a lazy compile on the lines above

         assert functionNode.hasState(CompilationState.EMITTED) : functionNode.getName() + " " + functionNode.getState() + " " + Debug.id(functionNode);

         // code exists - look it up and add it into the automatically sorted invoker list
         addCode(functionNode, null, null);
    }

    private MethodHandle addCode(final FunctionNode fn, final MethodHandle guard, final MethodHandle fallback) {
        final MethodHandle target =
            MH.findStatic(
                    LOOKUP,
                    fn.getCompileUnit().getCode(),
                    fn.getName(),
                    new FunctionSignature(fn).
                        getMethodType());
        MethodHandle mh = target;
        if (guard != null) {
            try {
                mh = MH.guardWithTest(MH.asCollector(guard, Object[].class, target.type().parameterCount()), MH.asType(target, fallback.type()), fallback);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        final CompiledFunction cf = new CompiledFunction(mh);
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

    @SuppressWarnings("unused")
    private static boolean paramTypeGuard(final Type[] compileTimeTypes, final Type[] runtimeTypes, Object... args) {
        //System.err.println("Param type guard " + Arrays.asList(args));
        return false;
    }

    private static final MethodHandle PARAM_TYPE_GUARD = findOwnMH("paramTypeGuard", boolean.class, Type[].class, Type[].class, Object[].class);

    @Override
    MethodHandle getBestInvoker(final MethodType callSiteType, final Object[] args) {
        final MethodHandle mh = super.getBestInvoker(callSiteType, args);

        if (!functionNode.canSpecialize() || !code.isLessSpecificThan(callSiteType)) {
            return mh;
        }

        final FunctionNode snapshot = functionNode.getSnapshot();
        if (snapshot == null) {
            return mh;
        }

        int i;

        //classes known at runtime
        final LinkedList<Type> runtimeArgs = new LinkedList<>();
        for (i = args.length - 1; i >= args.length - snapshot.getParameters().size(); i--) {
            runtimeArgs.addLast(runtimeType(args[i]));
        }

        //classes known at compile time
        final LinkedList<Type> compileTimeArgs = new LinkedList<>();
        for (i = callSiteType.parameterCount() - 1; i >= 0 && compileTimeArgs.size() < snapshot.getParameters().size(); i--) {
            compileTimeArgs.addLast(Type.typeFor(callSiteType.parameterType(i)));
        }

        //the classes known at compile time are a safe to generate as primitives without parameter guards
        //the classes known at runtime are safe to generate as primitives IFF there are parameter guards
        MethodHandle guard = null;
        for (i = 0; i < compileTimeArgs.size(); i++) {
            final Type runtimeType = runtimeArgs.get(i);
            final Type compileType = compileTimeArgs.get(i);

            if (compileType.isObject() && !runtimeType.isObject()) {
                if (guard == null) {
                    guard = PARAM_TYPE_GUARD;
                    guard = MH.insertArguments(guard, 0, compileTimeArgs.toArray(new Type[compileTimeArgs.size()]), runtimeArgs.toArray(new Type[runtimeArgs.size()]));
                }
            }
        }

        //System.err.println("Specialized " + name + " " + runtimeArgs + " known=" + compileTimeArgs);

        assert snapshot != null;
        assert snapshot != functionNode;

        final Compiler compiler = new Compiler(installer);
        final FunctionNode compiledSnapshot = compiler.compile(snapshot.setHints(null, new Compiler.Hints(compileTimeArgs.toArray(new Type[compileTimeArgs.size()]))));

        compiler.install(compiledSnapshot);

        final MethodHandle nmh = addCode(compiledSnapshot, guard, mh);

        return nmh;
    }

    private static MethodHandle findOwnMH(final String name, final Class<?> rtype, final Class<?>... types) {
        return MH.findStatic(MethodHandles.lookup(), RecompilableScriptFunctionData.class, name, MH.type(rtype, types));
    }

}

