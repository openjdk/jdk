/*
 * Copyright (c) 2026, JetBrains s.r.o.. All rights reserved.
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
 * @bug 8313706
 * @summary To check whether images placed on macOS clipboard are encoded in
 *          the correct format (TIFF, PNG, JPEG)
 * @key headful
 * @requires os.family == "mac"
 * @library /test/lib
 * @run main ImageNativeFormatsTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

public class ImageNativeFormatsTest {
    public static void main(String[] args) throws Exception {
        ImageNativeFormatsTest test = new ImageNativeFormatsTest();
        if (args.length > 0) {
            assert args.length == 2;
            test.verifyClipboardImage(args[0], Integer.parseInt(args[1]));
        } else {
            test.doTest();
        }
    }

    enum Format {
        TIFF("TIFF", new byte[][]{{77, 77, 0, 42}, {73, 73, 42, 0}}, true, false),
        PNG("PNG", new byte[][]{{(byte) 137, 80, 78, 71, 13, 10, 26, 10}}, true, false),
        JPEG("JFIF", new byte[][]{{(byte)255, (byte)216, (byte)255}}, false, true);

        final String nativeName;
        final byte[][] signatures;
        final boolean supportsAlpha;
        final boolean isLossy;

        private DataFlavor flavor = null;

        Format(String nativeName, byte[][] signatures, boolean supportsAlpha, boolean isLossy) {
            this.nativeName = nativeName;
            this.signatures = signatures;
            this.supportsAlpha = supportsAlpha;
            this.isLossy = isLossy;
        }

        public DataFlavor getFlavor() {
            if (flavor != null) {
                return flavor;
            }

            try {
                flavor = new DataFlavor("application/x-imagenativeformatstest-" + nativeName.toLowerCase() +
                        "; class=java.io.InputStream");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            SystemFlavorMap flavorMap = (SystemFlavorMap) SystemFlavorMap.getDefaultFlavorMap();
            flavorMap.addUnencodedNativeForFlavor(flavor, nativeName);
            flavorMap.addFlavorForUnencodedNative(nativeName, flavor);
            return flavor;
        }

        int getSignatureLength() {
            // both TIFF signatures are of the same length, so it's okay
            return signatures[0].length;
        }

        void checkSignature(byte[] bytes) {
            byte[] signature = Arrays.copyOfRange(bytes, 0, getSignatureLength());

            for (byte[] expected : signatures) {
                if (Arrays.equals(signature, expected)) {
                    return;
                }
            }

            throw new RuntimeException("Image in clipboard not in format " + name() +
                    ", received signature is " + HexFormat.of().formatHex(signature));
        }
    }

    BufferedImage getEagerImage(int imageType) {
        BufferedImage image = new BufferedImage(32, 32, imageType);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 16, 16);
        g.setColor(Color.GREEN);
        g.fillRect(16, 0, 16, 16);
        g.setColor(Color.BLUE);
        g.fillRect(0, 16, 16, 16);
        g.dispose();
        return image;
    }

    boolean areColorsEqualApprox(Color a, Color b) {
        int delta = 10;
        return Math.abs(a.getRed() - b.getRed()) <= delta &&
                Math.abs(a.getGreen() - b.getGreen()) <= delta &&
                Math.abs(a.getBlue() - b.getBlue()) <= delta &&
                Math.abs(a.getAlpha() - b.getAlpha()) <= delta;
    }

    void verifyImageContents(BufferedImage image, int imageType, Format format) {
        int w = image.getWidth();
        int h = image.getHeight();
        if (w != 32 || h != 32) {
            throw new RuntimeException(String.format("Decoded image has wrong size: %dx%d", w, h));
        }

        boolean didHaveAlpha = imageType == BufferedImage.TYPE_INT_ARGB ||
                imageType == BufferedImage.TYPE_INT_ARGB_PRE;

        Point[] points = new Point[]{
                new Point(1, 1),
                new Point(30, 1),
                new Point(1, 30),
                new Point(30, 30)
        };
        Color[] colors = new Color[]{
                Color.RED,
                Color.GREEN,
                Color.BLUE,
                didHaveAlpha ? (format.supportsAlpha ? new Color(0, 0, 0, 0) : Color.WHITE) : Color.BLACK,
        };
        for (int i = 0; i < points.length; ++i) {
            Point point = points[i];
            Color expected = colors[i];
            Color actual = new Color(image.getRGB(point.x, point.y), format.supportsAlpha);
            boolean equals = format.isLossy ? areColorsEqualApprox(expected, actual) : expected.equals(actual);
            if (!equals) {
                throw new RuntimeException(String.format("Wrong color at (%d, %d): expected %s, got %s", point.x, point.y, expected, actual));
            }
        }
    }

    Image getLazyImage(int imageType) {
        // make an image that needs waiting until it is loaded
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(getEagerImage(imageType), "png", bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Toolkit.getDefaultToolkit().createImage(bytes.toByteArray());
    }

    void copyImageToClipboard(Image image) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{DataFlavor.imageFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return Objects.equals(DataFlavor.imageFlavor, flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return image;
            }
        }, null);
    }

    void verifyClipboardImageIsInFormat(Format format, int imageType) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        try (InputStream inputStream = (InputStream) clipboard.getData(format.getFlavor())) {
            byte[] bytes = inputStream.readAllBytes();
            format.checkSignature(bytes);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            verifyImageContents(image, imageType, format);
        } catch (UnsupportedFlavorException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    void verifyClipboardImage(String kind, int imageType) {
        for (Format format : Format.values()) {
            System.out.println("Checking " + kind + " image for format " + format.name());
            verifyClipboardImageIsInFormat(format, imageType);
        }
    }

    void check(String imageTypeStr, int imageType) throws Exception {
        Image[] images = new Image[]{
                getEagerImage(imageType),
                getLazyImage(imageType),
        };
        String[] labels = new String[]{"eager", "lazy"};

        for (int i = 0; i < images.length; ++i) {
            Image image = images[i];
            String label = labels[i];
            String kind = String.format("%s %s", label, imageTypeStr);
            copyImageToClipboard(image);
            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                    ImageNativeFormatsTest.class.getName(), kind, Integer.toString(imageType));
            OutputAnalyzer out = ProcessTools.executeProcess(pb);
            out.shouldHaveExitValue(0);
        }
    }

    void doTest() throws Exception {
        check("ARGB_PRE", BufferedImage.TYPE_INT_ARGB_PRE);
        check("ARGB", BufferedImage.TYPE_INT_ARGB);
        check("RGB", BufferedImage.TYPE_INT_RGB);
    }
}
