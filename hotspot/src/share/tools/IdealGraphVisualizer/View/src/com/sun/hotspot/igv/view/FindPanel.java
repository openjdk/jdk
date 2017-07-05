/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.view;

import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.data.Properties.RegexpPropertyMatcher;
import com.sun.hotspot.igv.data.Property;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 *
 * @author Thomas Wuerthinger
 */
class FindPanel extends JPanel implements KeyListener {

    private JComboBox nameComboBox;
    private JTextField valueTextField;

    public FindPanel(List<Figure> figures) {
        createDesign();
        updateComboBox(figures);
    }

    protected void createDesign() {
        setLayout(new GridLayout());
        nameComboBox = new JComboBox();
        valueTextField = new JTextField();
        add(nameComboBox);
        add(valueTextField);
        valueTextField.addKeyListener(this);
    }

    public void updateComboBox(List<Figure> figures) {

        String sel = (String) nameComboBox.getSelectedItem();
        SortedSet<String> propertyNames = new TreeSet<String>();

        for (Figure f : figures) {
            Properties prop = f.getProperties();
            for (Property p : prop) {
                if (!propertyNames.contains(p.getName())) {
                    propertyNames.add(p.getName());
                }
            }
        }

        for (String s : propertyNames) {
            nameComboBox.addItem(s);
        }
        nameComboBox.setSelectedItem(sel);
    }

    public String getNameText() {
        return (String) nameComboBox.getSelectedItem();
    }

    public String getValueText() {
        return valueTextField.getText();
    }

    public void keyTyped(KeyEvent e) {
    }

    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            find();
        }
    }

    public void find() {
        EditorTopComponent comp = EditorTopComponent.getActive();
        if (comp != null) {
            RegexpPropertyMatcher matcher = new RegexpPropertyMatcher(getNameText(), getValueText());
            comp.setSelection(matcher);
        }
    }

    public void keyReleased(KeyEvent e) {

    }
}
