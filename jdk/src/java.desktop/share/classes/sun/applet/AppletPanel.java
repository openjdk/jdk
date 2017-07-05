/*
 * Copyright (c) 1995, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.SocketPermission;
import java.net.URL;
import java.security.*;
import java.util.*;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import sun.awt.AWTAccessor;
import sun.awt.AppContext;
import sun.awt.EmbeddedFrame;
import sun.awt.SunToolkit;
import sun.awt.util.PerformanceLogger;
import sun.security.util.SecurityConstants;

/**
 * Applet panel class. The panel manages and manipulates the
 * applet as it is being loaded. It forks a separate thread in a new
 * thread group to call the applet's init(), start(), stop(), and
 * destroy() methods.
 *
 * @author      Arthur van Hoff
 */
@SuppressWarnings("serial") // JDK implementation class
public
abstract class AppletPanel extends Panel implements AppletStub, Runnable {

    /**
     * The applet (if loaded).
     */
    Applet applet;


    /**
     * The classloader for the applet.
     */
    protected AppletClassLoader loader;

    /* applet event ids */
    public static final int APPLET_DISPOSE = 0;
    public static final int APPLET_LOAD = 1;
    public static final int APPLET_INIT = 2;
    public static final int APPLET_START = 3;
    public static final int APPLET_STOP = 4;
    public static final int APPLET_DESTROY = 5;
    public static final int APPLET_QUIT = 6;
    public static final int APPLET_ERROR = 7;

    /* send to the parent to force relayout */
    public static final int APPLET_RESIZE = 51234;

    /* sent to a (distant) parent to indicate that the applet is being
     * loaded or as completed loading
     */
    public static final int APPLET_LOADING = 51235;
    public static final int APPLET_LOADING_COMPLETED = 51236;

    /**
     * The current status. One of:
     *    APPLET_DISPOSE,
     *    APPLET_LOAD,
     *    APPLET_INIT,
     *    APPLET_START,
     *    APPLET_STOP,
     *    APPLET_DESTROY,
     *    APPLET_ERROR.
     */
    protected int status;

    /**
     * The thread for the applet.
     */
    protected Thread handler;


    /**
     * The initial applet size.
     */
    Dimension defaultAppletSize = new Dimension(10, 10);

    /**
     * The current applet size.
     */
    Dimension currentAppletSize = new Dimension(10, 10);

    /**
     * The thread to use during applet loading
     */

    Thread loaderThread = null;

    /**
     * Flag to indicate that a loading has been cancelled
     */
    boolean loadAbortRequest = false;

    /* abstract classes */
    protected abstract String getCode();
    protected abstract String getJarFiles();

    @Override
    public abstract int    getWidth();
    @Override
    public abstract int    getHeight();
    public abstract boolean hasInitialFocus();

    private static int threadGroupNumber = 0;

    protected void setupAppletAppContext() {
        // do nothing
    }

