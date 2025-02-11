/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, BELLSOFT. All rights reserved.
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

import java.util.Arrays;
import java.util.Set;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.Attribute;
import javax.print.attribute.standard.OutputBin;

/*
 * @test
 * @bug 8314070
 * @key printer
 * @summary javax.print: Support IPP output-bin attribute extension
 */

public class CheckSupportedOutputBinsTest {

    public static void main(String[] args) throws Exception {

        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);

        if (services == null) {
            System.out.printf("Skip the test as there are no available PrintServices.%n");
            return;
        }

        System.out.printf("Print services: %d%n", services.length);

        for (PrintService service : services) {
            checkSupportedOutputBins(service);
        }
    }

    private static void checkSupportedOutputBins(PrintService service) throws Exception {

        System.out.printf("Check printService: %s%n", service);

        boolean isOutputBinCategorySupported = service.isAttributeCategorySupported(OutputBin.class);
        OutputBin defaultOutputBin = (OutputBin) service.getDefaultAttributeValue(OutputBin.class);
        Set<Class<?>> supportedAttributeCategories = Set.of(service.getSupportedAttributeCategories());
        OutputBin[] supportedOutputBins = (OutputBin[]) service
                .getSupportedAttributeValues(OutputBin.class, null, null);

        if (!isOutputBinCategorySupported) {

            if (supportedAttributeCategories.contains(OutputBin.class)) {
                throw new Exception("OutputBin category is not supported" +
                        " and supported attribute categories contain OutputBin.class.");
            }

            if (defaultOutputBin != null) {
                throw new Exception("OutputBin category is not supported" +
                        " and the default output bin is not null.");
            }

            if (supportedOutputBins != null && supportedOutputBins.length > 0) {
                throw new Exception("OutputBin category is not supported" +
                        " and array of supported output bins is not null or its size is not zero.");
            }

            return;
        }

        if (!supportedAttributeCategories.contains(OutputBin.class)) {
            throw new Exception("OutputBin category is supported" +
                    " and supported attribute categories do not contain OutputBin.class.");
        }

        if (defaultOutputBin == null) {
            throw new Exception("OutputBin category is supported" +
                    " and the default output bin is null.");
        }

        if (supportedOutputBins == null || supportedOutputBins.length == 0) {
            throw new Exception("OutputBin category is supported" +
                    " and PrintService.getSupportedAttributeValues() returns null or an array with zero elements.");
        }

        if (!service.isAttributeValueSupported(defaultOutputBin, null, null)) {
            throw new Exception("OutputBin category is supported" +
                    " and the default output bin " + defaultOutputBin + " is not supported");
        }

        for (OutputBin outputBin : supportedOutputBins) {
            if (!service.isAttributeValueSupported(outputBin, null, null)) {
                throw new Exception("OutputBin category is supported" +
                        " and the output bin " + outputBin + " from supported attribute values" +
                        " is not supported");
            }
        }
    }
}