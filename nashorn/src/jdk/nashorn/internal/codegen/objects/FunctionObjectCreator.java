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
import static jdk.nashorn.internal.codegen.Compiler.SCRIPTFUNCTION_IMPL_OBJECT;
import static jdk.nashorn.internal.codegen.CompilerConstants.ALLOCATE;
import static jdk.nashorn.internal.codegen.CompilerConstants.constructorNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.methodDescriptor;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.EnumSet;

import jdk.nashorn.internal.codegen.CodeGenerator;
import jdk.nashorn.internal.codegen.FunctionSignature;
import jdk.nashorn.internal.codegen.MethodEmitter;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunctionData;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * Analyze a function object's characteristics for appropriate code
 * generation. This generates code for the instantiation of ScriptFunctions.
 */
public class FunctionObjectCreator extends ObjectCreator {

    private final FunctionNode functionNode;

    /**
     * Constructor
     *
     * @param codegen      the code generator
     * @param functionNode the function node to turn into a ScriptFunction implementation
     */
    public FunctionObjectCreator(final CodeGenerator codegen, final FunctionNode functionNode) {
        super(codegen, new ArrayList<String>(), new ArrayList<Symbol>(), false, false);
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

        final PropertyMap map = makeMap();
        final String signature = new FunctionSignature(true, functionNode.needsCallee(), functionNode.getReturnType(), functionNode.isVarArg() ? null : functionNode.getParameters()).toString();
        final ScriptFunctionData scriptFunctionData = new ScriptFunctionData(functionNode, map);

        /*
         * Instantiate the function object
         */
        method._new(SCRIPTFUNCTION_IMPL_OBJECT).dup();
        codegen.loadConstant(scriptFunctionData);
        loadHandle(method, signature);
        if(functionNode.needsParentScope()) {
            method.loadScope();
        } else {
            method.loadNull();
        }
        method.loadHandle(getClassName(), ALLOCATE.tag(), methodDescriptor(ScriptObject.class, PropertyMap.class), EnumSet.of(HANDLE_STATIC));

        /*
         * Invoke the constructor
         */
        method.invoke(constructorNoLookup(SCRIPTFUNCTION_IMPL_OBJECT, ScriptFunctionData.class, MethodHandle.class, ScriptObject.class, MethodHandle.class));

    }
}
