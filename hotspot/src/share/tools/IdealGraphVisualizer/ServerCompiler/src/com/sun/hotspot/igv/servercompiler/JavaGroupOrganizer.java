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
package com.sun.hotspot.igv.servercompiler;

import com.sun.hotspot.igv.data.Group;
import com.sun.hotspot.igv.data.services.GroupOrganizer;
import com.sun.hotspot.igv.data.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author Thomas Wuerthinger
 */
public class JavaGroupOrganizer implements GroupOrganizer {

    public String getName() {
        return "Java structure";
    }

    public List<Pair<String, List<Group>>> organize(List<String> subFolders, List<Group> groups) {

        List<Pair<String, List<Group>>> result = new ArrayList<Pair<String, List<Group>>>();

        if (subFolders.size() == 0) {
            buildResult(result, groups, packageNameProvider);
        } else if (subFolders.size() == 1) {
            buildResult(result, groups, classNameProvider);
        } else if (subFolders.size() == 2) {
            for (Group g : groups) {
                List<Group> children = new ArrayList<Group>();
                children.add(g);
                Pair<String, List<Group>> p = new Pair<String, List<Group>>();
                p.setLeft(reducedNameProvider.getName(g));
                p.setRight(children);
                result.add(p);
            }
        } else {
            result.add(new Pair<String, List<Group>>("", groups));
        }

        return result;
    }

    private void buildResult(List<Pair<String, List<Group>>> result, List<Group> groups, NameProvider provider) {
        HashMap<String, List<Group>> map = new HashMap<String, List<Group>>();
        for (Group g : groups) {
            String s = provider.getName(g);

            if (!map.containsKey(s)) {
                List<Group> list = new ArrayList<Group>();
                Pair<String, List<Group>> pair = new Pair<String, List<Group>>(s, list);
                result.add(pair);
                map.put(s, list);
            }

            List<Group> curList = map.get(s);
            curList.add(g);
        }

        Collections.sort(result, new Comparator<Pair<String, List<Group>>>() {

            public int compare(Pair<String, List<Group>> a, Pair<String, List<Group>> b) {
                return a.getLeft().compareTo(b.getLeft());
            }
        });
    }

    private static interface NameProvider {

        public String getName(Group g);
    }
    private NameProvider reducedNameProvider = new NameProvider() {

        public String getName(Group g) {
            String name = g.getName();
            assert name != null : "name of group must be set!";
            final String noReducedName = name;

            int firstPoint = name.indexOf(".");
            if (firstPoint == -1) {
                return noReducedName;
            }

            int firstParenthese = name.indexOf("(");
            if (firstParenthese == -1 || firstParenthese < firstPoint) {
                return noReducedName;
            }

            int current = firstPoint;
            while (current > 0 && name.charAt(current) != ' ') {
                current--;
            }

            String tmp = name.substring(0, firstParenthese);
            int lastPoint = tmp.lastIndexOf(".");
            if (lastPoint == -1) {
                return noReducedName;
            }

            name = name.substring(0, current + 1) + name.substring(lastPoint + 1);
            return name;
        }
    };
    private NameProvider packageNameProvider = new NameProvider() {

        public String getName(Group g) {
            String name = g.getName();
            assert name != null : "name of group must be set!";
            final String noPackage = "<default>";

            int firstPoint = name.indexOf(".");
            if (firstPoint == -1) {
                return noPackage;
            }

            int firstParenthese = name.indexOf("(");
            if (firstParenthese == -1 || firstParenthese < firstPoint) {
                return noPackage;
            }

            int current = firstPoint;
            while (current > 0 && name.charAt(current) != ' ') {
                current--;
            }

            String fullClassName = name.substring(current + 1, firstParenthese);
            int lastPoint = fullClassName.lastIndexOf(".");
            if (lastPoint == -1) {
                return noPackage;
            }
            lastPoint = fullClassName.lastIndexOf(".", lastPoint - 1);
            if (lastPoint == -1) {
                return noPackage;
            }

            String packageName = fullClassName.substring(0, lastPoint);
            return packageName;
        }
    };
    private NameProvider classNameProvider = new NameProvider() {

        public String getName(Group g) {
            String name = g.getName();
            assert name != null : "name of group must be set!";

            final String noClass = "<noclass>";

            int firstPoint = name.indexOf(".");
            if (firstPoint == -1) {
                return noClass;
            }

            int firstParenthese = name.indexOf("(");
            if (firstParenthese == -1 || firstParenthese < firstPoint) {
                return noClass;
            }

            int current = firstPoint;
            while (current > 0 && name.charAt(current) != ' ') {
                current--;
            }

            String fullClassName = name.substring(current + 1, firstParenthese);
            int lastPoint = fullClassName.lastIndexOf(".");
            if (lastPoint == -1) {
                return noClass;
            }
            int lastlastPoint = fullClassName.lastIndexOf(".", lastPoint - 1);

            String className = fullClassName.substring(lastlastPoint + 1, lastPoint);
            return className;
        }
    };
}
