/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.ExecutableElement;

import jdk.javadoc.internal.doclets.formats.html.SectionName;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

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

    private final DocPath file;
    private final Utils utils;

    /**
     * Creates a {@code Links} object for a specific file, to be written in a specific HTML version.
     * The version is used by the {@link #getName(String) getName} method
     * to help determine valid HTML names (ids), and to determine whether
     * to use an {@code id} or {@code name} attribute when creating anchors.
     *
     * @param file the file
     */
    public Links(DocPath file, Utils utils) {
        this.file = file;
        this.utils = utils;
    }

    /**
     * Creates a link of the form {@code <a href="#where">label</a>}.
     *
     * @param where      the position of the link in the file
     * @param label      the content for the link
     * @return a content tree for the link
     */
    public Content createLink(String where, Content label) {
        DocLink l = DocLink.fragment(getName(where));
        return createLink(l, label, "", "");
    }

    /**
     * Creates a link of the form {@code <a href="#sectionName">label</a>}.
     *
     * @param sectionName   the section name to which the link will be created
     * @param label         the content for the link
     * @return a content tree for the link
     */
    public Content createLink(SectionName sectionName, Content label) {
        DocLink l =  DocLink.fragment(sectionName.getName());
        return createLink(l, label, "", "");
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
        return createLink(l, label, "", "");
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
    public Content createLink(SectionName sectionName, Content label, String title, String target) {
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
    public Content createLink(DocPath path, String label) {
        return createLink(path, new StringContent(label), false, "", "");
    }

    /**
     * Creates a link of the form {@code <a href="path">label</a>}.
     *
     * @param path   the path for the link
     * @param label  the content for the link
     * @return a content tree for the link
     */
    public Content createLink(DocPath path, Content label) {
        return createLink(path, label, "", "");
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
    public Content createLink(DocPath path, Content label, boolean strong,
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
    public Content createLink(DocPath path, Content label, String title, String target) {
        return createLink(new DocLink(path), label, title, target);
    }

    /**
     * Creates a link of the form {@code <a href="link">label</a>}.
     *
     * @param link      the details for the link
     * @param label     the content for the link
     * @return a content tree for the link
     */
    public Content createLink(DocLink link, Content label) {
        return createLink(link, label, "", "");
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
    public Content createLink(DocLink link, Content label, String title, String target) {
        HtmlTree anchor = HtmlTree.A(link.relativizeAgainst(file).toString(), label);
        if (title != null && title.length() != 0) {
            anchor.put(HtmlAttr.TITLE, title);
        }
        if (target != null && target.length() != 0) {
            anchor.put(HtmlAttr.TARGET, target);
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
    public Content createLink(DocLink link, Content label, boolean strong,
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
    public Content createLink(DocLink link, Content label, boolean strong,
            String title, String target, boolean isExternal) {
        Content body = label;
        if (strong) {
            body = HtmlTree.SPAN(HtmlStyle.typeNameLink, body);
        }
        HtmlTree l = HtmlTree.A(link.relativizeAgainst(file).toString(), body);
        if (title != null && title.length() != 0) {
            l.put(HtmlAttr.TITLE, title);
        }
        if (target != null && target.length() != 0) {
            l.put(HtmlAttr.TARGET, target);
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
    public Content createLink(DocLink link, Content label, boolean isExternal) {
        HtmlTree anchor = HtmlTree.A(link.relativizeAgainst(file).toString(), label);
        anchor.setStyle(HtmlStyle.externalLink);
        return anchor;
    }

    /**
     * Returns the HTML id to use for an executable element.
     *
     * @param executableElement the element
     *
     * @return the id
     */
    public String getAnchor(ExecutableElement executableElement) {
        return getAnchor(executableElement, false);
    }

    /**
     * Returns the HTML id to use for an executable element.
     *
     * @param executableElement the element
     * @param isProperty whether or not the element represents a property
     *
     * @return the id
     */
    public String getAnchor(ExecutableElement executableElement, boolean isProperty) {
        String a = isProperty
                ? executableElement.getSimpleName().toString()
                : executableElement.getSimpleName()
                    + utils.makeSignature(executableElement, null, true, true);
        return getName(a);
    }

    /**
     * Converts a name to a valid HTML id.
     *
     * @param name the string that needs to be converted to a valid HTML id
     * @return a valid HTML name
     */
    public String getName(String name) {
        return name.replaceAll("\\s+", "");
    }

}
