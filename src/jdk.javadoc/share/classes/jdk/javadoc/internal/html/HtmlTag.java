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

import java.io.Serial;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Name;

import static jdk.javadoc.internal.html.HtmlAttr.*;

/**
 * Enum representing HTML tags.
 *
 * The intent of this class is to embody the semantics of the current HTML standard,
 * to the extent supported/used by javadoc.
 *
 * @see <a href="https://html.spec.whatwg.org/multipage/">HTML Living Standard</a>
 * @see <a href="http://www.w3.org/TR/html5/">HTML 5 Specification</a>
 * @see <a href="http://www.w3.org/TR/REC-html40/">HTML 4.01 Specification</a>
 * @see <a href="http://www.w3.org/TR/wai-aria/">WAI-ARIA Specification</a>
 * @see <a href="http://www.w3.org/TR/aria-in-html/#recommendations-table">WAI-ARIA Recommendations Table</a>
 */
public enum HtmlTag {
    A(BlockType.INLINE, EndKind.REQUIRED,
            attrs(AttrKind.OK, HREF, TARGET, ID),
            attrs(AttrKind.HTML4, REV, CHARSET, SHAPE, COORDS, NAME)),

    ABBR(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    ACRONYM(ElemKind.HTML4, BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    ADDRESS(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    ARTICLE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    ASIDE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    B(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    BDI(BlockType.INLINE, EndKind.REQUIRED),

    BIG(ElemKind.HTML4, BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    BLOCKQUOTE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    BODY(BlockType.OTHER, EndKind.REQUIRED),

    BR(BlockType.INLINE, EndKind.NONE,
            attrs(AttrKind.HTML4, CLEAR)),

    BUTTON(BlockType.INLINE, EndKind.REQUIRED,
            attrs(AttrKind.OK, FORM, NAME, TYPE, VALUE)),

    CAPTION(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_INLINE, Flag.EXPECT_CONTENT),
            attrs(AttrKind.HTML4, ALIGN)),

    CENTER(ElemKind.HTML4, BlockType.BLOCK, EndKind.REQUIRED,
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
            attrs(AttrKind.OK, HtmlAttr.CITE, HtmlAttr.DATETIME)),

    DETAILS(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    DFN(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    DIV(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.HTML4, ALIGN)),

    DL(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.HTML4, COMPACT)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == DT) || (t == DD);
        }
    },

    DT(BlockType.LIST_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_INLINE, Flag.EXPECT_CONTENT)),

    EM(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.NO_NEST)),

