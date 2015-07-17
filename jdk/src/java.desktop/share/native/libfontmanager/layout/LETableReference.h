/*
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

/*
 * -*- c++ -*-
 *
 * (C) Copyright IBM Corp. and others 2013 - All Rights Reserved
 *
 * Range checking
 *
 */

#ifndef __LETABLEREFERENCE_H
#define __LETABLEREFERENCE_H

#include "LETypes.h"
#include "LEFontInstance.h"

/**
 * \def LE_ENABLE_RAW
 * If this is 1, enables old non-safe raw access
 */
#ifndef LE_ENABLE_RAW
#define LE_ENABLE_RAW 0
#endif

#define kQuestionmarkTableTag  0x3F3F3F3FUL /* ???? */
#define kStaticTableTag  0x30303030UL  /* 0000 */
#define kTildeTableTag  0x7e7e7e7eUL /* ~~~~ */
#ifdef __cplusplus

// internal - interface for range checking
U_NAMESPACE_BEGIN

#if LE_ASSERT_BAD_FONT

#ifndef LE_TRACE_TR
#define LE_TRACE_TR 0
#endif

class LETableReference; // fwd
/**
 *  defined in OpenTypeUtilities.cpp
 * @internal
 */
U_CAPI void U_EXPORT2 _debug_LETableReference(const char *f, int l, const char *msg, const LETableReference *what, const void *ptr, size_t len);

#define LE_DEBUG_TR(x) _debug_LETableReference(__FILE__, __LINE__, x, this, NULL, 0);
#define LE_DEBUG_TR3(x,y,z) _debug_LETableReference(__FILE__, __LINE__, x, this, (const void*)y, (size_t)z);
#if LE_TRACE_TR
#define _TRTRACE(x) _debug_LETableReference(__FILE__, __LINE__, x, this, NULL, 0);
#else
#define _TRTRACE(x)
#endif

#else
#define LE_DEBUG_TR(x)
#define LE_DEBUG_TR3(x,y,z)
#define _TRTRACE(x)
#endif

/**
 * @internal
 */
class LETableReference {
public:

  /**
   * Dummy enum asserting that a value is actually static data
   * and does not need to be range checked
   */
  enum EStaticData { kStaticData = 0 };

/**
 * @internal
 * Construct from a specific tag
 */
  LETableReference(const LEFontInstance* font, LETag tableTag, LEErrorCode &success) :
    fFont(font), fTag(tableTag), fParent(NULL), fStart(NULL),fLength(LE_UINTPTR_MAX) {
      loadTable(success);
    _TRTRACE("INFO: new table load")
  }

  LETableReference(const LETableReference &parent, LEErrorCode &success) : fFont(parent.fFont), fTag(parent.fTag), fParent(&parent), fStart(parent.fStart), fLength(parent.fLength) {
    if(LE_FAILURE(success)) {
      clear();
    }
    _TRTRACE("INFO: new clone")
  }

#if LE_ENABLE_RAW
   /**
    * Construct  without a parent LETR.
    */
   LETableReference(const le_uint8* data, size_t length = LE_UINTPTR_MAX) :
    fFont(NULL), fTag(kQuestionmarkTableTag), fParent(NULL), fStart(data), fLength(length) {
    _TRTRACE("INFO: new raw")
  }
#endif

   /**
    * Construct  without a parent LETR.
    */
 LETableReference(EStaticData /* NOTUSED */, const le_uint8* data, size_t length) :
    fFont(NULL), fTag(kQuestionmarkTableTag), fParent(NULL), fStart(data), fLength(length) {
    _TRTRACE("INFO: new EStaticData")
  }

  LETableReference() :
    fFont(NULL), fTag(kQuestionmarkTableTag), fParent(NULL), fStart(NULL), fLength(0) {
    _TRTRACE("INFO: new empty")
  }

  ~LETableReference() {
    fTag= (LETag)kTildeTableTag;
    _TRTRACE("INFO: new dtor")
  }

