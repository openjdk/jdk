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
import java.awt.*;
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

    private static final JLabel selectedColorLabel = new JLabel("Preview");
    private static final JColorChooser colorChooser = new JColorChooser(Color.WHITE);
    private static final Color NO_COLOR = new Color(0, 0, 0, 0);

    public ColorAction() {
        initializeComponents();
    }

    private void initializeComponents() {
        selectedColorLabel.setPreferredSize(new Dimension(3 * 32, 32));
        selectedColorLabel.setOpaque(false); // Allow transparency
        selectedColorLabel.setBackground(NO_COLOR); // Set transparent background
        selectedColorLabel.setForeground(Color.BLACK); // Set text color
        selectedColorLabel.setHorizontalAlignment(SwingConstants.CENTER); // Center the text


        // Add a ChangeListener to react to color selection changes
        colorChooser.getSelectionModel().addChangeListener(e -> {
            Color selectedColor = colorChooser.getColor();
            if (selectedColor != null) {
                selectedColorLabel.setBackground(selectedColor);
                selectedColorLabel.setOpaque(selectedColor.getAlpha() != 0);
                selectedColorLabel.setForeground(FigureWidget.getTextColor(selectedColor));
            }
        });

        // Create a panel to display recent colors
        JPanel colorsPanel = new JPanel();
        colorsPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        for (Color color : colors) {
            JButton colorButton = new JButton();
            colorButton.setBackground(color);
            colorButton.setOpaque(true);
            colorButton.setBorderPainted(false);
            colorButton.setRolloverEnabled(false);
            colorButton.setRequestFocusEnabled(false);

            colorButton.setPreferredSize(new Dimension(16, 16));
            colorButton.addActionListener(e -> {
                selectedColorLabel.setBackground(color);
                selectedColorLabel.setOpaque(color.getAlpha() != 0);
                selectedColorLabel.setForeground(FigureWidget.getTextColor(color));
            });
            colorsPanel.add(colorButton);
        }

        // Add "No Color" button
        JButton noColorButton = new JButton("No Color");
        noColorButton.setOpaque(true);
        noColorButton.setContentAreaFilled(true);
        noColorButton.setBorderPainted(true);
        noColorButton.setPreferredSize(new Dimension(90, 24));
        noColorButton.setFocusPainted(false);
        noColorButton.addActionListener(e -> {
            selectedColorLabel.setBackground(NO_COLOR);
            selectedColorLabel.setOpaque(false);
            selectedColorLabel.setForeground(Color.BLACK);
        });
        colorsPanel.add(noColorButton);

        // Add the preview label
        colorsPanel.add(selectedColorLabel, 0);
        colorsPanel.revalidate();
        colorsPanel.repaint();

        // Add recent colors panel below the color chooser
        colorChooser.setPreviewPanel(colorsPanel);
    }

    // Variables to store the dialog position
    private Point dialogLoc = null;

    public void performAction(DiagramViewModel model) {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            // Create the dialog with an OK button to select the color
            final JDialog[] dialogHolder = new JDialog[1];
            dialogHolder[0] = JColorChooser.createDialog(
                    null,
                    "Choose a Color",
                    true,
                    colorChooser,
                    e -> {
                        // Save the current location
                        dialogLoc = dialogHolder[0].getLocation();
                        // OK button action
                        Color selectedColor = selectedColorLabel.getBackground();
                        if (selectedColor.equals(NO_COLOR)) {
                            selectedColor = null;
                        }
                        editor.colorSelectedFigures(selectedColor);
                    },
                    null // Cancel button action
            );

            // Set the dialog's position if previously saved
            if (dialogLoc != null) {
                dialogHolder[0].setLocation(dialogLoc);
            }
            dialogHolder[0].setVisible(true);
        }
    }

    @Override
    public boolean isEnabled(DiagramViewModel model) {
        return model != null && !model.getSelectedNodes().isEmpty();
    }
}
