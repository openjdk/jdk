/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6764257
 * @summary Tests that the color is reset properly after save/restore context
 * @author Dmitri.Trembovetski@sun.com: area=Graphics
 * @compile -XDignore.symbol.file=true RSLContextInvalidationTest.java
 * @run main/othervm RSLContextInvalidationTest
 * @run main/othervm -Dsun.java2d.noddraw=true RSLContextInvalidationTest
 * @run main/othervm -Dsun.java2d.opengl=True RSLContextInvalidationTest
 */

import java.awt.Color;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import sun.java2d.DestSurfaceProvider;
import sun.java2d.Surface;
import sun.java2d.pipe.RenderQueue;
import sun.java2d.pipe.hw.*;

public class RSLContextInvalidationTest {

    public static void main(String[] args) {
        GraphicsEnvironment ge =
            GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        VolatileImage vi = gc.createCompatibleVolatileImage(100, 100);
        vi.validate(gc);
        VolatileImage vi1 = gc.createCompatibleVolatileImage(100, 100);
        vi1.validate(gc);

        if (!(vi instanceof DestSurfaceProvider)) {
            System.out.println("Test considered PASSED: no HW acceleration");
            return;
        }

        DestSurfaceProvider p = (DestSurfaceProvider)vi;
        Surface s = p.getDestSurface();
        if (!(s instanceof AccelSurface)) {
            System.out.println("Test considered PASSED: no HW acceleration");
            return;
        }
        AccelSurface dst = (AccelSurface)s;

        Graphics g = vi.createGraphics();
        g.drawImage(vi1, 95, 95, null);
        g.setColor(Color.red);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.black);
        g.fillRect(0, 0, 100, 100);
        // after this the validated context color is black

        RenderQueue rq = dst.getContext().getRenderQueue();
        rq.lock();
        try {
            dst.getContext().saveState();
            dst.getContext().restoreState();
        } finally {
            rq.unlock();
        }

        // this will cause ResetPaint (it will set color to extended EA=ff,
        // which is ffffffff==Color.white)
        g.drawImage(vi1, 95, 95, null);

        // now try filling with black again, but it will come up as white
        // because this fill rect won't validate the color properly
        g.setColor(Color.black);
        g.fillRect(0, 0, 100, 100);

        BufferedImage bi = vi.getSnapshot();
        if (bi.getRGB(50, 50) != Color.black.getRGB()) {
            throw new RuntimeException("Test FAILED: found color="+
                Integer.toHexString(bi.getRGB(50, 50))+" instead of "+
                Integer.toHexString(Color.black.getRGB()));
        }

        System.out.println("Test PASSED.");
    }
}
