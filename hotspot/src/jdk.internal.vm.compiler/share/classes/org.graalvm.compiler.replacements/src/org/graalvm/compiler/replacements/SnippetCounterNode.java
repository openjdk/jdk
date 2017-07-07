/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.TargetDescription;
import sun.misc.Unsafe;

/**
 * This node can be used to add a counter to the code that will estimate the dynamic number of calls
 * by adding an increment to the compiled code. This should of course only be used for
 * debugging/testing purposes.
 *
 * A unique counter will be created for each unique name passed to the constructor. Depending on the
 * value of withContext, the name of the root method is added to the counter's name.
 */
@NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public class SnippetCounterNode extends FixedWithNextNode implements Lowerable {

    public static final NodeClass<SnippetCounterNode> TYPE = NodeClass.create(SnippetCounterNode.class);

    @Input protected ValueNode increment;

    protected final SnippetCounter counter;

    public SnippetCounterNode(SnippetCounter counter, ValueNode increment) {
        super(TYPE, StampFactory.forVoid());
        this.counter = counter;
        this.increment = increment;
    }

    public SnippetCounter getCounter() {
        return counter;
    }

    public ValueNode getIncrement() {
        return increment;
    }

    @NodeIntrinsic
    public static native void add(@ConstantNodeParameter SnippetCounter counter, int increment);

    public static void increment(@ConstantNodeParameter SnippetCounter counter) {
        add(counter, 1);
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
            SnippetCounterSnippets.Templates templates = tool.getReplacements().getSnippetTemplateCache(SnippetCounterSnippets.Templates.class);
            templates.lower(this, tool);
        }
    }

    /**
     * Add {@link #SNIPPET_COUNTER_LOCATION} to {@code privateLocations} if it isn't already there.
     *
     * @param privateLocations
     * @return a copy of privateLocations with any needed locations added
     */
    public static LocationIdentity[] addSnippetCounters(LocationIdentity[] privateLocations) {
        for (LocationIdentity location : privateLocations) {
            if (location.equals(SNIPPET_COUNTER_LOCATION)) {
                return privateLocations;
            }
        }
        LocationIdentity[] result = Arrays.copyOf(privateLocations, privateLocations.length + 1);
        result[result.length - 1] = SnippetCounterNode.SNIPPET_COUNTER_LOCATION;
        return result;
    }

    /**
     * We do not want to use the {@link LocationIdentity} of the {@link SnippetCounter#value()}
     * field, so that the usage in snippets is always possible. If a method accesses the counter via
     * the field and the snippet, the result might not be correct though.
     */
    public static final LocationIdentity SNIPPET_COUNTER_LOCATION = NamedLocationIdentity.mutable("SnippetCounter");

    static class SnippetCounterSnippets implements Snippets {

        @Fold
        static int countOffset() {
            try {
                return (int) UNSAFE.objectFieldOffset(SnippetCounter.class.getDeclaredField("value"));
            } catch (Exception e) {
                throw new GraalError(e);
            }
        }

        @Snippet
        public static void add(@ConstantParameter SnippetCounter counter, int increment) {
            long loadedValue = ObjectAccess.readLong(counter, countOffset(), SNIPPET_COUNTER_LOCATION);
            ObjectAccess.writeLong(counter, countOffset(), loadedValue + increment, SNIPPET_COUNTER_LOCATION);
        }

        public static class Templates extends AbstractTemplates {

            private final SnippetInfo add = snippet(SnippetCounterSnippets.class, "add", SNIPPET_COUNTER_LOCATION);

            Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
                super(options, factories, providers, snippetReflection, target);
            }

            public void lower(SnippetCounterNode counter, LoweringTool tool) {
                StructuredGraph graph = counter.graph();
                Arguments args = new Arguments(add, graph.getGuardsStage(), tool.getLoweringStage());
                args.addConst("counter", counter.getCounter());
                args.add("increment", counter.getIncrement());

                template(counter.getDebug(), args).instantiate(providers.getMetaAccess(), counter, DEFAULT_REPLACER, args);
            }
        }
    }

    private static final Unsafe UNSAFE = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }
}
