/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.sjavac;

import java.io.File;
import java.util.Set;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/** A Source object maintains information about a source file.
 * For example which package it belongs to and kind of source it is.
 * The class also knows how to find source files (scanRoot) given include/exclude
 * patterns and a root.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class Source implements Comparable<Source> {
    // The package the source belongs to.
   private Package pkg;
    // Name of this source file, relative its source root.
    // For example: java/lang/Object.java
    // Or if the source file is inside a module:
    // jdk.base/java/lang/Object.java
    private String name;
    // What kind of file is this.
    private String suffix;
    // When this source file was last_modified
    private long lastModified;
    // The source File.
    private File file;
    // The source root under which file resides.
    private File root;
    // If the source is generated.
    private boolean isGenerated;
    // If the source is only linked to, not compiled.
    private boolean linkedOnly;

    @Override
    public boolean equals(Object o) {
        return (o instanceof Source) && name.equals(((Source)o).name);
    }

    @Override
    public int compareTo(Source o) {
        return name.compareTo(o.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public Source(Module m, String n, File f, File r) {
        name = n;
        int dp = n.lastIndexOf(".");
        if (dp != -1) {
            suffix = n.substring(dp);
        } else {
            suffix = "";
        }
        file = f;
        root = r;
        lastModified = f.lastModified();
        linkedOnly = false;
    }

    public Source(Package p, String n, long lm) {
        pkg = p;
        name = n;
        int dp = n.lastIndexOf(".");
        if (dp != -1) {
            suffix = n.substring(dp);
        } else {
            suffix = "";
        }
        file = null;
        root = null;
        lastModified = lm;
        linkedOnly = false;
        int ls = n.lastIndexOf('/');
    }

    public String name() { return name; }
    public String suffix() { return suffix; }
    public Package pkg() { return pkg; }
    public File   file() { return file; }
    public File   root() { return root; }
    public long lastModified() {
        return lastModified;
    }

    public void setPackage(Package p) {
        pkg = p;
    }

    public void markAsGenerated() {
        isGenerated = true;
    }

    public boolean isGenerated() {
        return isGenerated;
    }

    public void markAsLinkedOnly() {
        linkedOnly = true;
    }

    public boolean isLinkedOnly() {
        return linkedOnly;
    }

    private void save(StringBuilder b) {
        String CL = linkedOnly?"L":"C";
        String GS = isGenerated?"G":"S";
        b.append(GS+" "+CL+" "+name+" "+file.lastModified()+"\n");
    }
    // Parse a line that looks like this:
    // S C /code/alfa/A.java 1357631228000
    static public Source load(Package lastPackage, String l, boolean isGenerated) {
        int sp = l.indexOf(' ',4);
        if (sp == -1) return null;
        String name = l.substring(4,sp);
        long last_modified = Long.parseLong(l.substring(sp+1));

        boolean isLinkedOnly = false;
        if (l.charAt(2) == 'L') {
            isLinkedOnly = true;
        } else if (l.charAt(2) == 'C') {
            isLinkedOnly = false;
        } else return null;

        Source s = new Source(lastPackage, name, last_modified);
        s.file = new File(name);
        if (isGenerated) s.markAsGenerated();
        if (isLinkedOnly) s.markAsLinkedOnly();
        return s;
    }

    public static void saveSources(Map<String,Source> sources, StringBuilder b) {
        List<String> sorted_sources = new ArrayList<String>();
        for (String key : sources.keySet()) {
            sorted_sources.add(key);
        }
        Collections.sort(sorted_sources);
        for (String key : sorted_sources) {
            Source s = sources.get(key);
            s.save(b);
        }
    }

    /**
     * Recurse into the directory root and find all files matchine the excl/incl/exclfiles/inclfiles rules.
     * Detects the existence of module-info.java files and presumes that the directory it resides in
     * is the name of the current module.
     */
    static public void scanRoot(File root,
                                Set<String> suffixes,
                                List<String> excludes, List<String> includes,
                                List<String> excludeFiles, List<String> includeFiles,
                                Map<String,Source> foundFiles,
                                Map<String,Module> foundModules,
                                Module currentModule,
                                boolean permitSourcesWithoutPackage,
                                boolean inGensrc,
                                boolean inLinksrc)
        throws ProblemException {

        if (root == null) return;
        int root_prefix = root.getPath().length()+1;
        // This is the root source directory, it must not contain any Java sources files
        // because we do not allow Java source files without a package.
        // (Unless of course --permit-sources-without-package has been specified.)
        // It might contain other source files however, (for -tr and -copy) these will
        // always be included, since no package pattern can match the root directory.
        currentModule = addFilesInDir(root, root_prefix, root, suffixes, permitSourcesWithoutPackage,
                                       excludeFiles, includeFiles, false,
                                       foundFiles, foundModules, currentModule,
                                       inGensrc, inLinksrc);

        File[] dirfiles = root.listFiles();
        for (File d : dirfiles) {
            if (d.isDirectory()) {
                // Descend into the directory structure.
                scanDirectory(d, root_prefix, root, suffixes,
                              excludes, includes, excludeFiles, includeFiles,
                              false, foundFiles, foundModules, currentModule, inGensrc, inLinksrc);
            }
        }
    }

    /**
     * Test if a path matches any of the patterns given.
     * The pattern foo.bar matches only foo.bar
     * The pattern foo.* matches foo.bar and foo.bar.zoo etc
     */
    static private boolean hasMatch(String path, List<String> patterns) {
        for (String p : patterns) {
            // Exact match
            if (p.equals(path)) {
                return true;
            }
            // Single dot the end matches this package and all its subpackages.
            if (p.endsWith(".*")) {
                // Remove the wildcard
                String patprefix = p.substring(0,p.length()-2);
                // Does the path start with the pattern prefix?
                if (path.startsWith(patprefix)) {
                    // If the path has the same length as the pattern prefix, then it is a match.
                    // If the path is longer, then make sure that
                    // the next part of the path starts with a dot (.) to prevent
                    // wildcard matching in the middle of a package name.
                    if (path.length()==patprefix.length() || path.charAt(patprefix.length())=='.') {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Matches patterns with the asterisk first. */
     // The pattern foo/bar.java only matches foo/bar.java
     // The pattern */bar.java matches foo/bar.java and zoo/bar.java etc
    static private boolean hasFileMatch(String path, List<String> patterns) {
        path = Util.normalizeDriveLetter(path);
        for (String p : patterns) {
            // Exact match
            if (p.equals(path)) {
                return true;
            }
            // Single dot the end matches this package and all its subpackages.
            if (p.startsWith("*")) {
                // Remove the wildcard
                String patsuffix = p.substring(1);
                // Does the path start with the pattern prefix?
                if (path.endsWith(patsuffix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Add the files in the directory, assuming that the file has not been excluded.
     * Returns a fresh Module object, if this was a dir with a module-info.java file.
     */
    static private Module addFilesInDir(File dir, int rootPrefix, File root,
                                        Set<String> suffixes, boolean allow_javas,
                                        List<String> excludeFiles, List<String> includeFiles, boolean all,
                                        Map<String,Source> foundFiles,
                                        Map<String,Module> foundModules,
                                        Module currentModule,
                                        boolean inGensrc,
                                        boolean inLinksrc)
        throws ProblemException
    {
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                boolean should_add =
                    (excludeFiles == null || excludeFiles.isEmpty() || !hasFileMatch(f.getPath(), excludeFiles))
                    && (includeFiles == null || includeFiles.isEmpty() || hasFileMatch(f.getPath(), includeFiles));

                if (should_add) {
                    if (!allow_javas && f.getName().endsWith(".java")) {
                        throw new ProblemException("No .java files are allowed in the source root "+dir.getPath()+
                                                   ", please remove "+f.getName());
                    }
                    // Extract the file name relative the root.
                    String fn = f.getPath().substring(rootPrefix);
                    // Extract the package name.
                    int sp = fn.lastIndexOf(File.separatorChar);
                    String pkg = "";
                    if (sp != -1) {
                        pkg = fn.substring(0,sp).replace(File.separatorChar,'.');
                    }
                    // Is this a module-info.java file?
                    if (fn.endsWith("module-info.java")) {
                        // Aha! We have recursed into a module!
                        if (!currentModule.name().equals("")) {
                            throw new ProblemException("You have an extra module-info.java inside a module! Please remove "+fn);
                        }
                        String module_name = fn.substring(0,fn.length()-16);
                        currentModule = new Module(module_name, f.getPath());
                        foundModules.put(module_name, currentModule);
                    }
                    // Extract the suffix.
                    int dp = fn.lastIndexOf(".");
                    String suffix = "";
                    if (dp > 0) {
                        suffix = fn.substring(dp);
                    }
                    // Should the file be added?
                    if (all || suffixes.contains(suffix)) {
                        Source of = foundFiles.get(f.getPath());
                        if (of != null) {
                            throw new ProblemException("You have already added the file "+fn+" from "+of.file().getPath());
                        }
                        of = currentModule.lookupSource(f.getPath());
                        if (of != null) {
                            // Oups, the source is already added, could be ok, could be not, lets check.
                            if (inLinksrc) {
                                // So we are collecting sources for linking only.
                                if (of.isLinkedOnly()) {
                                    // Ouch, this one is also for linking only. Bad.
                                    throw new ProblemException("You have already added the link only file "+fn+" from "+of.file().getPath());
                                }
                                // Ok, the existing source is to be compiled. Thus this link only is redundant
                                // since all compiled are also linked to. Continue to the next source.
                                // But we need to add the source, so that it will be visible to linking,
                                // if not the multi core compile will fail because a JavaCompiler cannot
                                // find the necessary dependencies for its part of the source.
                                foundFiles.put(f.getPath(), of);
                                continue;
                            } else {
                                // We are looking for sources to compile, if we find an existing to be compiled
                                // source with the same name, it is an internal error, since we must
                                // find the sources to be compiled before we find the sources to be linked to.
                                throw new ProblemException("Internal error: Double add of file "+fn+" from "+of.file().getPath());
                            }
                        }
                        Source s = new Source(currentModule, f.getPath(), f, root);
                        if (inGensrc) s.markAsGenerated();
                        if (inLinksrc) {
                            s.markAsLinkedOnly();
                        }
                        pkg = currentModule.name()+":"+pkg;
                        foundFiles.put(f.getPath(), s);
                        currentModule.addSource(pkg, s);
                    }
                }
            }
        }
        return currentModule;
    }

    private static boolean gurka = false;

    static private void scanDirectory(File dir, int rootPrefix, File root,
                                      Set<String> suffixes,
                                      List<String> excludes, List<String> includes,
                                      List<String> excludeFiles, List<String> includeFiles, boolean all,
                                      Map<String,Source> foundFiles,
                                      Map<String,Module> foundModules,
                                      Module currentModule, boolean inGensrc, boolean inLinksrc)
        throws ProblemException {

        String pkg_name = "";
        // Remove the root prefix from the dir path, and replace file separator with dots
        // to get the package name.
        if (dir.getPath().length() > rootPrefix) {
            pkg_name = dir.getPath().substring(rootPrefix).replace(File.separatorChar,'.');
        }
        // Should this package directory be included and not excluded?
        if (all || ((includes==null || includes.isEmpty() || hasMatch(pkg_name, includes)) &&
                    (excludes==null || excludes.isEmpty() || !hasMatch(pkg_name, excludes)))) {
            // Add the source files.
            currentModule = addFilesInDir(dir, rootPrefix, root, suffixes, true, excludeFiles, includeFiles, all,
                                          foundFiles, foundModules, currentModule, inGensrc, inLinksrc);
        }

        for (File d : dir.listFiles()) {
            if (d.isDirectory()) {
                // Descend into the directory structure.
                scanDirectory(d, rootPrefix, root, suffixes,
                              excludes, includes, excludeFiles, includeFiles, all,
                              foundFiles, foundModules, currentModule, inGensrc, inLinksrc);
            }
        }
    }
}
