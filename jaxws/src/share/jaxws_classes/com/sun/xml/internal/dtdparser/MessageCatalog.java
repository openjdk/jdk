/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.dtdparser;

import java.io.InputStream;
import java.text.FieldPosition;
import java.text.MessageFormat;
import java.util.Hashtable;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;


/**
 * This class provides support for multi-language string lookup, as needed
 * to localize messages from applications supporting multiple languages
 * at the same time.  One class of such applications is network services,
 * such as HTTP servers, which talk to clients who may not be from the
 * same locale as the server.  This class supports a form of negotiation
 * for the language used in presenting a message from some package, where
 * both user (client) preferences and application (server) support are
 * accounted for when choosing locales and formatting messages.
 * <p/>
 * <P> Each package should have a singleton package-private message catalog
 * class.  This ensures that the correct class loader will always be used to
 * access message resources, and minimizes use of memory: <PRE>
 * package <em>some.package</em>;
 * <p/>
 * // "foo" might be public
 * class foo {
 * ...
 * // package private
 * static final Catalog messages = new Catalog ();
 * static final class Catalog extends MessageCatalog {
 * Catalog () { super (Catalog.class); }
 * }
 * ...
 * }
 * </PRE>
 * <p/>
 * <P> Messages for a known client could be generated using code
 * something like this:  <PRE>
 * String clientLanguages [];
 * Locale clientLocale;
 * String clientMessage;
 * <p/>
 * // client languages will probably be provided by client,
 * // e.g. by an HTTP/1.1 "Accept-Language" header.
 * clientLanguages = new String [] { "en-ca", "fr-ca", "ja", "zh" };
 * clientLocale = foo.messages.chooseLocale (clientLanguages);
 * clientMessage = foo.messages.getMessage (clientLocale,
 * "fileCount",
 * new Object [] { new Integer (numberOfFiles) }
 * );
 * </PRE>
 * <p/>
 * <P> At this time, this class does not include functionality permitting
 * messages to be passed around and localized after-the-fact.  The consequence
 * of this is that the locale for messages must be passed down through layers
 * which have no normal reason to support such passdown, or else the system
 * default locale must be used instead of the one the client needs.
 * <p/>
 * <P> <hr> The following guidelines should be used when constructiong
 * multi-language applications:  <OL>
 * <p/>
 * <LI> Always use <a href=#chooseLocale>chooseLocale</a> to select the
 * locale you pass to your <code>getMessage</code> call.  This lets your
 * applications use IETF standard locale names, and avoids needless
 * use of system defaults.
 * <p/>
 * <LI> The localized messages for a given package should always go in
 * a separate <em>resources</em> sub-package.  There are security
 * implications; see below.
 * <p/>
 * <LI> Make sure that a language name is included in each bundle name,
 * so that the developer's locale will not be inadvertently used. That
 * is, don't create defaults like <em>resources/Messages.properties</em>
 * or <em>resources/Messages.class</em>, since ResourceBundle will choose
 * such defaults rather than giving software a chance to choose a more
 * appropriate language for its messages.  Your message bundles should
 * have names like <em>Messages_en.properties</em> (for the "en", or
 * English, language) or <em>Messages_ja.class</em> ("ja" indicates the
 * Japanese language).
 * <p/>
 * <LI> Only use property files for messages in languages which can
 * be limited to the ISO Latin/1 (8859-1) characters supported by the
 * property file format.  (This is mostly Western European languages.)
 * Otherwise, subclass ResourceBundle to provide your messages; it is
 * simplest to subclass <code>java.util.ListResourceBundle</code>.
 * <p/>
 * <LI> Never use another package's message catalog or resource bundles.
 * It should not be possible for a change internal to one package (such
 * as eliminating or improving messages) to break another package.
 * <p/>
 * </OL>
 * <p/>
 * <P> The "resources" sub-package can be treated separately from the
 * package with which it is associated.  That main package may be sealed
 * and possibly signed, preventing other software from adding classes to
 * the package which would be able to access methods and data which are
 * not designed to be publicly accessible.  On the other hand, resources
 * such as localized messages are often provided after initial product
 * shipment, without a full release cycle for the product.  Such files
 * (text and class files) need to be added to some package.  Since they
 * should not be added to the main package, the "resources" subpackage is
 * used without risking the security or integrity of that main package
 * as distributed in its JAR file.
 *
 * @author David Brownell
 * @version 1.1, 00/08/05
 * @see java.util.Locale
 * @see java.util.ListResourceBundle
 * @see java.text.MessageFormat
 */
