/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;

import java.util.Objects;

import java.lang.classfile.Label;
import java.lang.classfile.instruction.LabelTarget;

/**
 * Labels are created with a parent context, which is either a code attribute
 * or a code builder.  A label originating in a code attribute context may be
 * reused in a code builder context, but only labels from a single code
 * attribute may be reused by a single code builder.  Mappings to and from
 * BCI are the responsibility of the context in which it is used; a single
 * word of mutable state is provided, for the exclusive use of the owning
 * context.
 *
 * In practice, this means that labels created in a code attribute can simply
 * store the BCI in the state on creation, and labels created in in a code
 * builder can store the BCI in the state when the label is eventually set; if
 * a code attribute label is reused in a builder, the original BCI can be used
 * as an index into an array.
 */
public final class LabelImpl
        extends AbstractElement
        implements Label, LabelTarget {

    private final LabelContext labelContext;
    private int bci;

    public LabelImpl(LabelContext labelContext, int bci) {
        this.labelContext = Objects.requireNonNull(labelContext);
        this.bci = bci;
    }

    public LabelContext labelContext() {
        return labelContext;
    }

    public int getBCI() {
        return bci;
    }

    public void setBCI(int bci) {
        this.bci = bci;
    }

    @Override
    public Label label() {
        return this;
    }

    @Override
    public void writeTo(DirectCodeBuilder builder) {
        builder.setLabelTarget(this);
    }

    @Override
    public String toString() {
        return String.format("Label[context=%s, bci=%d]", labelContext, bci);
    }
}
