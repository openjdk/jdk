/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.SnippetCounters;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_SUPER_CHECK_OFFSET_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.METASPACE_ARRAY_LENGTH_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.PRIMARY_SUPERS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.SECONDARY_SUPERS_ELEMENT_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.SECONDARY_SUPERS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.SECONDARY_SUPER_CACHE_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.metaspaceArrayBaseOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.metaspaceArrayLengthOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.secondarySuperCacheOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.secondarySupersOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.superCheckOffsetOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.wordSize;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.NOT_LIKELY_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.util.Arrays;

import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.TypeCheckHints;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.MetaAccessProvider;

//JaCoCo Exclude

/**
 * Utilities and common code paths used by the type check snippets.
 */
public class TypeCheckSnippetUtils {

    static boolean checkSecondarySubType(KlassPointer t, KlassPointer s) {
        // if (S.cache == T) return true
        if (s.readKlassPointer(secondarySuperCacheOffset(INJECTED_VMCONFIG), SECONDARY_SUPER_CACHE_LOCATION).equal(t)) {
            cacheHit.inc();
            return true;
        }

        return checkSelfAndSupers(t, s);
    }

    static boolean checkUnknownSubType(KlassPointer t, KlassPointer s) {
        // int off = T.offset
        int superCheckOffset = t.readInt(superCheckOffsetOffset(INJECTED_VMCONFIG), KLASS_SUPER_CHECK_OFFSET_LOCATION);
        boolean primary = superCheckOffset != secondarySuperCacheOffset(INJECTED_VMCONFIG);

        // if (T = S[off]) return true
        if (s.readKlassPointer(superCheckOffset, PRIMARY_SUPERS_LOCATION).equal(t)) {
            if (primary) {
                cacheHit.inc();
            } else {
                displayHit.inc();
            }
            return true;
        }

        // if (off != &cache) return false
        if (primary) {
            displayMiss.inc();
            return false;
        }

        return checkSelfAndSupers(t, s);
    }

    private static boolean checkSelfAndSupers(KlassPointer t, KlassPointer s) {
        // if (T == S) return true
        if (s.equal(t)) {
            T_equals_S.inc();
            return true;
        }

        // if (S.scan_s_s_array(T)) { S.cache = T; return true; }
        Word secondarySupers = s.readWord(secondarySupersOffset(INJECTED_VMCONFIG), SECONDARY_SUPERS_LOCATION);
        int length = secondarySupers.readInt(metaspaceArrayLengthOffset(INJECTED_VMCONFIG), METASPACE_ARRAY_LENGTH_LOCATION);
        for (int i = 0; i < length; i++) {
            if (probability(NOT_LIKELY_PROBABILITY, t.equal(loadSecondarySupersElement(secondarySupers, i)))) {
                s.writeKlassPointer(secondarySuperCacheOffset(INJECTED_VMCONFIG), t, SECONDARY_SUPER_CACHE_LOCATION);
                secondariesHit.inc();
                return true;
            }
        }
        secondariesMiss.inc();
        return false;
    }

    /**
     * A set of type check hints ordered by decreasing probabilities.
     */
    public static class Hints {

        /**
         * The hubs of the hint types.
         */
        public final ConstantNode[] hubs;

        /**
         * A predicate over {@link #hubs} specifying whether the corresponding hint type is a
         * sub-type of the checked type.
         */
        public final boolean[] isPositive;

        Hints(ConstantNode[] hints, boolean[] hintIsPositive) {
            this.hubs = hints;
            this.isPositive = hintIsPositive;
        }
    }

    static Hints createHints(TypeCheckHints hints, MetaAccessProvider metaAccess, boolean positiveOnly, StructuredGraph graph) {
        ConstantNode[] hubs = new ConstantNode[hints.hints.length];
        boolean[] isPositive = new boolean[hints.hints.length];
        int index = 0;
        for (int i = 0; i < hubs.length; i++) {
            if (!positiveOnly || hints.hints[i].positive) {
                hubs[index] = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), ((HotSpotResolvedObjectType) hints.hints[i].type).klass(), metaAccess, graph);
                isPositive[index] = hints.hints[i].positive;
                index++;
            }
        }
        if (positiveOnly && index != hubs.length) {
            assert index < hubs.length;
            hubs = Arrays.copyOf(hubs, index);
            isPositive = Arrays.copyOf(isPositive, index);
        }
        return new Hints(hubs, isPositive);
    }

    static KlassPointer loadSecondarySupersElement(Word metaspaceArray, int index) {
        return KlassPointer.fromWord(metaspaceArray.readWord(metaspaceArrayBaseOffset(INJECTED_VMCONFIG) + index * wordSize(), SECONDARY_SUPERS_ELEMENT_LOCATION));
    }

    private static final SnippetCounter.Group counters = SnippetCounters.getValue() ? new SnippetCounter.Group("TypeCheck") : null;
    static final SnippetCounter hintsHit = new SnippetCounter(counters, "hintsHit", "hit a hint type");
    static final SnippetCounter hintsMiss = new SnippetCounter(counters, "hintsMiss", "missed a hint type");
    static final SnippetCounter exactHit = new SnippetCounter(counters, "exactHit", "exact type test succeeded");
    static final SnippetCounter exactMiss = new SnippetCounter(counters, "exactMiss", "exact type test failed");
    static final SnippetCounter isNull = new SnippetCounter(counters, "isNull", "object tested was null");
    static final SnippetCounter cacheHit = new SnippetCounter(counters, "cacheHit", "secondary type cache hit");
    static final SnippetCounter secondariesHit = new SnippetCounter(counters, "secondariesHit", "secondaries scan succeeded");
    static final SnippetCounter secondariesMiss = new SnippetCounter(counters, "secondariesMiss", "secondaries scan failed");
    static final SnippetCounter displayHit = new SnippetCounter(counters, "displayHit", "primary type test succeeded");
    static final SnippetCounter displayMiss = new SnippetCounter(counters, "displayMiss", "primary type test failed");
    static final SnippetCounter T_equals_S = new SnippetCounter(counters, "T_equals_S", "object type was equal to secondary type");

}