  /**
   * @internal
   * @param length  if LE_UINTPTR_MAX means "whole table"
   * subset
   */
  LETableReference(const LETableReference &parent, size_t offset, size_t length,
                   LEErrorCode &err) :
    fFont(parent.fFont), fTag(parent.fTag), fParent(&parent),
    fStart((parent.fStart)+offset), fLength(length) {
    if(LE_SUCCESS(err)) {
      if(isEmpty()) {
        //err = LE_MISSING_FONT_TABLE_ERROR;
        clear(); // it's just empty. Not an error.
      } else if(offset >= fParent->fLength || (offset & 0x01)) {
        LE_DEBUG_TR3("offset out of range or odd alignment: (%p) +%d", NULL, offset);
        err = LE_INDEX_OUT_OF_BOUNDS_ERROR;
        clear();
      } else {
        if(fLength == LE_UINTPTR_MAX &&
           fParent->fLength != LE_UINTPTR_MAX) {
          fLength = (fParent->fLength) - offset; // decrement length as base address is incremented
        }
        if(fLength != LE_UINTPTR_MAX) {  // if we have bounds:
          if((offset+fLength < offset) || (offset+fLength > fParent->fLength)) {
            LE_DEBUG_TR3("offset+fLength out of range: (%p) +%d", NULL, offset+fLength);
            err = LE_INDEX_OUT_OF_BOUNDS_ERROR; // exceeded
            clear();
          }
        }
      }
    } else {
      clear();
    }
    _TRTRACE("INFO: new subset")
  }

  const void* getAlias() const { return (const void*)fStart; }
#ifndef LE_ENABLE_RAW
  const void* getAliasRAW() const { LE_DEBUG_TR("getAliasRAW()"); return (const void*)fStart; }
#endif
  le_bool isEmpty() const { return fStart==NULL || fLength==0; }
  le_bool isValid() const { return !isEmpty(); }
  le_bool hasBounds() const { return fLength!=LE_UINTPTR_MAX; }
  void clear() { fLength=0; fStart=NULL; }
  size_t getLength() const { return fLength; }
  const LEFontInstance* getFont() const { return fFont; }
  LETag getTag() const { return fTag; }
  const LETableReference* getParent() const { return fParent; }

  void addOffset(size_t offset, LEErrorCode &success) {
    if(hasBounds()) {
      if(offset >= fLength) {
        LE_DEBUG_TR("addOffset off end");
        success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
        return;
      } else {
        fLength -= offset;
      }
    }
    fStart += offset;
  }

  size_t ptrToOffset(const void *atPtr, LEErrorCode &success) const {
    if(atPtr==NULL) return 0;
    if(LE_FAILURE(success)) return LE_UINTPTR_MAX;
    if((atPtr < fStart) ||
       (hasBounds() && (atPtr >= fStart+fLength))) {
      LE_DEBUG_TR3("ptrToOffset args out of range: %p", atPtr, 0);
      success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
      return LE_UINTPTR_MAX;
    }
    return ((const le_uint8*)atPtr)-fStart;
  }

  /**
   * Clamp down the length, for range checking.
   */
  size_t contractLength(size_t newLength) {
    if(fLength!=LE_UINTPTR_MAX&&newLength>0&&newLength<=fLength) {
      fLength = newLength;
    }
    return fLength;
  }

  /**
   * Throw an error if offset+length off end
   */
public:
  size_t verifyLength(size_t offset, size_t length, LEErrorCode &success) {
    if(isValid()&&
       LE_SUCCESS(success) &&
       fLength!=LE_UINTPTR_MAX && length!=LE_UINTPTR_MAX && offset!=LE_UINTPTR_MAX &&
       (offset+length)>fLength) {
      LE_DEBUG_TR3("verifyLength failed (%p) %d",NULL, offset+length);
      success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
#if LE_ASSERT_BAD_FONT
      fprintf(stderr, "offset=%lu, len=%lu, would be at %p, (%lu) off end. End at %p\n", offset,length, fStart+offset+length, (offset+length-fLength), (offset+length-fLength)+fStart);
#endif
    }
    return fLength;
  }

