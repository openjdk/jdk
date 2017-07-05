/*
 * Copyright 2000-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
package java.beans;

import com.sun.beans.ObjectHandler;

import java.io.InputStream;
import java.io.IOException;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import org.xml.sax.SAXException;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;

/**
 * The <code>XMLDecoder</code> class is used to read XML documents
 * created using the <code>XMLEncoder</code> and is used just like
 * the <code>ObjectInputStream</code>. For example, one can use
 * the following fragment to read the first object defined
 * in an XML document written by the <code>XMLEncoder</code>
 * class:
 * <pre>
 *       XMLDecoder d = new XMLDecoder(
 *                          new BufferedInputStream(
 *                              new FileInputStream("Test.xml")));
 *       Object result = d.readObject();
 *       d.close();
 * </pre>
 *
 *<p>
 * For more information you might also want to check out
 * <a
 href="http://java.sun.com/products/jfc/tsc/articles/persistence3">Long Term Persistence of JavaBeans Components: XML Schema</a>,
 * an article in <em>The Swing Connection.</em>
 * @see XMLEncoder
 * @see java.io.ObjectInputStream
 *
 * @since 1.4
 *
 * @author Philip Milne
 */
public class XMLDecoder {
    private InputStream in;
    private Object owner;
    private ExceptionListener exceptionListener;
    private ObjectHandler handler;
    private Reference clref;

    /**
     * Creates a new input stream for reading archives
     * created by the <code>XMLEncoder</code> class.
     *
     * @param in The underlying stream.
     *
     * @see XMLEncoder#XMLEncoder(java.io.OutputStream)
     */
    public XMLDecoder(InputStream in) {
        this(in, null);
    }

    /**
     * Creates a new input stream for reading archives
     * created by the <code>XMLEncoder</code> class.
     *
     * @param in The underlying stream.
     * @param owner The owner of this stream.
     *
     */
    public XMLDecoder(InputStream in, Object owner) {
        this(in, owner, null);
    }

    /**
     * Creates a new input stream for reading archives
     * created by the <code>XMLEncoder</code> class.
     *
     * @param in the underlying stream.
     * @param owner the owner of this stream.
     * @param exceptionListener the exception handler for the stream;
     *        if <code>null</code> the default exception listener will be used.
     */
    public XMLDecoder(InputStream in, Object owner, ExceptionListener exceptionListener) {
        this(in, owner, exceptionListener, null);
    }

    /**
     * Creates a new input stream for reading archives
     * created by the <code>XMLEncoder</code> class.
     *
     * @param in the underlying stream.  <code>null</code> may be passed without
     *        error, though the resulting XMLDecoder will be useless
     * @param owner the owner of this stream.  <code>null</code> is a legal
     *        value
     * @param exceptionListener the exception handler for the stream, or
     *        <code>null</code> to use the default
     * @param cl the class loader used for instantiating objects.
     *        <code>null</code> indicates that the default class loader should
     *        be used
     * @since 1.5
     */
    public XMLDecoder(InputStream in, Object owner,
                      ExceptionListener exceptionListener, ClassLoader cl) {
        this.in = in;
        setOwner(owner);
        setExceptionListener(exceptionListener);
        setClassLoader(cl);
    }


    /**
     * Set the class loader used to instantiate objects for this stream.
     *
     * @param cl a classloader to use; if null then the default class loader
     *           will be used
     */
    private void setClassLoader(ClassLoader cl) {
        if (cl != null) {
            this.clref = new WeakReference(cl);
        }
    }

    /**
     * Return the class loader used to instantiate objects. If the class loader
     * has not been explicitly set then null is returned.
     *
     * @return the class loader used to instantiate objects
     */
    private ClassLoader getClassLoader() {
        if (clref != null) {
            return (ClassLoader)clref.get();
        }
        return null;
    }

    /**
     * This method closes the input stream associated
     * with this stream.
     */
    public void close() {
        if (in != null) {
            getHandler();
            try {
                in.close();
            }
            catch (IOException e) {
                getExceptionListener().exceptionThrown(e);
            }
        }
    }

    /**
     * Sets the exception handler for this stream to <code>exceptionListener</code>.
     * The exception handler is notified when this stream catches recoverable
     * exceptions.
     *
     * @param exceptionListener The exception handler for this stream;
     * if <code>null</code> the default exception listener will be used.
     *
     * @see #getExceptionListener
     */
    public void setExceptionListener(ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
    }

    /**
     * Gets the exception handler for this stream.
     *
     * @return The exception handler for this stream.
     *     Will return the default exception listener if this has not explicitly been set.
     *
     * @see #setExceptionListener
     */
    public ExceptionListener getExceptionListener() {
        return (exceptionListener != null) ? exceptionListener :
            Statement.defaultExceptionListener;
    }

    /**
     * Reads the next object from the underlying input stream.
     *
     * @return the next object read
     *
     * @throws ArrayIndexOutOfBoundsException if the stream contains no objects
     *         (or no more objects)
     *
     * @see XMLEncoder#writeObject
     */
    public Object readObject() {
        if (in == null) {
            return null;
        }
        return getHandler().dequeueResult();
    }

    /**
     * Sets the owner of this decoder to <code>owner</code>.
     *
     * @param owner The owner of this decoder.
     *
     * @see #getOwner
     */
    public void setOwner(Object owner) {
        this.owner = owner;
    }

    /**
     * Gets the owner of this decoder.
     *
     * @return The owner of this decoder.
     *
     * @see #setOwner
     */
    public Object getOwner() {
        return owner;
    }

    /**
     * Returns the object handler for input stream.
     * The object handler is created if necessary.
     *
     * @return  the object handler
     */
    private ObjectHandler getHandler() {
        if ( handler == null ) {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            try {
                SAXParser parser = factory.newSAXParser();
                handler = new ObjectHandler( this, getClassLoader() );
                parser.parse( in, handler );
            }
            catch ( ParserConfigurationException e ) {
                getExceptionListener().exceptionThrown( e );
            }
            catch ( SAXException se ) {
                Exception e = se.getException();
                if ( e == null ) {
                    e = se;
                }
                getExceptionListener().exceptionThrown( e );
            }
            catch ( IOException ioe ) {
                getExceptionListener().exceptionThrown( ioe );
            }
        }
        return handler;
    }
}
