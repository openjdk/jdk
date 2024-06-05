/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class ThirdPartyAppLauncher {
    private static final Logger logger = Logger
            .getLogger(ThirdPartyAppLauncher.class.getName());
    private static final String FS = File.separator;
    private static final Path CWD = Paths.get(".");
    private static final String processIdFilePath = CWD + FS + "process.tmp";

    public static void main(String[] args) throws IOException {

        ProcessBuilder processBuilder = new ProcessBuilder("regedit");
        Process process = processBuilder.start();
        logger.info("RegEdit id=" + process.pid());

        File file = new File(processIdFilePath);
        BufferedWriter writer = null;
        try {
            if (file.createNewFile()) {
                logger.info(file.getAbsolutePath().toString());
                writer = new BufferedWriter(new FileWriter(file));
                writer.write("" + process.pid());
                writer.close();
                logger.info("Successfully written processid to file "
                        + processIdFilePath);
            }
        } catch (IOException e) {
            throw new IOException(e);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException ex) {
                throw new IOException(ex);
            }
        }
        System.exit(0);
    }
}
