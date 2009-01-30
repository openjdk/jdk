/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.jmx.remote.security;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import javax.management.MBeanServer;
import javax.security.auth.Subject;

/**
 * <p>An object of this class implements the MBeanServerAccessController
 * interface and, for each of its methods, calls an appropriate checking
 * method and then forwards the request to a wrapped MBeanServer object.
 * The checking method may throw a SecurityException if the operation is
 * not allowed; in this case the request is not forwarded to the
 * wrapped object.</p>
 *
 * <p>This class implements the {@link #checkRead()} and {@link #checkWrite()}
 * methods based on an access level properties file containing username/access
 * level pairs. The set of username/access level pairs is passed either as a
 * filename which denotes a properties file on disk, or directly as an instance
 * of the {@link Properties} class.  In both cases, the name of each property
 * represents a username, and the value of the property is the associated access
 * level.  Thus, any given username either does not exist in the properties or
 * has exactly one access level. The same access level can be shared by several
 * usernames.</p>
 *
 * <p>The supported access level values are <i>readonly</i> and
 * <i>readwrite</i>.</p>
 */
public class MBeanServerFileAccessController
    extends MBeanServerAccessController {

    public static final String READONLY = "readonly";
    public static final String READWRITE = "readwrite";

    /**
     * <p>Create a new MBeanServerAccessController that forwards all the
     * MBeanServer requests to the MBeanServer set by invoking the {@link
     * #setMBeanServer} method after doing access checks based on read and
     * write permissions.</p>
     *
     * <p>This instance is initialized from the specified properties file.</p>
     *
     * @param accessFileName name of the file which denotes a properties
     * file on disk containing the username/access level entries.
     *
     * @exception IOException if the file does not exist, is a
     * directory rather than a regular file, or for some other
     * reason cannot be opened for reading.
     *
     * @exception IllegalArgumentException if any of the supplied access
     * level values differs from "readonly" or "readwrite".
     */
    public MBeanServerFileAccessController(String accessFileName)
        throws IOException {
        super();
        this.accessFileName = accessFileName;
        props = propertiesFromFile(accessFileName);
        checkValues(props);
    }

    /**
     * <p>Create a new MBeanServerAccessController that forwards all the
     * MBeanServer requests to <code>mbs</code> after doing access checks
     * based on read and write permissions.</p>
     *
     * <p>This instance is initialized from the specified properties file.</p>
     *
     * @param accessFileName name of the file which denotes a properties
     * file on disk containing the username/access level entries.
     *
     * @param mbs the MBeanServer object to which requests will be forwarded.
     *
     * @exception IOException if the file does not exist, is a
     * directory rather than a regular file, or for some other
     * reason cannot be opened for reading.
     *
     * @exception IllegalArgumentException if any of the supplied access
     * level values differs from "readonly" or "readwrite".
     */
    public MBeanServerFileAccessController(String accessFileName,
                                           MBeanServer mbs)
        throws IOException {
        this(accessFileName);
        setMBeanServer(mbs);
    }

    /**
     * <p>Create a new MBeanServerAccessController that forwards all the
     * MBeanServer requests to the MBeanServer set by invoking the {@link
     * #setMBeanServer} method after doing access checks based on read and
     * write permissions.</p>
     *
     * <p>This instance is initialized from the specified properties instance.
     * This constructor makes a copy of the properties instance using its
     * <code>clone</code> method and it is the copy that is consulted to check
     * the username and access level of an incoming connection. The original
     * properties object can be modified without affecting the copy. If the
     * {@link #refresh} method is then called, the
     * <code>MBeanServerFileAccessController</code> will make a new copy of the
     * properties object at that time.</p>
     *
     * @param accessFileProps properties list containing the username/access
     * level entries.
     *
     * @exception IllegalArgumentException if <code>accessFileProps</code> is
     * <code>null</code> or if any of the supplied access level values differs
     * from "readonly" or "readwrite".
     */
    public MBeanServerFileAccessController(Properties accessFileProps)
        throws IOException {
        super();
        if (accessFileProps == null)
            throw new IllegalArgumentException("Null properties");
        originalProps = accessFileProps;
        props = (Properties) accessFileProps.clone();
        checkValues(props);
    }

    /**
     * <p>Create a new MBeanServerAccessController that forwards all the
     * MBeanServer requests to the MBeanServer set by invoking the {@link
     * #setMBeanServer} method after doing access checks based on read and
     * write permissions.</p>
     *
     * <p>This instance is initialized from the specified properties instance.
     * This constructor makes a copy of the properties instance using its
     * <code>clone</code> method and it is the copy that is consulted to check
     * the username and access level of an incoming connection. The original
     * properties object can be modified without affecting the copy. If the
     * {@link #refresh} method is then called, the
     * <code>MBeanServerFileAccessController</code> will make a new copy of the
     * properties object at that time.</p>
     *
     * @param accessFileProps properties list containing the username/access
     * level entries.
     *
     * @param mbs the MBeanServer object to which requests will be forwarded.
     *
     * @exception IllegalArgumentException if <code>accessFileProps</code> is
     * <code>null</code> or if any of the supplied access level values differs
     * from "readonly" or "readwrite".
     */
    public MBeanServerFileAccessController(Properties accessFileProps,
                                           MBeanServer mbs)
        throws IOException {
        this(accessFileProps);
        setMBeanServer(mbs);
    }

    /**
     * Check if the caller can do read operations. This method does
     * nothing if so, otherwise throws SecurityException.
     */
    public void checkRead() {
        checkAccessLevel(READONLY);
    }

    /**
     * Check if the caller can do write operations.  This method does
     * nothing if so, otherwise throws SecurityException.
     */
    public void checkWrite() {
        checkAccessLevel(READWRITE);
    }

    /**
     * <p>Refresh the set of username/access level entries.</p>
     *
     * <p>If this instance was created using the
     * {@link #MBeanServerFileAccessController(String)} or
     * {@link #MBeanServerFileAccessController(String,MBeanServer)}
     * constructors to specify a file from which the entries are read,
     * the file is re-read.</p>
     *
     * <p>If this instance was created using the
     * {@link #MBeanServerFileAccessController(Properties)} or
     * {@link #MBeanServerFileAccessController(Properties,MBeanServer)}
     * constructors then a new copy of the <code>Properties</code> object
     * is made.</p>
     *
     * @exception IOException if the file does not exist, is a
     * directory rather than a regular file, or for some other
     * reason cannot be opened for reading.
     *
     * @exception IllegalArgumentException if any of the supplied access
     * level values differs from "readonly" or "readwrite".
     */
    public void refresh() throws IOException {
        synchronized (props) {
            if (accessFileName == null)
                props = (Properties) originalProps.clone();
            else
                props = propertiesFromFile(accessFileName);
            checkValues(props);
        }
    }

    private static Properties propertiesFromFile(String fname)
        throws IOException {
        FileInputStream fin = new FileInputStream(fname);
        try {
            Properties p = new Properties();
            p.load(fin);
            return p;
        } finally {
            fin.close();
        }
    }

    private void checkAccessLevel(String accessLevel) {
        final AccessControlContext acc = AccessController.getContext();
        final Subject s =
            AccessController.doPrivileged(new PrivilegedAction<Subject>() {
                    public Subject run() {
                        return Subject.getSubject(acc);
                    }
                });
        if (s == null) return; /* security has not been enabled */
        final Set<Principal> principals = s.getPrincipals();
        for (Principal p : principals) {
            String grantedAccessLevel;
            synchronized (props) {
                grantedAccessLevel = props.getProperty(p.getName());
            }
            if (grantedAccessLevel != null) {
                if (accessLevel.equals(READONLY) &&
                    (grantedAccessLevel.equals(READONLY) ||
                     grantedAccessLevel.equals(READWRITE)))
                    return;
                if (accessLevel.equals(READWRITE) &&
                    grantedAccessLevel.equals(READWRITE))
                    return;
            }
        }
        throw new SecurityException("Access denied! Invalid access level for " +
                                    "requested MBeanServer operation.");
    }

    private void checkValues(Properties props) {
        Collection<?> c = props.values();
        for (Iterator<?> i = c.iterator(); i.hasNext(); ) {
            final String accessLevel = (String) i.next();
            if (!accessLevel.equals(READONLY) &&
                !accessLevel.equals(READWRITE)) {
                throw new IllegalArgumentException(
                    "Syntax error in access level entry [" + accessLevel + "]");
            }
        }
    }

    private Properties props;
    private Properties originalProps;
    private String accessFileName;
}
