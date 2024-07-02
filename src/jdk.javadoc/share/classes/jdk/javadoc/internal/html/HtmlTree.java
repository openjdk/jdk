/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.html;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A tree node representing an HTML element, containing the name of the element,
 * a collection of attributes, and content.
 *
 * Except where otherwise stated, all methods in this class will throw
 * {@code NullPointerException} for any arguments that are {@code null}
 * or that are arrays or collections that contain {@code null}.
 *
 * Many methods in this class return {@code this}, to enable a series
 * of chained method calls on a single object.
 *
 * Terminology: An HTML element is typically composed of a start tag, some
 * enclosed content and typically an end tag. The start tag contains any
 * attributes for the element. See:
 * <a href="https://en.wikipedia.org/wiki/HTML_element">HTML element</a>.
 *
 * @see <a href="https://html.spec.whatwg.org/multipage/syntax.html#normal-elements">WhatWG: Normal Elements</a>
 * @see <a href="https://www.w3.org/TR/html51/syntax.html#writing-html-documents-elements">HTML 5.1: Elements</a>
 */
public class HtmlTree extends Content {

    /**
     * The tag for the HTML element.
     * This value is never {@code null}.
     */
    public final HtmlTag tag;

    /**
     * The attributes for the HTML element.
     * The keys and values in this map are never {@code null}.
     */
    private Map<HtmlAttr, String> attrs = Map.of();

    /**
     * The enclosed content ("inner HTML") for this HTML element.
     * The items in this list are never {@code null}.
     */
    private List<Content> content = List.of();

    /**
     * Creates an {@code HTMLTree} object representing an HTML element
     * with the given name.
     *
     * @param tag the name
     */
    public HtmlTree(HtmlTag tag) {
        this.tag = Objects.requireNonNull(tag);
    }

    /**
     * Adds an attribute.
     *
     * @param attrName  the name of the attribute
     * @param attrValue the value of the attribute
     * @return this object
     */
    public HtmlTree put(HtmlAttr attrName, String attrValue) {
        if (attrs.isEmpty())
            attrs = new LinkedHashMap<>(3);
        attrs.put(Objects.requireNonNull(attrName), Entity.escapeHtmlChars(attrValue));
        return this;
    }

    /**
     * Sets the {@code id} attribute.
     *
     * @param id the value for the attribute
     * @return this object
     */
    public HtmlTree setId(HtmlId id) {
        return put(HtmlAttr.ID, id.name());
    }

    /**
     * Sets the {@code title} attribute.
     * Any nested start or end tags in the content will be removed.
     *
     * @param body the content for the title attribute
     * @return this object
     */
    public HtmlTree setTitle(Content body) {
        return put(HtmlAttr.TITLE, stripHtml(body));
    }

    /**
     * Sets the {@code role} attribute.
     *
     * @param role the role
     * @return this object
     */
    public HtmlTree setRole(HtmlAttr.Role role) {
        return put(HtmlAttr.ROLE, role.toString());
    }

    /**
     * Sets the {@code class} attribute.
     *
     * @param style the value for the attribute
     * @return this object
     */
    public HtmlTree setStyle(HtmlStyle style) {
        return put(HtmlAttr.CLASS, style.cssName());
    }

    public HtmlTree addStyle(HtmlStyle style) {
        return addStyle(style.cssName());
    }

    public HtmlTree addStyle(String style) {
        if (attrs.isEmpty())
            attrs = new LinkedHashMap<>(3);
        attrs.compute(HtmlAttr.CLASS, (attr, existingStyle) ->
                existingStyle == null ? style : existingStyle + " " + style);
        return this;
    }

