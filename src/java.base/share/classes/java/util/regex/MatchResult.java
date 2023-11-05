/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.util.regex;

import java.util.Map;
import java.util.Objects;

/**
 * The result of a match operation.
 *
 * <p>This interface contains query methods used to determine the
 * results of a match against a regular expression. The match boundaries,
 * groups and group boundaries can be seen but not modified through
 * a {@code MatchResult}.
 *
 * @implNote
 * Support for named groups is implemented by the default methods
 * {@link #start(String)}, {@link #end(String)} and {@link #group(String)}.
 * They all make use of the map returned by {@link #namedGroups()}, whose
 * default implementation simply throws {@link UnsupportedOperationException}.
 * It is thus sufficient to override {@link #namedGroups()} for these methods
 * to work. However, overriding them directly might be preferable for
 * performance or other reasons.
 *
 * @author  Michael McCloskey
 * @see Matcher
 * @since 1.5
 */
public interface MatchResult {

    /**
     * Returns the start index of the match.
     *
     * @return  The index of the first character matched
     *
     * @throws  IllegalStateException
     *          If no match has yet been attempted,
     *          or if the previous match operation failed
     */
    int start();

    /**
     * Returns the start index of the subsequence captured by the given group
     * during this match.
     *
     * <p> <a href="Pattern.html#cg">Capturing groups</a> are indexed from left
     * to right, starting at one.  Group zero denotes the entire pattern, so
     * the expression <i>m.</i>{@code start(0)} is equivalent to
     * <i>m.</i>{@code start()}.  </p>
     *
     * @param  group
     *         The index of a capturing group in this matcher's pattern
     *
     * @return  The index of the first character captured by the group,
     *          or {@code -1} if the match was successful but the group
     *          itself did not match anything
     *
     * @throws  IllegalStateException
     *          If no match has yet been attempted,
     *          or if the previous match operation failed
     *
     * @throws  IndexOutOfBoundsException
     *          If there is no capturing group in the pattern
     *          with the given index
     */
    int start(int group);

    /**
     * Returns the start index of the subsequence captured by the given
     * <a href="Pattern.html#groupname">named-capturing group</a> during the
     * previous match operation.
     *
     * @param  name
     *         The name of a named-capturing group in this matcher's pattern
     *
     * @return  The index of the first character captured by the group,
     *          or {@code -1} if the match was successful but the group
     *          itself did not match anything
     *
     * @throws  IllegalStateException
     *          If no match has yet been attempted,
     *          or if the previous match operation failed
     *
     * @throws  IllegalArgumentException
     *          If there is no capturing group in the pattern
     *          with the given name
     *
     * @implSpec
     * The default implementation of this method invokes {@link #namedGroups()}
     * to obtain the group number from the {@code name} argument, and uses it
     * as argument to an invocation of {@link #start(int)}.
     *
     * @since 20
     */
    default int start(String name) {
        return start(groupNumber(name));
    }

    /**
     * Returns the offset after the last character matched.
     *
     * @return  The offset after the last character matched
     *
     * @throws  IllegalStateException
     *          If no match has yet been attempted,
     *          or if the previous match operation failed
     */
    int end();

    /**
     * Returns the offset after the last character of the subsequence
     * captured by the given group during this match.
     *
     * <p> <a href="Pattern.html#cg">Capturing groups</a> are indexed from left
     * to right, starting at one.  Group zero denotes the entire pattern, so
     * the expression <i>m.</i>{@code end(0)} is equivalent to
     * <i>m.</i>{@code end()}.  </p>
     *
     * @param  group
     *         The index of a capturing group in this matcher's pattern
     *
     * @return  The offset after the last character captured by the group,
     *          or {@code -1} if the match was successful
     *          but the group itself did not match anything
     *
     * @throws  IllegalStateException
     *          If no match has yet been attempted,
     *          or if the previous match operation failed
     *
     * @throws  IndexOutOfBoundsException
     *          If there is no capturing group in the pattern
     *          with the given index
     */
    int end(int group);

    /**
     * Returns the offset after the last character of the subsequence
     * captured by the given <a href="Pattern.html#groupname">named-capturing
     * group</a> during the previous match operation.
     *
     * @param  name
     *         The name of a named-capturing group in this matcher's pattern
     *
     * @return  The offset after the last character captured by the group,
     *          or {@code -1} if the match was successful
     *          but the group itself did not match anything
     *
     * @throws  IllegalStateException
     *          If no match has yet been attempted,
     *          or if the previous match operation failed
     *
     * @throws  IllegalArgumentException
     *          If there is no capturing group in the pattern
     *          with the given name
     *
     * @implSpec
     * The default implementation of this method invokes {@link #namedGroups()}
     * to obtain the group number from the {@code name} argument, and uses it
     * as argument to an invocation of {@link #end(int)}.
     *
     * @since 20
     */
    default int end(String name) {
        return end(groupNumber(name));
    }

