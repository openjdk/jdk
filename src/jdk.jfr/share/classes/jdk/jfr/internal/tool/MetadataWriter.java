/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.tool;

import java.io.PrintWriter;
import java.util.List;
import java.util.StringJoiner;
import jdk.jfr.AnnotationElement;
import jdk.jfr.Name;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.PrivateAccess;

/**
* Print event metadata in a human-readable format.
*/
final class MetadataWriter extends StructuredWriter {
    private final boolean showIds;

    public MetadataWriter(PrintWriter p, boolean showIds) {
        super(p);
        this.showIds = showIds;
    }

    public void printType(Type t) {
        if (showIds) {
            print("// id: ");
            println(String.valueOf(t.getId()));
        }
        int commentIndex = t.getName().length() + 10;
        String typeName = t.getName();
        int index = typeName.lastIndexOf(".");
        if (index != -1) {
            println("@Name(\"" + typeName + "\")");
        }
        printAnnotations(commentIndex, t.getAnnotationElements());
        print("class " + typeName.substring(index + 1));
        String superType = t.getSuperType();
        if (superType != null) {
            print(" extends " + superType);
        }
        println(" {");
        indent();
        boolean first = true;
        for (ValueDescriptor v : t.getFields()) {
            printField(commentIndex, v, first);
            first = false;
        }
        retract();
        println("}");
        println();
    }

    private void printField(int commentIndex, ValueDescriptor v, boolean first) {
        if (!first) {
            println();
        }
        printAnnotations(commentIndex, v.getAnnotationElements());
        printIndent();
        Type vType = PrivateAccess.getInstance().getType(v);
        if (Type.SUPER_TYPE_SETTING.equals(vType.getSuperType())) {
            print("static ");
        }
        print(makeSimpleType(v.getTypeName()));
        if (v.isArray()) {
            print("[]");
        }
        print(" ");
        print(v.getName());
        print(";");
        printCommentRef(commentIndex, v.getTypeId());
    }

    private void printCommentRef(int commentIndex, long typeId) {
        if (showIds) {
            int column = getColumn();
            if (column > commentIndex) {
                print("  ");
            } else {
                while (column < commentIndex) {
                    print(" ");
                    column++;
                }
            }
            println(" // id=" + typeId);
        } else {
            println();
        }
    }

    private void printAnnotations(int commentIndex, List<AnnotationElement> annotations) {
        for (AnnotationElement a : annotations) {
            if (!Name.class.getName().equals(a.getTypeName())) {
                printIndent();
                print("@");
                print(makeSimpleType(a.getTypeName()));
                List<ValueDescriptor> vs = a.getValueDescriptors();
                if (!vs.isEmpty()) {
                    printAnnotation(a);
                    printCommentRef(commentIndex, a.getTypeId());
                } else {
                    println();
                }
            }
        }
    }

    private void printAnnotation(AnnotationElement a) {
        StringJoiner sj = new StringJoiner(", ", "(", ")");
        List<ValueDescriptor> vs = a.getValueDescriptors();
        for (ValueDescriptor v : vs) {
            Object o = a.getValue(v.getName());
            if (vs.size() == 1 && v.getName().equals("value")) {
                sj.add(textify(o));
            } else {
                sj.add(v.getName() + "=" + textify(o));
            }
        }
        print(sj.toString());
    }

    private String textify(Object o) {
        if (o.getClass().isArray()) {
            Object[] array = (Object[]) o;
            if (array.length == 1) {
                return quoteIfNeeded(array[0]);
            }
            StringJoiner s = new StringJoiner(", ", "{", "}");
            for (Object ob : array) {
                s.add(quoteIfNeeded(ob));
            }
            return s.toString();
        } else {
            return quoteIfNeeded(o);
        }
    }

    private String quoteIfNeeded(Object o) {
        if (o instanceof String) {
            return "\"" + o + "\"";
        } else {
            return String.valueOf(o);
        }
    }

    private String makeSimpleType(String typeName) {
        int index = typeName.lastIndexOf(".");
        return typeName.substring(index + 1);
    }
}