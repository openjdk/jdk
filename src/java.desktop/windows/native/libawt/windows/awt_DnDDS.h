/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef AWT_DND_DS_H
#define AWT_DND_DS_H

#include <Ole2.h>

#include <jni.h>
#include <jvm.h>
#include <jni_util.h>

#include "awt_Object.h"
#include "awt_Component.h"
#include "awt_Window.h"

class AwtCursor;

/**
 * Drag Source code
 */

class AwtDragSource : virtual public IDropSource, virtual public IDataObject {
    public:

        AwtDragSource(JNIEnv* env, jobject peer, jobject component,
                      jobject transferable, jobject trigger,
                      jint actions, jlongArray formats, jobject formatMap);

        virtual ~AwtDragSource();

        // IUnknown

        virtual HRESULT QueryInterface(REFIID riid, void __RPC_FAR *__RPC_FAR *ppvObject);

        virtual ULONG   AddRef(void);
        virtual ULONG   Release(void);

        // IDropSource

        virtual HRESULT QueryContinueDrag(BOOL fEscapeKeyPressed, DWORD grfKeyState);

        virtual HRESULT GiveFeedback(DWORD dwEffect);

        // IDataObject

        virtual HRESULT GetData(FORMATETC __RPC_FAR *pFormatEtc, STGMEDIUM __RPC_FAR *pmedium);
        virtual HRESULT GetDataHere(FORMATETC __RPC_FAR *pFormatEtc, STGMEDIUM __RPC_FAR *pmedium);

        virtual HRESULT QueryGetData(FORMATETC __RPC_FAR *pFormatEtc);

        virtual HRESULT GetCanonicalFormatEtc(FORMATETC __RPC_FAR *pFormatEtcIn, FORMATETC __RPC_FAR *pFormatEtcOut);

        virtual HRESULT SetData(FORMATETC __RPC_FAR *pFormatEtc, STGMEDIUM __RPC_FAR *pmedium, BOOL fRelease);

        virtual HRESULT EnumFormatEtc(DWORD dwDirection, IEnumFORMATETC *__RPC_FAR *ppenumFormatEtc);

        virtual HRESULT DAdvise(FORMATETC __RPC_FAR *pFormatEtc, DWORD advf, IAdviseSink __RPC_FAR *pAdvSink, DWORD __RPC_FAR *pdwConnection);
        virtual HRESULT DUnadvise(DWORD dwConnection);
        virtual HRESULT EnumDAdvise(IEnumSTATDATA __RPC_FAR *__RPC_FAR *ppenumAdvise);


        // AwtDragSource

        static void StartDrag(
            AwtDragSource* self,
            jobject cursor,
            jintArray imageData,
            jint imageWidth,
            jint imageHeight,
            jint x,
            jint y);

        HRESULT ChangeCursor();
        void SetCursor(jobject cursor);

        INLINE unsigned int getNTypes() { return m_ntypes; }

        INLINE FORMATETC getType(unsigned int i) { return m_types[i]; }

        INLINE jobject GetPeer() { return m_peer; }

        INLINE void Signal() { ::ReleaseMutex(m_mutex); }

        virtual HRESULT GetProcessId(FORMATETC __RPC_FAR *pFormatEtc, STGMEDIUM __RPC_FAR *pmedium);

    protected:
        INLINE void WaitUntilSignalled(BOOL retain) {
            do {
                // nothing ...
            } while(::WaitForSingleObject(m_mutex, INFINITE) == WAIT_FAILED);

            if (!retain) ::ReleaseMutex(m_mutex);
        }

        static void _DoDragDrop(void* param);

        HRESULT MatchFormatEtc(FORMATETC __RPC_FAR *pFormatEtcIn, FORMATETC *cacheEnt);

   private:

        void LoadCache(jlongArray formats);
        void UnloadCache();

        static int _compar(const void *, const void *);

        static void call_dSCenter(JNIEnv* env, jobject self, jint targetActions,
                                  jint modifiers, POINT pt);
        static void call_dSCmotion(JNIEnv* env, jobject self,
                                   jint targetActions, jint modifiers,
                                   POINT pt);
        static void call_dSCchanged(JNIEnv* env, jobject self,
                                    jint targetActions, jint modifiers,
                                    POINT pt);
        static void call_dSCmouseMoved(JNIEnv* env, jobject self,
                                       jint targetActions, jint modifiers,
                                       POINT pt);
        static void call_dSCexit(JNIEnv* env, jobject self, POINT pt);
        static void call_dSCddfinished(JNIEnv* env, jobject self,
                                       jboolean success, jint operations,
                                       POINT pt);
    protected:

