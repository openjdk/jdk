/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataFormat;
import javax.imageio.metadata.IIOMetadataFormatImpl;
import javax.imageio.stream.ImageInputStream;

/**
 * A superclass containing instance variables and methods common to
 * <code>ImageReaderSpi</code> and <code>ImageWriterSpi</code>.
 *
 * @see IIORegistry
 * @see ImageReaderSpi
 * @see ImageWriterSpi
 *
 */
public abstract class ImageReaderWriterSpi extends IIOServiceProvider {

    /**
     * An array of strings to be returned from
     * <code>getFormatNames</code>, initially <code>null</code>.
     * Constructors should set this to a non-<code>null</code> value.
     */
    protected String[] names = null;

    /**
     * An array of strings to be returned from
     * <code>getFileSuffixes</code>, initially <code>null</code>.
     */
    protected String[] suffixes = null;

    /**
     * An array of strings to be returned from
     * <code>getMIMETypes</code>, initially <code>null</code>.
     */
    protected String[] MIMETypes = null;

    /**
     * A <code>String</code> containing the name of the associated
     * plug-in class, initially <code>null</code>.
     */
    protected String pluginClassName = null;

    /**
     * A boolean indicating whether this plug-in supports the
     * standard metadata format for stream metadata, initially
     * <code>false</code>.
     */
    protected boolean supportsStandardStreamMetadataFormat = false;

    /**
     * A <code>String</code> containing the name of the native stream
     * metadata format supported by this plug-in, initially
     * <code>null</code>.
     */
    protected String nativeStreamMetadataFormatName = null;

    /**
     * A <code>String</code> containing the class name of the native
     * stream metadata format supported by this plug-in, initially
     * <code>null</code>.
     */
    protected String nativeStreamMetadataFormatClassName = null;

    /**
     * An array of <code>String</code>s containing the names of any
     * additional stream metadata formats supported by this plug-in,
     * initially <code>null</code>.
     */
    protected String[] extraStreamMetadataFormatNames = null;

    /**
     * An array of <code>String</code>s containing the class names of
     * any additional stream metadata formats supported by this plug-in,
     * initially <code>null</code>.
     */
    protected String[] extraStreamMetadataFormatClassNames = null;

    /**
     * A boolean indicating whether this plug-in supports the
     * standard metadata format for image metadata, initially
     * <code>false</code>.
     */
    protected boolean supportsStandardImageMetadataFormat = false;

    /**
     * A <code>String</code> containing the name of the
     * native stream metadata format supported by this plug-in,
     * initially <code>null</code>.
     */
    protected String nativeImageMetadataFormatName = null;

    /**
     * A <code>String</code> containing the class name of the
     * native stream metadata format supported by this plug-in,
     * initially <code>null</code>.
     */
    protected String nativeImageMetadataFormatClassName = null;

    /**
     * An array of <code>String</code>s containing the names of any
     * additional image metadata formats supported by this plug-in,
     * initially <code>null</code>.
     */
    protected String[] extraImageMetadataFormatNames = null;

    /**
     * An array of <code>String</code>s containing the class names of
     * any additional image metadata formats supported by this
     * plug-in, initially <code>null</code>.
     */
    protected String[] extraImageMetadataFormatClassNames = null;

