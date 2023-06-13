/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.events;
import jdk.jfr.internal.Utils;
import jdk.jfr.internal.event.EventConfiguration;

public final class EventConfigurations {
    public static final EventConfiguration SOCKET_READ = Utils.getConfiguration(SocketReadEvent.class);
    public static final EventConfiguration SOCKET_WRITE = Utils.getConfiguration(SocketWriteEvent.class);
    public static final EventConfiguration FILE_READ = Utils.getConfiguration(FileReadEvent.class);
    public static final EventConfiguration FILE_WRITE = Utils.getConfiguration(FileWriteEvent.class);
    public static final EventConfiguration FILE_FORCE = Utils.getConfiguration(FileForceEvent.class);
    public static final EventConfiguration ERROR_THROWN = Utils.getConfiguration(ErrorThrownEvent.class);
    public static final EventConfiguration EXCEPTION_THROWN = Utils.getConfiguration(ExceptionThrownEvent.class);
}
