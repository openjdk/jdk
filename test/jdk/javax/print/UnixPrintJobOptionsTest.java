/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, BELLSOFT. All rights reserved.
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

import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashDocAttributeSet;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttribute;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.NumberUp;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.Sides;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/*
 * @test
 * @bug 8349350
 * @key printer
 * @requires (os.family == "linux" | os.family == "mac")
 * @summary lpr command syntax for options. lpr [ -o option[=value] ]
 * @run main/manual/othervm -Dsun.print.ippdebug=true UnixPrintJobOptionsTest
 */

public class UnixPrintJobOptionsTest {

    static final DocFlavor docFlavor = DocFlavor.STRING.TEXT_PLAIN;
    static final List<Class<? extends PrintRequestAttribute>> requiredAttributes = new ArrayList<>();
    static {
        requiredAttributes.add(NumberUp.class);
        requiredAttributes.add(OrientationRequested.class);
        requiredAttributes.add(Sides.class);
    }
    static Map<PrintService,Class<? extends PrintRequestAttribute>[]> printerSupportedAttrs = new HashMap<>();

    public static void main(String[] args) {
        initializeTestPrintServices();
        if (printerSupportedAttrs.isEmpty()) {
            System.out.println("Print services not found");
            return;
        }

        PrintStream origPrintStream = System.out;
        ByteArrayOutputStream debugOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(debugOutputStream);
        System.setOut(printStream);
        try {
            if (!executeTest(debugOutputStream)) {
                System.out.println("Acceptable print service not found");
            }
        } finally {
            System.setOut(origPrintStream);
            System.out.println(debugOutputStream.toString());
        }
    }

    /**
     * Tries to execute the test on one of the available print services
     * @param debugOutputStream 'standard' output stream
     * @return true - test was run and pass, false - test was not run
     */
    private static boolean executeTest(ByteArrayOutputStream debugOutputStream) {
        for (PrintService testPrintService : printerSupportedAttrs.keySet()) {
            String patternStr = "/usr/bin/lpr\\s+.*";
            PrintRequestAttributeSet attributeSet = new HashPrintRequestAttributeSet();
            for (Class<? extends PrintRequestAttribute> attrClass : printerSupportedAttrs.get(testPrintService)) {
                Object supportedValues = testPrintService
                        .getSupportedAttributeValues(attrClass, docFlavor, attributeSet);
                if (supportedValues == null || ((Object[]) supportedValues).length == 0) {
                    continue;
                }
                if (attrClass == NumberUp.class) {
                    NumberUp numberUp = ((NumberUp[]) supportedValues)[0];
                    patternStr = patternStr + "\\s-o number-up=" + numberUp.getValue();
                    attributeSet.add(numberUp);
                } else if (attrClass == OrientationRequested.class) {
                    for (OrientationRequested orientationRequested : ((OrientationRequested[]) supportedValues)) {
                        if (orientationRequested != OrientationRequested.PORTRAIT) {
                            patternStr = patternStr + "\\s-o orientation-requested=" + orientationRequested.getValue();
                            attributeSet.add(orientationRequested);
                            break;
                        }
                    }
                } else if (attrClass == Sides.class) {
                    Sides s = ((Sides[]) supportedValues)[0];
                    patternStr = patternStr + "\\s-o sides=" + s.toString();
                    attributeSet.add(s);
                }
            }

            if (attributeSet.size() < 2) {
                continue;
            }
            System.out.println("Debug pattern: " + patternStr);
            DocPrintJob docPrintJob = testPrintService.createPrintJob();
            try {
                docPrintJob.print(new SimpleDoc("UnixPrintJob options test",
                        docFlavor, new HashDocAttributeSet()), attributeSet);
            } catch (PrintException ex) {
                throw new RuntimeException(ex);
            }
            String debug = debugOutputStream.toString();
            Pattern pattern = Pattern.compile(patternStr);
            if (!pattern.matcher(debug).find()) {
                throw new RuntimeException("Output does not contain necessary options");
            } else {
                return true;
            }
        }
        return false;
    }

    private static void initializeTestPrintServices() {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(docFlavor, null);
        if (printServices == null || printServices.length == 0) {
            return;
        }

        List<Class<? extends PrintRequestAttribute>> printerAttrs;
        for (PrintService ps : printServices) {
            printerAttrs = new ArrayList<>();
            for (Class<? extends PrintRequestAttribute> attr : requiredAttributes) {
                if (ps.isAttributeCategorySupported(attr)) {
                    printerAttrs.add(attr);
                }
            }
            if (printerAttrs.size() > 1) {
                printerSupportedAttrs.put(ps, printerAttrs.toArray(new Class[0]));
            }
        }
    }

}
