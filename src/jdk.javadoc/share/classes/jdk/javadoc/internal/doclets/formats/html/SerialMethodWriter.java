/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.formats.html.taglets.TagletManager;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.HtmlTag;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;


/**
 * Generate serialized form for Serializable/Externalizable methods.
 * Documentation denoted by the <code>serialData</code> tag is processed.
 */
public class SerialMethodWriter extends MethodWriter {

    public SerialMethodWriter(SubWriterHolderWriter writer, TypeElement typeElement) {
        super(writer, typeElement);
    }

    protected Content getSerializableMethodsHeader() {
        return HtmlTree.UL(HtmlStyles.blockList);
    }

    protected Content getMethodsContentHeader() {
        return new HtmlTree(HtmlTag.LI);
    }

    /**
     * Add serializable methods.
     *
     * @param heading the heading for the section
     * @param source the content to be added to the serializable methods
     *        content
     * @return a content for the serializable methods content
     */
    protected Content getSerializableMethods(String heading, Content source) {
        Content headingContent = Text.of(heading);
        var serialHeading = HtmlTree.HEADING(Headings.SerializedForm.CLASS_SUBHEADING, headingContent);
        var section = HtmlTree.SECTION(HtmlStyles.detail, serialHeading);
        section.add(source);
        return HtmlTree.LI(section);
    }

    /**
     * Return the no customization message.
     *
     * @param msg the message to be displayed
     * @return no customization message content
     */
    protected Content getNoCustomizationMsg(String msg) {
        return Text.of(msg);
    }

    /**
     * Add the member header.
     *
     * @param member the method document to be listed
     * @param methodsContent the content to which the member header will be added
     */
    protected void addMemberHeader(ExecutableElement member, Content methodsContent) {
        Content memberContent = Text.of(name(member));
        var heading = HtmlTree.HEADING(Headings.SerializedForm.MEMBER_HEADING, memberContent);
        methodsContent.add(heading);
        methodsContent.add(getSignature(member));
    }

    /**
     * Add the deprecated information for this member.
     *
     * @param member the method to document.
     * @param methodsContent the content to which the deprecated info will be added
     */
    protected void addDeprecatedMemberInfo(ExecutableElement member, Content methodsContent) {
        addDeprecatedInfo(member, methodsContent);
    }

    /**
     * Add the description text for this member.
     *
     * @param member the method to document.
     * @param methodsContent the content to which the deprecated info will be added
     */
    protected void addMemberDescription(ExecutableElement member, Content methodsContent) {
        addComment(member, methodsContent);
    }

    /**
     * Add the tag information for this member.
     *
     * @param member the method to document.
     * @param methodsContent the content to which the member tags info will be added
     */
    protected void addMemberTags(ExecutableElement member, Content methodsContent) {
        TagletManager tagletManager = configuration.tagletManager;
        Content tagContent = writer.getBlockTagOutput(member, tagletManager.getSerializedFormTaglets());
        var dl = HtmlTree.DL(HtmlStyles.notes);
        dl.add(tagContent);
        methodsContent.add(dl);
        if (name(member).equals("writeExternal")
                && utils.getSerialDataTrees(member).isEmpty()) {
            serialWarning(member, "doclet.MissingSerialDataTag",
                utils.getFullyQualifiedName(member.getEnclosingElement()), name(member));
        }
    }
}
