/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 */

#include "awt.h"
#include "awt_DataTransferer.h"
#include "awt_DnDDT.h"
#include "awt_DnDDS.h"
#include "awt_Toolkit.h"
#include "java_awt_dnd_DnDConstants.h"
#include "sun_awt_windows_WDropTargetContextPeer.h"
#include "awt_Container.h"

#include <memory.h>
#include <shellapi.h>

// forwards

extern "C" {
DWORD __cdecl convertActionsToDROPEFFECT(jint actions);
jint  __cdecl convertDROPEFFECTToActions(DWORD effects);

DWORD __cdecl mapModsToDROPEFFECT(DWORD, DWORD);
} // extern "C"


IDataObject* AwtDropTarget::sm_pCurrentDnDDataObject = (IDataObject*)NULL;

/**
 * constructor
 */

AwtDropTarget::AwtDropTarget(JNIEnv* env, AwtComponent* component) {

    m_component     = component;
    m_window        = component->GetHWnd();
    m_refs          = 1U;
    m_target        = env->NewGlobalRef(component->GetTarget(env));
    m_registered    = 0;
    m_dataObject    = NULL;
    m_formats       = NULL;
    m_nformats      = 0;
    m_dtcp          = NULL;
    m_cfFormats     = NULL;
    m_mutex         = ::CreateMutex(NULL, FALSE, NULL);
}

/**
 * destructor
 */

AwtDropTarget::~AwtDropTarget() {
    JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    // fix for 6212440: on application shutdown, this object's
    // destruction might be suppressed due to dangling COM references.
    // On destruction, VM might be shut down already, so we should make
    // a null check on env.
    if (env) {
        env->DeleteGlobalRef(m_target);
        env->DeleteGlobalRef(m_dtcp);
    }

    ::CloseHandle(m_mutex);

    UnloadCache();
}

/**
 * QueryInterface
 */

HRESULT __stdcall AwtDropTarget::QueryInterface(REFIID riid, void __RPC_FAR *__RPC_FAR *ppvObject) {
    if ( IID_IUnknown == riid ||
         IID_IDropTarget == riid )
    {
        *ppvObject = static_cast<IDropTarget*>(this);
        AddRef();
        return S_OK;
    }
    *ppvObject = NULL;
    return E_NOINTERFACE;
}

/**
 * AddRef
 */

ULONG __stdcall AwtDropTarget::AddRef() {
    return (ULONG)++m_refs;
}

/**
 * Release
 */

ULONG __stdcall AwtDropTarget::Release() {
    int refs;

    if ((refs = --m_refs) == 0) delete this;

    return (ULONG)refs;
}

/**
 * DragEnter
 */

HRESULT __stdcall AwtDropTarget::DragEnter(IDataObject __RPC_FAR *pDataObj, DWORD grfKeyState, POINTL pt, DWORD __RPC_FAR *pdwEffect) {
    TRY;

    AwtInterfaceLocker _lk(this);

    JNIEnv*    env       = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    HRESULT    ret       = S_OK;
    DWORD      retEffect = DROPEFFECT_NONE;
    jobject    dtcp = NULL;

    if ( (!IsLocalDnD() && !IsCurrentDnDDataObject(NULL)) ||
        (IsLocalDnD()  && !IsLocalDataObject(pDataObj)))
    {
        *pdwEffect = retEffect;
        return ret;
    }

    dtcp = call_dTCcreate(env);
    if (dtcp) {
        env->DeleteGlobalRef(m_dtcp);
        m_dtcp = env->NewGlobalRef(dtcp);
        env->DeleteLocalRef(dtcp);
    }

    if (JNU_IsNull(env, m_dtcp) || !JNU_IsNull(env, safe_ExceptionOccurred(env))) {
        return ret;
    }

    LoadCache(pDataObj);

    {
        POINT cp;
        RECT  wr;

        ::GetWindowRect(m_window, &wr);

        cp.x = pt.x - wr.left;
        cp.y = pt.y - wr.top;

        jint actions = call_dTCenter(env, m_dtcp, m_target,
                                     (jint)cp.x, (jint)cp.y,
                                     ::convertDROPEFFECTToActions(mapModsToDROPEFFECT(*pdwEffect, grfKeyState)),
                                     ::convertDROPEFFECTToActions(*pdwEffect),
                                     m_cfFormats, (jlong)this);

        try {
            if (!JNU_IsNull(env, safe_ExceptionOccurred(env))) {
                env->ExceptionDescribe();
                env->ExceptionClear();
                actions = java_awt_dnd_DnDConstants_ACTION_NONE;
            }
        } catch (std::bad_alloc&) {
            retEffect = ::convertActionsToDROPEFFECT(actions);
            *pdwEffect = retEffect;
            throw;
        }

        retEffect = ::convertActionsToDROPEFFECT(actions);
    }

    *pdwEffect = retEffect;

    return ret;

    CATCH_BAD_ALLOC_RET(E_OUTOFMEMORY);
}

/**
 * DragOver
 */

