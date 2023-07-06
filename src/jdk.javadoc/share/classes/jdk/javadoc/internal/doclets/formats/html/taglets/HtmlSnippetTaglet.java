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

package jdk.javadoc.internal.doclets.formats.html.taglets;

import java.util.HashSet;
import java.util.Set;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SnippetTree;
import com.sun.source.util.DocTreePath;

import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlAttr;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.TagName;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.taglets.SnippetTaglet;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.Style;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.StyledText;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

public class HtmlSnippetTaglet extends SnippetTaglet {
    HtmlSnippetTaglet(HtmlConfiguration config) {
        super(config);
    }

    @Override
    protected Content snippetTagOutput(Element element, SnippetTree tag, StyledText content,
                                       String id, String lang) {
        var tw = (TagletWriterImpl) tagletWriter;
        var pre = new HtmlTree(TagName.PRE).setStyle(HtmlStyle.snippet);
        if (id != null && !id.isBlank()) {
            pre.put(HtmlAttr.ID, id);
        }
        var code = new HtmlTree(TagName.CODE)
                .addUnchecked(Text.EMPTY); // Make sure the element is always rendered
        if (lang != null && !lang.isBlank()) {
            code.addStyle("language-" + lang);
        }

        content.consumeBy((styles, sequence) -> {
            CharSequence text = Text.normalizeNewlines(sequence);
            if (styles.isEmpty()) {
                code.add(text);
            } else {
                Element e = null;
                String t = null;
                boolean linkEncountered = false;
                boolean markupEncountered = false;
                Set<String> classes = new HashSet<>();
                for (Style s : styles) {
                    if (s instanceof Style.Name n) {
                        classes.add(n.name());
                    } else if (s instanceof Style.Link l) {
                        assert !linkEncountered; // TODO: do not assert; pick the first link report on subsequent
                        linkEncountered = true;
                        t = l.target();
                        e = getLinkedElement(element, t);
                        if (e == null) {
                            // TODO: diagnostic output
                        }
                    } else if (s instanceof Style.Markup) {
                        markupEncountered = true;
                        break;
                    } else {
                        // TODO: transform this if...else into an exhaustive
                        // switch over the sealed Style hierarchy when "Pattern
                        // Matching for switch" has been implemented (JEP 406
                        // and friends)
                        throw new AssertionError(styles);
                    }
                }
                Content c;
                if (markupEncountered) {
                    return;
                } else if (linkEncountered) {
                    assert e != null;
                    //disable preview tagging inside the snippets:
                    Utils.PreviewFlagProvider prevPreviewProvider = utils.setPreviewFlagProvider(el -> false);
                    try {
                        var lt = (HtmlLinkTaglet) config.tagletManager.getTaglet(DocTree.Kind.LINK);
                        c = lt.linkSeeReferenceOutput(element,
                                null,
                                t,
                                e,
                                false, // TODO: for now
                                Text.of(sequence.toString()),
                                (key, args) -> { /* TODO: report diagnostic */ },
                                tagletWriter);
                    } finally {
                        utils.setPreviewFlagProvider(prevPreviewProvider);
                    }
                } else {
                    c = HtmlTree.SPAN(Text.of(text));
                    classes.forEach(((HtmlTree) c)::addStyle);
                }
                code.add(c);
            }
        });
        String copyText = resources.getText("doclet.Copy_to_clipboard");
        String copiedText = resources.getText("doclet.Copied_to_clipboard");
        String copySnippetText = resources.getText("doclet.Copy_snippet_to_clipboard");
        var snippetContainer = HtmlTree.DIV(HtmlStyle.snippetContainer,
                new HtmlTree(TagName.BUTTON)
                        .add(HtmlTree.SPAN(Text.of(copyText))
                                .put(HtmlAttr.DATA_COPIED, copiedText))
                        .add(new HtmlTree(TagName.IMG)
                                .put(HtmlAttr.SRC, tw.getHtmlWriter().pathToRoot.resolve(DocPaths.CLIPBOARD_SVG).getPath())
                                .put(HtmlAttr.ALT, copySnippetText))
                        .addStyle(HtmlStyle.copy)
                        .addStyle(HtmlStyle.snippetCopy)
                        .put(HtmlAttr.ARIA_LABEL, copySnippetText)
                        .put(HtmlAttr.ONCLICK, "copySnippet(this)"));
        return snippetContainer.add(pre.add(code));
    }

    /*
     * Returns the element that is linked from the context of the referrer using
     * the provided signature; returns null if such element could not be found.
     *
     * This method is to be used when it is the target of the link that is
     * important, not the container of the link (e.g. was it an @see,
     * @link/@linkplain or @snippet tags, etc.)
     */
    public Element getLinkedElement(Element referer, String signature) {
        var factory = utils.docTrees.getDocTreeFactory();
        var docCommentTree = utils.getDocCommentTree(referer);
        var rootPath = new DocTreePath(utils.getTreePath(referer), docCommentTree);
        var reference = factory.newReferenceTree(signature);
        var fabricatedPath = new DocTreePath(rootPath, reference);
        return utils.docTrees.getElement(fabricatedPath);
    }
}
