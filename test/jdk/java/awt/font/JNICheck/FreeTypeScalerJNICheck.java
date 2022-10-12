/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, JetBrains s.r.o.. All rights reserved.
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
 */

/* @test
 * @bug 8269223
 * @summary Verifies that -Xcheck:jni issues no warnings from freetypeScaler.c
 * @library /test/lib
 * @run main FreeTypeScalerJNICheck
 */
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class FreeTypeScalerJNICheck {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("runtest")) {
            runTest();
        } else {
            ProcessBuilder pb = ProcessTools.createTestJvm("-Xcheck:jni", FreeTypeScalerJNICheck.class.getName(), "runtest");
            OutputAnalyzer oa = ProcessTools.executeProcess(pb);
            oa.shouldContain("Done").shouldNotContain("WARNING").shouldHaveExitValue(0);
        }
    }

    public static void runTest() {
        String families[] = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        BufferedImage bi = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bi.createGraphics();

        for (String ff : families)
        {
            Font font = new Font(ff, Font.PLAIN, 12);
            Rectangle2D bounds = font.getStringBounds("test", g2d.getFontRenderContext());
            g2d.setFont(font);
            FontMetrics metrics = g2d.getFontMetrics(font);
            System.out.println(bounds.getHeight() + metrics.getHeight()); // use bounds and metrics
        }

        System.out.println("Done");
    }
}

