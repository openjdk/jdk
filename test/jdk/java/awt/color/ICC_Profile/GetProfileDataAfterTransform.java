/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorConvertOp;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;

/**
 * @test
 * @bug 8272860 8359380
 * @summary Verifies that ICC_Profile methods work correctly after a
 *          ColorConvertOp transformation
 * @library /test/lib
 */
public final class GetProfileDataAfterTransform {

    private static final int[] CSS = {
            ColorSpace.CS_CIEXYZ, ColorSpace.CS_GRAY,
            ColorSpace.CS_LINEAR_RGB, ColorSpace.CS_PYCC, ColorSpace.CS_sRGB
    };

    /**
     * The main process records expected get*() values before conversion and
     * passes them to a subprocess, which verifies it after the transform.
     *
     * @param  args If empty, the main process runs all color space pairs and
     *         spawns subprocesses. If not empty, args[0] and args[1] are source
     *         and target color space constants, followed by expected profile
     *         values to validate in the subprocess.
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            for (int csFrom : CSS) {
                for (int csTo : CSS) {
                    var from = (ICC_ColorSpace) ColorSpace.getInstance(csFrom);
                    var to = (ICC_ColorSpace) ColorSpace.getInstance(csTo);

                    ICC_Profile profileFrom = from.getProfile();
                    ICC_Profile profileTo = to.getProfile();

                    List<String> cmd = new ArrayList<>();
                    cmd.add(GetProfileDataAfterTransform.class.getSimpleName());

                    cmd.add(String.valueOf(csFrom));
                    cmd.add(String.valueOf(csTo));

                    for (ICC_Profile p : List.of(profileFrom, profileTo)) {
                        cmd.add(String.valueOf(p.getPCSType()));
                        cmd.add(String.valueOf(p.getProfileClass()));
                        cmd.add(String.valueOf(p.getMinorVersion()));
                        cmd.add(String.valueOf(p.getMajorVersion()));
                        cmd.add(String.valueOf(p.getColorSpaceType()));
                        cmd.add(String.valueOf(p.getNumComponents()));
                    }

                    OutputAnalyzer output = ProcessTools.executeTestJava(cmd);
                    output.shouldHaveExitValue(0).stdoutShouldBeEmpty()
                          .stderrShouldBeEmpty();
                }
            }
        } else {
            int csFrom = Integer.parseInt(args[0]);
            int csTo = Integer.parseInt(args[1]);
            var from = (ICC_ColorSpace) ColorSpace.getInstance(csFrom);
            var to = (ICC_ColorSpace) ColorSpace.getInstance(csTo);

            BufferedImageOp op = new ColorConvertOp(from, to, null);
            // Note from.getProfile() and to.getProfile() are not loaded yet!
            op.filter(new BufferedImage(10, 10, TYPE_INT_RGB),
                      new BufferedImage(10, 10, TYPE_INT_RGB));

            test(from.getProfile(), args, 2);
            test(to.getProfile(), args, 8);
        }
    }

    private static void test(ICC_Profile profile, String[] args, int offset) {
        // Uncomment when JDK-8272860 is fixed
        // if (profile.getData() == null) {
        //    throw new RuntimeException("Profile data is null");
        // }
        if (profile.getPCSType() != Integer.parseInt(args[offset++])) {
            throw new RuntimeException("Wrong PCStype");
        }
        if (profile.getProfileClass() != Integer.parseInt(args[offset++])) {
            throw new RuntimeException("Wrong ProfileClass");
        }
        if (profile.getMinorVersion() != Integer.parseInt(args[offset++])) {
            throw new RuntimeException("Wrong MinorVersion");
        }
        if (profile.getMajorVersion() != Integer.parseInt(args[offset++])) {
            throw new RuntimeException("Wrong MajorVersion");
        }
        if (profile.getColorSpaceType() != Integer.parseInt(args[offset++])) {
            throw new RuntimeException("Wrong ColorSpaceType");
        }
        if (profile.getNumComponents() != Integer.parseInt(args[offset])) {
            throw new RuntimeException("Wrong NumComponents");
        }
    }
}
