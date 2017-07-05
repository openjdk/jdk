/*
 * Copyright (c) 2001, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets;

import com.sun.javadoc.*;

/**
 * The interface for a custom tag used by Doclets. A custom
 * tag must implement this interface.
 *
 * <p style="font-style: italic; font-size:larger">
 * <b>Note:</b> This interface has been superseded by its replacement,
 * {@link jdk.javadoc.doclet.Taglet}.
 * </p>
 *
 * To be loaded and used by
 * doclets at run-time, the taglet must have a static method called
 * <code>register</code> that accepts a {@link java.util.Map} as an
 * argument with the following signature:
 * <pre>
 *   public void register(Map map)
 * </pre>
 * This method should add an instance of the custom taglet to the map
 * with the name of the taglet as the key.  If overriding a taglet,
 * to avoid a name conflict, the overridden taglet must be deleted from
 * the map before an instance of the new taglet is added to the map.
 * <p>
 * It is recommended that the taglet throw an exception when it fails
 * to register itself.  The exception that it throws is up to the user.
 * <p>
 * Here are two sample taglets: <br>
 * <ul>
 *  <li><a href="http://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/taglet/ToDoTaglet.java">ToDoTaglet.java</a>
 *         - Standalone taglet</li>
 *  <li><a href="http://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/taglet/UnderlineTaglet.java">UnderlineTaglet.java</a>
 *         - Inline taglet</li>
 * </ul>
 * <p>
 * For more information on how to create your own Taglets, please see the
 * <a href="http://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/taglet/overview.html">Taglet Overview</a>.
 *
 * @since 1.4
 * @author Jamie Ho
 *
 * @deprecated
 * This interface has been superseded by its replacement,
 * {@link jdk.javadoc.doclet.Taglet}.
 */
@Deprecated
public interface Taglet {

    /**
     * Return true if this <code>Taglet</code>
     * is used in field documentation.  Set to
     * false for inline tags.
     * @return true if this <code>Taglet</code>
     * is used in field documentation and false
     * otherwise.
     */
    public abstract boolean inField();

    /**
     * Return true if this <code>Taglet</code>
     * is used in constructor documentation. Set to
     * false for inline tags.
     * @return true if this <code>Taglet</code>
     * is used in constructor documentation and false
     * otherwise.
     */
    public abstract boolean inConstructor();

    /**
     * Return true if this <code>Taglet</code>
     * is used in method documentation. Set to
     * false for inline tags.
     * @return true if this <code>Taglet</code>
     * is used in method documentation and false
     * otherwise.
     */
    public abstract boolean inMethod();

    /**
     * Return true if this <code>Taglet</code>
     * is used in overview documentation. Set to
     * false for inline tags.
     * @return true if this <code>Taglet</code>
     * is used in method documentation and false
     * otherwise.
     */
    public abstract boolean inOverview();

    /**
     * Return true if this <code>Taglet</code>
     * is used in package documentation. Set to
     * false for inline tags.
     * @return true if this <code>Taglet</code>
     * is used in package documentation and false
     * otherwise.
     */
    public abstract boolean inPackage();

    /**
     * Return true if this <code>Taglet</code>
     * is used in type documentation (classes or
     * interfaces). Set to false for inline tags.
     * @return true if this <code>Taglet</code>
     * is used in type documentation and false
     * otherwise.
     */
    public abstract boolean inType();

    /**
     * Return true if this <code>Taglet</code>
     * is an inline tag. Return false otherwise.
     * @return true if this <code>Taglet</code>
     * is an inline tag and false otherwise.
     */
    public abstract boolean isInlineTag();

    /**
     * Return the name of this custom tag.
     * @return the name of this custom tag.
     */
    public abstract String getName();

    /**
     * Given the <code>Tag</code> representation of this custom
     * tag, return its string representation, which is output
     * to the generated page.
     * @param tag the <code>Tag</code> representation of this custom tag.
     * @return the string representation of this <code>Tag</code>.
     */
    public abstract String toString(Tag tag);

    /**
     * Given an array of <code>Tag</code>s representing this custom
     * tag, return its string representation, which is output
     * to the generated page.  This method should
     * return null if this taglet represents an inline tag.
     * @param tags the array of <code>Tag</code>s representing of this custom tag.
     * @return the string representation of this <code>Tag</code>.
     */
    public abstract String toString(Tag[] tags);

}
