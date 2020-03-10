/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.formats.html.markup.Table;
import jdk.javadoc.internal.doclets.formats.html.markup.TableHeader;

import java.util.EnumMap;
import java.util.List;
import java.util.SortedSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DeprecatedAPIListBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.DeprecatedAPIListBuilder.DeprElementKind;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

/**
 * Generate File to list all the deprecated classes and class members with the
 * appropriate links.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @see java.util.List
 */
public class DeprecatedListWriter extends SubWriterHolderWriter {

    private String getAnchorName(DeprElementKind kind) {
        switch (kind) {
            case REMOVAL:
                return "forRemoval";
            case MODULE:
                return "module";
            case PACKAGE:
                return "package";
            case INTERFACE:
                return "interface";
            case CLASS:
                return "class";
            case ENUM:
                return "enum";
            case EXCEPTION:
                return "exception";
            case ERROR:
                return "error";
            case ANNOTATION_TYPE:
                return "annotation.type";
            case FIELD:
                return "field";
            case METHOD:
                return "method";
            case CONSTRUCTOR:
                return "constructor";
            case ENUM_CONSTANT:
                return "enum.constant";
            case ANNOTATION_TYPE_MEMBER:
                return "annotation.type.member";
            default:
                throw new AssertionError("unknown kind: " + kind);
        }
    }

    private String getHeadingKey(DeprElementKind kind) {
        switch (kind) {
            case REMOVAL:
                return "doclet.For_Removal";
            case MODULE:
                return "doclet.Modules";
            case PACKAGE:
                return "doclet.Packages";
            case INTERFACE:
                return "doclet.Interfaces";
            case CLASS:
                return "doclet.Classes";
            case ENUM:
                return "doclet.Enums";
            case EXCEPTION:
                return "doclet.Exceptions";
            case ERROR:
                return "doclet.Errors";
            case ANNOTATION_TYPE:
                return "doclet.Annotation_Types";
            case FIELD:
                return "doclet.Fields";
            case METHOD:
                return "doclet.Methods";
            case CONSTRUCTOR:
                return "doclet.Constructors";
            case ENUM_CONSTANT:
                return "doclet.Enum_Constants";
            case ANNOTATION_TYPE_MEMBER:
                return "doclet.Annotation_Type_Members";
            default:
                throw new AssertionError("unknown kind: " + kind);
        }
    }

    private String getSummaryKey(DeprElementKind kind) {
        switch (kind) {
            case REMOVAL:
                return "doclet.for_removal";
            case MODULE:
                return "doclet.modules";
            case PACKAGE:
                return "doclet.packages";
            case INTERFACE:
                return "doclet.interfaces";
            case CLASS:
                return "doclet.classes";
            case ENUM:
                return "doclet.enums";
            case EXCEPTION:
                return "doclet.exceptions";
            case ERROR:
                return "doclet.errors";
            case ANNOTATION_TYPE:
                return "doclet.annotation_types";
            case FIELD:
                return "doclet.fields";
            case METHOD:
                return "doclet.methods";
            case CONSTRUCTOR:
                return "doclet.constructors";
            case ENUM_CONSTANT:
                return "doclet.enum_constants";
            case ANNOTATION_TYPE_MEMBER:
                return "doclet.annotation_type_members";
            default:
                throw new AssertionError("unknown kind: " + kind);
        }
    }

    private String getHeaderKey(DeprElementKind kind) {
        switch (kind) {
            case REMOVAL:
                return "doclet.Element";
            case MODULE:
                return "doclet.Module";
            case PACKAGE:
                return "doclet.Package";
            case INTERFACE:
                return "doclet.Interface";
            case CLASS:
                return "doclet.Class";
            case ENUM:
                return "doclet.Enum";
            case EXCEPTION:
                return "doclet.Exceptions";
            case ERROR:
                return "doclet.Errors";
            case ANNOTATION_TYPE:
                return "doclet.AnnotationType";
            case FIELD:
                return "doclet.Field";
            case METHOD:
                return "doclet.Method";
            case CONSTRUCTOR:
                return "doclet.Constructor";
            case ENUM_CONSTANT:
                return "doclet.Enum_Constant";
            case ANNOTATION_TYPE_MEMBER:
                return "doclet.Annotation_Type_Member";
            default:
                throw new AssertionError("unknown kind: " + kind);
        }
    }

