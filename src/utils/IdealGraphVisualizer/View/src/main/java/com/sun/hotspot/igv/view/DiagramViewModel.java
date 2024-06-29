/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.data.*;
import com.sun.hotspot.igv.data.services.Scheduler;
import com.sun.hotspot.igv.difference.Difference;
import com.sun.hotspot.igv.filter.ColorFilter;
import com.sun.hotspot.igv.filter.FilterChain;
import com.sun.hotspot.igv.filter.FilterChainProvider;
import com.sun.hotspot.igv.graph.Diagram;
import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.graph.MatcherSelector;
import com.sun.hotspot.igv.settings.Settings;
import com.sun.hotspot.igv.util.RangeSliderModel;
import com.sun.hotspot.igv.view.actions.GlobalSelectionAction;
import java.awt.Color;
import java.util.*;
import java.util.function.Consumer;
import org.openide.util.Lookup;

/**
 *
 * @author Thomas Wuerthinger
 */
public class DiagramViewModel extends RangeSliderModel implements ChangedListener<RangeSliderModel> {

    private final Group group;
    private ArrayList<InputGraph> graphs;
    private Set<Integer> hiddenNodes;
    private Set<Integer> selectedNodes;
    private FilterChain filterChain;
    private final FilterChain customFilterChain;
    private final FilterChain filtersOrder;
    private Diagram diagram;
    private InputGraph cachedInputGraph;
    private final ChangedEvent<DiagramViewModel> diagramChangedEvent = new ChangedEvent<>(this);
    private final ChangedEvent<DiagramViewModel> graphChangedEvent = new ChangedEvent<>(this);
    private final ChangedEvent<DiagramViewModel> selectedNodesChangedEvent = new ChangedEvent<>(this);
    private final ChangedEvent<DiagramViewModel> hiddenNodesChangedEvent = new ChangedEvent<>(this);
    private ChangedListener<InputGraph> titleChangedListener = g -> {};
    private boolean showStableSea;
    private boolean showSea;
    private boolean showBlocks;
    private boolean showCFG;
    private boolean showNodeHull;
    private boolean showEmptyBlocks;
    private boolean hideDuplicates;
    private static boolean globalSelection = false;

    private final ChangedListener<FilterChain> filterChainChangedListener = changedFilterChain -> {
        assert filterChain == changedFilterChain;
        rebuildDiagram();
    };

    public Group getGroup() {
        return group;
    }

    public boolean getGlobalSelection() {
        return globalSelection;
    }

    public void setGlobalSelection(boolean enable, boolean fire) {
        globalSelection = enable;
        if (fire && enable) {
            diagramChangedEvent.fire();
        }
    }

    public boolean getShowStableSea() {
        return showStableSea;
    }

    public void setShowStableSea(boolean enable) {
        showStableSea = enable;
        if (enable) {
            diagramChangedEvent.fire();
        }
    }

    public boolean getShowSea() {
        return showSea;
    }

    public void setShowSea(boolean enable) {
        showSea = enable;
        if (enable) {
            diagramChangedEvent.fire();
        }
    }

    public boolean getShowBlocks() {
        return showBlocks;
    }

    public void setShowBlocks(boolean enable) {
        showBlocks = enable;
        if (enable) {
            diagramChangedEvent.fire();
        }
    }

    public boolean getShowCFG() {
        return showCFG;
    }

    public void setShowCFG(boolean enable) {
        showCFG = enable;
        diagram.setCFG(enable);
        if (enable) {
            diagramChangedEvent.fire();
        }
    }

    public boolean getShowNodeHull() {
        return showNodeHull;
    }

    public void setShowNodeHull(boolean b) {
        showNodeHull = b;
        diagramChangedEvent.fire();
    }

    public boolean getShowEmptyBlocks() {
        return showEmptyBlocks;
    }

    public void setShowEmptyBlocks(boolean b) {
        showEmptyBlocks = b;
        diagramChangedEvent.fire();
    }