HRESULT __stdcall AwtDropTarget::DragOver(DWORD grfKeyState, POINTL pt, DWORD __RPC_FAR *pdwEffect) {
    TRY;

    AwtInterfaceLocker _lk(this);

    JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    HRESULT ret = S_OK;
    POINT   cp;
    RECT    wr;
    jint    actions;

    if ( (!IsLocalDnD() && !IsCurrentDnDDataObject(m_dataObject)) ||
        (IsLocalDnD()  && !IsLocalDataObject(m_dataObject)))
    {
        *pdwEffect = DROPEFFECT_NONE;
        return ret;
    }

    ::GetWindowRect(m_window, &wr);

    cp.x = pt.x - wr.left;
    cp.y = pt.y - wr.top;

    actions = call_dTCmotion(env, m_dtcp, m_target,(jint)cp.x, (jint)cp.y,
                             ::convertDROPEFFECTToActions(mapModsToDROPEFFECT(*pdwEffect, grfKeyState)),
                             ::convertDROPEFFECTToActions(*pdwEffect),
                             m_cfFormats, (jlong)this);

    try {
        if (!JNU_IsNull(env, safe_ExceptionOccurred(env))) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            actions = java_awt_dnd_DnDConstants_ACTION_NONE;
        }
    } catch (std::bad_alloc&) {
        *pdwEffect = ::convertActionsToDROPEFFECT(actions);
        throw;
    }

    *pdwEffect = ::convertActionsToDROPEFFECT(actions);

    return ret;

    CATCH_BAD_ALLOC_RET(E_OUTOFMEMORY);
}

/**
 * DragLeave
 */

HRESULT __stdcall AwtDropTarget::DragLeave() {
    TRY_NO_VERIFY;

    AwtInterfaceLocker _lk(this);

    JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    HRESULT ret = S_OK;

    if ( (!IsLocalDnD() && !IsCurrentDnDDataObject(m_dataObject)) ||
        (IsLocalDnD()  && !IsLocalDataObject(m_dataObject)))
    {
        DragCleanup();
        return ret;
    }

    call_dTCexit(env, m_dtcp, m_target, (jlong)this);

    try {
        if (!JNU_IsNull(env, safe_ExceptionOccurred(env))) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
    } catch (std::bad_alloc&) {
        DragCleanup();
        throw;
    }

    DragCleanup();

    return ret;

    CATCH_BAD_ALLOC_RET(E_OUTOFMEMORY);
}

/**
 * Drop
 */

HRESULT __stdcall AwtDropTarget::Drop(IDataObject __RPC_FAR *pDataObj, DWORD grfKeyState, POINTL pt, DWORD __RPC_FAR *pdwEffect) {
    TRY;

    AwtInterfaceLocker _lk(this);

    JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    HRESULT ret = S_OK;
    POINT   cp;
    RECT    wr;

    if ( (!IsLocalDnD() && !IsCurrentDnDDataObject(pDataObj)) ||
        (IsLocalDnD()  && !IsLocalDataObject(pDataObj)))
    {
        *pdwEffect = DROPEFFECT_NONE;
        DragCleanup();
        return ret;
    }

    LoadCache(pDataObj);

    ::GetWindowRect(m_window, &wr);

    cp.x = pt.x - wr.left;
    cp.y = pt.y - wr.top;

    m_dropActions = java_awt_dnd_DnDConstants_ACTION_NONE;

    call_dTCdrop(env, m_dtcp, m_target, (jint)cp.x, (jint)cp.y,
                 ::convertDROPEFFECTToActions(mapModsToDROPEFFECT(*pdwEffect, grfKeyState)),
                 ::convertDROPEFFECTToActions(*pdwEffect),
                 m_cfFormats, (jlong)this);

    try {
        if (!JNU_IsNull(env, safe_ExceptionOccurred(env))) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            ret = E_FAIL;
        }
    } catch (std::bad_alloc&) {
        AwtToolkit::GetInstance().MessageLoop(AwtToolkit::SecondaryIdleFunc,
                                              AwtToolkit::CommonPeekMessageFunc);
        *pdwEffect = ::convertActionsToDROPEFFECT(m_dropActions);
        DragCleanup();
        throw;
    }

    /*
     * Fix for 4623377.
     * Dispatch all messages in the nested message loop running while the drop is
     * processed. This ensures that the modal dialog shown during drop receives
     * all events and so it is able to close. This way the app won't deadlock.
     */
    AwtToolkit::GetInstance().MessageLoop(AwtToolkit::SecondaryIdleFunc,
                                          AwtToolkit::CommonPeekMessageFunc);

    ret = (m_dropSuccess == JNI_TRUE) ? S_OK : E_FAIL;
    *pdwEffect = ::convertActionsToDROPEFFECT(m_dropActions);

    DragCleanup();

    return ret;

    CATCH_BAD_ALLOC_RET(E_OUTOFMEMORY);
}

/**
 * DoDropDone
 */

void AwtDropTarget::DoDropDone(jboolean success, jint action) {
    DropDoneRec ddr = { this, success, action };

    AwtToolkit::GetInstance().InvokeFunction(_DropDone, &ddr);
}

/**
 * _DropDone
 */

void AwtDropTarget::_DropDone(void* param) {
    DropDonePtr ddrp = (DropDonePtr)param;

    (ddrp->dropTarget)->DropDone(ddrp->success, ddrp->action);
}

/**
 * DropDone
 */

void AwtDropTarget::DropDone(jboolean success, jint action) {
    m_dropSuccess = success;
    m_dropActions = action;
    AwtToolkit::GetInstance().QuitMessageLoop(AwtToolkit::EXIT_ENCLOSING_LOOP);
}

/**
 * DoRegisterTarget
 */

void AwtDropTarget::_RegisterTarget(void* param) {
    RegisterTargetPtr rtrp = (RegisterTargetPtr)param;

    rtrp->dropTarget->RegisterTarget(rtrp->show);
}

/**
 * RegisterTarget
 */

