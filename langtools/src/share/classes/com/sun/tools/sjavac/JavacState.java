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

import java.io.*;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.net.URI;
import java.util.*;

/**
 * The javac state class maintains the previous (prev) and the current (now)
 * build states and everything else that goes into the javac_state file.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class JavacState
{
    // The arguments to the compile. If not identical, then it cannot
    // be an incremental build!
    String theArgs;
    // The number of cores limits how many threads are used for heavy concurrent work.
    int numCores;

    // The bin_dir/javac_state
    private String javacStateFilename;
    private File javacState;

    // The previous build state is loaded from javac_state
    private BuildState prev;
    // The current build state is constructed during the build,
    // then saved as the new javac_state.
    private BuildState now;

    // Something has changed in the javac_state. It needs to be saved!
    private boolean needsSaving;
    // If this is a new javac_state file, then do not print unnecessary messages.
    private boolean newJavacState;

    // These are packages where something has changed and the package
    // needs to be recompiled. Actions that trigger recompilation:
    // * source belonging to the package has changed
    // * artifact belonging to the package is lost, or its timestamp has been changed.
    // * an unknown artifact has appeared, we simply delete it, but we also trigger a recompilation.
    // * a package that is tainted, taints all packages that depend on it.
    private Set<String> taintedPackages;
    // After a compile, the pubapis are compared with the pubapis stored in the javac state file.
    // Any packages where the pubapi differ are added to this set.
    // Later we use this set and the dependency information to taint dependent packages.
    private Set<String> packagesWithChangedPublicApis;
    // When a module-info.java file is changed, taint the module,
    // then taint all modules that depend on that that module.
    // A module dependency can occur directly through a require, or
    // indirectly through a module that does a public export for the first tainted module.
    // When all modules are tainted, then taint all packages belonging to these modules.
    // Then rebuild. It is perhaps possible (and valuable?) to do a more finegrained examination of the
    // change in module-info.java, but that will have to wait.
    private Set<String> taintedModules;
    // The set of all packages that has been recompiled.
    // Copy over the javac_state for the packages that did not need recompilation,
    // verbatim from the previous (prev) to the new (now) build state.
    private Set<String> recompiledPackages;

    // The output directories filled with tasty artifacts.
    private File binDir, gensrcDir, headerDir;

    // The current status of the file system.
    private Set<File> binArtifacts;
    private Set<File> gensrcArtifacts;
    private Set<File> headerArtifacts;

    // The status of the sources.
    Set<Source> removedSources = null;
    Set<Source> addedSources = null;
    Set<Source> modifiedSources = null;

    // Visible sources for linking. These are the only
    // ones that -sourcepath is allowed to see.
    Set<URI> visibleSrcs;

    // Visible classes for linking. These are the only
    // ones that -classpath is allowed to see.
    // It maps from a classpath root to the set of visible classes for that root.
    // If the set is empty, then all classes are visible for that root.
    // It can also map from a jar file to the set of visible classes for that jar file.
    Map<URI,Set<String>> visibleClasses;

    // Setup two transforms that always exist.
    private CopyFile            copyFiles = new CopyFile();
    private CompileJavaPackages compileJavaPackages = new CompileJavaPackages();

    // Where to send stdout and stderr.
    private PrintStream out, err;

    JavacState(String[] args, File bd, File gd, File hd, boolean permitUnidentifiedArtifacts, boolean removeJavacState,
            PrintStream o, PrintStream e) {
        out = o;
        err = e;
        numCores = Main.findNumberOption(args, "-j");
        theArgs = "";
        for (String a : removeArgsNotAffectingState(args)) {
            theArgs = theArgs+a+" ";
        }
        binDir = bd;
        gensrcDir = gd;
        headerDir = hd;
        javacStateFilename = binDir.getPath()+File.separator+"javac_state";
        javacState = new File(javacStateFilename);
        if (removeJavacState && javacState.exists()) {
            javacState.delete();
        }
        newJavacState = false;
        if (!javacState.exists()) {
            newJavacState = true;
            // If there is no javac_state then delete the contents of all the artifact dirs!
            // We do not want to risk building a broken incremental build.
            // BUT since the makefiles still copy things straight into the bin_dir et al,
            // we avoid deleting files here, if the option --permit-unidentified-classes was supplied.
            if (!permitUnidentifiedArtifacts) {
                deleteContents(binDir);
                deleteContents(gensrcDir);
                deleteContents(headerDir);
            }
            needsSaving = true;
        }
        prev = new BuildState();
        now = new BuildState();
        taintedPackages = new HashSet<String>();
        recompiledPackages = new HashSet<String>();
        packagesWithChangedPublicApis = new HashSet<String>();
    }

    public BuildState prev() { return prev; }
    public BuildState now() { return now; }

    /**
     * Remove args not affecting the state.
     */
    static String[] removeArgsNotAffectingState(String[] args) {
        String[] out = new String[args.length];
        int j = 0;
        for (int i = 0; i<args.length; ++i) {
            if (args[i].equals("-j")) {
                // Just skip it and skip following value
                i++;
            } else if (args[i].startsWith("--server:")) {
                // Just skip it.
            } else if (args[i].startsWith("--log=")) {
                // Just skip it.
            } else if (args[i].equals("--compare-found-sources")) {
                // Just skip it and skip verify file name
                i++;
            } else {
                // Copy argument.
                out[j] = args[i];
                j++;
            }
        }
        String[] ret = new String[j];
        System.arraycopy(out, 0, ret, 0, j);
        return ret;
    }

    /**
     * Specify which sources are visible to the compiler through -sourcepath.
     */
    public void setVisibleSources(Map<String,Source> vs) {
        visibleSrcs = new HashSet<URI>();
        for (String s : vs.keySet()) {
            Source src = vs.get(s);
            visibleSrcs.add(src.file().toURI());
        }
    }

    /**
     * Specify which classes are visible to the compiler through -classpath.
     */
    public void setVisibleClasses(Map<String,Source> vs) {
        visibleSrcs = new HashSet<URI>();
        for (String s : vs.keySet()) {
            Source src = vs.get(s);
            visibleSrcs.add(src.file().toURI());
        }
    }
    /**
     * Returns true if this is an incremental build.
     */
    public boolean isIncremental() {
        return !prev.sources().isEmpty();
    }

    /**
     * Find all artifacts that exists on disk.
     */
    public void findAllArtifacts() {
        binArtifacts = findAllFiles(binDir);
        gensrcArtifacts = findAllFiles(gensrcDir);
        headerArtifacts = findAllFiles(headerDir);
    }

    /**
     * Lookup the artifacts generated for this package in the previous build.
     */
    private Map<String,File> fetchPrevArtifacts(String pkg) {
        Package p = prev.packages().get(pkg);
        if (p != null) {
            return p.artifacts();
        }
        return new HashMap<String,File>();
    }

    /**
     * Delete all prev artifacts in the currently tainted packages.
     */
    public void deleteClassArtifactsInTaintedPackages() {
        for (String pkg : taintedPackages) {
            Map<String,File> arts = fetchPrevArtifacts(pkg);
            for (File f : arts.values()) {
                if (f.exists() && f.getName().endsWith(".class")) {
                    f.delete();
                }
            }
        }
    }

    /**
     * Mark the javac_state file to be in need of saving and as a side effect,
     * it gets a new timestamp.
     */
    private void needsSaving() {
        needsSaving = true;
    }

    /**
     * Save the javac_state file.
     */
    public void save() throws IOException {
        if (!needsSaving) return;
        try (FileWriter out = new FileWriter(javacStateFilename)) {
            StringBuilder b = new StringBuilder();
            long millisNow = System.currentTimeMillis();
            Date d = new Date(millisNow);
            SimpleDateFormat df =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS");
            b.append("# javac_state ver 0.3 generated "+millisNow+" "+df.format(d)+"\n");
            b.append("# This format might change at any time. Please do not depend on it.\n");
            b.append("# M module\n");
            b.append("# P package\n");
            b.append("# S C source_tobe_compiled timestamp\n");
            b.append("# S L link_only_source timestamp\n");
            b.append("# G C generated_source timestamp\n");
            b.append("# A artifact timestamp\n");
            b.append("# D dependency\n");
            b.append("# I pubapi\n");
            b.append("# R arguments\n");
            b.append("R ").append(theArgs).append("\n");

            // Copy over the javac_state for the packages that did not need recompilation.
            now.copyPackagesExcept(prev, recompiledPackages, new HashSet<String>());
            // Save the packages, ie package names, dependencies, pubapis and artifacts!
            // I.e. the lot.
            Module.saveModules(now.modules(), b);

            String s = b.toString();
            out.write(s, 0, s.length());
        }
    }

    /**
     * Load a javac_state file.
     */
    public static JavacState load(String[] args, File binDir, File gensrcDir, File headerDir,
            boolean permitUnidentifiedArtifacts, PrintStream out, PrintStream err) {
        JavacState db = new JavacState(args, binDir, gensrcDir, headerDir, permitUnidentifiedArtifacts, false, out, err);
        Module  lastModule = null;
        Package lastPackage = null;
        Source  lastSource = null;
        boolean noFileFound = false;
        boolean foundCorrectVerNr = false;
        boolean newCommandLine = false;
        boolean syntaxError = false;

        try (BufferedReader in = new BufferedReader(new FileReader(db.javacStateFilename))) {
            for (;;) {
                String l = in.readLine();
                if (l==null) break;
                if (l.length()>=3 && l.charAt(1) == ' ') {
                    char c = l.charAt(0);
                    if (c == 'M') {
                        lastModule = db.prev.loadModule(l);
                    } else
                    if (c == 'P') {
                        if (lastModule == null) { syntaxError = true; break; }
                        lastPackage = db.prev.loadPackage(lastModule, l);
                    } else
                    if (c == 'D') {
                        if (lastModule == null || lastPackage == null) { syntaxError = true; break; }
                        lastPackage.loadDependency(l);
                    } else
                    if (c == 'I') {
                        if (lastModule == null || lastPackage == null) { syntaxError = true; break; }
                        lastPackage.loadPubapi(l);
                    } else
                    if (c == 'A') {
                        if (lastModule == null || lastPackage == null) { syntaxError = true; break; }
                        lastPackage.loadArtifact(l);
                    } else
                    if (c == 'S') {
                        if (lastModule == null || lastPackage == null) { syntaxError = true; break; }
                        lastSource = db.prev.loadSource(lastPackage, l, false);
                    } else
                    if (c == 'G') {
                        if (lastModule == null || lastPackage == null) { syntaxError = true; break; }
                        lastSource = db.prev.loadSource(lastPackage, l, true);
                    } else
                    if (c == 'R') {
                        String ncmdl = "R "+db.theArgs;
                        if (!l.equals(ncmdl)) {
                            newCommandLine = true;
                        }
                    } else
                         if (c == '#') {
                        if (l.startsWith("# javac_state ver ")) {
                            int sp = l.indexOf(" ", 18);
                            if (sp != -1) {
                                String ver = l.substring(18,sp);
                                if (!ver.equals("0.3")) {
                    break;
                                 }
                foundCorrectVerNr = true;
                            }
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // Silently create a new javac_state file.
            noFileFound = true;
        } catch (IOException e) {
            Log.info("Dropping old javac_state because of errors when reading it.");
            db = new JavacState(args, binDir, gensrcDir, headerDir, permitUnidentifiedArtifacts, true, out, err);
            foundCorrectVerNr = true;
            newCommandLine = false;
            syntaxError = false;
    }
        if (foundCorrectVerNr == false && !noFileFound) {
            Log.info("Dropping old javac_state since it is of an old version.");
            db = new JavacState(args, binDir, gensrcDir, headerDir, permitUnidentifiedArtifacts, true, out, err);
        } else
        if (newCommandLine == true && !noFileFound) {
            Log.info("Dropping old javac_state since a new command line is used!");
            db = new JavacState(args, binDir, gensrcDir, headerDir, permitUnidentifiedArtifacts, true, out, err);
        } else
        if (syntaxError == true) {
            Log.info("Dropping old javac_state since it contains syntax errors.");
            db = new JavacState(args, binDir, gensrcDir, headerDir, permitUnidentifiedArtifacts, true, out, err);
        }
        db.prev.calculateDependents();
        return db;
    }

    /**
     * Mark a java package as tainted, ie it needs recompilation.
     */
    public void taintPackage(String name, String because) {
        if (!taintedPackages.contains(name)) {
            if (because != null) Log.debug("Tainting "+Util.justPackageName(name)+" because "+because);
            // It has not been tainted before.
            taintedPackages.add(name);
            needsSaving();
            Package nowp = now.packages().get(name);
            if (nowp != null) {
                for (String d : nowp.dependents()) {
                    taintPackage(d, because);
                }
            }
        }
    }

    /**
     * This packages need recompilation.
     */
    public Set<String> taintedPackages() {
        return taintedPackages;
    }

    /**
     * Clean out the tainted package set, used after the first round of compiles,
     * prior to propagating dependencies.
     */
    public void clearTaintedPackages() {
        taintedPackages = new HashSet<String>();
    }

    /**
     * Go through all sources and check which have been removed, added or modified
     * and taint the corresponding packages.
     */
    public void checkSourceStatus(boolean check_gensrc) {
        removedSources = calculateRemovedSources();
        for (Source s : removedSources) {
            if (!s.isGenerated() || check_gensrc) {
                taintPackage(s.pkg().name(), "source "+s.name()+" was removed");
            }
        }

        addedSources = calculateAddedSources();
        for (Source s : addedSources) {
            String msg = null;
            if (isIncremental()) {
                // When building from scratch, there is no point
                // printing "was added" for every file since all files are added.
                // However for an incremental build it makes sense.
                msg = "source "+s.name()+" was added";
            }
            if (!s.isGenerated() || check_gensrc) {
                taintPackage(s.pkg().name(), msg);
            }
        }

        modifiedSources = calculateModifiedSources();
        for (Source s : modifiedSources) {
            if (!s.isGenerated() || check_gensrc) {
                taintPackage(s.pkg().name(), "source "+s.name()+" was modified");
            }
        }
    }

    /**
     * Acquire the compile_java_packages suffix rule for .java files.
     */
    public Map<String,Transformer> getJavaSuffixRule() {
        Map<String,Transformer> sr = new HashMap<String,Transformer>();
        sr.put(".java", compileJavaPackages);
        return sr;
    }

    /**
     * Acquire the copying transform.
     */
    public Transformer getCopier() {
        return copyFiles;
    }

    /**
     * If artifacts have gone missing, force a recompile of the packages
     * they belong to.
     */
    public void taintPackagesThatMissArtifacts() {
        for (Package pkg : prev.packages().values()) {
            for (File f : pkg.artifacts().values()) {
                if (!f.exists()) {
                    // Hmm, the artifact on disk does not exist! Someone has removed it....
                    // Lets rebuild the package.
                    taintPackage(pkg.name(), ""+f+" is missing.");
                }
            }
        }
    }

    /**
     * Propagate recompilation through the dependency chains.
     * Avoid re-tainting packages that have already been compiled.
     */
    public void taintPackagesDependingOnChangedPackages(Set<String> pkgs, Set<String> recentlyCompiled) {
        for (Package pkg : prev.packages().values()) {
            for (String dep : pkg.dependencies()) {
                if (pkgs.contains(dep) && !recentlyCompiled.contains(pkg.name())) {
                    taintPackage(pkg.name(), " its depending on "+dep);
                }
            }
        }
    }

    /**
     * Scan all output dirs for artifacts and remove those files (artifacts?)
     * that are not recognized as such, in the javac_state file.
     */
    public void removeUnidentifiedArtifacts() {
        Set<File> allKnownArtifacts = new HashSet<File>();
        for (Package pkg : prev.packages().values()) {
            for (File f : pkg.artifacts().values()) {
                allKnownArtifacts.add(f);
            }
        }
        // Do not forget about javac_state....
        allKnownArtifacts.add(javacState);

        for (File f : binArtifacts) {
            if (!allKnownArtifacts.contains(f)) {
                Log.debug("Removing "+f.getPath()+" since it is unknown to the javac_state.");
                f.delete();
            }
        }
        for (File f : headerArtifacts) {
            if (!allKnownArtifacts.contains(f)) {
                Log.debug("Removing "+f.getPath()+" since it is unknown to the javac_state.");
                f.delete();
            }
        }
        for (File f : gensrcArtifacts) {
            if (!allKnownArtifacts.contains(f)) {
                Log.debug("Removing "+f.getPath()+" since it is unknown to the javac_state.");
                f.delete();
            }
        }
    }

    /**
     * Remove artifacts that are no longer produced when compiling!
     */
    public void removeSuperfluousArtifacts(Set<String> recentlyCompiled) {
        // Nothing to do, if nothing was recompiled.
        if (recentlyCompiled.size() == 0) return;

        for (String pkg : now.packages().keySet()) {
            // If this package has not been recompiled, skip the check.
            if (!recentlyCompiled.contains(pkg)) continue;
            Collection<File> arts = now.artifacts().values();
            for (File f : fetchPrevArtifacts(pkg).values()) {
                if (!arts.contains(f)) {
                    Log.debug("Removing "+f.getPath()+" since it is now superfluous!");
                    if (f.exists()) f.delete();
                }
            }
        }
    }

    /**
     * Return those files belonging to prev, but not now.
     */
    private Set<Source> calculateRemovedSources() {
        Set<Source> removed = new HashSet<Source>();
        for (String src : prev.sources().keySet()) {
            if (now.sources().get(src) == null) {
                removed.add(prev.sources().get(src));
            }
        }
        return removed;
    }

    /**
     * Return those files belonging to now, but not prev.
     */
    private Set<Source> calculateAddedSources() {
        Set<Source> added = new HashSet<Source>();
        for (String src : now.sources().keySet()) {
            if (prev.sources().get(src) == null) {
                added.add(now.sources().get(src));
            }
        }
        return added;
    }

    /**
     * Return those files where the timestamp is newer.
     * If a source file timestamp suddenly is older than what is known
     * about it in javac_state, then consider it modified, but print
     * a warning!
     */
    private Set<Source> calculateModifiedSources() {
        Set<Source> modified = new HashSet<Source>();
        for (String src : now.sources().keySet()) {
            Source n = now.sources().get(src);
            Source t = prev.sources().get(src);
            if (prev.sources().get(src) != null) {
                if (t != null) {
                    if (n.lastModified() > t.lastModified()) {
                        modified.add(n);
                    } else if (n.lastModified() < t.lastModified()) {
                        modified.add(n);
                        Log.warn("The source file "+n.name()+" timestamp has moved backwards in time.");
                    }
                }
            }
        }
        return modified;
    }

    /**
     * Recursively delete a directory and all its contents.
     */
    private static void deleteContents(File dir) {
        if (dir != null && dir.exists()) {
            for (File f : dir.listFiles()) {
                if (f.isDirectory()) {
                    deleteContents(f);
                }
                f.delete();
            }
        }
    }

    /**
     * Run the copy translator only.
     */
    public void performCopying(File binDir, Map<String,Transformer> suffixRules) {
        Map<String,Transformer> sr = new HashMap<String,Transformer>();
        for (Map.Entry<String,Transformer> e : suffixRules.entrySet()) {
            if (e.getValue() == copyFiles) {
                sr.put(e.getKey(), e.getValue());
            }
        }
        perform(binDir, sr);
    }

    /**
     * Run all the translators that translate into java source code.
     * I.e. all translators that are not copy nor compile_java_source.
     */
    public void performTranslation(File gensrcDir, Map<String,Transformer> suffixRules) {
        Map<String,Transformer> sr = new HashMap<String,Transformer>();
        for (Map.Entry<String,Transformer> e : suffixRules.entrySet()) {
            if (e.getValue() != copyFiles &&
                e.getValue() != compileJavaPackages) {
                sr.put(e.getKey(), e.getValue());
            }
        }
        perform(gensrcDir, sr);
    }

    /**
     * Compile all the java sources. Return true, if it needs to be called again!
     */
    public boolean performJavaCompilations(File binDir,
                                           String serverSettings,
                                           String[] args,
                                           Set<String> recentlyCompiled,
                                           boolean[] rcValue) {
        Map<String,Transformer> suffixRules = new HashMap<String,Transformer>();
        suffixRules.put(".java", compileJavaPackages);
        compileJavaPackages.setExtra(serverSettings);
        compileJavaPackages.setExtra(args);

        rcValue[0] = perform(binDir, suffixRules);
        recentlyCompiled.addAll(taintedPackages());
        clearTaintedPackages();
        boolean again = !packagesWithChangedPublicApis.isEmpty();
        taintPackagesDependingOnChangedPackages(packagesWithChangedPublicApis, recentlyCompiled);
        packagesWithChangedPublicApis = new HashSet<String>();
        return again && rcValue[0];
    }

    /**
     * Store the source into the set of sources belonging to the given transform.
     */
    private void addFileToTransform(Map<Transformer,Map<String,Set<URI>>> gs, Transformer t, Source s) {
        Map<String,Set<URI>> fs = gs.get(t);
        if (fs == null) {
            fs = new HashMap<String,Set<URI>>();
            gs.put(t, fs);
        }
        Set<URI> ss = fs.get(s.pkg().name());
        if (ss == null) {
            ss = new HashSet<URI>();
            fs.put(s.pkg().name(), ss);
        }
        ss.add(s.file().toURI());
    }

    /**
     * For all packages, find all sources belonging to the package, group the sources
     * based on their transformers and apply the transformers on each source code group.
     */
    private boolean perform(File outputDir, Map<String,Transformer> suffixRules)
    {
        boolean rc = true;
        // Group sources based on transforms. A source file can only belong to a single transform.
        Map<Transformer,Map<String,Set<URI>>> groupedSources = new HashMap<Transformer,Map<String,Set<URI>>>();
        for (Source src : now.sources().values()) {
            Transformer t = suffixRules.get(src.suffix());
               if (t != null) {
                if (taintedPackages.contains(src.pkg().name()) && !src.isLinkedOnly()) {
                    addFileToTransform(groupedSources, t, src);
                }
            }
        }
        // Go through the transforms and transform them.
        for (Map.Entry<Transformer,Map<String,Set<URI>>> e : groupedSources.entrySet()) {
            Transformer t = e.getKey();
            Map<String,Set<URI>> srcs = e.getValue();
            // These maps need to be synchronized since multiple threads will be writing results into them.
            Map<String,Set<URI>> packageArtifacts = Collections.synchronizedMap(new HashMap<String,Set<URI>>());
            Map<String,Set<String>> packageDependencies = Collections.synchronizedMap(new HashMap<String,Set<String>>());
            Map<String,String> packagePublicApis = Collections.synchronizedMap(new HashMap<String,String>());

            boolean  r = t.transform(srcs,
                                     visibleSrcs,
                                     visibleClasses,
                                     prev.dependents(),
                                     outputDir.toURI(),
                                     packageArtifacts,
                                     packageDependencies,
                                     packagePublicApis,
                                     0,
                                     isIncremental(),
                                     numCores,
                                     out,
                                     err);
            if (!r) rc = false;

            for (String p : srcs.keySet()) {
                recompiledPackages.add(p);
            }
            // The transform is done! Extract all the artifacts and store the info into the Package objects.
            for (Map.Entry<String,Set<URI>> a : packageArtifacts.entrySet()) {
                Module mnow = now.findModuleFromPackageName(a.getKey());
                mnow.addArtifacts(a.getKey(), a.getValue());
            }
            // Extract all the dependencies and store the info into the Package objects.
            for (Map.Entry<String,Set<String>> a : packageDependencies.entrySet()) {
                Set<String> deps = a.getValue();
                Module mnow = now.findModuleFromPackageName(a.getKey());
                mnow.setDependencies(a.getKey(), deps);
            }
            // Extract all the pubapis and store the info into the Package objects.
            for (Map.Entry<String,String> a : packagePublicApis.entrySet()) {
                Module mprev = prev.findModuleFromPackageName(a.getKey());
                List<String> pubapi = Package.pubapiToList(a.getValue());
                Module mnow = now.findModuleFromPackageName(a.getKey());
                mnow.setPubapi(a.getKey(), pubapi);
                if (mprev.hasPubapiChanged(a.getKey(), pubapi)) {
                    // Aha! The pubapi of this package has changed!
                    // It can also be a new compile from scratch.
                    if (mprev.lookupPackage(a.getKey()).existsInJavacState()) {
                        // This is an incremental compile! The pubapi
                        // did change. Trigger recompilation of dependents.
                        packagesWithChangedPublicApis.add(a.getKey());
                        Log.info("The pubapi of "+Util.justPackageName(a.getKey())+" has changed!");
                    }
                }
            }
        }
        return rc;
    }

    /**
     * Utility method to recursively find all files below a directory.
     */
    private static Set<File> findAllFiles(File dir) {
        Set<File> foundFiles = new HashSet<File>();
        if (dir == null) {
            return foundFiles;
        }
        recurse(dir, foundFiles);
        return foundFiles;
    }

    private static void recurse(File dir, Set<File> foundFiles) {
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                foundFiles.add(f);
            } else if (f.isDirectory()) {
                recurse(f, foundFiles);
            }
        }
    }

    /**
     * Compare the calculate source list, with an explicit list, usually supplied from the makefile.
     * Used to detect bugs where the makefile and sjavac have different opinions on which files
     * should be compiled.
     */
    public void compareWithMakefileList(File makefileSourceList)
            throws ProblemException
    {
        // If we are building on win32 using for example cygwin the paths in the makefile source list
        // might be /cygdrive/c/.... which does not match c:\....
        // We need to adjust our calculated sources to be identical, if necessary.
        boolean mightNeedRewriting = File.pathSeparatorChar == ';';

        if (makefileSourceList == null) return;

        Set<String> calculatedSources = new HashSet<String>();
        Set<String> listedSources = new HashSet<String>();

        // Create a set of filenames with full paths.
        for (Source s : now.sources().values()) {
            calculatedSources.add(s.file().getPath());
        }
        // Read in the file and create another set of filenames with full paths.
        try {
            BufferedReader in = new BufferedReader(new FileReader(makefileSourceList));
            for (;;) {
                String l = in.readLine();
                if (l==null) break;
                l = l.trim();
                if (mightNeedRewriting) {
                    if (l.indexOf(":") == 1 && l.indexOf("\\") == 2) {
                        // Everything a-ok, the format is already C:\foo\bar
                    } else if (l.indexOf(":") == 1 && l.indexOf("/") == 2) {
                        // The format is C:/foo/bar, rewrite into the above format.
                        l = l.replaceAll("/","\\\\");
                    } else if (l.charAt(0) == '/' && l.indexOf("/",1) != -1) {
                        // The format might be: /cygdrive/c/foo/bar, rewrite into the above format.
                        // Do not hardcode the name cygdrive here.
                        int slash = l.indexOf("/",1);
                        l = l.replaceAll("/","\\\\");
                        l = ""+l.charAt(slash+1)+":"+l.substring(slash+2);
                    }
                    if (Character.isLowerCase(l.charAt(0))) {
                        l = Character.toUpperCase(l.charAt(0))+l.substring(1);
                    }
                }
                listedSources.add(l);
            }
        } catch (FileNotFoundException e) {
            throw new ProblemException("Could not open "+makefileSourceList.getPath()+" since it does not exist!");
        } catch (IOException e) {
            throw new ProblemException("Could not read "+makefileSourceList.getPath());
        }

        for (String s : listedSources) {
            if (!calculatedSources.contains(s)) {
                 throw new ProblemException("The makefile listed source "+s+" was not calculated by the smart javac wrapper!");
            }
        }

        for (String s : calculatedSources) {
            if (!listedSources.contains(s)) {
                throw new ProblemException("The smart javac wrapper calculated source "+s+" was not listed by the makefiles!");
            }
        }
    }
}
