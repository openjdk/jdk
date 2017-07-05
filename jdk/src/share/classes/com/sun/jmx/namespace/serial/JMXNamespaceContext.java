/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.jmx.namespace.serial;

import com.sun.jmx.defaults.JmxProperties;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * The JMXNamespaceContext class is used to implement a thread local
 * serialization / deserialization context for namespaces.
 * <p>
 * This class is consulted by {@link javax.management.ObjectName} at
 * serialization / deserialization time.
 * The serialization or deserialization context is established by
 * by the {@link SerialRewritingProcessor} defined in this package.
 * <p>
 * These classes are Sun proprietary APIs, subject to change without
 * notice. Do not use these classes directly.
 * The public API to rewrite ObjectNames embedded in parameters is
 * defined in {@link javax.management.namespace.JMXNamespaces}.
 *
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
public class JMXNamespaceContext {

    private static final Logger LOG = JmxProperties.NAMESPACE_LOGGER;

    public final String prefixToRemove;
    public final String prefixToAdd;

    private JMXNamespaceContext(String add, String remove) {
        prefixToRemove = (remove==null?"":remove);
        prefixToAdd    = (add==null?"":add);
    }

    private final static class SerialContext {
        private JMXNamespaceContext serializationContext;
        private JMXNamespaceContext deserializationContext;
        public SerialContext(){
            serializationContext = new JMXNamespaceContext("","");
            deserializationContext = new JMXNamespaceContext("","");
        }
    }

    private final static ThreadLocal<SerialContext> prefix =
            new ThreadLocal<SerialContext>() {
        @Override
        protected SerialContext initialValue() {
            return new SerialContext();
        }
    };

    public static JMXNamespaceContext getSerializationContext() {
        return prefix.get().serializationContext;
    }

    public static JMXNamespaceContext getDeserializationContext() {
        return prefix.get().deserializationContext;
    }

    private static String[] setSerializationContext(String oldPrefix,
            String newPrefix) {
        final SerialContext c = prefix.get();
        JMXNamespaceContext dc = c.serializationContext;
        String[] old = {dc.prefixToRemove, dc.prefixToAdd};
        c.serializationContext = new JMXNamespaceContext(newPrefix,oldPrefix);
        return old;
    }

    private static String[] setDeserializationContext(String oldPrefix,
            String newPrefix) {
        final SerialContext c = prefix.get();
        JMXNamespaceContext dc = c.deserializationContext;
        String[] old = {dc.prefixToRemove, dc.prefixToAdd};
        c.deserializationContext = new JMXNamespaceContext(newPrefix,oldPrefix);
        return old;
    }

    static void serialize(ObjectOutputStream stream, Object obj,
            String prefixToRemove, String prefixToAdd)
            throws IOException {
        final String[] old =
                setSerializationContext(prefixToRemove,prefixToAdd);
        try {
            stream.writeObject(obj);
        } finally {
            try {
                setSerializationContext(old[0],old[1]);
            } catch (Exception x) {
                LOG.log(Level.FINEST,
                        "failed to restore serialization context",x);
            }
        }
    }

    static Object deserialize(ObjectInputStream stream,
            String prefixToRemove,
            String prefixToAdd)
            throws IOException, ClassNotFoundException {
        final String[] old =
                setDeserializationContext(prefixToRemove,prefixToAdd);
        try {
            return stream.readObject();
        } finally {
            try {
                setDeserializationContext(old[0],old[1]);
            } catch (Exception x) {
                LOG.log(Level.FINEST,
                        "failed to restore serialization context",x);
            }
        }
    }

}
