; 커피트럭 포스 - 윈도우 설치 파일 (Inno Setup 6)
; 먼저 build_windows.bat 를 실행해 dist\DdalPOS 폴더를 만든 뒤, 이 파일을 Inno Setup에서 열고 Build > Compile
#define AppName "커피트럭 포스"
#define AppVer "1.0"

[Setup]
AppId={{6E0B1F3C-5D2A-4C1E-9F7A-0DDA1F05A001}
AppName={#AppName}
AppVersion={#AppVer}
AppPublisher=커피트럭
DefaultDirName={autopf}\DdalPOS
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
OutputDir=installer
OutputBaseFilename=DdalPOS_Setup
SetupIconFile=icon.ico
UninstallDisplayIcon={app}\DdalPOS.exe
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "korean"; MessagesFile: "compiler:Languages\Korean.isl"

[Tasks]
Name: "desktopicon"; Description: "바탕화면에 아이콘 만들기"; GroupDescription: "추가 아이콘:"

[Files]
Source: "dist\DdalPOS\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\DdalPOS.exe"
Name: "{group}\포스 기록 달력"; Filename: "{app}\DdalPOS.exe"; Parameters: "--history"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\DdalPOS.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\DdalPOS.exe"; Description: "지금 실행"; Flags: nowait postinstall skipifsilent
