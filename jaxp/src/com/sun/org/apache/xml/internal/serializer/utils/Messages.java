/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: Messages.java,v 1.1.4.1 2005/09/08 11:03:10 suresh_emailid Exp $
 */
package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * A utility class for issuing error messages.
 *
 * A user of this class normally would create a singleton
 * instance of this class, passing the name
 * of the message class on the constructor. For example:
 * <CODE>
 * static Messages x = new Messages("org.package.MyMessages");
 * </CODE>
 * Later the message is typically generated this way if there are no
 * substitution arguments:
 * <CODE>
 * String msg = x.createMessage(org.package.MyMessages.KEY_ONE, null);
 * </CODE>
 * If there are arguments substitutions then something like this:
 * <CODE>
 * String filename = ...;
 * String directory = ...;
 * String msg = x.createMessage(org.package.MyMessages.KEY_TWO,
 *   new Object[] {filename, directory) );
 * </CODE>
 *
 * The constructor of an instance of this class must be given
 * the class name of a class that extends java.util.ListResourceBundle
 * ("org.package.MyMessages" in the example above).
 * The name should not have any language suffix
 * which will be added automatically by this utility class.
 *
 * The message class ("org.package.MyMessages")
 * must define the abstract method getContents() that is
 * declared in its base class, for example:
 * <CODE>
 * public Object[][] getContents() {return contents;}
 * </CODE>
 *
 * It is suggested that the message class expose its
 * message keys like this:
 * <CODE>
 *   public static final String KEY_ONE = "KEY1";
 *   public static final String KEY_TWO = "KEY2";
 *   . . .
 * </CODE>
 * and used through their names (KEY_ONE ...) rather than
 * their values ("KEY1" ...).
 *
 * The field contents (returned by getContents()
 * should be initialized something like this:
 * <CODE>
 * public static final Object[][] contents = {
 * { KEY_ONE, "Something has gone wrong!" },
 * { KEY_TWO, "The file ''{0}'' does not exist in directory ''{1}''." },
 * . . .
 * { KEY_N, "Message N" }  }
 * </CODE>
 *
 * Where that section of code with the KEY to Message mappings
 * (where the message classes 'contents' field is initialized)
 * can have the Message strings translated in an alternate language
 * in a errorResourceClass with a language suffix.
 *
 * More sophisticated use of this class would be to pass null
 * when contructing it, but then call loadResourceBundle()
 * before creating any messages.
 *
 * This class is not a public API, it is only public because it is
 * used in com.sun.org.apache.xml.internal.serializer.
 *
 *  @xsl.usage internal
 */
public final class Messages
{
    /** The local object to use.  */
    private final Locale m_locale = Locale.getDefault();

    /** The language specific resource object for messages.  */
    private ListResourceBundle m_resourceBundle;

    /** The class name of the error message string table with no language suffix. */
    private String m_resourceBundleName;



    /**
     * Constructor.
     * @param resourceBundle the class name of the ListResourceBundle
     * that the instance of this class is associated with and will use when
     * creating messages.
     * The class name is without a language suffix. If the value passed
     * is null then loadResourceBundle(errorResourceClass) needs to be called
     * explicitly before any messages are created.
     *
     * @xsl.usage internal
     */
    Messages(String resourceBundle)
    {

        m_resourceBundleName = resourceBundle;
    }

    /*
     * Set the Locale object to use. If this method is not called the
     * default locale is used. This method needs to be called before
     * loadResourceBundle().
     *
     * @param locale non-null reference to Locale object.
     * @xsl.usage internal
     */
//    public void setLocale(Locale locale)
//    {
//        m_locale = locale;
//    }

    /**
     * Get the Locale object that is being used.
     *
     * @return non-null reference to Locale object.
     * @xsl.usage internal
     */
    private Locale getLocale()
    {
        return m_locale;
    }

    /**
     * Get the ListResourceBundle being used by this Messages instance which was
     * previously set by a call to loadResourceBundle(className)
     * @xsl.usage internal
     */
    private ListResourceBundle getResourceBundle()
    {
        return m_resourceBundle;
    }

    /**
     * Creates a message from the specified key and replacement
     * arguments, localized to the given locale.
     *
     * @param msgKey  The key for the message text.
     * @param args    The arguments to be used as replacement text
     * in the message created.
     *
     * @return The formatted message string.
     * @xsl.usage internal
     */
    public final String createMessage(String msgKey, Object args[])
    {
        if (m_resourceBundle == null)
            m_resourceBundle = loadResourceBundle(m_resourceBundleName);

        if (m_resourceBundle != null)
        {
            return createMsg(m_resourceBundle, msgKey, args);
        }
        else
            return "Could not load the resource bundles: "+ m_resourceBundleName;
    }

    /**
     * Creates a message from the specified key and replacement
     * arguments, localized to the given locale.
     *
     * @param errorCode The key for the message text.
     *
     * @param fResourceBundle The resource bundle to use.
     * @param msgKey  The message key to use.
     * @param args      The arguments to be used as replacement text
     *                  in the message created.
     *
     * @return The formatted message string.
     * @xsl.usage internal
     */
    private final String createMsg(
        ListResourceBundle fResourceBundle,
        String msgKey,
        Object args[]) //throws Exception
    {

        String fmsg = null;
        boolean throwex = false;
        String msg = null;

        if (msgKey != null)
            msg = fResourceBundle.getString(msgKey);
        else
            msgKey = "";

        if (msg == null)
        {
            throwex = true;
            /* The message is not in the bundle . . . this is bad,
             * so try to get the message that the message is not in the bundle
             */
            try
            {

                msg =
                    java.text.MessageFormat.format(
                        MsgKey.BAD_MSGKEY,
                        new Object[] { msgKey, m_resourceBundleName });
            }
            catch (Exception e)
            {
                /* even the message that the message is not in the bundle is
                 * not there ... this is really bad
                 */
                msg =
                    "The message key '"
                        + msgKey
                        + "' is not in the message class '"
                        + m_resourceBundleName+"'";
            }
        }
        else if (args != null)
        {
            try
            {
                // Do this to keep format from crying.
                // This is better than making a bunch of conditional
                // code all over the place.
                int n = args.length;

                for (int i = 0; i < n; i++)
                {
                    if (null == args[i])
                        args[i] = "";
                }

                fmsg = java.text.MessageFormat.format(msg, args);
                // if we get past the line above we have create the message ... hurray!
            }
            catch (Exception e)
            {
                throwex = true;
                try
                {
                    // Get the message that the format failed.
                    fmsg =
                        java.text.MessageFormat.format(
                            MsgKey.BAD_MSGFORMAT,
                            new Object[] { msgKey, m_resourceBundleName });
                    fmsg += " " + msg;
                }
                catch (Exception formatfailed)
                {
                    // We couldn't even get the message that the format of
                    // the message failed ... so fall back to English.
                    fmsg =
                        "The format of message '"
                            + msgKey
                            + "' in message class '"
                            + m_resourceBundleName
                            + "' failed.";
                }
            }
        }
        else
            fmsg = msg;

        if (throwex)
        {
            throw new RuntimeException(fmsg);
        }

        return fmsg;
    }

    /**
     * Return a named ResourceBundle for a particular locale.  This method mimics the behavior
     * of ResourceBundle.getBundle().
     *
     * @param className the name of the class that implements ListResourceBundle,
     * without language suffix.
     * @return the ResourceBundle
     * @throws MissingResourceException
     * @xsl.usage internal
     */
    private ListResourceBundle loadResourceBundle(String resourceBundle)
        throws MissingResourceException
    {
        m_resourceBundleName = resourceBundle;
        Locale locale = getLocale();

        ListResourceBundle lrb;

        try
        {

            ResourceBundle rb =
                ResourceBundle.getBundle(m_resourceBundleName, locale);
            lrb = (ListResourceBundle) rb;
        }
        catch (MissingResourceException e)
        {
            try // try to fall back to en_US if we can't load
                {

                // Since we can't find the localized property file,
                // fall back to en_US.
                lrb =
                    (ListResourceBundle) ResourceBundle.getBundle(
                        m_resourceBundleName,
                        new Locale("en", "US"));
            }
            catch (MissingResourceException e2)
            {

                // Now we are really in trouble.
                // very bad, definitely very bad...not going to get very far
                throw new MissingResourceException(
                    "Could not load any resource bundles." + m_resourceBundleName,
                    m_resourceBundleName,
                    "");
            }
        }
        m_resourceBundle = lrb;
        return lrb;
    }

    /**
     * Return the resource file suffic for the indicated locale
     * For most locales, this will be based the language code.  However
     * for Chinese, we do distinguish between Taiwan and PRC
     *
     * @param locale the locale
     * @return an String suffix which can be appended to a resource name
     * @xsl.usage internal
     */
    private static String getResourceSuffix(Locale locale)
    {

        String suffix = "_" + locale.getLanguage();
        String country = locale.getCountry();

        if (country.equals("TW"))
            suffix += "_" + country;

        return suffix;
    }
}
