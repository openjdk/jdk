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

package compiler.gallery;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.util.Random;
import javax.swing.JPanel;
import java.awt.Font;

import java.net.URL;
import java.net.URISyntaxException;
import java.io.File;
import java.util.Arrays;

/**
 * I presented this demo at the JVMLS 2025 conference, when giving
 * a talk about Auto-Vectorization in HotSpot, see:
 *   https://inside.java/2025/08/16/jvmls-hotspot-auto-vectorization/
 *
 * This is a stand-alone test that you can run directly with:
 *   java NormalMapping.java
 *
 * If you want to disable the auto-vectorizer, you can run:
 *   java -XX:-UseSuperWord NormalMapping.java
 *
 * On x86, you can also play with the UseAVX flag:
 *   java -XX:UseAVX=1 NormalMapping.java
 *
 * There is a JTREG test that automatically runs this demo,
 * see {@link TestNormalMapping}.
 *
 * My motivation for JVMLS 2025 was to present something that vectorizes
 * in an "embarassingly parallel" way. It should be something that C2's
 * SuperWord Auto Vectorizer could already do for many JDK releases,
 * and also has some visual appeal. I decided to use normal mapping, see:
 *   https://en.wikipedia.org/wiki/Normal_mapping
 *
 * At the conference, I only had the version that loads a normal map
 * from an image. I now also added some "generated" cases, which are
 * created from 2d height functions, and then converted to normal
 * maps. This allows us to show more "surfaces" without having to
 * store the images for all those cases.
 *
 * If you are interested in understanding the components, then look at these:
 * - computeLight: the normal mapping "shader / kernel".
 * - generateNormals / computeNormals: computing normals from height functions.
 * - nextNormals: add you own normal map png or height function.
 * - main: setup and endless-loop that triggers normals to be swapped periodically.
 * - MyDrawingPanel: drawing all the parts to the screen.
 */
public class NormalMapping {
    public static Random RANDOM = new Random();

    // Increasing this number will make the demo slower.
    public static final int NUMBER_OF_LIGHTS = 5;

    public static void main(String[] args) {
        System.out.println("Welcome to the Normal Mapping Demo!");
        State state = new State(NUMBER_OF_LIGHTS);

        // Set up a panel we can draw on, and put it in a window.
        System.out.println("Setting up Window...");
        MyDrawingPanel panel = new MyDrawingPanel(state);
        JFrame frame = new JFrame("Normal Mapping Demo (Auto-Vectorization)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(2000, 1000);
        frame.add(panel);
        frame.setVisible(true);
        System.out.println("Running Demo...");

        try {
            // Tight loop where we redraw the panel as fast as possible.
            int count = 0;
            while (true) {
                Thread.sleep(1);
                state.update();
                panel.repaint();
                if (count++ > 500) {
                    count = 0;
                    state.nextNormals();
                }
            }
        } catch (InterruptedException e) {
            System.out.println("Interrputed, terminating demo.");
        } finally {
            System.out.println("Shut down demo.");
            frame.setVisible(false);
            frame.dispose();
        }
    }

    public static File getLocalFile(String name) {
        // If we are in JTREG IR testing mode, we have to get the path via system property,
        // if it is run in stand-alone that property is not available, and we can load
        // via getResource.
        System.out.println("Loading file: " + name);
        String testSrc = System.getProperty("test.src", null);
        System.out.println("System Property test.src: " + testSrc);
        if (testSrc == null) {
            URL path = NormalMapping.class.getResource(name);
            System.out.println("  Loading via getResource: " + path);
            try {
                return new File(path.toURI());
            } catch (URISyntaxException e) {
                throw new RuntimeException("Could not load: ", e);
            }
        } else {
            return new File(testSrc + "/" + name);
        }
    }

    public static BufferedImage loadImage(File file) {
        try {
            return ImageIO.read(file);
        } catch (IOException e) {
            throw new RuntimeException("Could not load: ", e);
        }
    }

    /**
     * This class represents the lights that are located on the normal map,
     * moved around randomly, and shine their color of light on the scene.
     */
    public static class Light {
        public float x = 0.5f;
        public float y = 0.5f;
        private float dx;
        private float dy;

        private float h;
        public float r;
        public float g;
        public float b;

        Light() {
            this.h = RANDOM.nextFloat();
        }

        // Random movement of the Light
        public void update() {
            // Random acceleration with dampening.
            dx *= 0.99;
            dy *= 0.99;
            dx += RANDOM.nextFloat() * 0.001 - 0.0005;
            dy += RANDOM.nextFloat() * 0.001 - 0.0005;
            x += dx;
            y += dy;

            // Boounce off the walls.
            if (x < 0) { dx = +Math.abs(dx); }
            if (x > 1) { dx = -Math.abs(dx); }
            if (y < 0) { dy = +Math.abs(dy); }
            if (y > 1) { dy = -Math.abs(dy); }

            // Rotate the hue -> gets us nice rainbow colors.
            h += 0.001 + RANDOM.nextFloat() * 0.0002;
            Color c = Color.getHSBColor(h, 1f, 1f);
            r = (1f / 256f) * c.getRed();
            g = (1f / 256f) * c.getGreen();
            b = (1f / 256f) * c.getBlue();
        }
    }

    /**
     * This class manages the state of the demo, including the lights,
     * arrays passed in and out of the normal map computation, as well
     * as the image buffers and FPS tracking.
     */
    public static class State {
        private static final int sizeX = 1000;
        private static final int sizeY = 1000;

        public Light[] lights;

        public float[] coordsX;
        public float[] coordsY;

        private int nextNormalsId = 0;
        public BufferedImage normals;
        public float[] normalsX;
        public float[] normalsY;
        public float[] normalsZ;

        public BufferedImage output;
        public BufferedImage output_2;
        public int[] outputRGB;
        public int[] outputRGB_2;

        public long lastTime;
        public float fps;

        float luminosityCorrection = 1f;

        public State(int numberOfLights) {
            lights = new Light[numberOfLights];
            for (int i = 0; i < lights.length; i++) {
                lights[i] = new Light();
            }

            // Coordinates
            this.coordsX = new float[sizeX * sizeY];
            this.coordsY = new float[sizeX * sizeY];
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    this.coordsX[y * sizeX + x] = x * (1f / sizeX);
                    this.coordsY[y * sizeX + x] = y * (1f / sizeY);
                }
            }

            nextNormals();

            // Double buffered output images, where we render to.
            // Without double buffering, we would get some flickering effects,
            // because we would be concurrently updating the buffer and drawing it.
            this.output   = new BufferedImage(sizeX, sizeY, BufferedImage.TYPE_INT_RGB);
            this.output_2 = new BufferedImage(sizeX, sizeY, BufferedImage.TYPE_INT_RGB);
            this.outputRGB   = ((DataBufferInt) output.getRaster().getDataBuffer()).getData();
            this.outputRGB_2 = ((DataBufferInt) output_2.getRaster().getDataBuffer()).getData();

            // Set up the FPS tracker
            lastTime = System.nanoTime();
        }

