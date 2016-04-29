/*
 * Copyright (c) 1995, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.applet;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import javax.print.attribute.*;
import java.applet.*;
import java.net.URL;
import java.net.SocketPermission;
import java.security.AccessController;
import java.security.PrivilegedAction;
import sun.awt.SunToolkit;
import sun.awt.AppContext;

/**
 * A frame to show the applet tag in.
 */
@SuppressWarnings("serial") // JDK-implementation class
final class TextFrame extends Frame {

    /**
     * Create the tag frame.
     */
    @SuppressWarnings("deprecation")
    TextFrame(int x, int y, String title, String text) {
        setTitle(title);
        TextArea txt = new TextArea(20, 60);
        txt.setText(text);
        txt.setEditable(false);

        add("Center", txt);

        Panel p = new Panel();
        add("South", p);
        Button b = new Button(amh.getMessage("button.dismiss", "Dismiss"));
        p.add(b);

        class ActionEventListener implements ActionListener {
            @Override
            public void actionPerformed(ActionEvent evt) {
                dispose();
            }
        }
        b.addActionListener(new ActionEventListener());

        pack();
        move(x, y);
        setVisible(true);

        WindowListener windowEventListener = new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent evt) {
                dispose();
            }
        };

        addWindowListener(windowEventListener);
    }
    private static AppletMessageHandler amh = new AppletMessageHandler("textframe");

}

/**
 * Lets us construct one using unix-style one shot behaviors.
 */
final class StdAppletViewerFactory implements AppletViewerFactory {

    @Override
    public AppletViewer createAppletViewer(int x, int y,
                                           URL doc, Hashtable<String, String> atts) {
        return new AppletViewer(x, y, doc, atts, System.out, this);
    }

    @Override
    public MenuBar getBaseMenuBar() {
        return new MenuBar();
    }

    @Override
    public boolean isStandalone() {
        return true;
    }
}

/**
 * The applet viewer makes it possible to run a Java applet without using a browser.
 * For details on the syntax that <B>appletviewer</B> supports, see
 * <a href="../../../docs/tooldocs/appletviewertags.html">AppletViewer Tags</a>.
 * (The document named appletviewertags.html in the JDK's docs/tooldocs directory,
 *  once the JDK docs have been installed.)
 */
@SuppressWarnings("serial") // JDK implementation class
public class AppletViewer extends Frame implements AppletContext, Printable {

    /**
     * Some constants...
     */
    private static String defaultSaveFile = "Applet.ser";

    /**
     * The panel in which the applet is being displayed.
     */
    AppletViewerPanel panel;

    /**
     * The status line.
     */
    Label label;

    /**
     * output status messages to this stream
     */

    PrintStream statusMsgStream;

    /**
     * For cloning
     */
    AppletViewerFactory factory;


