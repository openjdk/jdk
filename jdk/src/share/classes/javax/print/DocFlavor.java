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

package javax.print;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;


/**
 * Class <code>DocFlavor</code> encapsulates an object that specifies the
 * format in which print data is supplied to a {@link DocPrintJob}.
 * "Doc" is a short, easy-to-pronounce term that means "a piece of print data."
 * The print data format, or "doc flavor", consists of two things:
 * <UL>
 * <LI>
 * <B>MIME type.</B> This is a Multipurpose Internet Mail Extensions (MIME)
 * media type (as defined in <A HREF="http://www.ietf.org/rfc/rfc2045.txt">RFC
 * 2045</A> and <A HREF="http://www.ietf.org/rfc/rfc2046.txt">RFC 2046</A>)
 * that specifies how the print data is to be interpreted.
 * The charset of text data should be the IANA MIME-preferred name, or its
 * canonical name if no preferred name is specified. Additionally a few
 * historical names supported by earlier versions of the Java platform may
 * be recognized.
 * See <a href="../../java/lang/package-summary.html#charenc">
 * character encodings</a> for more information on the character encodings
 * supported on the Java platform.
 *
 * <LI>
 * <B>Representation class name.</B> This specifies the fully-qualified name of
 * the class of the object from which the actual print data comes, as returned
 * by the {@link java.lang.Class#getName() Class.getName()} method.
 * (Thus the class name for <CODE>byte[]</CODE> is <CODE>"[B"</CODE>, for
 * <CODE>char[]</CODE> it is <CODE>"[C"</CODE>.)
 * </UL>
 * <P>
 * A <code>DocPrintJob</code> obtains its print data by means of interface
 * {@link Doc Doc}. A <code>Doc</code> object lets the <code>DocPrintJob</code>
 * determine the doc flavor the client can supply.  A <code>Doc</code> object
 * also lets the <code>DocPrintJob</code> obtain an instance of the doc flavor's
 * representation class, from which the <code>DocPrintJob</code> then obtains
 * the actual print data.
 *
 * <HR>
 * <H3>Client Formatted Print Data</H3>
 * There are two broad categories of print data, client formatted print data
 * and service formatted print data.
 * <P>
 * For <B>client formatted print data</B>, the client determines or knows the
 * print data format.
 * For example the client may have a JPEG encoded image, a URL for
 * HTML code, or a disk file containing plain text in some encoding,
 * possibly obtained from an external source, and
 * requires a way to describe the data format to the print service.
 * <p>
 * The doc flavor's representation class is a conduit for the JPS
 * <code>DocPrintJob</code> to obtain a sequence of characters or
 * bytes from the client. The
 * doc flavor's MIME type is one of the standard media types telling how to
 * interpret the sequence of characters or bytes. For a list of standard media
 * types, see the Internet Assigned Numbers Authority's (IANA's) <A
 * HREF="http://www.iana.org/assignments/media-types/">Media Types
 * Directory</A>. Interface {@link Doc Doc} provides two utility operations,
 * {@link Doc#getReaderForText() getReaderForText} and
 * {@link Doc#getStreamForBytes() getStreamForBytes()}, to help a
 * <code>Doc</code> object's client extract client formatted print data.
 * <P>
 * For client formatted print data, the print data representation class is
 * typically one of the following (although other representation classes are
 * permitted):
 * <UL>
 * <LI>
 * Character array (<CODE>char[]</CODE>) -- The print data consists of the
 * Unicode characters in the array.
 *
 * <LI>
 * <code>String</code>  --
 * The print data consists of the Unicode characters in the string.
 *
 * <LI>
 * Character stream ({@link java.io.Reader java.io.Reader})
 * -- The print data consists of the Unicode characters read from the stream
 * up to the end-of-stream.
 *
 * <LI>
 * Byte array (<CODE>byte[]</CODE>) -- The print data consists of the bytes in
 * the array. The bytes are encoded in the character set specified by the doc
 * flavor's MIME type. If the MIME type does not specify a character set, the
 * default character set is US-ASCII.
 *
 * <LI>
 * Byte stream ({@link java.io.InputStream java.io.InputStream}) --
 * The print data consists of the bytes read from the stream up to the
 * end-of-stream. The bytes are encoded in the character set specified by the
 * doc flavor's MIME type. If the MIME type does not specify a character set,
 * the default character set is US-ASCII.

 * <LI>
 * Uniform Resource Locator ({@link java.net.URL URL})
 * -- The print data consists of the bytes read from the URL location.
 * The bytes are encoded in the character set specified by the doc flavor's
 * MIME type. If the MIME type does not specify a character set, the default
 * character set is US-ASCII.
 * <P>
 * When the representation class is a URL, the print service itself accesses
 * and downloads the document directly from its URL address, without involving
 * the client. The service may be some form of network print service which
 * is executing in a different environment.
 * This means you should not use a URL print data flavor to print a
 * document at a restricted URL that the client can see but the printer cannot
 * see. This also means you should not use a URL print data flavor to print a
 * document stored in a local file that is not available at a URL
 * accessible independently of the client.
 * For example, a file that is not served up by an HTTP server or FTP server.
 * To print such documents, let the client open an input stream on the URL
 * or file and use an input stream data flavor.
 * </UL>
 *
 * <HR>
 * <h3>Default and Platform Encodings</h3>
 * <P>
 * For byte print data where the doc flavor's MIME type does not include a
 * <CODE>charset</CODE> parameter, the Java Print Service instance assumes the
 * US-ASCII character set by default. This is in accordance with
 * <A HREF="http://www.ietf.org/rfc/rfc2046.txt">RFC 2046</A>, which says the
 * default character set is US-ASCII. Note that US-ASCII is a subset of
 * UTF-8, so in the future this may be widened if a future RFC endorses
 * UTF-8 as the default in a compatible manner.
 * <p>
 * Also note that this is different than the behaviour of the Java runtime
 * when interpreting a stream of bytes as text data. That assumes the
 * default encoding for the user's locale. Thus, when spooling a file in local
 * encoding to a Java Print Service it is important to correctly specify
 * the encoding. Developers working in the English locales should
 * be particularly conscious of this, as their platform encoding corresponds
 * to the default mime charset. By this coincidence that particular
 * case may work without specifying the encoding of platform data.
 * <p>
 * Every instance of the Java virtual machine has a default character encoding
 * determined during virtual-machine startup and typically depends upon the
 * locale and charset being used by the underlying operating system.
 * In a distributed environment there is no guarantee that two VM share
 * the same default encoding. Thus clients which want to stream platform
 * encoded text data from the host platform to a Java Print Service instance
 * must explicitly declare the charset and not rely on defaults.
 * <p>
 * The preferred form is the official IANA primary name for an encoding.
 * Applications which stream text data should always specify the charset
 * in the mime type, which necessitates obtaining the encoding of the host
 * platform for data (eg files) stored in that platform's encoding.
 * A CharSet which corresponds to this and is suitable for use in a
 * mime-type for a DocFlavor can be obtained
 * from {@link DocFlavor#hostEncoding DocFlavor.hostEncoding}
 * This may not always be the primary IANA name but is guaranteed to be
 * understood by this VM.
 * For common flavors, the pre-defined *HOST DocFlavors may be used.
 * <p>
 * See <a href="../../java/lang/package-summary.html#charenc">
 * character encodings</a> for more information on the character encodings
 * supported on the Java platform.
 * <HR>
 * <h3>Recommended DocFlavors</h3>
 * <P>
 * The Java Print Service API does not define any mandatorily supported
 * DocFlavors.
 * However, here are some examples of MIME types that a Java Print Service
 * instance might support for client formatted print data.
 * Nested classes inside class DocFlavor declare predefined static
 * constant DocFlavor objects for these example doc flavors; class DocFlavor's
 * constructor can be used to create an arbitrary doc flavor.
 * <UL>
 * <LI>Preformatted text
 * <TABLE BORDER=1 CELLPADDING=0 CELLSPACING=0 SUMMARY="MIME-Types and their descriptions">
 * <TR>
 *  <TH>MIME-Type</TH><TH>Description</TH>
 * </TR>
 * <TR>
 * <TD><CODE>"text/plain"</CODE></TD>
 * <TD>Plain text in the default character set (US-ASCII)</TD>
 * </TR>
 * <TR>
 * <TD><CODE>"text/plain; charset=<I>xxx</I>"</CODE></TD>
 * <TD>Plain text in character set <I>xxx</I></TD>
 * </TR>
 * <TR>
 * <TD><CODE>"text/html"</CODE></TD>
 * <TD>HyperText Markup Language in the default character set (US-ASCII)</TD>
 * </TR>
 * <TR>
 * <TD><CODE>"text/html; charset=<I>xxx</I>"</CODE></TD>
 * <TD>HyperText Markup Language in character set <I>xxx</I></TD>
 * </TR>
 * </TABLE>
 * <P>
 * In general, preformatted text print data is provided either in a character
 * oriented representation class (character array, String, Reader) or in a
 * byte oriented representation class (byte array, InputStream, URL).
 *
 *  <LI>Preformatted page description language (PDL) documents
 *
 * <TABLE BORDER=1 CELLPADDING=0 CELLSPACING=0 SUMMARY="MIME-Types and their descriptions">
 * <TR>
 *  <TH>MIME-Type</TH><TH>Description</TH>
 * </TR>
 *<TR>
 * <TD><CODE>"application/pdf"</CODE></TD>
 * <TD>Portable Document Format document</TD>
 * </TR>
 * <TR>
 * <TD><CODE>"application/postscript"</CODE></TD>
 * <TD>PostScript document</TD>
 * </TR>
 * <TR>
 * <TD><CODE>"application/vnd.hp-PCL"</CODE></TD>
 * <TD>Printer Control Language document</TD>
 * </TR>
 * </TABLE>
 * <P>
 * In general, preformatted PDL print data is provided in a byte oriented
 * representation class (byte array, InputStream, URL).
 *
 *  <LI>Preformatted images
 *
 * <TABLE BORDER=1 CELLPADDING=0 CELLSPACING=0 SUMMARY="MIME-Types and their descriptions">
 * <TR>
 *  <TH>MIME-Type</TH><TH>Description</TH>
 * </TR>
 *
 * <TR>
 * <TD><CODE>"image/gif"</CODE></TD>
 * <TD>Graphics Interchange Format image</TD>
 * </TR>
 * <TR>
 * <TD><CODE>"image/jpeg"</CODE></TD>
 * <TD>Joint Photographic Experts Group image</TD>
 * </TR>
 * <TR>
 * <TD><CODE>"image/png"</CODE></TD>
 * <TD>Portable Network Graphics image</TD>
 * </TR>
 * </TABLE>
 * <P>
 * In general, preformatted image print data is provided in a byte oriented
 * representation class (byte array, InputStream, URL).
 *
 *  <LI>Preformatted autosense print data
 *
 * <TABLE BORDER=1 CELLPADDING=0 CELLSPACING=0 SUMMARY="MIME-Types and their descriptions">
 * <TR>
 *  <TH>MIME-Type</TH><TH>Description</TH>
 * </TR>
 *
 * <TR>
 * <TD><CODE>"application/octet-stream"</CODE></TD>
 * <TD>The print data format is unspecified (just an octet stream)</TD>
 * </TABLE>
 * <P>
 * The printer decides how to interpret the print data; the way this
 * "autosensing" works is implementation dependent. In general, preformatted
 * autosense print data is provided in a byte oriented representation class
 * (byte array, InputStream, URL).
 * </UL>
 *
 * <HR>
 * <H3>Service Formatted Print Data</H3>
 * <P>
 * For <B>service formatted print data</B>, the Java Print Service instance
 * determines the print data format. The doc flavor's representation class
 * denotes an interface whose methods the <code>DocPrintJob</code> invokes to
 * determine the content to be printed -- such as a renderable image
 * interface or a Java printable interface.
 * The doc flavor's MIME type is the special value
 * <CODE>"application/x-java-jvm-local-objectref"</CODE> indicating the client
 * will supply a reference to a Java object that implements the interface
 * named as the representation class.
 * This MIME type is just a placeholder; what's
 * important is the print data representation class.
 * <P>
 * For service formatted print data, the print data representation class is
 * typically one of the following (although other representation classes are
 * permitted). Nested classes inside class DocFlavor declare predefined static
 * constant DocFlavor objects for these example doc flavors; class DocFlavor's
 * constructor can be used to create an arbitrary doc flavor.
 * <UL>
 * <LI>
 * Renderable image object -- The client supplies an object that implements
 * interface
 * {@link java.awt.image.renderable.RenderableImage RenderableImage}. The
 * printer calls methods
 * in that interface to obtain the image to be printed.
 *
 * <LI>
 * Printable object -- The client supplies an object that implements interface
 * {@link java.awt.print.Printable Printable}.
 * The printer calls methods in that interface to obtain the pages to be
 * printed, one by one.
 * For each page, the printer supplies a graphics context, and whatever the
 * client draws in that graphics context gets printed.
 *
 * <LI>
 * Pageable object -- The client supplies an object that implements interface
 * {@link java.awt.print.Pageable Pageable}. The printer calls
 * methods in that interface to obtain the pages to be printed, one by one.
 * For each page, the printer supplies a graphics context, and whatever
 * the client draws in that graphics context gets printed.
 * </UL>
 *
 * <HR>
 *
 * <HR>
 * <H3>Pre-defined Doc Flavors</H3>
 * A Java Print Service instance is not <B><I>required</I></B> to support the
 * following print data formats and print data representation classes.  In
 * fact, a developer using this class should <b>never</b> assume that a
 * particular print service supports the document types corresponding to
 * these pre-defined doc flavors.  Always query the print service
 * to determine what doc flavors it supports.  However,
 * developers who have print services that support these doc flavors are
 * encouraged to refer to the predefined singleton instances created here.
 * <UL>
 * <LI>
 * Plain text print data provided through a byte stream. Specifically, the
 * following doc flavors are recommended to be supported:
 * <BR>&#183;&nbsp;&nbsp;
 * <CODE>("text/plain", "java.io.InputStream")</CODE>
 * <BR>&#183;&nbsp;&nbsp;
 * <CODE>("text/plain; charset=us-ascii", "java.io.InputStream")</CODE>
 * <BR>&#183;&nbsp;&nbsp;
 * <CODE>("text/plain; charset=utf-8", "java.io.InputStream")</CODE>
 *
 * <LI>
 * Renderable image objects. Specifically, the following doc flavor is
 * recommended to be supported:
 * <BR>&#183;&nbsp;&nbsp;
 * <CODE>("application/x-java-jvm-local-objectref", "java.awt.image.renderable.RenderableImage")</CODE>
 * </UL>
 * <P>
 * A Java Print Service instance is allowed to support any other doc flavors
 * (or none) in addition to the above mandatory ones, at the implementation's
 * choice.
 * <P>
 * Support for the above doc flavors is desirable so a printing client can rely
 * on being able to print on any JPS printer, regardless of which doc flavors
 * the printer supports. If the printer doesn't support the client's preferred
 * doc flavor, the client can at least print plain text, or the client can
 * convert its data to a renderable image and print the image.
 * <P>
 * Furthermore, every Java Print Service instance must fulfill these
 * requirements for processing plain text print data:
 * <UL>
 * <LI>
 * The character pair carriage return-line feed (CR-LF) means
 * "go to column 1 of the next line."
 * <LI>
 * A carriage return (CR) character standing by itself means
 * "go to column 1 of the next line."
 * <LI>
 * A line feed (LF) character standing by itself means
 * "go to column 1 of the next line."
 * <LI>
 * </UL>
 * <P>
 * The client must itself perform all plain text print data formatting not
 * addressed by the above requirements.
 *
 * <H3>Design Rationale</H3>
 * <P>
 * Class DocFlavor in package javax.print.data is similar to class
 * {@link java.awt.datatransfer.DataFlavor DataFlavor}. Class
 * <code>DataFlavor</code>
 * is not used in the Java Print Service (JPS) API
 * for three reasons which are all rooted in allowing the JPS API to be
 * shared by other print services APIs which may need to run on Java profiles
 * which do not include all of the Java Platform, Standard Edition.
 * <OL TYPE=1>
 * <LI>
 * The JPS API is designed to be used in Java profiles which do not support
 * AWT.
 *
 * <LI>
 * The implementation of class <code>java.awt.datatransfer.DataFlavor</code>
 * does not guarantee that equivalent data flavors will have the same
 * serialized representation. DocFlavor does, and can be used in services
 * which need this.
 *
 * <LI>
 * The implementation of class <code>java.awt.datatransfer.DataFlavor</code>
 * includes a human presentable name as part of the serialized representation.
 * This is not appropriate as part of a service matching constraint.
 * </OL>
 * <P>
 * Class DocFlavor's serialized representation uses the following
 * canonical form of a MIME type string. Thus, two doc flavors with MIME types
 * that are not identical but that are equivalent (that have the same
 * canonical form) may be considered equal.
 * <UL>
 * <LI> The media type, media subtype, and parameters are retained, but all
 *      comments and whitespace characters are discarded.
 * <LI> The media type, media subtype, and parameter names are converted to
 *      lowercase.
 * <LI> The parameter values retain their original case, except a charset
 *      parameter value for a text media type is converted to lowercase.
 * <LI> Quote characters surrounding parameter values are removed.
 * <LI> Quoting backslash characters inside parameter values are removed.
 * <LI> The parameters are arranged in ascending order of parameter name.
 * </UL>
 * <P>
 * Class DocFlavor's serialized representation also contains the
 * fully-qualified class <I>name</I> of the representation class
 * (a String object), rather than the representation class itself
 * (a Class object). This allows a client to examine the doc flavors a
 * Java Print Service instance supports without having
 * to load the representation classes, which may be problematic for
 * limited-resource clients.
 * <P>
 *
 * @author  Alan Kaminsky
 */
