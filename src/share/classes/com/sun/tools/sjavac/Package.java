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

package com.sun.tools.sjavac;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Package class maintains meta information about a package.
 * For example its sources, dependents,its pubapi and its artifacts.
 *
 * It might look odd that we track dependents/pubapi/artifacts on
 * a package level, but it makes sense since recompiling a full package
 * takes as long as recompiling a single java file in that package,
 * if you take into account the startup time of the jvm.
 *
 * Also the dependency information will be much smaller (good for the javac_state file size)
 * and it simplifies tracking artifact generation, you do not always know from which
 * source a class file was generated, but you always know which package it belongs to.
 *
 * It is also educational to see package dependencies triggering recompilation of
 * other packages. Even though the recompilation was perhaps not necessary,
 * the visible recompilation of the dependent packages indicates how much circular
 * dependencies your code has.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class Package implements Comparable<Package> {
    // The module this package belongs to. (There is a legacy module with an empty string name,
    // used for all legacy sources.)
    private Module mod;
    // Name of this package, module:pkg
    // ex1 jdk.base:java.lang
    // ex2 :java.lang (when in legacy mode)
    private String name;
    // The directory path to the package. If the package belongs to a module,
    // then that module's file system name is part of the path.
    private String dirname;
    // This package depends on these packages.
    private Set<String> dependencies = new HashSet<String>();
    // This package has the following dependents, that depend on this package.
    private Set<String> dependents = new HashSet<String>();
    // This is the public api of this package.
    private List<String> pubapi = new ArrayList<String>();
    // Map from source file name to Source info object.
    private Map<String,Source> sources = new HashMap<String,Source>();
    // This package generated these artifacts.
    private Map<String,File> artifacts = new HashMap<String,File>();

    public Package(Module m, String n) {
        int c = n.indexOf(":");
        assert(c != -1);
        String mn = n.substring(0,c);
        assert(m.name().equals(m.name()));
        name = n;
        dirname = n.replace('.', File.separatorChar);
        if (m.name().length() > 0) {
            // There is a module here, prefix the module dir name to the path.
            dirname = m.dirname()+File.separatorChar+dirname;
        }
    }

    public Module mod() { return mod; }
    public String name() { return name; }
    public String dirname() { return dirname; }
    public Map<String,Source> sources() { return sources; }
    public Map<String,File> artifacts() { return artifacts; }
    public List<String> pubapi() { return pubapi; }

    public Set<String> dependencies() { return dependencies; }
    public Set<String> dependents() { return dependents; }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Package) && name.equals(((Package)o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public int compareTo(Package o) {
        return name.compareTo(o.name);
    }

    public void addSource(Source s) {
        sources.put(s.file().getPath(), s);
    }

    public void addDependency(String d) {
        dependencies.add(d);
    }

    public void addDependent(String d) {
        dependents.add(d);
    }

    public void addPubapi(String p) {
        pubapi.add(p);
    }

    /**
     * Check if we have knowledge in the javac state that
     * describe the results of compiling this package before.
     */
    public boolean existsInJavacState() {
        return artifacts.size() > 0 || pubapi.size() > 0;
    }

    public static List<String> pubapiToList(String ps)
    {
        String[] lines = ps.split("\n");
        List<String> r = new ArrayList<String>();
        for (String l : lines) {
            r.add(l);
        }
        return r;
    }

    public boolean hasPubapiChanged(List<String> ps) {
        Iterator<String> i = ps.iterator();
        Iterator<String> j = pubapi.iterator();
        int line = 0;
        while (i.hasNext() && j.hasNext()) {
            String is = i.next();
            String js = j.next();
            if (!is.equals(js)) {
                Log.debug("Change in pubapi for package "+name+" line "+line);
                Log.debug("Old: "+js);
                Log.debug("New: "+is);
                return true;
            }
            line++;
        }
        if ((i.hasNext() && !j.hasNext() ) ||
            (!i.hasNext() && j.hasNext())) {
            Log.debug("Change in pubapi for package "+name);
            if (i.hasNext()) {
                Log.debug("New has more lines!");
            } else {
                Log.debug("Old has more lines!");
            }
            return true;
        }
        return false;
    }

    public void setPubapi(List<String> ps) {
        pubapi = ps;
    }

    public void setDependencies(Set<String> ds) {
        dependencies = ds;
    }

    public void save(StringBuilder b) {
        b.append("P ").append(name).append("\n");
        Source.saveSources(sources, b);
        saveDependencies(b);
        savePubapi(b);
        saveArtifacts(b);
    }

    static public Package load(Module module, String l) {
        String name = l.substring(2);
        return new Package(module, name);
    }

    public void loadDependency(String l) {
        String n = l.substring(2);
        addDependency(n);
    }

    public void loadPubapi(String l) {
        String pi = l.substring(2);
        addPubapi(pi);
    }

    public void saveDependencies(StringBuilder b) {
        List<String> sorted_dependencies = new ArrayList<String>();
        for (String key : dependencies) {
            sorted_dependencies.add(key);
        }
        Collections.sort(sorted_dependencies);
        for (String a : sorted_dependencies) {
            b.append("D "+a+"\n");
        }
    }

    public void savePubapi(StringBuilder b) {
        for (String l : pubapi) {
            b.append("I "+l+"\n");
        }
    }

    public static void savePackages(Map<String,Package> packages, StringBuilder b) {
        List<String> sorted_packages = new ArrayList<String>();
        for (String key : packages.keySet() ) {
            sorted_packages.add(key);
        }
        Collections.sort(sorted_packages);
        for (String s : sorted_packages) {
            Package p = packages.get(s);
            p.save(b);
        }
    }

    public void addArtifact(String a) {
        artifacts.put(a, new File(a));
    }

    public void addArtifact(File f) {
        artifacts.put(f.getPath(), f);
    }

    public void addArtifacts(Set<URI> as) {
        for (URI u : as) {
            addArtifact(new File(u));
        }
    }

    public void setArtifacts(Set<URI> as) {
        assert(!artifacts.isEmpty());
        artifacts = new HashMap<String,File>();
        addArtifacts(as);
    }

    public void loadArtifact(String l) {
        // Find next space after "A ".
        int dp = l.indexOf(' ',2);
        String fn = l.substring(2,dp);
        long last_modified = Long.parseLong(l.substring(dp+1));
        File f = new File(fn);
        if (f.exists() && f.lastModified() != last_modified) {
            // Hmm, the artifact on disk does not have the same last modified
            // timestamp as the information from the build database.
            // We no longer trust the artifact on disk. Delete it.
            // The smart javac wrapper will then rebuild the artifact.
            Log.debug("Removing "+f.getPath()+" since its timestamp does not match javac_state.");
            f.delete();
        }
        artifacts.put(f.getPath(), f);
    }

    public void saveArtifacts(StringBuilder b) {
        List<File> sorted_artifacts = new ArrayList<File>();
        for (File f : artifacts.values()) {
            sorted_artifacts.add(f);
        }
        Collections.sort(sorted_artifacts);
        for (File f : sorted_artifacts) {
            // The last modified information is only used
            // to detect tampering with the output dir.
            // If the outputdir has been modified, not by javac,
            // then a mismatch will be detected in the last modified
            // timestamps stored in the build database compared
            // to the timestamps on disk and the artifact will be deleted.

            b.append("A "+f.getPath()+" "+f.lastModified()+"\n");
        }
    }

    /**
     * Always clean out a tainted package before it is recompiled.
     */
    public void deleteArtifacts() {
        for (File a : artifacts.values()) {
            a.delete();
        }
    }
}