void AwtDropTarget::RegisterTarget(WORD show) {
    JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    HRESULT res;

    if (!AwtToolkit::IsMainThread()) {
        RegisterTargetRec rtr = { this, show };

        AwtToolkit::GetInstance().InvokeFunction(_RegisterTarget, &rtr);

        return;
    }

    // if we are'nt yet visible, defer until the parent is!

    if (show) {
        res = ::RegisterDragDrop(m_window, (IDropTarget*)this);
    } else {
        res = ::RevokeDragDrop(m_window);
    }

    if (res == S_OK) m_registered = show;
}

/**
 * DoGetData
 */

jobject AwtDropTarget::DoGetData(jlong format) {
    jobject    ret = (jobject)NULL;
    GetDataRec gdr = { this, format, &ret };

    AwtToolkit::GetInstance().WaitForSingleObject(m_mutex);

    AwtToolkit::GetInstance().InvokeFunctionLater(_GetData, &gdr);

    WaitUntilSignalled(FALSE);

    return ret;
}

/**
 * _GetData
 */

void AwtDropTarget::_GetData(void* param) {
    GetDataPtr gdrp = (GetDataPtr)param;

    *(gdrp->ret) = gdrp->dropTarget->GetData(gdrp->format);

    gdrp->dropTarget->Signal();
}


/**
 * GetData
 *
 * Returns the data object being transferred.
 */

