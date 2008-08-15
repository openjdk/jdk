
/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef WINDOWSFLAGS_H
#define WINDOWSFLAGS_H

extern BOOL      accelReset;         // reset registry 2d acceleration settings
extern BOOL      useD3D;             // d3d enabled flag
extern BOOL      forceD3DUsage;      // force d3d on or off
extern jboolean  g_offscreenSharing; // JAWT accelerated surface sharing
extern BOOL      checkRegistry;      // Diag tool: outputs 2d registry settings
extern BOOL      disableRegistry;    // Diag tool: disables registry interaction
extern BOOL      setHighDPIAware;    // whether to set High DPI Aware flag on Vista

void SetD3DEnabledFlag(JNIEnv *env, BOOL d3dEnabled, BOOL d3dSet);

BOOL IsD3DEnabled();
BOOL IsD3DForced();

#endif WINDOWSFLAGS_H
