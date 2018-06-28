/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.graph;

import static org.graalvm.compiler.graph.Edges.Type.Inputs;

import java.util.ArrayList;

import org.graalvm.compiler.graph.NodeClass.InputInfo;
import org.graalvm.compiler.nodeinfo.InputType;

public final class InputEdges extends Edges {

    private final InputType[] inputTypes;
    private final boolean[] isOptional;

    public InputEdges(int directCount, ArrayList<InputInfo> edges) {
        super(Inputs, directCount, edges);

        this.inputTypes = new InputType[edges.size()];
        this.isOptional = new boolean[edges.size()];
        for (int i = 0; i < edges.size(); i++) {
            this.inputTypes[i] = edges.get(i).inputType;
            this.isOptional[i] = edges.get(i).optional;
        }
    }

    public static void translateInto(InputEdges inputs, ArrayList<InputInfo> infos) {
        for (int index = 0; index < inputs.getCount(); index++) {
            infos.add(new InputInfo(inputs.offsets[index], inputs.getName(index), inputs.getType(index), inputs.getDeclaringClass(index), inputs.inputTypes[index], inputs.isOptional(index)));
        }
    }

    public InputType getInputType(int index) {
        return inputTypes[index];
    }

    public boolean isOptional(int index) {
        return isOptional[index];
    }

    @Override
    public void update(Node node, Node oldValue, Node newValue) {
        node.updateUsages(oldValue, newValue);
    }
}
