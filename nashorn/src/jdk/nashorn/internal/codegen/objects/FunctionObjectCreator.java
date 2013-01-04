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

package jdk.nashorn.internal.codegen.objects;

import static jdk.nashorn.internal.codegen.ClassEmitter.Flag.HANDLE_STATIC;
import static jdk.nashorn.internal.codegen.Compiler.SCRIPTOBJECT_IMPL_OBJECT;
import static jdk.nashorn.internal.codegen.CompilerConstants.ALLOCATE;
import static jdk.nashorn.internal.codegen.CompilerConstants.SOURCE;
import static jdk.nashorn.internal.codegen.CompilerConstants.constructorNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.methodDescriptor;

import java.lang.invoke.MethodHandle;
import java.util.EnumSet;
import java.util.List;
import jdk.nashorn.internal.codegen.CodeGenerator;
import jdk.nashorn.internal.codegen.FunctionSignature;
import jdk.nashorn.internal.codegen.MethodEmitter;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.Source;

/**
 * Analyze a function object's characteristics for appropriate code
 * generation. This generates code for the instantiation of ScriptFunction:s
 */
public class FunctionObjectCreator extends ObjectCreator {

    private final FunctionNode functionNode;

    /**
     * Constructor
     *
     * @param codegen      the code generator
     * @param functionNode the function node to turn into a ScriptFunction implementation
     * @param keys         initial keys for the object map
     * @param symbols      corresponding initial symbols for object map
     */
    public FunctionObjectCreator(final CodeGenerator codegen, final FunctionNode functionNode, final List<String> keys, final List<Symbol> symbols) {
        super(codegen, keys, symbols, false, false);
        this.functionNode = functionNode;
    }

    private void loadHandle(final MethodEmitter method, final String signature) {
    method.loadHandle(functionNode.getCompileUnit().getUnitClassName(), functionNode.getName(), signature, EnumSet.of(HANDLE_STATIC)); // function
    }

    /**
     * Emit code for creating the object
     *
     * @param method the method emitter
     */
    @Override
    public void makeObject(final MethodEmitter method) {
        makeMap();

        final IdentNode identNode  = functionNode.getIdent();
        final String    signature  = new FunctionSignature(true, functionNode.needsCallee(), functionNode.getReturnType(), functionNode.isVarArg() ? null : functionNode.getParameters()).toString();
        final long      firstToken = functionNode.getFirstToken();
        final long      lastToken  = functionNode.getLastToken();
        final int       position   = Token.descPosition(firstToken);
        final int       length     = Token.descPosition(lastToken) - position + Token.descLength(lastToken);
        final long      token      = Token.toDesc(TokenType.FUNCTION, position, length);

        /*
         * Instantiate the function object, must be referred to by name as
         * class is not available at compile time
         */
        method._new(SCRIPTOBJECT_IMPL_OBJECT).dup();
        method.load(functionNode.isAnonymous() ? "" : identNode.getName());
        loadHandle(method, signature);
        method.loadScope();
        method.getStatic(compileUnit.getUnitClassName(), SOURCE.tag(), SOURCE.descriptor());
        method.load(token);
        method.loadHandle(getClassName(), ALLOCATE.tag(), methodDescriptor(ScriptObject.class, PropertyMap.class), EnumSet.of(HANDLE_STATIC));

        /*
         * Emit code for the correct property map for the object
         */
        loadMap(method);

        /*
         * Invoke the constructor
         */
        method.load(functionNode.needsCallee());
        method.load(functionNode.isStrictMode());
        method.invoke(constructorNoLookup(SCRIPTOBJECT_IMPL_OBJECT,
                    String.class,
                    MethodHandle.class,
                    ScriptObject.class,
                    Source.class,
                    long.class,
                    MethodHandle.class,
                    PropertyMap.class,
                    boolean.class,
                    boolean.class));


        if (functionNode.isVarArg()) {
            method.dup();
            method.load(functionNode.getParameters().size());
            method.invoke(ScriptFunction.SET_ARITY);
        }
    }
}