    /**
     * Adds additional content for the HTML element.
     *
     * @implSpec In order to facilitate creation of succinct output this method
     * silently drops discardable content as determined by {@link #isDiscardable()}.
     * Use {@link #addUnchecked(Content)} to add content unconditionally.
     *
     * @param content the content
     * @return this HTML tree
     */
    @Override
    public HtmlTree add(Content content) {
        if (content instanceof ContentBuilder cb) {
            cb.contents.forEach(this::add);
        } else if (!content.isDiscardable()) {
            // quietly avoid adding empty or invalid nodes
            if (this.content.isEmpty())
                this.content = new ArrayList<>();
            this.content.add(content);
        }
        return this;
    }

    /**
     * Adds content to this HTML tree without checking whether it is discardable.
     *
     * @param content the content to add
     * @return this HTML tree
     */
    public HtmlTree addUnchecked(Content content) {
        if (content instanceof ContentBuilder cb) {
            cb.contents.forEach(this::addUnchecked);
        } else {
            if (this.content.isEmpty())
                this.content = new ArrayList<>();
            this.content.add(content);
        }
        return this;
    }

    /**
     * Adds text content for the HTML element.
     *
     * If the last content member that was added is a {@code StringContent},
     * appends the string to that item; otherwise, creates and uses a new {@code StringContent}
     * for the new text content.
     *
     * @param stringContent string content that needs to be added
     */
    @Override
    public HtmlTree add(CharSequence stringContent) {
        if (!content.isEmpty()) {
            Content lastContent = content.get(content.size() - 1);
            if (lastContent instanceof TextBuilder)
                lastContent.add(stringContent);
            else {
                add(new TextBuilder(stringContent));
            }
        }
        else {
            add(new TextBuilder(stringContent));
        }
        return this;
    }

    /**
     * Adds each of a list of content items.
     *
     * @param list the list
     * @return this object
     */
    public HtmlTree add(List<? extends Content> list) {
        list.forEach(this::add);
        return this;
    }

    /**
     * {@return the attributes of this node}
     */
    public Map<HtmlAttr, String> getAttrs() {
        return attrs;
    }

    /**
     * {@return the contents of this node}
     */
    public List<Content> getContents() {
        return content;
    }

    /**
     * Adds each of a collection of items, using a map function to create the content for each item.
     *
     * @param items  the items
     * @param mapper the map function to generate the content for each item
     *
     * @return this object
     */
    @Override
    public <T> HtmlTree addAll(Collection<T> items, Function<T, Content> mapper) {
        items.forEach(item -> add(mapper.apply(item)));
        return this;
    }

    @Override
    public int charCount() {
        int n = 0;
        for (Content c : content) {
            n += c.charCount();
        }
        return n;
    }

    /*
     * The sets of ASCII URI characters to be left unencoded.
     * See "Uniform Resource Identifier (URI): Generic Syntax"
     * IETF RFC 3986. https://tools.ietf.org/html/rfc3986
     */
    public static final BitSet MAIN_CHARS;
    public static final BitSet QUERY_FRAGMENT_CHARS;

    static {
        BitSet alphaDigit = bitSet(bitSet('A', 'Z'), bitSet('a', 'z'), bitSet('0', '9'));
        BitSet unreserved = bitSet(alphaDigit, bitSet("-._~"));
        BitSet genDelims = bitSet(":/?#[]@");
        BitSet subDelims = bitSet("!$&'()*+,;=");
        MAIN_CHARS = bitSet(unreserved, genDelims, subDelims);
        BitSet pchar = bitSet(unreserved, subDelims, bitSet(":@"));
        QUERY_FRAGMENT_CHARS = bitSet(pchar, bitSet("/?"));
    }

    private static BitSet bitSet(String s) {
        BitSet result = new BitSet();
        for (int i = 0; i < s.length(); i++) {
           result.set(s.charAt(i));
        }
        return result;
    }

    private static BitSet bitSet(char from, char to) {
        BitSet result = new BitSet();
        result.set(from, to + 1);
        return result;
    }

    private static BitSet bitSet(BitSet... sets) {
        BitSet result = new BitSet();
        for (BitSet set : sets) {
            result.or(set);
        }
        return result;
    }

