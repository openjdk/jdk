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
import java.util.Optional;
import java.util.function.Function;
import jdk.tools.jlink.internal.ModuleEntryFactory;
import jdk.tools.jlink.internal.ModulePoolImpl;
import jdk.tools.jlink.plugin.ModuleEntry;
import jdk.tools.jlink.plugin.ModulePool;

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
        ModulePool input = new ModulePoolImpl();
        for (int i = 0; i < 1000; ++i) {
            String module = "module" + (i / 100);
            input.add(newInMemoryImageFile("/" + module + "/java/class" + i,
                    ModuleEntry.Type.CONFIG, "class" + i));
        }
        if (input.getEntryCount() != 1000) {
            throw new AssertionError();
        }
        ModulePool output = new ModulePoolImpl();
        ResourceVisitor visitor = new ResourceVisitor();
        input.transformAndCopy(visitor, output);
        if (visitor.getAmountBefore() == 0) {
            throw new AssertionError("Resources not found");
        }
        if (visitor.getAmountBefore() != input.getEntryCount()) {
            throw new AssertionError("Number of visited resources. Expected: " +
                    visitor.getAmountBefore() + ", got: " + input.getEntryCount());
        }
        if (visitor.getAmountAfter() != output.getEntryCount()) {
            throw new AssertionError("Number of added resources. Expected: " +
                    visitor.getAmountAfter() + ", got: " + output.getEntryCount());
        }
        output.entries().forEach(outFile -> {
            String path = outFile.getPath().replaceAll(SUFFIX + "$", "");
            Optional<ModuleEntry> inFile = input.findEntry(path);
            if (!inFile.isPresent()) {
                throw new AssertionError("Unknown resource: " + path);
            }
        });
    }

    private static class ResourceVisitor implements Function<ModuleEntry, ModuleEntry> {

        private int amountBefore;
        private int amountAfter;

        @Override
        public ModuleEntry apply(ModuleEntry file) {
            int index = ++amountBefore % 3;
            switch (index) {
                case 0:
                    ++amountAfter;
                    return newInMemoryImageFile(file.getPath() + SUFFIX,
                            file.getType(), file.getPath());
                case 1:
                    ++amountAfter;
                    return newInMemoryImageFile(file.getPath(),
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
        ModulePoolImpl input = new ModulePoolImpl();
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
        if (input.findEntry("unknown").isPresent()) {
            throw new AssertionError("ImageFileModulePool does not return null for unknown file");
        }
        if (input.contains(newInMemoryImageFile("/unknown/foo", ModuleEntry.Type.CONFIG, "unknown"))) {
            throw new AssertionError("'contain' returns true for /unknown/foo file");
        }
        input.add(newInMemoryImageFile("/aaa/bbb", ModuleEntry.Type.CONFIG, ""));
        try {
            input.add(newInMemoryImageFile("/aaa/bbb", ModuleEntry.Type.CONFIG, ""));
            throw new AssertionError("Exception expected");
        } catch (Exception e) {
            // expected
        }
        input.setReadOnly();
        try {
            input.add(newInMemoryImageFile("/aaa/ccc", ModuleEntry.Type.CONFIG, ""));
            throw new AssertionError("Exception expected");
        } catch (Exception e) {
            // expected
        }
    }

    private static ModuleEntry newInMemoryImageFile(String path,
            ModuleEntry.Type type, String content) {
        return ModuleEntryFactory.create(path, type, content.getBytes());
    }
}
