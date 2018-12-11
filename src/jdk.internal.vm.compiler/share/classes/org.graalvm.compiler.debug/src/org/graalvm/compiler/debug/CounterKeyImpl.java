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


package org.graalvm.compiler.debug;

import jdk.internal.vm.compiler.collections.Pair;

class CounterKeyImpl extends AbstractKey implements CounterKey {

    CounterKeyImpl(String format, Object arg1, Object arg2) {
        super(format, arg1, arg2);
    }

    @Override
    public void increment(DebugContext debug) {
        add(debug, 1);
    }

    @Override
    public Pair<String, String> toCSVFormat(long value) {
        return Pair.create(String.valueOf(value), "");
    }

    @Override
    public String toHumanReadableFormat(long value) {
        return Long.toString(value);
    }

    @Override
    public void add(DebugContext debug, long value) {
        if (debug.isCounterEnabled(this)) {
            addToCurrentValue(debug, value);
        }
    }

    @Override
    public boolean isEnabled(DebugContext debug) {
        return debug.isCounterEnabled(this);
    }

    @Override
    public CounterKey doc(String doc) {
        setDoc(doc);
        return this;
    }
}