public class DocFlavor implements Serializable, Cloneable {

    private static final long serialVersionUID = -4512080796965449721L;

    /**
     * A String representing the host operating system encoding.
     * This will follow the conventions documented in
     * <a href="http://www.ietf.org/rfc/rfc2278.txt">
     * <i>RFC&nbsp;2278:&nbsp;IANA Charset Registration Procedures</i></a>
     * except where historical names are returned for compatibility with
     * previous versions of the Java platform.
     * The value returned from method is valid only for the VM which
     * returns it, for use in a DocFlavor.
     * This is the charset for all the "HOST" pre-defined DocFlavors in
     * the executing VM.
     */
    public static final String hostEncoding;

    static {
        hostEncoding =
            java.security.AccessController.doPrivileged(
                  new sun.security.action.GetPropertyAction("file.encoding"));
    }

    /**
     * MIME type.
     */
    private transient MimeType myMimeType;

    /**
     * Representation class name.
     * @serial
     */
    private String myClassName;

    /**
     * String value for this doc flavor. Computed when needed and cached.
     */
    private transient String myStringValue = null;


    /**
     * Constructs a new doc flavor object from the given MIME type and
     * representation class name. The given MIME type is converted into
     * canonical form and stored internally.
     *
     * @param  mimeType   MIME media type string.
     * @param  className  Fully-qualified representation class name.
     *
     * @exception  NullPointerException
     *     (unchecked exception) Thrown if <CODE>mimeType</CODE> is null or
     *     <CODE>className</CODE> is null.
     * @exception  IllegalArgumentException
     *     (unchecked exception) Thrown if <CODE>mimeType</CODE> does not
     *     obey the syntax for a MIME media type string.
     */
    public DocFlavor(String mimeType, String className) {
        if (className == null) {
            throw new NullPointerException();
        }
        myMimeType = new MimeType (mimeType);
        myClassName = className;
    }

