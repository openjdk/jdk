/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.taglet;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.lang.reflect.Field;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import com.sun.source.util.SimpleDocTreeVisitor;
import jdk.javadoc.doclet.Taglet;

import static com.sun.source.doctree.DocTree.Kind.*;

/**
 * A base class for block tags to insert a link to a local copy of JLS or JVMS.
 * The tags can be used as follows:
 *
 * <pre>
 * &commat;jls chapter.section description
 * &commat;jls preview-feature-chapter.section description
 * </pre>
 *
 * For example:
 *
 * <pre>
 * &commat;jls 3.4 Line Terminators
 * &commat;jls primitive-types-in-patterns-instanceof-switch-5.7.1 Exact Testing Conversions
 * </pre>
 *
 * will produce the following HTML, depending on the file containing
 * the tag.
 *
 * <pre>{@code
 * <dt>See <i>Java Language Specification</i>:
 * <dd><a href="../../specs/jls/jls-3.html#jls-3.4">3.4 Line terminators</a>
 * <dd><a href="../../specs/primitive-types-in-patterns-instanceof-switch-jls.html#jls-5.7.1">
 * 5.7.1 Exact Testing Conversions</a><sup class="preview-mark">
 * <a href="../../specs/jls/jls-1.html#jls-1.5.1">PREVIEW</a></sup>
 * }</pre>
 *
 * In inline tags (note you need manual JLS/JVMS prefix):
 * <pre>
 * JLS {&commat;jls 3.4}
 * </pre>
 *
 * produces (note the section sign and no trailing dot):
 * <pre>
 * JLS <a href="../../specs/jls/jls-3.html#jls-3.4">§3.4</a>
 * </pre>
 *
 * Copies of JLS, JVMS, and preview JLS and JVMS changes are expected to have
 * been placed in the {@code specs} folder. These documents are not included
 * in open-source repositories.
 */
public class JSpec implements Taglet  {

    public static class JLS extends JSpec {
        public JLS() {
            super("jls",
                "Java Language Specification",
                "jls");
        }
    }

    public static class JVMS extends JSpec {
        public JVMS() {
            super("jvms",
                "Java Virtual Machine Specification",
                "jvms");
        }
    }

    private final String tagName;
    private final String specTitle;
    private final String idPrefix;

    JSpec(String tagName, String specTitle, String idPrefix) {
        this.tagName = tagName;
        this.specTitle = specTitle;
        this.idPrefix = idPrefix;
    }

    // Note: Matches special cases like @jvms 6.5.checkcast
    private static final Pattern TAG_PATTERN = Pattern.compile("(?s)(.+ )?(?<preview>([a-z0-9]+-)+)?(?<chapter>[1-9][0-9]*)(?<section>[0-9a-z_.]*)( .*)?$");

    /**
     * Returns the set of locations in which the tag may be used.
     */
    @Override
    public Set<Location> getAllowedLocations() {
        return EnumSet.allOf(jdk.javadoc.doclet.Taglet.Location.class);
    }

    @Override
    public boolean isBlockTag() {
        return true;
    }

    @Override
    public boolean isInlineTag() {
        return true;
    }

    @Override
    public String getName() {
        return tagName;
    }

    @Override
    public String toString(List<? extends DocTree> tags, Element elem) {

        if (tags.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        boolean in_dd = false;

        for (DocTree tag : tags) {
            if (sb.length() == 0 && tag.getKind() == DocTree.Kind.UNKNOWN_BLOCK_TAG) {
                sb.append("<dt>See <i>").append(specTitle).append("</i>:</dt>\n")
                        .append("<dd>\n");
                in_dd = true;
            }

            List<? extends DocTree> contents;
            switch (tag.getKind()) {
                case UNKNOWN_BLOCK_TAG:
                    contents = ((UnknownBlockTagTree) tag).getContent();
                    break;
                case UNKNOWN_INLINE_TAG:
                    contents = ((UnknownInlineTagTree) tag).getContent();
                    break;
                default:
                    continue;
            }

            String tagText = contents.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining())
                    .trim();
            Matcher m = TAG_PATTERN.matcher(tagText);
            if (m.find()) {
                // preview-feature-4.6 is preview-feature-, 4, .6
                String preview = m.group("preview"); // null if no preview feature
                String chapter = m.group("chapter");
                String section = m.group("section");
                String rootParent = currentPath().replaceAll("[^/]+", "..");

                String url = preview == null ?
                        String.format("%1$s/specs/%2$s/%2$s-%3$s.html#%2$s-%3$s%4$s",
                                rootParent, idPrefix, chapter, section) :
                        String.format("%1$s/specs/%5$s%2$s.html#%2$s-%3$s%4$s",
                                rootParent, idPrefix, chapter, section, preview);

                var literal = expand(contents).trim();
                var prefix = (preview == null ? "" : preview) + chapter + section;
                if (literal.startsWith(prefix)) {
                    var hasFullTitle = literal.length() > prefix.length();
                    if (hasFullTitle) {
                        // Drop the preview identifier
                        literal = chapter + section + literal.substring(prefix.length());
                    } else {
                        // No section sign if the tag refers to a chapter, like {@jvms 4}
                        String sectionSign = section.isEmpty() ? "" : "§";
                        // Change whole text to "§chapter.x" in inline tags.
                        literal = sectionSign + chapter + section;
                    }
                }

                sb.append("<a href=\"")
                        .append(url)
                        .append("\">")
                        .append(literal)
                        .append("</a>");

                if (preview != null) {
                    // Add PREVIEW superscript that links to JLS/JVMS 1.5.1
                    // "Restrictions on the Use of Preview Features"
                    // Similar to how APIs link to the Preview info box warning
                    var sectionLink = String.format("%1$s/specs/%2$s/%2$s-%3$s.html#%2$s-%3$s%4$s",
                            rootParent, idPrefix, "1", ".5.1");
                    sb.append("<sup class=\"preview-mark\"><a href=\"")
                            .append(sectionLink)
                            .append("\">PREVIEW</a></sup>");
                }

                if (tag.getKind() == DocTree.Kind.UNKNOWN_BLOCK_TAG) {
                    sb.append("<br>");
                }
            }
        }

        if (in_dd) {
            sb.append("</dd>");
        }

        return sb.toString();
    }

    private static ThreadLocal<String> CURRENT_PATH = null;

    private String currentPath() {
        if (CURRENT_PATH == null) {
            try {
                Field f = Class.forName("jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter")
                               .getField("CURRENT_PATH");
                @SuppressWarnings("unchecked")
                ThreadLocal<String> tl = (ThreadLocal<String>) f.get(null);
                CURRENT_PATH = tl;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Cannot determine current path", e);
            }
        }
        return CURRENT_PATH.get();
    }

    private String expand(List<? extends DocTree> trees) {
        return (new SimpleDocTreeVisitor<StringBuilder, StringBuilder>() {
            public StringBuilder defaultAction(DocTree tree, StringBuilder sb) {
                return sb.append(tree.toString());
            }

            public StringBuilder visitLiteral(LiteralTree tree, StringBuilder sb) {
                if (tree.getKind() == CODE) {
                    sb.append("<code>");
                }
                sb.append(escape(tree.getBody().toString()));
                if (tree.getKind() == CODE) {
                    sb.append("</code>");
                }
                return sb;
            }

            private String escape(String s) {
                return s.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;");
            }
        }).visit(trees, new StringBuilder()).toString();
    }

}
