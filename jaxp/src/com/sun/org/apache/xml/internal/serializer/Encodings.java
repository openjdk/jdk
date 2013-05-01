/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: Encodings.java,v 1.3 2005/09/28 13:49:04 pvedula Exp $
 */
package com.sun.org.apache.xml.internal.serializer;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.io.BufferedWriter;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;
import java.util.StringTokenizer;

import com.sun.org.apache.xalan.internal.utils.SecuritySupport;

/**
 * Provides information about encodings. Depends on the Java runtime
 * to provides writers for the different encodings, but can be used
 * to override encoding names and provide the last printable character
 * for each encoding.
 *
 * @version $Revision: 1.11 $ $Date: 2010-11-01 04:34:44 $
 * @author <a href="mailto:arkin@intalio.com">Assaf Arkin</a>
 */

public final class Encodings extends Object
{

    /**
     * The last printable character for unknown encodings.
     */
    private static final int m_defaultLastPrintable = 0x7F;

    /**
     * Standard filename for properties file with encodings data.
     */
    private static final String ENCODINGS_FILE = "com/sun/org/apache/xml/internal/serializer/Encodings.properties";

    /**
     * Standard filename for properties file with encodings data.
     */
    private static final String ENCODINGS_PROP = "com.sun.org.apache.xalan.internal.serialize.encodings";


    /**
     * Returns a writer for the specified encoding based on
     * an output stream.
     *
     * @param output The output stream
     * @param encoding The encoding
     * @return A suitable writer
     * @throws UnsupportedEncodingException There is no convertor
     *  to support this encoding
     */
    static Writer getWriter(OutputStream output, String encoding)
        throws UnsupportedEncodingException
    {

        for (int i = 0; i < _encodings.length; ++i)
        {
            if (_encodings[i].name.equalsIgnoreCase(encoding))
            {
                try
                {
                    return new BufferedWriter(new OutputStreamWriter(
                        output,
                        _encodings[i].javaName));
                }
                catch (java.lang.IllegalArgumentException iae) // java 1.1.8
                {
                    // keep trying
                }
                catch (UnsupportedEncodingException usee)
                {

                    // keep trying
                }
            }
        }

        try
        {
            return new BufferedWriter(new OutputStreamWriter(output, encoding));
        }
        catch (java.lang.IllegalArgumentException iae) // java 1.1.8
        {
            throw new UnsupportedEncodingException(encoding);
        }
    }


    /**
     * Returns the last printable character for an unspecified
     * encoding.
     *
     * @return the default size
     */
    public static int getLastPrintable()
    {
        return m_defaultLastPrintable;
    }



    /**
     * Returns the EncodingInfo object for the specified
     * encoding.
     * <p>
     * This is not a public API.
     *
     * @param encoding The encoding
     * @return The object that is used to determine if
     * characters are in the given encoding.
     * @xsl.usage internal
     */
    static EncodingInfo getEncodingInfo(String encoding)
    {
        EncodingInfo ei;

        String normalizedEncoding = toUpperCaseFast(encoding);
        ei = (EncodingInfo) _encodingTableKeyJava.get(normalizedEncoding);
        if (ei == null)
            ei = (EncodingInfo) _encodingTableKeyMime.get(normalizedEncoding);
        if (ei == null) {
            // We shouldn't have to do this, but just in case.
            ei = new EncodingInfo(null,null);
        }

        return ei;
    }

    /**
     * A fast and cheap way to uppercase a String that is
     * only made of printable ASCII characters.
     * <p>
     * This is not a public API.
     * @param s a String of ASCII characters
     * @return an uppercased version of the input String,
     * possibly the same String.
     * @xsl.usage internal
     */
    static private String toUpperCaseFast(final String s) {

        boolean different = false;
        final int mx = s.length();
                char[] chars = new char[mx];
        for (int i=0; i < mx; i++) {
                char ch = s.charAt(i);
            // is the character a lower case ASCII one?
                if ('a' <= ch && ch <= 'z') {
                // a cheap and fast way to uppercase that is good enough
                        ch = (char) (ch + ('A' - 'a'));
                        different = true; // the uppercased String is different
                }
                chars[i] = ch;
        }

        // A little optimization, don't call String.valueOf() if
        // the uppercased string is the same as the input string.
        final String upper;
        if (different)
                upper = String.valueOf(chars);
        else
                upper = s;

        return upper;
    }

