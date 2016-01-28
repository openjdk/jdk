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

package jdk.javadoc.internal.doclets.formats.html;

import java.io.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DeprecatedAPIListBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.DocletAbortException;

import static jdk.javadoc.internal.doclets.toolkit.util.DeprecatedAPIListBuilder.*;

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
 * @author Atul M Dambalkar
 * @author Bhavesh Patel (Modified)
 */
public class DeprecatedListWriter extends SubWriterHolderWriter {

    private String getAnchorName(DeprElementKind kind) {
        switch (kind) {
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
            case PACKAGE:
                return "doclet.Deprecated_Packages";
            case INTERFACE:
                return "doclet.Deprecated_Interfaces";
            case CLASS:
                return "doclet.Deprecated_Classes";
            case ENUM:
                return "doclet.Deprecated_Enums";
            case EXCEPTION:
                return "doclet.Deprecated_Exceptions";
            case ERROR:
                return "doclet.Deprecated_Errors";
            case ANNOTATION_TYPE:
                return "doclet.Deprecated_Annotation_Types";
            case FIELD:
                return "doclet.Deprecated_Fields";
            case METHOD:
                return "doclet.Deprecated_Methods";
            case CONSTRUCTOR:
                return "doclet.Deprecated_Constructors";
            case ENUM_CONSTANT:
                return "doclet.Deprecated_Enum_Constants";
            case ANNOTATION_TYPE_MEMBER:
                return "doclet.Deprecated_Annotation_Type_Members";
            default:
                throw new AssertionError("unknown kind: " + kind);
        }
    }

    private String getSummaryKey(DeprElementKind kind) {
        switch (kind) {
            case PACKAGE:
                return "doclet.deprecated_packages";
            case INTERFACE:
                return "doclet.deprecated_interfaces";
            case CLASS:
                return "doclet.deprecated_classes";
            case ENUM:
                return "doclet.deprecated_enums";
            case EXCEPTION:
                return "doclet.deprecated_exceptions";
            case ERROR:
                return "doclet.deprecated_errors";
            case ANNOTATION_TYPE:
                return "doclet.deprecated_annotation_types";
            case FIELD:
                return "doclet.deprecated_fields";
            case METHOD:
                return "doclet.deprecated_methods";
            case CONSTRUCTOR:
                return "doclet.deprecated_constructors";
            case ENUM_CONSTANT:
                return "doclet.deprecated_enum_constants";
            case ANNOTATION_TYPE_MEMBER:
                return "doclet.deprecated_annotation_type_members";
            default:
                throw new AssertionError("unknown kind: " + kind);
        }
    }

