/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.hotspot.igv.data.Properties.RegexpPropertyMatcher;
import com.sun.hotspot.igv.data.services.InputGraphProvider;
import com.sun.hotspot.igv.util.LookupHistory;
import com.sun.hotspot.igv.util.StringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.NotifyDescriptor.Message;

public abstract class SimpleQuickSearch implements SearchProvider {

    abstract String prefix();

    abstract String id(Object entity);

    abstract Collection<Object> getAllEntities(InputGraph inputGraph);

    abstract void selectEntity(EditorTopComponent editor, Object entity);

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String rawValue = request.getText();
        if (rawValue.trim().isEmpty()) {
            return;
        }
        String value = ".*" + Pattern.quote(rawValue) + ".*";

        final InputGraphProvider p = LookupHistory.getLast(InputGraphProvider.class);
        if (p == null || p.getGraph() == null) {
            return;
        }

        InputGraph matchGraph = p.getGraph();
        // Search the current graph
        List<Object> matches = findMatches(value, p.getGraph(), response);
        if (matches == null) {
            // See if the it hits in a later graph
            for (InputGraph graph : p.searchForward()) {
                matches = findMatches(value, graph, response);
                if (matches != null) {
                    matchGraph = graph;
                    break;
                }
            }
        }
        if (matches == null) {
            // See if it hits in a earlier graph
            for (InputGraph graph : p.searchBackward()) {
                matches = findMatches(value, graph, response);
                if (matches != null) {
                    matchGraph = graph;
                    break;
                }
            }
        }
        if (matches != null) {
            // Rank the matches.
            matches.sort((Object a, Object b) ->
                         compareByRankThenNumVal(rawValue,
                                                 prefix() + id(a),
                                                 prefix() + id(b)));

            final InputGraph theGraph = p.getGraph() != matchGraph ? matchGraph : null;
            for (final Object entity : matches) {
                if (!response.addResult(() -> {
                            final EditorTopComponent editor = EditorTopComponent.getActive();
                            assert(editor != null);
                            if (theGraph != null) {
                                editor.getModel().selectGraph(theGraph);
                            }
                            editor.clearSelectedElements();
                            selectEntity(editor, entity);
                            editor.requestActive();
                        },
                        prefix() + id(entity) + (theGraph != null ? " in " + theGraph.getName() : ""))) {
                    return;
                }
            }
        }
    }

    private List<Object> findMatches(String entityName, InputGraph inputGraph, SearchResponse response) {
        try {
            RegexpPropertyMatcher matcher = new RegexpPropertyMatcher("", entityName, Pattern.CASE_INSENSITIVE);
            List<Object> matches = new ArrayList<>();
            for (Object entity : getAllEntities(inputGraph)) {
                if (matcher.match(prefix() + id(entity))) {
                    matches.add(entity);
                }
            }
            return matches.size() == 0 ? null : matches;
        } catch (Exception e) {
            final String msg = e.getMessage();
            response.addResult(() -> {
                    Message desc = new NotifyDescriptor.Message("An exception occurred during the search, "
                            + "perhaps due to a malformed query string:\n" + msg,
                            NotifyDescriptor.WARNING_MESSAGE);
                    DialogDisplayer.getDefault().notify(desc);
                },
                "(Error during search)"
            );
        }
        return null;
    }

    private int compareByRankThenNumVal(String qry, String l1, String l2) {
        int key1 = StringUtils.rankMatch(qry, l1);
        int key2 = StringUtils.rankMatch(qry, l2);
        if (key1 == key2) {
            // If the matches have the same rank, compare the numeric values of
            // their first words, if applicable.
            try {
                key1 = Integer.parseInt(l1.replace(prefix(), ""));
                key2 = Integer.parseInt(l2.replace(prefix(), ""));
            } catch (Exception e) {
                // Not applicable, return equality value.
                return 0;
            }
        }
        return Integer.compare(key1, key2);
    }

}
