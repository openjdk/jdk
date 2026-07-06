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

package compiler.gallery;

import java.util.Random;

import jdk.incubator.vector.*;

import javax.swing.*;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Font;
import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

/**
 * This is a visual demo of the Vector API, presenting an N-Body simulation,
 * where every body (particle) interacts with every other body.
 *
 * This is a stand-alone test that you can run directly with:
 *   java --add-modules=jdk.incubator.vector ParticleLife.java
 *
 * On x86, you can also play with the UseAVX flag:
 *   java --add-modules=jdk.incubator.vector -XX:UseAVX=2 ParticleLife.java
 *
 * There is a JTREG test that automatically runs this demo,
 * see {@link TestParticleLife}.
 *
 * The motivation for this demo is to present a realistic computation,
 * such as a physics simulation, but which is currently not auto
 * vectorized. It is thus a good candidate for the use of the Vector API.
 * This demo is based on the work of Tom Mohr and others before him:
 *   https://particle-life.com/
 *   https://www.youtube.com/@tom-mohr
 *
 * If you are interested in understanding the components, then look at these:
 * - State.update: one step in the simulation. This consists of two parts:
 *   - updateForce*: This computes the forces between all the particles, which affects the velocities.
 *                   We have multiple implementations (scalar and Vector API).
 *                   This is the most expensive part of the simulation.
 *   - updatePositions: The velocities are added to the position.
 */
public class ParticleLife {
    public static final Random RANDOM = new Random(123);

    // Increasing this number will make the demo slower.
    public static int NUMBER_OF_PARTICLES = 2560;
    public static int NUMBER_OF_GROUPS = 50;

    public static float ZOOM = 1500f;

    public static float SCALE1 = 0.02f;
    public static float SCALE2 = 0.04f;
    public static float SCALE3 = 1f;
    public static float FORCE_PARTICLE = 0.0001f;
    public static float FORCE_ORIGIN = 0.05f;

    // Dampening factor, applied to the velocity.
    // 0: no velocity carried to next update, particles have no momentum
    // 1: no dampening, can lead to increase in energy in the system over time.
    public static float DAMPENING = 0.3f;

    // Time step size of each update. Larger makes the simulation faster.
    // But if it is too large, this can lead to numerical instability.
    public static float DT = 1f;

    enum Implementation {
        Scalar, VectorAPI_Inner_Gather, VectorAPI_Inner_Rearranged, VectorAPI_Outer
    }

    public static Implementation IMPLEMENTATION = Implementation.Scalar;

    enum PoleGen {
        Default, Random, Rainbow, Sparse
    }

    public static PoleGen POLE_GEN = PoleGen.Default;

    private static final VectorSpecies<Float> SPECIES_F = FloatVector.SPECIES_PREFERRED;

    public static State STATE = new State();

