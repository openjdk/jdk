//   Copyright Naoki Shibata and contributors 2010 - 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <ctype.h>
#include <inttypes.h>
#include <assert.h>

//

#if !(defined(__MINGW32__) || defined(__MINGW64__) || defined(_MSC_VER))
#include <unistd.h>
#include <sys/types.h>
#include <sys/file.h>

static void FLOCK(FILE *fp) { flock(fileno(fp), LOCK_EX); }
static void FUNLOCK(FILE *fp) { flock(fileno(fp), LOCK_UN); }
static void FTRUNCATE(FILE *fp, off_t z) {
  if (ftruncate(fileno(fp), z))
    ;
}
static FILE *OPENTMPFILE() { return tmpfile(); }
static void CLOSETMPFILE(FILE *fp) { fclose(fp); }
#else
#include <windows.h>
#include <io.h>

static void FLOCK(FILE *fp) { }
static void FUNLOCK(FILE *fp) { }
static void FTRUNCATE(FILE *fp, long z) {
  fseek(fp, 0, SEEK_SET);
  SetEndOfFile((HANDLE)_get_osfhandle(_fileno(fp)));
}
static FILE *OPENTMPFILE() { return fopen("tmpfile.txt", "w+"); }
static void CLOSETMPFILE(FILE *fp) {
  fclose(fp);
  remove("tmpfile.txt");
}
#endif

//

#define MAGIC_ARRAYMAPNODE 0xf73130fa
#define MAGIC_ARRAYMAP 0x8693bd21
#define LOGNBUCKETS 8
#define NBUCKETS (1 << LOGNBUCKETS)

static int hash(uint64_t key) {
  return (key ^ (key >> LOGNBUCKETS) ^ (key >> (LOGNBUCKETS*2)) ^ (key >> (LOGNBUCKETS*3))) & (NBUCKETS-1);
}

static void String_trim(char *str) {
  char *dst = str, *src = str, *pterm = src;

  while(*src != '\0' && isspace((int)*src)) src++;

  for(;*src != '\0';src++) {
    *dst++ = *src;
    if (!isspace((int)*src)) pterm = dst;
  }

  *pterm = '\0';
}

typedef struct ArrayMapNode {
  uint32_t magic;
  uint64_t key;
  void *value;
} ArrayMapNode;

typedef struct ArrayMap {
  uint32_t magic;
  ArrayMapNode *array[NBUCKETS];
  int size[NBUCKETS], capacity[NBUCKETS], totalSize;
} ArrayMap;

ArrayMap *initArrayMap() {
  ArrayMap *thiz = (ArrayMap *)calloc(1, sizeof(ArrayMap));
  thiz->magic = MAGIC_ARRAYMAP;

  for(int i=0;i<NBUCKETS;i++) {
    thiz->capacity[i] = 8;
    thiz->array[i] = (ArrayMapNode *)malloc(thiz->capacity[i] * sizeof(ArrayMapNode));
    thiz->size[i] = 0;
  }

  thiz->totalSize = 0;
  return thiz;
}

void ArrayMap_dispose(ArrayMap *thiz) {
  assert(thiz != NULL && thiz->magic == MAGIC_ARRAYMAP);

  for(int j=0;j<NBUCKETS;j++) {
    for(int i=0;i<thiz->size[j];i++) {
      assert(thiz->array[j][i].magic == MAGIC_ARRAYMAPNODE);
      thiz->array[j][i].magic = 0;
    }
    free(thiz->array[j]);
  }

  thiz->magic = 0;
  free(thiz);
}

int ArrayMap_size(ArrayMap *thiz) {
  assert(thiz != NULL && thiz->magic == MAGIC_ARRAYMAP);
  return thiz->totalSize;
}

