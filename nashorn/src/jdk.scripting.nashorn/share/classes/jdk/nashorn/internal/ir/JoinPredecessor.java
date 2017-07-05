/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.ir;

/**
 * Interface implemented by AST nodes that either can occur as predecessors of a control flow join, or contain a control
 * flow join themselves. JoinPredecessor only provides a getter and setter for a {@link LocalVariableConversion}; the
 * semantics of control flow for a particular node implementing the interface are shared between
 * {@code LocalVariableTypesCalculator} that creates the conversions, and {@code CodeGenerator} that uses them.
 */
public interface JoinPredecessor {
    /**
     * Set the local variable conversions needed to unify their types at a control flow join point.
     * @param lc the current lexical context
     * @param conversion the conversions.
     * @return this node or a different node representing the change.
     */
    public JoinPredecessor setLocalVariableConversion(LexicalContext lc, LocalVariableConversion conversion);

    /**
     * Returns the local variable conversions needed to unify their types at a control flow join point.
     * @return the local variable conversions needed to unify their types at a control flow join point. Can be null.
     * Can contain {@link LocalVariableConversion#isLive() dead conversions}.
     */
    public LocalVariableConversion getLocalVariableConversion();
}
