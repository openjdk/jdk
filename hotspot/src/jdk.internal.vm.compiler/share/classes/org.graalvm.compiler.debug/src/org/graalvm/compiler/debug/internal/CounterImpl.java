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
package org.graalvm.compiler.debug.internal;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.debug.internal.method.MethodMetricsImpl;

public abstract class CounterImpl extends DebugValue implements DebugCounter {

    public CounterImpl(String name, boolean conditional) {
        super(name, conditional);
        if (isEnabled()) {
            // Allows for zero counters to be shown
            getCurrentValue();
        }
    }

    @Override
    public void increment() {
        add(1);
    }

    @Override
    public String rawUnit() {
        return "";
    }

    @Override
    public String toRawString(long value) {
        return Long.toString(value);
    }

    @Override
    public String toString(long value) {
        return Long.toString(value);
    }

    private static final class InterceptingCounterImpl extends CounterImpl {

        private InterceptingCounterImpl(String name, boolean conditional) {
            super(name, conditional);
        }

        @Override
        public void add(long value) {
            if (isEnabled()) {
                if (Debug.isMethodMeterEnabled()) {
                    MethodMetricsImpl.addToCurrentScopeMethodMetrics(getName(), value);
                }
                super.addToCurrentValue(value);
            }
        }
    }

    private static final class PlainCounterImpl extends CounterImpl {

        private PlainCounterImpl(String name, boolean conditional) {
            super(name, conditional);
        }

        @Override
        public void add(long value) {
            if (isEnabled()) {
                super.addToCurrentValue(value);
            }
        }
    }

    public static DebugCounter create(String name, boolean conditional, boolean intercepting) {
        if (intercepting) {
            return new InterceptingCounterImpl(name, conditional);
        }
        return new PlainCounterImpl(name, conditional);
    }
}
