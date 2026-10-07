#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

  static const size_t SHA256_DIGEST_LENGTH = 32;

  typedef int EVP_MD;
  typedef void ENGINE;

  typedef struct {
    int type;
    union {
      struct PSHA2_256_Internal *psha_256;
    };
  } EVP_MD_CTX;

  const EVP_MD *EVP_sha256(void);
  int EVP_MD_get_size(const EVP_MD *);
  size_t EVP_MD_size(const EVP_MD *);
  EVP_MD_CTX *EVP_MD_CTX_new(void);
  int EVP_DigestInit_ex(EVP_MD_CTX *ctx, const EVP_MD *type, ENGINE *impl);
  int EVP_DigestUpdate(EVP_MD_CTX *ctx, const void *d, size_t cnt);
  int EVP_DigestFinal_ex(EVP_MD_CTX *ctx, unsigned char *md, unsigned int *s);
  void EVP_MD_CTX_free(EVP_MD_CTX *ctx);

#ifdef __cplusplus
}
#endif
