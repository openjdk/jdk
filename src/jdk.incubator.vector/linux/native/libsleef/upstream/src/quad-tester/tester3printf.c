//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <assert.h>

#include <openssl/md5.h>

#include "sleefquad.h"

static void convertEndianness(void *ptr, int len) {
#if defined(__BYTE_ORDER__) && (__BYTE_ORDER__ == __ORDER_BIG_ENDIAN__)
  for(int k=0;k<len/2;k++) {
    unsigned char t = ((unsigned char *)ptr)[k];
    ((unsigned char *)ptr)[k] = ((unsigned char *)ptr)[len-1-k];
    ((unsigned char *)ptr)[len-1-k] = t;
  }
#else
#endif
}

static void testem(MD5_CTX *ctx, Sleef_quad val, char *types) {
  for(int alt=0;alt<2;alt++) {
    for(int zero=0;zero<2;zero++) {
      for(int left=0;left<2;left++) {
        for(int blank=0;blank<2;blank++) {
          for(int sign=0;sign<2;sign++) {
            static char fmt[100], buf[100];
            Sleef_quad q;
            int r;
            snprintf(fmt, 99, "%%%s%s%s%s%s%s",
                     alt ? "#" : "",
                     zero ? "0" : "",
                     left ? "-" : "",
                     blank ? " " : "",
                     sign ? "+" : "",
                     types);

            r = Sleef_snprintf(buf, 99, fmt, &val);
            assert(r < 100);
            MD5_Update(ctx, buf, r < 0 ? 0 : r);
            q = Sleef_strtoq(buf, NULL);
            convertEndianness(&q, sizeof(q));
            MD5_Update(ctx, &q, sizeof(Sleef_quad));

            for(int width=0;width<=40;width += 2) {
              snprintf(fmt, 99, "%%%s%s%s%s%s%d.%s",
                       alt ? "#" : "",
                       zero ? "0" : "",
                       left ? "-" : "",
                       blank ? " " : "",
                       sign ? "+" : "",
                       width, types);

              r = Sleef_snprintf(buf, 99, fmt, &val);
              assert(r < 100);
              MD5_Update(ctx, buf, r < 0 ? 0 : r);
              q = Sleef_strtoq(buf, NULL);
              convertEndianness(&q, sizeof(q));
              MD5_Update(ctx, &q, sizeof(Sleef_quad));
            }

            for(int prec=0;prec<=40;prec += 3) {
              for(int width=0;width<=40;width += 3) {
                snprintf(fmt, 99, "%%%s%s%s%s%s%d.%d%s",
                         alt ? "#" : "",
                         zero ? "0" : "",
                         left ? "-" : "",
                         blank ? " " : "",
                         sign ? "+" : "",
                         width, prec, types);

                r = Sleef_snprintf(buf, 99, fmt, &val);
                assert(r < 100);
                MD5_Update(ctx, buf, r < 0 ? 0 : r);
                q = Sleef_strtoq(buf, NULL);
                convertEndianness(&q, sizeof(q));
                MD5_Update(ctx, &q, sizeof(Sleef_quad));
              }

              snprintf(fmt, 99, "%%%s%s%s%s%s.%d%s",
                       alt ? "#" : "",
                       zero ? "0" : "",
                       left ? "-" : "",
                       blank ? " " : "",
                       sign ? "+" : "",
                       prec, types);

              r = Sleef_snprintf(buf, 99, fmt, &val);
              assert(r < 100);
              MD5_Update(ctx, buf, r < 0 ? 0 : r);
              q = Sleef_strtoq(buf, NULL);
              convertEndianness(&q, sizeof(q));
              MD5_Update(ctx, &q, sizeof(Sleef_quad));
            }
          }
        }
      }
    }
  }
}

static int test2(const char *fmt, ...) {
  static char tbuf[256], cbuf[256];

  va_list ap;
  va_start(ap, fmt);
  int tret = Sleef_vsnprintf(tbuf, 255, fmt, ap);
  va_end(ap);

  va_start(ap, fmt);
  int cret = vsnprintf(cbuf, 255, fmt, ap);
  va_end(ap);

  int success = tret == cret && strcmp(tbuf, cbuf) == 0;

  if (!success) fprintf(stderr, "fmt = %s\ntret = [%s]\ncret = [%s]\n", fmt, tbuf, cbuf);

  return success;
}

