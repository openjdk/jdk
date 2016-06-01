/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc.main;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sun.javadoc.*;
import com.sun.tools.javac.util.ListBuffer;

/**
 * Comment contains all information in comment part.
 *      It allows users to get first sentence of this comment, get
 *      comment for different tags...
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Kaiyang Liu (original)
 * @author Robert Field (rewrite)
 * @author Atul M Dambalkar
 * @author Neal Gafter (rewrite)
 */
@Deprecated
class Comment {

    /**
     * sorted comments with different tags.
     */
    private final ListBuffer<Tag> tagList = new ListBuffer<>();

    /**
     * text minus any tags.
     */
    private String text;

    /**
     * Doc environment
     */
    private final DocEnv docenv;

    /**
     * constructor of Comment.
     */
    Comment(final DocImpl holder, final String commentString) {
        this.docenv = holder.env;

        /**
         * Separate the comment into the text part and zero to N tags.
         * Simple state machine is in one of three states:
         * <pre>
         * IN_TEXT: parsing the comment text or tag text.
         * TAG_NAME: parsing the name of a tag.
         * TAG_GAP: skipping through the gap between the tag name and
         * the tag text.
         * </pre>
         */
        @SuppressWarnings("fallthrough")
        class CommentStringParser {
            /**
             * The entry point to the comment string parser
             */
            void parseCommentStateMachine() {
                final int IN_TEXT = 1;
                final int TAG_GAP = 2;
                final int TAG_NAME = 3;
                int state = TAG_GAP;
                boolean newLine = true;
                String tagName = null;
                int tagStart = 0;
                int textStart = 0;
                int lastNonWhite = -1;
                int len = commentString.length();
                for (int inx = 0; inx < len; ++inx) {
                    char ch = commentString.charAt(inx);
                    boolean isWhite = Character.isWhitespace(ch);
                    switch (state)  {
                        case TAG_NAME:
                            if (isWhite) {
                                tagName = commentString.substring(tagStart, inx);
                                state = TAG_GAP;
                            }
                            break;
                        case TAG_GAP:
                            if (isWhite) {
                                break;
                            }
                            textStart = inx;
                            state = IN_TEXT;
                            /* fall thru */
                        case IN_TEXT:
                            if (newLine && ch == '@') {
                                parseCommentComponent(tagName, textStart,
                                                      lastNonWhite+1);
                                tagStart = inx;
                                state = TAG_NAME;
                            }
                            break;
                    }
                    if (ch == '\n') {
                        newLine = true;
                    } else if (!isWhite) {
                        lastNonWhite = inx;
                        newLine = false;
                    }
                }
                // Finish what's currently being processed
                switch (state)  {
                    case TAG_NAME:
                        tagName = commentString.substring(tagStart, len);
                        /* fall thru */
                    case TAG_GAP:
                        textStart = len;
                        /* fall thru */
                    case IN_TEXT:
                        parseCommentComponent(tagName, textStart, lastNonWhite+1);
                        break;
                }
            }

            /**
             * Save away the last parsed item.
             */
            void parseCommentComponent(String tagName,
                                       int from, int upto) {
                String tx = upto <= from ? "" : commentString.substring(from, upto);
                if (tagName == null) {
                    text = tx;
                } else {
                    TagImpl tag;
                    switch (tagName) {
                        case "@exception":
                        case "@throws":
                            warnIfEmpty(tagName, tx);
                            tag = new ThrowsTagImpl(holder, tagName, tx);
                            break;
                        case "@param":
                            warnIfEmpty(tagName, tx);
                            tag = new ParamTagImpl(holder, tagName, tx);
                            break;
                        case "@see":
                            warnIfEmpty(tagName, tx);
                            tag = new SeeTagImpl(holder, tagName, tx);
                            break;
                        case "@serialField":
                            warnIfEmpty(tagName, tx);
                            tag = new SerialFieldTagImpl(holder, tagName, tx);
                            break;
                        case "@return":
                            warnIfEmpty(tagName, tx);
                            tag = new TagImpl(holder, tagName, tx);
                            break;
                        case "@author":
                            warnIfEmpty(tagName, tx);
                            tag = new TagImpl(holder, tagName, tx);
                            break;
                        case "@version":
                            warnIfEmpty(tagName, tx);
                            tag = new TagImpl(holder, tagName, tx);
                            break;
                        default:
                            tag = new TagImpl(holder, tagName, tx);
                            break;
                    }
                    tagList.append(tag);
                }
            }

            void warnIfEmpty(String tagName, String tx) {
                if (tx.length() == 0) {
                    docenv.warning(holder, "tag.tag_has_no_arguments", tagName);
                }
            }

        }

        new CommentStringParser().parseCommentStateMachine();
    }

