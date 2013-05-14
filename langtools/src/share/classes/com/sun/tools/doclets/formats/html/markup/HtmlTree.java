/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.formats.html.markup;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * Class for generating HTML tree for javadoc output.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public class HtmlTree extends Content {

    private HtmlTag htmlTag;
    private Map<HtmlAttr,String> attrs = Collections.<HtmlAttr,String>emptyMap();
    private List<Content> content = Collections.<Content>emptyList();
    public static final Content EMPTY = new StringContent("");

    /**
     * Constructor to construct HtmlTree object.
     *
     * @param tag HTML tag for the HtmlTree object
     */
    public HtmlTree(HtmlTag tag) {
        htmlTag = nullCheck(tag);
    }

    /**
     * Constructor to construct HtmlTree object.
     *
     * @param tag HTML tag for the HtmlTree object
     * @param contents contents to be added to the tree
     */
    public HtmlTree(HtmlTag tag, Content... contents) {
        this(tag);
        for (Content content: contents)
            addContent(content);
    }

    /**
     * Adds an attribute for the HTML tag.
     *
     * @param attrName name of the attribute
     * @param attrValue value of the attribute
     */
    public void addAttr(HtmlAttr attrName, String attrValue) {
        if (attrs.isEmpty())
            attrs = new LinkedHashMap<HtmlAttr,String>(3);
        attrs.put(nullCheck(attrName), escapeHtmlChars(attrValue));
    }

    public void setTitle(Content body) {
        addAttr(HtmlAttr.TITLE, stripHtml(body));
    }

    /**
     * Adds a style for the HTML tag.
     *
     * @param style style to be added
     */
    public void addStyle(HtmlStyle style) {
        addAttr(HtmlAttr.CLASS, style.toString());
    }

    /**
     * Adds content for the HTML tag.
     *
     * @param tagContent tag content to be added
     */
    public void addContent(Content tagContent) {
        if (tagContent == HtmlTree.EMPTY || tagContent.isValid()) {
            if (content.isEmpty())
                content = new ArrayList<Content>();
            content.add(tagContent);
        }
    }

    /**
     * This method adds a string content to the htmltree. If the last content member
     * added is a StringContent, append the string to that StringContent or else
     * create a new StringContent and add it to the html tree.
     *
     * @param stringContent string content that needs to be added
     */
    public void addContent(String stringContent) {
        if (!content.isEmpty()) {
            Content lastContent = content.get(content.size() - 1);
            if (lastContent instanceof StringContent)
                lastContent.addContent(stringContent);
            else
                addContent(new StringContent(stringContent));
        }
        else
            addContent(new StringContent(stringContent));
    }

    public int charCount() {
        int n = 0;
        for (Content c : content)
            n += c.charCount();
        return n;
    }

    /**
     * Given a string, escape all special html characters and
     * return the result.
     *
     * @param s The string to check.
     * @return the original string with all of the HTML characters escaped.
     */
    private static String escapeHtmlChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                // only start building a new string if we need to
                case '<': case '>': case '&':
                    StringBuilder sb = new StringBuilder(s.substring(0, i));
                    for ( ; i < s.length(); i++) {
                        ch = s.charAt(i);
                        switch (ch) {
                            case '<': sb.append("&lt;");  break;
                            case '>': sb.append("&gt;");  break;
                            case '&': sb.append("&amp;"); break;
                            default:  sb.append(ch);      break;
                        }
                    }
                    return sb.toString();
            }
        }
        return s;
    }

    /**
     * Generates an HTML anchor tag.
     *
     * @param ref reference url for the anchor tag
     * @param body content for the anchor tag
     * @return an HtmlTree object
     */
    public static HtmlTree A(String ref, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.A, nullCheck(body));
        htmltree.addAttr(HtmlAttr.HREF, ref);
        return htmltree;
    }

    /**
     * Generates an HTML anchor tag with name attribute and content.
     *
     * @param name name for the anchor tag
     * @param body content for the anchor tag
     * @return an HtmlTree object
     */
    public static HtmlTree A_NAME(String name, Content body) {
        HtmlTree htmltree = HtmlTree.A_NAME(name);
        htmltree.addContent(nullCheck(body));
        return htmltree;
    }

    /**
     * Generates an HTML anchor tag with name attribute.
     *
     * @param name name for the anchor tag
     * @return an HtmlTree object
     */
    public static HtmlTree A_NAME(String name) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.A);
        htmltree.addAttr(HtmlAttr.NAME, nullCheck(name));
        return htmltree;
    }

    /**
     * Generates a CAPTION tag with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the CAPTION tag
     */
    public static HtmlTree CAPTION(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.CAPTION, nullCheck(body));
        return htmltree;
    }

    /**
     * Generates a CODE tag with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the CODE tag
     */
    public static HtmlTree CODE(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.CODE, nullCheck(body));
        return htmltree;
    }

    /**
     * Generates a DD tag with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the DD tag
     */
    public static HtmlTree DD(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.DD, nullCheck(body));
        return htmltree;
    }

    /**
     * Generates a DL tag with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the DL tag
     */
    public static HtmlTree DL(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.DL, nullCheck(body));
        return htmltree;
    }

    /**
     * Generates a DIV tag with the style class attributes. It also encloses
     * a content.
     *
     * @param styleClass stylesheet class for the tag
     * @param body content for the tag
     * @return an HtmlTree object for the DIV tag
     */
    public static HtmlTree DIV(HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.DIV, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    /**
     * Generates a DIV tag with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the DIV tag
     */
    public static HtmlTree DIV(Content body) {
        return DIV(null, body);
    }

    /**
     * Generates a DT tag with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the DT tag
     */
    public static HtmlTree DT(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.DT, nullCheck(body));
        return htmltree;
    }

    /**
     * Generates a EM tag with some content.
     *
     * @param body content to be added to the tag
     * @return an HtmlTree object for the EM tag
     */
    public static HtmlTree EM(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.EM, nullCheck(body));
        return htmltree;
    }

    /**
     * Generates a FRAME tag.
     *
     * @param src the url of the document to be shown in the frame
     * @param name specifies the name of the frame
     * @param title the title for the frame
     * @param scrolling specifies whether to display scrollbars in the frame
     * @return an HtmlTree object for the FRAME tag
     */
    public static HtmlTree FRAME(String src, String name, String title, String scrolling) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.FRAME);
        htmltree.addAttr(HtmlAttr.SRC, nullCheck(src));
        htmltree.addAttr(HtmlAttr.NAME, nullCheck(name));
        htmltree.addAttr(HtmlAttr.TITLE, nullCheck(title));
        if (scrolling != null)
            htmltree.addAttr(HtmlAttr.SCROLLING, scrolling);
        return htmltree;
    }

    /**
     * Generates a Frame tag.
     *
     * @param src the url of the document to be shown in the frame
     * @param name specifies the name of the frame
     * @param title the title for the frame
     * @return an HtmlTree object for the SPAN tag
     */
    public static HtmlTree FRAME(String src, String name, String title) {
        return FRAME(src, name, title, null);
    }

    /**
     * Generates a FRAMESET tag.
     *
     * @param cols the size of columns in the frameset
     * @param rows the size of rows in the frameset
     * @param title the title for the frameset
     * @param onload the script to run when the document loads
     * @return an HtmlTree object for the FRAMESET tag
     */
    public static HtmlTree FRAMESET(String cols, String rows, String title, String onload) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.FRAMESET);
        if (cols != null)
            htmltree.addAttr(HtmlAttr.COLS, cols);
        if (rows != null)
            htmltree.addAttr(HtmlAttr.ROWS, rows);
        htmltree.addAttr(HtmlAttr.TITLE, nullCheck(title));
        htmltree.addAttr(HtmlAttr.ONLOAD, nullCheck(onload));
        return htmltree;
    }

    /**
     * Generates a heading tag (h1 to h6) with the title and style class attributes. It also encloses
     * a content.
     *
     * @param headingTag the heading tag to be generated
     * @param printTitle true if title for the tag needs to be printed else false
     * @param styleClass stylesheet class for the tag
     * @param body content for the tag
     * @return an HtmlTree object for the tag
     */
    public static HtmlTree HEADING(HtmlTag headingTag, boolean printTitle,
            HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(headingTag, nullCheck(body));
        if (printTitle)
            htmltree.setTitle(body);
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    /**
     * Generates a heading tag (h1 to h6) with style class attribute. It also encloses
     * a content.
     *
     * @param headingTag the heading tag to be generated
     * @param styleClass stylesheet class for the tag
     * @param body content for the tag
     * @return an HtmlTree object for the tag
     */
    public static HtmlTree HEADING(HtmlTag headingTag, HtmlStyle styleClass, Content body) {
        return HEADING(headingTag, false, styleClass, body);
    }

    /**
     * Generates a heading tag (h1 to h6) with the title attribute. It also encloses
     * a content.
     *
     * @param headingTag the heading tag to be generated
     * @param printTitle true if the title for the tag needs to be printed else false
     * @param body content for the tag
     * @return an HtmlTree object for the tag
     */
    public static HtmlTree HEADING(HtmlTag headingTag, boolean printTitle, Content body) {
        return HEADING(headingTag, printTitle, null, body);
    }

    /**
     * Generates a heading tag (h1 to h6)  with some content.
     *
     * @param headingTag the heading tag to be generated
     * @param body content for the tag
     * @return an HtmlTree object for the tag
     */
    public static HtmlTree HEADING(HtmlTag headingTag, Content body) {
        return HEADING(headingTag, false, null, body);
    }

    /**
     * Generates an HTML tag with lang attribute. It also adds head and body
     * content to the HTML tree.
     *
     * @param lang language for the HTML document
     * @param head head for the HTML tag
     * @param body body for the HTML tag
     * @return an HtmlTree object for the HTML tag
     */
    public static HtmlTree HTML(String lang, Content head, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.HTML, nullCheck(head), nullCheck(body));
        htmltree.addAttr(HtmlAttr.LANG, nullCheck(lang));
        return htmltree;
    }

    /**
     * Generates a I tag with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the I tag
     */
    public static HtmlTree I(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.I, nullCheck(body));
        return htmltree;
    }

    /**
     * Generates a LI tag with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the LI tag
     */
    public static HtmlTree LI(Content body) {
        return LI(null, body);
    }

    /**
     * Generates a LI tag with some content.
     *
     * @param styleClass style for the tag
     * @param body content for the tag
     * @return an HtmlTree object for the LI tag
     */
    public static HtmlTree LI(HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.LI, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    /**
     * Generates a LINK tag with the rel, type, href and title attributes.
     *
     * @param rel relevance of the link
     * @param type type of link
     * @param href the path for the link
     * @param title title for the link
     * @return an HtmlTree object for the LINK tag
     */
    public static HtmlTree LINK(String rel, String type, String href, String title) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.LINK);
        htmltree.addAttr(HtmlAttr.REL, nullCheck(rel));
        htmltree.addAttr(HtmlAttr.TYPE, nullCheck(type));
        htmltree.addAttr(HtmlAttr.HREF, nullCheck(href));
        htmltree.addAttr(HtmlAttr.TITLE, nullCheck(title));
        return htmltree;
    }

    /**
     * Generates a META tag with the http-equiv, content and charset attributes.
     *
     * @param httpEquiv http equiv attribute for the META tag
     * @param content type of content
     * @param charSet character set used
     * @return an HtmlTree object for the META tag
     */
    public static HtmlTree META(String httpEquiv, String content, String charSet) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.META);
        htmltree.addAttr(HtmlAttr.HTTP_EQUIV, nullCheck(httpEquiv));
        htmltree.addAttr(HtmlAttr.CONTENT, nullCheck(content));
        htmltree.addAttr(HtmlAttr.CHARSET, nullCheck(charSet));
        return htmltree;
    }

    /**
     * Generates a META tag with the name and content attributes.
     *
     * @param name name attribute
     * @param content type of content
     * @return an HtmlTree object for the META tag
     */
    public static HtmlTree META(String name, String content) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.META);
        htmltree.addAttr(HtmlAttr.NAME, nullCheck(name));
        htmltree.addAttr(HtmlAttr.CONTENT, nullCheck(content));
        return htmltree;
    }

    /**
     * Generates a NOSCRIPT tag with some content.
     *
     * @param body content of the noscript tag
     * @return an HtmlTree object for the NOSCRIPT tag
     */
    public static HtmlTree NOSCRIPT(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.NOSCRIPT, nullCheck(body));
        return htmltree;
    }

    /**
     * Generates a P tag with some content.
     *
     * @param body content of the Paragraph tag
     * @return an HtmlTree object for the P tag
     */
    public static HtmlTree P(Content body) {
        return P(null, body);
    }

    /**
     * Generates a P tag with some content.
     *
     * @param styleClass style of the Paragraph tag
     * @param body content of the Paragraph tag
     * @return an HtmlTree object for the P tag
     */
    public static HtmlTree P(HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.P, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    /**
     * Generates a SCRIPT tag with the type and src attributes.
     *
     * @param type type of link
     * @param src the path for the script
     * @return an HtmlTree object for the SCRIPT tag
     */
    public static HtmlTree SCRIPT(String type, String src) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.SCRIPT);
        htmltree.addAttr(HtmlAttr.TYPE, nullCheck(type));
        htmltree.addAttr(HtmlAttr.SRC, nullCheck(src));
        return htmltree;
    }

    /**
     * Generates a SMALL tag with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the SMALL tag
     */
    public static HtmlTree SMALL(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.SMALL, nullCheck(body));
        return htmltree;
    }

    /**
     * Generates a STRONG tag with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the STRONG tag
     */
    public static HtmlTree STRONG(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.STRONG, nullCheck(body));
        return htmltree;
    }

    /**
     * Generates a SPAN tag with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the SPAN tag
     */
    public static HtmlTree SPAN(Content body) {
        return SPAN(null, body);
    }

    /**
     * Generates a SPAN tag with style class attribute and some content.
     *
     * @param styleClass style class for the tag
     * @param body content for the tag
     * @return an HtmlTree object for the SPAN tag
     */
    public static HtmlTree SPAN(HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.SPAN, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    /**
     * Generates a SPAN tag with id and style class attributes. It also encloses
     * a content.
     *
     * @param id the id for the tag
     * @param styleClass stylesheet class for the tag
     * @param body content for the tag
     * @return an HtmlTree object for the SPAN tag
     */
    public static HtmlTree SPAN(String id, HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.SPAN, nullCheck(body));
        htmltree.addAttr(HtmlAttr.ID, nullCheck(id));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    /**
     * Generates a Table tag with border, width and summary attributes and
     * some content.
     *
     * @param border border for the table
     * @param width width of the table
     * @param summary summary for the table
     * @param body content for the table
     * @return an HtmlTree object for the TABLE tag
     */
    public static HtmlTree TABLE(int border, int width, String summary,
            Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.TABLE, nullCheck(body));
        htmltree.addAttr(HtmlAttr.BORDER, Integer.toString(border));
        htmltree.addAttr(HtmlAttr.WIDTH, Integer.toString(width));
        htmltree.addAttr(HtmlAttr.SUMMARY, nullCheck(summary));
        return htmltree;
    }

    /**
     * Generates a Table tag with style class, border, cell padding,
     * cellspacing and summary attributes and some content.
     *
     * @param styleClass style of the table
     * @param border border for the table
     * @param cellPadding cell padding for the table
     * @param cellSpacing cell spacing for the table
     * @param summary summary for the table
     * @param body content for the table
     * @return an HtmlTree object for the TABLE tag
     */
    public static HtmlTree TABLE(HtmlStyle styleClass, int border, int cellPadding,
            int cellSpacing, String summary, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.TABLE, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        htmltree.addAttr(HtmlAttr.BORDER, Integer.toString(border));
        htmltree.addAttr(HtmlAttr.CELLPADDING, Integer.toString(cellPadding));
        htmltree.addAttr(HtmlAttr.CELLSPACING, Integer.toString(cellSpacing));
        htmltree.addAttr(HtmlAttr.SUMMARY, nullCheck(summary));
        return htmltree;
    }

    /**
     * Generates a Table tag with border, cell padding,
     * cellspacing and summary attributes and some content.
     *
     * @param border border for the table
     * @param cellPadding cell padding for the table
     * @param cellSpacing cell spacing for the table
     * @param summary summary for the table
     * @param body content for the table
     * @return an HtmlTree object for the TABLE tag
     */
    public static HtmlTree TABLE(int border, int cellPadding,
            int cellSpacing, String summary, Content body) {
        return TABLE(null, border, cellPadding, cellSpacing, summary, body);
    }

    /**
     * Generates a TD tag with style class attribute and some content.
     *
     * @param styleClass style for the tag
     * @param body content for the tag
     * @return an HtmlTree object for the TD tag
     */
    public static HtmlTree TD(HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.TD, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    /**
     * Generates a TD tag for an HTML table with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the TD tag
     */
    public static HtmlTree TD(Content body) {
        return TD(null, body);
    }

    /**
     * Generates a TH tag with style class and scope attributes and some content.
     *
     * @param styleClass style for the tag
     * @param scope scope of the tag
     * @param body content for the tag
     * @return an HtmlTree object for the TH tag
     */
    public static HtmlTree TH(HtmlStyle styleClass, String scope, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.TH, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        htmltree.addAttr(HtmlAttr.SCOPE, nullCheck(scope));
        return htmltree;
    }

    /**
     * Generates a TH tag with scope attribute and some content.
     *
     * @param scope scope of the tag
     * @param body content for the tag
     * @return an HtmlTree object for the TH tag
     */
    public static HtmlTree TH(String scope, Content body) {
        return TH(null, scope, body);
    }

    /**
     * Generates a TITLE tag with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the TITLE tag
     */
    public static HtmlTree TITLE(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.TITLE, nullCheck(body));
        return htmltree;
    }

    /**
     * Generates a TR tag for an HTML table with some content.
     *
     * @param body content for the tag
     * @return an HtmlTree object for the TR tag
     */
    public static HtmlTree TR(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.TR, nullCheck(body));
        return htmltree;
    }

    /**
     * Generates a UL tag with the style class attribute and some content.
     *
     * @param styleClass style for the tag
     * @param body content for the tag
     * @return an HtmlTree object for the UL tag
     */
    public static HtmlTree UL(HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.UL, nullCheck(body));
        htmltree.addStyle(nullCheck(styleClass));
        return htmltree;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return (!hasContent() && !hasAttrs());
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
     * Returns true if the HTML tree is valid. This check is more specific to
     * standard doclet and not exactly similar to W3C specifications. But it
     * ensures HTML validation.
     *
     * @return true if the HTML tree is valid
     */
    public boolean isValid() {
        switch (htmlTag) {
            case A :
                return (hasAttr(HtmlAttr.NAME) || (hasAttr(HtmlAttr.HREF) && hasContent()));
            case BR :
                return (!hasContent() && (!hasAttrs() || hasAttr(HtmlAttr.CLEAR)));
            case FRAME :
                return (hasAttr(HtmlAttr.SRC) && !hasContent());
            case HR :
                return (!hasContent());
            case IMG :
                return (hasAttr(HtmlAttr.SRC) && hasAttr(HtmlAttr.ALT) && !hasContent());
            case LINK :
                return (hasAttr(HtmlAttr.HREF) && !hasContent());
            case META :
                return (hasAttr(HtmlAttr.CONTENT) && !hasContent());
            case SCRIPT :
                return ((hasAttr(HtmlAttr.TYPE) && hasAttr(HtmlAttr.SRC) && !hasContent()) ||
                        (hasAttr(HtmlAttr.TYPE) && hasContent()));
            default :
                return hasContent();
        }
    }

    /**
     * Returns true if the element is an inline element.
     *
     * @return true if the HTML tag is an inline element
     */
    public boolean isInline() {
        return (htmlTag.blockType == HtmlTag.BlockType.INLINE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean write(Writer out, boolean atNewline) throws IOException {
        if (!isInline() && !atNewline)
            out.write(DocletConstants.NL);
        String tagString = htmlTag.toString();
        out.write("<");
        out.write(tagString);
        Iterator<HtmlAttr> iterator = attrs.keySet().iterator();
        HtmlAttr key;
        String value;
        while (iterator.hasNext()) {
            key = iterator.next();
            value = attrs.get(key);
            out.write(" ");
            out.write(key.toString());
            if (!value.isEmpty()) {
                out.write("=\"");
                out.write(value);
                out.write("\"");
            }
        }
        out.write(">");
        boolean nl = false;
        for (Content c : content)
            nl = c.write(out, nl);
        if (htmlTag.endTagRequired()) {
            out.write("</");
            out.write(tagString);
            out.write(">");
        }
        if (!isInline()) {
            out.write(DocletConstants.NL);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Given a Content node, strips all html characters and
     * return the result.
     *
     * @param body The content node to check.
     * @return the plain text from the content node
     *
     */
    private static String stripHtml(Content body) {
        String rawString = body.toString();
        // remove HTML tags
        rawString = rawString.replaceAll("\\<.*?>", " ");
        // consolidate multiple spaces between a word to a single space
        rawString = rawString.replaceAll("\\b\\s{2,}\\b", " ");
        // remove extra whitespaces
        return rawString.trim();
    }
}
