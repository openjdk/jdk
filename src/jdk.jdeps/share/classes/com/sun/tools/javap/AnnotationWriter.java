/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import jdk.internal.classfile.Annotation;
import jdk.internal.classfile.AnnotationElement;
import jdk.internal.classfile.AnnotationValue;
import jdk.internal.classfile.constantpool.*;
import jdk.internal.classfile.Signature;
import jdk.internal.classfile.TypeAnnotation;
import jdk.internal.classfile.attribute.CodeAttribute;

/**
 *  A writer for writing annotations as text.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class AnnotationWriter extends BasicWriter {
    static AnnotationWriter instance(Context context) {
        AnnotationWriter instance = context.get(AnnotationWriter.class);
        if (instance == null)
            instance = new AnnotationWriter(context);
        return instance;
    }

    protected AnnotationWriter(Context context) {
        super(context);
        classWriter = ClassWriter.instance(context);
        constantWriter = ConstantWriter.instance(context);
    }

    public void write(Annotation annot) {
        write(annot, false);
        println();
        indent(+1);
        write(annot, true);
        indent(-1);
    }

    public void write(Annotation annot, boolean resolveIndices) {
        writeDescriptor(annot.className(), resolveIndices);
        if (resolveIndices) {
            boolean showParens = annot.elements().size() > 0;
            if (showParens) {
                println("(");
                indent(+1);
            }
            for (var element : annot.elements()) {
                write(element, true);
                println();
            }
            if (showParens) {
                indent(-1);
                print(")");
            }
        } else {
            print("(");
            for (int i = 0; i < annot.elements().size(); i++) {
                if (i > 0)
                    print(",");
                write(annot.elements().get(i), false);
            }
            print(")");
        }
    }

    public void write(TypeAnnotation annot, CodeAttribute lr) {
        write(annot, true, false, lr);
        println();
        indent(+1);
        write(annot, true);
        indent(-1);
    }

    public void write(TypeAnnotation annot, boolean showOffsets,
            boolean resolveIndices, CodeAttribute lr) {
        write(annot, resolveIndices);
        print(": ");
        write(annot.targetInfo(), annot.targetPath(), showOffsets, lr);
    }

    public void write(TypeAnnotation.TargetInfo targetInfo,
            List<TypeAnnotation.TypePathComponent> targetPath,
            boolean showOffsets, CodeAttribute lr) {
        print(targetInfo.targetType());

        switch (targetInfo) {
            // instanceof
            // new expression
            // constructor/method reference receiver
            case TypeAnnotation.OffsetTarget pos -> {
                if (showOffsets) {
                    print(", offset=");
                    print(lr.labelToBci(pos.target()));
                }
            }
            case TypeAnnotation.LocalVarTarget pos -> {
                if (pos.table().isEmpty()) {
                    print(", lvarOffset is Null!");
                    break;
                }
                print(", {");
                var table = pos.table();
                for (int i = 0; i < table.size(); ++i) {
                    var e = table.get(i);
                    if (i != 0) print("; ");
                    int startPc = lr.labelToBci(e.startLabel());
                    if (showOffsets) {
                        print("start_pc=");
                        print(startPc);
                    }
                    print(", length=");
                    print(lr.labelToBci(e.endLabel()) - startPc);
                    print(", index=");
                    print(e.index());
                }
                print("}");
            }
            case TypeAnnotation.CatchTarget pos -> {
                print(", exception_index=");
                print(pos.exceptionTableIndex());
            }
            case TypeAnnotation.TypeParameterTarget pos -> {
                print(", param_index=");
                print(pos.typeParameterIndex());
            }
            case TypeAnnotation.TypeParameterBoundTarget pos -> {
                print(", param_index=");
                print(pos.typeParameterIndex());
                print(", bound_index=");
                print(pos.boundIndex());
            }
            case TypeAnnotation.SupertypeTarget pos -> {
                print(", type_index=");
                print(pos.supertypeIndex());
            }
            case TypeAnnotation.ThrowsTarget pos -> {
                print(", type_index=");
                print(pos.throwsTargetIndex());
            }
            case TypeAnnotation.FormalParameterTarget pos -> {
                print(", param_index=");
                print(pos.formalParameterIndex());
            }
            case TypeAnnotation.TypeArgumentTarget pos -> {
                if (showOffsets) {
                    print(", offset=");
                    print(lr.labelToBci(pos.target()));
                }
                print(", type_index=");
                print(pos.typeArgumentIndex());
            }
            case TypeAnnotation.EmptyTarget pos -> {
                // Do nothing
            }
            default ->
                throw new AssertionError("AnnotationWriter: Unhandled target type: "
                        + targetInfo.getClass());
        }

        // Append location data for generics/arrays.
        if (!targetPath.isEmpty()) {
            print(", location=");
            print(targetPath.stream().map(tp -> tp.typePathKind().toString() +
                    (tp.typePathKind() == TypeAnnotation.TypePathComponent.Kind.TYPE_ARGUMENT
                            ? ("(" + tp.typeArgumentIndex() + ")")
                            : "")).toList());
        }
    }

    public void write(AnnotationElement pair, boolean resolveIndices) {
        writeIndex(pair.name(), resolveIndices);
        print("=");
        write(pair.value(), resolveIndices);
    }

    public void write(AnnotationValue value) {
        write(value, false);
        println();
        indent(+1);
        write(value, true);
        indent(-1);
    }

    private void writeDescriptor(Utf8Entry entry, boolean resolveIndices) {
        if (resolveIndices) {
            print(classWriter.sigPrinter.print(Signature.parseFrom(entry.stringValue())));
            return;
        }
        print("#" + entry.index());
    }

    private void writeIndex(PoolEntry entry, boolean resolveIndices) {
        if (resolveIndices) {
            print(constantWriter.stringValue(entry));
        } else
            print("#" + entry.index());
    }

    public void write(AnnotationValue value, boolean resolveIndices) {
        switch (value) {
            case AnnotationValue.OfConstant ev -> {
                if (resolveIndices) {
                    var entry = ev.constant();
                    switch (ev.tag()) {
                        case 'B':
                            print("(byte) ");
                            print(constantWriter.stringValue(entry));
                            break;
                        case 'C':
                            print("'");
                            print(constantWriter.charValue(entry));
                            print("'");
                            break;
                        case 'D':
                        case 'F':
                        case 'I':
                        case 'J':
                            print(constantWriter.stringValue(entry));
                            break;
                        case 'S':
                            print("(short) ");
                            print(constantWriter.stringValue(entry));
                            break;
                        case 'Z':
                            print(constantWriter.booleanValue(entry));
                            break;
                        case 's':
                            print("\"");
                            print(constantWriter.stringValue(entry));
                            print("\"");
                            break;
                        default:
                            print(ev.tag() + "#" + entry.index());
                            break;
                    }
                } else {
                    print(ev.tag() + "#" + ev.constant().index());
                }
            }
            case AnnotationValue.OfEnum ev -> {
                if (resolveIndices) {
                    writeIndex(ev.className(), resolveIndices);
                    print(".");
                    writeIndex(ev.constantName(), resolveIndices);
                } else {
                    print(ev.tag() + "#" + ev.className().index() + ".#"
                            + ev.constantName().index());
                }
            }
            case AnnotationValue.OfClass ev -> {
                if (resolveIndices) {
                    print("class ");
                    writeIndex(ev.className(), resolveIndices);
                } else {
                    print(ev.tag() + "#" + ev.className().index());
                }
            }
            case AnnotationValue.OfAnnotation ev -> {
                print(ev.tag());
                AnnotationWriter.this.write(ev.annotation(), resolveIndices);
            }
            case AnnotationValue.OfArray ev -> {
                print("[");
                for (int i = 0; i < ev.values().size(); i++) {
                    if (i > 0)
                        print(",");
                    write(ev.values().get(i), resolveIndices);
                }
                print("]");
            }
        }
    }

    private final ClassWriter classWriter;
    private final ConstantWriter constantWriter;
}
