/*
 * Copyright (c) 1999, 2001, Oracle and/or its affiliates. All rights reserved.
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

package javax.imageio.spi;

import java.io.IOException;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * The service provider interface (SPI) for <code>ImageReader</code>s.
 * For more information on service provider classes, see the class comment
 * for the <code>IIORegistry</code> class.
 *
 * <p> Each <code>ImageReaderSpi</code> provides several types of information
 * about the <code>ImageReader</code> class with which it is associated.
 *
 * <p> The name of the vendor who defined the SPI class and a
 * brief description of the class are available via the
 * <code>getVendorName</code>, <code>getDescription</code>,
 * and <code>getVersion</code> methods.
 * These methods may be internationalized to provide locale-specific
 * output.  These methods are intended mainly to provide short,
 * human-readable information that might be used to organize a pop-up
 * menu or other list.
 *
 * <p> Lists of format names, file suffixes, and MIME types associated
 * with the service may be obtained by means of the
 * <code>getFormatNames</code>, <code>getFileSuffixes</code>, and
 * <code>getMIMETypes</code> methods.  These methods may be used to
 * identify candidate <code>ImageReader</code>s for decoding a
 * particular file or stream based on manual format selection, file
 * naming, or MIME associations (for example, when accessing a file
 * over HTTP or as an email attachment).
 *
 * <p> A more reliable way to determine which <code>ImageReader</code>s
 * are likely to be able to parse a particular data stream is provided
 * by the <code>canDecodeInput</code> method.  This methods allows the
 * service provider to inspect the actual stream contents.
 *
 * <p> Finally, an instance of the <code>ImageReader</code> class
 * associated with this service provider may be obtained by calling
 * the <code>createReaderInstance</code> method.  Any heavyweight
 * initialization, such as the loading of native libraries or creation
 * of large tables, should be deferred at least until the first
 * invocation of this method.
 *
 * @see IIORegistry
 * @see javax.imageio.ImageReader
 *
 */
public abstract class ImageReaderSpi extends ImageReaderWriterSpi {

    /**
     * A single-element array, initially containing
     * <code>ImageInputStream.class</code>, to be returned from
     * <code>getInputTypes</code>.
     * @deprecated Instead of using this field, directly create
     * the equivalent array <code>{ ImageInputStream.class }</code>.
     */
    @Deprecated
    public static final Class[] STANDARD_INPUT_TYPE =
        { ImageInputStream.class };

    /**
     * An array of <code>Class</code> objects to be returned from
     * <code>getInputTypes</code>, initially <code>null</code>.
     */
    protected Class[] inputTypes = null;

    /**
     * An array of strings to be returned from
     * <code>getImageWriterSpiNames</code>, initially
     * <code>null</code>.
     */
    protected String[] writerSpiNames = null;

    /**
     * The <code>Class</code> of the reader, initially
     * <code>null</code>.
     */
    private Class readerClass = null;

    /**
     * Constructs a blank <code>ImageReaderSpi</code>.  It is up to
     * the subclass to initialize instance variables and/or override
     * method implementations in order to provide working versions of
     * all methods.
     */
    protected ImageReaderSpi() {
    }

