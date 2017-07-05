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
import org.jdesktop.swingx.designer.Canvas;
import org.jdesktop.swingx.designer.utils.HasPath;
import org.jdesktop.swingx.designer.utils.HasUIDefaults;
import org.jibx.runtime.IUnmarshallingContext;

import javax.swing.UIDefaults;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a State in the Synth LAF.
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
public class UIState extends AbstractBean implements HasUIStyle, HasPath {

    private List<String> stateKeys;
    private boolean inverted; //indicates whether to invert the meaning of the 9-square stretching insets
    /** A cached string representing the list of stateKeys deliminated with "+" */
    private String cachedName = null;
    private Canvas canvas;
    private UIStyle style;
    /** the region that this state belongs to */
    private UIRegion region;
    /**
     * This is a local UIDefaults that contains all the UIDefaults in the synth model. It is kept uptodate by the
     * indervidual UIDefaults nodes
     */
    private transient UIDefaults modelDefaults = null;

    // =================================================================================================================
    // Contructors

    public UIState() {
        // Create state keys as event list so model changes are propogated
        stateKeys = new ArrayList<String>();
    }

    public UIState(SynthModel model, UIRegion parentRegion, String... stateTypeKeys) {
        // Create state keys as event list so model changes are propogated
        stateKeys = new ArrayList<String>();
        this.stateKeys.addAll(Arrays.asList(stateTypeKeys));
        //
        modelDefaults = model.getUiDefaults();
        region = parentRegion;
        // create new canvas
        canvas = new Canvas(100, 30);
        canvas.setUiDefaults(modelDefaults);
        String canvasPath = getPath();
        canvas.setResourcesDir(new File(model.getResourcesDir(), canvasPath));
        canvas.setTemplatesDir(new File(model.getTemplatesDir(), canvasPath));
        canvas.setImagesDir(new File(model.getImagesDir(), canvasPath));
        canvas.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("canvas." + evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
            }
        });
        // create new style
        style = new UIStyle();
        style.setParentStyle(region.getStyle());
        style.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("style." + evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
            }
        });
    }

    // =================================================================================================================
    // JIBX Methods

    /**
     * JIBX needs this
     *
     * @param stateKeys The new list of states
     */
    private void setStateKeys(List<String> stateKeys) {
        if (stateKeys != this.stateKeys) {
            this.stateKeys.clear();
            this.stateKeys.addAll(stateKeys);
        }
    }

    /**
     * Called by JIBX after all fields have been set
     *
     * @param context The JIBX Unmarshalling Context
     */
    private void preSet(IUnmarshallingContext context) {
        // walk up till we get synth model
        for (int i = 0; i < context.getStackDepth(); i++) {
            if (context.getStackObject(i) instanceof HasUIDefaults) {
                modelDefaults = ((HasUIDefaults) context.getStackObject(i)).getUiDefaults();
                if (modelDefaults != null) break;
            }
        }
        for (int i = 0; i < context.getStackDepth(); i++) {
            if (context.getStackObject(i) instanceof UIRegion) {
                region = (UIRegion) context.getStackObject(i);
                break;
            }
        }
    }

    /**
     * Called by JIBX after all fields have been set
     *
     * @param context The JIBX Unmarshalling Context
     */
    private void postSet(IUnmarshallingContext context) {
        // add listeners to pass canvas and style events up tree
        canvas.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("canvas." + evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
            }
        });
        style.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("style." + evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
            }
        });
    }

    // =================================================================================================================
    // Bean Methods

    /**
     * Get path to this UI State of the form /RegionA/RegionB/StateName
     *
     * @return Path to this state
     */
    public String getPath() {
        StringBuilder buf = new StringBuilder(getName());
        UIRegion region = getRegion();
        // check if we are foreground background or border
        boolean found = false;
        for (UIState state : region.getBackgroundStates()) {
            if (state == this) {
                buf.insert(0, "Background/");
                found = true;
                break;
            }
        }
        if (!found) {
            for (UIState state : region.getForegroundStates()) {
                if (state == this) {
                    buf.insert(0, "Foreground/");
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            for (UIState state : region.getBorderStates()) {
                if (state == this) {
                    buf.insert(0, "Border/");
                    found = true;
                    break;
                }
            }
        }
        // add parent regions
        while (region != null) {
            buf.insert(0, '/');
            if (region instanceof UIComponent && ((UIComponent) region).getComponentName() != null) {
                buf.insert(0, ((UIComponent) region).getComponentName());
            } else {
                buf.insert(0, region.getName());
            }
            region = region.getRegion();
        }
        return buf.toString();
    }

    void setRegion(UIRegion r) {
        this.region = r;
        this.style.setParentStyle(r.getStyle());
    }

    public UIRegion getRegion() {
        return region;
    }

    /**
     * Get the local UIDefaults that contains all the UIDefaults in the synth model. It is kept uptodate by the
     * indervidual UIDefaults nodes
     *
     * @return The UIDefaults for the synth model
     */
    public UIDefaults getUIDefaults() {
        return modelDefaults;
    }


    /**
     * Get the list of state type keys for this state. This state is applied when the current component state matches as
     * many as possible of these state types.
     *
     * @return List of state types that need to be true for this state. This is direct access to the data and changes to
     *         the returned list will effect this UiState.
     */
    public List<String> getStateKeys() {
        return stateKeys;
    }

    public void setInverted(boolean b) {
        boolean old = inverted;
        inverted = b;
        firePropertyChange("invert", old, b);
    }

    public final boolean isInverted() {
        return inverted;
    }

    /**
     * Get the name of this state
     *
     * @return
     */
    public String getName() {
        if (cachedName == null) {
            StringBuilder buf = new StringBuilder();
            List<String> keys = new ArrayList<String>(stateKeys);
            Collections.sort(keys);
            for (Iterator<String> iter = keys.iterator(); iter.hasNext();) {
                buf.append(iter.next());
                if (iter.hasNext()) buf.append('+');
            }
            cachedName = buf.toString();
        }
        return cachedName;
    }

    public final Canvas getCanvas() {
        return canvas;
    }

    public void setCanvas(Canvas c) {
        Canvas old = canvas;
        canvas = c;
        firePropertyChange("canvas", old, c);
    }

    public UIStyle getStyle() {
        return style;
    }

    // =================================================================================================================
    // JIBX Helper Methods

    public static String keysToString(List<String> keys) {
        StringBuilder buf = new StringBuilder();
        for (Iterator<String> iter = keys.iterator(); iter.hasNext();) {
            buf.append(iter.next());
            if (iter.hasNext()) buf.append('+');
        }
        return buf.toString();
    }

    public static List<String> stringToKeys(String keysString) {
        return Arrays.asList(keysString.split("\\+"));
    }

}