    /**
     * Returns this doc flavor object's MIME type string based on the
     * canonical form. Each parameter value is enclosed in quotes.
     * @return the mime type
     */
    public String getMimeType() {
        return myMimeType.getMimeType();
    }

    /**
     * Returns this doc flavor object's media type (from the MIME type).
     * @return the media type
     */
    public String getMediaType() {
        return myMimeType.getMediaType();
    }

    /**
     * Returns this doc flavor object's media subtype (from the MIME type).
     * @return the media sub-type
     */
    public String getMediaSubtype() {
        return myMimeType.getMediaSubtype();
    }

    /**
     * Returns a <code>String</code> representing a MIME
     * parameter.
     * Mime types may include parameters which are usually optional.
     * The charset for text types is a commonly useful example.
     * This convenience method will return the value of the specified
     * parameter if one was specified in the mime type for this flavor.
     * <p>
     * @param paramName the name of the paramater. This name is internally
     * converted to the canonical lower case format before performing
     * the match.
     * @return String representing a mime parameter, or
     * null if that parameter is not in the mime type string.
     * @exception NullPointerException if paramName is null.
     */
    public String getParameter(String paramName) {
        return myMimeType.getParameterMap().get(paramName.toLowerCase());
    }

    /**
     * Returns the name of this doc flavor object's representation class.
     * @return the name of the representation class.
     */
    public String getRepresentationClassName() {
        return myClassName;
    }