jobject AwtDropTarget::GetData(jlong fmt) {
    JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    if (env->EnsureLocalCapacity(1) < 0) {
        return (jobject)NULL;
    }

    FORMATETC format = { (unsigned short)fmt };
    STGMEDIUM stgmedium;
    HRESULT hResult = E_FAIL;

    static const DWORD supportedTymeds[] = { TYMED_ISTREAM, TYMED_ENHMF,
                                             TYMED_GDI, TYMED_MFPICT,
                                             TYMED_FILE, TYMED_HGLOBAL };
    static const int nSupportedTymeds = 6;

    for (int i = 0; i < nSupportedTymeds; i++) {

        // Only TYMED_HGLOBAL is supported for CF_LOCALE.
        if (fmt == CF_LOCALE && supportedTymeds[i] != TYMED_HGLOBAL) {
            continue;
        }

        format.tymed = supportedTymeds[i];

        FORMATETC *cpp = (FORMATETC *)bsearch((const void *)&format,
                                              (const void *)m_formats,
                                              (size_t)      m_nformats,
                                              (size_t)      sizeof(FORMATETC),
                                              _compar
                                              );

        if (cpp == (FORMATETC *)NULL) {
            continue;
        }

        memcpy(&format, cpp, sizeof(FORMATETC));

        hResult = m_dataObject->GetData(&format, &stgmedium);

        if (hResult == S_OK) {
            break;
        }
    }

    // Failed to retrieve data.
    if (hResult != S_OK) {
        return (jobject)NULL;
    }

    jobject ret = NULL;
    jbyteArray paletteDataLocal = NULL;

    switch (stgmedium.tymed) {
        case TYMED_HGLOBAL: {
            if (fmt == CF_LOCALE) {
                LCID *lcid = (LCID *)::GlobalLock(stgmedium.hGlobal);
                if (lcid == NULL) {
                    ::ReleaseStgMedium(&stgmedium);
                    return NULL;
                }
                try {
                    ret = AwtDataTransferer::LCIDToTextEncoding(env, *lcid);
                } catch (...) {
                    ::GlobalUnlock(stgmedium.hGlobal);
                    ::ReleaseStgMedium(&stgmedium);
                    throw;
                }
                ::GlobalUnlock(stgmedium.hGlobal);
                ::ReleaseStgMedium(&stgmedium);
            } else {
                ::SetLastError(0); // clear error
                // Warning C4244.
                // Cast SIZE_T (__int64 on 64-bit/unsigned int on 32-bit)
                // to jsize (long).
                SIZE_T globalSize = ::GlobalSize(stgmedium.hGlobal);
                jsize size = (globalSize <= INT_MAX) ? (jsize)globalSize : INT_MAX;
                if (size == 0 && ::GetLastError() != 0) {
                    ::SetLastError(0); // clear error
                    ::ReleaseStgMedium(&stgmedium);
                    return (jobject)NULL; // failed
                }

                jbyteArray bytes = env->NewByteArray(size);
                if (bytes == NULL) {
                    ::ReleaseStgMedium(&stgmedium);
                    throw std::bad_alloc();
                }

                LPVOID data = ::GlobalLock(stgmedium.hGlobal);
                env->SetByteArrayRegion(bytes, 0, size, (jbyte *)data);
                ::GlobalUnlock(stgmedium.hGlobal);
                ::ReleaseStgMedium(&stgmedium);

                ret = bytes;
            }
            break;
        }
        case TYMED_FILE: {
            jobject local = JNU_NewStringPlatform(env, stgmedium.lpszFileName);
            jstring fileName = (jstring)env->NewGlobalRef(local);
            env->DeleteLocalRef(local);

            STGMEDIUM *stgm = NULL;
            try {
                stgm = (STGMEDIUM *)safe_Malloc(sizeof(STGMEDIUM));
            } catch (std::bad_alloc&) {
                env->DeleteGlobalRef(fileName);
                ::ReleaseStgMedium(&stgmedium);
                throw;
            }
            memcpy(stgm, &stgmedium, sizeof(STGMEDIUM));

            // Warning C4311.
            // Cast pointer to jlong (__int64).
            ret = call_dTCgetfs(env, fileName, (jlong)stgm);
            try {
                if (JNU_IsNull(env, ret) ||
                        !JNU_IsNull(env, safe_ExceptionOccurred(env))) {
                    env->DeleteGlobalRef(fileName);
                    free((void*)stgm);
                    ::ReleaseStgMedium(&stgmedium);
                    return (jobject)NULL;
                }
            } catch (std::bad_alloc&) {
                env->DeleteGlobalRef(fileName);
                free((void*)stgm);
                ::ReleaseStgMedium(&stgmedium);
                throw;
            }
            break;
        }

        case TYMED_ISTREAM: {
           WDTCPIStreamWrapper* istream = new WDTCPIStreamWrapper(&stgmedium);

           // Warning C4311.
           // Cast pointer to jlong (__int64).
           ret = call_dTCgetis(env, (jlong)istream);
           try {
               if (JNU_IsNull(env, ret) ||
                       !JNU_IsNull(env, safe_ExceptionOccurred(env))) {
                   istream->Close();
                   return (jobject)NULL;
               }
           } catch (std::bad_alloc&) {
               istream->Close();
               throw;
           }
           break;
        }

        case TYMED_GDI:
            // Currently support only CF_PALETTE for TYMED_GDI.
            switch (fmt) {
            case CF_PALETTE: {
                ret = AwtDataTransferer::GetPaletteBytes(stgmedium.hBitmap,
                                                         0, TRUE);
                break;
            }
            }
            ::ReleaseStgMedium(&stgmedium);
            break;
        case TYMED_MFPICT:
        case TYMED_ENHMF: {
            HENHMETAFILE hEnhMetaFile = NULL;
            LPBYTE lpEmfBits = NULL;

            if (stgmedium.tymed == TYMED_MFPICT) {
                LPMETAFILEPICT lpMetaFilePict =
                    (LPMETAFILEPICT)::GlobalLock(stgmedium.hMetaFilePict);
                UINT uSize = ::GetMetaFileBitsEx(lpMetaFilePict->hMF, 0, NULL);
                DASSERT(uSize != 0);

                try {
                    LPBYTE lpMfBits = (LPBYTE)safe_Malloc(uSize);
                    VERIFY(::GetMetaFileBitsEx(lpMetaFilePict->hMF, uSize,
                                               lpMfBits) == uSize);
                    hEnhMetaFile = ::SetWinMetaFileBits(uSize,
                                                        lpMfBits,
                                                        NULL,
                                                        lpMetaFilePict);
                    free(lpMfBits);
                } catch (...) {
                    ::GlobalUnlock(stgmedium.hMetaFilePict);
                    throw;
                }
                ::GlobalUnlock(stgmedium.hMetaFilePict);
            } else {
                hEnhMetaFile = stgmedium.hEnhMetaFile;
            }

            try {
                paletteDataLocal =
                    AwtDataTransferer::GetPaletteBytes(hEnhMetaFile,
                                                       OBJ_ENHMETAFILE,
                                                       FALSE);

                UINT uEmfSize = ::GetEnhMetaFileBits(hEnhMetaFile, 0, NULL);
                DASSERT(uEmfSize != 0);

                lpEmfBits = (LPBYTE)safe_Malloc(uEmfSize);
                VERIFY(::GetEnhMetaFileBits(hEnhMetaFile, uEmfSize,
                                            lpEmfBits) == uEmfSize);

                if (stgmedium.tymed == TYMED_MFPICT) {
                    ::DeleteEnhMetaFile(hEnhMetaFile);
                } else {
                    ::ReleaseStgMedium(&stgmedium);
                }
                hEnhMetaFile = NULL;

                jbyteArray bytes = env->NewByteArray(uEmfSize);
                if (bytes == NULL) {
                    throw std::bad_alloc();
                }

                env->SetByteArrayRegion(bytes, 0, uEmfSize, (jbyte*)lpEmfBits);
                free(lpEmfBits);
                lpEmfBits = NULL;

                ret = bytes;
            } catch (...) {
                if (!JNU_IsNull(env, paletteDataLocal)) {
                    env->DeleteLocalRef(paletteDataLocal);
                    paletteDataLocal = NULL;
                }
                if (hEnhMetaFile != NULL) {
                    if (stgmedium.tymed == TYMED_MFPICT) {
                        ::DeleteEnhMetaFile(hEnhMetaFile);
                    } else {
                        ::ReleaseStgMedium(&stgmedium);
                    }
                    hEnhMetaFile = NULL;
                }
                if (lpEmfBits != NULL) {
                    free(lpEmfBits);
                    lpEmfBits = NULL;
                }
                throw;
            }
            break;
        }
        case TYMED_ISTORAGE:
        default:
            ::ReleaseStgMedium(&stgmedium);
            return (jobject)NULL;
    }

    if (ret == NULL) {
        return (jobject)NULL;
    }

    switch (fmt) {
    case CF_METAFILEPICT:
    case CF_ENHMETAFILE:
        // If we failed to retrieve palette entries from metafile,
        // fall through and try CF_PALETTE format.
    case CF_DIB: {
        if (JNU_IsNull(env, paletteDataLocal)) {
            jobject paletteData = GetData(CF_PALETTE);

            if (JNU_IsNull(env, paletteData)) {
                paletteDataLocal =
                    AwtDataTransferer::GetPaletteBytes(NULL, 0, TRUE);
            } else {
                // GetData() returns a global ref.
                // We want to deal with local ref.
                paletteDataLocal = (jbyteArray)env->NewLocalRef(paletteData);
                env->DeleteGlobalRef(paletteData);
            }
        }
        DASSERT(!JNU_IsNull(env, paletteDataLocal) &&
                !JNU_IsNull(env, ret));

        jobject concat = AwtDataTransferer::ConcatData(env, paletteDataLocal, ret);

        if (!JNU_IsNull(env, safe_ExceptionOccurred(env))) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            env->DeleteLocalRef(ret);
            env->DeleteLocalRef(paletteDataLocal);
            return (jobject)NULL;
        }

        env->DeleteLocalRef(ret);
        env->DeleteLocalRef(paletteDataLocal);
        ret = concat;

        break;
    }
    }

    jobject global = env->NewGlobalRef(ret);
    env->DeleteLocalRef(ret);
    return global;
}

