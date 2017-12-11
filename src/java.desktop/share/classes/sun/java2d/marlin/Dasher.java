/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.marlin;

import java.util.Arrays;
import sun.awt.geom.PathConsumer2D;

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
final class Dasher implements PathConsumer2D, MarlinConst {

    static final int REC_LIMIT = 4;
    static final float ERR = 0.01f;
    static final float MIN_T_INC = 1.0f / (1 << REC_LIMIT);

    // More than 24 bits of mantissa means we can no longer accurately
    // measure the number of times cycled through the dash array so we
    // punt and override the phase to just be 0 past that point.
    static final float MAX_CYCLES = 16000000.0f;

    private PathConsumer2D out;
    private float[] dash;
    private int dashLen;
    private float startPhase;
    private boolean startDashOn;
    private int startIdx;

    private boolean starting;
    private boolean needsMoveTo;

    private int idx;
    private boolean dashOn;
    private float phase;

    private float sx, sy;
    private float x0, y0;

    // temporary storage for the current curve
    private final float[] curCurvepts;

    // per-thread renderer context
    final RendererContext rdrCtx;

    // flag to recycle dash array copy
    boolean recycleDashes;

    // dashes ref (dirty)
    final FloatArrayCache.Reference dashes_ref;
    // firstSegmentsBuffer ref (dirty)
    final FloatArrayCache.Reference firstSegmentsBuffer_ref;

    /**
     * Constructs a <code>Dasher</code>.
     * @param rdrCtx per-thread renderer context
     */
    Dasher(final RendererContext rdrCtx) {
        this.rdrCtx = rdrCtx;

        dashes_ref = rdrCtx.newDirtyFloatArrayRef(INITIAL_ARRAY); // 1K

        firstSegmentsBuffer_ref = rdrCtx.newDirtyFloatArrayRef(INITIAL_ARRAY); // 1K
        firstSegmentsBuffer     = firstSegmentsBuffer_ref.initial;

        // we need curCurvepts to be able to contain 2 curves because when
        // dashing curves, we need to subdivide it
        curCurvepts = new float[8 * 2];
    }

    /**
     * Initialize the <code>Dasher</code>.
     *
     * @param out an output <code>PathConsumer2D</code>.
     * @param dash an array of <code>float</code>s containing the dash pattern
     * @param dashLen length of the given dash array
     * @param phase a <code>float</code> containing the dash phase
     * @param recycleDashes true to indicate to recycle the given dash array
     * @return this instance
     */
    Dasher init(final PathConsumer2D out, float[] dash, int dashLen,
                float phase, boolean recycleDashes)
    {
        this.out = out;

        // Normalize so 0 <= phase < dash[0]
        int sidx = 0;
        dashOn = true;
        float sum = 0.0f;
        for (float d : dash) {
            sum += d;
        }
        float cycles = phase / sum;
        if (phase < 0.0f) {
            if (-cycles >= MAX_CYCLES) {
                phase = 0.0f;
            } else {
                int fullcycles = FloatMath.floor_int(-cycles);
                if ((fullcycles & dash.length & 1) != 0) {
                    dashOn = !dashOn;
                }
                phase += fullcycles * sum;
                while (phase < 0.0f) {
                    if (--sidx < 0) {
                        sidx = dash.length - 1;
                    }
                    phase += dash[sidx];
                    dashOn = !dashOn;
                }
            }
        } else if (phase > 0.0f) {
            if (cycles >= MAX_CYCLES) {
                phase = 0.0f;
            } else {
                int fullcycles = FloatMath.floor_int(cycles);
                if ((fullcycles & dash.length & 1) != 0) {
                    dashOn = !dashOn;
                }
                phase -= fullcycles * sum;
                float d;
                while (phase >= (d = dash[sidx])) {
                    phase -= d;
                    sidx = (sidx + 1) % dash.length;
                    dashOn = !dashOn;
                }
            }
        }

        this.dash = dash;
        this.dashLen = dashLen;
        this.phase = phase;
        this.startPhase = phase;
        this.startDashOn = dashOn;
        this.startIdx = sidx;
        this.starting = true;
        this.needsMoveTo = false;
        this.firstSegidx = 0;

        this.recycleDashes = recycleDashes;

        return this; // fluent API
    }

