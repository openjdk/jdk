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

package compiler.galery;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.util.Random;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.swing.JPanel;
import java.awt.Font;

import java.io.File;
import java.util.Arrays;

/**
 * TODO: desc: backing methods and stand-alone-runnable
 * TODO: link to JTREG / IR test
 * TODO: how to run
 */
public class NormalMapping {
    public static Random RANDOM = new Random();

    public static void main(String[] args) {
        // Create an applicateion state with 5 lights.
        State state = new State(5);

        // Set up a panel we can draw on, and put it in a window.
        MyDrawingPanel panel = new MyDrawingPanel(state);
        JFrame frame = new JFrame("Normal Mapping Demo (Auto Vectorization)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 1000);
        frame.add(panel);
        frame.setVisible(true);

        // Tight loop where we redraw the panel as fast as possible.
        while (true) {
            sleep(1);
            state.update();
            panel.repaint();
        }
    }

    public static void sleep(int t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            throw new RuntimeException("Sleep interrupted.");
        }
    }

    public static BufferedImage loadImage(String path) {
        try {
            return ImageIO.read(new File(path));
        } catch (IOException e) {
            throw new RuntimeException("Could not load: ", e);
        }
    }

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
            dx += RANDOM.nextFloat() * 0.001 - 0.0005;;
            dy += RANDOM.nextFloat() * 0.001 - 0.0005;;
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

    public static class State {
        public Light[] lights;

        public float[] coordsX;
        public float[] coordsY;
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

            int sizeX = 1000;
            int sizeY = 1000;

            // Coordinates
            this.coordsX = new float[sizeX * sizeY];
            this.coordsY = new float[sizeX * sizeY];
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    this.coordsX[y * sizeX + x] = x * (1f / sizeX);
                    this.coordsY[y * sizeX + x] = y * (1f / sizeY);
                }
            }

            // Extract normal values from RGB image
            // The loaded image may not have the desired INT_RGB format, so first convert it
            BufferedImage normalsLoaded = loadImage("normal_map.png");
            BufferedImage normals = new BufferedImage(sizeX, sizeY, BufferedImage.TYPE_INT_RGB);
            normals.getGraphics().drawImage(normalsLoaded, 0, 0, null);
            int[] normalsRGB = ((DataBufferInt) normals.getRaster().getDataBuffer()).getData();
            this.normalsX = new float[sizeX * sizeY];
            this.normalsY = new float[sizeX * sizeY];
            this.normalsZ = new float[sizeX * sizeY];
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    this.coordsY[y * sizeX + x] = y * (1f / sizeY);
                    int normal = normalsRGB[y * sizeX + x];
                    int nr = (normal >> 16) & 0xff;
                    int ng = (normal >>  8) & 0xff;
                    int nb = (normal >>  0) & 0xff;

                    float nx = ((float)nr) * (1f / 128f) - 1f;
                    float ny = ((float)ng) * (1f / 128f) - 1f;
                    float nz = ((float)nb) * (1f / 128f) - 1f;

                    this.normalsX[y * sizeX + x] = -nx;
                    this.normalsY[y * sizeX + x] = ny;
                    this.normalsZ[y * sizeX + x] = nz;
                }
            }

            // Double buffered output images, where we render to
            this.output   = new BufferedImage(sizeX, sizeY, BufferedImage.TYPE_INT_RGB);
            this.output_2 = new BufferedImage(sizeX, sizeY, BufferedImage.TYPE_INT_RGB);
            this.outputRGB   = ((DataBufferInt) output.getRaster().getDataBuffer()).getData();
            this.outputRGB_2 = ((DataBufferInt) output_2.getRaster().getDataBuffer()).getData();

            // Set up the FPS tracker
            lastTime = System.nanoTime();
        }

        public void update() {
            long nowTime = System.nanoTime();
            float newFPS = 1e9f / (nowTime - lastTime);
            fps = 0.99f * fps + 0.01f * newFPS;
            lastTime = nowTime;
            //System.out.println("fps: " + fps + " (lum: " + luminosityCorrection + ")");

            for (int i = 0; i < lights.length; i++) {
                lights[i].update();
            }

            int[] outputArray = ((DataBufferInt) output.getRaster().getDataBuffer()).getData();
            Arrays.fill(outputArray, 0);

            for (Light l : lights) {
                computeLight(l);
            }
            computeLuminosityCorrection();

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

                // Compute distance d
                float dx = x - l.x;
                float dy = y - l.y;
                float dz = 0.2f;
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

                int r = (int)(luminosity * l.r) & 0xff;
                int g = (int)(luminosity * l.g) & 0xff;
                int b = (int)(luminosity * l.b) & 0xff;
                int c = (r << 16) + (g << 8) + b;
                outputRGB[i] += c;
            }
        }

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
            luminosityCorrection *= 230f / maxC;
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
        }
    }
}
