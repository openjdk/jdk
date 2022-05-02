/* @test
   @bug 4380543
   @requires (os.family == "windows")
   @summary setMargin() does not work for AbstractButton
   @modules java.desktop/com.sun.java.swing.plaf.motif
            java.desktop/com.sun.java.swing.plaf.windows
   @author Andrey Pikalev
   @key headful
   @run main/manual bug4380543
*/

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.FlowLayout;
import java.awt.Insets;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class bug4380543 {
    static testFrame testObj;
    static JFrame frame;
    static final CountDownLatch latch = new CountDownLatch(1);
    private static AtomicReference<Boolean> testResult = new AtomicReference<>(false);

    public static void main(String args[]) throws Exception {

        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    testObj = new testFrame();
                    createUI();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        boolean status = latch.await(5, TimeUnit.MINUTES);

        if (!status) {
            System.out.println("Test timed out.");
        }

        onCompletion(testResult);

    }

    public static void createUI() throws Exception {
        frame = new JFrame();
        JPanel mainControlPanel = new JPanel(new BorderLayout());
        JPanel resultButtonPanel = new JPanel(new FlowLayout());

        JTextArea instructionTextArea = new JTextArea();

        String instructions
                = "INSTRUCTIONS:" +
                "\n 1. This is a Windows specific test. If you are not on " +
                "Windows, press Pass." +
                "\n 2. Check if the Left insets(margins) is set visually " +
                "similar to other three sides around Radio Button and CheckBox" +
                "(insets set to 20 on all 4 sides)." +
                "\n 3. If Left insets(margins) appear Empty, press Fail, " +
                "else press Pass.";

        instructionTextArea.setText(instructions);
        instructionTextArea.setEnabled(false);
        instructionTextArea.setDisabledTextColor(Color.black);
        instructionTextArea.setBackground(Color.white);

        mainControlPanel.add(instructionTextArea,BorderLayout.NORTH);
        JButton passButton = new JButton("Pass");
        passButton.setActionCommand("Pass");

        passButton.addActionListener((ActionEvent e) -> {
            testResult.set(true);
            latch.countDown();
        });

        JButton failButton = new JButton("Fail");
        failButton.setActionCommand("Fail");
        failButton.addActionListener((ActionEvent e) -> {
            testResult.set(false);
            latch.countDown();
        });

        resultButtonPanel.add(passButton);
        resultButtonPanel.add(failButton);
        mainControlPanel.add(resultButtonPanel);
        frame.getContentPane().add(mainControlPanel,BorderLayout.SOUTH);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocation(500,50);
        frame.setSize(400, 150);
        frame.pack();
        frame.setVisible(true);

        Thread.sleep(1000);
    }

    private static void disposeUI()
    {
        testObj.dispose();
        frame.dispose();
    }
    private static void onCompletion(AtomicReference<Boolean> res)
    {
        disposeUI();
        if (res.toString() == "false")
        {
            throw new RuntimeException("Test Failed");
        }
    }
}

class testFrame extends JFrame implements ActionListener {
    JPanel buttonsPanel;

    Map<String, String> lookAndFeelMaps = new HashMap<String, String>();
    public testFrame() throws InterruptedException {
        initMap();
        initComponents();

    }


    public void initMap()
    {
        String sLnF;
        String sMapKey;
        UIManager.LookAndFeelInfo[] lookAndFeel = UIManager.getInstalledLookAndFeels();
        for (UIManager.LookAndFeelInfo look : lookAndFeel) {

            sLnF = look.getClassName();
            sMapKey = sLnF.substring(sLnF.lastIndexOf(".")+1);
            sMapKey = sMapKey.replaceAll("LookAndFeel","");
            sMapKey = sMapKey.trim();

            lookAndFeelMaps.put(sMapKey, sLnF);

        }
    }


    public void initComponents() throws InterruptedException {
        JPanel p = new JPanel();
        buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS));

        JRadioButton rb  = new JRadioButton("JRadioButton");
        rb.setMargin(new Insets(20, 20, 20, 20));
        rb.setBackground(Color.green);
        rb.setAlignmentX(0.5f);
        buttonsPanel.add(rb);

        JCheckBox cb  = new JCheckBox("JCheckBox");
        cb.setMargin(new Insets(20, 20, 20, 20));
        cb.setBackground(Color.yellow);
        cb.setAlignmentX(0.5f);
        buttonsPanel.add(cb);

        getContentPane().add(buttonsPanel);

        for (Map.Entry mapElement : lookAndFeelMaps.entrySet()) {
            String btnName = mapElement.getKey().toString();
            JButton btn = new JButton(btnName);
            btn.setActionCommand(btnName);
            btn.addActionListener(this);
            p.add(btn);
        }

        getContentPane().add(p,BorderLayout.SOUTH);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(500, 300);
        setVisible(true);
        Thread.sleep(1000);
    }

    private static void setLookAndFeel(String laf) {
        try {
            UIManager.setLookAndFeel(laf);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported L&F: " + laf);
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void actionPerformed(ActionEvent e) {
        String key = e.getActionCommand();
        String val = lookAndFeelMaps.get(key);

        setLookAndFeel(val);
        SwingUtilities.updateComponentTreeUI(this);

    }
}
