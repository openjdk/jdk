/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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


#include "MsiDb.h"
#include "MsiCA.h"
#include "Version.h"
#include "FileUtils.h"
#include "WinErrorHandling.h"


JP_CA(CheckInstallDir) {
    const tstring installDir = ca.getProperty(_T("INSTALLDIR"));

    bool canProceed = !FileUtils::isFileExists(installDir);
    if (!canProceed && FileUtils::isDirectory(installDir)) {
        canProceed = !FileUtils::isDirectoryNotEmpty(installDir);
    }

    ca.setProperty(_T("INSTALLDIR_VALID"), canProceed ? _T("1") : _T("0"));
}


namespace {

typedef Version<
    VersionDetails::Base<10, VersionDetails::Parser, 2>
> DottedVersion;


class ProductInfo {
public:
    explicit ProductInfo(const Guid& pc): productCode(pc),
            version(msi::getProductInfo(pc, INSTALLPROPERTY_VERSIONSTRING)) {
    }

    const DottedVersion& getVersion() const {
        return version;
    }

    const Guid& getProductCode() const {
        return productCode;
    }

private:
    Guid productCode;
    DottedVersion version;
};


void findInstalledPackages(const Guid& upgradeCode,
                                            std::vector<ProductInfo>& products) {
    const LPCTSTR upgradeCodeStr = upgradeCode.toMsiString().c_str();
    for (DWORD productCodeIdx = 0; true; ++productCodeIdx) {
        TCHAR productCode[39 /* http://msdn.microsoft.com/en-us/library/aa370101(v=vs.85).aspx */];
        const UINT status = MsiEnumRelatedProducts(upgradeCodeStr, 0,
                                              productCodeIdx++, productCode);
        if (ERROR_NO_MORE_ITEMS == status) {
            break;
        }

        if (ERROR_SUCCESS != status) {
            LOG_WARNING(tstrings::any()
                        << "MsiEnumRelatedProducts("
                        << upgradeCodeStr << ", "
                        << productCodeIdx
                        << ") failed with error=[" << status << "]");
        }

        JP_NO_THROW(products.push_back(ProductInfo(Guid(productCode))));
    }
}

DottedVersion getDottedVersion(const msi::DatabaseRecord& record, UINT idx) {
    if (!MsiRecordIsNull(record.getHandle(), idx)) {
        JP_NO_THROW(return DottedVersion(record.getString(idx)));
    }

    return DottedVersion();
}

} // namespace

JP_CA(FindRelatedProductsEx) {
    const Guid upgradeCode = Guid(ca.getProperty(_T("UpgradeCode")));

    std::vector<ProductInfo> installedProducts;
    findInstalledPackages(upgradeCode, installedProducts);

    const msi::Database db(ca);

    msi::DatabaseView view(db, (tstrings::any()
            << _T("SELECT `VersionMin`,`VersionMax`,`Language`,`Attributes`,`Remove`,`ActionProperty` FROM Upgrade WHERE `ActionProperty` NOT NULL And `UpgradeCode` = '")
            << upgradeCode.toMsiString() << _T("'")).tstr());
    msi::DatabaseRecord record;
    while (!record.tryFetch(view).empty()) {
        const DottedVersion versionMin = getDottedVersion(record, 1);
        const DottedVersion versionMax = getDottedVersion(record, 2);

        const int attrs = MsiRecordIsNull(
                          record.getHandle(), 4) ? 0 : record.getInteger(4);

        std::vector<ProductInfo>::const_iterator productIt =
                                                installedProducts.begin();
        std::vector<ProductInfo>::const_iterator productEnd =
                                                installedProducts.end();
        for (; productIt != productEnd; ++productIt) {
            bool match = false;
            if (!versionMin.source().empty()) {
                if (attrs & msidbUpgradeAttributesVersionMinInclusive) {
                    match = (versionMin <= productIt->getVersion());
                } else {
                    match = (versionMin < productIt->getVersion());
                }
            }

            if (!versionMax.source().empty()) {
                if (attrs & msidbUpgradeAttributesVersionMaxInclusive) {
                    match = (productIt->getVersion() <= versionMax);
                } else {
                    match = (productIt->getVersion() < versionMax);
                }
            }

            const tstring actionProperty = record.getString(6);
            if (match) {
                ca.setProperty(actionProperty,
                                productIt->getProductCode().toMsiString());
            } else {
                ca.removeProperty(actionProperty);
            }
        }
    }
}
