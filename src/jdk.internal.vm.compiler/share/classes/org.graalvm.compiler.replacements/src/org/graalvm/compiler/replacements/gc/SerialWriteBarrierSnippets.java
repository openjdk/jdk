/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.gc;

import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.nodes.gc.SerialArrayRangeWriteBarrier;
import org.graalvm.compiler.nodes.gc.SerialWriteBarrier;
import org.graalvm.compiler.nodes.memory.address.AddressNode.Address;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.AssertionNode;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.Pointer;

public abstract class SerialWriteBarrierSnippets extends WriteBarrierSnippets implements Snippets {
    static class Counters {
        Counters(SnippetCounter.Group.Factory factory) {
            Group countersWriteBarriers = factory.createSnippetCounterGroup("Serial WriteBarriers");
            serialWriteBarrierCounter = new SnippetCounter(countersWriteBarriers, "serialWriteBarrier", "Number of Serial Write Barriers");
        }

        final SnippetCounter serialWriteBarrierCounter;
    }

    @Snippet
    public void serialImpreciseWriteBarrier(Object object, @ConstantParameter Counters counters, @ConstantParameter boolean verifyOnly) {
        if (verifyBarrier()) {
            verifyNotArray(object);
        }
        serialWriteBarrier(Word.objectToTrackedPointer(object), counters, verifyOnly);
    }

    @Snippet
    public void serialPreciseWriteBarrier(Address address, @ConstantParameter Counters counters, @ConstantParameter boolean verifyOnly) {
        serialWriteBarrier(Word.fromAddress(address), counters, verifyOnly);
    }

    @Snippet
    public void serialArrayRangeWriteBarrier(Address address, int length, @ConstantParameter int elementStride) {
        if (probability(NOT_FREQUENT_PROBABILITY, length == 0)) {
            return;
        }

        int cardShift = cardTableShift();
        Word cardTableAddress = cardTableAddress();
        Word start = cardTableAddress.add(getPointerToFirstArrayElement(address, length, elementStride).unsignedShiftRight(cardShift));
        Word end = cardTableAddress.add(getPointerToLastArrayElement(address, length, elementStride).unsignedShiftRight(cardShift));

        Word cur = start;
        do {
            cur.writeByte(0, dirtyCardValue(), GC_CARD_LOCATION);
            cur = cur.add(1);
        } while (cur.belowOrEqual(end));
    }

    private void serialWriteBarrier(Pointer ptr, Counters counters, boolean verifyOnly) {
        if (!verifyOnly) {
            counters.serialWriteBarrierCounter.inc();
        }

        Word base = cardTableAddress().add(ptr.unsignedShiftRight(cardTableShift()));
        if (verifyOnly) {
            byte cardValue = base.readByte(0, GC_CARD_LOCATION);
            AssertionNode.dynamicAssert(cardValue == dirtyCardValue(), "card must be dirty");
        } else {
            base.writeByte(0, dirtyCardValue(), GC_CARD_LOCATION);
        }
    }

    protected abstract Word cardTableAddress();

    protected abstract int cardTableShift();

    protected abstract boolean verifyBarrier();

    protected abstract byte dirtyCardValue();

    public static class SerialWriteBarrierLowerer {
        private final Counters counters;

        public SerialWriteBarrierLowerer(Group.Factory factory) {
            this.counters = new Counters(factory);
        }

        public void lower(AbstractTemplates templates, SnippetInfo preciseSnippet, SnippetInfo impreciseSnippet, SerialWriteBarrier barrier, LoweringTool tool) {
            Arguments args;
            if (barrier.usePrecise()) {
                args = new Arguments(preciseSnippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("address", barrier.getAddress());
            } else {
                args = new Arguments(impreciseSnippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
                OffsetAddressNode address = (OffsetAddressNode) barrier.getAddress();
                args.add("object", address.getBase());
            }
            args.addConst("counters", counters);
            args.addConst("verifyOnly", barrier.getVerifyOnly());

            templates.template(barrier, args).instantiate(templates.getProviders().getMetaAccess(), barrier, DEFAULT_REPLACER, args);
        }

        public void lower(AbstractTemplates templates, SnippetInfo snippet, SerialArrayRangeWriteBarrier barrier, LoweringTool tool) {
            Arguments args = new Arguments(snippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("address", barrier.getAddress());
            args.add("length", barrier.getLength());
            args.addConst("elementStride", barrier.getElementStride());

            templates.template(barrier, args).instantiate(templates.getProviders().getMetaAccess(), barrier, DEFAULT_REPLACER, args);
        }
    }
}
