/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

/*
 *
 * (C) Copyright IBM Corp. 1998-2005 - All Rights Reserved
 *
 */

#ifndef __GLYPHPOSITIONADJUSTMENTS_H
#define __GLYPHPOSITIONADJUSTMENTS_H

/**
 * \file
 * \internal
 */

#include "LETypes.h"
#include "OpenTypeTables.h"

U_NAMESPACE_BEGIN

class LEGlyphStorage;
class LEFontInstance;

class GlyphPositionAdjustments : public UMemory
{
private:
    class Adjustment : public UMemory {
    public:

        inline Adjustment();
        inline Adjustment(float xPlace, float yPlace, float xAdv, float yAdv, le_int32 baseOff = -1);
        inline ~Adjustment();

        inline float    getXPlacement() const;
        inline float    getYPlacement() const;
        inline float    getXAdvance() const;
        inline float    getYAdvance() const;

        inline le_int32 getBaseOffset() const;

        inline void     setXPlacement(float newXPlacement);
        inline void     setYPlacement(float newYPlacement);
        inline void     setXAdvance(float newXAdvance);
        inline void     setYAdvance(float newYAdvance);

        inline void     setBaseOffset(le_int32 newBaseOffset);

        inline void    adjustXPlacement(float xAdjustment);
        inline void    adjustYPlacement(float yAdjustment);
        inline void    adjustXAdvance(float xAdjustment);
        inline void    adjustYAdvance(float yAdjustment);

    private:
        float xPlacement;
        float yPlacement;
        float xAdvance;
        float yAdvance;

        le_int32 baseOffset;

        // allow copying of this class because all of its fields are simple types
    };

    class EntryExitPoint : public UMemory
    {
    public:
        inline EntryExitPoint();
        inline ~EntryExitPoint();

        inline le_bool isCursiveGlyph() const;
        inline le_bool baselineIsLogicalEnd() const;

        LEPoint *getEntryPoint(LEPoint &entryPoint) const;
        LEPoint *getExitPoint(LEPoint &exitPoint) const;

        inline void setEntryPoint(LEPoint &newEntryPoint, le_bool baselineIsLogicalEnd);
        inline void setExitPoint(LEPoint &newExitPoint, le_bool baselineIsLogicalEnd);
        inline void setCursiveGlyph(le_bool baselineIsLogicalEnd);

    private:
        enum EntryExitFlags
        {
            EEF_HAS_ENTRY_POINT         = 0x80000000L,
            EEF_HAS_EXIT_POINT          = 0x40000000L,
            EEF_IS_CURSIVE_GLYPH        = 0x20000000L,
            EEF_BASELINE_IS_LOGICAL_END = 0x10000000L
        };

        le_uint32 fFlags;
        LEPoint fEntryPoint;
        LEPoint fExitPoint;
    };

    le_int32 fGlyphCount;
    EntryExitPoint *fEntryExitPoints;
    Adjustment *fAdjustments;

    GlyphPositionAdjustments();

public:
    GlyphPositionAdjustments(le_int32 glyphCount);
    ~GlyphPositionAdjustments();

    inline le_bool hasCursiveGlyphs() const;
    inline le_bool isCursiveGlyph(le_int32 index) const;
    inline le_bool baselineIsLogicalEnd(le_int32 index) const;

    const LEPoint *getEntryPoint(le_int32 index, LEPoint &entryPoint) const;
    const LEPoint *getExitPoint(le_int32 index, LEPoint &exitPoint) const;

    inline float getXPlacement(le_int32 index) const;
    inline float getYPlacement(le_int32 index) const;
    inline float getXAdvance(le_int32 index) const;
    inline float getYAdvance(le_int32 index) const;

    inline le_int32 getBaseOffset(le_int32 index) const;

    inline void setXPlacement(le_int32 index, float newXPlacement);
    inline void setYPlacement(le_int32 index, float newYPlacement);
    inline void setXAdvance(le_int32 index, float newXAdvance);
    inline void setYAdvance(le_int32 index, float newYAdvance);

    inline void setBaseOffset(le_int32 index, le_int32 newBaseOffset);

