/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jimage.ImageReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @test
 * @bug 8247407
 * @summary Test that ImageReader doesn't create a Directory node with duplicate children
 * @modules java.base/jdk.internal.jimage
 * @run main ImageReaderDuplicateChildNodesTest
 */
public class ImageReaderDuplicateChildNodesTest {

    /**
     * Uses the ImageReader to open and read the JAVA_HOME/lib/modules image. Tests that the
     * {@link ImageReader#findNode(String)} corresponding to a directory doesn't return a node
     * with duplicate children in it.
     */
    public static void main(final String[] args) throws Exception {
        final Path imagePath = Paths.get(System.getProperty("java.home"), "lib", "modules");
        if (!Files.exists(imagePath)) {
            // skip the testing in the absence of the image file
            System.err.println("Skipping test since " + imagePath + " is absent");
            return;
        }
        System.out.println("Running test against image " + imagePath);
        final String integersParentResource = "/modules/java.base/java/lang";
        final String integerResource = integersParentResource + "/Integer.class";
        try (final ImageReader reader = ImageReader.open(imagePath)) {
            // find the child node/resource first
            final ImageReader.Node integerNode = reader.findNode(integerResource);
            if (integerNode == null) {
                throw new RuntimeException("ImageReader could not locate " + integerResource
                        + " in " + imagePath);
            }
            // now find the parent node (which will represent a directory)
            final ImageReader.Node parent = reader.findNode(integersParentResource);
            if (parent == null) {
                throw new RuntimeException("ImageReader could not locate " + integersParentResource
                        + " in " + imagePath);
            }
            // now verify that the parent node which is a directory, doesn't have duplicate children
            final List<ImageReader.Node> children = parent.getChildren();
            if (children == null || children.isEmpty()) {
                throw new RuntimeException("ImageReader did not return any child resources under "
                        + integersParentResource + " in " + imagePath);
            }
            final Set<ImageReader.Node> uniqueChildren = new HashSet<>();
            for (final ImageReader.Node child : children) {
                final boolean unique = uniqueChildren.add(child);
                if (!unique) {
                    throw new RuntimeException("ImageReader returned duplicate child resource "
                            + child + " under " + parent + " from image " + imagePath);
                }
            }
        }
    }
}
