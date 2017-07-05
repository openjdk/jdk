/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

package com.sun.classanalyzer;

import com.sun.classanalyzer.Klass.Method;

import com.sun.tools.classfile.*;
import com.sun.tools.classfile.Instruction.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author Mandy Chung
 */
public class CodeAttributeParser {
    private final ClassFileParser cfparser;
    private final ConstantPool cpool;
    private final ConstantPoolParser constantPoolParser;


    static final Map<String, Set<Method>> runtimeReferences =
            new HashMap<String, Set<Method>>();


    CodeAttributeParser(ClassFileParser parser) {
        this.cfparser = parser;
        this.cpool = cfparser.classfile.constant_pool;
        this.constantPoolParser = cfparser.constantPoolParser;
    }

    static boolean parseCodeAttribute = false; // by default don't parse code attribute
    static void setParseCodeAttribute(boolean newValue) {
        parseCodeAttribute = newValue;
    }

    void parse(Code_attribute attr, Klass.Method method) {
        if (!parseCodeAttribute) {
            return;
        }

        for (Instruction instr : attr.getInstructions()) {
            try {
                instr.accept(instructionVisitor, method);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new RuntimeException("error at or after byte " + instr.getPC());
            }

        }

        if (attr.exception_table_langth > 0) {
            for (int i = 0; i <
                    attr.exception_table.length; i++) {
                Code_attribute.Exception_data handler = attr.exception_table[i];
                int catch_type = handler.catch_type;
                if (catch_type > 0) {
                    addMethodReference(catch_type, method);
                }

            }
        }

    }


    private void addMethodReference(int index, Klass.Method m) {
        String method = constantPoolParser.getMethodName(index);

        if (method != null &&
               (method.equals("java.lang.Class.forName") ||
                method.equals("java.lang.Class.loadClass") ||
                method.startsWith("java.util.ServiceLoader.load") ||
                method.equals("sun.misc.Service.providers"))) {
            Set<Method> refs = runtimeReferences.get(method);
            if (refs == null) {
                refs = new TreeSet<Method>();
                runtimeReferences.put(method, refs);
            }
            refs.add(m);
        }
    }

    Instruction.KindVisitor<Void, Klass.Method> instructionVisitor =
            new Instruction.KindVisitor<Void, Klass.Method>() {

                public Void visitNoOperands(Instruction instr, Klass.Method m) {
                    return null;
                }

                public Void visitArrayType(Instruction instr, TypeKind kind, Klass.Method m) {
                    return null;
                }

                public Void visitBranch(Instruction instr, int offset, Klass.Method m) {
                    return null;
                }

                public Void visitConstantPoolRef(Instruction instr, int index, Klass.Method m) {
                    addMethodReference(index, m);
                    return null;
                }

                public Void visitConstantPoolRefAndValue(Instruction instr, int index, int value, Klass.Method m) {
                    addMethodReference(index, m);
                    return null;
                }

                public Void visitLocal(Instruction instr, int index, Klass.Method m) {
                    return null;
                }

                public Void visitLocalAndValue(Instruction instr, int index, int value, Klass.Method m) {
                    return null;
                }

                public Void visitLookupSwitch(Instruction instr, int default_, int npairs, int[] matches, int[] offsets, Klass.Method m) {
                    return null;
                }

                public Void visitTableSwitch(Instruction instr, int default_, int low, int high, int[] offsets, Klass.Method m) {
                    return null;
                }

                public Void visitValue(Instruction instr, int value, Klass.Method m) {
                    return null;
                }

                public Void visitUnknown(Instruction instr, Klass.Method m) {
                    return null;
                }
            };
}
