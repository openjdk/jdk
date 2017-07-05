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
//#define USE_TRACE

#include "Ports.h"
#include "PLATFORM_API_SolarisOS_Utils.h"

#if USE_PORTS == TRUE

#define MONITOR_GAIN_STRING "Monitor Gain"

#define ALL_TARGET_PORT_COUNT 6

// define the following to not use audio_prinfo_t.mod_ports
#define SOLARIS7_COMPATIBLE

// Solaris audio defines
static int targetPorts[ALL_TARGET_PORT_COUNT] = {
    AUDIO_SPEAKER,
    AUDIO_HEADPHONE,
    AUDIO_LINE_OUT,
    AUDIO_AUX1_OUT,
    AUDIO_AUX2_OUT,
    AUDIO_SPDIF_OUT
};

static char* targetPortNames[ALL_TARGET_PORT_COUNT] = {
    "Speaker",
    "Headphone",
    "Line Out",
    "AUX1 Out",
    "AUX2 Out",
    "SPDIF Out"
};

// defined in Ports.h
static int targetPortJavaSoundMapping[ALL_TARGET_PORT_COUNT] = {
    PORT_DST_SPEAKER,
    PORT_DST_HEADPHONE,
    PORT_DST_LINE_OUT,
    PORT_DST_UNKNOWN,
    PORT_DST_UNKNOWN,
    PORT_DST_UNKNOWN,
};

#define ALL_SOURCE_PORT_COUNT 7

// Solaris audio defines
static int sourcePorts[ALL_SOURCE_PORT_COUNT] = {
    AUDIO_MICROPHONE,
    AUDIO_LINE_IN,
    AUDIO_CD,
    AUDIO_AUX1_IN,
    AUDIO_AUX2_IN,
    AUDIO_SPDIF_IN,
    AUDIO_CODEC_LOOPB_IN
};

static char* sourcePortNames[ALL_SOURCE_PORT_COUNT] = {
    "Microphone In",
    "Line In",
    "Compact Disc In",
    "AUX1 In",
    "AUX2 In",
    "SPDIF In",
    "Internal Loopback"
};

// Ports.h defines
static int sourcePortJavaSoundMapping[ALL_SOURCE_PORT_COUNT] = {
    PORT_SRC_MICROPHONE,
    PORT_SRC_LINE_IN,
    PORT_SRC_COMPACT_DISC,
    PORT_SRC_UNKNOWN,
    PORT_SRC_UNKNOWN,
    PORT_SRC_UNKNOWN,
    PORT_SRC_UNKNOWN
};

struct tag_PortControlID;

typedef struct tag_PortInfo {
    int fd;                    // file descriptor of the pseudo device
    audio_info_t audioInfo;
    // ports
    int targetPortCount;
    int sourcePortCount;
    // indexes to sourcePorts/targetPorts
    // contains first target ports, then source ports
    int ports[ALL_TARGET_PORT_COUNT + ALL_SOURCE_PORT_COUNT];
    // controls
    int maxControlCount;       // upper bound of number of controls
    int usedControlIDs;        // number of items already filled in controlIDs
    struct tag_PortControlID* controlIDs; // the control IDs themselves
} PortInfo;

#define PORT_CONTROL_TYPE_PLAY          0x4000000
#define PORT_CONTROL_TYPE_RECORD        0x8000000
#define PORT_CONTROL_TYPE_SELECT_PORT   1
#define PORT_CONTROL_TYPE_GAIN          2
#define PORT_CONTROL_TYPE_BALANCE       3
#define PORT_CONTROL_TYPE_MONITOR_GAIN  10
#define PORT_CONTROL_TYPE_OUTPUT_MUTED  11
#define PORT_CONTROL_TYPE_PLAYRECORD_MASK PORT_CONTROL_TYPE_PLAY | PORT_CONTROL_TYPE_RECORD
#define PORT_CONTROL_TYPE_MASK 0xFFFFFF


