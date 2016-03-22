/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.tools.javac.code.ClassFinder;
import com.sun.tools.javac.code.Symbol.Completer;
import com.sun.tools.javac.code.Symbol.ModuleSymbol;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Abort;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;


/**
 *  This class could be the main entry point for Javadoc when Javadoc is used as a
 *  component in a larger software system. It provides operations to
 *  construct a new javadoc processor, and to run it on a set of source
 *  files.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 *  @author Neal Gafter
 */
public class JavadocTool extends com.sun.tools.javac.main.JavaCompiler {
    DocEnv docenv;

    final Messager messager;
    final ClassFinder javadocFinder;
    final Enter javadocEnter;
    final Set<JavaFileObject> uniquefiles;

    /**
     * Construct a new JavaCompiler processor, using appropriately
     * extended phases of the underlying compiler.
     */
    protected JavadocTool(Context context) {
        super(context);
        messager = Messager.instance0(context);
        javadocFinder = JavadocClassFinder.instance(context);
        javadocEnter = JavadocEnter.instance(context);
        uniquefiles = new HashSet<>();
    }

    /**
     * For javadoc, the parser needs to keep comments. Overrides method from JavaCompiler.
     */
    @Override
    protected boolean keepComments() {
        return true;
    }

    /**
     *  Construct a new javadoc tool.
     */
    public static JavadocTool make0(Context context) {
        // force the use of Javadoc's class finder
        JavadocClassFinder.preRegister(context);

        // force the use of Javadoc's own enter phase
        JavadocEnter.preRegister(context);

        // force the use of Javadoc's own member enter phase
        JavadocMemberEnter.preRegister(context);

        // force the use of Javadoc's own todo phase
        JavadocTodo.preRegister(context);

        // force the use of Messager as a Log
        Messager.instance0(context);

        return new JavadocTool(context);
    }

    public RootDocImpl getRootDocImpl(String doclocale,
                                      String encoding,
                                      ModifierFilter filter,
                                      List<String> args,
                                      List<String[]> options,
                                      Iterable<? extends JavaFileObject> fileObjects,
                                      boolean breakiterator,
                                      List<String> subPackages,
                                      List<String> excludedPackages,
                                      boolean docClasses,
                                      boolean legacyDoclet,
                      boolean quiet) throws IOException {
        docenv = DocEnv.instance(context);
        docenv.showAccess = filter;
        docenv.quiet = quiet;
        docenv.breakiterator = breakiterator;
        docenv.setLocale(doclocale);
        docenv.setEncoding(encoding);
        docenv.docClasses = docClasses;
        docenv.legacyDoclet = legacyDoclet;

        javadocFinder.sourceCompleter = docClasses ? Completer.NULL_COMPLETER : sourceCompleter;

        if (docClasses) {
            // If -Xclasses is set, the args should be a series of class names
            for (String arg: args) {
                if (!isValidPackageName(arg)) // checks
                    docenv.error(null, "main.illegal_class_name", arg);
            }
            if (messager.nerrors() != 0) {
                return null;
            }
            return new RootDocImpl(docenv, args, options);
        }

        ListBuffer<JCCompilationUnit> classTrees = new ListBuffer<>();
        Set<String> includedPackages = new LinkedHashSet<>();

        try {
            StandardJavaFileManager fm = docenv.fileManager instanceof StandardJavaFileManager
                    ? (StandardJavaFileManager) docenv.fileManager : null;
            Set<String> packageNames = new LinkedHashSet<>();
            // Normally, the args should be a series of package names or file names.
            // Parse the files and collect the package names.
            for (String arg: args) {
                if (fm != null && arg.endsWith(".java") && new File(arg).exists()) {
                    if (new File(arg).getName().equals("module-info.java")) {
                        docenv.warning(null, "main.file_ignored", arg);
                    } else {
                        parse(fm.getJavaFileObjects(arg), classTrees, true);
                    }
                } else if (isValidPackageName(arg)) {
                    packageNames.add(arg);
                } else if (arg.endsWith(".java")) {
                    if (fm == null)
                        throw new IllegalArgumentException();
                    else
                        docenv.error(null, "main.file_not_found", arg);
                } else {
                    docenv.error(null, "main.illegal_package_name", arg);
                }
            }

            // Parse file objects provide via the DocumentationTool API
            parse(fileObjects, classTrees, true);
            modules.enter(classTrees.toList(), null);

            syms.unnamedModule.complete(); // TEMP to force reading all named modules

            // Build up the complete list of any packages to be documented
            Location location =
                    modules.multiModuleMode && !modules.noModules ? StandardLocation.MODULE_SOURCE_PATH
                    : docenv.fileManager.hasLocation(StandardLocation.SOURCE_PATH) ? StandardLocation.SOURCE_PATH
                    : StandardLocation.CLASS_PATH;

            PackageTable t = new PackageTable(docenv.fileManager, location)
                    .packages(packageNames)
                    .subpackages(subPackages, excludedPackages);

            includedPackages = t.getIncludedPackages();

            // Parse the files in the packages to be documented
            ListBuffer<JCCompilationUnit> packageTrees = new ListBuffer<>();
            for (String packageName: includedPackages) {
                List<JavaFileObject> files = t.getFiles(packageName);
                docenv.notice("main.Loading_source_files_for_package", packageName);

                if (files.isEmpty())
                    messager.warning(Messager.NOPOS, "main.no_source_files_for_package", packageName);
                parse(files, packageTrees, false);
            }
            modules.enter(packageTrees.toList(), null);

            if (messager.nerrors() != 0) {
                return null;
            }

            // Enter symbols for all files
            docenv.notice("main.Building_tree");
            javadocEnter.main(classTrees.toList().appendList(packageTrees.toList()));
            enterDone = true;
        } catch (Abort ex) {}

        if (messager.nerrors() != 0)
            return null;

        return new RootDocImpl(docenv, listClasses(classTrees.toList()), List.from(includedPackages), options);
    }