// leave this as "abstract" -- each package needs its own subclass,
// else it's not always going to be using the right class loader.
abstract public class MessageCatalog {
    private String bundleName;

    /**
     * Create a message catalog for use by classes in the same package
     * as the specified class.  This uses <em>Messages</em> resource
     * bundles in the <em>resources</em> sub-package of class passed as
     * a parameter.
     *
     * @param packageMember Class whose package has localized messages
     */
    protected MessageCatalog(Class packageMember) {
        this(packageMember, "Messages");
    }

    /**
     * Create a message catalog for use by classes in the same package
     * as the specified class.  This uses the specified resource
     * bundle name in the <em>resources</em> sub-package of class passed
     * as a parameter; for example, <em>resources.Messages</em>.
     *
     * @param packageMember Class whose package has localized messages
     * @param bundle        Name of a group of resource bundles
     */
    private MessageCatalog(Class packageMember, String bundle) {
        int index;

        bundleName = packageMember.getName();
        index = bundleName.lastIndexOf('.');
        if (index == -1)    // "ClassName"
            bundleName = "";
        else            // "some.package.ClassName"
            bundleName = bundleName.substring(0, index) + ".";
        bundleName = bundleName + "resources." + bundle;
    }


    /**
     * Get a message localized to the specified locale, using the message ID
     * and package name if no message is available.  The locale is normally
     * that of the client of a service, chosen with knowledge that both the
     * client and this server support that locale.  There are two error
     * cases:  first, when the specified locale is unsupported or null, the
     * default locale is used if possible; second, when no bundle supports
     * that locale, the message ID and package name are used.
     *
     * @param locale    The locale of the message to use.  If this is null,
     *                  the default locale will be used.
     * @param messageId The ID of the message to use.
     * @return The message, localized as described above.
     */
    public String getMessage(Locale locale,
                             String messageId) {
        ResourceBundle bundle;

        // cope with unsupported locale...
        if (locale == null)
            locale = Locale.getDefault();

        try {
            bundle = ResourceBundle.getBundle(bundleName, locale);
        } catch (MissingResourceException e) {
            bundle = ResourceBundle.getBundle(bundleName, Locale.ENGLISH);
        }
        return bundle.getString(messageId);
    }


    /**
     * Format a message localized to the specified locale, using the message
     * ID with its package name if none is available.  The locale is normally
     * the client of a service, chosen with knowledge that both the client
     * server support that locale.  There are two error cases:  first, if the
     * specified locale is unsupported or null, the default locale is used if
     * possible; second, when no bundle supports that locale, the message ID
     * and package name are used.
     *
     * @param locale     The locale of the message to use.  If this is null,
     *                   the default locale will be used.
     * @param messageId  The ID of the message format to use.
     * @param parameters Used when formatting the message.  Objects in
     *                   this list are turned to strings if they are not Strings, Numbers,
     *                   or Dates (that is, if MessageFormat would treat them as errors).
     * @return The message, localized as described above.
     * @see java.text.MessageFormat
     */
    public String getMessage(Locale locale,
                             String messageId,
                             Object parameters []) {
        if (parameters == null)
            return getMessage(locale, messageId);

        // since most messages won't be tested (sigh), be friendly to
        // the inevitable developer errors of passing random data types
        // to the message formatting code.
        for (int i = 0; i < parameters.length; i++) {
            if (!(parameters[i] instanceof String)
                    && !(parameters[i] instanceof Number)
                    && !(parameters[i] instanceof java.util.Date)) {
                if (parameters[i] == null)
                    parameters[i] = "(null)";
                else
                    parameters[i] = parameters[i].toString();
            }
        }

        // similarly, cope with unsupported locale...
        if (locale == null)
            locale = Locale.getDefault();

        // get the appropriately localized MessageFormat object
        ResourceBundle bundle;
        MessageFormat format;

        try {
            bundle = ResourceBundle.getBundle(bundleName, locale);
        } catch (MissingResourceException e) {
            bundle = ResourceBundle.getBundle(bundleName, Locale.ENGLISH);
            /*String retval;

            retval = packagePrefix (messageId);
            for (int i = 0; i < parameters.length; i++) {
            retval += ' ';
            retval += parameters [i];
            }
            return retval;*/
        }
        format = new MessageFormat(bundle.getString(messageId));
        format.setLocale(locale);

        // return the formatted message
        StringBuffer result = new StringBuffer();

        result = format.format(parameters, result, new FieldPosition(0));
        return result.toString();
    }


