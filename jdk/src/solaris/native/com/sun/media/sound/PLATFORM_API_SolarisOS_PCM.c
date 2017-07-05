/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "DirectAudio.h"

#if USE_DAUDIO == TRUE


// The default buffer time
#define DEFAULT_PERIOD_TIME_MILLIS 50

///// implemented functions of DirectAudio.h

INT32 DAUDIO_GetDirectAudioDeviceCount() {
    return (INT32) getAudioDeviceCount();
}


INT32 DAUDIO_GetDirectAudioDeviceDescription(INT32 mixerIndex,
                                             DirectAudioDeviceDescription* description) {
    AudioDeviceDescription desc;

    if (getAudioDeviceDescriptionByIndex(mixerIndex, &desc, TRUE)) {
        description->maxSimulLines = desc.maxSimulLines;
        strncpy(description->name, desc.name, DAUDIO_STRING_LENGTH-1);
        description->name[DAUDIO_STRING_LENGTH-1] = 0;
        strncpy(description->vendor, desc.vendor, DAUDIO_STRING_LENGTH-1);
        description->vendor[DAUDIO_STRING_LENGTH-1] = 0;
        strncpy(description->version, desc.version, DAUDIO_STRING_LENGTH-1);
        description->version[DAUDIO_STRING_LENGTH-1] = 0;
        /*strncpy(description->description, desc.description, DAUDIO_STRING_LENGTH-1);*/
        strncpy(description->description, "Solaris Mixer", DAUDIO_STRING_LENGTH-1);
        description->description[DAUDIO_STRING_LENGTH-1] = 0;
        return TRUE;
    }
    return FALSE;

}

#define MAX_SAMPLE_RATES   20

void DAUDIO_GetFormats(INT32 mixerIndex, INT32 deviceID, int isSource, void* creator) {
    int fd = -1;
    AudioDeviceDescription desc;
    am_sample_rates_t      *sr;
    /* hardcoded bits and channels */
    int bits[] = {8, 16};
    int bitsCount = 2;
    int channels[] = {1, 2};
    int channelsCount = 2;
    /* for querying sample rates */
    int err;
    int ch, b, s;

    TRACE2("DAUDIO_GetFormats, mixer %d, isSource=%d\n", mixerIndex, isSource);
    if (getAudioDeviceDescriptionByIndex(mixerIndex, &desc, FALSE)) {
        fd = open(desc.pathctl, O_RDONLY);
    }
    if (fd < 0) {
        ERROR1("Couldn't open audio device ctl for device %d!\n", mixerIndex);
        return;
    }

    /* get sample rates */
    sr = (am_sample_rates_t*) malloc(AUDIO_MIXER_SAMP_RATES_STRUCT_SIZE(MAX_SAMPLE_RATES));
    if (sr == NULL) {
        ERROR1("DAUDIO_GetFormats: out of memory for mixer %d\n", (int) mixerIndex);
        close(fd);
        return;
    }

    sr->num_samp_rates = MAX_SAMPLE_RATES;
    sr->type = isSource?AUDIO_PLAY:AUDIO_RECORD;
    sr->samp_rates[0] = -2;
    err = ioctl(fd, AUDIO_MIXER_GET_SAMPLE_RATES, sr);
    if (err < 0) {
        ERROR1("  DAUDIO_GetFormats: AUDIO_MIXER_GET_SAMPLE_RATES failed for mixer %d!\n",
               (int)mixerIndex);
        ERROR2(" -> num_sample_rates=%d sample_rates[0] = %d\n",
               (int) sr->num_samp_rates,
               (int) sr->samp_rates[0]);
        /* Some Solaris 8 drivers fail for get sample rates!
         * Do as if we support all sample rates
         */
        sr->flags = MIXER_SR_LIMITS;
    }
    if ((sr->flags & MIXER_SR_LIMITS)
        || (sr->num_samp_rates > MAX_SAMPLE_RATES)) {
#ifdef USE_TRACE
        if ((sr->flags & MIXER_SR_LIMITS)) {
            TRACE1("  DAUDIO_GetFormats: floating sample rate allowed by mixer %d\n",
                   (int)mixerIndex);
        }
        if (sr->num_samp_rates > MAX_SAMPLE_RATES) {
            TRACE2("  DAUDIO_GetFormats: more than %d formats. Use -1 for sample rates mixer %d\n",
                   MAX_SAMPLE_RATES, (int)mixerIndex);
        }
#endif
        /*
         * Fake it to have only one sample rate: -1
         */
        sr->num_samp_rates = 1;
        sr->samp_rates[0] = -1;
    }
    close(fd);

    for (ch = 0; ch < channelsCount; ch++) {
        for (b = 0; b < bitsCount; b++) {
            for (s = 0; s < sr->num_samp_rates; s++) {
                DAUDIO_AddAudioFormat(creator,
                                      bits[b], /* significant bits */
                                      0, /* frameSize: let it be calculated */
                                      channels[ch],
                                      (float) ((int) sr->samp_rates[s]),
                                      DAUDIO_PCM, /* encoding - let's only do PCM */
                                      (bits[b] > 8)?TRUE:TRUE, /* isSigned */
#ifdef _LITTLE_ENDIAN
                                      FALSE /* little endian */
#else
                                      (bits[b] > 8)?TRUE:FALSE  /* big endian */
#endif
                                      );
            }
        }
    }
    free(sr);
}


