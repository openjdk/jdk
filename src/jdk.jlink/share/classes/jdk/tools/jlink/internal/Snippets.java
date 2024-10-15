/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal;

import java.lang.classfile.ClassBuilder;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_Set;
import java.lang.constant.MethodTypeDesc;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiConsumer;

public class Snippets {
    /**
     * Return a snippet builder that loads an enum onto the operand stack using
     * the enum name static final field
     */
    public static <T extends Enum<T>> BiConsumer<CodeBuilder, T> getEnumLoader(ClassDesc enumClassDesc) {
        return (cob, element) -> cob.getstatic(enumClassDesc, element.name(), enumClassDesc);
    }

    /**
     * Generate bytecode to create an array and load onto the operand stack.
     * Effectively like following pseudo code:
     *   new T[] { elements }
     *
     * @param cob The code builder to add the snipper
     * @param elementType The class descriptor of the element type T
     * @param elements The elements to be in the array
     * @param elementLoader A snippet generator to load an element T onto the operand stack.
     */
    public static <T> void loadArray(CodeBuilder cob,
                                     ClassDesc elementType,
                                     Collection<T> elements,
                                     BiConsumer<CodeBuilder, T> elementLoader) {
        cob.loadConstant(elements.size())
           .anewarray(elementType);
        int arrayIndex = 0;
        for (T t : elements) {
            cob.dup()    // arrayref
               .loadConstant(arrayIndex);
            elementLoader.accept(cob, t);  // value
            cob.aastore();
            arrayIndex++;
        }
    }

    /**
     * Generates bytecode to load a set onto the operand stack.
     * Effectively like following pseudo code:
     *   Set.of(elements)
     * @param cob The code builder to add the snippet
     * @param elements The set to be created
     * @param elementLoader Snippet generator to load an element onto the operand stack
     */
    public static <T> void loadImmutableSet(CodeBuilder cob,
                                            Collection<T> elements,
                                            BiConsumer<CodeBuilder, T> elementLoader) {
        if (elements.size() <= 10) {
            // call Set.of(e1, e2, ...)
            for (T t : elements) {
                elementLoader.accept(cob, t);
            }
            var mtdArgs = new ClassDesc[elements.size()];
            Arrays.fill(mtdArgs, CD_Object);
            cob.invokestatic(CD_Set, "of", MethodTypeDesc.of(CD_Set, mtdArgs), true);
        } else {
            // call Set.of(E... elements)
            loadArray(cob, CD_Object, elements, elementLoader);
            cob.invokestatic(CD_Set, "of", MethodTypeDesc.of(CD_Set, CD_Object.arrayType()), true);
        }
    }

    // Generate a method with pseudo code looks like this
    // static Set<T> methodName() {
    //     return Set.of(elements);
    // }
    public static <T> void genImmutableSetProvider(ClassBuilder clb,
                                                   String methodName,
                                                   Collection<T> elements,
                                                   BiConsumer<CodeBuilder, T> elementLoader) {
        clb.withMethodBody(
                methodName,
                MethodTypeDesc.of(CD_Set),
                ACC_STATIC,
                cob -> {
                    loadImmutableSet(cob, elements, elementLoader);
                    cob.areturn();
                });
    }

    // Generate a method with pseudo code looks like this
    // static T[] methodName() {
    //     return new T[] { elements };
    // }
    public static <T> void genArrayProvider(ClassBuilder clb,
                                            String methodName,
                                            ClassDesc elementType,
                                            Collection<T> elements,
                                            BiConsumer<CodeBuilder, T> elementLoader) {
        clb.withMethodBody(
                methodName,
                MethodTypeDesc.of(elementType.arrayType()),
                ACC_STATIC,
                cob -> {
                    loadArray(cob, elementType, elements, elementLoader);
                    cob.areturn();
                });
    }
}