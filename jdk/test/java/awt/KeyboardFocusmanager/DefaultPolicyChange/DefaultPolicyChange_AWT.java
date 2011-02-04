/*
  @test
  @bug 6741526
  @summary KeyboardFocusManager.setDefaultFocusTraversalPolicy(FocusTraversalPolicy) affects created components
  @library ../../regtesthelpers
  @build Sysout
  @author Andrei Dmitriev : area=awt-focus
  @run main DefaultPolicyChange_AWT
*/

import java.awt.*;
import test.java.awt.regtesthelpers.Sysout;

public class DefaultPolicyChange_AWT {
    public static void main(String []s) {
        DefaultPolicyChange_AWT.runTestAWT();
    }

    private static void runTestAWT(){
        KeyboardFocusManager currentKFM = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        FocusTraversalPolicy defaultFTP = currentKFM.getDefaultFocusTraversalPolicy();
        ContainerOrderFocusTraversalPolicy newFTP = new ContainerOrderFocusTraversalPolicy();

        Frame frame = new Frame();
        Window window = new Window(frame);

        FocusTraversalPolicy resultFTP = window.getFocusTraversalPolicy();
        Sysout.println("FocusTraversalPolicy on window = " + resultFTP);
        /**
         * Note: this call doesn't affect already created components as they have
         * their policy initialized. Only new components will use this policy as
         * their default policy.
         **/
        Sysout.println("Now will set another policy.");
        currentKFM.setDefaultFocusTraversalPolicy(newFTP);
        resultFTP = window.getFocusTraversalPolicy();
        if (!resultFTP.equals(defaultFTP)) {
            Sysout.println("Failure! FocusTraversalPolicy should not change");
            Sysout.println("Was: " + defaultFTP);
            Sysout.println("Become: " + resultFTP);
            throw new RuntimeException("Failure! FocusTraversalPolicy should not change");
        }
    }
}
