/*
 * Copyright 1998-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package javax.swing.text.html;

import java.io.*;
import java.util.Hashtable;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

/**
 * Constants used in the <code>HTMLDocument</code>.  These
 * are basically tag and attribute definitions.
 *
 * @author  Timothy Prinzing
 * @author  Sunita Mani
 *
 */
public class HTML {

    /**
     * Typesafe enumeration for an HTML tag.  Although the
     * set of HTML tags is a closed set, we have left the
     * set open so that people can add their own tag types
     * to their custom parser and still communicate to the
     * reader.
     */
    public static class Tag {

        /** @since 1.3 */
        public Tag() {}

        /**
         * Creates a new <code>Tag</code> with the specified <code>id</code>,
         * and with <code>causesBreak</code> and <code>isBlock</code>
         * set to <code>false</code>.
         *
         * @param id  the id of the new tag
         */
        protected Tag(String id) {
            this(id, false, false);
        }

        /**
         * Creates a new <code>Tag</code> with the specified <code>id</code>;
         * <code>causesBreak</code> and <code>isBlock</code> are defined
         * by the user.
         *
         * @param id the id of the new tag
         * @param causesBreak  <code>true</code> if this tag
         *    causes a break to the flow of data
         * @param isBlock <code>true</code> if the tag is used
         *    to add structure to a document
         */
        protected Tag(String id, boolean causesBreak, boolean isBlock) {
            name = id;
            this.breakTag = causesBreak;
            this.blockTag = isBlock;
        }

        /**
         * Returns <code>true</code> if this tag is a block
         * tag, which is a tag used to add structure to a
         * document.
         *
         * @return <code>true</code> if this tag is a block
         *   tag, otherwise returns <code>false</code>
         */
        public boolean isBlock() {
            return blockTag;
        }

        /**
         * Returns <code>true</code> if this tag causes a
         * line break to the flow of data, otherwise returns
         * <code>false</code>.
         *
         * @return <code>true</code> if this tag causes a
         *   line break to the flow of data, otherwise returns
         *   <code>false</code>
         */
        public boolean breaksFlow() {
            return breakTag;
        }

        /**
         * Returns <code>true</code> if this tag is pre-formatted,
         * which is true if the tag is either <code>PRE</code> or
         * <code>TEXTAREA</code>.
         *
         * @return <code>true</code> if this tag is pre-formatted,
         *   otherwise returns <code>false</code>
         */
        public boolean isPreformatted() {
            return (this == PRE || this == TEXTAREA);
        }

        /**
         * Returns the string representation of the
         * tag.
         *
         * @return the <code>String</code> representation of the tag
         */
        public String toString() {
            return name;
        }

        /**
         * Returns <code>true</code> if this tag is considered to be a paragraph
         * in the internal HTML model. <code>false</code> - otherwise.
         *
         * @return <code>true</code> if this tag is considered to be a paragraph
         *         in the internal HTML model. <code>false</code> - otherwise.
         * @see HTMLDocument.HTMLReader.ParagraphAction
         */
        boolean isParagraph() {
            return (
                this == P
                   || this == IMPLIED
                   || this == DT
                   || this == H1
                   || this == H2
                   || this == H3
                   || this == H4
                   || this == H5
                   || this == H6
            );
        }

        boolean blockTag;
        boolean breakTag;
        String name;
        boolean unknown;

        // --- Tag Names -----------------------------------