    private final class UserActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent evt) {
            processUserAction(evt);
        }
    }

    /**
     * Create the applet viewer.
     */
    @SuppressWarnings("deprecation")
    public AppletViewer(int x, int y, URL doc, Hashtable<String, String> atts,
                        PrintStream statusMsgStream, AppletViewerFactory factory) {
        this.factory = factory;
        this.statusMsgStream = statusMsgStream;
        setTitle(amh.getMessage("tool.title", atts.get("code")));

        MenuBar mb = factory.getBaseMenuBar();

        Menu m = new Menu(amh.getMessage("menu.applet"));

        addMenuItem(m, "menuitem.restart");
        addMenuItem(m, "menuitem.reload");
        addMenuItem(m, "menuitem.stop");
        addMenuItem(m, "menuitem.save");
        addMenuItem(m, "menuitem.start");
        addMenuItem(m, "menuitem.clone");
        m.add(new MenuItem("-"));
        addMenuItem(m, "menuitem.tag");
        addMenuItem(m, "menuitem.info");
        addMenuItem(m, "menuitem.edit").disable();
        addMenuItem(m, "menuitem.encoding");
        m.add(new MenuItem("-"));
        addMenuItem(m, "menuitem.print");
        m.add(new MenuItem("-"));
        addMenuItem(m, "menuitem.props");
        m.add(new MenuItem("-"));
        addMenuItem(m, "menuitem.close");
        if (factory.isStandalone()) {
            addMenuItem(m, "menuitem.quit");
        }

        mb.add(m);

        setMenuBar(mb);

        add("Center", panel = new AppletViewerPanel(doc, atts));
        add("South", label = new Label(amh.getMessage("label.hello")));
        panel.init();
        appletPanels.addElement(panel);

        pack();
        move(x, y);
        setVisible(true);

        WindowListener windowEventListener = new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent evt) {
                appletClose();
            }

            @Override
            public void windowIconified(WindowEvent evt) {
                appletStop();
            }

            @Override
            public void windowDeiconified(WindowEvent evt) {
                appletStart();
            }
        };

        class AppletEventListener implements AppletListener
        {
            final Frame frame;

            public AppletEventListener(Frame frame)
            {
                this.frame = frame;
            }

            @Override
            @SuppressWarnings("deprecation")
            public void appletStateChanged(AppletEvent evt)
            {
                AppletPanel src = (AppletPanel)evt.getSource();

                switch (evt.getID()) {
                    case AppletPanel.APPLET_RESIZE: {
                        if(src != null) {
                            resize(preferredSize());
                            validate();
                        }
                        break;
                    }
                    case AppletPanel.APPLET_LOADING_COMPLETED: {
                        Applet a = src.getApplet(); // sun.applet.AppletPanel

                        // Fixed #4754451: Applet can have methods running on main
                        // thread event queue.
                        //
                        // The cause of this bug is that the frame of the applet
                        // is created in main thread group. Thus, when certain
                        // AWT/Swing events are generated, the events will be
                        // dispatched through the wrong event dispatch thread.
                        //
                        // To fix this, we rearrange the AppContext with the frame,
                        // so the proper event queue will be looked up.
                        //
                        // Swing also maintains a Frame list for the AppContext,
                        // so we will have to rearrange it as well.
                        //
                        if (a != null)
                            AppletPanel.changeFrameAppContext(frame, SunToolkit.targetToAppContext(a));
                        else
                            AppletPanel.changeFrameAppContext(frame, AppContext.getAppContext());

                        break;
                    }
                }
            }
        };

        addWindowListener(windowEventListener);
        panel.addAppletListener(new AppletEventListener(this));

        // Start the applet
        showStatus(amh.getMessage("status.start"));
        initEventQueue();
    }

    // XXX 99/9/10 probably should be "private"
    public MenuItem addMenuItem(Menu m, String s) {
        MenuItem mItem = new MenuItem(amh.getMessage(s));
        mItem.addActionListener(new UserActionListener());
        return m.add(mItem);
    }

    /**
     * Send the initial set of events to the appletviewer event queue.
     * On start-up the current behaviour is to load the applet and call
     * Applet.init() and Applet.start().
     */
    private void initEventQueue() {
        // appletviewer.send.event is an undocumented and unsupported system
        // property which is used exclusively for testing purposes.
        String eventList = System.getProperty("appletviewer.send.event");

        if (eventList == null) {
            // Add the standard events onto the event queue.
            panel.sendEvent(AppletPanel.APPLET_LOAD);
            panel.sendEvent(AppletPanel.APPLET_INIT);
            panel.sendEvent(AppletPanel.APPLET_START);
        } else {
            // We're testing AppletViewer.  Force the specified set of events
            // onto the event queue, wait for the events to be processed, and
            // exit.

            // The list of events that will be executed is provided as a
            // ","-separated list.  No error-checking will be done on the list.
            String [] events = splitSeparator(",", eventList);

            for (int i = 0; i < events.length; i++) {
                System.out.println("Adding event to queue: " + events[i]);
                if (events[i].equals("dispose"))
                    panel.sendEvent(AppletPanel.APPLET_DISPOSE);
                else if (events[i].equals("load"))
                    panel.sendEvent(AppletPanel.APPLET_LOAD);
                else if (events[i].equals("init"))
                    panel.sendEvent(AppletPanel.APPLET_INIT);
                else if (events[i].equals("start"))
                    panel.sendEvent(AppletPanel.APPLET_START);
                else if (events[i].equals("stop"))
                    panel.sendEvent(AppletPanel.APPLET_STOP);
                else if (events[i].equals("destroy"))
                    panel.sendEvent(AppletPanel.APPLET_DESTROY);
                else if (events[i].equals("quit"))
                    panel.sendEvent(AppletPanel.APPLET_QUIT);
                else if (events[i].equals("error"))
                    panel.sendEvent(AppletPanel.APPLET_ERROR);
                else
                    // non-fatal error if we get an unrecognized event
                    System.out.println("Unrecognized event name: " + events[i]);
            }

            while (!panel.emptyEventQueue()) ;
            appletSystemExit();
        }
    }

    /**
     * Split a string based on the presence of a specified separator.  Returns
     * an array of arbitrary length.  The end of each element in the array is
     * indicated by the separator of the end of the string.  If there is a
     * separator immediately before the end of the string, the final element
     * will be empty.  None of the strings will contain the separator.  Useful
     * when separating strings such as "foo/bar/bas" using separator "/".
     *
     * @param sep  The separator.
     * @param s    The string to split.
     * @return     An array of strings.  Each string in the array is determined
     *             by the location of the provided sep in the original string,
     *             s.  Whitespace not stripped.
     */
    private String [] splitSeparator(String sep, String s) {
        Vector<String> v = new Vector<>();
        int tokenStart = 0;
        int tokenEnd   = 0;

        while ((tokenEnd = s.indexOf(sep, tokenStart)) != -1) {
            v.addElement(s.substring(tokenStart, tokenEnd));
            tokenStart = tokenEnd+1;
        }
        // Add the final element.
        v.addElement(s.substring(tokenStart));

        String [] retVal = new String[v.size()];
        v.copyInto(retVal);
        return retVal;
    }

    /*
     * Methods for java.applet.AppletContext
     */

    private static Map<URL, AudioClip> audioClips = new HashMap<>();

    /**
     * Get an audio clip.
     */
    @Override
    public AudioClip getAudioClip(URL url) {
        checkConnect(url);
        synchronized (audioClips) {
            AudioClip clip = audioClips.get(url);
            if (clip == null) {
                audioClips.put(url, clip = new AppletAudioClip(url));
            }
            return clip;
        }
    }

    private static Map<URL, AppletImageRef> imageRefs = new HashMap<>();

    /**
     * Get an image.
     */
    @Override
    public Image getImage(URL url) {
        return getCachedImage(url);
    }

    /**
     * Get an image.
     */
    static Image getCachedImage(URL url) {
        // System.getSecurityManager().checkConnection(url.getHost(), url.getPort());
        synchronized (imageRefs) {
            AppletImageRef ref = imageRefs.get(url);
            if (ref == null) {
                ref = new AppletImageRef(url);
                imageRefs.put(url, ref);
            }
            return ref.get();
        }
    }

    /**
     * Flush the image cache.
     */
    static void flushImageCache() {
        imageRefs.clear();
    }

    static Vector<AppletPanel> appletPanels = new Vector<>();

    /**
     * Get an applet by name.
     */
    @Override
    public Applet getApplet(String name) {
        AppletSecurity security = (AppletSecurity)System.getSecurityManager();
        name = name.toLowerCase();
        SocketPermission panelSp =
            new SocketPermission(panel.getCodeBase().getHost(), "connect");
        for (Enumeration<AppletPanel> e = appletPanels.elements() ; e.hasMoreElements() ;) {
            AppletPanel p = e.nextElement();
            String param = p.getParameter("name");
            if (param != null) {
                param = param.toLowerCase();
            }
            if (name.equals(param) &&
                p.getDocumentBase().equals(panel.getDocumentBase())) {

                SocketPermission sp =
                    new SocketPermission(p.getCodeBase().getHost(), "connect");

                if (panelSp.implies(sp)) {
                    return p.applet;
                }
            }
        }
        return null;
    }

    /**
     * Return an enumeration of all the accessible
     * applets on this page.
     */
    @Override
    public Enumeration<Applet> getApplets() {
        AppletSecurity security = (AppletSecurity)System.getSecurityManager();
        Vector<Applet> v = new Vector<>();
        SocketPermission panelSp =
            new SocketPermission(panel.getCodeBase().getHost(), "connect");

        for (Enumeration<AppletPanel> e = appletPanels.elements() ; e.hasMoreElements() ;) {
            AppletPanel p = e.nextElement();
            if (p.getDocumentBase().equals(panel.getDocumentBase())) {

                SocketPermission sp =
                    new SocketPermission(p.getCodeBase().getHost(), "connect");
                if (panelSp.implies(sp)) {
                    v.addElement(p.applet);
                }
            }
        }
        return v.elements();
    }

    /**
     * Ignore.
     */
    @Override
    public void showDocument(URL url) {
    }

    /**
     * Ignore.
     */
    @Override
    public void showDocument(URL url, String target) {
    }

    /**
     * Show status.
     */
    @Override
    public void showStatus(String status) {
        label.setText(status);
    }

    @Override
    public void setStream(String key, InputStream stream)throws IOException{
        // We do nothing.
    }

    @Override
    public InputStream getStream(String key){
        // We do nothing.
        return null;
    }

    @Override
    public Iterator<String> getStreamKeys(){
        // We do nothing.
        return null;
    }

    /**
     * System parameters.
     */
    static Hashtable<String, String> systemParam = new Hashtable<>();

    static {
        systemParam.put("codebase", "codebase");
        systemParam.put("code", "code");
        systemParam.put("alt", "alt");
        systemParam.put("width", "width");
        systemParam.put("height", "height");
        systemParam.put("align", "align");
        systemParam.put("vspace", "vspace");
        systemParam.put("hspace", "hspace");
    }

    /**
     * Print the HTML tag.
     */
    public static void printTag(PrintStream out, Hashtable<String, String> atts) {
        out.print("<applet");

        String v = atts.get("codebase");
        if (v != null) {
            out.print(" codebase=\"" + v + "\"");
        }

        v = atts.get("code");
        if (v == null) {
            v = "applet.class";
        }
        out.print(" code=\"" + v + "\"");
        v = atts.get("width");
        if (v == null) {
            v = "150";
        }
        out.print(" width=" + v);

        v = atts.get("height");
        if (v == null) {
            v = "100";
        }
        out.print(" height=" + v);

        v = atts.get("name");
        if (v != null) {
            out.print(" name=\"" + v + "\"");
        }
        out.println(">");

        // A very slow sorting algorithm
        int len = atts.size();
        String params[] = new String[len];
        len = 0;
        for (Enumeration<String> e = atts.keys() ; e.hasMoreElements() ;) {
            String param = e.nextElement();
            int i = 0;
            for (; i < len ; i++) {
                if (params[i].compareTo(param) >= 0) {
                    break;
                }
            }
            System.arraycopy(params, i, params, i + 1, len - i);
            params[i] = param;
            len++;
        }

        for (int i = 0 ; i < len ; i++) {
            String param = params[i];
            if (systemParam.get(param) == null) {
                out.println("<param name=" + param +
                            " value=\"" + atts.get(param) + "\">");
            }
        }
        out.println("</applet>");
    }

    /**
     * Make sure the atrributes are uptodate.
     */
    @SuppressWarnings("deprecation")
    public void updateAtts() {
        Dimension d = panel.size();
        Insets in = panel.insets();
        panel.atts.put("width",
                       Integer.toString(d.width - (in.left + in.right)));
        panel.atts.put("height",
                       Integer.toString(d.height - (in.top + in.bottom)));
    }

    /**
     * Restart the applet.
     */
    void appletRestart() {
        panel.sendEvent(AppletPanel.APPLET_STOP);
        panel.sendEvent(AppletPanel.APPLET_DESTROY);
        panel.sendEvent(AppletPanel.APPLET_INIT);
        panel.sendEvent(AppletPanel.APPLET_START);
    }

    /**
     * Reload the applet.
     */
    void appletReload() {
        panel.sendEvent(AppletPanel.APPLET_STOP);
        panel.sendEvent(AppletPanel.APPLET_DESTROY);
        panel.sendEvent(AppletPanel.APPLET_DISPOSE);

        /**
         * Fixed #4501142: Classloader sharing policy doesn't
         * take "archive" into account. This will be overridden
         * by Java Plug-in.                     [stanleyh]
         */
        AppletPanel.flushClassLoader(panel.getClassLoaderCacheKey());

        /*
         * Make sure we don't have two threads running through the event queue
         * at the same time.
         */
        try {
            panel.joinAppletThread();
            panel.release();
        } catch (InterruptedException e) {
            return;   // abort the reload
        }

        panel.createAppletThread();
        panel.sendEvent(AppletPanel.APPLET_LOAD);
        panel.sendEvent(AppletPanel.APPLET_INIT);
        panel.sendEvent(AppletPanel.APPLET_START);
    }

    /**
     * Save the applet to a well known file (for now) as a serialized object
     */
    @SuppressWarnings("deprecation")
    void appletSave() {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {

            @Override
            public Object run() {
                // XXX: this privileged block should be made smaller
                // by initializing a private static variable with "user.dir"

                // Applet needs to be stopped for serialization to succeed.
                // Since panel.sendEvent only queues the event, there is a
                // chance that the event will not be processed before
                // serialization begins.  However, by sending the event before
                // FileDialog is created, enough time is given such that this
                // situation is unlikely to ever occur.

                panel.sendEvent(AppletPanel.APPLET_STOP);
                FileDialog fd = new FileDialog(AppletViewer.this,
                                               amh.getMessage("appletsave.filedialogtitle"),
                                               FileDialog.SAVE);
                // needed for a bug under Solaris...
                fd.setDirectory(System.getProperty("user.dir"));
                fd.setFile(defaultSaveFile);
                fd.show();
                String fname = fd.getFile();
                if (fname == null) {
                    // Restart applet if Save is cancelled.
                    panel.sendEvent(AppletPanel.APPLET_START);
                    return null;                // cancelled
                }
                String dname = fd.getDirectory();
                File file = new File(dname, fname);

                try (FileOutputStream fos = new FileOutputStream(file);
                     BufferedOutputStream bos = new BufferedOutputStream(fos);
                     ObjectOutputStream os = new ObjectOutputStream(bos)) {

                    showStatus(amh.getMessage("appletsave.err1", panel.applet.toString(), file.toString()));
                    os.writeObject(panel.applet);
                } catch (IOException ex) {
                    System.err.println(amh.getMessage("appletsave.err2", ex));
                } finally {
                    panel.sendEvent(AppletPanel.APPLET_START);
                }
                return null;
            }
        });
    }

    /**
     * Clone the viewer and the applet.
     */
    @SuppressWarnings("deprecation")
    void appletClone() {
        Point p = location();
        updateAtts();
        @SuppressWarnings("unchecked")
        Hashtable<String, String> tmp = (Hashtable<String, String>) panel.atts.clone();
        factory.createAppletViewer(p.x + XDELTA, p.y + YDELTA,
                                   panel.documentURL, tmp);
    }

    /**
     * Show the applet tag.
     */
    @SuppressWarnings("deprecation")
    void appletTag() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        updateAtts();
        printTag(new PrintStream(out), panel.atts);
        showStatus(amh.getMessage("applettag"));

        Point p = location();
        new TextFrame(p.x + XDELTA, p.y + YDELTA, amh.getMessage("applettag.textframe"), out.toString());
    }

    /**
     * Show the applet info.
     */
    @SuppressWarnings("deprecation")
    void appletInfo() {
        String str = panel.applet.getAppletInfo();
        if (str == null) {
            str = amh.getMessage("appletinfo.applet");
        }
        str += "\n\n";

        String atts[][] = panel.applet.getParameterInfo();
        if (atts != null) {
            for (int i = 0 ; i < atts.length ; i++) {
                str += atts[i][0] + " -- " + atts[i][1] + " -- " + atts[i][2] + "\n";
            }
        } else {
            str += amh.getMessage("appletinfo.param");
        }

        Point p = location();
        new TextFrame(p.x + XDELTA, p.y + YDELTA, amh.getMessage("appletinfo.textframe"), str);

    }

    /**
     * Show character encoding type
     */
    void appletCharacterEncoding() {
        showStatus(amh.getMessage("appletencoding", encoding));
    }

    /**
     * Edit the applet.
     */
    void appletEdit() {
    }

    /**
     * Print the applet.
     */
    void appletPrint() {
        PrinterJob pj = PrinterJob.getPrinterJob();

        if (pj != null) {
            PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
            if (pj.printDialog(aset)) {
                pj.setPrintable(this);
                try {
                    pj.print(aset);
                    statusMsgStream.println(amh.getMessage("appletprint.finish"));
                } catch (PrinterException e) {
                   statusMsgStream.println(amh.getMessage("appletprint.fail"));
                }
            } else {
                statusMsgStream.println(amh.getMessage("appletprint.cancel"));
            }
        } else {
            statusMsgStream.println(amh.getMessage("appletprint.fail"));
        }
    }

    @Override
    public int print(Graphics graphics, PageFormat pf, int pageIndex) {
        if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        } else {
            Graphics2D g2d = (Graphics2D)graphics;
            g2d.translate(pf.getImageableX(), pf.getImageableY());
            panel.applet.printAll(graphics);
            return Printable.PAGE_EXISTS;
        }
    }

    /**
     * Properties.
     */
    static AppletProps props;
    public static synchronized void networkProperties() {
        if (props == null) {
            props = new AppletProps();
        }
        props.addNotify();
        props.setVisible(true);
    }

    /**
     * Start the applet.
     */
    void appletStart() {
        panel.sendEvent(AppletPanel.APPLET_START);
    }

    /**
     * Stop the applet.
     */
    void appletStop() {
        panel.sendEvent(AppletPanel.APPLET_STOP);
    }

    /**
     * Shutdown a viewer.
     * Stop, Destroy, Dispose and Quit a viewer
     */
    private void appletShutdown(AppletPanel p) {
        p.sendEvent(AppletPanel.APPLET_STOP);
        p.sendEvent(AppletPanel.APPLET_DESTROY);
        p.sendEvent(AppletPanel.APPLET_DISPOSE);
        p.sendEvent(AppletPanel.APPLET_QUIT);
    }

    /**
     * Close this viewer.
     * Stop, Destroy, Dispose and Quit an AppletView, then
     * reclaim resources and exit the program if this is
     * the last applet.
     */
    void appletClose() {

        // The caller thread is event dispatch thread, so
        // spawn a new thread to avoid blocking the event queue
        // when calling appletShutdown.
        //
        final AppletPanel p = panel;

        new Thread(null, new Runnable()
        {
            @Override
            public void run()
            {
                appletShutdown(p);
                appletPanels.removeElement(p);
                dispose();

                if (countApplets() == 0) {
                    appletSystemExit();
                }
            }
        },
        "AppletCloser", 0, false).start();
    }

    /**
     * Exit the program.
     * Exit from the program (if not stand alone) - do no clean-up
     */
    private void appletSystemExit() {
        if (factory.isStandalone())
            System.exit(0);
    }

    /**
     * Quit all viewers.
     * Shutdown all viewers properly then
     * exit from the program (if not stand alone)
     */
    protected void appletQuit()
    {
        // The caller thread is event dispatch thread, so
        // spawn a new thread to avoid blocking the event queue
        // when calling appletShutdown.
        //
        new Thread(null, new Runnable()
        {
            @Override
            public void run()
            {
                for (Enumeration<AppletPanel> e = appletPanels.elements() ; e.hasMoreElements() ;) {
                    AppletPanel p = e.nextElement();
                    appletShutdown(p);
                }
                appletSystemExit();
            }
        },
         "AppletQuit", 0, false).start();
    }

    /**
     * Handle events.
     */
    public void processUserAction(ActionEvent evt) {

        String label = ((MenuItem)evt.getSource()).getLabel();

        if (amh.getMessage("menuitem.restart").equals(label)) {
            appletRestart();
            return;
        }

        if (amh.getMessage("menuitem.reload").equals(label)) {
            appletReload();
            return;
        }

        if (amh.getMessage("menuitem.clone").equals(label)) {
            appletClone();
            return;
        }

        if (amh.getMessage("menuitem.stop").equals(label)) {
            appletStop();
            return;
        }

        if (amh.getMessage("menuitem.save").equals(label)) {
            appletSave();
            return;
        }

        if (amh.getMessage("menuitem.start").equals(label)) {
            appletStart();
            return;
        }

        if (amh.getMessage("menuitem.tag").equals(label)) {
            appletTag();
            return;
        }

        if (amh.getMessage("menuitem.info").equals(label)) {
            appletInfo();
            return;
        }

        if (amh.getMessage("menuitem.encoding").equals(label)) {
            appletCharacterEncoding();
            return;
        }

        if (amh.getMessage("menuitem.edit").equals(label)) {
            appletEdit();
            return;
        }

        if (amh.getMessage("menuitem.print").equals(label)) {
            appletPrint();
            return;
        }

        if (amh.getMessage("menuitem.props").equals(label)) {
            networkProperties();
            return;
        }

        if (amh.getMessage("menuitem.close").equals(label)) {
            appletClose();
            return;
        }

        if (factory.isStandalone() && amh.getMessage("menuitem.quit").equals(label)) {
            appletQuit();
            return;
        }
        //statusMsgStream.println("evt = " + evt);
    }

    /**
     * How many applets are running?
     */

    public static int countApplets() {
        return appletPanels.size();
    }


    /**
     * The current character.
     */
    static int c;

    /**
     * Scan spaces.
     */
    public static void skipSpace(Reader in) throws IOException {
        while ((c >= 0) &&
               ((c == ' ') || (c == '\t') || (c == '\n') || (c == '\r'))) {
            c = in.read();
        }
    }

    /**
     * Scan identifier
     */
    public static String scanIdentifier(Reader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (((c >= 'a') && (c <= 'z')) ||
                ((c >= 'A') && (c <= 'Z')) ||
                ((c >= '0') && (c <= '9')) || (c == '_')) {
                sb.append((char) c);
                c = in.read();
            } else {
                return sb.toString();
            }
        }
    }

    /**
     * Scan tag
     */
    public static Hashtable<String, String> scanTag(Reader in) throws IOException {
        Hashtable<String, String> atts = new Hashtable<>();
        skipSpace(in);
        while (c >= 0 && c != '>') {
            String att = scanIdentifier(in);
            String val = "";
            skipSpace(in);
            if (c == '=') {
                int quote = -1;
                c = in.read();
                skipSpace(in);
                if ((c == '\'') || (c == '\"')) {
                    quote = c;
                    c = in.read();
                }
                StringBuilder sb = new StringBuilder();
                while ((c > 0) &&
                       (((quote < 0) && (c != ' ') && (c != '\t') &&
                         (c != '\n') && (c != '\r') && (c != '>'))
                        || ((quote >= 0) && (c != quote)))) {
                    sb.append((char) c);
                    c = in.read();
                }
                if (c == quote) {
                    c = in.read();
                }
                skipSpace(in);
                val = sb.toString();
            }
            //statusMsgStream.println("PUT " + att + " = '" + val + "'");
            if (! val.equals("")) {
                atts.put(att.toLowerCase(java.util.Locale.ENGLISH), val);
            }
            while (true) {
                if ((c == '>') || (c < 0) ||
                    ((c >= 'a') && (c <= 'z')) ||
                    ((c >= 'A') && (c <= 'Z')) ||
                    ((c >= '0') && (c <= '9')) || (c == '_'))
                    break;
                c = in.read();
            }
            //skipSpace(in);
        }
        return atts;
    }

    /* values used for placement of AppletViewer's frames */
    private static int x = 0;
    private static int y = 0;
    private static final int XDELTA = 30;
    private static final int YDELTA = XDELTA;

    static String encoding = null;

    private static Reader makeReader(InputStream is) {
        if (encoding != null) {
            try {
                return new BufferedReader(new InputStreamReader(is, encoding));
            } catch (IOException x) { }
        }
        InputStreamReader r = new InputStreamReader(is);
        encoding = r.getEncoding();
        return new BufferedReader(r);
    }

    /**
     * Scan an html file for {@code <applet>} tags
     */
    public static void parse(URL url, String enc) throws IOException {
        encoding = enc;
        parse(url, System.out, new StdAppletViewerFactory());
    }

    public static void parse(URL url) throws IOException {
        parse(url, System.out, new StdAppletViewerFactory());
    }

    public static void parse(URL url, PrintStream statusMsgStream,
                             AppletViewerFactory factory) throws IOException {
        // <OBJECT> <EMBED> tag flags
        boolean isAppletTag = false;
        boolean isObjectTag = false;
        boolean isEmbedTag = false;

        // warning messages
        String requiresNameWarning = amh.getMessage("parse.warning.requiresname");
        String paramOutsideWarning = amh.getMessage("parse.warning.paramoutside");
        String appletRequiresCodeWarning = amh.getMessage("parse.warning.applet.requirescode");
        String appletRequiresHeightWarning = amh.getMessage("parse.warning.applet.requiresheight");
        String appletRequiresWidthWarning = amh.getMessage("parse.warning.applet.requireswidth");
        String objectRequiresCodeWarning = amh.getMessage("parse.warning.object.requirescode");
        String objectRequiresHeightWarning = amh.getMessage("parse.warning.object.requiresheight");
        String objectRequiresWidthWarning = amh.getMessage("parse.warning.object.requireswidth");
        String embedRequiresCodeWarning = amh.getMessage("parse.warning.embed.requirescode");
        String embedRequiresHeightWarning = amh.getMessage("parse.warning.embed.requiresheight");
        String embedRequiresWidthWarning = amh.getMessage("parse.warning.embed.requireswidth");
        String appNotLongerSupportedWarning = amh.getMessage("parse.warning.appnotLongersupported");

        java.net.URLConnection conn = url.openConnection();
        Reader in = makeReader(conn.getInputStream());
        /* The original URL may have been redirected - this
         * sets it to whatever URL/codebase we ended up getting
         */
        url = conn.getURL();

        int ydisp = 1;
        Hashtable<String, String> atts = null;

        while(true) {
            c = in.read();
            if (c == -1)
                break;

            if (c == '<') {
                c = in.read();
                if (c == '/') {
                    c = in.read();
                    String nm = scanIdentifier(in);
                    if (nm.equalsIgnoreCase("applet") ||
                        nm.equalsIgnoreCase("object") ||
                        nm.equalsIgnoreCase("embed")) {

                        // We can't test for a code tag until </OBJECT>
                        // because it is a parameter, not an attribute.
                        if(isObjectTag) {
                            if (atts.get("code") == null && atts.get("object") == null) {
                                statusMsgStream.println(objectRequiresCodeWarning);
                                atts = null;
                            }
                        }

                        if (atts != null) {
                            // XXX 5/18 In general this code just simply
                            // shouldn't be part of parsing.  It's presence
                            // causes things to be a little too much of a
                            // hack.
                            factory.createAppletViewer(x, y, url, atts);
                            x += XDELTA;
                            y += YDELTA;
                            // make sure we don't go too far!
                            Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
                            if ((x > d.width - 300) || (y > d.height - 300)) {
                                x = 0;
                                y = 2 * ydisp * YDELTA;
                                ydisp++;
                            }
                        }
                        atts = null;
                        isAppletTag = false;
                        isObjectTag = false;
                        isEmbedTag = false;
                    }
                }
                else {
                    String nm = scanIdentifier(in);
                    if (nm.equalsIgnoreCase("param")) {
                        Hashtable<String, String> t = scanTag(in);
                        String att = t.get("name");
                        if (att == null) {
                            statusMsgStream.println(requiresNameWarning);
                        } else {
                            String val = t.get("value");
                            if (val == null) {
                                statusMsgStream.println(requiresNameWarning);
                            } else if (atts != null) {
                                atts.put(att.toLowerCase(), val);
                            } else {
                                statusMsgStream.println(paramOutsideWarning);
                            }
                        }
                    }
                    else if (nm.equalsIgnoreCase("applet")) {
                        isAppletTag = true;
                        atts = scanTag(in);
                        if (atts.get("code") == null && atts.get("object") == null) {
                            statusMsgStream.println(appletRequiresCodeWarning);
                            atts = null;
                        } else if (atts.get("width") == null) {
                            statusMsgStream.println(appletRequiresWidthWarning);
                            atts = null;
                        } else if (atts.get("height") == null) {
                            statusMsgStream.println(appletRequiresHeightWarning);
                            atts = null;
                        }
                    }
                    else if (nm.equalsIgnoreCase("object")) {
                        isObjectTag = true;
                        atts = scanTag(in);
                        // The <OBJECT> attribute codebase isn't what
                        // we want. If its defined, remove it.
                        if(atts.get("codebase") != null) {
                            atts.remove("codebase");
                        }

                        if (atts.get("width") == null) {
                            statusMsgStream.println(objectRequiresWidthWarning);
                            atts = null;
                        } else if (atts.get("height") == null) {
                            statusMsgStream.println(objectRequiresHeightWarning);
                            atts = null;
                        }
                    }
                    else if (nm.equalsIgnoreCase("embed")) {
                        isEmbedTag = true;
                        atts = scanTag(in);

                        if (atts.get("code") == null && atts.get("object") == null) {
                            statusMsgStream.println(embedRequiresCodeWarning);
                            atts = null;
                        } else if (atts.get("width") == null) {
                            statusMsgStream.println(embedRequiresWidthWarning);
                            atts = null;
                        } else if (atts.get("height") == null) {
                            statusMsgStream.println(embedRequiresHeightWarning);
                            atts = null;
                        }
                    }
                    else if (nm.equalsIgnoreCase("app")) {
                        statusMsgStream.println(appNotLongerSupportedWarning);
                        Hashtable<String, String> atts2 = scanTag(in);
                        nm = atts2.get("class");
                        if (nm != null) {
                            atts2.remove("class");
                            atts2.put("code", nm + ".class");
                        }
                        nm = atts2.get("src");
                        if (nm != null) {
                            atts2.remove("src");
                            atts2.put("codebase", nm);
                        }
                        if (atts2.get("width") == null) {
                            atts2.put("width", "100");
                        }
                        if (atts2.get("height") == null) {
                            atts2.put("height", "100");
                        }
                        printTag(statusMsgStream, atts2);
                        statusMsgStream.println();
                    }
                }
            }
        }
        in.close();
    }

    /**
     * Old main entry point.
     *
     * @deprecated
     */
    @Deprecated
    public static void main(String argv[]) {
        // re-route everything to the new main entry point
        Main.main(argv);
    }

    private static AppletMessageHandler amh = new AppletMessageHandler("appletviewer");

    private static void checkConnect(URL url)
    {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            try {
                java.security.Permission perm =
                    url.openConnection().getPermission();
                if (perm != null)
                    security.checkPermission(perm);
                else
                    security.checkConnect(url.getHost(), url.getPort());
            } catch (java.io.IOException ioe) {
                    security.checkConnect(url.getHost(), url.getPort());
            }
        }
    }
}
