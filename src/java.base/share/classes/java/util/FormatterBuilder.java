/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

import java.io.IOException;
import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.Lookup;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.spi.NumberFormatProvider;
import java.util.FormatItem.*;
import java.util.Formatter.*;

import jdk.internal.util.FormatConcatItem;

import sun.invoke.util.Wrapper;
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.ResourceBundleBasedAdapter;

import static java.util.Formatter.Conversion.*;
import static java.util.Formatter.Flags.*;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;

/**
 * This package private class supports the construction of the {@link MethodHandle}
 * used by {@link FormatProcessor}.
 *
 * @since 21
 *
 * Warning: This class is part of PreviewFeature.Feature.STRING_TEMPLATES.
 *          Do not rely on its availability.
 */
final class FormatterBuilder {
    private static final Lookup LOOKUP = lookup();

    private final String format;
    private final Locale locale;
    private final Class<?>[] ptypes;
    private final DecimalFormatSymbols dfs;
    private final boolean isGenericDFS;

    FormatterBuilder(String format, Locale locale, Class<?>[] ptypes) {
        this.format = format;
        this.locale = locale;
        this.ptypes = ptypes;
        this.dfs = DecimalFormatSymbols.getInstance(locale);
        this.isGenericDFS = isGenericDFS(this.dfs);
    }

    private static boolean isGenericDFS(DecimalFormatSymbols dfs) {
        return dfs.getZeroDigit() == '0' &&
               dfs.getDecimalSeparator() == '.' &&
               dfs.getGroupingSeparator() == ',' &&
               dfs.getMinusSign() == '-';
    }

    private static Class<?> mapType(Class<?> type) {
        return type.isPrimitive() || type == String.class ? type : Object.class;
    }