    /**
     * Apply percent-encoding to a URL.
     * This is similar to {@link java.net.URLEncoder} but
     * is less aggressive about encoding some characters,
     * like '(', ')', ',' which are used in the anchor
     * names for Java methods in HTML5 mode.
     *
     * @param url the url to be percent-encoded.
     * @return a percent-encoded string.
     */
    public static String encodeURL(String url) {
        BitSet nonEncodingChars = MAIN_CHARS;
        StringBuilder sb = new StringBuilder();
        for (byte c : url.getBytes(StandardCharsets.UTF_8)) {
            if (c == '?' || c == '#') {
                sb.append((char) c);
                // switch to the more restrictive set inside
                // the query and/or fragment
                nonEncodingChars = QUERY_FRAGMENT_CHARS;
            } else if (nonEncodingChars.get(c & 0xFF)) {
                sb.append((char) c);
            } else {
                sb.append(String.format("%%%02X", c & 0xFF));
            }
        }
        return sb.toString();
    }

    /**
     * Creates an HTML {@code A} element.
     * The {@code ref} argument will be URL-encoded for use as the attribute value.
     *
     * @param ref the value for the {@code href} attribute
     * @param body the content for element
     * @return the element
     */
    public static HtmlTree A(String ref, Content body) {
        return new HtmlTree(HtmlTag.A)
                .put(HtmlAttr.HREF, encodeURL(ref))
                .add(body);
    }

    /**
     * Creates an HTML {@code A} element.
     * The {@code ref} argument is assumed to be already suitably encoded,
     * and will <i>not</i> be additionally URL-encoded, but will be
     * {@link URI#toASCIIString() converted} to ASCII for use as the attribute value.
     *
     * @param ref the value for the {@code href} attribute
     * @param body the content for element
     * @return the element
     */
    public static HtmlTree A(URI ref, Content body) {
        return new HtmlTree(HtmlTag.A)
                .put(HtmlAttr.HREF, ref.toASCIIString())
                .add(body);
    }

    /**
     * Creates an HTML {@code CAPTION} element with the given content.
     *
     * @param body content for the element
     * @return the element
     */
    public static HtmlTree CAPTION(Content body) {
        return new HtmlTree(HtmlTag.CAPTION)
                .add(body);
    }

    /**
     * Creates an HTML {@code CODE} element with the given content.
     *
     * @param body content for the element
     * @return the element
     */
    public static HtmlTree CODE(Content body) {
        return new HtmlTree(HtmlTag.CODE)
                .add(body);
    }

    /**
     * Creates an HTML {@code DD} element with the given content.
     *
     * @param body content for the element
     * @return the element
     */
    public static HtmlTree DD(Content body) {
        return new HtmlTree(HtmlTag.DD)
                .add(body);
    }

    /**
     * Creates an HTML {@code DETAILS} element.
     *
     * @return the element
     */
    public static HtmlTree DETAILS() {
        return new HtmlTree(HtmlTag.DETAILS);
    }

    /**
     * Creates an HTML {@code DETAILS} element.
     *
     * @return the element
     */
    public static HtmlTree DETAILS(HtmlStyle style) {
        return new HtmlTree(HtmlTag.DETAILS)
                .setStyle(style);
    }

    /**
     * Creates an HTML {@code DL} element with the given style.
     *
     * @param style the style
     * @return the element
     */
    public static HtmlTree DL(HtmlStyle style) {
        return new HtmlTree(HtmlTag.DL)
                .setStyle(style);
    }

    /**
     * Creates an HTML {@code DL} element with the given style and content.
     *
     * @param style the style
     * @param body  the content
     * @return the element
     */
    public static HtmlTree DL(HtmlStyle style, Content body) {
        return new HtmlTree(HtmlTag.DL)
                .setStyle(style)
                .add(body);
    }

