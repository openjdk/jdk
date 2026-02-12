/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Property list (plist) file reader.
 */
public final class PListReader {

    public record Raw(String value, Type type) {

        public enum Type {
            STRING,
            BOOLEAN,
            REAL,
            INTEGER,
            DATE,
            DATA;

            private static Optional<Type> fromElementName(String name) {
                switch (name) {
                    case "string" -> {
                        return Optional.of(STRING);
                    }
                    case "true" -> {
                        return Optional.of(BOOLEAN);
                    }
                    case "false" -> {
                        return Optional.of(BOOLEAN);
                    }
                    case "real" -> {
                        return Optional.of(REAL);
                    }
                    case "integer" -> {
                        return Optional.of(INTEGER);
                    }
                    case "date" -> {
                        return Optional.of(DATE);
                    }
                    case "data" -> {
                        return Optional.of(DATA);
                    }
                    default -> {
                        return Optional.empty();
                    }
                }
            }
        }

        public Raw {
            Objects.requireNonNull(value);
            Objects.requireNonNull(type);
        }

        private static Optional<Raw> tryCreate(Element e) {
            return Type.fromElementName(e.getNodeName()).map(type -> {
                if (type == Type.BOOLEAN) {
                    if ("true".equals(e.getNodeName())) {
                        return new Raw(Boolean.TRUE.toString(), type);
                    } else {
                        return new Raw(Boolean.FALSE.toString(), type);
                    }
                } else {
                    return new Raw(e.getTextContent(), type);
                }
            });
        }
    }

    /**
     * Returns the contents of the the underlying "dict" element as a Map.
     * <p>
     * The keys in the returned map are names of the properties.
     * <p>
     * Values of nested "dict" properties are stored as {@code Map<String, Object>}
     * or {@code PListReader} objects depending on the value of the
     * {@code fetchDictionaries} parameter.
     * <p>
     * Values of "array" properties are stored as {@code List<Object>} objects.
     * <p>
     * Values of other properties are stored as {@code Raw} objects.
     *
     * @param fetchDictionaries controls the type of objects of nested "dict"
     *                          elements. If the value is {@code true},
     *                          {@code Map<String, Object>} type is used, and
     *                          {@code PListReader} type otherwise.
     * @return the contents of the the underlying "dict" element as a Map
     */
    public Map<String, Object> toMap(boolean fetchDictionaries) {
        Map<String, Object> reply = new HashMap<>();
        var nodes = root.getChildNodes();
        for (int i = 0; i != nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element e) {
                tryCreateValue(e, fetchDictionaries).ifPresent(value -> {
                    final var query = "preceding-sibling::*[1]";
                    Optional.ofNullable(toSupplier(() -> {
                        return (Node) XPathSingleton.INSTANCE.evaluate(query, e, XPathConstants.NODE);
                    }).get()).ifPresent(n -> {
                        if ("key".equals(n.getNodeName())) {
                            var keyName = n.getTextContent();
                            reply.putIfAbsent(keyName, value);
                        }
                    });
                });
            }
        }

