/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexBuilder;

/**
 * Generate the file with list of all the classes in this run.
 */
public class AllClassesIndexWriter extends HtmlDocletWriter {

    /**
     * Index of all the classes.
     */
    protected IndexBuilder indexbuilder;

    /**
     * The HTML tree for main tag.
     */
    protected HtmlTree mainTree = HtmlTree.MAIN();

    private final Navigation navBar;

    /**
     * Construct AllClassesIndexWriter object. Also initializes the indexbuilder variable in this
     * class.
     *
     * @param configuration The current configuration
     * @param filename Path to the file which is getting generated.
     * @param indexbuilder Unicode based Index from {@link IndexBuilder}
     */
    public AllClassesIndexWriter(HtmlConfiguration configuration,
            DocPath filename, IndexBuilder indexbuilder) {
        super(configuration, filename);
        this.indexbuilder = indexbuilder;
        this.navBar = new Navigation(null, configuration, fixedNavDiv, PageMode.ALLCLASSES, path);
    }

    /**
     * Create AllClassesIndexWriter object.
     *
     * @param configuration The current configuration
     * @param indexBuilder IndexBuilder object for all classes index.
     * @throws DocFileIOException
     */
    public static void generate(HtmlConfiguration configuration,
            IndexBuilder indexBuilder) throws DocFileIOException {
        generate(configuration, indexBuilder, DocPaths.ALLCLASSES_INDEX);
    }

    private static void generate(HtmlConfiguration configuration, IndexBuilder indexBuilder,
            DocPath fileName) throws DocFileIOException {
        AllClassesIndexWriter allClassGen = new AllClassesIndexWriter(configuration,
                fileName, indexBuilder);
        allClassGen.buildAllClassesFile();
    }

    /**
     * Print all the classes in the file.
     */
    protected void buildAllClassesFile() throws DocFileIOException {
        String label = resources.getText("doclet.All_Classes");
        HtmlTree bodyTree = getBody(getWindowTitle(label));
        HtmlTree header = HtmlTree.HEADER();
        addTop(header);
        navBar.setUserHeader(getUserHeaderFooter(true));
        header.add(navBar.getContent(true));
        bodyTree.add(header);
        Content allClassesContent = new ContentBuilder();
        addContents(allClassesContent);
        mainTree.add(allClassesContent);
        bodyTree.add(mainTree);
        Content footer = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        footer.add(navBar.getContent(false));
        addBottom(footer);
        bodyTree.add(footer);
        printHtmlDocument(null, "class index", bodyTree);
    }

    /**
     * Add all types to the content tree.
     *
     * @param content HtmlTree content to which the links will be added
     */
    protected void addContents(Content content) {
        Table table = new Table(HtmlStyle.typeSummary)
                .setHeader(new TableHeader(contents.classLabel, contents.descriptionLabel))
                .setRowScopeColumn(1)
                .setColumnStyles(HtmlStyle.colFirst, HtmlStyle.colLast)
                .setDefaultTab(resources.getText("doclet.All_Classes"))
                .addTab(resources.interfaceSummary, utils::isInterface)
                .addTab(resources.classSummary, e -> utils.isOrdinaryClass((TypeElement)e))
                .addTab(resources.enumSummary, utils::isEnum)
                .addTab(resources.exceptionSummary, e -> utils.isException((TypeElement)e))
                .addTab(resources.errorSummary, e -> utils.isError((TypeElement)e))
                .addTab(resources.annotationTypeSummary, utils::isAnnotationType)
                .setTabScript(i -> "show(" + i + ");");
        for (Character unicode : indexbuilder.index()) {
            for (Element element : indexbuilder.getMemberList(unicode)) {
                TypeElement typeElement = (TypeElement) element;
                if (!utils.isCoreClass(typeElement)) {
                    continue;
                }
                addTableRow(table, typeElement);
            }
        }
        Content titleContent = contents.allClassesLabel;
        Content pHeading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, true,
                HtmlStyle.title, titleContent);
        Content headerDiv = HtmlTree.DIV(HtmlStyle.header, pHeading);
        content.add(headerDiv);
        if (!table.isEmpty()) {
            HtmlTree li = HtmlTree.LI(HtmlStyle.blockList, table.toContent());
            HtmlTree ul = HtmlTree.UL(HtmlStyle.blockList, li);
            HtmlTree div = new HtmlTree(HtmlTag.DIV);
            div.setStyle(HtmlStyle.allClassesContainer);
            div.add(ul);
            content.add(div);
            if (table.needsScript()) {
                getMainBodyScript().append(table.getScript());
            }
        }
    }

    /**
     * Add table row.
     *
     * @param table the table to which the row will be added
     * @param klass the type to be added to the table
     */
    protected void addTableRow(Table table, TypeElement klass) {
        List<Content> rowContents = new ArrayList<>();
        Content classLink = getLink(new LinkInfoImpl(
                configuration, LinkInfoImpl.Kind.INDEX, klass));
        ContentBuilder description = new ContentBuilder();
        if (utils.isDeprecated(klass)) {
            description.add(getDeprecatedPhrase(klass));
            List<? extends DocTree> tags = utils.getDeprecatedTrees(klass);
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
