/*
 * Copyright (c) 1998, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Set;

import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.Entity;
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
 *  Generates the Serialized Form Information Page, <i>serialized-form.html</i>.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class SerializedFormWriterImpl extends SubWriterHolderWriter
    implements SerializedFormWriter {

    Set<TypeElement> visibleClasses;

    private final Navigation navBar;

    /**
     * @param configuration the configuration data for the doclet
     */
    public SerializedFormWriterImpl(HtmlConfiguration configuration) {
        super(configuration, DocPaths.SERIALIZED_FORM);
        visibleClasses = configuration.getIncludedTypeElements();
        this.navBar = new Navigation(null, configuration, PageMode.SERIALIZEDFORM, path);
    }

    /**
     * Get the given header.
     *
     * @param header the header to write
     * @return the body content tree
     */
    @Override
    public Content getHeader(String header) {
        HtmlTree bodyTree = getBody(getWindowTitle(header));
        Content headerContent = new ContentBuilder();
        addTop(headerContent);
        navBar.setUserHeader(getUserHeaderFooter(true));
        headerContent.add(navBar.getContent(true));
        Content h1Content = new StringContent(header);
        Content heading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING, true,
                HtmlStyle.title, h1Content);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        bodyContents.setHeader(headerContent)
                .addMainContent(div);
        return bodyTree;
    }

    /**
     * Get the serialized form summaries header.
     *
     * @return the serialized form summary header tree
     */
    @Override
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
    @Override
    public Content getPackageSerializedHeader() {
        return HtmlTree.SECTION(HtmlStyle.serializedPackageContainer);
    }

    /**
     * Get the given package header.
     *
     * @param packageName the package header to write
     * @return a content tree for the package header
     */
    @Override
    public Content getPackageHeader(String packageName) {
        Content heading = HtmlTree.HEADING(Headings.SerializedForm.PACKAGE_HEADING, true,
                contents.packageLabel);
        heading.add(Entity.NO_BREAK_SPACE);
        heading.add(packageName);
        return heading;
    }

    /**
     * Get the serialized class header.
     *
     * @return a content tree for the serialized class header
     */
    @Override
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
    @Override
    public Content getClassHeader(TypeElement typeElement) {
        Content classLink = (isVisibleClass(typeElement))
                ? getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.DEFAULT, typeElement)
                        .label(configuration.getClassName(typeElement)))
                : new StringContent(utils.getFullyQualifiedName(typeElement));
        Content section = HtmlTree.SECTION(HtmlStyle.serializedClassDetails)
                .setId(utils.getFullyQualifiedName(typeElement));
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
        section.add(HtmlTree.HEADING(Headings.SerializedForm.CLASS_HEADING, className));
        return section;
    }

    /**
     * Get the serial UID info header.
     *
     * @return a content tree for the serial uid info header
     */
    @Override
    public Content getSerialUIDInfoHeader() {
        return HtmlTree.DL(HtmlStyle.nameValue);
    }

    /**
     * Adds the serial UID info.
     *
     * @param header the header that will show up before the UID.
     * @param serialUID the serial UID to print.
     * @param serialUidTree the serial UID content tree to which the serial UID
     *                      content will be added
     */
    @Override
    public void addSerialUIDInfo(String header,
                                 String serialUID,
                                 Content serialUidTree)
    {
        Content headerContent = new StringContent(header);
        serialUidTree.add(HtmlTree.DT(headerContent));
        Content serialContent = new StringContent(serialUID);
        serialUidTree.add(HtmlTree.DD(serialContent));
    }

    /**
     * Get the class serialize content header.
     *
     * @return a content tree for the class serialize content header
     */
    @Override
    public Content getClassContentHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.setStyle(HtmlStyle.blockList);
        return ul;
    }

    /**
     * Add the serialized content tree section.
     *
     * @param serializedTreeContent the serialized content tree to be added
     */
    @Override
    public void addSerializedContent(Content serializedTreeContent) {
        HtmlTree divContent = HtmlTree.DIV(HtmlStyle.serializedFormContainer,
                serializedTreeContent);
        bodyContents.addMainContent(divContent);
    }

    @Override
    public void addPackageSerializedTree(Content serializedSummariesTree,
                                         Content packageSerializedTree)
    {
        serializedSummariesTree.add(HtmlTree.LI(HtmlStyle.blockList, packageSerializedTree));
    }

    /**
     * Add the footer.
     */
    @Override
    public void addFooter() {
        Content htmlTree = HtmlTree.FOOTER();
        navBar.setUserFooter(getUserHeaderFooter(false));
        htmlTree.add(navBar.getContent(false));
        addBottom(htmlTree);
        bodyContents.setFooter(htmlTree);
    }

    @Override
    public void printDocument(Content serializedTree) throws DocFileIOException {
        serializedTree.add(bodyContents.toContent());
        printHtmlDocument(null, "serialized forms", serializedTree);
    }

    /**
     * Return an instance of a SerialFieldWriter.
     *
     * @return an instance of a SerialFieldWriter.
     */
    @Override
    public SerialFieldWriter getSerialFieldWriter(TypeElement typeElement) {
        return new HtmlSerialFieldWriter(this, typeElement);
    }

    /**
     * Return an instance of a SerialMethodWriter.
     *
     * @return an instance of a SerialMethodWriter.
     */
    @Override
    public SerialMethodWriter getSerialMethodWriter(TypeElement typeElement) {
        return new HtmlSerialMethodWriter(this, typeElement);
    }
}