    /*
     * Creates a thread to run the applet. This method is called
     * each time an applet is loaded and reloaded.
     */
    synchronized void createAppletThread() {
        // Create a thread group for the applet, and start a new
        // thread to load the applet.
        String nm = "applet-" + getCode();
        loader = getClassLoader(getCodeBase(), getClassLoaderCacheKey());
        loader.grab(); // Keep this puppy around!

        // 4668479: Option to turn off codebase lookup in AppletClassLoader
        // during resource requests. [stanley.ho]
        String param = getParameter("codebase_lookup");

        if (param != null && param.equals("false"))
            loader.setCodebaseLookup(false);
        else
            loader.setCodebaseLookup(true);


        ThreadGroup appletGroup = loader.getThreadGroup();
        handler = new Thread(appletGroup, this, "thread " + nm, 0, false);
        // set the context class loader for this thread
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    handler.setContextClassLoader(loader);
                    return null;
                }
            });
        handler.start();
    }

    void joinAppletThread() throws InterruptedException {
        if (handler != null) {
            handler.join();
            handler = null;
        }
    }

    void release() {
        if (loader != null) {
            loader.release();
            loader = null;
        }
    }

    /**
     * Construct an applet viewer and start the applet.
     */
    public void init() {
        try {
            // Get the width (if any)
            defaultAppletSize.width = getWidth();
            currentAppletSize.width = defaultAppletSize.width;

            // Get the height (if any)
            defaultAppletSize.height = getHeight();
            currentAppletSize.height = defaultAppletSize.height;

        } catch (NumberFormatException e) {
            // Turn on the error flag and let TagAppletPanel
            // do the right thing.
            status = APPLET_ERROR;
            showAppletStatus("badattribute.exception");
            showAppletLog("badattribute.exception");
            showAppletException(e);
        }

        setLayout(new BorderLayout());

        createAppletThread();
    }

    /**
     * Minimum size
     */
    @Override
    @SuppressWarnings("deprecation")
    public Dimension minimumSize() {
        return new Dimension(defaultAppletSize.width,
                             defaultAppletSize.height);
    }

    /**
     * Preferred size
     */
    @Override
    @SuppressWarnings("deprecation")
    public Dimension preferredSize() {
        return new Dimension(currentAppletSize.width,
                             currentAppletSize.height);
    }

    private AppletListener listeners;

    /**
     * AppletEvent Queue
     */
    private LinkedBlockingQueue<Integer> queue = null;

    public synchronized void addAppletListener(AppletListener l) {
        listeners = AppletEventMulticaster.add(listeners, l);
    }

    public synchronized void removeAppletListener(AppletListener l) {
        listeners = AppletEventMulticaster.remove(listeners, l);
    }

    /**
     * Dispatch event to the listeners..
     */
    public void dispatchAppletEvent(int id, Object argument) {
        //System.out.println("SEND= " + id);
        if (listeners != null) {
            AppletEvent evt = new AppletEvent(this, id, argument);
            listeners.appletStateChanged(evt);
        }
    }

    /**
     * Send an event. Queue it for execution by the handler thread.
     */
    public void sendEvent(int id) {
        synchronized(this) {
            if (queue == null) {
                //System.out.println("SEND0= " + id);
                queue = new LinkedBlockingQueue<>();
            }
            boolean inserted = queue.add(id);
            notifyAll();
        }
        if (id == APPLET_QUIT) {
            try {
                joinAppletThread(); // Let the applet event handler exit
            } catch (InterruptedException e) {
            }

            // AppletClassLoader.release() must be called by a Thread
            // not within the applet's ThreadGroup
            if (loader == null)
                loader = getClassLoader(getCodeBase(), getClassLoaderCacheKey());
            release();
        }
    }

    /**
     * Get an event from the queue.
     */
    synchronized AppletEvent getNextEvent() throws InterruptedException {
        while (queue == null || queue.isEmpty()) {
            wait();
        }
        int eventId = queue.take();
        return new AppletEvent(this, eventId, null);
    }

    boolean emptyEventQueue() {
        if ((queue == null) || (queue.isEmpty()))
            return true;
        else
            return false;
    }

    /**
     * This kludge is specific to get over AccessControlException thrown during
     * Applet.stop() or destroy() when static thread is suspended.  Set a flag
     * in AppletClassLoader to indicate that an
     * AccessControlException for RuntimePermission "modifyThread" or
     * "modifyThreadGroup" had occurred.
     */
     private void setExceptionStatus(AccessControlException e) {
     Permission p = e.getPermission();
     if (p instanceof RuntimePermission) {
         if (p.getName().startsWith("modifyThread")) {
             if (loader == null)
                 loader = getClassLoader(getCodeBase(), getClassLoaderCacheKey());
             loader.setExceptionStatus();
         }
     }
     }

    /**
     * Execute applet events.
     * Here is the state transition diagram
     *
     * <pre>{@literal
     *   Note: (XXX) is the action
     *         APPLET_XXX is the state
     *  (applet code loaded) --> APPLET_LOAD -- (applet init called)--> APPLET_INIT --
     *  (applet start called) --> APPLET_START -- (applet stop called) --> APPLET_STOP --
     *  (applet destroyed called) --> APPLET_DESTROY --> (applet gets disposed) -->
     *   APPLET_DISPOSE --> ...
     * }</pre>
     *
     * In the legacy lifecycle model. The applet gets loaded, inited and started. So it stays
     * in the APPLET_START state unless the applet goes away(refresh page or leave the page).
     * So the applet stop method called and the applet enters APPLET_STOP state. Then if the applet
     * is revisited, it will call applet start method and enter the APPLET_START state and stay there.
     *
     * In the modern lifecycle model. When the applet first time visited, it is same as legacy lifecycle
     * model. However, when the applet page goes away. It calls applet stop method and enters APPLET_STOP
     * state and then applet destroyed method gets called and enters APPLET_DESTROY state.
     *
     * This code is also called by AppletViewer. In AppletViewer "Restart" menu, the applet is jump from
     * APPLET_STOP to APPLET_DESTROY and to APPLET_INIT .
     *
     * Also, the applet can jump from APPLET_INIT state to APPLET_DESTROY (in Netscape/Mozilla case).
     * Same as APPLET_LOAD to
     * APPLET_DISPOSE since all of this are triggered by browser.
     *
     */
    @Override
    public void run() {

        Thread curThread = Thread.currentThread();
        if (curThread == loaderThread) {
            // if we are in the loader thread, cause
            // loading to occur.  We may exit this with
            // status being APPLET_DISPOSE, APPLET_ERROR,
            // or APPLET_LOAD
            runLoader();
            return;
        }

        boolean disposed = false;
        while (!disposed && !curThread.isInterrupted()) {
            AppletEvent evt;
            try {
                evt = getNextEvent();
            } catch (InterruptedException e) {
                showAppletStatus("bail");
                return;
            }

            //showAppletStatus("EVENT = " + evt.getID());
            try {
                switch (evt.getID()) {
                  case APPLET_LOAD:
                      if (!okToLoad()) {
                          break;
                      }
                      // This complexity allows loading of applets to be
                      // interruptable.  The actual thread loading runs
                      // in a separate thread, so it can be interrupted
                      // without harming the applet thread.
                      // So that we don't have to worry about
                      // concurrency issues, the main applet thread waits
                      // until the loader thread terminates.
                      // (one way or another).
                      if (loaderThread == null) {
                          setLoaderThread(new Thread(null, this,
                                          "AppletLoader", 0, false));
                          loaderThread.start();
                          // we get to go to sleep while this runs
                          loaderThread.join();
                          setLoaderThread(null);
                      } else {
                          // REMIND: issue an error -- this case should never
                          // occur.
                      }
                      break;

                  case APPLET_INIT:
                    // AppletViewer "Restart" will jump from destroy method to
                    // init, that is why we need to check status w/ APPLET_DESTROY
                      if (status != APPLET_LOAD && status != APPLET_DESTROY) {
                          showAppletStatus("notloaded");
                          break;
                      }
                      applet.resize(defaultAppletSize);

                      if (PerformanceLogger.loggingEnabled()) {
                          PerformanceLogger.setTime("Applet Init");
                          PerformanceLogger.outputLog();
                      }
                      applet.init();

                      //Need the default(fallback) font to be created in this AppContext
                      Font f = getFont();
                      if (f == null ||
                          "dialog".equals(f.getFamily().toLowerCase(Locale.ENGLISH)) &&
                          f.getSize() == 12 && f.getStyle() == Font.PLAIN) {
                          setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
                      }

                      // Validate the applet in event dispatch thread
                      // to avoid deadlock.
                      try {
                          final AppletPanel p = this;
                          Runnable r = new Runnable() {
                              @Override
                              public void run() {
                                  p.validate();
                              }
                          };
                          AWTAccessor.getEventQueueAccessor().invokeAndWait(applet, r);
                      }
                      catch(InterruptedException ie) {
                      }
                      catch(InvocationTargetException ite) {
                      }

                      status = APPLET_INIT;
                      showAppletStatus("inited");
                      break;

                  case APPLET_START:
                  {
                      if (status != APPLET_INIT && status != APPLET_STOP) {
                          showAppletStatus("notinited");
                          break;
                      }
                      applet.resize(currentAppletSize);
                      applet.start();

                      // Validate and show the applet in event dispatch thread
                      // to avoid deadlock.
                      try {
                          final AppletPanel p = this;
                          final Applet a = applet;
                          Runnable r = new Runnable() {
                              @Override
                              public void run() {
                                  p.validate();
                                  a.setVisible(true);

                                  // Fix for BugTraq ID 4041703.
                                  // Set the default focus for an applet.
                                  if (hasInitialFocus()) {
                                      setDefaultFocus();
                                  }
                              }
                          };
                          AWTAccessor.getEventQueueAccessor().invokeAndWait(applet, r);
                      }
                      catch(InterruptedException ie) {
                      }
                      catch(InvocationTargetException ite) {
                      }

                      status = APPLET_START;
                      showAppletStatus("started");
                      break;
                  }

                case APPLET_STOP:
                    if (status != APPLET_START) {
                        showAppletStatus("notstarted");
                        break;
                    }
                    status = APPLET_STOP;

                    // Hide the applet in event dispatch thread
                    // to avoid deadlock.
                    try {
                        final Applet a = applet;
                        Runnable r = new Runnable() {
                            @Override
                            public void run() {
                                a.setVisible(false);
                            }
                        };
                        AWTAccessor.getEventQueueAccessor().invokeAndWait(applet, r);
                    }
                    catch(InterruptedException ie) {
                    }
                    catch(InvocationTargetException ite) {
                    }


                    // During Applet.stop(), any AccessControlException on an involved Class remains in
                    // the "memory" of the AppletClassLoader.  If the same instance of the ClassLoader is
                    // reused, the same exception will occur during class loading.  Set the AppletClassLoader's
                    // exceptionStatusSet flag to allow recognition of what had happened
                    // when reusing AppletClassLoader object.
                    try {
                        applet.stop();
                    } catch (java.security.AccessControlException e) {
                        setExceptionStatus(e);
                        // rethrow exception to be handled as it normally would be.
                        throw e;
                    }
                    showAppletStatus("stopped");
                    break;

                case APPLET_DESTROY:
                    if (status != APPLET_STOP && status != APPLET_INIT) {
                        showAppletStatus("notstopped");
                        break;
                    }
                    status = APPLET_DESTROY;

                    // During Applet.destroy(), any AccessControlException on an involved Class remains in
                    // the "memory" of the AppletClassLoader.  If the same instance of the ClassLoader is
                    // reused, the same exception will occur during class loading.  Set the AppletClassLoader's
                    // exceptionStatusSet flag to allow recognition of what had happened
                    // when reusing AppletClassLoader object.
                    try {
                        applet.destroy();
                    } catch (java.security.AccessControlException e) {
                        setExceptionStatus(e);
                        // rethrow exception to be handled as it normally would be.
                        throw e;
                    }
                    showAppletStatus("destroyed");
                    break;

                case APPLET_DISPOSE:
                    if (status != APPLET_DESTROY && status != APPLET_LOAD) {
                        showAppletStatus("notdestroyed");
                        break;
                    }
                    status = APPLET_DISPOSE;

                    try {
                        final Applet a = applet;
                        Runnable r = new Runnable() {
                            @Override
                            public void run() {
                                remove(a);
                            }
                        };
                        AWTAccessor.getEventQueueAccessor().invokeAndWait(applet, r);
                    }
                    catch(InterruptedException ie)
                    {
                    }
                    catch(InvocationTargetException ite)
                    {
                    }
                    applet = null;
                    showAppletStatus("disposed");
                    disposed = true;
                    break;

                case APPLET_QUIT:
                    return;
                }
            } catch (Exception e) {
                status = APPLET_ERROR;
                if (e.getMessage() != null) {
                    showAppletStatus("exception2", e.getClass().getName(),
                                     e.getMessage());
                } else {
                    showAppletStatus("exception", e.getClass().getName());
                }
                showAppletException(e);
            } catch (ThreadDeath e) {
                showAppletStatus("death");
                return;
            } catch (Error e) {
                status = APPLET_ERROR;
                if (e.getMessage() != null) {
                    showAppletStatus("error2", e.getClass().getName(),
                                     e.getMessage());
                } else {
                    showAppletStatus("error", e.getClass().getName());
                }
                showAppletException(e);
            }
            clearLoadAbortRequest();
        }
    }

    /**
     * Gets most recent focus owner component associated with the given window.
     * It does that without calling Window.getMostRecentFocusOwner since it
     * provides its own logic contradicting with setDefautlFocus. Instead, it
     * calls KeyboardFocusManager directly.
     */
    private Component getMostRecentFocusOwnerForWindow(Window w) {
        Method meth = AccessController.doPrivileged(
            new PrivilegedAction<Method>() {
                @Override
                public Method run() {
                    Method meth = null;
                    try {
                        meth = KeyboardFocusManager.class.getDeclaredMethod(
                                "getMostRecentFocusOwner",
                                new Class<?>[]{Window.class});
                        meth.setAccessible(true);
                    } catch (Exception e) {
                        // Must never happen
                        e.printStackTrace();
                    }
                    return meth;
                }
            });
        if (meth != null) {
            // Meth refers static method
            try {
                return (Component)meth.invoke(null, new Object[] {w});
            } catch (Exception e) {
                // Must never happen
                e.printStackTrace();
            }
        }
        // Will get here if exception was thrown or meth is null
        return w.getMostRecentFocusOwner();
    }

    /*
     * Fix for BugTraq ID 4041703.
     * Set the focus to a reasonable default for an Applet.
     */
    private void setDefaultFocus() {
        Component toFocus = null;
        Container parent = getParent();

        if(parent != null) {
            if (parent instanceof Window) {
                toFocus = getMostRecentFocusOwnerForWindow((Window)parent);
                if (toFocus == parent || toFocus == null) {
                    toFocus = parent.getFocusTraversalPolicy().
                        getInitialComponent((Window)parent);
                }
            } else if (parent.isFocusCycleRoot()) {
                toFocus = parent.getFocusTraversalPolicy().
                    getDefaultComponent(parent);
            }
        }

        if (toFocus != null) {
            if (parent instanceof EmbeddedFrame) {
                ((EmbeddedFrame) parent).synthesizeWindowActivation(true);
            }
            // EmbeddedFrame might have focus before the applet was added.
            // Thus after its activation the most recent focus owner will be
            // restored. We need the applet's initial focusabled component to
            // be focused here.
            toFocus.requestFocusInWindow();
        }
    }

    /**
     * Load the applet into memory.
     * Runs in a seperate (and interruptible) thread from the rest of the
     * applet event processing so that it can be gracefully interrupted from
     * things like HotJava.
     */
    @SuppressWarnings("deprecation")
    private void runLoader() {
        if (status != APPLET_DISPOSE) {
            showAppletStatus("notdisposed");
            return;
        }

        dispatchAppletEvent(APPLET_LOADING, null);

        // REMIND -- might be cool to visually indicate loading here --
        // maybe do animation?
        status = APPLET_LOAD;

        // Create a class loader
        loader = getClassLoader(getCodeBase(), getClassLoaderCacheKey());

        // Load the archives if present.
        // REMIND - this probably should be done in a separate thread,
        // or at least the additional archives (epll).

        String code = getCode();

        // setup applet AppContext
        // this must be called before loadJarFiles
        setupAppletAppContext();

        try {
            loadJarFiles(loader);
            applet = createApplet(loader);
        } catch (ClassNotFoundException e) {
            status = APPLET_ERROR;
            showAppletStatus("notfound", code);
            showAppletLog("notfound", code);
            showAppletException(e);
            return;
        } catch (InstantiationException e) {
            status = APPLET_ERROR;
            showAppletStatus("nocreate", code);
            showAppletLog("nocreate", code);
            showAppletException(e);
            return;
        } catch (IllegalAccessException e) {
            status = APPLET_ERROR;
            showAppletStatus("noconstruct", code);
            showAppletLog("noconstruct", code);
            showAppletException(e);
            // sbb -- I added a return here
            return;
        } catch (Exception e) {
            status = APPLET_ERROR;
            showAppletStatus("exception", e.getMessage());
            showAppletException(e);
            return;
        } catch (ThreadDeath e) {
            status = APPLET_ERROR;
            showAppletStatus("death");
            return;
        } catch (Error e) {
            status = APPLET_ERROR;
            showAppletStatus("error", e.getMessage());
            showAppletException(e);
            return;
        } finally {
            // notify that loading is no longer going on
            dispatchAppletEvent(APPLET_LOADING_COMPLETED, null);
        }

        // Fixed #4508194: NullPointerException thrown during
        // quick page switch
        //
        if (applet != null)
        {
            // Stick it in the frame
            applet.setStub(this);
            applet.hide();
            add("Center", applet);
            showAppletStatus("loaded");
            validate();
        }
    }

    protected Applet createApplet(final AppletClassLoader loader) throws ClassNotFoundException,
                                                                         IllegalAccessException, IOException, InstantiationException, InterruptedException {
        String code = getCode();

        if (code != null) {
            applet = (Applet)loader.loadCode(code).newInstance();
        } else {
            String msg = "nocode";
            status = APPLET_ERROR;
            showAppletStatus(msg);
            showAppletLog(msg);
            repaint();
        }

        // Determine the JDK level that the applet targets.
        // This is critical for enabling certain backward
        // compatibility switch if an applet is a JDK 1.1
        // applet. [stanley.ho]
        findAppletJDKLevel(applet);

        if (Thread.interrupted()) {
            try {
                status = APPLET_DISPOSE; // APPLET_ERROR?
                applet = null;
                // REMIND: This may not be exactly the right thing: the
                // status is set by the stop button and not necessarily
                // here.
                showAppletStatus("death");
            } finally {
                Thread.currentThread().interrupt(); // resignal interrupt
            }
            return null;
        }
        return applet;
    }

    protected void loadJarFiles(AppletClassLoader loader) throws IOException,
                                                                 InterruptedException {
        // Load the archives if present.
        // REMIND - this probably should be done in a separate thread,
        // or at least the additional archives (epll).
        String jarFiles = getJarFiles();

        if (jarFiles != null) {
            StringTokenizer st = new StringTokenizer(jarFiles, ",", false);
            while(st.hasMoreTokens()) {
                String tok = st.nextToken().trim();
                try {
                    loader.addJar(tok);
                } catch (IllegalArgumentException e) {
                    // bad archive name
                    continue;
                }
            }
        }
    }

    /**
     * Request that the loading of the applet be stopped.
     */
    protected synchronized void stopLoading() {
        // REMIND: fill in the body
        if (loaderThread != null) {
            //System.out.println("Interrupting applet loader thread: " + loaderThread);
            loaderThread.interrupt();
        } else {
            setLoadAbortRequest();
        }
    }


    protected synchronized boolean okToLoad() {
        return !loadAbortRequest;
    }

    protected synchronized void clearLoadAbortRequest() {
        loadAbortRequest = false;
    }

    protected synchronized void setLoadAbortRequest() {
        loadAbortRequest = true;
    }


    private synchronized void setLoaderThread(Thread loaderThread) {
        this.loaderThread = loaderThread;
    }

    /**
     * Return true when the applet has been started.
     */
    @Override
    public boolean isActive() {
        return status == APPLET_START;
    }


    private EventQueue appEvtQ = null;
    /**
     * Is called when the applet wants to be resized.
     */
    @Override
    public void appletResize(int width, int height) {
        currentAppletSize.width = width;
        currentAppletSize.height = height;
        final Dimension currentSize = new Dimension(currentAppletSize.width,
                                                    currentAppletSize.height);

        if(loader != null) {
            AppContext appCtxt = loader.getAppContext();
            if(appCtxt != null)
                appEvtQ = (java.awt.EventQueue)appCtxt.get(AppContext.EVENT_QUEUE_KEY);
        }

        final AppletPanel ap = this;
        if (appEvtQ != null){
            appEvtQ.postEvent(new InvocationEvent(Toolkit.getDefaultToolkit(),
                                                  new Runnable() {
                                                      @Override
                                                      public void run() {
                                                          if (ap != null) {
                                                              ap.dispatchAppletEvent(
                                                                      APPLET_RESIZE,
                                                                      currentSize);
                                                          }
                                                      }
                                                  }));
        }
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        currentAppletSize.width = width;
        currentAppletSize.height = height;
    }

    public Applet getApplet() {
        return applet;
    }

    /**
     * Status line. Called by the AppletPanel to provide
     * feedback on the Applet's state.
     */
    protected void showAppletStatus(String status) {
        getAppletContext().showStatus(amh.getMessage(status));
    }

    protected void showAppletStatus(String status, Object arg) {
        getAppletContext().showStatus(amh.getMessage(status, arg));
    }
    protected void showAppletStatus(String status, Object arg1, Object arg2) {
        getAppletContext().showStatus(amh.getMessage(status, arg1, arg2));
    }

    /**
     * Called by the AppletPanel to print to the log.
     */
    protected void showAppletLog(String msg) {
        System.out.println(amh.getMessage(msg));
    }

    protected void showAppletLog(String msg, Object arg) {
        System.out.println(amh.getMessage(msg, arg));
    }

    /**
     * Called by the AppletPanel to provide
     * feedback when an exception has happened.
     */
    protected void showAppletException(Throwable t) {
        t.printStackTrace();
        repaint();
    }

    /**
     * Get caching key for classloader cache
     */
    public String getClassLoaderCacheKey()
    {
        /**
         * Fixed #4501142: Classloader sharing policy doesn't
         * take "archive" into account. This will be overridden
         * by Java Plug-in.                     [stanleyh]
         */
        return getCodeBase().toString();
    }

    /**
     * The class loaders
     */
    private static HashMap<String, AppletClassLoader> classloaders = new HashMap<>();

    /**
     * Flush a class loader.
     */
    public static synchronized void flushClassLoader(String key) {
        classloaders.remove(key);
    }

    /**
     * Flush all class loaders.
     */
    public static synchronized void flushClassLoaders() {
        classloaders = new HashMap<>();
    }

    /**
     * This method actually creates an AppletClassLoader.
     *
     * It can be override by subclasses (such as the Plug-in)
     * to provide different classloaders.
     */
    protected AppletClassLoader createClassLoader(final URL codebase) {
        return new AppletClassLoader(codebase);
    }

    /**
     * Get a class loader. Create in a restricted context
     */
    synchronized AppletClassLoader getClassLoader(final URL codebase, final String key) {
        AppletClassLoader c = classloaders.get(key);
        if (c == null) {
            AccessControlContext acc =
                getAccessControlContext(codebase);
            c = AccessController.doPrivileged(
                    new PrivilegedAction<AppletClassLoader>() {
                        @Override
                        public AppletClassLoader run() {
                            AppletClassLoader ac = createClassLoader(codebase);
                            /* Should the creation of the classloader be
                             * within the class synchronized block?  Since
                             * this class is used by the plugin, take care
                             * to avoid deadlocks, or specialize
                             * AppletPanel within the plugin.  It may take
                             * an arbitrary amount of time to create a
                             * class loader (involving getting Jar files
                             * etc.) and may block unrelated applets from
                             * finishing createAppletThread (due to the
                             * class synchronization). If
                             * createAppletThread does not finish quickly,
                             * the applet cannot process other messages,
                             * particularly messages such as destroy
                             * (which timeout when called from the browser).
                             */
                            synchronized (getClass()) {
                                AppletClassLoader res = classloaders.get(key);
                                if (res == null) {
                                    classloaders.put(key, ac);
                                    return ac;
                                } else {
                                    return res;
                                }
                            }
                        }
                    },acc);
        }
        return c;
    }

    /**
     * get the context for the AppletClassLoader we are creating.
     * the context is granted permission to create the class loader,
     * connnect to the codebase, and whatever else the policy grants
     * to all codebases.
     */
    private AccessControlContext getAccessControlContext(final URL codebase) {

        PermissionCollection perms = AccessController.doPrivileged(
                new PrivilegedAction<PermissionCollection>() {
                    @Override
                    public PermissionCollection run() {
                        Policy p = java.security.Policy.getPolicy();
                        if (p != null) {
                            return p.getPermissions(new CodeSource(null,
                                                                   (java.security.cert.Certificate[]) null));
                        } else {
                            return null;
                        }
                    }
                });

        if (perms == null)
            perms = new Permissions();

        //XXX: this is needed to be able to create the classloader itself!

        perms.add(SecurityConstants.CREATE_CLASSLOADER_PERMISSION);

        Permission p;
        java.net.URLConnection urlConnection = null;
        try {
            urlConnection = codebase.openConnection();
            p = urlConnection.getPermission();
        } catch (java.io.IOException ioe) {
            p = null;
        }

        if (p != null)
            perms.add(p);

        if (p instanceof FilePermission) {

            String path = p.getName();

            int endIndex = path.lastIndexOf(File.separatorChar);

            if (endIndex != -1) {
                path = path.substring(0, endIndex+1);

                if (path.endsWith(File.separator)) {
                    path += "-";
                }
                perms.add(new FilePermission(path,
                                             SecurityConstants.FILE_READ_ACTION));
            }
        } else {
            URL locUrl = codebase;
            if (urlConnection instanceof JarURLConnection) {
                locUrl = ((JarURLConnection)urlConnection).getJarFileURL();
            }
            String host = locUrl.getHost();
            if (host != null && (host.length() > 0))
                perms.add(new SocketPermission(host,
                                               SecurityConstants.SOCKET_CONNECT_ACCEPT_ACTION));
        }

        ProtectionDomain domain =
            new ProtectionDomain(new CodeSource(codebase,
                                                (java.security.cert.Certificate[]) null), perms);
        AccessControlContext acc =
            new AccessControlContext(new ProtectionDomain[] { domain });

        return acc;
    }

    public Thread getAppletHandlerThread() {
        return handler;
    }

    public int getAppletWidth() {
        return currentAppletSize.width;
    }

    public int getAppletHeight() {
        return currentAppletSize.height;
    }

    public static void changeFrameAppContext(Frame frame, AppContext newAppContext)
    {
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

        // Check if frame's AppContext has already been set properly
        AppContext oldAppContext = SunToolkit.targetToAppContext(frame);

        if (oldAppContext == newAppContext)
            return;

        // Synchronization on Window.class is needed for locking the
        // critical section of the window list in AppContext.
        synchronized (Window.class)
        {
            WeakReference<Window> weakRef = null;
            // Remove frame from the Window list in wrong AppContext
            {
                // Lookup current frame's AppContext
                @SuppressWarnings("unchecked")
                Vector<WeakReference<Window>> windowList =
                    (Vector<WeakReference<Window>>)oldAppContext.get(Window.class);
                if (windowList != null) {
                    for (WeakReference<Window> ref : windowList) {
                        if (ref.get() == frame) {
                            weakRef = ref;
                            break;
                        }
                    }
                    // Remove frame from wrong AppContext
                    if (weakRef != null)
                        windowList.remove(weakRef);
                }
            }

            // Put the frame into the applet's AppContext map
            SunToolkit.insertTargetMapping(frame, newAppContext);

            // Insert frame into the Window list in the applet's AppContext map
            {
                @SuppressWarnings("unchecked")
                Vector<WeakReference<Window>> windowList =
                    (Vector<WeakReference<Window>>)newAppContext.get(Window.class);
                if (windowList == null) {
                    windowList = new Vector<WeakReference<Window>>();
                    newAppContext.put(Window.class, windowList);
                }
                // use the same weakRef here as it is used elsewhere
                windowList.add(weakRef);
            }
        }
    }

    // Flag to indicate if applet is targeted for JDK 1.1.
    private boolean jdk11Applet = false;

    // Flag to indicate if applet is targeted for JDK 1.2.
    private boolean jdk12Applet = false;

    /**
     * Determine JDK level of an applet.
     */
    private void findAppletJDKLevel(Applet applet)
    {
        // To determine the JDK level of an applet, the
        // most reliable way is to check the major version
        // of the applet class file.

        // synchronized on applet class object, so calling from
        // different instances of the same applet will be
        // serialized.
        Class<?> appletClass = applet.getClass();

        synchronized(appletClass)  {
            // Determine if the JDK level of an applet has been
            // checked before.
            Boolean jdk11Target = loader.isJDK11Target(appletClass);
            Boolean jdk12Target = loader.isJDK12Target(appletClass);

            // if applet JDK level has been checked before, retrieve
            // value and return.
            if (jdk11Target != null || jdk12Target != null) {
                jdk11Applet = (jdk11Target == null) ? false : jdk11Target.booleanValue();
                jdk12Applet = (jdk12Target == null) ? false : jdk12Target.booleanValue();
                return;
            }

            String name = appletClass.getName();

            // first convert any '.' to '/'
            name = name.replace('.', '/');

            // append .class
            final String resourceName = name + ".class";

            byte[] classHeader = new byte[8];

            try (InputStream is = AccessController.doPrivileged(
                    (PrivilegedAction<InputStream>) () -> loader.getResourceAsStream(resourceName))) {

                // Read the first 8 bytes of the class file
                int byteRead = is.read(classHeader, 0, 8);

                // return if the header is not read in entirely
                // for some reasons.
                if (byteRead != 8)
                    return;
            }
            catch (IOException e)   {
                return;
            }

            // Check major version in class file header
            int major_version = readShort(classHeader, 6);

            // Major version in class file is as follows:
            //   45 - JDK 1.1
            //   46 - JDK 1.2
            //   47 - JDK 1.3
            //   48 - JDK 1.4
            //   49 - JDK 1.5
            if (major_version < 46)
                jdk11Applet = true;
            else if (major_version == 46)
                jdk12Applet = true;

            // Store applet JDK level in AppContext for later lookup,
            // e.g. page switch.
            loader.setJDK11Target(appletClass, jdk11Applet);
            loader.setJDK12Target(appletClass, jdk12Applet);
        }
    }

    /**
     * Return true if applet is targeted to JDK 1.1.
     */
    protected boolean isJDK11Applet()   {
        return jdk11Applet;
    }

    /**
     * Return true if applet is targeted to JDK1.2.
     */
    protected boolean isJDK12Applet()   {
        return jdk12Applet;
    }

    /**
     * Read short from byte array.
     */
    private int readShort(byte[] b, int off)    {
        int hi = readByte(b[off]);
        int lo = readByte(b[off + 1]);
        return (hi << 8) | lo;
    }

    private int readByte(byte b) {
        return ((int)b) & 0xFF;
    }


    private static AppletMessageHandler amh = new AppletMessageHandler("appletpanel");
}
