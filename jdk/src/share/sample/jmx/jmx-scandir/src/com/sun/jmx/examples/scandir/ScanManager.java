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

package com.sun.jmx.examples.scandir;

import static com.sun.jmx.examples.scandir.ScanManagerMXBean.ScanState.*;
import com.sun.jmx.examples.scandir.ScanManagerMXBean.ScanState;
import com.sun.jmx.examples.scandir.config.DirectoryScannerConfig;
import com.sun.jmx.examples.scandir.config.ScanManagerConfig;
import java.io.File;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.AttributeChangeNotification;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.JMX;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

/**
 * <p>
 * The <code>ScanManager</code> is responsible for applying a configuration,
 * starting and scheduling directory scans, and reporting application state.
 * </p>
 * <p>
 * The ScanManager MBean is a singleton MBean which controls
 * scan session. The ScanManager name is defined by
 * {@link #SCAN_MANAGER_NAME ScanManager.SCAN_MANAGER_NAME}.
 * </p>
 * <p>
 * The <code>ScanManager</code> MBean is the entry point of the <i>scandir</i>
 * application management interface. It is from this MBean that all other MBeans
 * will be created and registered.
 * </p>
 *
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
public class ScanManager implements ScanManagerMXBean,
        NotificationEmitter, MBeanRegistration {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(ScanManager.class.getName());

    /**
     * The name of the ScanManager singleton MBean.
     **/
    public final static ObjectName SCAN_MANAGER_NAME =
            makeSingletonName(ScanManagerMXBean.class);

    /**
     * Sequence number used for sending notifications. We use this
     * sequence number throughout the application.
     **/
    private static long seqNumber=0;

    /**
     * The NotificationBroadcasterSupport object used to handle
     * listener registration.
     **/
    private final NotificationBroadcasterSupport broadcaster;

    /**
     * The MBeanServer in which this MBean is registered. We obtain
     * this reference by implementing the {@link MBeanRegistration}
     * interface.
     **/
    private volatile MBeanServer mbeanServer;

    /**
     * A queue of pending notifications we are about to send.
     * We're using a BlockingQueue in order to avoid sending
     * notifications from within a synchronized block.
     **/
    private final BlockingQueue<Notification> pendingNotifs;

    /**
     * The state of the scan session.
     **/
    private volatile ScanState state = STOPPED;

    /**
     * The list of DirectoryScannerMBean that are run by a scan session.
     **/
    private final Map<ObjectName,DirectoryScannerMXBean> scanmap;

    /**
     * The list of ScanDirConfigMXBean that were created by this MBean.
     **/
    private final Map<ObjectName, ScanDirConfigMXBean> configmap;

    // The ResultLogManager for this application.
    private final ResultLogManager log;

    /**
     * We use a semaphore to ensure proper sequencing of exclusive
     * action. The logic we have implemented is to fail - rather
     * than block, if an exclusive action is already in progress.
     **/
    private final Semaphore sequencer = new Semaphore(1);

    // A proxy to the current ScanDirConfigMXBean which holds the current
    // configuration data.
    //
    private volatile ScanDirConfigMXBean config = null;

    // Avoid to write parameters twices when creating a new ConcurrentHashMap.
    //
    private static <K, V> Map<K, V> newConcurrentHashMap() {
        return new ConcurrentHashMap<K, V>();
    }

    // Avoid to write parameters twices when creating a new HashMap.
    //
    private static <K, V> Map<K, V> newHashMap() {
        return new HashMap<K, V>();
    }

    /**
     * Creates a default singleton ObjectName for a given class.
     * @param clazz The interface class of the MBean for which we want to obtain
     *        a default singleton name, or its implementation class.
     *        Give one or the other depending on what you wish to see in
     *        the value of the key {@code type=}.
     * @return A default singleton name for a singleton MBean class.
     * @throws IllegalArgumentException if the name can't be created
     *         for some unfathomable reason (e.g. an unexpected
     *         exception was raised).
     **/
    public final static ObjectName makeSingletonName(Class clazz) {
        try {
            final Package p = clazz.getPackage();
            final String packageName = (p==null)?null:p.getName();
            final String className   = clazz.getSimpleName();
            final String domain;
            if (packageName == null || packageName.length()==0) {
                // We use a reference to ScanDirAgent.class to ease
                // to keep track of possible class renaming.
                domain = ScanDirAgent.class.getSimpleName();
            } else {
                domain = packageName;
            }
            final ObjectName name = new ObjectName(domain,"type",className);
            return name;
        } catch (Exception x) {
            final IllegalArgumentException iae =
                    new IllegalArgumentException(String.valueOf(clazz),x);
            throw iae;
        }
    }

    /**
     * Creates a default ObjectName with keys <code>type=</code> and
     * <code>name=</code> for an instance of a given MBean interface class.
     * @param clazz The interface class of the MBean for which we want to obtain
     *        a default name, or its implementation class.
     *        Give one or the other depending on what you wish to see in
     *        the value of the key {@code type=}.
     * @param name The value of the <code>name=</code> key.
     * @return A default name for an instance of the given MBean interface class.
     * @throws IllegalArgumentException if the name can't be created.
     *         (e.g. an unexpected exception was raised).
     **/
    public static final ObjectName makeMBeanName(Class clazz, String name) {
        try {
            return ObjectName.
                getInstance(makeSingletonName(clazz)
                        .toString()+",name="+name);
        } catch (MalformedObjectNameException x) {
            final IllegalArgumentException iae =
                    new IllegalArgumentException(String.valueOf(name),x);
            throw iae;
        }
    }

    /**
     * Return the ObjectName for a DirectoryScannerMXBean of that name.
     * This is {@code makeMBeanName(DirectoryScannerMXBean.class,name)}.
     * @param name The value of the <code>name=</code> key.
     * @return the ObjectName for a DirectoryScannerMXBean of that name.
     */
    public static final ObjectName makeDirectoryScannerName(String name) {
        return makeMBeanName(DirectoryScannerMXBean.class,name);
    }

    /**
     * Return the ObjectName for a {@code ScanDirConfigMXBean} of that name.
     * This is {@code makeMBeanName(ScanDirConfigMXBean.class,name)}.
     * @param name The value of the <code>name=</code> key.
     * @return the ObjectName for a {@code ScanDirConfigMXBean} of that name.
     */
    public static final ObjectName makeScanDirConfigName(String name) {
        return makeMBeanName(ScanDirConfigMXBean.class,name);
    }

    /**
     * Create and register a new singleton instance of the ScanManager
     * MBean in the given {@link MBeanServerConnection}.
     * @param mbs The MBeanServer in which the new singleton instance
     *         should be created.
     * @throws JMException The MBeanServer connection raised an exception
     *         while trying to instantiate and register the singleton MBean
     *         instance.
     * @throws IOException There was a connection problem while trying to
     *         communicate with the underlying MBeanServer.
     * @return A proxy for the registered MBean.
     **/
    public static ScanManagerMXBean register(MBeanServerConnection mbs)
        throws IOException, JMException {
        final ObjectInstance moi =
                mbs.createMBean(ScanManager.class.getName(),SCAN_MANAGER_NAME);
        final ScanManagerMXBean proxy =
                JMX.newMXBeanProxy(mbs,moi.getObjectName(),
                                  ScanManagerMXBean.class,true);
        return proxy;
    }

    /**
     * Creates a new {@code ScanManagerMXBean} proxy over the given
     * {@code MBeanServerConnection}. Does not check whether a
     * {@code ScanManagerMXBean}
     * is actually registered in that {@code MBeanServerConnection}.
     * @return a new {@code ScanManagerMXBean} proxy.
     * @param mbs The {@code MBeanServerConnection} which holds the
     * {@code ScanManagerMXBean} to proxy.
     */
    public static ScanManagerMXBean
            newSingletonProxy(MBeanServerConnection mbs) {
        final ScanManagerMXBean proxy =
                JMX.newMXBeanProxy(mbs,SCAN_MANAGER_NAME,
                                  ScanManagerMXBean.class,true);
        return proxy;
    }

    /**
     * Creates a new {@code ScanManagerMXBean} proxy over the platform
     * {@code MBeanServer}. This is equivalent to
     * {@code newSingletonProxy(ManagementFactory.getPlatformMBeanServer())}.
     * @return a new {@code ScanManagerMXBean} proxy.
     **/
    public static ScanManagerMXBean newSingletonProxy() {
        return newSingletonProxy(ManagementFactory.getPlatformMBeanServer());
    }

    /**
     * Create and register a new singleton instance of the ScanManager
     * MBean in the given {@link MBeanServerConnection}.
     * @throws JMException The MBeanServer connection raised an exception
     *         while trying to instantiate and register the singleton MBean
     *         instance.
     * @throws IOException There was a connection problem while trying to
     *         communicate with the underlying MBeanServer.
     * @return A proxy for the registered MBean.
     **/
    public static ScanManagerMXBean register()
        throws IOException, JMException {
        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        return register(mbs);
    }

    /**
     * Create a new ScanManager MBean
     **/
    public ScanManager() {
        broadcaster = new NotificationBroadcasterSupport();
        pendingNotifs = new LinkedBlockingQueue<Notification>(100);
        scanmap = newConcurrentHashMap();
        configmap = newConcurrentHashMap();
        log = new ResultLogManager();
    }


    // Creates a new DirectoryScannerMXBean, from the given configuration data.
    DirectoryScannerMXBean createDirectoryScanner(DirectoryScannerConfig config) {
            return new DirectoryScanner(config,log);
    }

    // Applies a configuration.
    // throws IllegalStateException if lock can't be acquired.
    // Unregisters all existing directory scanners, the create and registers
    // new directory scanners according to the given config.
    // Then pushes the log config to the result log manager.
    //
    private void applyConfiguration(ScanManagerConfig bean)
        throws IOException, JMException {
        if (bean == null) return;
        if (!sequencer.tryAcquire()) {
            throw new IllegalStateException("Can't acquire lock");
        }
        try {
            unregisterScanners();
            final DirectoryScannerConfig[] scans = bean.getScanList();
            if (scans == null) return;
            for (DirectoryScannerConfig scan : scans) {
                addDirectoryScanner(scan);
            }
            log.setConfig(bean.getInitialResultLogConfig());
        } finally {
            sequencer.release();
        }
    }

    // See ScanManagerMXBean
    public void applyConfiguration(boolean fromMemory)
        throws IOException, JMException {
        if (fromMemory == false) config.load();
        applyConfiguration(config.getConfiguration());
    }

    // See ScanManagerMXBean
    public void applyCurrentResultLogConfig(boolean toMemory)
        throws IOException, JMException {
        final ScanManagerConfig bean = config.getConfiguration();
        bean.setInitialResultLogConfig(log.getConfig());
        config.setConfiguration(bean);
        if (toMemory==false) config.save();
    }

    // See ScanManagerMXBean
    public void setConfigurationMBean(ScanDirConfigMXBean config) {
        this.config = config;
    }

    // See ScanManagerMXBean
    public ScanDirConfigMXBean getConfigurationMBean() {
        return config;
    }

    // Creates and registers a new directory scanner.
    // Called by applyConfiguration.
    // throws IllegalStateException if state is not STOPPED or COMPLETED
    // (you cannot change the config while scanning is scheduled or running).
    //
    private DirectoryScannerMXBean addDirectoryScanner(
                DirectoryScannerConfig bean)
        throws JMException {
        try {
            final DirectoryScannerMXBean scanner;
            final ObjectName scanName;
            synchronized (this) {
                if (state != STOPPED && state != COMPLETED)
                   throw new IllegalStateException(state.toString());
                scanner = createDirectoryScanner(bean);
                scanName = makeDirectoryScannerName(bean.getName());
            }
            LOG.fine("server: "+mbeanServer);
            LOG.fine("scanner: "+scanner);
            LOG.fine("scanName: "+scanName);
            final ObjectInstance moi =
                mbeanServer.registerMBean(scanner,scanName);
            final ObjectName moiName = moi.getObjectName();
            final DirectoryScannerMXBean proxy =
                JMX.newMXBeanProxy(mbeanServer,moiName,
                DirectoryScannerMXBean.class,true);
            scanmap.put(moiName,proxy);
            return proxy;
        } catch (RuntimeException x) {
            final String msg = "Operation failed: "+x;
            if (LOG.isLoggable(Level.FINEST))
                LOG.log(Level.FINEST,msg,x);
            else LOG.fine(msg);
            throw x;
        } catch (JMException x) {
            final String msg = "Operation failed: "+x;
            if (LOG.isLoggable(Level.FINEST))
                LOG.log(Level.FINEST,msg,x);
            else LOG.fine(msg);
            throw x;
        }
    }

    // See ScanManagerMXBean
    public ScanDirConfigMXBean createOtherConfigurationMBean(String name,
            String filename)
        throws JMException {
        final ScanDirConfig profile = new ScanDirConfig(filename);
        final ObjectName profName = makeScanDirConfigName(name);
        final ObjectInstance moi = mbeanServer.registerMBean(profile,profName);
        final ScanDirConfigMXBean proxy =
                JMX.newMXBeanProxy(mbeanServer,profName,
                    ScanDirConfigMXBean.class,true);
        configmap.put(moi.getObjectName(),proxy);
        return proxy;
    }


    // See ScanManagerMXBean
    public Map<String,DirectoryScannerMXBean> getDirectoryScanners() {
        final Map<String,DirectoryScannerMXBean> proxyMap = newHashMap();
        for (Entry<ObjectName,DirectoryScannerMXBean> item : scanmap.entrySet()){
            proxyMap.put(item.getKey().getKeyProperty("name"),item.getValue());
        }
        return proxyMap;
    }

    // ---------------------------------------------------------------
    // State Management
    // ---------------------------------------------------------------

    /**
     * For each operation, this map stores a list of states from
     * which the corresponding operation can be legally called.
     * For instance, it is legal to call "stop" regardless of the
     * application state. However, "schedule" can be called only if
     * the application state is STOPPED, etc...
     **/
    private final static Map<String,EnumSet<ScanState>> allowedStates;
    static {
        allowedStates = newHashMap();
        // You can always call stop
        allowedStates.put("stop",EnumSet.allOf(ScanState.class));

        // You can only call closed when stopped
        allowedStates.put("close",EnumSet.of(STOPPED,COMPLETED,CLOSED));

        // You can call schedule only when the current task is
        // completed or stopped.
        allowedStates.put("schedule",EnumSet.of(STOPPED,COMPLETED));

        // switch reserved for background task: goes from SCHEDULED to
        //    RUNNING when it enters the run() method.
        allowedStates.put("scan-running",EnumSet.of(SCHEDULED));

        // switch reserved for background task: goes from RUNNING to
        //    SCHEDULED when it has completed but needs to reschedule
        //    itself for specified interval.
        allowedStates.put("scan-scheduled",EnumSet.of(RUNNING));

        // switch reserved for background task:
        //     goes from RUNNING to COMPLETED upon successful completion
        allowedStates.put("scan-done",EnumSet.of(RUNNING));
    }

    // Get this object's state. No need to synchronize because
    // state is volatile.
    // See ScanManagerMXBean
    public ScanState getState() {
        return state;
    }

    /**
     * Enqueue a state changed notification for the given states.
     **/
    private void queueStateChangedNotification(
                    long sequence,
                    long time,
                    ScanState old,
                    ScanState current) {
        final AttributeChangeNotification n =
                new AttributeChangeNotification(SCAN_MANAGER_NAME,sequence,time,
                "ScanManager State changed to "+current,"State",
                ScanState.class.getName(),old.toString(),current.toString());
        // Queue the notification. We have created an unlimited queue, so
        // this method should always succeed.
        try {
            if (!pendingNotifs.offer(n,2,TimeUnit.SECONDS)) {
                LOG.fine("Can't queue Notification: "+n);
            }
        } catch (InterruptedException x) {
                LOG.fine("Can't queue Notification: "+x);
        }
    }

    /**
     * Send all notifications present in the queue.
     **/
    private void sendQueuedNotifications() {
        Notification n;
        while ((n = pendingNotifs.poll()) != null) {
            broadcaster.sendNotification(n);
        }
    }

    /**
     * Checks that the current state is allowed for the given operation,
     * and if so, switch its value to the new desired state.
     * This operation also enqueue the appropriate state changed
     * notification.
     **/
    private ScanState switchState(ScanState desired,String forOperation) {
        return switchState(desired,allowedStates.get(forOperation));
    }

    /**
     * Checks that the current state is one of the allowed states,
     * and if so, switch its value to the new desired state.
     * This operation also enqueue the appropriate state changed
     * notification.
     **/
    private ScanState switchState(ScanState desired,EnumSet<ScanState> allowed) {
        final ScanState old;
        final long timestamp;
        final long sequence;
        synchronized(this) {
            old = state;
            if (!allowed.contains(state))
               throw new IllegalStateException(state.toString());
            state = desired;
            timestamp = System.currentTimeMillis();
            sequence  = getNextSeqNumber();
        }
        LOG.fine("switched state: "+old+" -> "+desired);
        if (old != desired)
            queueStateChangedNotification(sequence,timestamp,old,desired);
        return old;
    }


    // ---------------------------------------------------------------
    // schedule() creates a new SessionTask that will be executed later
    // (possibly right away if delay=0) by a Timer thread.
    // ---------------------------------------------------------------

    // The timer used by this object. Lazzy evaluation. Cleaned in
    // postDeregister()
    //
    private Timer timer = null;

    // See ScanManagerMXBean
    public void schedule(long delay, long interval) {
        if (!sequencer.tryAcquire()) {
            throw new IllegalStateException("Can't acquire lock");
        }
        try {
            LOG.fine("scheduling new task: state="+state);
            final ScanState old = switchState(SCHEDULED,"schedule");
            final boolean scheduled =
                scheduleSession(new SessionTask(interval),delay);
            if (scheduled)
                LOG.fine("new task scheduled: state="+state);
        } finally {
            sequencer.release();
        }
        sendQueuedNotifications();
    }

    // Schedule a SessionTask. The session task may reschedule
    // a new identical task when it eventually ends.
    // We use this logic so that the 'interval' time is measured
    // starting at the end of the task that finishes, rather than
    // at its beginning. Therefore if a repeated task takes x ms,
    // it will be repeated every x+interval ms.
    //
    private synchronized boolean scheduleSession(SessionTask task, long delay) {
        if (state == STOPPED) return false;
        if (timer == null) timer = new Timer("ScanManager");
        tasklist.add(task);
        timer.schedule(task,delay);
        return true;
    }

    // ---------------------------------------------------------------
    // start() is equivalent to schedule(0,0)
    // ---------------------------------------------------------------

    // See ScanManagerMXBean
    public void start() throws IOException, InstanceNotFoundException {
        schedule(0,0);
    }

    // ---------------------------------------------------------------
    // Methods used to implement stop() -  stop() is asynchronous,
    // and needs to notify any running background task that it needs
    // to stop. It also needs to prevent scheduled task from being
    // run.
    // ---------------------------------------------------------------

    // See ScanManagerMXBean
    public void stop() {
        if (!sequencer.tryAcquire())
            throw new IllegalStateException("Can't acquire lock");
        int errcount = 0;
        final StringBuilder b = new StringBuilder();

        try {
            switchState(STOPPED,"stop");

            errcount += cancelSessionTasks(b);
            errcount += stopDirectoryScanners(b);
        } finally {
            sequencer.release();
        }

        sendQueuedNotifications();
        if (errcount > 0) {
            b.insert(0,"stop partially failed with "+errcount+" error(s):");
            throw new RuntimeException(b.toString());
        }
    }

    // See ScanManagerMXBean
    public void close() {
        switchState(CLOSED,"close");
        sendQueuedNotifications();
    }

    // Appends exception to a StringBuilder message.
    //
    private void append(StringBuilder b,String prefix,Throwable t) {
        final String first = (prefix==null)?"\n":"\n"+prefix;
        b.append(first).append(String.valueOf(t));
        Throwable cause = t;
        while ((cause = cause.getCause())!=null) {
            b.append(first).append("Caused by:").append(first);
            b.append('\t').append(String.valueOf(cause));
        }
    }

    // Cancels all scheduled session tasks
    //
    private int cancelSessionTasks(StringBuilder b) {
        int errcount = 0;
        // Stops scheduled tasks if any...
        //
        for (SessionTask task : tasklist) {
            try {
                task.cancel();
                tasklist.remove(task);
            } catch (Exception ex) {
                errcount++;
                append(b,"\t",ex);
            }
        }
        return errcount;
    }

    // Stops all DirectoryScanners configured for this object.
    //
    private int stopDirectoryScanners(StringBuilder b) {
        int errcount = 0;
        // Stops directory scanners if any...
        //
        for (DirectoryScannerMXBean s : scanmap.values()) {
            try {
                s.stop();
            } catch (Exception ex) {
                errcount++;
                append(b,"\t",ex);
            }
        }
        return errcount;
    }


    // ---------------------------------------------------------------
    // We start scanning in background in a Timer thread.
    // The methods below implement that logic.
    // ---------------------------------------------------------------

    private void scanAllDirectories()
        throws IOException, InstanceNotFoundException {

        int errcount = 0;
        final StringBuilder b = new StringBuilder();
        for (ObjectName key : scanmap.keySet()) {
            final DirectoryScannerMXBean s = scanmap.get(key);
            try {
                if (state == STOPPED) return;
                s.scan();
            } catch (Exception ex) {
                LOG.log(Level.FINE,key + " failed to scan: "+ex,ex);
                errcount++;
                append(b,"\t",ex);
            }
        }
        if (errcount > 0) {
            b.insert(0,"scan partially performed with "+errcount+" error(s):");
            throw new RuntimeException(b.toString());
        }
    }

    // List of scheduled session task. Needed by stop() to cancel
    // scheduled sessions. There's usually at most 1 session in
    // this list (unless there's a bug somewhere ;-))
    //
    private final ConcurrentLinkedQueue<SessionTask> tasklist =
            new ConcurrentLinkedQueue<SessionTask>();

    // Used to give a unique id to session task - useful for
    // debugging.
    //
    private volatile static long taskcount = 0;

    /**
     * A session task will be scheduled to run in background in a
     * timer thread. There can be at most one session task running
     * at a given time (this is ensured by using a timer - which is
     * a single threaded object).
     *
     * If the session needs to be repeated, it will reschedule an
     * identical session when it finishes to run. This ensure that
     * two session runs are separated by the given interval time.
     *
     **/
    private class SessionTask extends TimerTask {

        /**
         * Delay after which the next iteration of this task will
         * start. This delay is measured  starting at the end of
         * the previous iteration.
         **/
        final long delayBeforeNext;

        /**
         * A unique id for this task.
         **/
        final long taskid;

        /**
         * Whether it's been cancelled by stop()
         **/
        volatile boolean cancelled=false;

        /**
         * create a new SessionTask.
         **/
        SessionTask(long scheduleNext) {
            delayBeforeNext = scheduleNext;
            taskid = taskcount++;
        }

        /**
         * When run() begins, the state is switched to RUNNING.
         * When run() ends then:
         *      If the task is repeated, the state will be switched
         *      to SCHEDULED (because a new task was scheduled).
         *      Otherwise the state will be switched to either
         *      STOPPED (if it was stopped before it could complete)
         *      or COMPLETED (if it completed gracefully)
         * This method is used to switch to the desired state and
         * send the appropriate notifications.
         * When entering the method, we check whether the state is
         * STOPPED. If so, we return false - and the SessionTask will
         * stop. Otherwise, we switch the state to the desired value.
         **/
        private boolean notifyStateChange(ScanState newState,String condition) {
            synchronized (ScanManager.this) {
                if (state == STOPPED || state == CLOSED) return false;
                switchState(newState,condition);
            }
            sendQueuedNotifications();
            return true;
        }

        // Cancels this task.
        public boolean cancel() {
            cancelled=true;
            return super.cancel();
        }

        /**
         * Invoke all directories scanners in sequence. At each
         * step, checks to see whether the task should stop.
         **/
        private boolean execute() {
            final String tag = "Scheduled session["+taskid+"]";
            try {
                if (cancelled) {
                    LOG.finer(tag+" cancelled: done");
                    return false;
                }
                if (!notifyStateChange(RUNNING,"scan-running")) {
                    LOG.finer(tag+" stopped: done");
                    return false;
                }
                scanAllDirectories();
            } catch (Exception x) {
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.log(Level.FINEST,
                            tag+" failed to scan: "+x,x);
                } else if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine(tag+" failed to scan: "+x);
                }
            }
            return true;
        }

        /**
         * Schedule an identical task for next iteration.
         **/
        private boolean scheduleNext() {
            final String tag = "Scheduled session["+taskid+"]";

            // We need now to reschedule a new task for after 'delayBeforeNext' ms.
            try {
                LOG.finer(tag+": scheduling next session for "+ delayBeforeNext + "ms");
                if (cancelled || !notifyStateChange(SCHEDULED,"scan-scheduled")) {
                    LOG.finer(tag+" stopped: do not reschedule");
                    return false;
                }
                final SessionTask nextTask = new SessionTask(delayBeforeNext);
                if (!scheduleSession(nextTask,delayBeforeNext)) return false;
                LOG.finer(tag+": next session successfully scheduled");
            } catch (Exception x) {
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.log(Level.FINEST,tag+
                            " failed to schedule next session: "+x,x);
                } else if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine(tag+" failed to schedule next session: "+x);
                }
            }
            return true;
        }


        /**
         * The run method:
         * executes scanning logic, the schedule next iteration if needed.
         **/
        public void run() {
            final String tag = "Scheduled session["+taskid+"]";
            LOG.entering(SessionTask.class.getName(),"run");
            LOG.finer(tag+" starting...");
            try {
                if (execute()==false) return;

                LOG.finer(tag+" terminating - state is "+state+
                    ((delayBeforeNext >0)?(" next session is due in "+delayBeforeNext+" ms."):
                        " no additional session scheduled"));

                // if delayBeforeNext <= 0 we are done, either because the session was
                // stopped or because it successfully completed.
                if (delayBeforeNext <= 0) {
                    if (!notifyStateChange(COMPLETED,"scan-done"))
                        LOG.finer(tag+" stopped: done");
                    else
                        LOG.finer(tag+" completed: done");
                    return;
                }

                // we need to reschedule a new session for 'delayBeforeNext' ms.
                scheduleNext();

            } finally {
                tasklist.remove(this);
                LOG.finer(tag+" finished...");
                LOG.exiting(SessionTask.class.getName(),"run");
            }
        }
    }

    // ---------------------------------------------------------------
    // ---------------------------------------------------------------

    // ---------------------------------------------------------------
    // MBean Notification support
    // The methods below are imported from {@link NotificationEmitter}
    // ---------------------------------------------------------------

    /**
     * Delegates the implementation of this method to the wrapped
     * {@code NotificationBroadcasterSupport} object.
     **/
    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) throws IllegalArgumentException {
        broadcaster.addNotificationListener(listener, filter, handback);
    }


    /**
     * We emit an {@code AttributeChangeNotification} when the {@code State}
     * attribute changes.
     **/
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[] {
            new MBeanNotificationInfo(new String[] {
                AttributeChangeNotification.ATTRIBUTE_CHANGE},
                AttributeChangeNotification.class.getName(),
                "Emitted when the State attribute changes")
            };
    }

    /**
     * Delegates the implementation of this method to the wrapped
     * {@code NotificationBroadcasterSupport} object.
     **/
    public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener);
    }

    /**
     * Delegates the implementation of this method to the wrapped
     * {@code NotificationBroadcasterSupport} object.
     **/
    public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener, filter, handback);
    }

    /**
     * Returns and increment the sequence number used for
     * notifications. We use the same sequence number throughout the
     * application - this is why this method is only package protected.
     * @return A unique sequence number for the next notification.
     */
    static synchronized long getNextSeqNumber() {
        return seqNumber++;
    }

    // ---------------------------------------------------------------
    // End of MBean Notification support
    // ---------------------------------------------------------------

    // ---------------------------------------------------------------
    // MBeanRegistration support
    // The methods below are imported from {@link MBeanRegistration}
    // ---------------------------------------------------------------

    /**
     * Allows the MBean to perform any operations it needs before being
     * registered in the MBean server. If the name of the MBean is not
     * specified, the MBean can provide a name for its registration. If
     * any exception is raised, the MBean will not be registered in the
     * MBean server.
     * <p>In this implementation, we check that the provided name is
     * either {@code null} or equals to {@link #SCAN_MANAGER_NAME}. If it
     * isn't then we throw an IllegalArgumentException, otherwise we return
     * {@link #SCAN_MANAGER_NAME}.</p>
     * <p>This ensures that there will be a single instance of ScanManager
     * registered in a given MBeanServer, and that it will always be
     * registered with the singleton's {@link #SCAN_MANAGER_NAME}.</p>
     * <p>We do not need to check whether an MBean by that name is
     *    already registered because the MBeanServer will perform
     *    this check just after having called preRegister().</p>
     * @param server The MBean server in which the MBean will be registered.
     * @param name The object name of the MBean. This name is null if the
     * name parameter to one of the createMBean or registerMBean methods in
     * the MBeanServer interface is null. In that case, this method must
     * return a non-null ObjectName for the new MBean.
     * @return The name under which the MBean is to be registered. This value
     * must not be null. If the name parameter is not null, it will usually
     * but not necessarily be the returned value.
     * @throws Exception This exception will be caught by the MBean server and
     * re-thrown as an MBeanRegistrationException.
     */
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        if (name != null) {
            if (!SCAN_MANAGER_NAME.equals(name))
                throw new IllegalArgumentException(String.valueOf(name));
        }
        mbeanServer = server;
        return SCAN_MANAGER_NAME;
    }

    // Returns the default configuration filename
    static String getDefaultConfigurationFileName() {
        // This is a file calles 'jmx-scandir.xml' located
        // in the user directory.
        final String user = System.getProperty("user.home");
        final String defconf = user+File.separator+"jmx-scandir.xml";
        return defconf;
    }

    /**
     * Allows the MBean to perform any operations needed after having
     * been registered in the MBean server or after the registration has
     * failed.
     * <p>
     * If registration was not successful, the method returns immediately.
     * <p>
     * If registration is successful, register the {@link ResultLogManager}
     * and default {@link ScanDirConfigMXBean}. If registering these
     * MBean fails, the {@code ScanManager} state will be switched to
     * {@link #close CLOSED}, and postRegister ends there.
     * </p>
     * <p>Otherwise the {@code ScanManager} will ask the
     * {@link ScanDirConfigMXBean} to load its configuration.
     * If it succeeds, the configuration will be {@link
     * #applyConfiguration applied}. Otherwise, the method simply returns,
     * assuming that the user will later create/update a configuration and
     * apply it.
     * @param registrationDone Indicates whether or not the MBean has been
     * successfully registered in the MBean server. The value false means
     * that the registration has failed.
     */
    public void postRegister(Boolean registrationDone) {
        if (!registrationDone) return;
        Exception test=null;
        try {
            mbeanServer.registerMBean(log,
                    ResultLogManager.RESULT_LOG_MANAGER_NAME);
            final String defconf = getDefaultConfigurationFileName();
            final String conf = System.getProperty("scandir.config.file",defconf);
            final String confname = ScanDirConfig.guessConfigName(conf,defconf);
            final ObjectName defaultProfileName =
                    makeMBeanName(ScanDirConfigMXBean.class,confname);
            if (!mbeanServer.isRegistered(defaultProfileName))
                mbeanServer.registerMBean(new ScanDirConfig(conf),
                        defaultProfileName);
            config = JMX.newMXBeanProxy(mbeanServer,defaultProfileName,
                    ScanDirConfigMXBean.class,true);
            configmap.put(defaultProfileName,config);
        } catch (Exception x) {
            LOG.config("Failed to populate MBeanServer: "+x);
            close();
            return;
        }
        try {
            config.load();
        } catch (Exception x) {
            LOG.finest("No config to load: "+x);
            test = x;
        }
        if (test == null) {
            try {
                applyConfiguration(config.getConfiguration());
            } catch (Exception x) {
                if (LOG.isLoggable(Level.FINEST))
                    LOG.log(Level.FINEST,"Failed to apply config: "+x,x);
                LOG.config("Failed to apply config: "+x);
            }
        }
    }

    // Unregisters all created DirectoryScanners
    private void unregisterScanners() throws JMException {
        unregisterMBeans(scanmap);
    }

    // Unregisters all created ScanDirConfigs
    private void unregisterConfigs() throws JMException {
        unregisterMBeans(configmap);
    }

    // Unregisters all MBeans named by the given map
    private void unregisterMBeans(Map<ObjectName,?> map) throws JMException {
        for (ObjectName key : map.keySet()) {
            if (mbeanServer.isRegistered(key))
                mbeanServer.unregisterMBean(key);
            map.remove(key);
        }
    }

    // Unregisters the ResultLogManager.
    private void unregisterResultLogManager() throws JMException {
        final ObjectName name = ResultLogManager.RESULT_LOG_MANAGER_NAME;
        if (mbeanServer.isRegistered(name)) {
            mbeanServer.unregisterMBean(name);
        }
    }

    /**
     * Allows the MBean to perform any operations it needs before being
     * unregistered by the MBean server.
     * This implementation also unregisters all the MXBeans
     * that were created by this object.
     * @throws IllegalStateException if the lock can't be acquire, or if
     *         the MBean's state doesn't allow the MBean to be unregistered
     *         (e.g. because it's scheduled or running).
     * @throws Exception This exception will be caught by the MBean server and
     * re-thrown as an MBeanRegistrationException.
     */
    public void preDeregister() throws Exception {
        try {
            close();
            if (!sequencer.tryAcquire())
                throw new IllegalStateException("can't acquire lock");
            try {
                unregisterScanners();
                unregisterConfigs();
                unregisterResultLogManager();
            } finally {
                sequencer.release();
            }
        } catch (Exception x) {
            LOG.log(Level.FINEST,"Failed to unregister: "+x,x);
            throw x;
        }
    }

    /**
     * Allows the MBean to perform any operations needed after having been
     * unregistered in the MBean server.
     * Cancels the internal timer - if any.
     */
    public synchronized void postDeregister() {
        if (timer != null) {
            try {
                timer.cancel();
            } catch (Exception x) {
                if (LOG.isLoggable(Level.FINEST))
                    LOG.log(Level.FINEST,"Failed to cancel timer",x);
                else if (LOG.isLoggable(Level.FINE))
                    LOG.fine("Failed to cancel timer: "+x);
            } finally {
                timer = null;
            }
        }
   }

    // ---------------------------------------------------------------
    // End of MBeanRegistration support
    // ---------------------------------------------------------------

}
