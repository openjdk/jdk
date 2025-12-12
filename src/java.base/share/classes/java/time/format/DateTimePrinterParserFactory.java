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
import java.lang.reflect.Constructor;
import java.time.DateTimeException;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;
import static java.time.format.DateTimeFormatterBuilder.DateTimePrinter;
import static java.time.format.DateTimeFormatterBuilder.DateTimePrinterParser;
import static java.time.format.DateTimeFormatterBuilder.DateTimeParser;

/**
 * A factory for creating specialized DateTimePrinter and DateTimeParser instances.
 * This class generates optimized implementations of DateTimePrinter and DateTimeParser
 * by dynamically creating classes at runtime using the ClassFile API to avoid loops
 * when processing multiple DateTimePrinterParser instances.
 *
 * <p>The generated classes implement efficient formatting and parsing operations
 * by unrolling loops into explicit sequences of calls, which allows the JVM's
 * TypeProfile optimizer to be more effective.</p>
 *
 * <p>For small numbers of printer parsers, specialized classes are generated to
 * provide optimal performance. For larger numbers, fallback implementations using
 * loops are provided.</p>
 *
 */
final class DateTimePrinterParserFactory {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final AtomicReferenceArray<Constructor<?>> DATETIME_PRINTER_CONSTRUCTORS = new AtomicReferenceArray<>(32);
    private static final AtomicReferenceArray<Constructor<?>> DATETIME_PARSER_CONSTRUCTORS = new AtomicReferenceArray<>(32);

    private static final ClassDesc
            CD_StringBuilder               = ClassDesc.ofDescriptor("Ljava/lang/StringBuilder;"),
            CD_DateTimePrinter             = ClassDesc.ofDescriptor("Ljava/time/format/DateTimeFormatterBuilder$DateTimePrinter;"),
            CD_DateTimePrintContext        = ClassDesc.ofDescriptor("Ljava/time/format/DateTimePrintContext;"),
            CD_DateTimeParser              = ClassDesc.ofDescriptor("Ljava/time/format/DateTimeFormatterBuilder$DateTimeParser;"),
            CD_DateTimeParseContext        = ClassDesc.ofDescriptor("Ljava/time/format/DateTimeParseContext;"),
            CD_DateTimePrinterParser       = ClassDesc.ofDescriptor("Ljava/time/format/DateTimeFormatterBuilder$DateTimePrinterParser;"),
            CD_DateTimePrinterParser_array = ClassDesc.ofDescriptor("[Ljava/time/format/DateTimeFormatterBuilder$DateTimePrinterParser;"),
            CD_CharSequence                = ClassDesc.ofDescriptor("Ljava/lang/CharSequence;");

    private static final MethodTypeDesc
            MTD_format                     = MethodTypeDesc.of(CD_boolean, CD_DateTimePrintContext, CD_StringBuilder, CD_boolean),
            MTD_parse                      = MethodTypeDesc.of(CD_int, CD_DateTimeParseContext, CD_CharSequence, CD_int),
            MTD_constructor                = MethodTypeDesc.of(CD_void, CD_DateTimePrinterParser_array);

    private DateTimePrinterParserFactory() {
    }

    /**
     * Creates a DateTimePrinter instance based on the provided printer parsers.
     * For small numbers of printer parsers (up to 32), this method attempts to
     * create a specialized class that unrolls the loop for better performance.
     * For larger numbers, it creates a fallback implementation using a traditional loop.
     *
     * @param printers an array of DateTimePrinterParser instances to format
     * @param optional whether the formatters are optional
     * @return a DateTimePrinter that formats according to the provided printer parsers
     * @throws DateTimeException if there's an issue instantiating the generated class
     */
    static DateTimePrinter createFormatter(DateTimePrinterParser[] printers, boolean optional) {
        int length = printers.length;
        if (length > DATETIME_PRINTER_CONSTRUCTORS.length()) {
            return (context, buf, opt) -> {
                for (var pp : printers) {
                    if (!pp.format(context, buf, opt)) {
                        return false;
                    }
                }
                return true;
            };
        }
        var printerConstructor = DATETIME_PRINTER_CONSTRUCTORS.get(length - 1);
        try {
            if (printerConstructor == null) {
                printerConstructor = generateFormatterClass(printers, optional)
                        .getDeclaredConstructor(DateTimePrinterParser[].class);
                DATETIME_PRINTER_CONSTRUCTORS.set(length - 1, printerConstructor);
            }
            return (DateTimePrinter) printerConstructor.newInstance(new Object[]{printers});
        } catch (Exception e) {
            throw new DateTimeException("Exception while instantiating the DateTimePrinter class", e);
        }
    }