    public void setHideDuplicates(boolean hideDuplicates) {
        this.hideDuplicates = hideDuplicates;
        InputGraph currentGraph = getFirstGraph();
        if (hideDuplicates) {
            // Back up to the unhidden equivalent graph
            int index = graphs.indexOf(currentGraph);
            while (graphs.get(index).getProperties().get("_isDuplicate") != null) {
                index--;
            }
            currentGraph = graphs.get(index);
        }
        filterGraphs();
        selectGraph(currentGraph);
    }

    public boolean getHideDuplicates() {
        return hideDuplicates;
    }

    private void initGroup() {
        group.getChangedEvent().addListener(g -> {
            assert g == group;
            if (group.getGraphs().isEmpty()) {
                // If the group has been emptied, all corresponding graph views
                // will be closed, so do nothing.
                return;
            }
            filterGraphs();
            setSelectedNodes(selectedNodes);
        });
        filterGraphs();
        super.getChangedEvent().addListener(this);
    }

    public DiagramViewModel(DiagramViewModel model) {
        super(model);
        globalSelection = false;
        group = model.getGroup();
        initGroup();
        graphs = new ArrayList<>(model.graphs);

        // initialize the filters from a model
        FilterChainProvider provider = Lookup.getDefault().lookup(FilterChainProvider.class);
        assert provider != null;
        customFilterChain = provider.createNewCustomFilterChain();
        customFilterChain.clearFilters();
        customFilterChain.addFilters(model.getCustomFilterChain().getFilters());
        setFilterChain(model.getFilterChain());
        filtersOrder = provider.getAllFiltersOrdered();

        globalSelection = GlobalSelectionAction.get(GlobalSelectionAction.class).isSelected();
        showCFG = model.getShowCFG();
        showSea = model.getShowSea();
        showBlocks = model.getShowBlocks();
        showNodeHull = model.getShowNodeHull();
        showEmptyBlocks = model.getShowEmptyBlocks();
        hideDuplicates = model.getHideDuplicates();

        hiddenNodes = new HashSet<>(model.getHiddenNodes());
        selectedNodes = new HashSet<>();
        changed(this);
    }

    public DiagramViewModel(InputGraph graph) {
        group = graph.getGroup();
        initGroup();

        FilterChainProvider provider = Lookup.getDefault().lookup(FilterChainProvider.class);
        assert provider != null;
        customFilterChain = provider.createNewCustomFilterChain();
        setFilterChain(provider.getFilterChain());
        filtersOrder = provider.getAllFiltersOrdered();

        globalSelection = GlobalSelectionAction.get(GlobalSelectionAction.class).isSelected();
        showStableSea = Settings.get().getInt(Settings.DEFAULT_VIEW, Settings.DEFAULT_VIEW_DEFAULT) == Settings.DefaultView.STABLE_SEA_OF_NODES;
        showSea = Settings.get().getInt(Settings.DEFAULT_VIEW, Settings.DEFAULT_VIEW_DEFAULT) == Settings.DefaultView.SEA_OF_NODES;
        showBlocks = Settings.get().getInt(Settings.DEFAULT_VIEW, Settings.DEFAULT_VIEW_DEFAULT) == Settings.DefaultView.CLUSTERED_SEA_OF_NODES;
        showCFG = Settings.get().getInt(Settings.DEFAULT_VIEW, Settings.DEFAULT_VIEW_DEFAULT) == Settings.DefaultView.CONTROL_FLOW_GRAPH;
        showNodeHull = true;
        showEmptyBlocks = true;
        hideDuplicates = false;

        hiddenNodes = new HashSet<>();
        selectedNodes = new HashSet<>();
        selectGraph(graph);
    }

    public ChangedEvent<DiagramViewModel> getDiagramChangedEvent() {
        return diagramChangedEvent;
    }

    public ChangedEvent<DiagramViewModel> getGraphChangedEvent() {
        return graphChangedEvent;
    }

    public ChangedEvent<DiagramViewModel> getSelectedNodesChangedEvent() {
        return selectedNodesChangedEvent;
    }

    public ChangedEvent<DiagramViewModel> getHiddenNodesChangedEvent() {
        return hiddenNodesChangedEvent;
    }

    public Set<Integer> getSelectedNodes() {
        return selectedNodes;
    }

    public Set<Integer> getHiddenNodes() {
        return hiddenNodes;
    }