/**
 *
 */

int __cdecl AwtDropTarget::_compar(const void* first, const void* second) {
    FORMATETC *fp = (FORMATETC *)first;
    FORMATETC *sp = (FORMATETC *)second;

    if (fp->cfFormat == sp->cfFormat) {
        return fp->tymed - sp->tymed;
    }

    return fp->cfFormat - sp->cfFormat;
}

const unsigned int AwtDropTarget::CACHE_INCR = 16;

void AwtDropTarget::LoadCache(IDataObject* pDataObj) {
    JNIEnv*      env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    unsigned int cnt = 0;
    HRESULT      res;
    IEnumFORMATETC* pEnumFormatEtc = NULL;

    if (m_dataObject != (IDataObject*)NULL) UnloadCache();

    if (!IsLocalDnD()) {
        SetCurrentDnDDataObject(pDataObj);
    }

    (m_dataObject = pDataObj)->AddRef();

    res = m_dataObject->EnumFormatEtc(DATADIR_GET, &pEnumFormatEtc);

    if (res == S_OK) {
    for (;;) {

        FORMATETC tmp;
        ULONG     actual = 1;

            res = pEnumFormatEtc->Next((ULONG)1, &tmp, &actual);

        if (res == S_FALSE) break;

        if (!(tmp.cfFormat  >= 1                &&
              tmp.ptd       == NULL             &&
              tmp.lindex    == -1               &&
              tmp.dwAspect  == DVASPECT_CONTENT &&
              (tmp.tymed    == TYMED_HGLOBAL    ||
               tmp.tymed    == TYMED_FILE       ||
               tmp.tymed    == TYMED_ISTREAM    ||
               tmp.tymed    == TYMED_GDI        ||
               tmp.tymed    == TYMED_MFPICT     ||
               tmp.tymed    == TYMED_ENHMF
              ) // but not ISTORAGE
             )
        ) continue;

        if (m_dataObject->QueryGetData(&tmp) != S_OK) continue;

        if (m_nformats % CACHE_INCR == 0) {
            m_formats = (FORMATETC *)safe_Realloc(m_formats,
                                                  (CACHE_INCR + m_nformats) *
                                                  sizeof(FORMATETC));
        }

        memcpy(m_formats + m_nformats, &tmp, sizeof(FORMATETC));

        m_nformats++;
    }

        // We are responsible for releasing the enumerator.
        pEnumFormatEtc->Release();
    }

    if (m_nformats > 0) {
        qsort((void*)m_formats, m_nformats, sizeof(FORMATETC),
              AwtDropTarget::_compar);
    }

    if (m_cfFormats != NULL) {
        env->DeleteGlobalRef(m_cfFormats);
    }
    jlongArray l_cfFormats = env->NewLongArray(m_nformats);
    if (l_cfFormats == NULL) {
        throw std::bad_alloc();
    }
    m_cfFormats = (jlongArray)env->NewGlobalRef(l_cfFormats);
    env->DeleteLocalRef(l_cfFormats);

    jboolean isCopy;
    jlong *lcfFormats = env->GetLongArrayElements(m_cfFormats, &isCopy),
        *saveFormats = lcfFormats;

    for (unsigned int i = 0; i < m_nformats; i++, lcfFormats++) {
        *lcfFormats = m_formats[i].cfFormat;
    }

    env->ReleaseLongArrayElements(m_cfFormats, saveFormats, 0);
}

/**
 * UnloadCache
 */

void AwtDropTarget::UnloadCache() {
    if (m_dataObject == (IDataObject*)NULL) return;

    JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    free((void*)m_formats);
    m_formats  = (FORMATETC *)NULL;
    m_nformats = 0;

    // fix for 6212440: on application shutdown, this object's
    // destruction might be suppressed due to dangling COM references.
    // This method is called from the destructor.
    // On destruction, VM might be shut down already, so we should make
    // a null check on env.
    if (env) {
        env->DeleteGlobalRef(m_cfFormats);
    }
    m_cfFormats = NULL;

    if (!IsLocalDnD()) {
        DASSERT(IsCurrentDnDDataObject(m_dataObject));
        SetCurrentDnDDataObject(NULL);
    }

    m_dataObject->Release();
    m_dataObject = (IDataObject*)NULL;
}

/**
 * DragCleanup
 */

void AwtDropTarget::DragCleanup(void) {
    UnloadCache();
}

