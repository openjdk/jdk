/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.internal.jrtfs;

import jdk.internal.jimage.ImageReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * Exposes enough of the SystemImage API of the ExplodedImage class for testing.
 */
public final class ImageHelper {
    // Eventually this could also be a JImage based SystemImage for common tests.
    private final SystemImage image;

    public static ImageHelper createExplodedImage(Path moduleDir) throws IOException {
        return new ImageHelper(new ExplodedImage(moduleDir));
    }

    private ImageHelper(SystemImage image) throws IOException {
        this.image = image;
    }

    public Node findNode(String path) {
        try {
            ImageReader.Node node = image.findNode(path);
            return node != null ? new Node(node) : null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public final class Node {
        private final ImageReader.Node node;

        public Node(ImageReader.Node node) {
            this.node = Objects.requireNonNull(node);
        }

        public String getName() {
            return node.getName();
        }

        public String getLocalName() {
            return extractLocalName(node.getName());
        }

        public boolean isDirectory() {
            return node.isDirectory();
        }

        public boolean isLink() {
            return node.isLink();
        }

        public Node resolveLink(boolean recursive) {
            return new Node(node.resolveLink(recursive));
        }

        private Stream<String> childNames() {
            return node.getChildren().stream().map(ImageReader.Node::getName);
        }

        public Set<String> getChildNames() {
            return childNames().collect(toUnmodifiableSet());
        }

        public Set<String> getLocalChildNames() {
            return childNames().map(Node::extractLocalName).collect(toUnmodifiableSet());
        }

        public byte[] getResource() throws IOException {
            return image.getResource(node);
        }

        // "/foo/bar/baz" -> "baz", "/" -> "" (only for root)
        private static String extractLocalName(String name) {
            return name.substring(name.lastIndexOf('/') + 1);
        }

        @Override
        public boolean equals(Object obj) {
            return (obj instanceof Node) && ((Node) obj).node.equals(node);
        }

        @Override
        public int hashCode() {
            return node.hashCode() ^ 0x55555555;
        }
    }
}