uint64_t *ArrayMap_keyArray(ArrayMap *thiz) {
  assert(thiz != NULL && thiz->magic == MAGIC_ARRAYMAP);
  uint64_t *a = (uint64_t *)malloc(sizeof(uint64_t) * thiz->totalSize);
  int p = 0;
  for(int j=0;j<NBUCKETS;j++) {
    for(int i=0;i<thiz->size[j];i++) {
      assert(thiz->array[j][i].magic == MAGIC_ARRAYMAPNODE);
      a[p++] = thiz->array[j][i].key;
    }
  }
  return a;
}

void **ArrayMap_valueArray(ArrayMap *thiz) {
  assert(thiz != NULL && thiz->magic == MAGIC_ARRAYMAP);
  void **a = (void **)malloc(sizeof(void *) * thiz->totalSize);
  int p = 0;
  for(int j=0;j<NBUCKETS;j++) {
    for(int i=0;i<thiz->size[j];i++) {
      assert(thiz->array[j][i].magic == MAGIC_ARRAYMAPNODE);
      a[p++] = thiz->array[j][i].value;
    }
  }
  return a;
}

void *ArrayMap_remove(ArrayMap *thiz, uint64_t key) {
  assert(thiz != NULL && thiz->magic == MAGIC_ARRAYMAP);

  int h = hash(key);
  for(int i=0;i<thiz->size[h];i++) {
    assert(thiz->array[h][i].magic == MAGIC_ARRAYMAPNODE);
    if (thiz->array[h][i].key == key) {
      void *old = thiz->array[h][i].value;
      thiz->array[h][i].key   = thiz->array[h][thiz->size[h]-1].key;
      thiz->array[h][i].value = thiz->array[h][thiz->size[h]-1].value;
      thiz->array[h][thiz->size[h]-1].magic = 0;
      thiz->size[h]--;
      thiz->totalSize--;
      return old;
    }
  }

  return NULL;
}

void *ArrayMap_put(ArrayMap *thiz, uint64_t key, void *value) {
  if (value == NULL) return ArrayMap_remove(thiz, key);

  assert(thiz != NULL && thiz->magic == MAGIC_ARRAYMAP);

  int h = hash(key);
  for(int i=0;i<thiz->size[h];i++) {
    assert(thiz->array[h][i].magic == MAGIC_ARRAYMAPNODE);
    if (thiz->array[h][i].key == key) {
      void *old = thiz->array[h][i].value;
      thiz->array[h][i].value = value;
      return old;
    }
  }

  if (thiz->size[h] >= thiz->capacity[h]) {
    thiz->capacity[h] *= 2;
    thiz->array[h] = (ArrayMapNode *)realloc(thiz->array[h], thiz->capacity[h] * sizeof(ArrayMapNode));
  }

  ArrayMapNode *n = &(thiz->array[h][thiz->size[h]++]);
  n->magic = MAGIC_ARRAYMAPNODE;
  n->key = key;
  n->value = value;

  thiz->totalSize++;

  return NULL;
}

void *ArrayMap_get(ArrayMap *thiz, uint64_t key) {
  assert(thiz != NULL && thiz->magic == MAGIC_ARRAYMAP);

  int h = hash(key);
  for(int i=0;i<thiz->size[h];i++) {
    assert(thiz->array[h][i].magic == MAGIC_ARRAYMAPNODE);
    if (thiz->array[h][i].key == key) {
      return thiz->array[h][i].value;
    }
  }

  return NULL;
}

#define LINELEN (1024*1024)