typedef struct {
    int fd;
    audio_info_t info;
    int bufferSizeInBytes;
    int frameSize; /* storage size in Bytes */
    /* how many bytes were written or read */
    INT32 transferedBytes;
    /* if transferedBytes exceed 32-bit boundary,
     * it will be reset and positionOffset will receive
     * the offset
     */
    INT64 positionOffset;
} SolPcmInfo;


void* DAUDIO_Open(INT32 mixerIndex, INT32 deviceID, int isSource,
                  int encoding, float sampleRate, int sampleSizeInBits,
                  int frameSize, int channels,
                  int isSigned, int isBigEndian, int bufferSizeInBytes) {
    int err = 0;
    int openMode;
    AudioDeviceDescription desc;
    SolPcmInfo* info;

    TRACE0("> DAUDIO_Open\n");
    if (encoding != DAUDIO_PCM) {
        ERROR1(" DAUDIO_Open: invalid encoding %d\n", (int) encoding);
        return NULL;
    }

    info = (SolPcmInfo*) malloc(sizeof(SolPcmInfo));
    if (!info) {
        ERROR0("Out of memory\n");
        return NULL;
    }
    memset(info, 0, sizeof(SolPcmInfo));
    info->frameSize = frameSize;
    info->fd = -1;

    if (isSource) {
        openMode = O_WRONLY;
    } else {
        openMode = O_RDONLY;
    }

#ifndef __linux__
    /* blackdown does not use NONBLOCK */
    openMode |= O_NONBLOCK;
#endif

    if (getAudioDeviceDescriptionByIndex(mixerIndex, &desc, FALSE)) {
        info->fd = open(desc.path, openMode);
    }
    if (info->fd < 0) {
        ERROR1("Couldn't open audio device for mixer %d!\n", mixerIndex);
        free(info);
        return NULL;
    }
    /* set to multiple open */
    if (ioctl(info->fd, AUDIO_MIXER_MULTIPLE_OPEN, NULL) >= 0) {
        TRACE1("DAUDIO_Open: %s set to multiple open\n", desc.path);
    } else {
        ERROR1("DAUDIO_Open: ioctl AUDIO_MIXER_MULTIPLE_OPEN failed on %s!\n", desc.path);
    }

    AUDIO_INITINFO(&(info->info));
    /* need AUDIO_GETINFO ioctl to get this to work on solaris x86  */
    err = ioctl(info->fd, AUDIO_GETINFO, &(info->info));

    /* not valid to call AUDIO_SETINFO ioctl with all the fields from AUDIO_GETINFO. */
    AUDIO_INITINFO(&(info->info));

    if (isSource) {
        info->info.play.sample_rate = sampleRate;
        info->info.play.precision = sampleSizeInBits;
        info->info.play.channels = channels;
        info->info.play.encoding = AUDIO_ENCODING_LINEAR;
        info->info.play.buffer_size = bufferSizeInBytes;
        info->info.play.pause = 1;
    } else {
        info->info.record.sample_rate = sampleRate;
        info->info.record.precision = sampleSizeInBits;
        info->info.record.channels = channels;
        info->info.record.encoding = AUDIO_ENCODING_LINEAR;
        info->info.record.buffer_size = bufferSizeInBytes;
        info->info.record.pause = 1;
    }
    err = ioctl(info->fd, AUDIO_SETINFO,  &(info->info));
    if (err < 0) {
        ERROR0("DAUDIO_Open: could not set info!\n");
        DAUDIO_Close((void*) info, isSource);
        return NULL;
    }
    DAUDIO_Flush((void*) info, isSource);

    err = ioctl(info->fd, AUDIO_GETINFO, &(info->info));
    if (err >= 0) {
        if (isSource) {
            info->bufferSizeInBytes = info->info.play.buffer_size;
        } else {
            info->bufferSizeInBytes = info->info.record.buffer_size;
        }
        TRACE2("DAUDIO: buffersize in bytes: requested=%d, got %d\n",
               (int) bufferSizeInBytes,
               (int) info->bufferSizeInBytes);
    } else {
        ERROR0("DAUDIO_Open: cannot get info!\n");
        DAUDIO_Close((void*) info, isSource);
        return NULL;
    }
    TRACE0("< DAUDIO_Open: Opened device successfully.\n");
    return (void*) info;
}


