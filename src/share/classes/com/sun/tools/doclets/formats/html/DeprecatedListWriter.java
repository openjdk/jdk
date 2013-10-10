/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.formats.html;

import java.io.*;

import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

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

    private static final String[] ANCHORS = new String[] {
        "package", "interface", "class", "enum", "exception", "error",
        "annotation.type", "field", "method", "constructor", "enum.constant",
        "annotation.type.member"
    };

    private static final String[] HEADING_KEYS = new String[] {
        "doclet.Deprecated_Packages", "doclet.Deprecated_Interfaces",
        "doclet.Deprecated_Classes", "doclet.Deprecated_Enums",
        "doclet.Deprecated_Exceptions", "doclet.Deprecated_Errors",
        "doclet.Deprecated_Annotation_Types",
        "doclet.Deprecated_Fields",
        "doclet.Deprecated_Methods", "doclet.Deprecated_Constructors",
        "doclet.Deprecated_Enum_Constants",
        "doclet.Deprecated_Annotation_Type_Members"
    };

    private static final String[] SUMMARY_KEYS = new String[] {
        "doclet.deprecated_packages", "doclet.deprecated_interfaces",
        "doclet.deprecated_classes", "doclet.deprecated_enums",
        "doclet.deprecated_exceptions", "doclet.deprecated_errors",
        "doclet.deprecated_annotation_types",
        "doclet.deprecated_fields",
        "doclet.deprecated_methods", "doclet.deprecated_constructors",
        "doclet.deprecated_enum_constants",
        "doclet.deprecated_annotation_type_members"
    };

    private static final String[] HEADER_KEYS = new String[] {
        "doclet.Package", "doclet.Interface", "doclet.Class",
        "doclet.Enum", "doclet.Exceptions",
        "doclet.Errors",
        "doclet.AnnotationType",
        "doclet.Field",
        "doclet.Method", "doclet.Constructor",
        "doclet.Enum_Constant",
        "doclet.Annotation_Type_Member"
    };

    private AbstractMemberWriter[] writers;

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
        writers = new AbstractMemberWriter[]
            {classW, classW, classW, classW, classW, classW,
            new FieldWriterImpl(this),
            new MethodWriterImpl(this),
            new ConstructorWriterImpl(this),
            new EnumConstantWriterImpl(this),
            new AnnotationTypeOptionalMemberWriterImpl(this, null)};
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
        Content body = getHeader();
        body.addContent(getContentsList(deprapi));
        String memberTableSummary;
        String[] memberTableHeader = new String[1];
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.contentContainer);
        for (int i = 0; i < DeprecatedAPIListBuilder.NUM_TYPES; i++) {
            if (deprapi.hasDocumentation(i)) {
                addAnchor(deprapi, i, div);
                memberTableSummary =
                        configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText(HEADING_KEYS[i]),
                        configuration.getText(SUMMARY_KEYS[i]));
                memberTableHeader[0] = configuration.getText("doclet.0_and_1",
                        configuration.getText(HEADER_KEYS[i]),
                        configuration.getText("doclet.Description"));
                // DeprecatedAPIListBuilder.PACKAGE == 0, so if i == 0, it is
                // a PackageDoc.
                if (i == DeprecatedAPIListBuilder.PACKAGE)
                    addPackageDeprecatedAPI(deprapi.getList(i),
                            HEADING_KEYS[i], memberTableSummary, memberTableHeader, div);
                else
                    writers[i - 1].addDeprecatedAPI(deprapi.getList(i),
                            HEADING_KEYS[i], memberTableSummary, memberTableHeader, div);
            }
        }
        body.addContent(div);
        addNavLinks(false, body);
        addBottom(body);
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
            int type, Content contentTree) {
        if (builder.hasDocumentation(type)) {
            Content li = HtmlTree.LI(getHyperLink(ANCHORS[type],
                    getResource(HEADING_KEYS[type])));
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
        for (int i = 0; i < DeprecatedAPIListBuilder.NUM_TYPES; i++) {
            addIndexLink(deprapi, i, ul);
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
    private void addAnchor(DeprecatedAPIListBuilder builder, int type, Content htmlTree) {
        if (builder.hasDocumentation(type)) {
            htmlTree.addContent(getMarkerAnchor(ANCHORS[type]));
        }
    }

    /**
     * Get the header for the deprecated API Listing.
     *
     * @return a content tree for the header
     */
    public Content getHeader() {
        String title = configuration.getText("doclet.Window_Deprecated_List");
        Content bodyTree = getBody(true, getWindowTitle(title));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
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
