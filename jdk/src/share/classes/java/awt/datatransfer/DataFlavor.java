/*
 * Copyright (c) 1996, 2006, Oracle and/or its affiliates. All rights reserved.
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

package java.awt.datatransfer;

import java.io.*;
import java.nio.*;
import java.util.*;

import sun.awt.datatransfer.DataTransferer;
import sun.reflect.misc.ReflectUtil;

import static sun.security.util.SecurityConstants.GET_CLASSLOADER_PERMISSION;

/**
 * A {@code DataFlavor} provides meta information about data. {@code DataFlavor}
 * is typically used to access data on the clipboard, or during
 * a drag and drop operation.
 * <p>
 * An instance of {@code DataFlavor} encapsulates a content type as
 * defined in <a href="http://www.ietf.org/rfc/rfc2045.txt">RFC 2045</a>
 * and <a href="http://www.ietf.org/rfc/rfc2046.txt">RFC 2046</a>.
 * A content type is typically referred to as a MIME type.
 * <p>
 * A content type consists of a media type (referred
 * to as the primary type), a subtype, and optional parameters. See
 * <a href="http://www.ietf.org/rfc/rfc2045.txt">RFC 2045</a>
 * for details on the syntax of a MIME type.
 * <p>
 * The JRE data transfer implementation interprets the parameter &quot;class&quot;
 * of a MIME type as <B>a representation class</b>.
 * The representation class reflects the class of the object being
 * transferred. In other words, the representation class is the type of
 * object returned by {@link Transferable#getTransferData}.
 * For example, the MIME type of {@link #imageFlavor} is
 * {@code "image/x-java-image;class=java.awt.Image"},
 * the primary type is {@code image}, the subtype is
 * {@code x-java-image}, and the representation class is
 * {@code java.awt.Image}. When {@code getTransferData} is invoked
 * with a {@code DataFlavor} of {@code imageFlavor}, an instance of
 * {@code java.awt.Image} is returned.
 * It's important to note that {@code DataFlavor} does no error checking
 * against the representation class. It is up to consumers of
 * {@code DataFlavor}, such as {@code Transferable}, to honor the representation
 * class.
 * <br>
 * Note, if you do not specify a representation class when
 * creating a {@code DataFlavor}, the default
 * representation class is used. See appropriate documentation for
 * {@code DataFlavor}'s constructors.
 * <p>
 * Also, {@code DataFlavor} instances with the &quot;text&quot; primary
 * MIME type may have a &quot;charset&quot; parameter. Refer to
 * <a href="http://www.ietf.org/rfc/rfc2046.txt">RFC 2046</a> and
 * {@link #selectBestTextFlavor} for details on &quot;text&quot; MIME types
 * and the &quot;charset&quot; parameter.
 * <p>
 * Equality of {@code DataFlavors} is determined by the primary type,
 * subtype, and representation class. Refer to {@link #equals(DataFlavor)} for
 * details. When determining equality, any optional parameters are ignored.
 * For example, the following produces two {@code DataFlavors} that
 * are considered identical:
 * <pre>
 *   DataFlavor flavor1 = new DataFlavor(Object.class, &quot;X-test/test; class=&lt;java.lang.Object&gt;; foo=bar&quot;);
 *   DataFlavor flavor2 = new DataFlavor(Object.class, &quot;X-test/test; class=&lt;java.lang.Object&gt;; x=y&quot;);
 *   // The following returns true.
 *   flavor1.equals(flavor2);
 * </pre>
 * As mentioned, {@code flavor1} and {@code flavor2} are considered identical.
 * As such, asking a {@code Transferable} for either {@code DataFlavor} returns
 * the same results.
 * <p>
 * For more information on the using data transfer with Swing see
 * the <a href="http://java.sun.com/docs/books/tutorial/uiswing/misc/dnd.html">
 * How to Use Drag and Drop and Data Transfer</a>,
 * section in <em>Java Tutorial</em>.
 *
 * @author      Blake Sullivan
 * @author      Laurence P. G. Cable
 * @author      Jeff Dunn
 */
public class DataFlavor implements Externalizable, Cloneable {

    private static final long serialVersionUID = 8367026044764648243L;
    private static final Class ioInputStreamClass = java.io.InputStream.class;

