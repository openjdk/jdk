package java2d;

import java.awt.Color;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JTabbedPane;

/**
 * The interface provides access to instance variables of 'J2Ddemo' object, which
 * were static variables of 'J2Ddemo' class read/written from various parts of the
 * demo classes. The interface is a part of the fix which changed static variables
 * for instance variables in certain demo classes.
 */
public interface DemoInstVarsAccessor {
    public GlobalControls getControls();
    public MemoryMonitor getMemoryMonitor();
    public PerformanceMonitor getPerformanceMonitor();
    public JTabbedPane getTabbedPane();
    public DemoGroup[] getGroup();
    public void setGroupColumns(int columns);
    public JCheckBoxMenuItem getVerboseCB();
    public JCheckBoxMenuItem getCcthreadCB();
    public JCheckBoxMenuItem getPrintCB();
    public Color getBackgroundColor();
    public JCheckBoxMenuItem getMemoryCB();
    public JCheckBoxMenuItem getPerfCB();
    public Intro getIntro();
}
