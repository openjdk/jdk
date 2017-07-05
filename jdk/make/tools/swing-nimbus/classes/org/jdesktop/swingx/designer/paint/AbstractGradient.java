/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
package org.jdesktop.swingx.designer.paint;

import java.awt.Color;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.Paint;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** @author rbair */
public abstract class AbstractGradient extends PaintModel {
    private final Comparator<GradientStop> sorter = new Comparator<GradientStop>() {
        public int compare(GradientStop s1, GradientStop s2) {
            //since a float value may be -.001 or .001, and since casting
            //this to an int will round off to 0, I have to do a more direct
            //comparison
            float v = s1.getPosition() - s2.getPosition();

            if (v < 0) return -1;
            else if (v == 0) return 0;
            else return 1;
        }
    };
    private PropertyChangeListener stopListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals("position")) {
                if (stops.contains((GradientStop) evt.getSource())) {
                    resortModel(true);
                } else {
                    System.err.println("[WARNING] The position of an orphaned stop was changed.");
                }
            } else {
                firePropertyChange("paint", null, getPaint());
            }
        }
    };

    private List<GradientStop> stops = new ArrayList<GradientStop>();
    private List<GradientStop> unmodifiable;
    private CycleMethod cycleMethod;

    protected AbstractGradient() {
        unmodifiable = Collections.unmodifiableList(stops);
        cycleMethod = CycleMethod.NO_CYCLE;
        setStops(new GradientStop(0, new Matte(Color.BLUE, null)),
                new GradientStop(1, new Matte(Color.WHITE, null)));
    }

    /**
     * Copy stops and cycleMethod from src to dst
     *
     * @param dst The gradient to update to same stops and cycle method as this gradient
     */
    protected void copyTo(AbstractGradient dst) {
        dst.stops.clear();
        List<GradientStop> stops = new ArrayList<GradientStop>();
        for (GradientStop stop : this.stops) {
            stops.add(stop.clone());
        }
        dst.setStops(stops);
        dst.cycleMethod = this.cycleMethod;
    }


    public PaintControlType getPaintControlType() {
        return PaintControlType.control_line;
    }

    public void setCycleMethod(CycleMethod method) {
        CycleMethod old = cycleMethod;
        Paint oldp = getPaint();
        cycleMethod = method == null ? CycleMethod.NO_CYCLE : method;
        firePropertyChange("cycleMethod", old, cycleMethod);
        firePropertyChange("paint", oldp, getPaint());
    }

    public final CycleMethod getCycleMethod() {
        return cycleMethod;
    }

    public void setStops(GradientStop... stops) {
        if (stops == null || stops.length < 1) {
            throw new IllegalArgumentException("Must have more than one stop");
        }
        List<GradientStop> old = new ArrayList<GradientStop>(this.stops);
        for (GradientStop stop : old) {
            stop.removePropertyChangeListener(stopListener);
        }
        Paint oldp = getPaint();
        this.stops.clear();
        Collections.addAll(this.stops, stops);
        for (GradientStop stop : this.stops) {
            stop.addPropertyChangeListener(stopListener);
        }
        resortModel(false);
        firePropertyChange("stops", old, getStops());
        firePropertyChange("paint", oldp, getPaint());
    }

    public final void setStops(List<GradientStop> stops) {
        setStops(stops == null ? null : stops.toArray(new GradientStop[0]));
    }

    public final List<GradientStop> getStops() {
        return unmodifiable;
    }

    private void resortModel(boolean fireEvent) {
        Collections.sort(this.stops, sorter);
        if (fireEvent) {
            Paint oldp = getPaint();
            firePropertyChange("stops", null, getStops());
            firePropertyChange("paint", oldp, getPaint());
        }
    }

    //adds a new stop, and interoplates the proper color to use based on
    //its position
    public GradientStop addStop(float position) {
        GradientStop prevStop = null;
        GradientStop nextStop = null;
        for (GradientStop stop : stops) {
            if (stop.getPosition() <= position) {
                prevStop = stop;
            } else if (stop.getPosition() >= position) {
                nextStop = stop;
            }
        }

        Matte c = null;
        if (prevStop != null && nextStop != null) {
            //interpolate the value of c
            c = interpolate(prevStop.getColor(), nextStop.getColor(),
                    position / (nextStop.getPosition() - prevStop.getPosition()));
        } else if (prevStop != null) {
            c = prevStop.getColor().clone();
        } else if (nextStop != null) {
            c = nextStop.getColor().clone();
        }

        return addStop(position, c);
    }

    public GradientStop addStop(float position, Matte color) {
        GradientStop s = new GradientStop(position, color);
        s.addPropertyChangeListener(stopListener);
        List<GradientStop> old = new ArrayList<GradientStop>(stops);
        Paint oldp = getPaint();
        stops.add(s);
        resortModel(false);
        firePropertyChange("stops", old, getStops());
        firePropertyChange("paint", oldp, getPaint());

        return s;
    }

    public GradientStop removeStop(GradientStop s) {
        List<GradientStop> old = new ArrayList<GradientStop>(stops);
        Paint oldp = getPaint();
        stops.remove(s);
        s.removePropertyChangeListener(stopListener);
        resortModel(false);
        firePropertyChange("stops", old, getStops());
        firePropertyChange("paint", oldp, getPaint());
        return s;
    }

    @Override public Paint getPaint() {
        if (stops.size() == 0) {
            return null;
        }

        //there are stops.size() number of main stops. Between each is
        //a fractional stop. Thus, there are:
        //stops.size() + stops.size() - 1
        //number of fractions and colors.

        float[] fractions = new float[stops.size() + stops.size() - 1];
        Matte[] colors = new Matte[fractions.length];

        //for each stop, create the stop and it's associated fraction
        int index = 0; // the index into fractions and colors
        for (int i = 0; i < stops.size(); i++) {
            GradientStop s = stops.get(i);
            //copy over the stop's data
            colors[index] = s.getColor();
            fractions[index] = s.getPosition();

            //If this isn't the last stop, then add in the fraction
            if (index < fractions.length - 1) {
                float f1 = s.getPosition();
                float f2 = stops.get(i + 1).getPosition();

                index++;
                fractions[index] = f1 + (f2 - f1) * s.getMidpoint();
                colors[index] = interpolate(colors[index - 1], stops.get(i + 1).getColor(), .5f);
            }

            index++;
        }

        for (int i = 1; i < fractions.length; i++) {
            //to avoid an error with LinearGradientPaint where two fractions
            //are identical, bump up the fraction value by a miniscule amount
            //if it is identical to the previous one
            //NOTE: The <= is critical because the previous value may already
            //have been bumped up
            if (fractions[i] <= fractions[i - 1]) {
                fractions[i] = fractions[i - 1] + .000001f;
            }
        }

        //another boundary condition where multiple stops are all at the end. The
        //previous loop bumped all but one of these past 1.0, which is bad.
        //so remove any fractions (and their colors!) that are beyond 1.0
        int outOfBoundsIndex = -1;
        for (int i = 0; i < fractions.length; i++) {
            if (fractions[i] > 1) {
                outOfBoundsIndex = i;
                break;
            }
        }

        if (outOfBoundsIndex >= 0) {
            float[] f = fractions;
            Matte[] c = colors;
            fractions = new float[outOfBoundsIndex];
            colors = new Matte[outOfBoundsIndex];
            System.arraycopy(f, 0, fractions, 0, outOfBoundsIndex);
            System.arraycopy(c, 0, colors, 0, outOfBoundsIndex);
        }

        return createPaint(fractions, colors, cycleMethod);
    }

    protected abstract Paint createPaint(float[] fractions, Matte[] colors, CycleMethod method);

    protected static Matte interpolate(Matte v0, Matte v1, float fraction) {
        return new Matte(interpolate(v0.getColor(), v1.getColor(), fraction), v0.getUiDefaults());
    }

    protected static Color interpolate(Color v0, Color v1, float fraction) {
        int r = v0.getRed() +
                (int) ((v1.getRed() - v0.getRed()) * fraction + 0.5f);
        int g = v0.getGreen() +
                (int) ((v1.getGreen() - v0.getGreen()) * fraction + 0.5f);
        int b = v0.getBlue() +
                (int) ((v1.getBlue() - v0.getBlue()) * fraction + 0.5f);
        int a = v0.getAlpha() +
                (int) ((v1.getAlpha() - v0.getAlpha()) * fraction + 0.5f);
        return new Color(r, g, b, a);
    }
}
