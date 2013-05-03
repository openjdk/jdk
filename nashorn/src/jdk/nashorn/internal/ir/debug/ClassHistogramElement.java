/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.ir.debug;

import java.util.Comparator;

/**
 * Class histogram element for IR / Java object instrumentation
 */
public class ClassHistogramElement {
    /**
     * Instance comparator
     */
    public static final Comparator<ClassHistogramElement> COMPARE_INSTANCES = new Comparator<ClassHistogramElement>() {
        @Override
        public int compare(final ClassHistogramElement o1, final ClassHistogramElement o2) {
            return (int)Math.abs(o1.instances - o2.instances);
        }
    };

    /**
     * Bytes comparator
     */
    public static final Comparator<ClassHistogramElement> COMPARE_BYTES = new Comparator<ClassHistogramElement>() {
        @Override
        public int compare(final ClassHistogramElement o1, final ClassHistogramElement o2) {
            return (int)Math.abs(o1.bytes - o2.bytes);
        }
    };

    /**
     * Classname comparator
     */
    public static final Comparator<ClassHistogramElement> COMPARE_CLASSNAMES = new Comparator<ClassHistogramElement>() {
        @Override
        public int compare(final ClassHistogramElement o1, final ClassHistogramElement o2) {
            return o1.clazz.getCanonicalName().compareTo(o2.clazz.getCanonicalName());
        }
    };

    private final Class<?> clazz;
    private long instances;
    private long bytes;

    /**
     * Constructor
     * @param clazz class for which to construct histogram
     */
    public ClassHistogramElement(final Class<?> clazz) {
        this.clazz = clazz;
    }

    /**
     * Add an instance
     * @param sizeInBytes byte count
     */
    public void addInstance(final long sizeInBytes) {
        instances++;
        this.bytes += sizeInBytes;
    }

    /**
     * Get size in bytes
     * @return size in bytes
     */
    public long getBytes() {
        return bytes;
    }

    /**
     * Get class
     * @return class
     */
    public Class<?> getClazz() {
        return clazz;
    }

    /**
     * Get number of instances
     * @return number of instances
     */
    public long getInstances() {
        return instances;
    }

    @Override
    public String toString() {
        return "ClassHistogramElement[class=" + clazz.getCanonicalName() + ", instances=" + instances + ", bytes=" + bytes + "]";
    }
}
