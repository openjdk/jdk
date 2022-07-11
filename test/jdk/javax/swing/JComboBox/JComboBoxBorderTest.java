import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.FlowLayout;
import javax.swing.UIManager;

public class JComboBoxBorderTest implements Runnable {
    public static void main(String[] args) {
        try {
//            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            UIManager.setLookAndFeel("com.apple.laf.AquaLookAndFeel");
        } catch (Exception e) {

        }

//        JLabel label = new JLabel("Editable combo box:");

        // Notice that the button positioning is incorrect and that the highlighting does
        // not go all the way around the combo box.
        JComboBox<String> comboBox = new JComboBox<>(new String[] { "Item 1", "Item 2", "Item 3" });
        JComboBox<String> comboBox2 = new JComboBox<>(new String[] { "Item 1", "Item 2", "Item 3" });
        comboBox.setEditable(true);

        FlowLayout layout = new FlowLayout(FlowLayout.LEADING);
//        layout.setAlignOnBaseline(true);
        JPanel panel = new JPanel(layout);
//        panel.add(label);
        panel.add(comboBox);
        panel.add(comboBox2);

        JFrame frame = new JFrame();
        frame.setContentPane(panel);
        frame.pack();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);

//        SwingUtilities.invokeAndWait(() -> UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel"));
//        SwingUtilities.invokeLater(new JComboBoxBorderTest());
    }

    @Override
    public void run() {
        JLabel label = new JLabel("Editable combo box:");

        // Notice that the button positioning is incorrect and that the highlighting does
        // not go all the way around the combo box.
        JComboBox<String> comboBox = new JComboBox<>(new String[] { "Item 1", "Item 2", "Item 3" });
        JComboBox<String> comboBox2 = new JComboBox<>(new String[] { "Item 1", "Item 2", "Item 3" });
        comboBox.setEditable(true);

        FlowLayout layout = new FlowLayout(FlowLayout.LEADING);
//        layout.setAlignOnBaseline(true);
        JPanel panel = new JPanel(layout);
        panel.add(label);
        panel.add(comboBox);
        panel.add(comboBox2);

        JFrame frame = new JFrame();
        frame.setContentPane(panel);
        frame.pack();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}