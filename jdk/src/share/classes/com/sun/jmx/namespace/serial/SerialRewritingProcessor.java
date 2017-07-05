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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Queue;

import javax.management.ObjectName;

/**
 * Class SerialRewritingProcessor. A RewritingProcessor that uses
 * Java Serialization to rewrite ObjectNames contained in
 * input & results...
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
class SerialRewritingProcessor extends RewritingProcessor {


    private static class CloneOutput extends ObjectOutputStream {
        Queue<Class<?>> classQueue = new LinkedList<Class<?>>();

        CloneOutput(OutputStream out) throws IOException {
            super(out);
        }

        @Override
        protected void annotateClass(Class<?> c) {
            classQueue.add(c);
        }

        @Override
        protected void annotateProxyClass(Class<?> c) {
            classQueue.add(c);
        }
    }

    private static class CloneInput extends ObjectInputStream {
        private final CloneOutput output;

        CloneInput(InputStream in, CloneOutput output) throws IOException {
            super(in);
            this.output = output;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass osc)
        throws IOException, ClassNotFoundException {
            Class<?> c = output.classQueue.poll();
            String expected = osc.getName();
            String found = (c == null) ? null : c.getName();
            if (!expected.equals(found)) {
                throw new InvalidClassException("Classes desynchronized: " +
                        "found " + found + " when expecting " + expected);
            }
            return c;
        }

        @Override
        protected Class<?> resolveProxyClass(String[] interfaceNames)
        throws IOException, ClassNotFoundException {
            return output.classQueue.poll();
        }
    }


    final String targetPrefix;
    final String sourcePrefix;
    final boolean identity;


    public SerialRewritingProcessor(String targetDirName) {
        this(targetDirName,null);
    }

    /** Creates a new instance of SerialRewritingProcessor */
    public SerialRewritingProcessor(final String remove, final String add) {
        super(new RoutingOnlyProcessor(remove,add));
        this.targetPrefix = remove;
        this.sourcePrefix = add;
        identity = targetPrefix.equals(sourcePrefix);
    }

    private <T> T  switchContext(T result, String from,String to)
            throws IOException, ClassNotFoundException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final CloneOutput ostream = new CloneOutput(baos);

        JMXNamespaceContext.serialize(ostream,result,from,null);
        ostream.flush();

        final byte[] bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final CloneInput istream = new CloneInput(bais, ostream);
        @SuppressWarnings("unchecked")
        final T clone = (T) JMXNamespaceContext.deserialize(istream,null,to);
        return clone;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T rewriteOutput(T result) {
        if (identity) return result;
        return (T) processOutput(result);
    }

    private Object processOutput(Object result) {
        try {
            if (result instanceof ObjectName)
                return toTargetContext((ObjectName) result);
            return switchContext(result,sourcePrefix,targetPrefix);
        } catch (ClassNotFoundException x) {
            throw new IllegalArgumentException("Can't process result: "+x,x);
        } catch (IOException x) {
            throw new IllegalArgumentException("Can't process result: "+x,x);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T rewriteInput(T input) {
        if (identity) return input;
        return (T) processInput(input);
    }

    private Object processInput(Object input) {
        try {
            if (input instanceof ObjectName)
                return toSourceContext((ObjectName) input);
            return switchContext(input,targetPrefix,sourcePrefix);
        } catch (ClassNotFoundException x) {
            throw new IllegalArgumentException("Can't process input: "+x,x);
        } catch (IOException x) {
            throw new IllegalArgumentException("Can't process input: "+x,x);
        }
    }

}
