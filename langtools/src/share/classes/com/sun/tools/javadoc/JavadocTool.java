/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javadoc;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.parser.DocCommentScanner;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Abort;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Position;


/**
 *  This class could be the main entry point for Javadoc when Javadoc is used as a
 *  component in a larger software system. It provides operations to
 *  construct a new javadoc processor, and to run it on a set of source
 *  files.
 *  @author Neal Gafter
 */
public class JavadocTool extends com.sun.tools.javac.main.JavaCompiler {
    DocEnv docenv;

    final Context context;
    final Messager messager;
    final JavadocClassReader reader;
    final JavadocEnter enter;
    final Annotate annotate;

    /**
     * Construct a new JavaCompiler processor, using appropriately
     * extended phases of the underlying compiler.
     */
    protected JavadocTool(Context context) {
        super(context);
        this.context = context;
        messager = Messager.instance0(context);
        reader = JavadocClassReader.instance0(context);
        enter = JavadocEnter.instance0(context);
        annotate = Annotate.instance(context);
    }

    /**
     * For javadoc, the parser needs to keep comments. Overrides method from JavaCompiler.
     */
    protected boolean keepComments() {
        return true;
    }

    /**
     *  Construct a new javadoc tool.
     */
    public static JavadocTool make0(Context context) {
        Messager messager = null;
        try {
            // force the use of Javadoc's class reader
            JavadocClassReader.preRegister(context);

            // force the use of Javadoc's own enter phase
            JavadocEnter.preRegister(context);

            // force the use of Javadoc's own member enter phase
            JavadocMemberEnter.preRegister(context);

            // force the use of Javadoc's own todo phase
            JavadocTodo.preRegister(context);

            // force the use of Messager as a Log
            messager = Messager.instance0(context);

            // force the use of the scanner that captures Javadoc comments
            DocCommentScanner.Factory.preRegister(context);

            return new JavadocTool(context);
        } catch (CompletionFailure ex) {
            messager.error(Position.NOPOS, ex.getMessage());
            return null;
        }
    }

