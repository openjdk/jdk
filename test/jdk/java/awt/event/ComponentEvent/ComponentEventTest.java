import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.List;
import java.awt.Panel;
import java.awt.Robot;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.util.ArrayList;

/*
 * @test
 * @key headful
 * @bug 8333403
 * @summary Test performs various operations to check components events are triggered properly.
 * @run main ComponentEventTest
 */
public class ComponentEventTest {

    private static Frame frame;
    private static Component[] components;
    private static boolean componentHidden = false;
    private static boolean componentShown = false;
    private static boolean componentMoved = false;
    private static boolean componentResized = false;
    private static ArrayList<ComponentEvent> events =
        new ArrayList<ComponentEvent>();

    private static final ComponentListener componentListener =
        new ComponentListener() {

            @Override
            public void componentShown(ComponentEvent e) {
                System.out.println("ComponentShown: " + e.getSource());
                componentShown = true;
                events.add(e);
            }

            @Override
            public void componentResized(ComponentEvent e) {
                System.out.println("ComponentResized: " + e.getSource());
                componentResized = true;
                events.add(e);
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                System.out.println("ComponentMoved: " + e.getSource());
                componentMoved = true;
                events.add(e);
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                System.out.println("ComponentHidden: " + e.getSource());
                componentHidden = true;
                events.add(e);
            }
        };