    inline void adjustXPlacement(le_int32 index, float xAdjustment);
    inline void adjustYPlacement(le_int32 index, float yAdjustment);
    inline void adjustXAdvance(le_int32 index, float xAdjustment);
    inline void adjustYAdvance(le_int32 index, float yAdjustment);

    void setEntryPoint(le_int32 index, LEPoint &newEntryPoint, le_bool baselineIsLogicalEnd);
    void setExitPoint(le_int32 index, LEPoint &newExitPoint, le_bool baselineIsLogicalEnd);
    void setCursiveGlyph(le_int32 index, le_bool baselineIsLogicalEnd);

    void applyCursiveAdjustments(LEGlyphStorage &glyphStorage, le_bool rightToLeft, const LEFontInstance *fontInstance);
};

inline GlyphPositionAdjustments::Adjustment::Adjustment()
  : xPlacement(0), yPlacement(0), xAdvance(0), yAdvance(0), baseOffset(-1)
{
    // nothing else to do!
}

inline GlyphPositionAdjustments::Adjustment::Adjustment(float xPlace, float yPlace, float xAdv, float yAdv, le_int32 baseOff)
  : xPlacement(xPlace), yPlacement(yPlace), xAdvance(xAdv), yAdvance(yAdv), baseOffset(baseOff)
{
    // nothing else to do!
}

inline GlyphPositionAdjustments::Adjustment::~Adjustment()
{
    // nothing to do!
}

inline float GlyphPositionAdjustments::Adjustment::getXPlacement() const
{
    return xPlacement;
}

inline float GlyphPositionAdjustments::Adjustment::getYPlacement() const
{
    return yPlacement;
}

inline float GlyphPositionAdjustments::Adjustment::getXAdvance() const
{
    return xAdvance;
}

inline float GlyphPositionAdjustments::Adjustment::getYAdvance() const
{
    return yAdvance;
}

inline le_int32 GlyphPositionAdjustments::Adjustment::getBaseOffset() const
{
    return baseOffset;
}

inline void GlyphPositionAdjustments::Adjustment::setXPlacement(float newXPlacement)
{
    xPlacement = newXPlacement;
}

inline void GlyphPositionAdjustments::Adjustment::setYPlacement(float newYPlacement)
{
    yPlacement = newYPlacement;
}

inline void GlyphPositionAdjustments::Adjustment::setXAdvance(float newXAdvance)
{
    xAdvance = newXAdvance;
}

inline void GlyphPositionAdjustments::Adjustment::setYAdvance(float newYAdvance)
{
    yAdvance = newYAdvance;
}

inline void GlyphPositionAdjustments::Adjustment::setBaseOffset(le_int32 newBaseOffset)
{
    baseOffset = newBaseOffset;
}

inline void GlyphPositionAdjustments::Adjustment::adjustXPlacement(float xAdjustment)
{
    xPlacement += xAdjustment;
}

inline void GlyphPositionAdjustments::Adjustment::adjustYPlacement(float yAdjustment)
{
    yPlacement += yAdjustment;
}

inline void GlyphPositionAdjustments::Adjustment::adjustXAdvance(float xAdjustment)
{
    xAdvance += xAdjustment;
}

inline void GlyphPositionAdjustments::Adjustment::adjustYAdvance(float yAdjustment)
{
    yAdvance += yAdjustment;
}

inline GlyphPositionAdjustments::EntryExitPoint::EntryExitPoint()
    : fFlags(0)
{
    fEntryPoint.fX = fEntryPoint.fY = fExitPoint.fX = fExitPoint.fY = 0;
}

inline GlyphPositionAdjustments::EntryExitPoint::~EntryExitPoint()
{
    // nothing special to do
}

inline le_bool GlyphPositionAdjustments::EntryExitPoint::isCursiveGlyph() const
{
    return (fFlags & EEF_IS_CURSIVE_GLYPH) != 0;
}

inline le_bool GlyphPositionAdjustments::EntryExitPoint::baselineIsLogicalEnd() const
{
    return (fFlags & EEF_BASELINE_IS_LOGICAL_END) != 0;
}

