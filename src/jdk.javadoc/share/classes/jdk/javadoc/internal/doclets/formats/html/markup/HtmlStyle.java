/*
 * Copyright (c) 2010, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.markup;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Enum representing HTML styles, with associated entries in the stylesheet files.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @apiNote
 * Despite the name, the members of this enum provide values for the HTML {@code class} attribute,
 * and <strong>not</strong> the HTML {@code style} attribute.
 * This is to avoid confusion with the widespread use of the word "class" in the Java ecosystem,
 * and the potential for clashes with methods called {@code setClass} instead of {@code setStyle}.
 *
 * @see <a href="https://html.spec.whatwg.org/#classes>WhatWG: {@code class} attribute</a>
 */
public enum HtmlStyle {
    aboutLanguage,
    activeTableTab,
    altColor,
    block,
    blockList,
    bottomNav,
    circle,
    classUses,
    colConstructorName,
    colDeprecatedItemName,
    colFirst,
    colLast,
    colSecond,
    constantsSummary,
    constructorDetails,
    constructorSummary,
    constantDetails,
    deprecatedLabel,
    deprecatedSummary,
    deprecationBlock,
    deprecationComment,
    descfrmTypeLabel,
    details,
    detailsList,
    detail,
    externalLink,
    fieldDetails,
    fieldSummary,
    header,
    helpFootnote,
    helpSection,
    helpSectionList,
    hierarchy,
    horizontal,
    implementationLabel,
    index,
    inheritance,
    inheritedList,
    interfaceName,
    legalCopy,
    memberDetails,
    memberList,
    memberNameLabel,
    memberNameLink,
    memberSummary,
    methodDetails,
    methodSummary,
    moduleLabelInPackage,
    moduleLabelInType,
    modulesSummary,
    nameValue,
    navBarCell1Rev,
    navList,
    navListSearch,
    nestedClassSummary,
    overviewSummary,
    packages,
    packageHierarchyLabel,
    packageLabelInType,
    packagesSummary,
    packageUses,
    propertyDetails,
    propertySummary,
    providesSummary,
    requiresSummary,
    rowColor,
    searchTagLink,
    searchTagResult,
    serializedPackageContainer,
    serializedClassDetails,
    servicesSummary,
    skipNav,
    sourceContainer,
    sourceLineNo,
    subNav,
    subNavList,
    subTitle,
    summary,
    systemPropertiesSummary,
    tabEnd,
    tableTab,
    title,
    topNav,
    typeNameLabel,
    typeNameLink,
    typeSummary,
    useSummary,
    usesSummary,
    verticalSeparator,

    //<editor-fold desc="documentation comments">
    // The following constants are used for the components used to present the content
    // generated from documentation comments.

    /**
     * The class of the element used to present the documentation comment for a type or member
     * element.
     * The content of the block tags will be in a nested element with class {@link #notes}.
     */
    description,

    /**
     * The class of the element used to present the documentation comment for a module element,
     * excluding block tags.
     * The content of the block tags will be in a sibling element with class {@link #moduleTags}.
     */
    moduleDescription,

    /**
     * The class of the {@code dl} element used to present the block tags in the documentation
     * comment for a module element.
     * Additional (derived) information, such as implementation or inheritance details, may
     * also appear in this element.
     */
    moduleTags,

    /**
     * The class of the element used to present the documentation comment for package element.
     * The content of the block tags will be in a nested element with class {@link #notes}.
     */
    packageDescription,

    /**
     * The class of the {@code dl} element used to present the block tags in the documentation
     * comment for a package, type or member element.
     * Additional (derived) information, such as implementation or inheritance details, may
     * also appear in this element.
     */
    notes,
    //</editor-fold>

    //<editor-fold desc="flex layout">
    // The following constants are used for the components of the top-level structures for "flex" layout.

    /**
     * The class of the top-level {@code div} element used to arrange for "flex" layout in
     * a browser window. The element should contain two child elements: one with class
     * {@link #flexHeader flex-header} and one with class {@link #flexContent flex-content}.
     */
    flexBox,

    /**
     * The class of the {@code header} element within a {@link #flexBox flex-box} container.
     * The element is always displayed at the top of the viewport.
     */
    flexHeader,

