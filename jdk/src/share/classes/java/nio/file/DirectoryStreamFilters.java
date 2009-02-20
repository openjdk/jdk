/*
 * Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.nio.file;

import java.io.IOException;
import java.io.IOError;
import sun.nio.fs.MimeType;

/**
 * This class consists exclusively of static methods that construct or combine
 * filters.
 *
 * @since 1.7
 */

public final class DirectoryStreamFilters {
    private DirectoryStreamFilters() { }

    /**
     * Constructs a directory stream filter that filters directory entries by
     * their  <a href="http://www.ietf.org/rfc/rfc2045.txt">MIME</a> content
     * type. The directory stream filter's {@link
     * java.nio.file.DirectoryStream.Filter#accept accept} method returns {@code
     * true} if the content type of the directory entry can be determined by
     * invoking the {@link Files#probeContentType probeContentType} method, and
     * the content type matches the given content type.
     *
     * <p> The {@code type} parameter is the value of a Multipurpose Internet
     * Mail Extension (MIME) content type as defined by <a
     * href="http://www.ietf.org/rfc/rfc2045.txt"><i>RFC&nbsp;2045: Multipurpose
     * Internet Mail Extensions (MIME) Part One: Format of Internet Message
     * Bodies</i></a>. It is parsable according to the grammar in the RFC. Any
     * space characters (<code>'&#92;u0020'</code>) surrounding its components are
     * ignored. The {@code type} parameter is parsed into its primary and subtype
     * components which are used to match the primary and subtype components of
     * each directory entry's content type. Parameters are not allowed. The
     * primary type matches if it has value {@code '*'} or is equal to the
     * primary type of the directory entry's content type without regard to
     * case. The subtype matches if has the value {@code '*'} or is equal to the
     * subtype of the directory entry's content type without regard to case. If
     * both the primary and subtype match then the filter's {@code accept} method
     * returns {@code true}. If the content type of a directory entry cannot be
     * determined then the entry is filtered.
     *
     * <p> The {@code accept} method of the resulting directory stream filter
     * throws {@link IOError} if the probing of the content type fails by
     * throwing an {@link IOException}. Security exceptions are also propogated
     * to the caller of the {@code accept} method.
     *
     * <p> <b>Usage Example:</b>
     * Suppose we require to list only the HTML files in a directory.
     * <pre>
     *     DirectoryStream.Filter&lt;FileRef&gt; filter =
     *         DirectoryStreamFilters.newContentTypeFilter("text/html");
     * </pre>
     *
     * @param   type
     *          The content type
     *
     * @return  A new directory stream filter
     *
     * @throws  IllegalArgumentException
     *          If the {@code type} parameter cannot be parsed as a MIME type
     *          or it has parameters
     */
    public static <T extends FileRef> DirectoryStream.Filter<T>
        newContentTypeFilter(String type)
    {
        final MimeType matchType = MimeType.parse(type);
        if (matchType.hasParameters())
            throw new IllegalArgumentException("Parameters not allowed");
        return new DirectoryStream.Filter<T>() {
            @Override
            public boolean accept(T entry) {
                String fileType;
                try {
                    fileType = Files.probeContentType(entry);
                } catch (IOException x) {
                    throw new IOError(x);
                }
                if (fileType != null) {
                    return matchType.match(fileType);
                }
                return false;
            }
        };
    }

    /**
     * Returns a directory stream filter that {@link DirectoryStream.Filter#accept
     * accepts} a directory entry if the entry is accepted by all of the given
     * filters.
     *
     * <p> This method returns a filter that invokes, in iterator order, the
     * {@code accept} method of each of the filters. If {@code false} is returned
     * by any of the filters then the directory entry is filtered. If the
     * directory entry is not filtered then the resulting filter accepts the
     * entry. If the iterator returns zero elements then the resulting filter
     * accepts all directory entries.
     *
     * <p> <b>Usage Example:</b>
     * <pre>
     *     List&lt;DirectoryStream.Filter&lt;? super Path&gt;&gt; filters = ...
     *     DirectoryStream.Filter&lt;Path&gt; filter = DirectoryStreamFilters.allOf(filters);
     * </pre>
     *
     * @param   filters
     *          The sequence of filters
     *
     * @return  The resulting filter
     */
    public static <T> DirectoryStream.Filter<T>
        allOf(final Iterable<? extends DirectoryStream.Filter<? super T>> filters)
    {
        if (filters == null)
            throw new NullPointerException("'filters' is null");
        return new DirectoryStream.Filter<T>() {
            @Override
            public boolean accept(T entry) {
                for (DirectoryStream.Filter<? super T> filter: filters) {
                    if (!filter.accept(entry))
                        return false;
                }
                return true;
            }
        };
    }

    /**
     * Returns a directory stream filter that {@link DirectoryStream.Filter#accept
     * accepts} a directory entry if the entry is accepted by one or more of
     * the given filters.
     *
     * <p> This method returns a filter that invokes, in iteration order, the
     * {@code accept} method of each of filter. If {@code true} is returned by
     * any of the filters then the directory entry is accepted. If none of the
     * filters accepts the directory entry then it is filtered. If the iterator
     * returns zero elements then the resulting filter filters all directory
     * entries.
     *
     * @param   filters
     *          The sequence of filters
     *
     * @return  The resulting filter
     */
    public static <T> DirectoryStream.Filter<T>
        anyOf(final Iterable<? extends DirectoryStream.Filter<? super T>> filters)
    {
        if (filters == null)
            throw new NullPointerException("'filters' is null");
        return new DirectoryStream.Filter<T>() {
            @Override
            public boolean accept(T entry) {
                for (DirectoryStream.Filter<? super T> filter: filters) {
                    if (filter.accept(entry))
                        return true;
                }
                return false;
            }
        };
    }

    /**
     * Returns a directory stream filter that is the <em>complement</em> of the
     * given filter. The resulting filter {@link
     * java.nio.file.DirectoryStream.Filter#accept accepts} a directory entry
     * if filtered by the given filter, and filters any entries that are accepted
     * by the given filter.
     *
     * @param   filter
     *          The given filter
     *
     * @return  The resulting filter that is the complement of the given filter
     */
    public static <T> DirectoryStream.Filter<T>
        complementOf(final DirectoryStream.Filter<T> filter)
    {
        if (filter == null)
            throw new NullPointerException("'filter' is null");
        return new DirectoryStream.Filter<T>() {
            @Override
            public boolean accept(T entry) {
                return !filter.accept(entry);
            }
        };
    }
}
