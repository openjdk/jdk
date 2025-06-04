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
package jdk.jfr.internal.consumer.filter;

import jdk.jfr.internal.Type;

/**
 * Represents the binary content of constant pool, both key and value.
 * <p>
 * All positional values are relative to file start, not the chunk.
 */
final class PoolEntry {
    private final long startPosition;
    private final long endPosition;
    private final Type type;
    private final long keyId;
    private final Object references;

    private boolean touched;

    PoolEntry(long startPosition, long endPosition, Type type, long keyId, Object references) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.type = type;
        this.keyId = keyId;
        this.references = references;
    }

    public void touch() {
        this.touched = true;
    }

    public boolean isTouched() {
        return touched;
    }

    public Object getReferences() {
        return references;
    }

    public long getStartPosition() {
        return startPosition;
    }

    public long getEndPosition() {
        return endPosition;
    }

    public Type getType() {
        return type;
    }

    public long getId() {
        return keyId;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("start: ").append(startPosition).append("\n");
        sb.append("end: ").append(endPosition).append("\n");
        sb.append("type: ").append(type).append(" (").append(type.getId()).append(")\n");
        sb.append("key: ").append(keyId).append("\n");
        sb.append("object: ").append(references).append("\n");
        return sb.toString();
    }
}