    /**
     * Converts this <code>DocFlavor</code> to a string.
     *
     * @return  MIME type string based on the canonical form. Each parameter
     *          value is enclosed in quotes.
     *          A "class=" parameter is appended to the
     *          MIME type string to indicate the representation class name.
     */
    public String toString() {
        return getStringValue();
    }

    /**
     * Returns a hash code for this doc flavor object.
     */
    public int hashCode() {
        return getStringValue().hashCode();
    }

    /**
     * Determines if this doc flavor object is equal to the given object.
     * The two are equal if the given object is not null, is an instance
     * of <code>DocFlavor</code>, has a MIME type equivalent to this doc
     * flavor object's MIME type (that is, the MIME types have the same media
     * type, media subtype, and parameters), and has the same representation
     * class name as this doc flavor object. Thus, if two doc flavor objects'
     * MIME types are the same except for comments, they are considered equal.
     * However, two doc flavor objects with MIME types of "text/plain" and
     * "text/plain; charset=US-ASCII" are not considered equal, even though
     * they represent the same media type (because the default character
     * set for plain text is US-ASCII).
     *
     * @param  obj  Object to test.
     *
     * @return  True if this doc flavor object equals <CODE>obj</CODE>, false
     *          otherwise.
     */
    public boolean equals(Object obj) {
        return
            obj != null &&
            obj instanceof DocFlavor &&
            getStringValue().equals (((DocFlavor) obj).getStringValue());
    }

    /**
     * Returns this doc flavor object's string value.
     */
    private String getStringValue() {
        if (myStringValue == null) {
            myStringValue = myMimeType + "; class=\"" + myClassName + "\"";
        }
        return myStringValue;
    }

    /**
     * Write the instance to a stream (ie serialize the object).
     */
    private void writeObject(ObjectOutputStream s) throws IOException {

        s.defaultWriteObject();
        s.writeObject(myMimeType.getMimeType());
    }

    /**
     * Reconstitute an instance from a stream (that is, deserialize it).
     *
     * @serialData
     * The serialised form of a DocFlavor is the String naming the
     * representation class followed by the String representing the canonical
     * form of the mime type.
     */
    private void readObject(ObjectInputStream s)
        throws ClassNotFoundException, IOException {

        s.defaultReadObject();
        myMimeType = new MimeType((String)s.readObject());
    }

    /**
     * Class DocFlavor.BYTE_ARRAY provides predefined static constant
     * DocFlavor objects for example doc flavors using a byte array
     * (<CODE>byte[]</CODE>) as the print data representation class.
     * <P>
     *
     * @author  Alan Kaminsky
     */
    public static class BYTE_ARRAY extends DocFlavor {