        public static final Tag A = new Tag("a");
        public static final Tag ADDRESS = new Tag("address");
        public static final Tag APPLET = new Tag("applet");
        public static final Tag AREA = new Tag("area");
        public static final Tag B = new Tag("b");
        public static final Tag BASE = new Tag("base");
        public static final Tag BASEFONT = new Tag("basefont");
        public static final Tag BIG = new Tag("big");
        public static final Tag BLOCKQUOTE = new Tag("blockquote", true, true);
        public static final Tag BODY = new Tag("body", true, true);
        public static final Tag BR = new Tag("br", true, false);
        public static final Tag CAPTION = new Tag("caption");
        public static final Tag CENTER = new Tag("center", true, false);
        public static final Tag CITE = new Tag("cite");
        public static final Tag CODE = new Tag("code");
        public static final Tag DD = new Tag("dd", true, true);
        public static final Tag DFN = new Tag("dfn");
        public static final Tag DIR = new Tag("dir", true, true);
        public static final Tag DIV = new Tag("div", true, true);
        public static final Tag DL = new Tag("dl", true, true);
        public static final Tag DT = new Tag("dt", true, true);
        public static final Tag EM = new Tag("em");
        public static final Tag FONT = new Tag("font");
        public static final Tag FORM = new Tag("form", true, false);
        public static final Tag FRAME = new Tag("frame");
        public static final Tag FRAMESET = new Tag("frameset");
        public static final Tag H1 = new Tag("h1", true, true);
        public static final Tag H2 = new Tag("h2", true, true);
        public static final Tag H3 = new Tag("h3", true, true);
        public static final Tag H4 = new Tag("h4", true, true);
        public static final Tag H5 = new Tag("h5", true, true);
        public static final Tag H6 = new Tag("h6", true, true);
        public static final Tag HEAD = new Tag("head", true, true);
        public static final Tag HR = new Tag("hr", true, false);
        public static final Tag HTML = new Tag("html", true, false);
        public static final Tag I = new Tag("i");
        public static final Tag IMG = new Tag("img");
        public static final Tag INPUT = new Tag("input");
        public static final Tag ISINDEX = new Tag("isindex", true, false);
        public static final Tag KBD = new Tag("kbd");
        public static final Tag LI = new Tag("li", true, true);
        public static final Tag LINK = new Tag("link");
        public static final Tag MAP = new Tag("map");
        public static final Tag MENU = new Tag("menu", true, true);
        public static final Tag META = new Tag("meta");
        /*public*/ static final Tag NOBR = new Tag("nobr");
        public static final Tag NOFRAMES = new Tag("noframes", true, true);
        public static final Tag OBJECT = new Tag("object");
        public static final Tag OL = new Tag("ol", true, true);
        public static final Tag OPTION = new Tag("option");
        public static final Tag P = new Tag("p", true, true);
        public static final Tag PARAM = new Tag("param");
        public static final Tag PRE = new Tag("pre", true, true);
        public static final Tag SAMP = new Tag("samp");
        public static final Tag SCRIPT = new Tag("script");
        public static final Tag SELECT = new Tag("select");
        public static final Tag SMALL = new Tag("small");
        public static final Tag SPAN = new Tag("span");
        public static final Tag STRIKE = new Tag("strike");
        public static final Tag S = new Tag("s");
        public static final Tag STRONG = new Tag("strong");
        public static final Tag STYLE = new Tag("style");
        public static final Tag SUB = new Tag("sub");
        public static final Tag SUP = new Tag("sup");
        public static final Tag TABLE = new Tag("table", false, true);
        public static final Tag TD = new Tag("td", true, true);
        public static final Tag TEXTAREA = new Tag("textarea");
        public static final Tag TH = new Tag("th", true, true);
        public static final Tag TITLE = new Tag("title", true, true);
        public static final Tag TR = new Tag("tr", false, true);
        public static final Tag TT = new Tag("tt");
        public static final Tag U = new Tag("u");
        public static final Tag UL = new Tag("ul", true, true);
        public static final Tag VAR = new Tag("var");

        /**
         * All text content must be in a paragraph element.
         * If a paragraph didn't exist when content was
         * encountered, a paragraph is manufactured.
         * <p>
         * This is a tag synthesized by the HTML reader.
         * Since elements are identified by their tag type,
         * we create a some fake tag types to mark the elements
         * that were manufactured.
         */
        public static final Tag IMPLIED = new Tag("p-implied");

        /**
         * All text content is labeled with this tag.
         * <p>
         * This is a tag synthesized by the HTML reader.
         * Since elements are identified by their tag type,
         * we create a some fake tag types to mark the elements
         * that were manufactured.
         */
        public static final Tag CONTENT = new Tag("content");

        /**
         * All comments are labeled with this tag.
         * <p>
         * This is a tag synthesized by the HTML reader.
         * Since elements are identified by their tag type,
         * we create a some fake tag types to mark the elements
         * that were manufactured.
         */
        public static final Tag COMMENT = new Tag("comment");

