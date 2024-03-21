/*
  @test
  @bug 4690465
  @summary Tests that after dialog is hid on another EDT owning EDT gets notified.
  @author dom@sparc.spb.su: area=awt.focus
  @modules java.desktop/sun.awt
  @key headful
  @run applet JaWSTest.java
*/

/*
  <applet code=JaWSTest.class width=10 height=10></applet>
*/

import java.awt.*;
import java.awt.event.*;
import sun.awt.SunToolkit;
import sun.awt.AppContext;
import java.applet.Applet;

public class JaWSTest extends Applet implements ActionListener, Runnable {
    static Frame frame;
    static JaWSTest worker;
    static Dialog dummyDialog;
    static Object signalObject = new Object();
    static AppContext appContextObject = null;
    static Object exitLock = new Object();
    Button button = null;
    boolean dialogFinished = false;
    public void init() {
        worker = this;
        frame = new Frame("Main User Frame");
        button = new Button("Press To Save");
        button.addActionListener(worker);
        frame.add(button);
        frame.pack();
    }
    public void start() {
        frame.setVisible(true);
        Robot robot = null;
        try {
            robot = new Robot();
        } catch (Exception e) {
            throw new RuntimeException("Can't create robot");
        }
        robot.delay(1000);
        Point buttonLocation = button.getLocationOnScreen();
        robot.mouseMove(buttonLocation.x, buttonLocation.y);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        robot.delay(10000);
        if (!worker.dialogFinished) {
            throw new RuntimeException("Dialog thread is blocked");
        } else {
            frame.dispose();
        }
//          synchronized(exitObject) {
//              exitObject.wait();
//          }
    }

    public void actionPerformed(ActionEvent ae) {
        System.err.println("Action Performed");
        synchronized (signalObject) {
            ThreadGroup askUser = new ThreadGroup("askUser");
            final Thread handler = new Thread(askUser, worker, "userDialog");

            dummyDialog = new Dialog(frame, "Dummy Modal Dialog", true);
            dummyDialog.setBounds(200, 200, 100, 100);
            dummyDialog.addWindowListener(new WindowAdapter() {
                    public void windowOpened(WindowEvent we) {
                        System.err.println("handler is started");
                        handler.start();
                    }
                    public void windowClosing(WindowEvent e) {
                        dummyDialog.setVisible(false);
                    }
                });
            dummyDialog.setResizable(false);
            dummyDialog.toBack();
            System.err.println("Before First Modal");
            dummyDialog.setVisible(true);
            System.err.println("After First Modal");
            try {
                signalObject.wait();
            } catch (Exception e) {
                e.printStackTrace();
                dummyDialog.hide();
            }
            if (appContextObject != null) {
                System.err.println("before");
                appContextObject.dispose();
                System.err.println("after");
                appContextObject = null;
            }
            dummyDialog.dispose();
        }
        System.err.println("Show Something");
        dialogFinished = true;
//          synchronized(exitObject) {
//              exitObject.notify();
//          }
    }

    public void run() {
        System.err.println("Running");
        try {
            appContextObject = SunToolkit.createNewAppContext();
//              Frame localFrame = new Frame("Local Frame");
//              final Dialog localDialog = new Dialog(localFrame, "Local Dialog", true);
//              Button button = new Button("Press To Close");
//              button.addActionListener(new ActionListener() {
//                      public void actionPerformed(ActionEvent ae) {
//                          System.err.println("Hiding");
//                          localDialog.setVisible(false);
//                      }
//                  });
//              localDialog.add(button);
//              localDialog.pack();
//              localDialog.setVisible(true);
//              System.err.println("After Hiding");
       } finally {
           try {
               Thread.sleep(1000);
           } catch (InterruptedException ie) {
               ie.printStackTrace();
           }
           System.err.println("Before Hiding 1");
           dummyDialog.setVisible(false);
           System.err.println("Before Synchronized");
           synchronized (signalObject) {
               System.err.println("In Synchronized");
               signalObject.notify();
               System.err.println("After Notify");
           }
        }
        System.err.println("Stop Running");
    }
}