    FONT(ElemKind.HTML4, BlockType.INLINE, EndKind.REQUIRED, // tag itself is deprecated
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.HTML4, SIZE, COLOR, FACE)),

    FOOTER(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return switch (t) {
                case HEADER, FOOTER, MAIN -> false;
                default -> (t.blockType == BlockType.BLOCK) || (t.blockType == BlockType.INLINE);
            };
        }
    },

    FIGURE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    FIGCAPTION(BlockType.BLOCK, EndKind.REQUIRED),

    FRAME(ElemKind.HTML4, BlockType.OTHER, EndKind.NONE),

    FRAMESET(ElemKind.HTML4, BlockType.OTHER, EndKind.REQUIRED),

    H1(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.HTML4, ALIGN)),
    H2(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.HTML4, ALIGN)),
    H3(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.HTML4, ALIGN)),
    H4(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.HTML4, ALIGN)),
    H5(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.HTML4, ALIGN)),
    H6(BlockType.BLOCK, EndKind.REQUIRED,
            attrs(AttrKind.HTML4, ALIGN)),

    HEAD(BlockType.OTHER, EndKind.REQUIRED),

    HEADER(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return switch (t) {
                case HEADER, FOOTER, MAIN -> false;
                default -> (t.blockType == BlockType.BLOCK) || (t.blockType == BlockType.INLINE);
            };
        }
    },

    HR(BlockType.BLOCK, EndKind.NONE,
            attrs(AttrKind.HTML4, WIDTH, ALIGN, NOSHADE, SIZE)),

    HTML(BlockType.OTHER, EndKind.REQUIRED),

    I(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    IFRAME(BlockType.OTHER, EndKind.REQUIRED),

    IMG(BlockType.INLINE, EndKind.NONE,
            attrs(AttrKind.OK, SRC, ALT, HEIGHT, WIDTH, CROSSORIGIN),
            attrs(AttrKind.HTML4, NAME, ALIGN, HSPACE, VSPACE, BORDER)),

    INPUT(BlockType.INLINE, EndKind.NONE,
            attrs(AttrKind.OK, NAME, TYPE, VALUE)),

    INS(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST),
            attrs(AttrKind.OK, HtmlAttr.CITE, HtmlAttr.DATETIME)),

    KBD(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    LABEL(BlockType.INLINE, EndKind.REQUIRED),

    LI(BlockType.LIST_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.OK, VALUE),
            attrs(AttrKind.HTML4, TYPE)),

    LINK(BlockType.INLINE, EndKind.NONE,
            attrs(AttrKind.OK, REL)),

    MAIN(BlockType.OTHER, EndKind.REQUIRED),

    MARK(BlockType.INLINE, EndKind.REQUIRED),

    MENU(BlockType.BLOCK, EndKind.REQUIRED) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == LI);
        }
    },

    META(BlockType.OTHER, EndKind.NONE),

    NAV(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    NOFRAMES(ElemKind.HTML4, BlockType.OTHER, EndKind.REQUIRED),

    NOSCRIPT(BlockType.BLOCK, EndKind.REQUIRED),

    OL(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.OK, START, TYPE, REVERSED),
            attrs(AttrKind.HTML4, COMPACT)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == LI);
        }
    },

    P(BlockType.BLOCK, EndKind.OPTIONAL,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.HTML4, ALIGN)),

    PRE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.HTML4, WIDTH)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return switch (t) {
                case IMG, BIG, SMALL, SUB, SUP -> false;
                default -> (t.blockType == BlockType.INLINE);
            };
        }
    },

    Q(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    S(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    SAMP(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    SCRIPT(BlockType.INLINE, EndKind.REQUIRED,
            attrs(AttrKind.OK, SRC, TYPE)),

    SECTION(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    SMALL(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    SPAN(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    STRIKE(ElemKind.HTML4, BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    STRONG(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),

    STYLE(BlockType.OTHER, EndKind.REQUIRED),

    SUB(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    SUMMARY(BlockType.BLOCK, EndKind.REQUIRED),

    SUP(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),

    TABLE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.OK, BORDER),
            attrs(AttrKind.HTML4, HtmlAttr.SUMMARY, CELLPADDING, CELLSPACING,
                    HtmlAttr.FRAME, RULES, WIDTH, ALIGN, BGCOLOR)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return switch (t) {
                case CAPTION, COLGROUP, THEAD, TBODY, TFOOT, TR -> true;
                default -> false;
            };
        }
    },

    TBODY(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.HTML4, ALIGN, VALIGN, CHAR, CHAROFF)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TR);
        }
    },

    TD(BlockType.TABLE_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.OK, COLSPAN, ROWSPAN, HEADERS),
            attrs(AttrKind.HTML4, AXIS, HtmlAttr.ABBR, SCOPE, ALIGN, VALIGN, CHAR, CHAROFF,
                    WIDTH, BGCOLOR, HEIGHT, NOWRAP)),

    TEMPLATE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),

    TFOOT(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            attrs(AttrKind.HTML4, ALIGN, VALIGN, CHAR, CHAROFF)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TR);
        }
    },

    TH(BlockType.TABLE_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.OK, COLSPAN, ROWSPAN, HEADERS, SCOPE, HtmlAttr.ABBR),
            attrs(AttrKind.HTML4, WIDTH, BGCOLOR, HEIGHT, NOWRAP, AXIS, ALIGN, CHAR, CHAROFF, VALIGN)),

    THEAD(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            attrs(AttrKind.HTML4, ALIGN, VALIGN, CHAR, CHAROFF)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TR);
        }
    },

    TIME(BlockType.INLINE, EndKind.REQUIRED),

    TITLE(BlockType.OTHER, EndKind.REQUIRED),

    TR(BlockType.TABLE_ITEM, EndKind.OPTIONAL,
            attrs(AttrKind.HTML4, ALIGN, CHAR, CHAROFF, BGCOLOR, VALIGN)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TH) || (t == TD);
        }
    },

    TT(ElemKind.HTML4, BlockType.INLINE, EndKind.REQUIRED,
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

    WBR(BlockType.INLINE, EndKind.NONE),

    VAR(BlockType.INLINE, EndKind.REQUIRED);

    /**
     * Enum representing the supportability of HTML element.
     */
    public enum ElemKind {
        OK,
        INVALID,
        OBSOLETE,
        HTML4
    }

    /**
     * Enum representing the type of HTML element.
     */
    // See JDK-8337586 for suggestions
    public enum BlockType {
        BLOCK,
        INLINE,
        LIST_ITEM,
        TABLE_ITEM,
        OTHER
    }

    /**
     * Enum representing HTML end tag requirement.
     */
    public enum EndKind {
        NONE,
        OPTIONAL,
        REQUIRED
    }

    public enum Flag {
        ACCEPTS_BLOCK,
        ACCEPTS_INLINE,
        EXPECT_CONTENT,
        NO_NEST
    }

    // This class exists to avoid warnings from using parameterized vararg type
    // Map<Attr,AttrKind> in signature of HtmlTag constructor.
    private static class AttrMap extends EnumMap<HtmlAttr,AttrKind> {
        @Serial
        private static final long serialVersionUID = 0;
        AttrMap() {
            super(HtmlAttr.class);
        }
    }


    public final ElemKind elemKind;
    public final BlockType blockType;
    public final EndKind endKind;
    public final Set<Flag> flags;
    private final Map<HtmlAttr,AttrKind> attrs;

    HtmlTag(BlockType blockType, EndKind endKind, AttrMap... attrMaps) {
        this(ElemKind.OK, blockType, endKind, Set.of(), attrMaps);
    }

    HtmlTag(ElemKind elemKind, BlockType blockType, EndKind endKind, AttrMap... attrMaps) {
        this(elemKind, blockType, endKind, Set.of(), attrMaps);
    }

    HtmlTag(BlockType blockType, EndKind endKind, Set<Flag> flags, AttrMap... attrMaps) {
        this(ElemKind.OK, blockType, endKind, flags, attrMaps);
    }

    HtmlTag(ElemKind elemKind, BlockType blockType, EndKind endKind, Set<Flag> flags, AttrMap... attrMaps) {
        this.elemKind = elemKind;
        this.blockType = blockType;
        this.endKind = endKind;
        this.flags = flags;
        this.attrs = new EnumMap<>(HtmlAttr.class);
        for (Map<HtmlAttr,AttrKind> m: attrMaps)
            this.attrs.putAll(m);
    }

    public boolean accepts(HtmlTag t) {
        if (flags.contains(Flag.ACCEPTS_BLOCK) && flags.contains(Flag.ACCEPTS_INLINE)) {
            return (t.blockType == BlockType.BLOCK) || (t.blockType == BlockType.INLINE);
        } else if (flags.contains(Flag.ACCEPTS_BLOCK)) {
            return (t.blockType == BlockType.BLOCK);
        } else if (flags.contains(Flag.ACCEPTS_INLINE)) {
            return (t.blockType == BlockType.INLINE);
        } else {
            // any combination which could otherwise arrive here
            // ought to have been handled in an overriding method
            return switch (blockType) {
                case BLOCK, INLINE -> (t.blockType == BlockType.INLINE);
                case OTHER ->
                    // OTHER tags are invalid in doc comments, and will be
                    // reported separately, so silently accept/ignore any content
                        true;
                default -> throw new AssertionError(this + ":" + t);
            };
        }
    }

    public boolean acceptsText() {
        // generally, anywhere we can put text we can also put inline tag
        // so check if a typical inline tag is allowed
        return accepts(B);
    }

    public String getName() {
        return name().toLowerCase(Locale.ROOT).replace("_", "-");
    }

    public HtmlAttr getAttr(Name attrName) {
        return HtmlAttr.of(attrName);
    }

    public AttrKind getAttrKind(Name attrName) {
        HtmlAttr attr = getAttr(attrName);
        if (attr == null) {
            return AttrKind.INVALID;
        }
        return attr.isGlobal() ?
                AttrKind.OK :
                attrs.getOrDefault(attr, AttrKind.INVALID);
    }

    private static AttrMap attrs(AttrKind k, HtmlAttr... attrs) {
        AttrMap map = new AttrMap();
        for (HtmlAttr a : attrs) map.put(a, k);
        return map;
    }

    private static final Map<String, HtmlTag> index = new HashMap<>();
    static {
        for (HtmlTag t: values()) {
            index.put(t.getName(), t);
        }
    }

    public static HtmlTag of(CharSequence tagName) {
        return index.get(tagName.toString().toLowerCase(Locale.ROOT));
    }
}
