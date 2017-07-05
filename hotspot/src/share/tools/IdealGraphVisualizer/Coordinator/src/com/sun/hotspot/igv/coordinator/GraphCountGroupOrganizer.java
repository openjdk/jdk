/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.coordinator;

import com.sun.hotspot.igv.data.Group;
import com.sun.hotspot.igv.data.Pair;
import com.sun.hotspot.igv.data.services.GroupOrganizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author Thomas Wuerthinger
 */
public class GraphCountGroupOrganizer implements GroupOrganizer {

    public String getName() {
        return "Graph count structure";
    }

    public List<Pair<String, List<Group>>> organize(List<String> subFolders, List<Group> groups) {

        List<Pair<String, List<Group>>> result = new ArrayList<Pair<String, List<Group>>>();

        if (subFolders.size() == 0) {
            Map<Integer, List<Group>> map = new HashMap<Integer, List<Group>>();
            for (Group g : groups) {
                Integer cur = g.getGraphs().size();
                if (!map.containsKey(cur)) {
                    map.put(cur, new ArrayList<Group>());
                }
                map.get(cur).add(g);
            }

            SortedSet<Integer> keys = new TreeSet<Integer>(map.keySet());
            for (Integer i : keys) {
                result.add(new Pair<String, List<Group>>("Graph count " + i, map.get(i)));
            }

        } else if (subFolders.size() == 1) {
            for (Group g : groups) {
                List<Group> children = new ArrayList<Group>();
                children.add(g);
                Pair<String, List<Group>> p = new Pair<String, List<Group>>();
                p.setLeft(g.getName());
                p.setRight(children);
                result.add(p);
            }
        } else if (subFolders.size() == 2) {
            result.add(new Pair<String, List<Group>>("", groups));
        }

        return result;
    }
}
