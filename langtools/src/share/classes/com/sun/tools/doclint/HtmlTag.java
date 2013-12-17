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
import java.util.Locale;
import java.util.Map;

import javax.lang.model.element.Name;

import static com.sun.tools.doclint.HtmlTag.Attr.*;
import com.sun.tools.javac.util.StringUtils;

/**
 * Enum representing HTML tags.
 *
 * The intent of this class is to embody the semantics of W3C HTML 4.01
 * to the extent supported/used by javadoc.
 * In time, we may wish to transition javadoc and doclint to using HTML 5.
 *
 * This is derivative of com.sun.tools.doclets.formats.html.markup.HtmlTag.
 * Eventually, these two should be merged back together, and possibly made
 * public.
 *
 * @see <a href="http://www.w3.org/TR/REC-html40/">HTML 4.01 Specification</a>
 * @see <a href="http://www.w3.org/TR/html5/">HTML 5 Specification</a>
 * @author Bhavesh Patel
 * @author Jonathan Gibbons (revised)
 */
public enum HtmlTag {
    A(BlockType.INLINE, EndKind.REQUIRED,
            attrs(AttrKind.OK, HREF, TARGET, NAME)),

    B(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    BIG(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    BLOCKQUOTE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    BODY(BlockType.OTHER, EndKind.REQUIRED),

    BR(BlockType.INLINE, EndKind.NONE,
            attrs(AttrKind.USE_CSS, CLEAR)),

    CAPTION(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_INLINE, Flag.EXPECT_CONTENT)),

    CENTER(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    CITE(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    CODE(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    DD(BlockType.LIST_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE, Flag.EXPECT_CONTENT)),

    DFN(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    DIV(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    DL(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.USE_CSS, COMPACT)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == DT) || (t == DD);
        }
    },

    DT(BlockType.LIST_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_INLINE, Flag.EXPECT_CONTENT)),

    EM(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.NO_NEST)),