    /**
     * Constructs an <code>ImageReaderWriterSpi</code> with a given
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
     * @param pluginClassName the fully-qualified name of the
     * associated <code>ImageReader</code> or <code>ImageWriter</code>
     * class, as a non-<code>null</code> <code>String</code>.
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
     * @exception IllegalArgumentException if <code>pluginClassName</code>
     * is <code>null</code>.
     */
    public ImageReaderWriterSpi(String vendorName,
                                String version,
                                String[] names,
                                String[] suffixes,
                                String[] MIMETypes,
                                String pluginClassName,
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
        super(vendorName, version);
        if (names == null) {
            throw new IllegalArgumentException("names == null!");
        }
        if (names.length == 0) {
            throw new IllegalArgumentException("names.length == 0!");
        }
        if (pluginClassName == null) {
            throw new IllegalArgumentException("pluginClassName == null!");
        }

        this.names = names.clone();
        // If length == 0, leave it null
        if (suffixes != null && suffixes.length > 0) {
            this.suffixes = suffixes.clone();
        }
        // If length == 0, leave it null
        if (MIMETypes != null && MIMETypes.length > 0) {
            this.MIMETypes = MIMETypes.clone();
        }
        this.pluginClassName = pluginClassName;

        this.supportsStandardStreamMetadataFormat =
            supportsStandardStreamMetadataFormat;
        this.nativeStreamMetadataFormatName = nativeStreamMetadataFormatName;
        this.nativeStreamMetadataFormatClassName =
            nativeStreamMetadataFormatClassName;
        // If length == 0, leave it null
        if (extraStreamMetadataFormatNames != null &&
            extraStreamMetadataFormatNames.length > 0) {
            this.extraStreamMetadataFormatNames =
                extraStreamMetadataFormatNames.clone();
        }
        // If length == 0, leave it null
        if (extraStreamMetadataFormatClassNames != null &&
            extraStreamMetadataFormatClassNames.length > 0) {
            this.extraStreamMetadataFormatClassNames =
                extraStreamMetadataFormatClassNames.clone();
        }
        this.supportsStandardImageMetadataFormat =
            supportsStandardImageMetadataFormat;
        this.nativeImageMetadataFormatName = nativeImageMetadataFormatName;
        this.nativeImageMetadataFormatClassName =
            nativeImageMetadataFormatClassName;
        // If length == 0, leave it null
        if (extraImageMetadataFormatNames != null &&
            extraImageMetadataFormatNames.length > 0) {
            this.extraImageMetadataFormatNames =
                extraImageMetadataFormatNames.clone();
        }
        // If length == 0, leave it null
        if (extraImageMetadataFormatClassNames != null &&
            extraImageMetadataFormatClassNames.length > 0) {
            this.extraImageMetadataFormatClassNames =
                extraImageMetadataFormatClassNames.clone();
        }
    }

    /**
     * Constructs a blank <code>ImageReaderWriterSpi</code>.  It is up
     * to the subclass to initialize instance variables and/or
     * override method implementations in order to provide working
     * versions of all methods.
     */
    public ImageReaderWriterSpi() {
    }

    /**
     * Returns an array of <code>String</code>s containing
     * human-readable names for the formats that are generally usable
     * by the <code>ImageReader</code> or <code>ImageWriter</code>
     * implementation associated with this service provider.  For
     * example, a single <code>ImageReader</code> might be able to
     * process both PBM and PNM files.
     *
     * @return a non-<code>null</code> array of <code>String</code>s
     * or length at least 1 containing informal format names
     * associated with this reader or writer.
     */
    public String[] getFormatNames() {
        return names.clone();
    }

    /**
     * Returns an array of <code>String</code>s containing a list of
     * file suffixes associated with the formats that are generally
     * usable by the <code>ImageReader</code> or
     * <code>ImageWriter</code> implementation associated with this
     * service provider.  For example, a single
     * <code>ImageReader</code> might be able to process files with
     * '.pbm' and '.pnm' suffixes, or both '.jpg' and '.jpeg'
     * suffixes.  If there are no known file suffixes,
     * <code>null</code> will be returned.
     *
     * <p> Returning a particular suffix does not guarantee that files
     * with that suffix can be processed; it merely indicates that it
     * may be worthwhile attempting to decode or encode such files
     * using this service provider.
     *
     * @return an array of <code>String</code>s or length at least 1
     * containing common file suffixes associated with this reader or
     * writer, or <code>null</code>.
     */
    public String[] getFileSuffixes() {
        return suffixes == null ? null : suffixes.clone();
    }

    /**
     * Returns an array of <code>String</code>s containing a list of
     * MIME types associated with the formats that are generally
     * usable by the <code>ImageReader</code> or
     * <code>ImageWriter</code> implementation associated with this
     * service provider.
     *
     * <p> Ideally, only a single MIME type would be required in order
     * to describe a particular format.  However, for several reasons
     * it is necessary to associate a list of types with each service
     * provider.  First, many common image file formats do not have
     * standard MIME types, so a list of commonly used unofficial
     * names will be required, such as <code>image/x-pbm</code> and
     * <code>image/x-portable-bitmap</code>.  Some file formats have
     * official MIME types but may sometimes be referred to using
     * their previous unofficial designations, such as
     * <code>image/x-png</code> instead of the official
     * <code>image/png</code>.  Finally, a single service provider may
     * be capable of parsing multiple distinct types from the MIME
     * point of view, for example <code>image/x-xbitmap</code> and
     * <code>image/x-xpixmap</code>.
     *
     * <p> Returning a particular MIME type does not guarantee that
     * files claiming to be of that type can be processed; it merely
     * indicates that it may be worthwhile attempting to decode or
     * encode such files using this service provider.
     *
     * @return an array of <code>String</code>s or length at least 1
     * containing MIME types associated with this reader or writer, or
     * <code>null</code>.
     */
    public String[] getMIMETypes() {
        return MIMETypes == null ? null : MIMETypes.clone();
    }

