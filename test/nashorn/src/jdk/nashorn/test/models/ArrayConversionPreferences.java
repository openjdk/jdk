/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.test.models;

import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class ArrayConversionPreferences {
    public boolean testCollectionOverMap(final Collection x) { return true; }
    public boolean testCollectionOverMap(final Map x) { return false; }

    public boolean testCollectionOverArray(final Collection x) { return true; }
    public boolean testCollectionOverArray(final Object[] x) { return false; }

    public boolean testListOverMap(final List x) { return true; }
    public boolean testListOverMap(final Map x) { return false; }

    public boolean testListOverArray(final List x) { return true; }
    public boolean testListOverArray(final Object[] x) { return false; }

    public boolean testListOverCollection(final List x) { return true; }
    public boolean testListOverCollection(final Collection x) { return false; }

    public boolean testQueueOverMap(final Queue x) { return true; }
    public boolean testQueueOverMap(final Map x) { return false; }

    public boolean testQueueOverArray(final Queue x) { return true; }
    public boolean testQueueOverArray(final Object[] x) { return false; }

    public boolean testQueueOverCollection(final Queue x) { return true; }
    public boolean testQueueOverCollection(final Collection x) { return false; }

    public boolean testDequeOverMap(final Deque x) { return true; }
    public boolean testDequeOverMap(final Map x) { return false; }

    public boolean testDequeOverArray(final Deque x) { return true; }
    public boolean testDequeOverArray(final Object[] x) { return false; }

    public boolean testDequeOverCollection(final Deque x) { return true; }
    public boolean testDequeOverCollection(final Collection x) { return false; }

    public boolean testDequeOverQueue(final Deque x) { return true; }
    public boolean testDequeOverQueue(final Queue x) { return false; }

    public boolean testArrayOverMap(final Object[] x) { return true; }
    public boolean testArrayOverMap(final Map x) { return false; }
}

