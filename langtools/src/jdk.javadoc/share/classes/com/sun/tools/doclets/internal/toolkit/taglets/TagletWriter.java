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

package com.sun.tools.doclets.internal.toolkit.taglets;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * The interface for the taglet writer.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @since 1.5
 * @author Jamie Ho
 */

public abstract class TagletWriter {

    /**
     * True if we only want to write the first sentence.
     */
    protected final boolean isFirstSentence;

    protected TagletWriter(boolean isFirstSentence) {
        this.isFirstSentence = isFirstSentence;
    }

    /**
     * @return an instance of an output object.
     */
    public abstract Content getOutputInstance();

    /**
     * Return the output for a {@code {@code ...}} tag.
     *
     * @param tag the tag.
     * @return the output of the taglet.
     */
    protected abstract Content codeTagOutput(Tag tag);

    /**
     * Return the output for a {@index...} tag.
     *
     * @param tag the tag.
     * @return the output of the taglet.
     */
    protected abstract Content indexTagOutput(Tag tag);

    /**
     * Returns the output for the DocRoot inline tag.
     * @return the output for the DocRoot inline tag.
     */
    protected abstract Content getDocRootOutput();

    /**
     * Return the deprecated tag output.
     *
     * @param doc the doc to write deprecated documentation for.
     * @return the output of the deprecated tag.
     */
    protected abstract Content deprecatedTagOutput(Doc doc);

    /**
     * Return the output for a {@code {@literal ...}} tag.
     *
     * @param tag the tag.
     * @return the output of the taglet.
     */
    protected abstract Content literalTagOutput(Tag tag);

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
    protected abstract Content getParamHeader(String header);

    /**
     * Return the output for param tags.
     *
     * @param paramTag the parameter to document.
     * @param paramName the name of the parameter.
     * @return the output of the param tag.
     */
    protected abstract Content paramTagOutput(ParamTag paramTag,
        String paramName);

    /**
     * Return the output for property tags.
     *
     * @param propertyTag the parameter to document.
     * @param prefix the text with which to prefix the property name.
     * @return the output of the param tag.
     */
    protected abstract Content propertyTagOutput(Tag propertyTag, String prefix);

    /**
     * Return the return tag output.
     *
     * @param returnTag the return tag to output.
     * @return the output of the return tag.
     */
    protected abstract Content returnTagOutput(Tag returnTag);

    /**
     * Return the see tag output.
     *
     * @param seeTags the array of See tags.
     * @return the output of the see tags.
     */
    protected abstract Content seeTagOutput(Doc holder, SeeTag[] seeTags);

    /**
     * Return the output for a simple tag.
     *
     * @param simpleTags the array of simple tags.
     * @return the output of the simple tags.
     */
    protected abstract Content simpleTagOutput(Tag[] simpleTags,
        String header);

    /**
     * Return the output for a simple tag.
     *
     * @param simpleTag the simple tag.
     * @return the output of the simple tag.
     */
    protected abstract Content simpleTagOutput(Tag simpleTag, String header);

    /**
     * Return the header for the throws tag.
     *
     * @return the header for the throws tag.
     */
    protected abstract Content getThrowsHeader();

    /**
     * Return the header for the throws tag.
     *
     * @param throwsTag the throws tag.
     * @return the output of the throws tag.
     */
    protected abstract Content throwsTagOutput(ThrowsTag throwsTag);

    /**
     * Return the output for the throws tag.
     *
     * @param throwsType the throws type.
     * @return the output of the throws type.
     */
    protected abstract Content throwsTagOutput(Type throwsType);

    /**
     * Return the output for the value tag.
     *
     * @param field       the constant field that holds the value tag.
     * @param constantVal the constant value to document.
     * @param includeLink true if we should link the constant text to the
     *                    constant field itself.
     * @return the output of the value tag.
     */
    protected abstract Content valueTagOutput(FieldDoc field,
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
            Taglet[] taglets, TagletWriter writer, Content output) {
        tagletManager.checkTags(doc, doc.tags(), false);
        tagletManager.checkTags(doc, doc.inlineTags(), true);
        Content currentOutput = null;
        for (Taglet taglet : taglets) {
            currentOutput = null;
            if (doc instanceof ClassDoc && taglet instanceof ParamTaglet) {
                //The type parameters are documented in a special section away
                //from the tag info, so skip here.
                continue;
            }
            if (taglet instanceof DeprecatedTaglet) {
                //Deprecated information is documented "inline", not in tag info
                //section.
                continue;
            }
            try {
                currentOutput = taglet.getTagletOutput(doc, writer);
            } catch (IllegalArgumentException e) {
                //The taglet does not take a member as an argument.  Let's try
                //a single tag.
                Tag[] tags = doc.tags(taglet.getName());
                if (tags.length > 0) {
                    currentOutput = taglet.getTagletOutput(tags[0], writer);
                }
            }
            if (currentOutput != null) {
                tagletManager.seenCustomTag(taglet.getName());
                output.addContent(currentOutput);
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
    public static Content getInlineTagOuput(TagletManager tagletManager,
            Tag holderTag, Tag inlineTag, TagletWriter tagletWriter) {
        Taglet[] definedTags = tagletManager.getInlineCustomTaglets();
        //This is a custom inline tag.
        for (Taglet definedTag : definedTags) {
            if (("@" + definedTag.getName()).equals(inlineTag.name())) {
                //Given a name of a seen custom tag, remove it from the
                // set of unseen custom tags.
                tagletManager.seenCustomTag(definedTag.getName());
                Content output = definedTag.getTagletOutput(
                        holderTag != null &&
                        definedTag.getName().equals("inheritDoc") ?
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
     * @return the {@link Content} representing the comments.
     */
    public abstract Content commentTagsToOutput(Tag holderTag, Tag[] tags);

    /**
     * Converts inline tags and text to TagOutput, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to non-inline tags.
     *
     * @param holderDoc specific doc where comment resides.
     * @param tags   array of text tags and inline tags (often alternating)
     *               present in the text of interest for this doc.
     * @return the {@link Content} representing the comments.
     */
    public abstract Content commentTagsToOutput(Doc holderDoc, Tag[] tags);

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
     * @return the {@link Content} representing the comments.
     */
    public abstract Content commentTagsToOutput(Tag holderTag,
        Doc holderDoc, Tag[] tags, boolean isFirstSentence);

    /**
     * @return an instance of the configuration used for this doclet.
     */
    public abstract Configuration configuration();
}