int main(int argc, char **argv) {
#if __GLIBC__ > 2 || (__GLIBC__ == 2 && __GLIBC_MINOR__ > 13)
#pragma GCC diagnostic ignored "-Wformat"
#pragma GCC diagnostic ignored "-Wformat-extra-args"
  Sleef_registerPrintfHook();
  static char buf[110];
  Sleef_quad q = Sleef_strtoq("3.1415926535897932384626433832795028842", NULL);

  snprintf(buf, 100, "%50.40Pe", &q);
  if (strcmp(buf, "    3.1415926535897932384626433832795027974791e+00") != 0) {
    fprintf(stderr, "%%50.40Pe %s\n", buf);
    exit(-1);
  }
  snprintf(buf, 100, "%50.40Pf", &q);
  if (strcmp(buf, "        3.1415926535897932384626433832795027974791") != 0) {
    fprintf(stderr, "%%50.40Pf %s\n", buf);
    exit(-1);
  }
  snprintf(buf, 100, "%50.40Pg", &q);
  if (strcmp(buf, "         3.141592653589793238462643383279502797479") != 0) {
    fprintf(stderr, "%%50.40Pg %s\n", buf);
    exit(-1);
  }
  snprintf(buf, 100, "%Pa", &q);
  if (strcmp(buf, "0x1.921fb54442d18469898cc51701b8p+1") != 0) {
    fprintf(stderr, "%%Pa %s\n", buf);
    exit(-1);
  }
#endif

  //

  FILE *fp = NULL;

  if (argc != 1) {
    fp = fopen(argv[1], "r");
    if (fp == NULL) {
      fprintf(stderr, "Could not open %s\n", argv[1]);
      exit(-1);
    }
  }

  //

  static char *types[] = { "Pe", "Pf", "Pg", "Pa" };

  static const char *strvals[] = {
    "1.2345678912345678912345e+0Q",
    "1.2345678912345678912345e+1Q",
    "1.2345678912345678912345e-1Q",
    "1.2345678912345678912345e+2Q",
    "1.2345678912345678912345e-2Q",
    "1.2345678912345678912345e+3Q",
    "1.2345678912345678912345e-3Q",
    "1.2345678912345678912345e+4Q",
    "1.2345678912345678912345e-4Q",
    "1.2345678912345678912345e+5Q",
    "1.2345678912345678912345e-5Q",
    "1.2345678912345678912345e+10Q",
    "1.2345678912345678912345e-10Q",
    "1.2345678912345678912345e+15Q",
    "1.2345678912345678912345e-15Q",
    "1.2345678912345678912345e+30Q",
    "1.2345678912345678912345e-30Q",
    "1.2345678912345678912345e+1000Q",
    "1.2345678912345678912345e-1000Q",
    "1.2345678912345678912345e-4950Q",
    "1.2345678912345678912345e+4920Q",
    "3.36210314311209350626267781732175260e-4932",
    "1.18973149535723176508575932662800702e+4932",
    "6.475175119438025110924438958227646552e-4966",
    "0.0Q", "1.0Q",
    "1e+1Q", "1e+2Q", "1e+3Q", "1e+4Q", "1e+5Q", "1e+6Q",
    "1e-1Q", "1e-2Q", "1e-3Q", "1e-4Q", "1e-5Q", "1e-6Q",
    "inf", "nan",
  };
  Sleef_quad vals[sizeof(strvals) / sizeof(char *)];
  for(int i=0;i<sizeof(strvals) / sizeof(char *);i++) {
    vals[i] = Sleef_strtoq(strvals[i], NULL);
  }

  int success = 1;

  //

  success = success && test2("head %d tail", 123);
  success = success && test2("head %.8d %hhd %hd %d %ld %lld %jd %zd %td %.4d tail",
                             123, (signed char)1, (short int)2, (int)3, (long int)4, (long long int)5, (intmax_t)6, (size_t)7, (ptrdiff_t) 8, 321);
  success = success && test2("head %10.8d %hhi %hi %i %li %lli %ji %zi %ti %8.5d tail",
                             123, (signed char)1, (short int)2, (int)3, (long int)4, (long long int)5, (intmax_t)6, (size_t)7, (ptrdiff_t) 8, 321);
  success = success && test2("head %-10d %hhx %hx %x %lx %llx %jx %zx %tx %-10.9d tail",
                             123, (unsigned char)1, (short unsigned)2, (unsigned)3, (long unsigned)4, (long long unsigned)5, (uintmax_t)6, (size_t)7, (ptrdiff_t) 8, 321);
  success = success && test2("head %+10d %hhX %hX %X %lX %llX %jX %zX %tX %+10.9d tail",
                             123, (unsigned char)1, (short unsigned)2, (unsigned)3, (long unsigned)4, (long long unsigned)5, (uintmax_t)6, (size_t)7, (ptrdiff_t) 8, 321);
  success = success && test2("head %d %hhu %hu %u %lu %llu %ju %zu %tu %d tail",
                             123, (unsigned char)1, (short unsigned)2, (unsigned)3, (long unsigned)4, (long long unsigned)5, (uintmax_t)6, (size_t)7, (ptrdiff_t) 8, 321);
  success = success && test2("head %d %hho %ho %o %lo %llo %jo %zo %to %d tail",
                             123, (unsigned char)1, (short unsigned)2, (unsigned)3, (long unsigned)4, (long long unsigned)5, (uintmax_t)6, (size_t)7, (ptrdiff_t) 8, 321);
  success = success && test2("head %d %f %F %e %E %g %G %a %A %d tail",
                             123, 0.11, 0.21, 0.31, 0.41, 0.51, 0.61, 0.71, 0.81, 321);
  success = success && test2("head %d %Lf %LF %Le %LE %Lg %LG %La %LA %d tail",
                             123, 0.11L, 0.21L, 0.31L, 0.41L, 0.51L, 0.61L, 0.71L, 0.81L, 321);
  success = success && test2("head %d %c %s %p %p %d tail", 123, 111, "string", NULL, &success, 321);

  if (!success) exit(-1);

  //

  for(int j=0;j<4;j++) {
    MD5_CTX ctx;
    memset(&ctx, 0, sizeof(MD5_CTX));
    MD5_Init(&ctx);

    for(int i=0;i<sizeof(vals)/sizeof(Sleef_quad);i++) {
      testem(&ctx, vals[i], types[j]);
      testem(&ctx, Sleef_negq1_purec(vals[i]), types[j]);
    }

    unsigned char d[16], mes[64], buf[64];
    MD5_Final(d, &ctx);

    snprintf((char *)mes, 60, "%s %02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x",
             types[j], d[0],d[1],d[2],d[3],d[4],d[5],d[6],d[7],
             d[8],d[9],d[10],d[11],d[12],d[13],d[14],d[15]);

    if (fp != NULL) {
      fgets((char *)buf, 60, fp);
      if (strncmp((char *)mes, (char *)buf, strlen((char *)mes)) != 0) {
        puts((char *)mes);
        puts((char *)buf);
        success = 0;
      }
    } else puts((char *)mes);
  }

  if (fp != NULL) fclose(fp);

  exit(success ? 0 : -1);
}
