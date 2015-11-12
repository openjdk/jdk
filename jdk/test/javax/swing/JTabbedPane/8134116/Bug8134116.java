
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleComponent;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.*;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

/*
 * @test
 * @bug 8134116
 * @summary JTabbedPane$Page.getBounds throws IndexOutOfBoundsException
 * @run main Bug8134116
 */
public class Bug8134116 {

    public static void main(String args[]) throws Exception {

        try {
            UIManager.setLookAndFeel(new NimbusLookAndFeel());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        SwingUtilities.invokeAndWait(() -> {
            JPanel panel0 = new JPanel();
            BadPane badPane = new BadPane();
            badPane.add("zero", panel0);
            badPane.add("one", null);
            JFrame frame = new JFrame();
            frame.add(badPane);
            frame.setSize(300, 300);
            frame.setVisible(true);

            AccessibleContext ac = badPane.getAccessibleContext();
            Accessible page0 = ac.getAccessibleChild(0);
            if (page0 == null) {
                // Not something being tested, but checking anyway
                throw new RuntimeException("getAccessibleChild(0) is null");
            }
            Accessible page1 = ac.getAccessibleChild(1);
            if (page1 == null) {
                // Not something being tested, but checking anyway
                throw new RuntimeException("getAccessibleChild(1) is null");
            }
            // page0 and page1 are a JTabbedPane.Page, a private inner class
            // and is an AccessibleContext
            // and implements Accessible and AccessibleComponent
            AccessibleContext pac0 = page0.getAccessibleContext();
            AccessibleContext pac1 = page1.getAccessibleContext();

            // the following would fail if JDK-8134116 fix not present

            // test Page.getBounds
            // ensure no IndexOutOfBoundsException
            pac0.getAccessibleComponent().getBounds();

            // test Page.getAccessibleStateSet
            // At this point page 0 is selected
            AccessibleStateSet accSS0 = pac0.getAccessibleStateSet();
            if (!accSS0.contains(AccessibleState.SELECTED)) {
                String msg = "Empty title -> AccessibleState.SELECTED not set";
                throw new RuntimeException(msg);
            }

            // test Page.getAccessibleIndexInParent
            if (pac0.getAccessibleIndexInParent() == -1) {
                String msg = "Empty title -> negative AccessibleIndexInParent";
                throw new RuntimeException(msg);
            }

            // test Page.getAccessibleName
            String accName = pac0.getAccessibleName();
            if (!accName.equals("zero")) {
                String msg = "Empty title -> empty AccessibleName";
                throw new RuntimeException(msg);
            }
            // test Page.getAccessibleName when component is null
            accName = pac1.getAccessibleName();
            if (!accName.equals("one")) {
                String msg = "AccessibleName of null panel not 'one'";
                throw new RuntimeException(msg);
            }

            // test Page.setDisplayedMnemonicIndex
            //  Empty title -> IllegalArgumnetException
            badPane.setDisplayedMnemonicIndexAt(0, 1);

            // test Page.updateDisplayedMnemonicIndex
            badPane.setMnemonicAt(0, KeyEvent.VK_Z);
            if (badPane.getDisplayedMnemonicIndexAt(0) == -1) {
                String msg="Empty title -> getDisplayedMnemonicIndexAt failure";
                throw new RuntimeException(msg);
            }
        });
    }

    // The following is likely what is being done in Burp Suite
    // https://portswigger.net/burp/ which fails in the same way, i.e. the
    // pages List in JTabbedPane is not being managed properly and thus
    // Page.title is "" for each page.  The overridden insertTab manages titles
    // in the subclass passing a "" title to the superclass JTabbedPane through
    // its insertTab.  Later an overridden getTitleAt returns the titles as
    // managed by the subclass.
    static class BadPane extends JTabbedPane {
        private List<String> titles;

        BadPane() {
            titles = new ArrayList<String>(1);
        }

        @Override
        public void insertTab( String title, Icon icon, Component component,
                               String tip, int index ) {
            titles.add(index, title);
            super.insertTab("", icon, component, tip, index);
        }

        @Override
        public String getTitleAt(int i) {
            return titles.get(i);
        }
    }

}
