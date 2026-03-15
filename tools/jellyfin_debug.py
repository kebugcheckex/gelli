#!/usr/bin/env python3
import argparse
import json
import sys
import urllib.parse
import urllib.request
import ssl
from typing import Dict, List, Tuple


def build_url(base: str, path: str, params: Dict[str, str]) -> str:
    base = base.rstrip("/")
    path = path if path.startswith("/") else f"/{path}"
    query = urllib.parse.urlencode(params, doseq=True)
    return f"{base}{path}?{query}"


def request_json(url: str, headers: Dict[str, str], insecure: bool) -> Tuple[int, Dict]:
    req = urllib.request.Request(url, headers=headers, method="GET")
    context = None
    if insecure:
        context = ssl._create_unverified_context()
    try:
        with urllib.request.urlopen(req, context=context, timeout=30) as resp:
            status = resp.getcode()
            data = resp.read()
            return status, json.loads(data.decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {e.code} {e.reason}\n{body}") from e


def print_summary(label: str, status: int, payload: Dict) -> None:
    items = payload.get("Items") or []
    total = payload.get("TotalRecordCount")
    print(f"\n== {label} ==")
    print(f"status: {status}")
    print(f"total: {total}")
    print(f"items: {len(items)}")
    for i, item in enumerate(items[:3]):
        name = item.get("Name")
        item_id = item.get("Id")
        item_type = item.get("Type") or item.get("BaseItemType")
        print(f"- {i + 1}. {name} ({item_type}) id={item_id}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Debug Jellyfin item queries used by Gramophone.")
    parser.add_argument("--server", required=True, help="Base server URL, e.g. https://jellyfin.example.com")
    parser.add_argument("--user-id", required=True, help="Jellyfin user ID")
    parser.add_argument("--token", required=True, help="Jellyfin access token (X-Emby-Token)")
    parser.add_argument("--album-id", required=True, help="Album ID to query")
    parser.add_argument("--library-id", help="Optional library (collection) ID")
    parser.add_argument("--include-item-types", default="Audio", help="IncludeItemTypes value")
    parser.add_argument("--fields", default="MediaSources", help="Fields value")
    parser.add_argument("--sort-by", default="ParentIndexNumber,IndexNumber", help="SortBy value")
    parser.add_argument("--insecure", action="store_true", help="Disable TLS certificate verification")
    parser.add_argument("--print-response", action="store_true", help="Print raw JSON for each query")
    parser.add_argument("--save-response", help="Save raw JSON to this file prefix")
    args = parser.parse_args()

    headers = {
        "Accept": "application/json",
        "X-Emby-Token": args.token,
        "X-Emby-Authorization": (
            "MediaBrowser Client=GramophoneDebug, Device=DebugCLI, "
            f"DeviceId=gramophone-debug, Version=0.0, Token={args.token}"
        ),
    }

    base_path = f"/Users/{args.user_id}/Items"
    common = {
        "IncludeItemTypes": args.include_item_types,
        "Fields": args.fields,
        "SortBy": args.sort_by,
        "Recursive": "true",
    }

    queries: List[Tuple[str, Dict[str, str]]] = []
    queries.append(("ParentId (album)", {**common, "ParentId": args.album_id}))
    queries.append(("AlbumIds (album)", {**common, "AlbumIds": args.album_id}))
    if args.library_id:
        queries.append(
            ("Library Parent + AlbumIds", {**common, "ParentId": args.library_id, "AlbumIds": args.album_id})
        )
        queries.append(
            ("Library Parent + ParentId (album)", {**common, "ParentId": args.album_id, "TopParentId": args.library_id})
        )

    for idx, (label, params) in enumerate(queries, start=1):
        url = build_url(args.server, base_path, params)
        print(f"\nRequest {idx}: {label}")
        print(url)
        status, payload = request_json(url, headers, args.insecure)
        print_summary(label, status, payload)
        if args.print_response:
            print(json.dumps(payload, indent=2))
        if args.save_response:
            path = f"{args.save_response}.{idx}.json"
            with open(path, "w", encoding="utf-8") as f:
                json.dump(payload, f, indent=2)
            print(f"saved: {path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
