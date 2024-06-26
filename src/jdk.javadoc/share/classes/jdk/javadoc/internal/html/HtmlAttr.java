package jdk.javadoc.internal.html;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public enum HtmlAttr {

    ABBR,
    ACCESSKEY(true),
    ALIGN,
    ALINK,
    ALT,
    ARIA_ACTIVEDESCENDANT(true),
    ARIA_CONTROLS(true),
    ARIA_DESCRIBEDBY(true),
    ARIA_EXPANDED(true),
    ARIA_LABEL(true),
    ARIA_LABELLEDBY(true),
    ARIA_LEVEL(true),
    ARIA_MULTISELECTABLE(true),
    ARIA_ORIENTATION,
    ARIA_OWNS(true),
    ARIA_POSINSET(true),
    ARIA_READONLY(true),
    ARIA_REQUIRED(true),
    ARIA_SELECTED(true),
    ARIA_SETSIZE(true),
    ARIA_SORT(true),
    AUTOCAPITALIZE(true),
    AUTOCOMPLETE,
    AUTOFOCUS(true),
    AXIS,
    BACKGROUND,
    BGCOLOR,
    BORDER,
    CELLPADDING,
    CELLSPACING,
    CHAR,
    CHAROFF,
    CHARSET,
    CHECKED,
    CITE,
    CLASS(true),
    CLEAR,
    COLOR,
    COLS,
    COLSPAN,
    COMPACT,
    CONTENT,
    CONTENTEDITABLE(true),
    COORDS,
    CROSSORIGIN,
    DATA_COPIED, // custom HTML5 data attribute
    DATETIME,
    DIR(true),
    DISABLED,
    DRAGGABLE(true),
    ENTERKEYHINT(true),
    FACE,
    FOR,
    FRAME,
    FRAMEBORDER,
    HEADERS,
    HEIGHT,
    HIDDEN(true),
    HREF,
    HSPACE,
    HTTP_EQUIV,
    ID(true),
    INERT(true),
    INPUTMODE(true),
    IS(true),
    ITEMID(true),
    ITEMPROP(true),
    ITEMREF(true),
    ITEMSCOPE(true),
    ITEMTYPE(true),
    LANG(true),
    LINK,
    LONGDESC,
    MARGINHEIGHT,
    MARGINWIDTH,
    NAME,
    NONCE(true),
    NOSHADE,
    NOWRAP,
    ONCLICK,
    ONKEYDOWN,
    ONLOAD,
    PLACEHOLDER,
    POPOVER(true),
    PROFILE,
    REL,
    REV,
    REVERSED,
    ROLE,
    ROWS,
    ROWSPAN,
    RULES,
    SCHEME,
    SCOPE,
    SCROLLING,
    SHAPE,
    SIZE,
    SPACE,
    SPELLCHECK(true),
    SRC,
    START,
    STYLE(true),
    SUMMARY,
    TABINDEX(true),
    TARGET,
    TEXT,
    TITLE(true),
    TRANSLATE(true),
    TYPE,
    VALIGN,
    VALUE,
    VERSION,
    VLINK,
    VSPACE,
    WIDTH,
    WRITINGSUGGESTIONS(true);

    private final String text;
    private final boolean isGlobal;

    public enum Role {

        BANNER,
        CONTENTINFO,
        MAIN,
        NAVIGATION,
        REGION;

        private final String role;

        Role() {
            role = name().toLowerCase(Locale.ROOT);
        }

        public String toString() {
            return role;
        }
    }

    public enum InputType {

        CHECKBOX,
        RESET,
        TEXT;

        private final String type;

        InputType() {
            type = name().toLowerCase(Locale.ROOT);
        }

        public String toString() {
            return type;
        }
    }

    HtmlAttr() {
        this(false);
    }

    HtmlAttr(boolean flag) {
        text = name().toLowerCase(Locale.ROOT).replace("_", "-");
        isGlobal = flag;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return text;
    }

    static final Map<String, HtmlAttr> index = new HashMap<>();

    static {
        for (HtmlAttr t : values()) {
            index.put(t.getText(), t);
        }
    }

    public enum AttrKind {
        OK,
        INVALID,
        OBSOLETE,
        HTML4
    }
}
