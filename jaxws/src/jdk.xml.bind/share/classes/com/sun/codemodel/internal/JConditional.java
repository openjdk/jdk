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
 * If statement, with optional else clause
 */

public class JConditional implements JStatement {

    /**
     * JExpression to test to determine branching
     */
    private JExpression test = null;

    /**
     * JBlock of statements for "then" clause
     */
    private JBlock _then = new JBlock();

    /**
     * JBlock of statements for optional "else" clause
     */
    private JBlock _else = null;

    /**
     * Constructor
     *
     * @param test
     *        JExpression which will determine branching
     */
    JConditional(JExpression test) {
       this.test = test;
    }

    /**
     * Return the block to be excuted by the "then" branch
     *
     * @return Then block
     */
    public JBlock _then() {
        return _then;
    }

    /**
     * Create a block to be executed by "else" branch
     *
     * @return Newly generated else block
     */
    public JBlock _else() {
        if (_else == null) _else = new JBlock();
        return _else;
    }

    /**
     * Creates {@code ... else if(...) ...} code.
     */
    public JConditional _elseif(JExpression boolExp) {
        return _else()._if(boolExp);
    }

    public void state(JFormatter f) {
        if(test==JExpr.TRUE) {
            _then.generateBody(f);
            return;
        }
        if(test==JExpr.FALSE) {
            _else.generateBody(f);
            return;
        }

        if (JOp.hasTopOp(test)) {
            f.p("if ").g(test);
        } else {
            f.p("if (").g(test).p(')');
        }
        f.g(_then);
        if (_else != null)
            f.p("else").g(_else);
        f.nl();
    }
}
