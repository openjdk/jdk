/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * or visit www.oracle.com if you have any questions.
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

    private static Process xvfb;

    public static void main(String[] args) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(GIF));
        if (image == null || image.getWidth() != 1 || image.getHeight() != 1) {
            throw new RuntimeException("crafted GIF is not decodable");
        }

        byte[] validGif = GIF.clone();
        validGif[BACKGROUND_COLOR] = 1;

        Path dir = Files.createTempDirectory("splash-gif-background");
        try {
            Path java = Path.of(System.getProperty("java.home"), "bin",
                    isWindows() ? "java.exe" : "java");
            Path badGif = dir.resolve("background-color-oob.gif");
            Files.write(badGif, GIF);
            runSplash(java, badGif, 5);
            Path goodGif = dir.resolve("background-color-valid.gif");
            Files.write(goodGif, validGif);
            runSplash(java, goodGif, 1);
            System.out.println("OK: splash screen decoded GIFs with an out-of-range and a valid background color index");
        } finally {
            if (xvfb != null) {
                xvfb.destroyForcibly().waitFor();
            }
            delete(dir);
        }
    }

    private static void runSplash(Path java, Path gif, int times)
            throws Exception {
        Path dir = gif.getParent();
        for (int i = 0; i < times; i++) {
            Path out = dir.resolve(gif.getFileName() + ".log");
            var pb = new ProcessBuilder(java.toString(),
                    "-splash:" + gif, "-version")
                    .redirectErrorStream(true).redirectOutput(out.toFile());

            String display = display();
            if (display != null) {
                pb.environment().put("DISPLAY", display);
            }
            Process p = pb.start();
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly().waitFor();
                throw new RuntimeException("child VM timed out");
            }
            if (p.exitValue() != 0) {
                throw new RuntimeException("child VM failed to start, exit "
                        + p.exitValue() + ":\n" + Files.readString(out));
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    /*
     * Returns the X display the child should use, starting a private Xvfb
     * server when the environment has no display. Returns null on platforms
     * that do not use X11.
     */
    private static String display() throws Exception {
        String os = System.getProperty("os.name");
        if (!os.equals("Linux")) {
            return null;
        }
        String current = System.getenv("DISPLAY");
        if (current != null && !current.isEmpty()) {
            return current;
        }
        if (xvfb == null) {
            xvfb = startXvfb();
        }
        return xvfbDisplay;
    }

    private static String xvfbDisplay;

    private static Process startXvfb() throws Exception {
        Path socketDir = Path.of("/tmp/.X11-unix");
        for (int number = 96; number < 256; number++) {
            if (Files.exists(socketDir.resolve("X" + number))) {
                continue;
            }
            String display = ":" + number;
            Process p = new ProcessBuilder("Xvfb", display,
                    "-screen", "0", "1280x1024x24")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            Path socket = socketDir.resolve("X" + number);
            for (int i = 0; i < 100; i++) {
                if (!p.isAlive()) {
                    break;
                }
                if (Files.exists(socket)) {
                    Thread.sleep(200);
                    xvfbDisplay = display;
                    return p;
                }
                Thread.sleep(100);
            }
            p.destroyForcibly().waitFor();
        }
        throw new RuntimeException("cannot start Xvfb; install it or set DISPLAY to run this test");
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
