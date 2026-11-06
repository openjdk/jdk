/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

/*
 * @test
 * @bug 8393264
 * @key headful
 * @summary The splash screen GIF decoder must not read past the color table
 *          when the background color index is not covered by the global
 *          color table
 * @run main SplashGifBackgroundColorTest
 */

public class SplashGifBackgroundColorTest {

    private static final byte[] GIF = {
        'G', 'I', 'F', '8', '9', 'a',
        0x01, 0x00, 0x01, 0x00, (byte) 0x80, (byte) 0xFF, 0x00,
        0x00, 0x00, 0x00,
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
        0x21, (byte) 0xF9, 0x04, 0x08, 0x00, 0x00, 0x00, 0x00,
        0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        0x02, 0x02, 0x44, 0x01,
        0x00, 0x3B
    };

    private static final int BACKGROUND_COLOR = 11;

    public static void main(String[] args) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(GIF));
        if (image == null || image.getWidth() != 1 || image.getHeight() != 1) {
            throw new RuntimeException("crafted GIF is not decodable");
        }

        byte[] validGif = GIF.clone();
        validGif[BACKGROUND_COLOR] = 1;

        Path dir = Files.createTempDirectory("splash-gif-background");
        try {
            Path java = Path.of(System.getProperty("java.home"), "bin", "java");
            Path badGif = dir.resolve("background-color-oob.gif");
            Files.write(badGif, GIF);
            runSplash(java, badGif);
            Path goodGif = dir.resolve("background-color-valid.gif");
            Files.write(goodGif, validGif);
            runSplash(java, goodGif);
            System.out.println("OK: splash screen decoded GIFs with an out-of-range and a valid background color index");
        } finally {
            delete(dir);
        }
    }


    private static void runSplash(Path java, Path gif) throws Exception {
        Path log = gif.resolveSibling(gif.getFileName() + ".log");

        Process p = new ProcessBuilder(java.toString(),
                "-splash:" + gif, "-version")
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();

        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly().waitFor();
            throw new RuntimeException("child VM timed out: " + gif);
        }

        if (p.exitValue() != 0) {
            throw new RuntimeException("child VM failed, exit "
                    + p.exitValue() + ":\n" + Files.readString(log));
        }
    }

    private static void delete(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.list(path)) {
            for (var entry : stream.toList()) {
                Files.deleteIfExists(entry);
            }
        }
        Files.deleteIfExists(path);
    }
}
