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
    annotations,
    arguments,
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
    description,
    descfrmTypeLabel,
    details,
    detail,
    emphasizedPhrase,
    exceptions,
    externalLink,
    fieldDetails,
    fieldSummary,
    flexBox,
    flexHeader,
    flexContent,
    header,
    helpSection,
    hierarchy,
    horizontal,
    implementationLabel,
    index,
    inheritance,
    inheritedList,
    interfaceName,
    legalCopy,
    memberDetails,
    memberName,
    memberNameLabel,
    memberNameLink,
    memberSignature,
    memberSummary,
    methodDetails,
    methodSummary,
    modifiers,
    moduleDescription,
    moduleLabelInPackage,
    moduleLabelInType,
    moduleTags,
    modulesSummary,
    nameValue,
    navBarCell1Rev,
    navList,
    navListSearch,
    nestedClassSummary,
    notes,
    overviewSummary,
    packages,
    packageDescription,
    packageHierarchyLabel,
    packageLabelInType,
    packagesSummary,
    packageUses,
    propertyDetails,
    propertySummary,
    providesSummary,
    requiresSummary,
    returnType,
    rowColor,
    searchTagLink,
    searchTagResult,
    serializedPackageContainer,
    serializedClassDetails,
    servicesSummary,
    skipNav,
    source,
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
    typeParameters,
    typeParametersLong,
    typeSummary,
    useSummary,
    usesSummary,
    verticalSeparator;

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
