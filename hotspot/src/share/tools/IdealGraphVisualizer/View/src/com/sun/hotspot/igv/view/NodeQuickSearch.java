/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.view;

import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.data.Properties.RegexpPropertyMatcher;
import com.sun.hotspot.igv.data.services.InputGraphProvider;
import com.sun.hotspot.igv.util.LookupHistory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.NotifyDescriptor.Message;

/**
 *
 * @author Thomas Wuerthinger
 */
public class NodeQuickSearch implements SearchProvider {

    private static final String DEFAULT_PROPERTY = "name";

    /**
     * Method is called by infrastructure when search operation was requested.
     * Implementors should evaluate given request and fill response object with
     * apropriate results
     *
     * @param request Search request object that contains information what to search for
     * @param response Search response object that stores search results. Note that it's important to react to return value of SearchResponse.addResult(...) method and stop computation if false value is returned.
     */
    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String query = request.getText();
        if (query.trim().isEmpty()) {
            return;
        }

        final String[] parts = query.split("=", 2);

        String name;
        String value;

        if (parts.length == 1) {
            name = DEFAULT_PROPERTY;
            value = ".*" + Pattern.quote(parts[0]) + ".*";
        } else {
            name = parts[0];
            value = parts[1];
        }

        if (value.isEmpty()) {
            value = ".*";
        }

        final InputGraphProvider p = LookupHistory.getLast(InputGraphProvider.class);
        if (p != null && p.getGraph() != null) {
            InputGraph matchGraph = p.getGraph();
            // Search the current graph
            List<InputNode> matches = findMatches(name, value, p.getGraph(), response);
            if (matches == null) {
                // See if the it hits in a later graph
                for (InputGraph graph : p.searchForward()) {
                    matches = findMatches(name, value, graph, response);
                    if (matches != null) {
                        matchGraph = graph;
                        break;
                    }
                }
            }
            if (matches == null) {
                // See if it hits in a earlier graph
                for (InputGraph graph : p.searchBackward()) {
                    matches = findMatches(name, value, graph, response);
                    if (matches != null) {
                        matchGraph = graph;
                        break;
                    }
                }
            }

            if (matches != null) {
                final Set<InputNode> set = new HashSet<>(matches);
                final InputGraph theGraph = p.getGraph() != matchGraph ? matchGraph : null;
                response.addResult(new Runnable() {
                    @Override
                    public void run() {
                        final EditorTopComponent comp = EditorTopComponent.getActive();
                        if (comp != null) {
                            if (theGraph != null) {
                                comp.getDiagramModel().selectGraph(theGraph);
                            }
                            comp.setSelectedNodes(set);
                            comp.requestActive();
                        }
                    }
                },
                        "All " + matches.size() + " matching nodes (" + name + "=" + value + ")" + (theGraph != null ? " in " + theGraph.getName() : "")
                );

                // Single matches
                for (final InputNode n : matches) {
                    response.addResult(new Runnable() {
                        @Override
                        public void run() {
                            final EditorTopComponent comp = EditorTopComponent.getActive();
                            if (comp != null) {
                                final Set<InputNode> tmpSet = new HashSet<>();
                                tmpSet.add(n);
                                if (theGraph != null) {
                                    comp.getDiagramModel().selectGraph(theGraph);
                                }
                                comp.setSelectedNodes(tmpSet);
                                comp.requestActive();
                            }
                        }
                    },
                            n.getProperties().get(name) + " (" + n.getId() + " " + n.getProperties().get("name") + ")" + (theGraph != null ? " in " + theGraph.getName() : "")
                    );
                }
            }
        } else {
            System.out.println("no input graph provider!");
        }
    }

    private List<InputNode> findMatches(String name, String value, InputGraph inputGraph, SearchResponse response) {
        try {
            RegexpPropertyMatcher matcher = new RegexpPropertyMatcher(name, value, Pattern.CASE_INSENSITIVE);
            Properties.PropertySelector<InputNode> selector = new Properties.PropertySelector<>(inputGraph.getNodes());
            List<InputNode> matches = selector.selectMultiple(matcher);
            return matches.size() == 0 ? null : matches;
        } catch (Exception e) {
            final String msg = e.getMessage();
            response.addResult(new Runnable() {
                @Override
                public void run() {
                    Message desc = new NotifyDescriptor.Message("An exception occurred during the search, "
                            + "perhaps due to a malformed query string:\n" + msg,
                            NotifyDescriptor.WARNING_MESSAGE);
                    DialogDisplayer.getDefault().notify(desc);
                }
            },
                    "(Error during search)"
            );
        }
        return null;
    }
}