typedef struct tag_PortControlID {
    PortInfo*  portInfo;
    INT32                 controlType;  // PORT_CONTROL_TYPE_XX
    uint_t                port;
} PortControlID;


///// implemented functions of Ports.h

INT32 PORT_GetPortMixerCount() {
    return (INT32) getAudioDeviceCount();
}


INT32 PORT_GetPortMixerDescription(INT32 mixerIndex, PortMixerDescription* description) {
    AudioDeviceDescription desc;

    if (getAudioDeviceDescriptionByIndex(mixerIndex, &desc, TRUE)) {
        strncpy(description->name, desc.name, PORT_STRING_LENGTH-1);
        description->name[PORT_STRING_LENGTH-1] = 0;
        strncpy(description->vendor, desc.vendor, PORT_STRING_LENGTH-1);
        description->vendor[PORT_STRING_LENGTH-1] = 0;
        strncpy(description->version, desc.version, PORT_STRING_LENGTH-1);
        description->version[PORT_STRING_LENGTH-1] = 0;
        /*strncpy(description->description, desc.description, PORT_STRING_LENGTH-1);*/
        strncpy(description->description, "Solaris Ports", PORT_STRING_LENGTH-1);
        description->description[PORT_STRING_LENGTH-1] = 0;
        return TRUE;
    }
    return FALSE;
}


void* PORT_Open(INT32 mixerIndex) {
    PortInfo* info = NULL;
    int fd = -1;
    AudioDeviceDescription desc;
    int success = FALSE;

    TRACE0("PORT_Open\n");
    if (getAudioDeviceDescriptionByIndex(mixerIndex, &desc, FALSE)) {
        fd = open(desc.pathctl, O_RDWR);
    }
    if (fd < 0) {
        ERROR1("Couldn't open audio device ctl for device %d!\n", mixerIndex);
        return NULL;
    }

    info = (PortInfo*) malloc(sizeof(PortInfo));
    if (info != NULL) {
        memset(info, 0, sizeof(PortInfo));
        info->fd = fd;
        success = TRUE;
    }
    if (!success) {
        if (fd >= 0) {
            close(fd);
        }
        PORT_Close((void*) info);
        info = NULL;
    }
    return info;
}

void PORT_Close(void* id) {
    TRACE0("PORT_Close\n");
    if (id != NULL) {
        PortInfo* info = (PortInfo*) id;
        if (info->fd >= 0) {
            close(info->fd);
            info->fd = -1;
        }
        if (info->controlIDs) {
            free(info->controlIDs);
            info->controlIDs = NULL;
        }
        free(info);
    }
}



INT32 PORT_GetPortCount(void* id) {
    int ret = 0;
    PortInfo* info = (PortInfo*) id;
    if (info != NULL) {
        if (!info->targetPortCount && !info->sourcePortCount) {
            int i;
            AUDIO_INITINFO(&info->audioInfo);
            if (ioctl(info->fd, AUDIO_GETINFO, &info->audioInfo) >= 0) {
                for (i = 0; i < ALL_TARGET_PORT_COUNT; i++) {
                    if (info->audioInfo.play.avail_ports & targetPorts[i]) {
                        info->ports[info->targetPortCount] = i;
                        info->targetPortCount++;
                    }
#ifdef SOLARIS7_COMPATIBLE
                    TRACE3("Target %d %s: avail=%d\n", i, targetPortNames[i],
                           info->audioInfo.play.avail_ports & targetPorts[i]);
#else
                    TRACE4("Target %d %s: avail=%d  mod=%d\n", i, targetPortNames[i],
                           info->audioInfo.play.avail_ports & targetPorts[i],
                           info->audioInfo.play.mod_ports & targetPorts[i]);
#endif
                }
                for (i = 0; i < ALL_SOURCE_PORT_COUNT; i++) {
                    if (info->audioInfo.record.avail_ports & sourcePorts[i]) {
                        info->ports[info->targetPortCount + info->sourcePortCount] = i;
                        info->sourcePortCount++;
                    }
#ifdef SOLARIS7_COMPATIBLE
                    TRACE3("Source %d %s: avail=%d\n", i, sourcePortNames[i],
                           info->audioInfo.record.avail_ports & sourcePorts[i]);
#else
                    TRACE4("Source %d %s: avail=%d  mod=%d\n", i, sourcePortNames[i],
                           info->audioInfo.record.avail_ports & sourcePorts[i],
                           info->audioInfo.record.mod_ports & sourcePorts[i]);
#endif
                }
            }
        }
        ret = info->targetPortCount + info->sourcePortCount;
    }
    return ret;
}

