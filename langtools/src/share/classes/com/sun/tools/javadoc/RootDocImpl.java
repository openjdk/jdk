/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.Locale;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import com.sun.javadoc.*;

import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Position;

/**
 * This class holds the information from one run of javadoc.
 * Particularly the packages, classes and options specified
 * by the user..
 *
 * @since 1.2
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Neal Gafter (rewrite)
 */
public class RootDocImpl extends DocImpl implements RootDoc {

    /**
     * list of classes specified on the command line.
     */
    private List<ClassDocImpl> cmdLineClasses;

    /**
     * list of packages specified on the command line.
     */
    private List<PackageDocImpl> cmdLinePackages;

    /**
     * a collection of all options.
     */
    private List<String[]> options;

    /**
     * Constructor used when reading source files.
     *
     * @param env the documentation environment, state for this javadoc run
     * @param classes list of classes specified on the commandline
     * @param packages list of package names specified on the commandline
     * @param options list of options
     */
    public RootDocImpl(DocEnv env, List<JCClassDecl> classes, List<String> packages, List<String[]> options) {
        super(env, null);
        this.options = options;
        setPackages(env, packages);
        setClasses(env, classes);
    }

    /**
     * Constructor used when reading class files.
     *
     * @param env the documentation environment, state for this javadoc run
     * @param classes list of class names specified on the commandline
     * @param options list of options
     */
    public RootDocImpl(DocEnv env, List<String> classes, List<String[]> options) {
        super(env, null);
        this.options = options;
        cmdLinePackages = List.nil();
        ListBuffer<ClassDocImpl> classList = new ListBuffer<ClassDocImpl>();
        for (String className : classes) {
            ClassDocImpl c = env.loadClass(className);
            if (c == null)
                env.error(null, "javadoc.class_not_found", className);
            else
                classList = classList.append(c);
        }
        cmdLineClasses = classList.toList();
    }

    /**
     * Initialize classes information. Those classes are input from
     * command line.
     *
     * @param env the compilation environment
     * @param classes a list of ClassDeclaration
     */
    private void setClasses(DocEnv env, List<JCClassDecl> classes) {
        ListBuffer<ClassDocImpl> result = new ListBuffer<ClassDocImpl>();
        for (JCClassDecl def : classes) {
            //### Do we want modifier check here?
            if (env.shouldDocument(def.sym)) {
                ClassDocImpl cd = env.getClassDoc(def.sym);
                if (cd != null) {
                    cd.isIncluded = true;
                    result.append(cd);
                } //else System.out.println(" (classdoc is null)");//DEBUG
            } //else System.out.println(" (env.shouldDocument() returned false)");//DEBUG
        }
        cmdLineClasses = result.toList();
    }

    /**
     * Initialize packages information.
     *
     * @param env the compilation environment
     * @param packages a list of package names (String)
     */
    private void setPackages(DocEnv env, List<String> packages) {
        ListBuffer<PackageDocImpl> packlist = new ListBuffer<PackageDocImpl>();
        for (String name : packages) {
            PackageDocImpl pkg = env.lookupPackage(name);
            if (pkg != null) {
                pkg.isIncluded = true;
                packlist.append(pkg);
            } else {
                env.warning(null, "main.no_source_files_for_package", name);
            }
        }
        cmdLinePackages = packlist.toList();
    }

    /**
     * Command line options.
     *
     * <pre>
     * For example, given:
     *     javadoc -foo this that -bar other ...
     *
     * This method will return:
     *      options()[0][0] = "-foo"
     *      options()[0][1] = "this"
     *      options()[0][2] = "that"
     *      options()[1][0] = "-bar"
     *      options()[1][1] = "other"
     * </pre>
     *
     * @return an array of arrays of String.
     */
    public String[][] options() {
        return options.toArray(new String[options.length()][]);
    }

    /**
     * Packages specified on the command line.
     */
    public PackageDoc[] specifiedPackages() {
        return (PackageDoc[])cmdLinePackages
            .toArray(new PackageDocImpl[cmdLinePackages.length()]);
    }

    /**
     * Classes and interfaces specified on the command line.
     */
    public ClassDoc[] specifiedClasses() {
        ListBuffer<ClassDocImpl> classesToDocument = new ListBuffer<ClassDocImpl>();
        for (ClassDocImpl cd : cmdLineClasses) {
            cd.addAllClasses(classesToDocument, true);
        }
        return (ClassDoc[])classesToDocument.toArray(new ClassDocImpl[classesToDocument.length()]);
    }

