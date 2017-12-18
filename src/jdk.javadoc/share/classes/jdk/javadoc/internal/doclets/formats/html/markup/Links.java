/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.markup;

import jdk.javadoc.internal.doclets.formats.html.SectionName;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;

/**
 * Factory for HTML A elements, both links (with a {@code href} attribute)
 * and anchors (with an {@code id} or {@code name} attribute).
 *
 * Most methods in this class are static factory methods.
 * The exceptions are those methods that directly or indirectly depend on the HTML version
 * being used, when determining valid HTML names (ids),
 * and those methods that generate anchors.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Links {

    private final HtmlVersion version;

    /**
     * Creates a {@code Links} object for a specific HTML version.
     * The version is used by the {@link #getName(String) getName} method
     * to help determine valid HTML names (ids), and to determine whether
     * to use an {@code id} or {@code name} attribute when creating anchors.
     *
     * @param version the HTML version
     */
    public Links(HtmlVersion version) {
        this.version = version;
    }

    /**
     * Creates an anchor of the form {@code <a id="name"><!-- --></a>}.
     * In HTML4, a {@code name} attribute will be generated instead of an {@code id} attribute.
     *
     * @param name the value for the {@code id} or {@code name} attribute
     * @return a content tree for the anchor
     */
    public Content createAnchor(String name) {
        return createAnchor(getName(name), null);
    }

    /**
     * Creates an anchor of the form {@code <a id="sectionName"><!-- --></a>}.
     * In HTML4, a {@code name} attribute will be generated instead of an {@code id} attribute.
     *
     * @param sectionName the value for the {@code id} or {@code name} attribute
     * @return a content tree for the anchor
     */
    public Content createAnchor(SectionName sectionName) {
        return createAnchor(sectionName.getName(), null);
    }

    /**
     * Creates an anchor of the form {@code <a id="sectionNameName"><!-- --></a>}.
     * In HTML4, a {@code name} attribute will be generated instead of an {@code id} attribute.
     *
     * @param sectionName the first part of the value for the {@code id} or {@code name} attribute
     * @param name the second part of the value for the {@code id} or {@code name} attribute
     * @return a content tree for the anchor
     */
    public Content createAnchor(SectionName sectionName, String name) {
        return createAnchor(sectionName.getName() + getName(name), null);
    }

    /**
     * Creates an anchor of the form {@code <a id="anchorName">content</a>}.
     * In HTML4, a {@code name} attribute will be generated instead of an {@code id} attribute.
     *
     * @param name the value for the {@code id} or {@code name} attribute
     * @param content the content that should be added to the anchor,
     *              or null, to use an empty comment
     * @return a content tree for the marker anchor
     */
    public Content createAnchor(String name, Content content) {
        return HtmlTree.A(version, name, (content == null ? EMPTY_COMMENT : content));
    }

    private static final Content EMPTY_COMMENT = new Comment(" ");

    /**
     * Creates a link of the form {@code <a href="#where">label</a>}.
     *
     * @param where      the position of the link in the file
     * @param label      the content for the link
     * @return a content tree for the link
     */
    public Content createLink(String where, Content label) {
        DocLink l = DocLink.fragment(getName(where));
        return Links.createLink(l, label, "", "");
    }

    /**
     * Creates a link of the form {@code <a href="#sectionName">label</a>}.
     *
     * @param sectionName   the section name to which the link will be created
     * @param label         the content for the link
     * @return a content tree for the link
     */
    public static Content createLink(SectionName sectionName, Content label) {
        DocLink l =  DocLink.fragment(sectionName.getName());
        return Links.createLink(l, label, "", "");
    }

    /**
     * Creates a link of the form {@code <a href="#sectionNameWhere">label</a>}.
     *
     * @param sectionName   the section name combined with where to which the link
     *                      will be created
     * @param where         the fragment combined with sectionName to which the link
     *                      will be created
     * @param label         the content for the link
     * @return a content tree for the link
     */
    public Content createLink(SectionName sectionName, String where, Content label) {
        DocLink l =  DocLink.fragment(sectionName.getName() + getName(where));
        return Links.createLink(l, label, "", "");
    }

    /**
     * Creates a link of the form {@code <a href="#stylename" title="title" target="target">label</a>}.
     *
     * @param sectionName   the section name to which the link will be created
     * @param label     the content for the link
     * @param title     the title for the link
     * @param target    the target for the link, or null
     * @return a content tree for the link
     */
    public static Content createLink(SectionName sectionName, Content label, String title, String target) {
        DocLink l = DocLink.fragment(sectionName.getName());
        return createLink(l, label, title, target);
    }

    /**
     * Creates a link of the form {@code <a href="path">label</a>}.
     *
     * @param path   the path for the link
     * @param label  the content for the link
     * @return a content tree for the link
     */
    public static Content createLink(DocPath path, String label) {
        return Links.createLink(path, new StringContent(label), false, "", "");
    }

    /**
     * Creates a link of the form {@code <a href="path">label</a>}.
     *
     * @param path   the path for the link
     * @param label  the content for the link
     * @return a content tree for the link
     */
    public static Content createLink(DocPath path, Content label) {
        return Links.createLink(path, label, "", "");
    }

    /**
     * Creates a link of the form {@code <a href="path" title="title" target="target">label</a>}.
     * If {@code strong} is set, the label will be wrapped in
     *      {@code <span style="typeNameLink">...</span>}.
     *
     * @param path      the path for the link
     * @param label     the content for the link
     * @param strong    whether to wrap the {@code label} in a SPAN element
     * @param title     the title for the link
     * @param target    the target for the link, or null
     * @return a content tree for the link
     */
    public static Content createLink(DocPath path, Content label, boolean strong,
            String title, String target) {
        return createLink(new DocLink(path), label, strong, title, target);
    }

    /**
     * Creates a link of the form {@code <a href="path" title="title" target="target">label</a>}.
     *
     * @param path      the path for the link
     * @param label     the content for the link
     * @param title     the title for the link
     * @param target    the target for the link, or null
     * @return a content tree for the link
     */
    public static Content createLink(DocPath path, Content label, String title, String target) {
        return Links.createLink(new DocLink(path), label, title, target);
    }

    /**
     * Creates a link of the form {@code <a href="link">label</a>}.
     *
     * @param link      the details for the link
     * @param label     the content for the link
     * @return a content tree for the link
     */
    public static Content createLink(DocLink link, Content label) {
        return Links.createLink(link, label, "", "");
    }

    /**
     * Creates a link of the form {@code <a href="path" title="title" target="target">label</a>}.
     *
     * @param link      the details for the link
     * @param label     the content for the link
     * @param title     the title for the link
     * @param target    the target for the link, or null
     * @return a content tree for the link
     */
    public static Content createLink(DocLink link, Content label, String title, String target) {
        HtmlTree anchor = HtmlTree.A(link.toString(), label);
        if (title != null && title.length() != 0) {
            anchor.addAttr(HtmlAttr.TITLE, title);
        }
        if (target != null && target.length() != 0) {
            anchor.addAttr(HtmlAttr.TARGET, target);
        }
        return anchor;
    }

    /**
     * Creates a link of the form {@code <a href="link" title="title" target="target">label</a>}.
     * If {@code strong} is set, the label will be wrapped in
     *      {@code <span style="typeNameLink">...</span>}.
     *
     * @param link      the details for the link
     * @param label     the content for the link
     * @param strong    whether to wrap the {@code label} in a SPAN element
     * @param title     the title for the link
     * @param target    the target for the link, or null
     * @return a content tree for the link
     */
    public static Content createLink(DocLink link, Content label, boolean strong,
            String title, String target) {
        return createLink(link, label, strong, title, target, false);
    }

    /**
     * Creates a link of the form {@code <a href="link" title="title" target="target">label</a>}.
     * If {@code strong} is set, the label will be wrapped in
     *      {@code <span style="typeNameLink">...</span>}.
     *
     * @param link       the details for the link
     * @param label      the content for the link
     * @param strong     whether to wrap the {@code label} in a SPAN element
     * @param title      the title for the link
     * @param target     the target for the link, or null
     * @param isExternal is the link external to the generated documentation
     * @return a content tree for the link
     */
    public static Content createLink(DocLink link, Content label, boolean strong,
            String title, String target, boolean isExternal) {
        Content body = label;
        if (strong) {
            body = HtmlTree.SPAN(HtmlStyle.typeNameLink, body);
        }
        HtmlTree l = HtmlTree.A(link.toString(), body);
        if (title != null && title.length() != 0) {
            l.addAttr(HtmlAttr.TITLE, title);
        }
        if (target != null && target.length() != 0) {
            l.addAttr(HtmlAttr.TARGET, target);
        }
        if (isExternal) {
            l.setStyle(HtmlStyle.externalLink);
        }
        return l;
    }

    /**
     * Creates a link.
     *
     * @param link       the details for the link
     * @param label      the content for the link
     * @param isExternal is the link external to the generated documentation
     * @return a content tree for the link
     */
    public static Content createLink(DocLink link, Content label, boolean isExternal) {
        HtmlTree anchor = HtmlTree.A(link.toString(), label);
        anchor.setStyle(HtmlStyle.externalLink);
        return anchor;
    }


    /**
     * Converts a name to a valid HTML name (id).
     * This depends on the HTML version specified when the {@code Links} object was created.
     *
     * @param name the string that needs to be converted to a valid HTML name
     * @return a valid HTML name
     */
    public String getName(String name) {
        /* The HTML 4 spec at http://www.w3.org/TR/html4/types.html#h-6.2 mentions
         * that the name/id should begin with a letter followed by other valid characters.
         * The HTML 5 spec (draft) is more permissive on names/ids where the only restriction
         * is that it should be at least one character long and should not contain spaces.
         * The spec draft is @ http://www.w3.org/html/wg/drafts/html/master/dom.html#the-id-attribute.
         *
         * For HTML 4, we need to check for non-characters at the beginning of the name and
         * substitute it accordingly, "_" and "$" can appear at the beginning of a member name.
         * The method substitutes "$" with "Z:Z:D" and will prefix "_" with "Z:Z".
         */

        if (version == HtmlVersion.HTML5) {
            return name.replaceAll(" +", "");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            switch (ch) {
                case '(':
                case ')':
                case '<':
                case '>':
                case ',':
                    sb.append('-');
                    break;
                case ' ':
                case '[':
                    break;
                case ']':
                    sb.append(":A");
                    break;
                // Any appearance of $ needs to be substituted with ":D" and not with hyphen
                // since a field name "P$$ and a method P(), both valid member names, can end
                // up as "P--". A member name beginning with $ needs to be substituted with
                // "Z:Z:D".
                case '$':
                    if (i == 0)
                        sb.append("Z:Z");
                    sb.append(":D");
                    break;
                // A member name beginning with _ needs to be prefixed with "Z:Z" since valid anchor
                // names can only begin with a letter.
                case '_':
                    if (i == 0)
                        sb.append("Z:Z");
                    sb.append(ch);
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }

}
