/*
 * Copyright (c) 2003, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor9;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.IndexTree;
import com.sun.source.doctree.SystemPropertyTree;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletElement;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.builders.SerializedFormBuilder;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.DocletConstants;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * The taglet writer that writes HTML.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @author Bhavesh Patel (Modified)
 */

public class TagletWriterImpl extends TagletWriter {

    private final HtmlDocletWriter htmlWriter;
    private final HtmlConfiguration configuration;
    private final Utils utils;
    private final boolean inSummary;
    private final Resources resources;

    public TagletWriterImpl(HtmlDocletWriter htmlWriter, boolean isFirstSentence) {
        this(htmlWriter, isFirstSentence, false);
    }

    public TagletWriterImpl(HtmlDocletWriter htmlWriter, boolean isFirstSentence, boolean inSummary) {
        super(isFirstSentence);
        this.htmlWriter = htmlWriter;
        configuration = htmlWriter.configuration;
        this.utils = configuration.utils;
        this.inSummary = inSummary;
        resources = configuration.getResources();
    }

    /**
     * {@inheritDoc}
     */
    public Content getOutputInstance() {
        return new ContentBuilder();
    }

    /**
     * {@inheritDoc}
     */
    protected Content codeTagOutput(Element element, DocTree tag) {
        CommentHelper ch = utils.getCommentHelper(element);
        StringContent content = new StringContent(utils.normalizeNewlines(ch.getText(tag)));
        Content result = HtmlTree.CODE(content);
        return result;
    }

    protected Content indexTagOutput(Element element, DocTree tag) {
        CommentHelper ch = utils.getCommentHelper(element);
        IndexTree itt = (IndexTree)tag;

        String tagText =  ch.getText(itt.getSearchTerm());
        if (tagText.charAt(0) == '"' && tagText.charAt(tagText.length() - 1) == '"') {
            tagText = tagText.substring(1, tagText.length() - 1);
        }
        String desc = ch.getText(itt.getDescription());

        return createAnchorAndSearchIndex(element, tagText,desc);
    }

    /**
     * {@inheritDoc}
     */
    public Content getDocRootOutput() {
        String path;
        if (htmlWriter.pathToRoot.isEmpty())
            path = ".";
        else
            path = htmlWriter.pathToRoot.getPath();
        return new StringContent(path);
    }