int isSourcePort(PortInfo* info, INT32 portIndex) {
    return (portIndex >= info->targetPortCount);
}

INT32 PORT_GetPortType(void* id, INT32 portIndex) {
    PortInfo* info = (PortInfo*) id;
    if ((portIndex >= 0) && (portIndex < PORT_GetPortCount(id))) {
        if (isSourcePort(info, portIndex)) {
            return sourcePortJavaSoundMapping[info->ports[portIndex]];
        } else {
            return targetPortJavaSoundMapping[info->ports[portIndex]];
        }
    }
    return 0;
}

// pre-condition: portIndex must have been verified!
char* getPortName(PortInfo* info, INT32 portIndex) {
    char* ret = NULL;

    if (isSourcePort(info, portIndex)) {
        ret = sourcePortNames[info->ports[portIndex]];
    } else {
        ret = targetPortNames[info->ports[portIndex]];
    }
    return ret;
}

INT32 PORT_GetPortName(void* id, INT32 portIndex, char* name, INT32 len) {
    PortInfo* info = (PortInfo*) id;
    char* n;

    if ((portIndex >= 0) && (portIndex < PORT_GetPortCount(id))) {
        n = getPortName(info, portIndex);
        if (n) {
            strncpy(name, n, len-1);
            name[len-1] = 0;
            return TRUE;
        }
    }
    return FALSE;
}

void createPortControl(PortInfo* info, PortControlCreator* creator, INT32 portIndex,
                       INT32 type, void** controlObjects, int* controlCount) {
    PortControlID* controlID;
    void* newControl = NULL;
    int controlIndex;
    char* jsType = NULL;
    int isBoolean = FALSE;

    TRACE0(">createPortControl\n");

    // fill the ControlID structure and add this control
    if (info->usedControlIDs >= info->maxControlCount) {
        ERROR1("not enough free controlIDs !! maxControlIDs = %d\n", info->maxControlCount);
        return;
    }
    controlID = &(info->controlIDs[info->usedControlIDs]);
    controlID->portInfo = info;
    controlID->controlType = type;
    controlIndex = info->ports[portIndex];
    if (isSourcePort(info, portIndex)) {
        controlID->port = sourcePorts[controlIndex];
    } else {
        controlID->port = targetPorts[controlIndex];
    }
    switch (type & PORT_CONTROL_TYPE_MASK) {
    case PORT_CONTROL_TYPE_SELECT_PORT:
        jsType = CONTROL_TYPE_SELECT; isBoolean = TRUE; break;
    case PORT_CONTROL_TYPE_GAIN:
        jsType = CONTROL_TYPE_VOLUME;  break;
    case PORT_CONTROL_TYPE_BALANCE:
        jsType = CONTROL_TYPE_BALANCE; break;
    case PORT_CONTROL_TYPE_MONITOR_GAIN:
        jsType = CONTROL_TYPE_VOLUME; break;
    case PORT_CONTROL_TYPE_OUTPUT_MUTED:
        jsType = CONTROL_TYPE_MUTE; isBoolean = TRUE; break;
    }
    if (isBoolean) {
        TRACE0(" PORT_CONTROL_TYPE_BOOLEAN\n");
        newControl = (creator->newBooleanControl)(creator, controlID, jsType);
    }
    else if (jsType == CONTROL_TYPE_BALANCE) {
        TRACE0(" PORT_CONTROL_TYPE_BALANCE\n");
        newControl = (creator->newFloatControl)(creator, controlID, jsType,
                                                -1.0f, 1.0f, 2.0f / 65.0f, "");
    } else {
        TRACE0(" PORT_CONTROL_TYPE_FLOAT\n");
        newControl = (creator->newFloatControl)(creator, controlID, jsType,
                                                0.0f, 1.0f, 1.0f / 256.0f, "");
    }
    if (newControl) {
        controlObjects[*controlCount] = newControl;
        (*controlCount)++;
        info->usedControlIDs++;
    }
    TRACE0("<createPortControl\n");
}