    /**
     * Return the text of the comment.
     */
    String commentText() {
        return text;
    }

    /**
     * Return all tags in this comment.
     */
    Tag[] tags() {
        return tagList.toArray(new Tag[tagList.length()]);
    }

    /**
     * Return tags of the specified kind in this comment.
     */
    Tag[] tags(String tagname) {
        ListBuffer<Tag> found = new ListBuffer<>();
        String target = tagname;
        if (target.charAt(0) != '@') {
            target = "@" + target;
        }
        for (Tag tag : tagList) {
            if (tag.kind().equals(target)) {
                found.append(tag);
            }
        }
        return found.toArray(new Tag[found.length()]);
    }

    /**
     * Return throws tags in this comment.
     */
    ThrowsTag[] throwsTags() {
        ListBuffer<ThrowsTag> found = new ListBuffer<>();
        for (Tag next : tagList) {
            if (next instanceof ThrowsTag) {
                found.append((ThrowsTag)next);
            }
        }
        return found.toArray(new ThrowsTag[found.length()]);
    }

    /**
     * Return param tags (excluding type param tags) in this comment.
     */
    ParamTag[] paramTags() {
        return paramTags(false);
    }

    /**
     * Return type param tags in this comment.
     */
    ParamTag[] typeParamTags() {
        return paramTags(true);
    }

    /**
     * Return param tags in this comment.  If typeParams is true
     * include only type param tags, otherwise include only ordinary
     * param tags.
     */
    private ParamTag[] paramTags(boolean typeParams) {
        ListBuffer<ParamTag> found = new ListBuffer<>();
        for (Tag next : tagList) {
            if (next instanceof ParamTag) {
                ParamTag p = (ParamTag)next;
                if (typeParams == p.isTypeParameter()) {
                    found.append(p);
                }
            }
        }
        return found.toArray(new ParamTag[found.length()]);
    }

    /**
     * Return see also tags in this comment.
     */
    SeeTag[] seeTags() {
        ListBuffer<SeeTag> found = new ListBuffer<>();
        for (Tag next : tagList) {
            if (next instanceof SeeTag) {
                found.append((SeeTag)next);
            }
        }
        return found.toArray(new SeeTag[found.length()]);
    }

    /**
     * Return serialField tags in this comment.
     */
    SerialFieldTag[] serialFieldTags() {
        ListBuffer<SerialFieldTag> found = new ListBuffer<>();
        for (Tag next : tagList) {
            if (next instanceof SerialFieldTag) {
                found.append((SerialFieldTag)next);
            }
        }
        return found.toArray(new SerialFieldTag[found.length()]);
    }

