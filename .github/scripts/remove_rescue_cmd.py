#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""إزالة أوامر الإنقاذ منتهية النافذة (cat=_update_cmd مع حقل until) — v2 (2026-10-20)

المنطق الجديد (v2):
  - عنصر _update_cmd **بلا حقل until**: لا يُمس أبداً — يُدار يدوياً من المطور.
  - عنصر _update_cmd مع until=YYYY-MM-DD: يُزال تلقائياً عندما يبلغ التاريخ أو يجاوزه
    (بتوقيت دمشق UTC+3). هذا يسمح بوضع أوامر إنقاذ متعددة قيد التشغيل في الوقت نفسه
    مع انتهاء تلقائي مرتب لكل منها.

يستدعيه سير العمل الدوري .github/workflows/remove-rescue-cmd.yml ويمكن تشغيله يدوياً:
  python3 .github/scripts/remove_rescue_cmd.py
"""
import json
import os
import sys
import datetime

# جذر المستودع = مجلد الملف + ../../ (من .github/scripts إلى الجذر)
REPO_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CONTENT = os.path.join(REPO_DIR, "content.json")

TZ_DAMASCUS = datetime.timezone(datetime.timedelta(hours=3))


def main():
    today = datetime.datetime.now(TZ_DAMASCUS).date()

    with open(CONTENT, encoding="utf-8") as f:
        items = json.load(f)

    removed, kept = [], []
    for it in items:
        if isinstance(it, dict) and it.get("cat") == "_update_cmd":
            until = str(it.get("until", "") or "").strip()
            try:
                due = datetime.datetime.strptime(until, "%Y-%m-%d").date()
            except ValueError:
                kept.append(it)  # بلا until أو صيغة غير صالحة → يبقى (إدارة يدوية)
                continue
            if today >= due:
                removed.append(str(it.get("id", until)))
                continue
        kept.append(it)

    if not removed:
        print("NOOP: لا أوامر منتهية النافذة — عدد العناصر %d" % len(items))
        return 0

    with open(CONTENT, "w", encoding="utf-8") as f:
        json.dump(kept, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print("REMOVED (%s): العناصر %d -> %d" % (", ".join(removed), len(items), len(kept)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
