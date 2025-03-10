/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import com.sun.source.doctree.SinceTree;

import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;

import javax.lang.model.element.Element;
import java.util.List;


import static com.sun.source.doctree.DocTree.Kind.SINCE;

/**
 * Build list of all the packages, classes, constructors, fields and methods
 * that were added in one of the releases specified by the {@code --since}
 * option. The release names must exactly match the names used in the javadoc
 * {@code @since} tags of the respective elements.
 */
public class NewAPIBuilder extends SummaryAPIListBuilder {

    public final List<String> releases;

    public NewAPIBuilder(BaseConfiguration configuration, List<String> releases) {
        super(configuration);
        this.releases = releases;
        buildSummaryAPIInfo();
    }

    @Override
    protected boolean belongsToSummary(Element element) {
        if (!utils.hasDocCommentTree(element)) {
            return false;
        }
        var sinceTrees = utils.getBlockTags(element, SINCE, SinceTree.class);
        if (sinceTrees.isEmpty()) {
            return false;
        }

        // assumes a simple string value with no formatting
        return sinceTrees.stream()
                .anyMatch(tree -> releases.contains(tree.getBody().getFirst().toString()));
    }
}
