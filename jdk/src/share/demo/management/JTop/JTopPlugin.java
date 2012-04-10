/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


/*
 *
 * Example of a JConsole Plugin.  This loads JTop as a JConsole tab.
 *
 * @author Mandy Chung
 */

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.SwingWorker;

import com.sun.tools.jconsole.JConsoleContext;
import com.sun.tools.jconsole.JConsoleContext.ConnectionState;
import com.sun.tools.jconsole.JConsolePlugin;

/**
 * JTopPlugin is a subclass to com.sun.tools.jconsole.JConsolePlugin
 *
 * JTopPlugin is loaded and instantiated by JConsole.  One instance
 * is created for each window that JConsole creates. It listens to
 * the connected property change so that it will update JTop with
 * the valid MBeanServerConnection object.  JTop is a JPanel object
 * displaying the thread and its CPU usage information.
 */
public class JTopPlugin extends JConsolePlugin implements PropertyChangeListener
{
    private JTop jtop = null;
    private Map<String, JPanel> tabs = null;

    public JTopPlugin() {
        // register itself as a listener
        addContextPropertyChangeListener(this);
    }

    /*
     * Returns a JTop tab to be added in JConsole.
     */
    @Override
    public synchronized Map<String, JPanel> getTabs() {
        if (tabs == null) {
            jtop = new JTop();
            jtop.setMBeanServerConnection(
                getContext().getMBeanServerConnection());
            // use LinkedHashMap if you want a predictable order
            // of the tabs to be added in JConsole
            tabs = new LinkedHashMap<String, JPanel>();
            tabs.put("JTop", jtop);
        }
        return tabs;
    }

    /*
     * Returns a SwingWorker which is responsible for updating the JTop tab.
     */
    @Override
    public SwingWorker<?,?> newSwingWorker() {
        return jtop.newSwingWorker();
    }

    // You can implement the dispose() method if you need to release
    // any resource when the plugin instance is disposed when the JConsole
    // window is closed.
    //
    // public void dispose() {
    // }

    /*
     * Property listener to reset the MBeanServerConnection
     * at reconnection time.
     */
    @Override
    public void propertyChange(PropertyChangeEvent ev) {
        String prop = ev.getPropertyName();
        if (prop == JConsoleContext.CONNECTION_STATE_PROPERTY) {
            ConnectionState newState = (ConnectionState)ev.getNewValue();
            // JConsole supports disconnection and reconnection
            // The MBeanServerConnection will become invalid when
            // disconnected. Need to use the new MBeanServerConnection object
            // created at reconnection time.
            if (newState == ConnectionState.CONNECTED && jtop != null) {
                jtop.setMBeanServerConnection(
                    getContext().getMBeanServerConnection());
            }
        }
    }
}
