/*
 * Copyright (c) 2006, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.beans.Introspector;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Class that defines convenience methods for common, simple use of JAXB.
 *
 * <p>
 * Methods defined in this class are convenience methods that combine several basic operations
 * in the {@link JAXBContext}, {@link Unmarshaller}, and {@link Marshaller}.
 *
 * They are designed
 * to be the prefered methods for developers new to JAXB. They have
 * the following characterstics:
 *
 * <ol>
 *  <li>Generally speaking, the performance is not necessarily optimal.
 *      It is expected that people who need to write performance
 *      critical code will use the rest of the JAXB API directly.
 *  <li>Errors that happen during the processing is wrapped into
 *      {@link DataBindingException} (which will have {@link JAXBException}
 *      as its {@link Throwable#getCause() cause}. It is expected that
 *      people who prefer the checked exception would use
 *      the rest of the JAXB API directly.
 * </ol>
 *
 * <p>
 * In addition, the <tt>unmarshal</tt> methods have the following characteristic:
 *
 * <ol>
 *  <li>Schema validation is not performed on the input XML.
 *      The processing will try to continue even if there
 *      are errors in the XML, as much as possible. Only as
 *      the last resort, this method fails with {@link DataBindingException}.
 * </ol>
 *
 * <p>
 * Similarly, the <tt>marshal</tt> methods have the following characteristic:
 * <ol>
 *  <li>The processing will try to continue even if the Java object tree
 *      does not meet the validity requirement. Only as
 *      the last resort, this method fails with {@link DataBindingException}.
 * </ol>
 *
 *
 * <p>
 * All the methods on this class require non-null arguments to all parameters.
 * The <tt>unmarshal</tt> methods either fail with an exception or return
 * a non-null value.
 *
 * @author Kohsuke Kawaguchi
 * @since 2.1
 */
public final class JAXB {
    /**
     * No instanciation is allowed.
     */
    private JAXB() {}

    /**
     * To improve the performance, we'll cache the last {@link JAXBContext} used.
     */
    private static final class Cache {
        final Class type;
        final JAXBContext context;

        public Cache(Class type) throws JAXBException {
            this.type = type;
            this.context = JAXBContext.newInstance(type);
        }
    }

    /**
     * Cache. We don't want to prevent the {@link Cache#type} from GC-ed,
     * hence {@link WeakReference}.
     */
    private static volatile WeakReference<Cache> cache;

    /**
     * Obtains the {@link JAXBContext} from the given type,
     * by using the cache if possible.
     *
     * <p>
     * We don't use locks to control access to {@link #cache}, but this code
     * should be thread-safe thanks to the immutable {@link Cache} and {@code volatile}.
     */
    private static <T> JAXBContext getContext(Class<T> type) throws JAXBException {
        WeakReference<Cache> c = cache;
        if(c!=null) {
            Cache d = c.get();
            if(d!=null && d.type==type)
                return d.context;
        }

        // overwrite the cache
        Cache d = new Cache(type);
        cache = new WeakReference<Cache>(d);

        return d.context;
    }

    /**
     * Reads in a Java object tree from the given XML input.
     *
     * @param xml
     *      Reads the entire file as XML.
     */
    public static <T> T unmarshal( File xml, Class<T> type ) {
        try {
            JAXBElement<T> item = getContext(type).createUnmarshaller().unmarshal(new StreamSource(xml), type);
            return item.getValue();
        } catch (JAXBException e) {
            throw new DataBindingException(e);
        }
    }

    /**
     * Reads in a Java object tree from the given XML input.
     *
     * @param xml
     *      The resource pointed by the URL is read in its entirety.
     */
    public static <T> T unmarshal( URL xml, Class<T> type ) {
        try {
            JAXBElement<T> item = getContext(type).createUnmarshaller().unmarshal(toSource(xml), type);
            return item.getValue();
        } catch (JAXBException e) {
            throw new DataBindingException(e);
        } catch (IOException e) {
            throw new DataBindingException(e);
        }
    }

