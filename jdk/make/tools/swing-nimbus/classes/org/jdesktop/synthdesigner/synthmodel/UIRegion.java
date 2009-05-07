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
import org.jdesktop.swingx.designer.utils.HasUIDefaults;
import org.jibx.runtime.IUnmarshallingContext;

import javax.swing.UIDefaults;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a "Region" in synth, which also includes entire components.
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
public class UIRegion extends AbstractBean implements HasUIDefaults, HasUIStyle {
    private String name;//the code-wise name of the region
    protected String key; //the UIdefaults key for this region
    protected String title; //the user friendly name/title of this region
    /** List of background states */
    private List<UIState> backgroundStates;
    /** List of foreground states */
    private List<UIState> foregroundStates;
    /** List of border states */
    private List<UIState> borderStates;
    private UIStyle style = new UIStyle();
    protected Insets contentMargins = new Insets(0, 0, 0, 0);
    /** Sub regions, if any */
    private List<UIRegion> subRegions;

    //together with name, these two fields allow me to reconstruct, in
    //code, a synth Region, including a custom Region, if you make one.
    private String ui;
    private boolean subregion;
    /**
     * This is a local UIDefaults that contains all the UIDefaults in the synth model. It is kept uptodate by the
     * indervidual UIDefaults nodes
     */
    private transient UIDefaults modelDefaults = null;

    private UIRegion region; //the region that this region belongs to

    // =================================================================================================================
    // Constructors

    /** no-args contructor for JIBX */
    protected UIRegion() {
        subRegions = new ArrayList<UIRegion>();
        backgroundStates = new ArrayList<UIState>();
        foregroundStates = new ArrayList<UIState>();
        borderStates = new ArrayList<UIState>();
        style.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                firePropertyChange("style." + evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
            }
        });
    }

    public UIRegion(String name, UIRegion... subRegions) {
        this(name, null, true, subRegions);
    }

    public UIRegion(String name, String ui, UIRegion... subRegions) {
        this(name, ui, false, subRegions);
    }

    public UIRegion(String name, String ui, boolean subregion, UIRegion... subRegions) {
        this();
        this.name = name;
        this.ui = ui;
        this.subregion = subregion;
        if (subRegions != null) {
            for (UIRegion r : subRegions) {
                if (r != null) {
                    this.subRegions.add(r);
                    r.getStyle().setParentStyle(getStyle());
                }
            }
        }
    }

    // =================================================================================================================
    // JIBX Methods

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
            if (context.getStackObject(i) instanceof UIRegion && context.getStackObject(i) != this) {
                region = (UIRegion) context.getStackObject(i);
                break;
            }
        }
    }

    // =================================================================================================================
    // Bean Methods

    public Insets getContentMargins() {
        return contentMargins;
    }

    public void setContentMargins(Insets contentMargins) {
        Insets old = getContentMargins();
        this.contentMargins = contentMargins;
        firePropertyChange("contentMargins", old, getContentMargins());
    }

    void setRegion(UIRegion r) {
        this.region = r;
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
    public UIDefaults getUiDefaults() {
        return modelDefaults;
    }

    public String getName() {
        return name;
    }

    public final UIRegion[] getSubRegions() {
        return subRegions.toArray(new UIRegion[0]);
    }

    public final UIState[] getBackgroundStates() {
        return backgroundStates.toArray(new UIState[0]);
    }

    public final UIState[] getForegroundStates() {
        return foregroundStates.toArray(new UIState[0]);
    }

    public final UIState[] getBorderStates() {
        return borderStates.toArray(new UIState[0]);
    }

    public UIStyle getStyle() {
        return style;
    }

    public final boolean isSubRegion() {
        return subregion;
    }

    public final String getUi() {
        return ui;
    }

    public void addBackgroundState(UIState state) {
        // check if we already have that state
        for (UIState uiState : backgroundStates) {
            if (uiState.getName().equals(state.getName())) return;
        }
        backgroundStates.add(state);
        state.setRegion(this);
        firePropertyChange("backgroundStates", null, backgroundStates);
    }

    public void removeBackgroundState(UIState state) {
        if (backgroundStates.remove(state)) {
            firePropertyChange("backgroundStates", null, backgroundStates);
        }
    }

    public void addForegroundState(UIState state) {
        // check if we already have that state
        for (UIState uiState : foregroundStates) {
            if (uiState.getName().equals(state.getName())) return;
        }
        foregroundStates.add(state);
        state.setRegion(this);
        firePropertyChange("foregroundStates", null, foregroundStates);
    }

    public void removeForegroundState(UIState state) {
        if (foregroundStates.remove(state)) {
            firePropertyChange("foregroundStates", null, foregroundStates);
        }
    }

    public void addBorderState(UIState state) {
        // check if we already have that state
        for (UIState uiState : borderStates) {
            if (uiState.getName().equals(state.getName())) return;
        }
        borderStates.add(state);
        state.setRegion(this);
        firePropertyChange("borderStates", null, borderStates);
    }

    public void removeBorderState(UIState state) {
        if (borderStates.remove(state)) {
            firePropertyChange("borderStates", null, borderStates);
        }
    }


    public String getKey() {
        return key == null || "".equals(key) ? name : key;
    }

    public String getTitle() {
        return title == null || "".equals(title) ? name : title;
    }
}
