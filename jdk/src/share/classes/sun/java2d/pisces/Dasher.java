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
public class Dasher extends LineSink {

    LineSink output;
    int[] dash;
    int startPhase;
    boolean startDashOn;
    int startIdx;

    int idx;
    boolean dashOn;
    int phase;

    int sx, sy;
    int x0, y0;

    int m00, m01;
    int m10, m11;

    Transform4 transform;

    boolean symmetric;
    long ldet;

    boolean firstDashOn;
    boolean starting;
    int sx1, sy1;

    /**
     * Empty constructor.  <code>setOutput</code> and
     * <code>setParameters</code> must be called prior to calling any
     * other methods.
     */
    public Dasher() {}

    /**
     * Constructs a <code>Dasher</code>.
     *
     * @param output an output <code>LineSink</code>.
     * @param dash an array of <code>int</code>s containing the dash
     * pattern in S15.16 format.
     * @param phase an <code>int</code> containing the dash phase in
     * S15.16 format.
     * @param transform a <code>Transform4</code> object indicating
     * the transform that has been previously applied to all incoming
     * coordinates.  This is required in order to compute dash lengths
     * properly.
     */
    public Dasher(LineSink output,
                  int[] dash, int phase,
                  Transform4 transform) {
        setOutput(output);
        setParameters(dash, phase, transform);
    }

    /**
     * Sets the output <code>LineSink</code> of this
     * <code>Dasher</code>.
     *
     * @param output an output <code>LineSink</code>.
     */
    public void setOutput(LineSink output) {
        this.output = output;
    }

    /**
     * Sets the parameters of this <code>Dasher</code>.
     *
     * @param dash an array of <code>int</code>s containing the dash
     * pattern in S15.16 format.
     * @param phase an <code>int</code> containing the dash phase in
     * S15.16 format.
     * @param transform a <code>Transform4</code> object indicating
     * the transform that has been previously applied to all incoming
     * coordinates.  This is required in order to compute dash lengths
     * properly.
     */
    public void setParameters(int[] dash, int phase,
                              Transform4 transform) {
        if (phase < 0) {
            throw new IllegalArgumentException("phase < 0 !");
        }

        // Normalize so 0 <= phase < dash[0]
        int idx = 0;
        dashOn = true;
        int d;
        while (phase >= (d = dash[idx])) {
            phase -= d;
            idx = (idx + 1) % dash.length;
            dashOn = !dashOn;
        }

        this.dash = new int[dash.length];
        for (int i = 0; i < dash.length; i++) {
            this.dash[i] = dash[i];
        }
        this.startPhase = this.phase = phase;
        this.startDashOn = dashOn;
        this.startIdx = idx;

        this.transform = transform;

        this.m00 = transform.m00;
        this.m01 = transform.m01;
        this.m10 = transform.m10;
        this.m11 = transform.m11;
        this.ldet = ((long)m00*m11 - (long)m01*m10) >> 16;
        this.symmetric = (m00 == m11 && m10 == -m01);
    }

    public void moveTo(int x0, int y0) {
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

    private void goTo(int x1, int y1) {
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

    public void lineTo(int x1, int y1) {
        while (true) {
            int d = dash[idx] - phase;
            int lx = x1 - x0;
            int ly = y1 - y0;

            // Compute segment length in the untransformed
            // coordinate system
            // IMPL NOTE - use fixed point

            int l;
            if (symmetric) {
                l = (int)((PiscesMath.hypot(lx, ly)*65536L)/ldet);
            } else{
                long la = ((long)ly*m00 - (long)lx*m10)/ldet;
                long lb = ((long)ly*m01 - (long)lx*m11)/ldet;
                l = (int)PiscesMath.hypot(la, lb);
            }

            if (l < d) {
                goTo(x1, y1);
                // Advance phase within current dash segment
                phase += l;
                return;
            }

            long t;
            int xsplit, ysplit;
//             // For zero length dashses, SE appears to move 1/8 unit
//             // in device space
//             if (d == 0) {
//                 double dlx = lx/65536.0;
//                 double dly = ly/65536.0;
//                 len = PiscesMath.hypot(dlx, dly);
//                 double dt = 1.0/(8*len);
//                 double dxsplit = (x0/65536.0) + dt*dlx;
//                 double dysplit = (y0/65536.0) + dt*dly;
//                 xsplit = (int)(dxsplit*65536.0);
//                 ysplit = (int)(dysplit*65536.0);
//             } else {
                t = ((long)d << 16)/l;
                xsplit = x0 + (int)(t*(x1 - x0) >> 16);
                ysplit = y0 + (int)(t*(y1 - y0) >> 16);
//             }
            goTo(xsplit, ysplit);

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