    FONT(BlockType.INLINE, EndKind.REQUIRED, // tag itself is deprecated
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.USE_CSS, SIZE, COLOR, FACE)),

    FRAME(BlockType.OTHER, EndKind.NONE),

    FRAMESET(BlockType.OTHER, EndKind.REQUIRED),

    H1(BlockType.BLOCK, EndKind.REQUIRED),
    H2(BlockType.BLOCK, EndKind.REQUIRED),
    H3(BlockType.BLOCK, EndKind.REQUIRED),
    H4(BlockType.BLOCK, EndKind.REQUIRED),
    H5(BlockType.BLOCK, EndKind.REQUIRED),
    H6(BlockType.BLOCK, EndKind.REQUIRED),

    HEAD(BlockType.OTHER, EndKind.REQUIRED),

    HR(BlockType.BLOCK, EndKind.NONE,
            attrs(AttrKind.OK, WIDTH)), // OK in 4.01; not allowed in 5

    HTML(BlockType.OTHER, EndKind.REQUIRED),

    I(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    IMG(BlockType.INLINE, EndKind.NONE,
            attrs(AttrKind.OK, SRC, ALT, HEIGHT, WIDTH),
            attrs(AttrKind.OBSOLETE, NAME),
            attrs(AttrKind.USE_CSS, ALIGN, HSPACE, VSPACE, BORDER)),

    LI(BlockType.LIST_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.OK, VALUE)),

    LINK(BlockType.OTHER, EndKind.NONE),

    MENU(BlockType.BLOCK, EndKind.REQUIRED) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == LI);
        }
    },

    META(BlockType.OTHER, EndKind.NONE),

    NOFRAMES(BlockType.OTHER, EndKind.REQUIRED),

    NOSCRIPT(BlockType.BLOCK, EndKind.REQUIRED),

    OL(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.OK, START, TYPE)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == LI);
        }
    },

    P(BlockType.BLOCK, EndKind.OPTIONAL,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.USE_CSS, ALIGN)),

    PRE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)) {
        @Override
        public boolean accepts(HtmlTag t) {
            switch (t) {
                case IMG: case BIG: case SMALL: case SUB: case SUP:
                    return false;
                default:
                    return (t.blockType == BlockType.INLINE);
            }
        }
    },

    SCRIPT(BlockType.OTHER, EndKind.REQUIRED),

    SMALL(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    SPAN(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    STRONG(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    SUB(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    SUP(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    TABLE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.OK, SUMMARY, Attr.FRAME, RULES, BORDER,
                CELLPADDING, CELLSPACING, WIDTH), // width OK in 4.01; not allowed in 5
            attrs(AttrKind.USE_CSS, ALIGN, BGCOLOR)) {
        @Override
        public boolean accepts(HtmlTag t) {
            switch (t) {
                case CAPTION:
                case THEAD: case TBODY: case TFOOT:
                case TR: // HTML 3.2
                    return true;
                default:
                    return false;
            }
        }
    },

    TBODY(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.OK, ALIGN, CHAR, CHAROFF, VALIGN)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TR);
        }
    },

    TD(BlockType.TABLE_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.OK, COLSPAN, ROWSPAN, HEADERS, SCOPE, ABBR, AXIS,
                ALIGN, CHAR, CHAROFF, VALIGN),
            attrs(AttrKind.USE_CSS, WIDTH, BGCOLOR, HEIGHT, NOWRAP)),

    TFOOT(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            attrs(AttrKind.OK, ALIGN, CHAR, CHAROFF, VALIGN)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TR);
        }
    },

    TH(BlockType.TABLE_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.OK, COLSPAN, ROWSPAN, HEADERS, SCOPE, ABBR, AXIS,
                ALIGN, CHAR, CHAROFF, VALIGN),
            attrs(AttrKind.USE_CSS, WIDTH, BGCOLOR, HEIGHT, NOWRAP)),

    THEAD(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            attrs(AttrKind.OK, ALIGN, CHAR, CHAROFF, VALIGN)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TR);
        }
    },

    TITLE(BlockType.OTHER, EndKind.REQUIRED),

    TR(BlockType.TABLE_ITEM, EndKind.OPTIONAL,
            attrs(AttrKind.OK, ALIGN, CHAR, CHAROFF, VALIGN),
            attrs(AttrKind.USE_CSS, BGCOLOR)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TH) || (t == TD);
        }
    },

    TT(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    U(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    UL(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.OK, COMPACT, TYPE)) { // OK in 4.01; not allowed in 5
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == LI);
        }
    },

    VAR(BlockType.INLINE, EndKind.REQUIRED);

    /**
     * Enum representing the type of HTML element.
     */
    public static enum BlockType {
        BLOCK,
        INLINE,
        LIST_ITEM,
        TABLE_ITEM,
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
        ACCEPTS_BLOCK,
        ACCEPTS_INLINE,
        EXPECT_CONTENT,
        NO_NEST
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
        VALUE,
        VSPACE,
        WIDTH;

        public String getText() {
            return StringUtils.toLowerCase(name());
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

    HtmlTag(BlockType blockType, EndKind endKind, AttrMap... attrMaps) {
        this(blockType, endKind, Collections.<Flag>emptySet(), attrMaps);
    }

    HtmlTag(BlockType blockType, EndKind endKind, Set<Flag> flags, AttrMap... attrMaps) {
        this.blockType = blockType;
        this.endKind = endKind;
        this.flags = flags;
        this.attrs = new EnumMap<Attr,AttrKind>(Attr.class);
        for (Map<Attr,AttrKind> m: attrMaps)
            this.attrs.putAll(m);
        attrs.put(Attr.CLASS, AttrKind.OK);
        attrs.put(Attr.ID, AttrKind.OK);
        attrs.put(Attr.STYLE, AttrKind.OK);
    }

    public boolean accepts(HtmlTag t) {
        if (flags.contains(Flag.ACCEPTS_BLOCK) && flags.contains(Flag.ACCEPTS_INLINE)) {
            return (t.blockType == BlockType.BLOCK) || (t.blockType == BlockType.INLINE);
        } else if (flags.contains(Flag.ACCEPTS_BLOCK)) {
            return (t.blockType == BlockType.BLOCK);
        } else if (flags.contains(Flag.ACCEPTS_INLINE)) {
            return (t.blockType == BlockType.INLINE);
        } else
            switch (blockType) {
                case BLOCK:
                case INLINE:
                    return (t.blockType == BlockType.INLINE);
                case OTHER:
                    // OTHER tags are invalid in doc comments, and will be
                    // reported separately, so silently accept/ignore any content
                    return true;
                default:
                    // any combination which could otherwise arrive here
                    // ought to have been handled in an overriding method
                    throw new AssertionError(this + ":" + t);
            }
    }

    public boolean acceptsText() {
        // generally, anywhere we can put text we can also put inline tag
        // so check if a typical inline tag is allowed
        return accepts(B);
    }

    public String getText() {
        return StringUtils.toLowerCase(name());
    }

    public Attr getAttr(Name attrName) {
        return Attr.index.get(StringUtils.toLowerCase(attrName.toString()));
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
        return index.get(StringUtils.toLowerCase(tagName.toString()));
    }

}