BOOL AwtDropTarget::IsLocalDataObject(IDataObject __RPC_FAR *pDataObject) {
    BOOL local = FALSE;

    if (pDataObject != NULL) {
        FORMATETC format;
        STGMEDIUM stgmedium;

        format.cfFormat = AwtDragSource::PROCESS_ID_FORMAT;
        format.ptd      = NULL;
        format.dwAspect = DVASPECT_CONTENT;
        format.lindex   = -1;
        format.tymed    = TYMED_HGLOBAL;

        if (pDataObject->GetData(&format, &stgmedium) == S_OK) {
            ::SetLastError(0); // clear error
            // Warning C4244.
            SIZE_T size = ::GlobalSize(stgmedium.hGlobal);
            if (size < sizeof(DWORD) || ::GetLastError() != 0) {
                ::SetLastError(0); // clear error
            } else {

                DWORD id = ::CoGetCurrentProcess();

                LPVOID data = ::GlobalLock(stgmedium.hGlobal);
                if (memcmp(data, &id, sizeof(id)) == 0) {
                    local = TRUE;
                }
                ::GlobalUnlock(stgmedium.hGlobal);
            }
            ::ReleaseStgMedium(&stgmedium);
        }
    }

    return local;
}

DECLARE_JAVA_CLASS(dTCClazz, "sun/awt/windows/WDropTargetContextPeer")

jobject
AwtDropTarget::call_dTCcreate(JNIEnv* env) {
    DECLARE_STATIC_OBJECT_JAVA_METHOD(dTCcreate, dTCClazz,
                                      "getWDropTargetContextPeer",
                                      "()Lsun/awt/windows/WDropTargetContextPeer;");
    return env->CallStaticObjectMethod(clazz, dTCcreate);
}

jint
AwtDropTarget::call_dTCenter(JNIEnv* env, jobject self, jobject component, jint x, jint y,
              jint dropAction, jint actions, jlongArray formats,
              jlong nativeCtxt) {
    DECLARE_JINT_JAVA_METHOD(dTCenter, dTCClazz, "handleEnterMessage",
                            "(Ljava/awt/Component;IIII[JJ)I");
    DASSERT(!JNU_IsNull(env, self));
    return env->CallIntMethod(self, dTCenter, component, x, y, dropAction,
                              actions, formats, nativeCtxt);
}

void
AwtDropTarget::call_dTCexit(JNIEnv* env, jobject self, jobject component, jlong nativeCtxt) {
    DECLARE_VOID_JAVA_METHOD(dTCexit, dTCClazz, "handleExitMessage",
                            "(Ljava/awt/Component;J)V");
    DASSERT(!JNU_IsNull(env, self));
    env->CallVoidMethod(self, dTCexit, component, nativeCtxt);
}

jint
AwtDropTarget::call_dTCmotion(JNIEnv* env, jobject self, jobject component, jint x, jint y,
               jint dropAction, jint actions, jlongArray formats,
               jlong nativeCtxt) {
    DECLARE_JINT_JAVA_METHOD(dTCmotion, dTCClazz, "handleMotionMessage",
                            "(Ljava/awt/Component;IIII[JJ)I");
    DASSERT(!JNU_IsNull(env, self));
    return env->CallIntMethod(self, dTCmotion, component, x, y,
                                 dropAction, actions, formats, nativeCtxt);
}

void
AwtDropTarget::call_dTCdrop(JNIEnv* env, jobject self, jobject component, jint x, jint y,
             jint dropAction, jint actions, jlongArray formats,
             jlong nativeCtxt) {
    DECLARE_VOID_JAVA_METHOD(dTCdrop, dTCClazz, "handleDropMessage",
                            "(Ljava/awt/Component;IIII[JJ)V");
    DASSERT(!JNU_IsNull(env, self));
    env->CallVoidMethod(self, dTCdrop, component, x, y,
                           dropAction, actions, formats, nativeCtxt);
}

jobject
AwtDropTarget::call_dTCgetfs(JNIEnv* env, jstring fileName, jlong stgmedium) {
    DECLARE_STATIC_OBJECT_JAVA_METHOD(dTCgetfs, dTCClazz, "getFileStream",
                                      "(Ljava/lang/String;J)Ljava/io/FileInputStream;");
    return env->CallStaticObjectMethod(clazz, dTCgetfs, fileName, stgmedium);
}

jobject
AwtDropTarget::call_dTCgetis(JNIEnv* env, jlong istream) {
    DECLARE_STATIC_OBJECT_JAVA_METHOD(dTCgetis, dTCClazz, "getIStream",
                                      "(J)Ljava/lang/Object;");
    return env->CallStaticObjectMethod(clazz, dTCgetis, istream);
}

/*****************************************************************************/

jclass WDTCPIStreamWrapper::javaIOExceptionClazz = (jclass)NULL;

/**
 * construct a wrapper
 */

WDTCPIStreamWrapper::WDTCPIStreamWrapper(STGMEDIUM* stgmedium) {
    JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    m_stgmedium = *stgmedium;
    m_istream   = stgmedium->pstm;
    m_mutex     = ::CreateMutex(NULL, FALSE, NULL);

    if (javaIOExceptionClazz == (jclass)NULL) {
        javaIOExceptionClazz = env->FindClass("java/io/IOException");

        if (JNU_IsNull(env, javaIOExceptionClazz)) {
            env->ThrowNew(env->FindClass("java/lang/ClassNotFoundException"),
                          "Cant find java/io/IOException"
            );
        }
    }
}

/**
 * destroy a wrapper
 */

WDTCPIStreamWrapper::~WDTCPIStreamWrapper() {
    ::CloseHandle(m_mutex);

    ::ReleaseStgMedium(&m_stgmedium);
}

/**
 * return available data
 */

