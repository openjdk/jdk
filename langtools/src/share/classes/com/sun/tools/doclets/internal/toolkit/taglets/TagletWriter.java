/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.javadoc.*;

/**
 * The interface for the taglet writer.
 *
 * @since 1.5
 * @author Jamie Ho
 */

public abstract class TagletWriter {

    /**
     * True if we only want to write the first sentence.
     */
    protected boolean isFirstSentence = false;

    /**
     * @return an instance of the output object.
     */
    public abstract TagletOutput getOutputInstance();

    /**
     * Returns the output for the DocRoot inline tag.
     * @return the output for the DocRoot inline tag.
     */
    protected abstract TagletOutput getDocRootOutput();

    /**
     * Return the deprecated tag output.
     *
     * @param doc the doc to write deprecated documentation for.
     * @return the output of the deprecated tag.
     */
    protected abstract TagletOutput deprecatedTagOutput(Doc doc);

    /**
     * Returns {@link MessageRetriever} for output purposes.
     *
     * @return {@link MessageRetriever} for output purposes.
     */
    protected abstract MessageRetriever getMsgRetriever();

    /**
     * Return the header for the param tags.
     *
     * @param header the header to display.
     * @return the header for the param tags.
     */
    protected abstract TagletOutput getParamHeader(String header);

    /**
     * Return the output for param tags.
     *
     * @param paramTag the parameter to document.
     * @param paramName the name of the parameter.
     * @return the output of the param tag.
     */
    protected abstract TagletOutput paramTagOutput(ParamTag paramTag,
        String paramName);

    /**
     * Return the return tag output.
     *
     * @param returnTag the return tag to output.
     * @return the output of the return tag.
     */
    protected abstract TagletOutput returnTagOutput(Tag returnTag);

    /**
     * Return the see tag output.
     *
     * @param seeTags the array of See tags.
     * @return the output of the see tags.
     */
    protected abstract TagletOutput seeTagOutput(Doc holder, SeeTag[] seeTags);

    /**
     * Return the output for a simple tag.
     *
     * @param simpleTags the array of simple tags.
     * @return the output of the simple tags.
     */
    protected abstract TagletOutput simpleTagOutput(Tag[] simpleTags,
        String header);

    /**
     * Return the output for a simple tag.
     *
     * @param simpleTag the simple tag.
     * @return the output of the simple tag.
     */
    protected abstract TagletOutput simpleTagOutput(Tag simpleTag, String header);

    /**
     * Return the header for the throws tag.
     *
     * @return the header for the throws tag.
     */
    protected abstract TagletOutput getThrowsHeader();

    /**
     * Return the header for the throws tag.
     *
     * @param throwsTag the throws tag.
     * @return the output of the throws tag.
     */
    protected abstract TagletOutput throwsTagOutput(ThrowsTag throwsTag);

    /**
     * Return the output for the throws tag.
     *
     * @param throwsType the throws type.
     * @return the output of the throws type.
     */
    protected abstract TagletOutput throwsTagOutput(Type throwsType);

    /**
     * Return the output for the value tag.
     *
     * @param field       the constant field that holds the value tag.
     * @param constantVal the constant value to document.
     * @param includeLink true if we should link the constant text to the
     *                    constant field itself.
     * @return the output of the value tag.
     */
    protected abstract TagletOutput valueTagOutput(FieldDoc field,
        String constantVal, boolean includeLink);

    /**
     * Given an output object, append to it the tag documentation for
     * the given member.
     *
     * @param tagletManager the manager that manages the taglets.
     * @param doc the Doc that we are print tags for.
     * @param taglets the taglets to print.
     * @param writer the writer that will generate the output strings.
     * @param output the output buffer to store the output in.
     */
    public static void genTagOuput(TagletManager tagletManager, Doc doc,
            Taglet[] taglets, TagletWriter writer, TagletOutput output) {
        tagletManager.checkTags(doc, doc.tags(), false);
        tagletManager.checkTags(doc, doc.inlineTags(), true);
        TagletOutput currentOutput = null;
        for (int i = 0; i < taglets.length; i++) {
            if (doc instanceof ClassDoc && taglets[i] instanceof ParamTaglet) {
                //The type parameters are documented in a special section away
                //from the tag info, so skip here.
                continue;
            }
            if (taglets[i] instanceof DeprecatedTaglet) {
                //Deprecated information is documented "inline", not in tag info
                //section.
                continue;
            }
            try {
                currentOutput = taglets[i].getTagletOutput(doc, writer);
            } catch (IllegalArgumentException e) {
                //The taglet does not take a member as an argument.  Let's try
                //a single tag.
                Tag[] tags = doc.tags(taglets[i].getName());
                if (tags.length > 0) {
                    currentOutput = taglets[i].getTagletOutput(tags[0], writer);
                }
            }
            if (currentOutput != null) {
                tagletManager.seenCustomTag(taglets[i].getName());
                output.appendOutput(currentOutput);
            }
        }
    }

    /**
     * Given an inline tag, return its output.
     * @param tagletManager The taglet manager for the current doclet.
     * @param holderTag The tag this holds this inline tag.  Null if there
     * is no tag that holds it.
     * @param inlineTag The inline tag to be documented.
     * @param tagletWriter The taglet writer to write the output.
     * @return The output of the inline tag.
     */
    public static TagletOutput getInlineTagOuput(TagletManager tagletManager,
            Tag holderTag, Tag inlineTag, TagletWriter tagletWriter) {
        Taglet[] definedTags = tagletManager.getInlineCustomTags();
        //This is a custom inline tag.
        for (int j = 0; j < definedTags.length; j++) {
            if (("@"+definedTags[j].getName()).equals(inlineTag.name())) {
                //Given a name of a seen custom tag, remove it from the
                // set of unseen custom tags.
                tagletManager.seenCustomTag(definedTags[j].getName());
                TagletOutput output = definedTags[j].getTagletOutput(
                    holderTag != null &&
                        definedTags[j].getName().equals("inheritDoc") ?
                            holderTag : inlineTag, tagletWriter);
                return output;
            }
        }
        return null;
    }

    /**
     * Converts inline tags and text to TagOutput, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to non-inline tags.
     *
     * @param holderTag the tag that holds the documentation.
     * @param tags   array of text tags and inline tags (often alternating)
     *               present in the text of interest for this doc.
     * @return the {@link TagletOutput} representing the comments.
     */
    public abstract TagletOutput commentTagsToOutput(Tag holderTag, Tag[] tags);

    /**
     * Converts inline tags and text to TagOutput, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to non-inline tags.
     *
     * @param holderDoc specific doc where comment resides.
     * @param tags   array of text tags and inline tags (often alternating)
     *               present in the text of interest for this doc.
     * @return the {@link TagletOutput} representing the comments.
     */
    public abstract TagletOutput commentTagsToOutput(Doc holderDoc, Tag[] tags);

    /**
     * Converts inline tags and text to TagOutput, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to non-inline tags.
     *
     * @param holderTag the tag that holds the documentation.
     * @param holderDoc specific doc where comment resides.
     * @param tags   array of text tags and inline tags (often alternating)
     *               present in the text of interest for this doc.
     * @param isFirstSentence true if this is the first sentence.
     * @return the {@link TagletOutput} representing the comments.
     */
    public abstract TagletOutput commentTagsToOutput(Tag holderTag,
        Doc holderDoc, Tag[] tags, boolean isFirstSentence);

    /**
     * @return an instance of the configuration used for this doclet.
     */
    public abstract Configuration configuration();

    /**
     * @return an instance of the taglet output object.
     */
    public abstract TagletOutput getTagletOutputInstance();
}
