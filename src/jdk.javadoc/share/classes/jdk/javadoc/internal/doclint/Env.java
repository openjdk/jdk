/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclint;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.MatchingUtils;
import com.sun.tools.javac.util.StringUtils;
import jdk.javadoc.internal.tool.AccessLevel;

/**
 * Utility container for current execution environment,
 * providing the current declaration and its doc comment.
 */
public class Env {

    /** Message handler. */
    final Messages messages;

    Set<String> customTags;

    Set<Pattern> includePackages;
    Set<Pattern> excludePackages;

    /**
     * How to handle bad references.
     *
     * If {@code false}, a reference into a module that is not
     * in the module graph will just be reported as a warning.
     * All other bad references will be reported as errors.
     * This is the desired behavior for javac.
     *
     * If {@code true}, all bad references will be reported as
     * errors. This is the desired behavior for javadoc.
     *
     */
    boolean strictReferenceChecks = false;

    // Utility classes
    DocTrees trees;
    Elements elements;
    Types types;

    // Types used when analysing doc comments.
    TypeMirror java_lang_Error;
    TypeMirror java_lang_RuntimeException;
    TypeMirror java_lang_SuppressWarnings;
    TypeMirror java_lang_Throwable;
    TypeMirror java_lang_Void;

    /** The path for the declaration containing the comment currently being analyzed. */
    TreePath currPath;
    /** The element for the declaration containing the comment currently being analyzed. */
    Element currElement;
    /** The comment current being analyzed. */
    DocCommentTree currDocComment;
    /**
     * The access level of the declaration containing the comment currently being analyzed.
     * This is the most limiting access level of the declaration itself
     * and that of its containers. For example, a public method in a private class is
     * noted as private.
     */
    AccessLevel currAccess;
    /** The set of methods, if any, that the current declaration overrides. */
    Set<? extends ExecutableElement> currOverriddenMethods;

    /** A map containing the info derived from {@code @SuppressWarnings} for an element. */
    Map<Element, Set<Messages.Group>> suppressWarnings = new HashMap<>();

    Env() {
        messages = new Messages(this);
    }

    void init(JavacTask task) {
        init(DocTrees.instance(task), task.getElements(), task.getTypes());
    }

    void init(DocTrees trees, Elements elements, Types types) {
        this.trees = trees;
        this.elements = elements;
        this.types = types;
    }

    void initTypes() {
        if (java_lang_Error != null)
            return ;

        java_lang_Error = elements.getTypeElement("java.lang.Error").asType();
        java_lang_RuntimeException = elements.getTypeElement("java.lang.RuntimeException").asType();
        java_lang_SuppressWarnings = elements.getTypeElement("java.lang.SuppressWarnings").asType();
        java_lang_Throwable = elements.getTypeElement("java.lang.Throwable").asType();
        java_lang_Void = elements.getTypeElement("java.lang.Void").asType();
    }

    void setCustomTags(String cTags) {
        customTags = new LinkedHashSet<>();
        for (String s : cTags.split(DocLint.SEPARATOR)) {
            if (!s.isEmpty())
                customTags.add(s);
        }
    }

    void setCheckPackages(String packages) {
        includePackages = new HashSet<>();
        excludePackages = new HashSet<>();
        for (String pack : packages.split(DocLint.SEPARATOR)) {
            boolean excluded = false;
            if (pack.startsWith("-")) {
                pack = pack.substring(1);
                excluded = true;
            }
            if (pack.isEmpty())
                continue;
            Pattern pattern = MatchingUtils.validImportStringToPattern(pack);
            if (excluded) {
                excludePackages.add(pattern);
            } else {
                includePackages.add(pattern);
            }
        }
    }

    static boolean validatePackages(String packages) {
        for (String pack : packages.split(DocLint.SEPARATOR)) {
            if (pack.startsWith("-")) {
                pack = pack.substring(1);
            }
            if (!pack.isEmpty() && !MatchingUtils.isValidImportString(pack))
                return false;
        }
        return true;
    }