        private static final long serialVersionUID = -9065578006593857475L;

        /**
         * Constructs a new doc flavor with the given MIME type and a print
         * data representation class name of <CODE>"[B"</CODE> (byte array).
         *
         * @param  mimeType   MIME media type string.
         *
         * @exception  NullPointerException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> is null.
         * @exception  IllegalArgumentException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> does not
         *     obey the syntax for a MIME media type string.
         */
        public BYTE_ARRAY (String mimeType) {
            super (mimeType, "[B");
        }

        /**
         * Doc flavor with MIME type = <CODE>"text/plain"</CODE>,
         * encoded in the host platform encoding.
         * See {@link DocFlavor#hostEncoding hostEncoding}
         * Print data representation class name =
         * <CODE>"[B"</CODE> (byte array).
         */
        public static final BYTE_ARRAY TEXT_PLAIN_HOST =
            new BYTE_ARRAY ("text/plain; charset="+hostEncoding);

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=utf-8"</CODE>,
         * print data representation class name = <CODE>"[B"</CODE> (byte
         * array).
         */
        public static final BYTE_ARRAY TEXT_PLAIN_UTF_8 =
            new BYTE_ARRAY ("text/plain; charset=utf-8");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=utf-16"</CODE>,
         * print data representation class name = <CODE>"[B"</CODE> (byte
         * array).
         */
        public static final BYTE_ARRAY TEXT_PLAIN_UTF_16 =
            new BYTE_ARRAY ("text/plain; charset=utf-16");


        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=utf-16be"</CODE>
         * (big-endian byte ordering),
         * print data representation class name = <CODE>"[B"</CODE> (byte
         * array).
         */
        public static final BYTE_ARRAY TEXT_PLAIN_UTF_16BE =
            new BYTE_ARRAY ("text/plain; charset=utf-16be");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=utf-16le"</CODE>
         * (little-endian byte ordering),
         * print data representation class name = <CODE>"[B"</CODE> (byte
         * array).
         */
        public static final BYTE_ARRAY TEXT_PLAIN_UTF_16LE =
            new BYTE_ARRAY ("text/plain; charset=utf-16le");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=us-ascii"</CODE>,
         * print data representation class name =
         * <CODE>"[B"</CODE> (byte array).
         */
        public static final BYTE_ARRAY TEXT_PLAIN_US_ASCII =
            new BYTE_ARRAY ("text/plain; charset=us-ascii");


        /**
         * Doc flavor with MIME type = <CODE>"text/html"</CODE>,
         * encoded in the host platform encoding.
         * See {@link DocFlavor#hostEncoding hostEncoding}
         * Print data representation class name =
         * <CODE>"[B"</CODE> (byte array).
         */
        public static final BYTE_ARRAY TEXT_HTML_HOST =
            new BYTE_ARRAY ("text/html; charset="+hostEncoding);

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=utf-8"</CODE>,
         * print data representation class name = <CODE>"[B"</CODE> (byte
         * array).
         */
        public static final BYTE_ARRAY TEXT_HTML_UTF_8 =
            new BYTE_ARRAY ("text/html; charset=utf-8");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=utf-16"</CODE>,
         * print data representation class name = <CODE>"[B"</CODE> (byte
         * array).
         */
        public static final BYTE_ARRAY TEXT_HTML_UTF_16 =
            new BYTE_ARRAY ("text/html; charset=utf-16");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=utf-16be"</CODE>
         * (big-endian byte ordering),
         * print data representation class name = <CODE>"[B"</CODE> (byte
         * array).
         */
        public static final BYTE_ARRAY TEXT_HTML_UTF_16BE =
            new BYTE_ARRAY ("text/html; charset=utf-16be");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=utf-16le"</CODE>
         * (little-endian byte ordering),
         * print data representation class name = <CODE>"[B"</CODE> (byte
         * array).
         */
        public static final BYTE_ARRAY TEXT_HTML_UTF_16LE =
            new BYTE_ARRAY ("text/html; charset=utf-16le");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=us-ascii"</CODE>,
         * print data representation class name =
         * <CODE>"[B"</CODE> (byte array).
         */
        public static final BYTE_ARRAY TEXT_HTML_US_ASCII =
            new BYTE_ARRAY ("text/html; charset=us-ascii");


        /**
         * Doc flavor with MIME type = <CODE>"application/pdf"</CODE>, print
         * data representation class name = <CODE>"[B"</CODE> (byte array).
         */
        public static final BYTE_ARRAY PDF = new BYTE_ARRAY ("application/pdf");

        /**
         * Doc flavor with MIME type = <CODE>"application/postscript"</CODE>,
         * print data representation class name = <CODE>"[B"</CODE> (byte
         * array).
         */
        public static final BYTE_ARRAY POSTSCRIPT =
            new BYTE_ARRAY ("application/postscript");

        /**
         * Doc flavor with MIME type = <CODE>"application/vnd.hp-PCL"</CODE>,
         * print data representation class name = <CODE>"[B"</CODE> (byte
         * array).
         */
        public static final BYTE_ARRAY PCL =
            new BYTE_ARRAY ("application/vnd.hp-PCL");

        /**
         * Doc flavor with MIME type = <CODE>"image/gif"</CODE>, print data
         * representation class name = <CODE>"[B"</CODE> (byte array).
         */
        public static final BYTE_ARRAY GIF = new BYTE_ARRAY ("image/gif");

        /**
         * Doc flavor with MIME type = <CODE>"image/jpeg"</CODE>, print data
         * representation class name = <CODE>"[B"</CODE> (byte array).
         */
        public static final BYTE_ARRAY JPEG = new BYTE_ARRAY ("image/jpeg");

        /**
         * Doc flavor with MIME type = <CODE>"image/png"</CODE>, print data
         * representation class name = <CODE>"[B"</CODE> (byte array).
         */
        public static final BYTE_ARRAY PNG = new BYTE_ARRAY ("image/png");

