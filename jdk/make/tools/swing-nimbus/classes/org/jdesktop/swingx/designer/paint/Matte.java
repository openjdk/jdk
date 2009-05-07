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

import org.jdesktop.swingx.designer.utils.HasUIDefaults;
import org.jibx.runtime.IUnmarshallingContext;

import javax.swing.UIDefaults;
import java.awt.Color;
import java.awt.Paint;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Representing a single uniform color. Basically, it represents the java.awt.Color. It can either be absolute or
 * derived from a UIDefault color.
 *
 * @author rbair & jasper potts
 */
public class Matte extends PaintModel implements HasUIDefaults {
    private float[] tmpf1 = new float[3];
    private float[] tmpf2 = new float[3];

    private int red;
    private int green;
    private int blue;
    private int alpha;
    private Color cached = null;

    /**
     * The name of the ui default key to derive this color from.
     */
    private String uiDefaultParentName = null;
    /**
     * The name of the bean property, or client property, on this component
     * from which to extract a color used for painting. So for example the color
     * used in a painter could be the background of the component.
     */
    private String componentPropertyName = null;
    private float hueOffset = 0, saturationOffset = 0, brightnessOffset = 0;
    private int alphaOffset = 0;
    /**
     * When true this color will become a UIResource in the UIManager defaults
     * table. If false, then it will not be a UIResource. This is sometimes
     * required, such as with colors installed on renderers.
     */
    private boolean uiResource = true;

