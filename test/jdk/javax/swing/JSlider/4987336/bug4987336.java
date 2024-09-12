/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4987336
 * @summary JSlider doesn't show label's animated icon.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4987336
*/
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ButtonGroup;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import java.util.Hashtable;

public class bug4987336 {
    private static final String INSTRUCTIONS = """
            There are four Sliders.
            Each of them has a label with animated gif (a waving duke)
            and a label with static image.
            If it is rendered correctly, click Pass else click Fail.""";

    private static final String IMAGE_RES = "box.gif";

    private static final String ANIM_IMAGE_RES = "duke.gif";
    private static JFrame frame;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Slider rendering Instructions")
                .instructions(INSTRUCTIONS)
                .rows(5)
                .columns(35)
                .testUI(bug4987336::createTestUI)
                .position(PassFailJFrame.Position.TOP_LEFT_CORNER)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        frame = new JFrame("bug4987336");
        JPanel pnLafs = new JPanel();
        pnLafs.setLayout(new BoxLayout(pnLafs, BoxLayout.Y_AXIS));

        ButtonGroup group = new ButtonGroup();

        pnLafs.setBorder(new TitledBorder("Available Lafs"));

        for (UIManager.LookAndFeelInfo lafInfo : UIManager.getInstalledLookAndFeels()) {
            LafRadioButton comp = new LafRadioButton(lafInfo);

            pnLafs.add(comp);
            group.add(comp);
        }

        JPanel pnContent = new JPanel();

        pnContent.setLayout(new BoxLayout(pnContent, BoxLayout.Y_AXIS));

        pnContent.add(pnLafs);
        pnContent.add(createSlider(true, IMAGE_RES, IMAGE_RES, ANIM_IMAGE_RES, ANIM_IMAGE_RES));
        pnContent.add(createSlider(false, IMAGE_RES, IMAGE_RES, ANIM_IMAGE_RES, ANIM_IMAGE_RES));
        pnContent.add(createSlider(true, ANIM_IMAGE_RES, null, IMAGE_RES, IMAGE_RES));
        pnContent.add(createSlider(false, ANIM_IMAGE_RES, null, IMAGE_RES, IMAGE_RES));

        frame.getContentPane().add(new JScrollPane(pnContent));
        frame.pack();
        return frame;
    }

    private static JSlider createSlider(boolean enabled,
                                        String firstEnabledImage, String firstDisabledImage,
                                        String secondEnabledImage, String secondDisabledImage) {
        Hashtable<Integer, JComponent> dictionary = new Hashtable<Integer, JComponent>();

        dictionary.put(0, createLabel(firstEnabledImage, firstDisabledImage));
        dictionary.put(1, createLabel(secondEnabledImage, secondDisabledImage));

        JSlider result = new JSlider(0, 1);

        result.setLabelTable(dictionary);
        result.setPaintLabels(true);
        result.setEnabled(enabled);

        return result;
    }

    private static JLabel createLabel(String enabledImage, String disabledImage) {
        ImageIcon enabledIcon = enabledImage == null ? null :
                new ImageIcon(bug4987336.class.getResource(enabledImage));

        ImageIcon disabledIcon = disabledImage == null ? null :
                new ImageIcon(bug4987336.class.getResource(disabledImage));

        JLabel result = new JLabel(enabledImage == null && disabledImage == null ? "No image" : "Image",
                enabledIcon, SwingConstants.LEFT);

        result.setDisabledIcon(disabledIcon);

        return result;
    }

    private static class LafRadioButton extends JRadioButton {
        public LafRadioButton(final UIManager.LookAndFeelInfo lafInfo) {
            super(lafInfo.getName(), lafInfo.getName().equals(UIManager.getLookAndFeel().getName()));

            addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        UIManager.setLookAndFeel(lafInfo.getClassName());

                        SwingUtilities.updateComponentTreeUI(frame);
                    } catch (Exception ex) {
                        // Ignore such errors
                        System.out.println("Cannot set LAF " + lafInfo.getName());
                    }
                }
            });
        }
    }
}
