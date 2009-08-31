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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */
package com.sun.xml.internal.org.jvnet.fastinfoset.sax.helpers;

import com.sun.xml.internal.fastinfoset.CommonResourceBundle;
import com.sun.xml.internal.fastinfoset.EncodingConstants;
import com.sun.xml.internal.fastinfoset.algorithm.BuiltInEncodingAlgorithmFactory;
import java.io.IOException;
import java.util.Map;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithm;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmException;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmIndexes;
import com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetException;
import com.sun.xml.internal.org.jvnet.fastinfoset.sax.EncodingAlgorithmAttributes;
import org.xml.sax.Attributes;

/**
 * Default implementation of the {@link EncodingAlgorithmAttributes} interface.
 *
 * <p>This class provides a default implementation of the SAX2
 * {@link EncodingAlgorithmAttributes} interface, with the
 * addition of manipulators so that the list can be modified or
 * reused.</p>
 *
 * <p>There are two typical uses of this class:</p>
 *
 * <ol>
 * <li>to take a persistent snapshot of an EncodingAlgorithmAttributes object
 *  in a {@link org.xml.sax.ContentHandler#startElement startElement} event; or</li>
 * <li>to construct or modify an EncodingAlgorithmAttributes object in a SAX2
 * driver or filter.</li>
 * </ol>
 */
public class EncodingAlgorithmAttributesImpl implements EncodingAlgorithmAttributes {
    private static final int DEFAULT_CAPACITY       = 8;

    private static final int URI_OFFSET             = 0;
    private static final int LOCALNAME_OFFSET       = 1;
    private static final int QNAME_OFFSET           = 2;
    private static final int TYPE_OFFSET            = 3;
    private static final int VALUE_OFFSET           = 4;
    private static final int ALGORITHMURI_OFFSET    = 5;

    private static final int SIZE                   = 6;

    private Map _registeredEncodingAlgorithms;

    private int _length;

    private String[] _data;

    private int[] _algorithmIds;

    private Object[] _algorithmData;

    private String[] _alphabets;

    private boolean[] _toIndex;

    /**
     * Construct a new, empty EncodingAlgorithmAttributesImpl object.
     */
    public EncodingAlgorithmAttributesImpl() {
        this(null, null);
    }

    /**
     * Copy an existing Attributes object.
     *
     * <p>This constructor is especially useful inside a
     * {@link org.xml.sax.ContentHandler#startElement startElement} event.</p>
     *
     * @param attributes The existing Attributes object.
     */
    public EncodingAlgorithmAttributesImpl(Attributes attributes) {
        this(null, attributes);
    }

    /**
     * Use registered encoding algorithms and copy an existing Attributes object.
     *
     * <p>This constructor is especially useful inside a
     * {@link org.xml.sax.ContentHandler#startElement startElement} event.</p>
     *
     * @param registeredEncodingAlgorithms
     *      The registeredEncodingAlgorithms encoding algorithms.
     * @param attributes The existing Attributes object.
     */
    public EncodingAlgorithmAttributesImpl(Map registeredEncodingAlgorithms,
            Attributes attributes) {
        _data = new String[DEFAULT_CAPACITY * SIZE];
        _algorithmIds = new int[DEFAULT_CAPACITY];
        _algorithmData = new Object[DEFAULT_CAPACITY];
        _alphabets = new String[DEFAULT_CAPACITY];
        _toIndex = new boolean[DEFAULT_CAPACITY];

        _registeredEncodingAlgorithms = registeredEncodingAlgorithms;

        if (attributes != null) {
            if (attributes instanceof EncodingAlgorithmAttributes) {
                setAttributes((EncodingAlgorithmAttributes)attributes);
            } else {
                setAttributes(attributes);
            }
        }
    }

    /**
     * Clear the attribute list for reuse.
     *
     */
    public final void clear() {
        for (int i = 0; i < _length; i++) {
            _data[i * SIZE + VALUE_OFFSET] = null;
            _algorithmData[i] = null;
        }
        _length = 0;
    }

