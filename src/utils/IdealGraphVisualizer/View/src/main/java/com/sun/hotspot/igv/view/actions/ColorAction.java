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
 *
 */
package com.sun.hotspot.igv.view.actions;

import com.sun.hotspot.igv.view.DiagramViewModel;
import com.sun.hotspot.igv.view.EditorTopComponent;
import com.sun.hotspot.igv.view.widgets.FigureWidget;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.*;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;


@ActionID(category = "View", id = "com.sun.hotspot.igv.view.actions.ColorAction")
@ActionRegistration(displayName = "#CTL_ColorAction")
@ActionReferences({
        @ActionReference(path = "Menu/View", position = 360),
        @ActionReference(path = "Shortcuts", name = "D-C")
})
@Messages({
        "CTL_ColorAction=Color",
        "HINT_ColorAction=Color current set of selected nodes"
})
public final class ColorAction extends ModelAwareAction {

    @Override
    protected String iconResource() {
        return "com/sun/hotspot/igv/view/images/color.gif"; // NOI18N
    }

    @Override
    protected String getDescription() {
        return NbBundle.getMessage(ColorAction.class, "HINT_ColorAction");
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(ColorAction.class, "CTL_ColorAction");
    }

    private static final ArrayList<Color> colors = new ArrayList<>(Arrays.asList(
            Color.RED,
            Color.ORANGE,
            Color.YELLOW,
            Color.GREEN,
            Color.CYAN,
            Color.BLUE,
            Color.MAGENTA,
            Color.PINK,
            Color.DARK_GRAY,
            Color.GRAY,
            Color.LIGHT_GRAY,
            Color.WHITE
    ));

    private static final JButton selectedColorButton = new JButton("Preview");
    private static final JColorChooser colorChooser = new JColorChooser(Color.WHITE);

    public ColorAction() {
        // Store the current look and feel
        String originalLookAndFeel = UIManager.getLookAndFeel().getClass().getName();

        try {
            // Set Look and Feel
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            SwingUtilities.updateComponentTreeUI(selectedColorButton);

            Font defaultFont = new Font("Dialog", Font.PLAIN, 12);
            UIManager.put("ColorChooser.font", defaultFont);

            // Initialize components with the custom Look and Feel
            initializeComponents();

            // Update the UI for the button and color chooser to apply the new Look and Feel
            SwingUtilities.updateComponentTreeUI(selectedColorButton);
            SwingUtilities.updateComponentTreeUI(colorChooser);
        } catch (Exception ignored) {
        } finally {
            try {
                // Restore the original Look and Feel for the rest of the application
                UIManager.setLookAndFeel(originalLookAndFeel);
            } catch (Exception ignored) {}
        }
    }
    private void initializeComponents() {
        selectedColorButton.setPreferredSize(new Dimension(3 * 32, 32));
        selectedColorButton.setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, Color.BLACK));
        selectedColorButton.setOpaque(true);
        selectedColorButton.setContentAreaFilled(true); // Ensures content is filled without rounded edges
        selectedColorButton.setBackground(Color.WHITE);
        selectedColorButton.setForeground(Color.BLACK); // Set text color

        // Add a ChangeListener to react to color selection changes
        colorChooser.getSelectionModel().addChangeListener(e -> {
            Color selectedColor = colorChooser.getColor();
            if (selectedColor != null) {
                selectedColorButton.setBackground(selectedColor);
                selectedColorButton.setForeground(FigureWidget.getTextColor(selectedColor));
            }
        });

        // Create a panel to display recent colors
        JPanel recentColorsPanel = new JPanel();
        recentColorsPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        updateColorsPanel(recentColorsPanel);

        // Add recent colors panel below the color chooser
        colorChooser.setPreviewPanel(recentColorsPanel);
    }

    public void performAction(DiagramViewModel model) {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            // Create the dialog with an OK button to select the color
            JDialog dialog = JColorChooser.createDialog(
                    null,
                    "Choose a Color",
                    true,
                    colorChooser,
                    e -> {
                        // OK button action
                        Color selectedColor = selectedColorButton.getBackground();
                        if (selectedColor != null) {
                            editor.colorSelectedFigures(selectedColor);
                        }
                    },
                    null // Cancel button action
            );
            dialog.setVisible(true);
        }
    }

    private void updateColorsPanel(JPanel panel) {
        panel.removeAll();
        for (Color color : colors) {
            JButton colorButton = new JButton();
            colorButton.setBackground(color);
            colorButton.setOpaque(true);
            colorButton.setPreferredSize(new Dimension(16, 16));
            colorButton.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            colorButton.addActionListener(e -> {
                selectedColorButton.setBackground(color);
                selectedColorButton.setForeground(FigureWidget.getTextColor(color));
            });
            panel.add(colorButton);
        }
        panel.add(selectedColorButton, 0);
        panel.revalidate();
        panel.repaint();
    }

    @Override
    public boolean isEnabled(DiagramViewModel model) {
        return model != null && !model.getSelectedNodes().isEmpty();
    }
}
