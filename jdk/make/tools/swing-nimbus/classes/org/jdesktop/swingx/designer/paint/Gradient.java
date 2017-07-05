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
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.Paint;

/**
 * Represents a GradientPaint or LinearGradientPaint.
 *
 * @author rbair
 */
public class Gradient extends AbstractGradient implements Cloneable {
    protected Paint createPaint(float[] fractions, Matte[] mattes, CycleMethod method) {
        Color[] colors = new Color[mattes.length];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = mattes[i].getColor();
        }
        return new LinearGradientPaint(0, 0, 1, 0, fractions, colors, method);
    }

    @Override public Gradient clone() {
        Gradient gradient = new Gradient();
        copyTo(gradient);
        return gradient;
    }
}
