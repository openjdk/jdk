/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Copyright (c) 2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.time.chrono;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.security.AccessController;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.function.Block;

/**
 * A reader for Hijrah Deviation files.
 * <p>
 * For each Hijrah calendar a deviation file is used
 * to modify the initial Hijrah calendar by changing the length of
 * individual months.
 * <p>
 * The default location of the deviation files is
 * {@code <java.home> + FILE_SEP + "lib"}.
 * The deviation files are named {@code "hijrah_" + ID}.
 * <p>
 * The deviation file for a calendar can be overridden by defining the
 * property {@code java.time.chrono.HijrahChronology.File.hijrah_<ID>}
 * with the full pathname of the deviation file.
 * <p>
 * The deviation file is read line by line:
 * <ul>
 * <li>The "#" character begins a comment -
 *     all characters including and after the "#" on the line are ignored</li>
 * <li>Valid lines contain two fields, separated by whitespace, whitespace
 *     is otherwise ignored.</li>
 * <li>The first field is a LocalDate using the format "MMM-dd-yyyy";
 *     The LocalDate must be converted to a HijrahDate using
 *     the {@code islamic} calendar.</li>
 * <li>The second field is the offset, +2, +1, -1, -2 as parsed
 *     by Integer.valueOf to modify the length of the Hijrah month.</li>
 * <li>Empty lines are ignore.</li>
 * <li>Exceptions are throw for invalid formatted dates and offset,
 *     and other I/O errors on the file.
 * </ul>
 * <p>Example:</p>
 * <pre># Deviation data for islamicc calendar
 * Mar-23-2012 -1
 * Apr-22-2012 +1
 * May-21-2012 -1
 * Dec-14-2012 +1
 * </pre>
 *
 * @since 1.8
 */
final class HijrahDeviationReader {

    /**
     * Default prefix for name of deviation file; suffix is typeId.
     */
    private static final String DEFAULT_CONFIG_FILE_PREFIX = "hijrah_";

    /**
     * Read Hijrah_deviation.cfg file. The config file contains the deviation
     * data with format defined in the class javadoc.
     *
     * @param typeId the name of the calendar
     * @param calendarType the calendar type
     * @return {@code true} if the file was read and each entry accepted by the
     * Block; else {@code false} no configuration was done
     *
     * @throws IOException for zip/jar file handling exception.
     * @throws ParseException if the format of the configuration file is wrong.
     */
    static boolean readDeviation(String typeId, String calendarType,
            Block<HijrahChronology.Deviation> block) throws IOException, ParseException {
        InputStream is = getConfigFileInputStream(typeId);
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String line = "";
                int num = 0;
                while ((line = br.readLine()) != null) {
                    num++;
                    HijrahChronology.Deviation entry = parseLine(line, num);
                    if (entry != null) {
                        block.accept(entry);
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Parse each deviation element.
     *
     * @param line  a line to parse
     * @param num  line number
     * @return an Entry or null if the line is empty.
     * @throws ParseException if line has incorrect format.
     */
    private static HijrahChronology.Deviation parseLine(final String line, final int num) throws ParseException {
        int hash = line.indexOf("#");
        String nocomment = (hash < 0) ? line : line.substring(0, hash);
        String[] split = nocomment.split("\\s");
        if (split.length == 0 || split[0].isEmpty()) {
            return null;    // Nothing to parse
        }
        if (split.length != 2) {
            throw new ParseException("Less than 2 tokens on line : " + line + Arrays.toString(split) + ", split.length: " + split.length, num);
        }

        //element [0] is a date
        //element [1] is the offset

        LocalDate isoDate = DateTimeFormatter.ofPattern("MMM-dd-yyyy").parse(split[0], LocalDate::from);
        int offset = Integer.valueOf(split[1]);

        // Convert date to HijrahDate using the default Islamic Calendar

        HijrahDate hijrahDate = HijrahChronology.INSTANCE.date(isoDate);

        int year = hijrahDate.get(ChronoField.YEAR);
        int month = hijrahDate.get(ChronoField.MONTH_OF_YEAR);
        return new HijrahChronology.Deviation(year, month, year, month, offset);
    }


    /**
     * Return InputStream for deviation configuration file. The default location
     * of the deviation file is:
     * <pre>
     *   $CLASSPATH/java/time/calendar
     * </pre> And the default file name is:
     * <pre>
     *   hijrah_ + typeId + .cfg
     * </pre> The default location and file name can be overridden by setting
     * following two Java system properties.
     * <pre>
     *   Location: java.time.chrono.HijrahDate.deviationConfigDir
     *   File name: java.time.chrono.HijrahDate.File. + typeid
     * </pre> Regarding the file format, see readDeviationConfig() method for
     * details.
     *
     * @param typeId the name of the calendar deviation data
     * @return InputStream for file reading.
     * @throws IOException for zip/jar file handling exception.
     */
    private static InputStream getConfigFileInputStream(final String typeId) throws IOException {
        try {
            InputStream stream = AccessController
                    .doPrivileged((java.security.PrivilegedExceptionAction<InputStream>) () -> {
                String propFilename = "java.time.chrono.HijrahChronology.File." + typeId;
                String filename = System.getProperty(propFilename);
                File file = null;
                if (filename != null) {
                    file = new File(filename);
                } else {
                    String libDir = System.getProperty("java.home") + File.separator + "lib";
                    try {
                        libDir = FileSystems.getDefault().getPath(libDir).toRealPath().toString();
                    } catch(Exception e) {}
                    filename = DEFAULT_CONFIG_FILE_PREFIX + typeId + ".cfg";
                    file = new File(libDir, filename);
                }

                if (file.exists()) {
                    try {
                        return new FileInputStream(file);
                    } catch (IOException ioe) {
                        throw ioe;
                    }
                } else {
                    return null;
                }
            });
            return stream;
        } catch (Exception ex) {
            ex.printStackTrace();
            // Not working
            return null;
        }
    }
}
