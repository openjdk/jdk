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

import java.util.Collection;
import java.util.LinkedHashMap;

import jdk.jfr.internal.Type;

/**
 * Represents a checkpoint event.
 * <p>
 * All positional values are relative to file start, not the chunk.
 */
public final class CheckpointEvent {
    private final ChunkWriter chunkWriter;
    private final LinkedHashMap<Long, CheckpointPool> pools = new LinkedHashMap<>();
    private final long startPosition;

    public CheckpointEvent(ChunkWriter chunkWriter, long startPosition) {
        this.chunkWriter = chunkWriter;
        this.startPosition = startPosition;
    }

    public PoolEntry addEntry(Type type, long id, long startPosition, long endPosition, Object references) {
        long typeId = type.getId();
        PoolEntry pe = new PoolEntry(startPosition, endPosition, type, id, references);
        var cpp = pools.computeIfAbsent(typeId, k -> new CheckpointPool(typeId));
        cpp.add(pe);
        chunkWriter.getPool(type).add(id, pe);
        return pe;
    }

    public long touchedPools() {
        int count = 0;
        for (CheckpointPool cpp : pools.values()) {
            if (cpp.isTouched()) {
                count++;
            }
        }
        return count;
    }

    public Collection<CheckpointPool> getPools() {
        return pools.values();
    }

    public long getStartPosition() {
        return startPosition;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (CheckpointPool p : pools.values()) {
            for (var e : p.getEntries()) {
                if (e.isTouched()) {
                    sb.append(e.getType().getName() + " " + e.getId() + "\n");
                }
            }
        }
        return sb.toString();
    }
}