    /** Set the current declaration and its doc comment. */
    void setCurrent(TreePath path, DocCommentTree comment) {
        currPath = path;
        currDocComment = comment;
        currElement = trees.getElement(currPath);
        currOverriddenMethods = ((JavacTypes) types).getOverriddenMethods(currElement);

        // It's convenient to use AccessLevel to model effects that nesting has
        // on access. While very similar, those are not the same concept.
        var mostLimitingSoFar = AccessLevel.PUBLIC;
        for (TreePath p = path; p != null; p = p.getParentPath()) {
            Element e = trees.getElement(p);
            if (e != null && e.getKind() != ElementKind.PACKAGE && e.getKind() != ElementKind.MODULE) {
                var level = AccessLevel.of(e.getModifiers());
                mostLimitingSoFar = mostLimitingSoFar.compareTo(level) <= 0
                        ? mostLimitingSoFar : level;
            }
        }
        currAccess = mostLimitingSoFar;
    }

    long getPos(TreePath p) {
        return ((JCTree) p.getLeaf()).pos;
    }

    long getStartPos(TreePath p) {
        SourcePositions sp = trees.getSourcePositions();
        return sp.getStartPosition(p.getCompilationUnit(), p.getLeaf());
    }

    boolean shouldCheck(CompilationUnitTree unit) {
        if (includePackages == null)
            return true;

        String packageName =   unit.getPackageName() != null
                             ? unit.getPackageName().toString()
                             : "";

        if (!includePackages.isEmpty()) {
            boolean included = false;
            for (Pattern pack : includePackages) {
                if (pack.matcher(packageName).matches()) {
                    included = true;
                    break;
                }
            }
            if (!included)
                return false;
        }

        for (Pattern pack : excludePackages) {
            if (pack.matcher(packageName).matches()) {
                return false;
            }
        }

        return true;
    }

    /**
     * {@return whether or not warnings in a group are suppressed for the current element}
     * @param g the group
     */
    boolean suppressWarnings(Messages.Group g) {
        return suppressWarnings(currElement, g);
    }

    /**
     * {@return whether or not warnings in a group are suppressed for a given element}
     * @param e the element
     * @param g the group
     */
    boolean suppressWarnings(Element e, Messages.Group g) {
        // check if warnings are suppressed in any enclosing classes
        Element encl = e.getEnclosingElement();
        if (encl != null && encl.asType().getKind() == TypeKind.DECLARED) {
            if (suppressWarnings(encl, g)) {
                return true;
            }
        }

        // check the local @SuppressWarnings annotation, caching the results
        return suppressWarnings.computeIfAbsent(e, this::getSuppressedGroups).contains(g);
    }

    /**
     * Returns the set of groups for an element for which messages should be suppressed.
     * The set is determined by examining the arguments for any {@code @SuppressWarnings}
     * annotation that may be present on the element.
     * The supported strings are: "doclint" and "doclint:GROUP,..." for each GROUP
     *
     * @param e the element
     * @return  the set
     */
    private Set<Messages.Group> getSuppressedGroups(Element e) {
        var gMap = Arrays.stream(Messages.Group.values())
                .collect(Collectors.toMap(Messages.Group::optName, Function.identity()));
        var set = EnumSet.noneOf(Messages.Group.class);
        for (String arg : getSuppressWarningsValue(e)) {
            if (arg.equals("doclint")) {
                set = EnumSet.allOf(Messages.Group.class);
                break;
            } else if (arg.startsWith("doclint:")) {
                final int len = "doclint:".length();
                for (String a : arg.substring(len).split(",")) {
                    Messages.Group argGroup = gMap.get(a);
                    if (argGroup != null) {
                        set.add(argGroup);
                    }
                }
            }
        }
        return set;
    }

    /**
     * Returns the list of values given to an instance of {@code @SuppressWarnings} for an element,
     * or an empty list if there is no annotation.
     *
     * @param e the element
     * @return the list
     */
    private List<String> getSuppressWarningsValue(Element e) {
        for (AnnotationMirror am : e.getAnnotationMirrors()) {
            DeclaredType dt = am.getAnnotationType();
            if (types.isSameType(dt, java_lang_SuppressWarnings)) {
                var values = am.getElementValues();
                for (var entry : values.entrySet()) {
                    if (entry.getKey().getSimpleName().contentEquals("value")) {
                        AnnotationValue av = entry.getValue();
                        if (av.getValue() instanceof List<?> list) {
                            List<String> result = new ArrayList<>();
                            for (var item : list) {
                                if (item instanceof AnnotationValue avItem
                                        && avItem.getValue() instanceof String s) {
                                    result.add(s);
                                }
                            }
                            return result;
                        }
                    }
                }

            }
        }
        return List.of();
    }
}