    /**
     * Return array of tags with text and inline See Tags for a Doc comment.
     */
    static Tag[] getInlineTags(DocImpl holder, String inlinetext) {
        ListBuffer<Tag> taglist = new ListBuffer<>();
        int delimend = 0, textstart = 0, len = inlinetext.length();
        boolean inPre = false;
        DocEnv docenv = holder.env;

        if (len == 0) {
            return taglist.toArray(new Tag[taglist.length()]);
        }
        while (true) {
            int linkstart;
            if ((linkstart = inlineTagFound(holder, inlinetext,
                                            textstart)) == -1) {
                taglist.append(new TagImpl(holder, "Text",
                                           inlinetext.substring(textstart)));
                break;
            } else {
                inPre = scanForPre(inlinetext, textstart, linkstart, inPre);
                int seetextstart = linkstart;
                for (int i = linkstart; i < inlinetext.length(); i++) {
                    char c = inlinetext.charAt(i);
                    if (Character.isWhitespace(c) ||
                        c == '}') {
                        seetextstart = i;
                        break;
                     }
                }
                String linkName = inlinetext.substring(linkstart+2, seetextstart);
                if (!(inPre && (linkName.equals("code") || linkName.equals("literal")))) {
                    //Move past the white space after the inline tag name.
                    while (Character.isWhitespace(inlinetext.
                                                      charAt(seetextstart))) {
                        if (inlinetext.length() <= seetextstart) {
                            taglist.append(new TagImpl(holder, "Text",
                                                       inlinetext.substring(textstart, seetextstart)));
                            docenv.warning(holder,
                                           "tag.Improper_Use_Of_Link_Tag",
                                           inlinetext);
                            return taglist.toArray(new Tag[taglist.length()]);
                        } else {
                            seetextstart++;
                        }
                    }
                }
                taglist.append(new TagImpl(holder, "Text",
                                           inlinetext.substring(textstart, linkstart)));
                textstart = seetextstart;   // this text is actually seetag
                if ((delimend = findInlineTagDelim(inlinetext, textstart)) == -1) {
                    //Missing closing '}' character.
                    // store the text as it is with the {@link.
                    taglist.append(new TagImpl(holder, "Text",
                                               inlinetext.substring(textstart)));
                    docenv.warning(holder,
                                   "tag.End_delimiter_missing_for_possible_SeeTag",
                                   inlinetext);
                    return taglist.toArray(new Tag[taglist.length()]);
                } else {
                    //Found closing '}' character.
                    if (linkName.equals("see")
                           || linkName.equals("link")
                           || linkName.equals("linkplain")) {
                        taglist.append( new SeeTagImpl(holder, "@" + linkName,
                              inlinetext.substring(textstart, delimend)));
                    } else {
                        taglist.append( new TagImpl(holder, "@" + linkName,
                              inlinetext.substring(textstart, delimend)));
                    }
                    textstart = delimend + 1;
                }
            }
            if (textstart == inlinetext.length()) {
                break;
            }
        }
        return taglist.toArray(new Tag[taglist.length()]);
    }

    /** regex for case-insensitive match for {@literal <pre> } and  {@literal </pre> }. */
    private static final Pattern prePat = Pattern.compile("(?i)<(/?)pre>");

    private static boolean scanForPre(String inlinetext, int start, int end, boolean inPre) {
        Matcher m = prePat.matcher(inlinetext).region(start, end);
        while (m.find()) {
            inPre = m.group(1).isEmpty();
        }
        return inPre;
    }

    /**
     * Recursively find the index of the closing '}' character for an inline tag
     * and return it.  If it can't be found, return -1.
     * @param inlineText the text to search in.
     * @param searchStart the index of the place to start searching at.
     * @return the index of the closing '}' character for an inline tag.
     * If it can't be found, return -1.
     */
    private static int findInlineTagDelim(String inlineText, int searchStart) {
        int delimEnd, nestedOpenBrace;
        if ((delimEnd = inlineText.indexOf("}", searchStart)) == -1) {
            return -1;
        } else if (((nestedOpenBrace = inlineText.indexOf("{", searchStart)) != -1) &&
            nestedOpenBrace < delimEnd){
            //Found a nested open brace.
            int nestedCloseBrace = findInlineTagDelim(inlineText, nestedOpenBrace + 1);
            return (nestedCloseBrace != -1) ?
                findInlineTagDelim(inlineText, nestedCloseBrace + 1) :
                -1;
        } else {
            return delimEnd;
        }
    }

    /**
     * Recursively search for the characters '{', '@', followed by
     * name of inline tag and white space,
     * if found
     *    return the index of the text following the white space.
     * else
     *    return -1.
     */
    private static int inlineTagFound(DocImpl holder, String inlinetext, int start) {
        DocEnv docenv = holder.env;
        int linkstart = inlinetext.indexOf("{@", start);
        if (start == inlinetext.length() || linkstart == -1) {
            return -1;
        } else if (inlinetext.indexOf('}', linkstart) == -1) {
            //Missing '}'.
            docenv.warning(holder, "tag.Improper_Use_Of_Link_Tag",
                    inlinetext.substring(linkstart, inlinetext.length()));
            return -1;
        } else {
            return linkstart;
        }
    }


    /**
     * Return array of tags for the locale specific first sentence in the text.
     */
    static Tag[] firstSentenceTags(DocImpl holder, String text) {
        DocLocale doclocale = holder.env.doclocale;
        return getInlineTags(holder,
                             doclocale.localeSpecificFirstSentence(holder, text));
    }

    /**
     * Return text for this Doc comment.
     */
    @Override
    public String toString() {
        return text;
    }
}
