/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.api.tree;

/**
 * A tree node for a basic 'for' loop statement.
 *
 * For example:
 * <pre>
 *   for ( <em>initializer</em> ; <em>condition</em> ; <em>update</em> )
 *       <em>statement</em>
 * </pre>
 *
 * @since 9
 */
public interface ForLoopTree extends ConditionalLoopTree {
    /**
     * Returns the initializer expression of this 'for' statement.
     *
     * @return the initializer expression
     */
    ExpressionTree getInitializer();

    /**
     * Returns the condition expression of this 'for' statement.
     *
     * @return the condition expression
     */
    @Override
    ExpressionTree getCondition();

    /**
     * Returns the update expression of this 'for' statement.
     *
     * @return the update expression
     */
    ExpressionTree getUpdate();

    /**
     * Returns the statement contained in this 'for' statement.
     *
     * @return the statement
     */
    @Override
    StatementTree getStatement();
}
