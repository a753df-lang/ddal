# -*- coding: utf-8 -*-
"""
커피트럭 포스 — PC 프로그램 (윈도우 / 맥)

포스 웹페이지(https://a753df-lang.github.io/ddal/)를 프로그램 창으로 띄웁니다.
 - 포스를 고치면 프로그램도 자동으로 최신 버전 (다시 설치할 필요 없음)
 - 로그인하면 아이패드·갤럭시탭·핸드폰과 같은 장부로 이어서 판매
 - 한 번 켜 두면 인터넷이 없어도 실행 (포스의 오프라인 기능 그대로)
 - 판매 기록·로그인은 이 PC의 사용자 폴더에 저장 (프로그램을 지웠다 깔아도 유지)

단축키:  F11 전체화면 켜기/끄기   F5 새로고침   F2 기록 달력 창   Ctrl+P 인쇄
"""
import os
import sys

from PyQt6.QtCore import QStandardPaths, QUrl, Qt
from PyQt6.QtGui import QIcon, QKeySequence, QShortcut
from PyQt6.QtPrintSupport import QPrintDialog, QPrinter
from PyQt6.QtWebEngineCore import (QWebEngineDownloadRequest, QWebEnginePage,
                                   QWebEngineProfile, QWebEngineSettings)
from PyQt6.QtWebEngineWidgets import QWebEngineView
from PyQt6.QtWidgets import QApplication, QFileDialog, QMainWindow, QMessageBox

APP_NAME = "커피트럭 포스"
BASE_URL = os.environ.get("DDALPOS_BASE", "https://a753df-lang.github.io/ddal/")
POS_URL = BASE_URL                      # index.html
HISTORY_URL = BASE_URL + "history.html"  # 기록 달력

OFFLINE_HTML = """<!doctype html><html><head><meta charset="utf-8"><style>
body{font-family:'Malgun Gothic','Apple SD Gothic Neo',sans-serif;background:#f1f3f5;display:flex;align-items:center;justify-content:center;height:100vh;margin:0}
.b{background:#fff;border-radius:14px;padding:30px 36px;box-shadow:0 2px 10px rgba(0,0,0,.15);text-align:center;max-width:460px}
h2{margin:0 0 10px} p{color:#444;line-height:1.6} a{display:inline-block;margin-top:14px;background:#2e7d32;color:#fff;padding:12px 26px;border-radius:8px;text-decoration:none;font-weight:bold}
</style></head><body><div class="b"><h2>☁ 인터넷 연결이 필요합니다</h2>
<p>처음 한 번은 인터넷에 연결된 상태에서 켜야 합니다.<br>한 번 열리고 나면 그 다음부터는 인터넷 없이도 열립니다.</p>
<a href="%s">다시 시도</a></div></body></html>"""


def resource_path(name):
    base = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(base, name)


def make_profile(app):
    # 판매 기록(localStorage)·로그인·오프라인 저장본을 지우지 않고 계속 쓰는 저장 공간
    data_dir = QStandardPaths.writableLocation(QStandardPaths.StandardLocation.AppDataLocation)
    root = os.path.join(data_dir or os.path.expanduser("~"), "DdalPOS")
    os.makedirs(root, exist_ok=True)
    profile = QWebEngineProfile("DdalPOS", app)
    profile.setPersistentStoragePath(os.path.join(root, "storage"))
    profile.setCachePath(os.path.join(root, "cache"))
    profile.setHttpCacheType(QWebEngineProfile.HttpCacheType.DiskHttpCache)
    profile.setPersistentCookiesPolicy(QWebEngineProfile.PersistentCookiesPolicy.ForcePersistentCookies)
    s = profile.settings()
    s.setAttribute(QWebEngineSettings.WebAttribute.LocalStorageEnabled, True)
    s.setAttribute(QWebEngineSettings.WebAttribute.JavascriptEnabled, True)
    s.setAttribute(QWebEngineSettings.WebAttribute.JavascriptCanAccessClipboard, True)
    s.setAttribute(QWebEngineSettings.WebAttribute.ScrollAnimatorEnabled, False)
    profile.downloadRequested.connect(on_download)
    return profile


