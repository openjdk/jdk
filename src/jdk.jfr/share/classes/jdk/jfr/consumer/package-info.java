/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This package contains classes for consuming Flight Recorder data.
 * <p>
 * In the following example, the program prints a histogram of all method samples in a recording.
 * <pre>
 * <code>
 *   public static void main(String[] args) {
 *     if (args.length != 0) {
 *       System.out.println("Must specify recording file.");
 *       return;
 *     }
 *     try (RecordingFile f = new RecordingFile(Paths.get(args[0]))) {
 *       Map{@literal <}String, SimpleEntry{@literal <}String, Integer{@literal >}{@literal >} histogram = new HashMap{@literal <}{@literal >}();
 *       int total = 0;
 *       while (f.hasMoreEvents()) {
 *         RecordedEvent event = f.readEvent();
 *         if (event.getEventType().getName().equals("jdk.ExecutionSample")) {
 *           RecordedStackTrace s = event.getStackTrace();
 *           if (s != null) {
 *             RecordedFrame topFrame= s.getFrames().get(0);
 *             if (topFrame.isJavaFrame())  {
 *               RecordedMethod method = topFrame.getMethod();
 *               String methodName = method.getType().getName() + "#" + method.getName() + " " + method.getDescriptor();
 *               Entry entry = histogram.computeIfAbsent(methodName, u -{@literal >} new SimpleEntry{@literal <}String, Integer{@literal >}(methodName, 0));
 *               entry.setValue(entry.getValue() + 1);
 *               total++;
 *             }
 *           }
 *         }
 *       }
 *       List{@literal <}SimpleEntry{@literal <}String, Integer{@literal >}{@literal >} entries = new ArrayList{@literal <}{@literal >}(histogram.values());
 *       entries.sort((u, v) -{@literal >} v.getValue().compareTo(u.getValue()));
 *       for (SimpleEntry{@literal <}String, Integer{@literal >} c : entries) {
 *         System.out.printf("%2.0f%% %s\n", 100 * (float) c.getValue() / total, c.getKey());
 *       }
 *       System.out.println("\nSample count: " + total);
 *     } catch (IOException ioe) {
 *       System.out.println("Error reading file " + args[0] + ". " + ioe.getMessage());
 *     }
 *   }
 * </code>
 * </pre>
 * <p>
 * <b>Null-handling</b>
 * <p>
 * All methods define whether they accept or return {@code null} in the Javadoc.
 * Typically this is expressed as {@code "not null"}. If a {@code null}
 * parameter is used where it is not allowed, a
 * {@code java.lang.NullPointerException} is thrown. If a {@code null}
 * parameters is passed to a method that throws other exceptions, such as
 * {@code java.io.IOException}, the {@code java.lang.NullPointerException} takes
 * precedence, unless the Javadoc for the method explicitly states how
 * {@code null} is handled, i.e. by throwing {@code java.lang.IllegalArgumentException}.
 *
 * @since 9
 */
package jdk.jfr.consumer;
