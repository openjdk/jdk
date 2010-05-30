/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc;


import com.sun.javadoc.*;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTags;


/**
 * Represents a value of an annotation type element.
 *
 * @author Scott Seligman
 * @since 1.5
 */

public class AnnotationValueImpl implements AnnotationValue {

    private final DocEnv env;
    private final Attribute attr;


    AnnotationValueImpl(DocEnv env, Attribute attr) {
        this.env = env;
        this.attr = attr;
    }

    /**
     * Returns the value.
     * The type of the returned object is one of the following:
     * <ul><li> a wrapper class for a primitive type
     *     <li> <code>String</code>
     *     <li> <code>Type</code> (representing a class literal)
     *     <li> <code>FieldDoc</code> (representing an enum constant)
     *     <li> <code>AnnotationDesc</code>
     *     <li> <code>AnnotationValue[]</code>
     * </ul>
     */
    public Object value() {
        ValueVisitor vv = new ValueVisitor();
        attr.accept(vv);
        return vv.value;
    }

    private class ValueVisitor implements Attribute.Visitor {
        public Object value;

        public void visitConstant(Attribute.Constant c) {
            if (c.type.tag == TypeTags.BOOLEAN) {
                // javac represents false and true as integers 0 and 1
                value = Boolean.valueOf(
                                ((Integer)c.value).intValue() != 0);
            } else {
                value = c.value;
            }
        }

        public void visitClass(Attribute.Class c) {
            value = TypeMaker.getType(env,
                                      env.types.erasure(c.type));
        }

        public void visitEnum(Attribute.Enum e) {
            value = env.getFieldDoc(e.value);
        }

        public void visitCompound(Attribute.Compound c) {
            value = new AnnotationDescImpl(env, c);
        }

        public void visitArray(Attribute.Array a) {
            AnnotationValue vals[] = new AnnotationValue[a.values.length];
            for (int i = 0; i < vals.length; i++) {
                vals[i] = new AnnotationValueImpl(env, a.values[i]);
            }
            value = vals;
        }

        public void visitError(Attribute.Error e) {
            value = "<error>";
        }
    }

    /**
     * Returns a string representation of the value.
     *
     * @return the text of a Java language annotation value expression
     *          whose value is the value of this annotation type element.
     */
    public String toString() {
        ToStringVisitor tv = new ToStringVisitor();
        attr.accept(tv);
        return tv.toString();
    }

    private class ToStringVisitor implements Attribute.Visitor {
        private final StringBuffer sb = new StringBuffer();

        public String toString() {
            return sb.toString();
        }

        public void visitConstant(Attribute.Constant c) {
            if (c.type.tag == TypeTags.BOOLEAN) {
                // javac represents false and true as integers 0 and 1
                sb.append(((Integer)c.value).intValue() != 0);
            } else {
                sb.append(FieldDocImpl.constantValueExpression(c.value));
            }
        }

        public void visitClass(Attribute.Class c) {
            sb.append(c);
        }

        public void visitEnum(Attribute.Enum e) {
            sb.append(e);
        }

        public void visitCompound(Attribute.Compound c) {
            sb.append(new AnnotationDescImpl(env, c));
        }

        public void visitArray(Attribute.Array a) {
            // Omit braces from singleton.
            if (a.values.length != 1) sb.append('{');

            boolean first = true;
            for (Attribute elem : a.values) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                elem.accept(this);
            }
            // Omit braces from singleton.
            if (a.values.length != 1) sb.append('}');
        }

        public void visitError(Attribute.Error e) {
            sb.append("<error>");
        }
    }
}
