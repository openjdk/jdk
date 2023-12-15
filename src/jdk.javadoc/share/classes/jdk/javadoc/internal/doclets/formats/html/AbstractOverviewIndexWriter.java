/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.formats.html.markup.BodyContents;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.Navigation.PageMode;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;

/**
 * Abstract class to generate the top-level "overview" files.
 */
public abstract class AbstractOverviewIndexWriter extends HtmlDocletWriter {

    /**
     * Constructs the AbstractOverviewIndexWriter.
     *
     * @param configuration  The current configuration
     * @param filename Name of the module index file to be generated.
     */
    public AbstractOverviewIndexWriter(HtmlConfiguration configuration,
                                       DocPath filename) {
        super(configuration, filename);
    }

    /**
     * {@return the page description, for the {@code <meta>} element}
     */
    protected abstract String getDescription();

    /**
     * {@return the title for the page}
     */
    protected abstract String getTitleKey();

    /**
     * Adds the overview summary comment for this documentation. Add one line
     * summary at the top of the page and generate a link to the description,
     * which is added at the end of this page.
     *
     * @param target the content to which the overview header will be added
     */
    protected void addOverviewHeader(Content target) {
        addConfigurationTitle(target);
        addOverviewComment(target);
        addOverviewTags(target);
    }

    /**
     * Adds the overview comment as provided in the file specified by the
     * "-overview" option on the command line.
     *
     * @param content the content to which the overview comment will be added
     */
    protected void addOverviewComment(Content content) {
        if (!utils.getFullBody(configuration.overviewElement).isEmpty()) {
            addInlineComment(configuration.overviewElement, content);
        }
    }

    /**
     * Adds the block tags provided in the file specified by the "-overview" option.
     *
     * @param content the content to which the tags will be added
     */
    protected void addOverviewTags(Content content) {
        if (!utils.getFullBody(configuration.overviewElement).isEmpty()) {
            addTagsInfo(configuration.overviewElement, content);
        }
    }

    @Override
    public void buildPage() throws DocFileIOException {
        var titleKey = getTitleKey();
        String windowOverview = resources.getText(titleKey);
        Content body = getBody(getWindowTitle(windowOverview));
        Content main = new ContentBuilder();
        addOverviewHeader(main);
        addIndex(main);
        body.add(new BodyContents()
                .setHeader(getHeader(PageMode.OVERVIEW))
                .addMainContent(main)
                .setFooter(getFooter()));
        printHtmlDocument(
                configuration.metakeywords.getOverviewMetaKeywords(titleKey, configuration.getOptions().docTitle()),
                getDescription(), body);
    }

    @Override
    public boolean isIndexable() {
        return true;
    }

    /**
     * Adds the index to the documentation.
     *
     * @param target the content to which the packages/modules list will be added
     */
    protected abstract void addIndex(Content target);

    /**
     * Adds the doctitle to the documentation, if it is specified on the command line.
     *
     * @param target the content to which the title will be added
     */
    protected void addConfigurationTitle(Content target) {
        String doctitle = configuration.getOptions().docTitle();
        if (!doctitle.isEmpty()) {
            var title = RawHtml.of(doctitle);
            var heading = HtmlTree.HEADING(Headings.PAGE_TITLE_HEADING,
                    HtmlStyle.title, title);
            var div = HtmlTree.DIV(HtmlStyle.header, heading);
            target.add(div);
        }
    }
}
