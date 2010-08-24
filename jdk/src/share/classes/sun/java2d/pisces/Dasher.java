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
 * The <code>Dasher</code> class takes a series of linear commands
 * (<code>moveTo</code>, <code>lineTo</code>, <code>close</code> and
 * <code>end</code>) and breaks them into smaller segments according to a
 * dash pattern array and a starting dash phase.
 *
 * <p> Issues: in J2Se, a zero length dash segment as drawn as a very
 * short dash, whereas Pisces does not draw anything.  The PostScript
 * semantics are unclear.
 *
 */
public class Dasher implements LineSink {
    private final LineSink output;
    private final float[] dash;
    private final float startPhase;
    private final boolean startDashOn;
    private final int startIdx;

    private final float m00, m10, m01, m11;
    private final float det;

    private boolean firstDashOn;
    private boolean starting;

    private int idx;
    private boolean dashOn;
    private float phase;

    private float sx, sy;
    private float x0, y0;
    private float sx1, sy1;


    /**
     * Constructs a <code>Dasher</code>.
     *
     * @param output an output <code>LineSink</code>.
     * @param dash an array of <code>int</code>s containing the dash pattern
     * @param phase an <code>int</code> containing the dash phase
     * @param transform a <code>Transform4</code> object indicating
     * the transform that has been previously applied to all incoming
     * coordinates.  This is required in order to compute dash lengths
     * properly.
     */
    public Dasher(LineSink output,
                  float[] dash, float phase,
                  float a00, float a01, float a10, float a11) {
        if (phase < 0) {
            throw new IllegalArgumentException("phase < 0 !");
        }

        this.output = output;

        // Normalize so 0 <= phase < dash[0]
        int idx = 0;
        dashOn = true;
        float d;
        while (phase >= (d = dash[idx])) {
            phase -= d;
            idx = (idx + 1) % dash.length;
            dashOn = !dashOn;
        }

        this.dash = dash;
        this.startPhase = this.phase = phase;
        this.startDashOn = dashOn;
        this.startIdx = idx;

        m00 = a00;
        m01 = a01;
        m10 = a10;
        m11 = a11;
        det = m00 * m11 - m01 * m10;
    }

    public void moveTo(float x0, float y0) {
        output.moveTo(x0, y0);
        this.idx = startIdx;
        this.dashOn = this.startDashOn;
        this.phase = this.startPhase;
        this.sx = this.x0 = x0;
        this.sy = this.y0 = y0;
        this.starting = true;
    }

    public void lineJoin() {
        output.lineJoin();
    }

    private void goTo(float x1, float y1) {
        if (dashOn) {
            if (starting) {
                this.sx1 = x1;
                this.sy1 = y1;
                firstDashOn = true;
                starting = false;
            }
            output.lineTo(x1, y1);
        } else {
            if (starting) {
                firstDashOn = false;
                starting = false;
            }
            output.moveTo(x1, y1);
        }
        this.x0 = x1;
        this.y0 = y1;
    }

    public void lineTo(float x1, float y1) {
        // The widened line is squished to a 0 width one, so no drawing is done
        if (det == 0) {
            goTo(x1, y1);
            return;
        }
        float dx = x1 - x0;
        float dy = y1 - y0;


        // Compute segment length in the untransformed
        // coordinate system

        float la = (dy*m00 - dx*m10)/det;
        float lb = (dy*m01 - dx*m11)/det;
        float origLen = (float) Math.hypot(la, lb);

        if (origLen == 0) {
            // Let the output LineSink deal with cases where dx, dy are 0.
            goTo(x1, y1);
            return;
        }

        // The scaling factors needed to get the dx and dy of the
        // transformed dash segments.
        float cx = dx / origLen;
        float cy = dy / origLen;

        while (true) {
            float leftInThisDashSegment = dash[idx] - phase;
            if (origLen < leftInThisDashSegment) {
                goTo(x1, y1);
                // Advance phase within current dash segment
                phase += origLen;
                return;
            } else if (origLen == leftInThisDashSegment) {
                goTo(x1, y1);
                phase = 0f;
                idx = (idx + 1) % dash.length;
                dashOn = !dashOn;
                return;
            }

            float dashx, dashy;
            float dashdx = dash[idx] * cx;
            float dashdy = dash[idx] * cy;
            if (phase == 0) {
                dashx = x0 + dashdx;
                dashy = y0 + dashdy;
            } else {
                float p = (leftInThisDashSegment) / dash[idx];
                dashx = x0 + p * dashdx;
                dashy = y0 + p * dashdy;
            }

            goTo(dashx, dashy);

            origLen -= (dash[idx] - phase);
            // Advance to next dash segment
            idx = (idx + 1) % dash.length;
            dashOn = !dashOn;
            phase = 0;
        }
    }


    public void close() {
        lineTo(sx, sy);
        if (firstDashOn) {
            output.lineTo(sx1, sy1);
        }
    }

    public void end() {
        output.end();
    }
}
