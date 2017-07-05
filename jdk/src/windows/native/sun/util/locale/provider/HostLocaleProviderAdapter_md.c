/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include "sun_util_locale_provider_HostLocaleProviderAdapterImpl.h"
#include <windows.h>
#include <gdefs.h>
#include <stdlib.h>

#define BUFLEN 256

// global variables
typedef int (WINAPI *PGLIE)(const jchar *, LCTYPE, LPWSTR, int);
typedef int (WINAPI *PGCIE)(const jchar *, CALID, LPCWSTR, CALTYPE, LPWSTR, int, LPDWORD);
PGLIE pGetLocaleInfoEx;
PGCIE pGetCalendarInfoEx;
BOOL initialized = FALSE;

// prototypes
int getLocaleInfoWrapper(const jchar *langtag, LCTYPE type, LPWSTR data, int buflen);
int getCalendarInfoWrapper(const jchar *langtag, CALID id, LPCWSTR reserved, CALTYPE type, LPWSTR data, int buflen, LPDWORD val);
jint getCalendarID(const jchar *langtag);
void replaceCalendarArrayElems(JNIEnv *env, jstring jlangtag, jobjectArray jarray,
                       CALTYPE* pCalTypes, int offset, int length);
WCHAR * getNumberPattern(const jchar * langtag, const jint numberStyle);
void getNumberPart(const jchar * langtag, const jint numberStyle, WCHAR * number);
void getFixPart(const jchar * langtag, const jint numberStyle, BOOL positive, BOOL prefix, WCHAR * ret);

// from java_props_md.c
extern __declspec(dllexport) const char * getJavaIDFromLangID(LANGID langID);

CALTYPE monthsType[] = {
    CAL_SMONTHNAME1,
    CAL_SMONTHNAME2,
    CAL_SMONTHNAME3,
    CAL_SMONTHNAME4,
    CAL_SMONTHNAME5,
    CAL_SMONTHNAME6,
    CAL_SMONTHNAME7,
    CAL_SMONTHNAME8,
    CAL_SMONTHNAME9,
    CAL_SMONTHNAME10,
    CAL_SMONTHNAME11,
    CAL_SMONTHNAME12,
    CAL_SMONTHNAME13,
};

CALTYPE sMonthsType[] = {
    CAL_SABBREVMONTHNAME1,
    CAL_SABBREVMONTHNAME2,
    CAL_SABBREVMONTHNAME3,
    CAL_SABBREVMONTHNAME4,
    CAL_SABBREVMONTHNAME5,
    CAL_SABBREVMONTHNAME6,
    CAL_SABBREVMONTHNAME7,
    CAL_SABBREVMONTHNAME8,
    CAL_SABBREVMONTHNAME9,
    CAL_SABBREVMONTHNAME10,
    CAL_SABBREVMONTHNAME11,
    CAL_SABBREVMONTHNAME12,
    CAL_SABBREVMONTHNAME13,
};

CALTYPE wDaysType[] = {
    CAL_SDAYNAME7,
    CAL_SDAYNAME1,
    CAL_SDAYNAME2,
    CAL_SDAYNAME3,
    CAL_SDAYNAME4,
    CAL_SDAYNAME5,
    CAL_SDAYNAME6,
};

CALTYPE sWDaysType[] = {
    CAL_SABBREVDAYNAME7,
    CAL_SABBREVDAYNAME1,
    CAL_SABBREVDAYNAME2,
    CAL_SABBREVDAYNAME3,
    CAL_SABBREVDAYNAME4,
    CAL_SABBREVDAYNAME5,
    CAL_SABBREVDAYNAME6,
};

