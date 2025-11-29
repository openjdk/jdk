/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Alibaba Group Holding Limited. All Rights Reserved.
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
package java.time.format;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.constant.ConstantUtils;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.time.DateTimeException;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

final class DateTimePrinterParserFactory {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private DateTimePrinterParserFactory() {
    }

    static final ClassDesc
            CD_StringBuilder               = ClassDesc.ofDescriptor("Ljava/lang/StringBuilder;"),
            CD_DateTimePrinter             = ClassDesc.ofDescriptor("Ljava/time/format/DateTimeFormatterBuilder$DateTimePrinter;"),
            CD_DateTimePrintContext        = ClassDesc.ofDescriptor("Ljava/time/format/DateTimePrintContext;"),
            CD_DateTimePrinterParser       = ClassDesc.ofDescriptor("Ljava/time/format/DateTimeFormatterBuilder$DateTimePrinterParser;"),
            CD_DateTimePrinterParser_array = ClassDesc.ofDescriptor("[Ljava/time/format/DateTimeFormatterBuilder$DateTimePrinterParser;");

    static final MethodTypeDesc
            MTD_format                     = MethodTypeDesc.of(CD_boolean, CD_DateTimePrintContext, CD_StringBuilder, CD_boolean),
            MTD_constructor                = MethodTypeDesc.of(CD_void, CD_DateTimePrinterParser_array);

    /**
     * Creates a DateTimePrinter based on the number of printer parsers.
     * This method efficiently creates specialized formatters for different numbers of printer parsers,
     * optimizing performance by avoiding loops for small numbers of parsers.
     * The manual expansion of cases (rather than using a loop) is designed to allow the TypeProfile optimizer to be effective.
     *
     * <blockquote><pre>
     *  class DateTimeFormatterBuilder$$Printer extends DateTimeFormatterBuilder.DateTimePrinter {
     *      private final DateTimePrinterParser[] printerParsers;

     *      public DateTimeFormatterBuilder$$Printer(DateTimeFormatterBuilder.DateTimePrinterParser[] printerParsers) {
     *          this.printerParsers = printerParsers;
     *          ...
     *      }
     *
     *      public boolean format(DateTimePrintContext context, StringBuilder buf, boolean optional) {
     *          return printerParser[0].format(context, buf, optional)
     *              && printerParser[1].format(context, buf, optional)
     *              && printerParser[2].format(context, buf, optional)
     *                 ...;
     *      }
     *  }
     *
     * </pre></blockquote>
     *
     * @param printerParsers an array of DateTimePrinterParser instances to format
     * @param optional whether the formatters are optional
     * @return a DateTimePrinter that formats according to the provided printer parsers
     */
    static DateTimeFormatterBuilder.DateTimePrinter createFormatter(DateTimeFormatterBuilder.DateTimePrinterParser[] printerParsers, boolean optional) {
        var className = "java.time.format.DateTimeFormatterBuilder$$Printer";
        var classDesc = ConstantUtils.binaryNameToDesc(className);
        byte[] classBytes = ClassFile.of().build(classDesc, clb -> {
            String fieldName = "printerParsers";
            clb.withFlags(ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC)
               .withSuperclass(CD_Object)
               .withInterfaces(clb.constantPool().classEntry(CD_DateTimePrinter))
               .withField(fieldName, CD_DateTimePrinterParser_array, ACC_FINAL | ACC_PRIVATE)
               .withMethodBody(INIT_NAME, MTD_constructor, ACC_PUBLIC, cb -> {
                    int thisSlot    = cb.receiverSlot(),
                        parsersSlot = cb.parameterSlot(0);
                    /*
                     * super()
                     */
                    cb.aload(thisSlot)
                      .invokespecial(CD_Object, INIT_NAME, MTD_void)
                    /*
                     * this.printerParser = printerParsers;
                     */
                      .aload(thisSlot)
                      .aload(parsersSlot)
                      .putfield(classDesc, fieldName, CD_DateTimePrinterParser_array)
                      .return_();
                })
               .withMethodBody("format", MTD_format, ACC_PUBLIC | ACC_FINAL, cb -> {
                    int thisSlot    = cb.receiverSlot(),
                        contextSlot = cb.parameterSlot(0),
                        bufSlot     = cb.parameterSlot(1);
                    /*
                     * return printerParsers[0].format(context, buf, optional)
                     *     && printerParsers[1].format(context, buf, optional)
                     *     && printerParsers[2].format(context, buf, optional)
                     *        ...
                     */
                    Label L0 = cb.newLabel(), L1 = cb.newLabel();
                    for (int i = 0; i < printerParsers.length; ++i) {
                        cb.aload(thisSlot)
                          .getfield(classDesc, fieldName, CD_DateTimePrinterParser_array)
                          .bipush(i)
                          .aaload()
                          .aload(contextSlot)
                          .aload(bufSlot);
                        if (optional) {
                            cb.iconst_1();
                        } else {
                            cb.iconst_0();
                        }
                        cb.invokeinterface(CD_DateTimePrinterParser, "format", MTD_format)
                          .ifeq(L0);
                    }
                    cb.iconst_1()
                      .goto_(L1)
                      .labelBinding(L0)
                      .iconst_0()
                      .labelBinding(L1)
                      .ireturn();
               });
        });
        try {
            var lookupClass = MethodHandles.lookup().lookupClass();
            var loader      = lookupClass.getClassLoader();
            var pd          = (loader != null) ? JLA.protectionDomain(lookupClass) : null;
            var hiddenClass = JLA.defineClass(loader, lookupClass, className, classBytes, pd, true, ACC_FINAL | ACC_PRIVATE | ACC_STATIC, null);
            var constructor = hiddenClass.getConstructor(DateTimeFormatterBuilder.DateTimePrinterParser[].class);
            return (DateTimeFormatterBuilder.DateTimePrinter) constructor.newInstance(new Object[] {printerParsers});
        } catch (Exception e) {
            throw new DateTimeException("Exception while spinning the DateTimePrinter class", e);
        }
    }
}