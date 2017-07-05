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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.tools.example.debug.gui;

import java.io.*;
import java.util.*;

public class SearchPath {

    private String pathString;

    private String[] pathArray;

    public SearchPath(String searchPath) {
        //### Should check searchpath for well-formedness.
        StringTokenizer st = new StringTokenizer(searchPath, File.pathSeparator);
        List<String> dlist = new ArrayList<String>();
        while (st.hasMoreTokens()) {
            dlist.add(st.nextToken());
        }
        pathString = searchPath;
        pathArray = dlist.toArray(new String[dlist.size()]);
    }

    public boolean isEmpty() {
        return (pathArray.length == 0);
    }

    public String asString() {
        return pathString;
    }

    public String[] asArray() {
        return pathArray.clone();
    }

    public File resolve(String relativeFileName) {
        for (String element : pathArray) {
            File path = new File(element, relativeFileName);
            if (path.exists()) {
                return path;
            }
        }
        return null;
    }

    //### return List?

    public String[] children(String relativeDirName, FilenameFilter filter) {
        // If a file appears at the same relative path
        // with respect to multiple entries on the classpath,
        // the one corresponding to the earliest entry on the
        // classpath is retained.  This is the one that will be
        // found if we later do a 'resolve'.
        SortedSet<String> s = new TreeSet<String>();  // sorted, no duplicates
        for (String element : pathArray) {
            File path = new File(element, relativeDirName);
            if (path.exists()) {
                String[] childArray = path.list(filter);
                if (childArray != null) {
                    for (int j = 0; j < childArray.length; j++) {
                        if (!s.contains(childArray[j])) {
                            s.add(childArray[j]);
                        }
                    }
                }
            }
        }
        return s.toArray(new String[s.size()]);
    }

}
