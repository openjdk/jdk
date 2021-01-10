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

import com.sun.source.doctree.DeprecatedTree;
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
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DeprecatedAPIListBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.DeprecatedAPIListBuilder.DeprElementKind;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem;

import static jdk.javadoc.internal.doclets.toolkit.util.DeprecatedAPIListBuilder.DeprElementKind.RECORD_CLASS;
import static jdk.javadoc.internal.doclets.toolkit.util.DeprecatedAPIListBuilder.DeprElementKind.RECORD_CLASS;

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
        return switch (kind) {
            case REMOVAL                -> "forRemoval";
            case MODULE                 -> "module";
            case PACKAGE                -> "package";
            case INTERFACE              -> "interface";
            case CLASS                  -> "class";
            case ENUM                   -> "enum.class";
            case EXCEPTION              -> "exception";
            case ERROR                  -> "error";
            case ANNOTATION_TYPE        -> "annotation.interface";
            case FIELD                  -> "field";
            case METHOD                 -> "method";
            case CONSTRUCTOR            -> "constructor";
            case ENUM_CONSTANT          -> "enum.constant";
            case ANNOTATION_TYPE_MEMBER -> "annotation.interface.member";
            case RECORD_CLASS           -> "record.class";
        };
    }

    private String getHeadingKey(DeprElementKind kind) {
        return switch (kind) {
            case REMOVAL                -> "doclet.For_Removal";
            case MODULE                 -> "doclet.Modules";
            case PACKAGE                -> "doclet.Packages";
            case INTERFACE              -> "doclet.Interfaces";
            case CLASS                  -> "doclet.Classes";
            case ENUM                   -> "doclet.Enums";
            case EXCEPTION              -> "doclet.Exceptions";
            case ERROR                  -> "doclet.Errors";
            case ANNOTATION_TYPE        -> "doclet.Annotation_Types";
            case RECORD_CLASS           -> "doclet.RecordClasses";
            case FIELD                  -> "doclet.Fields";
            case METHOD                 -> "doclet.Methods";
            case CONSTRUCTOR            -> "doclet.Constructors";
            case ENUM_CONSTANT          -> "doclet.Enum_Constants";
            case ANNOTATION_TYPE_MEMBER -> "doclet.Annotation_Type_Members";
        };
    }

    private String getSummaryKey(DeprElementKind kind) {
        return switch (kind) {
            case REMOVAL                -> "doclet.for_removal";
            case MODULE                 -> "doclet.modules";
            case PACKAGE                -> "doclet.packages";
            case INTERFACE              -> "doclet.interfaces";
            case CLASS                  -> "doclet.classes";
            case ENUM                   -> "doclet.enums";
            case EXCEPTION              -> "doclet.exceptions";
            case ERROR                  -> "doclet.errors";
            case ANNOTATION_TYPE        -> "doclet.annotation_types";
            case RECORD_CLASS           -> "doclet.record_classes";
            case FIELD                  -> "doclet.fields";
            case METHOD                 -> "doclet.methods";
            case CONSTRUCTOR            -> "doclet.constructors";
            case ENUM_CONSTANT          -> "doclet.enum_constants";
            case ANNOTATION_TYPE_MEMBER -> "doclet.annotation_type_members";
        };
    }

    private String getHeaderKey(DeprElementKind kind) {
        return switch (kind) {
            case REMOVAL                -> "doclet.Element";
            case MODULE                 -> "doclet.Module";
            case PACKAGE                -> "doclet.Package";
            case INTERFACE              -> "doclet.Interface";
            case CLASS                  -> "doclet.Class";
            case ENUM                   -> "doclet.Enum";
            case EXCEPTION              -> "doclet.Exceptions";
            case ERROR                  -> "doclet.Errors";
            case ANNOTATION_TYPE        -> "doclet.AnnotationType";
            case RECORD_CLASS           -> "doclet.RecordClass";
            case FIELD                  -> "doclet.Field";
            case METHOD                 -> "doclet.Method";
            case CONSTRUCTOR            -> "doclet.Constructor";
            case ENUM_CONSTANT          -> "doclet.Enum_Constant";
            case ANNOTATION_TYPE_MEMBER -> "doclet.Annotation_Type_Member";
        };
    }

    private EnumMap<DeprElementKind, AbstractMemberWriter> writerMap;

    /**
     * Constructor.
     *
     * @param configuration the configuration for this doclet
     * @param filename the file to be generated
     */

    public DeprecatedListWriter(HtmlConfiguration configuration, DocPath filename) {
        super(configuration, filename);
        NestedClassWriterImpl classW = new NestedClassWriterImpl(this);
        writerMap = new EnumMap<>(DeprElementKind.class);
        for (DeprElementKind kind : DeprElementKind.values()) {
            switch (kind) {
                case REMOVAL, MODULE, PACKAGE, INTERFACE, CLASS, ENUM, EXCEPTION, ERROR,
                        ANNOTATION_TYPE, RECORD_CLASS
                                            -> writerMap.put(kind, classW);
                case FIELD                  -> writerMap.put(kind, new FieldWriterImpl(this));
                case METHOD                 -> writerMap.put(kind, new MethodWriterImpl(this));
                case CONSTRUCTOR            -> writerMap.put(kind, new ConstructorWriterImpl(this));
                case ENUM_CONSTANT          -> writerMap.put(kind, new EnumConstantWriterImpl(this));
                case ANNOTATION_TYPE_MEMBER -> writerMap.put(kind, new AnnotationTypeOptionalMemberWriterImpl(this, null));

                default -> throw new AssertionError("unknown kind: " + kind);
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
        if (configuration.conditionalPages.contains(HtmlConfiguration.ConditionalPage.DEPRECATED)) {
            DocPath filename = DocPaths.DEPRECATED_LIST;
            DeprecatedListWriter depr = new DeprecatedListWriter(configuration, filename);
            depr.generateDeprecatedListFile(configuration.deprecatedAPIListBuilder);
        }
    }

    /**
     * Generate the deprecated API list.
     *
     * @param deprAPI list of deprecated API built already.
     * @throws DocFileIOException if there is a problem writing the deprecated list
     */
    protected void generateDeprecatedListFile(DeprecatedAPIListBuilder deprAPI)
            throws DocFileIOException {
        HtmlTree body = getHeader();
        bodyContents.addMainContent(getContentsList(deprAPI));
        Content content = new ContentBuilder();
        for (DeprElementKind kind : DeprElementKind.values()) {
            if (deprAPI.hasDocumentation(kind)) {
                TableHeader memberTableHeader = new TableHeader(
                        contents.getContent(getHeaderKey(kind)), contents.descriptionLabel);
                addDeprecatedAPI(deprAPI.getSet(kind), getAnchorName(kind),
                            getHeadingKey(kind), memberTableHeader, content);
            }
        }
        bodyContents.addMainContent(content);
        bodyContents.setFooter(getFooter());
        String description = "deprecated elements";
        body.add(bodyContents);
        printHtmlDocument(null, description, body);

        if (!deprAPI.isEmpty() && configuration.mainIndex != null) {
            configuration.mainIndex.add(IndexItem.of(IndexItem.Category.TAGS,
                    resources.getText("doclet.Deprecated_API"), path));
        }
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
        Content heading = HtmlTree.HEADING_TITLE(Headings.PAGE_TITLE_HEADING,
                HtmlStyle.title, headContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        Content headingContent = contents.contentsHeading;
        div.add(HtmlTree.HEADING_TITLE(Headings.CONTENT_HEADING,
                headingContent));
        Content ul = new HtmlTree(TagName.UL);
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
        bodyContents.setHeader(getHeader(PageMode.DEPRECATED));
        return bodyTree;
    }

    /**
     * Add deprecated information to the documentation tree
     *
     * @param deprList list of deprecated API elements
     * @param id the id attribute of the table
     * @param headingKey the caption for the deprecated table
     * @param tableHeader table headers for the deprecated table
     * @param contentTree the content tree to which the deprecated table will be added
     */
    protected void addDeprecatedAPI(SortedSet<Element> deprList, String id, String headingKey,
            TableHeader tableHeader, Content contentTree) {
        if (deprList.size() > 0) {
            Content caption = contents.getContent(headingKey);
            Table table = new Table(HtmlStyle.summaryTable)
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
                List<? extends DeprecatedTree> tags = utils.getDeprecatedTrees(e);
                if (!tags.isEmpty()) {
                    addInlineDeprecatedComment(e, tags.get(0), desc);
                } else {
                    desc.add(HtmlTree.EMPTY);
                }
                table.addRow(link, desc);
            }
            // note: singleton list
            contentTree.add(HtmlTree.UL(HtmlStyle.blockList, HtmlTree.LI(table)));
        }
    }

    protected Content getDeprecatedLink(Element e) {
        AbstractMemberWriter writer = switch (e.getKind()) {
            case INTERFACE, CLASS, ENUM, ANNOTATION_TYPE, RECORD    -> new NestedClassWriterImpl(this);
            case FIELD                                              -> new FieldWriterImpl(this);
            case METHOD                                             -> new MethodWriterImpl(this);
            case CONSTRUCTOR                                        -> new ConstructorWriterImpl(this);
            case ENUM_CONSTANT                                      -> new EnumConstantWriterImpl(this);

            default -> new AnnotationTypeOptionalMemberWriterImpl(this, null);
        };
        return writer.getDeprecatedLink(e);
    }
}