  /**
  * Throw an error if size*count overflows
  */
  size_t verifyLength(size_t offset, size_t size, le_uint32 count, LEErrorCode &success) {
    if(count!=0 && size>LE_UINT32_MAX/count) {
      LE_DEBUG_TR3("verifyLength failed size=%u, count=%u", size, count);
      success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
      return 0;
    }
    return verifyLength(offset, size*count, success);
  }

  /**
   * Change parent link to another
   */
  LETableReference &reparent(const LETableReference &base) {
    fParent = &base;
    return *this;
  }

  /**
   * remove parent link. Factory functions should do this.
   */
  void orphan(void) {
    fParent=NULL;
  }

protected:
  const LEFontInstance* fFont;
  LETag  fTag;
  const LETableReference *fParent;
  const le_uint8 *fStart; // keep as 8 bit internally, for pointer math
  size_t fLength;

  void loadTable(LEErrorCode &success) {
    if(LE_SUCCESS(success)) {
      fStart = (const le_uint8*)(fFont->getFontTable(fTag, fLength)); // note - a null table is not an error.
    }
  }

  void setRaw(const void *data, size_t length = LE_UINTPTR_MAX) {
    fFont = NULL;
    fTag = (LETag)kQuestionmarkTableTag;
    fParent = NULL;
    fStart = (const le_uint8*)data;
    fLength = length;
  }

  /**
   * set this object pointing to static data
   */
  void setTo(EStaticData /*notused*/, const void *data, size_t length) {
    fFont = NULL;
    fTag = (LETag)kStaticTableTag;
    fParent = NULL;
    fStart = (const le_uint8*)data;
    fLength = length;
  }
};


template<class T>
class LETableVarSizer {
 public:
  inline static size_t getSize();
};

// base definition- could override for adjustments
template<class T> inline
size_t LETableVarSizer<T>::getSize() {
  return sizeof(T);
}

/**
 * \def LE_VAR_ARRAY
 * @param x Type (T)
 * @param y some member that is of length ANY_NUMBER
 * Call this after defining a class, for example:
 *   LE_VAR_ARRAY(FeatureListTable,featureRecordArray)
 * this is roughly equivalent to:
 *   template<> inline size_t LETableVarSizer<FeatureListTable>::getSize() { return sizeof(FeatureListTable) - (sizeof(le_uint16)*ANY_NUMBER); }
 * it's a specialization that informs the LETableReference subclasses to NOT include the variable array in the size.
 * dereferencing NULL is valid here because we never actually dereference it, just inside sizeof.
 */
#define LE_VAR_ARRAY(x,y) template<> inline size_t LETableVarSizer<x>::getSize() { return sizeof(x) - (sizeof(((const x*)0)->y)); }
/**
 * \def LE_CORRECT_SIZE
 * @param x type (T)
 * @param y fixed size for T
 */
#define LE_CORRECT_SIZE(x,y) template<> inline size_t LETableVarSizer<x>::getSize() { return y; }

/**
 * Open a new entry based on an existing table
 */

