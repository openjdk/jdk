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
package com.sun.xml.internal.org.jvnet.fastinfoset;

import java.io.OutputStream;
import java.util.Map;

/**
 * A general interface for serializers of fast infoset documents.
 *
 * <p>
 * This interface contains common methods that are not specific to any
 * API associated with the serialization of XML Infoset to fast infoset
 * documents.
 *
 * @author Paul.Sandoz@Sun.Com
 */
public interface FastInfosetSerializer {
    /**
     * The feature to ignore the document type declaration and the
     * internal subset.
     * <p>
     * The default value is false. If true a serializer shall ignore document
     * type declaration and the internal subset.
     */
    public static final String IGNORE_DTD_FEATURE =
        "http://jvnet.org/fastinfoset/serializer/feature/ignore/DTD";

    /**
     * The feature to ignore comments.
     * <p>
     * The default value is false. If true a serializer shall ignore comments
     * and shall not serialize them.
     */
    public static final String IGNORE_COMMENTS_FEATURE =
        "http://jvnet.org/fastinfoset/serializer/feature/ignore/comments";

    /**
     * The feature to ignore processing instructions.
     * <p>
     * The default value is false. If true a serializer shall ignore processing
     * instructions and shall not serialize them.
     */
    public static final String IGNORE_PROCESSING_INSTRUCTIONS_FEATURE =
        "http://jvnet.org/fastinfoset/serializer/feature/ignore/processingInstructions";

    /**
     * The feature to ignore text content that consists completely of white
     * space characters.
     * <p>
     * The default value is false. If true a serializer shall ignore text
     * content that consists completely of white space characters.
     */
    public static final String IGNORE_WHITE_SPACE_TEXT_CONTENT_FEATURE =
        "http://jvnet.org/fastinfoset/serializer/feature/ignore/whiteSpaceTextContent";

    /**
     * The property name to be used for getting and setting the buffer size
     * of a parser.
     */
    public static final String BUFFER_SIZE_PROPERTY =
        "http://jvnet.org/fastinfoset/parser/properties/buffer-size";

    /**
     * The property name to be used for getting and setting the
     * Map containing encoding algorithms.
     *
     */
    public static final String REGISTERED_ENCODING_ALGORITHMS_PROPERTY =
        "http://jvnet.org/fastinfoset/parser/properties/registered-encoding-algorithms";

   /**
     * The property name to be used for getting and setting the
     * Map containing external vocabularies.
     *
     */
    public static final String EXTERNAL_VOCABULARIES_PROPERTY =
        "http://jvnet.org/fastinfoset/parser/properties/external-vocabularies";

    /**
     * The default minimum size of the character content chunks,
     * that will be indexed.
     */
    public final static int MIN_CHARACTER_CONTENT_CHUNK_SIZE = 0;

    /**
     * The default maximum size of the character content chunks,
     * that will be indexed.
     */
    public final static int MAX_CHARACTER_CONTENT_CHUNK_SIZE = 32;

    /**
     * The default value for limit on the size of indexed Map for attribute values
     * Limit is measured in bytes not in number of entries
     */
    public static final int CHARACTER_CONTENT_CHUNK_MAP_MEMORY_CONSTRAINT = Integer.MAX_VALUE;

    /**
     * The default minimum size of the attribute values, that will be indexed.
     */
    public final static int MIN_ATTRIBUTE_VALUE_SIZE = 0;

    /**
     * The default maximum size of the attribute values, that will be indexed.
     */
    public final static int MAX_ATTRIBUTE_VALUE_SIZE = 32;

    /**
     * The default value for limit on the size of indexed Map for attribute values
     * Limit is measured in bytes not in number of entries
     */
    public static final int ATTRIBUTE_VALUE_MAP_MEMORY_CONSTRAINT = Integer.MAX_VALUE;

    /**
     * The character encoding scheme string for UTF-8.
     */
    public static final String UTF_8 = "UTF-8";

    /**
     * The character encoding scheme string for UTF-16BE.
     */
    public static final String UTF_16BE = "UTF-16BE";

    /**
     * Set the {@link #IGNORE_DTD_FEATURE}.
     * @param ignoreDTD true if the feature shall be ignored.
     */
    public void setIgnoreDTD(boolean ignoreDTD);

    /**
     * Get the {@link #IGNORE_DTD_FEATURE}.
     * @return true if the feature is ignored, false otherwise.
     */
    public boolean getIgnoreDTD();

    /**
     * Set the {@link #IGNORE_COMMENTS_FEATURE}.
     * @param ignoreComments true if the feature shall be ignored.
     */
    public void setIgnoreComments(boolean ignoreComments);

    /**
     * Get the {@link #IGNORE_COMMENTS_FEATURE}.
     * @return true if the feature is ignored, false otherwise.
     */
    public boolean getIgnoreComments();

    /**
     * Set the {@link #IGNORE_PROCESSING_INSTRUCTIONS_FEATURE}.
     * @param ignoreProcesingInstructions true if the feature shall be ignored.
     */
    public void setIgnoreProcesingInstructions(boolean ignoreProcesingInstructions);