        static final Tag allTags[]  = {
            A, ADDRESS, APPLET, AREA, B, BASE, BASEFONT, BIG,
            BLOCKQUOTE, BODY, BR, CAPTION, CENTER, CITE, CODE,
            DD, DFN, DIR, DIV, DL, DT, EM, FONT, FORM, FRAME,
            FRAMESET, H1, H2, H3, H4, H5, H6, HEAD, HR, HTML,
            I, IMG, INPUT, ISINDEX, KBD, LI, LINK, MAP, MENU,
            META, NOBR, NOFRAMES, OBJECT, OL, OPTION, P, PARAM,
            PRE, SAMP, SCRIPT, SELECT, SMALL, SPAN, STRIKE, S,
            STRONG, STYLE, SUB, SUP, TABLE, TD, TEXTAREA,
            TH, TITLE, TR, TT, U, UL, VAR
        };

        static {
            // Force HTMLs static initialize to be loaded.
            getTag("html");
        }
    }

    // There is no unique instance of UnknownTag, so we allow it to be
    // Serializable.
    public static class UnknownTag extends Tag implements Serializable {

        /**
         * Creates a new <code>UnknownTag</code> with the specified
         * <code>id</code>.
         * @param id the id of the new tag
         */
        public UnknownTag(String id) {
            super(id);
        }

        /**
         * Returns the hash code which corresponds to the string
         * for this tag.
         */
        public int hashCode() {
            return toString().hashCode();
        }

        /**
         * Compares this object to the specifed object.
         * The result is <code>true</code> if and only if the argument is not
         * <code>null</code> and is an <code>UnknownTag</code> object
         * with the same name.
         *
         * @param     obj   the object to compare this tag with
         * @return    <code>true</code> if the objects are equal;
         *            <code>false</code> otherwise
         */
        public boolean equals(Object obj) {
            if (obj instanceof UnknownTag) {
                return toString().equals(obj.toString());
            }
            return false;
        }

        private void writeObject(java.io.ObjectOutputStream s)
                     throws IOException {
            s.defaultWriteObject();
            s.writeBoolean(blockTag);
            s.writeBoolean(breakTag);
            s.writeBoolean(unknown);
            s.writeObject(name);
        }

        private void readObject(ObjectInputStream s)
            throws ClassNotFoundException, IOException {
            s.defaultReadObject();
            blockTag = s.readBoolean();
            breakTag = s.readBoolean();
            unknown = s.readBoolean();
            name = (String)s.readObject();
        }
    }

    /**
     * Typesafe enumeration representing an HTML
     * attribute.
     */
    public static final class Attribute {

        /**
         * Creates a new <code>Attribute</code> with the specified
         * <code>id</code>.
         *
         * @param id the id of the new <code>Attribute</code>
         */
        Attribute(String id) {
            name = id;
        }

        /**
         * Returns the string representation of this attribute.
         * @return the string representation of this attribute
         */
        public String toString() {
            return name;
        }

        private String name;

