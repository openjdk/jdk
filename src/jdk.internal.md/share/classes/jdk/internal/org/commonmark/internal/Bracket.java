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
 * Opening bracket for links (<code>[</code>) or images (<code>![</code>).
 */
public class Bracket {

    public final Text node;

    /**
     * The position of the marker for the bracket (<code>[</code> or <code>![</code>)
     */
    public final Position markerPosition;

    /**
     * The position of the content (after the opening bracket)
     */
    public final Position contentPosition;

    /**
     * Whether this is an image or link.
     */
    public final boolean image;

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
     * Whether there is an unescaped bracket (opening or closing) anywhere after this opening bracket.
     */
    public boolean bracketAfter = false;

    static public Bracket link(Text node, Position markerPosition, Position contentPosition, Bracket previous, Delimiter previousDelimiter) {
        return new Bracket(node, markerPosition, contentPosition, previous, previousDelimiter, false);
    }

    static public Bracket image(Text node, Position markerPosition, Position contentPosition, Bracket previous, Delimiter previousDelimiter) {
        return new Bracket(node, markerPosition, contentPosition, previous, previousDelimiter, true);
    }

    private Bracket(Text node, Position markerPosition, Position contentPosition, Bracket previous, Delimiter previousDelimiter, boolean image) {
        this.node = node;
        this.markerPosition = markerPosition;
        this.contentPosition = contentPosition;
        this.image = image;
        this.previous = previous;
        this.previousDelimiter = previousDelimiter;
    }
}
