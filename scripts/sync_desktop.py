#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import types

ANDROID_ROOT = Path(__file__).resolve().parents[1]
DESKTOP = Path(sys.argv[1] if len(sys.argv) > 1 else "../noticias-python").resolve()
SRC = DESKTOP / "src"
ASSETS = ANDROID_ROOT / "app" / "src" / "main" / "assets"
ASSETS.mkdir(parents=True, exist_ok=True)

if not SRC.exists():
    raise SystemExit(f"Projeto desktop não encontrado: {DESKTOP}")


def package(name: str, path: Path | None = None):
    mod = types.ModuleType(name)
    mod.__path__ = [str(path)] if path else []
    sys.modules[name] = mod
    return mod


def load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Não foi possível carregar {path}")
    mod = importlib.util.module_from_spec(spec)
    sys.modules[name] = mod
    spec.loader.exec_module(mod)
    return mod


# Carrega somente os módulos de catálogo puros, sem importar a UI desktop/PySide6.
package("monitor_noticias", SRC / "monitor_noticias")
models_pkg = package("monitor_noticias.models", SRC / "monitor_noticias" / "models")
source_mod = load(
    "monitor_noticias.models.source",
    SRC / "monitor_noticias" / "models" / "source.py",
)
models_pkg.MediaSource = source_mod.MediaSource
models_pkg.VideoSource = source_mod.VideoSource

package("monitor_noticias.collectors", SRC / "monitor_noticias" / "collectors")
video_pkg = package(
    "monitor_noticias.collectors.video",
    SRC / "monitor_noticias" / "collectors" / "video",
)
video_dir = SRC / "monitor_noticias" / "collectors" / "video"
sources_mod = load("monitor_noticias.collectors.video.sources", video_dir / "sources.py")
helpers_mod = load("monitor_noticias.collectors.video.catalog_helpers", video_dir / "catalog_helpers.py")
regions_a = load("monitor_noticias.collectors.video.catalog_regions_a", video_dir / "catalog_regions_a.py")
regions_b = load("monitor_noticias.collectors.video.catalog_regions_b", video_dir / "catalog_regions_b.py")
video_catalog = load("monitor_noticias.collectors.video.catalog", video_dir / "catalog.py")

package("monitor_noticias.ui", SRC / "monitor_noticias" / "ui")
ui_catalog = load(
    "monitor_noticias.ui.catalog",
    SRC / "monitor_noticias" / "ui" / "catalog.py",
)


def news_row(s):
    return {
        "id": s.id,
        "name": s.name,
        "region": s.region,
        "state": s.state,
        "group": s.group,
    }


def video_row(s):
    return {
        "id": s.id,
        "name": s.name,
        "group": s.group,
        "region": s.region,
        "state": s.state,
        "landingUrl": s.landingUrl,
        "searchUrlTemplate": s.searchUrlTemplate,
        "searchPrefix": s.searchPrefix,
    }


news = [news_row(s) for s in ui_catalog.NEWS_SOURCES]
videos = [video_row(s) for s in video_catalog.VIDEO_SOURCES]

(ASSETS / "news_sources.json").write_text(
    json.dumps(news, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
(ASSETS / "video_sources.json").write_text(
    json.dumps(videos, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)

# Capas: lista de jornais e configuração oficial já usada pelo desktop.
covers_assets = SRC / "monitor_noticias" / "capas_tool" / "assets" / "newspapers.json"
(ASSETS / "newspapers.json").write_bytes(covers_assets.read_bytes())


def constant(path: Path, name: str) -> str:
    text = path.read_text(encoding="utf-8")
    match = re.search(
        rf"(?ms)^\s*{re.escape(name)}\s*=\s*\(\s*['\"]([^'\"]+)['\"]\s*\)|^\s*{re.escape(name)}\s*=\s*['\"]([^'\"]+)['\"]",
        text,
    )
    if not match:
        raise RuntimeError(f"Constante {name} não encontrada em {path}")
    return next(v for v in match.groups() if v is not None)


auth_file = SRC / "monitor_noticias" / "auth" / "config.py"
covers_config = SRC / "monitor_noticias" / "capas_tool" / "app" / "config.py"

try:
    sha = subprocess.check_output(
        ["git", "-C", str(DESKTOP), "rev-parse", "HEAD"], text=True
    ).strip()
except Exception:
    sha = "unknown"

config = {
    "auth_api_url": constant(auth_file, "AUTH_API_URL"),
    "covers_apps_script_url": constant(covers_config, "APPS_SCRIPT_URL"),
    "covers_access_key": constant(covers_config, "ACCESS_KEY"),
    "desktop_head_sha": sha,
}
(ASSETS / "synced_config.json").write_text(
    json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)

print(f"NEWS_SOURCES={len(news)}")
print(f"VIDEO_SOURCES={len(videos)}")
print(f"AUTH={config['auth_api_url']}")
print(f"DESKTOP_HEAD={sha}")