ArrayMap *ArrayMap_load(const char *fn, const char *prefix, const char *idstr, int doLock) {
  const int idstrlen = (int)strlen(idstr);
  int prefixLen = (int)strlen(prefix) + 3;

  if (prefixLen >= LINELEN-10 || idstrlen >= LINELEN-10) return NULL;

  FILE *fp = fopen(fn, "r");
  if (fp == NULL) return NULL;

  if (doLock) FLOCK(fp);

  ArrayMap *thiz = initArrayMap();

  char *prefix2 = malloc(prefixLen+10);
  strcpy(prefix2, prefix);
  String_trim(prefix2);
  for(char *p = prefix2;*p != '\0';p++) {
    if (*p == ':') *p = ';';
    if (*p == ' ') *p = '_';
  }
  strcat(prefix2, " : ");
  prefixLen = (int)strlen(prefix2);

  char *line = malloc(sizeof(char) * (LINELEN+10));
  line[idstrlen] = '\0';

  if (fread(line, sizeof(char), idstrlen, fp) != idstrlen ||
      strcmp(idstr, line) != 0) {
    if (doLock) FUNLOCK(fp);
    fclose(fp);
    free(prefix2);
    free(line);
    return NULL;
  }

  for(;;) {
    line[LINELEN] = '\0';
    if (fgets(line, LINELEN, fp) == NULL) break;
    if (strncmp(line, prefix2, prefixLen) != 0) continue;

    uint64_t key;
    char *value = malloc(sizeof(char) * LINELEN);

    if (sscanf(line + prefixLen, "%" SCNx64 " : %s\n", &key, value) == 2) {
      ArrayMap_put(thiz, (uint64_t)key, (void *)value);
    } else {
      free(value);
    }
  }

  if (doLock) FUNLOCK(fp);
  fclose(fp);

  free(prefix2);
  free(line);

  return thiz;
}

int ArrayMap_save(ArrayMap *thiz, const char *fn, const char *prefix, const char *idstr) {
  assert(thiz != NULL && thiz->magic == MAGIC_ARRAYMAP);

  const int idstrlen = (int)strlen(idstr);
  int prefixLen = (int)strlen(prefix) + 3;

  if (prefixLen >= LINELEN-10 || idstrlen >= LINELEN-10) return -1;

  // Generate prefix2

  char *prefix2 = malloc(prefixLen+10);
  strcpy(prefix2, prefix);
  String_trim(prefix2);
  for(char *p = prefix2;*p != '\0';p++) {
    if (*p == ':') *p = ';';
    if (*p == ' ') *p = '_';
  }
  strcat(prefix2, " : ");
  prefixLen = (int)strlen(prefix2);

  //

  FILE *fp = fopen(fn, "a+");
  if (fp == NULL) return -1;

  FLOCK(fp);
  fseek(fp, 0, SEEK_SET);

  // Copy the file specified by fn to tmpfile

  FILE *tmpfp = OPENTMPFILE();
  if (tmpfp == NULL) {
    FUNLOCK(fp);
    fclose(fp);
    return -1;
  }

  char *line = malloc(sizeof(char) * (LINELEN+10));
  line[idstrlen] = '\0';

  if (fread(line, sizeof(char), idstrlen, fp) == idstrlen && strcmp(idstr, line) == 0) {
    for(;;) {
      line[LINELEN] = '\0';
      if (fgets(line, LINELEN, fp) == NULL) break;
      if (strncmp(line, prefix2, prefixLen) != 0) fputs(line, tmpfp);
    }
  }

  // Write the contents in the map into tmpfile

  uint64_t *keys = ArrayMap_keyArray(thiz);
  int s = ArrayMap_size(thiz);

  for(int i=0;i<s;i++) {
    char *value = ArrayMap_get(thiz, keys[i]);
    if (strlen(value) + prefixLen >= LINELEN-10) continue;
    fprintf(tmpfp, "%s %" PRIx64 " : %s\n", prefix2, keys[i], value);
  }

  free(keys);

  fseek(fp, 0, SEEK_SET);
  FTRUNCATE(fp, 0);
  fwrite(idstr, sizeof(char), strlen(idstr), fp);

  fseek(tmpfp, 0, SEEK_SET);

  for(;;) {
    size_t s = fread(line, 1, LINELEN, tmpfp);
    if (s == 0) break;
    fwrite(line, 1, s, fp);
  }

  FUNLOCK(fp);
  fclose(fp);

  CLOSETMPFILE(tmpfp);
  free(prefix2);
  free(line);
  return 0;
}