    static void main() {
        System.out.println("Welcome to the Particle Life Demo!");

        // Set up a panel we can draw on, and put it in a window.
        JFrame frame = new JFrame("Particle Life Demo (VectorAPI)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1400, 1000);
        frame.setResizable(false);
        frame.setLayout(new BorderLayout());

        ParticlePanel panel = new ParticlePanel();
        panel.setPreferredSize(new Dimension(1000, 0));

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(null);
        controlPanel.setPreferredSize(new Dimension(400, 0));

        int y = 10;
        // ---------------------------- Reset Button -------------------------
        JButton button = new JButton("Reset");
        button.setBounds(10, y, 120, 30);
        button.setToolTipText("Reset state, with new numbers of particles and groups, and new poles.");
        controlPanel.add(button);

        button.addActionListener(_ -> { STATE = new State(); });
        y += 40;

        // ---------------------------- Computation Selector -------------------------
        {
            JLabel label = new JLabel("Implementation");
            label.setBounds(10, y, 150, 30);
            controlPanel.add(label);

            String[] options = {"Scalar", "VectorAPI Inner Gather", "VectorAPI Inner Rearranged", "VectorAPI Outer"};
            JComboBox<String> comboBox = new JComboBox<>(options);
            comboBox.setBounds(160, y, 210, 30);
            comboBox.setToolTipText("Choose the implementation of the force computation. Using the VectorAPI should be faster.");
            controlPanel.add(comboBox);

            comboBox.addActionListener(_ -> {
                String selected = (String) comboBox.getSelectedItem();
                switch (selected) {
                    case "Scalar"                     -> IMPLEMENTATION = Implementation.Scalar;
                    case "VectorAPI Inner Gather"     -> IMPLEMENTATION = Implementation.VectorAPI_Inner_Gather;
                    case "VectorAPI Inner Rearranged" -> IMPLEMENTATION = Implementation.VectorAPI_Inner_Rearranged;
                    case "VectorAPI Outer"            -> IMPLEMENTATION = Implementation.VectorAPI_Outer;
                }
            });
        }
        y += 40;

        // ---------------------------- Zoom Slider -------------------------
        JLabel zoomLabel = new JLabel("Zoom");
        zoomLabel.setBounds(10, y, 80, 30);
        controlPanel.add(zoomLabel);

        JSlider zoomSlider = new JSlider(JSlider.HORIZONTAL, 10, 2500, (int)ZOOM);
        zoomSlider.setBounds(160, y, 200, 30);
        zoomSlider.setMajorTickSpacing(100);
        zoomSlider.setPaintTicks(false);
        zoomSlider.setPaintLabels(false);
        controlPanel.add(zoomSlider);

        zoomSlider.addChangeListener(_ -> {
            ZOOM = zoomSlider.getValue();
        });
        zoomSlider.setValue((int)ZOOM);
        y += 40;

        // ---------------------------- :Particles Slider -------------------------
        JLabel particlesLabel = new JLabel("Particles");
        particlesLabel.setBounds(10, y, 150, 30);
        controlPanel.add(particlesLabel);

        JSlider particlesSlider = new JSlider(JSlider.HORIZONTAL, 64, 10000, 64);
        particlesSlider.setBounds(160, y, 200, 30);
        particlesSlider.setMajorTickSpacing(100);
        particlesSlider.setPaintTicks(false);
        particlesSlider.setPaintLabels(false);
        particlesSlider.setToolTipText("More particles make the simulation slower. Only applied on Reset.");
        controlPanel.add(particlesSlider);

        particlesSlider.addChangeListener(_ -> {
            NUMBER_OF_PARTICLES = particlesSlider.getValue() / 64 * 64;
            particlesLabel.setText("Particles = " + NUMBER_OF_PARTICLES);
        });
        particlesSlider.setValue(NUMBER_OF_PARTICLES);
        y += 40;

        // ---------------------------- Groups Slider -------------------------
        JLabel groupsLabel = new JLabel("Groups");
        groupsLabel.setBounds(10, y, 150, 30);
        controlPanel.add(groupsLabel);

        JSlider groupsSlider = new JSlider(JSlider.HORIZONTAL, 1, 100, 1);
        groupsSlider.setBounds(160, y, 200, 30);
        groupsSlider.setMajorTickSpacing(100);
        groupsSlider.setPaintTicks(false);
        groupsSlider.setPaintLabels(false);
        groupsSlider.setToolTipText("More groups lead to more complex behavior. Only applied on Reset.");
        controlPanel.add(groupsSlider);

        groupsSlider.addChangeListener(_ -> {
            NUMBER_OF_GROUPS = groupsSlider.getValue();
            groupsLabel.setText("Groups = " + NUMBER_OF_GROUPS);
        });
        groupsSlider.setValue(NUMBER_OF_GROUPS);
        y += 40;

        // ---------------------------- Pole Gen Selector -------------------------
        {
            JLabel label = new JLabel("Poles");
            label.setBounds(10, y, 150, 30);
            controlPanel.add(label);

            String[] options = {"Default", "Random", "Rainbow", "Sparse"};
            JComboBox<String> comboBox = new JComboBox<>(options);
            comboBox.setBounds(160, y, 210, 30);
            comboBox.setToolTipText("Poles define attraction/repulsion between groups. Only applied on Reset.");
            controlPanel.add(comboBox);

            comboBox.addActionListener(_ -> {
                String selected = (String) comboBox.getSelectedItem();
                switch (selected) {
                    case "Default" -> POLE_GEN = PoleGen.Default;
                    case "Random"  -> POLE_GEN = PoleGen.Random;
                    case "Rainbow" -> POLE_GEN = PoleGen.Rainbow;
                    case "Sparse"  -> POLE_GEN = PoleGen.Sparse;
                }
            });
        }
        y += 40;

        // ---------------------------- Scale1 Slider -------------------------
        JLabel scale1Label = new JLabel("scale1");
        scale1Label.setBounds(10, y, 150, 30);
        controlPanel.add(scale1Label);

        JSlider scale1Slider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        scale1Slider.setBounds(160, y, 200, 30);
        scale1Slider.setMajorTickSpacing(100);
        scale1Slider.setPaintTicks(false);
        scale1Slider.setPaintLabels(false);
        scale1Slider.setToolTipText("Defines (inner) radius: repulsion between all particles.");
        controlPanel.add(scale1Slider);

        scale1Slider.addChangeListener(_ -> {
            SCALE1 = scale1Slider.getValue() * 0.002f + 0.001f;
            scale1Label.setText("scale1 = " + String.format("%.4f", SCALE1));
        });
        scale1Slider.setValue(10);
        y += 40;

        // ---------------------------- Scale2 Slider -------------------------
        JLabel scale2Label = new JLabel("scale2");
        scale2Label.setBounds(10, y, 150, 30);
        controlPanel.add(scale2Label);

        JSlider scale2Slider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        scale2Slider.setBounds(160, y, 200, 30);
        scale2Slider.setMajorTickSpacing(100);
        scale2Slider.setPaintTicks(false);
        scale2Slider.setPaintLabels(false);
        scale2Slider.setToolTipText("Defines (outer) radius: attraction/repulsion depending on poles/groups.");
        controlPanel.add(scale2Slider);

        scale2Slider.addChangeListener(_ -> {
            SCALE2 = scale2Slider.getValue() * 0.002f + 0.001f;
            scale2Label.setText("scale2 = " + String.format("%.4f", SCALE2));
        });
        scale2Slider.setValue(20);
        y += 40;

        // ---------------------------- Scale3 Slider -------------------------
        JLabel scale3Label = new JLabel("scale3");
        scale3Label.setBounds(10, y, 150, 30);
        controlPanel.add(scale3Label);

        JSlider scale3Slider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        scale3Slider.setBounds(160, y, 200, 30);
        scale3Slider.setMajorTickSpacing(101);
        scale3Slider.setPaintTicks(false);
        scale3Slider.setPaintLabels(false);
        scale3Slider.setToolTipText("Poles factor: adjust attraction/repulsion strenght.");
        controlPanel.add(scale3Slider);

        scale3Slider.addChangeListener(_ -> {
            SCALE3 = scale3Slider.getValue() * 0.02f;
            scale3Label.setText("scale3 = " + String.format("%.4f", SCALE3));
        });
        scale3Slider.setValue(50);
        y += 40;

        // ---------------------------- FORCE_PARTICLE Slider -------------------------
        JLabel forceParticlesLabel = new JLabel("fParticles");
        forceParticlesLabel.setBounds(10, y, 150, 30);
        controlPanel.add(forceParticlesLabel);

        JSlider forceParticlesSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        forceParticlesSlider.setBounds(160, y, 200, 30);
        forceParticlesSlider.setMajorTickSpacing(100);
        forceParticlesSlider.setPaintTicks(false);
        forceParticlesSlider.setPaintLabels(false);
        forceParticlesSlider.setToolTipText("Particles force factor: adjust force strength between particles.");
        controlPanel.add(forceParticlesSlider);

        forceParticlesSlider.addChangeListener(_ -> {
            FORCE_PARTICLE = forceParticlesSlider.getValue() * 0.00001f;
            forceParticlesLabel.setText("fParticles = " + String.format("%.5f", FORCE_PARTICLE));
        });
        forceParticlesSlider.setValue(10);
        y += 40;

        // ---------------------------- FORCE_ORIGIN Slider -------------------------
        JLabel forceOriginLabel = new JLabel("fOrigin");
        forceOriginLabel.setBounds(10, y, 150, 30);
        controlPanel.add(forceOriginLabel);

        JSlider forceOriginSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        forceOriginSlider.setBounds(160, y, 200, 30);
        forceOriginSlider.setMajorTickSpacing(100);
        forceOriginSlider.setPaintTicks(false);
        forceOriginSlider.setPaintLabels(false);
        forceOriginSlider.setToolTipText("Origin force factor: adjust force attracting all particles to the center/origin.");
        controlPanel.add(forceOriginSlider);

        forceOriginSlider.addChangeListener(_ -> {
            FORCE_ORIGIN = forceOriginSlider.getValue() * 0.0005f;
            forceOriginLabel.setText("fOrigin = " + String.format("%.5f", FORCE_ORIGIN));
        });
        forceOriginSlider.setValue(50);
        y += 40;

        // ---------------------------- DAMPENING Slider -------------------------
        JLabel dampeningLabel = new JLabel("dampening");
        dampeningLabel.setBounds(10, y, 150, 30);
        controlPanel.add(dampeningLabel);

        JSlider dampeningSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        dampeningSlider.setBounds(160, y, 200, 30);
        dampeningSlider.setMajorTickSpacing(100);
        dampeningSlider.setPaintTicks(false);
        dampeningSlider.setPaintLabels(false);
        dampeningSlider.setToolTipText("Dampening removes energy from the system over time. 1 = no dampening.");
        controlPanel.add(dampeningSlider);

        dampeningSlider.addChangeListener(_ -> {
            DAMPENING = dampeningSlider.getValue() * 0.01f;
            dampeningLabel.setText("dampening = " + String.format("%.5f", DAMPENING));
        });
        dampeningSlider.setValue(30);
        y += 40;

        // ---------------------------- DT Slider -------------------------
        JLabel dtLabel = new JLabel("dt");
        dtLabel.setBounds(10, y, 150, 30);
        controlPanel.add(dtLabel);

        JSlider dtSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        dtSlider.setBounds(160, y, 200, 30);
        dtSlider.setMajorTickSpacing(100);
        dtSlider.setPaintTicks(false);
        dtSlider.setPaintLabels(false);
        dtSlider.setToolTipText("Time delta between simulation steps. Small values lead to slow simulation, large values can lead to simulation instability.");
        controlPanel.add(dtSlider);

        dtSlider.addChangeListener(_ -> {
            DT = dtSlider.getValue() * 0.04f + 0.001f;
            dtLabel.setText("dt = " + String.format("%.3f", DT));
        });
        dtSlider.setValue(25);
        y += 40;

        // ---------------------------- Force Panel -------------------------
        ForcePanel forcePanel = new ForcePanel();
        forcePanel.setBounds(10, y, 350, 350);
        forcePanel.setToolTipText("Displays the attraction/repulsion between the groups. Only updated on Reset.");
        controlPanel.add(forcePanel);
        y += 360;

        frame.add(panel, BorderLayout.WEST);
        frame.add(controlPanel, BorderLayout.CENTER);
        frame.setVisible(true);

        System.out.println("Running Demo...");
        try {
            // Tight loop where we redraw the panel as fast as possible.
            while (true) {
                Thread.sleep(1);
                STATE.update();
                panel.repaint();
                forcePanel.repaint();
            }
        } catch (InterruptedException e) {
            System.out.println("Interrputed, terminating demo.");
        } finally {
            System.out.println("Shut down demo.");
            frame.setVisible(false);
            frame.dispose();
        }
    }

    /**
     * State of the simulation.
     */
    public static class State {
        public long lastTime;
        public float fps;

        // "struct of arrays" approach allows adjacent vector loads.
        public float[] x;
        public float[] y;
        public float[] vx;
        public float[] vy;
        public int[] group; // group index of the particle

        public Color[] colors; // color of the group

        // Matrix of the poles: defines attraction/repulsion between groups i and j
        public float[][] poles;
        public float[][] polesT; // transpose of poles
        public float[] polesScratch;

        public State() {
            int n = NUMBER_OF_PARTICLES;
            int g = NUMBER_OF_GROUPS;
            x = new float[n];
            y = new float[n];
            vx = new float[n];
            vy = new float[n];
            group = new int[n];

            for (int i = 0; i < n; i++) {
                x[i] = 0.2f * (RANDOM.nextFloat() - 0.5f);
                y[i] = 0.2f * (RANDOM.nextFloat() - 0.5f);
                group[i] = RANDOM.nextInt(g);
            }

            colors = new Color[g];
            for (int i = 0; i < g; i++) {
                float h = i / (float)g;
                colors[i] = Color.getHSBColor(h, 1f, 1f);
            }

            poles = new float[g][g];
            polesT = new float[g][g];
            polesScratch = new float[n];
            for (int i = 0; i < g; i++) {
                for (int j = 0; j < g; j++) {
                    poles[i][j] = poleGen(i, j, g);
                    polesT[j][i] = poles[i][j];
                }
            }

            // Set up the FPS tracker
            lastTime = System.nanoTime();
        }

        public static float poleGen(int i, int j, int g) {
            int offset = (i - j + g) % g;
            return switch (POLE_GEN) {
                case PoleGen.Default -> (i == j) ? -1f : RANDOM.nextFloat() * 2f - 1f;
                case PoleGen.Random  -> RANDOM.nextFloat() * 2f - 1f;
                case PoleGen.Rainbow -> (i == j) ? -1f : ((offset == 1) ? -0.5f : 0f);
                case PoleGen.Sparse  -> (i == j) ? -1f : (RANDOM.nextInt(g) <= 2 ? -0.5f : 0.3f);
            };
        }

        public void update() {
            long nowTime = System.nanoTime();
            float newFPS = 1e9f / (nowTime - lastTime);
            fps = 0.9f * fps + 0.1f * newFPS;
            lastTime = nowTime;

            switch (IMPLEMENTATION) {
                case Implementation.Scalar                     -> updateForcesScalar();
                case Implementation.VectorAPI_Inner_Gather     -> updateForcesVectorAPI_Inner_Gather();
                case Implementation.VectorAPI_Inner_Rearranged -> updateForcesVectorAPI_Inner_Rearranged();
                case Implementation.VectorAPI_Outer            -> updateForcesVectorAPI_Outer();
                default -> throw new RuntimeException("not implemented");
            }

            updatePositions();
        }

        public void updateForcesScalar() {
            for (int i = 0; i < x.length; i++) {
                float pix = x[i];
                float piy = y[i];
                float pivx = vx[i];
                float pivy = vy[i];
                for (int j = 0; j < x.length; j++) {
                    float pjx = x[j];
                    float pjy = y[j];

                    float dx = pix - pjx;
                    float dy = piy - pjy;
                    float d = (float)Math.sqrt(dx * dx + dy * dy);

                    // Ignoring d=0 avoids division by zero.
                    // This would happen for i==j which we want to exclude anyway,
                    // of if two particles have identical position.
                    if (d > 0f) {
                        float pole = poles[group[i]][group[j]];
                        // If the distance is very large, the force is zero.
                        float f = 0;
                        if (d < SCALE1) {
                            // Small distance: repell all particles
                            f = (SCALE1 - d) / SCALE1;
                        } else if (d < SCALE1 + SCALE2) {
                            // Medium distance: attract/repell according to pole
                            f = (d - SCALE1) / SCALE2 * pole * SCALE3;
                        } else if (d < SCALE1 + 2f * SCALE2) {
                            // Medium distance: attract/repell according to pole
                            f = ((SCALE1 + 2f * SCALE2) - d) / SCALE2 * pole * SCALE3;
                        }
                        // The force is adjustable by the user via FORCE_PARTICLE.
                        // Additionally we need to respect the DT factor of the simulation
                        // time step. Finally, we need to normalize dx and dy by dividing
                        // by d.
                        f *= FORCE_PARTICLE * DT / d;
                        pivx += dx * f;
                        pivy += dy * f;
                    }
                }
                vx[i] = pivx;
                vy[i] = pivy;
            }
        }

        // Inner loop vectorization, the inner loop is vectorized.
        public void updateForcesVectorAPI_Inner_Gather() {
            // We don't want to deal with tail loops, so we just assert that the number of
            // particles is a multiple of the vector length.
            if (x.length % SPECIES_F.length() != 0) {
                throw new RuntimeException("Number of particles is not a multiple of the vector length.");
            }

            for (int i = 0; i < x.length; i++) {
                float pix = x[i];
                float piy = y[i];

                // We consider the force of multiple (j) particles on particle i.
                var fx = FloatVector.zero(SPECIES_F);
                var fy = FloatVector.zero(SPECIES_F);

                for (int j = 0; j < x.length; j += SPECIES_F.length()) {
                    var pjx = FloatVector.fromArray(SPECIES_F, x, j);
                    var pjy = FloatVector.fromArray(SPECIES_F, y, j);

                    var dx = pjx.sub(pix).neg();
                    var dy = pjy.sub(piy).neg();
                    var d2 = ( dx.mul(dx) ).add( dy.mul(dy) );
                    var d = d2.lanewise(VectorOperators.SQRT);

                    // We directly gather the poles from the matrix.
                    var pole = FloatVector.fromArray(SPECIES_F, poles[group[i]], 0, group, j);

                    // We need to compute all 3 piece-wise liner parts.
                    var poleDivScale2 = pole.mul(SCALE3 / SCALE2);
                    var f1 = d.sub(SCALE1).neg().mul(1f / SCALE1);
                    var f2 = d.sub(SCALE1).mul(poleDivScale2);
                    var f3 = d.sub(SCALE1 + SCALE2 * 2f).neg().mul(poleDivScale2);

                    // And we need to perform all checks, for the boundaries of the piece-wise parts.
                    var f0Mask = d.compare(VectorOperators.GT, 0);
                    var f1Mask = d.compare(VectorOperators.LT, SCALE1);
                    var f2Mask = d.compare(VectorOperators.LT, SCALE1 + SCALE2);
                    var f3Mask = d.compare(VectorOperators.LT, SCALE1 + SCALE2 * 2f);
                    var f03Mask = f0Mask.and(f3Mask);

                    // Then, we put together the 3 middle parts.
                    var f12  = f2.blend(f1, f1Mask);
                    var f123 = f3.blend(f12, f2Mask);

                    f123 = f123.mul(FORCE_PARTICLE * DT).div(d);

                    // And we only apply the middle (non-zero) parts if the mask is enabled.
                    fx = fx.add(dx.mul(f123), f03Mask);
                    fy = fy.add(dy.mul(f123), f03Mask);
                }
                // We need to add the force of all the (j) particles onto i's velocity.
                vx[i] += fx.reduceLanes(VectorOperators.ADD);
                vy[i] += fy.reduceLanes(VectorOperators.ADD);
            }
        }

        // Inner loop vectorization, the inner loop is vectorized. But instead of gathering the poles
        // in the inner loop, we rearrange it in the outer loop, so the inner loop has a linear access.
        public void updateForcesVectorAPI_Inner_Rearranged() {
            // We don't want to deal with tail loops, so we just assert that the number of
            // particles is a multiple of the vector length.
            if (x.length % SPECIES_F.length() != 0) {
                throw new RuntimeException("Number of particles is not a multiple of the vector length.");
            }

            for (int i = 0; i < x.length; i++) {
                // Rearrange data to avoid rearrange in the loop.
                // We could also use the VectorAPI for this loop, but it is not even necessary for speedups.
                float[] polesgi = poles[group[i]];
                for (int j = 0; j < x.length; j++) {
                    polesScratch[j] = polesgi[group[j]];
                }

                float pix = x[i];
                float piy = y[i];

                // We consider the force of multiple (j) particles on particle i.
                var fx = FloatVector.zero(SPECIES_F);
                var fy = FloatVector.zero(SPECIES_F);

                for (int j = 0; j < x.length; j += SPECIES_F.length()) {
                    var pjx = FloatVector.fromArray(SPECIES_F, x, j);
                    var pjy = FloatVector.fromArray(SPECIES_F, y, j);

                    var dx = pjx.sub(pix).neg();
                    var dy = pjy.sub(piy).neg();
                    var d2 = ( dx.mul(dx) ).add( dy.mul(dy) );
                    var d = d2.lanewise(VectorOperators.SQRT);

                    // We can now access the poles from scratch in a linear access, avoiding the
                    // repeated gather in each inner loop. This helps especially if gather is
                    // not supported on a platform. But it also improves the access pattern on
                    // platforms where gather would be supported, but linear access is faster.
                    var pole = FloatVector.fromArray(SPECIES_F, polesScratch, j);

                    // We need to compute all 3 piece-wise liner parts.
                    var poleDivScale2 = pole.mul(SCALE3 / SCALE2);
                    var f1 = d.sub(SCALE1).neg().mul(1f / SCALE1);
                    var f2 = d.sub(SCALE1).mul(poleDivScale2);
                    var f3 = d.sub(SCALE1 + SCALE2 * 2f).neg().mul(poleDivScale2);

                    // And we need to perform all checks, for the boundaries of the piece-wise parts.
                    var f0Mask = d.compare(VectorOperators.GT, 0);
                    var f1Mask = d.compare(VectorOperators.LT, SCALE1);
                    var f2Mask = d.compare(VectorOperators.LT, SCALE1 + SCALE2);
                    var f3Mask = d.compare(VectorOperators.LT, SCALE1 + SCALE2 * 2f);
                    var f03Mask = f0Mask.and(f3Mask);

                    // Then, we put together the 3 middle parts.
                    var f12  = f2.blend(f1, f1Mask);
                    var f123 = f3.blend(f12, f2Mask);

                    f123 = f123.mul(FORCE_PARTICLE * DT).div(d);

                    // And we only apply the middle (non-zero) parts if the mask is enabled.
                    fx = fx.add(dx.mul(f123), f03Mask);
                    fy = fy.add(dy.mul(f123), f03Mask);
                }
                // We need to add the force of all the (j) particles onto i's velocity.
                vx[i] += fx.reduceLanes(VectorOperators.ADD);
                vy[i] += fy.reduceLanes(VectorOperators.ADD);
            }
        }

        // Instead of vectorizing the inner loop, we can also vectorize the outer loop.
        public void updateForcesVectorAPI_Outer() {
            // We don't want to deal with tail loops, so we just assert that the number of
            // particles is a multiple of the vector length.
            if (x.length % SPECIES_F.length() != 0) {
                throw new RuntimeException("Number of particles is not a multiple of the vector length.");
            }

            for (int i = 0; i < x.length; i += SPECIES_F.length()) {
                var pix = FloatVector.fromArray(SPECIES_F, x, i);
                var piy = FloatVector.fromArray(SPECIES_F, y, i);
                var pivx = FloatVector.fromArray(SPECIES_F, vx, i);
                var pivy = FloatVector.fromArray(SPECIES_F, vy, i);

                // Let's consider the force of the j particle on all of the i particles in the vector.
                for (int j = 0; j < x.length; j++) {
                    float pjx = x[j];
                    float pjy = y[j];

                    var dx = pix.sub(pjx);
                    var dy = piy.sub(pjy);
                    var d2 = ( dx.mul(dx) ).add( dy.mul(dy) );
                    var d = d2.lanewise(VectorOperators.SQRT);

                    // We need to access transpose of poles, because we need to access adjacent i's
                    var pole = FloatVector.fromArray(SPECIES_F, polesT[group[j]], 0, group, i);

                    var poleDivScale2 = pole.mul(SCALE3 / SCALE2);
                    var f1 = d.sub(SCALE1).neg().mul(1f / SCALE1);
                    var f2 = d.sub(SCALE1).mul(poleDivScale2);
                    var f3 = d.sub(SCALE1 + SCALE2 * 2f).neg().mul(poleDivScale2);

                    var f0Mask = d.compare(VectorOperators.GT, 0);
                    var f1Mask = d.compare(VectorOperators.LT, SCALE1);
                    var f2Mask = d.compare(VectorOperators.LT, SCALE1 + SCALE2);
                    var f3Mask = d.compare(VectorOperators.LT, SCALE1 + SCALE2 * 2f);
                    var f03Mask = f0Mask.and(f3Mask);

                    var f12  = f2.blend(f1, f1Mask);
                    var f123 = f3.blend(f12, f2Mask);

                    f123 = f123.mul(FORCE_PARTICLE * DT).div(d);
                    pivx = pivx.add(dx.mul(f123), f03Mask);
                    pivy = pivy.add(dy.mul(f123), f03Mask);
                }
                pivx.intoArray(vx, i);
                pivy.intoArray(vy, i);
            }
        }

        // The loop is so simple that it can be auto vectorized
        public void updatePositions() {
            float effectiveDampening = (float)Math.pow(DAMPENING, DT);
            for (int i = 0; i < x.length; i++) {
                float px = x[i];
                float py = y[i];
                float pvx = vx[i];
                float pvy = vy[i];

                // Force that pulls to origin, based on distance.
                float d = (float)Math.sqrt(px * px + py * py);
                pvx -= px * d * FORCE_ORIGIN * DT;
                pvy -= py * d * FORCE_ORIGIN * DT;

                // Update position and put drag on speed
                px += pvx * DT;
                py += pvy * DT;
                pvx *= effectiveDampening;
                pvy *= effectiveDampening;

                x[i] = px;
                y[i] = py;
                vx[i] = pvx;
                vy[i] = pvy;
            }
        }
    }

    /**
     * This panel displays the simulation.
     **/
    public static class ParticlePanel extends JPanel {

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Rendering settings for smoother circles
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            g2d.setColor(new Color(0, 0, 0));
            g2d.fillRect(0, 0, 1000, 1000);

            // Draw position of points
            for (int i = 0; i < STATE.x.length; i++) {
                g2d.setColor(STATE.colors[STATE.group[i]]);
                int xx = (int)(STATE.x[i] * ZOOM + 500f);
                int yy = (int)(STATE.y[i] * ZOOM + 500f);
                //g2d.fillRect(xx - 3, yy - 3, 6, 6);
                g2d.fill(new Ellipse2D.Double(xx - 3, yy - 3, 6, 6));
            }

            g2d.setColor(new Color(0, 0, 0));
            g2d.fillRect(0, 0, 150, 35);
            g2d.setColor(new Color(255, 255, 255));
            g2d.setFont(new Font("Consolas", Font.PLAIN, 30));
            g2d.drawString("FPS: " + (int)Math.floor(STATE.fps), 0, 30);

            g2d.setColor(new Color(255, 255, 255));
            int r1 = (int)(ZOOM * SCALE1);
            int r2 = (int)(ZOOM * (SCALE1 + SCALE2 * 2f));
            g2d.drawOval(900 - r1, 100 - r1, 2 * r1, 2 * r1);
            g2d.drawOval(900 - r2, 100 - r2, 2 * r2, 2 * r2);
        }
    }

    /**
     * This panel displays the pole matrix.
     **/
    public static class ForcePanel extends JPanel {

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            g2d.setColor(new Color(0, 0, 0));
            g2d.fillRect(0, 0, 350, 350);

            int nGroups = STATE.poles.length;
            int scale = (int)(300f / nGroups);
            for (int i = 0; i < nGroups; i++) {
                g2d.setColor(STATE.colors[i]);
                g2d.fillRect(scale * (i + 1), 0, scale, scale);
                g2d.fillRect(0, scale * (i + 1), scale, scale);

                for (int j = 0; j < nGroups; j++) {
                    float p = STATE.poles[i][j];
                    float cr = Math.max(0, p);
                    float cg = Math.max(0, -p);
                    g2d.setColor(new Color(cr, cg, 0));
                    g2d.fillRect(scale * (i + 1), scale * (j + 1), scale, scale);
                }
            }
        }
    }
}
