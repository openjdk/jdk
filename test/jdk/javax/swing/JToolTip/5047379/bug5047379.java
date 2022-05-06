/* @test
   @bug 5047379
   @summary Checks that tooltips are rendered properly
   @author Shannon Hickey
   @library ../../regtesthelpers
   @build Util
   @run main bug5047379
*/

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JToolTip;
import javax.swing.KeyStroke;
import javax.swing.plaf.basic.BasicToolTipUI;
import javax.swing.plaf.metal.MetalToolTipUI;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;


import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.KeyEvent;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Robot;

import java.util.HashMap;
import java.util.Map;


public class bug5047379 {
    private static final long TIMEOUT =  20 * 1000;
    static Robot testRobot;
    static toolTipTest testObj;
    static Map<String, String> lookAndFeelMaps = new HashMap<String, String>();

    public static void main(String[] args) throws Exception {
        initMap();

        testObj = new toolTipTest();
        testRobot = new Robot();
        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                try {
                    testObj.runTest();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        for (Map.Entry<String, String> value : lookAndFeelMaps.entrySet()) {

            if (value.getKey().equals("Nimbus")) {
                continue;
            }

            testRobot.setAutoDelay(50);
            testRobot.waitForIdle();
            Point movePoint = testObj.getButtonPoint(testObj.b);
            testRobot.mouseMove(movePoint.x, movePoint.y);
            testRobot.waitForIdle();

            long timeout = System.currentTimeMillis() + 9000;
            while (!testObj.isTooltipAdded && (System.currentTimeMillis() < timeout)) {
                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                }
            }
            testObj.handleToolTip(value.getKey(),value.getValue());

            Thread.sleep(1000);

        }
    }

    public static void initMap() {
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
};

class toolTipTest{
    volatile boolean isTooltipAdded;
    JFrame frame;
    JButton a;
    JButton b;
    JButton c;
    JButton d;

    void handleToolTip(String key, String Value) throws Exception {
        UIManager.setLookAndFeel(Value);
        SwingUtilities.updateComponentTreeUI(frame);

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    JToolTip tooltip = (JToolTip) Util.findSubComponent(
                            JFrame.getFrames()[0], "JToolTip");

                    BasicToolTipUI toolTipObj = null;

                    switch (key) {
                        case "Metal":
                            toolTipObj = (MetalToolTipUI) MetalToolTipUI.createUI(tooltip);
                            break;
                        case "WindowsClassic":
                        case "Windows":
                        case "Motif":
                            toolTipObj = (BasicToolTipUI) BasicToolTipUI.createUI(tooltip);
                            break;
                        default:
                            return;
                    }

                    if (tooltip == null) {
                        throw new RuntimeException("Tooltip has not been found for : "+key);
                    }
                    checkAcclString(toolTipObj,tooltip,key);

                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    void checkAcclString(BasicToolTipUI toolTipObj, JToolTip tooltip,String key) {
        toolTipObj.installUI(tooltip);

        if ((!"Ctrl+B".equals(toolTipObj.getAcceleratorString())) &&
                (!"Ctrl-B".equals(toolTipObj.getAcceleratorString()))) {
            throw new RuntimeException("Tooltip acceleration is not properly set, Key : "+key);
        }
    }

    Point getButtonPoint(JButton button) throws Exception {
        final Point[] result = new Point[1];

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                Point p = button.getLocationOnScreen();
                Dimension size = button.getSize();
                result[0] = new Point(p.x + size.width / 2, p.y + size.height / 2);
            }
        });
        return result[0];
    }

    void runTest() throws Exception {
        frame = new JFrame();
        JTextArea area = new JTextArea();
        JPanel p = new JPanel();
        JPanel resPanel = new JPanel(new FlowLayout());

        String text  = "Mouse is hover over button B for the \t\t\n";
        text+= "ToolTip to appear. Here is what should show\t\t\n";
        text+= "The word \\\"TEXT\\\" and then \\\"CTRL B\\\"\\n\"\t\t";
        text+= "\n";

        area.setText(text);
        area.setEditable(false);
        area.setFocusable(false);

        frame.add(area, BorderLayout.CENTER);

        p.setLayout(new GridLayout(1, 5));

        a = new JButton("A");
        a.setMnemonic(KeyEvent.VK_A);
        a.setToolTipText("TEXT");
        p.add(a);

        b = new JButton("B");
        b.setMnemonic(KeyEvent.VK_B);
        b.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl B"), "foo");
        b.setToolTipText("TEXT");
        p.add(b);

        c = new JButton("C");
        c.setMnemonic(KeyEvent.VK_C);
        c.setToolTipText("<html>TEXT");
        p.add(c);

        d = new JButton("D");
        d.setMnemonic(KeyEvent.VK_D);
        d.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ctrl D"), "foo");
        d.setToolTipText("<html>TEXT");
        p.add(d);
        frame.add(p, BorderLayout.NORTH);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();

        JLayeredPane layeredPane = (JLayeredPane) Util.findSubComponent(
                frame, "JLayeredPane");
        layeredPane.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent e) {
                isTooltipAdded = true;
            }
        });
        frame.setVisible(true);
    }
}