    /** The default encoding, ISO style, ISO style.   */
    static final String DEFAULT_MIME_ENCODING = "UTF-8";

    /**
     * Get the proper mime encoding.  From the XSLT recommendation: "The encoding
     * attribute specifies the preferred encoding to use for outputting the result
     * tree. XSLT processors are required to respect values of UTF-8 and UTF-16.
     * For other values, if the XSLT processor does not support the specified
     * encoding it may signal an error; if it does not signal an error it should
     * use UTF-8 or UTF-16 instead. The XSLT processor must not use an encoding
     * whose name does not match the EncName production of the XML Recommendation
     * [XML]. If no encoding attribute is specified, then the XSLT processor should
     * use either UTF-8 or UTF-16."
     *
     * @param encoding Reference to java-style encoding string, which may be null,
     * in which case a default will be found.
     *
     * @return The ISO-style encoding string, or null if failure.
     */
    static String getMimeEncoding(String encoding)
    {

        if (null == encoding)
        {
            try
            {

                // Get the default system character encoding.  This may be
                // incorrect if they passed in a writer, but right now there
                // seems to be no way to get the encoding from a writer.
                encoding = SecuritySupport.getSystemProperty("file.encoding", "UTF8");

                if (null != encoding)
                {

                    /*
                    * See if the mime type is equal to UTF8.  If you don't
                    * do that, then  convertJava2MimeEncoding will convert
                    * 8859_1 to "ISO-8859-1", which is not what we want,
                    * I think, and I don't think I want to alter the tables
                    * to convert everything to UTF-8.
                    */
                    String jencoding =
                        (encoding.equalsIgnoreCase("Cp1252")
                            || encoding.equalsIgnoreCase("ISO8859_1")
                            || encoding.equalsIgnoreCase("8859_1")
                            || encoding.equalsIgnoreCase("UTF8"))
                            ? DEFAULT_MIME_ENCODING
                            : convertJava2MimeEncoding(encoding);

                    encoding =
                        (null != jencoding) ? jencoding : DEFAULT_MIME_ENCODING;
                }
                else
                {
                    encoding = DEFAULT_MIME_ENCODING;
                }
            }
            catch (SecurityException se)
            {
                encoding = DEFAULT_MIME_ENCODING;
            }
        }
        else
        {
            encoding = convertJava2MimeEncoding(encoding);
        }

        return encoding;
    }

    /**
     * Try the best we can to convert a Java encoding to a XML-style encoding.
     *
     * @param encoding non-null reference to encoding string, java style.
     *
     * @return ISO-style encoding string.
     */
    private static String convertJava2MimeEncoding(String encoding)
    {
        EncodingInfo enc =
            (EncodingInfo) _encodingTableKeyJava.get(encoding.toUpperCase());
        if (null != enc)
            return enc.name;
        return encoding;
    }

    /**
     * Try the best we can to convert a Java encoding to a XML-style encoding.
     *
     * @param encoding non-null reference to encoding string, java style.
     *
     * @return ISO-style encoding string.
     */
    public static String convertMime2JavaEncoding(String encoding)
    {

        for (int i = 0; i < _encodings.length; ++i)
        {
            if (_encodings[i].name.equalsIgnoreCase(encoding))
            {
                return _encodings[i].javaName;
            }
        }

        return encoding;
    }

