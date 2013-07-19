/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.encryption;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import com.sun.org.apache.xml.internal.security.c14n.Canonicalizer;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Converts <code>String</code>s into <code>Node</code>s and visa versa.
 *
 * An abstract class for common Serializer functionality
 */
public abstract class AbstractSerializer implements Serializer {

    protected Canonicalizer canon;

    public void setCanonicalizer(Canonicalizer canon) {
        this.canon = canon;
    }

    /**
     * Returns a <code>String</code> representation of the specified
     * <code>Element</code>.
     * <p/>
     * Refer also to comments about setup of format.
     *
     * @param element the <code>Element</code> to serialize.
     * @return the <code>String</code> representation of the serilaized
     *   <code>Element</code>.
     * @throws Exception
     */
    public String serialize(Element element) throws Exception {
        return canonSerialize(element);
    }

    /**
     * Returns a <code>byte[]</code> representation of the specified
     * <code>Element</code>.
     *
     * @param element the <code>Element</code> to serialize.
     * @return the <code>byte[]</code> representation of the serilaized
     *   <code>Element</code>.
     * @throws Exception
     */
    public byte[] serializeToByteArray(Element element) throws Exception {
        return canonSerializeToByteArray(element);
    }

    /**
     * Returns a <code>String</code> representation of the specified
     * <code>NodeList</code>.
     * <p/>
     * This is a special case because the NodeList may represent a
     * <code>DocumentFragment</code>. A document fragment may be a
     * non-valid XML document (refer to appropriate description of
     * W3C) because it my start with a non-element node, e.g. a text
     * node.
     * <p/>
     * The methods first converts the node list into a document fragment.
     * Special care is taken to not destroy the current document, thus
     * the method clones the nodes (deep cloning) before it appends
     * them to the document fragment.
     * <p/>
     * Refer also to comments about setup of format.
     *
     * @param content the <code>NodeList</code> to serialize.
     * @return the <code>String</code> representation of the serialized
     *   <code>NodeList</code>.
     * @throws Exception
     */
    public String serialize(NodeList content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        canon.setWriter(baos);
        canon.notReset();
        for (int i = 0; i < content.getLength(); i++) {
            canon.canonicalizeSubtree(content.item(i));
        }
        String ret = baos.toString("UTF-8");
        baos.reset();
        return ret;
    }

    /**
     * Returns a <code>byte[]</code> representation of the specified
     * <code>NodeList</code>.
     *
     * @param content the <code>NodeList</code> to serialize.
     * @return the <code>byte[]</code> representation of the serialized
     *   <code>NodeList</code>.
     * @throws Exception
     */
    public byte[] serializeToByteArray(NodeList content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        canon.setWriter(baos);
        canon.notReset();
        for (int i = 0; i < content.getLength(); i++) {
            canon.canonicalizeSubtree(content.item(i));
        }
        return baos.toByteArray();
    }

    /**
     * Use the Canonicalizer to serialize the node
     * @param node
     * @return the canonicalization of the node
     * @throws Exception
     */
    public String canonSerialize(Node node) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        canon.setWriter(baos);
        canon.notReset();
        canon.canonicalizeSubtree(node);
        String ret = baos.toString("UTF-8");
        baos.reset();
        return ret;
    }

    /**
     * Use the Canonicalizer to serialize the node
     * @param node
     * @return the (byte[]) canonicalization of the node
     * @throws Exception
     */
    public byte[] canonSerializeToByteArray(Node node) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        canon.setWriter(baos);
        canon.notReset();
        canon.canonicalizeSubtree(node);
        return baos.toByteArray();
    }

    /**
     * @param source
     * @param ctx
     * @return the Node resulting from the parse of the source
     * @throws XMLEncryptionException
     */
    public abstract Node deserialize(String source, Node ctx) throws XMLEncryptionException;

    /**
     * @param source
     * @param ctx
     * @return the Node resulting from the parse of the source
     * @throws XMLEncryptionException
     */
    public abstract Node deserialize(byte[] source, Node ctx) throws XMLEncryptionException;

    protected static byte[] createContext(byte[] source, Node ctx) throws XMLEncryptionException {
        // Create the context to parse the document against
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream, "UTF-8");
            outputStreamWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><dummy");

            // Run through each node up to the document node and find any xmlns: nodes
            Map<String, String> storedNamespaces = new HashMap<String, String>();
            Node wk = ctx;
            while (wk != null) {
                NamedNodeMap atts = wk.getAttributes();
                if (atts != null) {
                    for (int i = 0; i < atts.getLength(); ++i) {
                        Node att = atts.item(i);
                        String nodeName = att.getNodeName();
                        if ((nodeName.equals("xmlns") || nodeName.startsWith("xmlns:"))
                                && !storedNamespaces.containsKey(att.getNodeName())) {
                            outputStreamWriter.write(" ");
                            outputStreamWriter.write(nodeName);
                            outputStreamWriter.write("=\"");
                            outputStreamWriter.write(att.getNodeValue());
                            outputStreamWriter.write("\"");
                            storedNamespaces.put(nodeName, att.getNodeValue());
                        }
                    }
                }
                wk = wk.getParentNode();
            }
            outputStreamWriter.write(">");
            outputStreamWriter.flush();
            byteArrayOutputStream.write(source);

            outputStreamWriter.write("</dummy>");
            outputStreamWriter.close();

            return byteArrayOutputStream.toByteArray();
        } catch (UnsupportedEncodingException e) {
            throw new XMLEncryptionException("empty", e);
        } catch (IOException e) {
            throw new XMLEncryptionException("empty", e);
        }
    }

    protected static String createContext(String source, Node ctx) {
        // Create the context to parse the document against
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><dummy");

        // Run through each node up to the document node and find any xmlns: nodes
        Map<String, String> storedNamespaces = new HashMap<String, String>();
        Node wk = ctx;
        while (wk != null) {
            NamedNodeMap atts = wk.getAttributes();
            if (atts != null) {
                for (int i = 0; i < atts.getLength(); ++i) {
                    Node att = atts.item(i);
                    String nodeName = att.getNodeName();
                    if ((nodeName.equals("xmlns") || nodeName.startsWith("xmlns:"))
                        && !storedNamespaces.containsKey(att.getNodeName())) {
                        sb.append(" " + nodeName + "=\"" + att.getNodeValue() + "\"");
                        storedNamespaces.put(nodeName, att.getNodeValue());
                    }
                }
            }
            wk = wk.getParentNode();
        }
        sb.append(">" + source + "</dummy>");
        return sb.toString();
    }

}