    /**
     * Disposes this dasher:
     * clean up before reusing this instance
     */
    void dispose() {
        if (DO_CLEAN_DIRTY) {
            // Force zero-fill dirty arrays:
            Arrays.fill(curCurvepts, 0.0f);
        }
        // Return arrays:
        if (recycleDashes) {
            dash = dashes_ref.putArray(dash);
        }
        firstSegmentsBuffer = firstSegmentsBuffer_ref.putArray(firstSegmentsBuffer);
    }

    float[] copyDashArray(final float[] dashes) {
        final int len = dashes.length;
        final float[] newDashes;
        if (len <= MarlinConst.INITIAL_ARRAY) {
            newDashes = dashes_ref.initial;
        } else {
            if (DO_STATS) {
                rdrCtx.stats.stat_array_dasher_dasher.add(len);
            }
            newDashes = dashes_ref.getArray(len);
        }
        System.arraycopy(dashes, 0, newDashes, 0, len);
        return newDashes;
    }

    @Override
    public void moveTo(final float x0, final float y0) {
        if (firstSegidx != 0) {
            out.moveTo(sx, sy);
            emitFirstSegments();
        }
        needsMoveTo = true;
        this.idx = startIdx;
        this.dashOn = this.startDashOn;
        this.phase = this.startPhase;
        this.sx = x0;
        this.sy = y0;
        this.x0 = x0;
        this.y0 = y0;
        this.starting = true;
    }

    private void emitSeg(float[] buf, int off, int type) {
        switch (type) {
        case 8:
            out.curveTo(buf[off+0], buf[off+1],
                        buf[off+2], buf[off+3],
                        buf[off+4], buf[off+5]);
            return;
        case 6:
            out.quadTo(buf[off+0], buf[off+1],
                       buf[off+2], buf[off+3]);
            return;
        case 4:
            out.lineTo(buf[off], buf[off+1]);
            return;
        default:
        }
    }

    private void emitFirstSegments() {
        final float[] fSegBuf = firstSegmentsBuffer;

        for (int i = 0, len = firstSegidx; i < len; ) {
            int type = (int)fSegBuf[i];
            emitSeg(fSegBuf, i + 1, type);
            i += (type - 1);
        }
        firstSegidx = 0;
    }
    // We don't emit the first dash right away. If we did, caps would be
    // drawn on it, but we need joins to be drawn if there's a closePath()
    // So, we store the path elements that make up the first dash in the
    // buffer below.
    private float[] firstSegmentsBuffer; // dynamic array
    private int firstSegidx;

    // precondition: pts must be in relative coordinates (relative to x0,y0)
    private void goTo(final float[] pts, final int off, final int type,
                      final boolean on)
    {
        final int index = off + type;
        final float x = pts[index - 4];
        final float y = pts[index - 3];

        if (on) {
            if (starting) {
                goTo_starting(pts, off, type);
            } else {
                if (needsMoveTo) {
                    needsMoveTo = false;
                    out.moveTo(x0, y0);
                }
                emitSeg(pts, off, type);
            }
        } else {
            if (starting) {
                // low probability test (hotspot)
                starting = false;
            }
            needsMoveTo = true;
        }
        this.x0 = x;
        this.y0 = y;
    }

    private void goTo_starting(final float[] pts, final int off, final int type) {
        int len = type - 1; // - 2 + 1
        int segIdx = firstSegidx;
        float[] buf = firstSegmentsBuffer;

        if (segIdx + len  > buf.length) {
            if (DO_STATS) {
                rdrCtx.stats.stat_array_dasher_firstSegmentsBuffer
                    .add(segIdx + len);
            }
            firstSegmentsBuffer = buf
                = firstSegmentsBuffer_ref.widenArray(buf, segIdx,
                                                     segIdx + len);
        }
        buf[segIdx++] = type;
        len--;
        // small arraycopy (2, 4 or 6) but with offset:
        System.arraycopy(pts, off, buf, segIdx, len);
        firstSegidx = segIdx + len;
    }