    /**
     * Reads in a Java object tree from the given XML input.
     *
     * @param xml
     *      The URI is {@link URI#toURL() turned into URL} and then
     *      follows the handling of <tt>URL</tt>.
     */
    public static <T> T unmarshal( URI xml, Class<T> type ) {
        try {
            JAXBElement<T> item = getContext(type).createUnmarshaller().unmarshal(toSource(xml), type);
            return item.getValue();
        } catch (JAXBException e) {
            throw new DataBindingException(e);
        } catch (IOException e) {
            throw new DataBindingException(e);
        }
    }

    /**
     * Reads in a Java object tree from the given XML input.
     *
     * @param xml
     *      The string is first interpreted as an absolute <tt>URI</tt>.
     *      If it's not {@link URI#isAbsolute() a valid absolute URI},
     *      then it's interpreted as a <tt>File</tt>
     */
    public static <T> T unmarshal( String xml, Class<T> type ) {
        try {
            JAXBElement<T> item = getContext(type).createUnmarshaller().unmarshal(toSource(xml), type);
            return item.getValue();
        } catch (JAXBException e) {
            throw new DataBindingException(e);
        } catch (IOException e) {
            throw new DataBindingException(e);
        }
    }

    /**
     * Reads in a Java object tree from the given XML input.
     *
     * @param xml
     *      The entire stream is read as an XML infoset.
     *      Upon a successful completion, the stream will be closed by this method.
     */
    public static <T> T unmarshal( InputStream xml, Class<T> type ) {
        try {
            JAXBElement<T> item = getContext(type).createUnmarshaller().unmarshal(toSource(xml), type);
            return item.getValue();
        } catch (JAXBException e) {
            throw new DataBindingException(e);
        } catch (IOException e) {
            throw new DataBindingException(e);
        }
    }

    /**
     * Reads in a Java object tree from the given XML input.
     *
     * @param xml
     *      The character stream is read as an XML infoset.
     *      The encoding declaration in the XML will be ignored.
     *      Upon a successful completion, the stream will be closed by this method.
     */
    public static <T> T unmarshal( Reader xml, Class<T> type ) {
        try {
            JAXBElement<T> item = getContext(type).createUnmarshaller().unmarshal(toSource(xml), type);
            return item.getValue();
        } catch (JAXBException e) {
            throw new DataBindingException(e);
        } catch (IOException e) {
            throw new DataBindingException(e);
        }
    }

    /**
     * Reads in a Java object tree from the given XML input.
     *
     * @param xml
     *      The XML infoset that the {@link Source} represents is read.
     */
    public static <T> T unmarshal( Source xml, Class<T> type ) {
        try {
            JAXBElement<T> item = getContext(type).createUnmarshaller().unmarshal(toSource(xml), type);
            return item.getValue();
        } catch (JAXBException e) {
            throw new DataBindingException(e);
        } catch (IOException e) {
            throw new DataBindingException(e);
        }
    }



    /**
     * Creates {@link Source} from various XML representation.
     * See {@link #unmarshal} for the conversion rules.
     */
    private static Source toSource(Object xml) throws IOException {
        if(xml==null)
            throw new IllegalArgumentException("no XML is given");

        if (xml instanceof String) {
            try {
                xml=new URI((String)xml);
            } catch (URISyntaxException e) {
                xml=new File((String)xml);
            }
        }
        if (xml instanceof File) {
            File file = (File) xml;
            return new StreamSource(file);
        }
        if (xml instanceof URI) {
            URI uri = (URI) xml;
            xml=uri.toURL();
        }
        if (xml instanceof URL) {
            URL url = (URL) xml;
            return new StreamSource(url.toExternalForm());
        }
        if (xml instanceof InputStream) {
            InputStream in = (InputStream) xml;
            return new StreamSource(in);
        }
        if (xml instanceof Reader) {
            Reader r = (Reader) xml;
            return new StreamSource(r);
        }
        if (xml instanceof Source) {
            return (Source) xml;
        }
        throw new IllegalArgumentException("I don't understand how to handle "+xml.getClass());
    }

