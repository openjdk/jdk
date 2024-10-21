/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package com.sun.hotspot.igv.layout;

import java.util.*;

/**
 *
 * @author Thomas Wuerthinger
 */
public class LayoutGraph {

    private final Set<? extends Link> links;
    private final SortedSet<Vertex> vertices;
    private final HashMap<Vertex, Set<Port>> inputPorts;
    private final HashMap<Vertex, Set<Port>> outputPorts;
    private final HashMap<Port, Set<Link>> portLinks;

    public LayoutGraph(Set<? extends Link> links) {
        this(links, new HashSet<>());
    }

    public LayoutGraph(Set<? extends Link> links, Set<? extends Vertex> additionalVertices) {
        this.links = links;

        vertices = new TreeSet<>(additionalVertices);
        portLinks = new HashMap<>(links.size());
        inputPorts = new HashMap<>(links.size());
        outputPorts = new HashMap<>(links.size());

        for (Link link : links) {
            if (link.getFrom() == null || link.getTo() == null) {
                continue;
            }
            Port fromPort = link.getFrom();
            Port toPort = link.getTo();
            Vertex fromVertex = fromPort.getVertex();
            Vertex toVertex = toPort.getVertex();

            vertices.add(fromVertex);
            vertices.add(toVertex);

            outputPorts.computeIfAbsent(fromVertex, k -> new HashSet<>()).add(fromPort);
            inputPorts.computeIfAbsent(toVertex, k -> new HashSet<>()).add(toPort);

            portLinks.computeIfAbsent(fromPort, k -> new HashSet<>()).add(link);
            portLinks.computeIfAbsent(toPort, k -> new HashSet<>()).add(link);
        }
    }

    public Set<? extends Link> getLinks() {
        return links;
    }

    public SortedSet<Vertex> getVertices() {
        return vertices;
    }

    private void markNotRoot(Set<Vertex> notRootSet, Vertex v, Vertex startingVertex) {
        if (notRootSet.contains(v)) {
            return;
        }
        if (v != startingVertex) {
            notRootSet.add(v);
        }
        for (Port port : outputPorts.getOrDefault(v, Collections.emptySet())) {
            for (Link link : portLinks.get(port)) {
                Port toLink = link.getTo();
                Vertex toVertex = toLink.getVertex();
                if (toVertex != startingVertex) {
                    markNotRoot(notRootSet, toVertex, startingVertex);
                }
            }
        }
    }

    public Set<Vertex> findRootVertices() {
        Set<Vertex> notRootSet = new HashSet<>();
        for (Vertex vertex : vertices) {
            if (!notRootSet.contains(vertex)) {
                if (inputPorts.getOrDefault(vertex, Collections.emptySet()).isEmpty()) {
                    markNotRoot(notRootSet, vertex, vertex);
                }
            }
        }

        Set<Vertex> result = new HashSet<>();
        for (Vertex vertex : vertices) {
            if (!notRootSet.contains(vertex)) {
                result.add(vertex);
            }
        }
        return result;
    }

    public Set<Link> getInputLinks(Vertex vertex) {
        Set<Link> inputLinks = new HashSet<>();
        for (Port inputPort : inputPorts.getOrDefault(vertex, Collections.emptySet())) {
            inputLinks.addAll(portLinks.getOrDefault(inputPort, Collections.emptySet()));
        }
        return inputLinks;
    }

    public Set<Link> getOutputLinks(Vertex vertex) {
        Set<Link> outputLinks = new HashSet<>();
        for (Port outputPort : outputPorts.getOrDefault(vertex, Collections.emptySet())) {
            outputLinks.addAll(portLinks.getOrDefault(outputPort, Collections.emptySet()));
        }
        return outputLinks;
    }
}
