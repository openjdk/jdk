/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.view;

import com.sun.hotspot.igv.data.Group;
import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.difference.Difference;
import com.sun.hotspot.igv.filter.FilterChain;
import com.sun.hotspot.igv.graph.Diagram;
import com.sun.hotspot.igv.data.ChangedEvent;
import com.sun.hotspot.igv.util.RangeSliderModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.sun.hotspot.igv.data.ChangedListener;
import com.sun.hotspot.igv.settings.Settings;
import java.awt.Color;

/**
 *
 * @author Thomas Wuerthinger
 */
public class DiagramViewModel extends RangeSliderModel implements ChangedListener<RangeSliderModel> {

    // Warning: Update setData method if fields are added
    private Group group;
    private Set<Integer> hiddenNodes;
    private Set<Integer> onScreenNodes;
    private Set<Integer> selectedNodes;
    private FilterChain filterChain;
    private FilterChain sequenceFilterChain;
    private Diagram diagram;
    private ChangedEvent<DiagramViewModel> groupChangedEvent;
    private ChangedEvent<DiagramViewModel> diagramChangedEvent;
    private ChangedEvent<DiagramViewModel> viewChangedEvent;
    private ChangedEvent<DiagramViewModel> viewPropertiesChangedEvent;
    private boolean showBlocks;
    private boolean showNodeHull;
    private ChangedListener<FilterChain> filterChainChangedListener = new ChangedListener<FilterChain>() {

        public void changed(FilterChain source) {
            diagramChanged();
        }
    };

    @Override
    public DiagramViewModel copy() {
        DiagramViewModel result = new DiagramViewModel(group, filterChain, sequenceFilterChain);
        result.setData(this);
        return result;
    }

    public void setData(DiagramViewModel newModel) {
        super.setData(newModel);
        boolean diagramChanged = false;
        boolean viewChanged = false;
        boolean viewPropertiesChanged = false;

        boolean groupChanged = (group == newModel.group);
        this.group = newModel.group;
        diagramChanged |= (filterChain != newModel.filterChain);
        this.filterChain = newModel.filterChain;
        diagramChanged |= (sequenceFilterChain != newModel.sequenceFilterChain);
        this.sequenceFilterChain = newModel.sequenceFilterChain;
        diagramChanged |= (diagram != newModel.diagram);
        this.diagram = newModel.diagram;
        viewChanged |= (hiddenNodes != newModel.hiddenNodes);
        this.hiddenNodes = newModel.hiddenNodes;
        viewChanged |= (onScreenNodes != newModel.onScreenNodes);
        this.onScreenNodes = newModel.onScreenNodes;
        viewChanged |= (selectedNodes != newModel.selectedNodes);
        this.selectedNodes = newModel.selectedNodes;
        viewPropertiesChanged |= (showBlocks != newModel.showBlocks);
        this.showBlocks = newModel.showBlocks;
        viewPropertiesChanged |= (showNodeHull != newModel.showNodeHull);
        this.showNodeHull = newModel.showNodeHull;

        if(groupChanged) {
            groupChangedEvent.fire();
        }

        if (diagramChanged) {
            diagramChangedEvent.fire();
        }
        if (viewPropertiesChanged) {
            viewPropertiesChangedEvent.fire();
        }
        if (viewChanged) {
            viewChangedEvent.fire();
        }
    }

    public boolean getShowBlocks() {
        return showBlocks;
    }

    public void setShowBlocks(boolean b) {
        showBlocks = b;
        viewPropertiesChangedEvent.fire();
    }

    public boolean getShowNodeHull() {
        return showNodeHull;
    }

    public void setShowNodeHull(boolean b) {
        showNodeHull = b;
        viewPropertiesChangedEvent.fire();
    }

    public DiagramViewModel(Group g, FilterChain filterChain, FilterChain sequenceFilterChain) {
        super(calculateStringList(g));

        this.showNodeHull = true;
        this.showBlocks = true;
        this.group = g;
        assert filterChain != null;
        this.filterChain = filterChain;
        assert sequenceFilterChain != null;
        this.sequenceFilterChain = sequenceFilterChain;
        hiddenNodes = new HashSet<Integer>();
        onScreenNodes = new HashSet<Integer>();
        selectedNodes = new HashSet<Integer>();
        super.getChangedEvent().addListener(this);
        diagramChangedEvent = new ChangedEvent<DiagramViewModel>(this);
        viewChangedEvent = new ChangedEvent<DiagramViewModel>(this);
        viewPropertiesChangedEvent = new ChangedEvent<DiagramViewModel>(this);
        groupChangedEvent = new ChangedEvent<DiagramViewModel>(this);
        groupChangedEvent.addListener(groupChangedListener);
        groupChangedEvent.fire();

        filterChain.getChangedEvent().addListener(filterChainChangedListener);
        sequenceFilterChain.getChangedEvent().addListener(filterChainChangedListener);
    }