    /**
     * Returns the fully-qualified class name of the
     * <code>ImageReader</code> or <code>ImageWriter</code> plug-in
     * associated with this service provider.
     *
     * @return the class name, as a non-<code>null</code>
     * <code>String</code>.
     */
    public String getPluginClassName() {
        return pluginClassName;
    }

    /**
     * Returns <code>true</code> if the standard metadata format is
     * among the document formats recognized by the
     * <code>getAsTree</code> and <code>setFromTree</code> methods on
     * the stream metadata objects produced or consumed by this
     * plug-in.
     *
     * @return <code>true</code> if the standard format is supported
     * for stream metadata.
     */
    public boolean isStandardStreamMetadataFormatSupported() {
        return supportsStandardStreamMetadataFormat;
    }

    /**
     * Returns the name of the "native" stream metadata format for
     * this plug-in, which typically allows for lossless encoding and
     * transmission of the stream metadata stored in the format handled by
     * this plug-in.  If no such format is supported,
     * <code>null</code>will be returned.
     *
     * <p> The default implementation returns the
     * <code>nativeStreamMetadataFormatName</code> instance variable,
     * which is typically set by the constructor.
     *
     * @return the name of the native stream metadata format, or
     * <code>null</code>.
     *
     */
    public String getNativeStreamMetadataFormatName() {
        return nativeStreamMetadataFormatName;
    }

    /**
     * Returns an array of <code>String</code>s containing the names
     * of additional document formats, other than the native and
     * standard formats, recognized by the
     * <code>getAsTree</code> and <code>setFromTree</code> methods on
     * the stream metadata objects produced or consumed by this
     * plug-in.
     *
     * <p> If the plug-in does not handle metadata, null should be
     * returned.
     *
     * <p> The set of formats may differ according to the particular
     * images being read or written; this method should indicate all
     * the additional formats supported by the plug-in under any
     * circumstances.
     *
     * <p> The default implementation returns a clone of the
     * <code>extraStreamMetadataFormatNames</code> instance variable,
     * which is typically set by the constructor.
     *
     * @return an array of <code>String</code>s, or null.
     *
     * @see IIOMetadata#getMetadataFormatNames
     * @see #getExtraImageMetadataFormatNames
     * @see #getNativeStreamMetadataFormatName
     */
    public String[] getExtraStreamMetadataFormatNames() {
        return extraStreamMetadataFormatNames == null ?
            null : extraStreamMetadataFormatNames.clone();
    }

    /**
     * Returns <code>true</code> if the standard metadata format is
     * among the document formats recognized by the
     * <code>getAsTree</code> and <code>setFromTree</code> methods on
     * the image metadata objects produced or consumed by this
     * plug-in.
     *
     * @return <code>true</code> if the standard format is supported
     * for image metadata.
     */
    public boolean isStandardImageMetadataFormatSupported() {
        return supportsStandardImageMetadataFormat;
    }

    /**
     * Returns the name of the "native" image metadata format for
     * this plug-in, which typically allows for lossless encoding and
     * transmission of the image metadata stored in the format handled by
     * this plug-in.  If no such format is supported,
     * <code>null</code>will be returned.
     *
     * <p> The default implementation returns the
     * <code>nativeImageMetadataFormatName</code> instance variable,
     * which is typically set by the constructor.
     *
     * @return the name of the native image metadata format, or
     * <code>null</code>.
     *
     * @see #getExtraImageMetadataFormatNames
     */
    public String getNativeImageMetadataFormatName() {
        return nativeImageMetadataFormatName;
    }

