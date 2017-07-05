/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

import java.util.List;

/**
 * Facility for recording value frequencies.
 */
public interface DebugHistogram {

    /**
     * Gets the name specified when this objected was {@linkplain Debug#createHistogram(String)
     * created}.
     */
    String getName();

    /**
     * Increments the count for a given value.
     */
    void add(Object value);

    void add(Object value, long count);

    /**
     * A value and a frequency. The ordering imposed by {@link #compareTo(CountedValue)} places
     * values with higher frequencies first.
     */
    class CountedValue implements Comparable<CountedValue> {

        private long count;
        private final Object value;

        public CountedValue(long count, Object value) {
            this.count = count;
            this.value = value;
        }

        @Override
        public int compareTo(CountedValue o) {
            if (count < o.count) {
                return 1;
            } else if (count > o.count) {
                return -1;
            }
            return 0;
        }

        @Override
        public String toString() {
            return count + " -> " + value;
        }

        public void inc() {
            count++;
        }

        public void add(long n) {
            count += n;
        }

        public long getCount() {
            return count;
        }

        public Object getValue() {
            return value;
        }
    }

    /**
     * Gets a list of the counted values, sorted in descending order of frequency.
     */
    List<CountedValue> getValues();

    /**
     * Interface for a service that can render a visualization of a histogram.
     */
    public interface Printer {

        void print(DebugHistogram histogram);
    }
}
