/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.codemodel.internal;

import java.util.ArrayList;
import java.util.List;


/**
 * For statement
 */

public class JForLoop implements JStatement {

    private List<Object> inits = new ArrayList<Object>();
    private JExpression test = null;
    private List<JExpression> updates = new ArrayList<JExpression>();
    private JBlock body = null;

    public JVar init(int mods, JType type, String var, JExpression e) {
        JVar v = new JVar(JMods.forVar(mods), type, var, e);
        inits.add(v);
        return v;
    }

    public JVar init(JType type, String var, JExpression e) {
        return init(JMod.NONE, type, var, e);
    }

    public void init(JVar v, JExpression e) {
        inits.add(JExpr.assign(v, e));
    }

    public void test(JExpression e) {
        this.test = e;
    }

    public void update(JExpression e) {
        updates.add(e);
    }

    public JBlock body() {
        if (body == null) body = new JBlock();
        return body;
    }

    public void state(JFormatter f) {
        f.p("for (");
        boolean first = true;
        for (Object o : inits) {
            if (!first) f.p(',');
            if (o instanceof JVar)
                f.b((JVar) o);
            else
                f.g((JExpression) o);
            first = false;
        }
        f.p(';').g(test).p(';').g(updates).p(')');
        if (body != null)
            f.g(body).nl();
        else
            f.p(';').nl();
    }

}