template<class T>
class LEReferenceTo : public LETableReference {
public:
  /**
   * open a sub reference.
   * @param parent parent reference
   * @param success error status
   * @param atPtr location of reference - if NULL, will be at offset zero (i.e. downcast of parent). Otherwise must be a pointer within parent's bounds.
   */
 inline LEReferenceTo(const LETableReference &parent, LEErrorCode &success, const void* atPtr)
    : LETableReference(parent, parent.ptrToOffset(atPtr, success), LE_UINTPTR_MAX, success) {
    verifyLength(0, LETableVarSizer<T>::getSize(), success);
    if(LE_FAILURE(success)) clear();
  }
  /**
   * ptr plus offset
   */
 inline LEReferenceTo(const LETableReference &parent, LEErrorCode &success, const void* atPtr, size_t offset)
    : LETableReference(parent, parent.ptrToOffset(atPtr, success)+offset, LE_UINTPTR_MAX, success) {
    verifyLength(0, LETableVarSizer<T>::getSize(), success);
    if(LE_FAILURE(success)) clear();
  }
 inline LEReferenceTo(const LETableReference &parent, LEErrorCode &success, size_t offset)
    : LETableReference(parent, offset, LE_UINTPTR_MAX, success) {
    verifyLength(0, LETableVarSizer<T>::getSize(), success);
    if(LE_FAILURE(success)) clear();
  }
 inline LEReferenceTo(const LETableReference &parent, LEErrorCode &success)
    : LETableReference(parent, 0, LE_UINTPTR_MAX, success) {
    verifyLength(0, LETableVarSizer<T>::getSize(), success);
    if(LE_FAILURE(success)) clear();
  }
 inline LEReferenceTo(const LEFontInstance *font, LETag tableTag, LEErrorCode &success)
   : LETableReference(font, tableTag, success) {
    verifyLength(0, LETableVarSizer<T>::getSize(), success);
    if(LE_FAILURE(success)) clear();
  }
#if LE_ENABLE_RAW
 inline LEReferenceTo(const le_uint8 *data, size_t length = LE_UINTPTR_MAX) : LETableReference(data, length) {}
 inline LEReferenceTo(const T *data, size_t length = LE_UINTPTR_MAX) : LETableReference((const le_uint8*)data, length) {}
#endif
 inline LEReferenceTo(EStaticData staticData, const le_uint8 *data, size_t length) : LETableReference(staticData, data, length) {}
 inline LEReferenceTo(EStaticData staticData, const T *data, size_t length) : LETableReference(staticData, (const le_uint8*)data, length) {}

 inline LEReferenceTo() : LETableReference() {}

#if LE_ENABLE_RAW
 inline LEReferenceTo<T>& operator=(const T* other) {
    setRaw(other);
    return *this;
  }
#endif

 LEReferenceTo<T>& setTo(LETableReference::EStaticData staticData, const T* other, size_t length) {
   LETableReference::setTo(staticData, other, length);
   return *this;
 }

  LEReferenceTo<T> &reparent(const LETableReference &base) {
    fParent = &base;
    return *this;
  }

  /**
   * roll forward by one <T> size.
   * same as addOffset(LETableVarSizer<T>::getSize(),success)
   */
  void addObject(LEErrorCode &success) {
    addOffset(LETableVarSizer<T>::getSize(), success);
  }
  void addObject(size_t count, LEErrorCode &success) {
    addOffset(LETableVarSizer<T>::getSize()*count, success);
  }

  const T *operator->() const { return getAlias(); }
  const T *operator*() const { return getAlias(); }
  const T *getAlias() const { return (const T*)fStart; }
#if LE_ENABLE_RAW
  const T *getAliasRAW() const { LE_DEBUG_TR("getAliasRAW<>"); return (const T*)fStart; }
#endif

};


/**
 * \def LE_UNBOUNDED_ARRAY
 * define an array with no *known* bound. Will trim to available size.
 * @internal
 */
#define LE_UNBOUNDED_ARRAY LE_UINT32_MAX

template<class T>
class LEReferenceToArrayOf : public LETableReference {
public:
  LEReferenceToArrayOf(const LETableReference &parent, LEErrorCode &success, size_t offset, le_uint32 count)
    : LETableReference(parent, offset, LE_UINTPTR_MAX, success), fCount(count) {
    _TRTRACE("INFO: new RTAO by offset")
    if(LE_SUCCESS(success)) {
      if(fCount == LE_UNBOUNDED_ARRAY) { // not a known length
        fCount = getLength()/LETableVarSizer<T>::getSize(); // fit to max size
      }
      LETableReference::verifyLength(0, LETableVarSizer<T>::getSize(), fCount, success);
    }
    if(LE_FAILURE(success)) {
      fCount=0;
      clear();
    }
  }