    /**
     * Creates an HTML {@code DIV} element with the given style.
     *
     * @param style the style
     * @return the element
     */
    public static HtmlTree DIV(HtmlStyle style) {
        return new HtmlTree(HtmlTag.DIV)
                .setStyle(style);
    }

    /**
     * Creates an HTML {@code DIV} element with the given style and content.
     *
     * @param style the style
     * @param body  the content
     * @return the element
     */
    public static HtmlTree DIV(HtmlStyle style, Content body) {
        return new HtmlTree(HtmlTag.DIV)
                .setStyle(style)
                .add(body);
    }

    /**
     * Creates an HTML {@code DIV} element with the given content.
     *
     * @param body the content
     * @return the element
     */
    public static HtmlTree DIV(Content body) {
        return new HtmlTree(HtmlTag.DIV)
                .add(body);
    }

    /**
     * Creates an HTML {@code DT} element with the given content.
     *
     * @param body the content
     * @return the element
     */
    public static HtmlTree DT(Content body) {
        return new HtmlTree(HtmlTag.DT)
                .add(body);
    }

    /**
     * Creates an HTML {@code FOOTER} element.
     * The role is set to {@code contentinfo}.
     *
     * @return the element
     */
    public static HtmlTree FOOTER() {
        return new HtmlTree(HtmlTag.FOOTER)
                .setRole(HtmlAttr.Role.CONTENTINFO);
    }

    /**
     * Creates an HTML {@code HEADER} element.
     * The role is set to {@code banner}.
     *
     * @return the element
     */
    public static HtmlTree HEADER() {
        return new HtmlTree(HtmlTag.HEADER)
                .setRole(HtmlAttr.Role.BANNER);
    }

    /**
     * Creates an HTML heading element with the given content.
     *
     * @param headingTag the tag for the heading
     * @param body       the content
     * @return the element
     */
    public static HtmlTree HEADING(HtmlTag headingTag, Content body) {
        return new HtmlTree(checkHeading(headingTag))
                .add(body);
    }

    /**
     * Creates an HTML heading element with the given style and content.
     *
     * @param headingTag the tag for the heading
     * @param style      the stylesheet class
     * @param body       the content
     * @return the element
     */
    public static HtmlTree HEADING(HtmlTag headingTag, HtmlStyle style, Content body) {
        return new HtmlTree(checkHeading(headingTag))
                .setStyle(style)
                .add(body);
    }

    /**
     * Creates an HTML heading element with the given style and content.
     * The {@code title} attribute is set from the content.
     *
     * @param headingTag the tag for the heading
     * @param style      the stylesheet class
     * @param body       the content
     * @return the element
     */
    public static HtmlTree HEADING_TITLE(HtmlTag headingTag,
                                         HtmlStyle style, Content body) {
        return new HtmlTree(checkHeading(headingTag))
                .setTitle(body)
                .setStyle(style)
                .add(body);
    }

    /**
     * Creates an HTML heading element with the given style and content.
     * The {@code title} attribute is set from the content.
     *
     * @param headingTag the tag for the heading
     * @param body       the content
     * @return the element
     */
    public static HtmlTree HEADING_TITLE(HtmlTag headingTag, Content body) {
        return new HtmlTree(checkHeading(headingTag))
                .setTitle(body)
                .add(body);
    }

    private static HtmlTag checkHeading(HtmlTag headingTag) {
        return switch (headingTag) {
            case H1, H2, H3, H4, H5, H6 -> headingTag;
            default -> throw new IllegalArgumentException(headingTag.toString());
        };
    }

    /**
     * Creates an HTML {@code HTML} element with the given {@code lang} attribute,
     * and {@code HEAD} and {@code BODY} contents.
     *
     * @param lang the value for the {@code lang} attribute
     * @param head the {@code HEAD} element
     * @param body the {@code BODY} element
     * @return the {@code HTML} element
     */
    public static HtmlTree HTML(String lang, Content head, Content body) {
        return new HtmlTree(HtmlTag.HTML)
                .put(HtmlAttr.LANG, lang)
                .add(head)
                .add(body);
    }

