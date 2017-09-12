/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.org.glassfish.external.amx;

import javax.management.ObjectName;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;

import java.io.IOException;


/**
 * AMX behavior specific to Glassfish V3.
 */
public final class AMXGlassfish
{
    public static final String DEFAULT_JMX_DOMAIN = "amx";

    /** Default domain support */
    public static final AMXGlassfish DEFAULT = new AMXGlassfish(DEFAULT_JMX_DOMAIN);

    private final String     mJMXDomain;
    private final ObjectName mDomainRoot;

    /** Anything other than {@link #DEFAULT} is not supported in Glassfish V3 */
    public AMXGlassfish(final String jmxDomain)
    {
        mJMXDomain = jmxDomain;
        mDomainRoot = newObjectName("", "domain-root", null);
    }

    /** Return a version string, or null if not running in Glassfish */
    public static String getGlassfishVersion()
    {
        // must all exist as a check to verify that it's Glassfish V3
        final String version = System.getProperty( "glassfish.version" );
        return version;
    }


    /** JMX domain used by AMX MBeans.
     * <p>
     * All MBeans in this domain must be AMX-compliant, see http://tinyurl.com/nryoqp =
    https://glassfish.dev.java.net/nonav/v3/admin/planning/V3Changes/V3_AMX_SPI.html
    */
    public String amxJMXDomain()
    {
        return mJMXDomain;
    }

    /** JMX domain used by AMX support MBeans.  Private use only */
    public String amxSupportDomain()
    {
        return amxJMXDomain() + "-support";
    }

    /** name of the Domain Admin Server (DAS) as found in an ObjectName */
    public String dasName()
    {
        return "server";
    }

    /** name of the Domain Admin Server (DAS) &lt;config> */
    public String dasConfig()
    {
        return dasName() + "-config";
    }

    /** return the ObjectName of the AMX DomainRoot MBean */
    public ObjectName domainRoot()
    {
        return mDomainRoot;
    }

    /** ObjectName for top-level monitoring MBean (parent of those for each server) */
    public ObjectName monitoringRoot()
    {
        return newObjectName("/", "mon", null);
    }

    /** ObjectName for top-level monitoring MBean for specified server */
    public ObjectName serverMon(final String serverName)
    {
        return newObjectName("/mon", "server-mon", serverName);
    }

    /** ObjectName for top-level monitoring MBean for the DAS. */
    public ObjectName serverMonForDAS() {
        return serverMon( "server" ) ;
    }

    /** Make a new AMX ObjectName with unchecked exception.
     *  name must be null to create a singleton ObjectName.
     *  Note that the arguments must not contain the characters
     * @param pp The parent part
     * @param type The ObjectName type
     * @param name The ObjectName name
     * @return The objectname with pp, type, and (optionally) name.
     */
    public ObjectName newObjectName(
            final String pp,
            final String type,
            final String name)
    {
        String props = prop(AMX.PARENT_PATH_KEY, pp) + "," + prop(AMX.TYPE_KEY, type);
        if (name != null) {
            props = props + "," + prop(AMX.NAME_KEY, name);
        }

        return newObjectName( props);
    }

    /** Make a new ObjectName for AMX domain with unchecked exception */
    public ObjectName newObjectName(final String s)
    {
        String name = s;
        if ( ! name.startsWith( amxJMXDomain() ) ) {
            name = amxJMXDomain() + ":" + name;
        }

        return AMXUtil.newObjectName( name );
    }

    private static String prop(final String key, final String value)
    {
        return key + "=" + value;
    }

    /**
        ObjectName for {@link BootAMXMBean}
     */
    public ObjectName getBootAMXMBeanObjectName()
    {
        return AMXUtil.newObjectName( amxSupportDomain() + ":type=boot-amx" );
    }

