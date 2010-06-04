/*
 * Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
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

#define USE_ERROR
#define USE_TRACE

#include "PLATFORM_API_SolarisOS_Utils.h"

#define MAX_AUDIO_DEVICES 20

// not thread safe...
static AudioDevicePath globalADPaths[MAX_AUDIO_DEVICES];
static int globalADCount = -1;
static int globalADCacheTime = -1;
/* how many seconds do we cache devices */
#define AD_CACHE_TIME 30

// return seconds
long getTimeInSeconds() {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return tv.tv_sec;
}


int getAudioDeviceCount() {
    int count = MAX_AUDIO_DEVICES;

    getAudioDevices(globalADPaths, &count);
    return count;
}

/* returns TRUE if the path exists at all */
int addAudioDevice(char* path, AudioDevicePath* adPath, int* count) {
    int i;
    int found = 0;
    int fileExists = 0;
    // not thread safe...
    static struct stat statBuf;

    // get stats on the file
    if (stat(path, &statBuf) == 0) {
        // file exists.
        fileExists = 1;
        // If it is not yet in the adPath array, add it to the array
        for (i = 0; i < *count; i++) {
            if (adPath[i].st_ino == statBuf.st_ino
                && adPath[i].st_dev == statBuf.st_dev) {
                found = 1;
                break;
            }
        }
        if (!found) {
            adPath[*count].st_ino = statBuf.st_ino;
            adPath[*count].st_dev = statBuf.st_dev;
            strncpy(adPath[*count].path, path, MAX_NAME_LENGTH);
            adPath[*count].path[MAX_NAME_LENGTH] = 0;
            (*count)++;
            TRACE1("Added audio device %s\n", path);
        }
    }
    return fileExists;
}


void getAudioDevices(AudioDevicePath* adPath, int* count) {
    int maxCount = *count;
    char* audiodev;
    char devsound[15];
    int i;
    long timeInSeconds = getTimeInSeconds();

    if (globalADCount < 0
        || (getTimeInSeconds() - globalADCacheTime) > AD_CACHE_TIME
        || (adPath != globalADPaths)) {
        *count = 0;
        // first device, if set, is AUDIODEV variable
        audiodev = getenv("AUDIODEV");
        if (audiodev != NULL && audiodev[0] != 0) {
            addAudioDevice(audiodev, adPath, count);
        }
        // then try /dev/audio
        addAudioDevice("/dev/audio", adPath, count);
        // then go through all of the /dev/sound/? devices
        for (i = 0; i < 100; i++) {
            sprintf(devsound, "/dev/sound/%d", i);
            if (!addAudioDevice(devsound, adPath, count)) {
                break;
            }
        }
        if (adPath == globalADPaths) {
            /* commit cache */
            globalADCount = *count;
            /* set cache time */
            globalADCacheTime = timeInSeconds;
        }
    } else {
        /* return cache */
        *count = globalADCount;
    }
    // that's it
}

int getAudioDeviceDescriptionByIndex(int index, AudioDeviceDescription* adDesc, int getNames) {
    int count = MAX_AUDIO_DEVICES;
    int ret = 0;

    getAudioDevices(globalADPaths, &count);
    if (index>=0 && index < count) {
        ret = getAudioDeviceDescription(globalADPaths[index].path, adDesc, getNames);
    }
    return ret;
}

int getAudioDeviceDescription(char* path, AudioDeviceDescription* adDesc, int getNames) {
    int fd;
    int mixerMode;
    int len;
    audio_info_t info;
    audio_device_t deviceInfo;

    strncpy(adDesc->path, path, MAX_NAME_LENGTH);
    adDesc->path[MAX_NAME_LENGTH] = 0;
    strcpy(adDesc->pathctl, adDesc->path);
    strcat(adDesc->pathctl, "ctl");
    strcpy(adDesc->name, adDesc->path);
    adDesc->vendor[0] = 0;
    adDesc->version[0] = 0;
    adDesc->description[0] = 0;
    adDesc->maxSimulLines = 1;

    // try to open the pseudo device and get more information
    fd = open(adDesc->pathctl, O_WRONLY | O_NONBLOCK);
    if (fd >= 0) {
        close(fd);
        if (getNames) {
            fd = open(adDesc->pathctl, O_RDONLY);
            if (fd >= 0) {
                if (ioctl(fd, AUDIO_GETDEV, &deviceInfo) >= 0) {
                    strncpy(adDesc->vendor, deviceInfo.name, MAX_AUDIO_DEV_LEN);
                    adDesc->vendor[MAX_AUDIO_DEV_LEN] = 0;
                    strncpy(adDesc->version, deviceInfo.version, MAX_AUDIO_DEV_LEN);
                    adDesc->version[MAX_AUDIO_DEV_LEN] = 0;
                    /* add config string to the dev name
                     * creates a string like "/dev/audio (onboard1)"
                     */
                    len = strlen(adDesc->name) + 1;
                    if (MAX_NAME_LENGTH - len > 3) {
                        strcat(adDesc->name, " (");
                        strncat(adDesc->name, deviceInfo.config, MAX_NAME_LENGTH - len);
                        strcat(adDesc->name, ")");
                    }
                    adDesc->name[MAX_NAME_LENGTH-1] = 0;
                }
                if (ioctl(fd, AUDIO_MIXERCTL_GET_MODE, &mixerMode) >= 0) {
                    if (mixerMode == AM_MIXER_MODE) {
                        TRACE1(" getAudioDeviceDescription: %s is in mixer mode\n", adDesc->path);
                        adDesc->maxSimulLines = -1;
                    }
                } else {
                    ERROR1("ioctl AUDIO_MIXERCTL_GET_MODE failed on %s!\n", adDesc->path);
                }
                close(fd);
            } else {
                ERROR1("could not open %s!\n", adDesc->pathctl);
            }
        }
        return 1;
    }
    return 0;
}
