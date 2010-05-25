/*
 * Copyright (c) 1998, 2000, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _JDGADEVICE_H_
#define _JDGADEVICE_H_

/*
 *   Interface for Supporting DGA to Framebuffers under Java
 *   -------------------------------------------------------
 *
 *  This interface will allow third party (and Sun) framebuffers which
 *  support the Direct Graphics Access (DGA) interface to be accessed with
 *  DGA in Java applications.
 *
 *  It coexists with the existing device-independent interfaces provided in
 *  libsunwjdga.so.
 *
 *  Framebuffers desiring access to Java DGA must supply a dynamically
 *  loaded library named "libjdga<fbname>.so", where <fbname> is the name
 *  returned by the VIS_GETIDENTIFIER ioctl as defined in the Solaris
 *  VISUAL environment (visual_io(7i)). For example, the Java DGA library
 *  for Sun's cg6 framebuffer will be named libjdgaSUNWcg6.so.
 *
 *  Because multiple instances of a framebuffer type may exist on a system,
 *  the device-dependent library must avoid the use of static or global
 *  variables for any framebuffer-related variables. In other words it
 *  must be reentrant.
 *
 *  The device-independent function Solaris_JDga_LibInit() is called in the
 *  static initializer for X11Graphics.java. Solaris_JDga_LibInit() will be
 *  modified to seek out a device-dependent DGA library as follows.
 *
 *  - DGA grab the DefaultRootWindow to get a Dga_drawable.
 *
 *  - Use the Dga_drawable ID to get the device file descriptor
 *       fd = dga_win_devfd(dga_draw_id)
 *
 *  - Use the VIS_GETIDENTIFIER ioctl to get the device name string.
 *
 *  - Construct the library path name using the device name string.
 *    The device-dependent library must be located in a location specified
 *    in the LD_LIBRARY_PATH.
 *
 *  - The device-dependent library will be dlopen'ed and then a dlsym will
 *    be performed for the function "SolarisJDgaDevOpen", which must
 *    be implemented by the device-dependent library writer.
 *
 *  - The function SolarisJDgaDevOpen() will then be called with a
 *    pointer to a SolarisJDgaDevInfo structure. This structure will
 *    have its major and minor version numbers filled in with their
 *    current values by the device-independent calling code. The
 *    device-dependent library must examine these version numbers and
 *    act as follows:
 *
 *      - In all cases, the device-dependent code should reset the
 *        supplied major and minor version numbers to those of the
 *        device-dependent library.
 *
 *      - If the supplied major version number is not the same as that
 *        of the device library, the open must fail and return JDGA_FAILED.
 *
 *      - If the supplied minor version number is less than or equal to
 *        the device minor version number, then backward compatibility
 *        is assumed and the open should return JDGA_SUCCESS.
 *
 *      - If the supplied minor version number is greater than the
 *        device minor version number, the open should also return
 *        JDGA_SUCCESS. The returned device minor version number will
 *        indicate to the device-independent code what features are
 *        supported in the device library.
 *
 *  - The function SolarisJDgaDevOpen() must also return a structure
 *    containing function pointers as given in the SolarisJDgaDevFunc
 *    structure below. The winlock and winunlock functions are
 *    required only if there is some device-specific locking to be done
 *    in addition to the DGA lock. If this is not required for the device
 *    these function pointers may be specified as NULL pointers.
 *
 */

#include <dga/dga.h>
#include <unistd.h>     /* ioctl */
#include <stdlib.h>
#include <sys/mman.h>   /* mmap */
#include <sys/visual_io.h>
#include <X11/Xlib.h>

/*
 * Status return codes
 */
#ifndef _DEFINE_JDGASTATUS_
#define _DEFINE_JDGASTATUS_
typedef enum {
    JDGA_SUCCESS        = 0,    /* operation succeeded */
    JDGA_FAILED         = 1     /* unable to complete operation */
} JDgaStatus;
#endif

/*
 * Structure to be filled in by device-dependent library's
 * SolarisJDgaDevOpen() function
 */
typedef struct {
  char *                         visidName; /* device name from ioctl */
  int                         majorVersion;
  int                         minorVersion;
  struct _SolarisJDgaDevFuncList* function;    /* Device function pointers */
} SolarisJDgaDevInfo;

/*
 * Structure returned by device-dependent library for a window
 */
typedef struct {
  SolarisJDgaDevInfo* devInfo;        /* Supplied by caller */
  Dga_drawable        dgaDraw;        /* Supplied by caller */
  caddr_t             mapAddr;        /* FB mapping for this window */
  int                 mapDepth;       /* Depth in bits */
  int                 mapWidth;       /* Width in pixels */
  int                 mapHeight;      /* Height in lines */
  int                 mapLineStride;  /* Byte stride line-to-line */
  int                 mapPixelStride; /* Byte stride pixel-to-pixel */
  void*               privateData;    /* Handle for device-dependent library */
} SolarisJDgaWinInfo;

typedef JDgaStatus (*SolarisJDgaDevFunction)(SolarisJDgaDevInfo*);
typedef JDgaStatus (*SolarisJDgaWinFunction)(SolarisJDgaWinInfo*);

/*
 * Structure for device-dependent functions
 */
typedef struct _SolarisJDgaDevFuncList {
  SolarisJDgaDevFunction devclose;
  SolarisJDgaWinFunction winopen;
  SolarisJDgaWinFunction winclose;
  SolarisJDgaWinFunction winlock;
  SolarisJDgaWinFunction winunlock;
} SolarisJDgaDevFuncList;

/*
 * Function to be supplied by the device-dependent library implementor.
 * It will accept a SolarisJDgaDevInfo structure with a filled-in
 * major and minor version number and will return updated version
 * numbers and the function pointers described below.
 */
typedef JDgaStatus SolarisJDgaDevOpenFunc(SolarisJDgaDevInfo* devInfo);

JDgaStatus SolarisJDgaDevOpen(SolarisJDgaDevInfo* devInfo);

/*
 * Functions supplied by the device-dependent library.
 * These function pointers will be returned to the
 * device-independent code in the SolarisJDgaDevFunc structure.
 */

JDgaStatus (*winopen)(SolarisJDgaWinInfo* info);

/*
 *  Fills in window-specific information in the supplied SolarisJDgaWinInfo
 *  structure. Because multiple windows may be open concurrently,
 *  implementations should avoid the use of static structures.
 */

JDgaStatus (*winclose)(SolarisJDgaWinInfo* info);

/*
 *  Frees any resources allocated by the device-dependent library for
 *  this window.  It may also perform an unmap if this is the last
 *  window using this particular memory map. Devices, such as the FFB,
 *  which support multiple depths, can have different device memory
 *  mappings for different depths.
 */

JDgaStatus (*winlock)(SolarisJDgaWinInfo* info);

/*
 *  Performs any device-specific locking needed for the framebuffer.
 *  In most cases it will be unnecessary. In those cases, the
 *  device-dependent library can supply NULL for this function pointer.
 */

JDgaStatus (*winunlock)(SolarisJDgaWinInfo* info);

/*
 *  Performs any device-specific unlocking needed for the framebuffer.
 *  In most cases it will be unnecessary. In those cases, the
 *  device-dependent library can supply NULL for this function pointer.
 */

JDgaStatus (*devclose)(SolarisJDgaDevInfo* info);

/*
 *  This function will be called at the last usage of the framebuffer
 *  device to allow the library to clean up any remaining resources.
 */

#endif  /* _JDGADEVICE_H_ */