    /** Is the given string a valid package name? */
    boolean isValidPackageName(String s) {
        int index;
        while ((index = s.indexOf('.')) != -1) {
            if (!isValidClassName(s.substring(0, index))) return false;
            s = s.substring(index+1);
        }
        return isValidClassName(s);
    }

    private void parse(Iterable<? extends JavaFileObject> files, ListBuffer<JCCompilationUnit> trees,
                       boolean trace) {
        for (JavaFileObject fo: files) {
            if (uniquefiles.add(fo)) { // ignore duplicates
                if (trace)
                    docenv.notice("main.Loading_source_file", fo.getName());
                trees.append(parse(fo));
            }
        }
    }

    /** Are surrogates supported?
     */
    final static boolean surrogatesSupported = surrogatesSupported();
    private static boolean surrogatesSupported() {
        try {
            boolean b = Character.isHighSurrogate('a');
            return true;
        } catch (NoSuchMethodError ex) {
            return false;
        }
    }

    /**
     * Return true if given file name is a valid class name
     * (including "package-info").
     * @param s the name of the class to check.
     * @return true if given class name is a valid class name
     * and false otherwise.
     */
    public static boolean isValidClassName(String s) {
        if (s.length() < 1) return false;
        if (s.equals("package-info")) return true;
        if (surrogatesSupported) {
            int cp = s.codePointAt(0);
            if (!Character.isJavaIdentifierStart(cp))
                return false;
            for (int j=Character.charCount(cp); j<s.length(); j+=Character.charCount(cp)) {
                cp = s.codePointAt(j);
                if (!Character.isJavaIdentifierPart(cp))
                    return false;
            }
        } else {
            if (!Character.isJavaIdentifierStart(s.charAt(0)))
                return false;
            for (int j=1; j<s.length(); j++)
                if (!Character.isJavaIdentifierPart(s.charAt(j)))
                    return false;
        }
        return true;
    }

