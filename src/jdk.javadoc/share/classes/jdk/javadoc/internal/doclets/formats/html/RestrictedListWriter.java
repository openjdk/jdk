/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Element;

import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem;
import jdk.javadoc.internal.doclets.toolkit.util.RestrictedAPIListBuilder;

/**
 * Generate File to list all the restricted methods with the
 * appropriate links.
 */
public class RestrictedListWriter extends SummaryListWriter<RestrictedAPIListBuilder> {

    /**
     * Constructor.
     *
     * @param configuration the configuration for this doclet
     */
    public RestrictedListWriter(HtmlConfiguration configuration) {
        super(configuration, DocPaths.RESTRICTED_LIST, configuration.restrictedAPIListBuilder);
        if (configuration.restrictedAPIListBuilder != null) {
            configuration.indexBuilder.add(IndexItem.of(IndexItem.Category.TAGS,
                    resources.getText("doclet.Restricted_Methods"), path));
        }
    }

    @Override
    protected PageMode getPageMode() {
        return PageMode.RESTRICTED;
    }

    @Override
    protected String getDescription() {
        return "restricted methods";
    }

    @Override
    protected Content getHeadContent() {
        return configuration.contents.restrictedMethods;
    }

    @Override
    protected String getTitleKey() {
        return "doclet.Window_Restricted_List";
    }

    @Override
    protected void addComments(Element e, Content desc) {
        addSummaryComment(e, desc);
    }
}