    /**
     * Add an attribute to the end of the list.
     *
     * <p>For the sake of speed, this method does no checking
     * to see if the attribute is already in the list: that is
     * the responsibility of the application.</p>
     *
     * @param URI The Namespace URI, or the empty string if
     *        none is available or Namespace processing is not
     *        being performed.
     * @param localName The local name, or the empty string if
     *        Namespace processing is not being performed.
     * @param qName The qualified (prefixed) name, or the empty string
     *        if qualified names are not available.
     * @param type The attribute type as a string.
     * @param value The attribute value.
     */
    public void addAttribute(String URI, String localName, String qName,
            String type, String value) {
        if (_length >= _algorithmData.length) {
            resize();
        }

        int i = _length * SIZE;
        _data[i++] = replaceNull(URI);
        _data[i++] = replaceNull(localName);
        _data[i++] = replaceNull(qName);
        _data[i++] = replaceNull(type);
        _data[i++] = replaceNull(value);
        _toIndex[_length] = false;
        _alphabets[_length] = null;

        _length++;
    }

    /**
     * Add an attribute to the end of the list.
     *
     * <p>For the sake of speed, this method does no checking
     * to see if the attribute is already in the list: that is
     * the responsibility of the application.</p>
     *
     * @param URI The Namespace URI, or the empty string if
     *        none is available or Namespace processing is not
     *        being performed.
     * @param localName The local name, or the empty string if
     *        Namespace processing is not being performed.
     * @param qName The qualified (prefixed) name, or the empty string
     *        if qualified names are not available.
     * @param type The attribute type as a string.
     * @param value The attribute value.
     * @param index True if attribute should be indexed.
     * @param alphabet The alphabet associated with the attribute value,
     *              may be null if there is no associated alphabet.
     */
    public void addAttribute(String URI, String localName, String qName,
            String type, String value, boolean index, String alphabet) {
        if (_length >= _algorithmData.length) {
            resize();
        }

        int i = _length * SIZE;
        _data[i++] = replaceNull(URI);
        _data[i++] = replaceNull(localName);
        _data[i++] = replaceNull(qName);
        _data[i++] = replaceNull(type);
        _data[i++] = replaceNull(value);
        _toIndex[_length] = index;
        _alphabets[_length] = alphabet;

        _length++;
    }

    /**
     * Add an attribute with built in algorithm data to the end of the list.
     *
     * <p>For the sake of speed, this method does no checking
     * to see if the attribute is already in the list: that is
     * the responsibility of the application.</p>
     *
     * @param URI The Namespace URI, or the empty string if
     *        none is available or Namespace processing is not
     *        being performed.
     * @param localName The local name, or the empty string if
     *        Namespace processing is not being performed.
     * @param qName The qualified (prefixed) name, or the empty string
     *        if qualified names are not available.
     * @param builtInAlgorithmID The built in algorithm ID.
     * @param algorithmData The built in algorithm data.
     */
    public void addAttributeWithBuiltInAlgorithmData(String URI, String localName, String qName,
            int builtInAlgorithmID, Object algorithmData) {
        if (_length >= _algorithmData.length) {
            resize();
        }

        int i = _length * SIZE;
        _data[i++] = replaceNull(URI);
        _data[i++] = replaceNull(localName);
        _data[i++] = replaceNull(qName);
        _data[i++] = "CDATA";
        _data[i++] = "";
        _data[i++] = null;
        _algorithmIds[_length] = builtInAlgorithmID;
        _algorithmData[_length] = algorithmData;
        _toIndex[_length] = false;
        _alphabets[_length] = null;

        _length++;
    }

    /**
     * Add an attribute with algorithm data to the end of the list.
     *
     * <p>For the sake of speed, this method does no checking
     * to see if the attribute is already in the list: that is
     * the responsibility of the application.</p>
     *
     * @param URI The Namespace URI, or the empty string if
     *        none is available or Namespace processing is not
     *        being performed.
     * @param localName The local name, or the empty string if
     *        Namespace processing is not being performed.
     * @param qName The qualified (prefixed) name, or the empty string
     *        if qualified names are not available.
     * @param algorithmURI The algorithm URI, or null if a built in algorithm
     * @param algorithmID The algorithm ID.
     * @param algorithmData The algorithm data.
     */
    public void addAttributeWithAlgorithmData(String URI, String localName, String qName,
            String algorithmURI, int algorithmID, Object algorithmData) {
        if (_length >= _algorithmData.length) {
            resize();
        }

        int i = _length * SIZE;
        _data[i++] = replaceNull(URI);
        _data[i++] = replaceNull(localName);
        _data[i++] = replaceNull(qName);
        _data[i++] = "CDATA";
        _data[i++] = "";
        _data[i++] = algorithmURI;
        _algorithmIds[_length] = algorithmID;
        _algorithmData[_length] = algorithmData;
        _toIndex[_length] = false;
        _alphabets[_length] = null;

        _length++;
    }