        /**
         * Doc flavor with MIME type =
         * <CODE>"application/octet-stream"</CODE>,
         * print data representation class name = <CODE>"[B"</CODE> (byte
         * array). The client must determine that data described
         * using this DocFlavor is valid for the printer.
         */
        public static final BYTE_ARRAY AUTOSENSE =
            new BYTE_ARRAY ("application/octet-stream");

    }

    /**
     * Class DocFlavor.INPUT_STREAM provides predefined static constant
     * DocFlavor objects for example doc flavors using a byte stream ({@link
     * java.io.InputStream java.io.InputStream}) as the print
     * data representation class.
     * <P>
     *
     * @author  Alan Kaminsky
     */
    public static class INPUT_STREAM extends DocFlavor {

        private static final long serialVersionUID = -7045842700749194127L;

        /**
         * Constructs a new doc flavor with the given MIME type and a print
         * data representation class name of
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         *
         * @param  mimeType   MIME media type string.
         *
         * @exception  NullPointerException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> is null.
         * @exception  IllegalArgumentException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> does not
         *     obey the syntax for a MIME media type string.
         */
        public INPUT_STREAM (String mimeType) {
            super (mimeType, "java.io.InputStream");
        }

        /**
         * Doc flavor with MIME type = <CODE>"text/plain"</CODE>,
         * encoded in the host platform encoding.
         * See {@link DocFlavor#hostEncoding hostEncoding}
         * Print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM TEXT_PLAIN_HOST =
            new INPUT_STREAM ("text/plain; charset="+hostEncoding);

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=utf-8"</CODE>,
         * print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM TEXT_PLAIN_UTF_8 =
            new INPUT_STREAM ("text/plain; charset=utf-8");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=utf-16"</CODE>,
         * print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM TEXT_PLAIN_UTF_16 =
            new INPUT_STREAM ("text/plain; charset=utf-16");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=utf-16be"</CODE>
         * (big-endian byte ordering),
         * print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM TEXT_PLAIN_UTF_16BE =
            new INPUT_STREAM ("text/plain; charset=utf-16be");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=utf-16le"</CODE>
         * (little-endian byte ordering),
         * print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM TEXT_PLAIN_UTF_16LE =
            new INPUT_STREAM ("text/plain; charset=utf-16le");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=us-ascii"</CODE>,
         * print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM TEXT_PLAIN_US_ASCII =
                new INPUT_STREAM ("text/plain; charset=us-ascii");

        /**
         * Doc flavor with MIME type = <CODE>"text/html"</CODE>,
         * encoded in the host platform encoding.
         * See {@link DocFlavor#hostEncoding hostEncoding}
         * Print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM TEXT_HTML_HOST =
            new INPUT_STREAM ("text/html; charset="+hostEncoding);

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=utf-8"</CODE>,
         * print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM TEXT_HTML_UTF_8 =
            new INPUT_STREAM ("text/html; charset=utf-8");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=utf-16"</CODE>,
         * print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM TEXT_HTML_UTF_16 =
            new INPUT_STREAM ("text/html; charset=utf-16");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=utf-16be"</CODE>
         * (big-endian byte ordering),
         * print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM TEXT_HTML_UTF_16BE =
            new INPUT_STREAM ("text/html; charset=utf-16be");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=utf-16le"</CODE>
         * (little-endian byte ordering),
         * print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM TEXT_HTML_UTF_16LE =
            new INPUT_STREAM ("text/html; charset=utf-16le");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=us-ascii"</CODE>,
         * print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM TEXT_HTML_US_ASCII =
            new INPUT_STREAM ("text/html; charset=us-ascii");


        /**
         * Doc flavor with MIME type = <CODE>"application/pdf"</CODE>, print
         * data representation class name = <CODE>"java.io.InputStream"</CODE>
         * (byte stream).
         */
        public static final INPUT_STREAM PDF = new INPUT_STREAM ("application/pdf");

        /**
         * Doc flavor with MIME type = <CODE>"application/postscript"</CODE>,
         * print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM POSTSCRIPT =
            new INPUT_STREAM ("application/postscript");

        /**
         * Doc flavor with MIME type = <CODE>"application/vnd.hp-PCL"</CODE>,
         * print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM PCL =
            new INPUT_STREAM ("application/vnd.hp-PCL");

        /**
         * Doc flavor with MIME type = <CODE>"image/gif"</CODE>, print data
         * representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM GIF = new INPUT_STREAM ("image/gif");

        /**
         * Doc flavor with MIME type = <CODE>"image/jpeg"</CODE>, print data
         * representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM JPEG = new INPUT_STREAM ("image/jpeg");

        /**
         * Doc flavor with MIME type = <CODE>"image/png"</CODE>, print data
         * representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         */
        public static final INPUT_STREAM PNG = new INPUT_STREAM ("image/png");

        /**
         * Doc flavor with MIME type =
         * <CODE>"application/octet-stream"</CODE>,
         * print data representation class name =
         * <CODE>"java.io.InputStream"</CODE> (byte stream).
         * The client must determine that data described
         * using this DocFlavor is valid for the printer.
         */
        public static final INPUT_STREAM AUTOSENSE =
            new INPUT_STREAM ("application/octet-stream");

    }

    /**
     * Class DocFlavor.URL provides predefined static constant DocFlavor
     * objects.
     * For example doc flavors using a Uniform Resource Locator ({@link
     * java.net.URL java.net.URL}) as the print data
     * representation  class.
     * <P>
     *
     * @author  Alan Kaminsky
     */
    public static class URL extends DocFlavor {
        private static final long serialVersionUID = 2936725788144902062L;

        /**
         * Constructs a new doc flavor with the given MIME type and a print
         * data representation class name of <CODE>"java.net.URL"</CODE>.
         *
         * @param  mimeType   MIME media type string.
         *
         * @exception  NullPointerException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> is null.
         * @exception  IllegalArgumentException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> does not
         *     obey the syntax for a MIME media type string.
         */
        public URL (String mimeType) {
            super (mimeType, "java.net.URL");
        }

