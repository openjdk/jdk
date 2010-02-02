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

import com.sun.tools.classfile.Annotation;
import com.sun.tools.classfile.ExtendedAnnotation;
import com.sun.tools.classfile.Annotation.Annotation_element_value;
import com.sun.tools.classfile.Annotation.Array_element_value;
import com.sun.tools.classfile.Annotation.Class_element_value;
import com.sun.tools.classfile.Annotation.Enum_element_value;
import com.sun.tools.classfile.Annotation.Primitive_element_value;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Descriptor;
import com.sun.tools.classfile.Descriptor.InvalidDescriptor;

/**
 *  A writer for writing annotations as text.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
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
    }

    public void write(Annotation annot, boolean resolveIndices) {
        writeDescriptor(annot.type_index, resolveIndices);
        boolean showParens = annot.num_element_value_pairs > 0 || !resolveIndices;
        if (showParens)
            print("(");
        for (int i = 0; i < annot.num_element_value_pairs; i++) {
            if (i > 0)
                print(",");
            write(annot.element_value_pairs[i], resolveIndices);
        }
        if (showParens)
            print(")");
    }

    public void write(ExtendedAnnotation annot) {
        write(annot, true, false);
    }

    public void write(ExtendedAnnotation annot, boolean showOffsets, boolean resolveIndices) {
        write(annot.annotation, resolveIndices);
        print(": ");
        write(annot.position, showOffsets);
    }

    public void write(ExtendedAnnotation.Position pos, boolean showOffsets) {
        print(pos.type);

        switch (pos.type) {
        // type case
        case TYPECAST:
        case TYPECAST_GENERIC_OR_ARRAY:
        // object creation
        case INSTANCEOF:
        case INSTANCEOF_GENERIC_OR_ARRAY:
        // new expression
        case NEW:
        case NEW_GENERIC_OR_ARRAY:
        case NEW_TYPE_ARGUMENT:
        case NEW_TYPE_ARGUMENT_GENERIC_OR_ARRAY:
            if (showOffsets) {
                print(", offset=");
                print(pos.offset);
            }
            break;
         // local variable
        case LOCAL_VARIABLE:
        case LOCAL_VARIABLE_GENERIC_OR_ARRAY:
            print(", {");
            for (int i = 0; i < pos.lvarOffset.length; ++i) {
                if (i != 0) print("; ");
                if (showOffsets) {
                    print(", start_pc=");
                    print(pos.lvarOffset[i]);
                }
                print(", length=");
                print(pos.lvarLength[i]);
                print(", index=");
                print(pos.lvarIndex[i]);
            }
            print("}");
            break;
         // method receiver
        case METHOD_RECEIVER:
            // Do nothing
            break;
        // type parameters
        case CLASS_TYPE_PARAMETER:
        case METHOD_TYPE_PARAMETER:
            print(", param_index=");
            print(pos.parameter_index);
            break;
        // type parameters bound
        case CLASS_TYPE_PARAMETER_BOUND:
        case CLASS_TYPE_PARAMETER_BOUND_GENERIC_OR_ARRAY:
        case METHOD_TYPE_PARAMETER_BOUND:
        case METHOD_TYPE_PARAMETER_BOUND_GENERIC_OR_ARRAY:
            print(", param_index=");
            print(pos.parameter_index);
            print(", bound_index=");
            print(pos.bound_index);
            break;
         // wildcard
        case WILDCARD_BOUND:
        case WILDCARD_BOUND_GENERIC_OR_ARRAY:
            print(", wild_card=");
            print(pos.wildcard_position);
            break;
         // Class extends and implements clauses
        case CLASS_EXTENDS:
        case CLASS_EXTENDS_GENERIC_OR_ARRAY:
            print(", type_index=");
            print(pos.type_index);
            break;
        // throws
        case THROWS:
            print(", type_index=");
            print(pos.type_index);
            break;
        case CLASS_LITERAL:
        case CLASS_LITERAL_GENERIC_OR_ARRAY:
            if (showOffsets) {
                print(", offset=");
                print(pos.offset);
            }
            break;
        // method parameter: not specified
        case METHOD_PARAMETER_GENERIC_OR_ARRAY:
            print(", param_index=");
            print(pos.parameter_index);
            break;
        // method type argument: wasn't specified
        case METHOD_TYPE_ARGUMENT:
        case METHOD_TYPE_ARGUMENT_GENERIC_OR_ARRAY:
            if (showOffsets) {
                print(", offset=");
                print(pos.offset);
            }
            print(", type_index=");
            print(pos.type_index);
            break;
        // We don't need to worry abut these
        case METHOD_RETURN_GENERIC_OR_ARRAY:
        case FIELD_GENERIC_OR_ARRAY:
            break;
        case UNKNOWN:
            break;
        default:
            throw new AssertionError("unknown type: " + pos.type);
        }

        // Append location data for generics/arrays.
        if (pos.type.hasLocation()) {
            print(", location=");
            print(pos.location);
        }
    }

    public void write(Annotation.element_value_pair pair) {
        write(pair, false);
    }

    public void write(Annotation.element_value_pair pair, boolean resolveIndices) {
        writeIndex(pair.element_name_index, resolveIndices);
        print("=");
        write(pair.value, resolveIndices);
    }

    public void write(Annotation.element_value value) {
        write(value, false);
    }

    public void write(Annotation.element_value value, boolean resolveIndices) {
        ev_writer.write(value, resolveIndices);
    }

    private void writeDescriptor(int index, boolean resolveIndices) {
        if (resolveIndices) {
            try {
                ConstantPool constant_pool = classWriter.getClassFile().constant_pool;
                Descriptor d = new Descriptor(index);
                print(d.getFieldType(constant_pool));
                return;
            } catch (ConstantPoolException ignore) {
            } catch (InvalidDescriptor ignore) {
            }
        }

        print("#" + index);
    }

    private void writeIndex(int index, boolean resolveIndices) {
        if (resolveIndices) {
            print(constantWriter.stringValue(index));
        } else
            print("#" + index);
    }

    element_value_Writer ev_writer = new element_value_Writer();

    class element_value_Writer implements Annotation.element_value.Visitor<Void,Boolean> {
        public void write(Annotation.element_value value, boolean resolveIndices) {
            value.accept(this, resolveIndices);
        }

        public Void visitPrimitive(Primitive_element_value ev, Boolean resolveIndices) {
            if (resolveIndices)
                writeIndex(ev.const_value_index, resolveIndices);
            else
                print(((char) ev.tag) + "#" + ev.const_value_index);
            return null;
        }

        public Void visitEnum(Enum_element_value ev, Boolean resolveIndices) {
            if (resolveIndices) {
                writeIndex(ev.type_name_index, resolveIndices);
                print(".");
                writeIndex(ev.const_name_index, resolveIndices);
            } else
                print(((char) ev.tag) + "#" + ev.type_name_index + ".#" + ev.const_name_index);
            return null;
        }

        public Void visitClass(Class_element_value ev, Boolean resolveIndices) {
            if (resolveIndices) {
                writeIndex(ev.class_info_index, resolveIndices);
                print(".class");
            } else
                print(((char) ev.tag) + "#" + ev.class_info_index);
            return null;
        }

        public Void visitAnnotation(Annotation_element_value ev, Boolean resolveIndices) {
            print((char) ev.tag);
            AnnotationWriter.this.write(ev.annotation_value, resolveIndices);
            return null;
        }

        public Void visitArray(Array_element_value ev, Boolean resolveIndices) {
            print("[");
            for (int i = 0; i < ev.num_values; i++) {
                if (i > 0)
                    print(",");
                write(ev.values[i], resolveIndices);
            }
            print("]");
            return null;
        }

    }

    private ClassWriter classWriter;
    private ConstantWriter constantWriter;
}
