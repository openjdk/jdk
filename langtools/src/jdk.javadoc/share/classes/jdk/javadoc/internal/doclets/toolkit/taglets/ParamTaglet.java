/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets;

import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder.Input;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

import static com.sun.source.doctree.DocTree.Kind.*;

/**
 * A taglet that represents the @param tag.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 */
public class ParamTaglet extends BaseTaglet implements InheritableTaglet {

    /**
     * Construct a ParamTaglet.
     */
    public ParamTaglet() {
        name = PARAM.tagName;
    }

    /**
     * Given an array of <code>Parameter</code>s, return
     * a name/rank number map.  If the array is null, then
     * null is returned.
     * @param params The array of parameters (from type or executable member) to
     *               check.
     * @return a name-rank number map.
     */
    private static Map<String, String> getRankMap(Utils utils, List<? extends Element> params){
        if (params == null) {
            return null;
        }
        HashMap<String, String> result = new HashMap<>();
        int rank = 0;
        for (Element e : params) {
            String name = utils.isTypeParameterElement(e)
                    ? utils.getTypeName(e.asType(), false)
                    : utils.getSimpleName(e);
            result.put(name, String.valueOf(rank));
            rank++;
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        Utils utils = input.utils;
        if (input.tagId == null) {
            input.isTypeVariableParamTag = ((ParamTree)input.docTreeInfo.docTree).isTypeParameter();
            ExecutableElement ee = (ExecutableElement)input.docTreeInfo.element;
            CommentHelper ch = utils.getCommentHelper(ee);
            List<? extends Element> parameters = input.isTypeVariableParamTag
                    ? ee.getTypeParameters()
                    : ee.getParameters();
            String target = ch.getParameterName(input.docTreeInfo.docTree);
            for (int i = 0 ; i < parameters.size(); i++) {
                Element e = parameters.get(i);
                String pname = input.isTypeVariableParamTag
                        ? utils.getTypeName(e.asType(), false)
                        : utils.getSimpleName(e);
                if (pname.equals(target)) {
                    input.tagId = String.valueOf(i);
                    break;
                }
            }
        }
        ExecutableElement md = (ExecutableElement)input.element;
        CommentHelper ch = utils.getCommentHelper(md);
        List<? extends DocTree> tags = input.isTypeVariableParamTag
                ? utils.getTypeParamTrees(md)
                : utils.getParamTrees(md);
        List<? extends Element> parameters = input.isTypeVariableParamTag
                ? md.getTypeParameters()
                : md.getParameters();
        Map<String, String> rankMap = getRankMap(utils, parameters);
        for (DocTree tag : tags) {
            String paramName = ch.getParameterName(tag);
            if (rankMap.containsKey(paramName) && rankMap.get(paramName).equals((input.tagId))) {
                output.holder = input.element;
                output.holderTag = tag;
                output.inlineTags = ch.getBody(utils.configuration, tag);
                return;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean inField() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean inMethod() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean inOverview() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean inPackage() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean inType() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isInlineTag() {
        return false;
    }

    /**
     * Given an array of <code>ParamTag</code>s,return its string representation.
     * @param holder the member that holds the param tags.
     * @param writer the TagletWriter that will write this tag.
     * @return the TagletOutput representation of these <code>ParamTag</code>s.
     */
    public Content getTagletOutput(Element holder, TagletWriter writer) {
        Utils utils = writer.configuration().utils;
        if (utils.isExecutableElement(holder)) {
            ExecutableElement member = (ExecutableElement) holder;
            Content output = getTagletOutput(false, member, writer,
                member.getTypeParameters(), utils.getTypeParamTrees(member));
            output.addContent(getTagletOutput(true, member, writer,
                member.getParameters(), utils.getParamTrees(member)));
            return output;
        } else {
            TypeElement typeElement = (TypeElement) holder;
            return getTagletOutput(false, typeElement, writer,
                typeElement.getTypeParameters(), utils.getTypeParamTrees(typeElement));
        }
    }

    /**
     * Given an array of <code>ParamTag</code>s,return its string representation.
     * Try to inherit the param tags that are missing.
     *
     * @param holder            the element that holds the param tags.
     * @param writer            the TagletWriter that will write this tag.
     * @param formalParameters  The array of parmeters (from type or executable
     *                          member) to check.
     *
     * @return the TagletOutput representation of these <code>ParamTag</code>s.
     */
    private Content getTagletOutput(boolean isParameters, Element holder,
            TagletWriter writer, List<? extends Element> formalParameters, List<? extends DocTree> paramTags) {
        Content result = writer.getOutputInstance();
        Set<String> alreadyDocumented = new HashSet<>();
        if (!paramTags.isEmpty()) {
            result.addContent(
                processParamTags(holder, isParameters, paramTags,
                getRankMap(writer.configuration().utils, formalParameters), writer, alreadyDocumented)
            );
        }
        if (alreadyDocumented.size() != formalParameters.size()) {
            //Some parameters are missing corresponding @param tags.
            //Try to inherit them.
            result.addContent(getInheritedTagletOutput(isParameters, holder,
                writer, formalParameters, alreadyDocumented));
        }
        return result;
    }

    /**
     * Loop through each individual parameter, despite not having a
     * corresponding param tag, try to inherit it.
     */
    private Content getInheritedTagletOutput(boolean isParameters, Element holder,
            TagletWriter writer, List<? extends Element> formalParameters,
            Set<String> alreadyDocumented) {
        Utils utils = writer.configuration().utils;
        Content result = writer.getOutputInstance();
        if ((!alreadyDocumented.contains(null)) && utils.isExecutableElement(holder)) {
            for (int i = 0; i < formalParameters.size(); i++) {
                if (alreadyDocumented.contains(String.valueOf(i))) {
                    continue;
                }
                // This parameter does not have any @param documentation.
                // Try to inherit it.
                Input input = new DocFinder.Input(writer.configuration().utils, holder, this,
                        Integer.toString(i), !isParameters);
                DocFinder.Output inheritedDoc = DocFinder.search(writer.configuration(), input);
                if (inheritedDoc.inlineTags != null && !inheritedDoc.inlineTags.isEmpty()) {
                    Element e = formalParameters.get(i);
                    String lname = isParameters
                            ? utils.getSimpleName(e)
                            : utils.getTypeName(e.asType(), false);
                    CommentHelper ch = utils.getCommentHelper(holder);
                    ch.setOverrideElement(inheritedDoc.holder);
                    Content content = processParamTag(holder, isParameters, writer,
                            inheritedDoc.holderTag,
                            lname,
                            alreadyDocumented.isEmpty());
                    result.addContent(content);
                }
                alreadyDocumented.add(String.valueOf(i));
            }
        }
        return result;
    }

    /**
     * Given an array of <code>Tag</code>s representing this custom
     * tag, return its string representation.  Print a warning for param
     * tags that do not map to parameters.  Print a warning for param
     * tags that are duplicated.
     *
     * @param paramTags the array of <code>ParamTag</code>s to convert.
     * @param writer the TagletWriter that will write this tag.
     * @param alreadyDocumented the set of exceptions that have already
     *        been documented.
     * @param rankMap a {@link java.util.Map} which holds ordering
     *                    information about the parameters.
     * @param rankMap a {@link java.util.Map} which holds a mapping
                of a rank of a parameter to its name.  This is
                used to ensure that the right name is used
                when parameter documentation is inherited.
     * @return the Content representation of this <code>Tag</code>.
     */
    private Content processParamTags(Element e, boolean isParams,
            List<? extends DocTree> paramTags, Map<String, String> rankMap, TagletWriter writer,
            Set<String> alreadyDocumented) {
        Content result = writer.getOutputInstance();
        if (!paramTags.isEmpty()) {
            CommentHelper ch = writer.configuration().utils.getCommentHelper(e);
            for (DocTree dt : paramTags) {
                String paramName = isParams
                        ? ch.getParameterName(dt)
                        : "<" + ch.getParameterName(dt) + ">";
                if (!rankMap.containsKey(ch.getParameterName(dt))) {
                    writer.getMsgRetriever().warning(ch.getDocTreePath(dt),
                                                     isParams ?
                                                     "doclet.Parameters_warn" :
                                                     "doclet.Type_Parameters_warn",
                                                     paramName);
                }
                String rank = rankMap.get(ch.getParameterName(dt));
                if (rank != null && alreadyDocumented.contains(rank)) {
                    writer.getMsgRetriever().warning(ch.getDocTreePath(dt),
                                                     isParams ?
                                                     "doclet.Parameters_dup_warn" :
                                                     "doclet.Type_Parameters_dup_warn",
                                                     paramName);
                }
                result.addContent(processParamTag(e, isParams, writer, dt,
                                                  ch.getParameterName(dt), alreadyDocumented.isEmpty()));
                alreadyDocumented.add(rank);
            }
        }
        return result;
    }

    /**
     * Convert the individual ParamTag into Content.
     *
     * @param isNonTypeParams true if this is just a regular param tag.  False
     *                        if this is a type param tag.
     * @param writer          the taglet writer for output writing.
     * @param paramTag        the tag whose inline tags will be printed.
     * @param name            the name of the parameter.  We can't rely on
     *                        the name in the param tag because we might be
     *                        inheriting documentation.
     * @param isFirstParam    true if this is the first param tag being printed.
     *
     */
    private Content processParamTag(Element e, boolean isParams,
            TagletWriter writer, DocTree paramTag, String name,
            boolean isFirstParam) {
        Content result = writer.getOutputInstance();
        String header = writer.configuration().getText(
            isParams ? "doclet.Parameters" : "doclet.TypeParameters");
        if (isFirstParam) {
            result.addContent(writer.getParamHeader(header));
        }
        result.addContent(writer.paramTagOutput(e, paramTag, name));
        return result;
    }
}