jint WDTCPIStreamWrapper::DoAvailable(WDTCPIStreamWrapper* istream) {
    WDTCPIStreamWrapperRec iswr = { istream, 0 };

    AwtToolkit::GetInstance().WaitForSingleObject(istream->m_mutex);

    AwtToolkit::GetInstance().InvokeFunctionLater( _Available, &iswr);

    istream->WaitUntilSignalled(FALSE);

    return iswr.ret;
}

/**
 * return available data
 */

void WDTCPIStreamWrapper::_Available(void *param) {
    WDTCPIStreamWrapperPtr iswrp = (WDTCPIStreamWrapperPtr)param;

    iswrp->ret = (iswrp->istream)->Available();

    iswrp->istream->Signal();
}

/**
 * return available data
 */

jint WDTCPIStreamWrapper::Available() {
    JNIEnv* env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    if (m_istream->Stat(&m_statstg, STATFLAG_NONAME) != S_OK) {
        env->ThrowNew(javaIOExceptionClazz, "IStream::Stat() failed");
        return 0;
    }

    if (m_statstg.cbSize.QuadPart > 0x7ffffffL) {
        env->ThrowNew(javaIOExceptionClazz, "IStream::Stat() cbSize > 0x7ffffff");
        return 0;
    }

    return (jint)m_statstg.cbSize.LowPart;
}

/**
 * read 1 byte
 */

jint WDTCPIStreamWrapper::DoRead(WDTCPIStreamWrapper* istream) {
    WDTCPIStreamWrapperRec iswr = { istream, 0 };

    AwtToolkit::GetInstance().WaitForSingleObject(istream->m_mutex);

    AwtToolkit::GetInstance().InvokeFunctionLater(_Read, &iswr);

    istream->WaitUntilSignalled(FALSE);

    return iswr.ret;
}

/**
 * read 1 byte
 */

void WDTCPIStreamWrapper::_Read(void* param) {
    WDTCPIStreamWrapperPtr iswrp = (WDTCPIStreamWrapperPtr)param;

    iswrp->ret = (iswrp->istream)->Read();

    iswrp->istream->Signal();
}

/**
 * read 1 byte
 */

jint WDTCPIStreamWrapper::Read() {
    JNIEnv* env    = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jint    b      = 0;
    ULONG   actual = 0;
    HRESULT res;

    switch (res = m_istream->Read((void *)&b, (ULONG)1, &actual)) {
        case S_FALSE:
            return (jint)-1;

        case S_OK:
            return (jint)(actual == 0 ? -1 : b);

        default:
            env->ThrowNew(javaIOExceptionClazz, "IStream::Read failed");
    }
    return (jint)-1;
}

/**
 * read Buffer
 */

jint WDTCPIStreamWrapper::DoReadBytes(WDTCPIStreamWrapper* istream, jbyteArray array, jint off, jint len) {
    WDTCPIStreamWrapperReadBytesRec iswrbr = { istream, 0, array, off, len };

    AwtToolkit::GetInstance().WaitForSingleObject(istream->m_mutex);

    AwtToolkit::GetInstance().InvokeFunctionLater(_ReadBytes, &iswrbr);

    istream->WaitUntilSignalled(FALSE);

    return iswrbr.ret;
}

/**
 * read buffer
 */

void WDTCPIStreamWrapper::_ReadBytes(void*  param) {
    WDTCPIStreamWrapperReadBytesPtr iswrbrp =
        (WDTCPIStreamWrapperReadBytesPtr)param;

    iswrbrp->ret = (iswrbrp->istream)->ReadBytes(iswrbrp->array,
                                                 iswrbrp->off,
                                                 iswrbrp->len);
    iswrbrp->istream->Signal();
}

/**
 * read buffer
 */

jint WDTCPIStreamWrapper::ReadBytes(jbyteArray buf, jint off, jint len) {
    JNIEnv*  env     = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jboolean isCopy  = JNI_FALSE;
    ULONG    actual  = 0;
    jbyte*   local   = env->GetByteArrayElements(buf, &isCopy);
    HRESULT  res;

    switch (res = m_istream->Read((void *)(local + off), (ULONG)len, &actual)) {
        case S_FALSE:
        case S_OK: {
            int eof = (actual == 0);

            env->ReleaseByteArrayElements(buf, local, !eof ? 0 : JNI_ABORT);
            return (jint)(!eof ? actual : -1);
        }

        default:
            env->ReleaseByteArrayElements(buf, local, JNI_ABORT);
            env->ThrowNew(javaIOExceptionClazz, "IStream::Read failed");
    }

    return (jint)-1;
}

/**
 * close
 */

void WDTCPIStreamWrapper::DoClose(WDTCPIStreamWrapper* istream) {
    AwtToolkit::GetInstance().InvokeFunctionLater(_Close, istream);
}

/**
 * close
 */

void WDTCPIStreamWrapper::_Close(void* param) {
    ((WDTCPIStreamWrapper*)param)->Close();
}

/**
 * close
 */

void WDTCPIStreamWrapper::Close() {
    delete this;
}

/*****************************************************************************/