        public void nextNormals() {
            switch (nextNormalsId) {
                case 0 -> setNormals(loadNormals("normal_map.png"));
                case 1 -> setNormals(generateNormals("heart"));
                case 2 -> setNormals(generateNormals("hex"));
                case 3 -> setNormals(generateNormals("cone"));
                case 4 -> setNormals(generateNormals("ripple"));
                case 5 -> setNormals(generateNormals("hill"));
                case 6 -> setNormals(generateNormals("ripple2"));
                case 7 -> setNormals(generateNormals("cones"));
                case 8 -> setNormals(generateNormals("spheres"));
                case 9 -> setNormals(generateNormals("donut"));
                default -> throw new RuntimeException();
            }
            nextNormalsId = (nextNormalsId + 1) % 10;
        }

        public BufferedImage loadNormals(String name) {
            // Extract normal values from RGB image
            // The loaded image may not have the desired INT_RGB format, so first convert it
            BufferedImage normalsLoaded = loadImage(getLocalFile(name));
            BufferedImage buf = new BufferedImage(sizeX, sizeY, BufferedImage.TYPE_INT_RGB);
            buf.getGraphics().drawImage(normalsLoaded, 0, 0, null);
            return buf;
        }

        public void setNormals(BufferedImage buf) {
            this.normals = buf;

            int[] normalsRGB = ((DataBufferInt) this.normals.getRaster().getDataBuffer()).getData();
            this.normalsX = new float[sizeX * sizeY];
            this.normalsY = new float[sizeX * sizeY];
            this.normalsZ = new float[sizeX * sizeY];
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    this.coordsY[y * sizeX + x] = y * (1f / sizeY);
                    int normal = normalsRGB[y * sizeX + x];
                    // RGB values in range [0 ... 255]
                    int nr = (normal >> 16) & 0xff;
                    int ng = (normal >>  8) & 0xff;
                    int nb = (normal >>  0) & 0xff;

                    // Map range [0..255] -> [-1 .. 1]
                    float nx = ((float)nr) * (1f / 128f) - 1f;
                    float ny = ((float)ng) * (1f / 128f) - 1f;
                    float nz = ((float)nb) * (1f / 128f) - 1f;

                    this.normalsX[y * sizeX + x] = -nx;
                    this.normalsY[y * sizeX + x] = ny;
                    this.normalsZ[y * sizeX + x] = nz;
                }
            }
        }

        interface HeightFunction {
            // x and y should be in [0..1]
            double call(double x, double y);
        }

        public BufferedImage generateNormals(String name) {
            System.out.println("  generate normals for: " + name);
            return computeNormals((double x, double y) -> {
                // Scale out, so we see a little more
                x = 10 * (x - 0.5);
                y = 10 * (y - 0.5);

                // A selection of "height functions":
                return switch (name) {
                    case "cone" -> 0.1 * Math.max(0, 2 - Math.sqrt(x * x + y * y));
                    case "heart" -> {
                        double heart = Math.abs(Math.pow(x * x + y * y - 1, 3) - x * x * Math.pow(-y, 3));
                        double decay = Math.exp(-(x * x + y * y));
                        yield 0.1 * heart * decay;
                    }
                    case "hill" ->    0.5 * Math.exp(-(x * x + y * y));
                    case "ripple" ->  0.01 * Math.sin(x * x + y * y);
                    case "ripple2" -> 0.3 * Math.sin(x) * Math.sin(y);
                    case "donut" -> {
                        double d = Math.sqrt(x * x + y * y) - 2;
                        double i = 1 - d*d;
                        yield (i >= 0) ? 0.1 * Math.sqrt(i) : 0;
                    }
                    case "hex" -> {
                        double f = 3.0;
                        double a = Math.cos(f * x);
                        double b = Math.cos(f * (-0.5 * x + Math.sqrt(3) / 2.0 * y));
                        double c = Math.cos(f * (-0.5 * x - Math.sqrt(3) / 2.0 * y));
                        yield 0.03 * (a + b + c);
                    }
                    case "cones" -> {
                        double scale = 2.0;
                        double r = 0.8;
                        double cx = scale * (Math.floor(x / scale) + 0.5);
                        double cy = scale * (Math.floor(y / scale) + 0.5);
                        double dx = x - cx;
                        double dy = y - cy;
                        double d = Math.sqrt(dx * dx + dy * dy);
                        yield 0.1 * Math.max(0, 0.8 - d);
                    }
                    case "spheres" -> {
                        double scale = 2.0;
                        double r = 0.8;
                        double cx = scale * (Math.floor(x / scale) + 0.5);
                        double cy = scale * (Math.floor(y / scale) + 0.5);
                        double dx = x - cx;
                        double dy = y - cy;
                        double d2 = dx * dx + dy * dy;
                        if (d2 <= r * r) {
                            yield 0.03 * Math.sqrt(r * r - d2);
                        }
                        yield 0.0;
                    }
                    default -> throw new RuntimeException("not supported: " + name);
                };
            });
        }

        public static BufferedImage computeNormals(HeightFunction fun) {
            BufferedImage out = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
            int[] arr = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
            int sx = out.getWidth();
            int sy = out.getHeight();

            double delta = 0.00001;
            double dxx = 1.0 / sx;
            double dyy = 1.0 / sy;
            for (int yy = 0; yy < sy; yy++) {
                int nStart = sy * yy;
                for (int xx = 0; xx < sx; xx++) {
                    double x = xx * dxx;
                    double y = yy * dyy;

                    // Compute the partial derivatives in x and y direction;
                    double fdx = fun.call(x + delta, y) - fun.call(x - delta, y);
                    double fdy = fun.call(x, y + delta) - fun.call(x, y - delta);
                    // We can compute the normal from the cross product of:
                    //
                    //  df/dx  x  df/dy = [2*delta, 0, fdx]  x  [0, 2*delta, fdy]
                    //                  = [0*fdy - fdx*2*delta, fdx*0 - 2*delta*fdy, 2*delta*2*delta - 0*0]
                    double nx = -fdx * 2 * delta;
                    double ny = -2 * delta * fdy;
                    double nz = 2 * delta * 2 * delta;

                    // normalize
                    float dist = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
                    nx /= dist;
                    ny /= dist;
                    nz /= dist;

                    // Now transform [-1..1] -> [0..255]
                    int r = (int)(nx * 127f + 127f) & 0xff;
                    int g = (int)(ny * 127f + 127f) & 0xff;
                    int b = (int)(nz * 127f + 127f) & 0xff;
                    int c = (r << 16) + (g << 8) + b;
                    arr[nStart + xx] = c;
                }
            }
            return out;
        }

        public void update() {
            long nowTime = System.nanoTime();
            float newFPS = 1e9f / (nowTime - lastTime);
            fps = 0.99f * fps + 0.01f * newFPS;
            lastTime = nowTime;

            for (Light light : lights) {
                light.update();
            }

            // Reset the buffer
            int[] outputArray = ((DataBufferInt) output.getRaster().getDataBuffer()).getData();
            Arrays.fill(outputArray, 0);

            // Add in the contribution of each light
            for (Light l : lights) {
                computeLight(l);
            }
            computeLuminosityCorrection();

            // Swap the buffers for double buffering.
            var outputTmp = output;
            output = output_2;
            output_2 = outputTmp;

            var outputRGBTmp = outputRGB;
            outputRGB = outputRGB_2;
            outputRGB_2 = outputRGBTmp;
        }

        public void computeLight(Light l) {
            for (int i = 0; i < outputRGB.length; i++) {
                float x = coordsX[i];
                float y = coordsY[i];
                float nx = normalsX[i];
                float ny = normalsY[i];
                float nz = normalsZ[i];

                // Compute distance vector between the light and the pixel
                float dx = x - l.x;
                float dy = y - l.y;
                float dz = 0.2f; // how much the lights float above the scene

                // Compute the distance (dot product of d with itself)
                float d2 = dx * dx + dy * dy + dz * dz;
                float d = (float)Math.sqrt(d2);
                float d3 = d * d2;

                // Compute dot-product between distance and normal vector
                float dotProduct = nx * dx + ny * dy + nz * dz;

                // If the dot-product is negative:
                //   Light on wrong side -> 0
                // If the dot-product is positive:
                //   There should be light normalize by distance (d), and divide by the
                //   squared distance (d2) to have physically accurately decaying light.
                //   Correct the luminosity so the RGB values are going to be close
                //   to 255, but not over.
                float luminosity = Math.max(0, dotProduct / d3) * luminosityCorrection;

                // Now we compute the color values that hopefully end up in the range
                // [0..255]. If the hack/trick with luminosityCorrection fails, we may
                // occasionally go out of the range and generate an overflow in the masking.
                // This can lead to some funky visual artifacts around the lights, but it
                // is quite rare.
                //
                // Feel free to play with the targetExposure below, and see if you can
                // observe the artefacts.
                int r = (int)(luminosity * l.r) & 0xff;
                int g = (int)(luminosity * l.g) & 0xff;
                int b = (int)(luminosity * l.b) & 0xff;
                int c = (r << 16) + (g << 8) + b;
                outputRGB[i] += c;
            }
        }

        // This is a bit of a horrible hack, but it mostly works.
        // Essentially, it tries to solve the "exposure" problem:
        // It is hard to know how much light a pixel will receive at most, and
        // we have to convert this value to a byte [0..255] at some point.
        // If we chose the "exposure" too low, we get a very dark picture
        // that is not very exciting to look at. If we over-expose, then we
        // may overflow/clip the range [0..255], leading to unpleasant visual
        // artifacts.
        public void computeLuminosityCorrection() {
            // Find maximum R, G, and B value.
            float maxR = 0;
            float maxG = 0;
            float maxB = 0;
            for (int i = 0; i < outputRGB.length; i++) {
                int c = outputRGB[i];
                int cr = (c >> 16) & 0xff;
                int cg = (c >>  8) & 0xff;
                int cb = (c >>  0) & 0xff;

                maxR = Math.max(maxR, cr);
                maxG = Math.max(maxG, cg);
                maxB = Math.max(maxB, cb);
            }

            float maxC = Math.max(Math.max(maxR, maxG), maxB);

            // Correct the maximum value to be 230, so we are safely in range 0..255
            // Setting it instead to 255 will make the image brighter, but most likely
            // it will give you some funky artefacts.
            // Setting it to 100 will make the image darker.
            float targetExposure = 230f;
            luminosityCorrection *= targetExposure / maxC;
        }
    }

    public static class MyDrawingPanel extends JPanel {
        private final State state;

        public MyDrawingPanel(State state) {
            this.state = state;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Draw color output
            g2d.drawImage(state.output_2, 0, 0, null);

            // Draw position of lights
            for (Light l : state.lights) {
                g2d.setColor(new Color(l.r, l.g, l.b));
                g2d.fillRect((int)(1000f * l.x) - 3, (int)(1000f * l.y) - 3, 6, 6);
            }

            g2d.setColor(new Color(0, 0, 0));
            g2d.fillRect(0, 0, 150, 35);
            g2d.setColor(new Color(255, 255, 255));
            g2d.setFont(new Font("Consolas", Font.PLAIN, 30));
            g2d.drawString("FPS: " + (int)Math.floor(state.fps), 0, 30);

            g2d.drawImage(state.normals, 1000, 0, null);
        }
    }
}
