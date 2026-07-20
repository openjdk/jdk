/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include <shlwapi.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <windows.h>

#define JAVA_EXECUTABLE_NAME "java.exe"

#ifndef LAUNCHER_ARGS
#error LAUNCHER_ARGS must be defined
#endif

static char* launcher_args[] = LAUNCHER_ARGS;

char* quote_argument(char* arg) {
    // See https://learn.microsoft.com/en-us/archive/blogs/twistylittlepassagesallalike/everyone-quotes-command-line-arguments-the-wrong-way
    // for an explanation of how to properly quote command lines for CreateProcess
    size_t arg_length = strlen(arg);

    if (strcspn(arg, " \t\n\v\"") == arg_length) {
        // No quoting is needed
        return arg;
    }

    // Worst-case buffer size: all characters need a backslash, and starting + end quotes
    size_t buffer_size = arg_length * 2 + 3;
    char* buffer = malloc(buffer_size);
    if (buffer == NULL) {
        return NULL;
    }

    int backslashes = 0;
    char* write_pos = buffer;
    char* read_pos = arg;

    // Start with a quote character
    *write_pos++ = '"';

    while (*read_pos) {
        while (*read_pos == '\\') {
            read_pos++;
            backslashes++;
        }

        if (*read_pos == '"') {
            // Any potential backslashes before a quote needs to be doubled,
            // and the quote needs to be escaped with an additional backslash
            for (int i = 0; i < backslashes * 2 + 1; i++) {
                *write_pos++ = '\\';
            }
            *write_pos++ = *read_pos++;
            backslashes = 0;
        } else {
            // Backslashes not preceeding a quote are copied without escaping
            for (int i = 0; i < backslashes; i++) {
                *write_pos++ = '\\';
            }
            if (*read_pos) {
                *write_pos++ = *read_pos++;
                backslashes = 0;
            }
        }
    }

    // If the string ended with backslashes, they need to be doubled before
    // the final quote character
    for (int i = 0; i < backslashes; i++) {
        *write_pos++ = '\\';
    }
    *write_pos++ = '"';
    *write_pos = '\0';

    return buffer;
}

int main(int argc, char* argv[]) {
    ////////////////////////////////////////////////////////////////////////////
    // Create a fully qualified path to the java executable in the same
    // directory as this file resides in.

    // Calculate path length first
    DWORD our_full_path_len = GetFullPathName(argv[0], 0, NULL, NULL);
    if (our_full_path_len == 0) {
        fprintf(stderr, "failed to get the full path of the executable: %lu\n", GetLastError());
        return 1;
    }

    char* our_full_path = malloc(our_full_path_len + 1);
    if (our_full_path == NULL) {
        perror("malloc failed");
        return 1;
    }

    if (GetFullPathName(argv[0], our_full_path_len + 1, our_full_path, NULL) == 0) {
        fprintf(stderr, "failed to get the full path of the executable: %lu\n", GetLastError());
        return 1;
    }

    char *last_slash_pos = strrchr(our_full_path, '\\');
    if (last_slash_pos == NULL) {
        fprintf(stderr, "no '\\' found in the full path of the executable\n");
        return 1;
    }

    size_t base_length = last_slash_pos - our_full_path + 1;
    size_t java_path_length = base_length + strlen(JAVA_EXECUTABLE_NAME) + 1;

    char *java_path = malloc(java_path_length);
    if (java_path == NULL) {
        perror("malloc failed");
        return 1;
    }

    memcpy(java_path, our_full_path, base_length);
    strcpy(java_path + base_length, JAVA_EXECUTABLE_NAME);

    ////////////////////////////////////////////////////////////////////////////
    // Build the argument list: our executable name + launcher args + users args

    int launcher_argsc = sizeof(launcher_args) / sizeof(char *);

    char **java_args = malloc((launcher_argsc + argc + 1) * sizeof(char *));
    if (java_args == NULL) {
        perror("malloc failed");
        return 1;
    }

    // Our executable name
    java_args[0] = quote_argument(argv[0]);
    if (java_args[0] == NULL) {
        perror("malloc failed");
        return 1;
    }

    // Launcher arguments
    for (int i = 0; i < launcher_argsc; i++) {
        char* quoted = quote_argument(launcher_args[i]);
        if (quoted == NULL) {
            perror("malloc failed");
            return 1;
        }
        java_args[i + 1] = quoted;
    }

    // User arguments
    for (int i = 1; i < argc; i++) {
        char* quoted = quote_argument(argv[i]);
        if (quoted == NULL) {
            perror("malloc failed");
            return 1;
        }
        java_args[launcher_argsc + i] = quoted;
    }

    java_args[launcher_argsc + argc] = NULL;

    // Windows needs the command line as a single string, not as an array of char*
    size_t total_length = 0;
    for (int i = 0; java_args[i] != NULL; i++) {
        char* arg = java_args[i];
        total_length += strlen(java_args[i]) + 1;
    }

    char* command_line = malloc(total_length);
    if (command_line == NULL) {
        perror("malloc failed");
        return 1;
    }

    // Concatenate the quoted arguments with a space between them
    char* write_pos = command_line;
    for (int i = 0; java_args[i] != NULL; i++) {
        size_t arg_len = strlen(java_args[i]);
        memcpy(write_pos, java_args[i], arg_len);
        write_pos += arg_len;

        // Append a space
        *write_pos++ = ' ';
    }

    // Replace the last space with a null terminator
    write_pos--;
    *write_pos = '\0';

    ////////////////////////////////////////////////////////////////////////////
    // Finally execute the real java process with the constructed arguments

    if (GetEnvironmentVariable("_JAVA_LAUNCHER_DEBUG", NULL, 0)) {
        char *program_name = PathFindFileName(argv[0]);

        fprintf(stderr, "%s: executing: '%s' '%s'\n", program_name, java_path, command_line);
    }

    STARTUPINFO si;
    PROCESS_INFORMATION pi;

    memset(&si, 0, sizeof(si));
    memset(&pi, 0, sizeof(pi));

    // Windows has no equivalent of exec, so start the process and wait for it
    // to finish, to be able to return the same exit code
    if (!CreateProcess(java_path, command_line, NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi)) {
        fprintf(stderr, "CreateProcess failed: %lu\n", GetLastError());
        return 1;
    }

    if (WaitForSingleObject(pi.hProcess, INFINITE) == WAIT_FAILED) {
        fprintf(stderr, "WaitForSingleObject failed: %lu\n", GetLastError());
        return 1;
    }

    DWORD exit_code;
    if (!GetExitCodeProcess(pi.hProcess, &exit_code)) {
        fprintf(stderr, "GetExitCodeProcess failed: %lu\n", GetLastError());
        return 1;
    }
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);

    return exit_code;
}
