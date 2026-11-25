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

import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Destination;

/*
 * @test
 * @bug 8397801
 * @key printer
 * @requires (os.family == "windows")
 * @run main/othervm  --enable-native-access=ALL-UNNAMED PrinterDCLeak
 */

public class PrinterDCLeak {

    private static final int GR_GDIOBJECTS = 0;
    private static final int ITERATIONS = 30;
    private static int TEST_ITERATION = 10;
    private static MethodHandle getCurrentProcess, getGuiResources;

    public static void main(String[] args) throws Throwable {
        initNative();
        PrintService ps = PrintServiceLookup.lookupDefaultPrintService();
        if (ps == null) {
            throw new RuntimeException("No print service found");
        }
        System.out.println("Using printer: " + ps.getName());

        System.out.println("GDI objects before warm up: " + gdiObjects());
        print(ps); // warm up.
        int startCnt = gdiObjects();
        System.out.println("GDI objects after warm up: " + startCnt);

        int currCnt = startCnt;
        int growCnt = 0;
        for (int i = 1; i <= ITERATIONS; i++) {
            print(ps);
            if (i % TEST_ITERATION == 0) {
                System.gc();
                Thread.sleep(500); // let the Disposer run
                int prevCnt = currCnt;
                currCnt = gdiObjects();
                if (currCnt > prevCnt) growCnt++;
                System.out.println("After " + i + " jobs, GDI objects: " + currCnt);
            }
        }
        if (((currCnt - startCnt) > ITERATIONS) && (growCnt == ITERATIONS / TEST_ITERATION)) {
            throw new RuntimeException("Leak cnt is " + (currCnt - startCnt));
        }
    }

    private static void print(PrintService ps) {
        File out = null;
        try {
            out = File.createTempFile("dcleak", ".prn");
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(ps);
            job.setPrintable((g, pf, page) -> {
                if (page > 0) return Printable.NO_SUCH_PAGE;
                g.drawString("DC leak test", 100, 100);
                return Printable.PAGE_EXISTS;
            });
            PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
            attrs.add(new Destination(out.toURI()));
            job.print(attrs);
        } catch (Exception e) {
        } finally {
            if (out != null) {
                out.delete();
            }
        }
    }

    private static void initNative() {
        Linker linker = Linker.nativeLinker();
        SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
        SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
        getCurrentProcess = linker.downcallHandle(
                kernel32.find("GetCurrentProcess").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS));
        getGuiResources = linker.downcallHandle(
                user32.find("GetGuiResources").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    }

    private static int gdiObjects() throws Throwable {
        MemorySegment proc = (MemorySegment) getCurrentProcess.invokeExact();
        return (int) getGuiResources.invokeExact(proc, GR_GDIOBJECTS);
    }
}
