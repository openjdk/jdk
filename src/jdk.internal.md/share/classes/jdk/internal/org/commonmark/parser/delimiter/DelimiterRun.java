/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.parser.delimiter;

import jdk.internal.org.commonmark.node.Text;

/**
 * A delimiter run is one or more of the same delimiter character, e.g. {@code ***}.
 */
public interface DelimiterRun {

    /**
     * @return whether this can open a delimiter
     */
    boolean canOpen();

    /**
     * @return whether this can close a delimiter
     */
    boolean canClose();

    /**
     * @return the number of characters in this delimiter run (that are left for processing)
     */
    int length();

    /**
     * @return the number of characters originally in this delimiter run; at the start of processing, this is the same
     * as {{@link #length()}}
     */
    int originalLength();

    /**
     * @return the innermost opening delimiter, e.g. for {@code ***} this is the last {@code *}
     */
    Text getOpener();

    /**
     * @return the innermost closing delimiter, e.g. for {@code ***} this is the first {@code *}
     */
    Text getCloser();

    /**
     * Get the opening delimiter nodes for the specified length of delimiters. Length must be between 1 and
     * {@link #length()}.
     * <p>
     * For example, for a delimiter run {@code ***}, calling this with 1 would return the last {@code *}.
     * Calling it with 2 would return the second last {@code *} and the last {@code *}.
     */
    Iterable<Text> getOpeners(int length);

    /**
     * Get the closing delimiter nodes for the specified length of delimiters. Length must be between 1 and
     * {@link #length()}.
     * <p>
     * For example, for a delimiter run {@code ***}, calling this with 1 would return the first {@code *}.
     * Calling it with 2 would return the first {@code *} and the second {@code *}.
     */
    Iterable<Text> getClosers(int length);
}
