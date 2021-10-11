package jdk.test.lib.manual;

import java.io.IOException;
import java.net.URL;

/**
 * This is part of the standard test machinery. It creates a dialog (with the
 * instructions), and is the interface for sending text messages to the user. To
 * print the instructions, send an array of strings to Sysout.createDialog
 * WithInstructions method. Put one line of instructions per array entry. To
 * display a message for the tester to see, simply call Sysout.println with the
 * string to be displayed. This mimics System.out.println but works within the
 * test harness as well as standalone.
 */

public class TestInstructionUIHelper {
    private static TestInstructionUI frame;

    public static void createDialogWithInstructions(String[] instructions, AbstractManualTest testclass) {
        frame = new TestInstructionUI("Instructions", testclass);
        println("Any messages for the tester will display here.");
    }

    public static void createDialogWithInstructions(URL url, AbstractManualTest testclass) throws IOException {
        frame = new TestInstructionUI(testclass.getClass().getSimpleName() + " Instructions", testclass);
        frame.printInstructions(url);
    }

    public static void println(String messageIn) {
        frame.displayMessage(messageIn);
    }

    public static void dispose(AbstractManualTest testClass) {
        TestInstructionUIHelper.println("Shutting down the Java process..");
        testClass.closeSwingSetDemo();
        frame.dispose();
        frame.dispose();
    }
}