    private static void initializeGUI() {
        frame = new Frame("Component Event Test");
        frame.setLayout(new FlowLayout());

        Panel panel = new Panel();
        Button button = new Button("Button");
        Label label = new Label("Label");
        List list = new List();
        list.add("One");
        list.add("Two");
        list.add("Three");
        Choice choice = new Choice();
        choice.add("Red");
        choice.add("Orange");
        choice.add("Yellow");
        Checkbox checkbox = new Checkbox("Checkbox");
        Scrollbar scrollbar = new Scrollbar(Scrollbar.HORIZONTAL, 0, 1, 0, 255);
        TextField textfield = new TextField(15);
        TextArea textarea = new TextArea(5, 15);

        components = new Component[] { panel, button, label, list, choice,
            checkbox, scrollbar, textfield, textarea, frame };

        for (int i = 0; i < components.length - 1; i++) {
            components[i].addComponentListener(componentListener);
            frame.add(components[i]);
        }
        frame.addComponentListener(componentListener);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(100);
            robot.setAutoWaitForIdle(true);

            EventQueue.invokeAndWait(ComponentEventTest::initializeGUI);
            robot.waitForIdle();

            robot.mouseMove(
                components[0].getLocationOnScreen().x
                    + components[0].getSize().width / 2,
                components[0].getLocationOnScreen().y
                    + components[0].getSize().height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            // Hide all components and check if the ComponentEvent is triggered
            for (int i = 0; i < components.length; i++) {
                Component currentComponent = components[i];

                resetValues();
                EventQueue.invokeAndWait(() -> {
                    currentComponent.setVisible(false);
                    frame.invalidate();
                    frame.validate();
                });

                robot.waitForIdle();
                if (!componentHidden) {
                    throw new RuntimeException(
                        "FAIL: ComponentHidden not triggered for "
                            + currentComponent.getClass());
                }

                resetValues();
                EventQueue.invokeAndWait(() -> {
                    currentComponent.setVisible(false);
                    frame.invalidate();
                    frame.validate();
                });

                robot.waitForIdle();
                if (componentHidden) {
                    throw new RuntimeException(
                        "FAIL: ComponentHidden triggered when setVisible(false) "
                            + "called for a hidden "
                            + components[i].getClass());
                }

                resetValues();
                EventQueue.invokeAndWait(() -> {
                    currentComponent.setVisible(true);
                    frame.invalidate();
                    frame.validate();
                });

                robot.waitForIdle();
                if (!componentShown) {
                    throw new RuntimeException(
                        "FAIL: ComponentShown not triggered for "
                            + components[i].getClass());
                }

                resetValues();
                EventQueue.invokeAndWait(() -> {
                    currentComponent.setVisible(true);
                    frame.invalidate();
                    frame.validate();
                });

                robot.waitForIdle();
                if (componentShown) {
                    throw new RuntimeException(
                        "FAIL: ComponentShown triggered when setVisible(true) "
                            + "called for a shown " + components[i].getClass());
                }

                resetValues();
                EventQueue.invokeAndWait(() -> {
                    currentComponent.setLocation(
                        currentComponent.getLocation().x + 1,
                        currentComponent.getLocation().y);
                    frame.invalidate();
                    frame.validate();
                });

                robot.waitForIdle();
                if (!componentMoved) {
                    throw new RuntimeException(
                        "FAIL: ComponentMoved not triggered for "
                            + components[i].getClass());
                }

                resetValues();
                EventQueue.invokeAndWait(() -> {
                    currentComponent.setSize(
                        currentComponent.getSize().width + 1,
                        currentComponent.getSize().height);
                    frame.invalidate();
                    frame.validate();
                });

                robot.waitForIdle();
                if (!componentResized) {
                    throw new RuntimeException(
                        "FAIL: ComponentResized not triggered for "
                            + components[i].getClass());
                }

                // Disable the components and do the same set of operations

                EventQueue.invokeAndWait(() -> {
                    currentComponent.setEnabled(false);
                    frame.invalidate();
                    frame.validate();
                });
                robot.waitForIdle();

                resetValues();
                EventQueue.invokeAndWait(() -> {
                    currentComponent.setVisible(false);
                    frame.invalidate();
                    frame.validate();
                });

                robot.waitForIdle();
                if (!componentHidden) {
                    throw new RuntimeException(
                        "FAIL: ComponentHidden not triggered for disabled "
                            + components[i].getClass());
                }

                resetValues();
                EventQueue.invokeAndWait(() -> {
                    currentComponent.setVisible(false);
                    frame.invalidate();
                    frame.validate();
                });

                robot.waitForIdle();
                if (componentHidden) {
                    throw new RuntimeException(
                        "FAIL: ComponentHidden triggered when setVisible(false) "
                            + "called for a hidden disabled "
                            + components[i].getClass());
                }

                resetValues();
                EventQueue.invokeAndWait(() -> {
                    currentComponent.setVisible(true);
                    frame.invalidate();
                    frame.validate();
                });

                robot.waitForIdle();
                if (!componentShown) {
                    throw new RuntimeException(
                        "FAIL: ComponentShown not triggered for disabled "
                            + components[i].getClass());
                }

                resetValues();
                EventQueue.invokeAndWait(() -> {
                    currentComponent.setVisible(true);
                    frame.invalidate();
                    frame.validate();
                });

                robot.waitForIdle();
                if (componentShown) {
                    throw new RuntimeException(
                        "FAIL: ComponentShown triggered when setVisible(true) "
                            + "called for a shown disabled "
                            + components[i].getClass());
                }

                resetValues();
                EventQueue.invokeAndWait(() -> {
                    currentComponent.setLocation(
                        currentComponent.getLocation().x - 1,
                        currentComponent.getLocation().y);
                    frame.invalidate();
                    frame.validate();
                });

                robot.waitForIdle();
                if (!componentMoved) {
                    throw new RuntimeException(
                        "FAIL: ComponentMoved not triggered for disabled "
                            + components[i].getClass());
                }

                resetValues();
                EventQueue.invokeAndWait(() -> {
                    currentComponent.setSize(
                        currentComponent.getSize().width - 1,
                        currentComponent.getSize().height);
                    frame.invalidate();
                    frame.validate();
                });

                robot.waitForIdle();
                if (!componentResized) {
                    throw new RuntimeException(
                        "FAIL: ComponentResized not triggered for disabled "
                            + components[i].getClass());
                }
            }

            EventQueue.invokeAndWait(() -> {
                frame.dispose();
                frame.setVisible(true);
            });

            robot.waitForIdle();

            resetValues();
            EventQueue.invokeAndWait(() -> {
                frame.setExtendedState(Frame.ICONIFIED);
            });

            robot.waitForIdle();
            if (componentShown || componentHidden || componentMoved
                || componentResized) {
                System.err.print("Events triggered are: ");
                for (int j = 0; j < events.size();
                    System.err.print(events.get(j) + "; "), j++);
                System.err.println("");
                throw new RuntimeException(
                    "FAIL: ComponentEvent triggered when frame is iconified");
            }

            resetValues();
            EventQueue.invokeAndWait(() -> {
                frame.setExtendedState(Frame.NORMAL);
            });

            robot.waitForIdle();
            if (componentShown || componentHidden) {
                System.err.print("Events triggered are: ");
                for (int j = 0; j < events.size();
                    System.err.print(events.get(j) + "; "), j++);
                System.err.println("");
                throw new RuntimeException(
                    "FAIL: ComponentEvent triggered when frame is set to normal state");
            }

            System.out.println("Test PASSED");
        } finally {
            EventQueue.invokeAndWait(ComponentEventTest::disposeFrame);
        }
    }

    private static void resetValues() {
        componentShown = false;
        componentHidden = false;
        componentMoved = false;
        componentResized = false;
        events.clear();
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }
}
