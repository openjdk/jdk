/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * A histogram that can (only) be {@linkplain #inc(long) incremented} from within a snippet for
 * gathering snippet specific metrics.
 */
public final class SnippetIntegerHistogram {
    private final SnippetCounter.Group group;
    private final String name;

    private final SnippetCounter counter0;
    private final SnippetCounter counter1;
    private final SnippetCounter counter2;
    private final SnippetCounter counter3;
    private final SnippetCounter counter4;
    private final SnippetCounter counter5;
    private final SnippetCounter counter6;
    private final SnippetCounter counter7;
    private final SnippetCounter counter8;
    private final SnippetCounter counter9;
    private final SnippetCounter counter10;

    private final int counter0UpperBound;
    private final int counter1UpperBound;
    private final int counter2UpperBound;
    private final int counter3UpperBound;
    private final int counter4UpperBound;
    private final int counter5UpperBound;
    private final int counter6UpperBound;
    private final int counter7UpperBound;
    private final int counter8UpperBound;
    private final int counter9UpperBound;

    public SnippetIntegerHistogram(SnippetCounter.Group group, int log2StepLength, String name, String description) {
        assert log2StepLength > 0;

        this.group = group;
        this.name = name;

        int lowerBound = 0;
        counter0UpperBound = 0;
        counter0 = createCounter(group, name, description, lowerBound, counter0UpperBound);

        lowerBound = counter0UpperBound + 1;
        counter1UpperBound = Math.max(1, lowerBound - 1) << log2StepLength;
        counter1 = createCounter(group, name, description, lowerBound, counter1UpperBound);

        lowerBound = counter1UpperBound + 1;
        counter2UpperBound = Math.max(1, lowerBound - 1) << log2StepLength;
        counter2 = createCounter(group, name, description, lowerBound, counter2UpperBound);

        lowerBound = counter2UpperBound + 1;
        counter3UpperBound = Math.max(1, lowerBound - 1) << log2StepLength;
        counter3 = createCounter(group, name, description, lowerBound, counter3UpperBound);

        lowerBound = counter3UpperBound + 1;
        counter4UpperBound = Math.max(1, lowerBound - 1) << log2StepLength;
        counter4 = createCounter(group, name, description, lowerBound, counter4UpperBound);

        lowerBound = counter4UpperBound + 1;
        counter5UpperBound = Math.max(1, lowerBound - 1) << log2StepLength;
        counter5 = createCounter(group, name, description, lowerBound, counter5UpperBound);

        lowerBound = counter5UpperBound + 1;
        counter6UpperBound = Math.max(1, lowerBound - 1) << log2StepLength;
        counter6 = createCounter(group, name, description, lowerBound, counter6UpperBound);

        lowerBound = counter6UpperBound + 1;
        counter7UpperBound = Math.max(1, lowerBound - 1) << log2StepLength;
        counter7 = createCounter(group, name, description, lowerBound, counter7UpperBound);

        lowerBound = counter7UpperBound + 1;
        counter8UpperBound = Math.max(1, lowerBound - 1) << log2StepLength;
        counter8 = createCounter(group, name, description, lowerBound, counter8UpperBound);

        lowerBound = counter8UpperBound + 1;
        counter9UpperBound = Math.max(1, lowerBound - 1) << log2StepLength;
        counter9 = createCounter(group, name, description, lowerBound, counter9UpperBound);

        lowerBound = counter9UpperBound + 1;
        counter10 = createCounter(group, name, description, lowerBound, Long.MAX_VALUE);
    }

    private static SnippetCounter createCounter(SnippetCounter.Group group, String name, String description, long lowerBound, long upperBound) {
        if (group != null) {
            SnippetCounter snippetCounter = new SnippetCounter(group, name + "[" + lowerBound + ", " + upperBound + "]", description);
            return snippetCounter;
        }
        return null;
    }

    /**
     * Increments the value of the matching histogram element. This method can only be used in a
     * snippet on a compile-time constant {@link SnippetIntegerHistogram} object.
     */
    public void inc(long value) {
        if (group != null) {
            if (value <= counter0UpperBound) {
                counter0.inc();
            } else if (value <= counter1UpperBound) {
                counter1.inc();
            } else if (value <= counter2UpperBound) {
                counter2.inc();
            } else if (value <= counter3UpperBound) {
                counter3.inc();
            } else if (value <= counter4UpperBound) {
                counter4.inc();
            } else if (value <= counter5UpperBound) {
                counter5.inc();
            } else if (value <= counter6UpperBound) {
                counter6.inc();
            } else if (value <= counter7UpperBound) {
                counter7.inc();
            } else if (value <= counter8UpperBound) {
                counter8.inc();
            } else if (value <= counter9UpperBound) {
                counter9.inc();
            } else {
                counter10.inc();
            }
        }
    }

    @Override
    public String toString() {
        if (group != null) {
            return "SnippetHistogram-" + group.name + ":" + name;
        }
        return super.toString();
    }
}
