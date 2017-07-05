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

import static com.sun.jmx.examples.scandir.ScanManager.getNextSeqNumber;
import com.sun.jmx.examples.scandir.ScanManagerMXBean.ScanState;
import static com.sun.jmx.examples.scandir.ScanManagerMXBean.ScanState.*;
import static com.sun.jmx.examples.scandir.config.DirectoryScannerConfig.Action.*;
import com.sun.jmx.examples.scandir.config.XmlConfigUtils;
import com.sun.jmx.examples.scandir.config.DirectoryScannerConfig;
import com.sun.jmx.examples.scandir.config.DirectoryScannerConfig.Action;
import com.sun.jmx.examples.scandir.config.ResultRecord;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.AttributeChangeNotification;
import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;

/**
 * A <code>DirectoryScanner</code> is an MBean that
 * scans a file system starting at a given root directory,
 * and then looks for files that match a given criteria.
 * <p>
 * When such a file is found, the <code>DirectoryScanner</code> takes
 * the action for which it was configured: emit a notification,
 * <i>and or</i> log a {@link
 * com.sun.jmx.examples.scandir.config.ResultRecord} for this file,
 * <i>and or</i> delete that file.
 * </p>
 * <p>
 * The code that would actually delete the file is commented out - so that
 * nothing valuable is lost if this example is run by mistake on the wrong
 * set of directories.<br>
 * Logged results are logged by sending them to the {@link ResultLogManager}.
 * </p>
 * <p>
 * <code>DirectoryScannerMXBeans</code> are created, initialized, and
 * registered by the {@link ScanManagerMXBean}.
 * The {@link ScanManagerMXBean} will also schedule and run them in
 * background by calling their {@link #scan} method.
 * </p>
 * <p>Client code is not expected to create or register directly any such
 * MBean. Instead, clients are expected to modify the configuration, using
 * the {@link ScanDirConfigMXBean}, and then apply it, using the {@link
 * ScanManagerMXBean}. Instances of <code>DirectoryScannerMXBeans</code>
 * will then be created and registered (or unregistered and garbage collected)
 * as a side effect of applying that configuration.
 * </p>
 *
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
public class DirectoryScanner implements
        DirectoryScannerMXBean, NotificationEmitter {

    /**
     * The type for <i>com.sun.jmx.examples.scandir.filematch</i> notifications.
     * Notifications of this type will be emitted whenever a file that
     * matches this {@code DirectoryScanner} criteria is found, but only if
     * this {@code DirectoryScanner} was configured to {@link
     * Action#NOTIFY notify} for matching files.
     **/
    public static final String FILE_MATCHES_NOTIFICATION =
            "com.sun.jmx.examples.scandir.filematch";

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(DirectoryScanner.class.getName());

    // Attribute : State
    //
    private volatile ScanState state = STOPPED;

    // The DirectoryScanner delegates the implementation of
    // the NotificationEmitter interface to a wrapped instance
    // of NotificationBroadcasterSupport.
    //
    private final NotificationBroadcasterSupport broadcaster;

    // The root directory at which this DirectoryScanner will start
    // scanning. Constructed from config.getRootDirectory().
    //
    private final File rootFile;

    // This DirectoryScanner config - this is a constant which is
    // provided at construction time by the {@link ScanManager}.
    //
    private final DirectoryScannerConfig config;

    // The set of actions for which this DirectoryScanner is configured.
    // Constructed from config.getActions()
    //
    final Set<Action> actions;

    // The ResultLogManager that this DirectoryScanner will use to log
    // info. This is a hard reference to another MBean, provided
    // at construction time by the ScanManager.
    // The ScanManager makes sure that the life cycle of these two MBeans
    // is consistent.
    //
    final ResultLogManager logManager;

    /**
     * Constructs a new {@code DirectoryScanner}.
     * <p>This constructor is
     * package protected, and this MBean cannot be created by a remote
     * client, because it needs a reference to the {@link ResultLogManager},
     * which cannot be provided from remote.
     * </p>
     * <p>This is a conscious design choice: {@code DirectoryScanner} MBeans
     * are expected to be completely managed (created, registered, unregistered)
     * by the {@link ScanManager} which does provide this reference.
     * </p>
     *
     * @param config This {@code DirectoryScanner} configuration.
     * @param logManager The info log manager with which to log the info
     *        records.
     * @throws IllegalArgumentException if one of the parameter is null, or if
     *         the provided {@code config} doesn't have its {@code name} set,
     *         or if the {@link DirectoryScannerConfig#getRootDirectory
     *         root directory} provided in the {@code config} is not acceptable
     *         (not provided or not found or not readable, etc...).
     **/
    public DirectoryScanner(DirectoryScannerConfig config,
                            ResultLogManager logManager)
        throws IllegalArgumentException {
        if (logManager == null)
            throw new IllegalArgumentException("log=null");
        if (config == null)
            throw new IllegalArgumentException("config=null");
        if (config.getName() == null)
            throw new IllegalArgumentException("config.name=null");

         broadcaster = new NotificationBroadcasterSupport();

         // Clone the config: ensure data encapsulation.
         //
         this.config = XmlConfigUtils.xmlClone(config);

         // Checks that the provided root directory is valid.
         // Throws IllegalArgumentException if it isn't.
         //
         rootFile = validateRoot(config.getRootDirectory());

         // Initialize the Set<Action> for which this DirectoryScanner
         // is configured.
         //
         if (config.getActions() == null)
             actions = Collections.emptySet();
         else
             actions = EnumSet.copyOf(Arrays.asList(config.getActions()));
         this.logManager = logManager;
    }

    // see DirectoryScannerMXBean
    public void stop() {
        // switch state to stop and send AttributeValueChangeNotification
        setStateAndNotify(STOPPED);
    }

    // see DirectoryScannerMXBean
    public String getRootDirectory() {
        return rootFile.getAbsolutePath();
    }


    // see DirectoryScannerMXBean
    public ScanState getState() {
        return state;
    }

    // see DirectoryScannerMXBean
    public DirectoryScannerConfig getConfiguration() {
        return config;
    }

    // see DirectoryScannerMXBean
    public String getCurrentScanInfo() {
        final ScanTask currentOrLastTask = currentTask;
        if (currentOrLastTask == null) return "Never Run";
        return currentOrLastTask.getScanInfo();
    }

    // This variable points to the current (or latest) scan.
    //
    private volatile ScanTask currentTask = null;

    // see DirectoryScannerMXBean
    public void scan() {
        final ScanTask task;

        synchronized (this) {
            final LinkedList<File> list;
            switch (state) {
                case RUNNING:
                case SCHEDULED:
                    throw new IllegalStateException(state.toString());
                case STOPPED:
                case COMPLETED:
                    // only accept to scan if state is STOPPED or COMPLETED.
                    list = new LinkedList<File>();
                    list.add(rootFile);
                    break;
                default:
                    throw new IllegalStateException(String.valueOf(state));
            }

            // Create a new ScanTask object for our root directory file.
            //
            currentTask = task = new ScanTask(list,this);

            // transient state... will be switched to RUNNING when
            // task.execute() is called. This code could in fact be modified
            // to use java.util.concurent.Future and, to push the task to
            // an executor. We would then need to wait for the task to
            // complete before returning.  However, this wouldn't buy us
            // anything - since this method should wait for the task to
            // finish anyway: so why would we do it?
            // As it stands, we simply call task.execute() in the current
            // thread - brave and fearless readers may want to attempt the
            // modification ;-)
            //
            setStateAndNotify(SCHEDULED);
        }
        task.execute();
    }

    // This method is invoked to carry out the configured actions on a
    // matching file.
    // Do not call this method from within synchronized() { } as this
    // method may send notifications!
    //
    void actOn(File file) {

        // Which action were actually taken
        //
        final Set<Action> taken = new HashSet<Action>();
        boolean logresult = false;

        // Check out which actions are configured and carry them out.
        //
        for (Action action : actions) {
            switch (action) {
                case DELETE:
                    if (deleteFile(file)) {
                        // Delete succeeded: add DELETE to the set of
                        // actions carried out.
                        taken.add(DELETE);
                    }
                    break;
                case NOTIFY:
                    if (notifyMatch(file)) {
                        // Notify succeeded: add NOTIFY to the set of
                        // actions carried out.
                        taken.add(NOTIFY);
                    }
                    break;
                case LOGRESULT:
                    // LOGRESULT was configured - log actions carried out.
                    // => we must execute this action as the last action.
                    //    simply set logresult=true for now. We will do
                    //    the logging later
                    logresult = true;
                    break;
                default:
                    LOG.fine("Failed to execute action: " +action +
                            " - action not supported");
                    break;
            }
        }

        // Now is time for logging:
        if (logresult) {
            taken.add(LOGRESULT);
            if (!logResult(file,taken.toArray(new Action[taken.size()])))
                taken.remove(LOGRESULT); // just for the last trace below...
        }

        LOG.finest("File processed: "+taken+" - "+file.getAbsolutePath());
    }

    // Deletes a matching file.
    private boolean deleteFile(File file) {
        try {
            // file.delete() is commented so that we don't do anything
            // bad if the example is mistakenly run on the wrong set of
            // directories.
            //
            /* file.delete(); */
            System.out.println("DELETE not implemented for safety reasons.");
            return true;
        } catch (Exception x) {
            LOG.fine("Failed to delete: "+file.getAbsolutePath());
        }
        return false;
    }

    // Notifies of a matching file.
    private boolean notifyMatch(File file) {
        try {
            final Notification n =
                    new Notification(FILE_MATCHES_NOTIFICATION,this,
                    getNextSeqNumber(),
                    file.getAbsolutePath());

            // This method *is not* called from any synchronized block, so
            // we can happily call broadcaster.sendNotification() here.
            // Note that verifying whether a method is called from within
            // a synchronized block demends a thoroughful code reading,
            // examining each of the 'parent' methods in turn.
            //
            broadcaster.sendNotification(n);
            return true;
        } catch (Exception x) {
            LOG.fine("Failed to notify: "+file.getAbsolutePath());
        }
        return false;
    }

    // Logs a result with the ResultLogManager
    private boolean logResult(File file,Action[] actions) {
        try {
            logManager.log(new ResultRecord(config, actions,file));
            return true;
        } catch (Exception x) {
            LOG.fine("Failed to log: "+file.getAbsolutePath());
        }
        return false;
    }


    // Contextual object used to store info about current
    // (or last) scan.
    //
    private static class ScanTask {

        // List of Files that remain to scan.
        // When files are discovered they are added to the list.
        // When they are being handled, they are removed from the list.
        // When the list is empty, the scanning is finished.
        //
        private final LinkedList<File>   list;
        private final DirectoryScanner scan;

        // Some statistics...
        //
        private volatile long scanned=0;
        private volatile long matching=0;

        private volatile String info="Not started";

        ScanTask(LinkedList<File> list, DirectoryScanner scan) {
            this.list = list; this.scan = scan;
        }

        public void execute() {
            scan(list);
        }

        private void scan(LinkedList<File> list) {
             scan.scan(this,list);
        }

        public String getScanInfo() {
            return info+" - ["+scanned+" scanned, "+matching+" matching]";
        }
    }

    // The actual scan logic. Switches state to RUNNING,
    // and scan the list of given dirs.
    // The list is a live object which is updated by this method.
    // This would allow us to implement methods like "pause" and "resume",
    // since all the info needed to resume would be in the list.
    //
    private void scan(ScanTask task, LinkedList<File> list) {
        setStateAndNotify(RUNNING);
        task.info = "In Progress";
        try {

            // The FileFilter will tell us which files match and which don't.
            //
            final FileFilter filter = config.buildFileFilter();

            // We have two condition to end the loop: either the list is
            // empty, meaning there's nothing more to scan, or the state of
            // the DirectoryScanner was asynchronously switched to STOPPED by
            // another thread, e.g. because someone called "stop" on the
            // ScanManagerMXBean
            //
            while (!list.isEmpty() && state == RUNNING) {

                // Get and remove the first element in the list.
                //
                final File current = list.poll();

                // Increment number of file scanned.
                task.scanned++;

                // If 'current' is a file, it's already been matched by our
                // file filter (see below): act on it.
                // Note that for the first iteration of this loop, there will
                // be one single file in the list: the root directory for this
                // scanner.
                //
                if (current.isFile()) {
                    task.matching++;
                    actOn(current);
                }

                // If 'current' is a directory, then
                // find files and directories that match the file filter
                // in this directory
                //
                if (current.isDirectory()) {

                    // Gets matching files and directories
                    final File[] content = current.listFiles(filter);
                    if (content == null) continue;

                    // Adds all matching file to the list.
                    list.addAll(0,Arrays.asList(content));
                }
            }

            // The loop terminated. If the list is empty, then we have
            // completed our task. If not, then somebody must have called
            // stop() on this directory scanner.
            //
            if (list.isEmpty()) {
                task.info = "Successfully Completed";
                setStateAndNotify(COMPLETED);
            }
        } catch (Exception x) {
            // We got an exception: stop the scan
            //
            task.info = "Failed: "+x;
            if (LOG.isLoggable(Level.FINEST))
                LOG.log(Level.FINEST,"scan task failed: "+x,x);
            else if (LOG.isLoggable(Level.FINE))
                LOG.log(Level.FINE,"scan task failed: "+x);
            setStateAndNotify(STOPPED);
        } catch (Error e) {
            // We got an Error:
            // Should not happen unless we ran out of memory or
            // whatever - don't even try to notify, but
            // stop the scan anyway!
            //
            state=STOPPED;
            task.info = "Error: "+e;

            // rethrow error.
            //
            throw e;
        }
    }

    /**
     * MBeanNotification support - delegates to broadcaster.
     */
    public void addNotificationListener(NotificationListener listener,
            NotificationFilter filter, Object handback)
            throws IllegalArgumentException {
        broadcaster.addNotificationListener(listener, filter, handback);
    }

    // Switch this object state to the desired value an send
    // a notification. Don't call this method from within a
    // synchronized block!
    //
    private final void setStateAndNotify(ScanState desired) {
        final ScanState old = state;
        if (old == desired) return;
        state = desired;
        final AttributeChangeNotification n =
                new AttributeChangeNotification(this,
                getNextSeqNumber(),System.currentTimeMillis(),
                "state change","State",ScanState.class.getName(),
                String.valueOf(old),String.valueOf(desired));
        broadcaster.sendNotification(n);
    }


    /**
     * The {@link DirectoryScannerMXBean} may send two types of
     * notifications: filematch, and state attribute changed.
     **/
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[] {
            new MBeanNotificationInfo(
                    new String[] {FILE_MATCHES_NOTIFICATION},
                    Notification.class.getName(),
                    "Emitted when a file that matches the scan criteria is found"
                    ),
            new MBeanNotificationInfo(
                    new String[] {AttributeChangeNotification.ATTRIBUTE_CHANGE},
                    AttributeChangeNotification.class.getName(),
                    "Emitted when the State attribute changes"
                    )
        };
    }

    /**
     * MBeanNotification support - delegates to broadcaster.
     */
    public void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener);
    }

    /**
     * MBeanNotification support - delegates to broadcaster.
     */
    public void removeNotificationListener(NotificationListener listener,
            NotificationFilter filter, Object handback)
            throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener, filter, handback);
    }

    // Validates the given root directory, returns a File object for
    // that directory.
    // Throws IllegalArgumentException if the given root is not
    // acceptable.
    //
    private static File validateRoot(String root) {
        if (root == null)
            throw new IllegalArgumentException("no root specified");
        if (root.length() == 0)
            throw new IllegalArgumentException("specified root \"\" is invalid");
        final File f = new File(root);
        if (!f.canRead())
            throw new IllegalArgumentException("can't read "+root);
        if (!f.isDirectory())
            throw new IllegalArgumentException("no such directory: "+root);
        return f;
    }

}