void addCompoundControl(PortInfo* info, PortControlCreator* creator, char* name, void** controlObjects, int* controlCount) {
    void* compControl;

    TRACE1(">addCompoundControl %d controls\n", *controlCount);
    if (*controlCount) {
        // create compound control and add it to the vector
        compControl = (creator->newCompoundControl)(creator, name, controlObjects, *controlCount);
        if (compControl) {
            TRACE1(" addCompoundControl: calling addControl %p\n", compControl);
            (creator->addControl)(creator, compControl);
        }
        *controlCount = 0;
    }
    TRACE0("<addCompoundControl\n");
}

void addAllControls(PortInfo* info, PortControlCreator* creator, void** controlObjects, int* controlCount) {
    int i = 0;

    TRACE0(">addAllControl\n");
    // go through all controls and add them to the vector
    for (i = 0; i < *controlCount; i++) {
        (creator->addControl)(creator, controlObjects[i]);
    }
    *controlCount = 0;
    TRACE0("<addAllControl\n");
}

void PORT_GetControls(void* id, INT32 portIndex, PortControlCreator* creator) {
    PortInfo* info = (PortInfo*) id;
    int portCount = PORT_GetPortCount(id);
    void* controls[4];
    int controlCount = 0;
    INT32 type;
    int selectable = 1;

    TRACE4(">PORT_GetControls(id=%p, portIndex=%d). controlIDs=%p, maxControlCount=%d\n",
           id, portIndex, info->controlIDs, info->maxControlCount);
    if ((portIndex >= 0) && (portIndex < portCount)) {
        // if the memory isn't reserved for the control structures, allocate it
        if (!info->controlIDs) {
            int maxCount = 0;
            TRACE0("getControl: allocate mem\n");
            // get a maximum number of controls:
            // each port has a select, balance, and volume control.
            maxCount = 3 * portCount;
            // then there is monitorGain and outputMuted
            maxCount += (2 * info->targetPortCount);
            info->maxControlCount = maxCount;
            info->controlIDs = (PortControlID*) malloc(sizeof(PortControlID) * maxCount);
        }
        if (!isSourcePort(info, portIndex)) {
            type = PORT_CONTROL_TYPE_PLAY;
            // add master mute control
            createPortControl(info, creator, portIndex,
                              type | PORT_CONTROL_TYPE_OUTPUT_MUTED,
                              controls, &controlCount);
            addAllControls(info, creator, controls, &controlCount);
#ifdef SOLARIS7_COMPATIBLE
            selectable = info->audioInfo.play.avail_ports & targetPorts[info->ports[portIndex]];
#else
            selectable = info->audioInfo.play.mod_ports & targetPorts[info->ports[portIndex]];
#endif
        } else {
            type = PORT_CONTROL_TYPE_RECORD;
#ifdef SOLARIS7_COMPATIBLE
            selectable = info->audioInfo.record.avail_ports & sourcePorts[info->ports[portIndex]];
#else
            selectable = info->audioInfo.record.mod_ports & sourcePorts[info->ports[portIndex]];
#endif
        }
        // add a mixer strip with volume, ...
        createPortControl(info, creator, portIndex,
                          type | PORT_CONTROL_TYPE_GAIN,
                          controls, &controlCount);
        // ... balance, ...
        createPortControl(info, creator, portIndex,
                          type | PORT_CONTROL_TYPE_BALANCE,
                          controls, &controlCount);
        // ... and select control (if not always on)...
        if (selectable) {
            createPortControl(info, creator, portIndex,
                              type | PORT_CONTROL_TYPE_SELECT_PORT,
                              controls, &controlCount);
        }
        // ... packaged in a compound control.
        addCompoundControl(info, creator, getPortName(info, portIndex), controls, &controlCount);

        if (type == PORT_CONTROL_TYPE_PLAY) {
            // add a single strip for source ports with monitor gain
            createPortControl(info, creator, portIndex,
                              type | PORT_CONTROL_TYPE_MONITOR_GAIN,
                              controls, &controlCount);
            // also in a compound control
            addCompoundControl(info, creator, MONITOR_GAIN_STRING, controls, &controlCount);
        }
    }
    TRACE0("< PORT_getControls\n");
}

