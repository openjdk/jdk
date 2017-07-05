/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

/* @test
   @bug 6824600
   @summary OOM occurs when setLookAndFeel() is executed in Windows L&F(XP style)
   @author Pavel Porvatov
   @run main Test6824600
*/

import com.sun.java.swing.plaf.windows.DesktopProperty;

import java.awt.*;

public class Test6824600 {
    public static void main(String[] args) throws Exception {
        Toolkit toolkit = Toolkit.getDefaultToolkit();

        HackedDesktopProperty desktopProperty = new HackedDesktopProperty("Button.background", null);

        // Register listener in toolkit
        desktopProperty.getValueFromDesktop();

        int length = toolkit.getPropertyChangeListeners().length;

        // Make several invocations
        desktopProperty.getValueFromDesktop();
        desktopProperty.getValueFromDesktop();

        desktopProperty.invalidate();

        desktopProperty.getValueFromDesktop();
        desktopProperty.getValueFromDesktop();

        if (length != toolkit.getPropertyChangeListeners().length) {
            throw new RuntimeException("New listeners were added into Toolkit");
        }
    }

    public static class HackedDesktopProperty extends DesktopProperty {
        public HackedDesktopProperty(String key, Object fallback) {
            super(key, fallback);
        }

        // Publish the method
        public Object getValueFromDesktop() {
            return super.getValueFromDesktop();
        }
    }
}
