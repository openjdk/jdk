/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlConstants;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTag;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation;
import jdk.javadoc.internal.doclets.formats.html.markup.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.SerializedFormWriter;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

/**
 * Generate the Serialized Form Information Page.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 */
public class SerializedFormWriterImpl extends SubWriterHolderWriter
    implements SerializedFormWriter {

    Set<TypeElement> visibleClasses;

    /**
     * HTML tree for main tag.
     */
    private HtmlTree mainTree = HtmlTree.MAIN();

    private final Navigation navBar;

    /**
     * @param configuration the configuration data for the doclet
     */
    public SerializedFormWriterImpl(HtmlConfiguration configuration) {
        super(configuration, DocPaths.SERIALIZED_FORM);
        visibleClasses = configuration.getIncludedTypeElements();
        this.navBar = new Navigation(null, configuration, fixedNavDiv, PageMode.SERIALIZEDFORM, path);
    }

    /**
     * Get the given header.
     *
     * @param header the header to write
     * @return the body content tree
     */
    public Content getHeader(String header) {
        HtmlTree bodyTree = getBody(true, getWindowTitle(header));
        HtmlTree htmlTree = HtmlTree.HEADER();
        addTop(htmlTree);
        navBar.setUserHeader(getUserHeaderFooter(true));
        htmlTree.addContent(navBar.getContent(true));
        bodyTree.addContent(htmlTree);
        Content h1Content = new StringContent(header);
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, h1Content);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        mainTree.addContent(div);
        return bodyTree;
    }

    /**
     * Get the serialized form summaries header.
     *
     * @return the serialized form summary header tree
     */
    public Content getSerializedSummariesHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.setStyle(HtmlStyle.blockList);
        return ul;
    }

    /**
     * Get the package serialized form header.
     *
     * @return the package serialized form header tree
     */
    public Content getPackageSerializedHeader() {
        return HtmlTree.SECTION();
    }

    /**
     * Get the given package header.
     *
     * @param packageName the package header to write
     * @return a content tree for the package header
     */
    public Content getPackageHeader(String packageName) {
        Content heading = HtmlTree.HEADING(HtmlConstants.PACKAGE_HEADING, true,
                contents.packageLabel);
        heading.addContent(Contents.SPACE);
        heading.addContent(packageName);
        return heading;
    }

    /**
     * Get the serialized class header.
     *
     * @return a content tree for the serialized class header
     */
    public Content getClassSerializedHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.setStyle(HtmlStyle.blockList);
        return ul;
    }

    /**
     * Checks if a class is generated and is visible.
     *
     * @param typeElement the class being processed.
     * @return true if the class, that is being processed, is generated and is visible.
     */
    public boolean isVisibleClass(TypeElement typeElement) {
        return visibleClasses.contains(typeElement) && configuration.isGeneratedDoc(typeElement);
    }

    /**
     * Get the serializable class heading.
     *
     * @param typeElement the class being processed
     * @return a content tree for the class header
     */
    public Content getClassHeader(TypeElement typeElement) {
        Content classLink = (isVisibleClass(typeElement))
                ? getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.DEFAULT, typeElement)
                        .label(configuration.getClassName(typeElement)))
                : new StringContent(utils.getFullyQualifiedName(typeElement));
        Content li = HtmlTree.LI(HtmlStyle.blockList, links.createAnchor(
                utils.getFullyQualifiedName(typeElement)));
        Content superClassLink = typeElement.getSuperclass() != null
                ? getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.SERIALIZED_FORM,
                        typeElement.getSuperclass()))
                : null;

        //Print the heading.
        Content className = superClassLink == null ?
            contents.getContent(
            "doclet.Class_0_implements_serializable", classLink) :
            contents.getContent(
            "doclet.Class_0_extends_implements_serializable", classLink,
            superClassLink);
        li.addContent(HtmlTree.HEADING(HtmlConstants.SERIALIZED_MEMBER_HEADING,
                className));
        return li;
    }

    /**
     * Get the serial UID info header.
     *
     * @return a content tree for the serial uid info header
     */
    public Content getSerialUIDInfoHeader() {
        HtmlTree dl = new HtmlTree(HtmlTag.DL);
        dl.setStyle(HtmlStyle.nameValue);
        return dl;
    }

    /**
     * Adds the serial UID info.
     *
     * @param header the header that will show up before the UID.
     * @param serialUID the serial UID to print.
     * @param serialUidTree the serial UID content tree to which the serial UID
     *                      content will be added
     */
    public void addSerialUIDInfo(String header, String serialUID,
            Content serialUidTree) {
        Content headerContent = new StringContent(header);
        serialUidTree.addContent(HtmlTree.DT(headerContent));
        Content serialContent = new StringContent(serialUID);
        serialUidTree.addContent(HtmlTree.DD(serialContent));
    }

    /**
     * Get the class serialize content header.
     *
     * @return a content tree for the class serialize content header
     */
    public Content getClassContentHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.setStyle(HtmlStyle.blockList);
        return ul;
    }

    /**
     * Get the serialized content tree section.
     *
     * @param serializedTreeContent the serialized content tree to be added
     * @return a div content tree
     */
    public Content getSerializedContent(Content serializedTreeContent) {
        HtmlTree divContent = HtmlTree.DIV(HtmlStyle.serializedFormContainer,
                serializedTreeContent);
        mainTree.addContent(divContent);
        return mainTree;
    }

    /**
     * {@inheritDoc}
     */
    public void addPackageSerializedTree(Content serializedSummariesTree,
            Content packageSerializedTree) {
        serializedSummariesTree.addContent(HtmlTree.LI(HtmlStyle.blockList, packageSerializedTree));
    }

    /**
     * Add the footer.
     *
     * @param serializedTree the serialized tree to be added
     */
    public void addFooter(Content serializedTree) {
        Content htmlTree = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        htmlTree.addContent(navBar.getContent(false));
        addBottom(htmlTree);
        serializedTree.addContent(htmlTree);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void printDocument(Content serializedTree) throws DocFileIOException {
        printHtmlDocument(null, true, serializedTree);
    }

    /**
     * Return an instance of a SerialFieldWriter.
     *
     * @return an instance of a SerialFieldWriter.
     */
    public SerialFieldWriter getSerialFieldWriter(TypeElement typeElement) {
        return new HtmlSerialFieldWriter(this, typeElement);
    }

    /**
     * Return an instance of a SerialMethodWriter.
     *
     * @return an instance of a SerialMethodWriter.
     */
    public SerialMethodWriter getSerialMethodWriter(TypeElement typeElement) {
        return new HtmlSerialMethodWriter(this, typeElement);
    }
}
