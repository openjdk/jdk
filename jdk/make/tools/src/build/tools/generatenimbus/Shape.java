/*
 * Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package build.tools.generatenimbus;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;


public abstract class Shape {
    @XmlElement
    private PaintPoints paintPoints;
    public double getPaintX1() { return paintPoints.x1; }
    public double getPaintX2() { return paintPoints.x2; }
    public double getPaintY1() { return paintPoints.y1; }
    public double getPaintY2() { return paintPoints.y2; }

    @XmlElements({
        @XmlElement(name = "matte", type = Matte.class),
        @XmlElement(name = "gradient", type = Gradient.class),
        @XmlElement(name = "radialGradient", type = RadialGradient.class)
    })
    private Paint paint;
    public Paint getPaint() { return paint; }

    static class PaintPoints {
        @XmlAttribute double x1;
        @XmlAttribute double y1;
        @XmlAttribute double x2;
        @XmlAttribute double y2;
    }
}

class Point {
    @XmlAttribute private double x;
    public double getX() { return x; }

    @XmlAttribute private double y;
    public double getY() { return y; }

    @XmlAttribute(name="cp1x") private double cp1x;
    public double getCp1X() { return cp1x; }

    @XmlAttribute(name="cp1y") private double cp1y;
    public double getCp1Y() { return cp1y; }

    @XmlAttribute(name="cp2x") private double cp2x;
    public double getCp2X() { return cp2x; }

    @XmlAttribute(name="cp2y") private double cp2y;
    public double getCp2Y() { return cp2y; }

    public boolean isP1Sharp() {
        return cp1x == x && cp1y == y;
    }

    public boolean isP2Sharp() {
        return cp2x == x && cp2y == y;
    }
}

class Path extends Shape {
    @XmlElement(name="point")
    @XmlElementWrapper(name="points")
    private List<Point> controlPoints = new ArrayList<Point>();
    public List<Point> getControlPoints() { return controlPoints; }
}

class Rectangle extends Shape {
    @XmlAttribute private double x1;
    public double getX1() { return x1; }

    @XmlAttribute private double x2;
    public double getX2() { return x2; }

    @XmlAttribute private double y1;
    public double getY1() { return y1; }

    @XmlAttribute private double y2;
    public double getY2() { return y2; }

    @XmlAttribute
    public double getRounding() {
        double rounding = Math.abs(roundingX - x1) * 2;
        return rounding > 2 ? rounding : 0;
    }

    public void setRounding(double rounding) {
        if (rounding > 0 && rounding < 2) {
            rounding = 0;
        }
        roundingX = rounding / 2d + x1;
    }
    private double roundingX;

    public boolean isRounded() {
        return getRounding() > 0;
    }

}

class Ellipse extends Shape {
    @XmlAttribute private double x1;
    public double getX1() { return x1; }

    @XmlAttribute private double x2;
    public double getX2() { return x2; }

    @XmlAttribute private double y1;
    public double getY1() { return y1; }

    @XmlAttribute private double y2;
    public double getY2() { return y2; }
}
