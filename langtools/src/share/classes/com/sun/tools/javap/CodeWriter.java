/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 */

package com.sun.tools.javap;

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.DescriptorException;
import com.sun.tools.classfile.Instruction;
import com.sun.tools.classfile.Instruction.TypeKind;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.Opcode;

//import static com.sun.tools.classfile.OpCodes.*;

/*
 *  Write the contents of a Code attribute.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
class CodeWriter extends BasicWriter {
    static CodeWriter instance(Context context) {
        CodeWriter instance = context.get(CodeWriter.class);
        if (instance == null)
            instance = new CodeWriter(context);
        return instance;
    }

    protected CodeWriter(Context context) {
        super(context);
        context.put(CodeWriter.class, this);
        attrWriter = AttributeWriter.instance(context);
        classWriter = ClassWriter.instance(context);
        constantWriter = ConstantWriter.instance(context);
    }

    void write(Code_attribute attr, ConstantPool constant_pool) {
        println("  Code:");
        writeVerboseHeader(attr, constant_pool);
        writeInstrs(attr);
        writeExceptionTable(attr);
        attrWriter.write(attr, attr.attributes, constant_pool);
    }

    public void writeVerboseHeader(Code_attribute attr, ConstantPool constant_pool) {
        Method method = classWriter.getMethod();
        String argCount;
        try {
            int n = method.descriptor.getParameterCount(constant_pool);
            if (!method.access_flags.is(AccessFlags.ACC_STATIC))
                ++n;  // for 'this'
            argCount = Integer.toString(n);
        } catch (ConstantPoolException e) {
            argCount = report(e);
        } catch (DescriptorException e) {
            argCount = report(e);
        }

        println("   Stack=" + attr.max_stack +
                ", Locals=" + attr.max_locals +
                ", Args_size=" + argCount);

    }

    public void writeInstrs(Code_attribute attr) {
        for (Instruction instr: attr.getInstructions()) {
            try {
                writeInstr(instr);
            } catch (ArrayIndexOutOfBoundsException e) {
                println(report("error at or after byte " + instr.getPC()));
                break;
            }
        }
    }

    public void writeInstr(Instruction instr) {
        print("   " + instr.getPC() + ":\t");
        print(instr.getMnemonic());
        instr.accept(instructionPrinter, null);
        println();
    }
    // where
    Instruction.KindVisitor<Void,Void> instructionPrinter =
            new Instruction.KindVisitor<Void,Void>() {

        public Void visitNoOperands(Instruction instr, Void p) {
            return null;
        }

        public Void visitArrayType(Instruction instr, TypeKind kind, Void p) {
            print(" " + kind.name);
            return null;
        }

        public Void visitBranch(Instruction instr, int offset, Void p) {
            print("\t" + (instr.getPC() + offset));
            return null;
        }

        public Void visitConstantPoolRef(Instruction instr, int index, Void p) {
            print("\t#" + index + "; //");
            printConstant(index);
            return null;
        }

        public Void visitConstantPoolRefAndValue(Instruction instr, int index, int value, Void p) {
            print("\t#" + index + ",  " + value + "; //");
            printConstant(index);
            return null;
        }

        public Void visitLocal(Instruction instr, int index, Void p) {
            print("\t" + index);
            return null;
        }

        public Void visitLocalAndValue(Instruction instr, int index, int value, Void p) {
            print("\t" + index + ", " + value);
            return null;
        }

        public Void visitLookupSwitch(Instruction instr, int default_, int npairs, int[] matches, int[] offsets) {
            int pc = instr.getPC();
            print("{ //" + npairs);
            for (int i = 0; i < npairs; i++) {
                print("\n\t\t" + matches[i] + ": " + (pc + offsets[i]) + ";");
            }
            print("\n\t\tdefault: " + (pc + default_) + " }");
            return null;
        }

        public Void visitTableSwitch(Instruction instr, int default_, int low, int high, int[] offsets) {
            int pc = instr.getPC();
            print("{ //" + low + " to " + high);
            for (int i = 0; i < offsets.length; i++) {
                print("\n\t\t" + (low + i) + ": " + (pc + offsets[i]) + ";");
            }
            print("\n\t\tdefault: " + (pc + default_) + " }");
            return null;
        }

        public Void visitValue(Instruction instr, int value, Void p) {
            print("\t" + value);
            return null;
        }

        public Void visitUnknown(Instruction instr, Void p) {
            return null;
        }
    };


    public void writeExceptionTable(Code_attribute attr) {
        if (attr.exception_table_langth > 0) {
            println("  Exception table:");
            println("   from   to  target type");
            for (int i = 0; i < attr.exception_table.length; i++) {
                Code_attribute.Exception_data handler = attr.exception_table[i];
                printFixedWidthInt(handler.start_pc, 6);
                printFixedWidthInt(handler.end_pc, 6);
                printFixedWidthInt(handler.handler_pc, 6);
                print("   ");
                int catch_type = handler.catch_type;
                if (catch_type == 0) {
                    println("any");
                } else {
                    print("Class ");
                    println(constantWriter.stringValue(catch_type));
                    println("");
                }
            }
        }

    }

    private void printConstant(int index) {
        constantWriter.write(index);
    }

    private void printFixedWidthInt(int n, int width) {
        String s = String.valueOf(n);
        for (int i = s.length(); i < width; i++)
            print(" ");
        print(s);
    }

    private static int align(int n) {
        return (n + 3) & ~3;
    }

    private AttributeWriter attrWriter;
    private ClassWriter classWriter;
    private ConstantWriter constantWriter;
}