extern "C" {

/**
 * awt_dnd_initialize: initial DnD system
 */

void awt_dnd_initialize() {
    ::OleInitialize((LPVOID)NULL);
}

/**
 * awt_dnd_uninitialize: deactivate DnD system
 */

void awt_dnd_uninitialize() {
    ::OleUninitialize();
}

/**
 * convertActionsToDROPEFFECT
 */

DWORD convertActionsToDROPEFFECT(jint actions) {
    DWORD effects = DROPEFFECT_NONE;

    if (actions & java_awt_dnd_DnDConstants_ACTION_LINK) effects |= DROPEFFECT_LINK;
    if (actions & java_awt_dnd_DnDConstants_ACTION_MOVE) effects |= DROPEFFECT_MOVE;
    if (actions & java_awt_dnd_DnDConstants_ACTION_COPY) effects |= DROPEFFECT_COPY;
    return effects;
}

/**
 * convertDROPEFFECTToAction
 */

jint convertDROPEFFECTToActions(DWORD effects) {
    jint actions = java_awt_dnd_DnDConstants_ACTION_NONE;

    if (effects & DROPEFFECT_LINK) actions |= java_awt_dnd_DnDConstants_ACTION_LINK;
    if (effects & DROPEFFECT_MOVE) actions |= java_awt_dnd_DnDConstants_ACTION_MOVE;
    if (effects & DROPEFFECT_COPY) actions |= java_awt_dnd_DnDConstants_ACTION_COPY;

    return actions;
}

/**
 * map keyboard modifiers to a DROPEFFECT
 */

DWORD mapModsToDROPEFFECT(DWORD effects, DWORD mods) {
    DWORD ret = DROPEFFECT_NONE;

    /*
     * Fix for 4285634.
     * Calculate the drop action to match Motif DnD behavior.
     * If the user selects an operation (by pressing a modifier key),
     * return the selected operation or DROPEFFECT_NONE if the selected
     * operation is not supported by the drag source.
     * If the user doesn't select an operation search the set of operations
     * supported by the drag source for DROPEFFECT_MOVE, then for
     * DROPEFFECT_COPY, then for DROPEFFECT_LINK and return the first operation
     * found.
     */
    switch (mods & (MK_CONTROL | MK_SHIFT)) {
        case MK_CONTROL:
            ret = DROPEFFECT_COPY;
        break;

        case MK_CONTROL | MK_SHIFT:
            ret = DROPEFFECT_LINK;
        break;

        case MK_SHIFT:
            ret = DROPEFFECT_MOVE;
        break;

        default:
            if (effects & DROPEFFECT_MOVE) {
                ret = DROPEFFECT_MOVE;
            } else if (effects & DROPEFFECT_COPY) {
                ret = DROPEFFECT_COPY;
            } else if (effects & DROPEFFECT_LINK) {
                ret = DROPEFFECT_LINK;
            }
            break;
    }

    return ret & effects;
}

/**
 * downcall to fetch data ... gets scheduled on message thread
 */

JNIEXPORT jobject JNICALL Java_sun_awt_windows_WDropTargetContextPeer_getData(JNIEnv* env, jobject self, jlong dropTarget, jlong format) {
    TRY;

    AwtDropTarget* pDropTarget = (AwtDropTarget*)dropTarget;

    DASSERT(!::IsBadReadPtr(pDropTarget, sizeof(AwtDropTarget)));
    return pDropTarget->DoGetData(format);

    CATCH_BAD_ALLOC_RET(NULL);
}

/**
 * downcall to signal drop done ... gets scheduled on message thread
 */

JNIEXPORT void JNICALL
Java_sun_awt_windows_WDropTargetContextPeer_dropDone(JNIEnv* env, jobject self,
                             jlong dropTarget, jboolean success, jint actions) {
    TRY_NO_HANG;

    AwtDropTarget* pDropTarget = (AwtDropTarget*)dropTarget;

    DASSERT(!::IsBadReadPtr(pDropTarget, sizeof(AwtDropTarget)));
    pDropTarget->DoDropDone(success, actions);

    CATCH_BAD_ALLOC;
}

/**
 * downcall to free up storage medium for FileStream
 */

JNIEXPORT void JNICALL Java_sun_awt_windows_WDropTargetContextPeerFileStream_freeStgMedium(JNIEnv* env, jobject self, jlong stgmedium) {
    TRY;

    ::ReleaseStgMedium((STGMEDIUM*)stgmedium);

    free((void*)stgmedium);

    CATCH_BAD_ALLOC;
}

/**
 *
 */

JNIEXPORT jint JNICALL Java_sun_awt_windows_WDropTargetContextPeerIStream_Available(JNIEnv* env, jobject self, jlong istream) {
    TRY;

    return WDTCPIStreamWrapper::DoAvailable((WDTCPIStreamWrapper*)istream);

    CATCH_BAD_ALLOC_RET(0);
}

/**
 *
 */

JNIEXPORT jint JNICALL Java_sun_awt_windows_WDropTargetContextPeerIStream_Read(JNIEnv* env, jobject self, jlong istream) {
    TRY;

    return WDTCPIStreamWrapper::DoRead((WDTCPIStreamWrapper*)istream);

    CATCH_BAD_ALLOC_RET(0);
}

/**
 *
 */

JNIEXPORT jint JNICALL Java_sun_awt_windows_WDropTargetContextPeerIStream_ReadBytes(JNIEnv* env, jobject self, jlong istream, jbyteArray buf, jint off, jint len) {
    TRY;

    return WDTCPIStreamWrapper::DoReadBytes((WDTCPIStreamWrapper*)istream, buf, off, len);

    CATCH_BAD_ALLOC_RET(0);
}

/**
 *
 */

JNIEXPORT void JNICALL Java_sun_awt_windows_WDropTargetContextPeerIStream_Close(JNIEnv* env, jobject self, jlong istream) {
    TRY_NO_VERIFY;

    WDTCPIStreamWrapper::DoClose((WDTCPIStreamWrapper*)istream);

    CATCH_BAD_ALLOC;
}

} /* extern "C" */