    /**
     * Creates an HTML {@code INPUT} element with the given id.
     * The element as marked as initially disabled.
     *
     * @param type  the type of input
     * @param id    the id
     * @return the element
     */
    public static HtmlTree INPUT(HtmlAttr.InputType type, HtmlId id) {
        return new HtmlTree(HtmlTag.INPUT)
                .put(HtmlAttr.TYPE, type.toString())
                .setId(id)
                .put(HtmlAttr.DISABLED, "");
    }

    /**
     * Creates an HTML {@code INPUT} element with the given type
     * and style. The element is marked as initially disabled.
     * @param type  the input type
     * @param style the input style
     * @return      the element
     */
    public static HtmlTree INPUT(HtmlAttr.InputType type, HtmlStyle style) {
        return new HtmlTree(HtmlTag.INPUT)
                .put(HtmlAttr.TYPE, type.toString())
                .setStyle(style)
                .put(HtmlAttr.DISABLED, "");
    }
    /**
     * Creates an HTML {@code LABEL} element with the given content.
     *
     * @param forLabel the value of the {@code for} attribute
     * @param body     the content
     * @return the element
     */
    public static HtmlTree LABEL(String forLabel, Content body) {
        return new HtmlTree(HtmlTag.LABEL)
                .put(HtmlAttr.FOR, forLabel)
                .add(body);
    }

    /**
     * Creates an HTML {@code LI} element with the given content.
     *
     * @param body the content
     * @return the element
     */
    public static HtmlTree LI(Content body) {
        return new HtmlTree(HtmlTag.LI)
                .add(body);
    }

    /**
     * Creates an HTML {@code LI} element with the given style and the given content.
     *
     * @param style the style
     * @param body  the content
     * @return the element
     */
    public static HtmlTree LI(HtmlStyle style, Content body) {
        return LI(body)
                .setStyle(style);
    }

    /**
     * Creates an HTML {@code LINK} tag with the given attributes.
     *
     * @param rel   the relevance of the link: the {@code rel} attribute
     * @param type  the type of link: the {@code type} attribute
     * @param href  the path for the link: the {@code href} attribute
     * @param title title for the link: the {@code title} attribute
     * @return the element
     */
    public static HtmlTree LINK(String rel, String type, String href, String title) {
        return new HtmlTree(HtmlTag.LINK)
                .put(HtmlAttr.REL, rel)
                .put(HtmlAttr.TYPE, type)
                .put(HtmlAttr.HREF, href)
                .put(HtmlAttr.TITLE, title);
    }

    /**
     * Creates an HTML {@code MAIN} element.
     * The role is set to {@code main}.
     *
     * @return the element
     */
    public static HtmlTree MAIN() {
        return new HtmlTree(HtmlTag.MAIN)
                .setRole(HtmlAttr.Role.MAIN);
    }

    /**
     * Creates an HTML {@code MAIN} element with the given content.
     * The role is set to {@code main}.
     *
     * @return the element
     */
    public static HtmlTree MAIN(Content body) {
        return new HtmlTree(HtmlTag.MAIN)
                .setRole(HtmlAttr.Role.MAIN)
                .add(body);
    }

    /**
     * Creates an HTML {@code META} element with {@code http-equiv} and {@code content} attributes.
     *
     * @param httpEquiv the value for the {@code http-equiv} attribute
     * @param content   the type of content, to be used in the {@code content} attribute
     * @param charset   the character set for the document, to be used in the {@code content} attribute
     * @return the element
     */
    public static HtmlTree META(String httpEquiv, String content, String charset) {
        return new HtmlTree(HtmlTag.META)
                .put(HtmlAttr.HTTP_EQUIV, httpEquiv)
                .put(HtmlAttr.CONTENT, content + "; charset=" + charset);
    }

