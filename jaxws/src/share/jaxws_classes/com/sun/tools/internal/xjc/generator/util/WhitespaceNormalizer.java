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

package com.sun.tools.internal.xjc.generator.util;

import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JExpression;
import com.sun.codemodel.internal.JStringLiteral;
import com.sun.xml.internal.bind.WhiteSpaceProcessor;

/**
 * Generates code that performs the whitespace normalization.
 */
public abstract class WhitespaceNormalizer
{
    /**
     * Generates the expression that normalizes
     * the given expression (which evaluates to java.lang.String).
     *
     * @param codeModel
     *      The owner code model object under which a new expression
     *      will be created.
     */
    public abstract JExpression generate( JCodeModel codeModel, JExpression literal );

    /**
     * Parses "preserve","replace" or "collapse" into
     * the corresponding WhitespaceNormalizer object.
     *
     * @param method
     *      Either "preserve", "replace", or "collapse"
     *
     * @exception    IllegalArgumentException
     *        when the specified method is invalid.
     */
    public static WhitespaceNormalizer parse( String method ) {
        if( method.equals("preserve") )
            return PRESERVE;

        if( method.equals("replace") )
            return REPLACE;

        if( method.equals("collapse") )
            return COLLAPSE;

        throw new IllegalArgumentException(method);
    }

    public static final WhitespaceNormalizer PRESERVE = new WhitespaceNormalizer() {
        public JExpression generate( JCodeModel codeModel, JExpression literal ) {
            return literal;
        }
    };

    public static final WhitespaceNormalizer REPLACE = new WhitespaceNormalizer() {
        public JExpression generate( JCodeModel codeModel, JExpression literal ) {
            // WhitespaceProcessor.replace(<literal>);
            if( literal instanceof JStringLiteral )
                // optimize
                return JExpr.lit( WhiteSpaceProcessor.replace(((JStringLiteral)literal).str) );
            else
                return codeModel.ref(WhiteSpaceProcessor.class)
                    .staticInvoke("replace").arg(literal);
        }
    };

    public static final WhitespaceNormalizer COLLAPSE = new WhitespaceNormalizer() {
        public JExpression generate( JCodeModel codeModel, JExpression literal ) {
            // WhitespaceProcessor.replace(<literal>);
            if( literal instanceof JStringLiteral )
                // optimize
                return JExpr.lit( WhiteSpaceProcessor.collapse(((JStringLiteral)literal).str) );
            else
                return codeModel.ref(WhiteSpaceProcessor.class)
                    .staticInvoke("collapse").arg(literal);
        }
    };
}
