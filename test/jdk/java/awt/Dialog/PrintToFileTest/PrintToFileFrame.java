import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.PrintJob;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class PrintToFileFrame extends Frame implements ActionListener {
    Button nativeDlg = new Button("Show print dialog");

    public PrintToFileFrame() {
        this.setLayout(new FlowLayout());
        add(nativeDlg);
        nativeDlg.addActionListener(this);

        setSize(300, 300);
    }

    @SuppressWarnings("removal")
    public void actionPerformed(ActionEvent ae) {
        if (System.getSecurityManager() == null) {
            throw new RuntimeException("Security manager isn't installed.");
        }

        try {
            System.getSecurityManager().checkPrintJobAccess();
            System.out.println("checkPrintJobAccess - OK");
        } catch (SecurityException e) {
            System.out.println("checkPrintJobAccess - ERROR " + e);
        }

        PrintJob printJob = getToolkit().getPrintJob(this, null, null);

        if (printJob != null) {
            System.out.println("Print Job: " + printJob);
        } else {
            System.out.println("Print Job is null.");
        }
    }
}
