/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

/* Switch to the correct jni_md.h file without reliance on -I options. */
#ifdef TARGET_ARCH_x86
# include "jni_x86.h"
#endif
#ifdef TARGET_ARCH_sparc
# include "jni_sparc.h"
#endif
#ifdef TARGET_ARCH_zero
# include "jni_zero.h"
#endif
#ifdef TARGET_ARCH_arm
# include "jni_arm.h"
#endif
#ifdef TARGET_ARCH_ppc
# include "jni_ppc.h"
#endif


/*
  The local copies of JNI header files may be refreshed
  from a JDK distribution by means of these commands:

  cp ${JDK_DIST}/solaris/include/solaris/jni_md.h  ./jni_sparc.h
  cp ${JDK_DIST}/win32/include/win32/jni_md.h      ./jni_i486.h
  cp ${JDK_DIST}/win32/include/jni.h               ./jni.h

*/
