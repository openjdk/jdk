/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.text.spi;

import java.text.DateFormat;
import java.util.Locale;
import java.util.spi.LocaleServiceProvider;

/**
 * An abstract class for service providers that
 * provide concrete implementations of the
 * {@link java.text.DateFormat DateFormat} class.
 *
 * @since        1.6
 */
public abstract class DateFormatProvider extends LocaleServiceProvider {

    /**
     * Sole constructor.  (For invocation by subclass constructors, typically
     * implicit.)
     */
    protected DateFormatProvider() {
    }

    /**
     * Returns a new <code>DateFormat</code> instance which formats time
     * with the given formatting style for the specified locale.
     * @param style the given formatting style.  Either one of
     *     {@link java.text.DateFormat#SHORT DateFormat.SHORT},
     *     {@link java.text.DateFormat#MEDIUM DateFormat.MEDIUM},
     *     {@link java.text.DateFormat#LONG DateFormat.LONG}, or
     *     {@link java.text.DateFormat#FULL DateFormat.FULL}.
     * @param locale the desired locale.
     * @exception IllegalArgumentException if <code>style</code> is invalid,
     *     or if <code>locale</code> isn't
     *     one of the locales returned from
     *     {@link java.util.spi.LocaleServiceProvider#getAvailableLocales()
     *     getAvailableLocales()}.
     * @exception NullPointerException if <code>locale</code> is null
     * @return a time formatter.
     * @see java.text.DateFormat#getTimeInstance(int, java.util.Locale)
     */
    public abstract DateFormat getTimeInstance(int style, Locale locale);

    /**
     * Returns a new <code>DateFormat</code> instance which formats date
     * with the given formatting style for the specified locale.
     * @param style the given formatting style.  Either one of
     *     {@link java.text.DateFormat#SHORT DateFormat.SHORT},
     *     {@link java.text.DateFormat#MEDIUM DateFormat.MEDIUM},
     *     {@link java.text.DateFormat#LONG DateFormat.LONG}, or
     *     {@link java.text.DateFormat#FULL DateFormat.FULL}.
     * @param locale the desired locale.
     * @exception IllegalArgumentException if <code>style</code> is invalid,
     *     or if <code>locale</code> isn't
     *     one of the locales returned from
     *     {@link java.util.spi.LocaleServiceProvider#getAvailableLocales()
     *     getAvailableLocales()}.
     * @exception NullPointerException if <code>locale</code> is null
     * @return a date formatter.
     * @see java.text.DateFormat#getDateInstance(int, java.util.Locale)
     */
    public abstract DateFormat getDateInstance(int style, Locale locale);

    /**
     * Returns a new <code>DateFormat</code> instance which formats date and time
     * with the given formatting style for the specified locale.
     * @param dateStyle the given date formatting style.  Either one of
     *     {@link java.text.DateFormat#SHORT DateFormat.SHORT},
     *     {@link java.text.DateFormat#MEDIUM DateFormat.MEDIUM},
     *     {@link java.text.DateFormat#LONG DateFormat.LONG}, or
     *     {@link java.text.DateFormat#FULL DateFormat.FULL}.
     * @param timeStyle the given time formatting style.  Either one of
     *     {@link java.text.DateFormat#SHORT DateFormat.SHORT},
     *     {@link java.text.DateFormat#MEDIUM DateFormat.MEDIUM},
     *     {@link java.text.DateFormat#LONG DateFormat.LONG}, or
     *     {@link java.text.DateFormat#FULL DateFormat.FULL}.
     * @param locale the desired locale.
     * @exception IllegalArgumentException if <code>dateStyle</code> or
     *     <code>timeStyle</code> is invalid,
     *     or if <code>locale</code> isn't
     *     one of the locales returned from
     *     {@link java.util.spi.LocaleServiceProvider#getAvailableLocales()
     *     getAvailableLocales()}.
     * @exception NullPointerException if <code>locale</code> is null
     * @return a date/time formatter.
     * @see java.text.DateFormat#getDateTimeInstance(int, int, java.util.Locale)
     */
    public abstract DateFormat
        getDateTimeInstance(int dateStyle, int timeStyle, Locale locale);
}