    @Override
    public void lineTo(final float x1, final float y1) {
        final float dx = x1 - x0;
        final float dy = y1 - y0;

        float len = dx*dx + dy*dy;
        if (len == 0.0f) {
            return;
        }
        len = (float) Math.sqrt(len);

        // The scaling factors needed to get the dx and dy of the
        // transformed dash segments.
        final float cx = dx / len;
        final float cy = dy / len;

        final float[] _curCurvepts = curCurvepts;
        final float[] _dash = dash;
        final int _dashLen = this.dashLen;

        int _idx = idx;
        boolean _dashOn = dashOn;
        float _phase = phase;

        float leftInThisDashSegment;
        float d, dashdx, dashdy, p;

        while (true) {
            d = _dash[_idx];
            leftInThisDashSegment = d - _phase;

            if (len <= leftInThisDashSegment) {
                _curCurvepts[0] = x1;
                _curCurvepts[1] = y1;

                goTo(_curCurvepts, 0, 4, _dashOn);

                // Advance phase within current dash segment
                _phase += len;

                // TODO: compare float values using epsilon:
                if (len == leftInThisDashSegment) {
                    _phase = 0.0f;
                    _idx = (_idx + 1) % _dashLen;
                    _dashOn = !_dashOn;
                }

                // Save local state:
                idx = _idx;
                dashOn = _dashOn;
                phase = _phase;
                return;
            }

            dashdx = d * cx;
            dashdy = d * cy;

            if (_phase == 0.0f) {
                _curCurvepts[0] = x0 + dashdx;
                _curCurvepts[1] = y0 + dashdy;
            } else {
                p = leftInThisDashSegment / d;
                _curCurvepts[0] = x0 + p * dashdx;
                _curCurvepts[1] = y0 + p * dashdy;
            }

            goTo(_curCurvepts, 0, 4, _dashOn);

            len -= leftInThisDashSegment;
            // Advance to next dash segment
            _idx = (_idx + 1) % _dashLen;
            _dashOn = !_dashOn;
            _phase = 0.0f;
        }
    }

    // shared instance in Dasher
    private final LengthIterator li = new LengthIterator();

    // preconditions: curCurvepts must be an array of length at least 2 * type,
    // that contains the curve we want to dash in the first type elements
    private void somethingTo(final int type) {
        if (pointCurve(curCurvepts, type)) {
            return;
        }
        final LengthIterator _li = li;
        final float[] _curCurvepts = curCurvepts;
        final float[] _dash = dash;
        final int _dashLen = this.dashLen;

        _li.initializeIterationOnCurve(_curCurvepts, type);

        int _idx = idx;
        boolean _dashOn = dashOn;
        float _phase = phase;

        // initially the current curve is at curCurvepts[0...type]
        int curCurveoff = 0;
        float lastSplitT = 0.0f;
        float t;
        float leftInThisDashSegment = _dash[_idx] - _phase;

        while ((t = _li.next(leftInThisDashSegment)) < 1.0f) {
            if (t != 0.0f) {
                Helpers.subdivideAt((t - lastSplitT) / (1.0f - lastSplitT),
                                    _curCurvepts, curCurveoff,
                                    _curCurvepts, 0,
                                    _curCurvepts, type, type);
                lastSplitT = t;
                goTo(_curCurvepts, 2, type, _dashOn);
                curCurveoff = type;
            }
            // Advance to next dash segment
            _idx = (_idx + 1) % _dashLen;
            _dashOn = !_dashOn;
            _phase = 0.0f;
            leftInThisDashSegment = _dash[_idx];
        }

        goTo(_curCurvepts, curCurveoff + 2, type, _dashOn);

        _phase += _li.lastSegLen();
        if (_phase >= _dash[_idx]) {
            _phase = 0.0f;
            _idx = (_idx + 1) % _dashLen;
            _dashOn = !_dashOn;
        }
        // Save local state:
        idx = _idx;
        dashOn = _dashOn;
        phase = _phase;

        // reset LengthIterator:
        _li.reset();
    }

    private static boolean pointCurve(float[] curve, int type) {
        for (int i = 2; i < type; i++) {
            if (curve[i] != curve[i-2]) {
                return false;
            }
        }
        return true;
    }

