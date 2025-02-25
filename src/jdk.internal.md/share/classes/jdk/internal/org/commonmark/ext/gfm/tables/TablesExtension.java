/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.ext.gfm.tables;

import jdk.internal.org.commonmark.Extension;
import jdk.internal.org.commonmark.ext.gfm.tables.internal.TableBlockParser;
import jdk.internal.org.commonmark.ext.gfm.tables.internal.TableHtmlNodeRenderer;
import jdk.internal.org.commonmark.ext.gfm.tables.internal.TableMarkdownNodeRenderer;
import jdk.internal.org.commonmark.ext.gfm.tables.internal.TableTextContentNodeRenderer;
import jdk.internal.org.commonmark.parser.Parser;
import jdk.internal.org.commonmark.renderer.NodeRenderer;
import jdk.internal.org.commonmark.renderer.html.HtmlNodeRendererContext;
import jdk.internal.org.commonmark.renderer.html.HtmlNodeRendererFactory;
import jdk.internal.org.commonmark.renderer.html.HtmlRenderer;
import jdk.internal.org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import jdk.internal.org.commonmark.renderer.markdown.MarkdownNodeRendererFactory;
import jdk.internal.org.commonmark.renderer.markdown.MarkdownRenderer;
import jdk.internal.org.commonmark.renderer.text.TextContentNodeRendererContext;
import jdk.internal.org.commonmark.renderer.text.TextContentNodeRendererFactory;
import jdk.internal.org.commonmark.renderer.text.TextContentRenderer;

import java.util.Collections;
import java.util.Set;

/**
 * Extension for GFM tables using "|" pipes (GitHub Flavored Markdown).
 * <p>
 * Create it with {@link #create()} and then configure it on the builders
 * ({@link org.commonmark.parser.Parser.Builder#extensions(Iterable)},
 * {@link HtmlRenderer.Builder#extensions(Iterable)}).
 * </p>
 * <p>
 * The parsed tables are turned into {@link TableBlock} blocks.
 * </p>
 *
 * @see <a href="https://github.github.com/gfm/#tables-extension-">Tables (extension) in GitHub Flavored Markdown Spec</a>
 */
public class TablesExtension implements Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension,
        TextContentRenderer.TextContentRendererExtension, MarkdownRenderer.MarkdownRendererExtension {

    private TablesExtension() {
    }

    public static Extension create() {
        return new TablesExtension();
    }

    @Override
    public void extend(Parser.Builder parserBuilder) {
        parserBuilder.customBlockParserFactory(new TableBlockParser.Factory());
    }

    @Override
    public void extend(HtmlRenderer.Builder rendererBuilder) {
        rendererBuilder.nodeRendererFactory(new HtmlNodeRendererFactory() {
            @Override
            public NodeRenderer create(HtmlNodeRendererContext context) {
                return new TableHtmlNodeRenderer(context);
            }
        });
    }

    @Override
    public void extend(TextContentRenderer.Builder rendererBuilder) {
        rendererBuilder.nodeRendererFactory(new TextContentNodeRendererFactory() {
            @Override
            public NodeRenderer create(TextContentNodeRendererContext context) {
                return new TableTextContentNodeRenderer(context);
            }
        });
    }

    @Override
    public void extend(MarkdownRenderer.Builder rendererBuilder) {
        rendererBuilder.nodeRendererFactory(new MarkdownNodeRendererFactory() {
            @Override
            public NodeRenderer create(MarkdownNodeRendererContext context) {
                return new TableMarkdownNodeRenderer(context);
            }

            @Override
            public Set<Character> getSpecialCharacters() {
                return Collections.singleton('|');
            }
        });
    }
}
