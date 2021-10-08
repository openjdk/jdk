package jdk.test.lib.manual;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.imageio.ImageIO;

public abstract class AbstractManualTest {

    protected static boolean theTestPassed;

    protected static boolean testGeneratedInterrupt;

    protected static Thread mainThread;

    protected static int sleepTime = 600000;

    protected Process process = null;

    protected String testName;

    public AbstractManualTest() {
        process = execute();
    }

    public void fail() {
        theTestPassed = false;
        testGeneratedInterrupt = true;
    }

    public void pass() {
        theTestPassed = true;
        testGeneratedInterrupt = true;

    }

    public void comment(String text) {
        if (!theTestPassed) {
            System.out.println("Failure details : " + text);
        }
    }

    protected Process execute() {
        String javahome = System.getProperty("java.home");
        System.out.println("java.home =" + System.getProperty("java.home"));

        javahome = javahome + File.separator;
        System.out.println("javahome= " + javahome);
        String jarFile = javahome + "demo" + File.separator + "jfc" + File.separator + "SwingSet2" + File.separator
                + "SwingSet2.jar";
        java.io.File demoFile = new java.io.File(jarFile);
        if (!demoFile.exists()) {
            String errMessage = "ERROR: SwingDemo.jar is not found, \nlocation is " + jarFile
                    + ". \nPlease install demo into " + javahome + " and rerun test.";
            javax.swing.JOptionPane.showMessageDialog(null, errMessage, "ERROR", javax.swing.JOptionPane.ERROR_MESSAGE);
            System.out.println(errMessage);
            System.out.println("Will exit");
            System.exit(2);
        }
        String command = javahome + "bin" + File.separator + "java -jar " + jarFile;
        String lafProp = System.getProperty("swing.defaultlaf");
        if (lafProp == null) {
            command += " -Dswing.defaultlaf=" + lafProp;
        }
        System.out.println("command=" + command);
        ProcessBuilder pBuilder = new ProcessBuilder();
        Process p = null;

        try {
            pBuilder.command(command.split(" "));
            p = pBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return p;
    }

    protected void captureScreenShot() {
        try {
            Thread.sleep(120);
            Robot robot = new Robot();
            Rectangle rectangle = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage bufferedImage = robot.createScreenCapture(rectangle);
            String filePath = getClass().getResource("").getPath() + File.separator + getTestName() + "_failed"
                    + ".jpg";
            File file = new File(filePath);
            boolean status = ImageIO.write(bufferedImage, "jpg", file);
            System.out.println("Screen Captured ? " + status + " File Path :- " + file.getAbsolutePath());
        } catch (AWTException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveAndClose() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        int exitCode;
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            exitCode = process.waitFor();
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("\nExited with the code : " + exitCode);
        mainThread.interrupt();
    }

    public void closeSwingSetDemo() {
        try {
            if (System.getProperty("os.name").startsWith("Windows")) {
                Runtime.getRuntime()
                    .exec("wmic Path win32_process Where \"CommandLine Like '%SwingSet2.jar%'\" Call Terminate");
            else {
            Runtime.getRuntime()
                        .exec("ps -ef | grep -v grep | grep  SwingSet2.jar | cut -d' ' -f4 | xargs kill -9");
            }

        } catch (IOException e1) {
            System.err.print("Error while terminating the SwingSet2.jar " + e1.getMessage());
        }
    }

    abstract protected String getTestName();
}
