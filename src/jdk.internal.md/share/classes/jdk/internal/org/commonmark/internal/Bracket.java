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

package jdk.internal.org.commonmark.internal;

import jdk.internal.org.commonmark.node.Text;
import jdk.internal.org.commonmark.parser.beta.Position;

/**
 * Opening bracket for links ({@code [}), images ({@code ![}), or links with other markers.
 */
public class Bracket {

    /**
     * The node of a marker such as {@code !} if present, null otherwise.
     */
    public final Text markerNode;

    /**
     * The position of the marker if present, null otherwise.
     */
    public final Position markerPosition;

    /**
     * The node of {@code [}.
     */
    public final Text bracketNode;

    /**
     * The position of {@code [}.
     */
    public final Position bracketPosition;

    /**
     * The position of the content (after the opening bracket)
     */
    public final Position contentPosition;

    /**
     * Previous bracket.
     */
    public final Bracket previous;

    /**
     * Previous delimiter (emphasis, etc) before this bracket.
     */
    public final Delimiter previousDelimiter;

    /**
     * Whether this bracket is allowed to form a link/image (also known as "active").
     */
    public boolean allowed = true;

    /**
     * Whether there is an unescaped bracket (opening or closing) after this opening bracket in the text parsed so far.
     */
    public boolean bracketAfter = false;

    static public Bracket link(Text bracketNode, Position bracketPosition, Position contentPosition, Bracket previous, Delimiter previousDelimiter) {
        return new Bracket(null, null, bracketNode, bracketPosition, contentPosition, previous, previousDelimiter);
    }

    static public Bracket withMarker(Text markerNode, Position markerPosition, Text bracketNode, Position bracketPosition, Position contentPosition, Bracket previous, Delimiter previousDelimiter) {
        return new Bracket(markerNode, markerPosition, bracketNode, bracketPosition, contentPosition, previous, previousDelimiter);
    }

    private Bracket(Text markerNode, Position markerPosition, Text bracketNode, Position bracketPosition, Position contentPosition, Bracket previous, Delimiter previousDelimiter) {
        this.markerNode = markerNode;
        this.markerPosition = markerPosition;
        this.bracketNode = bracketNode;
        this.bracketPosition = bracketPosition;
        this.contentPosition = contentPosition;
        this.previous = previous;
        this.previousDelimiter = previousDelimiter;
    }
}
