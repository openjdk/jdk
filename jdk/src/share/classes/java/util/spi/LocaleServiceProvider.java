/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.util.spi;

import java.util.Locale;

/**
 * <p>
 * This is the super class of all the locale sensitive service provider
 * interfaces (SPIs).
 * <p>
 * Locale sensitive  service provider interfaces are interfaces that
 * correspond to locale sensitive classes in the <code>java.text</code>
 * and <code>java.util</code> packages. The interfaces enable the
 * construction of locale sensitive objects and the retrieval of
 * localized names for these packages. Locale sensitive factory methods
 * and methods for name retrieval in the <code>java.text</code> and
 * <code>java.util</code> packages use implementations of the provider
 * interfaces to offer support for locales beyond the set of locales
 * supported by the Java runtime environment itself.
 * <p>
 * <h4>Packaging of Locale Sensitive Service Provider Implementations</h4>
 * Implementations of these locale sensitive services are packaged using the
 * <a href="../../../../technotes/guides/extensions/index.html">Java Extension Mechanism</a>
 * as installed extensions.  A provider identifies itself with a
 * provider-configuration file in the resource directory META-INF/services,
 * using the fully qualified provider interface class name as the file name.
 * The file should contain a list of fully-qualified concrete provider class names,
 * one per line. A line is terminated by any one of a line feed ('\n'), a carriage
 * return ('\r'), or a carriage return followed immediately by a line feed. Space
 * and tab characters surrounding each name, as well as blank lines, are ignored.
 * The comment character is '#' ('\u0023'); on each line all characters following
 * the first comment character are ignored. The file must be encoded in UTF-8.
 * <p>
 * If a particular concrete provider class is named in more than one configuration
 * file, or is named in the same configuration file more than once, then the
 * duplicates will be ignored. The configuration file naming a particular provider
 * need not be in the same jar file or other distribution unit as the provider itself.
 * The provider must be accessible from the same class loader that was initially
 * queried to locate the configuration file; this is not necessarily the class loader
 * that loaded the file.
 * <p>
 * For example, an implementation of the
 * {@link java.text.spi.DateFormatProvider DateFormatProvider} class should
 * take the form of a jar file which contains the file:
 * <pre>
 * META-INF/services/java.text.spi.DateFormatProvider
 * </pre>
 * And the file <code>java.text.spi.DateFormatProvider</code> should have
 * a line such as:
 * <pre>
 * <code>com.foo.DateFormatProviderImpl</code>
 * </pre>
 * which is the fully qualified class name of the class implementing
 * <code>DateFormatProvider</code>.
 * <h4>Invocation of Locale Sensitive Services</h4>
 * <p>
 * Locale sensitive factory methods and methods for name retrieval in the
 * <code>java.text</code> and <code>java.util</code> packages invoke
 * service provider methods when needed to support the requested locale.
 * The methods first check whether the Java runtime environment itself
 * supports the requested locale, and use its support if available.
 * Otherwise, they call the <code>getAvailableLocales()</code> methods of
 * installed providers for the appropriate interface to find one that
 * supports the requested locale. If such a provider is found, its other
 * methods are called to obtain the requested object or name. If neither
 * the Java runtime environment itself nor an installed provider supports
 * the requested locale, a fallback locale is constructed by replacing the
 * first of the variant, country, or language strings of the locale that's
 * not an empty string with an empty string, and the lookup process is
 * restarted. In the case that the variant contains one or more '_'s, the
 * fallback locale is constructed by replacing the variant with a new variant
 * which eliminates the last '_' and the part following it.  Even if a
 * fallback occurs, methods that return requested objects or name are
 * invoked with the original locale before the fallback.The Java runtime
 * environment must support the root locale for all locale sensitive services
 * in order to guarantee that this process terminates.
 * <p>
 * Providers of names (but not providers of other objects) are allowed to
 * return null for some name requests even for locales that they claim to
 * support by including them in their return value for
 * <code>getAvailableLocales</code>. Similarly, the Java runtime
 * environment itself may not have all names for all locales that it
 * supports. This is because the sets of objects for which names are
 * requested can be large and vary over time, so that it's not always
 * feasible to cover them completely. If the Java runtime environment or a
 * provider returns null instead of a name, the lookup will proceed as
 * described above as if the locale was not supported.
 *
 * @since        1.6
 */
public abstract class LocaleServiceProvider {

    /**
     * Sole constructor.  (For invocation by subclass constructors, typically
     * implicit.)
     */
    protected LocaleServiceProvider() {
    }

    /**
     * Returns an array of all locales for which this locale service provider
     * can provide localized objects or names.
     *
     * @return An array of all locales for which this locale service provider
     * can provide localized objects or names.
     */
    public abstract Locale[] getAvailableLocales();
}
