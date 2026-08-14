/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

public class HelloInlineClassApp {

    public HelloInlineClassApp() {
        point = new Point(0, 0);
        super();
    }

    @LooselyConsistentValue
    static value class Point {
        int x, y;

        public String toString() {
            return "(" + x + ", " + y + ")";
        }

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        Point add(Point p1) {
            return new Point(x + p1.x, y + p1.y);
        }

        Point add(Point p1, Point p2) {
            return new Point(x + p1.x + p2.x, y + p1.y + p2.y);
        }

        Point add(Point p1, int x2, int y2, Point p3) {
            return new Point(x + p1.x + x2 + p3.x, y + p1.y + y2 + p3.y);
        }
    }

    @LooselyConsistentValue
    static value class Rectangle {
        @NullRestricted
        Point p0 = new Point(0,0);
        @NullRestricted
        Point p1 = new Point(1,1);
    }

    @NullRestricted
    Point point;

    @NullRestricted
    static Rectangle rectangle = new Rectangle();

    static value record ValueRecord(int i, String name) {}

    public static void main(String[] args) throws Exception {
        Point p = new Point(0, 123);
        System.out.println("Point = " + p);
        String req = "(0, 123)";
        if (!p.toString().equals(req)) {
            throw new RuntimeException("Expected " + req + " but got " + p);
        }

        Point p1 = new Point(1, 1);
        Point p2 = new Point(2, 2);
        Point p3 = new Point(3, 3);
        int x2 = 200;
        int y2 = 200;

        int loops = 100000;
        for (int i=0; i<loops; i++) {
            p = p.add(p1);
            p = p.add(p1, p2);
            p = p.add(p1, x2, y2, p3);
        }

        int expectedX = 0 +
            loops * p1.x +
            loops * (p1.x + p2.x) +
            loops * (p1.x + x2 + p3.x);

        int expectedY = 123 +
            loops * p1.y +
            loops * (p1.y + p2.y) +
            loops * (p1.y + y2 + p3.y);

        System.out.println("Point (2) = " + p);

        if (p.x != expectedX || p.y != expectedY) {
            throw new RuntimeException("Expected (" + expectedX + ", " + expectedY + " but got " + p);
        }

        Point pzero = new Point(0,0);
        Point pone = new Point(1, 1);
        if (HelloInlineClassApp.rectangle.p0 != pzero || HelloInlineClassApp.rectangle.p1 != pone) {
            throw new RuntimeException("Static field rectangle not as expected");
        }

        HelloInlineClassApp app = new HelloInlineClassApp();
        if (app.point != pzero) {
            throw new RuntimeException("Non-static field point not as expected");
        }

        ValueRecord valueRec = new ValueRecord(30, "thirty");
        if (!valueRec.toString().equals("ValueRecord[i=30, name=thirty]")) {
            throw new RuntimeException("ValueRecord toString unexpected value: " + valueRec.toString());
        }
    }
}
