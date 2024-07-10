/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package javax.management.remote.http;

import java.io.*;
import java.util.Map;
import javax.management.*;
import javax.management.remote.*;

public class HttpRestConnector implements JMXConnector {

    protected JMXServiceURL url;
    protected Map<String,?> env;

    public HttpRestConnector(JMXServiceURL url, Map<String,?> env) {
        this.url = url;
    }

    public void connect() throws IOException {

    }

    public void connect(Map<String,?> env) throws IOException {
        this.env = env;
    }

    public MBeanServerConnection getMBeanServerConnection() throws IOException {
        MBeanServerConnection mbsc = new HttpRestConnection(url.getHost(), url.getPort(), env);
        return mbsc;
    }

    public void close() throws IOException {

    }
    public void addConnectionNotificationListener(NotificationListener listener,
                                          NotificationFilter filter,
                                          Object handback) {

    }

    public void removeConnectionNotificationListener(NotificationListener listener)
            throws ListenerNotFoundException {

    }

    public void removeConnectionNotificationListener(NotificationListener l,
                                                     NotificationFilter f,
                                                     Object handback)
            throws ListenerNotFoundException {

    }

    public String getConnectionId() throws IOException {
        return null;
    }
}
