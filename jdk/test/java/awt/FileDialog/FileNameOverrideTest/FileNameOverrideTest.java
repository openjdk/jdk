/*
  test
  @bug 6260659
  @summary File Name set programmatically in FileDialog is overridden during navigation, XToolkit
  @author Dmitry.Cherepanov@SUN.COM area=awt.filedialog
  @library ../../regtesthelpers
  @build Sysout
  @run applet/manual=yesno FileNameOverrideTest.html
*/

import test.java.awt.regtesthelpers.Sysout;

import java.applet.Applet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

public class FileNameOverrideTest extends Applet implements ActionListener {
    private final static String fileName = "input";
    private final static String clickDirName = "Directory for double click";
    private final static String dirPath = ".";
    private Button showBtn;
    private FileDialog fd;

    public void init() {
        this.setLayout(new GridLayout(1, 1));

        fd = new FileDialog(new Frame(), "Open");

        showBtn = new Button("Show File Dialog");
        showBtn.addActionListener(this);
        add(showBtn);

        try {
            File tmpFileUp = new File(dirPath + File.separator + fileName);
            File tmpDir = new File(dirPath + File.separator + clickDirName);
            File tmpFileIn = new File(tmpDir.getAbsolutePath() + File.separator + fileName);
            tmpDir.mkdir();
            tmpFileUp.createNewFile();
            tmpFileIn.createNewFile();
        } catch (IOException ex) {
            throw new RuntimeException("Cannot create test folder", ex);
        }

        String[] instructions = {
                "1) Click on 'Show File Dialog' button. A file dialog will come up.",
                "2) Double-click on '" + clickDirName + "' and click OK.",
                "3) See result of the test below"
        };
        Sysout.createDialogWithInstructions(instructions);
    }//End  init()

    public void start() {
        setSize(200, 200);
        show();
    }// start()

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == showBtn) {
            fd.setFile(fileName);
            fd.setDirectory(dirPath);
            fd.setVisible(true);
            String output = fd.getFile();
            if (fileName.equals(output)) {
                Sysout.println("TEST PASSED");
            } else {
                Sysout.println("TEST FAILED (output file - " + output + ")");
            }
        }
    }
}// class ManualYesNoTest
