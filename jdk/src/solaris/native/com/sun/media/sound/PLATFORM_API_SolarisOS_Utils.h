/*
 * Copyright 2002-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <Utilities.h>
#include <string.h>
#include <stdlib.h>
#include <fcntl.h>
/* does not work on Solaris 2.7 */
#include <sys/audio.h>
#include <sys/mixer.h>
#include <sys/types.h>
#ifndef __linux__
#include <stropts.h>
#endif
#include <sys/conf.h>
#include <sys/stat.h>
#include <unistd.h>

#ifndef PLATFORM_API_SOLARISOS_UTILS_H_INCLUDED
#define PLATFORM_API_SOLARISOS_UTILS_H_INCLUDED

/* defines for Solaris 2.7
   #ifndef AUDIO_AUX1_OUT
   #define AUDIO_AUX1_OUT   (0x08)  // output to aux1 out
   #define AUDIO_AUX2_OUT   (0x10)  // output to aux2 out
   #define AUDIO_SPDIF_OUT  (0x20)  // output to SPDIF port
   #define AUDIO_AUX1_IN    (0x08)    // input from aux1 in
   #define AUDIO_AUX2_IN    (0x10)    // input from aux2 in
   #define AUDIO_SPDIF_IN   (0x20)    // input from SPDIF port
   #endif
*/

/* input from Codec inter. loopback */
#ifndef AUDIO_CODEC_LOOPB_IN
#define AUDIO_CODEC_LOOPB_IN       (0x40)
#endif


#define MAX_NAME_LENGTH 300

typedef struct tag_AudioDevicePath {
    char path[MAX_NAME_LENGTH];
    ino_t st_ino; // inode number to detect duplicate devices
    dev_t st_dev; // device ID to detect duplicate audio devices
} AudioDevicePath;

typedef struct tag_AudioDeviceDescription {
    INT32 maxSimulLines;
    char path[MAX_NAME_LENGTH+1];
    char pathctl[MAX_NAME_LENGTH+4];
    char name[MAX_NAME_LENGTH+1];
    char vendor[MAX_NAME_LENGTH+1];
    char version[MAX_NAME_LENGTH+1];
    char description[MAX_NAME_LENGTH+1];
} AudioDeviceDescription;

int getAudioDeviceCount();

/*
 * adPath is an array of AudioDevicePath structures
 * count contains initially the number of elements in adPath
 *       and will be set to the returned number of paths.
 */
void getAudioDevices(AudioDevicePath* adPath, int* count);

/*
 * fills adDesc from the audio device given in path
 * returns 0 if an error occured
 * if getNames is 0, only path and pathctl are filled
 */
int getAudioDeviceDescription(char* path, AudioDeviceDescription* adDesc, int getNames);
int getAudioDeviceDescriptionByIndex(int index, AudioDeviceDescription* adDesc, int getNames);


#endif // PLATFORM_API_SOLARISOS_UTILS_H_INCLUDED