    private EnumMap<DeprElementKind, AbstractMemberWriter> writerMap;

    private HtmlConfiguration configuration;

    private final Navigation navBar;

    /**
     * Constructor.
     *
     * @param configuration the configuration for this doclet
     * @param filename the file to be generated
     */

    public DeprecatedListWriter(HtmlConfiguration configuration, DocPath filename) {
        super(configuration, filename);
        this.configuration = configuration;
        this.navBar = new Navigation(null, configuration, PageMode.DEPRECATED, path);
        NestedClassWriterImpl classW = new NestedClassWriterImpl(this);
        writerMap = new EnumMap<>(DeprElementKind.class);
        for (DeprElementKind kind : DeprElementKind.values()) {
            switch (kind) {
                case REMOVAL:
                case MODULE:
                case PACKAGE:
                case INTERFACE:
                case CLASS:
                case ENUM:
                case EXCEPTION:
                case ERROR:
                case ANNOTATION_TYPE:
                    writerMap.put(kind, classW);
                    break;
                case FIELD:
                    writerMap.put(kind, new FieldWriterImpl(this));
                    break;
                case METHOD:
                    writerMap.put(kind, new MethodWriterImpl(this));
                    break;
                case CONSTRUCTOR:
                    writerMap.put(kind, new ConstructorWriterImpl(this));
                    break;
                case ENUM_CONSTANT:
                    writerMap.put(kind, new EnumConstantWriterImpl(this));
                    break;
                case ANNOTATION_TYPE_MEMBER:
                    writerMap.put(kind, new AnnotationTypeOptionalMemberWriterImpl(this, null));
                    break;
                default:
                   throw new AssertionError("unknown kind: " + kind);
            }
        }
    }

    /**
     * Get list of all the deprecated classes and members in all the Packages
     * specified on the command line.
     * Then instantiate DeprecatedListWriter and generate File.
     *
     * @param configuration the current configuration of the doclet.
     * @throws DocFileIOException if there is a problem writing the deprecated list
     */
    public static void generate(HtmlConfiguration configuration) throws DocFileIOException {
        DocPath filename = DocPaths.DEPRECATED_LIST;
        DeprecatedListWriter depr = new DeprecatedListWriter(configuration, filename);
        depr.generateDeprecatedListFile(
               new DeprecatedAPIListBuilder(configuration));
    }

    /**
     * Generate the deprecated API list.
     *
     * @param deprapi list of deprecated API built already.
     * @throws DocFileIOException if there is a problem writing the deprecated list
     */
    protected void generateDeprecatedListFile(DeprecatedAPIListBuilder deprapi)
            throws DocFileIOException {
        HtmlTree body = getHeader();
        bodyContents.addMainContent(getContentsList(deprapi));
        String memberTableSummary;
        Content content = new ContentBuilder();
        for (DeprElementKind kind : DeprElementKind.values()) {
            if (deprapi.hasDocumentation(kind)) {
                memberTableSummary = resources.getText("doclet.Member_Table_Summary",
                        resources.getText(getHeadingKey(kind)),
                        resources.getText(getSummaryKey(kind)));
                TableHeader memberTableHeader = new TableHeader(
                        contents.getContent(getHeaderKey(kind)), contents.descriptionLabel);
                addDeprecatedAPI(deprapi.getSet(kind), getAnchorName(kind),
                            getHeadingKey(kind), memberTableSummary, memberTableHeader, content);
            }
        }
        bodyContents.addMainContent(content);
        HtmlTree htmlTree = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        htmlTree.add(navBar.getContent(Navigation.Position.BOTTOM));
        addBottom(htmlTree);
        bodyContents.setFooter(htmlTree);
        String description = "deprecated elements";
        body.add(bodyContents);
        printHtmlDocument(null, description, body);
    }

    /**
     * Add the index link.
     *
     * @param builder the deprecated list builder
     * @param kind the kind of list being documented
     * @param contentTree the content tree to which the index link will be added
     */
    private void addIndexLink(DeprecatedAPIListBuilder builder,
            DeprElementKind kind, Content contentTree) {
        if (builder.hasDocumentation(kind)) {
            Content li = HtmlTree.LI(links.createLink(getAnchorName(kind),
                    contents.getContent(getHeadingKey(kind))));
            contentTree.add(li);
        }
    }

