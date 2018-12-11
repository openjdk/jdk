/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.nodeinfo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker type for describing node inputs in snippets that are not of type {@link InputType#Value}.
 */
public abstract class StructuralInput {

    private StructuralInput() {
        throw new Error("Illegal instance of StructuralInput. This class should be used in snippets only.");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    public @interface MarkerType {
        InputType value();
    }

    /**
     * Marker type for {@link InputType#Memory} edges in snippets.
     */
    @MarkerType(InputType.Memory)
    public abstract static class Memory extends StructuralInput {
    }

    /**
     * Marker type for {@link InputType#Condition} edges in snippets.
     */
    @MarkerType(InputType.Condition)
    public abstract static class Condition extends StructuralInput {
    }

    /**
     * Marker type for {@link InputType#State} edges in snippets.
     */
    @MarkerType(InputType.State)
    public abstract static class State extends StructuralInput {
    }

    /**
     * Marker type for {@link InputType#Guard} edges in snippets.
     */
    @MarkerType(InputType.Guard)
    public abstract static class Guard extends StructuralInput {
    }

    /**
     * Marker type for {@link InputType#Anchor} edges in snippets.
     */
    @MarkerType(InputType.Anchor)
    public abstract static class Anchor extends StructuralInput {
    }

    /**
     * Marker type for {@link InputType#Association} edges in snippets.
     */
    @MarkerType(InputType.Association)
    public abstract static class Association extends StructuralInput {
    }

    /**
     * Marker type for {@link InputType#Extension} edges in snippets.
     */
    @MarkerType(InputType.Extension)
    public abstract static class Extension extends StructuralInput {
    }
}
