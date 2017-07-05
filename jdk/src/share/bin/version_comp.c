/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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

#include <ctype.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include "jni.h"
#include "jli_util.h"
#include "version_comp.h"

/*
 *      A collection of useful strings. One should think of these as #define
 *      entries, but actual strings can be more efficient (with many compilers).
 */
static const char *separators   = ".-_";
static const char *zero_string  = "0";

/*
 *      Validate a string as parsable as a "Java int". If so parsable,
 *      return true (non-zero) and store the numeric value at the address
 *      passed in as "value"; otherwise return false (zero).
 *
 *      Note that the maximum allowable value is 2147483647 as defined by
 *      the "Java Language Specification" which precludes the use of native
 *      conversion routines which may have other limits.
 *
 *      Also note that we don't have to worry about the alternate maximum
 *      allowable value of 2147483648 because it is only allowed after
 *      the unary negation operator and this grammar doesn't have one
 *      of those.
 *
 *      Finally, note that a value which exceeds the maximum jint value will
 *      return false (zero). This results in the otherwise purely numeric
 *      string being compared as a string of characters (as per the spec.)
 */
static int
isjavaint(const char *s, jint *value)
{
    jlong sum = 0;
    jint digit;
    while (*s != '\0')
        if (isdigit(*s)) {
            digit = (jint)((int)(*s++) - (int)('0'));
            sum = (sum * 10) + digit;
            if (sum > 2147483647)
                return (0);     /* Overflows jint (but not jlong) */
        } else
            return (0);
    *value = (jint)sum;
    return (1);
}

/*
 *      Modeled after strcmp(), compare two strings (as in the grammar defined
 *      in Appendix A of JSR 56).  If both strings can be interpreted as
 *      Java ints, do a numeric comparison, else it is strcmp().
 */
static int
comp_string(const char *s1, const char *s2)
{
    jint v1, v2;
    if (isjavaint(s1, &v1) && isjavaint(s2, &v2))
        return ((int)(v1 - v2));
    else
        return (JLI_StrCmp(s1, s2));
}

/*
 *      Modeled after strcmp(), compare two version-ids for a Prefix
 *      Match as defined in JSR 56.
 */
int
JLI_PrefixVersionId(const char *id1, char *id2)
{
    char        *s1 = JLI_StringDup(id1);
    char        *s2 = JLI_StringDup(id2);
    char        *m1 = s1;
    char        *m2 = s2;
    char        *end1 = NULL;
    char        *end2 = NULL;
    int res = 0;

    do {

        if ((s1 != NULL) && ((end1 = JLI_StrPBrk(s1, ".-_")) != NULL))
            *end1 = '\0';
        if ((s2 != NULL) && ((end2 = JLI_StrPBrk(s2, ".-_")) != NULL))
            *end2 = '\0';

        res = comp_string(s1, s2);

        if (end1 != NULL)
            s1 = end1 + 1;
        else
            s1 = NULL;
        if (end2 != NULL)
            s2 = end2 + 1;
        else
            s2 = NULL;

    } while (res == 0 && ((s1 != NULL) && (s2 != NULL)));

    JLI_MemFree(m1);
    JLI_MemFree(m2);
    return (res);
}

/*
 *      Modeled after strcmp(), compare two version-ids for an Exact
 *      Match as defined in JSR 56.
 */
int
JLI_ExactVersionId(const char *id1, char *id2)
{
    char        *s1 = JLI_StringDup(id1);
    char        *s2 = JLI_StringDup(id2);
    char        *m1 = s1;
    char        *m2 = s2;
    char        *end1 = NULL;
    char        *end2 = NULL;
    int res = 0;

    do {

        if ((s1 != NULL) && ((end1 = JLI_StrPBrk(s1, separators)) != NULL))
            *end1 = '\0';
        if ((s2 != NULL) && ((end2 = JLI_StrPBrk(s2, separators)) != NULL))
            *end2 = '\0';

        if ((s1 != NULL) && (s2 == NULL))
            res = comp_string(s1, zero_string);
        else if ((s1 == NULL) && (s2 != NULL))
            res = comp_string(zero_string, s2);
        else
            res = comp_string(s1, s2);

        if (end1 != NULL)
            s1 = end1 + 1;
        else
            s1 = NULL;
        if (end2 != NULL)
            s2 = end2 + 1;
        else
            s2 = NULL;

    } while (res == 0 && ((s1 != NULL) || (s2 != NULL)));

    JLI_MemFree(m1);
    JLI_MemFree(m2);
    return (res);
}

/*
 *      Return true if this simple-element (as defined in JSR 56) forms
 *      an acceptable match.
 *
 *      JSR 56 is modified by the Java Web Start <rel> Developer Guide
 *      where it is stated "... Java Web Start will not consider an installed
 *      non-FCS (i.e., milestone) JRE as a match. ... a JRE from Sun
 *      Microsystems, Inc., is by convention a non-FCS (milestone) JRE
 *      if there is a dash (-) in the version string."
 *
 *      An undocumented caveat to the above is that an exact match with a
 *      hyphen is accepted as a development extension.
 *
 *      These modifications are addressed by the specific comparisons
 *      for releases with hyphens.
 */
