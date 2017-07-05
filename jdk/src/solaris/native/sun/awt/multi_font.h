/*
 * Copyright 1996-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
/*
 * header for Multi Font String
 */
#ifndef _MULTI_FONT_H_
#define _MULTI_FONT_H_

#ifndef HEADLESS
jobject awtJNI_GetFont(JNIEnv *env,jobject this);
jboolean awtJNI_IsMultiFont(JNIEnv *env,jobject this);
jboolean awtJNI_IsMultiFontMetrics(JNIEnv *env,jobject this);
#ifndef XAWT
XmString awtJNI_MakeMultiFontString(JNIEnv *env,jstring s,jobject font);
XmFontList awtJNI_GetFontList(JNIEnv *env,jobject font);
#endif
XFontSet awtJNI_MakeFontSet(JNIEnv *env,jobject font);
struct FontData *awtJNI_GetFontData(JNIEnv *env,jobject font, char **errmsg);
int32_t awtJNI_GetMFStringWidth(JNIEnv * env, jcharArray s, int32_t offset,
                                int32_t length, jobject font);
#endif /* !HEADLESS */

#endif /* _MULTI_FONT_H_ */
