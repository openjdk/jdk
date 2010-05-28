/*
 * Copyright (c) 2001, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.taglets;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import java.util.*;

/**
 * A taglet that represents the @param tag.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.4
 */
public class ParamTaglet extends BaseTaglet implements InheritableTaglet {

    /**
     * Construct a ParamTaglet.
     */
    public ParamTaglet() {
        name = "param";
    }

    /**
     * Given an array of <code>Parameter</code>s, return
     * a name/rank number map.  If the array is null, then
     * null is returned.
     * @param params The array of parmeters (from type or executable member) to
     *               check.
     * @return a name-rank number map.
     */
    private static Map<String,String> getRankMap(Object[] params){
        if (params == null) {
            return null;
        }
        HashMap<String,String> result = new HashMap<String,String>();
        for (int i = 0; i < params.length; i++) {
            String name = params[i] instanceof Parameter ?
                ((Parameter) params[i]).name() :
                ((TypeVariable) params[i]).typeName();
            result.put(name, String.valueOf(i));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        if (input.tagId == null) {
            input.isTypeVariableParamTag = ((ParamTag) input.tag).isTypeParameter();
            Object[] parameters = input.isTypeVariableParamTag ?
                (Object[]) ((MethodDoc) input.tag.holder()).typeParameters() :
                (Object[]) ((MethodDoc) input.tag.holder()).parameters();
            String target = ((ParamTag) input.tag).parameterName();
            int i;
            for (i = 0; i < parameters.length; i++) {
                String name = parameters[i] instanceof Parameter ?
                    ((Parameter) parameters[i]).name() :
                    ((TypeVariable) parameters[i]).typeName();
                if (name.equals(target)) {
                    input.tagId = String.valueOf(i);
                    break;
                }
            }
            if (i == parameters.length) {
                //Someone used {@inheritDoc} on an invalid @param tag.
                //We don't know where to inherit from.
                //XXX: in the future when Configuration is available here,
                //print a warning for this mistake.
                return;
            }
        }
        ParamTag[] tags = input.isTypeVariableParamTag ?
            input.method.typeParamTags() : input.method.paramTags();
        Map<String, String> rankMap = getRankMap(input.isTypeVariableParamTag ?
            (Object[]) input.method.typeParameters() :
            (Object[]) input.method.parameters());
        for (int i = 0; i < tags.length; i++) {
            if (rankMap.containsKey(tags[i].parameterName()) &&
                    rankMap.get(tags[i].parameterName()).equals((input.tagId))) {
                output.holder = input.method;
                output.holderTag = tags[i];
                output.inlineTags = input.isFirstSentence ?
                    tags[i].firstSentenceTags() : tags[i].inlineTags();
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
    public TagletOutput getTagletOutput(Doc holder, TagletWriter writer) {
        if (holder instanceof ExecutableMemberDoc) {
            ExecutableMemberDoc member = (ExecutableMemberDoc) holder;
            TagletOutput output = getTagletOutput(false, member, writer,
                member.typeParameters(), member.typeParamTags());
            output.appendOutput(getTagletOutput(true, member, writer,
                member.parameters(), member.paramTags()));
            return output;
        } else {
            ClassDoc classDoc = (ClassDoc) holder;
            return getTagletOutput(false, classDoc, writer,
                classDoc.typeParameters(), classDoc.typeParamTags());
        }
    }

    /**
     * Given an array of <code>ParamTag</code>s,return its string representation.
     * Try to inherit the param tags that are missing.
     *
     * @param doc               the doc that holds the param tags.
     * @param writer            the TagletWriter that will write this tag.
     * @param formalParameters  The array of parmeters (from type or executable
     *                          member) to check.
     *
     * @return the TagletOutput representation of these <code>ParamTag</code>s.
     */
    private TagletOutput getTagletOutput(boolean isNonTypeParams, Doc holder,
            TagletWriter writer, Object[] formalParameters, ParamTag[] paramTags) {
        TagletOutput result = writer.getOutputInstance();
        Set<String> alreadyDocumented = new HashSet<String>();
        if (paramTags.length > 0) {
            result.appendOutput(
                processParamTags(isNonTypeParams, paramTags,
                getRankMap(formalParameters), writer, alreadyDocumented)
            );
        }
        if (alreadyDocumented.size() != formalParameters.length) {
            //Some parameters are missing corresponding @param tags.
            //Try to inherit them.
            result.appendOutput(getInheritedTagletOutput (isNonTypeParams, holder,
                writer, formalParameters, alreadyDocumented));
        }
        return result;
    }

    /**
     * Loop through each indivitual parameter.  It it does not have a
     * corresponding param tag, try to inherit it.
     */
    private TagletOutput getInheritedTagletOutput(boolean isNonTypeParams, Doc holder,
            TagletWriter writer, Object[] formalParameters,
            Set<String> alreadyDocumented) {
        TagletOutput result = writer.getOutputInstance();
        if ((! alreadyDocumented.contains(null)) &&
                holder instanceof MethodDoc) {
            for (int i = 0; i < formalParameters.length; i++) {
                if (alreadyDocumented.contains(String.valueOf(i))) {
                    continue;
                }
                //This parameter does not have any @param documentation.
                //Try to inherit it.
                DocFinder.Output inheritedDoc =
                    DocFinder.search(new DocFinder.Input((MethodDoc) holder, this,
                        String.valueOf(i), ! isNonTypeParams));
                if (inheritedDoc.inlineTags != null &&
                        inheritedDoc.inlineTags.length > 0) {
                    result.appendOutput(
                        processParamTag(isNonTypeParams, writer,
                            (ParamTag) inheritedDoc.holderTag,
                            isNonTypeParams ?
                                ((Parameter) formalParameters[i]).name():
                                ((TypeVariable) formalParameters[i]).typeName(),
                            alreadyDocumented.size() == 0));
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
     * @param nameMap a {@link java.util.Map} which holds a mapping
     *                of a rank of a parameter to its name.  This is
     *                used to ensure that the right name is used
     *                when parameter documentation is inherited.
     * @return the TagletOutput representation of this <code>Tag</code>.
     */
    private TagletOutput processParamTags(boolean isNonTypeParams,
            ParamTag[] paramTags, Map<String, String> rankMap, TagletWriter writer,
            Set<String> alreadyDocumented) {
        TagletOutput result = writer.getOutputInstance();
        if (paramTags.length > 0) {
            for (int i = 0; i < paramTags.length; ++i) {
                ParamTag pt = paramTags[i];
                String paramName = isNonTypeParams ?
                    pt.parameterName() : "<" + pt.parameterName() + ">";
                if (! rankMap.containsKey(pt.parameterName())) {
                    writer.getMsgRetriever().warning(pt.position(),
                        isNonTypeParams ?
                            "doclet.Parameters_warn" :
                            "doclet.Type_Parameters_warn",
                        paramName);
                }
                String rank = rankMap.get(pt.parameterName());
                if (rank != null && alreadyDocumented.contains(rank)) {
                    writer.getMsgRetriever().warning(pt.position(),
                       isNonTypeParams ?
                           "doclet.Parameters_dup_warn" :
                           "doclet.Type_Parameters_dup_warn",
                       paramName);
                }
                result.appendOutput(processParamTag(isNonTypeParams, writer, pt,
                     pt.parameterName(), alreadyDocumented.size() == 0));
                alreadyDocumented.add(rank);
            }
        }
        return result;
    }
    /**
     * Convert the individual ParamTag into TagletOutput.
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
    private TagletOutput processParamTag(boolean isNonTypeParams,
            TagletWriter writer, ParamTag paramTag, String name,
            boolean isFirstParam) {
        TagletOutput result = writer.getOutputInstance();
        String header = writer.configuration().getText(
            isNonTypeParams ? "doclet.Parameters" : "doclet.TypeParameters");
        if (isFirstParam) {
            result.appendOutput(writer.getParamHeader(header));
        }
        result.appendOutput(writer.paramTagOutput(paramTag,
            name));
        return result;
    }
}