    /**
     * From a list of top level trees, return the list of contained class definitions
     */
    List<JCClassDecl> listClasses(List<JCCompilationUnit> trees) {
        ListBuffer<JCClassDecl> result = new ListBuffer<>();
        for (JCCompilationUnit t : trees) {
            for (JCTree def : t.defs) {
                if (def.hasTag(JCTree.Tag.CLASSDEF))
                    result.append((JCClassDecl)def);
            }
        }
        return result.toList();
    }

    /**
     * A table to manage included and excluded packages.
     */
    class PackageTable {
        private final Map<String, Entry> entries = new LinkedHashMap<>();
        private final Set<String> includedPackages = new LinkedHashSet<>();
        private final JavaFileManager fm;
        private final Location location;
        private final Set<JavaFileObject.Kind> sourceKinds = EnumSet.of(JavaFileObject.Kind.SOURCE);

        /**
         * Creates a table to manage included and excluded packages.
         * @param fm The file manager used to locate source files
         * @param locn the location used to locate source files
         */
        PackageTable(JavaFileManager fm, Location locn) {
            this.fm = fm;
            this.location = locn;
            getEntry("").excluded = false;
        }

        PackageTable packages(Collection<String> packageNames) {
            includedPackages.addAll(packageNames);
            return this;
        }

        PackageTable subpackages(Collection<String> packageNames, Collection<String> excludePackageNames)
                throws IOException {
            for (String p: excludePackageNames) {
                getEntry(p).excluded = true;
            }

            for (String packageName: packageNames) {
                Location packageLocn = getLocation(packageName);
                for (JavaFileObject fo: fm.list(packageLocn, packageName, sourceKinds, true)) {
                    String binaryName = fm.inferBinaryName(packageLocn, fo);
                    String pn = getPackageName(binaryName);
                    String simpleName = getSimpleName(binaryName);
                    Entry e = getEntry(pn);
                    if (!e.isExcluded() && isValidClassName(simpleName)) {
                        includedPackages.add(pn);
                        e.files = (e.files == null ? List.of(fo) : e.files.prepend(fo));
                    }
                }
            }
            return this;
        }

        /**
         * Returns the aggregate set of included packages.
         * @return the aggregate set of included packages
         */
        Set<String> getIncludedPackages() {
            return includedPackages;
        }

        /**
         * Returns the set of source files for a package.
         * @param packageName the specified package
         * @return the set of file objects for the specified package
         * @throws IOException if an error occurs while accessing the files
         */
        List<JavaFileObject> getFiles(String packageName) throws IOException {
            Entry e = getEntry(packageName);
            // The files may have been found as a side effect of searching for subpackages
            if (e.files != null)
                return e.files;

            ListBuffer<JavaFileObject> lb = new ListBuffer<>();
            Location packageLocn = getLocation(packageName);
            for (JavaFileObject fo: fm.list(packageLocn, packageName, sourceKinds, false)) {
                String binaryName = fm.inferBinaryName(packageLocn, fo);
                String simpleName = getSimpleName(binaryName);
                if (isValidClassName(simpleName)) {
                    lb.append(fo);
                }
            }

            return lb.toList();
        }

        private Location getLocation(String packageName) throws IOException {
            if (location == StandardLocation.MODULE_SOURCE_PATH) {
                // TODO: handle invalid results
                ModuleSymbol msym = syms.inferModule(names.fromString(packageName));
                return fm.getModuleLocation(location, msym.name.toString());
            } else {
                return location;
            }
        }

        private Entry getEntry(String name) {
            Entry e = entries.get(name);
            if (e == null)
                entries.put(name, e = new Entry(name));
            return e;
        }

        private String getPackageName(String name) {
            int lastDot = name.lastIndexOf(".");
            return (lastDot == -1 ? "" : name.substring(0, lastDot));
        }

        private String getSimpleName(String name) {
            int lastDot = name.lastIndexOf(".");
            return (lastDot == -1 ? name : name.substring(lastDot + 1));
        }

        class Entry {
            final String name;
            Boolean excluded;
            List<JavaFileObject> files;

            Entry(String name) {
                this.name = name;
            }

            boolean isExcluded() {
                if (excluded == null)
                    excluded = getEntry(getPackageName(name)).isExcluded();
                return excluded;
            }
        }
    }

}
