/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import jdk.internal.vm.compiler.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ObjectSnippets implements Snippets {

    @NodeIntrinsic(ForeignCallNode.class)
    public static native boolean fastNotifyStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object o);

    @Snippet
    public static void fastNotify(Object thisObj) {
        if (fastNotifyStub(HotSpotHostForeignCallsProvider.NOTIFY, thisObj)) {
            return;
        } else {
            PiNode.piCastNonNull(thisObj, SnippetAnchorNode.anchor()).notify();
        }
    }

    @Snippet
    public static void fastNotifyAll(Object thisObj) {
        if (fastNotifyStub(HotSpotHostForeignCallsProvider.NOTIFY_ALL, thisObj)) {
            return;
        } else {
            PiNode.piCastNonNull(thisObj, SnippetAnchorNode.anchor()).notifyAll();
        }
    }

    public static class Templates extends AbstractTemplates {
        private final SnippetInfo notifySnippet = snippet(ObjectSnippets.class, "fastNotify", originalNotifyCall(false), null);
        private final SnippetInfo notifyAllSnippet = snippet(ObjectSnippets.class, "fastNotifyAll", originalNotifyCall(true), null);

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, HotSpotProviders providers, TargetDescription target) {
            super(options, factories, providers, providers.getSnippetReflection(), target);
        }

        private ResolvedJavaMethod originalNotifyCall(boolean notifyAll) throws GraalError {
            if (notifyAll) {
                return findMethod(providers.getMetaAccess(), Object.class, "notifyAll");
            } else {
                return findMethod(providers.getMetaAccess(), Object.class, "notify");
            }
        }

        public void lower(Node n, LoweringTool tool) {
            if (n instanceof FastNotifyNode) {
                FastNotifyNode fn = (FastNotifyNode) n;
                StructuredGraph graph = (StructuredGraph) n.graph();
                FrameState stateDuringCall = fn.stateDuring();
                assert stateDuringCall != null : "Must have valid state for snippet recursive notify call";
                Arguments args = new Arguments(fn.isNotifyAll() ? notifyAllSnippet : notifySnippet, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("thisObj", fn.object);
                SnippetTemplate template = template(fn, args);
                graph.getDebug().log("Lowering fast notify in %s: node=%s, template=%s, arguments=%s", graph, fn, template, args);
                UnmodifiableEconomicMap<Node, Node> replacements = template.instantiate(providers.getMetaAccess(), fn, DEFAULT_REPLACER, args);
                for (Node originalNode : replacements.getKeys()) {
                    if (originalNode instanceof InvokeNode) {
                        InvokeNode invoke = (InvokeNode) replacements.get(originalNode);
                        assert invoke.asNode().graph() == graph;
                        // Here we need to fix the bci of the invoke
                        invoke.setBci(fn.getBci());
                        invoke.setStateDuring(null);
                        invoke.setStateAfter(null);
                        invoke.setStateDuring(stateDuringCall);
                    } else if (originalNode instanceof InvokeWithExceptionNode) {
                        throw new GraalError("unexpected invoke with exception %s in snippet", originalNode);
                    }
                }
            } else {
                GraalError.shouldNotReachHere("Unknown object snippet lowered node");
            }
        }

    }
}
