/* Simple Plugin API */
/* SPDX-FileCopyrightText: Copyright © 2018 Wim Taymans */
/* SPDX-License-Identifier: MIT */

#ifndef SPA_AUDIO_RAW_H
#define SPA_AUDIO_RAW_H

#include <stdint.h>

#include <spa/utils/endian.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * \addtogroup spa_param
 * \{
 */

/* This is the max number of channels, changing this will change the
 * size of some helper structures. This value should be at least 64 */
#ifndef SPA_AUDIO_MAX_CHANNELS
#define SPA_AUDIO_MAX_CHANNELS	64u
#endif

enum spa_audio_format {
	SPA_AUDIO_FORMAT_UNKNOWN,
	SPA_AUDIO_FORMAT_ENCODED,

	/* interleaved formats */
	SPA_AUDIO_FORMAT_START_Interleaved	= 0x100,
	SPA_AUDIO_FORMAT_S8,
	SPA_AUDIO_FORMAT_U8,
	SPA_AUDIO_FORMAT_S16_LE,
	SPA_AUDIO_FORMAT_S16_BE,
	SPA_AUDIO_FORMAT_U16_LE,
	SPA_AUDIO_FORMAT_U16_BE,
	SPA_AUDIO_FORMAT_S24_32_LE,
	SPA_AUDIO_FORMAT_S24_32_BE,
	SPA_AUDIO_FORMAT_U24_32_LE,
	SPA_AUDIO_FORMAT_U24_32_BE,
	SPA_AUDIO_FORMAT_S32_LE,
	SPA_AUDIO_FORMAT_S32_BE,
	SPA_AUDIO_FORMAT_U32_LE,
	SPA_AUDIO_FORMAT_U32_BE,
	SPA_AUDIO_FORMAT_S24_LE,
	SPA_AUDIO_FORMAT_S24_BE,
	SPA_AUDIO_FORMAT_U24_LE,
	SPA_AUDIO_FORMAT_U24_BE,
	SPA_AUDIO_FORMAT_S20_LE,
	SPA_AUDIO_FORMAT_S20_BE,
	SPA_AUDIO_FORMAT_U20_LE,
	SPA_AUDIO_FORMAT_U20_BE,
	SPA_AUDIO_FORMAT_S18_LE,
	SPA_AUDIO_FORMAT_S18_BE,
	SPA_AUDIO_FORMAT_U18_LE,
	SPA_AUDIO_FORMAT_U18_BE,
	SPA_AUDIO_FORMAT_F32_LE,
	SPA_AUDIO_FORMAT_F32_BE,
	SPA_AUDIO_FORMAT_F64_LE,
	SPA_AUDIO_FORMAT_F64_BE,

	SPA_AUDIO_FORMAT_ULAW,
	SPA_AUDIO_FORMAT_ALAW,

	/* planar formats */
	SPA_AUDIO_FORMAT_START_Planar		= 0x200,
	SPA_AUDIO_FORMAT_U8P,
	SPA_AUDIO_FORMAT_S16P,
	SPA_AUDIO_FORMAT_S24_32P,
	SPA_AUDIO_FORMAT_S32P,
	SPA_AUDIO_FORMAT_S24P,
	SPA_AUDIO_FORMAT_F32P,
	SPA_AUDIO_FORMAT_F64P,
	SPA_AUDIO_FORMAT_S8P,

	/* other formats start here */
	SPA_AUDIO_FORMAT_START_Other		= 0x400,

	/* Aliases */

	/* DSP formats */
	SPA_AUDIO_FORMAT_DSP_S32 = SPA_AUDIO_FORMAT_S24_32P,
	SPA_AUDIO_FORMAT_DSP_F32 = SPA_AUDIO_FORMAT_F32P,
	SPA_AUDIO_FORMAT_DSP_F64 = SPA_AUDIO_FORMAT_F64P,

	/* native endian */
#if __BYTE_ORDER == __BIG_ENDIAN
	SPA_AUDIO_FORMAT_S16 = SPA_AUDIO_FORMAT_S16_BE,
	SPA_AUDIO_FORMAT_U16 = SPA_AUDIO_FORMAT_U16_BE,
	SPA_AUDIO_FORMAT_S24_32 = SPA_AUDIO_FORMAT_S24_32_BE,
	SPA_AUDIO_FORMAT_U24_32 = SPA_AUDIO_FORMAT_U24_32_BE,
	SPA_AUDIO_FORMAT_S32 = SPA_AUDIO_FORMAT_S32_BE,
	SPA_AUDIO_FORMAT_U32 = SPA_AUDIO_FORMAT_U32_BE,
	SPA_AUDIO_FORMAT_S24 = SPA_AUDIO_FORMAT_S24_BE,
	SPA_AUDIO_FORMAT_U24 = SPA_AUDIO_FORMAT_U24_BE,
	SPA_AUDIO_FORMAT_S20 = SPA_AUDIO_FORMAT_S20_BE,
	SPA_AUDIO_FORMAT_U20 = SPA_AUDIO_FORMAT_U20_BE,
	SPA_AUDIO_FORMAT_S18 = SPA_AUDIO_FORMAT_S18_BE,
	SPA_AUDIO_FORMAT_U18 = SPA_AUDIO_FORMAT_U18_BE,
	SPA_AUDIO_FORMAT_F32 = SPA_AUDIO_FORMAT_F32_BE,
	SPA_AUDIO_FORMAT_F64 = SPA_AUDIO_FORMAT_F64_BE,
	SPA_AUDIO_FORMAT_S16_OE = SPA_AUDIO_FORMAT_S16_LE,
	SPA_AUDIO_FORMAT_U16_OE = SPA_AUDIO_FORMAT_U16_LE,
	SPA_AUDIO_FORMAT_S24_32_OE = SPA_AUDIO_FORMAT_S24_32_LE,
	SPA_AUDIO_FORMAT_U24_32_OE = SPA_AUDIO_FORMAT_U24_32_LE,
	SPA_AUDIO_FORMAT_S32_OE = SPA_AUDIO_FORMAT_S32_LE,
	SPA_AUDIO_FORMAT_U32_OE = SPA_AUDIO_FORMAT_U32_LE,
	SPA_AUDIO_FORMAT_S24_OE = SPA_AUDIO_FORMAT_S24_LE,
	SPA_AUDIO_FORMAT_U24_OE = SPA_AUDIO_FORMAT_U24_LE,
	SPA_AUDIO_FORMAT_S20_OE = SPA_AUDIO_FORMAT_S20_LE,
	SPA_AUDIO_FORMAT_U20_OE = SPA_AUDIO_FORMAT_U20_LE,
	SPA_AUDIO_FORMAT_S18_OE = SPA_AUDIO_FORMAT_S18_LE,
	SPA_AUDIO_FORMAT_U18_OE = SPA_AUDIO_FORMAT_U18_LE,
	SPA_AUDIO_FORMAT_F32_OE = SPA_AUDIO_FORMAT_F32_LE,
	SPA_AUDIO_FORMAT_F64_OE = SPA_AUDIO_FORMAT_F64_LE,
#elif __BYTE_ORDER == __LITTLE_ENDIAN
	SPA_AUDIO_FORMAT_S16 = SPA_AUDIO_FORMAT_S16_LE,
	SPA_AUDIO_FORMAT_U16 = SPA_AUDIO_FORMAT_U16_LE,
	SPA_AUDIO_FORMAT_S24_32 = SPA_AUDIO_FORMAT_S24_32_LE,
	SPA_AUDIO_FORMAT_U24_32 = SPA_AUDIO_FORMAT_U24_32_LE,
	SPA_AUDIO_FORMAT_S32 = SPA_AUDIO_FORMAT_S32_LE,
	SPA_AUDIO_FORMAT_U32 = SPA_AUDIO_FORMAT_U32_LE,
	SPA_AUDIO_FORMAT_S24 = SPA_AUDIO_FORMAT_S24_LE,
	SPA_AUDIO_FORMAT_U24 = SPA_AUDIO_FORMAT_U24_LE,
	SPA_AUDIO_FORMAT_S20 = SPA_AUDIO_FORMAT_S20_LE,
	SPA_AUDIO_FORMAT_U20 = SPA_AUDIO_FORMAT_U20_LE,
	SPA_AUDIO_FORMAT_S18 = SPA_AUDIO_FORMAT_S18_LE,
	SPA_AUDIO_FORMAT_U18 = SPA_AUDIO_FORMAT_U18_LE,
	SPA_AUDIO_FORMAT_F32 = SPA_AUDIO_FORMAT_F32_LE,
	SPA_AUDIO_FORMAT_F64 = SPA_AUDIO_FORMAT_F64_LE,
	SPA_AUDIO_FORMAT_S16_OE = SPA_AUDIO_FORMAT_S16_BE,
	SPA_AUDIO_FORMAT_U16_OE = SPA_AUDIO_FORMAT_U16_BE,
	SPA_AUDIO_FORMAT_S24_32_OE = SPA_AUDIO_FORMAT_S24_32_BE,
	SPA_AUDIO_FORMAT_U24_32_OE = SPA_AUDIO_FORMAT_U24_32_BE,
	SPA_AUDIO_FORMAT_S32_OE = SPA_AUDIO_FORMAT_S32_BE,
	SPA_AUDIO_FORMAT_U32_OE = SPA_AUDIO_FORMAT_U32_BE,
	SPA_AUDIO_FORMAT_S24_OE = SPA_AUDIO_FORMAT_S24_BE,
	SPA_AUDIO_FORMAT_U24_OE = SPA_AUDIO_FORMAT_U24_BE,
	SPA_AUDIO_FORMAT_S20_OE = SPA_AUDIO_FORMAT_S20_BE,
	SPA_AUDIO_FORMAT_U20_OE = SPA_AUDIO_FORMAT_U20_BE,
	SPA_AUDIO_FORMAT_S18_OE = SPA_AUDIO_FORMAT_S18_BE,
	SPA_AUDIO_FORMAT_U18_OE = SPA_AUDIO_FORMAT_U18_BE,
	SPA_AUDIO_FORMAT_F32_OE = SPA_AUDIO_FORMAT_F32_BE,
	SPA_AUDIO_FORMAT_F64_OE = SPA_AUDIO_FORMAT_F64_BE,
#endif
};

#define SPA_AUDIO_FORMAT_IS_INTERLEAVED(fmt)	((fmt) > SPA_AUDIO_FORMAT_START_Interleaved && (fmt) < SPA_AUDIO_FORMAT_START_Planar)
#define SPA_AUDIO_FORMAT_IS_PLANAR(fmt)		((fmt) > SPA_AUDIO_FORMAT_START_Planar && (fmt) < SPA_AUDIO_FORMAT_START_Other)

enum spa_audio_channel {
	SPA_AUDIO_CHANNEL_UNKNOWN,	/**< unspecified */
	SPA_AUDIO_CHANNEL_NA,		/**< N/A, silent */

	SPA_AUDIO_CHANNEL_MONO,         /**< mono stream */

	/**                                                        Azimuth Elevation */
	SPA_AUDIO_CHANNEL_FL,           /**< front left               30       0     */
	SPA_AUDIO_CHANNEL_FR,           /**< front right             -30       0     */
	SPA_AUDIO_CHANNEL_FC,           /**< front center              0       0     */
	SPA_AUDIO_CHANNEL_LFE,          /**< LFE                       0     -30     */
	SPA_AUDIO_CHANNEL_SL,           /**< side left                90       0     */
	SPA_AUDIO_CHANNEL_SR,           /**< side right              -90       0     */
	SPA_AUDIO_CHANNEL_FLC,          /**< front left center        22.5     0     */
	SPA_AUDIO_CHANNEL_FRC,          /**< front right center      -22.5     0     */
	SPA_AUDIO_CHANNEL_RC,           /**< rear center             180       0     */
	SPA_AUDIO_CHANNEL_RL,           /**< rear left               110       0     */
	SPA_AUDIO_CHANNEL_RR,           /**< rear right             -110       0     */
	SPA_AUDIO_CHANNEL_TC,           /**< top center                0      90     */
	SPA_AUDIO_CHANNEL_TFL,          /**< top front left           30      30     */
	SPA_AUDIO_CHANNEL_TFC,          /**< top front center          0      30     */
	SPA_AUDIO_CHANNEL_TFR,          /**< top front right         -30      30     */
	SPA_AUDIO_CHANNEL_TRL,          /**< top rear left           110      30     */
	SPA_AUDIO_CHANNEL_TRC,          /**< top rear center         180      30     */
	SPA_AUDIO_CHANNEL_TRR,          /**< top rear right         -110      30     */
	SPA_AUDIO_CHANNEL_RLC,          /**< rear left center        135       0     */
	SPA_AUDIO_CHANNEL_RRC,          /**< rear right center      -135       0     */
	SPA_AUDIO_CHANNEL_FLW,          /**< front left wide          60       0     */
	SPA_AUDIO_CHANNEL_FRW,          /**< front right wide        -60       0     */
	SPA_AUDIO_CHANNEL_LFE2,		/**< LFE 2                     0     -30     */
	SPA_AUDIO_CHANNEL_FLH,          /**< front left high         22.5     30     */
	SPA_AUDIO_CHANNEL_FCH,          /**< front center high        0       30     */
	SPA_AUDIO_CHANNEL_FRH,          /**< front right high       -22.5     30     */
	SPA_AUDIO_CHANNEL_TFLC,         /**< top front left center    45      30     */
	SPA_AUDIO_CHANNEL_TFRC,         /**< top front right center  -45      30     */
	SPA_AUDIO_CHANNEL_TSL,          /**< top side left            90       0     */
	SPA_AUDIO_CHANNEL_TSR,          /**< top side right          -90       0     */
	SPA_AUDIO_CHANNEL_LLFE,         /**< left LFE                 45     -30     */
	SPA_AUDIO_CHANNEL_RLFE,         /**< right LFE               -45     -30     */
	SPA_AUDIO_CHANNEL_BC,           /**< bottom center             0     -30     */
	SPA_AUDIO_CHANNEL_BLC,          /**< bottom left center       45     -30     */
	SPA_AUDIO_CHANNEL_BRC,          /**< bottom right center     -45     -30     */

	SPA_AUDIO_CHANNEL_START_Aux	= 0x1000,	/**< aux channels */
	SPA_AUDIO_CHANNEL_AUX0 = SPA_AUDIO_CHANNEL_START_Aux,
	SPA_AUDIO_CHANNEL_AUX1,
	SPA_AUDIO_CHANNEL_AUX2,
	SPA_AUDIO_CHANNEL_AUX3,
	SPA_AUDIO_CHANNEL_AUX4,
	SPA_AUDIO_CHANNEL_AUX5,
	SPA_AUDIO_CHANNEL_AUX6,
	SPA_AUDIO_CHANNEL_AUX7,
	SPA_AUDIO_CHANNEL_AUX8,
	SPA_AUDIO_CHANNEL_AUX9,
	SPA_AUDIO_CHANNEL_AUX10,
	SPA_AUDIO_CHANNEL_AUX11,
	SPA_AUDIO_CHANNEL_AUX12,
	SPA_AUDIO_CHANNEL_AUX13,
	SPA_AUDIO_CHANNEL_AUX14,
	SPA_AUDIO_CHANNEL_AUX15,
	SPA_AUDIO_CHANNEL_AUX16,
	SPA_AUDIO_CHANNEL_AUX17,
	SPA_AUDIO_CHANNEL_AUX18,
	SPA_AUDIO_CHANNEL_AUX19,
	SPA_AUDIO_CHANNEL_AUX20,
	SPA_AUDIO_CHANNEL_AUX21,
	SPA_AUDIO_CHANNEL_AUX22,
	SPA_AUDIO_CHANNEL_AUX23,
	SPA_AUDIO_CHANNEL_AUX24,
	SPA_AUDIO_CHANNEL_AUX25,
	SPA_AUDIO_CHANNEL_AUX26,
	SPA_AUDIO_CHANNEL_AUX27,
	SPA_AUDIO_CHANNEL_AUX28,
	SPA_AUDIO_CHANNEL_AUX29,
	SPA_AUDIO_CHANNEL_AUX30,
	SPA_AUDIO_CHANNEL_AUX31,
	SPA_AUDIO_CHANNEL_AUX32,
	SPA_AUDIO_CHANNEL_AUX33,
	SPA_AUDIO_CHANNEL_AUX34,
	SPA_AUDIO_CHANNEL_AUX35,
	SPA_AUDIO_CHANNEL_AUX36,
	SPA_AUDIO_CHANNEL_AUX37,
	SPA_AUDIO_CHANNEL_AUX38,
	SPA_AUDIO_CHANNEL_AUX39,
	SPA_AUDIO_CHANNEL_AUX40,
	SPA_AUDIO_CHANNEL_AUX41,
	SPA_AUDIO_CHANNEL_AUX42,
	SPA_AUDIO_CHANNEL_AUX43,
	SPA_AUDIO_CHANNEL_AUX44,
	SPA_AUDIO_CHANNEL_AUX45,
	SPA_AUDIO_CHANNEL_AUX46,
	SPA_AUDIO_CHANNEL_AUX47,
	SPA_AUDIO_CHANNEL_AUX48,
	SPA_AUDIO_CHANNEL_AUX49,
	SPA_AUDIO_CHANNEL_AUX50,
	SPA_AUDIO_CHANNEL_AUX51,
	SPA_AUDIO_CHANNEL_AUX52,
	SPA_AUDIO_CHANNEL_AUX53,
	SPA_AUDIO_CHANNEL_AUX54,
	SPA_AUDIO_CHANNEL_AUX55,
	SPA_AUDIO_CHANNEL_AUX56,
	SPA_AUDIO_CHANNEL_AUX57,
	SPA_AUDIO_CHANNEL_AUX58,
	SPA_AUDIO_CHANNEL_AUX59,
	SPA_AUDIO_CHANNEL_AUX60,
	SPA_AUDIO_CHANNEL_AUX61,
	SPA_AUDIO_CHANNEL_AUX62,
	SPA_AUDIO_CHANNEL_AUX63,

	SPA_AUDIO_CHANNEL_LAST_Aux	= 0x1fff,	/**< aux channels */

	SPA_AUDIO_CHANNEL_START_Custom	= 0x10000,
};

#define SPA_AUDIO_CHANNEL_IS_AUX(ch)	((ch)>=SPA_AUDIO_CHANNEL_START_Aux && (ch)<=SPA_AUDIO_CHANNEL_LAST_Aux)

enum spa_audio_volume_ramp_scale {
	SPA_AUDIO_VOLUME_RAMP_INVALID,
	SPA_AUDIO_VOLUME_RAMP_LINEAR,
	SPA_AUDIO_VOLUME_RAMP_CUBIC,
};

/** Extra audio flags */
#define SPA_AUDIO_FLAG_NONE		(0)		/*< no valid flag */
#define SPA_AUDIO_FLAG_UNPOSITIONED	(1 << 0)	/*< the position array explicitly
							 *  contains unpositioned channels. */
/** Audio information description. You can assume when you receive this structure
 * that there is enought padding to accomodate all channel positions in case the
 * channel count is more than SPA_AUDIO_MAX_CHANNELS. */
struct spa_audio_info_raw {
	enum spa_audio_format format;		/*< format, one of enum spa_audio_format */
	uint32_t flags;				/*< extra flags */
	uint32_t rate;				/*< sample rate */
	uint32_t channels;			/*< number of channels. This can be more than SPA_AUDIO_MAX_CHANNELS
						 *  and you may assume there is enough padding for the extra
						 *  channel positions. */
	uint32_t position[SPA_AUDIO_MAX_CHANNELS];	/*< channel position from enum spa_audio_channel */
	/* padding follows here when channels > SPA_AUDIO_MAX_CHANNELS */
};

#define SPA_AUDIO_INFO_RAW_INIT(...)		((struct spa_audio_info_raw) { __VA_ARGS__ })

#define SPA_AUDIO_INFO_RAW_MAX_POSITION(size)	(((size)-offsetof(struct spa_audio_info_raw,position))/sizeof(uint32_t))

#define SPA_AUDIO_INFO_RAW_VALID_SIZE(size)	((size) >= offsetof(struct spa_audio_info_raw, position))


#define SPA_KEY_AUDIO_FORMAT		"audio.format"		/**< an audio format as string,
								  *  Ex. "S16LE" */
#define SPA_KEY_AUDIO_CHANNEL		"audio.channel"		/**< an audio channel as string,
								  *  Ex. "FL" */
#define SPA_KEY_AUDIO_CHANNELS		"audio.channels"	/**< an audio channel count as int */
#define SPA_KEY_AUDIO_RATE		"audio.rate"		/**< an audio sample rate as int */
#define SPA_KEY_AUDIO_LAYOUT		"audio.layout"		/**< channel positions as predefined layout */
#define SPA_KEY_AUDIO_POSITION		"audio.position"	/**< channel positions as comma separated list
								  *  of channels ex. "FL,FR" */
#define SPA_KEY_AUDIO_ALLOWED_RATES	"audio.allowed-rates"	/**< a list of allowed samplerates
								  *  ex. "[ 44100 48000 ]" */
/**
 * \}
 */

#ifdef __cplusplus
}  /* extern "C" */
#endif

#endif /* SPA_AUDIO_RAW_H */