        return reply;
    }

    /**
     * Returns the value of the given string property in the underlying "dict"
     * element.
     *
     * @param keyName the name of a string property whose value to query
     * @return the value of the string property with the specified name in the
     *         underlying "dict" element
     * @throws NoSuchElementException if there is no string property with the given
     *                                name in the underlying "dict" element
     */
    public String queryValue(String keyName) {
        return findValue(keyName).orElseThrow(NoSuchElementException::new);
    }

    public Optional<String> findValue(String keyName) {
        return findNode(keyName).filter(node -> {
            return "string".equals(node.getNodeName());
        }).map(Node::getTextContent);
    }

    /**
     * Returns the value of the given "dict" property in the underlying "dict"
     * element.
     *
     * @param keyName the name of a "dict" property whose value to query
     * @return the value of the "dict" property with the specified name in the
     *         underlying "dict" element
     * @throws NoSuchElementException if there is no "dict" property with the given
     *                                name in the underlying "dict" element
     */
    public PListReader queryDictValue(String keyName) {
        return findDictValue(keyName).orElseThrow(NoSuchElementException::new);
    }

    public Optional<PListReader> findDictValue(String keyName) {
        return findNode(keyName).filter(node -> {
            return "dict".equals(node.getNodeName());
        }).map(PListReader::new);
    }

    /**
     * Returns the value of the given boolean property in the underlying "dict"
     * element.
     *
     * @param keyName the name of a boolean property whose value to query
     * @return the value of the boolean property with the specified name in the
     *         underlying "dict" element
     * @throws NoSuchElementException if there is no string property with the given
     *                                name in the underlying "dict" element
     */
    public boolean queryBoolValue(String keyName) {
        return findBoolValue(keyName).orElseThrow(NoSuchElementException::new);
    }

    public Optional<Boolean> findBoolValue(String keyName) {
        return findNode(keyName).filter(node -> {
            switch (node.getNodeName()) {
                case "true", "false" -> {
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }).map(Node::getNodeName).map(Boolean::parseBoolean);
    }

    /**
     * Returns the value of the given array property in the underlying "dict"
     * element as a list of strings.
     * <p>
     * Processes the result of calling {@link #queryArrayValue(String)} on the
     * specified property name by filtering {@link Raw} instances of type
     * {@link Raw.Type#STRING}.
     *
     * @param keyName the name of an array property whose value to query
     * @return the value of the array property with the specified name in the
     *         underlying "dict" element
     * @throws NoSuchElementException if there is no array property with the given
     *                                name in the underlying "dict" element
     */
    public List<String> queryStringArrayValue(String keyName) {
        return findStringArrayValue(keyName).orElseThrow(NoSuchElementException::new);
    }

    public Optional<List<String>> findStringArrayValue(String keyName) {
        return findArrayValue(keyName, false).map(stream -> {
            return stream.map(v -> {
                if (v instanceof Raw r) {
                    if (r.type() == Raw.Type.STRING) {
                        return r.value();
                    }
                }
                return (String)null;
            }).filter(Objects::nonNull).toList();
        });
    }

    /**
     * Returns the value of the given array property in the underlying "dict"
     * element as a stream of {@link Object}-s.
     * <p>
     * Values of "dict" array items are stored as {@code Map<String, Object>} or
     * {@code PListReader} objects depending on the value of the
     * {@code fetchDictionaries} parameter.
     * <p>
     * Values of "array" array items are stored as {@code List<Object>} objects.
     * <p>
     * Values of other types are stored as {@code Raw} objects.
     *
     * @param keyName           the name of an array property whose value to query
     * @param fetchDictionaries controls the type of objects of "dict" elements. If
     *                          the value is {@code true},
     *                          {@code Map<String, Object>} type is used, and
     *                          {@code PListReader} type otherwise.
     * @return the value of the array property with the specified name in the
     *         underlying "dict" element
     * @throws NoSuchElementException if there is no array key with the given name
     *                                in the underlying "dict" element
     */
    public Stream<Object> queryArrayValue(String keyName, boolean fetchDictionaries) {
        return findArrayValue(keyName, fetchDictionaries).orElseThrow(NoSuchElementException::new);
    }

    public Optional<Stream<Object>> findArrayValue(String keyName, boolean fetchDictionaries) {
        return findNode(keyName).filter(node -> {
            return "array".equals(node.getNodeName());
        }).map(node -> {
            return readArray(node, fetchDictionaries);
        });
    }

    public XmlConsumer toXmlConsumer() {
        return xml -> {
            XmlUtils.concatXml(xml, new DOMSource(root));
        };
    }

    /**
     * Creates plist reader from the given node.
     * <p>
     * If the specified node is an element with the name "dict", the reader is bound
     * to the specified node; otherwise, it is bound to the {@code /plist/dict}
     * element in the document.
     *
     * @param node the node
     * @throws NoSuchElementException if the specified node is not an element with
     *                                name "dict" and there is no
     *                                {@code /plist/dict} node in the document
     */
    public PListReader(Node node) {
        Objects.requireNonNull(node);
        if (node.getNodeName().equals("dict")) {
            this.root = node;
        } else {
            this.root = Optional.ofNullable(toSupplier(() -> {
                return (Node) XPathSingleton.INSTANCE.evaluate("/plist[1]/dict[1]", node, XPathConstants.NODE);
            }).get()).orElseThrow(NoSuchElementException::new);
        }
    }

    public PListReader(byte[] xmlData) throws SAXException, IOException {
        this(XmlUtils.initDocumentBuilder().parse(new ByteArrayInputStream(xmlData)));
    }

    private Optional<?> tryCreateValue(Element e, boolean fetchDictionaries) {
        switch (e.getNodeName()) {
            case "dict" -> {
                var plistReader = new PListReader(e);
                if (fetchDictionaries) {
                    return Optional.of(plistReader.toMap(fetchDictionaries));
                } else {
                    return Optional.of(plistReader);
                }
            }
            case "array" -> {
                return Optional.of(readArray(e, fetchDictionaries).toList());
            }
            default -> {
                return Raw.tryCreate(e);
            }
        }
    }

    private Stream<Object> readArray(Node node, boolean fetchDictionaries) {
        return XmlUtils.toStream(node.getChildNodes()).map(n -> {
            if (n instanceof Element e) {
                return tryCreateValue(e, fetchDictionaries);
            } else {
                return Optional.<Raw>empty();
            }
        }).filter(Optional::isPresent).map(Optional::get);
    }

    private Optional<Node> findNode(String keyName) {
        Objects.requireNonNull(keyName);
        final var query = String.format("*[preceding-sibling::key = \"%s\"][1]", keyName);
        return Optional.ofNullable(toSupplier(() -> {
            return (Node) XPathSingleton.INSTANCE.evaluate(query, root, XPathConstants.NODE);
        }).get());
    }


    private static final class XPathSingleton {
        private static final XPath INSTANCE = XPathFactory.newInstance().newXPath();
    }


    private final Node root;
}