INT32 PORT_GetIntValue(void* controlIDV) {
    PortControlID* controlID = (PortControlID*) controlIDV;
    audio_info_t audioInfo;
    audio_prinfo_t* prinfo;

    AUDIO_INITINFO(&audioInfo);
    if (ioctl(controlID->portInfo->fd, AUDIO_GETINFO, &audioInfo) >= 0) {
        if (controlID->controlType & PORT_CONTROL_TYPE_PLAY) {
            prinfo = &(audioInfo.play);
        } else {
            prinfo = &(audioInfo.record);
        }
        switch (controlID->controlType & PORT_CONTROL_TYPE_MASK) {
        case PORT_CONTROL_TYPE_SELECT_PORT:
            return (prinfo->port & controlID->port)?TRUE:FALSE;
        case PORT_CONTROL_TYPE_OUTPUT_MUTED:
            return (audioInfo.output_muted)?TRUE:FALSE;
        default:
            ERROR1("PORT_GetIntValue: Wrong type %d !\n", controlID->controlType & PORT_CONTROL_TYPE_MASK);
        }
    }
    ERROR0("PORT_GetIntValue: Could not ioctl!\n");
    return 0;
}

void PORT_SetIntValue(void* controlIDV, INT32 value) {
    PortControlID* controlID = (PortControlID*) controlIDV;
    audio_info_t audioInfo;
    audio_prinfo_t* prinfo;
    int setPort;

    if (controlID->controlType & PORT_CONTROL_TYPE_PLAY) {
        prinfo = &(audioInfo.play);
    } else {
        prinfo = &(audioInfo.record);
    }
    switch (controlID->controlType & PORT_CONTROL_TYPE_MASK) {
    case PORT_CONTROL_TYPE_SELECT_PORT:
        // first try to just add this port. if that fails, set ONLY to this port.
        AUDIO_INITINFO(&audioInfo);
        if (ioctl(controlID->portInfo->fd, AUDIO_GETINFO, &audioInfo) >= 0) {
            if (value) {
                setPort = (prinfo->port | controlID->port);
            } else {
                setPort = (prinfo->port - controlID->port);
            }
            AUDIO_INITINFO(&audioInfo);
            prinfo->port = setPort;
            if (ioctl(controlID->portInfo->fd, AUDIO_SETINFO, &audioInfo) < 0) {
                // didn't work. Either this line doesn't support to select several
                // ports at once (e.g. record), or a real error
                if (value) {
                    // set to ONLY this port (and disable any other currently selected ports)
                    AUDIO_INITINFO(&audioInfo);
                    prinfo->port = controlID->port;
                    if (ioctl(controlID->portInfo->fd, AUDIO_SETINFO, &audioInfo) < 0) {
                        ERROR2("Error setting output select port %d to port %d!\n", controlID->port, controlID->port);
                    }
                } else {
                    // assume it's an error
                    ERROR2("Error setting output select port %d to port %d!\n", controlID->port, setPort);
                }
            }
            break;
        case PORT_CONTROL_TYPE_OUTPUT_MUTED:
            AUDIO_INITINFO(&audioInfo);
            audioInfo.output_muted = (value?TRUE:FALSE);
            if (ioctl(controlID->portInfo->fd, AUDIO_SETINFO, &audioInfo) < 0) {
                ERROR2("Error setting output muted on port %d to %d!\n", controlID->port, value);
            }
            break;
        default:
            ERROR1("PORT_SetIntValue: Wrong type %d !\n", controlID->controlType & PORT_CONTROL_TYPE_MASK);
        }
    }
}