    // Objects of this class are used to iterate through curves. They return
    // t values where the left side of the curve has a specified length.
    // It does this by subdividing the input curve until a certain error
    // condition has been met. A recursive subdivision procedure would
    // return as many as 1<<limit curves, but this is an iterator and we
    // don't need all the curves all at once, so what we carry out a
    // lazy inorder traversal of the recursion tree (meaning we only move
    // through the tree when we need the next subdivided curve). This saves
    // us a lot of memory because at any one time we only need to store
    // limit+1 curves - one for each level of the tree + 1.
    // NOTE: the way we do things here is not enough to traverse a general
    // tree; however, the trees we are interested in have the property that
    // every non leaf node has exactly 2 children
    static final class LengthIterator {
        private enum Side {LEFT, RIGHT}
        // Holds the curves at various levels of the recursion. The root
        // (i.e. the original curve) is at recCurveStack[0] (but then it
        // gets subdivided, the left half is put at 1, so most of the time
        // only the right half of the original curve is at 0)
        private final float[][] recCurveStack; // dirty
        // sides[i] indicates whether the node at level i+1 in the path from
        // the root to the current leaf is a left or right child of its parent.
        private final Side[] sides; // dirty
        private int curveType;
        // lastT and nextT delimit the current leaf.
        private float nextT;
        private float lenAtNextT;
        private float lastT;
        private float lenAtLastT;
        private float lenAtLastSplit;
        private float lastSegLen;
        // the current level in the recursion tree. 0 is the root. limit
        // is the deepest possible leaf.
        private int recLevel;
        private boolean done;

        // the lengths of the lines of the control polygon. Only its first
        // curveType/2 - 1 elements are valid. This is an optimization. See
        // next() for more detail.
        private final float[] curLeafCtrlPolyLengths = new float[3];

        LengthIterator() {
            this.recCurveStack = new float[REC_LIMIT + 1][8];
            this.sides = new Side[REC_LIMIT];
            // if any methods are called without first initializing this object
            // on a curve, we want it to fail ASAP.
            this.nextT = Float.MAX_VALUE;
            this.lenAtNextT = Float.MAX_VALUE;
            this.lenAtLastSplit = Float.MIN_VALUE;
            this.recLevel = Integer.MIN_VALUE;
            this.lastSegLen = Float.MAX_VALUE;
            this.done = true;
        }

        /**
         * Reset this LengthIterator.
         */
        void reset() {
            // keep data dirty
            // as it appears not useful to reset data:
            if (DO_CLEAN_DIRTY) {
                final int recLimit = recCurveStack.length - 1;
                for (int i = recLimit; i >= 0; i--) {
                    Arrays.fill(recCurveStack[i], 0.0f);
                }
                Arrays.fill(sides, Side.LEFT);
                Arrays.fill(curLeafCtrlPolyLengths, 0.0f);
                Arrays.fill(nextRoots, 0.0f);
                Arrays.fill(flatLeafCoefCache, 0.0f);
                flatLeafCoefCache[2] = -1.0f;
            }
        }

        void initializeIterationOnCurve(float[] pts, int type) {
            // optimize arraycopy (8 values faster than 6 = type):
            System.arraycopy(pts, 0, recCurveStack[0], 0, 8);
            this.curveType = type;
            this.recLevel = 0;
            this.lastT = 0.0f;
            this.lenAtLastT = 0.0f;
            this.nextT = 0.0f;
            this.lenAtNextT = 0.0f;
            goLeft(); // initializes nextT and lenAtNextT properly
            this.lenAtLastSplit = 0.0f;
            if (recLevel > 0) {
                this.sides[0] = Side.LEFT;
                this.done = false;
            } else {
                // the root of the tree is a leaf so we're done.
                this.sides[0] = Side.RIGHT;
                this.done = true;
            }
            this.lastSegLen = 0.0f;
        }

        // 0 == false, 1 == true, -1 == invalid cached value.
        private int cachedHaveLowAcceleration = -1;

