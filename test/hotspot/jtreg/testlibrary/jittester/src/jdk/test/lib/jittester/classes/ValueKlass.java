/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.test.lib.jittester.classes;

import java.util.ArrayList;

import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.visitors.Visitor;


/**
 * A simple POJO representing Value Class in the AST.
 */
public class ValueKlass extends Klass {

    /** Creates a ValueKlass AST node.
     *
     * @param thisKlass this class' TypeClass
     * @param parent parent class TypeClass
     * @param interfaces a list of interfaces the value class implements
     * @param name name for the create type
     * @param level depth level
     * @param variableDeclarations variables of the class
     * @param constructorDefinitions constructors
     * @param functionDefinitions method definitions
     * @param abstractFunctionRedefinitions redefinitions of abstract methods
     * @param overridenFunctionRedefitions overriden methods
     * @param functionDeclarations methods declarations
     * @param printVariablesBlock print variables block
     */
    public ValueKlass(TypeKlass thisKlass, TypeKlass parent,
            ArrayList<TypeKlass> interfaces, String name, int level,
            IRNode variableDeclarations, IRNode constructorDefinitions,
            IRNode functionDefinitions, IRNode abstractFunctionRedefinitions,
            IRNode overridenFunctionRedefitions, IRNode functionDeclarations,
            IRNode printVariablesBlock) {
        super(thisKlass, parent, interfaces, name, level,
                variableDeclarations, constructorDefinitions, functionDefinitions,
                abstractFunctionRedefinitions, overridenFunctionRedefitions,
                functionDeclarations, printVariablesBlock);
    }

    /** Forwards this node to a visitor.
     *
     * @param v visitor to forward to
     */
    @Override
    public<T> T accept(Visitor<T> v) {
        return v.visit(this);
    }
}

