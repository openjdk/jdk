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

import java.util.ArrayList;
import java.util.List;

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.DescriptorException;
import com.sun.tools.classfile.Instruction;
import com.sun.tools.classfile.Instruction.TypeKind;
import com.sun.tools.classfile.Method;

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
        sourceWriter = SourceWriter.instance(context);
        tryBlockWriter = TryBlockWriter.instance(context);
        stackMapWriter = StackMapWriter.instance(context);
        localVariableTableWriter = LocalVariableTableWriter.instance(context);
        localVariableTypeTableWriter = LocalVariableTypeTableWriter.instance(context);
        typeAnnotationWriter = TypeAnnotationWriter.instance(context);
        options = Options.instance(context);
    }

    void write(Code_attribute attr, ConstantPool constant_pool) {
        println("Code:");
        indent(+1);
        writeVerboseHeader(attr, constant_pool);
        writeInstrs(attr);
        writeExceptionTable(attr);
        attrWriter.write(attr, attr.attributes, constant_pool);
        indent(-1);
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

        println("stack=" + attr.max_stack +
                ", locals=" + attr.max_locals +
                ", args_size=" + argCount);

    }

    public void writeInstrs(Code_attribute attr) {
        List<InstructionDetailWriter> detailWriters = getDetailWriters(attr);

        for (Instruction instr: attr.getInstructions()) {
            try {
                for (InstructionDetailWriter w: detailWriters)
                    w.writeDetails(instr);
                writeInstr(instr);
            } catch (ArrayIndexOutOfBoundsException e) {
                println(report("error at or after byte " + instr.getPC()));
                break;
            }
        }

        for (InstructionDetailWriter w: detailWriters)
            w.flush();
    }

    public void writeInstr(Instruction instr) {
        print(String.format("%4d: %-12s ", instr.getPC(), instr.getMnemonic()));
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
            print((instr.getPC() + offset));
            return null;
        }

        public Void visitConstantPoolRef(Instruction instr, int index, Void p) {
            print("#" + index);
            tab();
            print("// ");
            printConstant(index);
            return null;
        }

        public Void visitConstantPoolRefAndValue(Instruction instr, int index, int value, Void p) {
            print("#" + index + ",  " + value);
            tab();
            print("// ");
            printConstant(index);
            return null;
        }

        public Void visitLocal(Instruction instr, int index, Void p) {
            print(index);
            return null;
        }

        public Void visitLocalAndValue(Instruction instr, int index, int value, Void p) {
            print(index + ", " + value);
            return null;
        }

        public Void visitLookupSwitch(Instruction instr, int default_, int npairs, int[] matches, int[] offsets) {
            int pc = instr.getPC();
            print("{ // " + npairs);
            indent(+1);
            for (int i = 0; i < npairs; i++) {
                print("\n" + matches[i] + ": " + (pc + offsets[i]));
            }
            print("\ndefault: " + (pc + default_) + " }");
            indent(-1);
            return null;
        }

        public Void visitTableSwitch(Instruction instr, int default_, int low, int high, int[] offsets) {
            int pc = instr.getPC();
            print("{ //" + low + " to " + high);
            indent(+1);
            for (int i = 0; i < offsets.length; i++) {
                print("\n" + (low + i) + ": " + (pc + offsets[i]));
            }
            print("\ndefault: " + (pc + default_) + " }");
            indent(-1);
            return null;
        }

        public Void visitValue(Instruction instr, int value, Void p) {
            print(value);
            return null;
        }

        public Void visitUnknown(Instruction instr, Void p) {
            return null;
        }
    };


    public void writeExceptionTable(Code_attribute attr) {
        if (attr.exception_table_langth > 0) {
            println("Exception table:");
            indent(+1);
            println(" from    to  target type");
            for (int i = 0; i < attr.exception_table.length; i++) {
                Code_attribute.Exception_data handler = attr.exception_table[i];
                print(String.format(" %5d %5d %5d",
                        handler.start_pc, handler.end_pc, handler.handler_pc));
                print("   ");
                int catch_type = handler.catch_type;
                if (catch_type == 0) {
                    println("any");
                } else {
                    print("Class ");
                    println(constantWriter.stringValue(catch_type));
                }
            }
            indent(-1);
        }

    }

    private void printConstant(int index) {
        constantWriter.write(index);
    }

    private List<InstructionDetailWriter> getDetailWriters(Code_attribute attr) {
        List<InstructionDetailWriter> detailWriters =
                new ArrayList<InstructionDetailWriter>();
        if (options.details.contains(InstructionDetailWriter.Kind.SOURCE)) {
            sourceWriter.reset(classWriter.getClassFile(), attr);
            detailWriters.add(sourceWriter);
        }

        if (options.details.contains(InstructionDetailWriter.Kind.LOCAL_VARS)) {
            localVariableTableWriter.reset(attr);
            detailWriters.add(localVariableTableWriter);
        }

        if (options.details.contains(InstructionDetailWriter.Kind.LOCAL_VAR_TYPES)) {
            localVariableTypeTableWriter.reset(attr);
            detailWriters.add(localVariableTypeTableWriter);
        }

        if (options.details.contains(InstructionDetailWriter.Kind.STACKMAPS)) {
            stackMapWriter.reset(attr);
            stackMapWriter.writeInitialDetails();
            detailWriters.add(stackMapWriter);
        }

        if (options.details.contains(InstructionDetailWriter.Kind.TRY_BLOCKS)) {
            tryBlockWriter.reset(attr);
            detailWriters.add(tryBlockWriter);
        }

        if (options.details.contains(InstructionDetailWriter.Kind.TYPE_ANNOS)) {
            typeAnnotationWriter.reset(attr);
            detailWriters.add(typeAnnotationWriter);
        }

        return detailWriters;
    }

    private AttributeWriter attrWriter;
    private ClassWriter classWriter;
    private ConstantWriter constantWriter;
    private LocalVariableTableWriter localVariableTableWriter;
    private LocalVariableTypeTableWriter localVariableTypeTableWriter;
    private TypeAnnotationWriter typeAnnotationWriter;
    private SourceWriter sourceWriter;
    private StackMapWriter stackMapWriter;
    private TryBlockWriter tryBlockWriter;
    private Options options;
}
