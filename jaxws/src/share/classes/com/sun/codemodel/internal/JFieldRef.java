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
 * Field Reference
 */

public class JFieldRef extends JExpressionImpl implements JAssignmentTarget {
    /**
     * Object expression upon which this field will be accessed, or
     * null for the implicit 'this'.
     */
    private JGenerable object;

    /**
     * Name of the field to be accessed. Either this or {@link #var} is set.
     */
    private String name;

    /**
     * Variable to be accessed.
     */
    private JVar var;

    /**
     * Indicates if an explicit this should be generated
     */
    private boolean explicitThis;

    /**
     * Field reference constructor given an object expression and field name
     *
     * @param object
     *        JExpression for the object upon which
     *        the named field will be accessed,
     *
     * @param name
     *        Name of field to access
     */
    JFieldRef(JExpression object, String name) {
        this(object, name, false);
    }

    JFieldRef(JExpression object, JVar v) {
        this(object, v, false);
    }

    /**
     * Static field reference.
     */
    JFieldRef(JType type, String name) {
        this(type, name, false);
    }

    JFieldRef(JType type, JVar v) {
        this(type, v, false);
    }

    JFieldRef(JGenerable object, String name, boolean explicitThis) {
        this.explicitThis = explicitThis;
        this.object = object;
        if (name.indexOf('.') >= 0)
            throw new IllegalArgumentException("Field name contains '.': " + name);
        this.name = name;
    }

    JFieldRef(JGenerable object, JVar var, boolean explicitThis) {
        this.explicitThis = explicitThis;
        this.object = object;
        this.var = var;
    }

    public void generate(JFormatter f) {
        String name = this.name;
        if(name==null)  name=var.name();

        if (object != null) {
            f.g(object).p('.').p(name);
        } else {
            if (explicitThis) {
                f.p("this.").p(name);
            } else {
                f.id(name);
            }
        }
    }

    public JExpression assign(JExpression rhs) {
        return JExpr.assign(this, rhs);
    }
    public JExpression assignPlus(JExpression rhs) {
        return JExpr.assignPlus(this, rhs);
    }
}