    private final ChangedListener<DiagramViewModel> groupChangedListener = new ChangedListener<DiagramViewModel>() {

        private Group oldGroup;

        public void changed(DiagramViewModel source) {
            if(oldGroup != null) {
                oldGroup.getChangedEvent().removeListener(groupContentChangedListener);
            }
            group.getChangedEvent().addListener(groupContentChangedListener);
            oldGroup = group;
        }
    };


    private final ChangedListener<Group> groupContentChangedListener = new ChangedListener<Group>() {

        public void changed(Group source) {
            assert source == group;
            setPositions(calculateStringList(source));
            setSelectedNodes(selectedNodes);
        }

    };

    public ChangedEvent<DiagramViewModel> getDiagramChangedEvent() {
        return diagramChangedEvent;
    }

    public ChangedEvent<DiagramViewModel> getViewChangedEvent() {
        return viewChangedEvent;
    }

    public ChangedEvent<DiagramViewModel> getViewPropertiesChangedEvent() {
        return viewPropertiesChangedEvent;
    }

    public Set<Integer> getSelectedNodes() {
        return Collections.unmodifiableSet(selectedNodes);
    }

    public Set<Integer> getHiddenNodes() {
        return Collections.unmodifiableSet(hiddenNodes);
    }

    public Set<Integer> getOnScreenNodes() {
        return Collections.unmodifiableSet(onScreenNodes);
    }

    public void setSelectedNodes(Set<Integer> nodes) {
        this.selectedNodes = nodes;
        List<Color> colors = new ArrayList<Color>();
        for (String s : getPositions()) {
            colors.add(Color.black);
        }
        if (nodes.size() >= 1) {
            for (Integer id : nodes) {
                if (id < 0) {
                    id = -id;
                }
                InputNode last = null;
                int index = 0;
                for (InputGraph g : group.getGraphs()) {
                    Color curColor = colors.get(index);
                    InputNode cur = g.getNode(id);
                    if (cur != null) {
                        if (last == null) {
                            curColor = Color.green;
                        } else {
                            if (last.equals(cur)) {
                                if (curColor == Color.black) {
                                    curColor = Color.white;
                                }
                            } else {
                                if (curColor != Color.green) {
                                    curColor = Color.orange;
                                }
                            }
                        }
                    }
                    last = cur;
                    colors.set(index, curColor);
                    index++;
                }
            }
            this.setColors(colors);
        }
        setColors(colors);
        viewChangedEvent.fire();
    }

    public void setHiddenNodes(Set<Integer> nodes) {
        this.hiddenNodes = nodes;
        viewChangedEvent.fire();
    }

    public void setOnScreenNodes(Set<Integer> onScreenNodes) {
        this.onScreenNodes = onScreenNodes;
        viewChangedEvent.fire();
    }

    public FilterChain getSequenceFilterChain() {
        return filterChain;
    }

    public void setSequenceFilterChain(FilterChain chain) {
        assert chain != null : "sequenceFilterChain must never be null";
        sequenceFilterChain.getChangedEvent().removeListener(filterChainChangedListener);
        sequenceFilterChain = chain;
        sequenceFilterChain.getChangedEvent().addListener(filterChainChangedListener);
        diagramChanged();
    }

    private void diagramChanged() {
        // clear diagram
        diagram = null;
        getDiagramChangedEvent().fire();

    }

    public FilterChain getFilterChain() {
        return filterChain;
    }

    public void setFilterChain(FilterChain chain) {
        assert chain != null : "filterChain must never be null";
        filterChain.getChangedEvent().removeListener(filterChainChangedListener);
        filterChain = chain;
        filterChain.getChangedEvent().addListener(filterChainChangedListener);
        diagramChanged();
    }

    private static List<String> calculateStringList(Group g) {
        List<String> result = new ArrayList<String>();
        for (InputGraph graph : g.getGraphs()) {
            result.add(graph.getName());
        }
        return result;
    }

    public InputGraph getFirstGraph() {
        return group.getGraphs().get(getFirstPosition());
    }

    public InputGraph getSecondGraph() {
        List<InputGraph> graphs = group.getGraphs();
        if (graphs.size() >= getSecondPosition())
            return group.getGraphs().get(getSecondPosition());
        return getFirstGraph();
    }

    public void selectGraph(InputGraph g) {
        int index = group.getGraphs().indexOf(g);
        assert index != -1;
        setPositions(index, index);
    }

    public Diagram getDiagramToView() {

        if (diagram == null) {
            diagram = Diagram.createDiagram(getGraphToView(), Settings.get().get(Settings.NODE_TEXT, Settings.NODE_TEXT_DEFAULT));
            getFilterChain().apply(diagram, getSequenceFilterChain());
        }

        return diagram;
    }

    public InputGraph getGraphToView() {
        if (getFirstGraph() != getSecondGraph()) {
            InputGraph inputGraph = Difference.createDiffGraph(getSecondGraph(), getFirstGraph());
            return inputGraph;
        } else {
            InputGraph inputGraph = getFirstGraph();
            return inputGraph;
        }
    }

    public void changed(RangeSliderModel source) {
        diagramChanged();
    }
}
