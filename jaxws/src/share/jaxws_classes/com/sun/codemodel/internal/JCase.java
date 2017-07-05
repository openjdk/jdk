/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Case statement
 */
public final class JCase implements JStatement {

    /**
     * label part of the case statement
     */
    private JExpression label;

    /**
     * JBlock of statements which makes up body of this While statement
     */
    private JBlock body = null;

    /**
     * is this a regular case statement or a default case statement?
     */
    private boolean isDefaultCase = false;

    /**
     * Construct a case statement
     */
    JCase(JExpression label) {
        this(label, false);
    }

    /**
     * Construct a case statement.  If isDefaultCase is true, then
     * label should be null since default cases don't have a label.
     */
    JCase(JExpression label, boolean isDefaultCase) {
        this.label = label;
        this.isDefaultCase = isDefaultCase;
    }

    public JExpression label() {
        return label;
    }

    public JBlock body() {
        if (body == null) body=new JBlock( false, true );
        return body;
    }

    public void state(JFormatter f) {
        f.i();
        if( !isDefaultCase ) {
            f.p("case ").g(label).p(':').nl();
        } else {
            f.p("default:").nl();
        }
        if (body != null)
            f.s(body);
        f.o();
    }
}
