/*
  @test
  @bug 6271792 8227077
  @key headful
  @summary Tests that all the blocked frames stay below their modal blocker dialog.
  @run main ZOrderTest
*/

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Dialog;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

public class ZOrderTest
{
    private static Frame f1, f2, f3;
    private static Dialog d;

    private static int width=400, height=400;

    public static void main( String args[] ) throws Exception
    {
        try {
            f1 = new Frame("F1");
            f1.setBounds(0, 0, width, height);
            f1.setBackground(Color.RED);
            f1.setLayout(new BorderLayout());
            Button b = new Button("Click");
            b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    d.setVisible(true);
                }
            });
            f1.add(b, BorderLayout.SOUTH);
            f1.setVisible(true);

            f2 = new Frame("F2");
            f2.setBounds(width + 1, 0, width, height);
            f2.setBackground(Color.RED);
            f2.setVisible(true);

            f3 = new Frame("F3");
            f3.setBounds(width + 1, 0 + height + 1, width, height);
            f3.setBackground(Color.RED);
            f3.setVisible(true);

            d = new Dialog(f1, "D", true);
            d.setBounds(3 * width / 4, 3 * height / 4,
                    width / 2, height / 2);
            d.setBackground(Color.BLUE);

            Robot robot = new Robot();
            robot.setAutoDelay(100);

            Point bl = b.getLocationOnScreen();
            mouseClick(robot, bl.x + b.getBounds().width / 2, bl.y + b.getBounds().height / 2);

            clickFrame(robot, f1);
            Color color = robot.getPixelColor(d.getBounds().x + 10,
                    d.getBounds().y + d.getInsets().top + 10);
            if (!color.equals(Color.BLUE)) {
                System.out.println(color);
                throw new RuntimeException("Frame F1 is above its modal blocker dialog");
            }

            clickFrame(robot, f2);
            color = robot.getPixelColor(d.getBounds().x + d.getBounds().width - 10,
                    d.getBounds().y + d.getInsets().top + 10);
            if (!color.equals(Color.BLUE)) {
                throw new RuntimeException("Frame F2 is above its modal blocker dialog");
            }

            clickFrame(robot, f3);
            color = robot.getPixelColor(d.getBounds().x + d.getBounds().width - 10,
                    d.getBounds().y + d.getBounds().height - 10);
            if (!color.equals(Color.BLUE)) {
                throw new RuntimeException("Frame F3 is above its modal blocker dialog");
            }
        } finally {
            if (f1 != null) {
                f1.dispose();
            }
            if (f2 != null) {
                f2.dispose();
            }
            if (f3 != null) {
                f3.dispose();
            }
            if (d != null) {
                d.dispose();
            }
        }
    }

    private static void clickFrame(Robot robot, Frame frame) {
        Rectangle bounds = frame.getBounds();
        // click the title of frame
        mouseClick(robot, bounds.x + bounds.width / 2,
                bounds.y + frame.getInsets().top / 2 + 1);
        // click interior of frame
        mouseClick(robot, bounds.x + bounds.width / 2,
                bounds.y + frame.getInsets().top + 10);
    }

    private static void mouseClick(Robot robot, int x, int y) {
        robot.mouseMove(x, y);
        robot.waitForIdle();
        robot.delay(200);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        robot.delay(200);
    }
}

