/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import com.sun.tools.classfile.*;
import static com.sun.tools.classfile.ConstantPool.*;
import com.sun.tools.classfile.Instruction.TypeKind;

/**
 * MethodFinder utility class to find references to the given methods.
 */
public abstract class MethodFinder {
    final List<String> methods;
    public MethodFinder(String... methods) {
        this.methods = Arrays.asList(methods);
    }

    /**
     * A callback method will be invoked when a method referencing
     * any of the lookup methods.
     *
     * @param cf ClassFile
     * @param m Method
     * @param refs Set of constant pool indices that reference the methods
     *             matching the given lookup method names
     */
    public abstract void referenceFound(ClassFile cf, Method m, Set<Integer> refs)
            throws ConstantPoolException;

    public String parse(ClassFile cf) throws ConstantPoolException {
        List<Integer> cprefs = new ArrayList<Integer>();
        int index = 1;
        for (ConstantPool.CPInfo cpInfo : cf.constant_pool.entries()) {
            if (cpInfo.accept(cpVisitor, null)) {
                cprefs.add(index);
            }
            index += cpInfo.size();
        }

        if (!cprefs.isEmpty()) {
            for (Method m : cf.methods) {
                Set<Integer> refs = new HashSet<Integer>();
                Code_attribute c_attr = (Code_attribute) m.attributes.get(Attribute.Code);
                if (c_attr != null) {
                    for (Instruction instr : c_attr.getInstructions()) {
                        int idx = instr.accept(codeVisitor, cprefs);
                        if (idx > 0) {
                            refs.add(idx);
                        }
                    }
                }
                if (refs.size() > 0) {
                    referenceFound(cf, m, refs);
                }
            }
        }
        return cprefs.isEmpty() ? "" : cf.getName();
    }

    private ConstantPool.Visitor<Boolean,Void> cpVisitor =
            new ConstantPool.Visitor<Boolean,Void>()
    {
        private boolean matches(CPRefInfo info) {
            try {
                CONSTANT_NameAndType_info nat = info.getNameAndTypeInfo();
                return matches(info.getClassName(), nat.getName(), nat.getType());
            } catch (ConstantPoolException ex) {
                return false;
            }
        }

        private boolean matches(String cn, String name, String type) {
            return methods.contains(cn + "." + name);
        }

        public Boolean visitClass(CONSTANT_Class_info info, Void p) {
            return false;
        }

        public Boolean visitInterfaceMethodref(CONSTANT_InterfaceMethodref_info info, Void p) {
            return matches(info);
        }

        public Boolean visitMethodref(CONSTANT_Methodref_info info, Void p) {
            return matches(info);
        }

        public Boolean visitDouble(CONSTANT_Double_info info, Void p) {
            return false;
        }

        public Boolean visitFieldref(CONSTANT_Fieldref_info info, Void p) {
            return false;
        }

        public Boolean visitFloat(CONSTANT_Float_info info, Void p) {
            return false;
        }

        public Boolean visitInteger(CONSTANT_Integer_info info, Void p) {
            return false;
        }

        public Boolean visitInvokeDynamic(CONSTANT_InvokeDynamic_info info, Void p) {
            return false;
        }

        public Boolean visitLong(CONSTANT_Long_info info, Void p) {
            return false;
        }

        public Boolean visitNameAndType(CONSTANT_NameAndType_info info, Void p) {
            return false;
        }

        public Boolean visitMethodHandle(CONSTANT_MethodHandle_info info, Void p) {
            return false;
        }

        public Boolean visitMethodType(CONSTANT_MethodType_info info, Void p) {
            return false;
        }

        public Boolean visitString(CONSTANT_String_info info, Void p) {
            return false;
        }

        public Boolean visitUtf8(CONSTANT_Utf8_info info, Void p) {
            return false;
        }
    };

    private Instruction.KindVisitor<Integer, List<Integer>> codeVisitor =
            new Instruction.KindVisitor<Integer, List<Integer>>()
    {
        public Integer visitNoOperands(Instruction instr, List<Integer> p) {
            return 0;
        }

        public Integer visitArrayType(Instruction instr, TypeKind kind, List<Integer> p) {
            return 0;
        }

        public Integer visitBranch(Instruction instr, int offset, List<Integer> p) {
            return 0;
        }

        public Integer visitConstantPoolRef(Instruction instr, int index, List<Integer> p) {
            return p.contains(index) ? index : 0;
        }

        public Integer visitConstantPoolRefAndValue(Instruction instr, int index, int value, List<Integer> p) {
            return p.contains(index) ? index : 0;
        }

        public Integer visitLocal(Instruction instr, int index, List<Integer> p) {
            return 0;
        }

        public Integer visitLocalAndValue(Instruction instr, int index, int value, List<Integer> p) {
            return 0;
        }

        public Integer visitLookupSwitch(Instruction instr, int default_, int npairs, int[] matches, int[] offsets, List<Integer> p) {
            return 0;
        }

        public Integer visitTableSwitch(Instruction instr, int default_, int low, int high, int[] offsets, List<Integer> p) {
            return 0;
        }

        public Integer visitValue(Instruction instr, int value, List<Integer> p) {
            return 0;
        }

        public Integer visitUnknown(Instruction instr, List<Integer> p) {
            return 0;
        }
    };
}

