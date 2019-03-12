/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.core.common.GraalOptions;

/**
 * A counter that can (only) be {@linkplain #inc() incremented} from within a snippet for gathering
 * snippet specific metrics.
 */
public final class SnippetCounter implements Comparable<SnippetCounter> {

    public static final SnippetCounter DISABLED_COUNTER = new SnippetCounter(null, "Disabled", "Disabled");

    /**
     * A group of related counters.
     */
    public static class Group {

        public interface Factory {
            /**
             * If snippet counters are {@linkplain GraalOptions#SnippetCounters enabled}, creates
             * and registers a {@link Group} with the given name. Otherwise, returns null.
             *
             * @param name name of the counter group
             */
            Group createSnippetCounterGroup(String name);
        }

        public static final Factory NullFactory = new Factory() {

            @Override
            public Group createSnippetCounterGroup(String name) {
                return null;
            }
        };

        final String name;
        final List<SnippetCounter> counters;

        public Group(String name) {
            this.name = name;
            this.counters = new ArrayList<>();
        }

        @Override
        public synchronized String toString() {
            Collections.sort(counters);

            long total = 0;
            int maxNameLen = 0;
            for (SnippetCounter c : counters) {
                total += c.value;
                maxNameLen = Math.max(c.name.length(), maxNameLen);
            }

            StringBuilder buf = new StringBuilder(String.format("Counters: %s%n", name));

            String formatString = "  %" + maxNameLen + "s: %6.2f%%%," + (String.format("%,d", total).length() + 2) + "d  // %s%n";
            for (SnippetCounter c : counters) {
                double percent = total == 0D ? 0D : ((double) (c.value * 100)) / total;
                buf.append(String.format(formatString, c.name, percent, c.value, c.description));
            }
            buf.append(String.format(formatString, "TOTAL", 100.0D, total, ""));

            return buf.toString();
        }
    }

    /**
     * Sorts counters in descending order of their {@linkplain #value() values}.
     */
    @Override
    public int compareTo(SnippetCounter o) {
        return Long.signum(o.value - value);
    }

    private final Group group;
    private final String name;
    private final String description;
    private long value;

    /**
     * Creates a counter.
     *
     * @param group the group to which the counter belongs. If this is null, the newly created
     *            counter is disabled and {@linkplain #inc() incrementing} is a no-op.
     * @param name the name of the counter
     * @param description a brief comment describing the metric represented by the counter
     */
    public SnippetCounter(Group group, String name, String description) {
        this.group = group;
        this.name = name;
        this.description = description;
        if (group != null) {
            List<SnippetCounter> counters = group.counters;
            counters.add(this);
        }
    }

    public Group getGroup() {
        return group;
    }

    /**
     * Increments the value of this counter. This method can only be used in a snippet on a
     * compile-time constant {@link SnippetCounter} object.
     */
    public void inc() {
        if (getGroup() != null) {
            SnippetCounterNode.increment(this);
        }
    }

    /**
     * Increments the value of this counter. This method can only be used in a snippet on a
     * compile-time constant {@link SnippetCounter} object.
     */
    public void add(int increment) {
        if (getGroup() != null) {
            SnippetCounterNode.add(this, increment);
        }
    }

    /**
     * Gets the value of this counter.
     */
    public long value() {
        return value;
    }

    @Override
    public String toString() {
        if (group != null) {
            return "SnippetCounter-" + group.name + ":" + name;
        }
        return super.toString();
    }
}
