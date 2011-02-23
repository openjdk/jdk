/*
  @test
  @bug 6741526
  @summary KeyboardFocusManager.setDefaultFocusTraversalPolicy(FocusTraversalPolicy) affects created components
  @library ../../regtesthelpers
  @build Sysout
  @author Andrei Dmitriev : area=awt-focus
  @run main DefaultPolicyChange_Swing
*/

import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import test.java.awt.regtesthelpers.Sysout;

public class DefaultPolicyChange_Swing {
    public static void main(String []s) {
        EventQueue.invokeLater(new Runnable(){
            public void run (){
                DefaultPolicyChange_Swing.runTestSwing();
            }
        });
    }
    private static void runTestSwing(){
        KeyboardFocusManager currentKFM = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        FocusTraversalPolicy defaultFTP = currentKFM.getDefaultFocusTraversalPolicy();
        ContainerOrderFocusTraversalPolicy newFTP = new ContainerOrderFocusTraversalPolicy();


        JFrame jf = new JFrame("Test1");
        JWindow jw = new JWindow(jf);
        JDialog jd = new JDialog(jf);
        JPanel jp1 = new JPanel();
        JButton jb1 = new JButton("jb1");
        JTable jt1 = new JTable(new DefaultTableModel());

        jf.add(jb1);
        jf.add(jt1);
        jf.add(jp1);
        System.out.println("FTP current on jf= " + jf.getFocusTraversalPolicy());
        System.out.println("FTP current on jw= " + jw.getFocusTraversalPolicy());
        System.out.println("FTP current on jd= " + jd.getFocusTraversalPolicy());

        if (!(jf.getFocusTraversalPolicy() instanceof LayoutFocusTraversalPolicy) ||
            !(jw.getFocusTraversalPolicy() instanceof LayoutFocusTraversalPolicy) ||
            !(jd.getFocusTraversalPolicy() instanceof LayoutFocusTraversalPolicy))
        {
            throw new RuntimeException("Failure! Swing toplevel must have LayoutFocusTraversalPolicy installed");
        }

        jf.setVisible(true);

        System.out.println("Now will set another policy.");
        currentKFM.setDefaultFocusTraversalPolicy(newFTP);

        FocusTraversalPolicy resultFTP = jw.getFocusTraversalPolicy();

        System.out.println("FTP current on jf= " + jf.getFocusTraversalPolicy());
        System.out.println("FTP current on jw= " + jw.getFocusTraversalPolicy());
        System.out.println("FTP current on jd= " + jd.getFocusTraversalPolicy());

        if (!resultFTP.equals(defaultFTP)) {
            Sysout.println("Failure! FocusTraversalPolicy should not change");
            Sysout.println("Was: " + defaultFTP);
            Sysout.println("Become: " + resultFTP);
            throw new RuntimeException("Failure! FocusTraversalPolicy should not change");
        }
    }
}