    /**
    Invoke the bootAMX() method on {@link BootAMXMBean}.  Upon return,
    AMX continues to load.
    A cilent should call {@link invokeWaitAMXReady} prior to use.
    */
    public void invokeBootAMX(final MBeanServerConnection conn)
    {
        // start AMX and wait for it to be ready
        try
        {
            conn.invoke( getBootAMXMBeanObjectName(), BootAMXMBean.BOOT_AMX_OPERATION_NAME, null, null);
        }
        catch (final Exception e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
        Invoke the waitAMXReady() method on the DomainRoot MBean, which must already be loaded.
     */
    private static void invokeWaitAMXReady(final MBeanServerConnection conn, final ObjectName objectName)
    {
        try
        {
            conn.invoke( objectName, "waitAMXReady", null, null );
        }
        catch( final Exception e )
        {
            throw new RuntimeException(e);
        }
    }

      /**
        Listen for the registration of AMX DomainRoot
        Listening starts automatically.
     */
    public <T extends MBeanListener.Callback> MBeanListener<T> listenForDomainRoot(
        final MBeanServerConnection server,
        final T callback)
    {
        final MBeanListener<T> listener = new MBeanListener<T>( server, domainRoot(), callback);
        listener.startListening();
        return listener;
    }

    private static final class WaitForDomainRootListenerCallback extends MBeanListener.CallbackImpl {
        private final MBeanServerConnection mConn;

        public WaitForDomainRootListenerCallback( final MBeanServerConnection conn ) {
            mConn = conn;
        }

        @Override
        public void mbeanRegistered(final ObjectName objectName, final MBeanListener listener) {
            super.mbeanRegistered(objectName,listener);
            invokeWaitAMXReady(mConn, objectName);
            mLatch.countDown();
        }
    }

    /**
        Wait until AMX has loaded and is ready for use.
        <p>
        This will <em>not</em> cause AMX to load; it will block forever until AMX is ready. In other words,
        don't call this method unless it's a convenient thread that can wait forever.
     */
    public ObjectName waitAMXReady( final MBeanServerConnection server)
    {
        final WaitForDomainRootListenerCallback callback = new WaitForDomainRootListenerCallback(server);
        listenForDomainRoot( server, callback );
        callback.await();
        return callback.getRegistered();
    }

    /**
        Listen for the registration of the {@link BootAMXMBean}.
        Listening starts automatically.  See {@link AMXBooter#BootAMXCallback}.
     */
    public <T extends MBeanListener.Callback> MBeanListener<T> listenForBootAMX(
        final MBeanServerConnection server,
        final T callback)
    {
        final MBeanListener<T> listener = new MBeanListener<T>( server, getBootAMXMBeanObjectName(), callback);
        listener.startListening();
        return listener;
    }

    /**
        Callback for {@link MBeanListener} that waits for the BootAMXMBean to appear;
        it always will load early in server startup. Once it has loaded, AMX can be booted
        via {@link #bootAMX}.  A client should normally just call {@link #bootAMX}, but
        this callback may be suclassed if desired, and used as a trigger to
        boot AMX and then take other dependent actions.
     */
    public static class BootAMXCallback extends MBeanListener.CallbackImpl
    {
        private final MBeanServerConnection mConn;
        public BootAMXCallback(final MBeanServerConnection conn)
        {
            mConn = conn;
        }

        @Override
        public void mbeanRegistered(final ObjectName objectName, final MBeanListener listener)
        {
            super.mbeanRegistered(objectName, listener);
            mLatch.countDown();
        }
    }

    /**
    Ensure that AMX is loaded and ready to use.  This method returns only when all
    AMX subsystems have been loaded.
    It can be called more than once without ill effect, subsequent calls are ignored.
    @param conn connection to the MBeanServer
    @return the ObjectName of the domain-root MBean
     */
    public ObjectName bootAMX(final MBeanServerConnection conn)
        throws IOException
    {
        final ObjectName domainRoot = domainRoot();

        if ( !conn.isRegistered( domainRoot ) )
        {
            // wait for the BootAMXMBean to be available (loads at startup)
            final BootAMXCallback callback = new BootAMXCallback(conn);
            listenForBootAMX(conn, callback);
            callback.await(); // block until the MBean appears

            invokeBootAMX(conn);

            final WaitForDomainRootListenerCallback drCallback = new WaitForDomainRootListenerCallback(conn);
            listenForDomainRoot(conn, drCallback);
            drCallback.await();

            invokeWaitAMXReady(conn, domainRoot);
        }
        else
        {
            invokeWaitAMXReady(conn, domainRoot );
        }
        return domainRoot;
    }

    public ObjectName bootAMX(final MBeanServer server)
    {
        try
        {
            return bootAMX( (MBeanServerConnection)server);
        }
        catch( final IOException e )
        {
            throw new RuntimeException(e);
        }
    }
}
