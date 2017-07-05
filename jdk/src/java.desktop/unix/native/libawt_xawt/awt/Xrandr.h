/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * $XFree86: xc/lib/Xrandr/Xrandr.h,v 1.9 2002/09/29 23:39:44 keithp Exp $
 *
 * Copyright © 2000 Compaq Computer Corporation, Inc.
 * Copyright © 2002 Hewlett-Packard Company, Inc.
 *
 * Permission to use, copy, modify, distribute, and sell this software and its
 * documentation for any purpose is hereby granted without fee, provided that
 * the above copyright notice appear in all copies and that both that
 * copyright notice and this permission notice appear in supporting
 * documentation, and that the name of Compaq not be used in advertising or
 * publicity pertaining to distribution of the software without specific,
 * written prior permission.  HP makes no representations about the
 * suitability of this software for any purpose.  It is provided "as is"
 * without express or implied warranty.
 *
 * HP DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE, INCLUDING ALL
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL COMPAQ
 * BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 * Author:  Jim Gettys, HP Labs, HP.
 */

#ifndef _XRANDR_H_
#define _XRANDR_H_

/*#include <X11/extensions/randr.h>*/
#include "randr.h"

#include <X11/Xfuncproto.h>

_XFUNCPROTOBEGIN


typedef struct {
    int width, height;
    int mwidth, mheight;
} XRRScreenSize;

/*
 *  Events.
 */

typedef struct {
    int type;                   /* event base */
    unsigned long serial;       /* # of last request processed by server */
    Bool send_event;            /* true if this came from a SendEvent request */
    Display *display;           /* Display the event was read from */
    Window window;              /* window which selected for this event */
    Window root;                /* Root window for changed screen */
    Time timestamp;             /* when the screen change occurred */
    Time config_timestamp;      /* when the last configuration change */
    SizeID size_index;
    SubpixelOrder subpixel_order;
    Rotation rotation;
    int width;
    int height;
    int mwidth;
    int mheight;
} XRRScreenChangeNotifyEvent;

typedef XID RROutput;
typedef XID RRCrtc;
typedef XID RRMode;

typedef unsigned long XRRModeFlags;

typedef struct {
    RRMode              id;
    unsigned int        width;
    unsigned int        height;
    unsigned long       dotClock;
    unsigned int        hSyncStart;
    unsigned int        hSyncEnd;
    unsigned int        hTotal;
    unsigned int        hSkew;
    unsigned int        vSyncStart;
    unsigned int        vSyncEnd;
    unsigned int        vTotal;
    char                *name;
    unsigned int        nameLength;
    XRRModeFlags        modeFlags;
} XRRModeInfo;

typedef struct {
    Time        timestamp;
    Time        configTimestamp;
    int         ncrtc;
    RRCrtc      *crtcs;
    int         noutput;
    RROutput    *outputs;
    int         nmode;
    XRRModeInfo *modes;
} XRRScreenResources;

typedef struct {
    Time            timestamp;
    RRCrtc          crtc;
    char            *name;
    int             nameLen;
    unsigned long   mm_width;
    unsigned long   mm_height;
    Connection      connection;
    SubpixelOrder   subpixel_order;
    int             ncrtc;
    RRCrtc          *crtcs;
    int             nclone;
    RROutput        *clones;
    int             nmode;
    int             npreferred;
    RRMode          *modes;
} XRROutputInfo;

typedef struct {
    Time            timestamp;
    int             x, y;
    unsigned int    width, height;
    RRMode          mode;
    Rotation        rotation;
    int             noutput;
    RROutput        *outputs;
    Rotation        rotations;
    int             npossible;
    RROutput        *possible;
} XRRCrtcInfo;

XRRScreenResources *XRRGetScreenResources (Display *dpy, Window window);

void XRRFreeScreenResources (XRRScreenResources *resources);

XRROutputInfo * XRRGetOutputInfo (Display *dpy, XRRScreenResources *resources,
                                                               RROutput output);
void XRRFreeOutputInfo (XRROutputInfo *outputInfo);

XRRCrtcInfo *XRRGetCrtcInfo (Display *dpy, XRRScreenResources *resources,
                                                                   RRCrtc crtc);
void XRRFreeCrtcInfo (XRRCrtcInfo *crtcInfo);


/* internal representation is private to the library */
typedef struct _XRRScreenConfiguration XRRScreenConfiguration;

Bool XRRQueryExtension (Display *dpy, int *event_basep, int *error_basep);
Status XRRQueryVersion (Display *dpy,
                            int     *major_versionp,
                            int     *minor_versionp);

XRRScreenConfiguration *XRRGetScreenInfo (Display *dpy,
                                          Drawable draw);

void XRRFreeScreenConfigInfo (XRRScreenConfiguration *config);

/*
 * Note that screen configuration changes are only permitted if the client can
 * prove it has up to date configuration information.  We are trying to
 * insist that it become possible for screens to change dynamically, so
 * we want to ensure the client knows what it is talking about when requesting
 * changes.
 */
Status XRRSetScreenConfig (Display *dpy,
                           XRRScreenConfiguration *config,
                           Drawable draw,
                           int size_index,
                           Rotation rotation,
                           Time timestamp);

/* added in v1.1, sorry for the lame name */
Status XRRSetScreenConfigAndRate (Display *dpy,
                                  XRRScreenConfiguration *config,
                                  Drawable draw,
                                  int size_index,
                                  Rotation rotation,
                                  short rate,
                                  Time timestamp);


Rotation XRRConfigRotations(XRRScreenConfiguration *config, Rotation *current_rotation);

Time XRRConfigTimes (XRRScreenConfiguration *config, Time *config_timestamp);

XRRScreenSize *XRRConfigSizes(XRRScreenConfiguration *config, int *nsizes);

short *XRRConfigRates (XRRScreenConfiguration *config, int sizeID, int *nrates);

SizeID XRRConfigCurrentConfiguration (XRRScreenConfiguration *config,
                              Rotation *rotation);

short XRRConfigCurrentRate (XRRScreenConfiguration *config);

int XRRRootToScreen(Display *dpy, Window root);

/*
 * returns the screen configuration for the specified screen; does a lazy
 * evalution to delay getting the information, and caches the result.
 * These routines should be used in preference to XRRGetScreenInfo
 * to avoid unneeded round trips to the X server.  These are new
 * in protocol version 0.1.
 */


XRRScreenConfiguration *XRRScreenConfig(Display *dpy, int screen);
XRRScreenConfiguration *XRRConfig(Screen *screen);
void XRRSelectInput(Display *dpy, Window window, int mask);

/*
 * the following are always safe to call, even if RandR is not implemented
 * on a screen
 */


Rotation XRRRotations(Display *dpy, int screen, Rotation *current_rotation);
XRRScreenSize *XRRSizes(Display *dpy, int screen, int *nsizes);
short *XRRRates (Display *dpy, int screen, int sizeID, int *nrates);
Time XRRTimes (Display *dpy, int screen, Time *config_timestamp);


/*
 * intended to take RRScreenChangeNotify,  or
 * ConfigureNotify (on the root window)
 * returns 1 if it is an event type it understands, 0 if not
 */
int XRRUpdateConfiguration(XEvent *event);

_XFUNCPROTOEND

#endif /* _XRANDR_H_ */
