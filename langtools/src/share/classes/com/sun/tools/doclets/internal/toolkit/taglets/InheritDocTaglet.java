/*
 * Copyright (c) 2001, 2005, Oracle and/or its affiliates. All rights reserved.
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

/**
 * An inline Taglet representing the <b>inheritDoc</b> tag. This tag should only
 * be used with a method.  It is used to inherit documentation from overriden
 * and implemented methods.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.4
 */

public class InheritDocTaglet extends BaseInlineTaglet {

    /**
     * The inline tag that would appear in the documentation if
     * the writer wanted documentation to be inherited.
     */
    public static final String INHERIT_DOC_INLINE_TAG = "{@inheritDoc}";

    /**
     * Construct a new InheritDocTaglet.
     */
    public InheritDocTaglet () {
        name = "inheritDoc";
    }

    /**
     * Will return false because this inline tag may
     * only appear in Methods.
     * @return false since this is not a method.
     */
    public boolean inField() {
        return false;
    }

    /**
     * Will return false because this inline tag may
     * only appear in Methods.
     * @return false since this is not a method.
     */
    public boolean inConstructor() {
        return false;
    }

    /**
     * Will return false because this inline tag may
     * only appear in Methods.
     * @return false since this is not a method.
     */
    public boolean inOverview() {
        return false;
    }

    /**
     * Will return false because this inline tag may
     * only appear in Methods.
     * @return false since this is not a method.
     */
    public boolean inPackage() {
        return false;
    }

    /**
     * Will return false because this inline tag may
     * only appear in Methods.
     * @return false since this is not a method.
     */
    public boolean inType() {
        return false;
    }

    /**
     * Given a <code>MethodDoc</code> item, a <code>Tag</code> in the
     * <code>MethodDoc</code> item and a String, replace all occurances
     * of @inheritDoc with documentation from it's superclass or superinterface.
     *
     * @param writer the writer that is writing the output.
     * @param md the {@link MethodDoc} that we are documenting.
     * @param holderTag the tag that holds the inheritDoc tag.
     * @param isFirstSentence true if we only want to inherit the first sentence.
     */
    private TagletOutput retrieveInheritedDocumentation(TagletWriter writer,
            MethodDoc md, Tag holderTag, boolean isFirstSentence) {
        TagletOutput replacement = writer.getTagletOutputInstance();

        Taglet inheritableTaglet = holderTag == null ?
            null : writer.configuration().tagletManager.getTaglet(holderTag.name());
        if (inheritableTaglet != null &&
            !(inheritableTaglet instanceof InheritableTaglet)) {
                //This tag does not support inheritence.
                writer.configuration().message.warning(md.position(),
                "doclet.noInheritedDoc", md.name() + md.flatSignature());
         }
        DocFinder.Output inheritedDoc =
            DocFinder.search(new DocFinder.Input(md,
                (InheritableTaglet) inheritableTaglet, holderTag,
                isFirstSentence, true));
        if (inheritedDoc.isValidInheritDocTag == false) {
            writer.configuration().message.warning(md.position(),
                "doclet.noInheritedDoc", md.name() + md.flatSignature());
        } else if (inheritedDoc.inlineTags.length > 0) {
            replacement = writer.commentTagsToOutput(inheritedDoc.holderTag,
                inheritedDoc.holder, inheritedDoc.inlineTags, isFirstSentence);
        }
        return replacement;
    }

    /**
     * Given the <code>Tag</code> representation of this custom
     * tag, return its string representation, which is output
     * to the generated page.
     * @param tag the <code>Tag</code> representation of this custom tag.
     * @param tagletWriter the taglet writer for output.
     * @return the TagletOutput representation of this <code>Tag</code>.
     */
    public TagletOutput getTagletOutput(Tag tag, TagletWriter tagletWriter) {
        if (! (tag.holder() instanceof MethodDoc)) {
            return tagletWriter.getOutputInstance();
        }
        return tag.name().equals("@inheritDoc") ?
                retrieveInheritedDocumentation(tagletWriter, (MethodDoc) tag.holder(), null, tagletWriter.isFirstSentence) :
                retrieveInheritedDocumentation(tagletWriter, (MethodDoc) tag.holder(), tag, tagletWriter.isFirstSentence);
    }
}
