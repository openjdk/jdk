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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.filechooser.FileSystemView;

/*
 * @test
 * @bug 8227257
 * @requires (os.family == "windows")
 * @summary existing but inaccessible target for a link should be ignored
 * @run main/othervm InaccessibleLink
 * @run main/othervm -ea -esa InaccessibleLink
 */
public final class InaccessibleLink {

    /**
     * The link to the windows-update settings.
     */
    private static final byte[] bytes = {
            76, 0, 0, 0, 1, 20, 2, 0, 0, 0, 0, 0, -64, 0, 0, 0, 0, 0, 0, 70,
            -127, 0, 32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 84, 0, 20, 0, 31, 104, -128, 83,
            28, -121, -96, 66, 105, 16, -94, -22, 8, 0, 43, 48, 48, -99, 62, 0,
            97, -128, 0, 0, 0, 0, 109, 0, 115, 0, 45, 0, 115, 0, 101, 0, 116, 0,
            116, 0, 105, 0, 110, 0, 103, 0, 115, 0, 58, 0, 119, 0, 105, 0, 110,
            0, 100, 0, 111, 0, 119, 0, 115, 0, 117, 0, 112, 0, 100, 0, 97, 0,
            116, 0, 101, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    public static void main(String[] args) throws IOException {
        File file = new File("inaccessible.lnk");
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes);
            fos.close();

            FileSystemView fsv = FileSystemView.getFileSystemView();
            if (!fsv.isLink(file)) {
                throw new RuntimeException("not a link");
            }
            File linkLocation = fsv.getLinkLocation(file);
            if (linkLocation != null) {
                throw new RuntimeException(
                        "location is not null: " + linkLocation);
            }
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }
}