    /**
     * Writes a Java object tree to XML and store it to the specified location.
     *
     * @param jaxbObject
     *      The Java object to be marshalled into XML. If this object is
     *      a {@link JAXBElement}, it will provide the root tag name and
     *      the body. If this object has {@link XmlRootElement}
     *      on its class definition, that will be used as the root tag name
     *      and the given object will provide the body. Otherwise,
     *      the root tag name is {@link Introspector#decapitalize(String) infered} from
     *      {@link Class#getSimpleName() the short class name}.
     *      This parameter must not be null.
     *
     * @param xml
     *      XML will be written to this file. If it already exists,
     *      it will be overwritten.
     *
     * @throws DataBindingException
     *      If the operation fails, such as due to I/O error, unbindable classes.
     */
    public static void marshal( Object jaxbObject, File xml ) {
        _marshal(jaxbObject,xml);
    }

    /**
     * Writes a Java object tree to XML and store it to the specified location.
     *
     * @param jaxbObject
     *      The Java object to be marshalled into XML. If this object is
     *      a {@link JAXBElement}, it will provide the root tag name and
     *      the body. If this object has {@link XmlRootElement}
     *      on its class definition, that will be used as the root tag name
     *      and the given object will provide the body. Otherwise,
     *      the root tag name is {@link Introspector#decapitalize(String) infered} from
     *      {@link Class#getSimpleName() the short class name}.
     *      This parameter must not be null.
     *
     * @param xml
     *      The XML will be {@link URLConnection#getOutputStream() sent} to the
     *      resource pointed by this URL. Note that not all <tt>URL</tt>s support
     *      such operation, and exact semantics depends on the <tt>URL</tt>
     *      implementations. In case of {@link HttpURLConnection HTTP URLs},
     *      this will perform HTTP POST.
     *
     * @throws DataBindingException
     *      If the operation fails, such as due to I/O error, unbindable classes.
     */
    public static void marshal( Object jaxbObject, URL xml ) {
        _marshal(jaxbObject,xml);
    }

    /**
     * Writes a Java object tree to XML and store it to the specified location.
     *
     * @param jaxbObject
     *      The Java object to be marshalled into XML. If this object is
     *      a {@link JAXBElement}, it will provide the root tag name and
     *      the body. If this object has {@link XmlRootElement}
     *      on its class definition, that will be used as the root tag name
     *      and the given object will provide the body. Otherwise,
     *      the root tag name is {@link Introspector#decapitalize(String) infered} from
     *      {@link Class#getSimpleName() the short class name}.
     *      This parameter must not be null.
     *
     * @param xml
     *      The URI is {@link URI#toURL() turned into URL} and then
     *      follows the handling of <tt>URL</tt>. See above.
     *
     * @throws DataBindingException
     *      If the operation fails, such as due to I/O error, unbindable classes.
     */
    public static void marshal( Object jaxbObject, URI xml ) {
        _marshal(jaxbObject,xml);
    }

    /**
     * Writes a Java object tree to XML and store it to the specified location.
     *
     * @param jaxbObject
     *      The Java object to be marshalled into XML. If this object is
     *      a {@link JAXBElement}, it will provide the root tag name and
     *      the body. If this object has {@link XmlRootElement}
     *      on its class definition, that will be used as the root tag name
     *      and the given object will provide the body. Otherwise,
     *      the root tag name is {@link Introspector#decapitalize(String) infered} from
     *      {@link Class#getSimpleName() the short class name}.
     *      This parameter must not be null.
     *
     * @param xml
     *      The string is first interpreted as an absolute <tt>URI</tt>.
     *      If it's not {@link URI#isAbsolute() a valid absolute URI},
     *      then it's interpreted as a <tt>File</tt>
     *
     * @throws DataBindingException
     *      If the operation fails, such as due to I/O error, unbindable classes.
     */
    public static void marshal( Object jaxbObject, String xml ) {
        _marshal(jaxbObject,xml);
    }

