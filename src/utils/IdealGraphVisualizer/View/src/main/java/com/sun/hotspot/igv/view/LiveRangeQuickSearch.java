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
import com.sun.hotspot.igv.data.InputLiveRange;
import com.sun.hotspot.igv.data.Properties.RegexpPropertyMatcher;
import com.sun.hotspot.igv.data.services.InputGraphProvider;
import com.sun.hotspot.igv.util.LookupHistory;
import com.sun.hotspot.igv.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.NotifyDescriptor.Message;

public class LiveRangeQuickSearch implements SearchProvider {

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
        List<InputLiveRange> matches = findMatches(value, p.getGraph(), response);
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
            matches.sort((InputLiveRange a, InputLiveRange b) ->
                         compareByRankThenNumVal(rawValue,
                                                 "L" + a.getId(),
                                                 "L" + b.getId()));

            final InputGraph theGraph = p.getGraph() != matchGraph ? matchGraph : null;
            for (final InputLiveRange liveRange : matches) {
                if (!response.addResult(() -> {
                            final EditorTopComponent editor = EditorTopComponent.getActive();
                            assert(editor != null);
                            if (theGraph != null) {
                                editor.getModel().selectGraph(theGraph);
                            }
                            Set<InputLiveRange> liveRangeSingleton = new HashSet<>();
                            liveRangeSingleton.add(liveRange);
                            editor.clearSelectedElements();
                            editor.addSelectedLiveRanges(liveRangeSingleton, true);
                            editor.centerSelectedLiveRanges();
                            editor.requestActive();
                        },
                        "L" + liveRange.getId() + (theGraph != null ? " in " + theGraph.getName() : ""))) {
                    return;
                }
            }
        }
    }

    private List<InputLiveRange> findMatches(String liveRangeName, InputGraph inputGraph, SearchResponse response) {
        try {
            RegexpPropertyMatcher matcher = new RegexpPropertyMatcher("", liveRangeName, Pattern.CASE_INSENSITIVE);
            List<InputLiveRange> matches = new ArrayList<>();
            for (InputLiveRange liveRange : inputGraph.getLiveRanges()) {
                if (matcher.match("L" + liveRange.getId())) {
                    matches.add(liveRange);
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
                key1 = Integer.parseInt(l1.replace("L", ""));
                key2 = Integer.parseInt(l2.replace("L", ""));
            } catch (Exception e) {
                // Not applicable, return equality value.
                return 0;
            }
        }
        return Integer.compare(key1, key2);
    }

}