    /**
     * The class of the {@code div} element within a {@link #flexBox flex-box} container
     * This element appears below the header and can be scrolled if too big for the available height.
     */
    flexContent,
    //</editor-fold>

    //<editor-fold desc="member signature">
    // The following constants are used for the components of a signature of an element

    /**
     * The class of a {@code span} element for the signature of an element.
     * The signature will contain a member name and, depending on the kind of element,
     * it can contain any of the following:
     * annotations, type parameters, modifiers, return type, parameters, and exceptions.
     */
    memberSignature,

    /**
     * The class of a {@code span} element for any annotations in the signature of an element.
     */
    annotations,

    /**
     * The class of a {@code span} element for any exceptions in a signature of an executable element.
     */
    exceptions,

    /**
     * The class of a {@code span} for the member name in the signature of an element.
     */
    memberName,

    /**
     * The class of a {@code span} for any modifiers in the signature of an element.
     */
    modifiers,

    /**
     * The class of a {@code span} for any parameters in the signature of an executable element.
     */
    parameters,

    /**
     * The class of a {@code span} for the return type in the signature of an method element.
     */
    returnType,

    /**
     * The class of a {@code span} for type parameters in the signature of an element,
     * used when the type parameters should reasonably be displayed inline.
     */
    typeParameters,

    /**
     * The class of a {@code span} for type parameters in the signature of an element,
     * used when the type parameters are too long to be displayed inline.
     * @implNote
     * The threshold for choosing between {@code typeParameters} and {@code typeParametersLong}
     * is 50 characters.
     */
    typeParametersLong,
    //</editor-fold>

    //<editor-fold desc="page styles for <body> elements">
    // The following constants are used for the class of the {@code <body>} element
    // for the corresponding pages.

    /**
     * The class of the {@code body} element for the "All Classes" index page.
     */
    allClassesIndexPage,

    /**
     * The class of the {@code body} element for the "All Packages" index page.
     */
    allPackagesIndexPage,

    /**
     * The class of the {@code body} element for a class-declaration page.
     */
    classDeclarationPage,

    /**
     * The class of the {@code body} element for a class-use page.
     */
    classUsePage,

    /**
     * The class of the {@code body} element for the constants-summary page.
     */
    constantsSummaryPage,

    /**
     * The class of the {@code body} element for the page listing any deprecated items.
     */
    deprecatedListPage,

    /**
     * The class of the {@code body} element for a "doc-file" page..
     */
    docFilePage,

    /**
     * The class of the {@code body} element for the "help" page.
     */
    helpPage,

    /**
     * The class of the {@code body} element for the top-level redirect page.
     */
    indexRedirectPage,

    /**
     * The class of the {@code body} element for a module-declaration page.
     */
    moduleDeclarationPage,

    /**
     * The class of the {@code body} element for the module-index page.
     */
    moduleIndexPage,

    /**
     * The class of the {@code body} element for a package-declaration page.
     */
    packageDeclarationPage,

    /**
     * The class of the {@code body} element for the package-index page.
     */
    packageIndexPage,

    /**
     * The class of the {@code body} element for the page for the package hierarchy.
     */
    packageTreePage,

    /**
     * The class of the {@code body} element for a package-use page.
     */
    packageUsePage,

    /**
     * The class of the {@code body} element for the serialized-forms page.
     */
    serializedFormPage,

    /**
     * The class of the {@code body} element for the full single index page.
     */
    singleIndexPage,

    /**
     * The class of the {@code body} element for a page with the source code for a class.
     */
    sourcePage,

    /**
     * The class of the {@code body} element for a page in a "split index".
     */
    splitIndexPage,

    /**
     * The class of the {@code body} element for the system-properties page.
     */
    systemPropertiesPage,

    /**
     * The class of the {@code body} element for the page for the class hierarchy.
     */
    treePage;
    //</editor-fold>

    private final String cssName;

    HtmlStyle() {
        cssName = Pattern.compile("\\p{Upper}")
                .matcher(toString())
                .replaceAll(mr -> "-" + mr.group().toLowerCase(Locale.US));
    }

    HtmlStyle(String cssName) {
        this.cssName = cssName;
    }

    /**
     * Returns the CSS class name associated with this style.
     * @return the CSS class name
     */
    public String cssName() {
        return cssName;
    }
}
