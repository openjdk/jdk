/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.awt.Graphics;
import javax.swing.JComponent;

/**
 * SynthUI is used to fetch the SynthContext for a particular Component.
 *
 * @author Scott Violet
 * @since 1.7
 */
public interface SynthUI extends SynthConstants {

    /**
     * Returns the Context for the specified component.
     *
     * @param c Component requesting SynthContext.
     * @return SynthContext describing component.
     */
    public SynthContext getContext(JComponent c);

    /**
     * Paints the border.
     *
     * @param context a component context
     * @param g {@code Graphics} to paint on
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param w width of the border
     * @param h height of the border
     */
    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h);
}