        private boolean haveLowAcceleration(float err) {
            if (cachedHaveLowAcceleration == -1) {
                final float len1 = curLeafCtrlPolyLengths[0];
                final float len2 = curLeafCtrlPolyLengths[1];
                // the test below is equivalent to !within(len1/len2, 1, err).
                // It is using a multiplication instead of a division, so it
                // should be a bit faster.
                if (!Helpers.within(len1, len2, err * len2)) {
                    cachedHaveLowAcceleration = 0;
                    return false;
                }
                if (curveType == 8) {
                    final float len3 = curLeafCtrlPolyLengths[2];
                    // if len1 is close to 2 and 2 is close to 3, that probably
                    // means 1 is close to 3 so the second part of this test might
                    // not be needed, but it doesn't hurt to include it.
                    final float errLen3 = err * len3;
                    if (!(Helpers.within(len2, len3, errLen3) &&
                          Helpers.within(len1, len3, errLen3))) {
                        cachedHaveLowAcceleration = 0;
                        return false;
                    }
                }
                cachedHaveLowAcceleration = 1;
                return true;
            }

            return (cachedHaveLowAcceleration == 1);
        }

        // we want to avoid allocations/gc so we keep this array so we
        // can put roots in it,
        private final float[] nextRoots = new float[4];

        // caches the coefficients of the current leaf in its flattened
        // form (see inside next() for what that means). The cache is
        // invalid when it's third element is negative, since in any
        // valid flattened curve, this would be >= 0.
        private final float[] flatLeafCoefCache = new float[]{0.0f, 0.0f, -1.0f, 0.0f};

        // returns the t value where the remaining curve should be split in
        // order for the left subdivided curve to have length len. If len
        // is >= than the length of the uniterated curve, it returns 1.
        float next(final float len) {
            final float targetLength = lenAtLastSplit + len;
            while (lenAtNextT < targetLength) {
                if (done) {
                    lastSegLen = lenAtNextT - lenAtLastSplit;
                    return 1.0f;
                }
                goToNextLeaf();
            }
            lenAtLastSplit = targetLength;
            final float leaflen = lenAtNextT - lenAtLastT;
            float t = (targetLength - lenAtLastT) / leaflen;

            // cubicRootsInAB is a fairly expensive call, so we just don't do it
            // if the acceleration in this section of the curve is small enough.
            if (!haveLowAcceleration(0.05f)) {
                // We flatten the current leaf along the x axis, so that we're
                // left with a, b, c which define a 1D Bezier curve. We then
                // solve this to get the parameter of the original leaf that
                // gives us the desired length.
                final float[] _flatLeafCoefCache = flatLeafCoefCache;

                if (_flatLeafCoefCache[2] < 0.0f) {
                    float x =     curLeafCtrlPolyLengths[0],
                          y = x + curLeafCtrlPolyLengths[1];
                    if (curveType == 8) {
                        float z = y + curLeafCtrlPolyLengths[2];
                        _flatLeafCoefCache[0] = 3.0f * (x - y) + z;
                        _flatLeafCoefCache[1] = 3.0f * (y - 2.0f * x);
                        _flatLeafCoefCache[2] = 3.0f * x;
                        _flatLeafCoefCache[3] = -z;
                    } else if (curveType == 6) {
                        _flatLeafCoefCache[0] = 0.0f;
                        _flatLeafCoefCache[1] = y - 2.0f * x;
                        _flatLeafCoefCache[2] = 2.0f * x;
                        _flatLeafCoefCache[3] = -y;
                    }
                }
                float a = _flatLeafCoefCache[0];
                float b = _flatLeafCoefCache[1];
                float c = _flatLeafCoefCache[2];
                float d = t * _flatLeafCoefCache[3];

                // we use cubicRootsInAB here, because we want only roots in 0, 1,
                // and our quadratic root finder doesn't filter, so it's just a
                // matter of convenience.
                int n = Helpers.cubicRootsInAB(a, b, c, d, nextRoots, 0, 0.0f, 1.0f);
                if (n == 1 && !Float.isNaN(nextRoots[0])) {
                    t = nextRoots[0];
                }
            }
            // t is relative to the current leaf, so we must make it a valid parameter
            // of the original curve.
            t = t * (nextT - lastT) + lastT;
            if (t >= 1.0f) {
                t = 1.0f;
                done = true;
            }
            // even if done = true, if we're here, that means targetLength
            // is equal to, or very, very close to the total length of the
            // curve, so lastSegLen won't be too high. In cases where len
            // overshoots the curve, this method will exit in the while
            // loop, and lastSegLen will still be set to the right value.
            lastSegLen = len;
            return t;
        }