        public static final Attribute SIZE = new Attribute("size");
        public static final Attribute COLOR = new Attribute("color");
        public static final Attribute CLEAR = new Attribute("clear");
        public static final Attribute BACKGROUND = new Attribute("background");
        public static final Attribute BGCOLOR = new Attribute("bgcolor");
        public static final Attribute TEXT = new Attribute("text");
        public static final Attribute LINK = new Attribute("link");
        public static final Attribute VLINK = new Attribute("vlink");
        public static final Attribute ALINK = new Attribute("alink");
        public static final Attribute WIDTH = new Attribute("width");
        public static final Attribute HEIGHT = new Attribute("height");
        public static final Attribute ALIGN = new Attribute("align");
        public static final Attribute NAME = new Attribute("name");
        public static final Attribute HREF = new Attribute("href");
        public static final Attribute REL = new Attribute("rel");
        public static final Attribute REV = new Attribute("rev");
        public static final Attribute TITLE = new Attribute("title");
        public static final Attribute TARGET = new Attribute("target");
        public static final Attribute SHAPE = new Attribute("shape");
        public static final Attribute COORDS = new Attribute("coords");
        public static final Attribute ISMAP = new Attribute("ismap");
        public static final Attribute NOHREF = new Attribute("nohref");
        public static final Attribute ALT = new Attribute("alt");
        public static final Attribute ID = new Attribute("id");
        public static final Attribute SRC = new Attribute("src");
        public static final Attribute HSPACE = new Attribute("hspace");
        public static final Attribute VSPACE = new Attribute("vspace");
        public static final Attribute USEMAP = new Attribute("usemap");
        public static final Attribute LOWSRC = new Attribute("lowsrc");
        public static final Attribute CODEBASE = new Attribute("codebase");
        public static final Attribute CODE = new Attribute("code");
        public static final Attribute ARCHIVE = new Attribute("archive");
        public static final Attribute VALUE = new Attribute("value");
        public static final Attribute VALUETYPE = new Attribute("valuetype");
        public static final Attribute TYPE = new Attribute("type");
        public static final Attribute CLASS = new Attribute("class");
        public static final Attribute STYLE = new Attribute("style");
        public static final Attribute LANG = new Attribute("lang");
        public static final Attribute FACE = new Attribute("face");
        public static final Attribute DIR = new Attribute("dir");
        public static final Attribute DECLARE = new Attribute("declare");
        public static final Attribute CLASSID = new Attribute("classid");
        public static final Attribute DATA = new Attribute("data");
        public static final Attribute CODETYPE = new Attribute("codetype");
        public static final Attribute STANDBY = new Attribute("standby");
        public static final Attribute BORDER = new Attribute("border");
        public static final Attribute SHAPES = new Attribute("shapes");
        public static final Attribute NOSHADE = new Attribute("noshade");
        public static final Attribute COMPACT = new Attribute("compact");
        public static final Attribute START = new Attribute("start");
        public static final Attribute ACTION = new Attribute("action");
        public static final Attribute METHOD = new Attribute("method");
        public static final Attribute ENCTYPE = new Attribute("enctype");
        public static final Attribute CHECKED = new Attribute("checked");
        public static final Attribute MAXLENGTH = new Attribute("maxlength");
        public static final Attribute MULTIPLE = new Attribute("multiple");
        public static final Attribute SELECTED = new Attribute("selected");
        public static final Attribute ROWS = new Attribute("rows");
        public static final Attribute COLS = new Attribute("cols");
        public static final Attribute DUMMY = new Attribute("dummy");
        public static final Attribute CELLSPACING = new Attribute("cellspacing");
        public static final Attribute CELLPADDING = new Attribute("cellpadding");
        public static final Attribute VALIGN = new Attribute("valign");
        public static final Attribute HALIGN = new Attribute("halign");
        public static final Attribute NOWRAP = new Attribute("nowrap");
        public static final Attribute ROWSPAN = new Attribute("rowspan");
        public static final Attribute COLSPAN = new Attribute("colspan");
        public static final Attribute PROMPT = new Attribute("prompt");
        public static final Attribute HTTPEQUIV = new Attribute("http-equiv");
        public static final Attribute CONTENT = new Attribute("content");
        public static final Attribute LANGUAGE = new Attribute("language");
        public static final Attribute VERSION = new Attribute("version");
        public static final Attribute N = new Attribute("n");
        public static final Attribute FRAMEBORDER = new Attribute("frameborder");
        public static final Attribute MARGINWIDTH = new Attribute("marginwidth");
        public static final Attribute MARGINHEIGHT = new Attribute("marginheight");
        public static final Attribute SCROLLING = new Attribute("scrolling");
        public static final Attribute NORESIZE = new Attribute("noresize");
        public static final Attribute ENDTAG = new Attribute("endtag");
        public static final Attribute COMMENT = new Attribute("comment");
        static final Attribute MEDIA = new Attribute("media");

