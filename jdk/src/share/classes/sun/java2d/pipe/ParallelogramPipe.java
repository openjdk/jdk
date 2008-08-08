/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.java2d.pipe;

import sun.java2d.SunGraphics2D;

/**
 * This interface defines the set of calls that pipeline objects
 * can use to pass on responsibility for drawing arbitrary
 * parallelogram shapes.
 * Six floating point numbers are provided and the parallelogram
 * is defined as the quadrilateral with the following vertices:
 * <pre>
 *     origin: (x, y)
 *          => (x+dx1, y+dy1)
 *          => (x+dx1+dx2, y+dy1+dy2)
 *          => (x+dx2, y+dy2)
 *          => origin
 * </pre>
 */
public interface ParallelogramPipe {
    public void fillParallelogram(SunGraphics2D sg,
                                  double x, double y,
                                  double dx1, double dy1,
                                  double dx2, double dy2);

    /**
     * Draw a Parallelogram with the indicated line widths
     * assuming a standard BasicStroke with MITER joins.
     * lw1 specifies the width of the stroke along the dx1,dy1
     * vector and lw2 specifies the width of the stroke along
     * the dx2,dy2 vector.
     * This is equivalent to outsetting the indicated
     * parallelogram by lw/2 pixels, then insetting the
     * same parallelogram by lw/2 pixels and filling the
     * difference between the outer and inner parallelograms.
     */
    public void drawParallelogram(SunGraphics2D sg,
                                  double x, double y,
                                  double dx1, double dy1,
                                  double dx2, double dy2,
                                  double lw1, double lw2);
}