int DAUDIO_Start(void* id, int isSource) {
    SolPcmInfo* info = (SolPcmInfo*) id;
    int err, modified;
    audio_info_t audioInfo;

    TRACE0("> DAUDIO_Start\n");

    AUDIO_INITINFO(&audioInfo);
    err = ioctl(info->fd, AUDIO_GETINFO, &audioInfo);
    if (err >= 0) {
        // unpause
        modified = FALSE;
        if (isSource && audioInfo.play.pause) {
            audioInfo.play.pause = 0;
            modified = TRUE;
        }
        if (!isSource && audioInfo.record.pause) {
            audioInfo.record.pause = 0;
            modified = TRUE;
        }
        if (modified) {
            err = ioctl(info->fd, AUDIO_SETINFO, &audioInfo);
        }
    }

    TRACE1("< DAUDIO_Start %s\n", (err>=0)?"success":"error");
    return (err >= 0)?TRUE:FALSE;
}

int DAUDIO_Stop(void* id, int isSource) {
    SolPcmInfo* info = (SolPcmInfo*) id;
    int err, modified;
    audio_info_t audioInfo;

    TRACE0("> DAUDIO_Stop\n");

    AUDIO_INITINFO(&audioInfo);
    err = ioctl(info->fd, AUDIO_GETINFO, &audioInfo);
    if (err >= 0) {
        // pause
        modified = FALSE;
        if (isSource && !audioInfo.play.pause) {
            audioInfo.play.pause = 1;
            modified = TRUE;
        }
        if (!isSource && !audioInfo.record.pause) {
            audioInfo.record.pause = 1;
            modified = TRUE;
        }
        if (modified) {
            err = ioctl(info->fd, AUDIO_SETINFO, &audioInfo);
        }
    }

    TRACE1("< DAUDIO_Stop %s\n", (err>=0)?"success":"error");
    return (err >= 0)?TRUE:FALSE;
}

void DAUDIO_Close(void* id, int isSource) {
    SolPcmInfo* info = (SolPcmInfo*) id;

    TRACE0("DAUDIO_Close\n");
    if (info != NULL) {
        if (info->fd >= 0) {
            DAUDIO_Flush(id, isSource);
            close(info->fd);
        }
        free(info);
    }
}

#ifndef USE_TRACE
/* close to 2^31 */
#define POSITION_MAX 2000000000
#else
/* for testing */
#define POSITION_MAX 1000000
#endif

