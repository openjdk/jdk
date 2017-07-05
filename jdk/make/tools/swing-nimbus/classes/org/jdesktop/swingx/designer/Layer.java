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
package org.jdesktop.swingx.designer;

import org.jdesktop.swingx.designer.effects.Effect;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Layer
 *
 * @author Created by Jasper Potts (May 22, 2007)
 */
public class Layer extends SimpleShape implements Iterable<SimpleShape>, LayerContainer {
    public static enum LayerType {
        standard, template
    }

    private String name;
    protected LayerType type = LayerType.standard;
    /** List of shapes in this layer, first shape is painted on top */
    private List<SimpleShape> shapes = new ArrayList<SimpleShape>();
    private List<Effect> effects = new ArrayList<Effect>();
    private double opacity = 1;
    private double fillOpacity = 1;
    private BlendingMode blendingMode = BlendingMode.NORMAL;
    private boolean locked = false;
    private boolean visible = true;
    private PropertyChangeListener shapeChangeListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            int index = shapes.indexOf((SimpleShape) evt.getSource());
            firePropertyChange("shapes[" + index + "]." + evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
        }
    };
    private PropertyChangeListener effectChangeListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            int index = effects.indexOf((Effect) evt.getSource());
            System.out.println(
                    "Layer.propertyChange EFFECT PROPERTY CHANGED " + evt.getSource() + " -- " + evt.getPropertyName());
            firePropertyChange("effects[" + index + "]." + evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
        }
    };
    private BufferedImage buffer = null;
    // =================================================================================================================
    // Constructors

    public Layer() {
    }

    public Layer(String name) {
        this();
        this.name = name;
    }

    /** Called by JIBX after populating this layer so we can add listeners to children */
    protected void postInit() {
        for (SimpleShape shape : shapes) {
            shape.addPropertyChangeListener(shapeChangeListener);
            shape.setParent(this);
        }
        for (Effect effect : effects) {
            effect.addPropertyChangeListener(effectChangeListener);
        }
    }

    // =================================================================================================================
    // Bean Methods

    public LayerType getType() {
        return type;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        boolean old = isLocked();
        this.locked = locked;
        firePropertyChange("locked", old, isLocked());
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        boolean old = isVisible();
        this.visible = visible;
        firePropertyChange("visible", old, isVisible());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        String old = getName();
        this.name = name;
        firePropertyChange("name", old, getName());
    }

    public void setParent(LayerContainer parent) {
        super.setParent(parent);
        // generate a name if null
        if (name == null) {
            Canvas c = null;
            LayerContainer p = parent;
            while (true) {
                if (p instanceof Canvas) {
                    c = (Canvas) p;
                    break;
                } else if (p == null) {
                    break;
                }
                p = p.getParent();
            }
            if (c != null) {
                setName("Layer " + c.getNextLayerNameIndex());
            }
        }
    }

    /**
     * Add shape to top of layer so it paints above all other shapes
     *
     * @param shape The shape to add
     */
    public void add(SimpleShape shape) {
        shapes.add(0, shape);
        shape.setParent(this);
        shape.addPropertyChangeListener(shapeChangeListener);
        fireIndexedPropertyChange("shapes", 0, null, shape);
    }

    public void remove(SimpleShape shape) {
        int index = shapes.indexOf(shape);
        if (index != -1) {
            shapes.remove(shape);
            shape.setParent(null);
            fireIndexedPropertyChange("shapes", index, shape, null);
        }
    }

    /**
     * Returns an unmodifianle iterator over a set of elements of type SimpleShape.
     *
     * @return an Iterator.
     */
    public Iterator<SimpleShape> iterator() {
        return Collections.unmodifiableList(shapes).iterator();
    }


    public List<Effect> getEffects() {
        return Collections.unmodifiableList(effects);
    }

    public void addEffect(Effect effect) {
        int index = effects.size();
        effects.add(effect);
        effect.addPropertyChangeListener(effectChangeListener);
        fireIndexedPropertyChange("effects", index, null, effects);
    }

    public void removeEffect(Effect effect) {
        int index = effects.indexOf(effect);
        if (index != -1) {
            effects.remove(effect);
            effect.removePropertyChangeListener(effectChangeListener);
            fireIndexedPropertyChange("effects", index, effect, null);
        }
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double opacity) {
        if (opacity < 0 || opacity > 1) return;
        double old = getOpacity();
        this.opacity = opacity;
        firePropertyChange("opacity", old, getOpacity());
    }

    public double getFillOpacity() {
        return fillOpacity;
    }

    public void setFillOpacity(double fillOpacity) {
        if (fillOpacity < 0 || fillOpacity > 1) return;
        double old = getFillOpacity();
        this.fillOpacity = fillOpacity;
        firePropertyChange("fillOpacity", old, getFillOpacity());
    }

    public BlendingMode getBlendingMode() {
        return blendingMode;
    }

    public void setBlendingMode(BlendingMode blendingMode) {
        BlendingMode old = getBlendingMode();
        this.blendingMode = blendingMode;
        firePropertyChange("blendingMode", old, getBlendingMode());
    }

    // =================================================================================================================
    // Layer Methods

    /**
     * Get the parent canvas that contains this layer
     *
     * @return Parant canvas, or null if the layer is not in a canvas
     */
    public Canvas getCanvas() {
        LayerContainer lc = this;
        while (lc != null) {
            if (lc instanceof Canvas) return (Canvas) lc;
            lc = lc.getParent();
        }
        return null;
    }

    public List<SimpleShape> getShapes() {
        return new ArrayList<SimpleShape>(shapes);
    }

    public List<SimpleShape> getIntersectingShapes(Point2D p, double pixelSize) {
        if (isLocked() || !isVisible()) return Collections.emptyList();
        List<SimpleShape> intersectingShapes = new ArrayList<SimpleShape>();
        for (SimpleShape shape : shapes) {
            if (shape instanceof Layer) {
                intersectingShapes.addAll(((Layer) shape).getIntersectingShapes(p, pixelSize));
            } else {
                if (shape.isHit(p, pixelSize)) intersectingShapes.add(shape);
            }
        }
        return intersectingShapes;
    }

    public List<SimpleShape> getIntersectingShapes(Rectangle2D rect, double pixelSize) {
        if (isLocked() || !isVisible()) return Collections.emptyList();
        List<SimpleShape> intersectingShapes = new ArrayList<SimpleShape>();
        for (SimpleShape shape : shapes) {
            if (shape instanceof Layer) {
                intersectingShapes.addAll(((Layer) shape).getIntersectingShapes(rect, pixelSize));
            } else {
                if (shape.intersects(rect, pixelSize)) intersectingShapes.add(shape);
            }
        }
        return intersectingShapes;

    }

    public boolean isEmpty() {
        return shapes.isEmpty();
    }

    // =================================================================================================================
    // SimpleShape Methods

    public Rectangle2D getBounds(double pixelSize) {
        Rectangle2D.Double rect = new Rectangle2D.Double();
        for (SimpleShape shape : shapes) {
            rect.add(shape.getBounds(pixelSize));
        }
        return rect;
    }


    public Shape getShape() {
        return getBounds(0);
    }

    public boolean isHit(Point2D p, double pixelSize) {
        if (isLocked() || !isVisible()) return false;
        for (SimpleShape shape : shapes) {
            if (shape.isHit(p, pixelSize)) return true;
        }
        return false;
    }

    public boolean intersects(Rectangle2D rect, double pixelSize) {
        if (isLocked() || !isVisible()) return false;
        for (SimpleShape shape : shapes) {
            if (shape.intersects(rect, pixelSize)) return true;
        }
        return false;
    }

    public List<ControlPoint> getControlPoints() {
        return Collections.emptyList();
    }

    public void paint(Graphics2D g2, double pixelSize) {
    }

    public void paintControls(Graphics2D g2, double pixelSize, boolean paintControlLines) {

    }

    public String toString() {
        return getName();
    }

    // =================================================================================================================
    // LayerContainer Methods

    public void addLayer(int i, Layer layer) {
        // get existing layer at index i
        Layer existingLayer = getLayer(i);
        if (existingLayer == null) {
            addLayer(layer);
        } else {
            int index = indexOfLayer(existingLayer);
            shapes.add(index, layer);
            layer.setParent(this);
            layer.addPropertyChangeListener(shapeChangeListener);
            fireIndexedPropertyChange("layers", index, null, layer);
        }
    }

    public void addLayer(Layer layer) {
        shapes.add(layer);
        layer.setParent(this);
        layer.addPropertyChangeListener(shapeChangeListener);
        int index = indexOfLayer(layer);
        fireIndexedPropertyChange("layers", index, null, layer);
    }

    public Layer getLayer(int index) {
        int i = -1;
        for (SimpleShape shape : shapes) {
            if (shape instanceof Layer) i++;
            if (i == index) return (Layer) shape;
        }
        return null;
    }

    public int getLayerCount() {
        int count = 0;
        for (SimpleShape shape : shapes) {
            if (shape instanceof Layer) count++;
        }
        return count;
    }


    public Collection<Layer> getLayers() {
        List<Layer> layers = new ArrayList<Layer>();
        for (SimpleShape shape : shapes) {
            if (shape instanceof Layer) layers.add((Layer) shape);
        }
        return Collections.unmodifiableList(layers);
    }

    public Iterator<Layer> getLayerIterator() {
        return new Iterator<Layer>() {
            private int index = 0;

            public boolean hasNext() {
                for (int i = index; i < shapes.size(); i++) {
                    if (shapes.get(i) instanceof Layer) {
                        return true;
                    }
                }
                return false;
            }

            public Layer next() {
                for (; index < shapes.size(); index++) {
                    if (shapes.get(index) instanceof Layer) {
                        Layer nextLayer = (Layer) shapes.get(index);
                        index++; // increment index so we don't find the same one again
                        return nextLayer;
                    }
                }
                return null;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public int indexOfLayer(Layer layer) {
        int i = -1;
        for (SimpleShape s : shapes) {
            if (s instanceof Layer) i++;
            if (s == layer) return i;
        }
        return -1;
    }

    public void removeLayer(Layer layer) {
        int index = indexOfLayer(layer);
        if (index != -1) {
            shapes.remove(layer);
            layer.removePropertyChangeListener(shapeChangeListener);
            fireIndexedPropertyChange("layers", index, layer, null);
        }
    }


    public Dimension getRootSize() {
        return getParent().getRootSize();
    }
}
