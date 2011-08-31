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


package com.sun.jmx.examples.scandir;

import static com.sun.jmx.examples.scandir.ScanManager.getNextSeqNumber;
import static com.sun.jmx.examples.scandir.ScanDirConfigMXBean.SaveState.*;
import com.sun.jmx.examples.scandir.config.XmlConfigUtils;
import com.sun.jmx.examples.scandir.config.DirectoryScannerConfig;
import com.sun.jmx.examples.scandir.config.FileMatch;
import com.sun.jmx.examples.scandir.config.ScanManagerConfig;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.*;
import javax.xml.bind.JAXBException;

/**
 * <p>The <code>ScanDirConfig</code> MBean is in charge of the
 * <i>scandir</i> application configuration.
 * </p>
 * <p>The <code>ScanDirConfig</code> MBean is able to
 * load and save the <i>scandir</i> application configuration to and from an
 * XML file.
 * </p>
 * <p>
 * It will let you also interactively modify that configuration, which you
 * can later save to the file, by calling {@link #save}, or discard, by
 * reloading the file without saving - see {@link #load}.
 * </p>
 * <p>
 * There can be as many <code>ScanDirConfigMXBean</code> registered
 * in the MBeanServer as you like, but only one of them will be identified as
 * the current configuration of the {@link ScanManagerMXBean}.
 * You can switch to another configuration by calling {@link
 * ScanManagerMXBean#setConfigurationMBean
 * ScanManagerMXBean.setConfigurationMBean}.
 * </p>
 * <p>
 * Once the current configuration has been loaded (by calling {@link #load})
 * or modified (by calling one of {@link #addDirectoryScanner
 * addDirectoryScanner}, {@link #removeDirectoryScanner removeDirectoryScanner}
 * or {@link #setConfiguration setConfiguration}) it can be pushed
 * to the {@link ScanManagerMXBean} by calling {@link
 * ScanManagerMXBean#applyConfiguration
 * ScanManagerMXBean.applyConfiguration(true)} -
 * <code>true</code> means that we apply the configuration from memory,
 * without first reloading the file.
 * </p>
 * <p>
 * The <code>ScanDirConfig</code> uses the XML annotated Java Beans defined
 * in the {@link com.sun.jmx.examples.scandir.config} package.
 * </p>
 * <p>
 * <u>Note:</u> The <code>ScanDirConfig</code> should probably use
 * {@code java.nio.channels.FileLock} and lock its configuration file so that
 * two <code>ScanDirConfig</code> object do not share the same file, but it
 * doesn't. Feel free to improve the application in that way.
 * </p>
 * @author Sun Microsystems, 2006 - All rights reserved.
 */
