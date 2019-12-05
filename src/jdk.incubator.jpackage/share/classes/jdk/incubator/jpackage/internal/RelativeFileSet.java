/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.jpackage.internal;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * RelativeFileSet
 *
 * A class encapsulating a directory and a set of files within it.
 */
class RelativeFileSet {

    private File basedir;
    private Set<String> files = new LinkedHashSet<>();

    RelativeFileSet(File base, Collection<File> files) {
        basedir = base;
        String baseAbsolute = basedir.getAbsolutePath();
        for (File f: files) {
            String absolute = f.getAbsolutePath();
            if (!absolute.startsWith(baseAbsolute)) {
                throw new RuntimeException("File " + f.getAbsolutePath() +
                        " does not belong to " + baseAbsolute);
            }
            if (!absolute.equals(baseAbsolute)) {
                    // possible in jpackage case
                this.files.add(absolute.substring(baseAbsolute.length()+1));
            }
        }
    }

    RelativeFileSet(File base, Set<File> files) {
        this(base, (Collection<File>) files);
    }

    File getBaseDirectory() {
        return basedir;
    }

    Set<String> getIncludedFiles() {
        return files;
    }

    @Override
    public String toString() {
        if (files.size() ==  1) {
            return "" + basedir + File.pathSeparator + files;
        }
        return "RelativeFileSet {basedir:" + basedir
                + ", files: {" + files + "}";
    }

}
