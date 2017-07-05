/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.mimepull;

import java.io.*;


/**
 * This is a utility class that provides various MIME related
 * functionality. <p>
 *
 * There are a set of methods to encode and decode MIME headers as
 * per RFC 2047.  Note that, in general, these methods are
 * <strong>not</strong> needed when using methods such as
 * <code>setSubject</code> and <code>setRecipients</code>; JavaMail
 * will automatically encode and decode data when using these "higher
 * level" methods.  The methods below are only needed when maniuplating
 * raw MIME headers using <code>setHeader</code> and <code>getHeader</code>
 * methods.  A brief description on handling such headers is given below: <p>
 *
 * RFC 822 mail headers <strong>must</strong> contain only US-ASCII
 * characters. Headers that contain non US-ASCII characters must be
 * encoded so that they contain only US-ASCII characters. Basically,
 * this process involves using either BASE64 or QP to encode certain
 * characters. RFC 2047 describes this in detail. <p>
 *
 * In Java, Strings contain (16 bit) Unicode characters. ASCII is a
 * subset of Unicode (and occupies the range 0 - 127). A String
 * that contains only ASCII characters is already mail-safe. If the
 * String contains non US-ASCII characters, it must be encoded. An
 * additional complexity in this step is that since Unicode is not
 * yet a widely used charset, one might want to first charset-encode
 * the String into another charset and then do the transfer-encoding.
 * <p>
 * Note that to get the actual bytes of a mail-safe String (say,
 * for sending over SMTP), one must do
 * <p><blockquote><pre>
 *
 *      byte[] bytes = string.getBytes("iso-8859-1");
 *
 * </pre></blockquote><p>
 *
 * The <code>setHeader</code> and <code>addHeader</code> methods
 * on MimeMessage and MimeBodyPart assume that the given header values
 * are Unicode strings that contain only US-ASCII characters. Hence
 * the callers of those methods must insure that the values they pass
 * do not contain non US-ASCII characters. The methods in this class
 * help do this. <p>
 *
 * The <code>getHeader</code> family of methods on MimeMessage and
 * MimeBodyPart return the raw header value. These might be encoded
 * as per RFC 2047, and if so, must be decoded into Unicode Strings.
 * The methods in this class help to do this. <p>
 *
 * Several System properties control strict conformance to the MIME
 * spec.  Note that these are not session properties but must be set
 * globally as System properties. <p>
 *
 * The <code>mail.mime.decodetext.strict</code> property controls
 * decoding of MIME encoded words.  The MIME spec requires that encoded
 * words start at the beginning of a whitespace separated word.  Some
 * mailers incorrectly include encoded words in the middle of a word.
 * If the <code>mail.mime.decodetext.strict</code> System property is
 * set to <code>"false"</code>, an attempt will be made to decode these
 * illegal encoded words. The default is true. <p>
 *
 * The <code>mail.mime.encodeeol.strict</code> property controls the
 * choice of Content-Transfer-Encoding for MIME parts that are not of
 * type "text".  Often such parts will contain textual data for which
 * an encoding that allows normal end of line conventions is appropriate.
 * In rare cases, such a part will appear to contain entirely textual
 * data, but will require an encoding that preserves CR and LF characters
 * without change.  If the <code>mail.mime.encodeeol.strict</code>
 * System property is set to <code>"true"</code>, such an encoding will
 * be used when necessary.  The default is false. <p>
 *
 * In addition, the <code>mail.mime.charset</code> System property can
 * be used to specify the default MIME charset to use for encoded words
 * and text parts that don't otherwise specify a charset.  Normally, the
 * default MIME charset is derived from the default Java charset, as
 * specified in the <code>file.encoding</code> System property.  Most
 * applications will have no need to explicitly set the default MIME
 * charset.  In cases where the default MIME charset to be used for
 * mail messages is different than the charset used for files stored on
 * the system, this property should be set. <p>
 *
 * The current implementation also supports the following System property.
 * <p>
 * The <code>mail.mime.ignoreunknownencoding</code> property controls
 * whether unknown values in the <code>Content-Transfer-Encoding</code>
 * header, as passed to the <code>decode</code> method, cause an exception.
 * If set to <code>"true"</code>, unknown values are ignored and 8bit
 * encoding is assumed.  Otherwise, unknown values cause a MessagingException
 * to be thrown.
 *
 * @author  John Mani
 * @author  Bill Shannon
 */

/* FROM mail.jar */
final class MimeUtility {

    // This class cannot be instantiated
    private MimeUtility() { }

    private static final boolean ignoreUnknownEncoding =
        PropUtil.getBooleanSystemProperty(
            "mail.mime.ignoreunknownencoding", false);

    /**
     * Decode the given input stream. The Input stream returned is
     * the decoded input stream. All the encodings defined in RFC 2045
     * are supported here. They include "base64", "quoted-printable",
     * "7bit", "8bit", and "binary". In addition, "uuencode" is also
     * supported. <p>
     *
     * In the current implementation, if the
     * <code>mail.mime.ignoreunknownencoding</code> system property is set to
     * <code>"true"</code>, unknown encoding values are ignored and the
     * original InputStream is returned.
     *
     * @param   is              input stream
     * @param   encoding        the encoding of the stream.
     * @return                  decoded input stream.
     * @exception MessagingException    if the encoding is unknown
     */
    public static InputStream decode(InputStream is, String encoding)
                throws DecodingException {
        if (encoding.equalsIgnoreCase("base64"))
            return new BASE64DecoderStream(is);
        else if (encoding.equalsIgnoreCase("quoted-printable"))
            return new QPDecoderStream(is);
        else if (encoding.equalsIgnoreCase("uuencode") ||
                 encoding.equalsIgnoreCase("x-uuencode") ||
                 encoding.equalsIgnoreCase("x-uue"))
            return new UUDecoderStream(is);
        else if (encoding.equalsIgnoreCase("binary") ||
                 encoding.equalsIgnoreCase("7bit") ||
                 encoding.equalsIgnoreCase("8bit"))
            return is;
        else {
            if (!ignoreUnknownEncoding) {
                throw new DecodingException("Unknown encoding: " + encoding);
            }
            return is;
        }
    }
}