    /**
     * Replace an attribute value with algorithm data.
     *
     * <p>For the sake of speed, this method does no checking
     * to see if the attribute is already in the list: that is
     * the responsibility of the application.</p>
     *
     * @param index The index of the attribute whose value is to be replaced
     * @param algorithmURI The algorithm URI, or null if a built in algorithm
     * @param algorithmID The algorithm ID.
     * @param algorithmData The algorithm data.
     */
    public void replaceWithAttributeAlgorithmData(int index,
            String algorithmURI, int algorithmID, Object algorithmData) {
        if (index < 0 || index >= _length) return;

        int i = index * SIZE;
        _data[i + VALUE_OFFSET] = null;
        _data[i + ALGORITHMURI_OFFSET] = algorithmURI;
        _algorithmIds[index] = algorithmID;
        _algorithmData[index] = algorithmData;
        _toIndex[index] = false;
        _alphabets[index] = null;
    }

    /**
     * Copy an entire Attributes object.
     *
     * @param atts The attributes to copy.
     */
    public void setAttributes(Attributes atts) {
        _length = atts.getLength();
        if (_length > 0) {

            if (_length >= _algorithmData.length) {
                resizeNoCopy();
            }

            int index = 0;
            for (int i = 0; i < _length; i++) {
                _data[index++] = atts.getURI(i);
                _data[index++] = atts.getLocalName(i);
                _data[index++] = atts.getQName(i);
                _data[index++] = atts.getType(i);
                _data[index++] = atts.getValue(i);
                index++;
                _toIndex[i] = false;
                _alphabets[i] = null;
            }
        }
    }

    /**
     * Copy an entire EncodingAlgorithmAttributes object.
     *
     * @param atts The attributes to copy.
     */
    public void setAttributes(EncodingAlgorithmAttributes atts) {
        _length = atts.getLength();
        if (_length > 0) {

            if (_length >= _algorithmData.length) {
                resizeNoCopy();
            }

            int index = 0;
            for (int i = 0; i < _length; i++) {
                _data[index++] = atts.getURI(i);
                _data[index++] = atts.getLocalName(i);
                _data[index++] = atts.getQName(i);
                _data[index++] = atts.getType(i);
                _data[index++] = atts.getValue(i);
                _data[index++] = atts.getAlgorithmURI(i);
                _algorithmIds[i] = atts.getAlgorithmIndex(i);
                _algorithmData[i] = atts.getAlgorithmData(i);
                _toIndex[i] = false;
                _alphabets[i] = null;
            }
        }
    }

    // org.xml.sax.Attributes

    public final int getLength() {
        return _length;
    }

    public final String getLocalName(int index) {
        if (index >= 0 && index < _length) {
            return _data[index * SIZE + LOCALNAME_OFFSET];
        } else {
            return null;
        }
    }

    public final String getQName(int index) {
        if (index >= 0 && index < _length) {
            return _data[index * SIZE + QNAME_OFFSET];
        } else {
            return null;
        }
    }

    public final String getType(int index) {
        if (index >= 0 && index < _length) {
            return _data[index * SIZE + TYPE_OFFSET];
        } else {
            return null;
        }
    }

    public final String getURI(int index) {
        if (index >= 0 && index < _length) {
            return _data[index * SIZE + URI_OFFSET];
        } else {
            return null;
        }
    }

    public final String getValue(int index) {
        if (index >= 0 && index < _length) {
            final String value = _data[index * SIZE + VALUE_OFFSET];
            if (value != null) return value;
        } else {
            return null;
        }

        if (_algorithmData[index] == null || _registeredEncodingAlgorithms == null) {
            return null;
        }

        try {
            return _data[index * SIZE + VALUE_OFFSET] = convertEncodingAlgorithmDataToString(
                    _algorithmIds[index],
                    _data[index * SIZE + ALGORITHMURI_OFFSET],
                    _algorithmData[index]).toString();
        } catch (IOException e) {
            return null;
        } catch (FastInfosetException e) {
            return null;
        }
    }

    public final int getIndex(String qName) {
        for (int index = 0; index < _length; index++) {
            if (qName.equals(_data[index * SIZE + QNAME_OFFSET])) {
                return index;
            }
        }
        return -1;
    }

    public final String getType(String qName) {
        int index = getIndex(qName);
        if (index >= 0) {
            return _data[index * SIZE + TYPE_OFFSET];
        } else {
            return null;
        }
    }

