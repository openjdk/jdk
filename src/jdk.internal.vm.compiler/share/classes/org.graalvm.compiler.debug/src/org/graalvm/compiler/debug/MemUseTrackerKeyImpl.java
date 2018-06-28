/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.debug.DebugCloseable.VOID_CLOSEABLE;

import jdk.internal.vm.compiler.collections.Pair;

class MemUseTrackerKeyImpl extends AccumulatedKey implements MemUseTrackerKey {

    MemUseTrackerKeyImpl(String format, Object arg1, Object arg2) {
        super(new FlatMemUseTracker(format, arg1, arg2), format, arg1, arg2);
    }

    @Override
    public DebugCloseable start(DebugContext debug) {
        if (debug.isMemUseTrackerEnabled(this)) {
            CloseableCounter result = new MemUseCloseableCounterImpl(this, debug);
            debug.currentMemUseTracker = result;
            return result;
        }
        return VOID_CLOSEABLE;
    }

    public static String valueToString(long value) {
        return String.format("%d bytes", value);
    }

    @Override
    public String toHumanReadableFormat(long value) {
        return valueToString(value);
    }

    static final class FlatMemUseTracker extends AbstractKey implements MetricKey {

        FlatMemUseTracker(String nameFormat, Object nameArg1, Object nameArg2) {
            super(nameFormat, nameArg1, nameArg2);
        }

        @Override
        protected String createName(String format, Object arg1, Object arg2) {
            return super.createName(format, arg1, arg2) + FLAT_KEY_SUFFIX;
        }

        @Override
        public MetricKey doc(String doc) {
            throw new IllegalArgumentException("Cannot set documentation for derived key " + getName());
        }

        @Override
        public String getDocName() {
            return null;
        }

        @Override
        public String toHumanReadableFormat(long value) {
            return valueToString(value);
        }

        @Override
        public Pair<String, String> toCSVFormat(long value) {
            return Pair.create(String.valueOf(value), "bytes");
        }
    }

    static final class MemUseCloseableCounterImpl extends CloseableCounter implements DebugCloseable {

        private final DebugContext debug;

        MemUseCloseableCounterImpl(AccumulatedKey counter, DebugContext debug) {
            super(debug, debug.currentMemUseTracker, counter);
            this.debug = debug;
        }

        @Override
        long getCounterValue() {
            return MemUseTrackerKey.getCurrentThreadAllocatedBytes();
        }

        @Override
        public void close() {
            super.close();
            debug.currentMemUseTracker = parent;
        }
    }

    @Override
    public Pair<String, String> toCSVFormat(long value) {
        return Pair.create(String.valueOf(value), "bytes");
    }

    @Override
    public MemUseTrackerKey doc(String doc) {
        setDoc(doc);
        return this;
    }
}