void resetErrorFlagAndAdjustPosition(SolPcmInfo* info, int isSource, int count) {
    audio_info_t audioInfo;
    audio_prinfo_t* prinfo;
    int err;
    int offset = -1;
    int underrun = FALSE;
    int devBytes = 0;

    if (count > 0) {
        info->transferedBytes += count;

        if (isSource) {
            prinfo = &(audioInfo.play);
        } else {
            prinfo = &(audioInfo.record);
        }
        AUDIO_INITINFO(&audioInfo);
        err = ioctl(info->fd, AUDIO_GETINFO, &audioInfo);
        if (err >= 0) {
            underrun = prinfo->error;
            devBytes = prinfo->samples * info->frameSize;
        }
        AUDIO_INITINFO(&audioInfo);
        if (underrun) {
            /* if an underrun occurred, reset */
            ERROR1("DAUDIO_Write/Read: Underrun/overflow: adjusting positionOffset by %d:\n",
                   (devBytes - info->transferedBytes));
            ERROR1("    devBytes from %d to 0, ", devBytes);
            ERROR2(" positionOffset from %d to %d ",
                   (int) info->positionOffset,
                   (int) (info->positionOffset + info->transferedBytes));
            ERROR1(" transferedBytes from %d to 0\n",
                   (int) info->transferedBytes);
            prinfo->samples = 0;
            info->positionOffset += info->transferedBytes;
            info->transferedBytes = 0;
        }
        else if (info->transferedBytes > POSITION_MAX) {
            /* we will reset transferedBytes and
             * the samples field in prinfo
             */
            offset = devBytes;
            prinfo->samples = 0;
        }
        /* reset error flag */
        prinfo->error = 0;

        err = ioctl(info->fd, AUDIO_SETINFO, &audioInfo);
        if (err >= 0) {
            if (offset > 0) {
                /* upon exit of AUDIO_SETINFO, the samples parameter
                 * was set to the previous value. This is our
                 * offset.
                 */
                TRACE1("Adjust samplePos: offset=%d, ", (int) offset);
                TRACE2("transferedBytes=%d -> %d, ",
                       (int) info->transferedBytes,
                       (int) (info->transferedBytes - offset));
                TRACE2("positionOffset=%d -> %d\n",
                       (int) (info->positionOffset),
                       (int) (((int) info->positionOffset) + offset));
                info->transferedBytes -= offset;
                info->positionOffset += offset;
            }
        } else {
            ERROR0("DAUDIO: resetErrorFlagAndAdjustPosition ioctl failed!\n");
        }
    }
}

// returns -1 on error
int DAUDIO_Write(void* id, char* data, int byteSize) {
    SolPcmInfo* info = (SolPcmInfo*) id;
    int ret = -1;

    TRACE1("> DAUDIO_Write %d bytes\n", byteSize);
    if (info!=NULL) {
        ret = write(info->fd, data, byteSize);
        resetErrorFlagAndAdjustPosition(info, TRUE, ret);
        /* sets ret to -1 if buffer full, no error! */
        if (ret < 0) {
            ret = 0;
        }
    }
    TRACE1("< DAUDIO_Write: returning %d bytes.\n", ret);
    return ret;
}

// returns -1 on error
int DAUDIO_Read(void* id, char* data, int byteSize) {
    SolPcmInfo* info = (SolPcmInfo*) id;
    int ret = -1;

    TRACE1("> DAUDIO_Read %d bytes\n", byteSize);
    if (info != NULL) {
        ret = read(info->fd, data, byteSize);
        resetErrorFlagAndAdjustPosition(info, TRUE, ret);
        /* sets ret to -1 if buffer full, no error! */
        if (ret < 0) {
            ret = 0;
        }
    }
    TRACE1("< DAUDIO_Read: returning %d bytes.\n", ret);
    return ret;
}


int DAUDIO_GetBufferSize(void* id, int isSource) {
    SolPcmInfo* info = (SolPcmInfo*) id;
    if (info) {
        return info->bufferSizeInBytes;
    }
    return 0;
}

int DAUDIO_StillDraining(void* id, int isSource) {
    SolPcmInfo* info = (SolPcmInfo*) id;
    audio_info_t audioInfo;
    audio_prinfo_t* prinfo;
    int ret = FALSE;

    if (info!=NULL) {
        if (isSource) {
            prinfo = &(audioInfo.play);
        } else {
            prinfo = &(audioInfo.record);
        }
        /* check error flag */
        AUDIO_INITINFO(&audioInfo);
        ioctl(info->fd, AUDIO_GETINFO, &audioInfo);
        ret = (prinfo->error != 0)?FALSE:TRUE;
    }
    return ret;
}


