/*
 * Copyright 1998-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

package javax.swing.colorchooser;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.util.Locale;

/**
 * The standard RGB chooser.
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases. The current serialization support is
 * appropriate for short term storage or RMI between applications running
 * the same version of Swing.  As of 1.4, support for long term storage
 * of all JavaBeans<sup><font size="-2">TM</font></sup>
 * has been added to the <code>java.beans</code> package.
 * Please see {@link java.beans.XMLEncoder}.
 *
 * @author Steve Wilson
 * @author Mark Davidson
 * @see JColorChooser
 * @see AbstractColorChooserPanel
 */
class DefaultRGBChooserPanel extends AbstractColorChooserPanel implements ChangeListener {

    protected JSlider redSlider;
    protected JSlider greenSlider;
    protected JSlider blueSlider;
    protected JSpinner redField;
    protected JSpinner blueField;
    protected JSpinner greenField;

    private final int minValue = 0;
    private final int maxValue = 255;

    private boolean isAdjusting = false; // indicates the fields are being set internally

    public DefaultRGBChooserPanel() {
        super();
        setInheritsPopupMenu(true);
    }

    /**
     * Sets the values of the controls to reflect the color
     */
    private void setColor( Color newColor ) {
        int red = newColor.getRed();
        int blue = newColor.getBlue();
        int green = newColor.getGreen();

        if (redSlider.getValue() != red) {
            redSlider.setValue(red);
        }
        if (greenSlider.getValue() != green) {
            greenSlider.setValue(green);
        }
        if (blueSlider.getValue() != blue) {
            blueSlider.setValue(blue);
        }

        if (((Integer)redField.getValue()).intValue() != red)
            redField.setValue(new Integer(red));
        if (((Integer)greenField.getValue()).intValue() != green)
            greenField.setValue(new Integer(green));
        if (((Integer)blueField.getValue()).intValue() != blue )
            blueField.setValue(new Integer(blue));
    }

    public String getDisplayName() {
        return UIManager.getString("ColorChooser.rgbNameText", getLocale());
    }

    /**
     * Provides a hint to the look and feel as to the
     * <code>KeyEvent.VK</code> constant that can be used as a mnemonic to
     * access the panel. A return value <= 0 indicates there is no mnemonic.
     * <p>
     * The return value here is a hint, it is ultimately up to the look
     * and feel to honor the return value in some meaningful way.
     * <p>
     * This implementation looks up the value from the default
     * <code>ColorChooser.rgbMnemonic</code>, or if it
     * isn't available (or not an <code>Integer</code>) returns -1.
     * The lookup for the default is done through the <code>UIManager</code>:
     * <code>UIManager.get("ColorChooser.rgbMnemonic");</code>.
     *
     * @return KeyEvent.VK constant identifying the mnemonic; <= 0 for no
     *         mnemonic
     * @see #getDisplayedMnemonicIndex
     * @since 1.4
     */
    public int getMnemonic() {
        return getInt("ColorChooser.rgbMnemonic", -1);
    }

    /**
     * Provides a hint to the look and feel as to the index of the character in
     * <code>getDisplayName</code> that should be visually identified as the
     * mnemonic. The look and feel should only use this if
     * <code>getMnemonic</code> returns a value > 0.
     * <p>
     * The return value here is a hint, it is ultimately up to the look
     * and feel to honor the return value in some meaningful way. For example,
     * a look and feel may wish to render each
     * <code>AbstractColorChooserPanel</code> in a <code>JTabbedPane</code>,
     * and further use this return value to underline a character in
     * the <code>getDisplayName</code>.
     * <p>
     * This implementation looks up the value from the default
     * <code>ColorChooser.rgbDisplayedMnemonicIndex</code>, or if it
     * isn't available (or not an <code>Integer</code>) returns -1.
     * The lookup for the default is done through the <code>UIManager</code>:
     * <code>UIManager.get("ColorChooser.rgbDisplayedMnemonicIndex");</code>.
     *
     * @return Character index to render mnemonic for; -1 to provide no
     *                   visual identifier for this panel.
     * @see #getMnemonic
     * @since 1.4
     */
    public int getDisplayedMnemonicIndex() {
        return getInt("ColorChooser.rgbDisplayedMnemonicIndex", -1);
    }

    public Icon getSmallDisplayIcon() {
        return null;
    }

    public Icon getLargeDisplayIcon() {
        return null;
    }

    /**
     * The background color, foreground color, and font are already set to the
     * defaults from the defaults table before this method is called.
     */
    public void installChooserPanel(JColorChooser enclosingChooser) {
        super.installChooserPanel(enclosingChooser);
    }