static int
acceptable_simple_element(const char *release, char *simple_element)
{
    char        *modifier;
    modifier = simple_element + JLI_StrLen(simple_element) - 1;
    if (*modifier == '*') {
        *modifier = '\0';
        if (JLI_StrChr(release, '-'))
            return ((JLI_StrCmp(release, simple_element) == 0)?1:0);
        return ((JLI_PrefixVersionId(release, simple_element) == 0)?1:0);
    } else if (*modifier == '+') {
        *modifier = '\0';
        if (JLI_StrChr(release, '-'))
            return ((JLI_StrCmp(release, simple_element) == 0)?1:0);
        return ((JLI_ExactVersionId(release, simple_element) >= 0)?1:0);
    } else {
        return ((JLI_ExactVersionId(release, simple_element) == 0)?1:0);
    }
}

/*
 *      Return true if this element (as defined in JSR 56) forms
 *      an acceptable match. An element is the intersection (and)
 *      of multiple simple-elements.
 */
static int
acceptable_element(const char *release, char *element)
{
    char        *end;
    do {
        if ((end = JLI_StrChr(element, '&')) != NULL)
            *end = '\0';
        if (!acceptable_simple_element(release, element))
            return (0);
        if (end != NULL)
            element = end + 1;
    } while (end != NULL);
    return (1);
}

/*
 *      Checks if release is acceptable by the specification version-string.
 *      Return true if this version-string (as defined in JSR 56) forms
 *      an acceptable match. A version-string is the union (or) of multiple
 *      elements.
 */
int
JLI_AcceptableRelease(const char *release, char *version_string)
{
    char        *vs;
    char        *m1;
    char        *end;
    m1 = vs = JLI_StringDup(version_string);
    do {
        if ((end = JLI_StrChr(vs, ' ')) != NULL)
            *end = '\0';
        if (acceptable_element(release, vs)) {
            JLI_MemFree(m1);
            return (1);
        }
        if (end != NULL)
            vs = end + 1;
    } while (end != NULL);
    JLI_MemFree(m1);
    return (0);
}

/*
 *      Return true if this is a valid simple-element (as defined in JSR 56).
 *
 *      The official grammar for a simple-element is:
 *
 *              simple-element  ::= version-id | version-id modifier
 *              modifier        ::= '+' | '*'
 *              version-id      ::= string ( separator  string )*
 *              string          ::= char ( char )*
 *              char            ::= Any ASCII character except a space, an
 *                                  ampersand, a separator or a modifier
 *              separator       ::= '.' | '-' | '_'
 *
 *      However, for efficiency, it is time to abandon the top down parser
 *      implementation.  After deleting the potential trailing modifier, we
 *      are left with a version-id.
 *
 *      Note that a valid version-id has three simple properties:
 *
 *      1) Doesn't contain a space, an ampersand or a modifier.
 *
 *      2) Doesn't begin or end with a separator.
 *
 *      3) Doesn't contain two adjacent separators.
 *
 *      Any other line noise constitutes a valid version-id.
 */
static int
valid_simple_element(char *simple_element)
{
    char        *last;
    size_t      len;

    if ((simple_element == NULL) || ((len = JLI_StrLen(simple_element)) == 0))
        return (0);
    last = simple_element + len - 1;
    if (*last == '*' || *last == '+') {
        if (--len == 0)
            return (0);
        *last-- = '\0';
    }
    if (JLI_StrPBrk(simple_element, " &+*") != NULL)    /* Property #1 */
        return (0);
    if ((JLI_StrChr(".-_", *simple_element) != NULL) || /* Property #2 */
      (JLI_StrChr(".-_", *last) != NULL))
        return (0);
    for (; simple_element != last; simple_element++)    /* Property #3 */
        if ((JLI_StrChr(".-_", *simple_element) != NULL) &&
          (JLI_StrChr(".-_", *(simple_element + 1)) != NULL))
            return (0);
    return (1);
}

/*
 *      Return true if this is a valid element (as defined in JSR 56).
 *      An element is the intersection (and) of multiple simple-elements.
 */
static int
valid_element(char *element)
{
    char        *end;
    if ((element == NULL) || (JLI_StrLen(element) == 0))
        return (0);
    do {
        if ((end = JLI_StrChr(element, '&')) != NULL)
            *end = '\0';
        if (!valid_simple_element(element))
            return (0);
        if (end != NULL)
            element = end + 1;
    } while (end != NULL);
    return (1);
}

/*
 *      Validates a version string by the extended JSR 56 grammar.
 */
int
JLI_ValidVersionString(char *version_string)
{
    char        *vs;
    char        *m1;
    char        *end;
    if ((version_string == NULL) || (JLI_StrLen(version_string) == 0))
        return (0);
    m1 = vs = JLI_StringDup(version_string);
    do {
        if ((end = JLI_StrChr(vs, ' ')) != NULL)
            *end = '\0';
        if (!valid_element(vs)) {
            JLI_MemFree(m1);
            return (0);
        }
        if (end != NULL)
            vs = end + 1;
    } while (end != NULL);
    JLI_MemFree(m1);
    return (1);
}
