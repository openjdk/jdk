/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.codemodel.internal;


/**
 * Java built-in primitive types.
 *
 * Instances of this class can be obtained as constants of {@link JCodeModel},
 * such as {@link JCodeModel#BOOLEAN}.
 */
public final class JPrimitiveType extends JType {

    private final String typeName;
    private final JCodeModel owner;
    /**
     * Corresponding wrapper class.
     * For example, this would be "java.lang.Short" for short.
     */
    private final JClass wrapperClass;

    JPrimitiveType(JCodeModel owner, String typeName, Class wrapper ) {
        this.owner = owner;
        this.typeName = typeName;
        this.wrapperClass = owner.ref(wrapper);
    }

    public JCodeModel owner() { return owner; }

    public String fullName() {
        return typeName;
    }

    public String name() {
        return fullName();
    }

    public boolean isPrimitive() {
        return true;
    }

    private JClass arrayClass;
    public JClass array() {
        if(arrayClass==null)
            arrayClass = new JArrayClass(owner,this);
        return arrayClass;
    }

    /**
     * Obtains the wrapper class for this primitive type.
     * For example, this method returns a reference to java.lang.Integer
     * if this object represents int.
     */
    public JClass boxify() {
        return wrapperClass;
    }

    /**
     * @deprecated calling this method from {@link JPrimitiveType}
     * would be meaningless, since it's always guaranteed to
     * return <tt>this</tt>.
     */
    public JType unboxify() {
        return this;
    }

    /**
     * @deprecated
     *      Use {@link #boxify()}.
     */
    public JClass getWrapperClass() {
        return boxify();
    }

    /**
     * Wraps an expression of this type to the corresponding wrapper class.
     * For example, if this class represents "float", this method will return
     * the expression <code>new Float(x)</code> for the paramter x.
     *
     * REVISIT: it's not clear how this method works for VOID.
     */
    public JExpression wrap( JExpression exp ) {
        return JExpr._new(boxify()).arg(exp);
    }

    /**
     * Do the opposite of the wrap method.
     *
     * REVISIT: it's not clear how this method works for VOID.
     */
    public JExpression unwrap( JExpression exp ) {
        // it just so happens that the unwrap method is always
        // things like "intValue" or "booleanValue".
        return exp.invoke(typeName+"Value");
    }

    public void generate(JFormatter f) {
        f.p(typeName);
    }
}
