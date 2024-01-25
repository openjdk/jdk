/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package com.sun.hotspot.igv.selectioncoordinator;

import com.sun.hotspot.igv.data.ChangedEvent;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Thomas
 */
public class SelectionCoordinator {

    private static final SelectionCoordinator singleInstance = new SelectionCoordinator();
    private final Set<Integer> selectedObjects;
    private final Set<Integer> highlightedObjects;
    private final ChangedEvent<SelectionCoordinator> selectedChangedEvent;
    private final ChangedEvent<SelectionCoordinator> highlightedChangedEvent;

    public static SelectionCoordinator getInstance() {
        return singleInstance;
    }

    private SelectionCoordinator() {
        selectedChangedEvent = new ChangedEvent<>(this);
        highlightedChangedEvent = new ChangedEvent<>(this);
        selectedObjects = new HashSet<>();
        highlightedObjects = new HashSet<>();
    }

    public Set<Integer> getSelectedObjects() {
        return Collections.unmodifiableSet(selectedObjects);
    }

    public Set<Integer> getHighlightedObjects() {
        return Collections.unmodifiableSet(highlightedObjects);
    }

    public ChangedEvent<SelectionCoordinator> getHighlightedChangedEvent() {
        return highlightedChangedEvent;
    }

    public ChangedEvent<SelectionCoordinator> getSelectedChangedEvent() {
        return selectedChangedEvent;
    }


    public void setSelectedObjects(Set<Integer> s) {
        assert s != null;
        selectedObjects.clear();
        selectedObjects.addAll(s);
        getSelectedChangedEvent().fire();
    }

    public void setHighlightedObjects(Set<Integer> s) {
        assert s != null;
        highlightedObjects.clear();
        highlightedObjects.addAll(s);
        getHighlightedChangedEvent().fire();
    }
}
