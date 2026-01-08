/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.servercompiler;

import com.sun.hotspot.igv.data.*;
import com.sun.hotspot.igv.data.services.PreProcessor;
import org.openide.util.lookup.ServiceProvider;
import java.util.*;
import java.util.stream.*;

@ServiceProvider(service = PreProcessor.class)
public class ServerCompilerPreProcessor implements PreProcessor {

    private static boolean isPhi(InputNode node, int defLiveRange, List<Integer> uses) {
        String nodeName = node.getProperties().get("name");
        if (nodeName == null) {
            return false;
        }
        if (!nodeName.equals("Phi")) {
            return false;
        }
        Set<Integer> useSet = new HashSet<>(uses);
        if (useSet.size() == 1 && useSet.iterator().next() == defLiveRange) {
            // The phi operands have been coalesced, treat as a regular instruction.
            return false;
        }
        return true;
    }

    private static int getNumericPropertyOrZero(InputNode node, String k) {
        int v = 0;
        try {
            v = Integer.parseInt(node.getProperties().get(k));
        } catch (Exception e) {
        }
        return v;
    }

    private static boolean isAllocatableLiveRange(int liveRangeId) {
        return liveRangeId > 0;
    }

    private String liveRangeList(Stream<Integer> s) {
        return s.sorted().map(String::valueOf).collect(Collectors.joining(", "));
    }

    @Override
    public void preProcess(InputGraph graph) {
        if (graph.getLiveRanges().isEmpty()) {
            // No block-level liveness information available, move on.
            return;
        }
        // Build a map from nodes to live ranges used.
        Map<Integer, List<Integer>> usedLiveRanges = new HashMap<>(graph.getNodes().size());
        for (InputEdge e : graph.getEdges()) {
            int liveRangeId = getNumericPropertyOrZero(graph.getNode(e.getFrom()), "lrg");
            if (isAllocatableLiveRange(liveRangeId)) {
                int toId = e.getTo();
                if (usedLiveRanges.get(toId) == null) {
                    usedLiveRanges.put(toId, new ArrayList<Integer>());
                }
                usedLiveRanges.get(toId).add(liveRangeId);
            }
        }
        // Propagate block-level live-out information to each node.
        for (InputBlock b : graph.getBlocks()) {
            Set<Integer> liveOut = new HashSet<>();
            for (int lrg : b.getLiveOut()) {
                liveOut.add(lrg);
            }
            for (int i = b.getNodes().size() - 1; i >= 0; i--) {
                LivenessInfo livenessInfo = new LivenessInfo();
                InputNode n = b.getNodes().get(i);
                String liveOutList = liveRangeList(liveOut.stream());
                n.getProperties().setProperty("liveout", liveOutList);
                livenessInfo.liveout = new HashSet<>(liveOut);
                int defLiveRange = getNumericPropertyOrZero(n, "lrg");
                if (isAllocatableLiveRange(defLiveRange)) {
                    livenessInfo.def = defLiveRange;
                    // Otherwise it is missing or a non-allocatable live range.
                    liveOut.remove(defLiveRange);
                }
                List<Integer> uses = usedLiveRanges.get(n.getId());
                if (uses != null) {
                    String useList = liveRangeList(uses.stream());
                    if (isPhi(n, defLiveRange, uses)) {
                        // A phi's uses are not live simultaneously.
                        // Conceptually, they die at the block's incoming egdes.
                        n.getProperties().setProperty("joins", useList);
                        livenessInfo.join = new HashSet<>(uses);
                    } else {
                        n.getProperties().setProperty("uses", useList);
                        livenessInfo.use = new HashSet<>(uses);
                        // Compute kill set: all uses that are not in the
                        // live-out set of the node.
                        Set<Integer> kills = new HashSet<>();
                        for (int useLiveRange : uses) {
                            if (!liveOut.contains(useLiveRange) && useLiveRange != defLiveRange) {
                                kills.add(useLiveRange);
                            }
                        }
                        if (!kills.isEmpty()) {
                            String killList = liveRangeList(kills.stream());
                            n.getProperties().setProperty("kills", killList);
                            livenessInfo.kill = new HashSet<>(kills);
                        }
                        for (int useLiveRange : uses) {
                            liveOut.add(useLiveRange);
                        }
                    }
                }
                String liveInList = liveRangeList(liveOut.stream());
                n.getProperties().setProperty("livein", liveInList);
                livenessInfo.livein = new HashSet<>(liveOut);
                graph.addLivenessInfo(n, livenessInfo);
            }
        }
    }
}
