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

package com.sun.tools.internal.xjc.reader.gbind;

import java.util.Set;

/**
 * This builds content models.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Expression {

    /**
     * Computes {@code LAST(exp)}
     */
    abstract ElementSet lastSet();

    /**
     * True of {@code \epsilon \in L(exp)}
     */
    abstract boolean isNullable();

    /**
     * Builds up a DAG among {@link Element}s in this expression.
     */
    abstract void buildDAG(ElementSet incoming);

    /**
     * {@link Expression} that represents epsilon, the length-0 string.
     */
    public static final Expression EPSILON = new Expression() {
        ElementSet lastSet() {
            return ElementSet.EMPTY_SET;
        }

        boolean isNullable() {
            return true;
        }

        void buildDAG(ElementSet incoming) {
            // noop
        }

        public String toString() {
            return "-";
        }
    };
}
