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

package com.sun.tools.doclint;

import java.util.Set;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.Name;

import static com.sun.tools.doclint.HtmlTag.Attr.*;

/**
 * Enum representing HTML tags.
 *
 * The intent of this class is to embody the semantics of W3C HTML 4.01
 * to the extent supported/used by javadoc.
 *
 * This is derivative of com.sun.tools.doclets.formats.html.markup.HtmlTag.
 * Eventually, these two should be merged back together, and possibly made
 * public.
 *
 * @see <a href="http://www.w3.org/TR/REC-html40/">HTML 4.01 Specification</a>
 * @author Bhavesh Patel
 * @author Jonathan Gibbons (revised)
 */
public enum HtmlTag {
    A(BlockType.INLINE, EndKind.REQUIRED,
            attrs(AttrKind.OK, HREF, TARGET, NAME)),

    B(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    BLOCKQUOTE,

    BODY(BlockType.OTHER, EndKind.REQUIRED),

    BR(BlockType.INLINE, EndKind.NONE,
            attrs(AttrKind.USE_CSS, CLEAR)),

    CAPTION(EnumSet.of(Flag.EXPECT_CONTENT)),

    CENTER,

    CITE(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    CODE(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    DD(BlockType.BLOCK, EndKind.OPTIONAL,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    DIV,

    DL(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_TEXT),
            attrs(AttrKind.USE_CSS, COMPACT)),

    DT(BlockType.BLOCK, EndKind.OPTIONAL,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    EM(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.NO_NEST)),

    FONT(BlockType.INLINE, EndKind.REQUIRED, // tag itself is deprecated
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.USE_CSS, SIZE, COLOR, FACE)),

    FRAME(BlockType.OTHER, EndKind.NONE),

    FRAMESET(BlockType.OTHER, EndKind.REQUIRED),

    H1,
    H2,
    H3,
    H4,
    H5,
    H6,

    HEAD(BlockType.OTHER, EndKind.REQUIRED),

    HR(BlockType.BLOCK, EndKind.NONE),

    HTML(BlockType.OTHER, EndKind.REQUIRED),

    I(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    IMG(BlockType.INLINE, EndKind.NONE,
            attrs(AttrKind.OK, SRC, ALT, HEIGHT, WIDTH),
            attrs(AttrKind.OBSOLETE, NAME),
            attrs(AttrKind.USE_CSS, ALIGN, HSPACE, VSPACE, BORDER)),

    LI(BlockType.BLOCK, EndKind.OPTIONAL),

    LINK(BlockType.OTHER, EndKind.NONE),

    MENU,

    META(BlockType.OTHER, EndKind.NONE),

    NOFRAMES(BlockType.OTHER, EndKind.REQUIRED),

    NOSCRIPT(BlockType.OTHER, EndKind.REQUIRED),

    OL(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_TEXT),
            attrs(AttrKind.USE_CSS, START, TYPE)),

    P(BlockType.BLOCK, EndKind.OPTIONAL,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.USE_CSS, ALIGN)),

    PRE(EnumSet.of(Flag.EXPECT_CONTENT)),

    SCRIPT(BlockType.OTHER, EndKind.REQUIRED),

    SMALL(BlockType.INLINE, EndKind.REQUIRED),

    SPAN(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    STRONG(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    SUB(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    SUP(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    TABLE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_TEXT),
            attrs(AttrKind.OK, SUMMARY, Attr.FRAME, RULES, BORDER,
                CELLPADDING, CELLSPACING),
            attrs(AttrKind.USE_CSS, ALIGN, WIDTH, BGCOLOR)),

    TBODY(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_TEXT),
            attrs(AttrKind.OK, ALIGN, CHAR, CHAROFF, VALIGN)),

    TD(BlockType.BLOCK, EndKind.OPTIONAL,
            attrs(AttrKind.OK, COLSPAN, ROWSPAN, HEADERS, SCOPE, ABBR, AXIS,
                ALIGN, CHAR, CHAROFF, VALIGN),
            attrs(AttrKind.USE_CSS, WIDTH, BGCOLOR, HEIGHT, NOWRAP)),

    TFOOT(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.OK, ALIGN, CHAR, CHAROFF, VALIGN)),

    TH(BlockType.BLOCK, EndKind.OPTIONAL,
            attrs(AttrKind.OK, COLSPAN, ROWSPAN, HEADERS, SCOPE, ABBR, AXIS,
                ALIGN, CHAR, CHAROFF, VALIGN),
            attrs(AttrKind.USE_CSS, WIDTH, BGCOLOR, HEIGHT, NOWRAP)),

    THEAD(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.OK, ALIGN, CHAR, CHAROFF, VALIGN)),

