/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import java.util.Comparator;
import java.util.Objects;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import jdk.jfr.RecordingContextFilter;

/**
 * @since 17
 */
public final class RecordingContextFilterEngine {

    private static final ThreadLocal<Map<Long, Boolean>> current = ThreadLocal.withInitial(() -> null);

    private static final Consumer<RecordingContextBinding> onContextChangeListener =
        RecordingContextFilterEngine::onRecordingContextChange;

    private static RecordingContextFilter filter;
    private static Map<Long, RecordingContextPredicate> predicates;

    public static void setContextFilter(
            RecordingContextFilter filter,
            Map<Long, RecordingContextPredicate> predicates) {

        if (filter != null) {
            Objects.requireNonNull(predicates);
            Objects.checkIndex(0, predicates.size());
        }

        synchronized (RecordingContextFilterEngine.class) {
            if (RecordingContextFilterEngine.filter == null && filter != null) {
                RecordingContextBinding.addContextChangeListener(onContextChangeListener);
            }
            if (filter == null) {
                RecordingContextBinding.removeContextChangeListener(onContextChangeListener);
                // reset predicates
                current.set(null);
                JVM.getJVM().recordingContextFilterSet(null);
            }

            RecordingContextFilterEngine.filter = filter;
            RecordingContextFilterEngine.predicates = predicates;
        }
    }

    public static RecordingContextFilter contextFilter() {
        return filter;
    }

    private static void onRecordingContextChange(RecordingContextBinding b) {
        Map<Long, RecordingContextPredicate> predicates;
        synchronized (RecordingContextFilterEngine.class) {
            // We need to synchronize with setContextFilter as to avoid
            // this listener to be called before it was fully initialized
            predicates = RecordingContextFilterEngine.predicates;
        }

        Objects.requireNonNull(predicates);
        Objects.checkIndex(0, predicates.size());

        Map<Long, Boolean> matches =
            predicates.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().test(b)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        current.set(matches);

        final IntegerHolder h = new IntegerHolder();
        final int[] matchesArray = new int[matches.size() * 2];
        matches.entrySet().stream()
            .sorted(Comparator.comparingLong(Map.Entry::getKey))
            .forEach(e -> {
                matchesArray[h.i++] = e.getKey().intValue();
                matchesArray[h.i++] = e.getValue().booleanValue() ? 1 : 0;
            });

        JVM.getJVM().recordingContextFilterSet(matchesArray);
    }

    private static class IntegerHolder {
        public int i = 0;
    }

    public static boolean matchesCurrentBinding(long eventId) {
        Map<Long, Boolean> matches = current.get();
        if (matches == null) {
            // There are no filters, so it matches by default
            return true;
        }
        if (matches.containsKey(eventId)) {
            return matches.get(eventId);
        }
        if (matches.containsKey(-1L)) {
            return matches.get(-1L);
        }
        // There are filters but none of them match
        return false;
    }
}
