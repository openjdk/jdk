/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 */


package org.graalvm.compiler.printer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugVerifyHandler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;

/**
 * Verifies that graphs have no dead code.
 */
public class NoDeadCodeVerifyHandler implements DebugVerifyHandler {

    // The options below will be removed once all phases clean up their own dead code.

    private static final int OFF = 0;
    private static final int INFO = 1;
    private static final int VERBOSE = 2;
    private static final int FATAL = 3;

    static class Options {
        // @formatter:off
        @Option(help = "Run level for NoDeadCodeVerifyHandler (0 = off, 1 = info, 2 = verbose, 3 = fatal)", type = OptionType.Debug)
        public static final OptionKey<Integer> NDCV = new OptionKey<>(0);
        // @formatter:on
    }

    /**
     * Only the first instance of failure at any point is shown. This will also be removed once all
     * phases clean up their own dead code.
     */
    private static final Map<String, Boolean> discovered = new ConcurrentHashMap<>();

    @Override
    public void verify(DebugContext debug, Object object, String format, Object... args) {
        OptionValues options = debug.getOptions();
        if (Options.NDCV.getValue(options) != OFF && object instanceof StructuredGraph) {
            StructuredGraph graph = (StructuredGraph) object;
            List<Node> before = graph.getNodes().snapshot();
            new DeadCodeEliminationPhase().run(graph);
            List<Node> after = graph.getNodes().snapshot();
            assert after.size() <= before.size();
            if (before.size() != after.size()) {
                if (discovered.put(format, Boolean.TRUE) == null) {
                    before.removeAll(after);
                    String prefix = format == null ? "" : format + ": ";
                    GraalError error = new GraalError("%sfound dead nodes in %s: %s", prefix, graph, before);
                    if (Options.NDCV.getValue(options) == INFO) {
                        System.out.println(error.getMessage());
                    } else if (Options.NDCV.getValue(options) == VERBOSE) {
                        error.printStackTrace(System.out);
                    } else {
                        assert Options.NDCV.getValue(options) == FATAL;
                        throw error;
                    }
                }
            }
        }
    }
}
