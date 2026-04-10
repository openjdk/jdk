/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

String extractMethod(String line) {
    line = line.substring(0, line.indexOf('('));
    return line.substring(line.lastIndexOf(' ') + 1);
}

void main(String[] args) throws IOException {
    if (args.length < 1) {
        IO.println("Usage: java AddGLE my_h.java function_name...");
        return;
    }
    Map<String, String> all = Files.lines(Path.of(args[0]))
            .filter(s -> s.contains("public static") && s.endsWith("{") && !s.contains("$"))
            .collect(Collectors.toMap(s -> extractMethod(s), String::trim));

    System.out.println("""
                 private static final MemoryLayout CAPTURE_LAYOUT =
                         Linker.Option.captureStateLayout();
                 private static final BufferStack POOL =
                         BufferStack.of(CAPTURE_LAYOUT);
                 private static final Linker.Option CCS_GLE =
                         Linker.Option.captureCallState("GetLastError");
            """);
    for (int i = 1; i < args.length; i++) {
        String name = args[i];
        String line = all.get(name);
        String argList = line.substring(line.indexOf('(') + 1, line.indexOf(')'));
        String argListWithoutTypes = argList.replaceAll("\\w+ (\\w+)", "$1");
        if (line == null) {
            System.out.println("    // Cannot find " + name);
            continue;
        }
        System.out.printf("""
                    private static final MethodHandle MH_%1$sGLE = Linker.nativeLinker()
                            .downcallHandle(%1$s$address(), %1$s$descriptor(), CCS_GLE);

                    private int %1$sGLE(%2$s) {
                        try (var arena = POOL.pushFrame(CAPTURE_LAYOUT.byteSize() + 64,
                                CAPTURE_LAYOUT.byteAlignment())) {
                            MemorySegment cs = arena.allocate(CAPTURE_LAYOUT);
                            int output = (int) MH_%1$sGLE.invokeExact(
                                    cs, %3$s);
                            if (output == 0) {
                                if (debug) {
                                    DisplayErrorText(arena, "%1$s", cs);
                                }
                            }
                            return output;
                        } catch (Error | RuntimeException ex) {
                            throw ex;
                        } catch (Throwable t) {
                            throw new AssertionError("should not reach here", t);
                        }
                    }
                """, name, argList, argListWithoutTypes);
        System.out.println();
    }
}