    private String getHeaderKey(DeprElementKind kind) {
        switch (kind) {
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

    private ConfigurationImpl configuration;

    /**
     * Constructor.
     *
     * @param filename the file to be generated.
     */

    public DeprecatedListWriter(ConfigurationImpl configuration,
                                DocPath filename) throws IOException {
        super(configuration, filename);
        this.configuration = configuration;
        NestedClassWriterImpl classW = new NestedClassWriterImpl(this);
        writerMap = new EnumMap<>(DeprElementKind.class);
        for (DeprElementKind kind : DeprElementKind.values()) {
            switch (kind) {
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
     * specified on the Command Line.
     * Then instantiate DeprecatedListWriter and generate File.
     *
     * @param configuration the current configuration of the doclet.
     */
    public static void generate(ConfigurationImpl configuration) {
        DocPath filename = DocPaths.DEPRECATED_LIST;
        try {
            DeprecatedListWriter depr =
                   new DeprecatedListWriter(configuration, filename);
            depr.generateDeprecatedListFile(
                   new DeprecatedAPIListBuilder(configuration));
            depr.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                        "doclet.exception_encountered",
                        exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }

    /**
     * Generate the deprecated API list.
     *
     * @param deprapi list of deprecated API built already.
     */
    protected void generateDeprecatedListFile(DeprecatedAPIListBuilder deprapi)
            throws IOException {
        HtmlTree body = getHeader();
        HtmlTree htmlTree = (configuration.allowTag(HtmlTag.MAIN))
                ? HtmlTree.MAIN()
                : body;
        htmlTree.addContent(getContentsList(deprapi));
        String memberTableSummary;
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.contentContainer);
        for (DeprElementKind kind : DeprElementKind.values()) {
            if (deprapi.hasDocumentation(kind)) {
                addAnchor(deprapi, kind, div);
                memberTableSummary =
                        configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText(getHeadingKey(kind)),
                        configuration.getText(getSummaryKey(kind)));
                List<String> memberTableHeader = new ArrayList<>();
                memberTableHeader.add(configuration.getText("doclet.0_and_1",
                        configuration.getText(getHeaderKey(kind)),
                        configuration.getText("doclet.Description")));
                if (kind == DeprElementKind.PACKAGE)
                    addPackageDeprecatedAPI(deprapi.getSet(kind),
                            getHeadingKey(kind), memberTableSummary, memberTableHeader, div);
                else
                    writerMap.get(kind).addDeprecatedAPI(deprapi.getSet(kind),
                            getHeadingKey(kind), memberTableSummary, memberTableHeader, div);
            }
        }
        if (configuration.allowTag(HtmlTag.MAIN)) {
            htmlTree.addContent(div);
            body.addContent(htmlTree);
        } else {
            body.addContent(div);
        }
        htmlTree = (configuration.allowTag(HtmlTag.FOOTER))
                ? HtmlTree.FOOTER()
                : body;
        addNavLinks(false, htmlTree);
        addBottom(htmlTree);
        if (configuration.allowTag(HtmlTag.FOOTER)) {
            body.addContent(htmlTree);
        }
        printHtmlDocument(null, true, body);
    }

    /**
     * Add the index link.
     *
     * @param builder the deprecated list builder
     * @param type the type of list being documented
     * @param contentTree the content tree to which the index link will be added
     */
    private void addIndexLink(DeprecatedAPIListBuilder builder,
            DeprElementKind kind, Content contentTree) {
        if (builder.hasDocumentation(kind)) {
            Content li = HtmlTree.LI(getHyperLink(getAnchorName(kind),
                    getResource(getHeadingKey(kind))));
            contentTree.addContent(li);
        }
    }

    /**
     * Get the contents list.
     *
     * @param deprapi the deprecated list builder
     * @return a content tree for the contents list
     */
    public Content getContentsList(DeprecatedAPIListBuilder deprapi) {
        Content headContent = getResource("doclet.Deprecated_API");
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, headContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        Content headingContent = getResource("doclet.Contents");
        div.addContent(HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING, true,
                headingContent));
        Content ul = new HtmlTree(HtmlTag.UL);
        for (DeprElementKind kind : DeprElementKind.values()) {
            addIndexLink(deprapi, kind, ul);
        }
        div.addContent(ul);
        return div;
    }

    /**
     * Add the anchor.
     *
     * @param builder the deprecated list builder
     * @param type the type of list being documented
     * @param htmlTree the content tree to which the anchor will be added
     */
    private void addAnchor(DeprecatedAPIListBuilder builder, DeprElementKind kind, Content htmlTree) {
        if (builder.hasDocumentation(kind)) {
            htmlTree.addContent(getMarkerAnchor(getAnchorName(kind)));
        }
    }

    /**
     * Get the header for the deprecated API Listing.
     *
     * @return a content tree for the header
     */
    public HtmlTree getHeader() {
        String title = configuration.getText("doclet.Window_Deprecated_List");
        HtmlTree bodyTree = getBody(true, getWindowTitle(title));
        HtmlTree htmlTree = (configuration.allowTag(HtmlTag.HEADER))
                ? HtmlTree.HEADER()
                : bodyTree;
        addTop(htmlTree);
        addNavLinks(true, htmlTree);
        if (configuration.allowTag(HtmlTag.HEADER)) {
            bodyTree.addContent(htmlTree);
        }
        return bodyTree;
    }

    /**
     * Get the deprecated label.
     *
     * @return a content tree for the deprecated label
     */
    protected Content getNavLinkDeprecated() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, deprecatedLabel);
        return li;
    }
}
