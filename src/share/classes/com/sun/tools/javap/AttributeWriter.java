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

import java.util.Formatter;

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.AnnotationDefault_attribute;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.Attributes;
import com.sun.tools.classfile.CharacterRangeTable_attribute;
import com.sun.tools.classfile.Code_attribute;
import com.sun.tools.classfile.CompilationID_attribute;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.ConstantValue_attribute;
import com.sun.tools.classfile.DefaultAttribute;
import com.sun.tools.classfile.Deprecated_attribute;
import com.sun.tools.classfile.EnclosingMethod_attribute;
import com.sun.tools.classfile.Exceptions_attribute;
import com.sun.tools.classfile.InnerClasses_attribute;
import com.sun.tools.classfile.LineNumberTable_attribute;
import com.sun.tools.classfile.LocalVariableTable_attribute;
import com.sun.tools.classfile.LocalVariableTypeTable_attribute;
import com.sun.tools.classfile.ModuleExportTable_attribute;
import com.sun.tools.classfile.ModuleMemberTable_attribute;
import com.sun.tools.classfile.Module_attribute;
import com.sun.tools.classfile.RuntimeInvisibleAnnotations_attribute;
import com.sun.tools.classfile.RuntimeInvisibleParameterAnnotations_attribute;
import com.sun.tools.classfile.RuntimeInvisibleTypeAnnotations_attribute;
import com.sun.tools.classfile.RuntimeVisibleAnnotations_attribute;
import com.sun.tools.classfile.RuntimeVisibleParameterAnnotations_attribute;
import com.sun.tools.classfile.RuntimeVisibleTypeAnnotations_attribute;
import com.sun.tools.classfile.Signature_attribute;
import com.sun.tools.classfile.SourceDebugExtension_attribute;
import com.sun.tools.classfile.SourceFile_attribute;
import com.sun.tools.classfile.SourceID_attribute;
import com.sun.tools.classfile.StackMapTable_attribute;
import com.sun.tools.classfile.StackMap_attribute;
import com.sun.tools.classfile.Synthetic_attribute;

import static com.sun.tools.classfile.AccessFlags.*;

