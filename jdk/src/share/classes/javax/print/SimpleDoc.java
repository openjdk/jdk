/*
 * Copyright (c) 2001, 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.print;

import java.io.ByteArrayInputStream;
import java.io.CharArrayReader;
import java.io.StringReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import javax.print.attribute.AttributeSetUtilities;
import javax.print.attribute.DocAttributeSet;

/**
 * This class is an implementation of interface <code>Doc</code> that can
 * be used in many common printing requests.
 * It can handle all of the presently defined "pre-defined" doc flavors
 * defined as static variables in the DocFlavor class.
 * <p>
 * In particular this class implements certain required semantics of the
 * Doc specification as follows:
 * <ul>
 * <li>constructs a stream for the service if requested and appropriate.
 * <li>ensures the same object is returned for each call on a method.
 * <li>ensures multiple threads can access the Doc
 * <li>performs some validation of that the data matches the doc flavor.
 * </ul>
 * Clients who want to re-use the doc object in other jobs,
 * or need a MultiDoc will not want to use this class.
 * <p>
 * If the print data is a stream, or a print job requests data as a
 * stream, then <code>SimpleDoc</code> does not monitor if the service
 * properly closes the stream after data transfer completion or job
 * termination.
 * Clients may prefer to use provide their own implementation of doc that
 * adds a listener to monitor job completion and to validate that
 * resources such as streams are freed (ie closed).
 */

public final class SimpleDoc implements Doc {

    private DocFlavor flavor;
    private DocAttributeSet attributes;
    private Object printData;
    private Reader reader;
    private InputStream inStream;

    /**
     * Constructs a <code>SimpleDoc</code> with the specified
     * print data, doc flavor and doc attribute set.
     * @param printData the print data object
     * @param flavor the <code>DocFlavor</code> object
     * @param attributes a <code>DocAttributeSet</code>, which can
     *                   be <code>null</code>
     * @throws IllegalArgumentException if <code>flavor</code> or
     *         <code>printData</code> is <code>null</code>, or the
     *         <code>printData</code> does not correspond
     *         to the specified doc flavor--for example, the data is
     *         not of the type specified as the representation in the
     *         <code>DocFlavor</code>.
     */
    public SimpleDoc(Object printData,
                     DocFlavor flavor, DocAttributeSet attributes) {

       if (flavor == null || printData == null) {
           throw new IllegalArgumentException("null argument(s)");
       }

       Class<?> repClass = null;
       try {
            String className = flavor.getRepresentationClassName();
            sun.reflect.misc.ReflectUtil.checkPackageAccess(className);
            repClass = Class.forName(className, false,
                              Thread.currentThread().getContextClassLoader());
       } catch (Throwable e) {
           throw new IllegalArgumentException("unknown representation class");
       }

       if (!repClass.isInstance(printData)) {
           throw new IllegalArgumentException("data is not of declared type");
       }

       this.flavor = flavor;
       if (attributes != null) {
           this.attributes = AttributeSetUtilities.unmodifiableView(attributes);
       }
       this.printData = printData;
    }

   /**
     * Determines the doc flavor in which this doc object will supply its
     * piece of print data.
     *
     * @return  Doc flavor.
     */
    public DocFlavor getDocFlavor() {
        return flavor;
    }

    /**
     * Obtains the set of printing attributes for this doc object. If the
     * returned attribute set includes an instance of a particular attribute
     * <I>X,</I> the printer must use that attribute value for this doc,
     * overriding any value of attribute <I>X</I> in the job's attribute set.
     * If the returned attribute set does not include an instance
     * of a particular attribute <I>X</I> or if null is returned, the printer
     * must consult the job's attribute set to obtain the value for
     * attribute <I>X,</I> and if not found there, the printer must use an
     * implementation-dependent default value. The returned attribute set is
     * unmodifiable.
     *
     * @return  Unmodifiable set of printing attributes for this doc, or null
     *          to obtain all attribute values from the job's attribute
     *          set.
     */
    public DocAttributeSet getAttributes() {
        return attributes;
    }

    /*
     * Obtains the print data representation object that contains this doc
     * object's piece of print data in the format corresponding to the
     * supported doc flavor.
     * The <CODE>getPrintData()</CODE> method returns an instance of
     * the representation class whose name is given by
     * {@link DocFlavor#getRepresentationClassName() getRepresentationClassName},
     * and the return value can be cast
     * from class Object to that representation class.
     *
     * @return  Print data representation object.
     *
     * @exception  IOException if the representation class is a stream and
     *     there was an I/O error while constructing the stream.
     */
    public Object getPrintData() throws IOException {
        return printData;
    }

    /**
     * Obtains a reader for extracting character print data from this doc.
     * The <code>Doc</code> implementation is required to support this
     * method if the <code>DocFlavor</code> has one of the following print
     * data representation classes, and return <code>null</code>
     * otherwise:
     * <UL>
     * <LI> <code>char[]</code>
     * <LI> <code>java.lang.String</code>
     * <LI> <code>java.io.Reader</code>
     * </UL>
     * The doc's print data representation object is used to construct and
     * return a <code>Reader</code> for reading the print data as a stream
     * of characters from the print data representation object.
     * However, if the print data representation object is itself a
     * <code>Reader</code> then the print data representation object is
     * simply returned.
     *
     * @return  a <code>Reader</code> for reading the print data
     *          characters from this doc.
     *          If a reader cannot be provided because this doc does not meet
     *          the criteria stated above, <code>null</code> is returned.
     *
     * @exception  IOException if there was an I/O error while creating
     *             the reader.
     */
    public Reader getReaderForText() throws IOException {

        if (printData instanceof Reader) {
            return (Reader)printData;
        }

        synchronized (this) {
            if (reader != null) {
                return reader;
            }

            if (printData instanceof char[]) {
               reader = new CharArrayReader((char[])printData);
            }
            else if (printData instanceof String) {
                reader = new StringReader((String)printData);
            }
        }
        return reader;
    }

    /**
     * Obtains an input stream for extracting byte print data from
     * this doc.
     * The <code>Doc</code> implementation is required to support this
     * method if the <code>DocFlavor</code> has one of the following print
     * data representation classes; otherwise this method
     * returns <code>null</code>:
     * <UL>
     * <LI> <code>byte[]</code>
     * <LI> <code>java.io.InputStream</code>
     * </UL>
     * The doc's print data representation object is obtained.  Then, an
     * input stream for reading the print data
     * from the print data representation object as a stream of bytes is
     * created and returned.
     * However, if the print data representation object is itself an
     * input stream then the print data representation object is simply
     * returned.
     *
     * @return  an <code>InputStream</code> for reading the print data
     *          bytes from this doc.  If an input stream cannot be
     *          provided because this doc does not meet
     *          the criteria stated above, <code>null</code> is returned.
     *
     * @exception  IOException
     *     if there was an I/O error while creating the input stream.
     */
    public InputStream getStreamForBytes() throws IOException {

        if (printData instanceof InputStream) {
            return (InputStream)printData;
        }

        synchronized (this) {
            if (inStream != null) {
                return inStream;
            }

            if (printData instanceof byte[]) {
               inStream = new ByteArrayInputStream((byte[])printData);
            }
        }
        return inStream;
    }

}
