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
 * A Java expression.
 *
 * <p>
 * Unlike most of CodeModel, JExpressions are built bottom-up (
 * meaning you start from leaves and then gradually build compliated expressions
 * by combining them.)
 *
 * <p>
 * {@link JExpression} defines a series of composer methods,
 * which returns a complicated expression (by often taking other {@link JExpression}s
 * as parameters.
 * For example, you can build "5+2" by
 * {@code JExpr.lit(5).add(JExpr.lit(2))}
 */
public interface JExpression extends JGenerable {
    /**
     * Returns "-[this]" from "[this]".
     */
    JExpression minus();

    /**
     * Returns "![this]" from "[this]".
     */
    JExpression not();
    /**
     * Returns "~[this]" from "[this]".
     */
    JExpression complement();

    /**
     * Returns "[this]++" from "[this]".
     */
    JExpression incr();

    /**
     * Returns "[this]--" from "[this]".
     */
    JExpression decr();

    /**
     * Returns "[this]+[right]"
     */
    JExpression plus(JExpression right);

    /**
     * Returns "[this]-[right]"
     */
    JExpression minus(JExpression right);

    /**
     * Returns "[this]*[right]"
     */
    JExpression mul(JExpression right);

    /**
     * Returns "[this]/[right]"
     */
    JExpression div(JExpression right);

    /**
     * Returns "[this]%[right]"
     */
    JExpression mod(JExpression right);

    /**
     * Returns "[this]&lt;&lt;[right]"
     */
    JExpression shl(JExpression right);

    /**
     * Returns "[this]>>[right]"
     */
    JExpression shr(JExpression right);

    /**
     * Returns "[this]>>>[right]"
     */
    JExpression shrz(JExpression right);

    /** Bit-wise AND '&amp;'. */
    JExpression band(JExpression right);

    /** Bit-wise OR '|'. */
    JExpression bor(JExpression right);

    /** Logical AND '&amp;&amp;'. */
    JExpression cand(JExpression right);

    /** Logical OR '||'. */
    JExpression cor(JExpression right);

    JExpression xor(JExpression right);
    JExpression lt(JExpression right);
    JExpression lte(JExpression right);
    JExpression gt(JExpression right);
    JExpression gte(JExpression right);
    JExpression eq(JExpression right);
    JExpression ne(JExpression right);

    /**
     * Returns "[this] instanceof [right]"
     */
    JExpression _instanceof(JType right);

    /**
     * Returns "[this].[method]".
     *
     * Arguments shall be added to the returned {@link JInvocation} object.
     */
    JInvocation invoke(JMethod method);

    /**
     * Returns "[this].[method]".
     *
     * Arguments shall be added to the returned {@link JInvocation} object.
     */
    JInvocation invoke(String method);
    JFieldRef ref(JVar field);
    JFieldRef ref(String field);
    JArrayCompRef component(JExpression index);
}
