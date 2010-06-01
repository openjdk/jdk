/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javadoc;

import java.text.BreakIterator;
import java.util.Locale;

/**
 * Represents a simple documentation tag, such as @since, @author, @version.
 * Given a tag (e.g. "@since 1.2"), holds tag name (e.g. "@since")
 * and tag text (e.g. "1.2").  Tags with structure or which require
 * special processing are handled by subclasses such as ParamTag
 * (for @param), SeeTag (for @see and {&#064;link}), and ThrowsTag
 * (for @throws).
 *
 * @author Robert Field
 * @author Atul M Dambalkar
 * @see SeeTag
 * @see ParamTag
 * @see ThrowsTag
 * @see SerialFieldTag
 * @see Doc#tags()
 *
 */
public interface Tag {

    /**
     * Return the name of this tag.  The name is the string
     * starting with "@" that is used in a doc comment, such as
     * <code>@return</code>.  For inline tags, such as
     * <code>{&#064;link}</code>, the curly brackets
     * are not part of the name, so in this example the name
     * would be simply <code>@link</code>.
     */
    String name();

    /**
     * Return the containing {@link Doc} of this Tag element.
     */
    Doc holder();

    /**
     * Return the kind of this tag.
     * similar or synonymous tags.  For most tags,
     * <code>kind()&nbsp;==&nbsp;name()</code>;
     * the following table lists those cases where there is more
     * than one tag of a given kind:
     * <p>
     * <table border="1" cellpadding="4" cellspacing="0">
     * <tr><th><tt> kind()  </th>  <th><tt> name()      </th></tr>
     * <tr><td><tt> @throws </td>  <td><tt> @throws     </td></tr>
     * <tr><td><tt> @throws </td>  <td><tt> @exception  </td></tr>
     * <tr><td><tt> @see    </td>  <td><tt> @see        </td></tr>
     * <tr><td><tt> @see    </td>  <td><tt> @link       </td></tr>
     * <tr><td><tt> @see    </td>  <td><tt> @linkplain  </td></tr>
     * <tr><td><tt> @serial </td>  <td><tt> @serial     </td></tr>
     * <tr><td><tt> @serial </td>  <td><tt> @serialData </td></tr>
     * </table>
     */
    String kind();

    /**
     * Return the text of this tag, that is, portion beyond tag name.
     */
    String text();

    /**
     * Convert this object to a string.
     */
    String toString();

    /**
     * For a documentation comment with embedded <code>{&#064;link}</code>
     * tags, return an array of <code>Tag</code> objects.  The entire
     * doc comment is broken down into strings separated by
     * <code>{&#064;link}</code> tags, where each successive element
     * of the array represents either a string or
     * <code>{&#064;link}</code> tag, in order, from start to end.
     * Each string is represented by a <code>Tag</code> object of
     * name "Text", where {@link #text()} returns the string.  Each
     * <code>{&#064;link}</code> tag is represented by a
     * {@link SeeTag} of name "@link" and kind "@see".
     * For example, given the following comment
     * tag:
     * <p>
     *  <code>This is a {&#064;link Doc commentlabel} example.</code>
     * <p>
     * return an array of Tag objects:
     * <ul>
     *    <li> tags[0] is a {@link Tag} with name "Text" and text consisting
     *         of "This is a "
     *    <li> tags[1] is a {@link SeeTag} with name "@link", referenced
     *         class <code>Doc</code> and label "commentlabel"
     *    <li> tags[2] is a {@link Tag} with name "Text" and text consisting
     *         of " example."
     * </ul>
     *
     * @return Tag[] array of tags
     * @see ParamTag
     * @see ThrowsTag
     */
    Tag[] inlineTags();

    /**
     * Return the first sentence of the comment as an array of tags.
     * Includes inline tags
     * (i.e. {&#64link <i>reference</i>} tags)  but not
     * block tags.
     * Each section of plain text is represented as a {@link Tag}
     * of kind "Text".
     * Inline tags are represented as a {@link SeeTag} of kind "@link".
     * If the locale is English language, the first sentence is
     * determined by the rules described in the Java Language
     * Specification (first version): &quot;This sentence ends
     * at the first period that is followed by a blank, tab, or
     * line terminator or at the first tagline.&quot;, in
     * addition a line will be terminated by paragraph and
     * section terminating HTML tags: &lt;p&gt;  &lt;/p&gt;  &lt;h1&gt;
     * &lt;h2&gt;  &lt;h3&gt; &lt;h4&gt;  &lt;h5&gt;  &lt;h6&gt;
     * &lt;hr&gt;  &lt;pre&gt;  or &lt;/pre&gt;.
     * If the locale is not English, the sentence end will be
     * determined by
     * {@link BreakIterator#getSentenceInstance(Locale)}.
     *
     * @return an array of {@link Tag} objects representing the
     *         first sentence of the comment
     */
    Tag[] firstSentenceTags();

    /**
     * Return the source position of this tag.
     * @return the source position of this tag.
     */
    public SourcePosition position();
}
