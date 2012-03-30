/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

// Support for detecting Mac OS X Versions

#include <math.h>
#include <stdlib.h>
#include <stdio.h>
#import <JavaRuntimeSupport/JavaRuntimeSupport.h>


// returns 107 for Lion, 106 for SnowLeopard etc.
int getOSXMajorVersion() {
    char *ver = JRSCopyOSVersion();
    if (ver == NULL) { 
        return 0;
    }

    int len = strlen(ver);
    int v = 0;
    
    // Third char must be a '.'    
    if (len >= 3 && ver[2] == '.') {
        int i;
        
        v = (ver[0] - '0') * 10 + (ver[1] - '0');
        for (i = 3; i < len && isdigit(ver[i]); ++i) {
            v = v * 10 + (ver[i] - '0');
        }
    }

    free(ver);
    
    return v;
}

BOOL isSnowLeopardOrLower() {
    return (getOSXMajorVersion() < 107);
}
