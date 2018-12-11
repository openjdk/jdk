/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.match;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.compiler.nodes.ValueNode;

/**
 * Describes the properties of a node for use when building a {@link MatchPattern}. These
 * declarations are required when parsing a {@link MatchRule}. They are expected to be found on a
 * super type of the holder of the method declaring the {@link MatchRule}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(value = MatchableNodes.class)
public @interface MatchableNode {

    /**
     * The {@link ValueNode} subclass this annotation describes. These annotations might work better
     * if they were directly on the node being described but that may complicate the annotation
     * processing.
     */
    Class<? extends ValueNode> nodeClass();

    /**
     * The names of the inputs in the order they should appear in the match.
     */
    String[] inputs() default {};

    /**
     * Can a pattern be matched with the operands swapped. This will cause swapped versions of
     * patterns to be automatically generated.
     */
    boolean commutative() default false;

    /**
     * Can a node with multiple uses be safely matched by a rule.
     */
    boolean shareable() default false;
}
