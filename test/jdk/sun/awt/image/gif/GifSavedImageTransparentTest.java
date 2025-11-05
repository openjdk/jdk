/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8357034
 * @summary This test verifies that when the transparent pixel index changes
 * and we're rendering on top of another frame we respect the new transparency.
 */

import java.io.File;

public class GifSavedImageTransparentTest {
    public static void main(String[] args) throws Throwable {
        GifBuilder.FrameDescription[] frames =
                new GifBuilder.FrameDescription[] {
                        new GifBuilder.FrameDescription(
                                GifBuilder.Disposal.doNotDispose, false),
                        new GifBuilder.FrameDescription(
                                GifBuilder.Disposal.doNotDispose, true),
                        new GifBuilder.FrameDescription(
                                GifBuilder.Disposal.doNotDispose, true)
                };

        File dir = null;

        // un-comment to visually inspect the frames:
//        dir = new File("8357034-frames");
//        dir.mkdir();

        GifBuilder.test(frames, dir);
    }
}
