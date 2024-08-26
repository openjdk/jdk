/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DeprecatedTree;

import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.Utils.ElementFlag;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlTree;

/**
 * Generate the file with list of all the classes in this run.
 */
public class AllClassesIndexWriter extends HtmlDocletWriter {

    /**
     * Construct AllClassesIndexWriter object. Also initializes the indexBuilder variable in this
     * class.
     *
     * @param configuration The current configuration
     */
    public AllClassesIndexWriter(HtmlConfiguration configuration) {
        super(configuration, DocPaths.ALLCLASSES_INDEX);
    }

    @Override
    public void buildPage() throws DocFileIOException {
        messages.notice("doclet.Building_Index_For_All_Classes");
        String label = resources.getText("doclet.All_Classes_And_Interfaces");
        Content allClassesContent = new ContentBuilder();
        addContents(allClassesContent);
        Content mainContent = new ContentBuilder();
        mainContent.add(allClassesContent);
        HtmlTree body = getBody(getWindowTitle(label));
        body.add(new BodyContents()
                .setHeader(getHeader(PageMode.ALL_CLASSES))
                .addMainContent(mainContent)
                .setFooter(getFooter()));
        printHtmlDocument(null, "class index", body);
    }

    /**
     * Add all types to the content.
     *
     * @param target the content to which the links will be added
     */
    protected void addContents(Content target) {
        var table = new Table<TypeElement>(HtmlStyles.summaryTable)
                .setHeader(new TableHeader(contents.classLabel, contents.descriptionLabel))
                .setColumnStyles(HtmlStyles.colFirst, HtmlStyles.colLast)
                .setId(HtmlIds.ALL_CLASSES_TABLE)
                .setDefaultTab(contents.allClassesAndInterfacesLabel)
                .addTab(contents.interfaces, utils::isPlainInterface)
                .addTab(contents.classes, utils::isNonThrowableClass)
                .addTab(contents.enums, utils::isEnum)
                .addTab(contents.records, utils::isRecord)
                .addTab(contents.exceptionClasses, utils::isThrowable)
                .addTab(contents.annotationTypes, utils::isAnnotationInterface);
        Set<TypeElement> typeElements = getTypeElements();
        for (TypeElement typeElement : typeElements) {
            addTableRow(table, typeElement);
        }
        Content titleContent = contents.allClassesAndInterfacesLabel;
        var pHeading = HtmlTree.HEADING_TITLE(Headings.PAGE_TITLE_HEADING,
                HtmlStyles.title, titleContent);
        var headerDiv = HtmlTree.DIV(HtmlStyles.header, pHeading);
        target.add(headerDiv);
        if (!table.isEmpty()) {
            target.add(table);
        }
    }

    private Set<TypeElement> getTypeElements() {
        Set<TypeElement> classes = new TreeSet<>(utils.comparators.allClassesComparator());
        boolean noDeprecated = options.noDeprecated();
        Set<TypeElement> includedTypes = configuration.getIncludedTypeElements();
        for (TypeElement typeElement : includedTypes) {
            if (utils.hasHiddenTag(typeElement) || !utils.isCoreClass(typeElement)) {
                continue;
            }
            if (noDeprecated
                    && (utils.isDeprecated(typeElement)
                    || utils.isDeprecated(utils.containingPackage(typeElement)))) {
                continue;
            }
            classes.add(typeElement);
        }
        return classes;
    }

    /**
     * Add table row.
     *
     * @param table the table to which the row will be added
     * @param klass the type to be added to the table
     */
    protected void addTableRow(Table<TypeElement> table, TypeElement klass) {
        List<Content> rowContents = new ArrayList<>();
        Content classLink = getLink(new HtmlLinkInfo(
                configuration, HtmlLinkInfo.Kind.SHOW_TYPE_PARAMS_IN_LABEL, klass));
        ContentBuilder description = new ContentBuilder();
        Set<ElementFlag> flags = utils.elementFlags(klass);
        if (flags.contains(ElementFlag.PREVIEW)) {
            description.add(contents.previewPhrase);
            addSummaryComment(klass, description);
        } else if (flags.contains(ElementFlag.DEPRECATED)) {
            description.add(getDeprecatedPhrase(klass));
            List<? extends DeprecatedTree> tags = utils.getDeprecatedTrees(klass);
            if (!tags.isEmpty()) {
                addSummaryDeprecatedComment(klass, tags.get(0), description);
            }
        } else {
            addSummaryComment(klass, description);
        }
        rowContents.add(classLink);
        rowContents.add(description);
        table.addRow(klass, rowContents);
    }
}