    /**
     * Constructs an <code>ImageReaderSpi</code> with a given
     * set of values.
     *
     * @param vendorName the vendor name, as a non-<code>null</code>
     * <code>String</code>.
     * @param version a version identifier, as a non-<code>null</code>
     * <code>String</code>.
     * @param names a non-<code>null</code> array of
     * <code>String</code>s indicating the format names.  At least one
     * entry must be present.
     * @param suffixes an array of <code>String</code>s indicating the
     * common file suffixes.  If no suffixes are defined,
     * <code>null</code> should be supplied.  An array of length 0
     * will be normalized to <code>null</code>.
     * @param MIMETypes an array of <code>String</code>s indicating
     * the format's MIME types.  If no MIME types are defined,
     * <code>null</code> should be supplied.  An array of length 0
     * will be normalized to <code>null</code>.
     * @param readerClassName the fully-qualified name of the
     * associated <code>ImageReader</code> class, as a
     * non-<code>null</code> <code>String</code>.
     * @param inputTypes a non-<code>null</code> array of
     * <code>Class</code> objects of length at least 1 indicating the
     * legal input types.
     * @param writerSpiNames an array <code>String</code>s naming the
     * classes of all associated <code>ImageWriter</code>s, or
     * <code>null</code>.  An array of length 0 is normalized to
     * <code>null</code>.
     * @param supportsStandardStreamMetadataFormat a
     * <code>boolean</code> that indicates whether a stream metadata
     * object can use trees described by the standard metadata format.
     * @param nativeStreamMetadataFormatName a
     * <code>String</code>, or <code>null</code>, to be returned from
     * <code>getNativeStreamMetadataFormatName</code>.
     * @param nativeStreamMetadataFormatClassName a
     * <code>String</code>, or <code>null</code>, to be used to instantiate
     * a metadata format object to be returned from
     * <code>getNativeStreamMetadataFormat</code>.
     * @param extraStreamMetadataFormatNames an array of
     * <code>String</code>s, or <code>null</code>, to be returned from
     * <code>getExtraStreamMetadataFormatNames</code>.  An array of length
     * 0 is normalized to <code>null</code>.
     * @param extraStreamMetadataFormatClassNames an array of
     * <code>String</code>s, or <code>null</code>, to be used to instantiate
     * a metadata format object to be returned from
     * <code>getStreamMetadataFormat</code>.  An array of length
     * 0 is normalized to <code>null</code>.
     * @param supportsStandardImageMetadataFormat a
     * <code>boolean</code> that indicates whether an image metadata
     * object can use trees described by the standard metadata format.
     * @param nativeImageMetadataFormatName a
     * <code>String</code>, or <code>null</code>, to be returned from
     * <code>getNativeImageMetadataFormatName</code>.
     * @param nativeImageMetadataFormatClassName a
     * <code>String</code>, or <code>null</code>, to be used to instantiate
     * a metadata format object to be returned from
     * <code>getNativeImageMetadataFormat</code>.
     * @param extraImageMetadataFormatNames an array of
     * <code>String</code>s to be returned from
     * <code>getExtraImageMetadataFormatNames</code>.  An array of length 0
     * is normalized to <code>null</code>.
     * @param extraImageMetadataFormatClassNames an array of
     * <code>String</code>s, or <code>null</code>, to be used to instantiate
     * a metadata format object to be returned from
     * <code>getImageMetadataFormat</code>.  An array of length
     * 0 is normalized to <code>null</code>.
     *
     * @exception IllegalArgumentException if <code>vendorName</code>
     * is <code>null</code>.
     * @exception IllegalArgumentException if <code>version</code>
     * is <code>null</code>.
     * @exception IllegalArgumentException if <code>names</code>
     * is <code>null</code> or has length 0.
     * @exception IllegalArgumentException if <code>readerClassName</code>
     * is <code>null</code>.
     * @exception IllegalArgumentException if <code>inputTypes</code>
     * is <code>null</code> or has length 0.
     */
    public ImageReaderSpi(String vendorName,
                          String version,
                          String[] names,
                          String[] suffixes,
                          String[] MIMETypes,
                          String readerClassName,
                          Class[] inputTypes,
                          String[] writerSpiNames,
                          boolean supportsStandardStreamMetadataFormat,
                          String nativeStreamMetadataFormatName,
                          String nativeStreamMetadataFormatClassName,
                          String[] extraStreamMetadataFormatNames,
                          String[] extraStreamMetadataFormatClassNames,
                          boolean supportsStandardImageMetadataFormat,
                          String nativeImageMetadataFormatName,
                          String nativeImageMetadataFormatClassName,
                          String[] extraImageMetadataFormatNames,
                          String[] extraImageMetadataFormatClassNames) {
        super(vendorName, version,
              names, suffixes, MIMETypes, readerClassName,
              supportsStandardStreamMetadataFormat,
              nativeStreamMetadataFormatName,
              nativeStreamMetadataFormatClassName,
              extraStreamMetadataFormatNames,
              extraStreamMetadataFormatClassNames,
              supportsStandardImageMetadataFormat,
              nativeImageMetadataFormatName,
              nativeImageMetadataFormatClassName,
              extraImageMetadataFormatNames,
              extraImageMetadataFormatClassNames);

        if (inputTypes == null) {
            throw new IllegalArgumentException
                ("inputTypes == null!");
        }
        if (inputTypes.length == 0) {
            throw new IllegalArgumentException
                ("inputTypes.length == 0!");
        }

        this.inputTypes = (inputTypes == STANDARD_INPUT_TYPE) ?
            new Class<?>[] { ImageInputStream.class } :
            inputTypes.clone();

        // If length == 0, leave it null
        if (writerSpiNames != null && writerSpiNames.length > 0) {
            this.writerSpiNames = (String[])writerSpiNames.clone();
        }
    }

    /**
     * Returns an array of <code>Class</code> objects indicating what
     * types of objects may be used as arguments to the reader's
     * <code>setInput</code> method.
     *
     * <p> For most readers, which only accept input from an
     * <code>ImageInputStream</code>, a single-element array
     * containing <code>ImageInputStream.class</code> should be
     * returned.
     *
     * @return a non-<code>null</code> array of
     * <code>Class</code>objects of length at least 1.
     */
    public Class[] getInputTypes() {
        return (Class[])inputTypes.clone();
    }