        /**
         * Doc flavor with MIME type = <CODE>"text/plain"</CODE>,
         * encoded in the host platform encoding.
         * See {@link DocFlavor#hostEncoding hostEncoding}
         * Print data representation class name =
         * <CODE>"java.net.URL"</CODE> (byte stream).
         */
        public static final URL TEXT_PLAIN_HOST =
            new URL ("text/plain; charset="+hostEncoding);

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=utf-8"</CODE>,
         * print data representation class name =
         * <CODE>"java.net.URL"</CODE> (byte stream).
         */
        public static final URL TEXT_PLAIN_UTF_8 =
            new URL ("text/plain; charset=utf-8");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=utf-16"</CODE>,
         * print data representation class name =
         * <CODE>java.net.URL""</CODE> (byte stream).
         */
        public static final URL TEXT_PLAIN_UTF_16 =
            new URL ("text/plain; charset=utf-16");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=utf-16be"</CODE>
         * (big-endian byte ordering),
         * print data representation class name =
         * <CODE>"java.net.URL"</CODE> (byte stream).
         */
        public static final URL TEXT_PLAIN_UTF_16BE =
            new URL ("text/plain; charset=utf-16be");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=utf-16le"</CODE>
         * (little-endian byte ordering),
         * print data representation class name =
         * <CODE>"java.net.URL"</CODE> (byte stream).
         */
        public static final URL TEXT_PLAIN_UTF_16LE =
            new URL ("text/plain; charset=utf-16le");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/plain; charset=us-ascii"</CODE>,
         * print data representation class name =
         * <CODE>"java.net.URL"</CODE> (byte stream).
         */
        public static final URL TEXT_PLAIN_US_ASCII =
            new URL ("text/plain; charset=us-ascii");

        /**
         * Doc flavor with MIME type = <CODE>"text/html"</CODE>,
         * encoded in the host platform encoding.
         * See {@link DocFlavor#hostEncoding hostEncoding}
         * Print data representation class name =
         * <CODE>"java.net.URL"</CODE> (byte stream).
         */
        public static final URL TEXT_HTML_HOST =
            new URL ("text/html; charset="+hostEncoding);

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=utf-8"</CODE>,
         * print data representation class name =
         * <CODE>"java.net.URL"</CODE> (byte stream).
         */
        public static final URL TEXT_HTML_UTF_8 =
            new URL ("text/html; charset=utf-8");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=utf-16"</CODE>,
         * print data representation class name =
         * <CODE>"java.net.URL"</CODE> (byte stream).
         */
        public static final URL TEXT_HTML_UTF_16 =
            new URL ("text/html; charset=utf-16");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=utf-16be"</CODE>
         * (big-endian byte ordering),
         * print data representation class name =
         * <CODE>"java.net.URL"</CODE> (byte stream).
         */
        public static final URL TEXT_HTML_UTF_16BE =
            new URL ("text/html; charset=utf-16be");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=utf-16le"</CODE>
         * (little-endian byte ordering),
         * print data representation class name =
         * <CODE>"java.net.URL"</CODE> (byte stream).
         */
        public static final URL TEXT_HTML_UTF_16LE =
            new URL ("text/html; charset=utf-16le");

        /**
         * Doc flavor with MIME type =
         * <CODE>"text/html; charset=us-ascii"</CODE>,
         * print data representation class name =
         * <CODE>"java.net.URL"</CODE> (byte stream).
         */
        public static final URL TEXT_HTML_US_ASCII =
            new URL ("text/html; charset=us-ascii");


        /**
         * Doc flavor with MIME type = <CODE>"application/pdf"</CODE>, print
         * data representation class name = <CODE>"java.net.URL"</CODE>.
         */
        public static final URL PDF = new URL ("application/pdf");

        /**
         * Doc flavor with MIME type = <CODE>"application/postscript"</CODE>,
         * print data representation class name = <CODE>"java.net.URL"</CODE>.
         */
        public static final URL POSTSCRIPT = new URL ("application/postscript");

        /**
         * Doc flavor with MIME type = <CODE>"application/vnd.hp-PCL"</CODE>,
         * print data representation class name = <CODE>"java.net.URL"</CODE>.
         */
        public static final URL PCL = new URL ("application/vnd.hp-PCL");

        /**
         * Doc flavor with MIME type = <CODE>"image/gif"</CODE>, print data
         * representation class name = <CODE>"java.net.URL"</CODE>.
         */
        public static final URL GIF = new URL ("image/gif");

        /**
         * Doc flavor with MIME type = <CODE>"image/jpeg"</CODE>, print data
         * representation class name = <CODE>"java.net.URL"</CODE>.
         */
        public static final URL JPEG = new URL ("image/jpeg");

        /**
         * Doc flavor with MIME type = <CODE>"image/png"</CODE>, print data
         * representation class name = <CODE>"java.net.URL"</CODE>.
         */
        public static final URL PNG = new URL ("image/png");

        /**
         * Doc flavor with MIME type =
         * <CODE>"application/octet-stream"</CODE>,
         * print data representation class name = <CODE>"java.net.URL"</CODE>.
         *  The client must determine that data described
         * using this DocFlavor is valid for the printer.
         */
        public static final URL AUTOSENSE = new URL ("application/octet-stream");

    }

    /**
     * Class DocFlavor.CHAR_ARRAY provides predefined static constant
     * DocFlavor objects for example doc flavors using a character array
     * (<CODE>char[]</CODE>) as the print data representation class. As such,
     * the character set is Unicode.
     * <P>
     *
     * @author  Alan Kaminsky
     */
    public static class CHAR_ARRAY extends DocFlavor {

        private static final long serialVersionUID = -8720590903724405128L;

