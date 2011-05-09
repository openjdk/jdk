/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.builders.SerializedFormBuilder;
import com.sun.tools.doclets.internal.toolkit.taglets.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * The taglet writer that writes HTML.
 *
 * @since 1.5
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */

public class TagletWriterImpl extends TagletWriter {

    private HtmlDocletWriter htmlWriter;

    public TagletWriterImpl(HtmlDocletWriter htmlWriter, boolean isFirstSentence) {
        this.htmlWriter = htmlWriter;
        this.isFirstSentence = isFirstSentence;
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput getOutputInstance() {
        return new TagletOutputImpl("");
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput getDocRootOutput() {
        if (htmlWriter.configuration.docrootparent.length() > 0)
            return new TagletOutputImpl(htmlWriter.configuration.docrootparent);
        else
            return new TagletOutputImpl(htmlWriter.relativepathNoSlash);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput deprecatedTagOutput(Doc doc) {
        StringBuffer output = new StringBuffer();
        Tag[] deprs = doc.tags("deprecated");
        if (doc instanceof ClassDoc) {
            if (Util.isDeprecated((ProgramElementDoc) doc)) {
                output.append("<span class=\"strong\">" +
                    ConfigurationImpl.getInstance().
                        getText("doclet.Deprecated") + "</span>&nbsp;");
                if (deprs.length > 0) {
                    Tag[] commentTags = deprs[0].inlineTags();
                    if (commentTags.length > 0) {

                        output.append(commentTagsToOutput(null, doc,
                            deprs[0].inlineTags(), false).toString()
                        );
                    }
                }
            }
        } else {
            MemberDoc member = (MemberDoc) doc;
            if (Util.isDeprecated((ProgramElementDoc) doc)) {
                output.append("<span class=\"strong\">" +
                    ConfigurationImpl.getInstance().
                            getText("doclet.Deprecated") + "</span>&nbsp;");
                if (deprs.length > 0) {
                    output.append("<i>");
                    output.append(commentTagsToOutput(null, doc,
                        deprs[0].inlineTags(), false).toString());
                    output.append("</i>");
                }
            } else {
                if (Util.isDeprecated(member.containingClass())) {
                    output.append("<span class=\"strong\">" +
                    ConfigurationImpl.getInstance().
                            getText("doclet.Deprecated") + "</span>&nbsp;");
                }
            }
        }
        return new TagletOutputImpl(output.toString());
    }

    /**
     * {@inheritDoc}
     */
    public MessageRetriever getMsgRetriever() {
        return htmlWriter.configuration.message;
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput getParamHeader(String header) {
        StringBuffer result = new StringBuffer();
        result.append("<dt>");
        result.append("<span class=\"strong\">" +  header + "</span></dt>");
        return new TagletOutputImpl(result.toString());
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput paramTagOutput(ParamTag paramTag, String paramName) {
        TagletOutput result = new TagletOutputImpl("<dd><code>" + paramName + "</code>"
         + " - " + htmlWriter.commentTagsToString(paramTag, null, paramTag.inlineTags(), false) + "</dd>");
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput returnTagOutput(Tag returnTag) {
        TagletOutput result = new TagletOutputImpl(DocletConstants.NL + "<dt>" +
            "<span class=\"strong\">" + htmlWriter.configuration.getText("doclet.Returns") +
            "</span>" + "</dt>" + "<dd>" +
            htmlWriter.commentTagsToString(returnTag, null, returnTag.inlineTags(),
            false) + "</dd>");
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput seeTagOutput(Doc holder, SeeTag[] seeTags) {
        String result = "";
        if (seeTags.length > 0) {
            result = addSeeHeader(result);
            for (int i = 0; i < seeTags.length; ++i) {
                if (i > 0) {
                    result += ", " + DocletConstants.NL;
                }
                result += htmlWriter.seeTagToString(seeTags[i]);
            }
        }
        if (holder.isField() && ((FieldDoc)holder).constantValue() != null &&
                htmlWriter instanceof ClassWriterImpl) {
            //Automatically add link to constant values page for constant fields.
            result = addSeeHeader(result);
            result += htmlWriter.getHyperLinkString(htmlWriter.relativePath +
                ConfigurationImpl.CONSTANTS_FILE_NAME
                + "#" + ((ClassWriterImpl) htmlWriter).getClassDoc().qualifiedName()
                + "." + ((FieldDoc) holder).name(),
                htmlWriter.configuration.getText("doclet.Constants_Summary"));
        }
        if (holder.isClass() && ((ClassDoc)holder).isSerializable()) {
            //Automatically add link to serialized form page for serializable classes.
            if ((SerializedFormBuilder.serialInclude(holder) &&
                      SerializedFormBuilder.serialInclude(((ClassDoc)holder).containingPackage()))) {
                result = addSeeHeader(result);
                result += htmlWriter.getHyperLinkString(htmlWriter.relativePath + "serialized-form.html",
                        ((ClassDoc)holder).qualifiedName(), htmlWriter.configuration.getText("doclet.Serialized_Form"), false);
            }
        }
        return result.equals("") ? null : new TagletOutputImpl(result + "</dd>");
    }

    private String addSeeHeader(String result) {
        if (result != null && result.length() > 0) {
            return result + ", " + DocletConstants.NL;
        } else {
            return "<dt><span class=\"strong\">" +
                    htmlWriter.configuration().getText("doclet.See_Also") + "</span></dt><dd>";
        }
     }

    /**
     * {@inheritDoc}
     */
    public TagletOutput simpleTagOutput(Tag[] simpleTags, String header) {
        String result = "<dt><span class=\"strong\">" + header + "</span></dt>" + DocletConstants.NL +
            "  <dd>";
        for (int i = 0; i < simpleTags.length; i++) {
            if (i > 0) {
                result += ", ";
            }
            result += htmlWriter.commentTagsToString(simpleTags[i], null, simpleTags[i].inlineTags(), false);
        }
        result += "</dd>" + DocletConstants.NL;
        return new TagletOutputImpl(result);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput simpleTagOutput(Tag simpleTag, String header) {
        return new TagletOutputImpl("<dt><span class=\"strong\">" + header + "</span></dt>" + "  <dd>"
            + htmlWriter.commentTagsToString(simpleTag, null, simpleTag.inlineTags(), false)
            + "</dd>" + DocletConstants.NL);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput getThrowsHeader() {
        return new TagletOutputImpl(DocletConstants.NL + "<dt>" + "<span class=\"strong\">" +
            htmlWriter.configuration().getText("doclet.Throws") + "</span></dt>");
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput throwsTagOutput(ThrowsTag throwsTag) {
        String result = DocletConstants.NL + "<dd>";
        result += throwsTag.exceptionType() == null ?
            htmlWriter.codeText(throwsTag.exceptionName()) :
            htmlWriter.codeText(
                htmlWriter.getLink(new LinkInfoImpl(LinkInfoImpl.CONTEXT_MEMBER,
                throwsTag.exceptionType())));
        TagletOutput text = new TagletOutputImpl(
            htmlWriter.commentTagsToString(throwsTag, null,
            throwsTag.inlineTags(), false));
        if (text != null && text.toString().length() > 0) {
            result += " - " + text;
        }
        result += "</dd>";
        return new TagletOutputImpl(result);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput throwsTagOutput(Type throwsType) {
        return new TagletOutputImpl(DocletConstants.NL + "<dd>" +
            htmlWriter.codeText(htmlWriter.getLink(
                new LinkInfoImpl(LinkInfoImpl.CONTEXT_MEMBER, throwsType))) + "</dd>");
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput valueTagOutput(FieldDoc field, String constantVal,
            boolean includeLink) {
        return new TagletOutputImpl(includeLink ?
            htmlWriter.getDocLink(LinkInfoImpl.CONTEXT_VALUE_TAG, field,
                constantVal, false) : constantVal);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput commentTagsToOutput(Tag holderTag, Tag[] tags) {
        return commentTagsToOutput(holderTag, null, tags, false);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput commentTagsToOutput(Doc holderDoc, Tag[] tags) {
        return commentTagsToOutput(null, holderDoc, tags, false);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput commentTagsToOutput(Tag holderTag,
        Doc holderDoc, Tag[] tags, boolean isFirstSentence) {
        return new TagletOutputImpl(htmlWriter.commentTagsToString(
            holderTag, holderDoc, tags, isFirstSentence));
    }

    /**
     * {@inheritDoc}
     */
    public Configuration configuration() {
        return htmlWriter.configuration();
    }

    /**
     * Return an instance of a TagletWriter that knows how to write HTML.
     *
     * @return an instance of a TagletWriter that knows how to write HTML.
     */
    public TagletOutput getTagletOutputInstance() {
        return new TagletOutputImpl("");
    }
}
