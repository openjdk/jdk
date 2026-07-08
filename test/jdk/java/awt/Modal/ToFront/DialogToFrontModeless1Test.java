/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @key headful
 * @bug 8050885
 * @summary Check that calling toFront method does not bring a dialog to the top
 *          of a child modeless dialog.
 *
 * @library ../helpers /lib/client/
 * @library /test/lib
 * @build ExtendedRobot
 * @build Flag
 * @build TestDialog
 * @build TestFrame
 * @build jdk.test.lib.Platform
 * @run main DialogToFrontModeless1Test
 */

import jdk.test.lib.Platform;
import jtreg.SkippedException;

public class DialogToFrontModeless1Test {

    public static void main(String[] args) throws Exception {
        if (Platform.isOnWayland()) {
            // Some tested systems are still use XTEST(X11 protocol)
            // for key and mouse press emulation, but this will not work
            // outside of X11.
            // An emulated input event will reach X11 clients, but not the
            // Wayland compositor, which is responsible for window restacking.
            //
            // This skip can be removed later once all systems switch to
            // the default remote desktop XDG portal.
             throw new SkippedException("SKIPPED: robot functionality is limited on the current platform.");
        }
        (new DialogToFrontModelessTest()).doTest();
    }
}
