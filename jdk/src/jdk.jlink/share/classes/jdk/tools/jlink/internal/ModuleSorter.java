/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal;

import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import jdk.tools.jlink.plugin.ResourcePoolModule;
import jdk.tools.jlink.plugin.ResourcePoolModuleView;

import java.lang.module.ModuleDescriptor;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Helper class to sort modules in topological order
 */
public final class ModuleSorter {
    private final Deque<ResourcePoolModule> nodes = new LinkedList<>();
    private final Map<String, Set<ResourcePoolModule>> edges = new HashMap<>();
    private final Deque<ResourcePoolModule> result = new LinkedList<>();

    private final ResourcePoolModuleView moduleView;

    public ModuleSorter(ResourcePoolModuleView moduleView) {
        this.moduleView = moduleView;

        moduleView.modules().forEach(this::addModule);
    }

    private ModuleDescriptor readModuleDescriptor(ResourcePoolModule module) {
        String p = "/" + module.name() + "/module-info.class";
        ResourcePoolEntry content = module.findEntry(p).orElseThrow(() ->
            new PluginException("module-info.class not found for " +
                module.name() + " module")
        );
        ByteBuffer bb = ByteBuffer.wrap(content.contentBytes());
        return ModuleDescriptor.read(bb);
    }

    private ModuleSorter addModule(ResourcePoolModule module) {
        addNode(module);
        readModuleDescriptor(module).requires().stream()
            .forEach(req -> {
                String dm = req.name();
                ResourcePoolModule dep = moduleView.findModule(dm)
                    .orElseThrow(() -> new PluginException(dm + " not found"));
                addNode(dep);
                edges.get(module.name()).add(dep);
            });
        return this;
    }

    private void addNode(ResourcePoolModule module) {
        nodes.add(module);
        edges.computeIfAbsent(module.name(), _n -> new HashSet<>());
    }

    private synchronized void build() {
        if (!result.isEmpty() || nodes.isEmpty())
            return;

        Deque<ResourcePoolModule> visited = new LinkedList<>();
        Deque<ResourcePoolModule> done = new LinkedList<>();
        ResourcePoolModule node;
        while ((node = nodes.poll()) != null) {
            if (!visited.contains(node)) {
                visit(node, visited, done);
            }
        }
    }

    public Stream<ResourcePoolModule> sorted() {
        build();
        return result.stream();
    }

    private void visit(ResourcePoolModule node,
                       Deque<ResourcePoolModule> visited,
                       Deque<ResourcePoolModule> done) {
        if (visited.contains(node)) {
            if (!done.contains(node)) {
                throw new IllegalArgumentException("Cyclic detected: " +
                    node + " " + edges.get(node.name()));
            }
            return;
        }
        visited.add(node);
        edges.get(node.name()).stream()
             .forEach(x -> visit(x, visited, done));
        done.add(node);
        result.addLast(node);
    }
}
