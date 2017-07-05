/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package org.jdesktop.synthdesigner.synthmodel;

import org.jdesktop.beans.AbstractBean;
import org.jdesktop.swingx.designer.font.Typeface;
import org.jdesktop.swingx.designer.paint.Matte;
import org.jibx.runtime.IUnmarshallingContext;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.ArrayList;

/**
 * UIStyle
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
public class UIStyle extends AbstractBean {
    public static enum CacheMode {NO_CACHING,FIXED_SIZES,NINE_SQUARE_SCALE}
    public static enum HintAlphaInterpolation {
        DEFAULT, QUALITY, SPEED
    }

    public static enum HintAntialiasing {
        DEFAULT, ON, OFF
    }

    public static enum HintColorRendering {
        DEFAULT, QUALITY, SPEED
    }

    public static enum HintDithering {
        DEFAULT, DISABLE, ENABLE
    }

    public static enum HintFractionalMetrics {
        DEFAULT, ON, OFF
    }

    public static enum HintInterpolation {
        NEAREST_NEIGHBOR, BILINEAR, BICUBIC
    }

    public static enum HintRendering {
        DEFAULT, QUALITY, SPEED
    }

    public static enum HintStrokeControl {
        DEFAULT, NORMALIZE, PURE
    }

    public static enum HintTextAntialiasing {
        DEFAULT, ON, OFF, GASP, LCD_HBGR, LCD_HRGB, LCD_VBGR, LCD_VRGB
    }

    private Typeface font = null;
    private boolean fontInherited = true;
    private Matte textForeground = null;
    private boolean textForegroundInherited = true;
    private Matte textBackground = null;
    private boolean textBackgroundInherited = true;
    private Matte background = null;
    private boolean backgroundInherited = true;

    private boolean cacheSettingsInherited = true;
    private CacheMode cacheMode = CacheMode.FIXED_SIZES;
    private double maxHozCachedImgScaling = 1;
    private double maxVertCachedImgScaling = 1;

    private HintAlphaInterpolation hintAlphaInterpolation = null;
    private HintAntialiasing hintAntialiasing = null;
    private HintColorRendering hintColorRendering = null;
    private HintDithering hintDithering = null;
    private HintFractionalMetrics hintFractionalMetrics = null;
    private HintInterpolation hintInterpolation = null;
    private HintRendering hintRendering = null;
    private HintStrokeControl hintStrokeControl = null;
    private HintTextAntialiasing hintTextAntialiasing = null;
    private List<UIProperty> uiProperties;
    private UIStyle parentStyle = null;

    private PropertyChangeListener textForegoundListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            firePropertyChange("textForeground." + evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
        }
    };
    private PropertyChangeListener textBackgroundListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            firePropertyChange("textBackground." + evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
        }
    };
    private PropertyChangeListener backgroundListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            firePropertyChange("background." + evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
        }
    };

    // =================================================================================================================
    // Constructors

    public UIStyle() {
        uiProperties = new ArrayList<UIProperty>();
    }

    // =================================================================================================================
    // JIBX Methods

    /**
     * Called by JIBX after all fields have been set
     *
     * @param context The JIBX Unmarshalling Context
     */
    private void postSet(IUnmarshallingContext context) {
        // walk up till we get a parent style
        for (int i = 0; i < context.getStackDepth(); i++) {
            if (context.getStackObject(i) instanceof HasUIStyle) {
                HasUIStyle hasStyle = (HasUIStyle) context.getStackObject(i);
                if (hasStyle.getStyle() != this) {
                    parentStyle = hasStyle.getStyle();
                    if (parentStyle != null) break;
                }
            }
        }
    }

    // =================================================================================================================
    // Bean Methods

    public UIStyle getParentStyle() {
        return parentStyle;
    }

    public void setParentStyle(UIStyle parentStyle) {
        UIStyle old = getParentStyle();
        this.parentStyle = parentStyle;
        firePropertyChange("parentStyle", old, getParentStyle());
    }

    public List<UIProperty> getUiProperties() {
        return uiProperties;
    }

    public void addUiProperty(UIProperty uiProperty) {
        uiProperties.add(uiProperty);
        // todo not quite sure what events we want here
        fireIndexedPropertyChange("uiProperties", uiProperties.size(), null, uiProperty);
//        firePropertyChange("uiProperties", null, uiProperties);
    }

    public void removeUiProperty(UIProperty uiProperty) {
        int index = uiProperties.indexOf(uiProperty);
        if (index != -1) {
            uiProperties.remove(uiProperty);
            // todo not quite sure what events we want here
            fireIndexedPropertyChange("uiProperties", index, null, uiProperty);
//            firePropertyChange("uiProperties", null, uiProperties);
        }
    }

    public Typeface getFont() {
        if (isFontInherited()) {
            return parentStyle == null ? font : parentStyle.getFont();
        } else {
            return font;
        }
    }

    public void setFont(Typeface font) {
        Typeface old = getFont();
        this.font = font;
        firePropertyChange("font", old, font);
    }

    public boolean isFontInherited() {
        return fontInherited;
    }

    public void setFontInherited(boolean b) {
        boolean old = isFontInherited();
        fontInherited = b;
        firePropertyChange("fontInherited", old, b);

        if (!fontInherited && font == null && parentStyle != null && parentStyle.getFont() != null) {
            font = parentStyle.getFont().clone();
            firePropertyChange("font", null, font);
        }
    }

    public Matte getTextForeground() {
        if (isTextForegroundInherited()) {
            return parentStyle == null ? null : parentStyle.getTextForeground();
        } else {
            return textForeground;
        }
    }

    public boolean isTextForegroundInherited() {
        return textForegroundInherited;
    }

    public void setTextForegroundInherited(boolean b) {
        boolean old = isTextForegroundInherited();
        textForegroundInherited = b;
        firePropertyChange("foregroundInherited", old, b);

        if (!textForegroundInherited && textForeground == null && parentStyle != null &&
                parentStyle.getTextForeground() != null) {
            textForeground = parentStyle.getTextForeground().clone();
            firePropertyChange("textForeground", null, textForeground);
        }
    }

    public void setTextForeground(Matte textForeground) {
        Matte old = this.textForeground;
        if (old != null) old.removePropertyChangeListener(textForegoundListener);
        this.textForeground = textForeground;
        if (this.textForeground != null) this.textForeground.addPropertyChangeListener(textForegoundListener);
        firePropertyChange("textForeground", old, this.textForeground);
    }

    public Matte getTextBackground() {
        if (isTextBackgroundInherited()) {
            return parentStyle == null ? null : parentStyle.getBackground();
        } else {
            return textBackground;
        }
    }

    public boolean isTextBackgroundInherited() {
        return textBackgroundInherited;
    }

    public void setTextBackgroundInherited(boolean b) {
        boolean old = isBackgroundInherited();
        textBackgroundInherited = b;
        firePropertyChange("textBackgroundInherited", old, b);

        if (!textBackgroundInherited && textBackground == null && parentStyle != null &&
                parentStyle.getTextBackground() != null) {
            textBackground = parentStyle.getTextBackground().clone();
            firePropertyChange("textBackground", null, textBackground);
        }
    }

    public void setTextBackground(Matte textBackground) {
        Matte old = this.textBackground;
        if (old != null) old.removePropertyChangeListener(textBackgroundListener);
        this.textBackground = textBackground;
        if (this.textBackground != null) this.textBackground.addPropertyChangeListener(textBackgroundListener);
        firePropertyChange("textBackground", old, this.textBackground);
    }

    public Matte getBackground() {
        if (isBackgroundInherited()) {
            return parentStyle == null ? null : parentStyle.getBackground();
        } else {
            return background;
        }
    }

    public boolean isBackgroundInherited() {
        return backgroundInherited;
    }

    public void setBackgroundInherited(boolean b) {
        boolean old = isBackgroundInherited();
        backgroundInherited = b;
        firePropertyChange("backgroundInherited", old, b);

        if (!backgroundInherited && background == null && parentStyle != null && parentStyle.getBackground() != null) {
            background = parentStyle.getBackground().clone();
            firePropertyChange("background", null, background);
        }
    }

    public void setBackground(Matte background) {
        Matte old = this.background;
        if (old != null) old.removePropertyChangeListener(backgroundListener);
        this.background = background;
        if (this.background != null) this.background.addPropertyChangeListener(backgroundListener);
        firePropertyChange("background", old, this.background);
    }

    public HintAlphaInterpolation getHintAlphaInterpolation() {
        return hintAlphaInterpolation;
    }

    public void setHintAlphaInterpolation(HintAlphaInterpolation hintAlphaInterpolation) {
        HintAlphaInterpolation old = getHintAlphaInterpolation();
        this.hintAlphaInterpolation = hintAlphaInterpolation;
        firePropertyChange("hintAlphaInterpolation", old, getHintAlphaInterpolation());
    }

    public HintAntialiasing getHintAntialiasing() {
        return hintAntialiasing;
    }

    public void setHintAntialiasing(HintAntialiasing hintAntialiasing) {
        HintAntialiasing old = getHintAntialiasing();
        this.hintAntialiasing = hintAntialiasing;
        firePropertyChange("hintAntialiasing", old, getHintAntialiasing());
    }

    public HintColorRendering getHintColorRendering() {
        return hintColorRendering;
    }

    public void setHintColorRendering(HintColorRendering hintColorRendering) {
        HintColorRendering old = getHintColorRendering();
        this.hintColorRendering = hintColorRendering;
        firePropertyChange("hintColorRendering", old, getHintColorRendering());
    }

    public HintDithering getHintDithering() {
        return hintDithering;
    }

    public void setHintDithering(HintDithering hintDithering) {
        HintDithering old = getHintDithering();
        this.hintDithering = hintDithering;
        firePropertyChange("hintDithering", old, getHintDithering());
    }

    public HintFractionalMetrics getHintFractionalMetrics() {
        return hintFractionalMetrics;
    }

    public void setHintFractionalMetrics(HintFractionalMetrics hintFractionalMetrics) {
        HintFractionalMetrics old = getHintFractionalMetrics();
        this.hintFractionalMetrics = hintFractionalMetrics;
        firePropertyChange("hintFractionalMetrics", old, getHintFractionalMetrics());
    }

    public HintInterpolation getHintInterpolation() {
        return hintInterpolation;
    }

    public void setHintInterpolation(HintInterpolation hintInterpolation) {
        HintInterpolation old = getHintInterpolation();
        this.hintInterpolation = hintInterpolation;
        firePropertyChange("hintInterpolation", old, getHintInterpolation());
    }

    public HintRendering getHintRendering() {
        return hintRendering;
    }

    public void setHintRendering(HintRendering hintRendering) {
        HintRendering old = getHintRendering();
        this.hintRendering = hintRendering;
        firePropertyChange("hintRendering", old, getHintRendering());
    }

    public HintStrokeControl getHintStrokeControl() {
        return hintStrokeControl;
    }

    public void setHintStrokeControl(HintStrokeControl hintStrokeControl) {
        HintStrokeControl old = getHintStrokeControl();
        this.hintStrokeControl = hintStrokeControl;
        firePropertyChange("hintStrokeControl", old, getHintStrokeControl());
    }

    public HintTextAntialiasing getHintTextAntialiasing() {
        return hintTextAntialiasing;
    }

    public void setHintTextAntialiasing(HintTextAntialiasing hintTextAntialiasing) {
        HintTextAntialiasing old = getHintTextAntialiasing();
        this.hintTextAntialiasing = hintTextAntialiasing;
        firePropertyChange("hintTextAntialiasing", old, getHintTextAntialiasing());
    }

    public boolean isCacheSettingsInherited() {
        return cacheSettingsInherited;
    }

    public void setCacheSettingsInherited(boolean cacheSettingsInherited) {
        boolean old = isCacheSettingsInherited();
        this.cacheSettingsInherited = cacheSettingsInherited;
        firePropertyChange("cacheSettingsInherited", old, isCacheSettingsInherited());
    }

    public CacheMode getCacheMode() {
        if (isCacheSettingsInherited()) {
            return (parentStyle == null)?CacheMode.FIXED_SIZES : parentStyle.getCacheMode();
        } else {
            return cacheMode;
        }
    }

    public void setCacheMode(CacheMode cacheMode) {
        CacheMode old = this.cacheMode;
        this.cacheMode = cacheMode;
        if (isCacheSettingsInherited()) {
            setCacheSettingsInherited(false);
            UIStyle parent = getParentStyle();
            setMaxHozCachedImgScaling(parent == null ? 1 : parent.getMaxHozCachedImgScaling());
            setMaxVertCachedImgScaling(parent == null ? 1 : parent.getMaxVertCachedImgScaling());
        }
        firePropertyChange("cacheMode",old,cacheMode);
    }

    public double getMaxHozCachedImgScaling() {
        if (isCacheSettingsInherited()) {
            return parentStyle == null ? 1 : parentStyle.getMaxHozCachedImgScaling();
        } else {
            return maxHozCachedImgScaling;
        }
    }

    public void setMaxHozCachedImgScaling(double maxHozCachedImgScaling) {
        double old = getMaxHozCachedImgScaling();
        this.maxHozCachedImgScaling = maxHozCachedImgScaling;
        if (isCacheSettingsInherited()) {
            setCacheSettingsInherited(false);
            setCacheMode((parentStyle == null)?CacheMode.FIXED_SIZES : parentStyle.getCacheMode());
            setMaxVertCachedImgScaling(parentStyle == null ? 1 : parentStyle.getMaxVertCachedImgScaling());
        }
        firePropertyChange("maxHozCachedImgScaling", old, getMaxHozCachedImgScaling());
    }

    public double getMaxVertCachedImgScaling() {
        if (isCacheSettingsInherited()) {
            return parentStyle == null ? 1 : parentStyle.getMaxVertCachedImgScaling();
        } else {
            return maxVertCachedImgScaling;
        }
    }

    public void setMaxVertCachedImgScaling(double maxVertCachedImgScaling) {
        double old = getMaxVertCachedImgScaling();
        this.maxVertCachedImgScaling = maxVertCachedImgScaling;
        if (isCacheSettingsInherited()) {
            setCacheSettingsInherited(false);
            setCacheMode((parentStyle == null)?CacheMode.FIXED_SIZES : parentStyle.getCacheMode());
            setMaxHozCachedImgScaling(parentStyle == null ? 1 : parentStyle.getMaxHozCachedImgScaling());
        }
        firePropertyChange("maxVertCachedImgScaling", old, getMaxVertCachedImgScaling());
    }
}

