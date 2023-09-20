/*
 * Copyright (c) 2007, 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.classfile.constantpool.*;
import static jdk.internal.classfile.Classfile.*;

/*
 *  Write a constant pool entry.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ConstantWriter extends BasicWriter {
    public static ConstantWriter instance(Context context) {
        ConstantWriter instance = context.get(ConstantWriter.class);
        if (instance == null)
            instance = new ConstantWriter(context);
        return instance;
    }

    protected ConstantWriter(Context context) {
        super(context);
        context.put(ConstantWriter.class, this);
        classWriter = ClassWriter.instance(context);
        options = Options.instance(context);
    }

    protected void writeConstantPool() {
        var constant_pool = classWriter.getClassModel().constantPool();
        writeConstantPool(constant_pool);
    }

    protected void writeConstantPool(ConstantPool constant_pool) {
        println("Constant pool:");
        indent(+1);
        int width = String.valueOf(constant_pool.size()).length() + 1;
        int cpx = 1;
        while (cpx < constant_pool.size()) {
            print(String.format("%" + width + "s", ("#" + cpx)));
            try {
                var cpInfo = constant_pool.entryByIndex(cpx);
                print(String.format(" = %-18s ", cpTagName(cpInfo.tag())));
                switch (cpInfo) {
                    case ClassEntry info -> {
                        print(() -> "#" + info.name().index());
                        tab();
                        println(() -> "// " + stringValue(info));
                    }
                    case AnnotationConstantValueEntry info -> {
                        println(() -> stringValue(info));
                    }
                    case MemberRefEntry info -> {
                        print(() -> "#" + info.owner().index() + ".#"
                                + info.nameAndType().index());
                        tab();
                        println(() -> "// " + stringValue(info));
                    }
                    case DynamicConstantPoolEntry info -> {
                        print(() -> "#" + info.bootstrapMethodIndex() + ":#"
                                + info.nameAndType().index());
                        tab();
                        println(() -> "// " + stringValue(info));
                    }
                    case MethodHandleEntry info -> {
                        print(() -> info.kind() + ":#" + info.reference().index());
                        tab();
                        println(() -> "// " + stringValue(info));
                    }
                    case MethodTypeEntry info -> {
                        print(() -> "#" + info.descriptor().index());
                        tab();
                        println(() -> "//  " + stringValue(info));
                    }
                    case ModuleEntry info -> {
                        print(() -> "#" + info.name().index());
                        tab();
                        println(() -> "// " + stringValue(info));
                    }
                    case NameAndTypeEntry info -> {
                        print(() -> "#" + info.name().index() + ":#" + info.type().index());
                        tab();
                        println(() -> "// " + stringValue(info));
                    }
                    case PackageEntry info -> {
                        print(() -> "#" + info.name().index());
                        tab();
                        println("// " + stringValue(info));
                    }
                    case StringEntry info -> {
                        print(() -> "#" + info.utf8().index());
                        tab();
                        println(() -> "// " + stringValue(info));
                    }
                    default ->
                        throw new IllegalArgumentException("unknown entry: "+ cpInfo);
                }
                cpx += cpInfo.width();
            } catch (IllegalArgumentException e) {
                println(report(e));
                cpx++;
            }
        }
        indent(-1);
    }

    protected void write(int cpx) {
        if (cpx == 0) {
            print("#0");
            return;
        }
        var classModel = classWriter.getClassModel();

        var cpInfo = classModel.constantPool().entryByIndex(cpx);
        var tag = cpInfo.tag();
        if (cpInfo instanceof MemberRefEntry ref) {
            // simplify references within this class
            if (ref.owner().index() == classModel.thisClass().index())
                 cpInfo = ref.nameAndType();
        }
        print(tagName(tag) + " " + stringValue(cpInfo));
    }

    String cpTagName(int tag) {
        return switch (tag) {
            case TAG_UTF8 -> "Utf8";
            case TAG_INTEGER -> "Integer";
            case TAG_FLOAT -> "Float";
            case TAG_LONG -> "Long";
            case TAG_DOUBLE -> "Double";
            case TAG_CLASS -> "Class";
            case TAG_STRING -> "String";
            case TAG_FIELDREF -> "Fieldref";
            case TAG_METHODHANDLE -> "MethodHandle";
            case TAG_METHODTYPE -> "MethodType";
            case TAG_METHODREF -> "Methodref";
            case TAG_INTERFACEMETHODREF -> "InterfaceMethodref";
            case TAG_INVOKEDYNAMIC -> "InvokeDynamic";
            case TAG_CONSTANTDYNAMIC -> "Dynamic";
            case TAG_NAMEANDTYPE -> "NameAndType";
            default -> "Unknown";
        };
    }

    String tagName(int tag) {
        return switch (tag) {
            case TAG_UTF8 -> "Utf8";
            case TAG_INTEGER -> "int";
            case TAG_FLOAT -> "float";
            case TAG_LONG -> "long";
            case TAG_DOUBLE -> "double";
            case TAG_CLASS -> "class";
            case TAG_STRING -> "String";
            case TAG_FIELDREF -> "Field";
            case TAG_METHODHANDLE -> "MethodHandle";
            case TAG_METHODTYPE -> "MethodType";
            case TAG_METHODREF -> "Method";
            case TAG_INTERFACEMETHODREF -> "InterfaceMethod";
            case TAG_INVOKEDYNAMIC -> "InvokeDynamic";
            case TAG_CONSTANTDYNAMIC -> "Dynamic";
            case TAG_NAMEANDTYPE -> "NameAndType";
            default -> "(unknown tag " + tag + ")";
        };
    }

    String booleanValue(PoolEntry info) {
        if (info instanceof IntegerEntry ie) {
           switch (ie.intValue()) {
               case 0: return "false";
               case 1: return "true";
           }
        }
        return "#" + info.index();
    }

    String booleanValue(int constant_pool_index) {
        var info = classWriter.getClassModel().constantPool()
                .entryByIndex(constant_pool_index);
        if (info instanceof IntegerEntry ie) {
           switch (ie.intValue()) {
               case 0: return "false";
               case 1: return "true";
           }
        }
        return "#" + constant_pool_index;
    }

    String charValue(PoolEntry info) {
        if (info instanceof IntegerEntry ie) {
            int value = ie.intValue();
            return String.valueOf((char) value);
        } else {
            return "#" + info.index();
        }
    }

    String charValue(int constant_pool_index) {
        var info = classWriter.getClassModel().constantPool()
                .entryByIndex(constant_pool_index);
        if (info instanceof IntegerEntry ie) {
            int value = ie.intValue();
            return String.valueOf((char) value);
        } else {
            return "#" + constant_pool_index;
        }
    }

    String stringValue(int constant_pool_index) {
        return stringValue(classWriter.getClassModel().constantPool()
                .entryByIndex(constant_pool_index));
    }

    String stringValue(PoolEntry cpInfo) {
        return switch (cpInfo) {
            case ClassEntry info -> checkName(info.asInternalName());
            case DoubleEntry info -> info.doubleValue() + "d";
            case MemberRefEntry info -> checkName(info.owner().asInternalName())
                + '.' + stringValue(info.nameAndType());
            case FloatEntry info -> info.floatValue()+ "f";
            case IntegerEntry info -> String.valueOf(info.intValue());
            case DynamicConstantPoolEntry info -> "#" + info.bootstrapMethodIndex()
                + ":" + stringValue(info.nameAndType());
            case LongEntry info -> info.longValue()+ "l";
            case ModuleEntry info -> checkName(info.name().stringValue());
            case NameAndTypeEntry info -> checkName(info.name().stringValue())
                + ':' + info.type().stringValue();
            case PackageEntry info -> checkName(info.name().stringValue());
            case MethodHandleEntry info -> {
                String kind = switch (info.asSymbol().kind()) {
                    case STATIC, INTERFACE_STATIC -> "REF_invokeStatic";
                    case VIRTUAL -> "REF_invokeVirtual";
                    case INTERFACE_VIRTUAL -> "REF_invokeInterface";
                    case SPECIAL, INTERFACE_SPECIAL -> "REF_invokeSpecial";
                    case CONSTRUCTOR -> "REF_newInvokeSpecial";
                    case GETTER -> "REF_getField";
                    case SETTER -> "REF_putField";
                    case STATIC_GETTER -> "REF_getStatic";
                    case STATIC_SETTER -> "REF_putStatic";
                };
                yield kind + " " + stringValue(info.reference());
            }
            case MethodTypeEntry info -> info.descriptor().stringValue();
            case StringEntry info -> stringValue(info.utf8());
            case Utf8Entry info -> {
                StringBuilder sb = new StringBuilder();
                for (char c : info.stringValue().toCharArray()) {
                    sb.append(switch (c) {
                        case '\t' -> "\\t";
                        case '\n' -> "\\n";
                        case '\r' -> "\\r";
                        case '\b' -> "\\b";
                        case '\f' -> "\\f";
                        case '\"' -> "\\\"";
                        case '\'' -> "\\\'";
                        case '\\' -> "\\\\";
                        default -> Character.isISOControl(c)
                                ? String.format("\\u%04x", (int) c) : c;
                    });
                }
                yield sb.toString();
            }
            default -> throw new IllegalArgumentException("unknown " + cpInfo);
        };
    }

    /* If name is a valid binary name, return it; otherwise quote it. */
    private static String checkName(String name) {
        if (name == null)
            return "null";

        int len = name.length();
        if (len == 0)
            return "\"\"";

        int cc = '/';
        int cp;
        for (int k = 0; k < len; k += Character.charCount(cp)) {
            cp = name.codePointAt(k);
            if ((cc == '/' && !Character.isJavaIdentifierStart(cp))
                    || (cp != '/' && !Character.isJavaIdentifierPart(cp))) {
                return "\"" + addEscapes(name) + "\"";
            }
            cc = cp;
        }

        return name;
    }

    /* If name requires escapes, put them in, so it can be a string body. */
    private static String addEscapes(String name) {
        String esc = "\\\"\n\t";
        String rep = "\\\"nt";
        StringBuilder buf = null;
        int nextk = 0;
        int len = name.length();
        for (int k = 0; k < len; k++) {
            char cp = name.charAt(k);
            int n = esc.indexOf(cp);
            if (n >= 0) {
                if (buf == null)
                    buf = new StringBuilder(len * 2);
                if (nextk < k)
                    buf.append(name, nextk, k);
                buf.append('\\');
                buf.append(rep.charAt(n));
                nextk = k+1;
            }
        }
        if (buf == null)
            return name;
        if (nextk < len)
            buf.append(name, nextk, len);
        return buf.toString();
    }

    private final ClassWriter classWriter;
    private final Options options;
}