        class ADSIEnumFormatEtc : public virtual IEnumFORMATETC {
            public:
                ADSIEnumFormatEtc(AwtDragSource* parent);

                virtual ~ADSIEnumFormatEtc();

                // IUnknown

                virtual HRESULT QueryInterface(REFIID riid, void __RPC_FAR *__RPC_FAR *ppvObject);

                virtual ULONG   AddRef(void);
                virtual ULONG   Release(void);

                // IEnumFORMATETC

                virtual HRESULT Next(ULONG celt, FORMATETC __RPC_FAR *rgelt, ULONG __RPC_FAR *pceltFetched);
                virtual HRESULT Skip(ULONG celt);
                virtual HRESULT Reset();
                virtual HRESULT Clone(IEnumFORMATETC __RPC_FAR *__RPC_FAR *ppenum);

            private:
                AwtDragSource*  m_parent;
                ULONG           m_refs;

                unsigned int    m_idx;
        };

        class ADSIStreamProxy : public virtual IStream {
            private:
                ADSIStreamProxy(ADSIStreamProxy* cloneof);

            public:
                ADSIStreamProxy(AwtDragSource* parent, jbyteArray buffer, jint len);

                virtual ~ADSIStreamProxy();

                // IUnknown

                virtual HRESULT QueryInterface(REFIID riid, void __RPC_FAR *__RPC_FAR *ppvObject);

                virtual ULONG   AddRef(void);
                virtual ULONG   Release(void);

                // IStream


                virtual  HRESULT Read(void __RPC_FAR *pv, ULONG cb, ULONG __RPC_FAR *pcbRead);

                virtual  HRESULT Write(const void __RPC_FAR *pv, ULONG cb, ULONG __RPC_FAR *pcbWritten);

                virtual  HRESULT Seek(LARGE_INTEGER dlibMove, DWORD dwOrigin, ULARGE_INTEGER __RPC_FAR *plibNewPosition);

                virtual HRESULT SetSize(ULARGE_INTEGER libNewSize);

                virtual  HRESULT CopyTo(IStream __RPC_FAR *pstm, ULARGE_INTEGER cb, ULARGE_INTEGER __RPC_FAR *pcbRead, ULARGE_INTEGER __RPC_FAR *pcbWritten);

                virtual HRESULT Commit(DWORD grfCommitFlags);

                virtual HRESULT Revert();

                virtual HRESULT LockRegion(ULARGE_INTEGER libOffset, ULARGE_INTEGER cb, DWORD dwLockType);

                virtual HRESULT UnlockRegion(ULARGE_INTEGER libOffset, ULARGE_INTEGER cb, DWORD dwLockType);

                virtual HRESULT Stat(STATSTG __RPC_FAR *pstatstg, DWORD grfStatFlag);

                virtual HRESULT Clone(IStream __RPC_FAR *__RPC_FAR *ppstm);
            protected:
                AwtDragSource*   m_parent;

                signed   char*   m_buffer;
                unsigned int     m_off;
                unsigned int     m_blen;

                STATSTG          m_statstg;

                ADSIStreamProxy* m_cloneof;

                ULONG            m_refs;
        };

    public:
        static const UINT PROCESS_ID_FORMAT;

    private:

        // instance vars ...

        jobject         m_peer;

        jint            m_initmods;
        jint            m_lastmods;

        HWND            m_droptarget;
        int             m_enterpending;

        jint            m_actions;

        FORMATETC*      m_types;
        unsigned int    m_ntypes;

        ULONG           m_refs;

        AwtCursor*      m_cursor;

        HANDLE          m_mutex;

        jobject         m_component;
        jobject         m_transferable;
        jobject         m_formatMap;

        POINT           m_dragPoint; // device space (pixels)
        POINT           m_dropPoint; // device space (pixels)
        BOOL            m_fNC;
        BOOL            m_bRestoreNodropCustomCursor;//CR 6480706 - MS Bug on hold

        DWORD           m_dwPerformedDropEffect;

        // static's ...

        static jclass           dSCClazz;

        static jmethodID        dSCdragenter;
        static jmethodID        dSCdragmotion;
        static jmethodID        dSCopschanged;
        static jmethodID        dSCdragexit;
        static jmethodID        dSCddfinish;

        static jfieldID         awtIEmods;
};

extern const CLIPFORMAT CF_PERFORMEDDROPEFFECT;
extern const CLIPFORMAT CF_FILEGROUPDESCRIPTORA;
extern const CLIPFORMAT CF_FILEGROUPDESCRIPTORW;
extern const CLIPFORMAT CF_FILECONTENTS;

#endif /* AWT_DND_DS_H */