def on_download(item: QWebEngineDownloadRequest):
    # 기록 달력의 '휴대폰 달력으로 보내기'(.ics) 같은 파일 저장
    downloads = QStandardPaths.writableLocation(QStandardPaths.StandardLocation.DownloadLocation)
    name = item.downloadFileName() or "download"
    path, _ = QFileDialog.getSaveFileName(None, "파일 저장", os.path.join(downloads, name))
    if not path:
        item.cancel()
        return
    item.setDownloadDirectory(os.path.dirname(path))
    item.setDownloadFileName(os.path.basename(path))
    item.accept()


class PosPage(QWebEnginePage):
    def javaScriptConsoleMessage(self, level, message, line, source):
        pass  # 콘솔 메시지는 표시하지 않음


class PosWindow(QMainWindow):
    def __init__(self, profile, url, title):
        super().__init__()
        self.url = url
        self.setWindowTitle(title)
        icon = resource_path("icon.png")
        if os.path.exists(icon):
            self.setWindowIcon(QIcon(icon))
        self.view = QWebEngineView(self)
        self.page = PosPage(profile, self.view)
        self.view.setPage(self.page)
        self.view.setContextMenuPolicy(Qt.ContextMenuPolicy.NoContextMenu)  # 우클릭 메뉴 숨김 (실수 방지)
        self.setCentralWidget(self.view)
        self.page.printRequested.connect(self.print_page)        # 포스의 '인쇄' 버튼(window.print)
        self.view.printFinished.connect(self._print_done)
        self.view.loadFinished.connect(self._loaded)
        self._printer = None
        self._offline_shown = False

        QShortcut(QKeySequence("F11"), self, activated=self.toggle_full)
        QShortcut(QKeySequence("F5"), self, activated=self.reload)
        QShortcut(QKeySequence("Ctrl+P"), self, activated=self.print_page)
        self.resize(1280, 860)
        self.view.load(QUrl(url))

    def reload(self):
        self._offline_shown = False
        self.view.load(QUrl(self.url))

    def _loaded(self, ok):
        if ok or self._offline_shown:
            return
        # 인터넷이 없고 아직 한 번도 열린 적 없을 때만 안내 화면
        self._offline_shown = True
        self.view.setHtml(OFFLINE_HTML % self.url, QUrl("about:blank"))

    def toggle_full(self):
        if self.isFullScreen():
            self.showMaximized()
        else:
            self.showFullScreen()

    def print_page(self):
        printer = QPrinter(QPrinter.PrinterMode.HighResolution)
        dlg = QPrintDialog(printer, self)
        dlg.setWindowTitle("인쇄 (PDF로 저장하려면 프린터에서 'PDF'를 고르세요)")
        if dlg.exec() != QPrintDialog.DialogCode.Accepted:
            return
        self._printer = printer
        self.view.print(printer)

    def _print_done(self, ok):
        self._printer = None
        if not ok:
            QMessageBox.warning(self, APP_NAME, "인쇄하지 못했습니다. 프린터 연결을 확인하세요.")


def main():
    # 터치 화면(윈도우 태블릿)에서도 손가락으로 누르고 끌 수 있게
    os.environ.setdefault("QTWEBENGINE_CHROMIUM_FLAGS", "--touch-events=enabled")
    QApplication.setAttribute(Qt.ApplicationAttribute.AA_ShareOpenGLContexts)
    app = QApplication(sys.argv)
    app.setApplicationName("DdalPOS")
    app.setOrganizationName("DdalPOS")
    profile = make_profile(app)

    only_history = "--history" in sys.argv   # '포스 기록 달력' 아이콘으로 켠 경우 달력만
    win = PosWindow(profile, HISTORY_URL if only_history else POS_URL, APP_NAME + (" — 기록 달력" if only_history else ""))
    windows = [win]

    def open_history():
        # 기록 달력은 같은 저장 공간을 쓰는 별도 창 (같은 계정으로 로그인 유지)
        for w in windows[1:]:
            if w.isVisible():
                w.raise_(); w.activateWindow(); return
        h = PosWindow(profile, HISTORY_URL, APP_NAME + " — 기록 달력")
        windows.append(h)
        h.showMaximized()

    if not only_history:
        QShortcut(QKeySequence("F2"), win, activated=open_history)
    win.showMaximized()
    rc = app.exec()
    # 끝낼 때: 화면(페이지)을 먼저 정리한 뒤 저장 공간을 닫음 → 판매 기록이 디스크에 확실히 남음
    for w in windows:
        w.page.setParent(None)
        w.view.setPage(QWebEnginePage(w.view))
        w.page = None
    del windows[:]
    import gc; gc.collect()
    sys.exit(rc)


if __name__ == "__main__":
    main()