inline void GlyphPositionAdjustments::EntryExitPoint::setEntryPoint(LEPoint &newEntryPoint, le_bool baselineIsLogicalEnd)
{
    if (baselineIsLogicalEnd) {
        fFlags |= (EEF_HAS_ENTRY_POINT | EEF_IS_CURSIVE_GLYPH | EEF_BASELINE_IS_LOGICAL_END);
    } else {
        fFlags |= (EEF_HAS_ENTRY_POINT | EEF_IS_CURSIVE_GLYPH);
    }

    fEntryPoint = newEntryPoint;
}

inline void GlyphPositionAdjustments::EntryExitPoint::setExitPoint(LEPoint &newExitPoint, le_bool baselineIsLogicalEnd)
{
    if (baselineIsLogicalEnd) {
        fFlags |= (EEF_HAS_EXIT_POINT | EEF_IS_CURSIVE_GLYPH | EEF_BASELINE_IS_LOGICAL_END);
    } else {
        fFlags |= (EEF_HAS_EXIT_POINT | EEF_IS_CURSIVE_GLYPH);
    }

    fExitPoint  = newExitPoint;
}

inline void GlyphPositionAdjustments::EntryExitPoint::setCursiveGlyph(le_bool baselineIsLogicalEnd)
{
    if (baselineIsLogicalEnd) {
        fFlags |= (EEF_IS_CURSIVE_GLYPH | EEF_BASELINE_IS_LOGICAL_END);
    } else {
        fFlags |= EEF_IS_CURSIVE_GLYPH;
    }
}

inline le_bool GlyphPositionAdjustments::isCursiveGlyph(le_int32 index) const
{
    return fEntryExitPoints != NULL && fEntryExitPoints[index].isCursiveGlyph();
}

inline le_bool GlyphPositionAdjustments::baselineIsLogicalEnd(le_int32 index) const
{
    return fEntryExitPoints != NULL && fEntryExitPoints[index].baselineIsLogicalEnd();
}

inline float GlyphPositionAdjustments::getXPlacement(le_int32 index) const
{
    return fAdjustments[index].getXPlacement();
}

inline float GlyphPositionAdjustments::getYPlacement(le_int32 index) const
{
    return fAdjustments[index].getYPlacement();
}

inline float GlyphPositionAdjustments::getXAdvance(le_int32 index) const
{
    return fAdjustments[index].getXAdvance();
}

inline float GlyphPositionAdjustments::getYAdvance(le_int32 index) const
{
    return fAdjustments[index].getYAdvance();
}


inline le_int32 GlyphPositionAdjustments::getBaseOffset(le_int32 index) const
{
    return fAdjustments[index].getBaseOffset();
}

inline void GlyphPositionAdjustments::setXPlacement(le_int32 index, float newXPlacement)
{
    fAdjustments[index].setXPlacement(newXPlacement);
}

inline void GlyphPositionAdjustments::setYPlacement(le_int32 index, float newYPlacement)
{
    fAdjustments[index].setYPlacement(newYPlacement);
}

inline void GlyphPositionAdjustments::setXAdvance(le_int32 index, float newXAdvance)
{
    fAdjustments[index].setXAdvance(newXAdvance);
}

inline void GlyphPositionAdjustments::setYAdvance(le_int32 index, float newYAdvance)
{
    fAdjustments[index].setYAdvance(newYAdvance);
}

inline void GlyphPositionAdjustments::setBaseOffset(le_int32 index, le_int32 newBaseOffset)
{
    fAdjustments[index].setBaseOffset(newBaseOffset);
}

inline void GlyphPositionAdjustments::adjustXPlacement(le_int32 index, float xAdjustment)
{
    fAdjustments[index].adjustXPlacement(xAdjustment);
}

inline void GlyphPositionAdjustments::adjustYPlacement(le_int32 index, float yAdjustment)
{
    fAdjustments[index].adjustYPlacement(yAdjustment);
}

inline void GlyphPositionAdjustments::adjustXAdvance(le_int32 index, float xAdjustment)
{
    fAdjustments[index].adjustXAdvance(xAdjustment);
}

inline void GlyphPositionAdjustments::adjustYAdvance(le_int32 index, float yAdjustment)
{
    fAdjustments[index].adjustYAdvance(yAdjustment);
}

inline le_bool GlyphPositionAdjustments::hasCursiveGlyphs() const
{
    return fEntryExitPoints != NULL;
}

U_NAMESPACE_END
#endif
