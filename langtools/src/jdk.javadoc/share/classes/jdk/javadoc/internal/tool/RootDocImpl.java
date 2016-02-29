/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.tool;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileManager;

import com.sun.source.util.DocTrees;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import jdk.javadoc.doclet.DocletEnvironment;

/**
 * This class holds the information from one run of javadoc.
 * Particularly the packages, classes and options specified
 * by the user.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @author Neal Gafter (rewrite)
 */
public class RootDocImpl implements DocletEnvironment {

    /**
     * list of classes specified on the command line.
     */
    private Set<TypeElement> cmdLineClasses;

    /**
     * list of packages specified on the command line.
     */
    private  Set<PackageElement> cmdLinePackages;

    public final DocEnv env;

    /**
     * Constructor used when reading source files.
     *
     * @param env the documentation environment, state for this javadoc run
     * @param classes list of classes specified on the commandline
     * @param packages list of package names specified on the commandline
     */
    public RootDocImpl(DocEnv env, List<JCClassDecl> classes, List<String> packages) {
        this.env = env;
        setPackages(env, packages);
        setClasses(env, classes);
    }

    /**
     * Constructor used when reading class files.
     *
     * @param env the documentation environment, state for this javadoc run
     * @param classes list of class names specified on the commandline
     */
    public RootDocImpl(DocEnv env, List<String> classes) {
        //super(env, null);
        this.env = env;

        Set<TypeElement> classList = new LinkedHashSet<>();
        for (String className : classes) {
            TypeElement c = env.loadClass(className);
            if (c == null)
                env.error(null, "javadoc.class_not_found", className);
            else
                classList.add(c);
        }
        cmdLineClasses = classList;
    }

    /**
     * Initialize classes information. Those classes are input from
     * command line.
     *
     * @param env the compilation environment
     * @param classes a list of ClassDeclaration
     */
    private void setClasses(DocEnv env, List<JCClassDecl> classes) {
        Set<TypeElement> result = new LinkedHashSet<>();
        classes.stream().filter((def) -> (env.shouldDocument(def.sym))).forEach((def) -> {
            TypeElement te = (TypeElement)def.sym;
            if (te != null) {
                env.setIncluded((Element)def.sym);
                result.add(te);
            }
        });
        cmdLineClasses = Collections.unmodifiableSet(result);
    }

    /**
     * Initialize packages information.
     *
     * @param env the compilation environment
     * @param packages a list of package names (String)
     */
    private void setPackages(DocEnv env, List<String> packages) {
        Set<PackageElement> packlist = new LinkedHashSet<>();
        packages.stream().forEach((name) -> {
            PackageElement pkg =  getElementUtils().getPackageElement(name);
            if (pkg != null) {
                env.setIncluded(pkg);
                packlist.add(pkg);
            } else {
                env.warning("main.no_source_files_for_package", name);
            }
        });
        cmdLinePackages = Collections.unmodifiableSet(packlist);
    }

    /**
     * Packages specified on the command line.
     */
    public Set<PackageElement> specifiedPackages() {
        return cmdLinePackages;
    }

    /**
     * Classes and interfaces specified on the command line,
     * including their inner classes
     */
    public Set<TypeElement> specifiedClasses() {
       Set<TypeElement> out = new LinkedHashSet<>();
       cmdLineClasses.stream().forEach((te) -> {
            env.addAllClasses(out, te, true);
        });
       return out;
    }

    private Set<TypeElement> classesToDocument = null;
    /**
     * Return all classes and interfaces (including those inside
     * packages) to be documented.
     */
    public Set<TypeElement> getIncludedClasses() {
        if (classesToDocument == null) {
            Set<TypeElement> classes = new LinkedHashSet<>();

            cmdLineClasses.stream().forEach((te) -> {
                env.addAllClasses(classes, te, true);
            });
            cmdLinePackages.stream().forEach((pkg) -> {
                env.addAllClasses(classes, pkg);
            });
            classesToDocument = Collections.unmodifiableSet(classes);
        }
        return classesToDocument;
    }

    /**
     * Return the name of this  item.
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
     * Return true if this Element is included in the active set.
     * RootDocImpl isn't even a program entity so it is always false.
     */
    @Override
    public boolean isIncluded(Element e) {
        return env.isIncluded(e);
    }

//    Note: these reporting methods are no longer used.
//    /**
//     * Print error message, increment error count.
//     *
//     * @param msg message to print
//     */
//    public void printError(String msg) {
//        env.printError(msg);
//    }
//
//    /**
//     * Print error message, increment error count.
//     *
//     * @param msg message to print
//     */
//    public void printError(DocTreePath path, String msg) {
//        env.printError(path, msg);
//    }
//
//    public void printError(Element e, String msg) {
//        env.printError(e, msg);
//    }
//
//    public void printWarning(Element e, String msg) {
//        env.printWarning(e, msg);
//    }
//
//    public void printNotice(Element e, String msg) {
//       env.printNotice(e, msg);
//    }
//
//    /**
//     * Print warning message, increment warning count.
//     *
//     * @param msg message to print
//     */
//    public void printWarning(String msg) {
//        env.printWarning(msg);
//    }

    /**
     * Return the current file manager.
     */
    public JavaFileManager getFileManager() {
        return env.fileManager;
    }

    @Override
    public DocTrees getDocTrees() {
        return env.docTrees;
    }

    @Override
    public Elements getElementUtils() {
        return env.elements;
    }

    @Override
    public List<Element> getSelectedElements(List<? extends Element> elements) {
        return elements.stream()
                .filter(e -> isIncluded(e))
                .collect(Collectors.<Element>toList());
    }

    @Override
    public Set<Element> getSpecifiedElements() {
        Set<Element> out = new LinkedHashSet<>();
        specifiedPackages().stream().forEach((pe) -> {
            out.add(pe);
        });
        specifiedClasses().stream().forEach((e) -> {
            out.add(e);
        });
        return out;
    }

    @Override
    public Types getTypeUtils() {
        return env.typeutils;
    }

    @Override
    public JavaFileManager getJavaFileManager() {
        return env.fileManager;
    }

    @Override
    public SourceVersion getSourceVersion() {
        return Source.toSourceVersion(env.source);
    }
}