WCHAR * fixes[2][2][3][16] =
{
    { //prefix
        { //positive
            { // number
                L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"",
            },
            { // currency
                L"\xA4", L"", L"\xA4 ", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"",
            },
            { // percent
                L"", L"", L"%", L"% ", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"",
            }
        },
        { // negative
            { // number
                L"(", L"-", L"- ", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"",
            },
            { //currency
                L"(\xA4", L"-\xA4", L"\xA4-", L"\xA4", L"(", L"-", L"", L"", L"-", L"-\xA4 ", L"", L"\xA4 ", L"\xA4 -", L"", L"(\xA4 ", L"("
            },
            { // percent
                L"-", L"-", L"-%", L"%-", L"%", L"", L"", L"-% ", L"", L"% ", L"% -", L"", L"", L"", L"", L"",
            }
        }
    },
    { // suffix
        { //positive
            { // number
                L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L""
            },
            { // currency
                L"", L"\xA4 ", L"", L" \xA4", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"",
            },
            { // percent
                L" %", L"%", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"",
            }
        },
        { // negative
            { // number
                L")", L"", L" ", L"-", L" -", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"", L"",
            },
            { //currency
                L")", L"", L"", L"-", L"\xA4)", L"\xA4", L"-\xA4", L"\xA4-", L" \xA4", L"", L" \xA4-", L"-", L"", L"- \xA4", L")", L" \xA4)"
            },
            { // percent
                L" %", L"%", L"", L"", L"-", L"-%", L"%-", L"", L" %-", L"-", L"", L"- %", L"", L"", L"", L"",
            }
        }
    }
};

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    initialize
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_initialize
  (JNIEnv *env, jclass cls) {
    if (!initialized) {
        pGetLocaleInfoEx = (PGLIE)GetProcAddress(
            GetModuleHandle("kernel32.dll"),
            "GetLocaleInfoEx");
        pGetCalendarInfoEx = (PGCIE)GetProcAddress(
            GetModuleHandle("kernel32.dll"),
            "GetCalendarInfoEx");
        initialized =TRUE;
    }

    return pGetLocaleInfoEx != NULL &&
           pGetCalendarInfoEx != NULL;
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getDefaultLocale
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getDefaultLocale
  (JNIEnv *env, jclass cls, jint cat) {
    char * localeString = NULL;
    LANGID langid;
    jstring ret;

    switch (cat) {
        case sun_util_locale_provider_HostLocaleProviderAdapterImpl_CAT_DISPLAY:
            langid = LANGIDFROMLCID(GetUserDefaultUILanguage());
            break;
        case sun_util_locale_provider_HostLocaleProviderAdapterImpl_CAT_FORMAT:
        default:
            langid = LANGIDFROMLCID(GetUserDefaultLCID());
            break;
    }

    localeString = (char *)getJavaIDFromLangID(langid);
    ret = (*env)->NewStringUTF(env, localeString);
    free(localeString);
    return ret;
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getDateTimePattern
 * Signature: (IILjava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getDateTimePattern
  (JNIEnv *env, jclass cls, jint dateStyle, jint timeStyle, jstring jlangtag) {
    WCHAR pattern[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);

    pattern[0] = L'\0';

    if (dateStyle == 0 || dateStyle == 1) {
        getLocaleInfoWrapper(langtag, LOCALE_SLONGDATE, pattern, BUFLEN);
    } else if (dateStyle == 2 || dateStyle == 3) {
        getLocaleInfoWrapper(langtag, LOCALE_SSHORTDATE, pattern, BUFLEN);
    }

    if (timeStyle == 0 || timeStyle == 1) {
        getLocaleInfoWrapper(langtag, LOCALE_STIMEFORMAT, pattern, BUFLEN);
    } else if (timeStyle == 2 || timeStyle == 3) {
        getLocaleInfoWrapper(langtag, LOCALE_SSHORTTIME, pattern, BUFLEN);
    }

    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    return (*env)->NewString(env, pattern, wcslen(pattern));
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getCalendarID
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getCalendarID
  (JNIEnv *env, jclass cls, jstring jlangtag) {
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    jint ret = getCalendarID(langtag);
    (*env)->ReleaseStringChars(env, jlangtag, langtag);
    return ret;
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getAmPmStrings
 * Signature: (Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getAmPmStrings
  (JNIEnv *env, jclass cls, jstring jlangtag, jobjectArray ampms) {
    WCHAR buf[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);

    // AM
    int got = getLocaleInfoWrapper(langtag, LOCALE_S1159, buf, BUFLEN);
    if (got) {
        (*env)->SetObjectArrayElement(env, ampms, 0, (*env)->NewString(env, buf, wcslen(buf)));
    }

    // PM
    got = getLocaleInfoWrapper(langtag, LOCALE_S2359, buf, BUFLEN);
    if (got) {
        (*env)->SetObjectArrayElement(env, ampms, 1, (*env)->NewString(env, buf, wcslen(buf)));
    }

    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    return ampms;
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getEras
 * Signature: (Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getEras
  (JNIEnv *env, jclass cls, jstring jlangtag, jobjectArray eras) {
    WCHAR ad[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);

    getCalendarInfoWrapper(langtag, getCalendarID(langtag), NULL,
                      CAL_SERASTRING, ad, BUFLEN, NULL);

    // Windows does not provide B.C. era.
    (*env)->SetObjectArrayElement(env, eras, 1, (*env)->NewString(env, ad, wcslen(ad)));

    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    return eras;
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getMonths
 * Signature: (Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getMonths
  (JNIEnv *env, jclass cls, jstring jlangtag, jobjectArray months) {
    replaceCalendarArrayElems(env, jlangtag, months, monthsType,
                      0, sizeof(monthsType)/sizeof(CALTYPE));
    return months;
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getShortMonths
 * Signature: (Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getShortMonths
  (JNIEnv *env, jclass cls, jstring jlangtag, jobjectArray smonths) {
    replaceCalendarArrayElems(env, jlangtag, smonths, sMonthsType,
                      0, sizeof(sMonthsType)/sizeof(CALTYPE));
    return smonths;
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getWeekdays
 * Signature: (Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getWeekdays
  (JNIEnv *env, jclass cls, jstring jlangtag, jobjectArray wdays) {
    replaceCalendarArrayElems(env, jlangtag, wdays, wDaysType,
                      1, sizeof(wDaysType)/sizeof(CALTYPE));
    return wdays;
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getShortWeekdays
 * Signature: (Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getShortWeekdays
  (JNIEnv *env, jclass cls, jstring jlangtag, jobjectArray swdays) {
    replaceCalendarArrayElems(env, jlangtag, swdays, sWDaysType,
                      1, sizeof(sWDaysType)/sizeof(CALTYPE));
    return swdays;
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getNumberPattern
 * Signature: (ILjava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getNumberPattern
  (JNIEnv *env, jclass cls, jint numberStyle, jstring jlangtag) {
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    jstring ret;

    WCHAR * pattern = getNumberPattern(langtag, numberStyle);

    (*env)->ReleaseStringChars(env, jlangtag, langtag);
    ret = (*env)->NewString(env, pattern, wcslen(pattern));
    free(pattern);

    return ret;
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    isNativeDigit
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_isNativeDigit
  (JNIEnv *env, jclass cls, jstring jlangtag) {
    DWORD num;
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int got = getLocaleInfoWrapper(langtag,
        LOCALE_IDIGITSUBSTITUTION | LOCALE_RETURN_NUMBER,
        (LPWSTR)&num, sizeof(num));
    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    return got && num == 2; // 2: native digit substitution
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getCurrencySymbol
 * Signature: (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getCurrencySymbol
  (JNIEnv *env, jclass cls, jstring jlangtag, jstring currencySymbol) {
    WCHAR buf[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int got = getLocaleInfoWrapper(langtag, LOCALE_SCURRENCY, buf, BUFLEN);
    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    if (got) {
        return (*env)->NewString(env, buf, wcslen(buf));
    } else {
        return currencySymbol;
    }
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getDecimalSeparator
 * Signature: (Ljava/lang/String;C)C
 */
JNIEXPORT jchar JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getDecimalSeparator
  (JNIEnv *env, jclass cls, jstring jlangtag, jchar decimalSeparator) {
    WCHAR buf[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int got = getLocaleInfoWrapper(langtag, LOCALE_SDECIMAL, buf, BUFLEN);
    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    if (got) {
        return buf[0];
    } else {
        return decimalSeparator;
    }
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getGroupingSeparator
 * Signature: (Ljava/lang/String;C)C
 */
JNIEXPORT jchar JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getGroupingSeparator
  (JNIEnv *env, jclass cls, jstring jlangtag, jchar groupingSeparator) {
    WCHAR buf[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int got = getLocaleInfoWrapper(langtag, LOCALE_STHOUSAND, buf, BUFLEN);
    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    if (got) {
        return buf[0];
    } else {
        return groupingSeparator;
    }
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getInfinity
 * Signature: (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getInfinity
  (JNIEnv *env, jclass cls, jstring jlangtag, jstring infinity) {
    WCHAR buf[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int got = getLocaleInfoWrapper(langtag, LOCALE_SPOSINFINITY, buf, BUFLEN);
    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    if (got) {
        return (*env)->NewString(env, buf, wcslen(buf));
    } else {
        return infinity;
    }
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getInternationalCurrencySymbol
 * Signature: (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getInternationalCurrencySymbol
  (JNIEnv *env, jclass cls, jstring jlangtag, jstring internationalCurrencySymbol) {
    WCHAR buf[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int got = getLocaleInfoWrapper(langtag, LOCALE_SINTLSYMBOL, buf, BUFLEN);
    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    if (got) {
        return (*env)->NewString(env, buf, wcslen(buf));
    } else {
        return internationalCurrencySymbol;
    }
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getMinusSign
 * Signature: (Ljava/lang/String;C)C
 */
JNIEXPORT jchar JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getMinusSign
  (JNIEnv *env, jclass cls, jstring jlangtag, jchar minusSign) {
    WCHAR buf[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int got = getLocaleInfoWrapper(langtag, LOCALE_SNEGATIVESIGN, buf, BUFLEN);
    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    if (got) {
        return buf[0];
    } else {
        return minusSign;
    }
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getMonetaryDecimalSeparator
 * Signature: (Ljava/lang/String;C)C
 */
JNIEXPORT jchar JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getMonetaryDecimalSeparator
  (JNIEnv *env, jclass cls, jstring jlangtag, jchar monetaryDecimalSeparator) {
    WCHAR buf[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int got = getLocaleInfoWrapper(langtag, LOCALE_SMONDECIMALSEP, buf, BUFLEN);
    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    if (got) {
        return buf[0];
    } else {
        return monetaryDecimalSeparator;
    }
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getNaN
 * Signature: (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getNaN
  (JNIEnv *env, jclass cls, jstring jlangtag, jstring nan) {
    WCHAR buf[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int got = getLocaleInfoWrapper(langtag, LOCALE_SNAN, buf, BUFLEN);
    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    if (got) {
        return (*env)->NewString(env, buf, wcslen(buf));
    } else {
        return nan;
    }
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getPercent
 * Signature: (Ljava/lang/String;C)C
 */
JNIEXPORT jchar JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getPercent
  (JNIEnv *env, jclass cls, jstring jlangtag, jchar percent) {
    WCHAR buf[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int got = getLocaleInfoWrapper(langtag, LOCALE_SPERCENT, buf, BUFLEN);
    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    if (got) {
        return buf[0];
    } else {
        return percent;
    }
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getPerMill
 * Signature: (Ljava/lang/String;C)C
 */
JNIEXPORT jchar JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getPerMill
  (JNIEnv *env, jclass cls, jstring jlangtag, jchar perMill) {
    WCHAR buf[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int got = getLocaleInfoWrapper(langtag, LOCALE_SPERMILLE, buf, BUFLEN);
    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    if (got) {
        return buf[0];
    } else {
        return perMill;
    }
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getZeroDigit
 * Signature: (Ljava/lang/String;C)C
 */
JNIEXPORT jchar JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getZeroDigit
  (JNIEnv *env, jclass cls, jstring jlangtag, jchar zeroDigit) {
    WCHAR buf[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int got = getLocaleInfoWrapper(langtag, LOCALE_SNATIVEDIGITS, buf, BUFLEN);
    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    if (got) {
        return buf[0];
    } else {
        return zeroDigit;
    }
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getCalendarDataValue
 * Signature: (Ljava/lang/String;I)I
 */
JNIEXPORT jint JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getCalendarDataValue
  (JNIEnv *env, jclass cls, jstring jlangtag, jint type) {
    DWORD num;
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int got = 0;

    switch (type) {
    case sun_util_locale_provider_HostLocaleProviderAdapterImpl_CD_FIRSTDAYOFWEEK:
        got = getLocaleInfoWrapper(langtag,
            LOCALE_IFIRSTDAYOFWEEK | LOCALE_RETURN_NUMBER,
            (LPWSTR)&num, sizeof(num));
        break;
    }

    (*env)->ReleaseStringChars(env, jlangtag, langtag);

    if (got) {
        return num;
    } else {
        return -1;
    }
}

/*
 * Class:     sun_util_locale_provider_HostLocaleProviderAdapterImpl
 * Method:    getDisplayString
 * Signature: (Ljava/lang/String;ILjava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_sun_util_locale_provider_HostLocaleProviderAdapterImpl_getDisplayString
  (JNIEnv *env, jclass cls, jstring jlangtag, jint type, jstring jvalue) {
    LCTYPE lcType;
    jstring jStr;
    const jchar * pjChar;
    WCHAR buf[BUFLEN];
    int got = 0;

    switch (type) {
        case sun_util_locale_provider_HostLocaleProviderAdapterImpl_DN_CURRENCY_NAME:
            lcType = LOCALE_SNATIVECURRNAME;
            jStr = jlangtag;
            break;
        case sun_util_locale_provider_HostLocaleProviderAdapterImpl_DN_CURRENCY_SYMBOL:
            lcType = LOCALE_SCURRENCY;
            jStr = jlangtag;
            break;
        case sun_util_locale_provider_HostLocaleProviderAdapterImpl_DN_LOCALE_LANGUAGE:
            lcType = LOCALE_SLOCALIZEDLANGUAGENAME;
            jStr = jvalue;
            break;
        case sun_util_locale_provider_HostLocaleProviderAdapterImpl_DN_LOCALE_REGION:
            lcType = LOCALE_SLOCALIZEDCOUNTRYNAME;
            jStr = jvalue;
            break;
        default:
            return NULL;
    }

    pjChar = (*env)->GetStringChars(env, jStr, JNI_FALSE);
    got = getLocaleInfoWrapper(pjChar, lcType, buf, BUFLEN);
    (*env)->ReleaseStringChars(env, jStr, pjChar);

    if (got) {
        return (*env)->NewString(env, buf, wcslen(buf));
    } else {
        return NULL;
    }
}

int getLocaleInfoWrapper(const jchar *langtag, LCTYPE type, LPWSTR data, int buflen) {
    if (pGetLocaleInfoEx) {
        if (wcscmp(L"und", (LPWSTR)langtag) == 0) {
            // defaults to "en"
            return pGetLocaleInfoEx(L"en", type, data, buflen);
        } else {
            return pGetLocaleInfoEx((LPWSTR)langtag, type, data, buflen);
        }
    } else {
        // If we ever wanted to support WinXP, we will need extra module from
        // MS...
        // return GetLocaleInfo(DownlevelLocaleNameToLCID(langtag, 0), type, data, buflen);
        return 0;
    }
}

int getCalendarInfoWrapper(const jchar *langtag, CALID id, LPCWSTR reserved, CALTYPE type, LPWSTR data, int buflen, LPDWORD val) {
    if (pGetCalendarInfoEx) {
        if (wcscmp(L"und", (LPWSTR)langtag) == 0) {
            // defaults to "en"
            return pGetCalendarInfoEx(L"en", id, reserved, type, data, buflen, val);
        } else {
            return pGetCalendarInfoEx((LPWSTR)langtag, id, reserved, type, data, buflen, val);
        }
    } else {
        // If we ever wanted to support WinXP, we will need extra module from
        // MS...
        // return GetCalendarInfo(DownlevelLocaleNameToLCID(langtag, 0), ...);
        return 0;
    }
}

jint getCalendarID(const jchar *langtag) {
    DWORD type;
    int got = getLocaleInfoWrapper(langtag,
        LOCALE_ICALENDARTYPE | LOCALE_RETURN_NUMBER,
        (LPWSTR)&type, sizeof(type));

    if (got) {
        return type;
    } else {
        return 0;
    }
}

void replaceCalendarArrayElems(JNIEnv *env, jstring jlangtag, jobjectArray jarray, CALTYPE* pCalTypes, int offset, int length) {
    WCHAR name[BUFLEN];
    const jchar *langtag = (*env)->GetStringChars(env, jlangtag, JNI_FALSE);
    int calid = getCalendarID(langtag);

    if (calid != -1) {
        int i;
        for (i = 0; i < length; i++) {
            getCalendarInfoWrapper(langtag, calid, NULL,
                              pCalTypes[i], name, BUFLEN, NULL);
            (*env)->SetObjectArrayElement(env, jarray, i + offset,
                          (*env)->NewString(env, name, wcslen(name)));
        }
    }

    (*env)->ReleaseStringChars(env, jlangtag, langtag);
}

WCHAR * getNumberPattern(const jchar * langtag, const jint numberStyle) {
    WCHAR ret[BUFLEN];
    WCHAR number[BUFLEN];
    WCHAR fix[BUFLEN];

    getFixPart(langtag, numberStyle, TRUE, TRUE, ret); // "+"
    getNumberPart(langtag, numberStyle, number);
    wcscat_s(ret, BUFLEN-wcslen(ret), number);      // "+12.34"
    getFixPart(langtag, numberStyle, TRUE, FALSE, fix);
    wcscat_s(ret, BUFLEN-wcslen(ret), fix);         // "+12.34$"
    wcscat_s(ret, BUFLEN-wcslen(ret), L";");        // "+12.34$;"
    getFixPart(langtag, numberStyle, FALSE, TRUE, fix);
    wcscat_s(ret, BUFLEN-wcslen(ret), fix);         // "+12.34$;("
    wcscat_s(ret, BUFLEN-wcslen(ret), number);      // "+12.34$;(12.34"
    getFixPart(langtag, numberStyle, FALSE, FALSE, fix);
    wcscat_s(ret, BUFLEN-wcslen(ret), fix);         // "+12.34$;(12.34$)"

    return _wcsdup(ret);
}

void getNumberPart(const jchar * langtag, const jint numberStyle, WCHAR * number) {
    DWORD digits = 0;
    DWORD leadingZero = 0;
    WCHAR grouping[BUFLEN];
    int groupingLen;
    WCHAR fractionPattern[BUFLEN];
    WCHAR * integerPattern = number;
    WCHAR * pDest;

    // Get info from Windows
    switch (numberStyle) {
        case sun_util_locale_provider_HostLocaleProviderAdapterImpl_NF_CURRENCY:
            getLocaleInfoWrapper(langtag,
                LOCALE_ICURRDIGITS | LOCALE_RETURN_NUMBER,
                (LPWSTR)&digits, sizeof(digits));
            break;

        case sun_util_locale_provider_HostLocaleProviderAdapterImpl_NF_INTEGER:
            break;

        case sun_util_locale_provider_HostLocaleProviderAdapterImpl_NF_NUMBER:
        case sun_util_locale_provider_HostLocaleProviderAdapterImpl_NF_PERCENT:
        default:
            getLocaleInfoWrapper(langtag,
                LOCALE_IDIGITS | LOCALE_RETURN_NUMBER,
                (LPWSTR)&digits, sizeof(digits));
            break;
    }

    getLocaleInfoWrapper(langtag,
        LOCALE_ILZERO | LOCALE_RETURN_NUMBER,
        (LPWSTR)&leadingZero, sizeof(leadingZero));
    groupingLen = getLocaleInfoWrapper(langtag, LOCALE_SGROUPING, grouping, BUFLEN);

    // fraction pattern
    if (digits > 0) {
        int i;
        for(i = digits;  i > 0; i--) {
            fractionPattern[i] = L'0';
        }
        fractionPattern[0] = L'.';
        fractionPattern[digits+1] = L'\0';
    } else {
        fractionPattern[0] = L'\0';
    }

    // integer pattern
    pDest = integerPattern;
    if (groupingLen > 0) {
        int cur = groupingLen - 1;// subtracting null terminator
        while (--cur >= 0) {
            int repnum;

            if (grouping[cur] == L';') {
                continue;
            }

            repnum = grouping[cur] - 0x30;
            if (repnum > 0) {
                *pDest++ = L'#';
                *pDest++ = L',';
                while(--repnum > 0) {
                    *pDest++ = L'#';
                }
            }
        }
    }

    if (leadingZero != 0) {
        *pDest++ = L'0';
    } else {
        *pDest++ = L'#';
    }
    *pDest = L'\0';

    wcscat_s(integerPattern, BUFLEN, fractionPattern);
}

void getFixPart(const jchar * langtag, const jint numberStyle, BOOL positive, BOOL prefix, WCHAR * ret) {
    DWORD pattern = 0;
    int style = numberStyle;
    int got = 0;

    if (positive) {
        if (style == sun_util_locale_provider_HostLocaleProviderAdapterImpl_NF_CURRENCY) {
            got = getLocaleInfoWrapper(langtag,
                LOCALE_ICURRENCY | LOCALE_RETURN_NUMBER,
                (LPWSTR)&pattern, sizeof(pattern));
        } else if (style == sun_util_locale_provider_HostLocaleProviderAdapterImpl_NF_PERCENT) {
            got = getLocaleInfoWrapper(langtag,
                LOCALE_IPOSITIVEPERCENT | LOCALE_RETURN_NUMBER,
                (LPWSTR)&pattern, sizeof(pattern));
        }
    } else {
        if (style == sun_util_locale_provider_HostLocaleProviderAdapterImpl_NF_CURRENCY) {
            got = getLocaleInfoWrapper(langtag,
                LOCALE_INEGCURR | LOCALE_RETURN_NUMBER,
                (LPWSTR)&pattern, sizeof(pattern));
        } else if (style == sun_util_locale_provider_HostLocaleProviderAdapterImpl_NF_PERCENT) {
            got = getLocaleInfoWrapper(langtag,
                LOCALE_INEGATIVEPERCENT | LOCALE_RETURN_NUMBER,
                (LPWSTR)&pattern, sizeof(pattern));
        } else {
            got = getLocaleInfoWrapper(langtag,
                LOCALE_INEGNUMBER | LOCALE_RETURN_NUMBER,
                (LPWSTR)&pattern, sizeof(pattern));
        }
    }

    if (numberStyle == sun_util_locale_provider_HostLocaleProviderAdapterImpl_NF_INTEGER) {
        style = sun_util_locale_provider_HostLocaleProviderAdapterImpl_NF_NUMBER;
    }

    wcscpy(ret, fixes[!prefix][!positive][style][pattern]);
}
