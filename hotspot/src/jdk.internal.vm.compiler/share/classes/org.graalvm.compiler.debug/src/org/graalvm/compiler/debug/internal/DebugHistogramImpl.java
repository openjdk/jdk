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
package org.graalvm.compiler.debug.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.graalvm.compiler.debug.DebugHistogram;

public class DebugHistogramImpl implements DebugHistogram {

    private final String name;
    private HashMap<Object, CountedValue> map = new HashMap<>();

    public DebugHistogramImpl(String name) {
        this.name = name;
    }

    @Override
    public void add(Object value) {
        CountedValue cv = map.get(value);
        if (cv == null) {
            map.put(value, new CountedValue(1, value));
        } else {
            cv.inc();
        }
    }

    @Override
    public void add(Object value, long count) {
        CountedValue cv = map.get(value);
        if (cv == null) {
            map.put(value, new CountedValue(count, value));
        } else {
            cv.add(count);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<CountedValue> getValues() {
        ArrayList<CountedValue> res = new ArrayList<>(map.values());
        Collections.sort(res);
        return res;
    }
}