    /**
     * Return all classes and interfaces (including those inside
     * packages) to be documented.
     */
    public ClassDoc[] classes() {
        ListBuffer<ClassDocImpl> classesToDocument = new ListBuffer<ClassDocImpl>();
        for (ClassDocImpl cd : cmdLineClasses) {
            cd.addAllClasses(classesToDocument, true);
        }
        for (PackageDocImpl pd : cmdLinePackages) {
            pd.addAllClassesTo(classesToDocument);
        }
        return classesToDocument.toArray(new ClassDocImpl[classesToDocument.length()]);
    }

    /**
     * Return a ClassDoc for the specified class/interface name
     *
     * @param qualifiedName qualified class name
     *                        (i.e. includes package name).
     *
     * @return a ClassDocImpl holding the specified class, null if
     * this class is not referenced.
     */
    public ClassDoc classNamed(String qualifiedName) {
        return env.lookupClass(qualifiedName);
    }

    /**
     * Return a PackageDoc for the specified package name
     *
     * @param name package name
     *
     * @return a PackageDoc holding the specified package, null if
     * this package is not referenced.
     */
    public PackageDoc packageNamed(String name) {
        return env.lookupPackage(name);
    }

    /**
     * Return the name of this Doc item.
     *
     * @return the string <code>"*RootDocImpl*"</code>.
     */
    public String name() {
        return "*RootDocImpl*";
    }

    /**
     * Return the name of this Doc item.
     *
     * @return the string <code>"*RootDocImpl*"</code>.
     */
    public String qualifiedName() {
        return "*RootDocImpl*";
    }

    /**
     * Return true if this Doc is include in the active set.
     * RootDocImpl isn't even a program entity so it is always false.
     */
    public boolean isIncluded() {
        return false;
    }

    /**
     * Print error message, increment error count.
     *
     * @param msg message to print
     */
    public void printError(String msg) {
        env.printError(msg);
    }

    /**
     * Print error message, increment error count.
     *
     * @param msg message to print
     */
    public void printError(SourcePosition pos, String msg) {
        env.printError(pos, msg);
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param msg message to print
     */
    public void printWarning(String msg) {
        env.printWarning(msg);
    }

    /**
     * Print warning message, increment warning count.
     *
     * @param msg message to print
     */
    public void printWarning(SourcePosition pos, String msg) {
        env.printWarning(pos, msg);
    }

    /**
     * Print a message.
     *
     * @param msg message to print
     */
    public void printNotice(String msg) {
        env.printNotice(msg);
    }

    /**
     * Print a message.
     *
     * @param msg message to print
     */
    public void printNotice(SourcePosition pos, String msg) {
        env.printNotice(pos, msg);
    }

    /**
     * Return the path of the overview file and null if it does not exist.
     * @return the path of the overview file and null if it does not exist.
     */
    private JavaFileObject getOverviewPath() {
        for (String[] opt : options) {
            if (opt[0].equals("-overview")) {
                if (env.fileManager instanceof StandardJavaFileManager) {
                    StandardJavaFileManager fm = (StandardJavaFileManager) env.fileManager;
                    return fm.getJavaFileObjects(opt[1]).iterator().next();
                }
            }
        }
        return null;
    }

    /**
     * Do lazy initialization of "documentation" string.
     */
    protected String documentation() {
        if (documentation == null) {
            int cnt = options.length();
            JavaFileObject overviewPath = getOverviewPath();
            if (overviewPath == null) {
                // no doc file to be had
                documentation = "";
            } else {
                // read from file
                try {
                    documentation = readHTMLDocumentation(
                        overviewPath.openInputStream(),
                        overviewPath);
                } catch (IOException exc) {
                    documentation = "";
                    env.error(null, "javadoc.File_Read_Error", overviewPath.getName());
                }
            }
        }
        return documentation;
    }

    /**
     * Return the source position of the entity, or null if
     * no position is available.
     */
    public SourcePosition position() {
        JavaFileObject path;
        return ((path = getOverviewPath()) == null) ?
            null :
            SourcePositionImpl.make(path, Position.NOPOS, null);
    }

    /**
     * Return the locale provided by the user or the default locale value.
     */
    public Locale getLocale() {
        return env.doclocale.locale;
    }
}