    /**
     * Generates a specialized DateTimePrinter class based on the provided printer parsers.
     * This method creates a dynamic class that unrolls the formatting loop for optimal
     * performance. The generated class implements the format method by chaining calls
     * to each individual printer parser using explicit conditional logic instead of loops.
     *
     * <p>The following is an example of the generated class structure:</p>
     *
     * <blockquote><pre>
     *  class DateTimeFormatterBuilder$$Printer extends DateTimePrinter {
     *      private final DateTimePrinterParser[] printerParsers;

     *      public DateTimeFormatterBuilder$$Printer(DateTimePrinterParser[] printerParsers) {
     *          this.printerParsers = printerParsers;
     *      }
     *
     *      public boolean format(DateTimePrintContext context, StringBuilder buf, boolean optional) {
     *          return printerParsers[0].format(context, buf, optional)
     *              && printerParsers[1].format(context, buf, optional)
     *              && printerParsers[2].format(context, buf, optional)
     *                 ...;
     *      }
     *  }
     *
     * </pre></blockquote>
     *
     * @param printers an array of DateTimePrinterParser instances to format
     * @param optional whether the formatters are optional
     * @return a Class object representing the generated DateTimePrinter implementation
     * @throws DateTimeException if there's an issue generating the class
     */
    static Class<?> generateFormatterClass(DateTimePrinterParser[] printers, boolean optional) {
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
                       printerSlot = cb.parameterSlot(0);
                   /*
                    * super()
                    */
                   cb.aload(thisSlot)
                     .invokespecial(CD_Object, INIT_NAME, MTD_void)
                     /*
                      * this.printerParser = printerParsers;
                      */
                     .aload(thisSlot)
                     .aload(printerSlot)
                     .putfield(classDesc, fieldName, CD_DateTimePrinterParser_array)
                     .return_();
               })
               .withMethodBody("format", MTD_format, ACC_PUBLIC | ACC_FINAL, cb -> {
                   int thisSlot    = cb.receiverSlot(),
                       contextSlot = cb.parameterSlot(0),
                       bufSlot     = cb.parameterSlot(1),
                       parserSlot  = cb.allocateLocal(TypeKind.REFERENCE);
                   /*
                    * return printers[0].format(context, buf, optional)
                    *     && printers[1].format(context, buf, optional)
                    *     && printers[2].format(context, buf, optional)
                    *        ...
                    */
                   Label L0 = cb.newLabel(), L1 = cb.newLabel();
                   cb.aload(thisSlot)
                           .getfield(classDesc, fieldName, CD_DateTimePrinterParser_array)
                           .astore(parserSlot);
                   for (int i = 0; i < printers.length; ++i) {
                       cb.aload(parserSlot)
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
            return defineClass(className, classBytes);
        } catch (Exception e) {
            throw new DateTimeException("Exception while spinning the DateTimePrinter class", e);
        }
    }

    private static Class<?> defineClass(String className, byte[] classBytes) {
        var lookupClass = MethodHandles.lookup().lookupClass();
        var loader      = lookupClass.getClassLoader();
        var pd          = (loader != null) ? JLA.protectionDomain(lookupClass) : null;
        return JLA.defineClass(loader, lookupClass, className, classBytes, pd, true, ACC_FINAL | ACC_PRIVATE | ACC_STATIC, null);
    }

    /**
     * Generates a specialized DateTimeParser class based on the provided printer parsers.
     * This method creates a dynamic class that unrolls the parsing loop for optimal
     * performance. The generated class implements the parse method by chaining calls
     * to each individual printer parser using explicit conditional logic instead of loops.
     *
     * <p>The following is an example of the generated class structure:</p>
     *
     * <blockquote><pre>
     *  class DateTimeFormatterBuilder$$Parser extends DateTimeParser {
     *      private final DateTimePrinterParser[] printerParsers;
     *
     *      public DateTimeFormatterBuilder$$Parser(DateTimePrinterParser[] printerParsers) {
     *          this.printerParsers = printerParsers;
     *      }
     *
     *      public int parse(DateTimeParseContext context, CharSequence text, int position) {
     *          if ((position = printerParsers[0].parse(context, text, position)) >= 0) {
     *              if ((position = printerParsers[1].parse(context, text, position)) >= 0) {
     *                  if ((position = printerParsers[2].parse(context, text, position)) >= 0) {
     *                      ...
     *                  }
     *              }
     *          }
     *          return position;
     *      }
     *  }
     *
     * </pre></blockquote>
     *
     * @param printers an array of DateTimePrinterParser instances to parse
     * @param optional whether the parsers are optional
     * @return a Class object representing the generated DateTimeParser implementation
     * @throws DateTimeException if there's an issue generating the class
     */
    static Class<?> generateParserClass(DateTimePrinterParser[] printers, boolean optional) {
        var className = "java.time.format.DateTimeFormatterBuilder$$Parser";
        var classDesc = ConstantUtils.binaryNameToDesc(className);
        byte[] classBytes = ClassFile.of().build(classDesc, clb -> {
            String fieldName = "printerParsers";
            clb.withFlags(ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC)
               .withSuperclass(CD_Object)
               .withInterfaces(clb.constantPool().classEntry(CD_DateTimeParser))
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
               .withMethodBody("parse", MTD_parse, ACC_PUBLIC | ACC_FINAL, cb -> {
                        int thisSlot     = cb.receiverSlot(),
                            contextSlot  = cb.parameterSlot(0),
                            textSlot     = cb.parameterSlot(1),
                            positionSlot = cb.parameterSlot(2),
                            parserSlot   = cb.allocateLocal(TypeKind.REFERENCE);
                        /*
                         *  var printerParsers = this.printerParsers;
                         *  if ((position = printerParsers[0].parse(context, text, position)) >= 0) {
                         *      if ((position = printerParsers[1].parse(context, text, position)) >= 0) {
                         *          if ((position = printerParsers[2].parse(context, text, position)) >= 0) {
                         *              ...
                         *          }
                         *      }
                         * }
                         * return position;
                         */
                        var L0 = cb.newLabel();
                        cb.aload(thisSlot)
                          .getfield(classDesc, fieldName, CD_DateTimePrinterParser_array)
                          .astore(parserSlot);
                        for (int i = 0; i < printers.length; ++i) {
                            cb.aload(parserSlot)
                              .bipush(i)
                              .aaload()
                              .aload(contextSlot)
                              .aload(textSlot)
                              .iload(positionSlot)
                              .invokeinterface(CD_DateTimePrinterParser, "parse", MTD_parse)
                              .dup()
                              .istore(positionSlot)
                              .iflt(L0);
                        }
                        cb.labelBinding(L0)
                          .iload(positionSlot)
                          .ireturn();
                    });
        });
        try {
            return defineClass(className, classBytes);
        } catch (Exception e) {
            throw new DateTimeException("Exception while spinning the DateTimePrinter class", e);
        }
    }

    /**
     * Creates a DateTimeParser instance based on the provided printer parsers.
     * For small numbers of printer parsers (up to 32), this method attempts to
     * create a specialized class that unrolls the loop for better performance.
     * For larger numbers, it creates a fallback implementation using a traditional loop.
     * If the optional flag is true, the returned parser is wrapped with optional handling logic.
     *
     * @param printerParsers an array of DateTimePrinterParser instances to parse
     * @param optional whether the parsers are optional
     * @return a DateTimeParser that parses according to the provided printer parsers
     * @throws DateTimeException if there's an issue instantiating the generated class
     */
    static DateTimeParser createParser(DateTimePrinterParser[] printerParsers, boolean optional) {
        int length = printerParsers.length;
        DateTimeParser parser;
        if (length > DATETIME_PRINTER_CONSTRUCTORS.length()) {
            parser = (context, text, position) -> {
                for (var pp : printerParsers) {
                    position = pp.parse(context, text, position);
                    if (position < 0) {
                        break;
                    }
                }
                return position;
            };
        } else {
            var parserConstructor = DATETIME_PARSER_CONSTRUCTORS.get(length - 1);
            try {
                if (parserConstructor == null) {
                    parserConstructor = generateParserClass(printerParsers, optional)
                            .getDeclaredConstructor(DateTimePrinterParser[].class);
                    DATETIME_PARSER_CONSTRUCTORS.set(length - 1, parserConstructor);
                }
                parser = (DateTimeParser) parserConstructor.newInstance(new Object[]{printerParsers});
            } catch (Exception e) {
                throw new DateTimeException("Exception while instantiating the DateTimeParser class", e);
            }
        }

        if (!optional) {
            return parser;
        }
        // Wrap the parser with optional handling logic
        return (context, text, position) -> {
            context.startOptional();
            int pos = position;
            pos = parser.parse(context, text, pos);
            if (pos < 0) {
                context.endOptional(false);
                return position;  // return original position
            }
            context.endOptional(true);
            return pos;
        };
    }
}