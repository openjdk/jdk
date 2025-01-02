/*
 * Copyright (c) 2024, Red Hat, Inc.
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

package jdk.tools.jlink.internal.runtimelink;

import java.util.List;
import java.util.Objects;

import jdk.tools.jlink.internal.runtimelink.JimageDiffGenerator.ImageResource;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

@SuppressWarnings("try")
public class ResourcePoolReader implements ImageResource {

    private final ResourcePool pool;

    public ResourcePoolReader(ResourcePool pool) {
        this.pool = Objects.requireNonNull(pool);
    }

    @Override
    public void close() throws Exception {
        // nothing
    }

    @Override
    public List<String> getEntries() {
        return pool.entries().map(ResourcePoolEntry::path).toList();
    }

    @Override
    public byte[] getResourceBytes(String name) {
        return pool.findEntry(name).orElseThrow().contentBytes();
    }

}