    /**
     * Creates an HTML {@code META} element with {@code name} and {@code content} attributes.
     *
     * @param name    the value for the {@code name} attribute
     * @param content the value for the {@code content} attribute
     * @return the element
     */
    public static HtmlTree META(String name, String content) {
        return new HtmlTree(HtmlTag.META)
                .put(HtmlAttr.NAME, name)
                .put(HtmlAttr.CONTENT, content);
    }

    /**
     * Creates an HTML {@code NAV} element.
     * The role is set to {@code navigation}.
     *
     * @return the element
     */
    public static HtmlTree NAV() {
        return new HtmlTree(HtmlTag.NAV)
                .setRole(HtmlAttr.Role.NAVIGATION);
    }

    /**
     * Creates an HTML {@code NOSCRIPT} element with some content.
     *
     * @param body the content
     * @return the element
     */
    public static HtmlTree NOSCRIPT(Content body) {
        return new HtmlTree(HtmlTag.NOSCRIPT)
                .add(body);
    }

    /**
     * Creates an HTML {@code OL} element with the given style.
     *
     * @param style the style
     * @return the element
     */
    public static HtmlTree OL(HtmlStyle style) {
        return new HtmlTree(HtmlTag.OL)
                .setStyle(style);
    }

    /**
     * Creates an HTML {@code P} element with some content.
     *
     * @param body the content
     * @return the element
     */
    public static HtmlTree P(Content body) {
        return new HtmlTree(HtmlTag.P)
                .add(body);
    }

    /**
     * Creates an HTML {@code P} element with the given style and some content.
     *
     * @param style the style
     * @param body  the content
     * @return the element
     */
    public static HtmlTree P(HtmlStyle style, Content body) {
        return P(body)
                .setStyle(style);
    }

    /**
     * Creates an HTML {@code PRE} element with some content.
     *
     * @param body  the content
     * @return the element
     */
    public static HtmlTree PRE(Content body) {
        return new HtmlTree(HtmlTag.PRE).add(body);
    }

    /**
     * Creates an HTML {@code SCRIPT} element with some script content.
     * The type of the script is set to {@code text/javascript}.
     *
     * @param src the content
     * @return the element
     */
    public static HtmlTree SCRIPT(String src) {
        return new HtmlTree(HtmlTag.SCRIPT)
                .put(HtmlAttr.TYPE, "text/javascript")
                .put(HtmlAttr.SRC, src);

    }

    /**
     * Creates an HTML {@code SECTION} element with the given style.
     *
     * @param style the style
     * @return the element
     */
    public static HtmlTree SECTION(HtmlStyle style) {
        return new HtmlTree(HtmlTag.SECTION)
                .setStyle(style);
    }

    /**
     * Creates an HTML {@code SECTION} element with the given style and some content.
     *
     * @param style the style
     * @param body  the content
     * @return the element
     */
    public static HtmlTree SECTION(HtmlStyle style, Content body) {
        return new HtmlTree(HtmlTag.SECTION)
                .setStyle(style)
                .add(body);
    }

    /**
     * Creates an HTML {@code SMALL} element with some content.
     *
     * @param body  the content
     * @return the element
     */
    public static HtmlTree SMALL(Content body) {
        return new HtmlTree(HtmlTag.SMALL)
                .add(body);
    }

    /**
     * Creates an HTML {@code SPAN} element with some content.
     *
     * @param body  the content
     * @return the element
     */
    public static HtmlTree SPAN(Content body) {
        return new HtmlTree(HtmlTag.SPAN)
                .add(body);
    }

    /**
     * Creates an HTML {@code SPAN} element with the given style.
     *
     * @param styleClass the style
     * @return the element
     */
    public static HtmlTree SPAN(HtmlStyle styleClass) {
        return new HtmlTree(HtmlTag.SPAN)
                .setStyle(styleClass);
    }

