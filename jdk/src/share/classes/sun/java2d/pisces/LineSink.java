/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.pisces;

/**
 * The <code>LineSink</code> interface accepts a series of line
 * drawing commands: <code>moveTo</code>, <code>lineTo</code>,
 * <code>close</code> (equivalent to a <code>lineTo</code> command
 * with an argument equal to the argument of the last
 * <code>moveTo</code> command), and <code>end</code>.
 *
 * <p> A <code>Flattener</code> may be used to connect a general path
 * source to a <code>LineSink</code>.
 *
 * <p> The <code>Renderer</code> class implements the
 * <code>LineSink</code> interface.
 *
 */
public interface LineSink {

    /**
     * Moves the current drawing position to the point <code>(x0,
     * y0)</code>.
     *
     * @param x0 the X coordinate
     * @param y0 the Y coordinate
     */
    public void moveTo(float x0, float y0);

    /**
     * Provides a hint that the current segment should be joined to
     * the following segment using an explicit miter or round join if
     * required.
     *
     * <p> An application-generated path will generally have no need
     * to contain calls to this method; they are typically introduced
     * by a <code>Flattener</code> to mark segment divisions that
     * appear in its input, and consumed by a <code>Stroker</code>
     * that is responsible for emitting the miter or round join
     * segments.
     *
     * <p> Other <code>LineSink</code> classes should simply pass this
     * hint to their output sink as needed.
     */
    public void lineJoin();

    /**
     * Draws a line from the current drawing position to the point
     * <code>(x1, y1)</code> and sets the current drawing position to
     * <code>(x1, y1)</code>.
     *
     * @param x1 the X coordinate
     * @param y1 the Y coordinate
     */
    public void lineTo(float x1, float y1);

    /**
     * Closes the current path by drawing a line from the current
     * drawing position to the point specified by the moset recent
     * <code>moveTo</code> command.
     */
    public void close();

    /**
     * Ends the current path.  It may be necessary to end a path in
     * order to allow end caps to be drawn.
     */
    public void end();

}
