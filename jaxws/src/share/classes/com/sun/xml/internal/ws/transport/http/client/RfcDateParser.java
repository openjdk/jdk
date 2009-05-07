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

package com.sun.xml.internal.ws.transport.http.client;

import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A parser for date strings commonly found in http and email headers that
 * follow various RFC conventions.  Given a date-string, the parser will
 * attempt to parse it by trying matches with a set of patterns, returning
 * null on failure, a Date object on success.
 *
 * @author WS Development Team
 */
final class RfcDateParser {
    private boolean isGMT = false;
    static final String[] standardFormats =
        { "EEEE', 'dd-MMM-yy HH:mm:ss z", // RFC 850 (obsoleted by 1036)
        "EEEE', 'dd-MMM-yy HH:mm:ss", // ditto but no tz. Happens too often
        "EEE', 'dd-MMM-yyyy HH:mm:ss z", // RFC 822/1123
        "EEE', 'dd MMM yyyy HH:mm:ss z", // REMIND what rfc? Apache/1.1
        "EEEE', 'dd MMM yyyy HH:mm:ss z", // REMIND what rfc? Apache/1.1
        "EEE', 'dd MMM yyyy hh:mm:ss z", // REMIND what rfc? Apache/1.1
        "EEEE', 'dd MMM yyyy hh:mm:ss z", // REMIND what rfc? Apache/1.1
        "EEE MMM dd HH:mm:ss z yyyy", // Date's string output format
        "EEE MMM dd HH:mm:ss yyyy", // ANSI C asctime format()
        "EEE', 'dd-MMM-yy HH:mm:ss", // No time zone 2 digit year RFC 1123
        "EEE', 'dd-MMM-yyyy HH:mm:ss" // No time zone RFC 822/1123
    };
    static final String[] gmtStandardFormats =
        { "EEEE',' dd-MMM-yy HH:mm:ss 'GMT'", // RFC 850 (obsoleted by 1036)
        "EEE',' dd-MMM-yyyy HH:mm:ss 'GMT'", // RFC 822/1123
        "EEE',' dd MMM yyyy HH:mm:ss 'GMT'", // REMIND what rfc? Apache/1.1
        "EEEE',' dd MMM yyyy HH:mm:ss 'GMT'", // REMIND what rfc? Apache/1.1
        "EEE',' dd MMM yyyy hh:mm:ss 'GMT'", // REMIND what rfc? Apache/1.1
        "EEEE',' dd MMM yyyy hh:mm:ss 'GMT'", // REMIND what rfc? Apache/1.1
        "EEE MMM dd HH:mm:ss 'GMT' yyyy" // Date's string output format
    };
    String dateString;

    public RfcDateParser(String dateString) {

        this.dateString = dateString.trim();

        if (this.dateString.indexOf("GMT") != -1) {
            isGMT = true;
        }
    }

    public Date getDate() {

        // format is wdy, DD-Mon-yyyy HH:mm:ss GMT
        int arrayLen =
            isGMT ? gmtStandardFormats.length : standardFormats.length;

        for (int i = 0; i < arrayLen; i++) {
            Date d;

            if (isGMT) {
                d = tryParsing(gmtStandardFormats[i]);
            } else {
                d = tryParsing(standardFormats[i]);
            }

            if (d != null) {
                return d;
            }
        }

        return null;
    }

    private Date tryParsing(String format) {

        SimpleDateFormat df = new SimpleDateFormat(format, Locale.US);

        if (isGMT) {
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        try {
            return df.parse(dateString);
        } catch (ParseException e) {
            return null;
        }
    }
}
