/*
 * Copyright 2021 JetBrains s.r.o.
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
 * @summary Checks that emoji rendered via glyph cache and bypassing it look similar.
 */

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.stream.Stream;

import static java.awt.RenderingHints.KEY_TEXT_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON;

public class OutlineTextRendererEmoji {
    private static final int IMG_WIDTH = 84;
    private static final int IMG_HEIGHT = 84;
    private static final int EMOJI_X = 0;
    private static final int EMOJI_Y = 70;
    private static final int FONT_SIZE = 70;
    private static final String EMOJI = "\ud83d\udd25"; // Fire

    private static final int WINDOW_SIZE = 12; // In pixels
    private static final double THRESHOLD = 0.98;

    public static void main(String[] args) throws Exception {
        requireFont("Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji");

        BufferedImage small = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage rescaled = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage big = new BufferedImage(IMG_WIDTH*2, IMG_HEIGHT*2, BufferedImage.TYPE_INT_RGB);
        drawEmoji(small, EMOJI_X, EMOJI_Y, FONT_SIZE);
        drawEmoji(big, EMOJI_X*2, EMOJI_Y*2, FONT_SIZE*2);
        checkEmoji(small, big, rescaled);
    }

    private static void drawEmoji(Image img, int x, int y, int size) {
        Graphics2D g = (Graphics2D) img.getGraphics();
        g.setColor(Color.white);
        g.fillRect(0, 0, img.getWidth(null), img.getHeight(null));
        g.setFont(new Font(Font.DIALOG, Font.PLAIN, size));
        g.setRenderingHint(KEY_TEXT_ANTIALIASING, VALUE_TEXT_ANTIALIAS_ON);
        g.drawString(EMOJI, x, y);
        g.dispose();
    }

    private static void checkEmoji(BufferedImage small, BufferedImage big, BufferedImage rescaled) throws Exception {
        Graphics2D g2d = rescaled.createGraphics();
        g2d.drawImage(big.getScaledInstance(small.getWidth(), small.getHeight(), Image.SCALE_SMOOTH), 0, 0, null);
        g2d.dispose();

        double ssim = SSIM.calculate(small, rescaled, WINDOW_SIZE);
        System.out.println("SSIM is " + ssim);

        if (ssim < THRESHOLD) {
            ImageIO.write(small, "PNG", new File("OutlineTextRendererEmoji-small.png"));
            ImageIO.write(big, "PNG", new File("OutlineTextRendererEmoji-big.png"));
            ImageIO.write(rescaled, "PNG", new File("OutlineTextRendererEmoji-rescaled.png"));
            throw new Exception("Images mismatch: " + ssim);
        }
    }

    private static class SSIM {
        private static double calculate(BufferedImage a, BufferedImage b, int windowSize) {
            if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
                throw new IllegalArgumentException("Images must have same size");
            }
            if (a.getWidth() % windowSize != 0 || a.getHeight() % windowSize != 0) {
                throw new IllegalArgumentException("Image sizes must be multiple of windowSize");
            }

            final double K1 = 0.01, K2 = 0.03;
            final double L = 255; // dynamic range per component (2^8 - 1)
            final double c1 = Math.pow(L * K1, 2);
            final double c2 = Math.pow(L * K2, 2);

            double result = 0, alpha = 0;
            int windows = 0;
            for (int y = 0; y <= a.getHeight() - windowSize; y++) {
                for (int x = 0; x <= a.getWidth() - windowSize; x++) {

                    // Calculate averages
                    double[] avgA = vec(), avgB = vec();
                    for (int py = 0; py < windowSize; py++) {
                        for (int px = 0; px < windowSize; px++) {
                            avgA = add(avgA, vec(a.getRGB(x + px, y + py)));
                            avgB = add(avgB, vec(b.getRGB(x + px, y + py)));
                        }
                    }
                    avgA = div(avgA, windowSize * windowSize);
                    avgB = div(avgB, windowSize * windowSize);

                    // Calculate variance and covariance
                    double[] varA = vec(), varB = vec(), cov = vec();
                    for (int py = 0; py < windowSize; py++) {
                        for (int px = 0; px < windowSize; px++) {
                            double[] da = sub(avgA, vec(a.getRGB(x + px, y + py)));
                            double[] db = sub(avgB, vec(b.getRGB(x + px, y + py)));
                            varA = add(varA, mul(da, da));
                            varB = add(varB, mul(db, db));
                            cov = add(cov, mul(da, db));
                        }
                    }
                    varA = div(varA, windowSize * windowSize);
                    varB = div(varB, windowSize * windowSize);
                    cov = div(cov, windowSize * windowSize);

                    // Calculate ssim
                    double[] ssim = vec();
                    for (int i = 0; i < 4; i++) {
                        ssim[i] = (
                                (2 * avgA[i] * avgB[i] + c1) * (2 * cov[i] + c2)
                        ) / (
                                (avgA[i]*avgA[i] + avgB[i]*avgB[i] + c1) * (varA[i] + varB[i] + c2)
                        );
                    }

                    result += ssim[0] + ssim[1] + ssim[2];
                    alpha += ssim[3];
                    windows++;
                }
            }
            if (alpha == windows) result /= 3.0;
            else result = (result + alpha) / 4.0;
            return result / (double) windows;
        }

        private static double[] vec(double... v) {
            if (v.length == 0) return new double[4];
            else if (v.length == 1) return new double[] {v[0],v[0],v[0],v[0]};
            else return v;
        }
        private static double[] vec(int color) {
            return vec(color & 0xff, (color >> 8) & 0xff, (color >> 16) & 0xff, (color >> 24) & 0xff);
        }

        interface Op {  double apply(double a, double b); }
        private static double[] apply(Op op, double[] a, double... b) {
            b = vec(b);
            double[] r = new double[4];
            for (int i = 0; i < 4; i++) r[i] = op.apply(a[i], b[i]);
            return r;
        }

        private static double[] add(double[] a, double... b) { return apply((i, j) -> i + j, a, b); }
        private static double[] sub(double[] a, double... b) { return apply((i, j) -> i - j, a, b); }
        private static double[] mul(double[] a, double... b) { return apply((i, j) -> i * j, a, b); }
        private static double[] div(double[] a, double... b) { return apply((i, j) -> i / j, a, b); }
    }

    private static void requireFont(String macOS, String windows, String linux) {
        String os = System.getProperty("os.name").toLowerCase();
        String font;
        if (os.contains("mac")) font = macOS;
        else if (os.contains("windows")) font = windows;
        else if (os.contains("linux")) font = linux;
        else return;
        String[] fs = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        if (Stream.of(fs).noneMatch(s -> s.equals(font))) {
            throw new Error("Required font not found: " + font);
        }
    }
}