    /**
     * Chooses a client locale to use, using the first language specified in
     * the list that is supported by this catalog.  If none of the specified
     * languages is supported, a null value is returned.  Such a list of
     * languages might be provided in an HTTP/1.1 "Accept-Language" header
     * field, or through some other content negotiation mechanism.
     * <p/>
     * <P> The language specifiers recognized are RFC 1766 style ("fr" for
     * all French, "fr-ca" for Canadian French), although only the strict
     * ISO subset (two letter language and country specifiers) is currently
     * supported.  Java-style locale strings ("fr_CA") are also supported.
     *
     * @param languages Array of language specifiers, ordered with the most
     *                  preferable one at the front.  For example, "en-ca" then "fr-ca",
     *                  followed by "zh_CN".
     * @return The most preferable supported locale, or null.
     * @see java.util.Locale
     */
    public Locale chooseLocale(String languages []) {
        if ((languages = canonicalize(languages)) != null) {
            for (int i = 0; i < languages.length; i++)
                if (isLocaleSupported(languages[i]))
                    return getLocale(languages[i]);
        }
        return null;
    }


    //
    // Canonicalizes the RFC 1766 style language strings ("en-in") to
    // match standard Java usage ("en_IN"), removing strings that don't
    // use two character ISO language and country codes.   Avoids all
    // memory allocations possible, so that if the strings passed in are
    // just lowercase ISO codes (a common case) the input is returned.
    //
    private String[] canonicalize(String languages []) {
        boolean didClone = false;
        int trimCount = 0;

        if (languages == null)
            return languages;

        for (int i = 0; i < languages.length; i++) {
            String lang = languages[i];
            int len = lang.length();

            // no RFC1766 extensions allowed; "zh" and "zh-tw" (etc) are OK
            // as are regular locale names with no variant ("de_CH").
            if (!(len == 2 || len == 5)) {
                if (!didClone) {
                    languages = (String[]) languages.clone();
                    didClone = true;
                }
                languages[i] = null;
                trimCount++;
                continue;
            }

            // language code ... if already lowercase, we change nothing
            if (len == 2) {
                lang = lang.toLowerCase();
                if (lang != languages[i]) {
                    if (!didClone) {
                        languages = (String[]) languages.clone();
                        didClone = true;
                    }
                    languages[i] = lang;
                }
                continue;
            }

            // language_country ... fixup case, force "_"
            char buf [] = new char[5];

            buf[0] = Character.toLowerCase(lang.charAt(0));
            buf[1] = Character.toLowerCase(lang.charAt(1));
            buf[2] = '_';
            buf[3] = Character.toUpperCase(lang.charAt(3));
            buf[4] = Character.toUpperCase(lang.charAt(4));
            if (!didClone) {
                languages = (String[]) languages.clone();
                didClone = true;
            }
            languages[i] = new String(buf);
        }

        // purge any shadows of deleted RFC1766 extended language codes
        if (trimCount != 0) {
            String temp [] = new String[languages.length - trimCount];
            int i;

            for (i = 0, trimCount = 0; i < temp.length; i++) {
                while (languages[i + trimCount] == null)
                    trimCount++;
                temp[i] = languages[i + trimCount];
            }
            languages = temp;
        }
        return languages;
    }


