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
 * ForEach Statement
 * This will generate the code for statement based on the new
 * j2se 1.5 j.l.s.
 *
 * @author Bhakti
 */
public final class JForEach implements JStatement {

    private final JType type;
    private final String var;
    private JBlock body = null; // lazily created
    private final JExpression collection;
    private final JVar loopVar;

    public JForEach(JType vartype, String variable, JExpression collection) {

        this.type = vartype;
        this.var = variable;
        this.collection = collection;
        loopVar = new JVar(JMods.forVar(JMod.NONE), type, var, collection);
    }


    /**
     * Returns a reference to the loop variable.
     */
    public JVar var() {
        return loopVar;
    }

    public JBlock body() {
        if (body == null)
            body = new JBlock();
        return body;
    }

    public void state(JFormatter f) {
        f.p("for (");
        f.g(type).id(var).p(": ").g(collection);
        f.p(')');
        if (body != null)
            f.g(body).nl();
        else
            f.p(';').nl();
    }

}
