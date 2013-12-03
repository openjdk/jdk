/*
 * This keeps a list of defines for pcsc-lite.
 *
 * MUSCLE SmartCard Development ( http://www.linuxnet.com )
 *
 * Copyright (C) 1999-2004
 *  David Corcoran <corcoran@linuxnet.com>
 *  Ludovic Rousseau <ludovic.rousseau@free.fr>
 *
 * $Id: pcsclite.h.in,v 1.47 2004/08/24 21:46:57 rousseau Exp $
 */

#ifndef __pcsclite_h__
#define __pcsclite_h__

#ifndef __sun_jdk
#include <wintypes.h>
#else
#include <sys/types.h>
#include <inttypes.h>
#ifdef BYTE
#error BYTE is already defined
#else
  typedef unsigned char BYTE;
#endif /* End BYTE */

        typedef unsigned char UCHAR;
        typedef unsigned char *PUCHAR;
        typedef unsigned short USHORT;
        typedef unsigned long ULONG;
        typedef void *LPVOID;
        typedef short BOOL;
        typedef unsigned long *PULONG;
        typedef const void *LPCVOID;
        typedef unsigned long DWORD;
        typedef unsigned long *PDWORD;
        typedef unsigned short WORD;
        typedef long LONG;
        typedef long RESPONSECODE;
        typedef const char *LPCTSTR;
        typedef const BYTE *LPCBYTE;
        typedef BYTE *LPBYTE;
        typedef DWORD *LPDWORD;
        typedef char *LPTSTR;

#endif

