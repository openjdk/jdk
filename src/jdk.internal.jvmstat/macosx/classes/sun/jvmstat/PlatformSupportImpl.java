/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, BELLSOFT. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.jvmstat;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/*
 * macOS specific implementation of the PlatformSupport routines
 * providing temporary directory support.
 */
public class PlatformSupportImpl extends PlatformSupport {

    private static final String VAR_FOLDERS_PATH = "/var/folders";
    private static final String USER_NAME_SYSTEM_PROPERTY = "user.name";
    private static final String USER_NAME_ROOT = "root";
    private static final String DIRHELPER_TEMP_STR = "T";

    private static final boolean isCurrentUserRoot =
            System.getProperty(USER_NAME_SYSTEM_PROPERTY).equals(USER_NAME_ROOT);

    public PlatformSupportImpl() {
        super();
    }

    /*
     * Return a list of the temporary directories that the VM uses
     * for the attach and perf data files.
     *
     * This function returns the traditional temp directory. Additionally,
     * when called by root, it returns other temporary directories of non-root
     * users.
     *
     * macOS per-user temp directories are located under /var/folders
     * and have the form /var/folders/<BUCKET>/<ENCODED_UUID_UID>/T
     */
    @Override
    public List<String> getTemporaryDirectories(int pid) {
        if (!isCurrentUserRoot) {
            // early exit for non-root
            return List.of(PlatformSupport.getTemporaryDirectory());
        }
        List<String> result = new ArrayList<>();
        try (DirectoryStream<Path> bs = Files.newDirectoryStream(Path.of(VAR_FOLDERS_PATH))) {
            for (Path bucket : bs) {
                try (DirectoryStream<Path> encUuids = Files.newDirectoryStream(bucket)) {
                    for (Path encUuid : encUuids) {
                        try {
                            Path tempDir = encUuid.resolve(DIRHELPER_TEMP_STR);
                            if (Files.isDirectory(tempDir) && Files.isReadable(tempDir)) {
                                result.add(tempDir.toString());
                            }
                        } catch (Exception ignore) { // ignored unreadable bucket/encUuid, continue
                        }
                    }
                } catch (IOException ignore) { // IOException ignored, continue to the next bucket
                }
            }
        } catch (Exception ignore) { // var/folders directory is inaccessible / other errors
        }
        return result.isEmpty() ? List.of(PlatformSupport.getTemporaryDirectory()) : result;
    }
}
