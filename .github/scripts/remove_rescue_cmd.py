#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""إزالة أمر الإنقاذ المؤقت (cat=_update_cmd) من content.json — v26.17
يعمل بلا أي تغيير (NOOP) في حالتين:
  1) لم يحن الموعد بعد (قبل 2026-10-20 00:00 بتوقيت دمشق UTC+3)
  2) الأمر غير موجود أصلاً (أُزيل سابقاً)
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

# بداية نافذة الإزالة: 2026-10-20 00:00 بتوقيت دمشق (يومان من نشر قناة الإنقاذ في 2026-10-18)
START = datetime.datetime(2026, 10, 20, 0, 0, 0,
                          tzinfo=datetime.timezone(datetime.timedelta(hours=3)))

CMD_ID = "rescue-2617-20261018"


def main():
    now = datetime.datetime.now(datetime.timezone.utc).astimezone()
    if now < START:
        print("NOOP: لم يحن موعد الإزالة بعد (now=%s < start=%s)" %
              (now.isoformat(), START.isoformat()))
        return 0

    with open(CONTENT, encoding="utf-8") as f:
        items = json.load(f)

    kept = [i for i in items if i.get("cat") != "_update_cmd"]
    if len(kept) == len(items):
        print("NOOP: لا يوجد أمر _update_cmd (أُزيل سابقاً) — عدد العناصر %d" % len(items))
        return 0

    with open(CONTENT, "w", encoding="utf-8") as f:
        json.dump(kept, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print("REMOVED (%s): العناصر %d -> %d" %
          (CMD_ID, len(items), len(kept)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
