/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.javadoc.*;

/**
 * A taglet that represents the @see tag.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.4
 */
public class SeeTaglet extends BaseTaglet implements InheritableTaglet {

    public SeeTaglet() {
        name = "see";
    }

    /**
     * {@inheritDoc}
     */
    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        Tag[] tags = input.method.seeTags();
        if (tags.length > 0) {
            output.holder = input.method;
            output.holderTag = tags[0];
            output.inlineTags = input.isFirstSentence ?
                tags[0].firstSentenceTags() : tags[0].inlineTags();
        }
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput getTagletOutput(Doc holder, TagletWriter writer) {
        SeeTag[] tags = holder.seeTags();
        if (tags.length == 0 && holder instanceof MethodDoc) {
            DocFinder.Output inheritedDoc =
                DocFinder.search(new DocFinder.Input((MethodDoc) holder, this));
            if (inheritedDoc.holder != null) {
                tags = inheritedDoc.holder.seeTags();
            }
        }
        return writer.seeTagOutput(holder, tags);
    }
}
