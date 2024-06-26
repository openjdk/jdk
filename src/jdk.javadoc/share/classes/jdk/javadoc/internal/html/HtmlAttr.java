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
    FORM,
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

    /**
     * The "external" name of this attribute.
     */
    private final String name;
    /**
     * Whether this is a global attribute, that can be used with all HTML tags.
     */
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

    public enum AttrKind {
        OK,
        INVALID,
        OBSOLETE,
        HTML4
    }

    HtmlAttr() {
        this(false);
    }

    HtmlAttr(boolean flag) {
        name = name().toLowerCase(Locale.ROOT).replace("_", "-");
        isGlobal = flag;
    }

    /**
     * {@return the "external" name of this attribute}
     * The external name is the name of the enum member in lower case with {@code _} replaced by {@code -}.
     */
    public String getName() {
        return name;
    }

    /**
     * {@return whether this attribute is a global attribute, that may appear on all tags}
     */
    public boolean isGlobal() {
        return isGlobal;
    }

    // FIXME: this is used in doclint Checker, when generating messages
    @Override
    public String toString() {
        return name;
    }

    private static final Map<String, HtmlAttr> index = new HashMap<>();
    static {
        for (HtmlAttr t : values()) {
            index.put(t.getName(), t);
        }
    }

    /**
     * {@return the attribute with the given name, or {@code null} if there is no known attribute}
     *
     * @param name the name
     */
    public static HtmlAttr of(CharSequence name) {
        return index.get(name.toString().toLowerCase(Locale.ROOT));
    }
}
