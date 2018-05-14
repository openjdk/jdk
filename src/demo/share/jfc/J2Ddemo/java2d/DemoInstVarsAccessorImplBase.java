package java2d;

import java.awt.Color;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JTabbedPane;

/**
 * The implementation of 'DemoInstVarsAccessor' interface with empty methods.
 * It is used, when some parts of the demo are executed as standalone applications
 * not creating 'J2Ddemo' instances, for example in 'TextureChooser.main',
 * 'DemoGroup.main', 'Surface.createDemoFrame'.
 */
public class DemoInstVarsAccessorImplBase implements DemoInstVarsAccessor {
    private JCheckBoxMenuItem printCB = new JCheckBoxMenuItem("Default Printer");

    @Override
    public GlobalControls getControls() {
        return null;
    }

    @Override
    public MemoryMonitor getMemoryMonitor() {
        return null;
    }

    @Override
    public PerformanceMonitor getPerformanceMonitor() {
        return null;
    }

    @Override
    public JTabbedPane getTabbedPane() {
        return null;
    }

    @Override
    public DemoGroup[] getGroup() {
        return null;
    }

    @Override
    public void setGroupColumns(int columns) {
    }

    @Override
    public JCheckBoxMenuItem getVerboseCB() {
        return null;
    }

    @Override
    public JCheckBoxMenuItem getCcthreadCB() {
        return null;
    }

    @Override
    public JCheckBoxMenuItem getPrintCB() {
        return printCB;
    }

    @Override
    public Color getBackgroundColor() {
        return null;
    }

    @Override
    public JCheckBoxMenuItem getMemoryCB() {
        return null;
    }

    @Override
    public JCheckBoxMenuItem getPerfCB() {
        return null;
    }

    @Override
    public Intro getIntro() {
        return null;
    }
}
