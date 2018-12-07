/*
 * Copyright (c) 1995, 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.java;

import java.util.Enumeration;
import java.io.File;
import java.io.IOException;

/**
 * This class is used to represent the classes in a package.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class Package {
    /**
     * The path which we use to locate source files.
     */
    ClassPath sourcePath;

    /**
     * The path which we use to locate class (binary) files.
     */
    ClassPath binaryPath;

    /**
     * The path name of the package.
     */
    String pkg;

    /**
     * Create a package given a class path, and package name.
     */
    public Package(ClassPath path, Identifier pkg) throws IOException {
        this(path, path, pkg);
    }

    /**
     * Create a package given a source path, binary path, and package
     * name.
     */
    public Package(ClassPath sourcePath,
                   ClassPath binaryPath,
                   Identifier pkg)
    throws IOException {
        if (pkg.isInner())
            pkg = Identifier.lookup(pkg.getQualifier(), pkg.getFlatName());
        this.sourcePath = sourcePath;
        this.binaryPath = binaryPath;
        this.pkg = pkg.toString().replace('.', File.separatorChar);
    }

    /**
     * Check if a class is defined in this package.
     * (If it is an inner class name, it is assumed to exist
     * only if its binary file exists.  This is somewhat pessimistic.)
     */
    public boolean classExists(Identifier className) {
        return getBinaryFile(className) != null ||
                !className.isInner() &&
               getSourceFile(className) != null;
    }

    /**
     * Check if the package exists
     */
    public boolean exists() {
        // Look for the directory on our binary path.
        ClassFile dir = binaryPath.getDirectory(pkg);
        if (dir != null && dir.isDirectory()) {
            return true;
        }

        if (sourcePath != binaryPath) {
            // Look for the directory on our source path.
            dir = sourcePath.getDirectory(pkg);
            if (dir != null && dir.isDirectory()) {
                return true;
            }
        }

        /* Accommodate ZIP files without CEN entries for directories
         * (packages): look on class path for at least one binary
         * file or one source file with the right package prefix
         */
        String prefix = pkg + File.separator;

        return binaryPath.getFiles(prefix, ".class").hasMoreElements()
            || sourcePath.getFiles(prefix, ".java").hasMoreElements();
    }

    private String makeName(String fileName) {
        return pkg.isEmpty() ? fileName : pkg + File.separator + fileName;
    }

    /**
     * Get the .class file of a class
     */
    public ClassFile getBinaryFile(Identifier className) {
        className = Type.mangleInnerType(className);
        String fileName = className.toString() + ".class";
        return binaryPath.getFile(makeName(fileName));
    }

    /**
     * Get the .java file of a class
     */
    public ClassFile getSourceFile(Identifier className) {
        // The source file of an inner class is that of its outer class.
        className = className.getTopName();
        String fileName = className.toString() + ".java";
        return sourcePath.getFile(makeName(fileName));
    }

    public ClassFile getSourceFile(String fileName) {
        if (fileName.endsWith(".java")) {
            return sourcePath.getFile(makeName(fileName));
        }
        return null;
    }

    public Enumeration<ClassFile> getSourceFiles() {
        return sourcePath.getFiles(pkg, ".java");
    }

    public Enumeration<ClassFile> getBinaryFiles() {
        return binaryPath.getFiles(pkg, ".class");
    }

    public String toString() {
        if (pkg.isEmpty()) {
            return "unnamed package";
        }
        return "package " + pkg;
    }
}
