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

/**
 * A name and index for a metric value.
 */
abstract class AbstractKey implements MetricKey {

    private final String nameFormat;
    private final Object nameArg1;
    private final Object nameArg2;

    private String name;
    private int index;
    private String doc;

    protected AbstractKey(String nameFormat, Object nameArg1, Object nameArg2) {
        this.nameFormat = nameFormat;
        this.nameArg1 = nameArg1;
        this.nameArg2 = nameArg2;
        this.index = -1;
    }

    protected void setDoc(String doc) {
        this.doc = doc;
    }

    @Override
    public String getDoc() {
        return doc;
    }

    @Override
    public String getDocName() {
        return getName();
    }

    public long getCurrentValue(DebugContext debug) {
        ensureInitialized();
        return debug.getMetricValue(index);
    }

    void setCurrentValue(DebugContext debug, long l) {
        ensureInitialized();
        debug.setMetricValue(index, l);
    }

    void ensureInitialized() {
        if (index == -1) {
            index = KeyRegistry.register(this);
        }
    }

    void addToCurrentValue(DebugContext debug, long value) {
        ensureInitialized();
        debug.setMetricValue(index, debug.getMetricValue(index) + value);
    }

    /**
     * Gets the globally unique index for the value represented by this object.
     */
    public int getIndex() {
        ensureInitialized();
        return index;
    }

    /**
     * Gets the globally unique name for the value represented by this object.
     */
    @Override
    public String getName() {
        if (name == null) {
            name = createName(nameFormat, nameArg1, nameArg2);
        }
        return name;
    }

    protected String createName(String format, Object arg1, Object arg2) {
        return DebugContext.formatDebugName(format, arg1, arg2);
    }

    @Override
    public String toString() {
        return getName() + "@" + index;
    }
}