    /**
     * Returns <code>true</code> if the supplied source object appears
     * to be of the format supported by this reader.  Returning
     * <code>true</code> from this method does not guarantee that
     * reading will succeed, only that there appears to be a
     * reasonable chance of success based on a brief inspection of the
     * stream contents.  If the source is an
     * <code>ImageInputStream</code>, implementations will commonly
     * check the first several bytes of the stream for a "magic
     * number" associated with the format.  Once actual reading has
     * commenced, the reader may still indicate failure at any time
     * prior to the completion of decoding.
     *
     * <p> It is important that the state of the object not be
     * disturbed in order that other <code>ImageReaderSpi</code>s can
     * properly determine whether they are able to decode the object.
     * In particular, if the source is an
     * <code>ImageInputStream</code>, a
     * <code>mark</code>/<code>reset</code> pair should be used to
     * preserve the stream position.
     *
     * <p> Formats such as "raw," which can potentially attempt
     * to read nearly any stream, should return <code>false</code>
     * in order to avoid being invoked in preference to a closer
     * match.
     *
     * <p> If <code>source</code> is not an instance of one of the
     * classes returned by <code>getInputTypes</code>, the method
     * should simply return <code>false</code>.
     *
     * @param source the object (typically an
     * <code>ImageInputStream</code>) to be decoded.
     *
     * @return <code>true</code> if it is likely that this stream can
     * be decoded.
     *
     * @exception IllegalArgumentException if <code>source</code> is
     * <code>null</code>.
     * @exception IOException if an I/O error occurs while reading the
     * stream.
     */
    public abstract boolean canDecodeInput(Object source) throws IOException;

    /**
     * Returns an instance of the <code>ImageReader</code>
     * implementation associated with this service provider.
     * The returned object will initially be in an initial state
     * as if its <code>reset</code> method had been called.
     *
     * <p> The default implementation simply returns
     * <code>createReaderInstance(null)</code>.
     *
     * @return an <code>ImageReader</code> instance.
     *
     * @exception IOException if an error occurs during loading,
     * or initialization of the reader class, or during instantiation
     * or initialization of the reader object.
     */
    public ImageReader createReaderInstance() throws IOException {
        return createReaderInstance(null);
    }

    /**
     * Returns an instance of the <code>ImageReader</code>
     * implementation associated with this service provider.
     * The returned object will initially be in an initial state
     * as if its <code>reset</code> method had been called.
     *
     * <p> An <code>Object</code> may be supplied to the plug-in at
     * construction time.  The nature of the object is entirely
     * plug-in specific.
     *
     * <p> Typically, a plug-in will implement this method using code
     * such as <code>return new MyImageReader(this)</code>.
     *
     * @param extension a plug-in specific extension object, which may
     * be <code>null</code>.
     *
     * @return an <code>ImageReader</code> instance.
     *
     * @exception IOException if the attempt to instantiate
     * the reader fails.
     * @exception IllegalArgumentException if the
     * <code>ImageReader</code>'s contructor throws an
     * <code>IllegalArgumentException</code> to indicate that the
     * extension object is unsuitable.
     */
    public abstract ImageReader createReaderInstance(Object extension)
        throws IOException;

    /**
     * Returns <code>true</code> if the <code>ImageReader</code> object
     * passed in is an instance of the <code>ImageReader</code>
     * associated with this service provider.
     *
     * <p> The default implementation compares the fully-qualified
     * class name of the <code>reader</code> argument with the class
     * name passed into the constructor.  This method may be overridden
     * if more sophisticated checking is required.
     *
     * @param reader an <code>ImageReader</code> instance.
     *
     * @return <code>true</code> if <code>reader</code> is recognized.
     *
     * @exception IllegalArgumentException if <code>reader</code> is
     * <code>null</code>.
     */
    public boolean isOwnReader(ImageReader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("reader == null!");
        }
        String name = reader.getClass().getName();
        return name.equals(pluginClassName);
    }

    /**
     * Returns an array of <code>String</code>s containing the fully
     * qualified names of all the <code>ImageWriterSpi</code> classes
     * that can understand the internal metadata representation used
     * by the <code>ImageReader</code> associated with this service
     * provider, or <code>null</code> if there are no such
     * <code>ImageWriter</code>s specified.  If a
     * non-<code>null</code> value is returned, it must have non-zero
     * length.
     *
     * <p> The first item in the array must be the name of the service
     * provider for the "preferred" writer, as it will be used to
     * instantiate the <code>ImageWriter</code> returned by
     * <code>ImageIO.getImageWriter(ImageReader)</code>.
     *
     * <p> This mechanism may be used to obtain
     * <code>ImageWriters</code> that will understand the internal
     * structure of non-pixel meta-data (see
     * <code>IIOTreeInfo</code>) generated by an
     * <code>ImageReader</code>.  By obtaining this data from the
     * <code>ImageReader</code> and passing it on to one of the
     * <code>ImageWriters</code> obtained with this method, a client
     * program can read an image, modify it in some way, and write it
     * back out while preserving all meta-data, without having to
     * understand anything about the internal structure of the
     * meta-data, or even about the image format.
     *
     * @return an array of <code>String</code>s of length at least 1
     * containing names of <code>ImageWriterSpi</code>, or
     * <code>null</code>.
     *
     * @see javax.imageio.ImageIO#getImageWriter(ImageReader)
     */
    public String[] getImageWriterSpiNames() {
        return writerSpiNames == null ?
            null : (String[])writerSpiNames.clone();
    }
}
