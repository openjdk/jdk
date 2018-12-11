/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes;

import static java.lang.Character.toLowerCase;

import java.util.ArrayList;
import java.util.Collection;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.memory.MemoryNode;

import jdk.vm.ci.meta.JavaKind;

public class ValueNodeUtil {

    public static ValueNode assertKind(JavaKind kind, ValueNode x) {
        assert x != null && x.getStackKind() == kind : "kind=" + kind + ", value=" + x + ((x == null) ? "" : ", value.kind=" + x.getStackKind());
        return x;
    }

    public static RuntimeException shouldNotReachHere(String msg) {
        throw new InternalError("should not reach here: " + msg);
    }

    public static RuntimeException shouldNotReachHere() {
        throw new InternalError("should not reach here");
    }

    public static ValueNode assertLong(ValueNode x) {
        assert x != null && (x.getStackKind() == JavaKind.Long);
        return x;
    }

    public static ValueNode assertInt(ValueNode x) {
        assert x != null && (x.getStackKind() == JavaKind.Int);
        return x;
    }

    public static ValueNode assertFloat(ValueNode x) {
        assert x != null && (x.getStackKind() == JavaKind.Float);
        return x;
    }

    public static ValueNode assertObject(ValueNode x) {
        assert x != null && (x.getStackKind() == JavaKind.Object);
        return x;
    }

    public static ValueNode assertDouble(ValueNode x) {
        assert x != null && (x.getStackKind() == JavaKind.Double);
        return x;
    }

    public static void assertHigh(ValueNode x) {
        assert x == null;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Node> Collection<T> filter(Iterable<Node> nodes, Class<T> clazz) {
        ArrayList<T> phis = new ArrayList<>();
        for (Node node : nodes) {
            if (clazz.isInstance(node)) {
                phis.add((T) node);
            }
        }
        return phis;
    }

    /**
     * Converts a given instruction to a value string. The representation of an node as a value is
     * formed by concatenating the {@linkplain jdk.vm.ci.meta.JavaKind#getTypeChar character}
     * denoting its {@linkplain ValueNode#getStackKind kind} and its id. For example, {@code "i13"}.
     *
     * @param value the instruction to convert to a value string. If {@code value == null}, then "-"
     *            is returned.
     * @return the instruction representation as a string
     */
    public static String valueString(ValueNode value) {
        return (value == null) ? "-" : ("" + toLowerCase(value.getStackKind().getTypeChar()) + value.toString(Verbosity.Id));
    }

    public static ValueNode asNode(MemoryNode node) {
        if (node == null) {
            return null;
        } else {
            return node.asNode();
        }
    }
}
