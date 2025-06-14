/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javap;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.instruction.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/*
 *  Write the contents of a Code attribute.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class CodeWriter extends BasicWriter {
    public static CodeWriter instance(Context context) {
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

    void write(CodeAttribute attr) {
        writeInternal(attr, false);
    }

    void writeMinimal(CodeAttribute attr) {
        writeInternal(attr, true);
    }

    public void writeVerboseHeader(CodeAttribute attr) {
        MethodModel method = attr.parent().get();
        int n = method.methodTypeSymbol().parameterCount();
        if ((method.flags().flagsMask() & ClassFile.ACC_STATIC) == 0)
            ++n;  // for 'this'
        println("stack=" + attr.maxStack() +
                ", locals=" + attr.maxLocals() +
                ", args_size=" + Integer.toString(n));
    }

    public void writeInstrs(CodeAttribute attr) {
        List<InstructionDetailWriter> detailWriters = getDetailWriters(attr);

        int[] pcState = {0};
        try {
            attr.forEach(coe -> {
                if (coe instanceof Instruction instr) {
                    int pc = pcState[0];
                    for (InstructionDetailWriter w : detailWriters)
                        w.writeDetails(pc, instr);
                    writeInstr(pc, instr, attr);
                    pcState[0] = pc + instr.sizeInBytes();
                }
            });
        } catch (IllegalArgumentException e) {
            report("error at or after address " + pcState[0] + ": " + e.getMessage());
        }

        int pc = pcState[0];
        for (InstructionDetailWriter w: detailWriters)
            w.flush(pc);
    }

    public void writeInstr(int pc, Instruction ins, CodeAttribute lr) {
        print(String.format("%4d: %-13s ", pc, ins.opcode().name().toLowerCase(Locale.US)));
        try {
            // compute the number of indentations for the body of multi-line instructions
            // This is 6 (the width of "%4d: "), divided by the width of each indentation level,
            // and rounded up to the next integer.
            int indentWidth = options.indentWidth;
            int indent = (6 + indentWidth - 1) / indentWidth;
            switch (ins) {
                case BranchInstruction instr ->
                    print(lr.labelToBci(instr.target()));
                case ConstantInstruction.ArgumentConstantInstruction instr ->
                    print(instr.constantValue());
                case ConstantInstruction.LoadConstantInstruction instr ->
                    printConstantPoolRef(instr.constantEntry());
                case FieldInstruction instr ->
                    printConstantPoolRef(instr.field());
                case InvokeDynamicInstruction instr ->
                    printConstantPoolRefAndValue(instr.invokedynamic(), 0);
                case InvokeInstruction instr -> {
                    if (instr.opcode() == Opcode.INVOKEINTERFACE)
                        printConstantPoolRefAndValue(instr.method(), instr.count());
                    else printConstantPoolRef(instr.method());
                }
                case LoadInstruction instr ->
                    print(instr.sizeInBytes() > 1 ? instr.slot() : "");
                case StoreInstruction instr ->
                    print(instr.sizeInBytes() > 1 ? instr.slot() : "");
                case IncrementInstruction instr ->
                    print(instr.slot() + ", " + instr.constant());
                case LookupSwitchInstruction instr -> {
                    var cases = instr.cases();
                    print("{ // " + cases.size());
                    indent(indent);
                    for (var c : cases)
                        print(String.format("%n%12d: %d", c.caseValue(),
                                lr.labelToBci(c.target())));
                    print("\n     default: " + lr.labelToBci(instr.defaultTarget()) + "\n}");
                    indent(-indent);
                }
                case NewMultiArrayInstruction instr ->
                    printConstantPoolRefAndValue(instr.arrayType(), instr.dimensions());
                case NewObjectInstruction instr ->
                    printConstantPoolRef(instr.className());
                case NewPrimitiveArrayInstruction instr ->
                    print(" " + instr.typeKind().upperBound().displayName());
                case NewReferenceArrayInstruction instr ->
                    printConstantPoolRef(instr.componentType());
                case TableSwitchInstruction instr -> {
                    print("{ // " + instr.lowValue() + " to " + instr.highValue());
                    indent(indent);
                    var caseMap = instr.cases().stream().collect(
                            Collectors.toMap(SwitchCase::caseValue, SwitchCase::target));
                    for (int i = instr.lowValue(); i <= instr.highValue(); i++)
                        print(String.format("%n%12d: %d", i,
                                lr.labelToBci(caseMap.getOrDefault(i, instr.defaultTarget()))));
                    print("\n     default: " + lr.labelToBci(instr.defaultTarget()) + "\n}");
                    indent(-indent);
                }
                case TypeCheckInstruction instr ->
                    printConstantPoolRef(instr.type());
                default -> {}
            }
            println();
        } catch (IllegalArgumentException e) {
            println(report(e));
        }
    }

    private void printConstantPoolRef(PoolEntry entry) {
        print("#" + entry.index());
        tab();
        print("// ");
        constantWriter.write(entry.index());
    }

    private void printConstantPoolRefAndValue(PoolEntry entry, int value) {
        print("#" + entry.index() + ",  " + value);
        tab();
        print("// ");
        constantWriter.write(entry.index());
    }

    public void writeExceptionTable(CodeAttribute attr) {
        var excTable = attr.exceptionHandlers();
        if (excTable.size() > 0) {
            println("Exception table:");
            indent(+1);
            println(" from    to  target type");
            for (var handler : excTable) {
                print(String.format(" %5d %5d %5d",
                        attr.labelToBci(handler.tryStart()),
                        attr.labelToBci(handler.tryEnd()),
                        attr.labelToBci(handler.handler())));
                print("   ");
                var catch_type = handler.catchType();
                if (catch_type.isEmpty()) {
                    println("any");
                } else {
                    print("Class ");
                    println(constantWriter.stringValue(catch_type.get()));
                }
            }
            indent(-1);
        }

    }

    private List<InstructionDetailWriter> getDetailWriters(CodeAttribute attr) {
        List<InstructionDetailWriter> detailWriters = new ArrayList<>();
        if (options.details.contains(InstructionDetailWriter.Kind.SOURCE)) {
            sourceWriter.reset(attr);
            if (sourceWriter.hasSource())
                detailWriters.add(sourceWriter);
            else
                println("(Source code not available)");
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

    private void writeInternal(CodeAttribute attr, boolean minimal) {
        println("Code:");
        indent(+1);
        if (minimal) {
            writeMinimalMode(attr);
        } else {
            writeVerboseMode(attr);
        }
        indent(-1);
    }

    private void writeMinimalMode(CodeAttribute attr) {
        writeInstrs(attr);
        writeExceptionTable(attr);
        if (options.showLineAndLocalVariableTables) {
            writeLineAndLocalVariableTables(attr);
        }
    }

    private void writeVerboseMode(CodeAttribute attr) {
        writeVerboseHeader(attr);
        writeInstrs(attr);
        writeExceptionTable(attr);
        attrWriter.write(attr.attributes(), attr, classWriter.cffv());
    }

    private void writeLineAndLocalVariableTables(CodeAttribute attr) {
        attr.findAttribute(Attributes.lineNumberTable())
            .ifPresent(a -> attrWriter.write(a, attr, classWriter.cffv()));
        attr.findAttribute(Attributes.localVariableTable())
            .ifPresent(a -> attrWriter.write(a, attr, classWriter.cffv()));
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
