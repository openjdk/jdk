/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;

import javax.lang.model.element.Element;
import java.util.ArrayList;
import java.util.List;


import static com.sun.source.doctree.DocTree.Kind.SINCE;

/**
 * Build list of all the packages, classes, constructors, fields and methods
 * that were added in the current release. This class is only used when documenting
 * the platform library code, since that is the only time we know the version of
 * the code. To enable testing, the feature can also be activated by setting a system
 * property.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class NewAPIBuilder {

    public final List<PerReleaseBuilder> releases = new ArrayList<>();

    public NewAPIBuilder(BaseConfiguration configuration, List<String> newSince) {
        newSince.forEach(release -> {
            PerReleaseBuilder builder = new PerReleaseBuilder(configuration, release);
            if (!builder.isEmpty()) {
                releases.add(builder);
            }
        });
    }

    public boolean isEmpty() {
        // Builders in the list are guaranteed to be non-empty
        return releases.isEmpty();
    }

    public static class PerReleaseBuilder extends SummaryAPIListBuilder {

        public final String release;

        /**
         * Constructor.
         *
         * @param configuration the current configuration of the doclet
         * @param release single release id to document new APIs for
         */
        public PerReleaseBuilder(BaseConfiguration configuration, String release) {
            super(configuration, e -> isNewAPI(e, release, configuration.utils));
            this.release = release;
            buildSummaryAPIInfo();
        }

        private static boolean isNewAPI(Element e, String release, Utils utils) {
            if (!utils.hasDocCommentTree(e)) {
                return false;
            }
            List<? extends DocTree> since = utils.getBlockTags(e, SINCE);
            if (since.isEmpty()) {
                return false;
            }
            CommentHelper ch = utils.getCommentHelper(e);
            return since.stream().anyMatch(tree -> release.equals(ch.getBody(tree).toString()));
        }

    }
}