public class ScanDirConfig extends NotificationBroadcasterSupport
        implements ScanDirConfigMXBean, MBeanRegistration {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(ScanDirConfig.class.getName());

    // We will emit a notification when the save state of this object
    // chenges. We use directly the base notification class, with a
    // notification type that indicates the new state at which the
    // object has arrived.
    //
    // All these notification types will have the same prefix, which is
    // 'com.sun.jmx.examples.scandir.config'.
    //
    private final static String NOTIFICATION_PREFIX =
            ScanManagerConfig.class.getPackage().getName();

    /**
     * The <i>com.sun.jmx.examples.scandir.config.saved</i> notification
     * indicates that the configuration data was saved.
     **/
    public final static String NOTIFICATION_SAVED =
            NOTIFICATION_PREFIX+".saved";
    /**
     * The <i>com.sun.jmx.examples.scandir.config.loaded</i> notification
     * indicates that the configuration data was loaded.
     **/
    public final static String NOTIFICATION_LOADED =
            NOTIFICATION_PREFIX+".loaded";

    /**
     * The <i>com.sun.jmx.examples.scandir.config.modified</i> notification
     * indicates that the configuration data was modified.
     **/
    public final static String NOTIFICATION_MODIFIED =
            NOTIFICATION_PREFIX+".modified";

    // The array of MBeanNotificationInfo that will be exposed in the
    // ScanDirConfigMXBean MBeanInfo.
    // We will pass this array to the NotificationBroadcasterSupport
    // constructor.
    //
    private static MBeanNotificationInfo[] NOTIFICATION_INFO = {
        new MBeanNotificationInfo(
                new String[] {NOTIFICATION_SAVED},
                Notification.class.getName(),
                "Emitted when the configuration is saved"),
        new MBeanNotificationInfo(
                new String[] {NOTIFICATION_LOADED},
                Notification.class.getName(),
                "Emitted when the configuration is loaded"),
        new MBeanNotificationInfo(
                new String[] {NOTIFICATION_MODIFIED},
                Notification.class.getName(),
                "Emitted when the configuration is modified"),
    };

     // The ScanDirConfigMXBean configuration data.
    private volatile ScanManagerConfig config;

    // The name of the configuration file
    private String filename = null;

    // The name of this configuration. This is usually both equal to
    // config.getName() and objectName.getKeyProperty(name).
    private volatile String configname = null;

    // This object save state. CREATED is the initial state.
    //
    private volatile SaveState status = CREATED;

    /**
     * Creates a new {@link ScanDirConfigMXBean}.
     * <p>{@code ScanDirConfigMXBean} can be created by the {@link
     * ScanManagerMXBean}, or directly by a remote client, using
     * {@code createMBean} or {@code registerMBean}.
     * </p>
     * <p>{@code ScanDirConfigMXBean} created by the {@link
     * ScanManagerMXBean} will be unregistered by the
     * {@code ScanManagerMXBean}. {@code ScanDirConfigMXBean} created
     * directly by a remote client will not be unregistered by the
     * {@code ScanManagerMXBean} - this will remain to the responsibility of
     * the code/client that created them.
     * </p>
     * <p>This object is created empty, you should call load() if you want it
     *    to load its data from the configuration file.
     * </p>
     * @param  filename The configuration file used by this MBean.
     *         Can be null (in which case load() and save() will fail).
     *         Can point to a file that does not exists yet (in which case
     *         load() will fail if called before save(), and save() will
     *         attempt to create that file). Can point to an existing file,
     *         in which case load() will load that file and save() will save
     *         to that file.
     *
     **/
    public ScanDirConfig(String filename) {
        this(filename,null);
    }

    /**
     * Create a new ScanDirConfig MBean with an initial configuration.
     * @param filename The name of the configuration file.
     * @param initialConfig an initial configuration.
     **/
    public ScanDirConfig(String filename, ScanManagerConfig initialConfig) {
        super(NOTIFICATION_INFO);
        this.filename = filename;
        this.config = initialConfig;
    }


    // see ScanDirConfigMXBean
    public void load() throws IOException {
        if (filename == null)
            throw new UnsupportedOperationException("load");

        synchronized(this) {
            config = new XmlConfigUtils(filename).readFromFile();
            if (configname != null) config = config.copy(configname);
            else configname = config.getName();

            status=LOADED;
        }
        sendNotification(NOTIFICATION_LOADED);
    }

    // see ScanDirConfigMXBean
    public void save() throws IOException {
        if (filename == null)
            throw new UnsupportedOperationException("load");
        synchronized (this) {
            new XmlConfigUtils(filename).writeToFile(config);
            status = SAVED;
        }
        sendNotification(NOTIFICATION_SAVED);
    }

    // see ScanDirConfigMXBean
    public ScanManagerConfig getConfiguration() {
        synchronized (this) {
            return XmlConfigUtils.xmlClone(config);
        }
    }


    // sends a notification indicating the new save state.
    private void sendNotification(String type) {
        final Object source = (objectName==null)?this:objectName;
        final Notification n = new Notification(type,source,
                getNextSeqNumber(),
                "The configuration is "+
                type.substring(type.lastIndexOf('.')+1));
        sendNotification(n);
    }


    /**
     * Allows the MBean to perform any operations it needs before being
     * registered in the MBean server. If the name of the MBean is not
     * specified, the MBean can provide a name for its registration. If
     * any exception is raised, the MBean will not be registered in the
     * MBean server.
     * @param server The MBean server in which the MBean will be registered.
     * @param name The object name of the MBean. This name is null if the
     * name parameter to one of the createMBean or registerMBean methods in
     * the MBeanServer interface is null. In that case, this method will
     * try to guess its MBean name by examining its configuration data.
     * If its configuration data is null (nothing was provided in the
     * constructor) or doesn't contain a name, this method returns {@code null},
     * and registration will fail.
     * <p>
     * Otherwise, if {@code name} wasn't {@code null} or if a default name could
     * be constructed, the name of the configuration will be set to
     * the value of the ObjectName's {@code name=} key, and the configuration
     * data will always be renamed to reflect this change.
     * </p>
     *
     * @return The name under which the MBean is to be registered.
     * @throws Exception This exception will be caught by the MBean server and
     * re-thrown as an MBeanRegistrationException.
     */
    public ObjectName preRegister(MBeanServer server, ObjectName name)
        throws Exception {
        if (name == null) {
            if (config == null) return null;
            if (config.getName() == null) return null;
            name = ScanManager.
                    makeMBeanName(ScanDirConfigMXBean.class,config.getName());
        }
        objectName = name;
        mbeanServer = server;
        synchronized (this) {
            configname = name.getKeyProperty("name");
            if (config == null) config = new ScanManagerConfig(configname);
            else config = config.copy(configname);
        }
        return name;
    }

    /**
     * Allows the MBean to perform any operations needed after having
     * been registered in the MBean server or after the registration has
     * failed.
     * <p>This implementation does nothing</p>
     * @param registrationDone Indicates whether or not the MBean has been
     * successfully registered in the MBean server. The value false means
     * that the registration has failed.
     */
    public void postRegister(Boolean registrationDone) {
        // Nothing to do here.
    }

    /**
     * Allows the MBean to perform any operations it needs before being
     * unregistered by the MBean server.
     * <p>This implementation does nothing</p>
     * @throws Exception This exception will be caught by the MBean server and
     * re-thrown as an MBeanRegistrationException.
     */
    public void preDeregister() throws Exception {
        // Nothing to do here.
    }

    /**
     * Allows the MBean to perform any operations needed after having been
     * unregistered in the MBean server.
     * <p>This implementation does nothing</p>
     */
    public void postDeregister() {
        // Nothing to do here.
    }

    // see ScanDirConfigMXBean
    public String getConfigFilename() {
        return filename;
    }

    // see ScanDirConfigMXBean
    public void setConfiguration(ScanManagerConfig config) {
        synchronized (this) {
            if (config == null) {
                this.config = null;
                return;
            }

            if (configname == null)
                configname = config.getName();

            this.config = config.copy(configname);
            status = MODIFIED;
        }
        sendNotification(NOTIFICATION_MODIFIED);
    }

    // see ScanDirConfigMXBean
    public DirectoryScannerConfig
            addDirectoryScanner(String name, String dir, String filePattern,
                                long sizeExceedsMaxBytes, long sinceLastModified) {
         final DirectoryScannerConfig scanner =
                 new DirectoryScannerConfig(name);
         scanner.setRootDirectory(dir);
         if (filePattern!=null||sizeExceedsMaxBytes>0||sinceLastModified>0) {
            final FileMatch filter = new FileMatch();
            filter.setFilePattern(filePattern);
            filter.setSizeExceedsMaxBytes(sizeExceedsMaxBytes);
            if (sinceLastModified > 0)
                filter.setLastModifiedBefore(new Date(new Date().getTime()
                                                -sinceLastModified));
            scanner.addIncludeFiles(filter);
         }
         synchronized (this) {
            config.putScan(scanner);
            status = MODIFIED;
         }
         LOG.fine("config: "+config);
         sendNotification(NOTIFICATION_MODIFIED);
         return scanner;
    }

    // see ScanDirConfigMXBean
    public DirectoryScannerConfig removeDirectoryScanner(String name)
        throws IOException, InstanceNotFoundException {
        final DirectoryScannerConfig scanner;
        synchronized (this) {
            scanner = config.removeScan(name);
            if (scanner == null)
                throw new IllegalArgumentException(name+": scanner not found");
            status = MODIFIED;
        }
        sendNotification(NOTIFICATION_MODIFIED);
        return scanner;
    }

    // see ScanDirConfigMXBean
    public SaveState getSaveState() {
        return status;
    }

    // These methods are used by ScanManager to guess a configuration name from
    // a configuration filename.
    //
    static String DEFAULT = "DEFAULT";

    private static String getBasename(String name) {
        final int dot = name.indexOf('.');
        if (dot<0)  return name;
        if (dot==0) return getBasename(name.substring(1));
        return name.substring(0,dot);
    }

    static String guessConfigName(String configFileName,String defaultFile) {
        try {
            if (configFileName == null) return DEFAULT;
            final File f = new File(configFileName);
            if (f.canRead()) {
                final String confname = XmlConfigUtils.read(f).getName();
                if (confname != null && confname.length()>0) return confname;
            }
            final File f2 = new File(defaultFile);
            if (f.equals(f2)) return DEFAULT;
            final String guess = getBasename(f.getName());
            if (guess == null) return DEFAULT;
            if (guess.length()==0) return DEFAULT;
            return guess;
        } catch (Exception x) {
            return DEFAULT;
        }
    }

    // Set by preRegister()
    private volatile MBeanServer mbeanServer;
    private volatile ObjectName objectName;

}