        static final Attribute allAttributes[] = {
            FACE,
            COMMENT,
            SIZE,
            COLOR,
            CLEAR,
            BACKGROUND,
            BGCOLOR,
            TEXT,
            LINK,
            VLINK,
            ALINK,
            WIDTH,
            HEIGHT,
            ALIGN,
            NAME,
            HREF,
            REL,
            REV,
            TITLE,
            TARGET,
            SHAPE,
            COORDS,
            ISMAP,
            NOHREF,
            ALT,
            ID,
            SRC,
            HSPACE,
            VSPACE,
            USEMAP,
            LOWSRC,
            CODEBASE,
            CODE,
            ARCHIVE,
            VALUE,
            VALUETYPE,
            TYPE,
            CLASS,
            STYLE,
            LANG,
            DIR,
            DECLARE,
            CLASSID,
            DATA,
            CODETYPE,
            STANDBY,
            BORDER,
            SHAPES,
            NOSHADE,
            COMPACT,
            START,
            ACTION,
            METHOD,
            ENCTYPE,
            CHECKED,
            MAXLENGTH,
            MULTIPLE,
            SELECTED,
            ROWS,
            COLS,
            DUMMY,
            CELLSPACING,
            CELLPADDING,
            VALIGN,
            HALIGN,
            NOWRAP,
            ROWSPAN,
            COLSPAN,
            PROMPT,
            HTTPEQUIV,
            CONTENT,
            LANGUAGE,
            VERSION,
            N,
            FRAMEBORDER,
            MARGINWIDTH,
            MARGINHEIGHT,
            SCROLLING,
            NORESIZE,
            MEDIA,
            ENDTAG
        };
    }

    // The secret to 73, is that, given that the Hashtable contents
    // never change once the static initialization happens, the initial size
    // that the hashtable grew to was determined, and then that very size
    // is used.
    //
    private static final Hashtable<String, Tag> tagHashtable = new Hashtable<String, Tag>(73);

    /** Maps from StyleConstant key to HTML.Tag. */
    private static final Hashtable<Object, Tag> scMapping = new Hashtable<Object, Tag>(8);

    static {

        for (int i = 0; i < Tag.allTags.length; i++ ) {
            tagHashtable.put(Tag.allTags[i].toString(), Tag.allTags[i]);
            StyleContext.registerStaticAttributeKey(Tag.allTags[i]);
        }
        StyleContext.registerStaticAttributeKey(Tag.IMPLIED);
        StyleContext.registerStaticAttributeKey(Tag.CONTENT);
        StyleContext.registerStaticAttributeKey(Tag.COMMENT);
        for (int i = 0; i < Attribute.allAttributes.length; i++) {
            StyleContext.registerStaticAttributeKey(Attribute.
                                                    allAttributes[i]);
        }
        StyleContext.registerStaticAttributeKey(HTML.NULL_ATTRIBUTE_VALUE);
        scMapping.put(StyleConstants.Bold, Tag.B);
        scMapping.put(StyleConstants.Italic, Tag.I);
        scMapping.put(StyleConstants.Underline, Tag.U);
        scMapping.put(StyleConstants.StrikeThrough, Tag.STRIKE);
        scMapping.put(StyleConstants.Superscript, Tag.SUP);
        scMapping.put(StyleConstants.Subscript, Tag.SUB);
        scMapping.put(StyleConstants.FontFamily, Tag.FONT);
        scMapping.put(StyleConstants.FontSize, Tag.FONT);
    }

    /**
     * Returns the set of actual HTML tags that
     * are recognized by the default HTML reader.
     * This set does not include tags that are
     * manufactured by the reader.
     */
    public static Tag[] getAllTags() {
        Tag[] tags = new Tag[Tag.allTags.length];
        System.arraycopy(Tag.allTags, 0, tags, 0, Tag.allTags.length);
        return tags;
    }

    /**
     * Fetches a tag constant for a well-known tag name (i.e. one of
     * the tags in the set {A, ADDRESS, APPLET, AREA, B,
     * BASE, BASEFONT, BIG,
     * BLOCKQUOTE, BODY, BR, CAPTION, CENTER, CITE, CODE,
     * DD, DFN, DIR, DIV, DL, DT, EM, FONT, FORM, FRAME,
     * FRAMESET, H1, H2, H3, H4, H5, H6, HEAD, HR, HTML,
     * I, IMG, INPUT, ISINDEX, KBD, LI, LINK, MAP, MENU,
     * META, NOBR, NOFRAMES, OBJECT, OL, OPTION, P, PARAM,
     * PRE, SAMP, SCRIPT, SELECT, SMALL, SPAN, STRIKE, S,
     * STRONG, STYLE, SUB, SUP, TABLE, TD, TEXTAREA,
     * TH, TITLE, TR, TT, U, UL, VAR}.  If the given
     * name does not represent one of the well-known tags, then
     * <code>null</code> will be returned.
     *
     * @param tagName the <code>String</code> name requested
     * @return a tag constant corresponding to the <code>tagName</code>,
     *    or <code>null</code> if not found
     */
    public static Tag getTag(String tagName) {

        Tag t =  tagHashtable.get(tagName);
        return (t == null ? null : t);
    }

