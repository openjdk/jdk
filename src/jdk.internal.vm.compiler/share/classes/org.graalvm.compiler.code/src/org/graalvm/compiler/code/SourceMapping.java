/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.code;

import static jdk.vm.ci.meta.MetaUtil.identityHashCodeString;

import org.graalvm.compiler.graph.NodeSourcePosition;

/**
 * This provides a mapping between a half-open range of PCs in the generated code and a
 * {@link NodeSourcePosition} in the original program. Depending on the backend this information may
 * be represented in different ways or not at all.
 */
public final class SourceMapping {

    private final int startOffset;

    private final int endOffset;

    private final NodeSourcePosition sourcePosition;

    public SourceMapping(int startOffset, int endOffset, NodeSourcePosition sourcePosition) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.sourcePosition = sourcePosition;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public NodeSourcePosition getSourcePosition() {
        return sourcePosition;
    }

    @Override
    public String toString() {
        return identityHashCodeString(this);
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("hashCode");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SourceMapping) {
            SourceMapping other = (SourceMapping) obj;
            return other.startOffset == startOffset && other.endOffset == endOffset && other.sourcePosition.equals(sourcePosition);
        }
        return false;
    }

    public boolean contains(int offset) {
        return startOffset <= offset && offset < endOffset;
    }

}