#ifdef __cplusplus
extern "C"
{
#endif

#ifdef WIN32
#include <winscard.h>
#else
typedef long SCARDCONTEXT;
typedef SCARDCONTEXT *PSCARDCONTEXT;
typedef SCARDCONTEXT *LPSCARDCONTEXT;
typedef long SCARDHANDLE;
typedef SCARDHANDLE *PSCARDHANDLE;
typedef SCARDHANDLE *LPSCARDHANDLE;

#define MAX_ATR_SIZE                    33      /* Maximum ATR size */

typedef struct
{
        const char *szReader;
        void *pvUserData;
        unsigned long dwCurrentState;
        unsigned long dwEventState;
        unsigned long cbAtr;
        unsigned char rgbAtr[MAX_ATR_SIZE];
}
SCARD_READERSTATE_A;

typedef SCARD_READERSTATE_A SCARD_READERSTATE, *PSCARD_READERSTATE_A,
        *LPSCARD_READERSTATE_A;

typedef struct _SCARD_IO_REQUEST
{
        unsigned long dwProtocol;       /* Protocol identifier */
        unsigned long cbPciLength;      /* Protocol Control Inf Length */
}
SCARD_IO_REQUEST, *PSCARD_IO_REQUEST, *LPSCARD_IO_REQUEST;

typedef const SCARD_IO_REQUEST *LPCSCARD_IO_REQUEST;

extern SCARD_IO_REQUEST g_rgSCardT0Pci, g_rgSCardT1Pci,
        g_rgSCardRawPci;

#define SCARD_PCI_T0    (&g_rgSCardT0Pci)
#define SCARD_PCI_T1    (&g_rgSCardT1Pci)
#define SCARD_PCI_RAW   (&g_rgSCardRawPci)

#define SCARD_S_SUCCESS                 0x00000000
#define SCARD_E_CANCELLED               0x80100002
#define SCARD_E_CANT_DISPOSE            0x8010000E
#define SCARD_E_INSUFFICIENT_BUFFER     0x80100008
#define SCARD_E_INVALID_ATR             0x80100015
#define SCARD_E_INVALID_HANDLE          0x80100003
#define SCARD_E_INVALID_PARAMETER       0x80100004
#define SCARD_E_INVALID_TARGET          0x80100005
#define SCARD_E_INVALID_VALUE           0x80100011
#define SCARD_E_NO_MEMORY               0x80100006
#define SCARD_F_COMM_ERROR              0x80100013
#define SCARD_F_INTERNAL_ERROR          0x80100001
#define SCARD_F_UNKNOWN_ERROR           0x80100014
#define SCARD_F_WAITED_TOO_LONG         0x80100007
#define SCARD_E_UNKNOWN_READER          0x80100009
#define SCARD_E_TIMEOUT                 0x8010000A
#define SCARD_E_SHARING_VIOLATION       0x8010000B
#define SCARD_E_NO_SMARTCARD            0x8010000C
#define SCARD_E_UNKNOWN_CARD            0x8010000D
#define SCARD_E_PROTO_MISMATCH          0x8010000F
#define SCARD_E_NOT_READY               0x80100010
#define SCARD_E_SYSTEM_CANCELLED        0x80100012
#define SCARD_E_NOT_TRANSACTED          0x80100016
#define SCARD_E_READER_UNAVAILABLE      0x80100017

#define SCARD_W_UNSUPPORTED_CARD        0x80100065
#define SCARD_W_UNRESPONSIVE_CARD       0x80100066
#define SCARD_W_UNPOWERED_CARD          0x80100067
#define SCARD_W_RESET_CARD              0x80100068
#define SCARD_W_REMOVED_CARD            0x80100069

#define SCARD_E_PCI_TOO_SMALL           0x80100019
#define SCARD_E_READER_UNSUPPORTED      0x8010001A
#define SCARD_E_DUPLICATE_READER        0x8010001B
#define SCARD_E_CARD_UNSUPPORTED        0x8010001C
#define SCARD_E_NO_SERVICE              0x8010001D
#define SCARD_E_SERVICE_STOPPED         0x8010001E

#define SCARD_SCOPE_USER                0x0000  /* Scope in user space */
#define SCARD_SCOPE_TERMINAL            0x0001  /* Scope in terminal */
#define SCARD_SCOPE_SYSTEM              0x0002  /* Scope in system */

#define SCARD_PROTOCOL_UNSET            0x0000  /* protocol not set */
#define SCARD_PROTOCOL_T0               0x0001  /* T=0 active protocol. */
#define SCARD_PROTOCOL_T1               0x0002  /* T=1 active protocol. */
#define SCARD_PROTOCOL_RAW              0x0004  /* Raw active protocol. */
#define SCARD_PROTOCOL_T15              0x0008  /* T=15 protocol. */

#define SCARD_PROTOCOL_ANY              (SCARD_PROTOCOL_T0|SCARD_PROTOCOL_T1)   /* IFD determines prot. */

#define SCARD_SHARE_EXCLUSIVE           0x0001  /* Exclusive mode only */
#define SCARD_SHARE_SHARED              0x0002  /* Shared mode only */
#define SCARD_SHARE_DIRECT              0x0003  /* Raw mode only */

#define SCARD_LEAVE_CARD                0x0000  /* Do nothing on close */
#define SCARD_RESET_CARD                0x0001  /* Reset on close */
#define SCARD_UNPOWER_CARD              0x0002  /* Power down on close */
#define SCARD_EJECT_CARD                0x0003  /* Eject on close */

#define SCARD_UNKNOWN                   0x0001  /* Unknown state */
#define SCARD_ABSENT                    0x0002  /* Card is absent */
#define SCARD_PRESENT                   0x0004  /* Card is present */
#define SCARD_SWALLOWED                 0x0008  /* Card not powered */
#define SCARD_POWERED                   0x0010  /* Card is powered */
#define SCARD_NEGOTIABLE                0x0020  /* Ready for PTS */
#define SCARD_SPECIFIC                  0x0040  /* PTS has been set */

#define SCARD_STATE_UNAWARE             0x0000  /* App wants status */
#define SCARD_STATE_IGNORE              0x0001  /* Ignore this reader */
#define SCARD_STATE_CHANGED             0x0002  /* State has changed */
#define SCARD_STATE_UNKNOWN             0x0004  /* Reader unknown */
#define SCARD_STATE_UNAVAILABLE         0x0008  /* Status unavailable */
#define SCARD_STATE_EMPTY               0x0010  /* Card removed */
#define SCARD_STATE_PRESENT             0x0020  /* Card inserted */
#define SCARD_STATE_ATRMATCH            0x0040  /* ATR matches card */
#define SCARD_STATE_EXCLUSIVE           0x0080  /* Exclusive Mode */
#define SCARD_STATE_INUSE               0x0100  /* Shared Mode */
#define SCARD_STATE_MUTE                0x0200  /* Unresponsive card */
#define SCARD_STATE_UNPOWERED           0x0400  /* Unpowered card */

/*
 * Tags for requesting card and reader attributes
 */

#define SCARD_ATTR_VALUE(Class, Tag) ((((ULONG)(Class)) << 16) | ((ULONG)(Tag)))

#define SCARD_CLASS_VENDOR_INFO     1   /* Vendor information definitions */
#define SCARD_CLASS_COMMUNICATIONS  2   /* Communication definitions */
#define SCARD_CLASS_PROTOCOL        3   /* Protocol definitions */
#define SCARD_CLASS_POWER_MGMT      4   /* Power Management definitions */
#define SCARD_CLASS_SECURITY        5   /* Security Assurance definitions */
#define SCARD_CLASS_MECHANICAL      6   /* Mechanical characteristic definitions */
#define SCARD_CLASS_VENDOR_DEFINED  7   /* Vendor specific definitions */
#define SCARD_CLASS_IFD_PROTOCOL    8   /* Interface Device Protocol options */
#define SCARD_CLASS_ICC_STATE       9   /* ICC State specific definitions */
#define SCARD_CLASS_SYSTEM     0x7fff   /* System-specific definitions */

#define SCARD_ATTR_VENDOR_NAME SCARD_ATTR_VALUE(SCARD_CLASS_VENDOR_INFO, 0x0100)
#define SCARD_ATTR_VENDOR_IFD_TYPE SCARD_ATTR_VALUE(SCARD_CLASS_VENDOR_INFO, 0x0101)
#define SCARD_ATTR_VENDOR_IFD_VERSION SCARD_ATTR_VALUE(SCARD_CLASS_VENDOR_INFO, 0x0102)
#define SCARD_ATTR_VENDOR_IFD_SERIAL_NO SCARD_ATTR_VALUE(SCARD_CLASS_VENDOR_INFO, 0x0103)
#define SCARD_ATTR_CHANNEL_ID SCARD_ATTR_VALUE(SCARD_CLASS_COMMUNICATIONS, 0x0110)
#define SCARD_ATTR_ASYNC_PROTOCOL_TYPES SCARD_ATTR_VALUE(SCARD_CLASS_PROTOCOL, 0x0120)
#define SCARD_ATTR_DEFAULT_CLK SCARD_ATTR_VALUE(SCARD_CLASS_PROTOCOL, 0x0121)
#define SCARD_ATTR_MAX_CLK SCARD_ATTR_VALUE(SCARD_CLASS_PROTOCOL, 0x0122)
#define SCARD_ATTR_DEFAULT_DATA_RATE SCARD_ATTR_VALUE(SCARD_CLASS_PROTOCOL, 0x0123)
#define SCARD_ATTR_MAX_DATA_RATE SCARD_ATTR_VALUE(SCARD_CLASS_PROTOCOL, 0x0124)
#define SCARD_ATTR_MAX_IFSD SCARD_ATTR_VALUE(SCARD_CLASS_PROTOCOL, 0x0125)
#define SCARD_ATTR_SYNC_PROTOCOL_TYPES SCARD_ATTR_VALUE(SCARD_CLASS_PROTOCOL, 0x0126)
#define SCARD_ATTR_POWER_MGMT_SUPPORT SCARD_ATTR_VALUE(SCARD_CLASS_POWER_MGMT, 0x0131)
#define SCARD_ATTR_USER_TO_CARD_AUTH_DEVICE SCARD_ATTR_VALUE(SCARD_CLASS_SECURITY, 0x0140)
#define SCARD_ATTR_USER_AUTH_INPUT_DEVICE SCARD_ATTR_VALUE(SCARD_CLASS_SECURITY, 0x0142)
#define SCARD_ATTR_CHARACTERISTICS SCARD_ATTR_VALUE(SCARD_CLASS_MECHANICAL, 0x0150)

#define SCARD_ATTR_CURRENT_PROTOCOL_TYPE SCARD_ATTR_VALUE(SCARD_CLASS_IFD_PROTOCOL, 0x0201)
#define SCARD_ATTR_CURRENT_CLK SCARD_ATTR_VALUE(SCARD_CLASS_IFD_PROTOCOL, 0x0202)
#define SCARD_ATTR_CURRENT_F SCARD_ATTR_VALUE(SCARD_CLASS_IFD_PROTOCOL, 0x0203)
#define SCARD_ATTR_CURRENT_D SCARD_ATTR_VALUE(SCARD_CLASS_IFD_PROTOCOL, 0x0204)
#define SCARD_ATTR_CURRENT_N SCARD_ATTR_VALUE(SCARD_CLASS_IFD_PROTOCOL, 0x0205)
#define SCARD_ATTR_CURRENT_W SCARD_ATTR_VALUE(SCARD_CLASS_IFD_PROTOCOL, 0x0206)
#define SCARD_ATTR_CURRENT_IFSC SCARD_ATTR_VALUE(SCARD_CLASS_IFD_PROTOCOL, 0x0207)
#define SCARD_ATTR_CURRENT_IFSD SCARD_ATTR_VALUE(SCARD_CLASS_IFD_PROTOCOL, 0x0208)
#define SCARD_ATTR_CURRENT_BWT SCARD_ATTR_VALUE(SCARD_CLASS_IFD_PROTOCOL, 0x0209)
#define SCARD_ATTR_CURRENT_CWT SCARD_ATTR_VALUE(SCARD_CLASS_IFD_PROTOCOL, 0x020a)
#define SCARD_ATTR_CURRENT_EBC_ENCODING SCARD_ATTR_VALUE(SCARD_CLASS_IFD_PROTOCOL, 0x020b)
#define SCARD_ATTR_EXTENDED_BWT SCARD_ATTR_VALUE(SCARD_CLASS_IFD_PROTOCOL, 0x020c)

#define SCARD_ATTR_ICC_PRESENCE SCARD_ATTR_VALUE(SCARD_CLASS_ICC_STATE, 0x0300)
#define SCARD_ATTR_ICC_INTERFACE_STATUS SCARD_ATTR_VALUE(SCARD_CLASS_ICC_STATE, 0x0301)
#define SCARD_ATTR_CURRENT_IO_STATE SCARD_ATTR_VALUE(SCARD_CLASS_ICC_STATE, 0x0302)
#define SCARD_ATTR_ATR_STRING SCARD_ATTR_VALUE(SCARD_CLASS_ICC_STATE, 0x0303)
#define SCARD_ATTR_ICC_TYPE_PER_ATR SCARD_ATTR_VALUE(SCARD_CLASS_ICC_STATE, 0x0304)

#define SCARD_ATTR_ESC_RESET SCARD_ATTR_VALUE(SCARD_CLASS_VENDOR_DEFINED, 0xA000)
#define SCARD_ATTR_ESC_CANCEL SCARD_ATTR_VALUE(SCARD_CLASS_VENDOR_DEFINED, 0xA003)
#define SCARD_ATTR_ESC_AUTHREQUEST SCARD_ATTR_VALUE(SCARD_CLASS_VENDOR_DEFINED, 0xA005)
#define SCARD_ATTR_MAXINPUT SCARD_ATTR_VALUE(SCARD_CLASS_VENDOR_DEFINED, 0xA007)

#define SCARD_ATTR_DEVICE_UNIT SCARD_ATTR_VALUE(SCARD_CLASS_SYSTEM, 0x0001)
#define SCARD_ATTR_DEVICE_IN_USE SCARD_ATTR_VALUE(SCARD_CLASS_SYSTEM, 0x0002)
#define SCARD_ATTR_DEVICE_FRIENDLY_NAME_A SCARD_ATTR_VALUE(SCARD_CLASS_SYSTEM, 0x0003)
#define SCARD_ATTR_DEVICE_SYSTEM_NAME_A SCARD_ATTR_VALUE(SCARD_CLASS_SYSTEM, 0x0004)
#define SCARD_ATTR_DEVICE_FRIENDLY_NAME_W SCARD_ATTR_VALUE(SCARD_CLASS_SYSTEM, 0x0005)
#define SCARD_ATTR_DEVICE_SYSTEM_NAME_W SCARD_ATTR_VALUE(SCARD_CLASS_SYSTEM, 0x0006)
#define SCARD_ATTR_SUPRESS_T1_IFS_REQUEST SCARD_ATTR_VALUE(SCARD_CLASS_SYSTEM, 0x0007)

#ifdef UNICODE
#define SCARD_ATTR_DEVICE_FRIENDLY_NAME SCARD_ATTR_DEVICE_FRIENDLY_NAME_W
#define SCARD_ATTR_DEVICE_SYSTEM_NAME SCARD_ATTR_DEVICE_SYSTEM_NAME_W
#else
#define SCARD_ATTR_DEVICE_FRIENDLY_NAME SCARD_ATTR_DEVICE_FRIENDLY_NAME_A
#define SCARD_ATTR_DEVICE_SYSTEM_NAME SCARD_ATTR_DEVICE_SYSTEM_NAME_A
#endif

#endif

/* PC/SC Lite specific extensions */
#define SCARD_W_INSERTED_CARD           0x8010006A
#define SCARD_E_UNSUPPORTED_FEATURE     0x8010001F

#define SCARD_SCOPE_GLOBAL              0x0003  /* Scope is global */

#define SCARD_RESET                     0x0001  /* Card was reset */
#define SCARD_INSERTED                  0x0002  /* Card was inserted */
#define SCARD_REMOVED                   0x0004  /* Card was removed */

#define BLOCK_STATUS_RESUME             0x00FF  /* Normal resume */
#define BLOCK_STATUS_BLOCKING           0x00FA  /* Function is blocking */

#define PCSCLITE_CONFIG_DIR             "/etc"

#ifndef USE_IPCDIR
#define PCSCLITE_IPC_DIR                "/var/run"
#else
#define PCSCLITE_IPC_DIR                USE_IPCDIR
#endif

#define PCSCLITE_READER_CONFIG          PCSCLITE_CONFIG_DIR "/reader.conf"
#define PCSCLITE_PUBSHM_FILE            PCSCLITE_IPC_DIR "/pcscd.pub"
#define PCSCLITE_CSOCK_NAME             PCSCLITE_IPC_DIR "/pcscd.comm"

#define PCSCLITE_SVC_IDENTITY           0x01030000      /* Service ID */

#ifndef INFINITE
#define INFINITE                        0xFFFFFFFF      /* Infinite timeout */
#endif
#define PCSCLITE_INFINITE_TIMEOUT       4320000         /* 50 day infinite t/o */

#define PCSCLITE_VERSION_NUMBER         "1.2.9-beta7"   /* Current version */
#define PCSCLITE_CLIENT_ATTEMPTS        120             /* Attempts to reach sv */
#define PCSCLITE_MCLIENT_ATTEMPTS       20              /* Attempts to reach sv */
#define PCSCLITE_STATUS_POLL_RATE       400000          /* Status polling rate */
#define PCSCLITE_MSG_KEY_LEN            16              /* App ID key length */
#define PCSCLITE_RW_ATTEMPTS            100             /* Attempts to rd/wrt */

/* Maximum applications */
#define PCSCLITE_MAX_APPLICATIONS                       16
/* Maximum contexts by application */
#define PCSCLITE_MAX_APPLICATION_CONTEXTS               16
/* Maximum of applications contexts that pcscd can accept */
#define PCSCLITE_MAX_APPLICATIONS_CONTEXTS \
        PCSCLITE_MAX_APPLICATIONS * PCSCLITE_MAX_APPLICATION_CONTEXTS
/* Maximum channels on a reader context */
#define PCSCLITE_MAX_READER_CONTEXT_CHANNELS            16
/* Maximum channels on an application context */
#define PCSCLITE_MAX_APPLICATION_CONTEXT_CHANNELS       16
/* Maximum readers context (a slot is count as a reader) */
#define PCSCLITE_MAX_READERS_CONTEXTS                   16

/* PCSCLITE_MAX_READERS is deprecated
 * use PCSCLITE_MAX_READERS_CONTEXTS instead */
/* extern int PCSCLITE_MAX_READERS __attribute__ ((deprecated)); */

#define PCSCLITE_MAX_THREADS            16      /* Stat change threads */
#define PCSCLITE_STATUS_WAIT            200000  /* Status Change Sleep */
#define PCSCLITE_TRANSACTION_TIMEOUT    40      /* Transaction timeout */
#define MAX_READERNAME                  52
#define MAX_LIBNAME                     100
#define MAX_DEVICENAME          255

#ifndef SCARD_ATR_LENGTH
#define SCARD_ATR_LENGTH                MAX_ATR_SIZE    /* Maximum ATR size */
#endif

/*
 * Enhanced messaging has been added to accommodate newer devices which have
 * more advanced capabilities, such as dedicated secure co-processors which
 * can stream and encrypt data over USB. In order to used enhanced messaging
 * you must define PCSCLITE_ENHANCED_MESSAGING in the framework(library),
 * the daemon, and your application
 */
#undef PCSCLITE_ENHANCED_MESSAGING
#ifndef PCSCLITE_ENHANCED_MESSAGING
#define PCSCLITE_MAX_MESSAGE_SIZE       2048    /* Transport msg len */
#define MAX_BUFFER_SIZE                 264     /* Maximum Tx/Rx Buffer */
#define PCSCLITE_SERVER_ATTEMPTS        5       /* Attempts to reach cl */
#else
/*
 * The message and buffer sizes must be multiples of 16.
 * The max message size must be at least large enough
 * to accommodate the transmit_struct
 */
#define PCSCLITE_MAX_MESSAGE_SIZE       (1<<17) /* enhanced (128K) msg len */
#define MAX_BUFFER_SIZE                 (1<<15) /* enhanced (32K) Tx/Rx Buffer */
#define PCSCLITE_SERVER_ATTEMPTS        200     /* To allow larger data reads/writes */
#endif

/*
 * Gets a stringified error response
 */
char *pcsc_stringify_error(long);

#ifdef __cplusplus
}
#endif

#endif
