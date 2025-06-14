/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.view;

import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputLiveRange;
import java.util.*;

public class LiveRangeQuickSearch extends SimpleQuickSearch {

    @Override
    String prefix() {
        return "L";
    }

    @Override
    String id(Object entity) {
        assert entity instanceof InputLiveRange;
        return Integer.toString(((InputLiveRange)entity).getId());
    }

    @Override
    Collection<Object> getAllEntities(InputGraph inputGraph) {
        return new ArrayList<>(inputGraph.getLiveRanges());
    }

    @Override
    void selectEntity(EditorTopComponent editor, Object entity) {
        assert entity instanceof InputLiveRange;
        Set<InputLiveRange> entitySingleton = new HashSet<>();
        entitySingleton.add((InputLiveRange)entity);
        editor.addSelectedLiveRanges(entitySingleton, true);
        editor.centerSelectedLiveRanges();
    }
}
