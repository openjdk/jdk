/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef SHARE_GC_CMS_PROMOTIONINFO_INLINE_HPP
#define SHARE_GC_CMS_PROMOTIONINFO_INLINE_HPP

#include "gc/cms/promotionInfo.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"

//////////////////////////////////////////////////////////////////////////////
// We go over the list of promoted objects, removing each from the list,
// and applying the closure (this may, in turn, add more elements to
// the tail of the promoted list, and these newly added objects will
// also be processed) until the list is empty.
// To aid verification and debugging, in the non-product builds
// we actually forward _promoHead each time we process a promoted oop.
// Note that this is not necessary in general (i.e. when we don't need to
// call PromotionInfo::verify()) because oop_iterate can only add to the
// end of _promoTail, and never needs to look at _promoHead.

template <typename OopClosureType>
void PromotionInfo::promoted_oops_iterate(OopClosureType* cl) {
  NOT_PRODUCT(verify());
  PromotedObject *curObj, *nextObj;
  for (curObj = _promoHead; curObj != NULL; curObj = nextObj) {
    if ((nextObj = curObj->next()) == NULL) {
      /* protect ourselves against additions due to closure application
         below by resetting the list.  */
      assert(_promoTail == curObj, "Should have been the tail");
      _promoHead = _promoTail = NULL;
    }
    if (curObj->hasDisplacedMark()) {
      /* restore displaced header */
      oop(curObj)->set_mark_raw(nextDisplacedHeader());
    } else {
      /* restore prototypical header */
      oop(curObj)->init_mark_raw();
    }
    /* The "promoted_mark" should now not be set */
    assert(!curObj->hasPromotedMark(),
           "Should have been cleared by restoring displaced mark-word");
    NOT_PRODUCT(_promoHead = nextObj);
    if (cl != NULL) oop(curObj)->oop_iterate(cl);
    if (nextObj == NULL) { /* start at head of list reset above */
      nextObj = _promoHead;
    }
  }
  assert(noPromotions(), "post-condition violation");
  assert(_promoHead == NULL && _promoTail == NULL, "emptied promoted list");
  assert(_spoolHead == _spoolTail, "emptied spooling buffers");
  assert(_firstIndex == _nextIndex, "empty buffer");
}

#endif //  SHARE_GC_CMS_PROMOTIONINFO_INLINE_HPP