    //
    // Returns a locale object supporting the specified locale, using
    // a small cache to speed up some common languages and reduce the
    // needless allocation of memory.
    //
    private Locale getLocale(String localeName) {
        String language, country;
        int index;

        index = localeName.indexOf('_');
        if (index == -1) {
            //
            // Special case the builtin JDK languages
            //
            if (localeName.equals("de"))
                return Locale.GERMAN;
            if (localeName.equals("en"))
                return Locale.ENGLISH;
            if (localeName.equals("fr"))
                return Locale.FRENCH;
            if (localeName.equals("it"))
                return Locale.ITALIAN;
            if (localeName.equals("ja"))
                return Locale.JAPANESE;
            if (localeName.equals("ko"))
                return Locale.KOREAN;
            if (localeName.equals("zh"))
                return Locale.CHINESE;

            language = localeName;
            country = "";
        } else {
            if (localeName.equals("zh_CN"))
                return Locale.SIMPLIFIED_CHINESE;
            if (localeName.equals("zh_TW"))
                return Locale.TRADITIONAL_CHINESE;

            //
            // JDK also has constants for countries:  en_GB, en_US, en_CA,
            // fr_FR, fr_CA, de_DE, ja_JP, ko_KR.  We don't use those.
            //
            language = localeName.substring(0, index);
            country = localeName.substring(index + 1);
        }

        return new Locale(language, country);
    }


    //
    // cache for isLanguageSupported(), below ... key is a language
    // or locale name, value is a Boolean
    //
    private Hashtable cache = new Hashtable(5);


    /**
     * Returns true iff the specified locale has explicit language support.
     * For example, the traditional Chinese locale "zh_TW" has such support
     * if there are message bundles suffixed with either "zh_TW" or "zh".
     * <p/>
     * <P> This method is used to bypass part of the search path mechanism
     * of the <code>ResourceBundle</code> class, specifically the parts which
     * force use of default locales and bundles.  Such bypassing is required
     * in order to enable use of a client's preferred languages.  Following
     * the above example, if a client prefers "zh_TW" but can also accept
     * "ja", this method would be used to detect that there are no "zh_TW"
     * resource bundles and hence that "ja" messages should be used.  This
     * bypasses the ResourceBundle mechanism which will return messages in
     * some other locale (picking some hard-to-anticipate default) instead
     * of reporting an error and letting the client choose another locale.
     *
     * @param localeName A standard Java locale name, using two character
     *                   language codes optionally suffixed by country codes.
     * @return True iff the language of that locale is supported.
     * @see java.util.Locale
     */
    public boolean isLocaleSupported(String localeName) {
        //
        // Use previous results if possible.  We expect that the codebase
        // is immutable, so we never worry about changing the cache.
        //
        Boolean value = (Boolean) cache.get(localeName);

        if (value != null)
            return value.booleanValue();

        //
        // Try "language_country_variant", then "language_country",
        // then finally "language" ... assuming the longest locale name
        // is passed.  If not, we'll try fewer options.
        //
        ClassLoader loader = null;

        for (; ;) {
            String name = bundleName + "_" + localeName;

            // look up classes ...
            try {
                Class.forName(name);
                cache.put(localeName, Boolean.TRUE);
                return true;
            } catch (Exception e) {
            }

            // ... then property files (only for ISO Latin/1 messages)
            InputStream in;

            if (loader == null)
                loader = getClass().getClassLoader();

            name = name.replace('.', '/');
            name = name + ".properties";
            if (loader == null)
                in = ClassLoader.getSystemResourceAsStream(name);
            else
                in = loader.getResourceAsStream(name);
            if (in != null) {
                cache.put(localeName, Boolean.TRUE);
                return true;
            }

            int index = localeName.indexOf('_');

            if (index > 0)
                localeName = localeName.substring(0, index);
            else
                break;
        }

        //
        // If we got this far, we failed.  Remember for later.
        //
        cache.put(localeName, Boolean.FALSE);
        return false;
    }
}
