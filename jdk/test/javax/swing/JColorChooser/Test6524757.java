/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6524757
 * @summary Tests different locales
 * @author Sergey Malenkov
 */

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.colorchooser.AbstractColorChooserPanel;

import sun.swing.SwingUtilities2;

public class Test6524757 {
    private static final String[] KEYS = {
            "ColorChooser.okText", // NON-NLS: string key from JColorChooser
            "ColorChooser.cancelText", // NON-NLS: string key from JColorChooser
            "ColorChooser.resetText", // NON-NLS: string key from JColorChooser
            "ColorChooser.resetMnemonic", // NON-NLS: int key from JColorChooser

//NotAvail: "ColorChooser.sampleText", // NON-NLS: string key from DefaultPreviewPanel

            "ColorChooser.swatchesNameText", // NON-NLS: string key from DefaultSwatchChooserPanel
            "ColorChooser.swatchesMnemonic", // NON-NLS: string key from DefaultSwatchChooserPanel:int
            "ColorChooser.swatchesDisplayedMnemonicIndex", // NON-NLS: int key from DefaultSwatchChooserPanel
            "ColorChooser.swatchesSwatchSize", // NON-NLS: dimension key from DefaultSwatchChooserPanel
            "ColorChooser.swatchesRecentText", // NON-NLS: string key from DefaultSwatchChooserPanel
            "ColorChooser.swatchesRecentSwatchSize", // NON-NLS: dimension key from DefaultSwatchChooserPanel
//NotAvail: "ColorChooser.swatchesDefaultRecentColor", // NON-NLS: color key from DefaultSwatchChooserPanel

            "ColorChooser.hsbNameText", // NON-NLS: string key from DefaultHSBChooserPanel
            "ColorChooser.hsbMnemonic", // NON-NLS: int key from DefaultHSBChooserPanel
            "ColorChooser.hsbDisplayedMnemonicIndex", // NON-NLS: int key from DefaultHSBChooserPanel
            "ColorChooser.hsbHueText", // NON-NLS: string key from DefaultHSBChooserPanel
            "ColorChooser.hsbSaturationText", // NON-NLS: string key from DefaultHSBChooserPanel
            "ColorChooser.hsbBrightnessText", // NON-NLS: string key from DefaultHSBChooserPanel
            "ColorChooser.hsbRedText", // NON-NLS: string key from DefaultHSBChooserPanel
            "ColorChooser.hsbGreenText", // NON-NLS: string key from DefaultHSBChooserPanel
            "ColorChooser.hsbBlueText", // NON-NLS: string key from DefaultHSBChooserPanel

            "ColorChooser.rgbNameText", // NON-NLS: string key from DefaultRGBChooserPanel
            "ColorChooser.rgbMnemonic", // NON-NLS: int key from DefaultRGBChooserPanel
            "ColorChooser.rgbDisplayedMnemonicIndex", // NON-NLS: int key from DefaultRGBChooserPanel
            "ColorChooser.rgbRedText", // NON-NLS: string key from DefaultRGBChooserPanel
            "ColorChooser.rgbGreenText", // NON-NLS: string key from DefaultRGBChooserPanel
            "ColorChooser.rgbBlueText", // NON-NLS: string key from DefaultRGBChooserPanel
            "ColorChooser.rgbRedMnemonic", // NON-NLS: int key from DefaultRGBChooserPanel
            "ColorChooser.rgbGreenMnemonic", // NON-NLS: int key from DefaultRGBChooserPanel
            "ColorChooser.rgbBlueMnemonic", // NON-NLS: int key from DefaultRGBChooserPanel
    };
    private static final Object[] KOREAN = convert(Locale.KOREAN, KEYS);
    private static final Object[] FRENCH = convert(Locale.FRENCH, KEYS);

    public static void main(String[] args) {
        // it affects Swing because it is not initialized
        Locale.setDefault(Locale.KOREAN);
        Object[] korean = create();

        // it does not affect Swing because it is initialized
        Locale.setDefault(Locale.CANADA);
        Object[] canada = create();

        // it definitely should affect Swing
        JComponent.setDefaultLocale(Locale.FRENCH);
        Object[] french = create();

        validate(KOREAN, korean);
        validate(KOREAN, canada);
        validate(FRENCH, french);
    }

    private static void validate(Object[] expected, Object[] actual) {
        int count = expected.length;
        if (count != actual.length) {
            throw new Error("different size: " + count + " <> " + actual.length);
        }
        for (int i = 0; i < count; i++) {
            if (!expected[i].equals(actual[i])) {
                throw new Error("unexpected value for key: " + KEYS[i]);
            }
        }
    }