  LEReferenceToArrayOf(const LETableReference &parent, LEErrorCode &success, const T* array, le_uint32 count)
    : LETableReference(parent, parent.ptrToOffset(array, success), LE_UINTPTR_MAX, success), fCount(count) {
_TRTRACE("INFO: new RTAO")
    if(LE_SUCCESS(success)) {
      if(fCount == LE_UNBOUNDED_ARRAY) { // not a known length
        fCount = getLength()/LETableVarSizer<T>::getSize(); // fit to max size
      }
      LETableReference::verifyLength(0, LETableVarSizer<T>::getSize(), fCount, success);
    }
    if(LE_FAILURE(success)) clear();
  }
 LEReferenceToArrayOf(const LETableReference &parent, LEErrorCode &success, const T* array, size_t offset, le_uint32 count)
   : LETableReference(parent, parent.ptrToOffset(array, success)+offset, LE_UINTPTR_MAX, success), fCount(count) {
_TRTRACE("INFO: new RTAO")
    if(LE_SUCCESS(success)) {
      if(fCount == LE_UNBOUNDED_ARRAY) { // not a known length
        fCount = getLength()/LETableVarSizer<T>::getSize(); // fit to max size
      }
      LETableReference::verifyLength(0, LETableVarSizer<T>::getSize(), fCount, success);
    }
    if(LE_FAILURE(success)) clear();
  }

 LEReferenceToArrayOf() :LETableReference(), fCount(0) {}

  le_uint32 getCount() const { return fCount; }

  const T *getAlias() const { return (const T*)fStart; }

  const T *getAlias(le_uint32 i, LEErrorCode &success) const {
    return ((const T*)(((const char*)getAlias())+getOffsetFor(i, success)));
  }

#ifndef LE_ENABLE_RAW
  const T *getAliasRAW() const { LE_DEBUG_TR("getAliasRAW<>"); return (const T*)fStart; }
#endif

  const T& getObject(le_uint32 i, LEErrorCode &success) const {
      const T *ret = getAlias(i, success);
      if (LE_FAILURE(success) || ret==NULL) {
          return *(new T(0));
      } else {
          return *ret;
     }
  }

  /**
   * by-value array accessor for integral types.
   */
  const T operator[](le_uint32 i) const {
    LEErrorCode success = LE_NO_ERROR;
    const T *ret = getAlias(i, success);
    if(LE_FAILURE(success) || ret==NULL) {
#if LE_ASSERT_BAD_FONT
      LE_DEBUG_TR3("Range error, out of bounds? (%p) #%d", NULL, i);
#endif
      return T(0); // will not work for all types.
    }
    return *ret;
  }

  const LEReferenceTo<T> getReference(le_uint32 i, LEErrorCode &success) const {
    if(LE_FAILURE(success)) return LEReferenceTo<T>();
    return LEReferenceTo<T>(*this, success, getAlias(i,success));
  }

  const T& operator()(le_uint32 i, LEErrorCode &success) const {
    return *getAlias(i,success);
  }

  size_t getOffsetFor(le_uint32 i, LEErrorCode &success) const {
    if(LE_SUCCESS(success)&&i<getCount()) {
      return LETableVarSizer<T>::getSize()*i;
    } else {
      LE_DEBUG_TR3("getOffsetFor failed (%p) index=%d",NULL, i);
      success = LE_INDEX_OUT_OF_BOUNDS_ERROR;
    }
    return 0;
  }

  LEReferenceToArrayOf<T> &reparent(const LETableReference &base) {
    fParent = &base;
    return *this;
  }

 LEReferenceToArrayOf(const LETableReference& parent, LEErrorCode & success) : LETableReference(parent,0, LE_UINTPTR_MAX, success), fCount(0) {
    _TRTRACE("INFO: null RTAO")
  }

private:
  le_uint32 fCount;
};




#ifdef _TRTRACE
#undef _TRTRACE
#endif

U_NAMESPACE_END

#endif

#endif
