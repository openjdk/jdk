/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * Represents Java language constructs (package, class, constructor,
 * method, field) which have comments and have been processed by this
 * run of javadoc.  All Doc objects are unique, that is, they
 * are == comparable.
 *
 * @since 1.2
 * @author Robert Field
 * @author Scott Seligman (generics, enums, annotations)
 *
 * @deprecated
 *   The declarations in this package have been superseded by those
 *   in the package {@code jdk.javadoc.doclet}.
 *   For more information, see the <i>Migration Guide</i> in the documentation for that package.
 */
@Deprecated
public interface Doc extends Comparable<Object> {

    /**
     * Return the text of the comment for this doc item.
     * Tags have been removed.
     *
     * @return the text of the comment for this doc item.
     */
    String commentText();

    /**
     * Return all tags in this Doc item.
     *
     * @return an array of {@link Tag} objects containing all tags on
     *         this Doc item.
     */
    Tag[] tags();

    /**
     * Return tags of the specified {@linkplain Tag#kind() kind} in
     * this Doc item.
     *
     * For example, if 'tagname' has value "@serial", all tags in
     * this Doc item of kind "@serial" will be returned.
     *
     * @param tagname name of the tag kind to search for.
     * @return an array of Tag containing all tags whose 'kind()'
     * matches 'tagname'.
     */
    Tag[] tags(String tagname);

    /**
     * Return the see also tags in this Doc item.
     *
     * @return an array of SeeTag containing all @see tags.
     */
    SeeTag[] seeTags();

    /**
     * Return comment as an array of tags. Includes inline tags
     * (i.e. {&#64;link <i>reference</i>} tags)  but not
     * block tags.
     * Each section of plain text is represented as a {@link Tag}
     * of {@linkplain Tag#kind() kind} "Text".
     * Inline tags are represented as a {@link SeeTag} of kind "@see"
     * and name "@link".
     *
     * @return an array of {@link Tag}s representing the comment
     */
    Tag[] inlineTags();

    /**
     * Return the first sentence of the comment as an array of tags.
     * Includes inline tags
     * (i.e. {&#64;link <i>reference</i>} tags)  but not
     * block tags.
     * Each section of plain text is represented as a {@link Tag}
     * of {@linkplain Tag#kind() kind} "Text".
     * Inline tags are represented as a {@link SeeTag} of kind "@see"
     * and name "@link".
     * <p>
     * If the locale is English language, the first sentence is
     * determined by the rules described in the Java Language
     * Specification (first version): &quot;This sentence ends
     * at the first period that is followed by a blank, tab, or
     * line terminator or at the first tagline.&quot;, in
     * addition a line will be terminated by block
     * HTML tags: &lt;p&gt;  &lt;/p&gt;  &lt;h1&gt;
     * &lt;h2&gt;  &lt;h3&gt; &lt;h4&gt;  &lt;h5&gt;  &lt;h6&gt;
     * &lt;hr&gt;  &lt;pre&gt;  or &lt;/pre&gt;.
     * If the locale is not English, the sentence end will be
     * determined by
     * {@link BreakIterator#getSentenceInstance(Locale)}.

     * @return an array of {@link Tag}s representing the
     * first sentence of the comment
     */
    Tag[] firstSentenceTags();

    /**
     * Return the full unprocessed text of the comment.  Tags
     * are included as text.  Used mainly for store and retrieve
     * operations like internalization.
     *
     * @return the full unprocessed text of the comment.
     */
    String getRawCommentText();

    /**
     * Set the full unprocessed text of the comment.  Tags
     * are included as text.  Used mainly for store and retrieve
     * operations like internalization.
     *
     * @param rawDocumentation A String containing the full unprocessed text of the comment.
     */
    void setRawCommentText(String rawDocumentation);

    /**
     * Returns the non-qualified name of this Doc item.
     *
     * @return  the name
     */
    String name();

    /**
     * Compares this doc object with the specified object for order.  Returns a
     * negative integer, zero, or a positive integer as this doc object is less
     * than, equal to, or greater than the given object.
     * <p>
     * This method satisfies the {@link java.lang.Comparable} interface.
     *
     * @param   obj  the {@code Object} to be compared.
     * @return  a negative integer, zero, or a positive integer as this Object
     *      is less than, equal to, or greater than the given Object.
     * @exception ClassCastException the specified Object's type prevents it
     *        from being compared to this Object.
     */
    int compareTo(Object obj);

    /**
     * Is this Doc item a field (but not an enum constant)?
     *
     * @return true if it represents a field
     */
    boolean isField();

    /**
     * Is this Doc item an enum constant?
     *
     * @return true if it represents an enum constant
     * @since 1.5
     */
    boolean isEnumConstant();

    /**
     * Is this Doc item a constructor?
     *
     * @return true if it represents a constructor
     */
    boolean isConstructor();

    /**
     * Is this Doc item a method (but not a constructor or annotation
     * type element)?
     *
     * @return true if it represents a method
     */
    boolean isMethod();

    /**
     * Is this Doc item an annotation type element?
     *
     * @return true if it represents an annotation type element
     * @since 1.5
     */
    boolean isAnnotationTypeElement();

    /**
     * Is this Doc item an interface (but not an annotation type)?
     *
     * @return true if it represents an interface
     */
    boolean isInterface();

    /**
     * Is this Doc item an exception class?
     *
     * @return true if it represents an exception
     */
    boolean isException();

    /**
     * Is this Doc item an error class?
     *
     * @return true if it represents a error
     */
    boolean isError();

    /**
     * Is this Doc item an enum type?
     *
     * @return true if it represents an enum type
     * @since 1.5
     */
    boolean isEnum();

    /**
     * Is this Doc item an annotation type?
     *
     * @return true if it represents an annotation type
     * @since 1.5
     */
    boolean isAnnotationType();

    /**
     * Is this Doc item an
     * <a href="{@docRoot}/com/sun/javadoc/package-summary.html#class">ordinary
     * class</a>?
     * (i.e. not an interface, annotation type, enum, exception, or error)?
     *
     * @return true if it represents an ordinary class
     */
    boolean isOrdinaryClass();

    /**
     * Is this Doc item a
     * <a href="{@docRoot}/com/sun/javadoc/package-summary.html#class">class</a>
     * (and not an interface or annotation type)?
     * This includes ordinary classes, enums, errors and exceptions.
     *
     * @return true if it represents a class
     */
    boolean isClass();

    /**
     * Return true if this Doc item is
     * <a href="{@docRoot}/com/sun/javadoc/package-summary.html#included">included</a>
     * in the result set.
     *
     * @return true if this Doc item is
     *         <a href="{@docRoot}/com/sun/javadoc/package-summary.html#included">included</a>
     *         in the result set.
     */
    boolean isIncluded();

    /**
     * Return the source position of the first line of the
     * corresponding declaration, or null if
     * no position is available.  A default constructor returns
     * null because it has no location in the source file.
     *
     * @since 1.4
     * @return the source positino of the first line of the
     *         corresponding declaration, or null if
     *         no position is available.  A default constructor returns
     *         null because it has no location in the source file.
     */
    SourcePosition position();
}
