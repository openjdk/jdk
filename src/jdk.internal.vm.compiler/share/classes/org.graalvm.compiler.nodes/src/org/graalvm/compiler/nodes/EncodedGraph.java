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


package org.graalvm.compiler.nodes;

import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.graph.NodeClass;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A {@link StructuredGraph} encoded in a compact binary representation as a byte[] array. See
 * {@link GraphEncoder} for a description of the encoding format. Use {@link GraphDecoder} for
 * decoding.
 */
public class EncodedGraph {

    private final byte[] encoding;
    private final int startOffset;
    protected final Object[] objects;
    private final NodeClass<?>[] types;
    private final Assumptions assumptions;
    private final List<ResolvedJavaMethod> inlinedMethods;
    private final boolean trackNodeSourcePosition;
    private final EconomicSet<ResolvedJavaField> fields;
    private final boolean hasUnsafeAccess;

    /**
     * The "table of contents" of the encoded graph, i.e., the mapping from orderId numbers to the
     * offset in the encoded byte[] array. Used as a cache during decoding.
     */
    protected int[] nodeStartOffsets;

    public EncodedGraph(byte[] encoding, int startOffset, Object[] objects, NodeClass<?>[] types, StructuredGraph sourceGraph) {
        this(encoding, startOffset, objects, types, sourceGraph.getAssumptions(), sourceGraph.getMethods(), sourceGraph.getFields(), sourceGraph.hasUnsafeAccess(),
                        sourceGraph.trackNodeSourcePosition());
    }

    public EncodedGraph(byte[] encoding, int startOffset, Object[] objects, NodeClass<?>[] types, Assumptions assumptions, List<ResolvedJavaMethod> inlinedMethods,
                    EconomicSet<ResolvedJavaField> fields, boolean hasUnsafeAccess, boolean trackNodeSourcePosition) {
        this.encoding = encoding;
        this.startOffset = startOffset;
        this.objects = objects;
        this.types = types;
        this.assumptions = assumptions;
        this.inlinedMethods = inlinedMethods;
        this.trackNodeSourcePosition = trackNodeSourcePosition;
        this.fields = fields;
        this.hasUnsafeAccess = hasUnsafeAccess;
    }

    public byte[] getEncoding() {
        return encoding;
    }

    public int getStartOffset() {
        return startOffset;
    }

    protected Object[] getObjects() {
        return objects;
    }

    public int getNumObjects() {
        return objects.length;
    }

    public Object getObject(int i) {
        return objects[i];
    }

    public NodeClass<?>[] getNodeClasses() {
        return types;
    }

    public Assumptions getAssumptions() {
        return assumptions;
    }

    public List<ResolvedJavaMethod> getInlinedMethods() {
        return inlinedMethods;
    }

    public boolean trackNodeSourcePosition() {
        return trackNodeSourcePosition;
    }

    public EconomicSet<ResolvedJavaField> getFields() {
        return fields;
    }

    public boolean hasUnsafeAccess() {
        return hasUnsafeAccess;
    }

    @SuppressWarnings("unused")
    public boolean isCallToOriginal(ResolvedJavaMethod callTarget) {
        return false;
    }
}
