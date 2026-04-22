#include <stdio.h>
#include <stdlib.h>
#include <sleefquad.h>

#if defined(_MSC_VER)
#pragma warning(disable:4116) // warning C4116: unnamed type definition in parentheses
#endif

int main(int argc, char **argv) {
  if (argc == 1) {
    printf("Usage : %s <FP number>\n", argv[0]);
    exit(-1);
  }

  if (argc == 4) {
    int64_t h = strtoll(argv[1], NULL, 16);
    uint64_t l = strtoull(argv[2], NULL, 16);
    int e = atoi(argv[3]);
    Sleef_quad q = sleef_q(h, l, e);
    Sleef_printf("%+Pa = %.30Pg\n", &q, &q);
    exit(0);
  }

  union {
    struct {
#if defined(__BYTE_ORDER__) && (__BYTE_ORDER__ == __ORDER_BIG_ENDIAN__)
      unsigned long long h, l;
#else
      unsigned long long l, h;
#endif
    };
    Sleef_quad q;
  } cnv = { .q = Sleef_strtoq(argv[1], NULL) };

  Sleef_printf("%+Pa\nsleef_q(%c0x%c%012llxLL, 0x%016llxULL, %d)\n",
               &cnv.q, (cnv.h >> 63) ? '-' : '+',
               (int)((cnv.h >> 48) & 0x7fff) == 0 ? '0' : '1',
               (unsigned long long)(0xffffffffffffULL & cnv.h),
               (unsigned long long)cnv.l,
               (int)((cnv.h >> 48) & 0x7fff) - 16383);
}
