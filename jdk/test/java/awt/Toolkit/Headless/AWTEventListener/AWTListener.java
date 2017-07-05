/*
  @test
  @bug 6738181
  @library ../../../regtesthelpers
  @build Sysout
  @summary Toolkit.getAWTEventListeners returns empty array
  @author andrei dmitriev: area=awt.headless
  @run main/othervm -Djava.awt.headless=true AWTListener
*/

/**
 * In a headless mode add a listener for container events.
 * Check if a single listener is still assigned to the Toolkit class.
 */

import java.awt.*;
import java.awt.event.*;
import test.java.awt.regtesthelpers.Sysout;

public class AWTListener {
    public static void main(String []s) {
        Toolkit toolkit = Toolkit.getDefaultToolkit();

        AWTEventListener orig = new AWTEventListener() {
                public void eventDispatched(AWTEvent event) { }
            };

        Sysout.println("Test: listener to add = " +orig);
        toolkit.addAWTEventListener(orig, AWTEvent.CONTAINER_EVENT_MASK);

        for (AWTEventListener l: toolkit.getAWTEventListeners()){
            Sysout.println("Test: listener = " +l+" ");
        }

        if ( toolkit.getAWTEventListeners().length == 0 ) {
            throw new RuntimeException("Case 1. An empty array returned unexpectedly");
        }

        for (AWTEventListener l: toolkit.getAWTEventListeners(AWTEvent.CONTAINER_EVENT_MASK)){
            Sysout.println("Test: listener = " +l);
         }

        if ( toolkit.getAWTEventListeners(AWTEvent.CONTAINER_EVENT_MASK).length == 0 ) {
            throw new RuntimeException("Case 2. An empty array returned unexpectedly");
        }
        Sysout.println("Test PASSED");
    }
}
