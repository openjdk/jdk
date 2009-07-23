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
    }

    public void write(Annotation annot) {
        print("#" + annot.type_index + "(");
        for (int i = 0; i < annot.num_element_value_pairs; i++) {
            if (i > 0)
                print(",");
            write(annot.element_value_pairs[i]);
        }
        print(")");
    }

    public void write(ExtendedAnnotation annot) {
        write(annot.annotation);
        print('@');
        print(annot.position.toString());
    }

    public void write(Annotation.element_value_pair pair) {
        print("#" + pair.element_name_index + ":");
        write(pair.value);
    }

    public void write(Annotation.element_value value) {
        ev_writer.write(value);
    }

    element_value_Writer ev_writer = new element_value_Writer();

    class element_value_Writer implements Annotation.element_value.Visitor<Void,Void> {
        public void write(Annotation.element_value value) {
            value.accept(this, null);
        }

        public Void visitPrimitive(Primitive_element_value ev, Void p) {
            print(((char) ev.tag) + "#" + ev.const_value_index);
            return null;
        }

        public Void visitEnum(Enum_element_value ev, Void p) {
            print(((char) ev.tag) + "#" + ev.type_name_index + ".#" + ev.const_name_index);
            return null;
        }

        public Void visitClass(Class_element_value ev, Void p) {
            print(((char) ev.tag) + "#" + ev.class_info_index);
            return null;
        }

        public Void visitAnnotation(Annotation_element_value ev, Void p) {
            print((char) ev.tag);
            AnnotationWriter.this.write(ev.annotation_value);
            return null;
        }

        public Void visitArray(Array_element_value ev, Void p) {
            print("[");
            for (int i = 0; i < ev.num_values; i++) {
                if (i > 0)
                    print(",");
                write(ev.values[i]);
            }
            print("]");
            return null;
        }

    }
}