    /**
     * Returns the HTML <code>Tag</code> associated with the
     * <code>StyleConstants</code> key <code>sc</code>.
     * If no matching <code>Tag</code> is found, returns
     * <code>null</code>.
     *
     * @param sc the <code>StyleConstants</code> key
     * @return tag which corresponds to <code>sc</code>, or
     *   <code>null</code> if not found
     */
    static Tag getTagForStyleConstantsKey(StyleConstants sc) {
        return scMapping.get(sc);
    }

    /**
     * Fetches an integer attribute value.  Attribute values
     * are stored as a string, and this is a convenience method
     * to convert to an actual integer.
     *
     * @param attr the set of attributes to use to try to fetch a value
     * @param key the key to use to fetch the value
     * @param def the default value to use if the attribute isn't
     *  defined or there is an error converting to an integer
     */
    public static int getIntegerAttributeValue(AttributeSet attr,
                                               Attribute key, int def) {
        int value = def;
        String istr = (String) attr.getAttribute(key);
        if (istr != null) {
            try {
                value = Integer.valueOf(istr).intValue();
            } catch (NumberFormatException e) {
                value = def;
            }
        }
        return value;
    }

    //  This is used in cases where the value for the attribute has not
    //  been specified.
    //
    public static final String NULL_ATTRIBUTE_VALUE = "#DEFAULT";

    // size determined similar to size of tagHashtable
    private static final Hashtable<String, Attribute> attHashtable = new Hashtable<String, Attribute>(77);

    static {

        for (int i = 0; i < Attribute.allAttributes.length; i++ ) {
            attHashtable.put(Attribute.allAttributes[i].toString(), Attribute.allAttributes[i]);
        }
    }

    /**
     * Returns the set of HTML attributes recognized.
     * @return the set of HTML attributes recognized
     */
    public static Attribute[] getAllAttributeKeys() {
        Attribute[] attributes = new Attribute[Attribute.allAttributes.length];
        System.arraycopy(Attribute.allAttributes, 0,
                         attributes, 0, Attribute.allAttributes.length);
        return attributes;
    }

    /**
     * Fetches an attribute constant for a well-known attribute name
     * (i.e. one of the attributes in the set {FACE, COMMENT, SIZE,
     * COLOR, CLEAR, BACKGROUND, BGCOLOR, TEXT, LINK, VLINK, ALINK,
     * WIDTH, HEIGHT, ALIGN, NAME, HREF, REL, REV, TITLE, TARGET,
     * SHAPE, COORDS, ISMAP, NOHREF, ALT, ID, SRC, HSPACE, VSPACE,
     * USEMAP, LOWSRC, CODEBASE, CODE, ARCHIVE, VALUE, VALUETYPE,
     * TYPE, CLASS, STYLE, LANG, DIR, DECLARE, CLASSID, DATA, CODETYPE,
     * STANDBY, BORDER, SHAPES, NOSHADE, COMPACT, START, ACTION, METHOD,
     * ENCTYPE, CHECKED, MAXLENGTH, MULTIPLE, SELECTED, ROWS, COLS,
     * DUMMY, CELLSPACING, CELLPADDING, VALIGN, HALIGN, NOWRAP, ROWSPAN,
     * COLSPAN, PROMPT, HTTPEQUIV, CONTENT, LANGUAGE, VERSION, N,
     * FRAMEBORDER, MARGINWIDTH, MARGINHEIGHT, SCROLLING, NORESIZE,
     * MEDIA, ENDTAG}).
     * If the given name does not represent one of the well-known attributes,
     * then <code>null</code> will be returned.
     *
     * @param attName the <code>String</code> requested
     * @return the <code>Attribute</code> corresponding to <code>attName</code>
     */
    public static Attribute getAttributeKey(String attName) {
        Attribute a = attHashtable.get(attName);
        if (a == null) {
          return null;
        }
        return a;
    }

}
