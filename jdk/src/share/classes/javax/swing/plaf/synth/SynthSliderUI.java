/*
 * Copyright 2002-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.swing.plaf.synth;

import java.awt.event.*;
import java.awt.Graphics;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.Point;
import java.awt.Insets;
import java.beans.*;
import java.util.Dictionary;
import java.util.Enumeration;
import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.BasicSliderUI;
import sun.swing.plaf.synth.SynthUI;
import sun.swing.SwingUtilities2;


/**
 * Synth's SliderUI.
 *
 * @author Joshua Outwater
 */
class SynthSliderUI extends BasicSliderUI implements PropertyChangeListener,
        SynthUI {
    protected Dimension contentDim = new Dimension();
    protected Rectangle valueRect = new Rectangle();
    protected boolean paintValue;

    /**
     * When a JSlider is used as a renderer in a JTable, its layout is not
     * being recomputed even though the size is changing. Even though there
     * is a ComponentListener installed, it is not being notified. As such,
     * at times when being asked to paint the layout should first be redone.
     * At the end of the layout method we set this lastSize variable, which
     * represents the size of the slider the last time it was layed out.
     *
     * In the paint method we then check to see that this is accurate, that
     * the slider has not changed sizes since being last layed out. If necessary
     * we recompute the layout.
     */
    private Dimension lastSize = null;

    private int trackHeight;
    private int trackBorder;
    private int thumbWidth;
    private int thumbHeight;

    private SynthStyle style;
    private SynthStyle sliderTrackStyle;
    private SynthStyle sliderThumbStyle;

    /** Used to determine the color to paint the thumb. */
    private transient boolean thumbActive; //happens on rollover, and when pressed
    private transient boolean thumbPressed; //happens when mouse was depressed while over thumb

    ///////////////////////////////////////////////////
    // ComponentUI Interface Implementation methods
    ///////////////////////////////////////////////////
    public static ComponentUI createUI(JComponent c) {
        return new SynthSliderUI((JSlider)c);
    }

    public SynthSliderUI(JSlider c) {
        super(c);
    }

    protected void installDefaults(JSlider slider) {
        updateStyle(slider);
    }

    protected void uninstallDefaults() {
        SynthContext context = getContext(slider, ENABLED);
        style.uninstallDefaults(context);
        context.dispose();
        style = null;

        context = getContext(slider, Region.SLIDER_TRACK, ENABLED);
        sliderTrackStyle.uninstallDefaults(context);
        context.dispose();
        sliderTrackStyle = null;

        context = getContext(slider, Region.SLIDER_THUMB, ENABLED);
        sliderThumbStyle.uninstallDefaults(context);
        context.dispose();
        sliderThumbStyle = null;
    }

    protected void installListeners(JSlider slider) {
        super.installListeners(slider);
        slider.addPropertyChangeListener(this);
    }

    protected void uninstallListeners(JSlider slider) {
        slider.removePropertyChangeListener(this);
        super.uninstallListeners(slider);
    }

    private void updateStyle(JSlider c) {
        SynthContext context = getContext(c, ENABLED);
        SynthStyle oldStyle = style;
        style = SynthLookAndFeel.updateStyle(context, this);

        if (style != oldStyle) {
            thumbWidth =
                style.getInt(context, "Slider.thumbWidth", 30);

            thumbHeight =
                style.getInt(context, "Slider.thumbHeight", 14);

            // handle scaling for sizeVarients for special case components. The
            // key "JComponent.sizeVariant" scales for large/small/mini
            // components are based on Apples LAF
            String scaleKey = (String)slider.getClientProperty(
                    "JComponent.sizeVariant");
            if (scaleKey != null){
                if ("large".equals(scaleKey)){
                    thumbWidth *= 1.15;
                    thumbHeight *= 1.15;
                } else if ("small".equals(scaleKey)){
                    thumbWidth *= 0.857;
                    thumbHeight *= 0.857;
                } else if ("mini".equals(scaleKey)){
                    thumbWidth *= 0.784;
                    thumbHeight *= 0.784;
                }
            }

            trackBorder =
                style.getInt(context, "Slider.trackBorder", 1);

            trackHeight = thumbHeight + trackBorder * 2;

            paintValue = style.getBoolean(context,
                    "Slider.paintValue", true);
            if (oldStyle != null) {
                uninstallKeyboardActions(c);
                installKeyboardActions(c);
            }
        }
        context.dispose();

        context = getContext(c, Region.SLIDER_TRACK, ENABLED);
        sliderTrackStyle =
            SynthLookAndFeel.updateStyle(context, this);
        context.dispose();

        context = getContext(c, Region.SLIDER_THUMB, ENABLED);
        sliderThumbStyle =
            SynthLookAndFeel.updateStyle(context, this);
        context.dispose();
    }

    protected TrackListener createTrackListener(JSlider s) {
        return new SynthTrackListener();
    }

    private void updateThumbState(int x, int y) {
        setThumbActive(thumbRect.contains(x, y));
    }

    private void updateThumbState(int x, int y, boolean pressed) {
        updateThumbState(x, y);
        setThumbPressed(pressed);
    }

    private void setThumbActive(boolean active) {
        if (thumbActive != active) {
            thumbActive = active;
            slider.repaint(thumbRect);
        }
    }

    private void setThumbPressed(boolean pressed) {
        if (thumbPressed != pressed) {
            thumbPressed = pressed;
            slider.repaint(thumbRect);
        }
    }

    public int getBaseline(JComponent c, int width, int height) {
        if (c == null) {
            throw new NullPointerException("Component must be non-null");
        }
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException(
                    "Width and height must be >= 0");
        }
        if (slider.getPaintLabels() && labelsHaveSameBaselines()) {
            // Get the insets for the track.
            Insets trackInsets = new Insets(0, 0, 0, 0);
            SynthContext trackContext = getContext(slider,
                                                   Region.SLIDER_TRACK);
            style.getInsets(trackContext, trackInsets);
            trackContext.dispose();
            if (slider.getOrientation() == JSlider.HORIZONTAL) {
                int valueHeight = 0;
                if (paintValue) {
                    SynthContext context = getContext(slider);
                    valueHeight = context.getStyle().getGraphicsUtils(context).
                            getMaximumCharHeight(context);
                    context.dispose();
                }
                int tickHeight = 0;
                if (slider.getPaintTicks()) {
                    tickHeight = getTickLength();
                }
                int labelHeight = getHeightOfTallestLabel();
                int contentHeight = valueHeight + trackHeight +
                        trackInsets.top + trackInsets.bottom +
                        tickHeight + labelHeight + 4;
                int centerY = height / 2 - contentHeight / 2;
                centerY += valueHeight + 2;
                centerY += trackHeight + trackInsets.top + trackInsets.bottom;
                centerY += tickHeight + 2;
                JComponent label = (JComponent) slider.getLabelTable().elements().nextElement();
                Dimension pref = label.getPreferredSize();
                return centerY + label.getBaseline(pref.width, pref.height);
            }
            else { // VERTICAL
                Integer value = slider.getInverted() ? getLowestValue() :
                                                       getHighestValue();
                if (value != null) {
                    int valueY = insetCache.top;
                    int valueHeight = 0;
                    if (paintValue) {
                        SynthContext context = getContext(slider);
                        valueHeight = context.getStyle().getGraphicsUtils(
                                context).getMaximumCharHeight(context);
                        context.dispose();
                    }
                    int contentHeight = height - insetCache.top -
                            insetCache.bottom;
                    int trackY = valueY + valueHeight;
                    int trackHeight = contentHeight - valueHeight;
                    int yPosition = yPositionForValue(value.intValue(), trackY,
                                                      trackHeight);
                    JComponent label = (JComponent) slider.getLabelTable().get(value);
                    Dimension pref = label.getPreferredSize();
                    return yPosition - pref.height / 2 +
                            label.getBaseline(pref.width, pref.height);
                }
            }
        }
        return -1;
    }

    public Dimension getPreferredSize(JComponent c)  {
        recalculateIfInsetsChanged();
        Dimension d = new Dimension(contentDim);
        if (slider.getOrientation() == JSlider.VERTICAL) {
            d.height = 200;
        } else {
            d.width = 200;
        }
        Insets i = slider.getInsets();
        d.width += i.left + i.right;
        d.height += i.top + i.bottom;
        return d;
    }

    public Dimension getMinimumSize(JComponent c) {
        recalculateIfInsetsChanged();
        Dimension d = new Dimension(contentDim);
        if (slider.getOrientation() == JSlider.VERTICAL) {
            d.height = thumbRect.height + insetCache.top + insetCache.bottom;
        } else {
            d.width = thumbRect.width + insetCache.left + insetCache.right;
        }
        return d;
    }

    protected void calculateGeometry() {
        layout();
        calculateThumbLocation();
    }

    protected void layout() {
        SynthContext context = getContext(slider);
        SynthGraphicsUtils synthGraphics = style.getGraphicsUtils(context);

        // Set the thumb size.
        Dimension size = getThumbSize();
        thumbRect.setSize(size.width, size.height);

        // Get the insets for the track.
        Insets trackInsets = new Insets(0, 0, 0, 0);
        SynthContext trackContext = getContext(slider, Region.SLIDER_TRACK);
        style.getInsets(trackContext, trackInsets);
        trackContext.dispose();

        if (slider.getOrientation() == JSlider.HORIZONTAL) {
            // Calculate the height of all the subcomponents so we can center
            // them.
            valueRect.height = 0;
            if (paintValue) {
                valueRect.height =
                    synthGraphics.getMaximumCharHeight(context);
            }

            trackRect.height = trackHeight;

            tickRect.height = 0;
            if (slider.getPaintTicks()) {
                tickRect.height = getTickLength();
            }

            labelRect.height = 0;
            if (slider.getPaintLabels()) {
                labelRect.height = getHeightOfTallestLabel();
            }

            contentDim.height = valueRect.height + trackRect.height
                + trackInsets.top + trackInsets.bottom
                + tickRect.height + labelRect.height + 4;
            contentDim.width = slider.getWidth() - insetCache.left
                - insetCache.right;

            // Check if any of the labels will paint out of bounds.
            int pad = 0;
            if (slider.getPaintLabels()) {
                // Calculate the track rectangle.  It is necessary for
                // xPositionForValue to return correct values.
                trackRect.x = insetCache.left;
                trackRect.width = contentDim.width;

                Dictionary dictionary = slider.getLabelTable();
                if (dictionary != null) {
                    int minValue = slider.getMinimum();
                    int maxValue = slider.getMaximum();

                    // Iterate through the keys in the dictionary and find the
                    // first and last labels indices that fall within the
                    // slider range.
                    int firstLblIdx = Integer.MAX_VALUE;
                    int lastLblIdx = Integer.MIN_VALUE;
                    for (Enumeration keys = dictionary.keys();
                            keys.hasMoreElements(); ) {
                        int keyInt = ((Integer)keys.nextElement()).intValue();
                        if (keyInt >= minValue && keyInt < firstLblIdx) {
                            firstLblIdx = keyInt;
                        }
                        if (keyInt <= maxValue && keyInt > lastLblIdx) {
                            lastLblIdx = keyInt;
                        }
                    }
                    // Calculate the pad necessary for the labels at the first
                    // and last visible indices.
                    pad = getPadForLabel(firstLblIdx);
                    pad = Math.max(pad, getPadForLabel(lastLblIdx));
                }
            }
            // Calculate the painting rectangles for each of the different
            // slider areas.
            valueRect.x = trackRect.x = tickRect.x = labelRect.x =
                (insetCache.left + pad);
            valueRect.width = trackRect.width = tickRect.width =
                labelRect.width = (contentDim.width - (pad * 2));

            int centerY = slider.getHeight() / 2 - contentDim.height / 2;

            valueRect.y = centerY;
            centerY += valueRect.height + 2;

            trackRect.y = centerY + trackInsets.top;
            centerY += trackRect.height + trackInsets.top + trackInsets.bottom;

            tickRect.y = centerY;
            centerY += tickRect.height + 2;

            labelRect.y = centerY;
            centerY += labelRect.height;
        } else {
            // Calculate the width of all the subcomponents so we can center
            // them.
            trackRect.width = trackHeight;

            tickRect.width = 0;
            if (slider.getPaintTicks()) {
                tickRect.width = getTickLength();
            }

            labelRect.width = 0;
            if (slider.getPaintLabels()) {
                labelRect.width = getWidthOfWidestLabel();
            }

            valueRect.y = insetCache.top;
            valueRect.height = 0;
            if (paintValue) {
                valueRect.height =
                    synthGraphics.getMaximumCharHeight(context);
            }

            // Get the max width of the min or max value of the slider.
            FontMetrics fm = slider.getFontMetrics(slider.getFont());
            valueRect.width = Math.max(
                synthGraphics.computeStringWidth(context, slider.getFont(),
                    fm, "" + slider.getMaximum()),
                synthGraphics.computeStringWidth(context, slider.getFont(),
                    fm, "" + slider.getMinimum()));

            int l = valueRect.width / 2;
            int w1 = trackInsets.left + trackRect.width / 2;
            int w2 = trackRect.width / 2 + trackInsets.right +
                              tickRect.width + labelRect.width;
            contentDim.width = Math.max(w1, l) + Math.max(w2, l) +
                    2 + insetCache.left + insetCache.right;
            contentDim.height = slider.getHeight() -
                                    insetCache.top - insetCache.bottom;

            // Layout the components.
            trackRect.y = tickRect.y = labelRect.y =
                valueRect.y + valueRect.height;
            trackRect.height = tickRect.height = labelRect.height =
                contentDim.height - valueRect.height;

            int startX = slider.getWidth() / 2 - contentDim.width / 2;
            if (SynthLookAndFeel.isLeftToRight(slider)) {
                if (l > w1) {
                    startX += (l - w1);
                }
                trackRect.x = startX + trackInsets.left;

                startX += trackInsets.left + trackRect.width + trackInsets.right;
                tickRect.x = startX;
                labelRect.x = startX + tickRect.width + 2;
            } else {
                if (l > w2) {
                    startX += (l - w2);
                }
                labelRect.x = startX;

                startX += labelRect.width + 2;
                tickRect.x = startX;
                trackRect.x = startX + tickRect.width + trackInsets.left;
            }
        }
        context.dispose();
        lastSize = slider.getSize();
    }

    /**
     * Calculates the pad for the label at the specified index.
     *
     * @param i index of the label to calculate pad for.
     * @return padding required to keep label visible.
     */
    private int getPadForLabel(int i) {
        int pad = 0;

        JComponent c = (JComponent) slider.getLabelTable().get(i);
        if (c != null) {
            int centerX = xPositionForValue(i);
            int cHalfWidth = c.getPreferredSize().width / 2;
            if (centerX - cHalfWidth < insetCache.left) {
                pad = Math.max(pad, insetCache.left - (centerX - cHalfWidth));
            }

            if (centerX + cHalfWidth > slider.getWidth() - insetCache.right) {
                pad = Math.max(pad, (centerX + cHalfWidth) -
                        (slider.getWidth() - insetCache.right));
            }
        }
        return pad;
    }

    protected void calculateThumbLocation() {
        super.calculateThumbLocation();
        if (slider.getOrientation() == JSlider.HORIZONTAL) {
            thumbRect.y += trackBorder;
        } else {
            thumbRect.x += trackBorder;
        }
        Point mousePosition = slider.getMousePosition();
        if(mousePosition != null) {
        updateThumbState(mousePosition.x, mousePosition.y);
       }
    }

    protected void calculateTickRect() {
        if (slider.getOrientation() == JSlider.HORIZONTAL) {
            tickRect.x = trackRect.x;
            tickRect.y = trackRect.y + trackRect.height + 2 + getTickLength();
            tickRect.width = trackRect.width;
            tickRect.height = getTickLength();

            if (!slider.getPaintTicks()) {
                --tickRect.y;
                tickRect.height = 0;
            }
        } else {
            if (SynthLookAndFeel.isLeftToRight(slider)) {
                tickRect.x = trackRect.x + trackRect.width;
                tickRect.width = getTickLength();
            } else {
                tickRect.width = getTickLength();
                tickRect.x = trackRect.x - tickRect.width;
            }
            tickRect.y = trackRect.y;
            tickRect.height = trackRect.height;

            if (!slider.getPaintTicks()) {
                --tickRect.x;
                tickRect.width = 0;
            }
        }
    }

    public void setThumbLocation(int x, int y) {
        super.setThumbLocation(x, y);
        // Value rect is tied to the thumb location.  We need to repaint when
        // the thumb repaints.
        slider.repaint(valueRect.x, valueRect.y,
                valueRect.width, valueRect.height);
        setThumbActive(false);
    }

    protected int xPositionForValue(int value) {
        int min = slider.getMinimum();
        int max = slider.getMaximum();
        int trackLeft = trackRect.x + thumbRect.width / 2 + trackBorder;
        int trackRight = trackRect.x + trackRect.width - thumbRect.width / 2
            - trackBorder;
        int trackLength = trackRight - trackLeft;
        double valueRange = (double)max - (double)min;
        double pixelsPerValue = (double)trackLength / valueRange;
        int xPosition;

        if (!drawInverted()) {
            xPosition = trackLeft;
            xPosition += Math.round( pixelsPerValue * ((double)value - min));
        } else {
            xPosition = trackRight;
            xPosition -= Math.round( pixelsPerValue * ((double)value - min));
        }

        xPosition = Math.max(trackLeft, xPosition);
        xPosition = Math.min(trackRight, xPosition);

        return xPosition;
    }

    protected int yPositionForValue(int value, int trackY, int trackHeight) {
        int min = slider.getMinimum();
        int max = slider.getMaximum();
        int trackTop = trackY + thumbRect.height / 2 + trackBorder;
        int trackBottom = trackY + trackHeight - thumbRect.height / 2 -
                trackBorder;
        int trackLength = trackBottom - trackTop;
        double valueRange = (double)max - (double)min;
        double pixelsPerValue = (double)trackLength / valueRange;
        int yPosition;

        if (!drawInverted()) {
            yPosition = trackTop;
            yPosition += Math.round(pixelsPerValue * ((double)max - value));
        } else {
            yPosition = trackTop;
            yPosition += Math.round(pixelsPerValue * ((double)value - min));
        }

        yPosition = Math.max(trackTop, yPosition);
        yPosition = Math.min(trackBottom, yPosition);

        return yPosition;
    }

    /**
     * Returns a value give a y position.  If yPos is past the track at the
     * top or the bottom it will set the value to the min or max of the
     * slider, depending if the slider is inverted or not.
     */
    public int valueForYPosition(int yPos) {
        int value;
        int minValue = slider.getMinimum();
        int maxValue = slider.getMaximum();
        int trackTop = trackRect.y + thumbRect.height / 2 + trackBorder;
        int trackBottom = trackRect.y + trackRect.height
            - thumbRect.height / 2 - trackBorder;
        int trackLength = trackBottom - trackTop;

        if (yPos <= trackTop) {
            value = drawInverted() ? minValue : maxValue;
        } else if (yPos >= trackBottom) {
            value = drawInverted() ? maxValue : minValue;
        } else {
            int distanceFromTrackTop = yPos - trackTop;
            double valueRange = (double)maxValue - (double)minValue;
            double valuePerPixel = valueRange / (double)trackLength;
            int valueFromTrackTop =
                (int)Math.round(distanceFromTrackTop * valuePerPixel);
            value = drawInverted() ?
                minValue + valueFromTrackTop : maxValue - valueFromTrackTop;
        }
        return value;
    }

    /**
     * Returns a value give an x position.  If xPos is past the track at the
     * left or the right it will set the value to the min or max of the
     * slider, depending if the slider is inverted or not.
     */
    public int valueForXPosition(int xPos) {
        int value;
        int minValue = slider.getMinimum();
        int maxValue = slider.getMaximum();
        int trackLeft = trackRect.x + thumbRect.width / 2 + trackBorder;
        int trackRight = trackRect.x + trackRect.width
            - thumbRect.width / 2 - trackBorder;
        int trackLength = trackRight - trackLeft;

        if (xPos <= trackLeft) {
            value = drawInverted() ? maxValue : minValue;
        } else if (xPos >= trackRight) {
            value = drawInverted() ? minValue : maxValue;
        } else {
            int distanceFromTrackLeft = xPos - trackLeft;
            double valueRange = (double)maxValue - (double)minValue;
            double valuePerPixel = valueRange / (double)trackLength;
            int valueFromTrackLeft =
                (int)Math.round(distanceFromTrackLeft * valuePerPixel);
            value = drawInverted() ?
                maxValue - valueFromTrackLeft : minValue + valueFromTrackLeft;
        }
        return value;
    }

    protected Dimension getThumbSize() {
        Dimension size = new Dimension();

        if (slider.getOrientation() == JSlider.VERTICAL) {
            size.width = thumbHeight;
            size.height = thumbWidth;
        } else {
            size.width = thumbWidth;
            size.height = thumbHeight;
        }
        return size;
    }

    protected void recalculateIfInsetsChanged() {
        SynthContext context = getContext(slider);
        Insets newInsets = style.getInsets(context, null);
        Insets compInsets = slider.getInsets();
        newInsets.left += compInsets.left; newInsets.right += compInsets.right;
        newInsets.top += compInsets.top; newInsets.bottom += compInsets.bottom;
        if (!newInsets.equals(insetCache)) {
            insetCache = newInsets;
            calculateGeometry();
        }
        context.dispose();
    }

    public Region getRegion(JComponent c) {
        return SynthLookAndFeel.getRegion(c);
    }

    public SynthContext getContext(JComponent c) {
        return getContext(c, getComponentState(c));
    }

    public SynthContext getContext(JComponent c, int state) {
        return SynthContext.getContext(SynthContext.class, c,
                            SynthLookAndFeel.getRegion(c), style, state);
    }

    public SynthContext getContext(JComponent c, Region subregion) {
        return getContext(c, subregion, getComponentState(c, subregion));
    }

    private SynthContext getContext(JComponent c, Region subregion, int state) {
        SynthStyle style = null;
        Class klass = SynthContext.class;

        if (subregion == Region.SLIDER_TRACK) {
            style = sliderTrackStyle;
        } else if (subregion == Region.SLIDER_THUMB) {
            style = sliderThumbStyle;
        }
        return SynthContext.getContext(klass, c, subregion, style, state);
    }

    public int getComponentState(JComponent c) {
        return SynthLookAndFeel.getComponentState(c);
    }

    private int getComponentState(JComponent c, Region region) {
        if (region == Region.SLIDER_THUMB && thumbActive &&c.isEnabled()) {
            int state = thumbPressed ? PRESSED : MOUSE_OVER;
            if (c.isFocusOwner()) state |= FOCUSED;
            return state;
        }
        return SynthLookAndFeel.getComponentState(c);
    }

    public void update(Graphics g, JComponent c) {
        SynthContext context = getContext(c);
        SynthLookAndFeel.update(context, g);
        context.getPainter().paintSliderBackground(context,
                          g, 0, 0, c.getWidth(), c.getHeight(),
                          slider.getOrientation());
        paint(context, g);
        context.dispose();
    }

    public void paint(Graphics g, JComponent c) {
        SynthContext context = getContext(c);
        paint(context, g);
        context.dispose();
    }

    public void paint(SynthContext context, Graphics g) {
        recalculateIfInsetsChanged();
        recalculateIfOrientationChanged();
        Rectangle clip = g.getClipBounds();

        if (lastSize == null || !lastSize.equals(slider.getSize())) {
            calculateGeometry();
        }

        if (paintValue) {
            FontMetrics fm = SwingUtilities2.getFontMetrics(slider, g);
            int labelWidth = context.getStyle().getGraphicsUtils(context).
                computeStringWidth(context, g.getFont(), fm,
                    "" + slider.getValue());
            valueRect.x = thumbRect.x + (thumbRect.width - labelWidth) / 2;

            // For horizontal sliders, make sure value is not painted
            // outside slider bounds.
            if (slider.getOrientation() == JSlider.HORIZONTAL) {
                if (valueRect.x + labelWidth > insetCache.left + contentDim.width) {
                    valueRect.x =  (insetCache.left + contentDim.width) - labelWidth;
                }
                valueRect.x = Math.max(valueRect.x, 0);
            }

            g.setColor(context.getStyle().getColor(
                    context, ColorType.TEXT_FOREGROUND));
            context.getStyle().getGraphicsUtils(context).paintText(
                    context, g, "" + slider.getValue(), valueRect.x,
                    valueRect.y, -1);
        }

        SynthContext subcontext = getContext(slider, Region.SLIDER_TRACK);
        paintTrack(subcontext, g, trackRect);
        subcontext.dispose();

        subcontext = getContext(slider, Region.SLIDER_THUMB);
        paintThumb(subcontext, g, thumbRect);
        subcontext.dispose();

        if (slider.getPaintTicks() && clip.intersects(tickRect)) {
            paintTicks(g);
        }

        if (slider.getPaintLabels() && clip.intersects(labelRect)) {
            paintLabels(g);
        }
    }

    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        context.getPainter().paintSliderBorder(context, g, x, y, w, h,
                                               slider.getOrientation());
    }

    public void paintThumb(SynthContext context, Graphics g,
            Rectangle thumbBounds)  {
        int orientation = slider.getOrientation();
        SynthLookAndFeel.updateSubregion(context, g, thumbBounds);
        context.getPainter().paintSliderThumbBackground(context, g,
                             thumbBounds.x, thumbBounds.y, thumbBounds.width,
                             thumbBounds.height, orientation);
        context.getPainter().paintSliderThumbBorder(context, g,
                             thumbBounds.x, thumbBounds.y, thumbBounds.width,
                             thumbBounds.height, orientation);
    }

    public void paintTrack(SynthContext context, Graphics g,
            Rectangle trackBounds) {
        int orientation = slider.getOrientation();
        SynthLookAndFeel.updateSubregion(context, g, trackBounds);
        context.getPainter().paintSliderTrackBackground(context, g,
                trackBounds.x, trackBounds.y, trackBounds.width,
                trackBounds.height, orientation);
        context.getPainter().paintSliderTrackBorder(context, g,
                trackBounds.x, trackBounds.y, trackBounds.width,
                trackBounds.height, orientation);
    }

    public void propertyChange(PropertyChangeEvent e) {
        if (SynthLookAndFeel.shouldUpdateStyle(e)) {
            updateStyle((JSlider)e.getSource());
        }
    }

    //////////////////////////////////////////////////
    /// Track Listener Class
    //////////////////////////////////////////////////
    /**
     * Track mouse movements.
     */
    protected class SynthTrackListener extends TrackListener {

        public void mouseExited(MouseEvent e) {
            setThumbActive(false);
        }

        public void mousePressed(MouseEvent e) {
            super.mousePressed(e);
            setThumbPressed(thumbRect.contains(e.getX(), e.getY()));
        }

        public void mouseReleased(MouseEvent e) {
            super.mouseReleased(e);
            updateThumbState(e.getX(), e.getY(), false);
        }

        public void mouseDragged(MouseEvent e) {
            int thumbMiddle;

            if (!slider.isEnabled()) {
                return;
            }

            currentMouseX = e.getX();
            currentMouseY = e.getY();

            if (!isDragging()) {
                return;
            }

            slider.setValueIsAdjusting(true);

            switch (slider.getOrientation()) {
            case JSlider.VERTICAL:
                int halfThumbHeight = thumbRect.height / 2;
                int thumbTop = e.getY() - offset;
                int trackTop = trackRect.y;
                int trackBottom = trackRect.y + trackRect.height
                    - halfThumbHeight - trackBorder;
                int vMax = yPositionForValue(slider.getMaximum() -
                    slider.getExtent());

                if (drawInverted()) {
                    trackBottom = vMax;
                    trackTop = trackTop + halfThumbHeight;
                } else {
                    trackTop = vMax;
                }
                thumbTop = Math.max(thumbTop, trackTop - halfThumbHeight);
                thumbTop = Math.min(thumbTop, trackBottom - halfThumbHeight);

                setThumbLocation(thumbRect.x, thumbTop);

                thumbMiddle = thumbTop + halfThumbHeight;
                slider.setValue(valueForYPosition(thumbMiddle));
                break;
            case JSlider.HORIZONTAL:
                int halfThumbWidth = thumbRect.width / 2;
                int thumbLeft = e.getX() - offset;
                int trackLeft = trackRect.x + halfThumbWidth + trackBorder;
                int trackRight = trackRect.x + trackRect.width
                    - halfThumbWidth - trackBorder;
                int hMax = xPositionForValue(slider.getMaximum() -
                    slider.getExtent());

                if (drawInverted()) {
                    trackLeft = hMax;
                } else {
                    trackRight = hMax;
                }
                thumbLeft = Math.max(thumbLeft, trackLeft - halfThumbWidth);
                thumbLeft = Math.min(thumbLeft, trackRight - halfThumbWidth);

                setThumbLocation(thumbLeft, thumbRect.y);

                thumbMiddle = thumbLeft + halfThumbWidth;
                slider.setValue(valueForXPosition(thumbMiddle));
                break;
            default:
                return;
            }

            if (slider.getValueIsAdjusting()) {
                setThumbActive(true);
            }
        }

        public void mouseMoved(MouseEvent e) {
            updateThumbState(e.getX(), e.getY());
        }
    }
}