    public void setSelectedNodes(Set<Integer> nodes) {
        selectedNodes = nodes;
        List<Color> colors = new ArrayList<>();
        for (String ignored : getPositions()) {
            colors.add(Color.black);
        }
        if (nodes.size() >= 1) {
            for (Integer id : nodes) {
                if (id < 0) {
                    id = -id;
                }
                InputNode last = null;
                int index = 0;
                for (InputGraph g : graphs) {
                    Color curColor = colors.get(index);
                    InputNode cur = g.getNode(id);
                    if (cur != null) {
                        if (last == null) {
                            curColor = Color.green;
                        } else {
                            if (last.equals(cur) && last.getProperties().equals(cur.getProperties())) {
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
        }
        setColors(colors);
        selectedNodesChangedEvent.fire();
    }

    public void showFigures(Collection<Figure> figures) {
        boolean somethingChanged = false;
        for (Figure f : figures) {
            if (hiddenNodes.remove(f.getInputNode().getId())) {
                somethingChanged = true;
            }
        }
        if (somethingChanged) {
            hiddenNodesChangedEvent.fire();
        }
    }

    public Set<Figure> getSelectedFigures() {
        Set<Figure> result = new HashSet<>();
        for (Figure f : diagram.getFigures()) {
            if (getSelectedNodes().contains(f.getInputNode().getId())) {
                result.add(f);
            }
        }
        return result;
    }

    public void showOnly(final Set<Integer> nodes) {
        final HashSet<Integer> allNodes = new HashSet<>(getGroup().getAllNodes());
        allNodes.removeAll(nodes);
        setHiddenNodes(allNodes);


    }

    public Set<Integer> getVisibleNodes() {
        final Set<Integer> visibleNodes = new HashSet<>(getGraph().getNodesAsSet());
        visibleNodes.removeAll(hiddenNodes);
        return visibleNodes;
    }

    public void setHiddenNodes(Set<Integer> nodes) {
        hiddenNodes = nodes;
        selectedNodes.removeAll(hiddenNodes);
        hiddenNodesChangedEvent.fire();
    }

    private void setFilterChain(FilterChain newFC) {
        assert newFC != null && customFilterChain != null;
        if (filterChain != null) {
            filterChain.getChangedEvent().removeListener(filterChainChangedListener);
        }
        if (newFC.getName().equals(customFilterChain.getName())) {
            filterChain = customFilterChain;
        } else {
            filterChain = newFC;
        }
        filterChain.getChangedEvent().addListener(filterChainChangedListener);
    }

    void activateModel() {
        FilterChainProvider provider = Lookup.getDefault().lookup(FilterChainProvider.class);
        if (provider != null) {
            provider.setCustomFilterChain(customFilterChain);
            provider.selectFilterChain(filterChain);

            // link the Filters window with this model
            provider.setFilterChainSelectionChangedListener(l -> {
                // this function is called when user selects a different filter profile for this model
                setFilterChain(provider.getFilterChain());
                rebuildDiagram();
            });
        }
    }

    void close() {
        filterChain.getChangedEvent().removeListener(filterChainChangedListener);
        getChangedEvent().fire();
    }

    private void rebuildDiagram() {
        // clear diagram
        InputGraph graph = getGraph();
        if (graph.getBlocks().isEmpty()) {
            Scheduler s = Lookup.getDefault().lookup(Scheduler.class);
            graph.clearBlocks();
            s.schedule(graph);
            graph.ensureNodesInBlocks();
        }
        diagram = new Diagram(graph,
                Settings.get().get(Settings.NODE_TEXT, Settings.NODE_TEXT_DEFAULT),
                Settings.get().get(Settings.NODE_SHORT_TEXT, Settings.NODE_SHORT_TEXT_DEFAULT),
                Settings.get().get(Settings.NODE_TINY_TEXT, Settings.NODE_TINY_TEXT_DEFAULT));
        diagram.setCFG(getShowCFG());
        filterChain.applyInOrder(diagram, filtersOrder);
        if (graph.isDiffGraph()) {
            ColorFilter f = new ColorFilter("");
            f.addRule(stateColorRule("same",    Color.white));
            f.addRule(stateColorRule("changed", Color.orange));
            f.addRule(stateColorRule("new",     Color.green));
            f.addRule(stateColorRule("deleted", Color.red));
            f.apply(diagram);
        }
        diagramChangedEvent.fire();
    }

    public FilterChain getFilterChain() {
        return filterChain;
    }

    public FilterChain getCustomFilterChain() {
        return customFilterChain;
    }

    /*
     * Select the set of graphs to be presented.
     */
    private void filterGraphs() {
        ArrayList<InputGraph> result = new ArrayList<>();
        List<String> positions = new ArrayList<>();
        for (InputGraph graph : group.getGraphs()) {
            String duplicate = graph.getProperties().get("_isDuplicate");
            if (duplicate == null || !hideDuplicates) {
                result.add(graph);
                positions.add(graph.getName());
            }
        }
        this.graphs = result;
        setPositions(positions);
    }

    public InputGraph getFirstGraph() {
        InputGraph firstGraph;
        if (getFirstPosition() < graphs.size()) {
            firstGraph = graphs.get(getFirstPosition());
        } else {
            firstGraph = graphs.get(graphs.size() - 1);
        }
        if (firstGraph.isDiffGraph()) {
            firstGraph = firstGraph.getFirstGraph();
        }
        return firstGraph;
    }

    public InputGraph getSecondGraph() {
        InputGraph secondGraph;
        if (getSecondPosition() < graphs.size()) {
            secondGraph = graphs.get(getSecondPosition());
        } else {
            secondGraph = getFirstGraph();
        }
        if (secondGraph.isDiffGraph()) {
            secondGraph = secondGraph.getSecondGraph();
        }
        return secondGraph;
    }

    public void selectGraph(InputGraph graph) {
        int index = graphs.indexOf(graph);
        if (index == -1 && hideDuplicates) {
            // A graph was selected that's currently hidden, so unhide and select it.
            setHideDuplicates(false);
            index = graphs.indexOf(graph);
        }
        assert index != -1;
        setPositions(index, index);
    }

    public void selectDiffGraph(InputGraph graph) {
        int index = graphs.indexOf(graph);
        if (index == -1 && hideDuplicates) {
            // A graph was selected that's currently hidden, so unhide and select it.
            setHideDuplicates(false);
            index = graphs.indexOf(graph);
        }
        assert index != -1;
        int firstIndex = getFirstPosition();
        int secondIndex = getSecondPosition();
        if (firstIndex <= index) {
            setPositions(firstIndex, index);
        } else {
            setPositions(index, secondIndex);
        }
    }

    private static ColorFilter.ColorRule stateColorRule(String state, Color color) {
        return new ColorFilter.ColorRule(new MatcherSelector(new Properties.RegexpPropertyMatcher("state", state)), color);
    }

    public Diagram getDiagram() {
        return diagram;
    }

    public InputGraph getGraph() {
        return cachedInputGraph;
    }

    @Override
    public void changed(RangeSliderModel source) {
        if (cachedInputGraph != null) {
            cachedInputGraph.getDisplayNameChangedEvent().removeListener(titleChangedListener);
        }
        if (getFirstGraph() != getSecondGraph()) {
            cachedInputGraph = Difference.createDiffGraph(getFirstGraph(), getSecondGraph());
        } else {
            cachedInputGraph = getFirstGraph();
        }
        rebuildDiagram();
        graphChangedEvent.fire();
        assert titleChangedListener != null;
        cachedInputGraph.getDisplayNameChangedEvent().addListener(titleChangedListener);
    }

    void addTitleCallback(Consumer<InputGraph> titleCallback) {
        titleChangedListener = titleCallback::accept;
    }

    Iterable<InputGraph> getGraphsForward() {
        return () -> new Iterator<InputGraph>() {
            int index = getFirstPosition();

            @Override
            public boolean hasNext() {
                return index + 1 < graphs.size();
            }

            @Override
            public InputGraph next() {
                return graphs.get(++index);
            }
        };
    }

    Iterable<InputGraph> getGraphsBackward() {
        return () -> new Iterator<InputGraph>() {
            int index = getFirstPosition();

            @Override
            public boolean hasNext() {
                return index - 1 > 0;
            }

            @Override
            public InputGraph next() {
                return graphs.get(--index);
            }
        };
    }
}
