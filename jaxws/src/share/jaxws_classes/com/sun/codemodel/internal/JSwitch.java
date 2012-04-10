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
import java.util.Iterator;
import java.util.List;

/**
 * Switch statement
 */
public final class JSwitch implements JStatement {

    /**
     * Test part of switch statement.
     */
    private JExpression test;

    /**
     * vector of JCases.
     */
    private List<JCase> cases = new ArrayList<JCase>();

    /**
     * a single default case
     */
    private JCase defaultCase = null;

    /**
     * Construct a While statment
     */
    JSwitch(JExpression test) {
        this.test = test;
    }

    public JExpression test() { return test; }

    public Iterator<JCase> cases() { return cases.iterator(); }

    public JCase _case( JExpression label ) {
        JCase c = new JCase( label );
        cases.add(c);
        return c;
    }

    public JCase _default() {
        // what if (default != null) ???

        // default cases statements don't have a label
        defaultCase = new JCase(null, true);
        return defaultCase;
    }

    public void state(JFormatter f) {
        if (JOp.hasTopOp(test)) {
            f.p("switch ").g(test).p(" {").nl();
        } else {
            f.p("switch (").g(test).p(')').p(" {").nl();
        }
        for( JCase c : cases )
            f.s(c);
        if( defaultCase != null )
            f.s( defaultCase );
        f.p('}').nl();
    }

}
