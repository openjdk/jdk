/*
 * Copyright 2000-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.util;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TimeZone;
import sun.util.resources.LocaleData;

public class BuddhistCalendar extends GregorianCalendar {

//////////////////
// Class Variables
//////////////////

    private static final long serialVersionUID = -8527488697350388578L;

    private static final int buddhistOffset = 543;

///////////////
// Constructors
///////////////

    /**
     * Constructs a default BuddhistCalendar using the current time
     * in the default time zone with the default locale.
     */
    public BuddhistCalendar() {
        super();
    }

    /**
     * Constructs a BuddhistCalendar based on the current time
     * in the given time zone with the default locale.
     * @param zone the given time zone.
     */
    public BuddhistCalendar(TimeZone zone) {
        super(zone);
    }

    /**
     * Constructs a BuddhistCalendar based on the current time
     * in the default time zone with the given locale.
     * @param aLocale the given locale.
     */
    public BuddhistCalendar(Locale aLocale) {
        super(aLocale);
    }

    /**
     * Constructs a BuddhistCalendar based on the current time
     * in the given time zone with the given locale.
     * @param zone the given time zone.
     * @param aLocale the given locale.
     */
    public BuddhistCalendar(TimeZone zone, Locale aLocale) {
        super(zone, aLocale);
    }

/////////////////
// Public methods
/////////////////

    /**
     * Compares this BuddhistCalendar to an object reference.
     * @param obj the object reference with which to compare
     * @return true if this object is equal to <code>obj</code>; false otherwise
     */
    public boolean equals(Object obj) {
        return obj instanceof BuddhistCalendar
            && super.equals(obj);
    }

    /**
     * Override hashCode.
     * Generates the hash code for the BuddhistCalendar object
     */
    public int hashCode() {
        return super.hashCode() ^ buddhistOffset;
    }

    /**
     * Gets the value for a given time field.
     * @param field the given time field.
     * @return the value for the given time field.
     */
    public int get(int field)
    {
        if (field == YEAR) {
            return super.get(field) + yearOffset;
        }
        return super.get(field);
    }

    /**
     * Sets the time field with the given value.
     * @param field the given time field.
     * @param value the value to be set for the given time field.
     */
    public void set(int field, int value)
    {
        if (field == YEAR) {
            super.set(field, value - yearOffset);
        } else {
            super.set(field, value);
        }
    }

    /**
     * Adds the specified (signed) amount of time to the given time field.
     * @param field the time field.
     * @param amount the amount of date or time to be added to the field.
     */
    public void add(int field, int amount)
    {
        int savedYearOffset = yearOffset;
        yearOffset = 0;
        try {
            super.add(field, amount);
        } finally {
            yearOffset = savedYearOffset;
        }
    }

    /**
     * Add to field a signed amount without changing larger fields.
     * A negative roll amount means to subtract from field without changing
     * larger fields.
     * @param field the time field.
     * @param amount the signed amount to add to <code>field</code>.
     */
    public void roll(int field, int amount)
    {
        int savedYearOffset = yearOffset;
        yearOffset = 0;
        try {
            super.roll(field, amount);
        } finally {
            yearOffset = savedYearOffset;
        }
    }

    public String getDisplayName(int field, int style, Locale locale) {
        if (field != ERA) {
            return super.getDisplayName(field, style, locale);
        }

        // Handle Thai BuddhistCalendar specific era names
        if (field < 0 || field >= fields.length ||
            style < SHORT || style > LONG) {
            throw new IllegalArgumentException();
        }
        if (locale == null) {
            throw new NullPointerException();
        }
        ResourceBundle rb = LocaleData.getDateFormatData(locale);
        String[] eras = rb.getStringArray(getKey(style));
        return eras[get(field)];
    }

    public Map<String,Integer> getDisplayNames(int field, int style, Locale locale) {
        if (field != ERA) {
            return super.getDisplayNames(field, style, locale);
        }

        // Handle Thai BuddhistCalendar specific era names
        if (field < 0 || field >= fields.length ||
            style < ALL_STYLES || style > LONG) {
            throw new IllegalArgumentException();
        }
        if (locale == null) {
            throw new NullPointerException();
        }
        // ALL_STYLES
        if (style == ALL_STYLES) {
            Map<String,Integer> shortNames = getDisplayNamesImpl(field, SHORT, locale);
            Map<String,Integer> longNames = getDisplayNamesImpl(field, LONG, locale);
            if (shortNames == null) {
                return longNames;
            }
            if (longNames != null) {
                shortNames.putAll(longNames);
            }
            return shortNames;
        }

        // SHORT or LONG
        return getDisplayNamesImpl(field, style, locale);
    }

    private Map<String,Integer> getDisplayNamesImpl(int field, int style, Locale locale) {
        ResourceBundle rb = LocaleData.getDateFormatData(locale);
        String[] eras = rb.getStringArray(getKey(style));
        Map<String,Integer> map = new HashMap<String,Integer>(4);
        for (int i = 0; i < eras.length; i++) {
            map.put(eras[i], i);
        }
        return map;
    }

    private String getKey(int style) {
        StringBuilder key = new StringBuilder();
        key.append(BuddhistCalendar.class.getName());
        if (style == SHORT) {
            key.append(".short");
        }
        key.append(".Eras");
        return key.toString();
    }

    /**
     * Returns the maximum value that this field could have, given the
     * current date.  For example, with the date "Feb 3, 2540" and the
     * <code>DAY_OF_MONTH</code> field, the actual maximum is 28; for
     * "Feb 3, 2539" it is 29.
     *
     * @param field the field to determine the maximum of
     * @return the maximum of the given field for the current date of this Calendar
     */
    public int getActualMaximum(int field) {
        int savedYearOffset = yearOffset;
        yearOffset = 0;
        try {
            return super.getActualMaximum(field);
        } finally {
            yearOffset = savedYearOffset;
        }
    }

    public String toString() {
        // The super class produces a String with the Gregorian year
        // value (or '?')
        String s = super.toString();
        // If the YEAR field is UNSET, then return the Gregorian string.
        if (!isSet(YEAR)) {
            return s;
        }

        final String yearField = "YEAR=";
        int p = s.indexOf(yearField);
        // If the string doesn't include the year value for some
        // reason, then return the Gregorian string.
        if (p == -1) {
            return s;
        }
        p += yearField.length();
        StringBuilder sb = new StringBuilder(s.substring(0, p));
        // Skip the year number
        while (Character.isDigit(s.charAt(p++)))
            ;
        int year = internalGet(YEAR) + buddhistOffset;
        sb.append(year).append(s.substring(p - 1));
        return sb.toString();
    }

    private transient int yearOffset = buddhistOffset;

}
