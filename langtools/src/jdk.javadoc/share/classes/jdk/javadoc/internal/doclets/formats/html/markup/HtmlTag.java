/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * Enum representing HTML tags.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public enum HtmlTag {
    A(BlockType.INLINE, EndTag.END),
    BLOCKQUOTE,
    BODY(BlockType.OTHER, EndTag.END),
    BR(BlockType.INLINE, EndTag.NOEND),
    CAPTION,
    CENTER(HtmlVersion.HTML4),
    CODE(BlockType.INLINE, EndTag.END),
    DD,
    DIR(HtmlVersion.HTML4),
    DIV,
    DL,
    DT,
    EM(BlockType.INLINE, EndTag.END),
    FONT(HtmlVersion.HTML4, BlockType.INLINE, EndTag.END),
    FOOTER(HtmlVersion.HTML5),
    H1,
    H2,
    H3,
    H4,
    H5,
    H6,
    HEAD(BlockType.OTHER, EndTag.END),
    HEADER(HtmlVersion.HTML5),
    HR(BlockType.BLOCK, EndTag.NOEND),
    HTML(BlockType.OTHER, EndTag.END),
    I(BlockType.INLINE, EndTag.END),
    IFRAME(BlockType.OTHER, EndTag.END),
    IMG(BlockType.INLINE, EndTag.NOEND),
    INPUT(BlockType.BLOCK, EndTag.NOEND),
    LABEL(BlockType.INLINE, EndTag.END),
    LI,
    LISTING,
    LINK(BlockType.OTHER, EndTag.NOEND),
    MAIN(HtmlVersion.HTML5),
    MENU,
    META(BlockType.OTHER, EndTag.NOEND),
    NAV(HtmlVersion.HTML5),
    NOSCRIPT(BlockType.OTHER, EndTag.END),
    OL,
    P,
    PRE,
    SCRIPT(BlockType.OTHER, EndTag.END),
    SECTION(HtmlVersion.HTML5),
    SMALL(BlockType.INLINE, EndTag.END),
    SPAN(BlockType.INLINE, EndTag.END),
    STRONG(BlockType.INLINE, EndTag.END),
    SUB(BlockType.INLINE, EndTag.END),
    TABLE,
    TBODY,
    TD,
    TH,
    TITLE(BlockType.OTHER, EndTag.END),
    TR,
    TT(HtmlVersion.HTML4, BlockType.INLINE, EndTag.END),
    UL;

    public final BlockType blockType;
    public final EndTag endTag;
    public final String value;
    public final HtmlVersion htmlVersion;

    /**
     * Enum representing the type of HTML element.
     */
    public static enum BlockType {
        BLOCK,
        INLINE,
        OTHER
    }

    /**
     * Enum representing HTML end tag requirement.
     */
    public static enum EndTag {
        END,
        NOEND
    }

    HtmlTag() {
        this(HtmlVersion.ALL, BlockType.BLOCK, EndTag.END);
    }

    HtmlTag(HtmlVersion htmlVersion) {
        this(htmlVersion, BlockType.BLOCK, EndTag.END);
    }

    HtmlTag(BlockType blockType, EndTag endTag ) {
        this(HtmlVersion.ALL, blockType, endTag);
    }

    HtmlTag(HtmlVersion htmlVersion, BlockType blockType, EndTag endTag ) {
        this.htmlVersion = htmlVersion;
        this.blockType = blockType;
        this.endTag = endTag;
        this.value = Utils.toLowerCase(name());
    }

    /**
     * Returns true if the end tag is required. This is specific to the standard
     * doclet and does not exactly resemble the W3C specifications.
     *
     * @return true if end tag needs to be displayed else return false
     */
    public boolean endTagRequired() {
        return (endTag == EndTag.END);
    }

    /**
     * Returns true if the tag is allowed in the output HTML version of this javadoc run.
     *
     * @param htmlVer the output HTML version for this javadoc run
     * @return true if the tag is allowed
     */
    public boolean allowTag(HtmlVersion htmlVer) {
        return (this.htmlVersion == HtmlVersion.ALL || this.htmlVersion == htmlVer);
    }

    public String toString() {
        return value;
    }
}