    protected void buildChooser() {

        Locale locale = getLocale();
        String redString = UIManager.getString("ColorChooser.rgbRedText", locale);
        String greenString = UIManager.getString("ColorChooser.rgbGreenText", locale);
        String blueString = UIManager.getString("ColorChooser.rgbBlueText", locale);

        setLayout( new BorderLayout() );
        Color color = getColorFromModel();


        JPanel enclosure = new JPanel();
        enclosure.setLayout( new SmartGridLayout( 3, 3 ) );
        enclosure.setInheritsPopupMenu(true);

        // The panel that holds the sliders

        add( enclosure, BorderLayout.CENTER );
        //        sliderPanel.setBorder(new LineBorder(Color.black));

        // The row for the red value
        JLabel l = new JLabel(redString);
        l.setDisplayedMnemonic(getInt("ColorChooser.rgbRedMnemonic", -1));
        enclosure.add(l);
        redSlider = new JSlider(JSlider.HORIZONTAL, 0, 255, color.getRed());
        redSlider.setMajorTickSpacing( 85 );
        redSlider.setMinorTickSpacing( 17 );
        redSlider.setPaintTicks( true );
        redSlider.setPaintLabels( true );
        redSlider.setInheritsPopupMenu(true);
        enclosure.add( redSlider );
        redField = new JSpinner(
            new SpinnerNumberModel(color.getRed(), minValue, maxValue, 1));
        l.setLabelFor(redSlider);
        redField.setInheritsPopupMenu(true);
        JPanel redFieldHolder = new JPanel(new CenterLayout());
        redFieldHolder.setInheritsPopupMenu(true);
        redField.addChangeListener(this);
        redFieldHolder.add(redField);
        enclosure.add(redFieldHolder);


        // The row for the green value
        l = new JLabel(greenString);
        l.setDisplayedMnemonic(getInt("ColorChooser.rgbGreenMnemonic", -1));
        enclosure.add(l);
        greenSlider = new JSlider(JSlider.HORIZONTAL, 0, 255, color.getGreen());
        greenSlider.setMajorTickSpacing( 85 );
        greenSlider.setMinorTickSpacing( 17 );
        greenSlider.setPaintTicks( true );
        greenSlider.setPaintLabels( true );
        greenSlider.setInheritsPopupMenu(true);
        enclosure.add(greenSlider);
        greenField = new JSpinner(
            new SpinnerNumberModel(color.getGreen(), minValue, maxValue, 1));
        l.setLabelFor(greenSlider);
        greenField.setInheritsPopupMenu(true);
        JPanel greenFieldHolder = new JPanel(new CenterLayout());
        greenFieldHolder.add(greenField);
        greenFieldHolder.setInheritsPopupMenu(true);
        greenField.addChangeListener(this);
        enclosure.add(greenFieldHolder);

        // The slider for the blue value
        l = new JLabel(blueString);
        l.setDisplayedMnemonic(getInt("ColorChooser.rgbBlueMnemonic", -1));
        enclosure.add(l);
        blueSlider = new JSlider(JSlider.HORIZONTAL, 0, 255, color.getBlue());
        blueSlider.setMajorTickSpacing( 85 );
        blueSlider.setMinorTickSpacing( 17 );
        blueSlider.setPaintTicks( true );
        blueSlider.setPaintLabels( true );
        blueSlider.setInheritsPopupMenu(true);
        enclosure.add(blueSlider);
        blueField = new JSpinner(
            new SpinnerNumberModel(color.getBlue(), minValue, maxValue, 1));
        l.setLabelFor(blueSlider);
        blueField.setInheritsPopupMenu(true);
        JPanel blueFieldHolder = new JPanel(new CenterLayout());
        blueFieldHolder.add(blueField);
        blueField.addChangeListener(this);
        blueFieldHolder.setInheritsPopupMenu(true);
        enclosure.add(blueFieldHolder);

        redSlider.addChangeListener( this );
        greenSlider.addChangeListener( this );
        blueSlider.addChangeListener( this );

        redSlider.putClientProperty("JSlider.isFilled", Boolean.TRUE);
        greenSlider.putClientProperty("JSlider.isFilled", Boolean.TRUE);
        blueSlider.putClientProperty("JSlider.isFilled", Boolean.TRUE);
    }

    public void uninstallChooserPanel(JColorChooser enclosingChooser) {
        super.uninstallChooserPanel(enclosingChooser);
        removeAll();
    }

    public void updateChooser() {
        if (!isAdjusting) {
            isAdjusting = true;

            setColor(getColorFromModel());

            isAdjusting = false;
        }
    }

    public void stateChanged( ChangeEvent e ) {
        if ( e.getSource() instanceof JSlider && !isAdjusting) {

            int red = redSlider.getValue();
            int green = greenSlider.getValue();
            int blue = blueSlider.getValue() ;
            Color color = new Color (red, green, blue);

            getColorSelectionModel().setSelectedColor(color);
        } else if (e.getSource() instanceof JSpinner && !isAdjusting) {

            int red = ((Integer)redField.getValue()).intValue();
            int green = ((Integer)greenField.getValue()).intValue();
            int blue = ((Integer)blueField.getValue()).intValue();
            Color color = new Color (red, green, blue);

            getColorSelectionModel().setSelectedColor(color);
        }
    }

}