    /**
     * Writes a Java object tree to XML and store it to the specified location.
     *
     * @param jaxbObject
     *      The Java object to be marshalled into XML. If this object is
     *      a {@link JAXBElement}, it will provide the root tag name and
     *      the body. If this object has {@link XmlRootElement}
     *      on its class definition, that will be used as the root tag name
     *      and the given object will provide the body. Otherwise,
     *      the root tag name is {@link Introspector#decapitalize(String) infered} from
     *      {@link Class#getSimpleName() the short class name}.
     *      This parameter must not be null.
     *
     * @param xml
     *      The XML will be sent to the given {@link OutputStream}.
     *      Upon a successful completion, the stream will be closed by this method.
     *
     * @throws DataBindingException
     *      If the operation fails, such as due to I/O error, unbindable classes.
     */
    public static void marshal( Object jaxbObject, OutputStream xml ) {
        _marshal(jaxbObject,xml);
    }

    /**
     * Writes a Java object tree to XML and store it to the specified location.
     *
     * @param jaxbObject
     *      The Java object to be marshalled into XML. If this object is
     *      a {@link JAXBElement}, it will provide the root tag name and
     *      the body. If this object has {@link XmlRootElement}
     *      on its class definition, that will be used as the root tag name
     *      and the given object will provide the body. Otherwise,
     *      the root tag name is {@link Introspector#decapitalize(String) infered} from
     *      {@link Class#getSimpleName() the short class name}.
     *      This parameter must not be null.
     *
     * @param xml
     *      The XML will be sent as a character stream to the given {@link Writer}.
     *      Upon a successful completion, the stream will be closed by this method.
     *
     * @throws DataBindingException
     *      If the operation fails, such as due to I/O error, unbindable classes.
     */
    public static void marshal( Object jaxbObject, Writer xml ) {
        _marshal(jaxbObject,xml);
    }

    /**
     * Writes a Java object tree to XML and store it to the specified location.
     *
     * @param jaxbObject
     *      The Java object to be marshalled into XML. If this object is
     *      a {@link JAXBElement}, it will provide the root tag name and
     *      the body. If this object has {@link XmlRootElement}
     *      on its class definition, that will be used as the root tag name
     *      and the given object will provide the body. Otherwise,
     *      the root tag name is {@link Introspector#decapitalize(String) infered} from
     *      {@link Class#getSimpleName() the short class name}.
     *      This parameter must not be null.
     *
     * @param xml
     *      The XML will be sent to the {@link Result} object.
     *
     * @throws DataBindingException
     *      If the operation fails, such as due to I/O error, unbindable classes.
     */
    public static void marshal( Object jaxbObject, Result xml ) {
        _marshal(jaxbObject,xml);
    }