    /**
     * Returns an array of <code>String</code>s containing the names
     * of additional document formats, other than the native and
     * standard formats, recognized by the
     * <code>getAsTree</code> and <code>setFromTree</code> methods on
     * the image metadata objects produced or consumed by this
     * plug-in.
     *
     * <p> If the plug-in does not handle image metadata, null should
     * be returned.
     *
     * <p> The set of formats may differ according to the particular
     * images being read or written; this method should indicate all
     * the additional formats supported by the plug-in under any circumstances.
     *
     * <p> The default implementation returns a clone of the
     * <code>extraImageMetadataFormatNames</code> instance variable,
     * which is typically set by the constructor.
     *
     * @return an array of <code>String</code>s, or null.
     *
     * @see IIOMetadata#getMetadataFormatNames
     * @see #getExtraStreamMetadataFormatNames
     * @see #getNativeImageMetadataFormatName
     */
    public String[] getExtraImageMetadataFormatNames() {
        return extraImageMetadataFormatNames == null ?
            null : extraImageMetadataFormatNames.clone();
    }

    /**
     * Returns an <code>IIOMetadataFormat</code> object describing the
     * given stream metadata format, or <code>null</code> if no
     * description is available.  The supplied name must be the native
     * stream metadata format name, the standard metadata format name,
     * or one of those returned by
     * <code>getExtraStreamMetadataFormatNames</code>.
     *
     * @param formatName the desired stream metadata format.
     *
     * @return an <code>IIOMetadataFormat</code> object.
     *
     * @exception IllegalArgumentException if <code>formatName</code>
     * is <code>null</code> or is not a supported name.
     */
    public IIOMetadataFormat getStreamMetadataFormat(String formatName) {
        return getMetadataFormat(formatName,
                                 supportsStandardStreamMetadataFormat,
                                 nativeStreamMetadataFormatName,
                                 nativeStreamMetadataFormatClassName,
                                 extraStreamMetadataFormatNames,
                                 extraStreamMetadataFormatClassNames);
    }

    /**
     * Returns an <code>IIOMetadataFormat</code> object describing the
     * given image metadata format, or <code>null</code> if no
     * description is available.  The supplied name must be the native
     * image metadata format name, the standard metadata format name,
     * or one of those returned by
     * <code>getExtraImageMetadataFormatNames</code>.
     *
     * @param formatName the desired image metadata format.
     *
     * @return an <code>IIOMetadataFormat</code> object.
     *
     * @exception IllegalArgumentException if <code>formatName</code>
     * is <code>null</code> or is not a supported name.
     */
    public IIOMetadataFormat getImageMetadataFormat(String formatName) {
        return getMetadataFormat(formatName,
                                 supportsStandardImageMetadataFormat,
                                 nativeImageMetadataFormatName,
                                 nativeImageMetadataFormatClassName,
                                 extraImageMetadataFormatNames,
                                 extraImageMetadataFormatClassNames);
    }

    private IIOMetadataFormat getMetadataFormat(String formatName,
                                                boolean supportsStandard,
                                                String nativeName,
                                                String nativeClassName,
                                                String [] extraNames,
                                                String [] extraClassNames) {
        if (formatName == null) {
            throw new IllegalArgumentException("formatName == null!");
        }
        if (supportsStandard && formatName.equals
                (IIOMetadataFormatImpl.standardMetadataFormatName)) {

            return IIOMetadataFormatImpl.getStandardFormatInstance();
        }
        String formatClassName = null;
        if (formatName.equals(nativeName)) {
            formatClassName = nativeClassName;
        } else if (extraNames != null) {
            for (int i = 0; i < extraNames.length; i++) {
                if (formatName.equals(extraNames[i])) {
                    formatClassName = extraClassNames[i];
                    break;  // out of for
                }
            }
        }
        if (formatClassName == null) {
            throw new IllegalArgumentException("Unsupported format name");
        }
        try {
            Class<?> cls = Class.forName(formatClassName, true,
                                      ClassLoader.getSystemClassLoader());
            Method meth = cls.getMethod("getInstance");
            return (IIOMetadataFormat) meth.invoke(null);
        } catch (Exception e) {
            RuntimeException ex =
                new IllegalStateException ("Can't obtain format");
            ex.initCause(e);
            throw ex;
        }
    }
}