    /** This is a local UIDefaults that contains all the UIDefaults in the Model. */
    private transient UIDefaults uiDefaults = new UIDefaults();
    private PropertyChangeListener uiDefaultsChangeListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            if (uiDefaultParentName != null && uiDefaultParentName.equals(evt.getPropertyName())) {
                updateARGBFromOffsets();
            }
        }
    };

    // =================================================================================================================
    // Constructors

    /** propected constructor for JIBX */
    protected Matte() {}

    public Matte(Color c, UIDefaults uiDefaults) {
        if (c != null) {
            this.red = c.getRed();
            this.green = c.getGreen();
            this.blue = c.getBlue();
            this.alpha = c.getAlpha();
        }
        setUiDefaults(uiDefaults);
    }

    // =================================================================================================================
    // JIBX Methods

    /**
     * Called by JIBX after all fields have been set
     *
     * @param context The JIBX Unmarshalling Context
     */
    protected void postSet(IUnmarshallingContext context) {
        // walk up till we get synth model
        for (int i = 0; i < context.getStackDepth(); i++) {
            if (context.getStackObject(i) instanceof HasUIDefaults) {
                UIDefaults uiDefaults = ((HasUIDefaults) context.getStackObject(i)).getUiDefaults();
                if (uiDefaults != null) {
                    setUiDefaults(uiDefaults);
                    break;
                }
            }
        }
    }

    // =================================================================================================================
    // Matte methods

    /**
     * Is the matte an absolute color ot derived from a parent ui default
     *
     * @return <code>true</code> if this is a absolute not uidefault derived color
     */
    public boolean isAbsolute() {
        return uiDefaultParentName == null;
    }

    /**
     * Set all properties of this matte to be the same as <code>srcMatte</code> and fire all the change events
     *
     * @param srcMatte the matte to copy properties from
     */
    public void copy(Matte srcMatte) {
        // keep old values
        Color oldColor = getColor();
        String oldParentName = uiDefaultParentName;
        String oldComponentPropertyName = componentPropertyName;
        boolean oldUiResource = uiResource;
        int oldR = red, oldG = green, oldB = blue, oldA = alpha;
        float oldH = hueOffset, oldS = saturationOffset, oldBr = brightnessOffset;
        // set properties
        if (uiResource != srcMatte.uiResource) {
            uiResource = srcMatte.uiResource;
            firePropertyChange("uiResource", oldUiResource, isUiResource());
        }
        if (red != srcMatte.red) {
            red = srcMatte.red;
            firePropertyChange("red", oldR, getRed());
        }
        if (green != srcMatte.green) {
            green = srcMatte.green;
            firePropertyChange("green", oldG, getGreen());
        }
        if (blue != srcMatte.blue) {
            blue = srcMatte.blue;
            firePropertyChange("blue", oldB, getBlue());
        }
        if (alpha != srcMatte.alpha) {
            alpha = srcMatte.alpha;
            firePropertyChange("alpha", oldA, getAlpha());
        }
        if (hueOffset != srcMatte.hueOffset) {
            hueOffset = srcMatte.hueOffset;
            firePropertyChange("hueOffset", oldH, getHueOffset());
        }
        if (saturationOffset != srcMatte.saturationOffset) {
            saturationOffset = srcMatte.saturationOffset;
            firePropertyChange("saturationOffset", oldS, getSaturationOffset());
        }
        if (brightnessOffset != srcMatte.brightnessOffset) {
            brightnessOffset = srcMatte.brightnessOffset;
            firePropertyChange("brightnessOffset", oldBr, getBrightnessOffset());
        }
        if (alphaOffset != srcMatte.alphaOffset) {
            alphaOffset = srcMatte.alphaOffset;
            firePropertyChange("alphaOffset", oldA, getAlphaOffset());
        }
        if (uiDefaultParentName != srcMatte.uiDefaultParentName) {
            uiDefaultParentName = srcMatte.uiDefaultParentName;
            firePropertyChange("uiDefaultParentName", oldParentName, getUiDefaultParentName());
        }
        if (componentPropertyName != srcMatte.componentPropertyName) {
            componentPropertyName = srcMatte.componentPropertyName;
            firePropertyChange("componentPropertyName", oldComponentPropertyName, getComponentPropertyName());
        }
        if (uiDefaults != srcMatte.uiDefaults) {
            setUiDefaults(srcMatte.uiDefaults);
        }
        if (!oldColor.equals(srcMatte.getColor())) {
            firePropertyChange("paint", oldColor, getColor());
            firePropertyChange("color", oldColor, getColor());
            fireHSBChange(oldR, oldG, oldB);
        }
    }

    // =================================================================================================================
    // PaintModel methods

    public PaintControlType getPaintControlType() {
        return PaintControlType.none;
    }

    // =================================================================================================================
    // Bean Methods

    /**
     * Get the local UIDefaults that contains all the UIDefaults in the Model.
     *
     * @return The UIDefaults for the model that contains this Matte, can be null if this Matte is not part of a bigger
     *         model
     */
    public UIDefaults getUiDefaults() {
        return uiDefaults;
    }

    /**
     * Set the local UIDefaults that contains all the UIDefaults in the Model.
     *
     * @param uiDefaults The UIDefaults for the model that contains this Matte, can be null if this Matte is not part of
     *                   a bigger model
     */
    public void setUiDefaults(UIDefaults uiDefaults) {
        if (uiDefaults != this.uiDefaults) {
            UIDefaults old = getUiDefaults();
            if (old != null) old.removePropertyChangeListener(uiDefaultsChangeListener);
            this.uiDefaults = uiDefaults;
            if (uiDefaults != null) this.uiDefaults.addPropertyChangeListener(uiDefaultsChangeListener);
            firePropertyChange("uiDefaults", old, getUiDefaults());
        }
    }

    /**
     * Get the name if the uidefault color that is the parent that this matte is derived from. If null then this is a
     * absolute color.
     *
     * @return Parent color ui default name
     */
    public String getUiDefaultParentName() {
        return uiDefaultParentName;
    }

    /**
     * Set the name if the uidefault color that is the parent that this matte is derived from. If null then this is a
     * absolute color.
     *
     * @param uiDefaultParentName Parent color ui default name
     */
    public void setUiDefaultParentName(String uiDefaultParentName) {
        String old = getUiDefaultParentName();
        this.uiDefaultParentName = uiDefaultParentName;
        firePropertyChange("uiDefaultParentName", old, getUiDefaultParentName());
        if (isAbsolute()) {
            // reset offsets
            float oldH = hueOffset, oldS = saturationOffset, oldB = brightnessOffset;
            int oldA = alphaOffset;
            hueOffset = 0;
            saturationOffset = 0;
            brightnessOffset = 0;
            alphaOffset = 0;
            firePropertyChange("hueOffset", oldH, getHueOffset());
            firePropertyChange("saturationOffset", oldS, getSaturationOffset());
            firePropertyChange("brightnessOffset", oldB, getBrightnessOffset());
            firePropertyChange("alphaOffset", oldA, getAlphaOffset());
        }
        updateARGBFromOffsets();
    }

    /**
     * Sets the property to use for extracting the color for whatever component
     * is passed to the painter. Can be a key in client properties. Can be null.
     * @param name
     */
    public void setComponentPropertyName(String name) {
        String old = componentPropertyName;
        firePropertyChange("componentPropertyName", old, componentPropertyName = name);
    }

    /**
     * Gets the name of the bean property, or client property, on this component
     * from which to extract a color used for painting. So for example the color
     * used in a painter could be the background of the component.
     *
     * @return
     */
    public String getComponentPropertyName() {
        return componentPropertyName;
    }

    /**
     * Sets whether this color should be represented as a UIResource in UIDefaults
     * @param b true if the color should be a ui resource
     */
    public void setUiResource(boolean b) {
        boolean old = uiResource;
        firePropertyChange("uiResource", old, uiResource = b);
    }

    /**
     * When false this color will become a non-UIResource in the UIManager defaults
     * table. This is sometimes required to force swing to use the given color,
     * such as with renderers.
     * @return false if the color should not be a uiresource
     */
    public boolean isUiResource() {
        return uiResource;
    }

    public float getHueOffset() {
        return hueOffset;
    }

    public void setHueOffset(float hueOffset) {
        float old = getHueOffset();
        this.hueOffset = hueOffset;
        firePropertyChange("hueOffset", old, getHueOffset());
        updateARGBFromOffsets();
    }

    public float getSaturationOffset() {
        return saturationOffset;
    }

    public void setSaturationOffset(float satOffset) {
        float old = getSaturationOffset();
        this.saturationOffset = satOffset;
        firePropertyChange("saturationOffset", old, getSaturationOffset());
        updateARGBFromOffsets();
    }

    public float getBrightnessOffset() {
        return brightnessOffset;
    }

    public void setBrightnessOffset(float brightOffset) {
        float old = getBrightnessOffset();
        this.brightnessOffset = brightOffset;
        firePropertyChange("brightnessOffset", old, getBrightnessOffset());
        updateARGBFromOffsets();
    }

    public int getAlphaOffset() {
        return alphaOffset;
    }

    public void setAlphaOffset(int alphaOffset) {
        int old = getAlphaOffset();
        this.alphaOffset = alphaOffset;
        firePropertyChange("alphaOffset", old, alphaOffset);
        updateARGBFromOffsets();
    }


    public void setRed(int red) {
        red = clamp(red);
        if (this.red != red) {
            Color old = getColor();
            int oldr = this.red;
            this.red = red;
            firePropertyChange("paint", old, getColor());
            firePropertyChange("color", old, getColor());
            firePropertyChange("red", oldr, red);
            fireHSBChange(oldr, green, blue);
            updateOffsetsFromARGB();
        }
    }

    public final int getRed() {
        return red;
    }

    public void setGreen(int green) {
        green = clamp(green);
        if (this.green != green) {
            Color old = getColor();
            int oldg = this.green;
            this.green = green;
            firePropertyChange("paint", old, getColor());
            firePropertyChange("color", old, getColor());
            firePropertyChange("green", oldg, green);
            fireHSBChange(red, oldg, blue);
            updateOffsetsFromARGB();
        }
    }

    public final int getGreen() {
        return green;
    }

    public void setBlue(int blue) {
        blue = clamp(blue);
        if (this.blue != blue) {
            Color old = getColor();
            int oldb = this.blue;
            this.blue = blue;
            firePropertyChange("paint", old, getColor());
            firePropertyChange("color", old, getColor());
            firePropertyChange("blue", oldb, blue);
            fireHSBChange(red, green, oldb);
            updateOffsetsFromARGB();
        }
    }

    public final int getBlue() {
        return blue;
    }

    public void setAlpha(int alpha) {
        alpha = clamp(alpha);
        if (this.alpha != alpha) {
            int old = getAlpha();
            this.alpha = alpha;
            firePropertyChange("alpha", old, alpha);
            firePropertyChange("paint", old, getColor());
            firePropertyChange("color", old, getColor());
            updateOffsetsFromARGB();
        }
    }

    public final int getAlpha() {
        return alpha;
    }

    public Color getColor() {
        if (cached == null || red != cached.getRed() || green != cached.getGreen() ||
                blue != cached.getBlue() || alpha != cached.getAlpha()) {
            cached = new Color(red, green, blue, alpha);
        }
        return cached;
    }

    public void setColor(Color c) {
        setColor(c, false);
    }

    public void setColor(Color c, boolean dontSetAlpha) {
        Color oldColor = getColor();
        int oldR = red, oldG = green, oldB = blue, oldA = alpha;
        cached = c;
        red = c.getRed();
        green = c.getGreen();
        blue = c.getBlue();
        if (!dontSetAlpha) alpha = c.getAlpha();
        updateOffsetsFromARGB();
        firePropertyChange("red", oldR, getRed());
        firePropertyChange("green", oldG, getGreen());
        firePropertyChange("blue", oldB, getBlue());
        fireHSBChange(oldR, oldG, oldB);
        if (!dontSetAlpha) firePropertyChange("alpha", oldA, getAlpha());
        firePropertyChange("paint", oldColor, getColor());
        firePropertyChange("color", oldColor, getColor());
    }

    @Override public Paint getPaint() {
        return getColor();
    }


    @Override public String toString() {
        if (isAbsolute()) {
            return Matte.class.getName() + "[r=" + red + ", g=" + green + ", b=" + blue + ", a=" + alpha + "]";
        } else {
            return Matte.class.getName() + "[base=" + uiDefaultParentName + ", H+" + hueOffset +
                    ", S+" + saturationOffset + ", B+" + brightnessOffset + ", A+" + alphaOffset + "]";
        }
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Matte matte = (Matte) o;
        if (alpha != matte.alpha) return false;
        if (alphaOffset != matte.alphaOffset) return false;
        if (Float.compare(matte.alpha, alpha) != 0) return false;
        if (blue != matte.blue) return false;
        if (Float.compare(matte.brightnessOffset, brightnessOffset) != 0)
            return false;
        if (green != matte.green) return false;
        if (Float.compare(matte.hueOffset, hueOffset) != 0) return false;
        if (red != matte.red) return false;
        if (uiResource != matte.uiResource) return false;
        if (Float.compare(matte.saturationOffset, saturationOffset) != 0)
            return false;
        if (componentPropertyName != null ?
                !componentPropertyName.equals(componentPropertyName) :
                matte.componentPropertyName != null) return false;

        if (uiDefaultParentName != null ?
                !uiDefaultParentName.equals(matte.uiDefaultParentName) :
                matte.uiDefaultParentName != null) return false;
        return true;
    }

    public int hashCode() {
        int result;
        result = red;
        result = 31 * result + green;
        result = 31 * result + blue;
        result = 31 * result + alpha;
        result = 31 * result + (uiDefaultParentName != null ?
            uiDefaultParentName.hashCode() : 0);
        result = 31 * result + (componentPropertyName != null ?
            componentPropertyName.hashCode() : 0);
        result = 31 * result + hueOffset != +0.0f ?
            Float.floatToIntBits(hueOffset) : 0;
        result = 31 * result + saturationOffset != +0.0f ?
            Float.floatToIntBits(saturationOffset) : 0;
        result = 31 * result + brightnessOffset != +0.0f ?
            Float.floatToIntBits(brightnessOffset) : 0;
        result = 31 * result + (uiResource ? 1 : 0);
        return result;
    }

    @Override public Matte clone() {
        Matte m = new Matte();
        m.red = red;
        m.green = green;
        m.blue = blue;
        m.alpha = alpha;
        m.brightnessOffset = brightnessOffset;
        m.hueOffset = hueOffset;
        m.saturationOffset = saturationOffset;
        m.alphaOffset = alphaOffset;
        m.uiDefaultParentName = uiDefaultParentName;
        m.componentPropertyName = componentPropertyName;
        m.uiResource = uiResource;
        m.setUiDefaults(uiDefaults);
        return m;
    }

    // =================================================================================================================
    // Private Helper Methods

    private void updateOffsetsFromARGB() {
        if (!isAbsolute()) {
            tmpf1 = Color.RGBtoHSB(red, green, blue, tmpf1);
            Color parentColor = uiDefaults.getColor(uiDefaultParentName);
            tmpf2 = Color.RGBtoHSB(parentColor.getRed(), parentColor.getGreen(), parentColor.getBlue(), tmpf2);
            // update offset properties and fire events
            float oldH = hueOffset, oldS = saturationOffset, oldB = brightnessOffset;
            int oldA = alphaOffset;
            hueOffset = tmpf1[0] - tmpf2[0];
            saturationOffset = tmpf1[1] - tmpf2[1];
            brightnessOffset = tmpf1[2] - tmpf2[2];
            alphaOffset = alpha - parentColor.getAlpha();
            firePropertyChange("hueOffset", oldH, getHueOffset());
            firePropertyChange("saturationOffset", oldS, getSaturationOffset());
            firePropertyChange("brightnessOffset", oldB, getBrightnessOffset());
            firePropertyChange("alphaOffset", oldA, getAlphaOffset());
        }
    }

    private void updateARGBFromOffsets() {
        if (!isAbsolute()) {
            Color oldColor = getColor();
            // get parent color HSB
            Color parentColor = uiDefaults.getColor(uiDefaultParentName);
            tmpf1 = Color.RGBtoHSB(parentColor.getRed(), parentColor.getGreen(), parentColor.getBlue(), tmpf1);
            // apply offsets
            tmpf1[0] = clamp(tmpf1[0] + hueOffset);
            tmpf1[1] = clamp(tmpf1[1] + saturationOffset);
            tmpf1[2] = clamp(tmpf1[2] + brightnessOffset);
            int oldA = getAlpha();
            alpha = clamp(parentColor.getAlpha() + alphaOffset);
            updateRGB(tmpf1);
            // update fire events
            firePropertyChange("alpha", oldA, getAlpha());
            firePropertyChange("paint", oldColor, getColor());
            firePropertyChange("color", oldColor, getColor());
        }
    }

    private void updateRGB(float[] hsb) {
        int oldR = red, oldG = green, oldB = blue;
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
        red = (rgb >> 16) & 0xFF;
        green = (rgb >> 8) & 0xFF;
        blue = rgb & 0xFF;
        firePropertyChange("red", oldR, getRed());
        firePropertyChange("green", oldG, getGreen());
        firePropertyChange("blue", oldB, getBlue());
    }

    private void fireHSBChange(int oldR, int oldG, int oldB) {
        tmpf1 = Color.RGBtoHSB(oldR, oldG, oldB, tmpf1);
        tmpf2 = Color.RGBtoHSB(red, green, blue, tmpf2);
        firePropertyChange("hue", tmpf1[0], tmpf2[0]);
        firePropertyChange("saturation", tmpf1[1], tmpf2[1]);
        firePropertyChange("brightness", tmpf1[2], tmpf2[2]);
    }

    private float clamp(float value) {
        if (value < 0) {
            value = 0;
        } else if (value > 1) {
            value = 1;
        }
        return value;
    }

    private int clamp(int value) {
        if (value < 0) {
            value = 0;
        } else if (value > 255) {
            value = 255;
        }
        return value;
    }
}