    /**
     * Load a list of all the supported encodings.
     *
     * System property "encodings" formatted using URL syntax may define an
     * external encodings list. Thanks to Sergey Ushakov for the code
     * contribution!
     */
    private static EncodingInfo[] loadEncodingInfo()
    {
        try
        {
            String urlString = null;
            InputStream is = null;

            try
            {
                urlString = SecuritySupport.getSystemProperty(ENCODINGS_PROP, "");
            }
            catch (SecurityException e)
            {
            }

            if (urlString != null && urlString.length() > 0) {
                URL url = new URL(urlString);
                is = url.openStream();
            }

            if (is == null) {
                is = SecuritySupport.getResourceAsStream(ENCODINGS_FILE);
            }

            Properties props = new Properties();
            if (is != null) {
                props.load(is);
                is.close();
            } else {
                // Seems to be no real need to force failure here, let the
                // system do its best... The issue is not really very critical,
                // and the output will be in any case _correct_ though maybe not
                // always human-friendly... :)
                // But maybe report/log the resource problem?
                // Any standard ways to report/log errors (in static context)?
            }

            int totalEntries = props.size();
            int totalMimeNames = 0;
            Enumeration keys = props.keys();
            for (int i = 0; i < totalEntries; ++i)
            {
                String javaName = (String) keys.nextElement();
                String val = props.getProperty(javaName);
                totalMimeNames++;
                int pos = val.indexOf(' ');
                for (int j = 0; j < pos; ++j)
                    if (val.charAt(j) == ',')
                        totalMimeNames++;
            }
            EncodingInfo[] ret = new EncodingInfo[totalMimeNames];
            int j = 0;
            keys = props.keys();
            for (int i = 0; i < totalEntries; ++i)
            {
                String javaName = (String) keys.nextElement();
                String val = props.getProperty(javaName);
                int pos = val.indexOf(' ');
                String mimeName;
                //int lastPrintable;
                if (pos < 0)
                {
                    // Maybe report/log this problem?
                    //  "Last printable character not defined for encoding " +
                    //  mimeName + " (" + val + ")" ...
                    mimeName = val;
                    //lastPrintable = 0x00FF;
                }
                else
                {
                    //lastPrintable =
                    //    Integer.decode(val.substring(pos).trim()).intValue();
                    StringTokenizer st =
                        new StringTokenizer(val.substring(0, pos), ",");
                    for (boolean first = true;
                        st.hasMoreTokens();
                        first = false)
                    {
                        mimeName = st.nextToken();
                        ret[j] =
                            new EncodingInfo(mimeName, javaName);
                        _encodingTableKeyMime.put(
                            mimeName.toUpperCase(),
                            ret[j]);
                        if (first)
                            _encodingTableKeyJava.put(
                                javaName.toUpperCase(),
                                ret[j]);
                        j++;
                    }
                }
            }
            return ret;
        }
        catch (java.net.MalformedURLException mue)
        {
            throw new com.sun.org.apache.xml.internal.serializer.utils.WrappedRuntimeException(mue);
        }
        catch (java.io.IOException ioe)
        {
            throw new com.sun.org.apache.xml.internal.serializer.utils.WrappedRuntimeException(ioe);
        }
    }

    /**
     * Return true if the character is the high member of a surrogate pair.
     * <p>
     * This is not a public API.
     * @param ch the character to test
     * @xsl.usage internal
     */
    static boolean isHighUTF16Surrogate(char ch) {
        return ('\uD800' <= ch && ch <= '\uDBFF');
    }
    /**
     * Return true if the character is the low member of a surrogate pair.
     * <p>
     * This is not a public API.
     * @param ch the character to test
     * @xsl.usage internal
     */
    static boolean isLowUTF16Surrogate(char ch) {
        return ('\uDC00' <= ch && ch <= '\uDFFF');
    }
    /**
     * Return the unicode code point represented by the high/low surrogate pair.
     * <p>
     * This is not a public API.
     * @param highSurrogate the high char of the high/low pair
     * @param lowSurrogate the low char of the high/low pair
     * @xsl.usage internal
     */
    static int toCodePoint(char highSurrogate, char lowSurrogate) {
        int codePoint =
            ((highSurrogate - 0xd800) << 10)
                + (lowSurrogate - 0xdc00)
                + 0x10000;
        return codePoint;
    }
    /**
     * Return the unicode code point represented by the char.
     * A bit of a dummy method, since all it does is return the char,
     * but as an int value.
     * <p>
     * This is not a public API.
     * @param ch the char.
     * @xsl.usage internal
     */
    static int toCodePoint(char ch) {
        int codePoint = ch;
        return codePoint;
    }

    private static final HashMap _encodingTableKeyJava = new HashMap();
    private static final HashMap _encodingTableKeyMime = new HashMap();
    private static final EncodingInfo[] _encodings = loadEncodingInfo();
}