/*
 *  A writer for writing Attributes as text.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class AttributeWriter extends BasicWriter
        implements Attribute.Visitor<Void,Void>
{
    public static AttributeWriter instance(Context context) {
        AttributeWriter instance = context.get(AttributeWriter.class);
        if (instance == null)
            instance = new AttributeWriter(context);
        return instance;
    }

    protected AttributeWriter(Context context) {
        super(context);
        context.put(AttributeWriter.class, this);
        annotationWriter = AnnotationWriter.instance(context);
        codeWriter = CodeWriter.instance(context);
        constantWriter = ConstantWriter.instance(context);
        options = Options.instance(context);
    }

    public void write(Object owner, Attribute attr, ConstantPool constant_pool) {
        if (attr != null) {
            // null checks
            owner.getClass();
            constant_pool.getClass();
            this.constant_pool = constant_pool;
            this.owner = owner;
            attr.accept(this, null);
        }
    }

    public void write(Object owner, Attributes attrs, ConstantPool constant_pool) {
        if (attrs != null) {
            // null checks
            owner.getClass();
            constant_pool.getClass();
            this.constant_pool = constant_pool;
            this.owner = owner;
            for (Attribute attr: attrs)
                attr.accept(this, null);
        }
    }

    public Void visitDefault(DefaultAttribute attr, Void ignore) {
        byte[] data = attr.info;
        int i = 0;
        int j = 0;
        print("  ");
        try {
            print(attr.getName(constant_pool));
        } catch (ConstantPoolException e) {
            report(e);
            print("attribute name = #" + attr.attribute_name_index);
        }
        print(": ");
        println("length = 0x" + toHex(attr.info.length));

        print("   ");

        while (i < data.length) {
            print(toHex(data[i], 2));

            j++;
            if (j == 16) {
                println();
                print("   ");
                j = 0;
            } else {
                print(" ");
            }
            i++;
        }
        println();
        return null;
    }

    public Void visitAnnotationDefault(AnnotationDefault_attribute attr, Void ignore) {
        println("AnnotationDefault:");
        indent(+1);
        print("default_value: ");
        annotationWriter.write(attr.default_value);
        indent(-1);
        return null;
    }

    public Void visitCharacterRangeTable(CharacterRangeTable_attribute attr, Void ignore) {
        println("CharacterRangeTable:");
        indent(+1);
        for (int i = 0; i < attr.character_range_table.length; i++) {
            CharacterRangeTable_attribute.Entry e = attr.character_range_table[i];
            print("    " + e.start_pc + ", " +
                    e.end_pc + ", " +
                    Integer.toHexString(e.character_range_start) + ", " +
                    Integer.toHexString(e.character_range_end) + ", " +
                    Integer.toHexString(e.flags));
            tab();
            print("// ");
            print(e.start_pc + ", " +
                    e.end_pc + ", " +
                    (e.character_range_start >> 10) + ":" + (e.character_range_start & 0x3ff) + ", " +
                    (e.character_range_end >> 10) + ":" + (e.character_range_end & 0x3ff));
            if ((e.flags & CharacterRangeTable_attribute.CRT_STATEMENT) != 0)
                print(", statement");
            if ((e.flags & CharacterRangeTable_attribute.CRT_BLOCK) != 0)
                print(", block");
            if ((e.flags & CharacterRangeTable_attribute.CRT_ASSIGNMENT) != 0)
                print(", assignment");
            if ((e.flags & CharacterRangeTable_attribute.CRT_FLOW_CONTROLLER) != 0)
                print(", flow-controller");
            if ((e.flags & CharacterRangeTable_attribute.CRT_FLOW_TARGET) != 0)
                print(", flow-target");
            if ((e.flags & CharacterRangeTable_attribute.CRT_INVOKE) != 0)
                print(", invoke");
            if ((e.flags & CharacterRangeTable_attribute.CRT_CREATE) != 0)
                print(", create");
            if ((e.flags & CharacterRangeTable_attribute.CRT_BRANCH_TRUE) != 0)
                print(", branch-true");
            if ((e.flags & CharacterRangeTable_attribute.CRT_BRANCH_FALSE) != 0)
                print(", branch-false");
        }
        indent(-1);
        return null;
    }

    public Void visitCode(Code_attribute attr, Void ignore) {
        codeWriter.write(attr, constant_pool);
        return null;
    }

    public Void visitCompilationID(CompilationID_attribute attr, Void ignore) {
        constantWriter.write(attr.compilationID_index);
        return null;
    }

    public Void visitConstantValue(ConstantValue_attribute attr, Void ignore) {
        if (options.compat) // BUG 6622216 javap names some attributes incorrectly
            print("Constant value: ");
        else
            print("ConstantValue: ");
        constantWriter.write(attr.constantvalue_index);
        println();
        return null;
    }

    public Void visitDeprecated(Deprecated_attribute attr, Void ignore) {
        println("Deprecated: true");
        return null;
    }

    public Void visitEnclosingMethod(EnclosingMethod_attribute attr, Void ignore) {
        print("EnclosingMethod: #" + attr.class_index + ".#" + attr.method_index);
        tab();
        print("// " + getJavaClassName(attr));
        if (attr.method_index != 0)
            print("." + getMethodName(attr));
        println();
        return null;
    }

    private String getJavaClassName(EnclosingMethod_attribute a) {
        try {
            return getJavaName(a.getClassName(constant_pool));
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    private String getMethodName(EnclosingMethod_attribute a) {
        try {
            return a.getMethodName(constant_pool);
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    public Void visitExceptions(Exceptions_attribute attr, Void ignore) {
        println("Exceptions:");
        indent(+1);
        print("throws ");
        for (int i = 0; i < attr.number_of_exceptions; i++) {
            if (i > 0)
                print(", ");
            print(getJavaException(attr, i));
        }
        println();
        indent(-1);
        return null;
    }

    private String getJavaException(Exceptions_attribute attr, int index) {
        try {
            return getJavaName(attr.getException(index, constant_pool));
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    public Void visitInnerClasses(InnerClasses_attribute attr, Void ignore) {
        boolean first = true;
        if (options.compat) {
            writeInnerClassHeader();
            first = false;
        }
        for (int i = 0 ; i < attr.classes.length; i++) {
            InnerClasses_attribute.Info info = attr.classes[i];
            //access
            AccessFlags access_flags = info.inner_class_access_flags;
            if (options.compat) {
                // BUG 6622215: javap ignores certain relevant access flags
                access_flags = access_flags.ignore(ACC_STATIC | ACC_PROTECTED | ACC_PRIVATE | ACC_INTERFACE | ACC_SYNTHETIC | ACC_ENUM);
                // BUG 6622232: javap gets whitespace confused
                print("   ");
            }
            if (options.checkAccess(access_flags)) {
                if (first) {
                    writeInnerClassHeader();
                    first = false;
                }
                print("   ");
                for (String name: access_flags.getInnerClassModifiers())
                    print(name + " ");
                if (info.inner_name_index!=0) {
                    print("#" + info.inner_name_index + "= ");
                }
                print("#" + info.inner_class_info_index);
                if (info.outer_class_info_index != 0) {
                    print(" of #" + info.outer_class_info_index);
                }
                print("; //");
                if (info.inner_name_index != 0) {
                    print(getInnerName(constant_pool, info) + "=");
                }
                constantWriter.write(info.inner_class_info_index);
                if (info.outer_class_info_index != 0) {
                    print(" of ");
                    constantWriter.write(info.outer_class_info_index);
                }
                println();
            }
        }
        if (!first)
            indent(-1);
        return null;
    }

    String getInnerName(ConstantPool constant_pool, InnerClasses_attribute.Info info) {
        try {
            return info.getInnerName(constant_pool);
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    private void writeInnerClassHeader() {
        if (options.compat) // BUG 6622216: javap names some attributes incorrectly
            print("InnerClass");
        else
            print("InnerClasses");
        println(": ");
        indent(+1);
    }

    public Void visitLineNumberTable(LineNumberTable_attribute attr, Void ignore) {
        println("LineNumberTable:");
        indent(+1);
        for (LineNumberTable_attribute.Entry entry: attr.line_number_table) {
            println("line " + entry.line_number + ": " + entry.start_pc);
        }
        indent(-1);
        return null;
    }

    public Void visitLocalVariableTable(LocalVariableTable_attribute attr, Void ignore) {
        println("LocalVariableTable:");
        indent(+1);
        println("Start  Length  Slot  Name   Signature");
        for (LocalVariableTable_attribute.Entry entry : attr.local_variable_table) {
            Formatter formatter = new Formatter();
            println(formatter.format("%8d %7d %5d %5s   %s",
                    entry.start_pc, entry.length, entry.index,
                    constantWriter.stringValue(entry.name_index),
                    constantWriter.stringValue(entry.descriptor_index)));
        }
        indent(-1);
        return null;
    }

    public Void visitLocalVariableTypeTable(LocalVariableTypeTable_attribute attr, Void ignore) {
        println("LocalVariableTypeTable:");
        indent(+1);
        println("Start  Length  Slot  Name   Signature");
        for (LocalVariableTypeTable_attribute.Entry entry : attr.local_variable_table) {
            println(String.format("%5d %7d %5d %5s   %s",
                    entry.start_pc, entry.length, entry.index,
                    constantWriter.stringValue(entry.name_index),
                    constantWriter.stringValue(entry.signature_index)));
        }
        indent(-1);
        return null;
    }

    public Void visitModule(Module_attribute attr, Void ignore) {
        print("Module: #" + attr.module_name);
        tab();
        println("// " + getModuleName(attr));
        return null;
    }

    String getModuleName(Module_attribute attr) {
        try {
            return attr.getModuleName(constant_pool);
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    public Void visitModuleExportTable(ModuleExportTable_attribute attr, Void ignore) {
        println("ModuleExportTable:");
        indent(+1);
        println("Types: (" + attr.export_type_table.length + ")");
        for (int i = 0; i < attr.export_type_table.length; i++) {
            print("#" + attr.export_type_table[i]);
            tab();
            println("// " + getExportTypeName(attr, i));
        }
        indent(-1);
        return null;
    }

    String getExportTypeName(ModuleExportTable_attribute attr, int index) {
        try {
            return attr.getExportTypeName(index, constant_pool);
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    public Void visitModuleMemberTable(ModuleMemberTable_attribute attr, Void ignore) {
        println("ModuleMemberTable:");
        indent(+1);
        println("Packages: (" + attr.package_member_table.length + ")");
        for (int i = 0; i < attr.package_member_table.length; i++) {
            print("#" + attr.package_member_table[i]);
            tab();
            println("// " + getPackageMemberName(attr, i));
        }
        indent(-1);
        return null;
    }

    String getPackageMemberName(ModuleMemberTable_attribute attr, int index) {
        try {
            return attr.getPackageMemberName(index, constant_pool);
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    public Void visitRuntimeVisibleAnnotations(RuntimeVisibleAnnotations_attribute attr, Void ignore) {
        println("RuntimeVisibleAnnotations:");
        indent(+1);
        for (int i = 0; i < attr.annotations.length; i++) {
            print(i + ": ");
            annotationWriter.write(attr.annotations[i]);
            println();
        }
        indent(-1);
        return null;
    }

    public Void visitRuntimeInvisibleAnnotations(RuntimeInvisibleAnnotations_attribute attr, Void ignore) {
        println("RuntimeInvisibleAnnotations:");
        indent(+1);
        for (int i = 0; i < attr.annotations.length; i++) {
            print(i + ": ");
            annotationWriter.write(attr.annotations[i]);
            println();
        }
        indent(-1);
        return null;
    }

    public Void visitRuntimeVisibleTypeAnnotations(RuntimeVisibleTypeAnnotations_attribute attr, Void ignore) {
        println("RuntimeVisibleTypeAnnotations:");
        indent(+1);
        for (int i = 0; i < attr.annotations.length; i++) {
            print(i + ": ");
            annotationWriter.write(attr.annotations[i]);
            println();
        }
        indent(-1);
        return null;
    }

    public Void visitRuntimeInvisibleTypeAnnotations(RuntimeInvisibleTypeAnnotations_attribute attr, Void ignore) {
        println("RuntimeInvisibleTypeAnnotations:");
        indent(+1);
        for (int i = 0; i < attr.annotations.length; i++) {
            print(i + ": ");
            annotationWriter.write(attr.annotations[i]);
            println();
        }
        indent(-1);
        return null;
    }

    public Void visitRuntimeVisibleParameterAnnotations(RuntimeVisibleParameterAnnotations_attribute attr, Void ignore) {
        println("RuntimeVisibleParameterAnnotations:");
        indent(+1);
        for (int param = 0; param < attr.parameter_annotations.length; param++) {
            println("parameter " + param + ": ");
            indent(+1);
            for (int i = 0; i < attr.parameter_annotations[param].length; i++) {
                print(i + ": ");
                annotationWriter.write(attr.parameter_annotations[param][i]);
                println();
            }
            indent(-1);
        }
        indent(-1);
        return null;
    }

    public Void visitRuntimeInvisibleParameterAnnotations(RuntimeInvisibleParameterAnnotations_attribute attr, Void ignore) {
        println("RuntimeInvisibleParameterAnnotations:");
        indent(+1);
        for (int param = 0; param < attr.parameter_annotations.length; param++) {
            println(param + ": ");
            indent(+1);
            for (int i = 0; i < attr.parameter_annotations[param].length; i++) {
                print(i + ": ");
                annotationWriter.write(attr.parameter_annotations[param][i]);
                println();
            }
            indent(-1);
        }
        indent(-1);
        return null;
    }

    public Void visitSignature(Signature_attribute attr, Void ignore) {
        print("Signature: #" + attr.signature_index);
        tab();
        println("// " + getSignature(attr));
        return null;
    }

    String getSignature(Signature_attribute info) {
        try {
            return info.getSignature(constant_pool);
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    public Void visitSourceDebugExtension(SourceDebugExtension_attribute attr, Void ignore) {
        println("SourceDebugExtension: " + attr.getValue());
        return null;
    }

    public Void visitSourceFile(SourceFile_attribute attr, Void ignore) {
        println("SourceFile: \"" + getSourceFile(attr) + "\"");
        return null;
    }

    private String getSourceFile(SourceFile_attribute attr) {
        try {
            return attr.getSourceFile(constant_pool);
        } catch (ConstantPoolException e) {
            return report(e);
        }
    }

    public Void visitSourceID(SourceID_attribute attr, Void ignore) {
        constantWriter.write(attr.sourceID_index);
        return null;
    }

    public Void visitStackMap(StackMap_attribute attr, Void ignore) {
        println("StackMap: number_of_entries = " + attr.number_of_entries);
        indent(+1);
        StackMapTableWriter w = new StackMapTableWriter();
        for (StackMapTable_attribute.stack_map_frame entry : attr.entries) {
            w.write(entry);
        }
        println();
        indent(-1);
        return null;
    }

    public Void visitStackMapTable(StackMapTable_attribute attr, Void ignore) {
        println("StackMapTable: number_of_entries = " + attr.number_of_entries);
        indent(+1);
        StackMapTableWriter w = new StackMapTableWriter();
        for (StackMapTable_attribute.stack_map_frame entry : attr.entries) {
            w.write(entry);
        }
        println();
        indent(-1);
        return null;
    }

    class StackMapTableWriter // also handles CLDC StackMap attributes
            implements StackMapTable_attribute.stack_map_frame.Visitor<Void,Void> {
        public void write(StackMapTable_attribute.stack_map_frame frame) {
            frame.accept(this, null);
        }

        public Void visit_same_frame(StackMapTable_attribute.same_frame frame, Void p) {
            printHeader(frame);
            println(" /* same */");
            return null;
        }

        public Void visit_same_locals_1_stack_item_frame(StackMapTable_attribute.same_locals_1_stack_item_frame frame, Void p) {
            printHeader(frame);
            println(" /* same_locals_1_stack_item */");
            indent(+1);
            printMap("stack", frame.stack);
            indent(-1);
            return null;
        }

        public Void visit_same_locals_1_stack_item_frame_extended(StackMapTable_attribute.same_locals_1_stack_item_frame_extended frame, Void p) {
            printHeader(frame);
            println(" /* same_locals_1_stack_item_frame_extended */");
            indent(+1);
            println("offset_delta = " + frame.offset_delta);
            printMap("stack", frame.stack);
            indent(-1);
            return null;
        }

        public Void visit_chop_frame(StackMapTable_attribute.chop_frame frame, Void p) {
            printHeader(frame);
            println(" /* chop */");
            indent(+1);
            println("offset_delta = " + frame.offset_delta);
            indent(-1);
            return null;
        }

        public Void visit_same_frame_extended(StackMapTable_attribute.same_frame_extended frame, Void p) {
            printHeader(frame);
            println(" /* same_frame_extended */");
            indent(+1);
            println("offset_delta = " + frame.offset_delta);
            indent(-1);
            return null;
        }

        public Void visit_append_frame(StackMapTable_attribute.append_frame frame, Void p) {
            printHeader(frame);
            println(" /* append */");
            println("     offset_delta = " + frame.offset_delta);
            printMap("locals", frame.locals);
            return null;
        }

        public Void visit_full_frame(StackMapTable_attribute.full_frame frame, Void p) {
            printHeader(frame);
            if (frame instanceof StackMap_attribute.stack_map_frame) {
                indent(+1);
                println(" offset = " + frame.offset_delta);
            } else {
                println(" /* full_frame */");
                indent(+1);
                println("offset_delta = " + frame.offset_delta);
            }
            printMap("locals", frame.locals);
            printMap("stack", frame.stack);
            indent(-1);
            return null;
        }

        void printHeader(StackMapTable_attribute.stack_map_frame frame) {
            print("   frame_type = " + frame.frame_type);
        }

        void printMap(String name, StackMapTable_attribute.verification_type_info[] map) {
            print(name + " = [");
            for (int i = 0; i < map.length; i++) {
                StackMapTable_attribute.verification_type_info info = map[i];
                int tag = info.tag;
                switch (tag) {
                    case StackMapTable_attribute.verification_type_info.ITEM_Object:
                        print(" ");
                        constantWriter.write(((StackMapTable_attribute.Object_variable_info) info).cpool_index);
                        break;
                    case StackMapTable_attribute.verification_type_info.ITEM_Uninitialized:
                        print(" " + mapTypeName(tag));
                        print(" " + ((StackMapTable_attribute.Uninitialized_variable_info) info).offset);
                        break;
                    default:
                        print(" " + mapTypeName(tag));
                }
                print(i == (map.length - 1) ? " " : ",");
            }
            println("]");
        }

        String mapTypeName(int tag) {
            switch (tag) {
            case StackMapTable_attribute.verification_type_info.ITEM_Top:
                return "top";

            case StackMapTable_attribute.verification_type_info.ITEM_Integer:
                return "int";

            case StackMapTable_attribute.verification_type_info.ITEM_Float:
                return "float";

            case StackMapTable_attribute.verification_type_info.ITEM_Long:
                return "long";

            case StackMapTable_attribute.verification_type_info.ITEM_Double:
                return "double";

            case StackMapTable_attribute.verification_type_info.ITEM_Null:
                return "null";

            case StackMapTable_attribute.verification_type_info.ITEM_UninitializedThis:
                return "this";

            case StackMapTable_attribute.verification_type_info.ITEM_Object:
                return "CP";

            case StackMapTable_attribute.verification_type_info.ITEM_Uninitialized:
                return "uninitialized";

            default:
                report("unrecognized verification_type_info tag: " + tag);
                return "[tag:" + tag + "]";
            }
        }
    }

    public Void visitSynthetic(Synthetic_attribute attr, Void ignore) {
        println("Synthetic: true");
        return null;
    }

    static String getJavaName(String name) {
        return name.replace('/', '.');
    }

    String toHex(byte b, int w) {
        if (options.compat) // BUG 6622260: javap prints negative bytes incorrectly in hex
            return toHex((int) b, w);
        else
            return toHex(b & 0xff, w);
    }

    static String toHex(int i) {
        return Integer.toString(i, 16).toUpperCase();
    }

    static String toHex(int i, int w) {
        String s = Integer.toHexString(i).toUpperCase();
        while (s.length() < w)
            s = "0" + s;
        return s.toUpperCase();
    }

    private AnnotationWriter annotationWriter;
    private CodeWriter codeWriter;
    private ConstantWriter constantWriter;
    private Options options;

    private ConstantPool constant_pool;
    private Object owner;
}