        /**
         * Constructs a new doc flavor with the given MIME type and a print
         * data representation class name of
         * <CODE>"[C"</CODE> (character array).
         *
         * @param  mimeType  MIME media type string. If it is a text media
         *                      type, it is assumed to contain a
         *                      <CODE>"charset=utf-16"</CODE> parameter.
         *
         * @exception  NullPointerException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> is null.
         * @exception  IllegalArgumentException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> does not
         *     obey the syntax for a MIME media type string.
         */
        public CHAR_ARRAY (String mimeType) {
            super (mimeType, "[C");
        }

        /**
         * Doc flavor with MIME type = <CODE>"text/plain;
         * charset=utf-16"</CODE>, print data representation class name =
         * <CODE>"[C"</CODE> (character array).
         */
        public static final CHAR_ARRAY TEXT_PLAIN =
            new CHAR_ARRAY ("text/plain; charset=utf-16");

        /**
         * Doc flavor with MIME type = <CODE>"text/html;
         * charset=utf-16"</CODE>, print data representation class name =
         * <CODE>"[C"</CODE> (character array).
         */
        public static final CHAR_ARRAY TEXT_HTML =
            new CHAR_ARRAY ("text/html; charset=utf-16");

    }

    /**
     * Class DocFlavor.STRING provides predefined static constant DocFlavor
     * objects for example doc flavors using a string ({@link java.lang.String
     * java.lang.String}) as the print data representation class.
     * As such, the character set is Unicode.
     * <P>
     *
     * @author  Alan Kaminsky
     */
    public static class STRING extends DocFlavor {

        private static final long serialVersionUID = 4414407504887034035L;

        /**
         * Constructs a new doc flavor with the given MIME type and a print
         * data representation class name of <CODE>"java.lang.String"</CODE>.
         *
         * @param  mimeType  MIME media type string. If it is a text media
         *                      type, it is assumed to contain a
         *                      <CODE>"charset=utf-16"</CODE> parameter.
         *
         * @exception  NullPointerException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> is null.
         * @exception  IllegalArgumentException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> does not
         *     obey the syntax for a MIME media type string.
         */
        public STRING (String mimeType) {
            super (mimeType, "java.lang.String");
        }

        /**
         * Doc flavor with MIME type = <CODE>"text/plain;
         * charset=utf-16"</CODE>, print data representation class name =
         * <CODE>"java.lang.String"</CODE>.
         */
        public static final STRING TEXT_PLAIN =
            new STRING ("text/plain; charset=utf-16");

        /**
         * Doc flavor with MIME type = <CODE>"text/html;
         * charset=utf-16"</CODE>, print data representation class name =
         * <CODE>"java.lang.String"</CODE>.
         */
        public static final STRING TEXT_HTML =
            new STRING ("text/html; charset=utf-16");
    }

    /**
     * Class DocFlavor.READER provides predefined static constant DocFlavor
     * objects for example doc flavors using a character stream ({@link
     * java.io.Reader java.io.Reader}) as the print data
     * representation class. As such, the character set is Unicode.
     * <P>
     *
     * @author  Alan Kaminsky
     */
    public static class READER extends DocFlavor {

        private static final long serialVersionUID = 7100295812579351567L;

        /**
         * Constructs a new doc flavor with the given MIME type and a print
         * data representation class name of\
         * <CODE>"java.io.Reader"</CODE> (character stream).
         *
         * @param  mimeType  MIME media type string. If it is a text media
         *                      type, it is assumed to contain a
         *                      <CODE>"charset=utf-16"</CODE> parameter.
         *
         * @exception  NullPointerException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> is null.
         * @exception  IllegalArgumentException
         *     (unchecked exception) Thrown if <CODE>mimeType</CODE> does not
         *     obey the syntax for a MIME media type string.
         */
        public READER (String mimeType) {
            super (mimeType, "java.io.Reader");
        }

        /**
         * Doc flavor with MIME type = <CODE>"text/plain;
         * charset=utf-16"</CODE>, print data representation class name =
         * <CODE>"java.io.Reader"</CODE> (character stream).
         */
        public static final READER TEXT_PLAIN =
            new READER ("text/plain; charset=utf-16");

        /**
         * Doc flavor with MIME type = <CODE>"text/html;
         * charset=utf-16"</CODE>, print data representation class name =
         * <CODE>"java.io.Reader"</CODE> (character stream).
         */
        public static final READER TEXT_HTML =
            new READER ("text/html; charset=utf-16");

    }

    /**
     * Class DocFlavor.SERVICE_FORMATTED provides predefined static constant
     * DocFlavor objects for example doc flavors for service formatted print
     * data.
     * <P>
     *
     * @author  Alan Kaminsky
     */
    public static class SERVICE_FORMATTED extends DocFlavor {

        private static final long serialVersionUID = 6181337766266637256L;

        /**
         * Constructs a new doc flavor with a MIME type of
         * <CODE>"application/x-java-jvm-local-objectref"</CODE> indicating
         * service formatted print data and the given print data
         * representation class name.
         *
         * @param  className  Fully-qualified representation class name.
         *
         * @exception  NullPointerException
         *     (unchecked exception) Thrown if <CODE>className</CODE> is
         *     null.
         */
        public SERVICE_FORMATTED (String className) {
            super ("application/x-java-jvm-local-objectref", className);
        }

        /**
         * Service formatted print data doc flavor with print data
         * representation class name =
         * <CODE>"java.awt.image.renderable.RenderableImage"</CODE>
         * (renderable image object).
         */
        public static final SERVICE_FORMATTED RENDERABLE_IMAGE =
            new SERVICE_FORMATTED("java.awt.image.renderable.RenderableImage");

        /**
         * Service formatted print data doc flavor with print data
         * representation class name = <CODE>"java.awt.print.Printable"</CODE>
         * (printable object).
         */
        public static final SERVICE_FORMATTED PRINTABLE =
            new SERVICE_FORMATTED ("java.awt.print.Printable");

        /**
         * Service formatted print data doc flavor with print data
         * representation class name = <CODE>"java.awt.print.Pageable"</CODE>
         * (pageable object).
         */
        public static final SERVICE_FORMATTED PAGEABLE =
            new SERVICE_FORMATTED ("java.awt.print.Pageable");

        }

}
