/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4296930 5033603 7092679 8340326
 * @summary Ensure that Java detects the platform time zone correctly, even
 * if changed during runtime. Also ensure that the system time zone detection code
 * detects the "Automatically adjust clock for daylight saving changes" setting
 * correctly on Windows. This is a manual test dependent on making changes to
 * the platform setting of the machine and thus cannot be automated.
 * @library /java/awt/regtesthelpers
 * @run main/manual DefaultTimeZoneTest
 */

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DefaultTimeZoneTest  {

    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzzz (XXX)");
    private static final String INSTRUCTIONS =
            """
            Tests the platform time zone detection on all platforms.
            (Part I) and on/off of DST adjustment on Windows (Part II).

            Part I:
            Observe the displayed Time zone ID and the local time.
            Change the platform time zone setting, then click the
            "Update Time Zone" button. If the ID and local time
            update correctly, part I passes, otherwise press fail. Note that
            some time zone IDs have their aliases that may be displayed.
            For example, "US/Pacific" is an alias of "America/Los_Angeles".
            If this platform is Windows, proceed to Part II. Otherwise, press
            the Pass button to finish this applet.

            Part II:
            Note that Part II may require the Administrator privilege to change
            Windows setting.

              1. Open the Date and Time control panel.
              2. Select any time zone where daylight saving time is *currently*
                 in effect, such as "(GMT-08:00) Pacific Time (US & Canada);
                 Tijuana", "(GMT+10:00) Canberra, Melbourne, Sydney", and Apply.
              3. Observe the local time on the control panel (Date&Time pane) and
                 the applet local time should be the same (daylight time).
              4. Clear "Automatically adjust clock for daylight saving changes"
                 and Apply.
              5. Observe the two local times should be the same (standard time).
              6. Select "Automatically adjust clock for daylight saving changes"
                 and Apply.

            If the local time in the control panel and applet are always the same,
            then this test passes. Press the Pass or Fail button based on the Part II
            result and finish this applet.
           """;

    public static void main(String[] args)
            throws InterruptedException, InvocationTargetException {
        // Force platform time zone as default time zone
        TimeZone.setDefault(null);
        System.setProperty("user.timezone", "");
        // Construct test window
        PassFailJFrame.builder()
                .title("DefaultTimeZoneTest Instructions")
                .testUI(createTest())
                .instructions(INSTRUCTIONS)
                .build().awaitAndCheck();
    }

    private static Window createTest() {
        var contents = new JFrame("DefaultTimeZoneTest");
        var label = new JLabel(SDF.format(new Date()));
        var panel = new JPanel();
        var button = new JButton("Update Time Zone");
        panel.add(button);
        contents.setSize(350, 250);
        contents.add(label, BorderLayout.NORTH);
        contents.add(panel, BorderLayout.CENTER);
        // Update default time zone on button click
        button.addActionListener(e -> {
            TimeZone tz = TimeZone.getDefault();
            SDF.setTimeZone(tz);
            label.setText(SDF.format(new Date()));
            contents.repaint();
        });
        return contents;
    }
}
