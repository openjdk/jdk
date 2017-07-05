/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Test a pool containing external files.
 * @author Andrei Eremeev
 * @modules jdk.jlink/jdk.tools.jlink.internal
 * @run build ImageFilePoolTest
 * @run main ImageFilePoolTest
 */

import java.io.ByteArrayInputStream;
import jdk.tools.jlink.internal.PoolImpl;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;
import jdk.tools.jlink.plugin.Pool.ModuleDataType;
import jdk.tools.jlink.plugin.Pool.Visitor;

public class ImageFilePoolTest {
    public static void main(String[] args) throws Exception {
        new ImageFilePoolTest().test();
    }

    public void test() throws Exception {
        checkNegative();
        checkVisitor();
    }

    private static final String SUFFIX = "END";

    private void checkVisitor() throws Exception {
        Pool input = new PoolImpl();
        for (int i = 0; i < 1000; ++i) {
            String module = "module" + (i / 100);
            input.add(new InMemoryImageFile(module, "/" + module + "/java/class" + i,
                    ModuleDataType.CONFIG, "class" + i));
        }
        if (input.getContent().size() != 1000) {
            throw new AssertionError();
        }
        Pool output = new PoolImpl();
        ResourceVisitor visitor = new ResourceVisitor();
        input.visit(visitor, output);
        if (visitor.getAmountBefore() == 0) {
            throw new AssertionError("Resources not found");
        }
        if (visitor.getAmountBefore() != input.getContent().size()) {
            throw new AssertionError("Number of visited resources. Expected: " +
                    visitor.getAmountBefore() + ", got: " + input.getContent().size());
        }
        if (visitor.getAmountAfter() != output.getContent().size()) {
            throw new AssertionError("Number of added resources. Expected: " +
                    visitor.getAmountAfter() + ", got: " + output.getContent().size());
        }
        for (ModuleData outFile : output.getContent()) {
            String path = outFile.getPath().replaceAll(SUFFIX + "$", "");
            ModuleData inFile = input.get(path);
            if (inFile == null) {
                throw new AssertionError("Unknown resource: " + path);
            }
        }
    }

    private static class ResourceVisitor implements Visitor {

        private int amountBefore;
        private int amountAfter;

        @Override
        public ModuleData visit(ModuleData file) {
            int index = ++amountBefore % 3;
            switch (index) {
                case 0:
                    ++amountAfter;
                    return new InMemoryImageFile(file.getModule(), file.getPath() + SUFFIX,
                            file.getType(), file.getPath());
                case 1:
                    ++amountAfter;
                    return new InMemoryImageFile(file.getModule(), file.getPath(),
                            file.getType(), file.getPath());
            }
            return null;
        }

        public int getAmountAfter() {
            return amountAfter;
        }

        public int getAmountBefore() {
            return amountBefore;
        }
    }

    private void checkNegative() throws Exception {
        PoolImpl input = new PoolImpl();
        try {
            input.add(null);
            throw new AssertionError("NullPointerException is not thrown");
        } catch (NullPointerException e) {
            // expected
        }
        try {
            input.contains(null);
            throw new AssertionError("NullPointerException is not thrown");
        } catch (NullPointerException e) {
            // expected
        }
        if (input.get("unknown") != null) {
            throw new AssertionError("ImageFilePool does not return null for unknown file");
        }
        if (input.contains(new InMemoryImageFile("", "unknown", ModuleDataType.CONFIG, "unknown"))) {
            throw new AssertionError("'contain' returns true for unknown file");
        }
        input.add(new InMemoryImageFile("", "/aaa/bbb", ModuleDataType.CONFIG, ""));
        try {
            input.add(new InMemoryImageFile("", "/aaa/bbb", ModuleDataType.CONFIG, ""));
            throw new AssertionError("Exception expected");
        } catch (Exception e) {
            // expected
        }
        input.setReadOnly();
        try {
            input.add(new InMemoryImageFile("", "/aaa/ccc", ModuleDataType.CONFIG, ""));
            throw new AssertionError("Exception expected");
        } catch (Exception e) {
            // expected
        }
    }

    private static class InMemoryImageFile extends ModuleData {
        public InMemoryImageFile(String module, String path, ModuleDataType type, String content) {
            super(module, path, type, new ByteArrayInputStream(content.getBytes()), content.getBytes().length);
        }
    }
}
