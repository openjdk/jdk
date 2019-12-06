#include <algorithm>
#include <windows.h>

#include "SysInfo.h"
#include "FileUtils.h"
#include "Executor.h"
#include "Resources.h"
#include "WinErrorHandling.h"


int __stdcall WinMain(HINSTANCE, HINSTANCE, LPSTR lpCmdLine, int nShowCmd)
{
    JP_TRY;

    // Create temporary directory where to extract msi file.
    const auto tempMsiDir = FileUtils::createTempDirectory();

    // Schedule temporary directory for deletion.
    FileUtils::Deleter cleaner;
    cleaner.appendRecursiveDirectory(tempMsiDir);

    const auto msiPath = FileUtils::mkpath() << tempMsiDir << L"main.msi";

    // Extract msi file.
    Resource(L"msi", RT_RCDATA).saveToFile(msiPath);

    // Setup executor to run msiexec
    Executor msiExecutor(SysInfo::getWIPath());
    msiExecutor.arg(L"/i").arg(msiPath);
    const auto args = SysInfo::getCommandArgs();
    std::for_each(args.begin(), args.end(),
            [&msiExecutor] (const tstring& arg) {
        msiExecutor.arg(arg);
    });

    // Install msi file.
    return msiExecutor.execAndWaitForExit();

    JP_CATCH_ALL;

    return -1;
}