    /**
     * {@inheritDoc}
     */
    public Content deprecatedTagOutput(Element element) {
        ContentBuilder result = new ContentBuilder();
        CommentHelper ch = utils.getCommentHelper(element);
        List<? extends DocTree> deprs = utils.getBlockTags(element, DocTree.Kind.DEPRECATED);
        if (utils.isTypeElement(element)) {
            if (utils.isDeprecated(element)) {
                result.add(HtmlTree.SPAN(HtmlStyle.deprecatedLabel,
                        htmlWriter.getDeprecatedPhrase(element)));
                if (!deprs.isEmpty()) {
                    List<? extends DocTree> commentTags = ch.getDescription(configuration, deprs.get(0));
                    if (!commentTags.isEmpty()) {
                        result.add(commentTagsToOutput(null, element, commentTags, false));
                    }
                }
            }
        } else {
            if (utils.isDeprecated(element)) {
                result.add(HtmlTree.SPAN(HtmlStyle.deprecatedLabel,
                        htmlWriter.getDeprecatedPhrase(element)));
                if (!deprs.isEmpty()) {
                    List<? extends DocTree> bodyTags = ch.getBody(configuration, deprs.get(0));
                    Content body = commentTagsToOutput(null, element, bodyTags, false);
                    if (!body.isEmpty())
                        result.add(HtmlTree.DIV(HtmlStyle.deprecationComment, body));
                }
            } else {
                Element ee = utils.getEnclosingTypeElement(element);
                if (utils.isDeprecated(ee)) {
                    result.add(HtmlTree.SPAN(HtmlStyle.deprecatedLabel,
                        htmlWriter.getDeprecatedPhrase(ee)));
                }
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    protected Content literalTagOutput(Element element, DocTree tag) {
        CommentHelper ch = utils.getCommentHelper(element);
        Content result = new StringContent(utils.normalizeNewlines(ch.getText(tag)));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public Content getParamHeader(String header) {
        HtmlTree result = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.paramLabel,
                new StringContent(header)));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public Content paramTagOutput(Element element, DocTree paramTag, String paramName) {
        ContentBuilder body = new ContentBuilder();
        CommentHelper ch = utils.getCommentHelper(element);
        body.add(HtmlTree.CODE(new RawHtml(paramName)));
        body.add(" - ");
        List<? extends DocTree> description = ch.getDescription(configuration, paramTag);
        body.add(htmlWriter.commentTagsToContent(paramTag, element, description, false, inSummary));
        HtmlTree result = HtmlTree.DD(body);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public Content propertyTagOutput(Element element, DocTree tag, String prefix) {
        Content body = new ContentBuilder();
        CommentHelper ch = utils.getCommentHelper(element);
        body.add(new RawHtml(prefix));
        body.add(" ");
        body.add(HtmlTree.CODE(new RawHtml(ch.getText(tag))));
        body.add(".");
        Content result = HtmlTree.P(body);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public Content returnTagOutput(Element element, DocTree returnTag) {
        ContentBuilder result = new ContentBuilder();
        CommentHelper ch = utils.getCommentHelper(element);
        result.add(HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.returnLabel,
                new StringContent(resources.getText("doclet.Returns")))));
        result.add(HtmlTree.DD(htmlWriter.commentTagsToContent(
                returnTag, element, ch.getDescription(configuration, returnTag), false, inSummary)));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public Content seeTagOutput(Element holder, List<? extends DocTree> seeTags) {
        ContentBuilder body = new ContentBuilder();
        for (DocTree dt : seeTags) {
            appendSeparatorIfNotEmpty(body);
            body.add(htmlWriter.seeTagToContent(holder, dt));
        }
        if (utils.isVariableElement(holder) && ((VariableElement)holder).getConstantValue() != null &&
                htmlWriter instanceof ClassWriterImpl) {
            //Automatically add link to constant values page for constant fields.
            appendSeparatorIfNotEmpty(body);
            DocPath constantsPath =
                    htmlWriter.pathToRoot.resolve(DocPaths.CONSTANT_VALUES);
            String whichConstant =
                    ((ClassWriterImpl) htmlWriter).getTypeElement().getQualifiedName() + "." +
                    utils.getSimpleName(holder);
            DocLink link = constantsPath.fragment(whichConstant);
            body.add(htmlWriter.links.createLink(link,
                    new StringContent(resources.getText("doclet.Constants_Summary"))));
        }
        if (utils.isClass(holder) && utils.isSerializable((TypeElement)holder)) {
            //Automatically add link to serialized form page for serializable classes.
            if (SerializedFormBuilder.serialInclude(utils, holder) &&
                      SerializedFormBuilder.serialInclude(utils, utils.containingPackage(holder))) {
                appendSeparatorIfNotEmpty(body);
                DocPath serialPath = htmlWriter.pathToRoot.resolve(DocPaths.SERIALIZED_FORM);
                DocLink link = serialPath.fragment(utils.getFullyQualifiedName(holder));
                body.add(htmlWriter.links.createLink(link,
                        new StringContent(resources.getText("doclet.Serialized_Form"))));
            }
        }
        if (body.isEmpty())
            return body;

        ContentBuilder result = new ContentBuilder();
        result.add(HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.seeLabel,
                new StringContent(resources.getText("doclet.See_Also")))));
        result.add(HtmlTree.DD(body));
        return result;

    }

    private void appendSeparatorIfNotEmpty(ContentBuilder body) {
        if (!body.isEmpty()) {
            body.add(", ");
            body.add(DocletConstants.NL);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Content simpleTagOutput(Element element, List<? extends DocTree> simpleTags, String header) {
        CommentHelper ch = utils.getCommentHelper(element);
        ContentBuilder result = new ContentBuilder();
        result.add(HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.simpleTagLabel, new RawHtml(header))));
        ContentBuilder body = new ContentBuilder();
        boolean many = false;
        for (DocTree simpleTag : simpleTags) {
            if (many) {
                body.add(", ");
            }
            List<? extends DocTree> bodyTags = ch.getBody(configuration, simpleTag);
            body.add(htmlWriter.commentTagsToContent(simpleTag, element, bodyTags, false, inSummary));
            many = true;
        }
        result.add(HtmlTree.DD(body));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public Content simpleTagOutput(Element element, DocTree simpleTag, String header) {
        ContentBuilder result = new ContentBuilder();
        result.add(HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.simpleTagLabel, new RawHtml(header))));
        CommentHelper ch = utils.getCommentHelper(element);
        List<? extends DocTree> description = ch.getDescription(configuration, simpleTag);
        Content body = htmlWriter.commentTagsToContent(simpleTag, element, description, false, inSummary);
        result.add(HtmlTree.DD(body));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    protected Content systemPropertyTagOutput(Element element, DocTree tag) {
        SystemPropertyTree itt = (SystemPropertyTree)tag;
        String tagText = itt.getPropertyName().toString();
        return HtmlTree.CODE(createAnchorAndSearchIndex(element, tagText,
                resources.getText("doclet.System_Property")));
    }

    /**
     * {@inheritDoc}
     */
    public Content getThrowsHeader() {
        HtmlTree result = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.throwsLabel,
                new StringContent(resources.getText("doclet.Throws"))));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public Content throwsTagOutput(Element element, DocTree throwsTag) {
        ContentBuilder body = new ContentBuilder();
        CommentHelper ch = utils.getCommentHelper(element);
        Element exception = ch.getException(configuration, throwsTag);
        Content excName;
        if (exception == null) {
            excName = new RawHtml(ch.getExceptionName(throwsTag).toString());
        } else if (exception.asType() == null) {
            excName = new RawHtml(utils.getFullyQualifiedName(exception));
        } else {
            LinkInfoImpl link = new LinkInfoImpl(configuration, LinkInfoImpl.Kind.MEMBER,
                                                 exception.asType());
            link.excludeTypeBounds = true;
            excName = htmlWriter.getLink(link);
        }
        body.add(HtmlTree.CODE(excName));
        List<? extends DocTree> description = ch.getDescription(configuration, throwsTag);
        Content desc = htmlWriter.commentTagsToContent(throwsTag, element, description, false, inSummary);
        if (desc != null && !desc.isEmpty()) {
            body.add(" - ");
            body.add(desc);
        }
        HtmlTree result = HtmlTree.DD(body);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public Content throwsTagOutput(TypeMirror throwsType) {
        HtmlTree result = HtmlTree.DD(HtmlTree.CODE(htmlWriter.getLink(
                new LinkInfoImpl(configuration, LinkInfoImpl.Kind.MEMBER, throwsType))));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public Content valueTagOutput(VariableElement field, String constantVal, boolean includeLink) {
        return includeLink
                ? htmlWriter.getDocLink(LinkInfoImpl.Kind.VALUE_TAG, field, constantVal, false)
                : new StringContent(constantVal);
    }

    /**
     * {@inheritDoc}
     */
    public Content commentTagsToOutput(DocTree holderTag, List<? extends DocTree> tags) {
        return commentTagsToOutput(holderTag, null, tags, false);
    }

    /**
     * {@inheritDoc}
     */
    public Content commentTagsToOutput(Element holder, List<? extends DocTree> tags) {
        return commentTagsToOutput(null, holder, tags, false);
    }

    /**
     * {@inheritDoc}
     */
    public Content commentTagsToOutput(DocTree holderTag,
        Element holder, List<? extends DocTree> tags, boolean isFirstSentence) {
        return htmlWriter.commentTagsToContent(holderTag, holder,
                tags, isFirstSentence, inSummary);
    }

    /**
     * {@inheritDoc}
     */
    public BaseConfiguration configuration() {
        return configuration;
    }

    private Content createAnchorAndSearchIndex(Element element, String tagText, String desc){
        String anchorName = htmlWriter.links.getName(tagText);
        Content result = null;
        if (isFirstSentence && inSummary) {
            result = new StringContent(tagText);
        } else {
            result = HtmlTree.A_ID(HtmlStyle.searchTagResult, anchorName, new StringContent(tagText));
            if (configuration.createindex && !tagText.isEmpty()) {
                SearchIndexItem si = new SearchIndexItem();
                si.setLabel(tagText);
                si.setDescription(desc);
                si.setUrl(htmlWriter.path.getPath() + "#" + anchorName);
                DocPaths docPaths = configuration.docPaths;
                new SimpleElementVisitor9<Void, Void>() {
                    @Override
                    public Void visitVariable(VariableElement e, Void p) {
                        TypeElement te = utils.getEnclosingTypeElement(e);
                        si.setHolder(utils.getFullyQualifiedName(e) + "." + utils.getSimpleName(e));
                        return null;
                    }

                    @Override
                    public Void visitUnknown(Element e, Void p) {
                        if (e instanceof DocletElement) {
                            DocletElement de = (DocletElement) e;
                            switch (de.getSubKind()) {
                                case OVERVIEW:
                                    si.setHolder(resources.getText("doclet.Overview"));
                                    break;
                                case DOCFILE:
                                    si.setHolder(de.getPackageElement().toString());
                                    break;
                                default:
                                    throw new IllegalStateException();
                            }
                            return null;
                        } else {
                            return super.visitUnknown(e, p);
                        }
                    }

                    @Override
                    protected Void defaultAction(Element e, Void p) {
                        si.setHolder(utils.getFullyQualifiedName(e));
                        return null;
                    }
                }.visit(element);
                si.setCategory(SearchIndexItem.Category.SEARCH_TAGS);
                configuration.tagSearchIndex.add(si);
            }
        }
        return result;
    }
}