    private static MethodHandle findStringConcatItemConstructor(Class<?> cls,
                                          Class<?>... ptypes) {
        MethodType methodType = methodType(void.class, ptypes);

        try {
            MethodHandle mh = LOOKUP.findConstructor(cls, methodType);

            return mh.asType(mh.type().changeReturnType(FormatConcatItem.class));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Missing constructor in " +
                    cls + ": " + methodType);
        }
    }

    private static MethodHandle findMethod(Class<?> cls, String name,
                                           Class<?> rType, Class<?>... ptypes) {
        MethodType methodType = methodType(rType, ptypes);

        try {
            return LOOKUP.findVirtual(cls, name, methodType);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Missing method in " +
                    cls + ": " + name + " " + methodType);
        }
    }

    private static MethodHandle findStaticMethod(Class<?> cls, String name,
                                                 Class<?> rType, Class<?>... ptypes) {
        MethodType methodType = methodType(rType, ptypes);

        try {
            return LOOKUP.findStatic(cls, name, methodType);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Missing static method in " +
                    cls + ": " + name + " " + methodType);
        }
    }

    private static final MethodHandle FIDecimal_MH =
            findStringConcatItemConstructor(FormatItemDecimal.class,
                    DecimalFormatSymbols.class, int.class, char.class, boolean.class,
                    int.class, long.class);

    private static final MethodHandle FIHexadecimal_MH =
            findStringConcatItemConstructor(FormatItemHexadecimal.class,
                    int.class, boolean.class, long.class);

    private static final MethodHandle FIOctal_MH =
            findStringConcatItemConstructor(FormatItemOctal.class,
                    int.class, boolean.class, long.class);

    private static final MethodHandle FIBoolean_MH =
            findStringConcatItemConstructor(FormatItemBoolean.class,
                    boolean.class);

    private static final MethodHandle FICharacter_MH =
            findStringConcatItemConstructor(FormatItemCharacter.class,
                    char.class);

    private static final MethodHandle FIString_MH =
            findStringConcatItemConstructor(FormatItemString.class,
                    String.class);

    private static final MethodHandle FIFormatSpecifier_MH =
            findStringConcatItemConstructor(FormatItemFormatSpecifier.class,
                    FormatSpecifier.class, Locale.class, Object.class);

    private static final MethodHandle FIFormattable_MH =
            findStringConcatItemConstructor(FormatItemFormatSpecifier.class,
                    Locale.class, int.class, int.class, int.class,
                    Formattable.class);

    private static final MethodHandle FIFillLeft_MH =
             findStringConcatItemConstructor(FormatItemFillLeft.class,
                    int.class, FormatConcatItem.class);

    private static final MethodHandle FIFillRight_MH =
            findStringConcatItemConstructor(FormatItemFillRight.class,
                     int.class, FormatConcatItem.class);

    private static final MethodHandle FINull_MH =
            findStringConcatItemConstructor(FormatItemNull.class);

    private static final MethodHandle NullCheck_MH =
            findStaticMethod(FormatterBuilder.class, "nullCheck", boolean.class,
                    Object.class);

    private static final MethodHandle FormattableCheck_MH =
            findStaticMethod(FormatterBuilder.class, "formattableCheck", boolean.class,
                    Object.class);

    private static final MethodHandle ToLong_MH =
            findStaticMethod(java.util.FormatterBuilder.class, "toLong", long.class,
                    int.class);

    private static final MethodHandle ToString_MH =
            findStaticMethod(String.class, "valueOf", String.class,
                    Object.class);

    private static final MethodHandle HashCode_MH =
            findStaticMethod(Objects.class, "hashCode", int.class,
                    Object.class);

    private static boolean nullCheck(Object object) {
        return object == null;
    }

    private static boolean formattableCheck(Object object) {
        return Formattable.class.isAssignableFrom(object.getClass());
    }

    private static long toLong(int value) {
        return (long)value & 0xFFFFFFFFL;
    }

    private static boolean isFlag(int value, int flags) {
        return (value & flags) != 0;
    }

    private static boolean validFlags(int value, int flags) {
        return (value & ~flags) == 0;
    }

    private static int groupSize(Locale locale, DecimalFormatSymbols dfs) {
        if (isGenericDFS(dfs)) {
            return 3;
        }

        DecimalFormat df;
        NumberFormat nf = NumberFormat.getNumberInstance(locale);

        if (nf instanceof DecimalFormat) {
            df = (DecimalFormat)nf;
        } else {
            LocaleProviderAdapter adapter = LocaleProviderAdapter
                    .getAdapter(NumberFormatProvider.class, locale);

            if (!(adapter instanceof ResourceBundleBasedAdapter)) {
                adapter = LocaleProviderAdapter.getResourceBundleBased();
            }

            String[] all = adapter.getLocaleResources(locale)
                    .getNumberPatterns();

            df = new DecimalFormat(all[0], dfs);
        }

        return df.isGroupingUsed() ? df.getGroupingSize() : 0;
    }

    private MethodHandle formatSpecifier(FormatSpecifier fs, Class<?> ptype) {
        boolean isPrimitive = ptype.isPrimitive();
        MethodHandle mh = identity(ptype);
        MethodType mt = mh.type();

//cannot cast to primitive types as it breaks null values formatting
//        if (ptype == byte.class || ptype == short.class ||
//            ptype == Byte.class || ptype == Short.class ||
//            ptype == Integer.class) {
//            mt = mt.changeReturnType(int.class);
//        } else if (ptype == Long.class) {
//            mt = mt.changeReturnType(long.class);
//        } else if (ptype == float.class || ptype == Float.class ||
//                   ptype == Double.class) {
//            mt = mt.changeReturnType(double.class);
//        } else if (ptype == Boolean.class) {
//            mt = mt.changeReturnType(boolean.class);
//        } else if (ptype == Character.class) {
//            mt = mt.changeReturnType(char.class);
//        }

        Class<?> itype = mt.returnType();

        if (itype != ptype) {
            mh = explicitCastArguments(mh, mt);
        }

        boolean handled = false;
        int flags = fs.flags;
        int width = fs.width;
        int precision = fs.precision;
        Character conv = fs.dt ? 't' : fs.c;

        switch (Character.toLowerCase(conv)) {
            case BOOLEAN -> {
                if (itype == boolean.class && precision == -1) {
                    if (flags == 0 && width == -1 && isPrimitive) {
                        return null;
                    }

                    if (validFlags(flags, LEFT_JUSTIFY)) {
                        handled = true;
                        mh = filterReturnValue(mh, FIBoolean_MH);
                    }
                }
            }
            case STRING -> {
                if (flags == 0 && width == -1 && precision == -1) {
                    if (isPrimitive || ptype == String.class) {
                        return null;
                    } else if (itype.isPrimitive()) {
                        return mh;
                    }
                }

                if (validFlags(flags, LEFT_JUSTIFY) && precision == -1) {
                    if (itype == String.class) {
                        handled = true;
                        mh = filterReturnValue(mh, FIString_MH);
                    } else if (!itype.isPrimitive()) {
                        handled = true;
                        MethodHandle test = FormattableCheck_MH;
                        test = test.asType(test.type().changeParameterType(0, ptype));
                        MethodHandle pass = insertArguments(FIFormattable_MH,
                                0, locale, flags, width, precision);
                        pass = pass.asType(pass.type().changeParameterType(0, ptype));
                        MethodHandle fail = ToString_MH;
                        fail = filterReturnValue(fail, FIString_MH);
                        fail = fail.asType(fail.type().changeParameterType(0, ptype));
                        mh = guardWithTest(test, pass, fail);
                    }
                }
            }
            case CHARACTER -> {
                if (itype == char.class && precision == -1) {
                    if (flags == 0 && width == -1) {
                        return isPrimitive ? null : mh;
                    }

                    if (validFlags(flags, LEFT_JUSTIFY)) {
                        handled = true;
                        mh = filterReturnValue(mh, FICharacter_MH);
                    }
                }
            }
            case DECIMAL_INTEGER -> {
                if ((itype == int.class || itype == long.class) && precision == -1) {
                    if (itype == int.class) {
                        mh = explicitCastArguments(mh,
                                mh.type().changeReturnType(long.class));
                    }

                    if (flags == 0 && isGenericDFS && width == -1) {
                        return mh;
                    } else if (validFlags(flags, PLUS | LEADING_SPACE |
                                                 ZERO_PAD | GROUP |
                                                 PARENTHESES)) {
                        handled = true;
                        int zeroPad = isFlag(flags, ZERO_PAD) ? width : -1;
                        char sign = isFlag(flags, PLUS)          ? '+' :
                                    isFlag(flags, LEADING_SPACE) ? ' ' : '\0';
                        boolean parentheses = isFlag(flags, PARENTHESES);
                        int groupSize = isFlag(flags, GROUP) ?
                                groupSize(locale, dfs) : 0;
                        mh = filterReturnValue(mh,
                                insertArguments(FIDecimal_MH, 0, dfs, zeroPad,
                                        sign, parentheses, groupSize));
                    }
                }
            }
            case OCTAL_INTEGER -> {
                if ((itype == int.class || itype == long.class) &&
                         precision == -1 &&
                         validFlags(flags, ZERO_PAD | ALTERNATE)) {
                    handled = true;

                    if (itype == int.class) {
                        mh = filterReturnValue(mh, ToLong_MH);
                    }

                    int zeroPad = isFlag(flags, ZERO_PAD) ? width : -1;
                    boolean hasPrefix = isFlag(flags, ALTERNATE);
                    mh = filterReturnValue(mh,
                            insertArguments(FIOctal_MH, 0, zeroPad, hasPrefix));
                }
            }
            case HEXADECIMAL_INTEGER -> {
                if ((itype == int.class || itype == long.class) &&
                        precision == -1 &&
                        validFlags(flags, ZERO_PAD | ALTERNATE)) {
                    handled = true;

                    if (itype == int.class) {
                        mh = filterReturnValue(mh, ToLong_MH);
                    }

                    int zeroPad = isFlag(flags, ZERO_PAD) ? width : -1;
                    boolean hasPrefix = isFlag(flags, ALTERNATE);
                    mh = filterReturnValue(mh,
                            insertArguments(FIHexadecimal_MH, 0, zeroPad, hasPrefix));
                }
            }
            default -> {
                // pass thru
            }
        }

        if (handled) {
            if (!isPrimitive) {
                MethodHandle test = NullCheck_MH.asType(
                        NullCheck_MH.type().changeParameterType(0, ptype));
                MethodHandle pass = dropArguments(FINull_MH, 0, ptype);
                mh = guardWithTest(test, pass, mh);
            }

            if (0 < width) {
                if (isFlag(flags, LEFT_JUSTIFY)) {
                    mh = filterReturnValue(mh,
                            insertArguments(FIFillRight_MH, 0, width));
                } else {
                    mh = filterReturnValue(mh,
                            insertArguments(FIFillLeft_MH, 0, width));
                }
            }

            if (!isFlag(flags, UPPERCASE)) {
                return mh;
            }
        }

        mh = insertArguments(FIFormatSpecifier_MH, 0, fs, locale);
        mh = mh.asType(mh.type().changeParameterType(0, ptype));

        return mh;
    }

    /**
     * Construct concat {@link MethodHandle} for based on format.
     *
     * @param fsa  list of specifiers
     *
     * @return concat {@link MethodHandle} for based on format
     */
    private MethodHandle buildFilters(List<FormatString> fsa,
                                      List<String> segments,
                                      MethodHandle[] filters) {
        MethodHandle mh = null;
        int iParam = 0;
        StringBuilder segment = new StringBuilder();

        for (FormatString fs : fsa) {
            int index = fs.index();

            switch (index) {
                case -2:  // fixed string, "%n", or "%%"
                    String string = fs.toString();

                    if ("%%".equals(string)) {
                        segment.append('%');
                    } else if ("%n".equals(string)) {
                        segment.append(System.lineSeparator());
                    } else {
                        segment.append(string);
                    }
                    break;
                case 0:  // ordinary index
                    segments.add(segment.toString());
                    segment.setLength(0);

                    if (iParam < ptypes.length) {
                        Class<?> ptype = ptypes[iParam];
                        filters[iParam++] = formatSpecifier((FormatSpecifier)fs, ptype);
                    } else {
                        throw new MissingFormatArgumentException(fs.toString());
                    }
                    break;
                case -1:  // relative index
                default:  // explicit index
                    throw new IllegalFormatFlagsException("Indexing not allowed: " + fs.toString());
            }
        }

        segments.add(segment.toString());

        return mh;
    }

    /**
     * Build a {@link MethodHandle} to format arguments.
     *
     * @return new {@link MethodHandle} to format arguments
     */
    MethodHandle build() {
        List<String> segments = new ArrayList<>();
        MethodHandle[] filters = new MethodHandle[ptypes.length];
        buildFilters(Formatter.parse(format), segments, filters);
        Class<?>[] ftypes = new Class<?>[filters.length];

        for (int i = 0; i < filters.length; i++) {
            MethodHandle filter = filters[i];
            ftypes[i] = filter == null ? ptypes[i] : filter.type().returnType();
        }

        try {
            MethodHandle mh = StringConcatFactory.makeConcatWithTemplate(segments,
                    List.of(ftypes));
            mh = filterArguments(mh, 0, filters);

            return mh;
        } catch (StringConcatException ex) {
            throw new AssertionError("concat fail", ex);
        }
    }
}