    public RootDocImpl getRootDocImpl(String doclocale,
                                      String encoding,
                                      ModifierFilter filter,
                                      List<String> javaNames,
                                      List<String[]> options,
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
        reader.sourceCompleter = docClasses ? null : this;

        ListBuffer<String> names = new ListBuffer<String>();
        ListBuffer<JCCompilationUnit> classTrees = new ListBuffer<JCCompilationUnit>();
        ListBuffer<JCCompilationUnit> packTrees = new ListBuffer<JCCompilationUnit>();

        try {
            StandardJavaFileManager fm = (StandardJavaFileManager) docenv.fileManager;
            for (List<String> it = javaNames; it.nonEmpty(); it = it.tail) {
                String name = it.head;
                if (!docClasses && name.endsWith(".java") && new File(name).exists()) {
                    JavaFileObject fo = fm.getJavaFileObjects(name).iterator().next();
                    docenv.notice("main.Loading_source_file", name);
                    JCCompilationUnit tree = parse(fo);
                    classTrees.append(tree);
                } else if (isValidPackageName(name)) {
                    names = names.append(name);
                } else if (name.endsWith(".java")) {
                    docenv.error(null, "main.file_not_found", name);
                } else {
                    docenv.error(null, "main.illegal_package_name", name);
                }
            }

            if (!docClasses) {
                // Recursively search given subpackages.  If any packages
                //are found, add them to the list.
                Map<String,List<JavaFileObject>> packageFiles =
                        searchSubPackages(subPackages, names, excludedPackages);

                // Parse the packages
                for (List<String> packs = names.toList(); packs.nonEmpty(); packs = packs.tail) {
                    // Parse sources ostensibly belonging to package.
                    String packageName = packs.head;
                    parsePackageClasses(packageName, packageFiles.get(packageName), packTrees, excludedPackages);
                }

                if (messager.nerrors() != 0) return null;

                // Enter symbols for all files
                docenv.notice("main.Building_tree");
                enter.main(classTrees.toList().appendList(packTrees.toList()));
            }
        } catch (Abort ex) {}

        if (messager.nerrors() != 0)
            return null;

        if (docClasses)
            return new RootDocImpl(docenv, javaNames, options);
        else
            return new RootDocImpl(docenv, listClasses(classTrees.toList()), names.toList(), options);
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

    /**
     * search all directories in path for subdirectory name. Add all
     * .java files found in such a directory to args.
     */
    private void parsePackageClasses(String name,
            Iterable<JavaFileObject> files,
            ListBuffer<JCCompilationUnit> trees,
            List<String> excludedPackages)
            throws IOException {
        if (excludedPackages.contains(name)) {
            return;
        }

        boolean hasFiles = false;
        docenv.notice("main.Loading_source_files_for_package", name);

        if (files == null) {
            Location location = docenv.fileManager.hasLocation(StandardLocation.SOURCE_PATH)
                    ? StandardLocation.SOURCE_PATH : StandardLocation.CLASS_PATH;
            ListBuffer<JavaFileObject> lb = new ListBuffer<JavaFileObject>();
            for (JavaFileObject fo: docenv.fileManager.list(
                    location, name, EnumSet.of(JavaFileObject.Kind.SOURCE), false)) {
                String binaryName = docenv.fileManager.inferBinaryName(location, fo);
                String simpleName = getSimpleName(binaryName);
                if (isValidClassName(simpleName)) {
                    lb.append(fo);
                }
            }
            files = lb.toList();
        }

        for (JavaFileObject fo : files) {
            // messager.notice("main.Loading_source_file", fn);
            trees.append(parse(fo));
            hasFiles = true;
        }

        if (!hasFiles) {
            messager.warning(null, "main.no_source_files_for_package",
                    name.replace(File.separatorChar, '.'));
        }
    }

    /**
     * Recursively search all directories in path for subdirectory name.
     * Add all packages found in such a directory to packages list.
     */
    private Map<String,List<JavaFileObject>> searchSubPackages(
            List<String> subPackages,
            ListBuffer<String> packages,
            List<String> excludedPackages)
            throws IOException {
        Map<String,List<JavaFileObject>> packageFiles =
                new HashMap<String,List<JavaFileObject>>();

        Map<String,Boolean> includedPackages = new HashMap<String,Boolean>();
        includedPackages.put("", true);
        for (String p: excludedPackages)
            includedPackages.put(p, false);

        if (docenv.fileManager.hasLocation(StandardLocation.SOURCE_PATH)) {
            searchSubPackages(subPackages,
                    includedPackages,
                    packages, packageFiles,
                    StandardLocation.SOURCE_PATH,
                    EnumSet.of(JavaFileObject.Kind.SOURCE));
            searchSubPackages(subPackages,
                    includedPackages,
                    packages, packageFiles,
                    StandardLocation.CLASS_PATH,
                    EnumSet.of(JavaFileObject.Kind.CLASS));
        } else {
            searchSubPackages(subPackages,
                    includedPackages,
                    packages, packageFiles,
                    StandardLocation.CLASS_PATH,
                    EnumSet.of(JavaFileObject.Kind.SOURCE, JavaFileObject.Kind.CLASS));
        }
        return packageFiles;
    }

    private void searchSubPackages(List<String> subPackages,
            Map<String,Boolean> includedPackages,
            ListBuffer<String> packages,
            Map<String, List<JavaFileObject>> packageFiles,
            StandardLocation location, Set<JavaFileObject.Kind> kinds)
            throws IOException {
        for (String subPackage: subPackages) {
            if (!isIncluded(subPackage, includedPackages))
                continue;

            for (JavaFileObject fo: docenv.fileManager.list(location, subPackage, kinds, true)) {
                String binaryName = docenv.fileManager.inferBinaryName(location, fo);
                String packageName = getPackageName(binaryName);
                String simpleName = getSimpleName(binaryName);
                if (isIncluded(packageName, includedPackages) && isValidClassName(simpleName)) {
                    List<JavaFileObject> list = packageFiles.get(packageName);
                    list = (list == null ? List.of(fo) : list.prepend(fo));
                    packageFiles.put(packageName, list);
                    if (!packages.contains(packageName))
                        packages.add(packageName);
                }
            }
        }
    }

    private String getPackageName(String name) {
        int lastDot = name.lastIndexOf(".");
        return (lastDot == -1 ? "" : name.substring(0, lastDot));
    }

    private String getSimpleName(String name) {
        int lastDot = name.lastIndexOf(".");
        return (lastDot == -1 ? name : name.substring(lastDot + 1));
    }

    private boolean isIncluded(String packageName, Map<String,Boolean> includedPackages) {
        Boolean b = includedPackages.get(packageName);
        if (b == null) {
            b = isIncluded(getPackageName(packageName), includedPackages);
            includedPackages.put(packageName, b);
        }
        return b;
    }

    /**
     * Recursively search all directories in path for subdirectory name.
     * Add all packages found in such a directory to packages list.
     */
    private void searchSubPackage(String packageName,
                                  ListBuffer<String> packages,
                                  List<String> excludedPackages,
                                  Collection<File> pathnames) {
        if (excludedPackages.contains(packageName))
            return;

        String packageFilename = packageName.replace('.', File.separatorChar);
        boolean addedPackage = false;
        for (File pathname : pathnames) {
            File f = new File(pathname, packageFilename);
            String filenames[] = f.list();
            // if filenames not null, then found directory
            if (filenames != null) {
                for (String filename : filenames) {
                    if (!addedPackage
                            && (isValidJavaSourceFile(filename) ||
                                isValidJavaClassFile(filename))
                            && !packages.contains(packageName)) {
                        packages.append(packageName);
                        addedPackage = true;
                    } else if (isValidClassName(filename) &&
                               (new File(f, filename)).isDirectory()) {
                        searchSubPackage(packageName + "." + filename,
                                         packages, excludedPackages, pathnames);
                    }
                }
            }
        }
    }

    /**
     * Return true if given file name is a valid class file name.
     * @param file the name of the file to check.
     * @return true if given file name is a valid class file name
     * and false otherwise.
     */
    private static boolean isValidJavaClassFile(String file) {
        if (!file.endsWith(".class")) return false;
        String clazzName = file.substring(0, file.length() - ".class".length());
        return isValidClassName(clazzName);
    }

    /**
     * Return true if given file name is a valid Java source file name.
     * @param file the name of the file to check.
     * @return true if given file name is a valid Java source file name
     * and false otherwise.
     */
    private static boolean isValidJavaSourceFile(String file) {
        if (!file.endsWith(".java")) return false;
        String clazzName = file.substring(0, file.length() - ".java".length());
        return isValidClassName(clazzName);
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
     * @param clazzname the name of the class to check.
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
        ListBuffer<JCClassDecl> result = new ListBuffer<JCClassDecl>();
        for (JCCompilationUnit t : trees) {
            for (JCTree def : t.defs) {
                if (def.getTag() == JCTree.CLASSDEF)
                    result.append((JCClassDecl)def);
            }
        }
        return result.toList();
    }

}