int getDevicePosition(SolPcmInfo* info, int isSource) {
    audio_info_t audioInfo;
    audio_prinfo_t* prinfo;
    int err;

    if (isSource) {
        prinfo = &(audioInfo.play);
    } else {
        prinfo = &(audioInfo.record);
    }
    AUDIO_INITINFO(&audioInfo);
    err = ioctl(info->fd, AUDIO_GETINFO, &audioInfo);
    if (err >= 0) {
        /*TRACE2("---> device paused: %d  eof=%d\n",
               prinfo->pause, prinfo->eof);
        */
        return (int) (prinfo->samples * info->frameSize);
    }
    ERROR0("DAUDIO: getDevicePosition: ioctl failed!\n");
    return -1;
}

int DAUDIO_Flush(void* id, int isSource) {
    SolPcmInfo* info = (SolPcmInfo*) id;
    int err = -1;
    int pos;

    TRACE0("DAUDIO_Flush\n");
    if (info) {
        if (isSource) {
            err = ioctl(info->fd, I_FLUSH, FLUSHW);
        } else {
            err = ioctl(info->fd, I_FLUSH, FLUSHR);
        }
        if (err >= 0) {
            /* resets the transferedBytes parameter to
             * the current samples count of the device
             */
            pos = getDevicePosition(info, isSource);
            if (pos >= 0) {
                info->transferedBytes = pos;
            }
        }
    }
    if (err < 0) {
        ERROR0("ERROR in DAUDIO_Flush\n");
    }
    return (err < 0)?FALSE:TRUE;
}

int DAUDIO_GetAvailable(void* id, int isSource) {
    SolPcmInfo* info = (SolPcmInfo*) id;
    int ret = 0;
    int pos;

    if (info) {
        /* unfortunately, the STREAMS architecture
         * seems to not have a method for querying
         * the available bytes to read/write!
         * estimate it...
         */
        pos = getDevicePosition(info, isSource);
        if (pos >= 0) {
            if (isSource) {
                /* we usually have written more bytes
                 * to the queue than the device position should be
                 */
                ret = (info->bufferSizeInBytes) - (info->transferedBytes - pos);
            } else {
                /* for record, the device stream should
                 * be usually ahead of our read actions
                 */
                ret = pos - info->transferedBytes;
            }
            if (ret > info->bufferSizeInBytes) {
                ERROR2("DAUDIO_GetAvailable: available=%d, too big at bufferSize=%d!\n",
                       (int) ret, (int) info->bufferSizeInBytes);
                ERROR2("                     devicePos=%d, transferedBytes=%d\n",
                       (int) pos, (int) info->transferedBytes);
                ret = info->bufferSizeInBytes;
            }
            else if (ret < 0) {
                ERROR1("DAUDIO_GetAvailable: available=%d, in theory not possible!\n",
                       (int) ret);
                ERROR2("                     devicePos=%d, transferedBytes=%d\n",
                       (int) pos, (int) info->transferedBytes);
                ret = 0;
            }
        }
    }

    TRACE1("DAUDIO_GetAvailable returns %d bytes\n", ret);
    return ret;
}

INT64 DAUDIO_GetBytePosition(void* id, int isSource, INT64 javaBytePos) {
    SolPcmInfo* info = (SolPcmInfo*) id;
    int ret;
    int pos;
    INT64 result = javaBytePos;

    if (info) {
        pos = getDevicePosition(info, isSource);
        if (pos >= 0) {
            result = info->positionOffset + pos;
        }
    }

    //printf("getbyteposition: javaBytePos=%d , return=%d\n", (int) javaBytePos, (int) result);
    return result;
}


void DAUDIO_SetBytePosition(void* id, int isSource, INT64 javaBytePos) {
    SolPcmInfo* info = (SolPcmInfo*) id;
    int ret;
    int pos;

    if (info) {
        pos = getDevicePosition(info, isSource);
        if (pos >= 0) {
            info->positionOffset = javaBytePos - pos;
        }
    }
}

int DAUDIO_RequiresServicing(void* id, int isSource) {
    // never need servicing on Solaris
    return FALSE;
}

void DAUDIO_Service(void* id, int isSource) {
    // never need servicing on Solaris
}


#endif // USE_DAUDIO
