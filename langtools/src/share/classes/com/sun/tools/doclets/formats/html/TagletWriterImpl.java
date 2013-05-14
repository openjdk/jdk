/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.doclets.formats.html.markup.ContentBuilder;
import com.sun.tools.doclets.formats.html.markup.HtmlAttr;
import com.sun.tools.doclets.formats.html.markup.HtmlStyle;
import com.sun.tools.doclets.formats.html.markup.HtmlTag;
import com.sun.tools.doclets.formats.html.markup.HtmlTree;
import com.sun.tools.doclets.formats.html.markup.RawHtml;
import com.sun.tools.doclets.formats.html.markup.StringContent;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.builders.SerializedFormBuilder;
import com.sun.tools.doclets.internal.toolkit.taglets.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * The taglet writer that writes HTML.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @since 1.5
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */

public class TagletWriterImpl extends TagletWriter {

    private final HtmlDocletWriter htmlWriter;
    private final ConfigurationImpl configuration;

    public TagletWriterImpl(HtmlDocletWriter htmlWriter, boolean isFirstSentence) {
        super(isFirstSentence);
        this.htmlWriter = htmlWriter;
        configuration = htmlWriter.configuration;
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput getOutputInstance() {
        return new TagletOutputImpl();
    }

    /**
     * {@inheritDoc}
     */
    protected TagletOutput codeTagOutput(Tag tag) {
        Content result = HtmlTree.CODE(new StringContent(tag.text()));
        return new TagletOutputImpl(result);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput getDocRootOutput() {
        if (configuration.docrootparent.length() > 0)
            return new TagletOutputImpl(configuration.docrootparent);
        else if (htmlWriter.pathToRoot.isEmpty())
            return new TagletOutputImpl(".");
        else
            return new TagletOutputImpl(htmlWriter.pathToRoot.getPath());
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput deprecatedTagOutput(Doc doc) {
        ContentBuilder result = new ContentBuilder();
        Tag[] deprs = doc.tags("deprecated");
        if (doc instanceof ClassDoc) {
            if (Util.isDeprecated((ProgramElementDoc) doc)) {
                result.addContent(HtmlTree.SPAN(HtmlStyle.strong,
                        new StringContent(configuration.getText("doclet.Deprecated"))));
                result.addContent(RawHtml.nbsp);
                if (deprs.length > 0) {
                    Tag[] commentTags = deprs[0].inlineTags();
                    if (commentTags.length > 0) {
                        result.addContent(commentTagsToOutput(null, doc,
                            deprs[0].inlineTags(), false).getContent()
                        );
                    }
                }
            }
        } else {
            MemberDoc member = (MemberDoc) doc;
            if (Util.isDeprecated((ProgramElementDoc) doc)) {
                result.addContent(HtmlTree.SPAN(HtmlStyle.strong,
                        new StringContent(configuration.getText("doclet.Deprecated"))));
                result.addContent(RawHtml.nbsp);
                if (deprs.length > 0) {
                    TagletOutputImpl body = commentTagsToOutput(null, doc,
                        deprs[0].inlineTags(), false);
                    result.addContent(HtmlTree.I(body.getContent()));
                }
            } else {
                if (Util.isDeprecated(member.containingClass())) {
                    result.addContent(HtmlTree.SPAN(HtmlStyle.strong,
                            new StringContent(configuration.getText("doclet.Deprecated"))));
                    result.addContent(RawHtml.nbsp);
                }
            }
        }
        return new TagletOutputImpl(result);
    }

    /**
     * {@inheritDoc}
     */
    protected TagletOutput expertTagOutput(Tag tag) {
        HtmlTree result = new HtmlTree(HtmlTag.SUB, new StringContent(tag.text()));
        result.addAttr(HtmlAttr.ID, "expert");
        return new TagletOutputImpl(result);
    }

    /**
     * {@inheritDoc}
     */
    protected TagletOutput literalTagOutput(Tag tag) {
        Content result = new StringContent(tag.text());
        return new TagletOutputImpl(result);
    }

    /**
     * {@inheritDoc}
     */
    public MessageRetriever getMsgRetriever() {
        return configuration.message;
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput getParamHeader(String header) {
        HtmlTree result = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.strong,
                new StringContent(header)));
        return new TagletOutputImpl(result);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput paramTagOutput(ParamTag paramTag, String paramName) {
        ContentBuilder body = new ContentBuilder();
        body.addContent(HtmlTree.CODE(new RawHtml(paramName)));
        body.addContent(" - ");
        body.addContent(new RawHtml(htmlWriter.commentTagsToString(paramTag, null, paramTag.inlineTags(), false)));
        HtmlTree result = HtmlTree.DD(body);
        return new TagletOutputImpl(result);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput returnTagOutput(Tag returnTag) {
        ContentBuilder result = new ContentBuilder();
        result.addContent(HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.strong,
                new StringContent(configuration.getText("doclet.Returns")))));
        result.addContent(HtmlTree.DD(new RawHtml(htmlWriter.commentTagsToString(
                returnTag, null, returnTag.inlineTags(), false))));
        return new TagletOutputImpl(result);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput seeTagOutput(Doc holder, SeeTag[] seeTags) {
        ContentBuilder body = new ContentBuilder();
        if (seeTags.length > 0) {
            for (int i = 0; i < seeTags.length; ++i) {
                appendSeparatorIfNotEmpty(body);
                body.addContent(new RawHtml(htmlWriter.seeTagToString(seeTags[i])));
            }
        }
        if (holder.isField() && ((FieldDoc)holder).constantValue() != null &&
                htmlWriter instanceof ClassWriterImpl) {
            //Automatically add link to constant values page for constant fields.
            appendSeparatorIfNotEmpty(body);
            DocPath constantsPath =
                    htmlWriter.pathToRoot.resolve(DocPaths.CONSTANT_VALUES);
            String whichConstant =
                    ((ClassWriterImpl) htmlWriter).getClassDoc().qualifiedName() + "." + ((FieldDoc) holder).name();
            DocLink link = constantsPath.fragment(whichConstant);
            body.addContent(htmlWriter.getHyperLink(link,
                    new StringContent(configuration.getText("doclet.Constants_Summary"))));
        }
        if (holder.isClass() && ((ClassDoc)holder).isSerializable()) {
            //Automatically add link to serialized form page for serializable classes.
            if ((SerializedFormBuilder.serialInclude(holder) &&
                      SerializedFormBuilder.serialInclude(((ClassDoc)holder).containingPackage()))) {
                appendSeparatorIfNotEmpty(body);
                DocPath serialPath = htmlWriter.pathToRoot.resolve(DocPaths.SERIALIZED_FORM);
                DocLink link = serialPath.fragment(((ClassDoc)holder).qualifiedName());
                body.addContent(htmlWriter.getHyperLink(link,
                        new StringContent(configuration.getText("doclet.Serialized_Form"))));
            }
        }
        if (body.isEmpty())
            return new TagletOutputImpl(body);

        ContentBuilder result = new ContentBuilder();
        result.addContent(HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.strong,
                new StringContent(configuration.getText("doclet.See_Also")))));
        result.addContent(HtmlTree.DD(body));
        return new TagletOutputImpl(result);

    }

    private void appendSeparatorIfNotEmpty(ContentBuilder body) {
        if (!body.isEmpty()) {
            body.addContent(", ");
            body.addContent(DocletConstants.NL);
        }
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput simpleTagOutput(Tag[] simpleTags, String header) {
        ContentBuilder result = new ContentBuilder();
        result.addContent(HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.strong, new RawHtml(header))));
        ContentBuilder body = new ContentBuilder();
        for (int i = 0; i < simpleTags.length; i++) {
            if (i > 0) {
                body.addContent(", ");
            }
            body.addContent(new RawHtml(htmlWriter.commentTagsToString(
                    simpleTags[i], null, simpleTags[i].inlineTags(), false)));
        }
        result.addContent(HtmlTree.DD(body));
        return new TagletOutputImpl(result);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput simpleTagOutput(Tag simpleTag, String header) {
        ContentBuilder result = new ContentBuilder();
        result.addContent(HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.strong, new RawHtml(header))));
        Content body = new RawHtml(htmlWriter.commentTagsToString(
                simpleTag, null, simpleTag.inlineTags(), false));
        result.addContent(HtmlTree.DD(body));
        return new TagletOutputImpl(result);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput getThrowsHeader() {
        HtmlTree result = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.strong,
                new StringContent(configuration.getText("doclet.Throws"))));
        return new TagletOutputImpl(result);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput throwsTagOutput(ThrowsTag throwsTag) {
        ContentBuilder body = new ContentBuilder();
        Content excName = (throwsTag.exceptionType() == null) ?
                new RawHtml(throwsTag.exceptionName()) :
                htmlWriter.getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.MEMBER,
                throwsTag.exceptionType()));
        body.addContent(HtmlTree.CODE(excName));
        String desc = htmlWriter.commentTagsToString(throwsTag, null,
            throwsTag.inlineTags(), false);
        if (desc != null && !desc.isEmpty()) {
            body.addContent(" - ");
            body.addContent(new RawHtml(desc));
        }
        HtmlTree res2 = HtmlTree.DD(body);
        return new TagletOutputImpl(res2);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput throwsTagOutput(Type throwsType) {
        HtmlTree result = HtmlTree.DD(HtmlTree.CODE(htmlWriter.getLink(
                new LinkInfoImpl(configuration, LinkInfoImpl.Kind.MEMBER, throwsType))));
        return new TagletOutputImpl(result);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput valueTagOutput(FieldDoc field, String constantVal,
            boolean includeLink) {
        return new TagletOutputImpl(includeLink ?
            htmlWriter.getDocLink(LinkInfoImpl.Kind.VALUE_TAG, field,
                constantVal, false) : new RawHtml(constantVal));
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutputImpl commentTagsToOutput(Tag holderTag, Tag[] tags) {
        return commentTagsToOutput(holderTag, null, tags, false);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutputImpl commentTagsToOutput(Doc holderDoc, Tag[] tags) {
        return commentTagsToOutput(null, holderDoc, tags, false);
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutputImpl commentTagsToOutput(Tag holderTag,
        Doc holderDoc, Tag[] tags, boolean isFirstSentence) {
        return new TagletOutputImpl(new RawHtml(htmlWriter.commentTagsToString(
            holderTag, holderDoc, tags, isFirstSentence)));
    }

    /**
     * {@inheritDoc}
     */
    public Configuration configuration() {
        return configuration;
    }
}