float PORT_GetFloatValue(void* controlIDV) {
    PortControlID* controlID = (PortControlID*) controlIDV;
    audio_info_t audioInfo;
    audio_prinfo_t* prinfo;

    AUDIO_INITINFO(&audioInfo);
    if (ioctl(controlID->portInfo->fd, AUDIO_GETINFO, &audioInfo) >= 0) {
        if (controlID->controlType & PORT_CONTROL_TYPE_PLAY) {
            prinfo = &(audioInfo.play);
        } else {
            prinfo = &(audioInfo.record);
        }
        switch (controlID->controlType & PORT_CONTROL_TYPE_MASK) {
        case PORT_CONTROL_TYPE_GAIN:
            return ((float) (prinfo->gain - AUDIO_MIN_GAIN))
                / ((float) (AUDIO_MAX_GAIN - AUDIO_MIN_GAIN));
        case PORT_CONTROL_TYPE_BALANCE:
            return ((float) ((prinfo->balance - AUDIO_LEFT_BALANCE - AUDIO_MID_BALANCE) << 1))
                / ((float) (AUDIO_RIGHT_BALANCE - AUDIO_LEFT_BALANCE));
        case PORT_CONTROL_TYPE_MONITOR_GAIN:
            return ((float) (audioInfo.monitor_gain - AUDIO_MIN_GAIN))
                / ((float) (AUDIO_MAX_GAIN - AUDIO_MIN_GAIN));
        default:
            ERROR1("PORT_GetFloatValue: Wrong type %d !\n", controlID->controlType & PORT_CONTROL_TYPE_MASK);
        }
    }
    ERROR0("PORT_GetFloatValue: Could not ioctl!\n");
    return 0.0f;
}

void PORT_SetFloatValue(void* controlIDV, float value) {
    PortControlID* controlID = (PortControlID*) controlIDV;
    audio_info_t audioInfo;
    audio_prinfo_t* prinfo;

    AUDIO_INITINFO(&audioInfo);

    if (controlID->controlType & PORT_CONTROL_TYPE_PLAY) {
        prinfo = &(audioInfo.play);
    } else {
        prinfo = &(audioInfo.record);
    }
    switch (controlID->controlType & PORT_CONTROL_TYPE_MASK) {
    case PORT_CONTROL_TYPE_GAIN:
        prinfo->gain = AUDIO_MIN_GAIN
            + (int) ((value * ((float) (AUDIO_MAX_GAIN - AUDIO_MIN_GAIN))) + 0.5f);
        break;
    case PORT_CONTROL_TYPE_BALANCE:
        prinfo->balance =  AUDIO_LEFT_BALANCE + AUDIO_MID_BALANCE
            + ((int) (value * ((float) ((AUDIO_RIGHT_BALANCE - AUDIO_LEFT_BALANCE) >> 1))) + 0.5f);
        break;
    case PORT_CONTROL_TYPE_MONITOR_GAIN:
        audioInfo.monitor_gain = AUDIO_MIN_GAIN
            + (int) ((value * ((float) (AUDIO_MAX_GAIN - AUDIO_MIN_GAIN))) + 0.5f);
        break;
    default:
        ERROR1("PORT_SetFloatValue: Wrong type %d !\n", controlID->controlType & PORT_CONTROL_TYPE_MASK);
        return;
    }
    if (ioctl(controlID->portInfo->fd, AUDIO_SETINFO, &audioInfo) < 0) {
        ERROR0("PORT_SetFloatValue: Could not ioctl!\n");
    }
}

#endif // USE_PORTS