    /**
     * Returns the input subsequence matched by the previous match.
     *
     * <p> For a matcher <i>m</i> with input sequence <i>s</i>,
     * the expressions <i>m.</i>{@code group()} and
     * <i>s.</i>{@code substring(}<i>m.</i>{@code start(),}&nbsp;<i>m.</i>{@code end())}
     * are equivalent.  </p>
     *
     * <p> Note that some patterns, for example {@code a*}, match the empty
     * string.  This method will return the empty string when the pattern
     * successfully matches the empty string in the input.  </p>
     *
     * @return The (possibly empty) subsequence matched by the previous match,
     *         in string form
     *
     * @throws  IllegalStateException
     *          If no match has yet been attempted,
     *          or if the previous match operation failed
     */
    String group();

    /**
     * Returns the input subsequence captured by the given group during the
     * previous match operation.
     *
     * <p> For a matcher <i>m</i>, input sequence <i>s</i>, and group index
     * <i>g</i>, the expressions <i>m.</i>{@code group(}<i>g</i>{@code )} and
     * <i>s.</i>{@code substring(}<i>m.</i>{@code start(}<i>g</i>{@code
     * ),}&nbsp;<i>m.</i>{@code end(}<i>g</i>{@code ))}
     * are equivalent.  </p>
     *
     * <p> <a href="Pattern.html#cg">Capturing groups</a> are indexed from left
     * to right, starting at one.  Group zero denotes the entire pattern, so
     * the expression {@code m.group(0)} is equivalent to {@code m.group()}.
     * </p>
     *
     * <p> If the match was successful but the group specified failed to match
     * any part of the input sequence, then {@code null} is returned. Note
     * that some groups, for example {@code (a*)}, match the empty string.
     * This method will return the empty string when such a group successfully
     * matches the empty string in the input.  </p>
     *
     * @param  group
     *         The index of a capturing group in this matcher's pattern
     *
     * @return  The (possibly empty) subsequence captured by the group
     *          during the previous match, or {@code null} if the group
     *          failed to match part of the input
     *
     * @throws  IllegalStateException
     *          If no match has yet been attempted,
     *          or if the previous match operation failed
     *
     * @throws  IndexOutOfBoundsException
     *          If there is no capturing group in the pattern
     *          with the given index
     */
    String group(int group);

    /**
     * Returns the input subsequence captured by the given
     * <a href="Pattern.html#groupname">named-capturing group</a> during the
     * previous match operation.
     *
     * <p> If the match was successful but the group specified failed to match
     * any part of the input sequence, then {@code null} is returned. Note
     * that some groups, for example {@code (a*)}, match the empty string.
     * This method will return the empty string when such a group successfully
     * matches the empty string in the input.  </p>
     *
     * @param  name
     *         The name of a named-capturing group in this matcher's pattern
     *
     * @return  The (possibly empty) subsequence captured by the named group
     *          during the previous match, or {@code null} if the group
     *          failed to match part of the input
     *
     * @throws  IllegalStateException
     *          If no match has yet been attempted,
     *          or if the previous match operation failed
     *
     * @throws  IllegalArgumentException
     *          If there is no capturing group in the pattern
     *          with the given name
     *
     * @implSpec
     * The default implementation of this method invokes {@link #namedGroups()}
     * to obtain the group number from the {@code name} argument, and uses it
     * as argument to an invocation of {@link #group(int)}.
     *
     * @since 20
     */
    default String group(String name) {
        return group(groupNumber(name));
    }

    /**
     * Returns the number of capturing groups in this match result's pattern.
     *
     * <p> Group zero denotes the entire pattern by convention. It is not
     * included in this count.
     *
     * <p> Any non-negative integer smaller than or equal to the value
     * returned by this method is guaranteed to be a valid group index for
     * this matcher.  </p>
     *
     * @return The number of capturing groups in this matcher's pattern
     */
    int groupCount();

    /**
     * Returns an unmodifiable map from capturing group names to group numbers.
     * If there are no named groups, returns an empty map.
     *
     * @return an unmodifiable map from capturing group names to group numbers
     *
     * @throws UnsupportedOperationException if the implementation does not
     *          support named groups.
     *
     * @implSpec The default implementation of this method always throws
     *          {@link UnsupportedOperationException}
     *
     * @apiNote
     * This method must be overridden by an implementation that supports
     * named groups.
     *
     * @since 20
     */
    default Map<String,Integer> namedGroups() {
        throw new UnsupportedOperationException("namedGroups()");
    }

    private int groupNumber(String name) {
        Objects.requireNonNull(name, "Group name");
        Integer number = namedGroups().get(name);
        if (number != null) {
            return number;
        }
        throw new IllegalArgumentException("No group with name <" + name + ">");
    }

    /**
     * Returns whether {@code this} contains a valid match from
     * a previous match or find operation.
     *
     * @return whether {@code this} contains a valid match
     *
     * @throws UnsupportedOperationException if the implementation cannot report
     *          whether it has a match
     *
     * @implSpec The default implementation of this method always throws
     *          {@link UnsupportedOperationException}
     *
     * @since 20
     */
    default boolean hasMatch() {
        throw new UnsupportedOperationException("hasMatch()");
    }

}
