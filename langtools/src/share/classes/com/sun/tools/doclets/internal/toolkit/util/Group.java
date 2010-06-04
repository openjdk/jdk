/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.javadoc.*;
import java.util.*;

/**
 * Process and manage grouping of packages, as specified by "-group" option on
 * the command line.
 * <p>
 * For example, if user has used -group option as
 * -group "Core Packages" "java.*" -group "CORBA Packages" "org.omg.*", then
 * the packages specified on the command line will be grouped according to their
 * names starting with either "java." or "org.omg.". All the other packages
 * which do not fall in the user given groups, are grouped in default group,
 * named as either "Other Packages" or "Packages" depending upon if "-group"
 * option used or not at all used respectively.
 * </p>
 * <p>
 * Also the packages are grouped according to the longest possible match of
 * their names with the grouping information provided. For example, if there
 * are two groups, like -group "Lang" "java.lang" and -group "Core" "java.*",
 * will put the package java.lang in the group "Lang" and not in group "Core".
 * </p>
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Atul M Dambalkar
 */
public class Group {

    /**
     * Map of regular expressions with the corresponding group name.
     */
    private Map<String,String> regExpGroupMap = new HashMap<String,String>();

    /**
     * List of regular expressions sorted according to the length. Regular
     * expression with longest length will be first in the sorted order.
     */
    private List<String> sortedRegExpList = new ArrayList<String>();

    /**
     * List of group names in the same order as given on the command line.
     */
    private List<String> groupList = new ArrayList<String>();

    /**
     * Map of non-regular expressions(possible package names) with the
     * corresponding group name.
     */
    private Map<String,String> pkgNameGroupMap = new HashMap<String,String>();

    /**
     * The global configuration information for this run.
     */
    private final Configuration configuration;

    /**
     * Since we need to sort the keys in the reverse order(longest key first),
     * the compare method in the implementing class is doing the reverse
     * comparison.
     */
    private static class MapKeyComparator implements Comparator<String> {
        public int compare(String key1, String key2) {
            return key2.length() - key1.length();
        }
    }

    public Group(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Depending upon the format of the package name provided in the "-group"
     * option, generate two separate maps. There will be a map for mapping
     * regular expression(only meta character allowed is '*' and that is at the
     * end of the regular expression) on to the group name. And another map
     * for mapping (possible) package names(if the name format doesen't contain
     * meta character '*', then it is assumed to be a package name) on to the
     * group name. This will also sort all the regular expressions found in the
     * reverse order of their lengths, i.e. longest regular expression will be
     * first in the sorted list.
     *
     * @param groupname       The name of the group from -group option.
     * @param pkgNameFormList List of the package name formats.
     */
    public boolean checkPackageGroups(String groupname,
            String pkgNameFormList) {
        StringTokenizer strtok = new StringTokenizer(pkgNameFormList, ":");
        if (groupList.contains(groupname)) {
            configuration.message.warning("doclet.Groupname_already_used", groupname);
            return false;
        }
        groupList.add(groupname);
        while (strtok.hasMoreTokens()) {
            String id = strtok.nextToken();
            if (id.length() == 0) {
                configuration.message.warning("doclet.Error_in_packagelist", groupname, pkgNameFormList);
                return false;
            }
            if (id.endsWith("*")) {
                id = id.substring(0, id.length() - 1);
                if (foundGroupFormat(regExpGroupMap, id)) {
                    return false;
                }
                regExpGroupMap.put(id, groupname);
                sortedRegExpList.add(id);
            } else {
                if (foundGroupFormat(pkgNameGroupMap, id)) {
                    return false;
                }
                pkgNameGroupMap.put(id, groupname);
            }
        }
        Collections.sort(sortedRegExpList, new MapKeyComparator());
        return true;
    }

    /**
     * Search if the given map has given the package format.
     *
     * @param map Map to be searched.
     * @param pkgFormat The pacakge format to search.
     *
     * @return true if package name format found in the map, else false.
     */
    boolean foundGroupFormat(Map<String,?> map, String pkgFormat) {
        if (map.containsKey(pkgFormat)) {
            configuration.message.error("doclet.Same_package_name_used", pkgFormat);
            return true;
        }
        return false;
    }

    /**
     * Group the packages according the grouping information provided on the
     * command line. Given a list of packages, search each package name in
     * regular expression map as well as package name map to get the
     * corresponding group name. Create another map with mapping of group name
     * to the package list, which will fall under the specified group. If any
     * package doesen't belong to any specified group on the comamnd line, then
     * a new group named "Other Packages" will be created for it. If there are
     * no groups found, in other words if "-group" option is not at all used,
     * then all the packages will be grouped under group "Packages".
     *
     * @param packages Packages specified on the command line.
     */
    public Map<String,List<PackageDoc>> groupPackages(PackageDoc[] packages) {
        Map<String,List<PackageDoc>> groupPackageMap = new HashMap<String,List<PackageDoc>>();
        String defaultGroupName =
            (pkgNameGroupMap.isEmpty() && regExpGroupMap.isEmpty())?
                configuration.message.getText("doclet.Packages") :
                configuration.message.getText("doclet.Other_Packages");
        // if the user has not used the default group name, add it
        if (!groupList.contains(defaultGroupName)) {
            groupList.add(defaultGroupName);
        }
        for (int i = 0; i < packages.length; i++) {
            PackageDoc pkg = packages[i];
            String pkgName = pkg.name();
            String groupName = pkgNameGroupMap.get(pkgName);
            // if this package is not explicitly assigned to a group,
            // try matching it to group specified by regular expression
            if (groupName == null) {
                groupName = regExpGroupName(pkgName);
            }
            // if it is in neither group map, put it in the default
            // group
            if (groupName == null) {
                groupName = defaultGroupName;
            }
            getPkgList(groupPackageMap, groupName).add(pkg);
        }
        return groupPackageMap;
    }

    /**
     * Search for package name in the sorted regular expression
     * list, if found return the group name.  If not, return null.
     *
     * @param pkgName Name of package to be found in the regular
     * expression list.
     */
    String regExpGroupName(String pkgName) {
        for (int j = 0; j < sortedRegExpList.size(); j++) {
            String regexp = sortedRegExpList.get(j);
            if (pkgName.startsWith(regexp)) {
                return regExpGroupMap.get(regexp);
            }
        }
        return null;
    }

    /**
     * For the given group name, return the package list, on which it is mapped.
     * Create a new list, if not found.
     *
     * @param map Map to be searched for gorup name.
     * @param groupname Group name to search.
     */
    List<PackageDoc> getPkgList(Map<String,List<PackageDoc>> map, String groupname) {
        List<PackageDoc> list = map.get(groupname);
        if (list == null) {
            list = new ArrayList<PackageDoc>();
            map.put(groupname, list);
        }
        return list;
    }

    /**
     * Return the list of groups, in the same order as specified
     * on the command line.
     */
    public List<String> getGroupList() {
        return groupList;
    }
}