    /**
     * Creates an HTML {@code SPAN} element with the given style and some content.
     *
     * @param styleClass the style
     * @param body       the content
     * @return the element
     */
    public static HtmlTree SPAN(HtmlStyle styleClass, Content body) {
        return SPAN(body)
                .setStyle(styleClass);
    }

    /**
     * Creates an HTML {@code SPAN} element with the given id and some content.
     *
     * @param id    the id
     * @param body  the content
     * @return the element
     */
    public static HtmlTree SPAN_ID(HtmlId id, Content body) {
        return new HtmlTree(HtmlTag.SPAN)
                .setId(id)
                .add(body);
    }

    /**
     * Creates an HTML {@code SPAN} element with the given id and style, and some content.
     *
     * @param id    the id
     * @param style the style
     * @param body  the content
     * @return the element
     */
    public static HtmlTree SPAN(HtmlId id, HtmlStyle style, Content body) {
        return new HtmlTree(HtmlTag.SPAN)
                .setId(id)
                .setStyle(style)
                .add(body);
    }

    /**
     * Creates an HTML {@code SUMMARY} element with the given content.
     *
     * @param body the content
     * @return the element
     */
    public static HtmlTree SUMMARY(Content body) {
        return new HtmlTree(HtmlTag.SUMMARY)
                .add(body);
    }

    /**
     * Creates an HTML {@code SUP} element with the given content.
     *
     * @param body  the content
     * @return the element
     */
    public static HtmlTree SUP(Content body) {
        return new HtmlTree(HtmlTag.SUP)
                .add(body);
    }

    /**
     * Creates an HTML {@code TD} element with the given style and some content.
     *
     * @param style the style
     * @param body  the content
     * @return the element
     */
    public static HtmlTree TD(HtmlStyle style, Content body) {
        return new HtmlTree(HtmlTag.TD)
                .setStyle(style)
                .add(body);
    }

    /**
     * Creates an HTML {@code TH} element with the given style and scope, and some content.
     *
     * @param style the style
     * @param scope the value for the {@code scope} attribute
     * @param body  the content
     * @return the element
     */
    public static HtmlTree TH(HtmlStyle style, String scope, Content body) {
        return new HtmlTree(HtmlTag.TH)
                .setStyle(style)
                .put(HtmlAttr.SCOPE, scope)
                .add(body);
    }

    /**
     * Creates an HTML {@code TH} element with the given scope, and some content.
     *
     * @param scope the value for the {@code scope} attribute
     * @param body  the content
     * @return the element
     */
    public static HtmlTree TH(String scope, Content body) {
        return new HtmlTree(HtmlTag.TH)
                .put(HtmlAttr.SCOPE, scope)
                .add(body);
    }

    /**
     * Creates an HTML {@code TITLE} element with some content.
     *
     * @param body the content
     * @return the element
     */
    public static HtmlTree TITLE(String body) {
        return new HtmlTree(HtmlTag.TITLE)
                .add(body);
    }

    /**
     * Creates an HTML {@code UL} element with the given style.
     *
     * @param style the style
     * @return the element
     */
    public static HtmlTree UL(HtmlStyle style) {
        return new HtmlTree(HtmlTag.UL)
                .setStyle(style);
    }

    /**
     * Creates an HTML {@code UL} element with the given style and some content.
     *
     * @param style the style
     * @param first the initial content
     * @param more  additional content
     * @return the element
     */
    public static HtmlTree UL(HtmlStyle style, Content first, Content... more) {
        var ul = new HtmlTree(HtmlTag.UL)
                .setStyle(style);
        ul.add(first);
        for (Content c : more) {
            ul.add(c);
        }
        return ul;
    }

    /**
     * Creates an HTML {@code UL} element with the given style and content generated
     * from a collection of items.
     *
     * @param style the style
     * @param items the items to be added to the list
     * @param mapper a mapper to create the content for each item
     * @return the element
     */
    public static <T> HtmlTree UL(HtmlStyle style, Collection<T> items, Function<T,Content> mapper) {
        return new HtmlTree(HtmlTag.UL)
                .setStyle(style)
                .addAll(items, mapper);
    }

