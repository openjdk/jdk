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

import java.awt.Dimension;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Iterator;

/**
 * LayerContainer
 *
 * @author Created by Jasper Potts (May 31, 2007)
 */
public interface LayerContainer {
    public void addPropertyChangeListener(PropertyChangeListener listener);

    public void removePropertyChangeListener(PropertyChangeListener listener);

    public LayerContainer getParent();

    public void addLayer(Layer layer);

    public void addLayer(int i, Layer layer);

    public void removeLayer(Layer layer);

    public int getLayerCount();

    public Layer getLayer(int index);

    public int indexOfLayer(Layer layer);

    public Iterator<Layer> getLayerIterator();

    public Collection<Layer> getLayers();

    /**
     * Get the size in pixels of the root of the layer tree, this is usualy a canvas
     *
     * @return The size of the whole layer tree
     */
    public Dimension getRootSize();

}
