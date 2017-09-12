/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.net;

import java.io.FileDescriptor;
import java.net.SocketException;
import java.net.SocketOption;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Set;
import jdk.internal.misc.JavaIOFileDescriptorAccess;
import jdk.internal.misc.SharedSecrets;

/**
 * Defines extended socket options, beyond those defined in
 * {@link java.net.StandardSocketOptions}. These options may be platform
 * specific.
 *
 * @since 1.8
 */
public final class ExtendedSocketOptions {

    private static class ExtSocketOption<T> implements SocketOption<T> {
        private final String name;
        private final Class<T> type;
        ExtSocketOption(String name, Class<T> type) {
            this.name = name;
            this.type = type;
        }
        @Override public String name() { return name; }
        @Override public Class<T> type() { return type; }
        @Override public String toString() { return name; }
    }

    private ExtendedSocketOptions() { }

    /**
     * Service level properties. When a security manager is installed,
     * setting or getting this option requires a {@link NetworkPermission}
     * {@code ("setOption.SO_FLOW_SLA")} or {@code "getOption.SO_FLOW_SLA"}
     * respectively.
     */
    public static final SocketOption<SocketFlow> SO_FLOW_SLA = new
        ExtSocketOption<SocketFlow>("SO_FLOW_SLA", SocketFlow.class);


    private static final PlatformSocketOptions platformSocketOptions =
            PlatformSocketOptions.get();

    private static final boolean flowSupported =
            platformSocketOptions.flowSupported();

    private static final Set<SocketOption<?>> extendedOptions = options();

    static Set<SocketOption<?>> options() {
        if (flowSupported)
            return Set.of(SO_FLOW_SLA);
        else
            return Collections.<SocketOption<?>>emptySet();
    }

    static {
        // Registers the extended socket options with the base module.
        sun.net.ext.ExtendedSocketOptions.register(
                new sun.net.ext.ExtendedSocketOptions(extendedOptions) {

            @Override
            public void setOption(FileDescriptor fd,
                                  SocketOption<?> option,
                                  Object value)
                throws SocketException
            {
                SecurityManager sm = System.getSecurityManager();
                if (sm != null)
                    sm.checkPermission(new NetworkPermission("setOption." + option.name()));

                if (fd == null || !fd.valid())
                    throw new SocketException("socket closed");

                if (option == SO_FLOW_SLA) {
                    assert flowSupported;
                    SocketFlow flow = checkValueType(value, option.type());
                    setFlowOption(fd, flow);
                } else {
                    throw new InternalError("Unexpected option " + option);
                }
            }

            @Override
            public Object getOption(FileDescriptor fd,
                                    SocketOption<?> option)
                throws SocketException
            {
                SecurityManager sm = System.getSecurityManager();
                if (sm != null)
                    sm.checkPermission(new NetworkPermission("getOption." + option.name()));

                if (fd == null || !fd.valid())
                    throw new SocketException("socket closed");

                if (option == SO_FLOW_SLA) {
                    assert flowSupported;
                    SocketFlow flow = SocketFlow.create();
                    getFlowOption(fd, flow);
                    return flow;
                } else {
                    throw new InternalError("Unexpected option " + option);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T checkValueType(Object value, Class<?> type) {
        if (!type.isAssignableFrom(value.getClass())) {
            String s = "Found: " + value.getClass() + ", Expected: " + type;
            throw new IllegalArgumentException(s);
        }
        return (T) value;
    }

    private static final JavaIOFileDescriptorAccess fdAccess =
            SharedSecrets.getJavaIOFileDescriptorAccess();

    private static void setFlowOption(FileDescriptor fd, SocketFlow f)
        throws SocketException
    {
        int status = platformSocketOptions.setFlowOption(fdAccess.get(fd),
                                                         f.priority(),
                                                         f.bandwidth());
        f.status(status);  // augment the given flow with the status
    }

    private static void getFlowOption(FileDescriptor fd, SocketFlow f)
        throws SocketException
    {
        int status = platformSocketOptions.getFlowOption(fdAccess.get(fd), f);
        f.status(status);  // augment the given flow with the status
    }

    static class PlatformSocketOptions {

        protected PlatformSocketOptions() {}

        @SuppressWarnings("unchecked")
        private static PlatformSocketOptions newInstance(String cn) {
            Class<PlatformSocketOptions> c;
            try {
                c = (Class<PlatformSocketOptions>)Class.forName(cn);
                return c.getConstructor(new Class<?>[] { }).newInstance();
            } catch (ReflectiveOperationException x) {
                throw new AssertionError(x);
            }
        }

        private static PlatformSocketOptions create() {
            String osname = AccessController.doPrivileged(
                    new PrivilegedAction<String>() {
                        public String run() {
                            return System.getProperty("os.name");
                        }
                    });
            if ("SunOS".equals(osname))
                return newInstance("jdk.net.SolarisSocketOptions");
            return new PlatformSocketOptions();
        }

        private static final PlatformSocketOptions instance = create();

        static PlatformSocketOptions get() {
            return instance;
        }

        int setFlowOption(int fd, int priority, long bandwidth)
            throws SocketException
        {
            throw new UnsupportedOperationException("unsupported socket option");
        }

        int getFlowOption(int fd, SocketFlow f) throws SocketException {
            throw new UnsupportedOperationException("unsupported socket option");
        }

        boolean flowSupported() {
            return false;
        }
    }
}
