/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.streaming;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.streaming.XMLReaderException;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.xml.sax.InputSource;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessController;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.xml.internal.ws.resources.StreamingMessages;

/**
 * Factory for {@link XMLStreamReader}.
 *
 * <p>
 * This wraps {@link XMLInputFactory} and allows us to reuse {@link XMLStreamReader} instances
 * when appropriate.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("StaticNonFinalUsedInInitialization")
public abstract class XMLStreamReaderFactory {

    private static final Logger LOGGER = Logger.getLogger(XMLStreamReaderFactory.class.getName());

    private static final String CLASS_NAME_OF_WSTXINPUTFACTORY = "com.ctc.wstx.stax.WstxInputFactory";

    /**
     * Singleton instance.
     */
    private static volatile ContextClassloaderLocal<XMLStreamReaderFactory> streamReader =
            new ContextClassloaderLocal<XMLStreamReaderFactory>() {

                @Override
                protected XMLStreamReaderFactory initialValue() {

                    XMLInputFactory xif = getXMLInputFactory();
                    XMLStreamReaderFactory f=null;

                    // this system property can be used to disable the pooling altogether,
                    // in case someone hits an issue with pooling in the production system.
                    if(!getProperty(XMLStreamReaderFactory.class.getName()+".noPool")) {
                        f = Zephyr.newInstance(xif);
                    }

                    if(f==null) {
                        // is this Woodstox?
                        if (xif.getClass().getName().equals(CLASS_NAME_OF_WSTXINPUTFACTORY)) {
                            f = new Woodstox(xif);
                        }
                    }

                    if (f==null) {
                        f = new Default();
                    }

                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "XMLStreamReaderFactory instance is = {0}", f);
                    }
                    return f;
                }
            };

    private static XMLInputFactory getXMLInputFactory() {
        XMLInputFactory xif = null;
        if (getProperty(XMLStreamReaderFactory.class.getName()+".woodstox")) {
            try {
                xif = (XMLInputFactory)Class.forName("com.ctc.wstx.stax.WstxInputFactory").newInstance();
            } catch (Exception e) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, StreamingMessages.WOODSTOX_CANT_LOAD(CLASS_NAME_OF_WSTXINPUTFACTORY), e);
                }
            }
        }
        if (xif == null) {
             xif = XmlUtil.newXMLInputFactory(true);
        }
        xif.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        xif.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xif.setProperty(XMLInputFactory.IS_COALESCING, true);

        return xif;
    }

    /**
     * Overrides the singleton {@link XMLStreamReaderFactory} instance that
     * the JAX-WS RI uses.
     */
    public static void set(XMLStreamReaderFactory f) {
        if(f==null) {
            throw new IllegalArgumentException();
        }
        streamReader.set(f);
    }

    public static XMLStreamReaderFactory get() {
        return streamReader.get();
    }

    public static XMLStreamReader create(InputSource source, boolean rejectDTDs) {
        try {
            // Char stream available?
            if (source.getCharacterStream() != null) {
                return get().doCreate(source.getSystemId(), source.getCharacterStream(), rejectDTDs);
            }

            // Byte stream available?
            if (source.getByteStream() != null) {
                return get().doCreate(source.getSystemId(), source.getByteStream(), rejectDTDs);
            }

            // Otherwise, open URI
            return get().doCreate(source.getSystemId(), new URL(source.getSystemId()).openStream(),rejectDTDs);
        } catch (IOException e) {
            throw new XMLReaderException("stax.cantCreate",e);
        }
    }

    public static XMLStreamReader create(@Nullable String systemId, InputStream in, boolean rejectDTDs) {
        return get().doCreate(systemId,in,rejectDTDs);
    }

    public static XMLStreamReader create(@Nullable String systemId, InputStream in, @Nullable String encoding, boolean rejectDTDs) {
        return (encoding == null)
                ? create(systemId, in, rejectDTDs)
                : get().doCreate(systemId,in,encoding,rejectDTDs);
    }

    public static XMLStreamReader create(@Nullable String systemId, Reader reader, boolean rejectDTDs) {
        return get().doCreate(systemId,reader,rejectDTDs);
    }

    /**
     * Should be invoked when the code finished using an {@link XMLStreamReader}.
     *
     * <p>
     * If the recycled instance implements {@link RecycleAware},
     * {@link RecycleAware#onRecycled()} will be invoked to let the instance
     * know that it's being recycled.
     *
     * <p>
     * It is not a hard requirement to call this method on every {@link XMLStreamReader}
     * instance. Not doing so just reduces the performance by throwing away
     * possibly reusable instances. So the caller should always consider the effort
     * it takes to recycle vs the possible performance gain by doing so.
     *
     * <p>
     * This method may be invoked by multiple threads concurrently.
     *
     * @param r
     *      The {@link XMLStreamReader} instance that the caller finished using.
     *      This could be any {@link XMLStreamReader} implementation, not just
     *      the ones that were created from this factory. So the implementation
     *      of this class needs to be aware of that.
     */
    public static void recycle(XMLStreamReader r) {
        get().doRecycle(r);
        if (r instanceof RecycleAware) {
            ((RecycleAware)r).onRecycled();
        }
    }

    // implementations

    public abstract XMLStreamReader doCreate(String systemId, InputStream in, boolean rejectDTDs);

    private XMLStreamReader doCreate(String systemId, InputStream in, @NotNull String encoding, boolean rejectDTDs) {
        Reader reader;
        try {
            reader = new InputStreamReader(in, encoding);
        } catch(UnsupportedEncodingException ue) {
            throw new XMLReaderException("stax.cantCreate", ue);
        }
        return doCreate(systemId, reader, rejectDTDs);
    }

    public abstract XMLStreamReader doCreate(String systemId, Reader reader, boolean rejectDTDs);

    public abstract void doRecycle(XMLStreamReader r);

    /**
     * Interface that can be implemented by {@link XMLStreamReader} to
     * be notified when it's recycled.
     *
     * <p>
     * This provides a filtering {@link XMLStreamReader} an opportunity to
     * recycle its inner {@link XMLStreamReader}.
     */
    public interface RecycleAware {
        void onRecycled();
    }

    /**
     * {@link XMLStreamReaderFactory} implementation for SJSXP/JAXP RI.
     */
    private static final class Zephyr extends XMLStreamReaderFactory {
        private final XMLInputFactory xif;

        private final ThreadLocal<XMLStreamReader> pool = new ThreadLocal<XMLStreamReader>();

        /**
         * Sun StAX impl <code>XMLReaderImpl.setInputSource()</code> method via reflection.
         */
        private final Method setInputSourceMethod;

        /**
         * Sun StAX impl <code>XMLReaderImpl.reset()</code> method via reflection.
         */
        private final Method resetMethod;

        /**
         * The Sun StAX impl's {@link XMLStreamReader} implementation clas.
         */
        private final Class zephyrClass;

        /**
         * Creates {@link Zephyr} instance if the given {@link XMLInputFactory} is the one
         * from Zephyr.
         */
        public static @Nullable
        XMLStreamReaderFactory newInstance(XMLInputFactory xif) {
            // check if this is from Zephyr
            try {
                Class<?> clazz = xif.createXMLStreamReader(new StringReader("<foo/>")).getClass();
                // JDK has different XMLStreamReader impl class. Even if we check for that,
                // it doesn't have setInputSource(InputSource). Let it use Default
                if(!(clazz.getName().startsWith("com.sun.xml.internal.stream.")) )
                    return null;    // nope
                return new Zephyr(xif,clazz);
            } catch (NoSuchMethodException e) {
                return null;    // this factory is not for zephyr
            } catch (XMLStreamException e) {
                return null;    // impossible to fail to parse <foo/>, but anyway
            }
        }

        public Zephyr(XMLInputFactory xif, Class clazz) throws NoSuchMethodException {
            zephyrClass = clazz;
            setInputSourceMethod = clazz.getMethod("setInputSource", InputSource.class);
            resetMethod = clazz.getMethod("reset");

            try {
                // Turn OFF internal factory caching in Zephyr.
                // Santiago told me that this makes it thread-safe.
                xif.setProperty("reuse-instance", false);
            } catch (IllegalArgumentException e) {
                // falls through
            }
            this.xif = xif;
        }

        /**
         * Fetchs an instance from the pool if available, otherwise null.
         */
        private @Nullable XMLStreamReader fetch() {
            XMLStreamReader sr = pool.get();
            if(sr==null)    return null;
            pool.set(null);
            return sr;
        }

        @Override
        public void doRecycle(XMLStreamReader r) {
            if(zephyrClass.isInstance(r))
                pool.set(r);
        }

        @Override
        public XMLStreamReader doCreate(String systemId, InputStream in, boolean rejectDTDs) {
            try {
                XMLStreamReader xsr = fetch();
                if(xsr==null)
                    return xif.createXMLStreamReader(systemId,in);

                // try re-using this instance.
                InputSource is = new InputSource(systemId);
                is.setByteStream(in);
                reuse(xsr,is);
                return xsr;
            } catch (IllegalAccessException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            } catch (InvocationTargetException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            } catch (XMLStreamException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            }
        }

        @Override
        public XMLStreamReader doCreate(String systemId, Reader in, boolean rejectDTDs) {
            try {
                XMLStreamReader xsr = fetch();
                if(xsr==null)
                    return xif.createXMLStreamReader(systemId,in);

                // try re-using this instance.
                InputSource is = new InputSource(systemId);
                is.setCharacterStream(in);
                reuse(xsr,is);
                return xsr;
            } catch (IllegalAccessException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause == null) {
                    cause = e;
                }
                throw new XMLReaderException("stax.cantCreate", cause);
            } catch (XMLStreamException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            }
        }

        private void reuse(XMLStreamReader xsr, InputSource in) throws IllegalAccessException, InvocationTargetException {
            resetMethod.invoke(xsr);
            setInputSourceMethod.invoke(xsr,in);
        }
    }

    /**
     * Default {@link XMLStreamReaderFactory} implementation
     * that can work with any {@link XMLInputFactory}.
     *
     * <p>
     * {@link XMLInputFactory} is not required to be thread-safe, but
     * if the create method on this implementation is synchronized,
     * it may run into (see <a href="https://jax-ws.dev.java.net/issues/show_bug.cgi?id=555">
     * race condition</a>). Hence, using a XMLInputFactory per thread.
     */
    public static final class Default extends XMLStreamReaderFactory {

        private final ThreadLocal<XMLInputFactory> xif = new ThreadLocal<XMLInputFactory>() {
            @Override
            public XMLInputFactory initialValue() {
                return getXMLInputFactory();
            }
        };

        @Override
        public XMLStreamReader doCreate(String systemId, InputStream in, boolean rejectDTDs) {
            try {
                return xif.get().createXMLStreamReader(systemId,in);
            } catch (XMLStreamException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            }
        }

        @Override
        public XMLStreamReader doCreate(String systemId, Reader in, boolean rejectDTDs) {
            try {
                return xif.get().createXMLStreamReader(systemId,in);
            } catch (XMLStreamException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            }
        }

        @Override
        public void doRecycle(XMLStreamReader r) {
            // there's no way to recycle with the default StAX API.
        }

    }

    /**
     * Similar to {@link Default} but doesn't do any synchronization.
     *
     * <p>
     * This is useful when you know your {@link XMLInputFactory} is thread-safe by itself.
     */
    public static class NoLock extends XMLStreamReaderFactory {
        private final XMLInputFactory xif;

        public NoLock(XMLInputFactory xif) {
            this.xif = xif;
        }

        @Override
        public XMLStreamReader doCreate(String systemId, InputStream in, boolean rejectDTDs) {
            try {
                return xif.createXMLStreamReader(systemId,in);
            } catch (XMLStreamException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            }
        }

        @Override
        public XMLStreamReader doCreate(String systemId, Reader in, boolean rejectDTDs) {
            try {
                return xif.createXMLStreamReader(systemId,in);
            } catch (XMLStreamException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            }
        }

        @Override
        public void doRecycle(XMLStreamReader r) {
            // there's no way to recycle with the default StAX API.
        }
    }

    /**
     * Handles Woodstox's XIF, but sets properties to do the string interning, sets various limits, ...
     * Woodstox {@link XMLInputFactory} is thread safe.
     */
    public static final class Woodstox extends NoLock {

        public final static String PROPERTY_MAX_ATTRIBUTES_PER_ELEMENT = "xml.ws.maximum.AttributesPerElement";
        public final static String PROPERTY_MAX_ATTRIBUTE_SIZE = "xml.ws.maximum.AttributeSize";
        public final static String PROPERTY_MAX_CHILDREN_PER_ELEMENT = "xml.ws.maximum.ChildrenPerElement";
        public final static String PROPERTY_MAX_ELEMENT_COUNT = "xml.ws.maximum.ElementCount";
        public final static String PROPERTY_MAX_ELEMENT_DEPTH = "xml.ws.maximum.ElementDepth";
        public final static String PROPERTY_MAX_CHARACTERS = "xml.ws.maximum.Characters";

        private static final int DEFAULT_MAX_ATTRIBUTES_PER_ELEMENT = 500;
        private static final int DEFAULT_MAX_ATTRIBUTE_SIZE = 65536 * 8;
        private static final int DEFAULT_MAX_CHILDREN_PER_ELEMENT = Integer.MAX_VALUE;
        private static final int DEFAULT_MAX_ELEMENT_DEPTH = 500;
        private static final long DEFAULT_MAX_ELEMENT_COUNT = Integer.MAX_VALUE;
        private static final long DEFAULT_MAX_CHARACTERS = Long.MAX_VALUE;

        /* Woodstox default setting:
         int mMaxAttributesPerElement = 1000;
         int mMaxAttributeSize = 65536 * 8;
         int mMaxChildrenPerElement = Integer.MAX_VALUE;
         int mMaxElementDepth = 1000;
         long mMaxElementCount = Long.MAX_VALUE;
         long mMaxCharacters = Long.MAX_VALUE;
         */

        private int maxAttributesPerElement = DEFAULT_MAX_ATTRIBUTES_PER_ELEMENT;
        private int maxAttributeSize = DEFAULT_MAX_ATTRIBUTE_SIZE;
        private int maxChildrenPerElement = DEFAULT_MAX_CHILDREN_PER_ELEMENT;
        private int maxElementDepth = DEFAULT_MAX_ELEMENT_DEPTH;
        private long maxElementCount = DEFAULT_MAX_ELEMENT_COUNT;
        private long maxCharacters = DEFAULT_MAX_CHARACTERS;

        // Note: this is a copy from com.ctc.wstx.api.WstxInputProperties, to be removed in the future
        private static final java.lang.String P_MAX_ATTRIBUTES_PER_ELEMENT = "com.ctc.wstx.maxAttributesPerElement";
        private static final java.lang.String P_MAX_ATTRIBUTE_SIZE = "com.ctc.wstx.maxAttributeSize";
        private static final java.lang.String P_MAX_CHILDREN_PER_ELEMENT = "com.ctc.wstx.maxChildrenPerElement";
        private static final java.lang.String P_MAX_ELEMENT_COUNT = "com.ctc.wstx.maxElementCount";
        private static final java.lang.String P_MAX_ELEMENT_DEPTH = "com.ctc.wstx.maxElementDepth";
        private static final java.lang.String P_MAX_CHARACTERS = "com.ctc.wstx.maxCharacters";
        private static final java.lang.String P_INTERN_NSURIS = "org.codehaus.stax2.internNsUris";

        public Woodstox(XMLInputFactory xif) {
            super(xif);

            if (xif.isPropertySupported(P_INTERN_NSURIS)) {
                xif.setProperty(P_INTERN_NSURIS, true);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, P_INTERN_NSURIS + " is {0}", true);
                }
            }

            if (xif.isPropertySupported(P_MAX_ATTRIBUTES_PER_ELEMENT)) {
                maxAttributesPerElement = Integer.valueOf(buildIntegerValue(
                    PROPERTY_MAX_ATTRIBUTES_PER_ELEMENT, DEFAULT_MAX_ATTRIBUTES_PER_ELEMENT)
                );
                xif.setProperty(P_MAX_ATTRIBUTES_PER_ELEMENT, maxAttributesPerElement);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, P_MAX_ATTRIBUTES_PER_ELEMENT + " is {0}", maxAttributesPerElement);
                }
            }

            if (xif.isPropertySupported(P_MAX_ATTRIBUTE_SIZE)) {
                maxAttributeSize = Integer.valueOf(buildIntegerValue(
                    PROPERTY_MAX_ATTRIBUTE_SIZE, DEFAULT_MAX_ATTRIBUTE_SIZE)
                );
                xif.setProperty(P_MAX_ATTRIBUTE_SIZE, maxAttributeSize);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, P_MAX_ATTRIBUTE_SIZE + " is {0}", maxAttributeSize);
                }
            }

            if (xif.isPropertySupported(P_MAX_CHILDREN_PER_ELEMENT)) {
                maxChildrenPerElement = Integer.valueOf(buildIntegerValue(
                    PROPERTY_MAX_CHILDREN_PER_ELEMENT, DEFAULT_MAX_CHILDREN_PER_ELEMENT)
                );
                xif.setProperty(P_MAX_CHILDREN_PER_ELEMENT, maxChildrenPerElement);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, P_MAX_CHILDREN_PER_ELEMENT + " is {0}", maxChildrenPerElement);
                }
            }

            if (xif.isPropertySupported(P_MAX_ELEMENT_DEPTH)) {
                maxElementDepth = Integer.valueOf(buildIntegerValue(
                    PROPERTY_MAX_ELEMENT_DEPTH, DEFAULT_MAX_ELEMENT_DEPTH)
                );
                xif.setProperty(P_MAX_ELEMENT_DEPTH, maxElementDepth);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, P_MAX_ELEMENT_DEPTH + " is {0}", maxElementDepth);
                }
            }

            if (xif.isPropertySupported(P_MAX_ELEMENT_COUNT)) {
                maxElementCount = Long.valueOf(buildLongValue(
                    PROPERTY_MAX_ELEMENT_COUNT, DEFAULT_MAX_ELEMENT_COUNT)
                );
                xif.setProperty(P_MAX_ELEMENT_COUNT, maxElementCount);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, P_MAX_ELEMENT_COUNT + " is {0}", maxElementCount);
                }
            }

            if (xif.isPropertySupported(P_MAX_CHARACTERS)) {
                maxCharacters = Long.valueOf(buildLongValue(
                    PROPERTY_MAX_CHARACTERS, DEFAULT_MAX_CHARACTERS)
                );
                xif.setProperty(P_MAX_CHARACTERS, maxCharacters);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, P_MAX_CHARACTERS + " is {0}", maxCharacters);
                }
            }
        }

        @Override
        public XMLStreamReader doCreate(String systemId, InputStream in, boolean rejectDTDs) {
            return super.doCreate(systemId, in, rejectDTDs);
        }

        @Override
        public XMLStreamReader doCreate(String systemId, Reader in, boolean rejectDTDs) {
            return super.doCreate(systemId, in, rejectDTDs);
        }
    }

    private static int buildIntegerValue(String propertyName, int defaultValue) {
        String propVal = System.getProperty(propertyName);
        if (propVal != null && propVal.length() > 0) {
            try {
                Integer value = Integer.parseInt(propVal);
                if (value > 0) {
                    // return with the value in System property
                    return value;
                }
            } catch (NumberFormatException nfe) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, StreamingMessages.INVALID_PROPERTY_VALUE_INTEGER(propertyName, propVal, Integer.toString(defaultValue)), nfe);
                }
            }
        }
        // return with the default value
        return defaultValue;
    }

    private static long buildLongValue(String propertyName, long defaultValue) {
        String propVal = System.getProperty(propertyName);
        if (propVal != null && propVal.length() > 0) {
            try {
                long value = Long.parseLong(propVal);
                if (value > 0L) {
                    // return with the value in System property
                    return value;
                }
            } catch (NumberFormatException nfe) {
                // defult will be returned
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING, StreamingMessages.INVALID_PROPERTY_VALUE_LONG(propertyName, propVal, Long.toString(defaultValue)), nfe);
                }
            }
        }
        // return with the default value
        return defaultValue;
    }

    private static Boolean getProperty(final String prop) {
        return AccessController.doPrivileged(
            new java.security.PrivilegedAction<Boolean>() {
                @Override
                public Boolean run() {
                    String value = System.getProperty(prop);
                    return value != null ? Boolean.valueOf(value) : Boolean.FALSE;
                }
            }
        );
    }

}
