| Dialog | Control | Event | Argument | Condition | Ordering |
| --- | --- | --- | --- | --- | --- |
| ShortcutPromptDlg | Back | NewDialog | WelcomeDlg | NOT Installed | 6 |
| ShortcutPromptDlg | Cancel | SpawnDialog | CancelDlg | 1 | 1 |
| ShortcutPromptDlg | Next | NewDialog | VerifyReadyDlg | 1 | 1 |
| VerifyReadyDlg | Back | NewDialog | ShortcutPromptDlg | NOT Installed | 6 |
| WelcomeDlg | Next | NewDialog | ShortcutPromptDlg | NOT Installed | 6 |