    private static Object[] convert(Locale locale, String[] keys) {
        int count = keys.length;
        Object[] array = new Object[count];
        for (int i = 0; i < count; i++) {
            array[i] = convert(locale, keys[i]);
        }
        return array;
    }

    private static Object convert(Locale locale, String key) {
        if (key.endsWith("Text")) { // NON-NLS: suffix for text message
            return UIManager.getString(key, locale);
        }
        if (key.endsWith("Size")) { // NON-NLS: suffix for dimension
            return UIManager.getDimension(key, locale);
        }
        if (key.endsWith("Color")) { // NON-NLS: suffix for color
            return UIManager.getColor(key, locale);
        }
        int value = SwingUtilities2.getUIDefaultsInt(key, locale, -1);
        return Integer.valueOf(value);
    }

    private static Object[] create() {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setVisible(true);

        // show color chooser
        JColorChooser chooser = new JColorChooser();
        JDialog dialog = JColorChooser.createDialog(frame, null, false, chooser, null, null);
        dialog.setVisible(true);

        // process all values
        List<Object> list = new ArrayList<Object>(KEYS.length);
        addMain(list, dialog);
        addSwatch(list, chooser);
        addHSB(list, chooser);
        addRGB(list, chooser);

        // close dialog
        dialog.setVisible(false);
        dialog.dispose();

        // close frame
        frame.setVisible(false);
        frame.dispose();

        return list.toArray();
    }

    private static void addMain(List<Object> list, JDialog dialog) {
        Component component = getC(getC(dialog.getLayeredPane(), 0), 1);
        JButton ok = (JButton) getC(component, 0);
        JButton cancel = (JButton) getC(component, 1);
        JButton reset = (JButton) getC(component, 2);
        list.add(ok.getText());
        list.add(cancel.getText());
        list.add(reset.getText());
        list.add(Integer.valueOf(reset.getMnemonic()));
    }

    private static void addSwatch(List<Object> list, JColorChooser chooser) {
        Component component = addPanel(list, chooser, 0);
        JLabel label = (JLabel) getC(getC(component, 0), 1);
        JPanel upper = (JPanel) getC(getC(getC(component, 0), 0), 0);
        JPanel lower = (JPanel) getC(getC(getC(component, 0), 2), 0);
        addSize(list, upper, 1, 1, 31, 9);
        list.add(label.getText());
        addSize(list, lower, 1, 1, 5, 7);
    }

    private static void addHSB(List<Object> list, JColorChooser chooser) {
        Component component = addPanel(list, chooser, 1);
        JRadioButton h = (JRadioButton) getC(getC(getC(component, 1), 0), 0);
        JRadioButton s = (JRadioButton) getC(getC(getC(component, 1), 0), 2);
        JRadioButton b = (JRadioButton) getC(getC(getC(component, 1), 0), 4);
        list.add(h.getText());
        list.add(s.getText());
        list.add(b.getText());
        JLabel red = (JLabel) getC(getC(getC(component, 1), 2), 0);
        JLabel green = (JLabel) getC(getC(getC(component, 1), 2), 2);
        JLabel blue = (JLabel) getC(getC(getC(component, 1), 2), 4);
        list.add(red.getText());
        list.add(green.getText());
        list.add(blue.getText());
    }

    private static void addRGB(List<Object> list, JColorChooser chooser) {
        Component component = addPanel(list, chooser, 2);
        JLabel red = (JLabel) getC(getC(component, 0), 0);
        JLabel green = (JLabel) getC(getC(component, 0), 3);
        JLabel blue = (JLabel) getC(getC(component, 0), 6);
        list.add(red.getText());
        list.add(green.getText());
        list.add(blue.getText());
        list.add(Integer.valueOf(red.getDisplayedMnemonic()));
        list.add(Integer.valueOf(green.getDisplayedMnemonic()));
        list.add(Integer.valueOf(blue.getDisplayedMnemonic()));
    }

    private static void addSize(List<Object> list, Component component, int x, int y, int w, int h) {
        Dimension size = component.getPreferredSize();
        int width = (size.width + 1) / w - x;
        int height = (size.height + 1) / h - y;
        list.add(new Dimension(width, height));
    }

    private static Component addPanel(List<Object> list, JColorChooser chooser, int index) {
        AbstractColorChooserPanel panel = (AbstractColorChooserPanel) getC(getC(getC(chooser, 0), index), 0);
        list.add(panel.getDisplayName());
        list.add(Integer.valueOf(panel.getMnemonic()));
        list.add(Integer.valueOf(panel.getDisplayedMnemonicIndex()));
        return panel;
    }

    private static Component getC(Component component, int index) {
        Container container = (Container) component;
        return container.getComponent(index);
    }
}
