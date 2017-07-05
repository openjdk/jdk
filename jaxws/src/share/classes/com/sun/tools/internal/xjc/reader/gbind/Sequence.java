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

package com.sun.tools.internal.xjc.reader.gbind;

/**
 * {@link Expression} that represents a concatanation of two expressions
 * "A,B".
 *
 * @author Kohsuke Kawaguchi
 */
public final class Sequence extends Expression {
    /**
     * 'A' of 'A,B'
     */
    private final Expression lhs;
    /**
     * 'B' of 'A,B'
     */
    private final Expression rhs;
    /**
     * Compute this value eagerly for better performance
     */
    private final boolean isNullable;

    /**
     * Cached value of {@link #lastSet()} for better performance.
     * Sequence tends to be where the recursive lastSet computation occurs.
     */
    private ElementSet lastSet;

    public Sequence(Expression lhs, Expression rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
        isNullable = lhs.isNullable() && rhs.isNullable();
    }

    ElementSet lastSet() {
        if(lastSet==null) {
            if(rhs.isNullable())
                lastSet = ElementSets.union(lhs.lastSet(),rhs.lastSet());
            else
                lastSet = rhs.lastSet();
        }
        return lastSet;
    }

    boolean isNullable() {
        return isNullable;
    }

    void buildDAG(ElementSet incoming) {
        lhs.buildDAG(incoming);
        if(lhs.isNullable())
            rhs.buildDAG(ElementSets.union(incoming,lhs.lastSet()));
        else
            rhs.buildDAG(lhs.lastSet());
    }

    public String toString() {
        return '('+lhs.toString()+','+rhs.toString()+')';
    }
}
