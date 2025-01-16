/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug     6684104
 * @summary Test verifies that ImageIO uses cache if requested.
 * @run     main/othervm CachePermissionsTest true
 * @run     main/othervm CachePermissionsTest false
 */

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.ImageIO;

public class CachePermissionsTest {
    public static void main(String[] args) throws Exception {
        boolean isFileCacheExpected =
            Boolean.valueOf(args[0]).booleanValue();
        System.out.println("Is file cache expected: " + isFileCacheExpected);

        ImageIO.setUseCache(isFileCacheExpected);

        System.out.println("java.io.tmpdir is " + System.getProperty("java.io.tmpdir"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);

        boolean isFileCache = ios.isCachedFile();
        System.out.println("Is file cache used: " + isFileCache);

        if (isFileCache != isFileCacheExpected) {
            System.out.println("WARNING: file cache usage is not as expected!");
        }

        System.out.println("Verify data writing...");
            for (int i = 0; i < 8192; i++) {
            ios.writeInt(i);
        }

        System.out.println("Verify data reading...");
        ios.seek(0L);

        for (int i = 0; i < 8192; i++) {
            int j = ios.readInt();
            if (i != j) {
                throw new RuntimeException("Wrong data in the stream " + j + " instead of " + i);
            }
        }

        System.out.println("Verify stream closing...");
        ios.close();
    }
}
