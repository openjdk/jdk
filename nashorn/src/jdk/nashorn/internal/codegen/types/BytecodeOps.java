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

package jdk.nashorn.internal.codegen.types;

import jdk.internal.org.objectweb.asm.MethodVisitor;


/**
 * Interface for byte code generation for all runtime types. Each
 * type implements this interface and provides the type specific
 * operations to do the generic things described herein.
 *
 * The bytecode ops are coupled to a MethodVisitor from ASM for
 * byte code generation. They know nothing about our MethodGenerator,
 * which is the abstraction for working with Nashorn JS types
 * For exmaple, anything like "two or one slots" for a type, which
 * is represented in bytecode and ASM, is abstracted away in the
 * MethodGenerator. There you just say "dup" or "store".
 *
 * @see Type
 * @see MethodVisitor
 */
interface BytecodeOps {

    /**
     * Duplicate top entry of stack. If a too large depth is
     * given, so that there are no possible bytecode instructions
     * available to generate the dup sequence, null is returned.
     *
     * @param method  method visitor
     * @param depth   how far should the copy be pushed down
     *
     * @return        the type at the top of the stack or null
     */
    Type dup(MethodVisitor method, int depth);

    /**
     * Pop an entry of this type from the top of the bytecode
     * stack. This works regardless of what category this type
     * is
     *
     * @param method  method visitor
     *
     * @return the popped type
     */
    Type pop(MethodVisitor method);

    /**
     * Swap this type with the bytecode stack with the one below
     * Generate appropriate code no matter the categories of the
     * two types
     *
     * @param method  method visitor
     * @param other   the type below this one on the stack
     *
     * @return        the other type
     */
    Type swap(MethodVisitor method, Type other);

    /**
     * Pop two values on top of the stack and add the
     * first to the second, pushing the result on the stack
     *
     * @param method  method visitor
     * @param programPoint program point id
     * @return result type
     */
    Type add(MethodVisitor method, int programPoint);

    /**
     * Load a variable from a local slot to the stack
     *
     * @param method method visitor
     * @param slot   the slot to load
     *
     * @return       the type that was loaded
     */
    Type load(MethodVisitor method, int slot);

    /**
     * Store a variable from the stack to a local slot
     *
     * @param method  method visitor
     * @param slot    the slot to store to
     */
    void store(MethodVisitor method, int slot);

    /**
     * Load a constant to the stack.
     *
     * @param method  method visitor
     * @param c       the value of the constant
     *
     * @return        the type at the top of the stack after load
     */
    Type ldc(MethodVisitor method, Object c);

    /**
     * Load the "undefined" value to the stack. Note that
     * there may be different representations of this for
     * e.g. doubles and objects. Abstraction removes this
     *
     * @param  method  method visitor.
     *
     * @return the undefined type at the top of the stack
     */
    Type loadUndefined(MethodVisitor method);

    /**
     * Load the "forced initializer" value to the stack, used to ensure that a local variable has a value when it is
     * read by the unwarranted optimism catch block.
     *
     * @param  method  method visitor.
     *
     * @return the forced initialization type at the top of the stack
     */
    Type loadForcedInitializer(MethodVisitor method);


    /**
     * Load the "empty" value to the stack.
     *
     * @param  method  method visitor.
     * @return the undefined type at the top of the stack
     */
    Type loadEmpty(MethodVisitor method);

    /**
     * Generate code that pops and casts the element on top of the
     * stack to another type, given as parameter
     *
     * @param method  method visitor
     * @param to      the type to cast to
     *
     * @return the to type
     */
    Type convert(MethodVisitor method, Type to);

    /**
     * Return the parameter on top of the stack
     * from a method
     *
     * @param method the method visitor
     */
    void _return(MethodVisitor method);

}