    /**
     * Get the contents list.
     *
     * @param deprapi the deprecated list builder
     * @return a content tree for the contents list
     */
    public Content getContentsList(DeprecatedAPIListBuilder deprapi) {
        Content headContent = contents.deprecatedAPI;
        Content heading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, true,
                HtmlStyle.title, headContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        Content headingContent = contents.contentsHeading;
        div.add(HtmlTree.HEADING(Headings.CONTENT_HEADING, true,
                headingContent));
        Content ul = new HtmlTree(HtmlTag.UL);
        for (DeprElementKind kind : DeprElementKind.values()) {
            addIndexLink(deprapi, kind, ul);
        }
        div.add(ul);
        return div;
    }

    /**
     * Get the header for the deprecated API Listing.
     *
     * @return a content tree for the header
     */
    public HtmlTree getHeader() {
        String title = resources.getText("doclet.Window_Deprecated_List");
        HtmlTree bodyTree = getBody(getWindowTitle(title));
        Content headerContent = new ContentBuilder();
        addTop(headerContent);
        navBar.setUserHeader(getUserHeaderFooter(true));
        headerContent.add(navBar.getContent(Navigation.Position.TOP));
        bodyContents.setHeader(headerContent);
        return bodyTree;
    }

    /**
     * Add deprecated information to the documentation tree
     *
     * @param deprList list of deprecated API elements
     * @param id the id attribute of the table
     * @param headingKey the caption for the deprecated table
     * @param tableSummary the summary for the deprecated table
     * @param tableHeader table headers for the deprecated table
     * @param contentTree the content tree to which the deprecated table will be added
     */
    protected void addDeprecatedAPI(SortedSet<Element> deprList, String id, String headingKey,
            String tableSummary, TableHeader tableHeader, Content contentTree) {
        if (deprList.size() > 0) {
            Content caption = contents.getContent(headingKey);
            Table table = new Table(HtmlStyle.deprecatedSummary)
                    .setCaption(caption)
                    .setHeader(tableHeader)
                    .setId(id)
                    .setColumnStyles(HtmlStyle.colDeprecatedItemName, HtmlStyle.colLast);
            for (Element e : deprList) {
                Content link;
                switch (e.getKind()) {
                    case MODULE:
                        ModuleElement m = (ModuleElement) e;
                        link = getModuleLink(m, new StringContent(m.getQualifiedName()));
                        break;
                    case PACKAGE:
                        PackageElement pkg = (PackageElement) e;
                        link = getPackageLink(pkg, getPackageName(pkg));
                        break;
                    default:
                        link = getDeprecatedLink(e);
                }
                Content desc = new ContentBuilder();
                List<? extends DocTree> tags = utils.getDeprecatedTrees(e);
                if (!tags.isEmpty()) {
                    addInlineDeprecatedComment(e, tags.get(0), desc);
                } else {
                    desc.add(HtmlTree.EMPTY);
                }
                table.addRow(link, desc);
            }
            Content li = HtmlTree.LI(HtmlStyle.blockList, table);
            Content ul = HtmlTree.UL(HtmlStyle.blockList, li);
            contentTree.add(ul);
        }
    }

    protected Content getDeprecatedLink(Element e) {
        AbstractMemberWriter writer;
        switch (e.getKind()) {
            case INTERFACE:
            case CLASS:
            case ENUM:
            case ANNOTATION_TYPE:
                writer = new NestedClassWriterImpl(this);
                break;
            case FIELD:
                writer = new FieldWriterImpl(this);
                break;
            case METHOD:
                writer = new MethodWriterImpl(this);
                break;
            case CONSTRUCTOR:
                writer = new ConstructorWriterImpl(this);
                break;
            case ENUM_CONSTANT:
                writer = new EnumConstantWriterImpl(this);
                break;
            default:
                writer = new AnnotationTypeOptionalMemberWriterImpl(this, null);
        }
        return writer.getDeprecatedLink(e);
    }
}
