/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#include <Windows.h>
#include <malloc.h>
#include <versionhelpers.h>
#include <stdio.h>

int main()
{
  DWORD active_processor_count = GetActiveProcessorCount(ALL_PROCESSOR_GROUPS);
  if (active_processor_count == 0) {
    printf("GetActiveProcessorCount failed with error: %x\n", GetLastError());
    return 1;
  }

  printf("IsWindowsServer: %d\n", IsWindowsServer() ? 1 : 0);
  printf("Active processor count across all processor groups: %d\n", active_processor_count);

  USHORT group_count = 0;

  if (GetProcessGroupAffinity(GetCurrentProcess(), &group_count, NULL) == 0) {
    DWORD last_error = GetLastError();
    if (last_error == ERROR_INSUFFICIENT_BUFFER) {
      if (group_count == 0) {
        printf("Unexpected group count of 0 from GetProcessGroupAffinity.\n");
        return 1;
      }
    } else {
      printf("GetActiveProcessorCount failed with error: %x\n", GetLastError());
      return 1;
    }
  } else {
    printf("Unexpected GetProcessGroupAffinity success result.\n");
    return 1;
  }

  PUSHORT group_array = (PUSHORT)malloc(group_count * sizeof(USHORT));
  if (group_array == NULL) {
    printf("malloc failed.\n");
    return 1;
  }

  printf("Active processors per group: ");
  for (USHORT i=0; i < group_count; i++) {
    DWORD active_processors_in_group = GetActiveProcessorCount(i);
    if (active_processors_in_group == 0) {
      printf("GetActiveProcessorCount(%d) failed with error: %x\n", i, GetLastError());
      return 1;
    }

    printf("%d,", active_processors_in_group);
  }

  free(group_array);
  return 0;
}
