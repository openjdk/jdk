/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Name;

import com.sun.tools.javac.util.StringUtils;

import static com.sun.tools.doclint.HtmlTag.Attr.*;

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
 * @see <a href="http://www.w3.org/TR/wai-aria/ ">WAI-ARIA Specification</a>
 * @see <a href="http://www.w3.org/TR/aria-in-html/#recommendations-table">WAI-ARIA Recommendations Table</a>
 * @author Bhavesh Patel
 * @author Jonathan Gibbons (revised)
 */
public enum HtmlTag {
    A(BlockType.INLINE, EndKind.REQUIRED,
            attrs(AttrKind.ALL, HREF, TARGET, ID),
            attrs(AttrKind.HTML4, REV, CHARSET, SHAPE, COORDS, NAME)),

    ABBR(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    ACRONYM(HtmlVersion.HTML4, BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    ADDRESS(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    ARTICLE(HtmlVersion.HTML5, BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    ASIDE(HtmlVersion.HTML5, BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    B(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    BDI(HtmlVersion.HTML5, BlockType.INLINE, EndKind.REQUIRED),

    BIG(HtmlVersion.HTML4, BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    BLOCKQUOTE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    BODY(BlockType.OTHER, EndKind.REQUIRED),

    BR(BlockType.INLINE, EndKind.NONE,
            attrs(AttrKind.USE_CSS, CLEAR)),

    CAPTION(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_INLINE, Flag.EXPECT_CONTENT),
            attrs(AttrKind.USE_CSS, ALIGN)),

    CENTER(HtmlVersion.HTML4, BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    CITE(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    CODE(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    COL(BlockType.TABLE_ITEM, EndKind.NONE,
            attrs(AttrKind.HTML4, ALIGN, CHAR, CHAROFF, VALIGN, WIDTH)),

    COLGROUP(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            attrs(AttrKind.HTML4, ALIGN, CHAR, CHAROFF, VALIGN, WIDTH)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == COL);
        }
    },

    DD(BlockType.LIST_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE, Flag.EXPECT_CONTENT)),

    DEL(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST),
            attrs(AttrKind.ALL, Attr.CITE, Attr.DATETIME)),

    DFN(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    DIV(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.USE_CSS, ALIGN)),

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

    FONT(HtmlVersion.HTML4, BlockType.INLINE, EndKind.REQUIRED, // tag itself is deprecated
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.USE_CSS, SIZE, COLOR, FACE)),

    FOOTER(HtmlVersion.HTML5, BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)) {
        @Override
        public boolean accepts(HtmlTag t) {
            switch (t) {
                case HEADER: case FOOTER: case MAIN:
                    return false;
                default:
                    return (t.blockType == BlockType.BLOCK) || (t.blockType == BlockType.INLINE);
            }
        }
    },

    FIGURE(HtmlVersion.HTML5, BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    FIGCAPTION(HtmlVersion.HTML5, BlockType.BLOCK, EndKind.REQUIRED),

    FRAME(HtmlVersion.HTML4, BlockType.OTHER, EndKind.NONE),

    FRAMESET(HtmlVersion.HTML4, BlockType.OTHER, EndKind.REQUIRED),

    H1(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.USE_CSS, ALIGN)),
    H2(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.USE_CSS, ALIGN)),
    H3(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.USE_CSS, ALIGN)),
    H4(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.USE_CSS, ALIGN)),
    H5(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.USE_CSS, ALIGN)),
    H6(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.USE_CSS, ALIGN)),

    HEAD(BlockType.OTHER, EndKind.REQUIRED),

    HEADER(HtmlVersion.HTML5, BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)) {
        @Override
        public boolean accepts(HtmlTag t) {
            switch (t) {
                case HEADER: case FOOTER: case MAIN:
                    return false;
                default:
                    return (t.blockType == BlockType.BLOCK) || (t.blockType == BlockType.INLINE);
            }
        }
    },

    HR(BlockType.BLOCK, EndKind.NONE,
            attrs(AttrKind.HTML4, WIDTH),
            attrs(AttrKind.USE_CSS, ALIGN, NOSHADE, SIZE)),

    HTML(BlockType.OTHER, EndKind.REQUIRED),

    I(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    IFRAME(BlockType.OTHER, EndKind.REQUIRED),

    IMG(BlockType.INLINE, EndKind.NONE,
            attrs(AttrKind.ALL, SRC, ALT, HEIGHT, WIDTH),
            attrs(AttrKind.HTML5, CROSSORIGIN),
            attrs(AttrKind.OBSOLETE, NAME),
            attrs(AttrKind.USE_CSS, ALIGN, HSPACE, VSPACE, BORDER)),

    INS(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST),
            attrs(AttrKind.ALL, Attr.CITE, Attr.DATETIME)),

    KBD(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    LI(BlockType.LIST_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.ALL, VALUE),
            attrs(AttrKind.USE_CSS, TYPE)),

    LINK(BlockType.OTHER, EndKind.NONE),

    MAIN(HtmlVersion.HTML5, BlockType.OTHER, EndKind.REQUIRED),

    MARK(HtmlVersion.HTML5, BlockType.INLINE, EndKind.REQUIRED),

    MENU(BlockType.BLOCK, EndKind.REQUIRED) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == LI);
        }
    },

    META(BlockType.OTHER, EndKind.NONE),

    NAV(HtmlVersion.HTML5, BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    NOFRAMES(HtmlVersion.HTML4, BlockType.OTHER, EndKind.REQUIRED),

    NOSCRIPT(BlockType.BLOCK, EndKind.REQUIRED),

    OL(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.ALL, START, TYPE),
            attrs(AttrKind.HTML5, REVERSED),
            attrs(AttrKind.USE_CSS, COMPACT)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == LI);
        }
    },

    P(BlockType.BLOCK, EndKind.OPTIONAL,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.USE_CSS, ALIGN)),

    PRE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.USE_CSS, WIDTH)) {
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

    Q(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    S(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    SAMP(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    SCRIPT(BlockType.OTHER, EndKind.REQUIRED,
            attrs(AttrKind.ALL, SRC)),

    SECTION(HtmlVersion.HTML5, BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    SMALL(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    SPAN(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    STRIKE(HtmlVersion.HTML4, BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    STRONG(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    STYLE(BlockType.OTHER, EndKind.REQUIRED),

    SUB(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    SUP(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    TABLE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.ALL, BORDER),
            attrs(AttrKind.HTML4, SUMMARY, CELLPADDING, CELLSPACING, Attr.FRAME, RULES, WIDTH),
            attrs(AttrKind.USE_CSS, ALIGN, BGCOLOR)) {
        @Override
        public boolean accepts(HtmlTag t) {
            switch (t) {
                case CAPTION:
                case COLGROUP:
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
            attrs(AttrKind.ALL, VALIGN),
            attrs(AttrKind.HTML4, ALIGN, CHAR, CHAROFF)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TR);
        }
    },

    TD(BlockType.TABLE_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.ALL, COLSPAN, ROWSPAN, HEADERS, VALIGN),
            attrs(AttrKind.HTML4, AXIS, Attr.ABBR, SCOPE, ALIGN, CHAR, CHAROFF),
            attrs(AttrKind.USE_CSS, WIDTH, BGCOLOR, HEIGHT, NOWRAP)),

    TEMPLATE(HtmlVersion.HTML5, BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    TFOOT(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            attrs(AttrKind.ALL, VALIGN),
            attrs(AttrKind.HTML4, ALIGN, CHAR, CHAROFF)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TR);
        }
    },

    TH(BlockType.TABLE_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.ALL, COLSPAN, ROWSPAN, HEADERS, SCOPE, Attr.ABBR,
                VALIGN),
            attrs(AttrKind.HTML4, AXIS, ALIGN, CHAR, CHAROFF),
            attrs(AttrKind.USE_CSS, WIDTH, BGCOLOR, HEIGHT, NOWRAP)),

    THEAD(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            attrs(AttrKind.ALL, VALIGN),
            attrs(AttrKind.HTML4, ALIGN, CHAR, CHAROFF)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TR);
        }
    },

    TIME(HtmlVersion.HTML5, BlockType.INLINE, EndKind.REQUIRED),

    TITLE(BlockType.OTHER, EndKind.REQUIRED),

    TR(BlockType.TABLE_ITEM, EndKind.OPTIONAL,
            attrs(AttrKind.ALL, VALIGN),
            attrs(AttrKind.HTML4, ALIGN, CHAR, CHAROFF),
            attrs(AttrKind.USE_CSS, BGCOLOR)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TH) || (t == TD);
        }
    },

    TT(HtmlVersion.HTML4, BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    U(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    UL(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.HTML4, COMPACT, TYPE)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == LI);
        }
    },

    WBR(HtmlVersion.HTML5, BlockType.INLINE, EndKind.REQUIRED),

    VAR(BlockType.INLINE, EndKind.REQUIRED);

    /**
     * Enum representing the type of HTML element.
     */
    public static enum BlockType {
        BLOCK,
        INLINE,
        LIST_ITEM,
        TABLE_ITEM,
        OTHER
    }

    /**
     * Enum representing HTML end tag requirement.
     */
    public static enum EndKind {
        NONE,
        OPTIONAL,
        REQUIRED
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
        ALINK,
        ALT,
        ARIA_ACTIVEDESCENDANT,
        ARIA_CONTROLS,
        ARIA_DESCRIBEDBY,
        ARIA_EXPANDED,
        ARIA_LABEL,
        ARIA_LABELLEDBY,
        ARIA_LEVEL,
        ARIA_MULTISELECTABLE,
        ARIA_OWNS,
        ARIA_POSINSET,
        ARIA_SETSIZE,
        ARIA_READONLY,
        ARIA_REQUIRED,
        ARIA_SELECTED,
        ARIA_SORT,
        AXIS,
        BACKGROUND,
        BGCOLOR,
        BORDER,
        CELLSPACING,
        CELLPADDING,
        CHAR,
        CHAROFF,
        CHARSET,
        CITE,
        CLEAR,
        CLASS,
        COLOR,
        COLSPAN,
        COMPACT,
        COORDS,
        CROSSORIGIN,
        DATETIME,
        FACE,
        FRAME,
        FRAMEBORDER,
        HEADERS,
        HEIGHT,
        HREF,
        HSPACE,
        ID,
        LINK,
        LONGDESC,
        MARGINHEIGHT,
        MARGINWIDTH,
        NAME,
        NOSHADE,
        NOWRAP,
        PROFILE,
        REV,
        REVERSED,
        ROLE,
        ROWSPAN,
        RULES,
        SCHEME,
        SCOPE,
        SCROLLING,
        SHAPE,
        SIZE,
        SPACE,
        SRC,
        START,
        STYLE,
        SUMMARY,
        TARGET,
        TEXT,
        TYPE,
        VALIGN,
        VALUE,
        VERSION,
        VLINK,
        VSPACE,
        WIDTH;

        private final String name;

        Attr() {
            name = StringUtils.toLowerCase(name().replace("_", "-"));
        }

        public String getText() {
            return name;
        }

        static final Map<String,Attr> index = new HashMap<>();
        static {
            for (Attr t: values()) {
                index.put(t.getText(), t);
            }
        }
    }

    public static enum AttrKind {
        HTML4,
        HTML5,
        INVALID,
        OBSOLETE,
        USE_CSS,
        ALL
    }

    // This class exists to avoid warnings from using parameterized vararg type
    // Map<Attr,AttrKind> in signature of HtmlTag constructor.
    private static class AttrMap extends EnumMap<Attr,AttrKind>  {
        private static final long serialVersionUID = 0;
        AttrMap() {
            super(Attr.class);
        }
    }


    public final HtmlVersion allowedVersion;
    public final BlockType blockType;
    public final EndKind endKind;
    public final Set<Flag> flags;
    private final Map<Attr,AttrKind> attrs;

    HtmlTag(BlockType blockType, EndKind endKind, AttrMap... attrMaps) {
        this(HtmlVersion.ALL, blockType, endKind, Collections.emptySet(), attrMaps);
    }

    HtmlTag(HtmlVersion allowedVersion, BlockType blockType, EndKind endKind, AttrMap... attrMaps) {
        this(allowedVersion, blockType, endKind, Collections.emptySet(), attrMaps);
    }

    HtmlTag(BlockType blockType, EndKind endKind, Set<Flag> flags, AttrMap... attrMaps) {
        this(HtmlVersion.ALL, blockType, endKind, flags, attrMaps);
    }

    HtmlTag(HtmlVersion allowedVersion, BlockType blockType, EndKind endKind, Set<Flag> flags, AttrMap... attrMaps) {
        this.allowedVersion = allowedVersion;
        this.blockType = blockType;
        this.endKind = endKind;
        this.flags = flags;
        this.attrs = new EnumMap<>(Attr.class);
        for (Map<Attr,AttrKind> m: attrMaps)
            this.attrs.putAll(m);
        attrs.put(Attr.CLASS, AttrKind.ALL);
        attrs.put(Attr.ID, AttrKind.ALL);
        attrs.put(Attr.STYLE, AttrKind.ALL);
        attrs.put(Attr.ROLE, AttrKind.HTML5);
        // for now, assume that all ARIA attributes are allowed on all tags.
        attrs.put(Attr.ARIA_ACTIVEDESCENDANT, AttrKind.HTML5);
        attrs.put(Attr.ARIA_CONTROLS, AttrKind.HTML5);
        attrs.put(Attr.ARIA_DESCRIBEDBY, AttrKind.HTML5);
        attrs.put(Attr.ARIA_EXPANDED, AttrKind.HTML5);
        attrs.put(Attr.ARIA_LABEL, AttrKind.HTML5);
        attrs.put(Attr.ARIA_LABELLEDBY, AttrKind.HTML5);
        attrs.put(Attr.ARIA_LEVEL, AttrKind.HTML5);
        attrs.put(Attr.ARIA_MULTISELECTABLE, AttrKind.HTML5);
        attrs.put(Attr.ARIA_OWNS, AttrKind.HTML5);
        attrs.put(Attr.ARIA_POSINSET, AttrKind.HTML5);
        attrs.put(Attr.ARIA_READONLY, AttrKind.HTML5);
        attrs.put(Attr.ARIA_REQUIRED, AttrKind.HTML5);
        attrs.put(Attr.ARIA_SELECTED, AttrKind.HTML5);
        attrs.put(Attr.ARIA_SETSIZE, AttrKind.HTML5);
        attrs.put(Attr.ARIA_SORT, AttrKind.HTML5);
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

    private static final Map<String, HtmlTag> index = new HashMap<>();
    static {
        for (HtmlTag t: values()) {
            index.put(t.getText(), t);
        }
    }

    public static HtmlTag get(Name tagName) {
        return index.get(StringUtils.toLowerCase(tagName.toString()));
    }
}
