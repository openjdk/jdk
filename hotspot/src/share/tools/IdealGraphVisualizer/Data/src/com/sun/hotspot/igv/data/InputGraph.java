/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Thomas Wuerthinger
 */
public class InputGraph extends Properties.Entity {

    private HashMap<Integer, InputNode> nodes;
    private ArrayList<InputEdge> edges;
    private Group parent;
    private HashMap<String, InputBlock> blocks;
    private HashMap<Integer, InputBlock> nodeToBlock;
    private boolean isDifferenceGraph;

    public InputGraph(Group parent) {
        this(parent, null);
    }

    public InputGraph(Group parent, InputGraph last) {
        this(parent, last, "");
    }

    private void clearBlocks() {
        blocks.clear();
        nodeToBlock.clear();
    }

    public InputGraph(Group parent, InputGraph last, String name) {
        this.parent = parent;
        setName(name);
        nodes = new HashMap<Integer, InputNode>();
        edges = new ArrayList<InputEdge>();
        blocks = new HashMap<String, InputBlock>();
        nodeToBlock = new HashMap<Integer, InputBlock>();
        if (last != null) {

            for (InputNode n : last.getNodes()) {
                addNode(n);
            }

            for (InputEdge c : last.getEdges()) {
                addEdge(c);
            }
        }
    }

    public void schedule(Collection<InputBlock> newBlocks) {
        clearBlocks();
        InputBlock noBlock = new InputBlock(this, "no block");
        Set<InputNode> scheduledNodes = new HashSet<InputNode>();

        for (InputBlock b : newBlocks) {
            for (InputNode n : b.getNodes()) {
                assert !scheduledNodes.contains(n);
                scheduledNodes.add(n);
            }
        }

        for (InputNode n : this.getNodes()) {
            assert nodes.get(n.getId()) == n;
            if (!scheduledNodes.contains(n)) {
                noBlock.addNode(n.getId());
            }
        }

        if (noBlock.getNodes().size() != 0) {
            newBlocks.add(noBlock);
        }
        for (InputBlock b : newBlocks) {
            addBlock(b);
        }

        for (InputNode n : this.getNodes()) {
            assert this.getBlock(n) != null;
        }
    }

    public void setBlock(InputNode node, InputBlock block) {
        nodeToBlock.put(node.getId(), block);
    }

    public InputBlock getBlock(int nodeId) {
        return nodeToBlock.get(nodeId);
    }

    public InputBlock getBlock(InputNode node) {
        return getBlock(node.getId());
    }

    public InputGraph getNext() {
        List<InputGraph> list = parent.getGraphs();
        if (!list.contains(this)) {
            return null;
        }
        int index = list.indexOf(this);
        if (index == list.size() - 1) {
            return null;
        } else {
            return list.get(index + 1);
        }
    }

    public InputGraph getPrev() {
        List<InputGraph> list = parent.getGraphs();
        if (!list.contains(this)) {
            return null;
        }
        int index = list.indexOf(this);
        if (index == 0) {
            return null;
        } else {
            return list.get(index - 1);
        }
    }

    public String getName() {
        return getProperties().get("name");
    }

    public String getAbsoluteName() {
        String result = getName();
        if (this.parent != null) {
            result = parent.getName() + ": " + result;
        }
        return result;
    }

    public Collection<InputNode> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public Set<Integer> getNodesAsSet() {
        return Collections.unmodifiableSet(nodes.keySet());
    }

    public Collection<InputBlock> getBlocks() {
        return Collections.unmodifiableCollection(blocks.values());
    }

    public void addNode(InputNode node) {
        nodes.put(node.getId(), node);
    }

    public InputNode getNode(int id) {
        return nodes.get(id);
    }

    public InputNode removeNode(int index) {
        return nodes.remove(index);
    }

    public Collection<InputEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public void removeEdge(InputEdge c) {
        assert edges.contains(c);
        edges.remove(c);
        assert !edges.contains(c);
    }

    public void addEdge(InputEdge c) {
        assert !edges.contains(c);
        edges.add(c);
        assert edges.contains(c);
    }

    public Group getGroup() {
        return parent;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Graph " + getName() + " " + getProperties().toString() + "\n");
        for (InputNode n : nodes.values()) {
            sb.append(n.toString());
            sb.append("\n");
        }

        for (InputEdge c : edges) {
            sb.append(c.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    public void addBlock(InputBlock b) {
        blocks.put(b.getName(), b);
        for (InputNode n : b.getNodes()) {
            this.nodeToBlock.put(n.getId(), b);
        }
    }

    public void resolveBlockLinks() {
        for (InputBlock b : blocks.values()) {
            b.resolveBlockLinks();
        }
    }

    public void setName(String s) {
        getProperties().setProperty("name", s);
    }

    public InputBlock getBlock(String s) {
        return blocks.get(s);
    }

    public boolean isDifferenceGraph() {
        return this.isDifferenceGraph;
    }

    public void setIsDifferenceGraph(boolean b) {
        isDifferenceGraph = b;
    }
}
