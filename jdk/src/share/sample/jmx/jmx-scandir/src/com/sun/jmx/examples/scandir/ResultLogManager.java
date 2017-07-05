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
import com.sun.jmx.examples.scandir.config.ResultLogConfig;
import com.sun.jmx.examples.scandir.config.XmlConfigUtils;
import com.sun.jmx.examples.scandir.config.ResultRecord;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import javax.xml.bind.JAXBException;

/**
 * The <code>ResultLogManager</code> is in charge of managing result logs.
 * {@link DirectoryScanner DirectoryScanners} can be configured to log a
 * {@link ResultRecord} whenever they take action upon a file that
 * matches their set of matching criteria.
 * The <code>ResultLogManagerMXBean</code> is responsible for storing these
 * results in its result logs.
 * <p>The <code>ResultLogManagerMXBean</code> can be configured to log
 * these records to a flat file, or into a log held in memory, or both.
 * Both logs (file and memory) can be configured with a maximum capacity.
 * <br>When the maximum capacity of the memory log is reached - its first
 * entry (i.e. its eldest entry) is removed to make place for the latest.
 * <br>When the maximum capacity of the file log is reached, the file is
 * renamed by appending a tilde '~' to its name and a new result log is created.
 *
 *
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
public class ResultLogManager extends NotificationBroadcasterSupport
        implements ResultLogManagerMXBean, MBeanRegistration {

    /**
     * The default singleton name of the {@link ResultLogManagerMXBean}.
     **/
    public static final ObjectName RESULT_LOG_MANAGER_NAME =
            ScanManager.makeSingletonName(ResultLogManagerMXBean.class);

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(ResultLogManager.class.getName());

    // The memory log
    //
    private final List<ResultRecord> memoryLog;

    // Whether the memory log capacity was reached. In that case every
    // new entry triggers the deletion of the eldest one.
    //
    private volatile boolean memCapacityReached = false;

    // The maximum number of record that the memory log can
    // contain.
    //
    private volatile int memCapacity;

    // The maximum number of record that the ResultLogManager can
    // log in the log file before creating a new file.
    //
    private volatile long fileCapacity;

    // The current log file.
    //
    private volatile File logFile;

    // The OutputStream of the current log file.
    //
    private volatile OutputStream logStream = null;

    // number of record that this object has logged in the log file
    // since the log file was created. Creating a new file or clearing
    // the log file reset this value to '0'
    //
    private volatile long logCount = 0;

    // The ResultLogManager config - modified whenever
    // ScanManager.applyConfiguration is called.
    //
    private volatile ResultLogConfig config;

    /**
     * Create a new ResultLogManagerMXBean. This constructor is package
     * protected: only the {@link ScanManager} can create a
     * <code>ResultLogManager</code>.
     **/
    ResultLogManager() {
        // Instantiate the memory log - override the add() method so that
        // it removes the head of the list when the maximum capacity is
        // reached. Note that add() is the only method we will be calling,
        // otherwise we would have to override all the other flavors
        // of adding methods. Note also that this implies that the memoryLog
        // will *always* remain encapsulated in this object and is *never*
        // handed over (otherwise we wouldn't be able to ensure that
        // add() is the only method ever called to add a record).
        //
        memoryLog =
                Collections.synchronizedList(new LinkedList<ResultRecord>() {
            public synchronized boolean add(ResultRecord e) {
                final int max = getMemoryLogCapacity();
                while (max > 0 && size() >= max) {
                    memCapacityReached = true;
                    removeFirst();
                }
                return super.add(e);
            }
        });

        // default memory capacity
        memCapacity = 2048;

        // default file capacity: 0 means infinite ;-)
        fileCapacity = 0;

        // by default logging to file is disabled.
        logFile = null;

        // Until the ScanManager apply a new configuration, we're going to
        // work with a default ResultLogConfig object.
        config = new ResultLogConfig();
        config.setMemoryMaxRecords(memCapacity);
        config.setLogFileName(getLogFileName(false));
        config.setLogFileMaxRecords(fileCapacity);
    }


    /**
     * Allows the MBean to perform any operations it needs before being
     * registered in the MBean server.
     * <p>If the name of the MBean is not
     * specified, the MBean can provide a name for its registration. If
     * any exception is raised, the MBean will not be registered in the
     * MBean server.</p>
     * <p>The {@code ResultLogManager} uses this method to supply its own
     * default singleton ObjectName (if <var>name</var> parameter is null).
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
    public ObjectName preRegister(MBeanServer server, ObjectName name)
    throws Exception {
        if (name == null)
            name = RESULT_LOG_MANAGER_NAME;
        objectName = name;
        mbeanServer = server;
        return name;
    }

    /**
     * Allows the MBean to perform any operations needed after having
     * been registered in the MBean server or after the registration has
     * failed.
     * <p>This implementation does nothing.</p>
     * @param registrationDone Indicates whether or not the MBean has been
     * successfully registered in the MBean server. The value false means
     * that the registration has failed.
     */
    public void postRegister(Boolean registrationDone) {
        // Don't need to do anything here.
    }

    /**
     * Allows the MBean to perform any operations it needs before being
     * unregistered by the MBean server.
     * <p>This implementation does nothing.</p>
     * @throws Exception This exception will be caught by the MBean server and
     * re-thrown as an MBeanRegistrationException.
     */
    public void preDeregister() throws Exception {
        // Don't need to do anything here.
    }

    /**
     * Allows the MBean to perform any operations needed after having been
     * unregistered in the MBean server.
     * <p>Closes the log file stream, if it is still open.</p>
     */
    public void postDeregister() {
        try {
            if (logStream != null) {
                synchronized(this)  {
                    logStream.flush();
                    logStream.close();
                    logFile = null;
                    logStream = null;
                }
            }
        } catch (Exception x) {
            LOG.finest("Failed to close log properly: "+x);
        }
    }

    /**
     * Create a new empty log file from the given basename, renaming
     * previously existing file by appending '~' to its name.
     **/
    private File createNewLogFile(String basename) throws IOException {
        return XmlConfigUtils.createNewXmlFile(basename);
    }

    /**
     * Check whether a new log file should be created.
     * If a new file needs to be created, creates it, renaming
     * previously existing file by appending '~' to its name.
     * Also reset the log count and file capacity.
     * Sends a notification indicating that the log file was changed.
     * Returns the new log stream;
     * Creation of a new file can be forced by passing force=true.
     **/
    private OutputStream checkLogFile(String basename, long maxRecords,
                                      boolean force)
    throws IOException {
        final OutputStream newStream;
        synchronized(this) {
            if ((force==false) && (logCount < maxRecords))
                return logStream;
            final OutputStream oldStream = logStream;

            // First close the stream. On some platforms you cannot rename
            // a file that has open streams...
            //
            if (oldStream != null) {
                oldStream.flush();
                oldStream.close();
            }
            final File newFile = (basename==null)?null:createNewLogFile(basename);

            newStream = (newFile==null)?null:new FileOutputStream(newFile,true);
            logStream = newStream;
            logFile = newFile;
            fileCapacity = maxRecords;
            logCount = 0;
        }
        sendNotification(new Notification(LOG_FILE_CHANGED,objectName,
                getNextSeqNumber(),
                basename));
        return newStream;
    }

    // see ResultLogManagerMXBean
    public void log(ResultRecord record)
    throws IOException {
        if (memCapacity > 0) logToMemory(record);
        if (logFile != null) logToFile(record);
    }

    // see ResultLogManagerMXBean
    public ResultRecord[] getMemoryLog() {
        return memoryLog.toArray(new ResultRecord[0]);
    }

    // see ResultLogManagerMXBean
    public int getMemoryLogCapacity() {
        return memCapacity;
    }

    // see ResultLogManagerMXBean
    public void setMemoryLogCapacity(int maxRecords)  {
        synchronized(this) {
            memCapacity = maxRecords;
            if (memoryLog.size() < memCapacity)
                memCapacityReached = false;
            config.setMemoryMaxRecords(maxRecords);
        }
    }

    // see ResultLogManagerMXBean
    public void setLogFileCapacity(long maxRecords)
    throws IOException {
        synchronized (this) {
            fileCapacity = maxRecords;
            config.setLogFileMaxRecords(maxRecords);
        }
        checkLogFile(getLogFileName(),fileCapacity,false);
    }

    // see ResultLogManagerMXBean
    public long getLogFileCapacity()  {
        return fileCapacity;
    }

    // see ResultLogManagerMXBean
    public long getLoggedCount() {
        return logCount;
    }

    // see ResultLogManagerMXBean
    public void newLogFile(String logname, long maxRecord)
    throws IOException {
        checkLogFile(logname,maxRecord,true);
        config.setLogFileName(getLogFileName(false));
        config.setLogFileMaxRecords(getLogFileCapacity());
    }

    // see ResultLogManagerMXBean
    public String getLogFileName() {
        return getLogFileName(true);
    }

    // see ResultLogManagerMXBean
    public void clearLogs() throws IOException {
        clearMemoryLog();
        clearLogFile();
    }

    // Clear the memory log, sends a notification indicating that
    // the memory log was cleared.
    //
    private void clearMemoryLog()throws IOException {
        synchronized(this) {
            memoryLog.clear();
            memCapacityReached = false;
        }
        sendNotification(new Notification(MEMORY_LOG_CLEARED,
                objectName,
                getNextSeqNumber(),"memory log cleared"));
    }

    // Clears the log file.
    //
    private void clearLogFile() throws IOException {
        // simply force the creation of a new log file.
        checkLogFile(getLogFileName(),fileCapacity,true);
    }

    // Log a record to the memory log. Send a notification if the
    // maximum capacity of the memory log is reached.
    //
    private void logToMemory(ResultRecord record) {

        final boolean before = memCapacityReached;
        final boolean after;
        synchronized(this) {
            memoryLog.add(record);
            after = memCapacityReached;
        }
        if (before==false && after==true)
            sendNotification(new Notification(MEMORY_LOG_MAX_CAPACITY,
                    objectName,
                    getNextSeqNumber(),"memory log capacity reached"));
    }


    // Log a record to the memory log. Send a notification if the
    // maximum capacity of the memory log is reached.
    //
    private void logToFile(ResultRecord record) throws IOException {
        final String basename;
        final long   maxRecords;
        synchronized (this) {
            if (logFile == null) return;
            basename = getLogFileName(false);
            maxRecords = fileCapacity;
        }

        // Get the stream into which we should log.
        final OutputStream stream =
                checkLogFile(basename,maxRecords,false);

        // logging to file now disabled - too bad.
        if (stream == null) return;

        synchronized (this) {
            try {
                XmlConfigUtils.write(record,stream,true);
                stream.flush();
                // don't increment logCount if we were not logging in logStream.
                if (stream == logStream) logCount++;
            } catch (JAXBException x) {
                final IllegalArgumentException iae =
                        new IllegalArgumentException("bad record",x);
                LOG.finest("Failed to log record: "+x);
                throw iae;
            }
        }
    }

    /**
     * The notification type which indicates that the log file was switched:
     * <i>com.sun.jmx.examples.scandir.log.file.switched</i>.
     * The message contains the name of the new file (or null if log to file
     * is now disabled).
     **/
    public final static String LOG_FILE_CHANGED =
            "com.sun.jmx.examples.scandir.log.file.switched";

    /**
     * The notification type which indicates that the memory log capacity has
     * been reached:
     * <i>com.sun.jmx.examples.scandir.log.memory.full</i>.
     **/
    public final static String MEMORY_LOG_MAX_CAPACITY =
            "com.sun.jmx.examples.scandir.log.memory.full";

    /**
     * The notification type which indicates that the memory log was
     * cleared:
     * <i>com.sun.jmx.examples.scandir.log.memory.cleared</i>.
     **/
    public final static String MEMORY_LOG_CLEARED =
            "com.sun.jmx.examples.scandir.log.memory.cleared";

    /**
     * This MBean emits three kind of notifications:
     * <pre>
     *    <i>com.sun.jmx.examples.scandir.log.file.switched</i>
     *    <i>com.sun.jmx.examples.scandir.log.memory.full</i>
     *    <i>com.sun.jmx.examples.scandir.log.memory.cleared</i>
     * </pre>
     **/
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[] {
            new MBeanNotificationInfo(new String[] {
                LOG_FILE_CHANGED},
                    Notification.class.getName(),
                    "Emitted when the log file is switched")
                    ,
            new MBeanNotificationInfo(new String[] {
                MEMORY_LOG_MAX_CAPACITY},
                    Notification.class.getName(),
                    "Emitted when the memory log capacity is reached")
                    ,
            new MBeanNotificationInfo(new String[] {
                MEMORY_LOG_CLEARED},
                    Notification.class.getName(),
                    "Emitted when the memory log is cleared")
        };
    }

    // Return the name of the log file, or null if logging to file is
    // disabled.
    private String getLogFileName(boolean absolute) {
        synchronized (this) {
            if (logFile == null) return null;
            if (absolute) return logFile.getAbsolutePath();
            return logFile.getPath();
        }
    }

    // This method is be called by the ScanManagerMXBean when a configuration
    // is applied.
    //
    void setConfig(ResultLogConfig logConfigBean) throws IOException {
        if (logConfigBean == null)
            throw new IllegalArgumentException("logConfigBean is null");
        synchronized (this) {
            config = logConfigBean;
            setMemoryLogCapacity(config.getMemoryMaxRecords());
        }
        final String filename = config.getLogFileName();
        final String logname  = getLogFileName(false);
        if ((filename != null && !filename.equals(logname))
        || (filename == null && logname != null)) {
            newLogFile(config.getLogFileName(),
                    config.getLogFileMaxRecords());
        } else {
            setLogFileCapacity(config.getLogFileMaxRecords());
        }
    }

    // This method is called by the ScanManagerMXBean when
    // applyCurrentResultLogConfig() is called.
    //
    ResultLogConfig getConfig() {
        return config;
    }


    // Set by preRegister().
    private MBeanServer mbeanServer;
    private ObjectName objectName;



}