    TITLE(BlockType.OTHER, EndKind.REQUIRED),

    TR(BlockType.BLOCK, EndKind.OPTIONAL,
            EnumSet.of(Flag.NO_TEXT),
            attrs(AttrKind.OK, ALIGN, CHAR, CHAROFF, VALIGN),
            attrs(AttrKind.USE_CSS, BGCOLOR)),

    TT(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    U(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    UL(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_TEXT),
            attrs(AttrKind.USE_CSS, COMPACT, TYPE)),

    VAR(BlockType.INLINE, EndKind.REQUIRED);

    /**
     * Enum representing the type of HTML element.
     */
    public static enum BlockType {
        BLOCK,
        INLINE,
        OTHER;
    }

    /**
     * Enum representing HTML end tag requirement.
     */
    public static enum EndKind {
        NONE,
        OPTIONAL,
        REQUIRED;
    }

    public static enum Flag {
        EXPECT_CONTENT,
        NO_NEST,
        NO_TEXT
    }

    public static enum Attr {
        ABBR,
        ALIGN,
        ALT,
        AXIS,
        BGCOLOR,
        BORDER,
        CELLSPACING,
        CELLPADDING,
        CHAR,
        CHAROFF,
        CLEAR,
        CLASS,
        COLOR,
        COLSPAN,
        COMPACT,
        FACE,
        FRAME,
        HEADERS,
        HEIGHT,
        HREF,
        HSPACE,
        ID,
        NAME,
        NOWRAP,
        REVERSED,
        ROWSPAN,
        RULES,
        SCOPE,
        SIZE,
        SPACE,
        SRC,
        START,
        STYLE,
        SUMMARY,
        TARGET,
        TYPE,
        VALIGN,
        VSPACE,
        WIDTH;

        public String getText() {
            return name().toLowerCase();
        }

        static final Map<String,Attr> index = new HashMap<String,Attr>();
        static {
            for (Attr t: values()) {
                index.put(t.getText(), t);
            }
        }
    }

    public static enum AttrKind {
        INVALID,
        OBSOLETE,
        USE_CSS,
        OK
    }

    // This class exists to avoid warnings from using parameterized vararg type
    // Map<Attr,AttrKind> in signature of HtmlTag constructor.
    private static class AttrMap extends EnumMap<Attr,AttrKind>  {
        private static final long serialVersionUID = 0;
        AttrMap() {
            super(Attr.class);
        }
    }


    public final BlockType blockType;
    public final EndKind endKind;
    public final Set<Flag> flags;
    private final Map<Attr,AttrKind> attrs;


    HtmlTag() {
        this(BlockType.BLOCK, EndKind.REQUIRED);
    }

    HtmlTag(Set<Flag> flags) {
        this(BlockType.BLOCK, EndKind.REQUIRED, flags);
    }

    HtmlTag(BlockType blockType, EndKind endKind, AttrMap... attrMaps) {
        this(blockType, endKind, Collections.<Flag>emptySet(), attrMaps);
    }

    HtmlTag(BlockType blockType, EndKind endKind, Set<Flag> flags, AttrMap... attrMaps) {
        this.blockType = blockType;
        this.endKind = endKind;this.flags = flags;
        this.attrs = new EnumMap<Attr,AttrKind>(Attr.class);
        for (Map<Attr,AttrKind> m: attrMaps)
            this.attrs.putAll(m);
        attrs.put(Attr.CLASS, AttrKind.OK);
        attrs.put(Attr.ID, AttrKind.OK);
        attrs.put(Attr.STYLE, AttrKind.OK);
    }

    public String getText() {
        return name().toLowerCase();
    }

    public Attr getAttr(Name attrName) {
        return Attr.index.get(attrName.toString().toLowerCase());
    }

    public AttrKind getAttrKind(Name attrName) {
        AttrKind k = attrs.get(getAttr(attrName)); // null-safe
        return (k == null) ? AttrKind.INVALID : k;
    }

    private static AttrMap attrs(AttrKind k, Attr... attrs) {
        AttrMap map = new AttrMap();
        for (Attr a: attrs) map.put(a, k);
        return map;
    }

    private static final Map<String,HtmlTag> index = new HashMap<String,HtmlTag>();
    static {
        for (HtmlTag t: values()) {
            index.put(t.getText(), t);
        }
    }

    static HtmlTag get(Name tagName) {
        return index.get(tagName.toString().toLowerCase());
    }
}
