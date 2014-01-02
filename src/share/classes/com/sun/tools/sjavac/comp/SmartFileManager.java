/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.sjavac.comp;

import com.sun.tools.javac.util.ListBuffer;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import javax.tools.*;
import javax.tools.JavaFileObject.Kind;

/**
 * Intercepts reads and writes to the file system to gather
 * information about what artifacts are generated.
 *
 * Traps writes to certain files, if the content written is identical
 * to the existing file.
 *
 * Can also blind out the filemanager from seeing certain files in the file system.
 * Necessary to prevent javac from seeing some sources where the source path points.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class SmartFileManager extends ForwardingJavaFileManager<JavaFileManager> {

    // Set of sources that can be seen by javac.
    Set<URI> visibleSources = new HashSet<URI>();
    // Map from modulename:packagename to artifacts.
    Map<String,Set<URI>> packageArtifacts = new HashMap<String,Set<URI>>();
    // Where to print informational messages.
    PrintWriter stdout;

    public SmartFileManager(JavaFileManager fileManager) {
        super(fileManager);
    }

    public void setVisibleSources(Set<URI> s) {
        visibleSources = s;
    }

    public void cleanArtifacts() {
        packageArtifacts = new HashMap<String,Set<URI>>();
    }

    public void setLog(PrintWriter pw) {
        stdout = pw;
    }

    public Map<String,Set<URI>> getPackageArtifacts() {
        return packageArtifacts;
    }

    @Override
    public Iterable<JavaFileObject> list(Location location,
                                         String packageName,
                                         Set<Kind> kinds,
                                         boolean recurse)
        throws IOException
    {
        // Acquire the list of files.
        Iterable<JavaFileObject> files = super.list(location, packageName, kinds, recurse);
        if (visibleSources.isEmpty()) {
            return files;
        }
        // Now filter!
        ListBuffer<JavaFileObject> filteredFiles = new ListBuffer<JavaFileObject>();
        for (JavaFileObject f : files) {
            URI uri = f.toUri();
            String t = uri.toString();
            if (t.startsWith("jar:")
                || t.endsWith(".class")
                || visibleSources.contains(uri))
            {
                filteredFiles.add(f);
            }
        }
        return filteredFiles;
    }

    @Override
    public boolean hasLocation(Location location) {
        return super.hasLocation(location);
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location,
                                              String className,
                                              Kind kind)
        throws IOException
    {
        JavaFileObject file = super.getJavaFileForInput(location, className, kind);
        if (file == null || visibleSources.isEmpty()) {
            return file;
        }

        if (visibleSources.contains(file.toUri())) {
            return file;
        }
        return null;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location,
                                               String className,
                                               Kind kind,
                                               FileObject sibling)
        throws IOException
    {
        JavaFileObject file = super.getJavaFileForOutput(location, className, kind, sibling);
        if (file == null) return file;
        int dp = className.lastIndexOf('.');
        String pkg_name = "";
        if (dp != -1) {
            pkg_name = className.substring(0, dp);
        }
        // When modules are in use, then the mod_name might be something like "jdk_base"
        String mod_name = "";
        addArtifact(mod_name+":"+pkg_name, file.toUri());
        return file;
    }

    @Override
    public FileObject getFileForInput(Location location,
                                      String packageName,
                                      String relativeName)
        throws IOException
    {
        FileObject file =  super.getFileForInput(location, packageName, relativeName);
        if (file == null || visibleSources.isEmpty()) {
            return file;
        }

        if (visibleSources.contains(file.toUri())) {
            return file;
        }
        return null;
    }

    @Override
    public FileObject getFileForOutput(Location location,
                                       String packageName,
                                       String relativeName,
                                       FileObject sibling)
        throws IOException
    {
        FileObject file = super.getFileForOutput(location, packageName, relativeName, sibling);
        if (file == null) return file;
        if (location.equals(StandardLocation.NATIVE_HEADER_OUTPUT) &&
                file instanceof JavaFileObject) {
           file = new SmartFileObject((JavaFileObject)file, stdout);
           packageName = ":" + packageNameFromFileName(relativeName);
        }
        if (packageName.equals("")) {
            packageName = ":";
        }
        addArtifact(packageName, file.toUri());
        return file;
    }

    private String packageNameFromFileName(String fn) {
        StringBuilder sb = new StringBuilder();
        int p = fn.indexOf('_'), pp = 0;
        while (p != -1) {
            if (sb.length() > 0) sb.append('.');
            sb.append(fn.substring(pp,p));
            if (p == fn.length()-1) break;
            pp = p+1;
            p = fn.indexOf('_',pp);
        }
        return sb.toString();
    }

    @Override
    public void flush() throws IOException {
        super.flush();
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    void addArtifact(String pkgName, URI art) {
        Set<URI> s = packageArtifacts.get(pkgName);
        if (s == null) {
            s = new HashSet<URI>();
            packageArtifacts.put(pkgName, s);
        }
        s.add(art);
    }
}