    /**
     * Get the {@link #IGNORE_PROCESSING_INSTRUCTIONS_FEATURE}.
     * @return true if the feature is ignored, false otherwise.
     */
    public boolean getIgnoreProcesingInstructions();

    /**
     * Set the {@link #IGNORE_WHITE_SPACE_TEXT_CONTENT_FEATURE}.
     * @param ignoreWhiteSpaceTextContent true if the feature shall be ignored.
     */
    public void setIgnoreWhiteSpaceTextContent(boolean ignoreWhiteSpaceTextContent);

    /**
     * Get the {@link #IGNORE_WHITE_SPACE_TEXT_CONTENT_FEATURE}.
     * @return true if the feature is ignored, false otherwise.
     */
    public boolean getIgnoreWhiteSpaceTextContent();

    /**
     * Sets the character encoding scheme.
     *
     * The character encoding can be either UTF-8 or UTF-16BE for the
     * the encoding of chunks of CIIs, the [normalized value]
     * property of attribute information items, comment information
     * items and processing instruction information items.
     *
     * @param characterEncodingScheme The set of registered algorithms.
     */
    public void setCharacterEncodingScheme(String characterEncodingScheme);

    /**
     * Gets the character encoding scheme.
     *
     * @return The character encoding scheme.
     */
    public String getCharacterEncodingScheme();

    /**
     * Sets the set of registered encoding algorithms.
     *
     * @param algorithms The set of registered algorithms.
     */
    public void setRegisteredEncodingAlgorithms(Map algorithms);

    /**
     * Gets the set of registered encoding algorithms.
     *
     * @return The set of registered algorithms.
     */
    public Map getRegisteredEncodingAlgorithms();

    /**
     * Gets the minimum size of character content chunks
     * that will be indexed.
     *
     * @return The minimum character content chunk size.
     */
    public int getMinCharacterContentChunkSize();

    /**
     * Sets the minimum size of character content chunks
     * that will be indexed.
     *
     * @param size the minimum character content chunk size.
     */
    public void setMinCharacterContentChunkSize(int size);

    /**
     * Gets the maximum size of character content chunks
     * that will be indexed.
     *
     * @return The maximum character content chunk size.
     */
    public int getMaxCharacterContentChunkSize();

    /**
     * Sets the maximum size of character content chunks
     * that will be indexed.
     *
     * @param size the maximum character content chunk size.
     */
    public void setMaxCharacterContentChunkSize(int size);

    /**
     * Gets the limit on the memory size of Map of attribute values
     * that will be indexed.
     *
     * @return The attribute value size limit.
     */
    public int getCharacterContentChunkMapMemoryLimit();

    /**
     * Sets the limit on the memory size of Map of attribute values
     * that will be indexed.
     *
     * @param size The attribute value size limit. Any value less
     * that a length of size limit will be indexed.
     */
    public void setCharacterContentChunkMapMemoryLimit(int size);

    /**
     * Gets the minimum size of attribute values
     * that will be indexed.
     *
     * @return The minimum attribute values size.
     */
    public int getMinAttributeValueSize();

    /**
     * Sets the minimum size of attribute values
     * that will be indexed.
     *
     * @param size the minimum attribute values size.
     */
    public void setMinAttributeValueSize(int size);

    /**
     * Gets the maximum size of attribute values
     * that will be indexed.
     *
     * @return The maximum attribute values size.
     */
    public int getMaxAttributeValueSize();

    /**
     * Sets the maximum size of attribute values
     * that will be indexed.
     *
     * @param size the maximum attribute values size.
     */
    public void setMaxAttributeValueSize(int size);

    /**
     * Gets the limit on the memory size of Map of attribute values
     * that will be indexed.
     *
     * @return The attribute value size limit.
     */
    public int getAttributeValueMapMemoryLimit();

    /**
     * Sets the limit on the memory size of Map of attribute values
     * that will be indexed.
     *
     * @param size The attribute value size limit. Any value less
     * that a length of size limit will be indexed.
     */
    public void setAttributeValueMapMemoryLimit(int size);

    /**
     * Set the external vocabulary that shall be used when serializing.
     *
     * @param v the vocabulary.
     */
    public void setExternalVocabulary(ExternalVocabulary v);

    /**
     * Set the application data to be associated with the serializer vocabulary.
     *
     * @param data the application data.
     */
    public void setVocabularyApplicationData(VocabularyApplicationData data);

    /**
     * Get the application data associated with the serializer vocabulary.
     *
     * @return the application data.
     */
    public VocabularyApplicationData getVocabularyApplicationData();

    /**
     * Reset the serializer for reuse serializing another XML infoset.
     */
    public void reset();

    /**
     * Set the OutputStream to serialize the XML infoset to a
     * fast infoset document.
     *
     * @param s the OutputStream where the fast infoset document is written to.
     */
    public void setOutputStream(OutputStream s);
}