    /**
     * Writes a Java object tree to XML and store it to the specified location.
     *
     * <p>
     * This method is a convenience method that combines several basic operations
     * in the {@link JAXBContext} and {@link Marshaller}. This method is designed
     * to be the prefered method for developers new to JAXB. This method
     * has the following characterstics:
     *
     * <ol>
     *  <li>Generally speaking, the performance is not necessarily optimal.
     *      It is expected that those people who need to write performance
     *      critical code will use the rest of the JAXB API directly.
     *  <li>Errors that happen during the processing is wrapped into
     *      {@link DataBindingException} (which will have {@link JAXBException}
     *      as its {@link Throwable#getCause() cause}. It is expected that
     *      those people who prefer the checked exception would use
     *      the rest of the JAXB API directly.
     * </ol>
     *
     * @param jaxbObject
     *      The Java object to be marshalled into XML. If this object is
     *      a {@link JAXBElement}, it will provide the root tag name and
     *      the body. If this object has {@link XmlRootElement}
     *      on its class definition, that will be used as the root tag name
     *      and the given object will provide the body. Otherwise,
     *      the root tag name is {@link Introspector#decapitalize(String) infered} from
     *      {@link Class#getSimpleName() the short class name}.
     *      This parameter must not be null.
     *
     * @param xml
     *      Represents the receiver of XML. Objects of the following types are allowed.
     *
     *      <table><tr>
     *          <th>Type</th>
     *          <th>Operation</th>
     *      </tr><tr>
     *          <td>{@link File}</td>
     *          <td>XML will be written to this file. If it already exists,
     *              it will be overwritten.</td>
     *      </tr><tr>
     *          <td>{@link URL}</td>
     *          <td>The XML will be {@link URLConnection#getOutputStream() sent} to the
     *              resource pointed by this URL. Note that not all <tt>URL</tt>s support
     *              such operation, and exact semantics depends on the <tt>URL</tt>
     *              implementations. In case of {@link HttpURLConnection HTTP URLs},
     *              this will perform HTTP POST.</td>
     *      </tr><tr>
     *          <td>{@link URI}</td>
     *          <td>The URI is {@link URI#toURL() turned into URL} and then
     *              follows the handling of <tt>URL</tt>. See above.</td>
     *      </tr><tr>
     *          <td>{@link String}</td>
     *          <td>The string is first interpreted as an absolute <tt>URI</tt>.
     *              If it's not {@link URI#isAbsolute() a valid absolute URI},
     *              then it's interpreted as a <tt>File</tt></td>
     *      </tr><tr>
     *          <td>{@link OutputStream}</td>
     *          <td>The XML will be sent to the given {@link OutputStream}.
     *              Upon a successful completion, the stream will be closed by this method.</td>
     *      </tr><tr>
     *          <td>{@link Writer}</td>
     *          <td>The XML will be sent as a character stream to the given {@link Writer}.
     *              Upon a successful completion, the stream will be closed by this method.</td>
     *      </tr><tr>
     *          <td>{@link Result}</td>
     *          <td>The XML will be sent to the {@link Result} object.</td>
     *      </tr></table>
     *
     * @throws DataBindingException
     *      If the operation fails, such as due to I/O error, unbindable classes.
     */
    private static void _marshal( Object jaxbObject, Object xml ) {
        try {
            JAXBContext context;

            if(jaxbObject instanceof JAXBElement) {
                context = getContext(((JAXBElement<?>)jaxbObject).getDeclaredType());
            } else {
                Class<?> clazz = jaxbObject.getClass();
                XmlRootElement r = clazz.getAnnotation(XmlRootElement.class);
                context = getContext(clazz);
                if(r==null) {
                    // we need to infer the name
                    jaxbObject = new JAXBElement(new QName(inferName(clazz)),clazz,jaxbObject);
                }
            }

            Marshaller m = context.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT,true);
            m.marshal(jaxbObject, toResult(xml));
        } catch (JAXBException e) {
            throw new DataBindingException(e);
        } catch (IOException e) {
            throw new DataBindingException(e);
        }
    }

    private static String inferName(Class clazz) {
        return Introspector.decapitalize(clazz.getSimpleName());
    }

    /**
     * Creates {@link Result} from various XML representation.
     * See {@link #_marshal(Object,Object)} for the conversion rules.
     */
    private static Result toResult(Object xml) throws IOException {
        if(xml==null)
            throw new IllegalArgumentException("no XML is given");

        if (xml instanceof String) {
            try {
                xml=new URI((String)xml);
            } catch (URISyntaxException e) {
                xml=new File((String)xml);
            }
        }
        if (xml instanceof File) {
            File file = (File) xml;
            return new StreamResult(file);
        }
        if (xml instanceof URI) {
            URI uri = (URI) xml;
            xml=uri.toURL();
        }
        if (xml instanceof URL) {
            URL url = (URL) xml;
            URLConnection con = url.openConnection();
            con.setDoOutput(true);
            con.setDoInput(false);
            con.connect();
            return new StreamResult(con.getOutputStream());
        }
        if (xml instanceof OutputStream) {
            OutputStream os = (OutputStream) xml;
            return new StreamResult(os);
        }
        if (xml instanceof Writer) {
            Writer w = (Writer)xml;
            return new StreamResult(w);
        }
        if (xml instanceof Result) {
            return (Result) xml;
        }
        throw new IllegalArgumentException("I don't understand how to handle "+xml.getClass());
    }

}
