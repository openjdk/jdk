/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.api.streaming;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.streaming.XMLReaderException;
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
import java.util.logging.Logger;
import java.security.AccessController;

/**
 * Factory for {@link XMLStreamReader}.
 *
 * <p>
 * This wraps {@link XMLInputFactory} and allows us to reuse {@link XMLStreamReader} instances
 * when appropriate.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class XMLStreamReaderFactory {

    private static final Logger LOGGER = Logger.getLogger(XMLStreamReaderFactory.class.getName());

    /**
     * Singleton instance.
     */
    private static volatile @NotNull XMLStreamReaderFactory theInstance;

    static {
        XMLInputFactory xif = getXMLInputFactory();
        XMLStreamReaderFactory f=null;

        // this system property can be used to disable the pooling altogether,
        // in case someone hits an issue with pooling in the production system.
        if(!getProperty(XMLStreamReaderFactory.class.getName()+".noPool"))
            f = Zephyr.newInstance(xif);

        if(f==null) {
            // is this Woodstox?
            if(xif.getClass().getName().equals("com.ctc.wstx.stax.WstxInputFactory"))
                f = new Woodstox(xif);
        }

        if(f==null)
            f = new Default();

        theInstance = f;
        LOGGER.fine("XMLStreamReaderFactory instance is = "+theInstance);
    }

    private static XMLInputFactory getXMLInputFactory() {
        XMLInputFactory xif = null;
        if (getProperty(XMLStreamReaderFactory.class.getName()+".woodstox")) {
            try {
                xif = (XMLInputFactory)Class.forName("com.ctc.wstx.stax.WstxInputFactory").newInstance();
            } catch (Exception e) {
                // Ignore and fallback to default XMLInputFactory
            }
        }
        if (xif == null) {
            xif = XMLInputFactory.newInstance();
        }
        xif.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        xif.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        return xif;
    }


    /**
     * Overrides the singleton {@link XMLStreamReaderFactory} instance that
     * the JAX-WS RI uses.
     */
    public static void set(XMLStreamReaderFactory f) {
        if(f==null) throw new IllegalArgumentException();
        theInstance = f;
    }

    public static XMLStreamReaderFactory get() {
        return theInstance;
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
     * This method may be invked by multiple threads concurrently.
     *
     * @param r
     *      The {@link XMLStreamReader} instance that the caller finished using.
     *      This could be any {@link XMLStreamReader} implementation, not just
     *      the ones that were created from this factory. So the implementation
     *      of this class needs to be aware of that.
     */
    public static void recycle(XMLStreamReader r) {
        get().doRecycle(r);
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
    public static final class Zephyr extends XMLStreamReaderFactory {
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

        public void doRecycle(XMLStreamReader r) {
            if(zephyrClass.isInstance(r))
                pool.set(r);
            if(r instanceof RecycleAware)
                ((RecycleAware)r).onRecycled();
        }

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
     * race condition</a>). Hence, using a XMLInputFactory per theread.
     */
    public static final class Default extends XMLStreamReaderFactory {

        private final ThreadLocal<XMLInputFactory> xif = new ThreadLocal<XMLInputFactory>() {
            @Override
            public XMLInputFactory initialValue() {
                return getXMLInputFactory();
            }
        };

        public XMLStreamReader doCreate(String systemId, InputStream in, boolean rejectDTDs) {
            try {
                return xif.get().createXMLStreamReader(systemId,in);
            } catch (XMLStreamException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            }
        }

        public XMLStreamReader doCreate(String systemId, Reader in, boolean rejectDTDs) {
            try {
                return xif.get().createXMLStreamReader(systemId,in);
            } catch (XMLStreamException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            }
        }

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

        public XMLStreamReader doCreate(String systemId, InputStream in, boolean rejectDTDs) {
            try {
                return xif.createXMLStreamReader(systemId,in);
            } catch (XMLStreamException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            }
        }

        public XMLStreamReader doCreate(String systemId, Reader in, boolean rejectDTDs) {
            try {
                return xif.createXMLStreamReader(systemId,in);
            } catch (XMLStreamException e) {
                throw new XMLReaderException("stax.cantCreate",e);
            }
        }

        public void doRecycle(XMLStreamReader r) {
            // there's no way to recycle with the default StAX API.
        }
    }

    /**
     * Handles Woodstox's XIF but set properties to do the string interning.
     * Woodstox {@link XMLInputFactory} is thread safe.
     */
    public static final class Woodstox extends NoLock {
        public Woodstox(XMLInputFactory xif) {
            super(xif);
            xif.setProperty("org.codehaus.stax2.internNsUris",true);
        }

        public XMLStreamReader doCreate(String systemId, InputStream in, boolean rejectDTDs) {
            return super.doCreate(systemId, in, rejectDTDs);
        }

        public XMLStreamReader doCreate(String systemId, Reader in, boolean rejectDTDs) {
            return super.doCreate(systemId, in, rejectDTDs);
        }
    }

    private static Boolean getProperty(final String prop) {
        Boolean b = AccessController.doPrivileged(
            new java.security.PrivilegedAction<Boolean>() {
                public Boolean run() {
                    String value = System.getProperty(prop);
                    return value != null ? Boolean.valueOf(value) : Boolean.FALSE;
                }
            }
        );
        return Boolean.FALSE;
    }
}