    @Override
    public boolean isEmpty() {
        return (!hasContent() && !hasAttrs());
    }

    @Override
    public boolean isPhrasingContent() {
        return tag.blockType == HtmlTag.BlockType.INLINE;
    }

    /**
     * Returns true if the HTML tree has content.
     *
     * @return true if the HTML tree has content else return false
     */
    public boolean hasContent() {
        return (!content.isEmpty());
    }

    /**
     * Returns true if the HTML tree has attributes.
     *
     * @return true if the HTML tree has attributes else return false
     */
    public boolean hasAttrs() {
        return (!attrs.isEmpty());
    }

    /**
     * Returns true if the HTML tree has a specific attribute.
     *
     * @param attrName name of the attribute to check within the HTML tree
     * @return true if the HTML tree has the specified attribute else return false
     */
    public boolean hasAttr(HtmlAttr attrName) {
        return (attrs.containsKey(attrName));
    }

    /**
     * Returns {@code true} if the HTML tree does not affect the output and can be discarded.
     * This implementation considers non-void elements without content or {@code id} attribute
     * as discardable, with the exception of {@code SCRIPT} which can sometimes be used without
     * content.
     *
     * @return true if the HTML tree can be discarded without affecting the output
     */
    @Override
    public boolean isDiscardable() {
        return !isVoid()
            && !hasContent()
            && !hasAttr(HtmlAttr.ID)
            && tag != HtmlTag.SCRIPT;
    }

    /**
     * Returns true if the element is a normal element that is <em>phrasing content</em>.
     *
     * @return true if this is an inline element
     *
     * @see <a href="https://www.w3.org/TR/html51/dom.html#kinds-of-content-phrasing-content">Phrasing Content</a>
     */
    public boolean isInline() {
        return switch (tag) {
            case A, BUTTON, BR, CODE, EM, I, IMG, LABEL, SMALL, SPAN, STRONG, SUB, SUP, WBR -> true;
            default -> false;
        };
    }

    /**
     * Returns whether this is a <em>void</em> element.
     *
     * @return whether this is a void element
     *
     * @see <a href="https://www.w3.org/TR/html51/syntax.html#void-elements">Void Elements</a>
     */
    public boolean isVoid() {
        return switch (tag) {
            case BR, COL, FRAME, HR, IMG, INPUT, LINK, META, WBR -> true;
            default -> false;
        };
    }

    @Override
    public boolean write(Writer out, String newline, boolean atNewline) throws IOException {
        boolean isInline = isInline();
        if (!isInline && !atNewline) {
            out.write(newline);
        }
        String tagString = tag.getName();
        out.write("<");
        out.write(tagString);
        for (var attr : attrs.entrySet()) {
            var key = attr.getKey();
            var value = attr.getValue();
            out.write(" ");
            out.write(key.getName());
            if (!value.isEmpty()) {
                out.write("=\"");
                out.write(value.replace("\"", "&quot;"));
                out.write("\"");
            }
        }
        out.write(">");
        boolean nl = false;
        for (Content c : content) {
            nl = c.write(out, newline, nl);
        }
        if (!isVoid()) {
            out.write("</");
            out.write(tagString);
            out.write(">");
        }
        if (!isInline) {
            out.write(newline);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Given a Content node, strips all html characters and
     * returns the result.
     *
     * @param body The content node to check.
     * @return the plain text from the content node
     *
     */
    private static String stripHtml(Content body) {
        String rawString = body.toString();
        // remove HTML tags
        rawString = rawString.replaceAll("<.*?>", " ");
        // consolidate multiple spaces between a word to a single space
        rawString = rawString.replaceAll("\\b\\s{2,}\\b", " ");
        // remove extra whitespaces
        return rawString.trim();
    }
}
