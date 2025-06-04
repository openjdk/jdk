//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <string.h>
#include <stdbool.h>

#define N 1000

FILE *cygopen(const char *path, const char *mode) {
#if defined(__MINGW64__) || defined(__MINGW32__)
  FILE *fp = fopen(path, mode);
  if (fp != NULL) return fp;

  char *buf = malloc(strlen(path) + N + 1);
  snprintf(buf, strlen(path) + N, "cygpath -m '%s'", path);

  FILE *pfp = popen(buf, "r");

  if (pfp == NULL || fgets(buf, N, pfp) == NULL) {
    if (pfp != NULL) pclose(pfp);
    free(buf);
    return NULL;
  }

  pclose(pfp);

  int len = strlen(buf);
  if (0 < len && len < N && buf[len-1] == '\n') buf[len-1] = '\0';

  fp = fopen(buf, mode);

  free(buf);

  return fp;
#else
  return fopen(path, mode);
#endif
}

int nkeywords = 0, nalloc = 0;
char **keywords = NULL, *suffix = NULL;

int nIgnore = 0;
char **ignore = NULL;

void insert(char *buf) {
  for(int i=0;i<nIgnore;i++) if (strcmp(ignore[i], buf) == 0) return;

  for(int i=0;i<nkeywords;i++) {
    if (strcmp(keywords[i], buf) == 0) printf("%s", suffix);
  }
}

void doit(FILE *fp) {
  int state = 0;
  bool nl = true;
  char buf[N+10], *p = buf;

  for(;;) {
    int c = getc(fp);
    if (c == EOF) break;
    switch(state) {
    case 0:
      if (isalnum(c) || c == '_') {
        ungetc(c, fp);
        p = buf;
        state = 1;
        break;
      }
      if (c == '/') {
        int c2 = getc(fp);
        if (c2 == '*') {
          putc(c, stdout);
          putc(c2, stdout);
          state = 4;
          break;
        } else if (c2 == '/') {
          putc(c, stdout);
          putc(c2, stdout);
          do {
            c = getc(fp);
            putc(c, stdout);
          } while(c != '\n');
          break;
        }
        ungetc(c2, fp);
      }
      if (nl && c == '#') {
        putc(c, stdout);
        do {
          c = getc(fp);
          putc(c, stdout);
        } while(c != '\n');
        break;
      }
      putc(c, stdout);
      if (!isspace(c)) nl = false;
      if (c == '\n') nl = true;
      if (c == '\"') state = 2;
      if (c == '\'') state = 3;
      break;

    case 1: // Identifier
      if (isalnum(c) || c == '_') {
        if (p - buf < N) { *p++ = c; *p = '\0'; }
        putc(c, stdout);
      } else if (c == '\"') {
        insert(buf);
        putc(c, stdout);
        state = 2;
      } else if (c == '\'') {
        insert(buf);
        putc(c, stdout);
        state = 3;
      } else {
        insert(buf);
        putc(c, stdout);
        state = 0;
      }
      break;

    case 2: // String
      if (c == '\\') {
        putc(c, stdout);
        putc(getc(fp), stdout);
      } else if (c == '\"') {
        putc(c, stdout);
        state = 0;
      } else {
        putc(c, stdout);
      }
      break;

    case 3: // Character
      if (c == '\\') {
        putc(c, stdout);
        putc(getc(fp), stdout);
      } else if (c == '\'') {
        putc(c, stdout);
        state = 0;
      } else {
        putc(c, stdout);
      }
      break;

    case 4: // Comment
      if (c == '*') {
        int c2 = getc(fp);
        if (c2 == '/') {
          putc(c, stdout);
          putc(c2, stdout);
          state = 0;
          break;
        }
        ungetc(c2, fp);
      }
      putc(c, stdout);
      break;
    }
  }
}

int main(int argc, char **argv) {
  nalloc = 1;
  keywords = malloc(sizeof(char *) * nalloc);

  if (argc < 2) {
    fprintf(stderr, "%s <input file>\n", argv[0]);
    fprintf(stderr, "Print the file on the standard output\n");
    fprintf(stderr, "\n");
    fprintf(stderr, "%s <input file> <keywords file> <suffix> [<keywords to ignore> ... ]\n", argv[0]);
    fprintf(stderr, "Add the suffix to keywords\n");
    exit(-1);
  }

  char buf[N];

  if (argc == 2) {
    FILE *fp = cygopen(argv[1], "r");
    if (fp == NULL) {
      fprintf(stderr, "Cannot open %s\n", argv[1]);
      exit(-1);
    }

    while(fgets(buf, N, fp) != NULL) {
      fputs(buf, stdout);
    }
    fclose(fp);
    exit(0);
  }

  FILE *fp = cygopen(argv[2], "r");
  if (fp == NULL) {
    fprintf(stderr, "Cannot open %s\n", argv[2]);
    exit(-1);
  }

  while(fgets(buf, N, fp) != NULL) {
    if (strlen(buf) >= 1) buf[strlen(buf)-1] = '\0';
    keywords[nkeywords] = malloc(sizeof(char) * (strlen(buf) + 1));
    strcpy(keywords[nkeywords], buf);
    nkeywords++;
    if (nkeywords >= nalloc) {
      nalloc *= 2;
      keywords = realloc(keywords, sizeof(char *) * nalloc);
    }
  }

  fclose(fp);

  nIgnore = argc - 4;
  ignore = argv + 4;

  suffix = argv[3];

  fp = cygopen(argv[1], "r");
  if (fp == NULL) {
    fprintf(stderr, "Cannot open %s\n", argv[1]);
    exit(-1);
  }

  doit(fp);

  fclose(fp);

  exit(0);
}

// cat sleef*inline*.h | egrep -o '[a-zA-Z_][0-9a-zA-Z_]*' | sort | uniq > cand.txt