    /**
     * Tries to load a class from: the bootstrap loader, the system loader,
     * the context loader (if one is present) and finally the loader specified.
     *
     * @param className the name of the class to be loaded
     * @param fallback the fallback loader
     * @return the class loaded
     * @exception ClassNotFoundException if class is not found
     */
    protected final static Class<?> tryToLoadClass(String className,
                                                   ClassLoader fallback)
        throws ClassNotFoundException
    {
        ReflectUtil.checkPackageAccess(className);
        try {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPermission(GET_CLASSLOADER_PERMISSION);
            }
            ClassLoader loader = ClassLoader.getSystemClassLoader();
            try {
                // bootstrap class loader and system class loader if present
                return Class.forName(className, true, loader);
            }
            catch (ClassNotFoundException exception) {
                // thread context class loader if and only if present
                loader = Thread.currentThread().getContextClassLoader();
                if (loader != null) {
                    try {
                        return Class.forName(className, true, loader);
                    }
                    catch (ClassNotFoundException e) {
                        // fallback to user's class loader
                    }
                }
            }
        } catch (SecurityException exception) {
            // ignore secured class loaders
        }
        return Class.forName(className, true, fallback);
    }

    /*
     * private initializer
     */
    static private DataFlavor createConstant(Class rc, String prn) {
        try {
            return new DataFlavor(rc, prn);
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * private initializer
     */
    static private DataFlavor createConstant(String mt, String prn) {
        try {
            return new DataFlavor(mt, prn);
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * private initializer
     */
    static private DataFlavor initHtmlDataFlavor(String htmlFlavorType) {
        try {
            return new DataFlavor ("text/html; class=java.lang.String;document=" +
                                       htmlFlavorType + ";charset=Unicode");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The <code>DataFlavor</code> representing a Java Unicode String class,
     * where:
     * <pre>
     *     representationClass = java.lang.String
     *     mimeType           = "application/x-java-serialized-object"
     * </pre>
     */
    public static final DataFlavor stringFlavor = createConstant(java.lang.String.class, "Unicode String");

    /**
     * The <code>DataFlavor</code> representing a Java Image class,
     * where:
     * <pre>
     *     representationClass = java.awt.Image
     *     mimeType            = "image/x-java-image"
     * </pre>
     */
    public static final DataFlavor imageFlavor = createConstant("image/x-java-image; class=java.awt.Image", "Image");

    /**
     * The <code>DataFlavor</code> representing plain text with Unicode
     * encoding, where:
     * <pre>
     *     representationClass = InputStream
     *     mimeType            = "text/plain; charset=unicode"
     * </pre>
     * This <code>DataFlavor</code> has been <b>deprecated</b> because
     * (1) Its representation is an InputStream, an 8-bit based representation,
     * while Unicode is a 16-bit character set; and (2) The charset "unicode"
     * is not well-defined. "unicode" implies a particular platform's
     * implementation of Unicode, not a cross-platform implementation.
     *
     * @deprecated as of 1.3. Use <code>DataFlavor.getReaderForText(Transferable)</code>
     *             instead of <code>Transferable.getTransferData(DataFlavor.plainTextFlavor)</code>.
     */
    @Deprecated
    public static final DataFlavor plainTextFlavor = createConstant("text/plain; charset=unicode; class=java.io.InputStream", "Plain Text");

    /**
     * A MIME Content-Type of application/x-java-serialized-object represents
     * a graph of Java object(s) that have been made persistent.
     *
     * The representation class associated with this <code>DataFlavor</code>
     * identifies the Java type of an object returned as a reference
     * from an invocation <code>java.awt.datatransfer.getTransferData</code>.
     */
    public static final String javaSerializedObjectMimeType = "application/x-java-serialized-object";

    /**
     * To transfer a list of files to/from Java (and the underlying
     * platform) a <code>DataFlavor</code> of this type/subtype and
     * representation class of <code>java.util.List</code> is used.
     * Each element of the list is required/guaranteed to be of type
     * <code>java.io.File</code>.
     */
    public static final DataFlavor javaFileListFlavor = createConstant("application/x-java-file-list;class=java.util.List", null);

    /**
     * To transfer a reference to an arbitrary Java object reference that
     * has no associated MIME Content-type, across a <code>Transferable</code>
     * interface WITHIN THE SAME JVM, a <code>DataFlavor</code>
     * with this type/subtype is used, with a <code>representationClass</code>
     * equal to the type of the class/interface being passed across the
     * <code>Transferable</code>.
     * <p>
     * The object reference returned from
     * <code>Transferable.getTransferData</code> for a <code>DataFlavor</code>
     * with this MIME Content-Type is required to be
     * an instance of the representation Class of the <code>DataFlavor</code>.
     */
    public static final String javaJVMLocalObjectMimeType = "application/x-java-jvm-local-objectref";

    /**
     * In order to pass a live link to a Remote object via a Drag and Drop
     * <code>ACTION_LINK</code> operation a Mime Content Type of
     * application/x-java-remote-object should be used,
     * where the representation class of the <code>DataFlavor</code>
     * represents the type of the <code>Remote</code> interface to be
     * transferred.
     */
    public static final String javaRemoteObjectMimeType = "application/x-java-remote-object";

    /**
     * Represents a piece of an HTML markup. The markup consists of the part
     * selected on the source side. Therefore some tags in the markup may be
     * unpaired. If the flavor is used to represent the data in
     * a {@link Transferable} instance, no additional changes will be made.
     * This DataFlavor instance represents the same HTML markup as DataFlavor
     * instances which content MIME type does not contain document parameter
     * and representation class is the String class.
     * <pre>
     *     representationClass = String
     *     mimeType           = "text/html"
     * </pre>
     */
    public static DataFlavor selectionHtmlFlavor = initHtmlDataFlavor("selection");

    /**
     * Represents a piece of an HTML markup. If possible, the markup received
     * from a native system is supplemented with pair tags to be
     * a well-formed HTML markup. If the flavor is used to represent the data in
     * a {@link Transferable} instance, no additional changes will be made.
     * <pre>
     *     representationClass = String
     *     mimeType           = "text/html"
     * </pre>
     */
    public static DataFlavor fragmentHtmlFlavor = initHtmlDataFlavor("fragment");

    /**
     * Represents a piece of an HTML markup. If possible, the markup
     * received from a native system is supplemented with additional
     * tags to make up a well-formed HTML document. If the flavor is used to
     * represent the data in a {@link Transferable} instance,
     * no additional changes will be made.
     * <pre>
     *     representationClass = String
     *     mimeType           = "text/html"
     * </pre>
     */
    public static  DataFlavor allHtmlFlavor = initHtmlDataFlavor("all");

    /**
     * Constructs a new <code>DataFlavor</code>.  This constructor is
     * provided only for the purpose of supporting the
     * <code>Externalizable</code> interface.  It is not
     * intended for public (client) use.
     *
     * @since 1.2
     */
    public DataFlavor() {
        super();
    }

    /**
     * Constructs a fully specified <code>DataFlavor</code>.
     *
     * @exception NullPointerException if either <code>primaryType</code>,
     *            <code>subType</code> or <code>representationClass</code> is null
     */
    private DataFlavor(String primaryType, String subType, MimeTypeParameterList params, Class representationClass, String humanPresentableName) {
        super();
        if (primaryType == null) {
            throw new NullPointerException("primaryType");
        }
        if (subType == null) {
            throw new NullPointerException("subType");
        }
        if (representationClass == null) {
            throw new NullPointerException("representationClass");
        }

        if (params == null) params = new MimeTypeParameterList();

        params.set("class", representationClass.getName());

        if (humanPresentableName == null) {
            humanPresentableName = (String)params.get("humanPresentableName");

            if (humanPresentableName == null)
                humanPresentableName = primaryType + "/" + subType;
        }

        try {
            mimeType = new MimeType(primaryType, subType, params);
        } catch (MimeTypeParseException mtpe) {
            throw new IllegalArgumentException("MimeType Parse Exception: " + mtpe.getMessage());
        }

        this.representationClass  = representationClass;
        this.humanPresentableName = humanPresentableName;

        mimeType.removeParameter("humanPresentableName");
    }

    /**
     * Constructs a <code>DataFlavor</code> that represents a Java class.
     * <p>
     * The returned <code>DataFlavor</code> will have the following
     * characteristics:
     * <pre>
     *    representationClass = representationClass
     *    mimeType            = application/x-java-serialized-object
     * </pre>
     * @param representationClass the class used to transfer data in this flavor
     * @param humanPresentableName the human-readable string used to identify
     *                 this flavor; if this parameter is <code>null</code>
     *                 then the value of the the MIME Content Type is used
     * @exception NullPointerException if <code>representationClass</code> is null
     */
    public DataFlavor(Class<?> representationClass, String humanPresentableName) {
        this("application", "x-java-serialized-object", null, representationClass, humanPresentableName);
        if (representationClass == null) {
            throw new NullPointerException("representationClass");
        }
    }

    /**
     * Constructs a <code>DataFlavor</code> that represents a
     * <code>MimeType</code>.
     * <p>
     * The returned <code>DataFlavor</code> will have the following
     * characteristics:
     * <p>
     * If the <code>mimeType</code> is
     * "application/x-java-serialized-object; class=&lt;representation class&gt;",
     * the result is the same as calling
     * <code>new DataFlavor(Class:forName(&lt;representation class&gt;)</code>.
     * <p>
     * Otherwise:
     * <pre>
     *     representationClass = InputStream
     *     mimeType            = mimeType
     * </pre>
     * @param mimeType the string used to identify the MIME type for this flavor;
     *                 if the the <code>mimeType</code> does not specify a
     *                 "class=" parameter, or if the class is not successfully
     *                 loaded, then an <code>IllegalArgumentException</code>
     *                 is thrown
     * @param humanPresentableName the human-readable string used to identify
     *                 this flavor; if this parameter is <code>null</code>
     *                 then the value of the the MIME Content Type is used
     * @exception IllegalArgumentException if <code>mimeType</code> is
     *                 invalid or if the class is not successfully loaded
     * @exception NullPointerException if <code>mimeType</code> is null
     */
    public DataFlavor(String mimeType, String humanPresentableName) {
        super();
        if (mimeType == null) {
            throw new NullPointerException("mimeType");
        }
        try {
            initialize(mimeType, humanPresentableName, this.getClass().getClassLoader());
        } catch (MimeTypeParseException mtpe) {
            throw new IllegalArgumentException("failed to parse:" + mimeType);
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalArgumentException("can't find specified class: " + cnfe.getMessage());
        }
    }

    /**
     * Constructs a <code>DataFlavor</code> that represents a
     * <code>MimeType</code>.
     * <p>
     * The returned <code>DataFlavor</code> will have the following
     * characteristics:
     * <p>
     * If the mimeType is
     * "application/x-java-serialized-object; class=&lt;representation class&gt;",
     * the result is the same as calling
     * <code>new DataFlavor(Class:forName(&lt;representation class&gt;)</code>.
     * <p>
     * Otherwise:
     * <pre>
     *     representationClass = InputStream
     *     mimeType            = mimeType
     * </pre>
     * @param mimeType the string used to identify the MIME type for this flavor
     * @param humanPresentableName the human-readable string used to
     *          identify this flavor
     * @param classLoader the class loader to use
     * @exception ClassNotFoundException if the class is not loaded
     * @exception IllegalArgumentException if <code>mimeType</code> is
     *                 invalid
     * @exception NullPointerException if <code>mimeType</code> is null
     */
    public DataFlavor(String mimeType, String humanPresentableName, ClassLoader classLoader) throws ClassNotFoundException {
        super();
        if (mimeType == null) {
            throw new NullPointerException("mimeType");
        }
        try {
            initialize(mimeType, humanPresentableName, classLoader);
        } catch (MimeTypeParseException mtpe) {
            throw new IllegalArgumentException("failed to parse:" + mimeType);
        }
    }

    /**
     * Constructs a <code>DataFlavor</code> from a <code>mimeType</code> string.
     * The string can specify a "class=<fully specified Java class name>"
     * parameter to create a <code>DataFlavor</code> with the desired
     * representation class. If the string does not contain "class=" parameter,
     * <code>java.io.InputStream</code> is used as default.
     *
     * @param mimeType the string used to identify the MIME type for this flavor;
     *                 if the class specified by "class=" parameter is not
     *                 successfully loaded, then an
     *                 <code>ClassNotFoundException</code> is thrown
     * @exception ClassNotFoundException if the class is not loaded
     * @exception IllegalArgumentException if <code>mimeType</code> is
     *                 invalid
     * @exception NullPointerException if <code>mimeType</code> is null
     */
    public DataFlavor(String mimeType) throws ClassNotFoundException {
        super();
        if (mimeType == null) {
            throw new NullPointerException("mimeType");
        }
        try {
            initialize(mimeType, null, this.getClass().getClassLoader());
        } catch (MimeTypeParseException mtpe) {
            throw new IllegalArgumentException("failed to parse:" + mimeType);
        }
    }

   /**
    * Common initialization code called from various constructors.
    *
    * @param mimeType the MIME Content Type (must have a class= param)
    * @param humanPresentableName the human Presentable Name or
    *                 <code>null</code>
    * @param classLoader the fallback class loader to resolve against
    *
    * @throws MimeTypeParseException
    * @throws ClassNotFoundException
    * @throws  NullPointerException if <code>mimeType</code> is null
    *
    * @see tryToLoadClass
    */
    private void initialize(String mimeType, String humanPresentableName, ClassLoader classLoader) throws MimeTypeParseException, ClassNotFoundException {
        if (mimeType == null) {
            throw new NullPointerException("mimeType");
        }

        this.mimeType = new MimeType(mimeType); // throws

        String rcn = getParameter("class");

        if (rcn == null) {
            if ("application/x-java-serialized-object".equals(this.mimeType.getBaseType()))

                throw new IllegalArgumentException("no representation class specified for:" + mimeType);
            else
                representationClass = java.io.InputStream.class; // default
        } else { // got a class name
            representationClass = DataFlavor.tryToLoadClass(rcn, classLoader);
        }

        this.mimeType.setParameter("class", representationClass.getName());

        if (humanPresentableName == null) {
            humanPresentableName = this.mimeType.getParameter("humanPresentableName");
            if (humanPresentableName == null)
                humanPresentableName = this.mimeType.getPrimaryType() + "/" + this.mimeType.getSubType();
        }

        this.humanPresentableName = humanPresentableName; // set it.

        this.mimeType.removeParameter("humanPresentableName"); // just in case
    }

    /**
     * String representation of this <code>DataFlavor</code> and its
     * parameters. The resulting <code>String</code> contains the name of
     * the <code>DataFlavor</code> class, this flavor's MIME type, and its
     * representation class. If this flavor has a primary MIME type of "text",
     * supports the charset parameter, and has an encoded representation, the
     * flavor's charset is also included. See <code>selectBestTextFlavor</code>
     * for a list of text flavors which support the charset parameter.
     *
     * @return  string representation of this <code>DataFlavor</code>
     * @see #selectBestTextFlavor
     */
    public String toString() {
        String string = getClass().getName();
        string += "["+paramString()+"]";
        return string;
    }

    private String paramString() {
        String params = "";
        params += "mimetype=";
        if (mimeType == null) {
            params += "null";
        } else {
            params += mimeType.getBaseType();
        }
        params += ";representationclass=";
        if (representationClass == null) {
           params += "null";
        } else {
           params += representationClass.getName();
        }
        if (DataTransferer.isFlavorCharsetTextType(this) &&
            (isRepresentationClassInputStream() ||
             isRepresentationClassByteBuffer() ||
             DataTransferer.byteArrayClass.equals(representationClass)))
        {
            params += ";charset=" + DataTransferer.getTextCharset(this);
        }
        return params;
    }

    /**
     * Returns a <code>DataFlavor</code> representing plain text with Unicode
     * encoding, where:
     * <pre>
     *     representationClass = java.io.InputStream
     *     mimeType            = "text/plain;
     *                            charset=&lt;platform default Unicode encoding&gt;"
     * </pre>
     * Sun's implementation for Microsoft Windows uses the encoding <code>utf-16le</code>.
     * Sun's implementation for Solaris and Linux uses the encoding
     * <code>iso-10646-ucs-2</code>.
     *
     * @return a <code>DataFlavor</code> representing plain text
     *    with Unicode encoding
     * @since 1.3
     */
    public static final DataFlavor getTextPlainUnicodeFlavor() {
        String encoding = null;
        DataTransferer transferer = DataTransferer.getInstance();
        if (transferer != null) {
            encoding = transferer.getDefaultUnicodeEncoding();
        }
        return new DataFlavor(
            "text/plain;charset="+encoding
            +";class=java.io.InputStream", "Plain Text");
    }

    /**
     * Selects the best text <code>DataFlavor</code> from an array of <code>
     * DataFlavor</code>s. Only <code>DataFlavor.stringFlavor</code>, and
     * equivalent flavors, and flavors that have a primary MIME type of "text",
     * are considered for selection.
     * <p>
     * Flavors are first sorted by their MIME types in the following order:
     * <ul>
     * <li>"text/sgml"
     * <li>"text/xml"
     * <li>"text/html"
     * <li>"text/rtf"
     * <li>"text/enriched"
     * <li>"text/richtext"
     * <li>"text/uri-list"
     * <li>"text/tab-separated-values"
     * <li>"text/t140"
     * <li>"text/rfc822-headers"
     * <li>"text/parityfec"
     * <li>"text/directory"
     * <li>"text/css"
     * <li>"text/calendar"
     * <li>"application/x-java-serialized-object"
     * <li>"text/plain"
     * <li>"text/&lt;other&gt;"
     * </ul>
     * <p>For example, "text/sgml" will be selected over
     * "text/html", and <code>DataFlavor.stringFlavor</code> will be chosen
     * over <code>DataFlavor.plainTextFlavor</code>.
     * <p>
     * If two or more flavors share the best MIME type in the array, then that
     * MIME type will be checked to see if it supports the charset parameter.
     * <p>
     * The following MIME types support, or are treated as though they support,
     * the charset parameter:
     * <ul>
     * <li>"text/sgml"
     * <li>"text/xml"
     * <li>"text/html"
     * <li>"text/enriched"
     * <li>"text/richtext"
     * <li>"text/uri-list"
     * <li>"text/directory"
     * <li>"text/css"
     * <li>"text/calendar"
     * <li>"application/x-java-serialized-object"
     * <li>"text/plain"
     * </ul>
     * The following MIME types do not support, or are treated as though they
     * do not support, the charset parameter:
     * <ul>
     * <li>"text/rtf"
     * <li>"text/tab-separated-values"
     * <li>"text/t140"
     * <li>"text/rfc822-headers"
     * <li>"text/parityfec"
     * </ul>
     * For "text/&lt;other&gt;" MIME types, the first time the JRE needs to
     * determine whether the MIME type supports the charset parameter, it will
     * check whether the parameter is explicitly listed in an arbitrarily
     * chosen <code>DataFlavor</code> which uses that MIME type. If so, the JRE
     * will assume from that point on that the MIME type supports the charset
     * parameter and will not check again. If the parameter is not explicitly
     * listed, the JRE will assume from that point on that the MIME type does
     * not support the charset parameter and will not check again. Because
     * this check is performed on an arbitrarily chosen
     * <code>DataFlavor</code>, developers must ensure that all
     * <code>DataFlavor</code>s with a "text/&lt;other&gt;" MIME type specify
     * the charset parameter if it is supported by that MIME type. Developers
     * should never rely on the JRE to substitute the platform's default
     * charset for a "text/&lt;other&gt;" DataFlavor. Failure to adhere to this
     * restriction will lead to undefined behavior.
     * <p>
     * If the best MIME type in the array does not support the charset
     * parameter, the flavors which share that MIME type will then be sorted by
     * their representation classes in the following order:
     * <code>java.io.InputStream</code>, <code>java.nio.ByteBuffer</code>,
     * <code>[B</code>, &lt;all others&gt;.
     * <p>
     * If two or more flavors share the best representation class, or if no
     * flavor has one of the three specified representations, then one of those
     * flavors will be chosen non-deterministically.
     * <p>
     * If the best MIME type in the array does support the charset parameter,
     * the flavors which share that MIME type will then be sorted by their
     * representation classes in the following order:
     * <code>java.io.Reader</code>, <code>java.lang.String</code>,
     * <code>java.nio.CharBuffer</code>, <code>[C</code>, &lt;all others&gt;.
     * <p>
     * If two or more flavors share the best representation class, and that
     * representation is one of the four explicitly listed, then one of those
     * flavors will be chosen non-deterministically. If, however, no flavor has
     * one of the four specified representations, the flavors will then be
     * sorted by their charsets. Unicode charsets, such as "UTF-16", "UTF-8",
     * "UTF-16BE", "UTF-16LE", and their aliases, are considered best. After
     * them, the platform default charset and its aliases are selected.
     * "US-ASCII" and its aliases are worst. All other charsets are chosen in
     * alphabetical order, but only charsets supported by this implementation
     * of the Java platform will be considered.
     * <p>
     * If two or more flavors share the best charset, the flavors will then
     * again be sorted by their representation classes in the following order:
     * <code>java.io.InputStream</code>, <code>java.nio.ByteBuffer</code>,
     * <code>[B</code>, &lt;all others&gt;.
     * <p>
     * If two or more flavors share the best representation class, or if no
     * flavor has one of the three specified representations, then one of those
     * flavors will be chosen non-deterministically.
     *
     * @param availableFlavors an array of available <code>DataFlavor</code>s
     * @return the best (highest fidelity) flavor according to the rules
     *         specified above, or <code>null</code>,
     *         if <code>availableFlavors</code> is <code>null</code>,
     *         has zero length, or contains no text flavors
     * @since 1.3
     */
    public static final DataFlavor selectBestTextFlavor(
                                       DataFlavor[] availableFlavors) {
        if (availableFlavors == null || availableFlavors.length == 0) {
            return null;
        }

        if (textFlavorComparator == null) {
            textFlavorComparator = new TextFlavorComparator();
        }

        DataFlavor bestFlavor =
            (DataFlavor)Collections.max(Arrays.asList(availableFlavors),
                                        textFlavorComparator);

        if (!bestFlavor.isFlavorTextType()) {
            return null;
        }

        return bestFlavor;
    }

    private static Comparator textFlavorComparator;

    static class TextFlavorComparator
        extends DataTransferer.DataFlavorComparator {

        /**
         * Compares two <code>DataFlavor</code> objects. Returns a negative
         * integer, zero, or a positive integer as the first
         * <code>DataFlavor</code> is worse than, equal to, or better than the
         * second.
         * <p>
         * <code>DataFlavor</code>s are ordered according to the rules outlined
         * for <code>selectBestTextFlavor</code>.
         *
         * @param obj1 the first <code>DataFlavor</code> to be compared
         * @param obj2 the second <code>DataFlavor</code> to be compared
         * @return a negative integer, zero, or a positive integer as the first
         *         argument is worse, equal to, or better than the second
         * @throws ClassCastException if either of the arguments is not an
         *         instance of <code>DataFlavor</code>
         * @throws NullPointerException if either of the arguments is
         *         <code>null</code>
         *
         * @see #selectBestTextFlavor
         */
        public int compare(Object obj1, Object obj2) {
            DataFlavor flavor1 = (DataFlavor)obj1;
            DataFlavor flavor2 = (DataFlavor)obj2;

            if (flavor1.isFlavorTextType()) {
                if (flavor2.isFlavorTextType()) {
                    return super.compare(obj1, obj2);
                } else {
                    return 1;
                }
            } else if (flavor2.isFlavorTextType()) {
                return -1;
            } else {
                return 0;
            }
        }
    }

    /**
     * Gets a Reader for a text flavor, decoded, if necessary, for the expected
     * charset (encoding). The supported representation classes are
     * <code>java.io.Reader</code>, <code>java.lang.String</code>,
     * <code>java.nio.CharBuffer</code>, <code>[C</code>,
     * <code>java.io.InputStream</code>, <code>java.nio.ByteBuffer</code>,
     * and <code>[B</code>.
     * <p>
     * Because text flavors which do not support the charset parameter are
     * encoded in a non-standard format, this method should not be called for
     * such flavors. However, in order to maintain backward-compatibility,
     * if this method is called for such a flavor, this method will treat the
     * flavor as though it supports the charset parameter and attempt to
     * decode it accordingly. See <code>selectBestTextFlavor</code> for a list
     * of text flavors which do not support the charset parameter.
     *
     * @param transferable the <code>Transferable</code> whose data will be
     *        requested in this flavor
     *
     * @return a <code>Reader</code> to read the <code>Transferable</code>'s
     *         data
     *
     * @exception IllegalArgumentException if the representation class
     *            is not one of the seven listed above
     * @exception IllegalArgumentException if the <code>Transferable</code>
     *            has <code>null</code> data
     * @exception NullPointerException if the <code>Transferable</code> is
     *            <code>null</code>
     * @exception UnsupportedEncodingException if this flavor's representation
     *            is <code>java.io.InputStream</code>,
     *            <code>java.nio.ByteBuffer</code>, or <code>[B</code> and
     *            this flavor's encoding is not supported by this
     *            implementation of the Java platform
     * @exception UnsupportedFlavorException if the <code>Transferable</code>
     *            does not support this flavor
     * @exception IOException if the data cannot be read because of an
     *            I/O error
     * @see #selectBestTextFlavor
     * @since 1.3
     */
    public Reader getReaderForText(Transferable transferable)
        throws UnsupportedFlavorException, IOException
    {
        Object transferObject = transferable.getTransferData(this);
        if (transferObject == null) {
            throw new IllegalArgumentException
                ("getTransferData() returned null");
        }

        if (transferObject instanceof Reader) {
            return (Reader)transferObject;
        } else if (transferObject instanceof String) {
            return new StringReader((String)transferObject);
        } else if (transferObject instanceof CharBuffer) {
            CharBuffer buffer = (CharBuffer)transferObject;
            int size = buffer.remaining();
            char[] chars = new char[size];
            buffer.get(chars, 0, size);
            return new CharArrayReader(chars);
        } else if (transferObject instanceof char[]) {
            return new CharArrayReader((char[])transferObject);
        }

        InputStream stream = null;

        if (transferObject instanceof InputStream) {
            stream = (InputStream)transferObject;
        } else if (transferObject instanceof ByteBuffer) {
            ByteBuffer buffer = (ByteBuffer)transferObject;
            int size = buffer.remaining();
            byte[] bytes = new byte[size];
            buffer.get(bytes, 0, size);
            stream = new ByteArrayInputStream(bytes);
        } else if (transferObject instanceof byte[]) {
            stream = new ByteArrayInputStream((byte[])transferObject);
        }

        if (stream == null) {
            throw new IllegalArgumentException("transfer data is not Reader, String, CharBuffer, char array, InputStream, ByteBuffer, or byte array");
        }

        String encoding = getParameter("charset");
        return (encoding == null)
            ? new InputStreamReader(stream)
            : new InputStreamReader(stream, encoding);
    }

    /**
     * Returns the MIME type string for this <code>DataFlavor</code>.
     * @return the MIME type string for this flavor
     */
    public String getMimeType() {
        return (mimeType != null) ? mimeType.toString() : null;
    }

    /**
     * Returns the <code>Class</code> which objects supporting this
     * <code>DataFlavor</code> will return when this <code>DataFlavor</code>
     * is requested.
     * @return the <code>Class</code> which objects supporting this
     * <code>DataFlavor</code> will return when this <code>DataFlavor</code>
     * is requested
     */
    public Class<?> getRepresentationClass() {
        return representationClass;
    }

    /**
     * Returns the human presentable name for the data format that this
     * <code>DataFlavor</code> represents.  This name would be localized
     * for different countries.
     * @return the human presentable name for the data format that this
     *    <code>DataFlavor</code> represents
     */
    public String getHumanPresentableName() {
        return humanPresentableName;
    }

    /**
     * Returns the primary MIME type for this <code>DataFlavor</code>.
     * @return the primary MIME type of this <code>DataFlavor</code>
     */
    public String getPrimaryType() {
        return (mimeType != null) ? mimeType.getPrimaryType() : null;
    }

    /**
     * Returns the sub MIME type of this <code>DataFlavor</code>.
     * @return the Sub MIME type of this <code>DataFlavor</code>
     */
    public String getSubType() {
        return (mimeType != null) ? mimeType.getSubType() : null;
    }

    /**
     * Returns the human presentable name for this <code>DataFlavor</code>
     * if <code>paramName</code> equals "humanPresentableName".  Otherwise
     * returns the MIME type value associated with <code>paramName</code>.
     *
     * @param paramName the parameter name requested
     * @return the value of the name parameter, or <code>null</code>
     *  if there is no associated value
     */
    public String getParameter(String paramName) {
        if (paramName.equals("humanPresentableName")) {
            return humanPresentableName;
        } else {
            return (mimeType != null)
                ? mimeType.getParameter(paramName) : null;
        }
    }

    /**
     * Sets the human presentable name for the data format that this
     * <code>DataFlavor</code> represents. This name would be localized
     * for different countries.
     * @param humanPresentableName the new human presentable name
     */
    public void setHumanPresentableName(String humanPresentableName) {
        this.humanPresentableName = humanPresentableName;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The equals comparison for the {@code DataFlavor} class is implemented
     * as follows: Two <code>DataFlavor</code>s are considered equal if and
     * only if their MIME primary type and subtype and representation class are
     * equal. Additionally, if the primary type is "text", the subtype denotes
     * a text flavor which supports the charset parameter, and the
     * representation class is not <code>java.io.Reader</code>,
     * <code>java.lang.String</code>, <code>java.nio.CharBuffer</code>, or
     * <code>[C</code>, the <code>charset</code> parameter must also be equal.
     * If a charset is not explicitly specified for one or both
     * <code>DataFlavor</code>s, the platform default encoding is assumed. See
     * <code>selectBestTextFlavor</code> for a list of text flavors which
     * support the charset parameter.
     *
     * @param o the <code>Object</code> to compare with <code>this</code>
     * @return <code>true</code> if <code>that</code> is equivalent to this
     *         <code>DataFlavor</code>; <code>false</code> otherwise
     * @see #selectBestTextFlavor
     */
    public boolean equals(Object o) {
        return ((o instanceof DataFlavor) && equals((DataFlavor)o));
    }

    /**
     * This method has the same behavior as {@link #equals(Object)}.
     * The only difference being that it takes a {@code DataFlavor} instance
     * as a parameter.
     *
     * @param that the <code>DataFlavor</code> to compare with
     *        <code>this</code>
     * @return <code>true</code> if <code>that</code> is equivalent to this
     *         <code>DataFlavor</code>; <code>false</code> otherwise
     * @see #selectBestTextFlavor
     */
    public boolean equals(DataFlavor that) {
        if (that == null) {
            return false;
        }
        if (this == that) {
            return true;
        }

        if (representationClass == null) {
            if (that.getRepresentationClass() != null) {
                return false;
            }
        } else {
            if (!representationClass.equals(that.getRepresentationClass())) {
                return false;
            }
        }

        if (mimeType == null) {
            if (that.mimeType != null) {
                return false;
            }
        } else {
            if (!mimeType.match(that.mimeType)) {
                return false;
            }

            if ("text".equals(getPrimaryType())) {
                if (DataTransferer.doesSubtypeSupportCharset(this) &&
                    representationClass != null &&
                    !(isRepresentationClassReader() ||
                        String.class.equals(representationClass) ||
                        isRepresentationClassCharBuffer() ||
                        DataTransferer.charArrayClass.equals(representationClass)))
                {
                    String thisCharset =
                        DataTransferer.canonicalName(getParameter("charset"));
                    String thatCharset =
                        DataTransferer.canonicalName(that.getParameter("charset"));
                    if (thisCharset == null) {
                        if (thatCharset != null) {
                            return false;
                        }
                    } else {
                        if (!thisCharset.equals(thatCharset)) {
                            return false;
                        }
                    }
                }

                if ("html".equals(getSubType()) &&
                        this.getParameter("document") != null )
                {
                   if (!this.getParameter("document").
                            equals(that.getParameter("document")))
                    {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Compares only the <code>mimeType</code> against the passed in
     * <code>String</code> and <code>representationClass</code> is
     * not considered in the comparison.
     *
     * If <code>representationClass</code> needs to be compared, then
     * <code>equals(new DataFlavor(s))</code> may be used.
     * @deprecated As inconsistent with <code>hashCode()</code> contract,
     *             use <code>isMimeTypeEqual(String)</code> instead.
     * @param s the {@code mimeType} to compare.
     * @return true if the String (MimeType) is equal; false otherwise or if
     *         {@code s} is {@code null}
     */
    @Deprecated
    public boolean equals(String s) {
        if (s == null || mimeType == null)
            return false;
        return isMimeTypeEqual(s);
    }

    /**
     * Returns hash code for this <code>DataFlavor</code>.
     * For two equal <code>DataFlavor</code>s, hash codes are equal.
     * For the <code>String</code>
     * that matches <code>DataFlavor.equals(String)</code>, it is not
     * guaranteed that <code>DataFlavor</code>'s hash code is equal
     * to the hash code of the <code>String</code>.
     *
     * @return a hash code for this <code>DataFlavor</code>
     */
    public int hashCode() {
        int total = 0;

        if (representationClass != null) {
            total += representationClass.hashCode();
        }

        if (mimeType != null) {
            String primaryType = mimeType.getPrimaryType();
            if (primaryType != null) {
                total += primaryType.hashCode();
            }

            // Do not add subType.hashCode() to the total. equals uses
            // MimeType.match which reports a match if one or both of the
            // subTypes is '*', regardless of the other subType.

            if ("text".equals(primaryType) &&
                DataTransferer.doesSubtypeSupportCharset(this) &&
                representationClass != null &&
                !(isRepresentationClassReader() ||
                  String.class.equals(representationClass) ||
                  isRepresentationClassCharBuffer() ||
                  DataTransferer.charArrayClass.equals
                  (representationClass)))
            {
                String charset =
                    DataTransferer.canonicalName(getParameter("charset"));
                if (charset != null) {
                    total += charset.hashCode();
                }
            }
        }

        return total;
    }

    /**
     * Identical to {@link #equals(DataFlavor)}.
     *
     * @param that the <code>DataFlavor</code> to compare with
     *        <code>this</code>
     * @return <code>true</code> if <code>that</code> is equivalent to this
     *         <code>DataFlavor</code>; <code>false</code> otherwise
     * @see #selectBestTextFlavor
     * @since 1.3
     */
    public boolean match(DataFlavor that) {
        return equals(that);
    }

    /**
     * Returns whether the string representation of the MIME type passed in
     * is equivalent to the MIME type of this <code>DataFlavor</code>.
     * Parameters are not included in the comparison.
     *
     * @param mimeType the string representation of the MIME type
     * @return true if the string representation of the MIME type passed in is
     *         equivalent to the MIME type of this <code>DataFlavor</code>;
     *         false otherwise
     * @throws NullPointerException if mimeType is <code>null</code>
     */
    public boolean isMimeTypeEqual(String mimeType) {
        // JCK Test DataFlavor0117: if 'mimeType' is null, throw NPE
        if (mimeType == null) {
            throw new NullPointerException("mimeType");
        }
        if (this.mimeType == null) {
            return false;
        }
        try {
            return this.mimeType.match(new MimeType(mimeType));
        } catch (MimeTypeParseException mtpe) {
            return false;
        }
    }

    /**
     * Compares the <code>mimeType</code> of two <code>DataFlavor</code>
     * objects. No parameters are considered.
     *
     * @param dataFlavor the <code>DataFlavor</code> to be compared
     * @return true if the <code>MimeType</code>s are equal,
     *  otherwise false
     */

    public final boolean isMimeTypeEqual(DataFlavor dataFlavor) {
        return isMimeTypeEqual(dataFlavor.mimeType);
    }

    /**
     * Compares the <code>mimeType</code> of two <code>DataFlavor</code>
     * objects.  No parameters are considered.
     *
     * @return true if the <code>MimeType</code>s are equal,
     *  otherwise false
     */

    private boolean isMimeTypeEqual(MimeType mtype) {
        if (this.mimeType == null) {
            return (mtype == null);
        }
        return mimeType.match(mtype);
    }

   /**
    * Does the <code>DataFlavor</code> represent a serialized object?
    */

    public boolean isMimeTypeSerializedObject() {
        return isMimeTypeEqual(javaSerializedObjectMimeType);
    }

    public final Class<?> getDefaultRepresentationClass() {
        return ioInputStreamClass;
    }

    public final String getDefaultRepresentationClassAsString() {
        return getDefaultRepresentationClass().getName();
    }

   /**
    * Does the <code>DataFlavor</code> represent a
    * <code>java.io.InputStream</code>?
    */

    public boolean isRepresentationClassInputStream() {
        return ioInputStreamClass.isAssignableFrom(representationClass);
    }

    /**
     * Returns whether the representation class for this
     * <code>DataFlavor</code> is <code>java.io.Reader</code> or a subclass
     * thereof.
     *
     * @since 1.4
     */
    public boolean isRepresentationClassReader() {
        return java.io.Reader.class.isAssignableFrom(representationClass);
    }

    /**
     * Returns whether the representation class for this
     * <code>DataFlavor</code> is <code>java.nio.CharBuffer</code> or a
     * subclass thereof.
     *
     * @since 1.4
     */
    public boolean isRepresentationClassCharBuffer() {
        return java.nio.CharBuffer.class.isAssignableFrom(representationClass);
    }

    /**
     * Returns whether the representation class for this
     * <code>DataFlavor</code> is <code>java.nio.ByteBuffer</code> or a
     * subclass thereof.
     *
     * @since 1.4
     */
    public boolean isRepresentationClassByteBuffer() {
        return java.nio.ByteBuffer.class.isAssignableFrom(representationClass);
    }

   /**
    * Returns true if the representation class can be serialized.
    * @return true if the representation class can be serialized
    */

    public boolean isRepresentationClassSerializable() {
        return java.io.Serializable.class.isAssignableFrom(representationClass);
    }

   /**
    * Returns true if the representation class is <code>Remote</code>.
    * @return true if the representation class is <code>Remote</code>
    */

    public boolean isRepresentationClassRemote() {
        return DataTransferer.isRemote(representationClass);
    }

   /**
    * Returns true if the <code>DataFlavor</code> specified represents
    * a serialized object.
    * @return true if the <code>DataFlavor</code> specified represents
    *   a Serialized Object
    */

    public boolean isFlavorSerializedObjectType() {
        return isRepresentationClassSerializable() && isMimeTypeEqual(javaSerializedObjectMimeType);
    }

    /**
     * Returns true if the <code>DataFlavor</code> specified represents
     * a remote object.
     * @return true if the <code>DataFlavor</code> specified represents
     *  a Remote Object
     */

    public boolean isFlavorRemoteObjectType() {
        return isRepresentationClassRemote()
            && isRepresentationClassSerializable()
            && isMimeTypeEqual(javaRemoteObjectMimeType);
    }


   /**
    * Returns true if the <code>DataFlavor</code> specified represents
    * a list of file objects.
    * @return true if the <code>DataFlavor</code> specified represents
    *   a List of File objects
    */

   public boolean isFlavorJavaFileListType() {
        if (mimeType == null || representationClass == null)
            return false;
        return java.util.List.class.isAssignableFrom(representationClass) &&
               mimeType.match(javaFileListFlavor.mimeType);

   }

    /**
     * Returns whether this <code>DataFlavor</code> is a valid text flavor for
     * this implementation of the Java platform. Only flavors equivalent to
     * <code>DataFlavor.stringFlavor</code> and <code>DataFlavor</code>s with
     * a primary MIME type of "text" can be valid text flavors.
     * <p>
     * If this flavor supports the charset parameter, it must be equivalent to
     * <code>DataFlavor.stringFlavor</code>, or its representation must be
     * <code>java.io.Reader</code>, <code>java.lang.String</code>,
     * <code>java.nio.CharBuffer</code>, <code>[C</code>,
     * <code>java.io.InputStream</code>, <code>java.nio.ByteBuffer</code>, or
     * <code>[B</code>. If the representation is
     * <code>java.io.InputStream</code>, <code>java.nio.ByteBuffer</code>, or
     * <code>[B</code>, then this flavor's <code>charset</code> parameter must
     * be supported by this implementation of the Java platform. If a charset
     * is not specified, then the platform default charset, which is always
     * supported, is assumed.
     * <p>
     * If this flavor does not support the charset parameter, its
     * representation must be <code>java.io.InputStream</code>,
     * <code>java.nio.ByteBuffer</code>, or <code>[B</code>.
     * <p>
     * See <code>selectBestTextFlavor</code> for a list of text flavors which
     * support the charset parameter.
     *
     * @return <code>true</code> if this <code>DataFlavor</code> is a valid
     *         text flavor as described above; <code>false</code> otherwise
     * @see #selectBestTextFlavor
     * @since 1.4
     */
    public boolean isFlavorTextType() {
        return (DataTransferer.isFlavorCharsetTextType(this) ||
                DataTransferer.isFlavorNoncharsetTextType(this));
    }

   /**
    * Serializes this <code>DataFlavor</code>.
    */

   public synchronized void writeExternal(ObjectOutput os) throws IOException {
       if (mimeType != null) {
           mimeType.setParameter("humanPresentableName", humanPresentableName);
           os.writeObject(mimeType);
           mimeType.removeParameter("humanPresentableName");
       } else {
           os.writeObject(null);
       }

       os.writeObject(representationClass);
   }

   /**
    * Restores this <code>DataFlavor</code> from a Serialized state.
    */

   public synchronized void readExternal(ObjectInput is) throws IOException , ClassNotFoundException {
       String rcn = null;
        mimeType = (MimeType)is.readObject();

        if (mimeType != null) {
            humanPresentableName =
                mimeType.getParameter("humanPresentableName");
            mimeType.removeParameter("humanPresentableName");
            rcn = mimeType.getParameter("class");
            if (rcn == null) {
                throw new IOException("no class parameter specified in: " +
                                      mimeType);
            }
        }

        try {
            representationClass = (Class)is.readObject();
        } catch (OptionalDataException ode) {
            if (!ode.eof || ode.length != 0) {
                throw ode;
            }
            // Ensure backward compatibility.
            // Old versions didn't write the representation class to the stream.
            if (rcn != null) {
                representationClass =
                    DataFlavor.tryToLoadClass(rcn, getClass().getClassLoader());
            }
        }
   }

   /**
    * Returns a clone of this <code>DataFlavor</code>.
    * @return a clone of this <code>DataFlavor</code>
    */

    public Object clone() throws CloneNotSupportedException {
        Object newObj = super.clone();
        if (mimeType != null) {
            ((DataFlavor)newObj).mimeType = (MimeType)mimeType.clone();
        }
        return newObj;
    } // clone()

   /**
    * Called on <code>DataFlavor</code> for every MIME Type parameter
    * to allow <code>DataFlavor</code> subclasses to handle special
    * parameters like the text/plain <code>charset</code>
    * parameters, whose values are case insensitive.  (MIME type parameter
    * values are supposed to be case sensitive.
    * <p>
    * This method is called for each parameter name/value pair and should
    * return the normalized representation of the <code>parameterValue</code>.
    *
    * This method is never invoked by this implementation from 1.1 onwards.
    *
    * @deprecated
    */
    @Deprecated
    protected String normalizeMimeTypeParameter(String parameterName, String parameterValue) {
        return parameterValue;
    }

   /**
    * Called for each MIME type string to give <code>DataFlavor</code> subtypes
    * the opportunity to change how the normalization of MIME types is
    * accomplished.  One possible use would be to add default
    * parameter/value pairs in cases where none are present in the MIME
    * type string passed in.
    *
    * This method is never invoked by this implementation from 1.1 onwards.
    *
    * @deprecated
    */
    @Deprecated
    protected String normalizeMimeType(String mimeType) {
        return mimeType;
    }

    /*
     * fields
     */

    /* placeholder for caching any platform-specific data for flavor */

    transient int       atom;

    /* Mime Type of DataFlavor */

    MimeType            mimeType;

    private String      humanPresentableName;

    /** Java class of objects this DataFlavor represents **/

    private Class       representationClass;

} // class DataFlavor