    public final String getValue(String qName) {
        int index = getIndex(qName);
        if (index >= 0) {
            return getValue(index);
        } else {
            return null;
        }
    }

    public final int getIndex(String uri, String localName) {
        for (int index = 0; index < _length; index++) {
            if (localName.equals(_data[index * SIZE + LOCALNAME_OFFSET]) &&
                    uri.equals(_data[index * SIZE + URI_OFFSET])) {
                return index;
            }
        }
        return -1;
    }

    public final String getType(String uri, String localName) {
        int index = getIndex(uri, localName);
        if (index >= 0) {
            return _data[index * SIZE + TYPE_OFFSET];
        } else {
            return null;
        }
    }

    public final String getValue(String uri, String localName) {
        int index = getIndex(uri, localName);
        if (index >= 0) {
            return getValue(index);
        } else {
            return null;
        }
    }

    // EncodingAlgorithmAttributes

    public final String getAlgorithmURI(int index) {
        if (index >= 0 && index < _length) {
            return _data[index * SIZE + ALGORITHMURI_OFFSET];
        } else {
            return null;
        }
    }

    public final int getAlgorithmIndex(int index) {
        if (index >= 0 && index < _length) {
            return _algorithmIds[index];
        } else {
            return -1;
        }
    }

    public final Object getAlgorithmData(int index) {
        if (index >= 0 && index < _length) {
            return _algorithmData[index];
        } else {
            return null;
        }
    }

    // ExtendedAttributes

    public final String getAlpababet(int index) {
        if (index >= 0 && index < _length) {
            return _alphabets[index];
        } else {
            return null;
        }
    }

    public final boolean getToIndex(int index) {
        if (index >= 0 && index < _length) {
            return _toIndex[index];
        } else {
            return false;
        }
    }

    // -----

    private final String replaceNull(String s) {
        return (s != null) ? s : "";
    }

    private final void resizeNoCopy() {
        final int newLength = _length * 3 / 2 + 1;

        _data = new String[newLength * SIZE];
        _algorithmIds = new int[newLength];
        _algorithmData = new Object[newLength];
    }

    private final void resize() {
        final int newLength = _length * 3 / 2 + 1;

        String[] data = new String[newLength * SIZE];
        int[] algorithmIds = new int[newLength];
        Object[] algorithmData = new Object[newLength];
        String[] alphabets = new String[newLength];
        boolean[] toIndex = new boolean[newLength];

        System.arraycopy(_data, 0, data, 0, _length * SIZE);
        System.arraycopy(_algorithmIds, 0, algorithmIds, 0, _length);
        System.arraycopy(_algorithmData, 0, algorithmData, 0, _length);
        System.arraycopy(_alphabets, 0, alphabets, 0, _length);
        System.arraycopy(_toIndex, 0, toIndex, 0, _length);

        _data = data;
        _algorithmIds = algorithmIds;
        _algorithmData = algorithmData;
        _alphabets = alphabets;
        _toIndex = toIndex;
    }

    private final StringBuffer convertEncodingAlgorithmDataToString(
            int identifier, String URI, Object data) throws FastInfosetException, IOException {
        EncodingAlgorithm ea = null;
        if (identifier < EncodingConstants.ENCODING_ALGORITHM_BUILTIN_END) {
            ea = BuiltInEncodingAlgorithmFactory.getAlgorithm(identifier);
        } else if (identifier == EncodingAlgorithmIndexes.CDATA) {
            throw new EncodingAlgorithmException(
                    CommonResourceBundle.getInstance().getString("message.CDATAAlgorithmNotSupported"));
        } else if (identifier >= EncodingConstants.ENCODING_ALGORITHM_APPLICATION_START) {
            if (URI == null) {
                throw new EncodingAlgorithmException(
                        CommonResourceBundle.getInstance().getString("message.URINotPresent") + identifier);
            }

            ea = (EncodingAlgorithm)_registeredEncodingAlgorithms.get(URI);
            if (ea == null) {
                throw new EncodingAlgorithmException(
                        CommonResourceBundle.getInstance().getString("message.algorithmNotRegistered") + URI);
            }
        } else {
            // Reserved built-in algorithms for future use
            // TODO should use sax property to decide if event will be
            // reported, allows for support through handler if required.
            throw new EncodingAlgorithmException(
                    CommonResourceBundle.getInstance().getString("message.identifiers10to31Reserved"));
        }

        final StringBuffer sb = new StringBuffer();
        ea.convertToCharacters(data, sb);
        return sb;
    }
}