        float lastSegLen() {
            return lastSegLen;
        }

        // go to the next leaf (in an inorder traversal) in the recursion tree
        // preconditions: must be on a leaf, and that leaf must not be the root.
        private void goToNextLeaf() {
            // We must go to the first ancestor node that has an unvisited
            // right child.
            int _recLevel = recLevel;
            final Side[] _sides = sides;

            _recLevel--;
            while(_sides[_recLevel] == Side.RIGHT) {
                if (_recLevel == 0) {
                    recLevel = 0;
                    done = true;
                    return;
                }
                _recLevel--;
            }

            _sides[_recLevel] = Side.RIGHT;
            // optimize arraycopy (8 values faster than 6 = type):
            System.arraycopy(recCurveStack[_recLevel], 0,
                             recCurveStack[_recLevel+1], 0, 8);
            _recLevel++;

            recLevel = _recLevel;
            goLeft();
        }

        // go to the leftmost node from the current node. Return its length.
        private void goLeft() {
            float len = onLeaf();
            if (len >= 0.0f) {
                lastT = nextT;
                lenAtLastT = lenAtNextT;
                nextT += (1 << (REC_LIMIT - recLevel)) * MIN_T_INC;
                lenAtNextT += len;
                // invalidate caches
                flatLeafCoefCache[2] = -1.0f;
                cachedHaveLowAcceleration = -1;
            } else {
                Helpers.subdivide(recCurveStack[recLevel], 0,
                                  recCurveStack[recLevel+1], 0,
                                  recCurveStack[recLevel], 0, curveType);
                sides[recLevel] = Side.LEFT;
                recLevel++;
                goLeft();
            }
        }

        // this is a bit of a hack. It returns -1 if we're not on a leaf, and
        // the length of the leaf if we are on a leaf.
        private float onLeaf() {
            final float[] curve = recCurveStack[recLevel];
            final int _curveType = curveType;
            float polyLen = 0.0f;

            float x0 = curve[0], y0 = curve[1];
            for (int i = 2; i < _curveType; i += 2) {
                final float x1 = curve[i], y1 = curve[i+1];
                final float len = Helpers.linelen(x0, y0, x1, y1);
                polyLen += len;
                curLeafCtrlPolyLengths[(i >> 1) - 1] = len;
                x0 = x1;
                y0 = y1;
            }

            final float lineLen = Helpers.linelen(curve[0], curve[1],
                                                  curve[_curveType-2],
                                                  curve[_curveType-1]);
            if ((polyLen - lineLen) < ERR || recLevel == REC_LIMIT) {
                return (polyLen + lineLen) / 2.0f;
            }
            return -1.0f;
        }
    }

    @Override
    public void curveTo(final float x1, final float y1,
                        final float x2, final float y2,
                        final float x3, final float y3)
    {
        final float[] _curCurvepts = curCurvepts;
        _curCurvepts[0] = x0;        _curCurvepts[1] = y0;
        _curCurvepts[2] = x1;        _curCurvepts[3] = y1;
        _curCurvepts[4] = x2;        _curCurvepts[5] = y2;
        _curCurvepts[6] = x3;        _curCurvepts[7] = y3;
        somethingTo(8);
    }

    @Override
    public void quadTo(final float x1, final float y1,
                       final float x2, final float y2)
    {
        final float[] _curCurvepts = curCurvepts;
        _curCurvepts[0] = x0;        _curCurvepts[1] = y0;
        _curCurvepts[2] = x1;        _curCurvepts[3] = y1;
        _curCurvepts[4] = x2;        _curCurvepts[5] = y2;
        somethingTo(6);
    }

    @Override
    public void closePath() {
        lineTo(sx, sy);
        if (firstSegidx != 0) {
            if (!dashOn || needsMoveTo) {
                out.moveTo(sx, sy);
            }
            emitFirstSegments();
        }
        moveTo(sx, sy);
    }

    @Override
    public void pathDone() {
        if (firstSegidx != 0) {
            out.moveTo(sx, sy);
            emitFirstSegments();
        }
        out.pathDone();

        // Dispose this instance:
        dispose();
    }

    @Override
    public long getNativeConsumer() {
        throw new InternalError("Dasher does not use a native consumer");
    }
}

