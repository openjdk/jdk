/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sun.org.apache.xerces.internal.xpointer;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.PropertyResourceBundle;
import com.sun.org.apache.xerces.internal.util.MessageFormatter;
import com.sun.org.apache.xerces.internal.utils.SecuritySupport;

/**
 * XPointerMessageFormatter provides error messages for the XPointer Framework
 * and element() Scheme Recommendations.
 *
 * @xerces.internal
 *
 * @version $Id: XPointerMessageFormatter.java,v 1.5 2010-11-01 04:40:26 joehw Exp $
 */
class XPointerMessageFormatter implements MessageFormatter {

    public static final String XPOINTER_DOMAIN = "http://www.w3.org/TR/XPTR";

    // private objects to cache the locale and resource bundle
    private Locale fLocale = null;

    private ResourceBundle fResourceBundle = null;

    /**
     * Formats a message with the specified arguments using the given locale
     * information.
     *
     * @param locale
     *            The locale of the message.
     * @param key
     *            The message key.
     * @param arguments
     *            The message replacement text arguments. The order of the
     *            arguments must match that of the placeholders in the actual
     *            message.
     *
     * @return Returns the formatted message.
     *
     * @throws MissingResourceException
     *             Thrown if the message with the specified key cannot be found.
     */
    public String formatMessage(Locale locale, String key, Object[] arguments)
            throws MissingResourceException {

        if (fResourceBundle == null || locale != fLocale) {
            if (locale != null) {
                fResourceBundle = SecuritySupport.getResourceBundle(
                        "com.sun.org.apache.xerces.internal.impl.msg.XPointerMessages", locale);
                // memorize the most-recent locale
                fLocale = locale;
            }
            if (fResourceBundle == null)
                fResourceBundle = SecuritySupport.getResourceBundle(
                        "com.sun.org.apache.xerces.internal.impl.msg.XPointerMessages");
        }

        String msg = fResourceBundle.getString(key);
        if (arguments != null) {
            try {
                msg = java.text.MessageFormat.format(msg, arguments);
            } catch (Exception e) {
                msg = fResourceBundle.getString("FormatFailed");
                msg += " " + fResourceBundle.getString(key);
            }
        }

        if (msg == null) {
            msg = fResourceBundle.getString("BadMessageKey");
            throw new MissingResourceException(msg,
                    "com.sun.org.apache.xerces.internal.impl.msg.XPointerMessages", key);
        }

        return msg;
    }
}
