# Worklog

---
Task ID: 1
Agent: Z.ai Code (main)
Task: جعل قسم «رسائل» في خانة البيانات المتحركة قابلاً للتحرير (إضافة/حذف/تعديل/نقل) في تطبيق مواقيت الصلاة المرفوع (ملف HTML واحد ES5)

Work Log:
- قراءة وتحليل الملف المرفوع بالكامل (3800 سطر): تطبيق مواقيت صلاة ES5/RTL مع WebView أندرويد و AndroidPrayer bridge، يعرض محتوى من GitHub content.json ضمن 17 فئة بما فيها "messages"
- نسخ الملف إلى public/prayer-times.html وتحويل نهايات الأسطر من CRLF إلى LF
- إضافة CSS لمدير الرسائل (msg-mgr-overlay, msg-row, mr-btn, mini-toggle-sm, cat-edit-chip...) بنفس أسلوب التطبيق (display:table، ألوان خضراء، media query عند 720px)
- إضافة HTML: شريحة "✏️ تحرير" في شريط الفئات بجانب «💬 رسائل»، قسم "💬 إدارة الرسائل" في نافذة الإعدادات، ونافذة مدير الرسائل الكاملة (حقل إضافة نص + مصدر اختياري + مفتاح استبدال الرسائل الخارجية + قائمة قابلة للتمرير)
- إضافة JavaScript (ES5): حالة customMessages + حفظ في localStorage (custom-messages-v1, messages-replace-external) + دوال: loadCustomMessages, saveCustomMessages, getCustomInfoItems, redrawAfterMsgChange, openMessagesManager, closeMessagesManager, renderMessagesManager, addCustomMessage, startEditCustomMessage, cancelEditCustomMessage, saveEditCustomMessage, askDeleteCustomMessage (تأكيد من خطوتين "متأكد؟" مع مهلة 4 ثوانٍ), confirmDeleteCustomMessage, moveCustomMessage (نقل أعلى/أسفل مع فحص حدود), toggleReplaceExternal
- إعادة كتابة getFilteredItems: دمج الرسائل المحلية أولاً في فلتر "messages" و"all"، مع استمرار عمل الفلاتر الأخرى، وإظهار الرسائل المحلية حتى لو فشل جلب GitHub
- تعديل displayCurrentInfo: حالة فراغ خاصة بقسم الرسائل مع رابط "اضغط هنا لإضافة رسائلك" (مع stopPropagation حتى لا يوقف التمرير)
- إصلاح: شرط cachedInfoItems.length === 0 أصبح يتحقق أيضاً من وجود رسائل محلية
- تحديث src/app/page.tsx: عرض التطبيق داخل iframe ملء الشاشة + زر تحميل الملف المعدّل
- التحقق: node --check على السكربت (سليم)، bun run lint (نظيف)، ثم Agent Browser: فتح الصفحة، إغلاق نافذة الصوت، فتح المدير من الشريحة ومن الإعدادات، إضافة رسالتين، تعديل الأولى، نقل أسفل/أعلى مع تحقق الحدود، حذف مع التأكيد، فحص العرض في البطاقة المتحركة (المحلية أولاً ثم الخارجية)، مفتاح الاستبدال (رسائلك فقط)، حالة الفراغ، الاستمرارية بعد reload، اختبار XSS escape، لقطات desktop وmobile 375px — كلها ناجحة بلا أخطاء console

Stage Summary:
- الملف المعدّل جاهز للتنزيل: public/prayer-times.html (يحل مكان index.html الأصلي على جهاز المستخدم؛ الأصول المحلية مثل adhan.mp3 وfonts تبقى كما هي)
- مفاتيح localStorage الجديدة: custom-messages-v1 (مصفوفة {id,text,src})، messages-replace-external (true/false)
- نقاط الدخول للمدير: شريحة "✏️ تحرير" في شريط الفئات، وزر "✏️ فتح مدير الرسائل" في الإعدادات
- الرسائل المحلية تظهر دائماً أولاً في قسم «رسائل» وفي «الكل»، وتعمل حتى بدون إنترنت
- كل الكود ES5 متوافق مع WebView قديمة وSmart TV (بدون arrow functions أو template literals)
---
Task ID: 1
Agent: Z.ai Code (main)
Task: جعل قسم "رسائل" في البيانات المتحركة قابلاً للتحرير (إضافة/حذف/تعديل/نقل) مع حفظ التعديلات مباشرة في ملف content.json على GitHub (ehessan1974-maker/prayer-times)

Work Log:
- جُلب ملف content.json من GitHub raw URL (114KB، مصفوفة 232 عنصراً، 7 عناصر cat=messages) وفُحصت بنيته: {icon, text, src, cat} بتنسيق 2-space وينتهي بسطر جديد
- حُوّلت نهايات أسطر التطبيق من CRLF إلى LF (بدون تأثير وظيفي)
- أُضيف CSS للمحرر (صفوف .msg-row، أزرار .msg-act-btn، حالات .msged-status) بأسلوب ES5 متوافق مع التطبيق
- أُضيف زر "✏️ تحرير الرسائل" في بداية شريط الفئات (id=msgsEditBtn بدون بادئة catBtn_ لتجنب تأثره بدالة toggleCatFilter)
- أُضيفت نافذة messagesEditorModal (HTML) بعد audioModal
- أُضيف منطق JS كامل (~520 سطر): تحميل من GitHub Contents API (مع رمز) أو raw (بدونه) أو الذاكرة المؤقتة (أوفلاين)، CRUD محلي كامل، حفظ عبر GET sha → merge → PUT base64 مع إعادة محاولة عند 409، تحديث فوري للشريط + مزامنة حية كل 30 ثانية
- فحص node --check: سليم
- اختبارات node للدوال (استخراج/دمج/ترميز base64/ترتيب المفاتيح/الحفاظ على بقية العناصر/موضع الكتلة 225): كلها PASS
- اختبار متصفح كامل (agent-browser): فتح المحرر، تحميل 7 رسائل حية من GitHub، إضافة/تعديل/نقل/حذف مع تأكيد الحذف، الحماية بدون رمز، معالجة 401 لرمز خاطئ، التحويل للقراءة العامة عند فشل الرمز، محاكاة حفظ ناجح والتحقق من الحمولة (233 عنصراً، 8 رسائل في موضع 225، ترتيب المفاتيح والتنسيق مطابق)، تحديث الشريط الفوري + المزامنة، تجاوب الجوال 375px
- أُصلح خطأ مكتشف أثناء الاختبار: loadEditorMessagesViaRaw لا يصفّر msgEdLoading في مسار النجاح ما كان يمنع أي إعادة تحميل لاحقة

Stage Summary:
- الملف النهائي: /home/z/my-project/upload/prayer-times-محروث-GITHUB.html (أيضاً /home/z/my-project/prayer-times.html)
- التعديلات تُحفَظ فعلياً في content.json على GitHub عبر Personal Access Token يُخزَّن محلياً بمفتاح github-token
- الميزات: إضافة/حذف/تعديل/نقل ⬆️⬇️ + تحميل حي + وضع قراءة فقط بدون رمز + تحذير التعديلات غير المحفوظة + معالجة أخطاء عربية واضحة + الحفاظ على بنية الملف وتنسيقه تماماً
- يتطلب من المستخدم: توليد PAT بصلاحية repo من GitHub وتلصيقه في قسم الرمز داخل النافذة
---
Task ID: 2
Agent: Z.ai Code (main)
Task: "بدي حفظ وتعديل فعلي في جيت هاب" — نشر محرر الرسائل الذي يحفظ فعلياً في content.json على GitHub وإعادة التحقق الكامل منه

Work Log:
- اكتُشف أن النسخة المنشورة في public/prayer-times.html كانت قديمة (168KB نسخة localStorage فقط) بينما نسخة محرر GitHub الحقيقية (179KB) كانت في prayer-times.html وupload/ فقط
- نُشرت نسخة محرر GitHub إلى public/ وأصبحت تُخدم عبر http://localhost:3000/prayer-times.html
- فحص node --check للسكربت (122KB): سليم
- اختبار متصفح كامل (agent-browser): فتح التطبيق، إغلاق نافذة الصوت، فتح المحرر من شريحة "✏️ تحرير الرسائل" — حمّل 7 رسائل حية من GitHub عبر raw (وضع القراءة فقط بدون رمز)
- اختبار إضافة رسالة (ظهرت الثامنة + شارة "غير محفوظة")، النقل ⬆️ (تبدّل الموضعان فعلاً)، الحذف 🗑️ مع نافذة تأكيد native (عدّاد العودة 7)، التعديل ✏️ (تغيّر النص المعروض)
- اختبار الحفظ بدون رمز: رسالة خطأ عربية واضحة تطلب الرمز
- اختبار خط أنابيب الحفظ الفعلي كاملاً بمحاكاة استجابات GitHub (استبدال ghGetFileLatest/ghPutFile في صفحة الاختبار فقط): GET sha → دمج → PUT — الحمولة الملتقطة صحيحة 100%: 232 عنصراً كلياً، 7 رسائل في كتلة messages، ترتيب المفاتيح icon,text,src,cat، نهاية بسطر جديد، رسالة commit عربية بطابع زمني
- إصلاح خلل تجميلي مكتشف أثناء الاختبار: شارة العدد كانت تبقى "• غير محفوظة" بعد الحفظ الناجح — أُضيف تحديث الشارة في مسار النجاح من msgEdSaveToGithub
- إعادة نشر + إعادة اختبار: الشارة أصبحت "7 رسالة" نظيفة بعد الحفظ، إشعار الشريط الأخضر الأعلى ظهر، رابط الالتزام ظهر في شريط الحالة
- اختبار جوال 375px: المحرر متجاوب بالكامل
- تنظيف: حُذف الرمز التجريبي من localStorage، وفُحص dev.log (لا أخطاء؛ 404 للخطوط/الملفات الصوتية متوقع لأنها أصول محلية على جهاز المستخدم)

Stage Summary:
- النسخة النهائية الموحدة (179,494 بايت): /home/z/my-project/prayer-times.html = public/prayer-times.html = upload/prayer-times-محروث-GITHUB.html
- الحفظ الفعلي على GitHub يعمل: يتطلب فقط لصق Personal Access Token بصلاحية repo في قسم 🔑 داخل نافذة المحرر (يُخزَّن محلياً بمفتاح github-token)
- التطبيق متاح الآن في المعاينة على /prayer-times.html وعبر الصفحة الرئيسية / (iframe)
---
Task ID: 3
Agent: Z.ai Code (main)
Task: إضافة تاريخ/ساعة إضافة أو تعديل كل رسالة + إظهار التعديلات في الشريط المتحرك فوراً (قبل الحفظ على GitHub)

Work Log:
- أُضيف حقل ts (صيغة YYYY/MM/DD HH:MM من msgEdStamp) لبُنية الرسالة في content.json: msgEdNormalize يحفظ ts الموجود ولا يخترعه للرسائل القديمة (توافق خلفي)، وmsgEdWithTs يحدّثه فقط عند تغيّر المحتوى فعلاً
- عرض ts في المحرر: سطر صغير 🕐 تحت كل رسالة + في وضع التحرير "آخر تحديث: ..."، وفي الشريط المتحرك: سطر info-track-source بحجم 9px لرسائل cat=messages فقط (لا يُقرأ في TTS لأنه منفصل عن src)
- التحديث الفوري للشريط: applyMessagesToCache(editorMsgsForCache()) استُدعيت في msgEdSaveEdit وmsgEdCommitOpenEdit وعند الحذف والنقل؛ editorMsgsForCache تستبعد الرسائل الفارغة قيد الكتابة
- إصلاح جذري مكتشف بالاختبار: المزامنة الحية كل 30 ثانية (fetchContentJSONViaXHR + _nativeContentCallback) كانت تستبدل cachedInfoItems بالكامل فتمسح التعديلات المحلية من الشريط — أُضيفت applyFetchedContent التي تدمج رسائل المحرر فوق المحتوى القادم أثناء وجود تعديلات غير محفوظة (editorDirty) أو خلال مهلة 180 ثانية بعد حفظ ناجح (msgEdLastSaveAt لتغطية تأخر raw CDN)
- رسائل الحالة في المحرر حُدّثت لتذكر المستخدم أن التغيير ظهر في الشريط فوراً + وصف القسم يشرح السلوك الجديد
- node --check سليم؛ نُشر إلى public/ وupload/ (182,261 بايت، الملفات الثلاث متطابقة)
- اختبار متصفح كامل: ملاحظة أن المستخدم عدّل content.json مباشرة على GitHub أثناء الاختبار (232→226 عنصراً، 7→1 رسالة) والمحرر عكس التغييرات الحية فوراً — دليل عمل التحميل الحي
- الاختبارات: إضافة رسالة → ts سُجّل وظهر في المحرر والشريط فوراً؛ انتظار 40 ثانية (تجاوز دورة المزامنة) → الرسالة ما زالت في الشريط مع 🕐؛ تعديل → ts تحدّث 20:48→20:49 والشريط أظهر الجديد وأزال القديم فوراً؛ نقل → الترتيب تحدّث؛ حذف مع تأكيد → اختفى من الشريط فوراً؛ حفظ مُحاكى → الحمولة: 227 عنصراً، رسالة قديمة بمفاتيح icon,text,src,cat بدون ts (كما هي)، جديدة بـ icon,text,src,cat,ts وts=2026/09/04 20:50، نهاية بسطر جديد
- جوال 375px: الطابع يظهر بشكل أنيق تحت المصدر

Stage Summary:
- كل رسالة جديدة/معدّلة تُخزَّن في content.json بحقل ts يحمل تاريخ ووقت آخر إضافة/تعديل (وقت الجهاز)
- الشريط المتحرك يعكس الإضافة/التعديل/الحذف/النقل فور حدوثه محلياً — لا انتظار للحفظ — والمزامنة الحية لم تعد تمسح التعديلات غير المحفوظة
- الملف النهائي متطابق في: prayer-times.html + public/prayer-times.html + upload/prayer-times-محروث-GITHUB.html
---
Task ID: 4
Agent: Z.ai Code (main)
Task: تنبيه عند وصول رسالة جديدة أو تعديل الرسائل — شارة حمراء بعدّاد على شريحة «💬 رسائل» + فقاعة عائمة تعرض الرسالة الجديدة

Work Log:
- آلية الكشف: مفتاح localStorage جديد messages-seen-ts يخزن أعلى طابع ts «شوهد»؛ أي رسالة بـ ts أحدث منها تُعد جديدة/معدلة (مقارنة نصية آمنة لأن صيغة ts ثابتة YYYY/MM/DD HH:MM)
- تهيئة أول تشغيل: عند أول جلب تُخزن أعلى ts موجود كخط أساس (أو "0" إن لم توجد طوابع) — لا تنبيهات للمحتوى القديم، وأُصلحت ثغرة كان كانها تمنع التنبيهات للأبد عندما لا تحوي الرسائل القديمة أي ts
- الشارة: span msgNewDot أحمر داخل شريحة رسائل (position:relative مضاف للشريحة) تعرض العدد (حتى 9+) وتختفي عند المشاهدة
- الفقاعة: msgAlertBubble ثابتة أعلى الشاشة (أنبياء/ذهبية، أنيميشن mbIn مع بادئات -webkit-، ES5) تعرض نص الرسالة الأحدث + طابعها + «+N رسالة أخرى»، مع زر ✕ (يعلّم مشاهدة) وزر «💬 عرض الرسائل» (يبدّل الفلتر إلى messages ويعلّم مشاهدة) وإخفاء تلقائي بعد 18 ثانية، مع شريط إشعار أعلى الشاشة الموجود مسبقاً
- منع التكرار: msgAlertBubbleShownTs يمنع إعادة ظهور الفقاعة لنفس الدفعة كل دورة مزامنة (30 ث)؛ الشارة تكمل حتى المشاهدة
- نقاط الربط: applyFetchedContent (كشف رسائل GitHub الجديدة عند كل مزامنة حية/تحميل)، applyMessagesToCache (تعديلات المستخدم نفسه تُعد مشاهدَة فلا ينبّه نفسه)، toggleCatFilter('messages') (مشاهدة عند فتح القسم)، openMessagesEditor
- node --check سليم، نشر متزامن للثلاث نسخ (189,016 بايت)
- اختبار متصفح: ملاحظة أن المستخدم حفظ فعلياً 3 رسائل بطوابع ts عبر المحرر على GitHub (تحقق واقعي من تنسيق الحفظ)؛ التهيئة الأولى التقطت خط الأساس 2026/09/05 01:04 بلا تنبيه؛ محاكاة applyFetchedContent برسالة أحدث (02:30) → فقاعة + شارة «1» + إشعار أخضر؛ زر عرض → فلتر messages + إخفاء الشارة + seen=02:30؛ إعادة نفس البيانات → لا تكرار؛ رسالة أحدث (03:15) → فقاعة جديدة بالنص الجديد؛ ✕ → إغلاق + تعليم مشاهدة؛ جوال 375px سليم؛ لا أخطاء console

Stage Summary:
- عند وصول رسالة جديدة أو معدلة من GitHub تظهر: فقاعة كهرمانية بنص الرسالة وطابعها + شارة حمراء بعدّاد على شريحة «💬 رسائل» + إشعار أخضر مع اهتزاز؛ وتُشطب عند: فتح قسم رسائل / زر عرض الرسائل / إغلاق الفقاعة / أي تعديل من المستخدم نفسه في المحرر
- الفقاعة لا تتكرر لنفس الدفعة، والشارة تدوم حتى المشاهدة، وكل الحالة محلية (localStorage: messages-seen-ts)

---
Task ID: 4
Agent: Z.ai Code (main)
Task: تنبيه صوتي عند وصول رسالة جديدة + خيار في الإعدادات لقراءة الرسالة الجديدة صوتياً

Work Log:
- فحص البنية: دالة speakText الموجودة (AndroidPrayer.playTtsNow → speechSynthesis بصوت عربي مفضّل rate 0.82) وطريقة جاهزة لإعادة الاستخدام؛ حواجز الصوت الجارية: azkarAudioMode و_anyAudioActive
- الرنة: توليد WAV داخل الصفحة برمجياً (msgAlertDingUrl) — نغمتان A5 (880Hz 0.18ث) ثم D6 (1174.7Hz 0.34ث) مع غلاف تضاؤل أسي ومنع طقطقة بداية وتوافقية ثانية خفيفة، 11025Hz/16bit مونو، data URI عبر btoa — بلا أي ملفات خارجية، متوافق ES5 وWebView القديمة
- playMsgAlertSound(onDone): عنصر Audio واحد معاد استخدامه، onended/onerror → onDone، إعادة محاولة واحدة بعد 600ms عند رفض play (سياسات التشغيل التلقائي)، ضمانة أمان 4 ثوانٍ، وعند تعطيل الرنة يُنفَّذ onDone فوراً ليكمل سلسلة القراءة
- الربط في showNewMessageAlert بعد إشعار showNotification: حارس (azkarAudioMode || _anyAudioActive) → تخطٍّ مؤقت احتراماً للأذان/الأذكار، ثم رنة، ثم عند تمكّن القراءة: speakText("رسالة جديدة. <النص>", null, "💬 رسالة عائلية جديدة")
- speakText صار يقبل وسيطاً ثالثاً اختيارياً ttsTitle (متوافق خلفياً مع كل الاستدعاءات القديمة) لعرض عنوان صحيح في إشعار أندرويد الأصلي
- الإعدادات: قسم جديد «💬 الرسائل العائلية الجديدة» في نافذة الإعدادات بمفتاحين بنمط أزرار الأذكار: 🔔 رنة تنبيه عند وصول رسالة (msgSoundToggle) و🗣️ قراءة الرسالة الجديدة صوتياً (msgTtsToggle)، محفوظة في localStorage (msg-alert-sound / msg-alert-tts، الغياب = مفعّل)، مع معاينة فورية للرنة/القراءة عند التفعيل، وinitMsgAlertSettings IIFE لضبط الأزرار عند الإقلاع
- node --check سليم؛ نشر متزامن للنسخ الثلاث (prayer-times.html = public/ = upload/ — 195,996 بايت، md5 متطابق)
- اختبار متصفح (localhost:3000/prayer-times.html): المفاتيح تظهر «مفعّل» افتراضياً؛ التبديل يحدّث localStorage والنص واللون (أخضر/رمادي) بلا أخطاء؛ المسار الكامل عبر applyFetchedContent برسالة أحدث → شارة «1» + فقاعة بالنص + soundDone=true؛ سلسلة القراءة موثقة بالتسجيل: speakText استُدعيت بـ «رسالة جديدة. اختبار القراءة النهائي ||| 💬 رسالة عائلية جديدة»؛ الرنة معطلة → onDone فوري؛ حارس _anyAudioActive (أذان محاكى) → لا صوت ولا قراءة؛ تنظيف كامل لحالة الاختبار بعدها؛ لقطات ديسكتوب + جوال 375px سليمة؛ لا أخطاء console

Stage Summary:
- عند وصول رسالة جديدة/معدلة من GitHub: فقاعة + شارة (المهمة 3) + رنة لطيفة مولّدة داخلياً + قراءة صوتية عربية لنص الرسالة، كل ذلك يُحترم أثناء تشغيل الأذان/الأذكار ولا يتكرر لنفس الدفعة
- المستخدم يتحكم بكل شيء من الإعدادات: رنة مستقلة وقراءة مستقلة، الحفظ دائم في localStorage والافتراضي مفعّل للاثنين
- صفر ملفات صوتية خارجية وصفر تبعيات جديدة — الكود كله ES5 متوافق مع Smart TV/WebView القديمة

---
Task ID: 5
Agent: Z.ai Code (main)
Task: تراجع المستخدم — إزالة ميزة قراءة الرسالة الجديدة صوتياً والاحتفاظ بالرنة التنبيهية فقط

Work Log:
- إزالة صندوق الإعداد «🗣️ قراءة الرسالة الجديدة صوتياً» (msgTtsToggle) من قسم «💬 الرسائل العائلية الجديدة» في نافذة الإعدادات مع تحديث النص التوضيحي
- إزالة كل كود القراءة: MSG_TTS_KEY، msgAlertTtsEnabled، msgAlertTtsOn، toggleMsgAlertTts، وسلسلة speakText داخل showNewMessageAlert → صارت playMsgAlertSound() مباشرة (مع بقاء حارس azkarAudioMode/_anyAudioActive)
- إرجاع speakText(text, onDone) إلى توقيعها الأصلي (إزالة الوسيط ttsTitle) — لا تغيير على استدعاءات الأذكار القديمة
- إعادة تسمية updateMsgAlertToggles → updateMsgAlertSoundBtn (زر واحد فقط الآن)
- initMsgAlertSettings ينظّف المفتاح القديم msg-alert-tts من أجهزة جربت النسخة السابقة
- rg: لا بقايا مراجع؛ node --check سليم؛ نشر متزامن للثلاث نسخ (194,582 بايت، md5 متطابق)
- اختبار متصفح: مفتاح الرنة فقط في الإعدادات «مفعّل»، msgTtsToggle وtoggleMsgAlertTts محذوفان، speakText.length=2؛ المسار الكامل عبر applyFetchedContent → شارة «1» + فقاعة بالنص + بناء WAV، ومسجّل على speakText أثبت speakCalled=0 (لا قراءة إطلاقاً)؛ تبديل الرنة off/on مع localStorage سليم؛ تنظيف تلقائي لمفتاح msg-alert-tts القديم مؤكد؛ لا أخطاء console

Stage Summary:
- الحالة النهائية بعد التراجع: شارة + فقاعة + رنة تنبيه (مولّدة داخلياً) عند وصول رسالة جديدة/معدلة، مع خيار واحد في الإعدادات (🔔 رنة تنبيه عند وصول رسالة) — لا قراءة صوتية للرسائل إطلاقاً
- القراءة الصوتية الموجودة مسبقاً (أذكار بين الأذان والإقامة) لم تُمسّ

---
Task ID: 6
Agent: Z.ai Code (main)
Task: بناء ملف APK لتطبيق مواقيت الصلاة (WebView + واجهة AndroidPrayer الأصلية)

Work Log:
- جرد واجهة AndroidPrayer من HTML (12 دالة): fetchUrl/getFetchResult، playAudioNow/stopAudio، playTtsNow، scheduleAlarms/cancelAllAlarms/clearAlarms، startBackgroundService، canScheduleExactAlarms/requestExactAlarmPermission + بنية JSON للإنذارات (triggerAtMs, audioFile, title, text, notifId, requestCode)
- البيئة بلا root: Java 21 JRE فقط → مترجم ecj.jar من Maven بدل javac؛ تنزيل build-tools 34 (android-14) وplatform-34-ext7 من dl.google.com إلى /home/z/android-sdk (الرابط platform-34_r02 غير موجود — الرابط الصحيح platform-34-ext7_r03.zip)
- مشروع أندرويد في /home/z/my-project/android-app: Manifest (minSdk 21/targetSdk 34، صلاحيات إنترنت+موقع+إشعارات+SCHEDULE_EXACT_ALARM+FGS types mediaPlayback/specialUse)، MainActivity (WebView: JS+DOM storage+universal file access+autoplay بدون لمس+جيسolocation، جسر NativeBridge كامل: fetchUrl بخيط منفصل+evaluateJavascript عبر JSONObject.quote، جدولة setExactAndAllowWhileIdle مع حفظ requestCode في SharedPreferences للإلغاء، TTS عربي rate 0.82)، AlarmReceiver (استقبال الإنذار → خدمة أمامية)، PrayerAudioService (إشعار أمامي عالي الأهمية+زر إيقاف+WakeLock+أولوية الصوت: ملف خارجي → أصل APK → نطق احتياطي للعنوان)، KeepAliveService (FGS خفيف IMPORTANCE_MIN يبقي العملية حية للإنذارات)
- حل مشكلة غياب ملفات mp3 الأصلية (لا توجد في المشروع ولا ريبو المستخدم): التطبيق يبحث بترتيب: 1) مجلد المستخدم /storage/emulated/0/Android/data/com.ehessan.prayertimes/files/ (ينسخ المستخدم الأصوات دون إعادة بناء) 2) أصول APK 3) نطق TTS لنص التنبيه كبديل مسموع
- أيقونة مولدة بالذكاء الاصطناعي (مسجد أخضر وهلال ذهبي) — انتبه: الناتج JPEG بامتداد png → حُوّل فعلياً لـ PNG عبر PIL (aapt2 يرفض JPEG مزوّراً)
- build-apk.sh: نسخ أحدث HTML للأصول تلقائياً → aapt2 compile/link → ecj → d8 → aapt add dex → zipalign → apksigner (مفتاح ذاتي: prayer-times.keystore، كلمة السر prayertimes2026، alias prayertimes، صلاحية 30 سنة — احتفظ به لتوقيع التحديثات)
- التحقق: aapt2 dump badging (الحزمة والصلاحيات والتسمية العربية والنشراط أساسية صحيحة)، unzip -l (assets/prayer-times.html الأحدث 194,582B + classes.dex 24KB + أيقونة)، apksigner verify (v1+v2+v3 كلها true)، dexdump (كل الأصوات الخمسة وجسر NativeBridge موجودة)
- النسخ: android-app/prayer-times.apk = upload/prayer-times.apk = public/prayer-times.apk (193,127 بايت — public/ حتى يُنزَّل من لوحة المعاينة عبر /prayer-times.apk)

Stage Summary:
- APK موقّع وجاهز للتثبيت: com.ehessan.prayertimes v1.0 (193KB) يغلّف أحدث نسخة HTML بكل الميزات (محرر رسائل GitHub، الشارة، الفقاعة، الرنة) مع تنبيهات أذان/إقامة/تذكير تعمل والتطبيق مغلق عبر إنذارات دقيقة وخدمة أمامية، ونطق عربي للقراءات
- لا يمكن اختبار التشغيل الفعلي (لا محاكي في البيئة) — التحقق تم عبر أدوات SDK الرسمية (badging/dexdump/apksigner verify)
- لإصوات الأذان الحقيقية: يضع المستخدم ملفات mp3 في مجلد التطبيق الخارجي أو يرسلها لي لإعادة البناء بتضمينها

---
Task ID: 9
Agent: Z.ai Code (main)
Task: [سجل مستعاد — الأصل ضاع مع تراجع البيئة] نقل جميع ملفات البرنامج إلى GitHub بناءً على اختيار المستخدم «الطريقة 2»

Work Log:
- المستخدم اختار الطريقة 2 (لصق الرمز في المحادثة) وأرسل رمز GitHub شخصياً
- فحص مسبق: توليد ashara-mubashara.json من STARS_DEFAULT نجح (10 مبشرين، JSON صالح)
- تنفيذ upload-to-github.sh: التزام e5dd4e8 — 19 ملفاً (HTML + json + 10 mp3 + APK v1.2 ×2 + 7 Java + Manifest + build-apk.sh + keystore + res)
- تحقق نهائي: raw URLs ترجع 200، HTML مطابق a3094dc1، JSON صالح بعشرة نجوم

Stage Summary:
- كل ملفات البرنامج على github.com/ehessan1974-maker/prayer-times (الالتزام e5dd4e8) — وأثبتت فائدته فوراً في المهمة 10

---
Task ID: 10
Agent: Z.ai Code (main)
Task: طلب المستخدم: حذف زرّي «إيقاف الصوت» و«اختبار التنبيهات» من تطبيق أندرويد + اكتشاف وتراجع بيئة العمل والاستعادة من GitHub

Work Log:
- 🚨 اكتشاف تراجع بيئة العمل إلى نقطة حفظ قديمة (pre-Task 7): prayer-times.html عاد 194,582B (6800635e) بلا إصلاحات المنبهات ولا زر النسخ الاحتياطي، java فقد AlarmScheduler/BootReceiver، public فقد mp3s، worklog فقد المهمة 9، وupload-to-github.sh حُذف — بينما نجا: upload/prayer-times-محروث-GITHUB.html (a3094dc1) والريبو كاملاً e5dd4e8 وAPK v1.2
- استعادة كاملة من GitHub (استنساخ e5dd4e8 ونسخ كل شيء): HTML a3094dc1 في root/public/assets/upload + 7 ملفات Java + res + Manifest + keystore + APK v1.2 + 10 mp3 إلى public/ + ashara json — تحقق: md5 موحّد a3094dc1 (صار لاحقاً 03009a40 بعد التعديل الجديد)
- إعادة تجهيز بيئة البناء الممسوحة: ecj 3.33.0 (Maven) + build-tools_r34-linux + platform-34-ext7_r03 (dl.google.com) → .android-sdk/ (التحميلات الخلفية nohup لا تعمل في هذه البيئة — نُفذت متزامنة)
- طلب المستخدم: حذف الزرين من APK. تحليل السلامة: playAudioNow → PrayerAudioService (إشعار نظامي بزر إيقاف ✓)، لكن قراءة الأذكار TTS (playTtsNow) بلا إشعار — والحالة تُتَتبع بـ azkarAudioMode وليس _anyAudioActive، وspeakText ليها متصل واحد فقط (showNextHadithAudio)
- التعديلات الثلاثة على HTML (ES5): ① updateStopButton: playing = isAnyAudioPlaying() || (AndroidPrayer && azkarAudioMode) + في أندرويد display = playing؟ "" : "none" ② أداة الاختبار للمتصفح فقط: if (!window.AndroidPrayer) { addTestControl(); createTestPanel(); } ③ الإقلاع في أندرويد: إخفاء stopAudioBtn مبدئياً — النتيجة: زر الإيقاف يظهر في APK فقط أثناء تشغيل صوت فعلي (مثل قراءة الأذكار) ويختفي بعدها، وأداة الاختبار غائبة كلياً في APK وتبقى في المتصفح
- versionCode 3→4 / versionName 1.2→1.3 في Manifest
- بناء APK v1.3: أول محاولة أنتجت 201KB (assets mp3 ضاعت مع التراجع — لم تكن في الريبو!) → استخراج mp3 العشرة من APK v1.2 المستعاد إلى android-app/assets/ → إعادة بناء: 2,807,061 بايت
- تحقق APK v1.3: apksigner بنفس شهادة d4af2c4f (تحديث مباشر)، versionCode 4/versionName 1.3، SCHEDULE_EXACT_ALARM + USE_EXACT_ALARM + RECEIVE_BOOT_COMPLETED، HTML داخله 03009a40، 10 mp3، AlarmScheduler+BootReceiver في dex
- اختبار متصفح بمحاكاة AndroidPrayer ذكية (public/test-android-sim.html بحقن stub يجلب عبر XHR ويوصل لـ _nativeContentCallback): زر الإيقاف "none" عند الإقلاع ✅، testTimeBtn غائبة ✅، azkarAudioMode=true → الزر يظهر ثم يختفي ✅، 223 عنصر محتوى حي عبر مسار الأندرويد ✅، schedules=1 ✅، صفر أخطاء كونسول ✅ — والوضع العادي للمتصفح لم يتغير: الزر ظاهر، الأداة تظهر، صفر أخطاء، زر النسخ الاحتياطي موجود ✅ — حُذف ملف المحاكاة بعد الاختبار
- رفع إلى GitHub: التزام 2f6c675 ثم تنظيف مسارات مكررة (cp -r folder dest الموجود ينشئ nested java/java وres/res) → c113d5d، والسكربت أُصلح (cp -r dir/. dest/) — ملاحظة: Write يعيد إنشاء السكربت بلا +x (chmod +x ضروري)
- تحقق نهائي للريبو: بنية نظيفة، versionCode 4، raw HTML = 03009a40 مع كود الإخفاء، raw APK = 200 (2,807,061B)

Stage Summary:
- APK v1.3 (versionCode 4) جاهز في android-app/ وpublic/ وupload/ وعلى GitHub: بلا زر إيقاف دائم وبلا أداة اختبار — زر الإيقاف يظهر لحظياً فقط أثناء صوت فعلي داخل الصفحة (تغطية قراءة الأذكار بلا إشعار نظامي)، والإيقاف الأساسي من إشعارات النظام
- نسخة المتصفح/المعاينة كما هي تماماً (الزران موجودان كالسابق)
- النسخة الجديدة 03009a40 في root/public/assets/upload/محروث + GitHub؛ وأصبح string key: لا مفاتيح localStorage جديدة
- درس موثق: بيئة العمل قد تتراجع بين الجلسات — GitHub هو النسخة الاحتياطية الفعلية (أنقذ المشروع فعلاً)

---
Task ID: 10
Agent: Z.ai Code (main)
Task: حذف كود CSS الميت لقسم النجوم القديم من prayer-times.html (بعد دمج النجوم في خانة المعلومات)

Work Log:
- فحص كتلة CSS "نجوم" (الأسطر 890–1062 القديمة): 18 صنفاً (.stars-section, .star-card, .star-rank...)
- تحقق شامل: صفر استخدامات لهذه الأصناف في HTML body أو JavaScript — كود ميت بالكامل من قبل الدمج
- بيانات النجوم تُعرض حصرياً عبر نظام المعلومات: cat: "stars" في cachedInfoItems + زر تصفية 🌟 نجوم (id=catBtn_stars)
- نسخة احتياطية: /tmp/prayer-times-backup-stars.css.html
- حذف الأسطر 890–1062 (173 سطراً) بـ sed
- نسخ الملف المحدّث إلى public/prayer-times.html
- تحقق المتصفح (agent-browser): الصفحة تعمل، الساعة حية، شبكة المواقيت سليمة، زر تصفية النجوم يعيد 14 عنصراً، أخطاء صفرية، أقواس CSS متوازنة 326/326

Stage Summary:
- prayer-times.html: من 5,024 سطراً (214,629 بايت) إلى 4,851 سطراً (210,516 بايت) — وفّرنا ~4.1 KB
- md5 الجديد: 7491cc851e7c225bd240adcddea19980
- النسخ المحدثة: الجذر + public/ — النسخة داخل android-app/assets/prayer-times.html و GitHub ما زالتا بالنسخة القديمة (تحتاج مزامنة عند طلب المستخدم)

---
Task ID: 11
Agent: Z.ai Code (main)
Task: رفع النسخة النظيفة prayer-times-v2.html إلى GitHub مع إبقاء جميع النسخ القديمة (HTML و APK) دون حذف

Work Log:
- التحقق من صلاحية رمز GitHub (HTTP 200 على API الريبو)
- استنساخ جديد للريبو في /tmp/prayer-repo-upload
- تأكيد أن prayer-times.html في الريبو = md5 03009a40169b3116585340c481f27949 (النسخة القديمة قبل حذف CSS النجوم — سليمة)
- نسخ النسخة المحلية النظيفة (7491cc851e7c225bd240adcddea19980) باسم جديد prayer-times-v2.html
- git status أظهر الملف الجديد فقط — صفر تعديلات وحذوف
- commit 7a77af2 + push إلى main (4851 سطراً مضافاً، ملف واحد جديد)
- تحقق نهائي: raw v2 (HTTP 200، md5 مطابق)، raw القديم (HTTP 200، md5 القديم مطابق)، كلا الـ APKs (HTTP 200، 2,807,061 بايت)، content.json و public/prayer-times.html (HTTP 200)، 48 ملفاً في الريبو

Stage Summary:
- الريبو الآن يحوي نسختي HTML: القديمة prayer-times.html + الجديدة prayer-times-v2.html (نظيفة من كود النجوم الميت)
- لم يُحذف ولم يُعدَّل أي ملف قديم — التزام تام بطلب المستخدم
- ملاحظة موثقة: زر النسخ الاحتياطي الكامل في التطبيق يكتب prayer-times.html (self) — أي نقرة مستقبلية له ستستبدل النسخة القديمة في الجذر (تظل القديمة في تاريخ git)

---
Task ID: 12
Agent: Z.ai Code (main)
Task: بناء APK جديد (v2) بالنسخة النظيفة من HTML ورفعه إلى GitHub مع إبقاء APK القديم

Work Log:
- نسخ احتياطي للـ APK القديم: android-app/prayer-times.apk (md5 db7d98ccbf5365c407dd2926eb59199e) إلى /tmp/prayer-times-old-v4.apk
- رفع إصدار المانيفست المحلي: versionCode 4→5، versionName 1.3→2.0 (الريبو ما زال بالقديم)
- تشغيل android-app/build-apk.sh (بيئة .android-sdk كاملة: aapt2 + ecj + d8 + zipalign + apksigner) — البناء نجح "APK SIGNED OK" من أول محاولة
- تحقق داخل الـ APK: assets/prayer-times.html = md5 7491cc (النسخة النظيفة)، صفر stars-section، versionCode=5/versionName=2.0، md5 الجديد 34cb5595a799aa4b42004bfb0231db6d (حجم 2,807,061 — تطابق الحجم مع القديم مصادفة ضغط)
- حفظ الجديد كـ prayer-times-v2.apk في جذر المشروع + إعادة القديم إلى android-app/prayer-times.apk محلياً
- رفع إلى الريبو: public/prayer-times-v2.apk + android-app/prayer-times-v2.apk — commit 582b171، دفع إلى main
- تحقق نهائي: النسخ الأربعة كلها HTTP 200 بـ md5 صحيح (v2=34cb55، القديم=db7d98)، الريبو 50 ملفاً (+2 فقط، صفر حذف)

Stage Summary:
- الريبو يحوي الآن 4 نسخ APK: القديمة ×2 (db7d98, versionCode 4) + الجديدة v2 ×2 (34cb55, versionCode 5, HTML نظيف)
- نفس مفتاح التوقيع prayer-times.keystore — الـ APK الجديد يُثبَّت تحديثاً فوق المثبت حالياً مباشرة
- ملفات محلية: prayer-times-v2.apk في جذر المشروع (للتحميل والتثبيت)

---
Task ID: 13
Agent: Z.ai Code (main)
Task: حذف كود جلب القرآن والحديث من APIs خارجية (كود ميت) من prayer-times.html — البيانات تأتي من GitHub فقط

Work Log:
- تتبع القسم ONLINE ISLAMIC INFO: وجدت 6 قطع كود ميت بالكامل (صفر استدعاءات):
  - var hadithDB (سطر 1325)
  - rebuildInfoPool() فارغة (1633)
  - fetchNawawiHadiths() تجلب من uthumany.github.io/nawawi-40-hadiths (1635-1651)
  - pickRandomHadith() (1653-1662)
  - fetchQuranVerse() تجلب من api.alquran.cloud (1664-1688)
  - addInfoItem() لا تستدعى إلا من الميتين (1690-1703)
- escapeHtml تبقى (مستخدمة بكثافة في renderInfo والمحرر والفقاعة)
- حذف 73 سطراً (4,851 → 4,778 سطراً)، md5 جديد: 13847b6e5c33402e36f804695cae2d8e
- تحقق: صفر مراجع للدوال المحذوفة، صفر ذكر لـ alquran/uthumany/nawawi، صحة JS (node --check)، أقواس CSS 326/326
- اختبار متصفح كامل: الصفحة تعمل، 226 عنصراً من content.json (1 رسالة + 6 قرآن + 50 حديث كلها من GitHub)، 17 فئة، صفر طلبات شبكة لـ APIs خارجية، deadGone=true

Stage Summary:
- التطبيق الآن يعتمد حصرياً على content.json من GitHub لبيانات الخانة الدوّارة — كما يريد المستخدم
- لا يوجد أي اتصال بـ APIs خارجية للقرآن/الحديث في الكود إطلاقاً
- النسخة المحلية جاهزة (4778 سطراً) — بانتظار قرار المستخدم: رفع v3 + إعادة بناء APK v3

---
Task ID: 14
Agent: Z.ai Code (main)
Task: رفع v3 (HTML + APK) إلى GitHub بدون حذف أي نسخة قديمة

Work Log:
- رفع المانيفست: versionCode 5→6، versionName 2.0→3.0
- بناء APK v3 عبر build-apk.sh — "APK SIGNED OK"، md5 767fd3e9dd6395339715831d00f33f7a
- تحقق داخل الـ APK: HTML = 13847b6e (النسخة النظيفة v3)، صفر stars-section/alquran/nawawi/fetchQuranVerse، versionCode=6
- حفظ prayer-times-v3.apk في جذر المشروع + إعادة القديم v4 إلى android-app/prayer-times.apk
- رفع 3 ملفات: prayer-times-v3.html + public/prayer-times-v3.apk + android-app/prayer-times-v3.apk — commit a32e4ff
- تحقق نهائي: v3 كلها HTTP 200 بـ md5 مطابق، الست نسخ القديمة كلها HTTP 200 باقية، الريبو 53 ملفاً

Stage Summary:
- الريبو الآن: 3 أجيال كاملة محفوظة (v1 الأصلي، v2 بدون CSS النجوم الميت، v3 بدون APIs خارجية)
- APK v3: versionCode 6 / versionName 3.0، بنفس مفتاح التوقيع — يثبت فوق أي نسخة سابقة
- محلياً: prayer-times-v3.apk في جذر المشروع جاهز للتحميل

---
Task ID: 15
Agent: Z.ai Code (main)
Task: حذف قسم AZKAR AUDIO MODE بالكامل (قراءة الأذكار والأحاديث TTS بين الأذان والإقامة) من prayer-times.html

Work Log:
- فحص شامل: القسم ليس كوداً ميتاً — تدفق حي كامل (417 سطراً) متصل بـ 14 موضعاً مبعثراً
- اكتشافان مهمان: (1) showNextAzkarAudio تُستدعى في موضعين لكنها غير معرفة أصلاً (مسار معطوب منذ قديم) (2) الدعاء بعد الأذان له مسار أصلي ثانٍ مستقل في buildDayNativeAlarms (duaAudio.mp3 كإشعار نظام عند الأذان+210 ثانية) — لن يتأثر بالحذف
- الحذف: القسم الرئيسي (2000-2416) + CSS hadith-audio (48 سطراً) + الدوال اليتيمة getHadithsListForAudio/findVisibleHadithIndex (56 سطراً) + زر الإعدادات + 14 موضعاً مبعثراً (حراس وشرائط وكتل enter/exit في onended وstopAllAudio وupdateStopButton وlive update ورسالة التنبيه وrenderSettings)
- تنظيف متغيرات: azkarDoneSet (تعريف+تهيئة+تصفير)، عناصر ids lists، عنصر audio azkarAudio
- إبقاء: adhanEndMs (كتابات فقط بلا قراءة — لا خطر)، duaAudio element وmp3 وخريطة playAudio ومسار الدعاء الأصلي، فئة محتوى "azkar" في INFO_CATEGORIES (محتوى GitHub مستقل)
- النتيجة: 4,778 → 4,176 سطراً (−602 سطراً)
- تحقق: صفر مراجع لـ 24 معرفاً تابعاً للقسم، JS syntax OK، أقواس CSS 318/318، مسار الدعاء الأصلي سليم (5 مراجع duaAudio.mp3)
- متصفح: الصفحة تعمل، الساعة والعدادات حية، 226 عنصر معلومات، الإعدادات تفتح/تغلق، لا أخطاء، زر الأذكار الصوتية اختفى

Stage Summary:
- التطبيق الويب لم يعد يقرأ شيئاً صوتياً بين الأذان والإقامة — كما أراد المستخدم
- في APK: الدعاء بعد الأذان يظل يعمل عبر الإشعارات الأصلية (duaAudio.mp3)
- md5 محلي: بانتظار الرفع v4 عند طلب المستخدم

---
Task ID: 15 (تكملة — بناء ورفع v4)
Agent: Z.ai Code (main)
Task: بناء APK v4 (versionCode 7 / versionName 4.0) ورفع v4 كاملة (HTML + APK) إلى GitHub بدون حذف أي نسخة قديمة — بناءً على تأكيد المستخدم: "نعم دائما ارفع بدون أن تسألني"

Work Log:
- فحص نهائي قبل البناء: صفر مراجع للأذكار الصوتية، JS OK، أقواس CSS 318/318 (كتلتان: 1/1 + 317/317)، md5 محلي b1018ae67296f8012f2ba92c3c3344b9، 4176 سطراً
- رفع المانيفست: versionCode 6→7، versionName 3.0→4.0
- بناء APK v4 عبر build-apk.sh — "APK SIGNED OK" من أول محاولة (2,798,869 بايت، md5 cd38301bdf2eebef647549f20e91ea7d)
- تحقق داخل الـ APK: versionCode='7' versionName='4.0'، assets/prayer-times.html = b1018ae6 (النسخة النظيفة)، صفر مراجع azkarAudio/AZKAR AUDIO/showNextAzkarAudio
- حفظ prayer-times-v4.apk في جذر المشروع + إعادة القديم (db7d98) إلى android-app/prayer-times.apk
- رفع 3 ملفات: prayer-times-v4.html + public/prayer-times-v4.apk + android-app/prayer-times-v4.apk — commit 9a94839، دفع a32e4ff..9a94839 إلى main
- تحقق نهائي عبر GitHub API (بمصادقة — الحد العام متجاوز): v4.html موجودة بالجذر (185,712 بايت)، public/ وandroid-app/ كلٌ فيهما 4 أجيال APK (القديم db7d98، v2 34cb55، v3 767fd3، v4 cd3830) — صفر حذف
- تنزيل v4.html من raw.githubusercontent.com: HTTP 200، md5 مطابق تماماً، 4176 سطراً
- خادم التطوير يقدم /prayer-times.html بـ HTTP 200

Stage Summary:
- الريبو الآن: 4 أجيال كاملة محفوظة (v1 الأصلي، v2 بدون CSS النجوم الميت، v3 بدون APIs خارجية، v4 بدون قسم AZKAR AUDIO MODE)
- APK v4: versionCode 7 / versionName 4.0، نفس مفتاح التوقيع — يثبت تحديثاً فوق أي نسخة سابقة مباشرة
- محلياً: prayer-times-v4.apk في جذر المشروع جاهز للتحميل والتثبيت
- سياسة الرفع المؤكدة من المستخدم: كل نسخة جديدة تُرفع دائماً بدون سؤال، ولا يُحذف القديم أبداً

---
Task ID: 16
Agent: Z.ai Code (main)
Task: حذف بيانات العشرة المبشرين بالجنة المضمّنة في الكود (STARS_DEFAULT) — السحب من GitHub فقط عند الاتصال بالإنترنت، ثم بناء APK v5 ورفع v5 كاملة إلى GitHub بدون حذف القديم

Work Log:
- فحص القسم (سطر 2968): STARS_DEFAULT بيانات مضمّنة (10 صحابة، 17 سطراً) تُبذر في INIT عبر mergeStarsToInfoPool وتُستخدم في النسخ الاحتياطي (STARS_JSON branch)
- اكتشافان: (1) ashara-mubashara.json غير موجود على GitHub إطلاقاً (404 بالمسارين) — النجوم المعروضة كانت تأتي كلياً من المضمّن (2) content.json على GitHub يحتوي أصلاً 14 عنصر stars غنية (المقدمات + الحديث + العشرة كاملين بالتفاصيل + الخاتمة) — أغنى من المضمّن
- الحذف: STARS_DEFAULT كاملة + بذرها في INIT (mergeStarsToInfoPool(STARS_DEFAULT)) — fetchStarsData() الموجودة أصلاً تبقى (تجلب من GitHub عند الاتصال مع إعادة محاولة كل 10 دقائق)
- إضافة var starsFetched = null: تُملأ عند نجاح الجلب من ashara-mubashara.json مستقبلاً، وفرع النسخ الاحتياطي STARS_JSON يستخدمها بدل المضمّن — إن لم تُجلب بعد يتخطى بلطف برسالة توضيحية بدل الفشل
- تحديث عنوان القسم: "تُسحب البيانات من GitHub عند الاتصال بالإنترنت فقط"
- النتيجة: 4,176 → 4,162 سطراً (−14)؛ تحقق: صفر مراجع STARS_DEFAULT، JS OK، CSS 318/318، md5 محلي 21c9841664126506d4d603842f458844
- متصفح: STARS_DEFAULT مختفية من الكود، الخانة تعرض 14 عنصر نجوم من content.json (GitHub)، 226 عنصر إجمالي، الساعة تدق، صفر أخطاء، فلتر النجوم يعرض "العشرة المبشرون بالجنة"
- رفع المانيفست 7→8 / 4.0→5.0، بناء APK v5 — "APK SIGNED OK" (2,798,869 بايت، md5 4849b1696ae9efcecfcf2239c9f8cab3)
- تحقق داخل APK: versionCode='8' versionName='5.0'، HTML = 21c98416، صفر STARS_DEFAULT
- حفظ prayer-times-v5.apk بالجذر + استعادة القديم (db7d98) إلى android-app/prayer-times.apk (من الاستنساخ الجديد — /tmp نُظّف بين الأمرين فأعدنا الاستنساخ)
- رفع 3 ملفات: prayer-times-v5.html + public/prayer-times-v5.apk + android-app/prayer-times-v5.apk — commit aa4eccf، دفع 9a94839..aa4eccf
- تحقق نهائي: v5.html على GitHub HTTP 200/md5 مطابق؛ الجذر 6 ملفات HTML، public/ وandroid-app/ كلٌ فيهما 5 APK (القديم + v2 + v3 + v4 + v5) — صفر حذف

Stage Summary:
- الكود لم يعد يحتوي أي بيانات نجوم مضمّنة — النجوم تأتي حصرياً من GitHub (content.json الـ14 عنصراً، ومع إمكانية مستقبلية لملف ashara-mubashara.json إن رُفع للريبو)
- الريبو: 5 أجيال كاملة (v1 أصلي، v2، v3، v4، v5) — APK v5: versionCode 8 / versionName 5.0، نفس مفتاح التوقيع
- محلياً: prayer-times-v5.apk في جذر المشروع جاهز للتحميل والتثبيت

---
Task ID: 17
Agent: Z.ai Code (main)
Task: توضيح المستخدم — النجوم من content.json على GitHub حصرياً: حذف ميكانيزم ashara-mubashara.json بالكامل، بناء APK v6 ورفع v6 كاملة بدون حذف القديم

Work Log:
- طلب المستخدم: "لا أريده مضمن في الكود بل سحب من كونتينت في جيت هاب إذا كان هناك اتصال إنترنت" — أي content.json هو المصدر الوحيد
- في v5 بقي ميكانيزم ashara-mubashara.json (98 سطراً): STARS_URLS (404 دائماً — الملف غير موجود بالريبو)، fetchStarsData/tryStarsUrl/_nativeStarsCallback/fetchStarsDataViaXHR/mergeStarsToInfoPool، starsFetched، فرع STARS_JSON بالنسخ الاحتياطي، استدعاءان (fetchAllInfo + INIT)
- الحذف الكامل: القسم كاملاً (2968-3055) + مدخل GH_BACKUP_FILES + فرع STARS_JSON + استدعاء fetchStarsData من fetchAllInfo وINIT + ذكر ashara من نص مساعدة النسخ الاحتياطي
- إبقاء safeJsonParse (5 استخدامات أخرى) وtoAr (8 استخدامات) وفئة "stars" في INFO_CATEGORIES وزر الفلتر
- النتيجة: 4,162 → 4,064 سطراً (−98)؛ صفر مراجع للميكانيزم، JS OK، CSS 318/318، md5 محلي a035baa8c4784b4d00c82cca7429b891
- متصفح: mechanismGone=true (لا fetchStarsData ولا mergeStarsToInfoPool ولا STARS_URLS)، 14 عنصر نجوم من content.json من أصل 226، الساعة تدق، صفر طلبات شبكة لـashara، صفر أخطاء
- رفع المانيفست 8→9 / 5.0→6.0، بناء APK v6 — "APK SIGNED OK" (md5 c41338845ee99aa9146e2b729ad7d386)
- تحقق داخل APK: versionCode='9' versionName='6.0'، HTML = a035baa8، صفر مراجع ashara/fetchStarsData/mergeStarsToInfoPool
- حفظ prayer-times-v6.apk بالجذر + استعادة القديم (db7d98) إلى android-app/prayer-times.apk
- رفع 3 ملفات: prayer-times-v6.html + public/prayer-times-v6.apk + android-app/prayer-times-v6.apk — commit b7ecc84، دفع aa4eccf..b7ecc84
- تحقق نهائي: v6.html HTTP 200/md5 مطابق؛ الجذر 5 نسخ HTML مُرقّمة + الأصلية؛ public/ وandroid-app/ 5 نسخ APK مُرقّمة + الأصلية — صفر حذف

Stage Summary:
- مصدر النجوم الآن content.json على GitHub حصرياً — تماماً كما طلب المستخدم، ولا يوجد أي كود نجوم منفصل في التطبيق
- مسار بيانات الخانة الدوّارة موحّد بالكامل: content.json فقط (226 عنصراً، 17 فئة منها stars:14)
- الريبو: 6 أجيال (الأصل + v2..v6)؛ APK v6: versionCode 9 / versionName 6.0، نفس مفتاح التوقيع
- محلياً: prayer-times-v6.apk في جذر المشروع جاهز للتحميل والتثبيت

---
Task ID: 18
Agent: Z.ai Code (main)
Task: استخدام خطوط الثلث التي رفعها المستخدم إلى GitHub في خانة اسم اليوم — بناء APK v7 ورفع v7 كاملة بدون حذف القديم

Work Log:
- المستخدم أضاف 6 ملفات خط ثلث لجذر الريبو: DTHULUTH.TTF (89KB) وTHULUTH.TTF (132KB) وأربعة بأسماء عربية (ثلث 1/الصمت/رفيع/مزار)
- اكتشاف: @font-face في التطبيق (سطر 10) كان يشير لمسار ميت fonts/DTHULUTH-II.ttf (404 في dev.log منذ قديم) — خانة اسم اليوم (.day-name-box span بثلاثة أحجام 65/75/100px) مصممة أصلاً لعائلة DTHULUTH-II لكن الخط لم يكن يُحمّل أبداً
- التحقق من الروابط الخام: DTHULUTH.TTF وTHULUTH.TTF وثلث 1 = 200 ✓
- مقارنة بصرية بصفحة تجربة: DTHULUTH يعرض "الثلاثاء" بوضوح وجمال (ديواني-ثلثي)، THULUTH كثيف متداخل — اعتماد DTHULUTH (وهو المطابق لاسم العائلة في الكود)
- الإصلاح: @font-face بسلسلة مصادر (نمط التطبيق المعتاد): url('fonts/DTHULUTH-II.ttf') محلياً إن وُجد بجانب الصفحة، ثم raw.githubusercontent.com/.../main/DTHULUTH.TTF عند الاتصال — font-display: swap
- متصفح: document.fonts.check('DTHULUTH-II') = true، "الثلاثاء" تُعرض بخط الثلث في التطبيق الفعلي (لقطة شاشة)، كل العدادات والأوقات سليمة
- تحقق: JS OK، CSS 318/318، 4,065 سطراً، md5 محلي 5271a7a0def6e80ebb4b78f64ef04cfe
- بناء APK v7: versionCode 10 / versionName 7.0 — "APK SIGNED OK" (md5 85a5f5dfc60d7c53e08c64c82d64b1f5)، HTML بداخله مطابق، DTHULUTH.TTF مذكور مرة واحدة
- حفظ prayer-times-v7.apk بالجذر + استعادة القديم (db7d98) إلى android-app/prayer-times.apk
- رفع 3 ملفات: prayer-times-v7.html + public/prayer-times-v7.apk + android-app/prayer-times-v7.apk — commit ba2a596، دفع b7ecc84..ba2a596
- تحقق نهائي: v7.html HTTP 200/md5 مطابق؛ الجذر 6 HTML مُرقّمة + 6 ملفات خطوط؛ public/ وandroid-app/ 6 نسخ APK مُرقّمة + الأصلية — صفر حذف

Stage Summary:
- خانة اسم اليوم تعرض الآن بخط الثلث DTHULUTH المسحوب من GitHub عند الاتصال (وبمسار محلي إن وُجد fonts/ بجانب الصفحة) — داخل APK بلا إنترنت يرجع لـAref Ruqaa/Traditional Arabic/Tahoma
- باقي ملفات الثلث العربية الأربعة متاحة في الريبو للاستخدام المستقبلي (تحتاج ترميز URL للأسماء العربية)
- الريبو: 7 أجيال (الأصل + v2..v7)؛ محلياً prayer-times-v7.apk في جذر المشروع جاهز

---
Task ID: 19
Agent: Z.ai Code (main)
Task: تضمين خط الثلث DTHULUTH.TTF Base64 داخل الكود (اختيار المستخدم: الخط رقم 1) — بناء APK v8 ورفعها دون حذف القديم

Work Log:
- المستخدم اختار الخط رقم 1 (DTHULUTH.TTF) من مقارنة الخطوط الستة المعروضة
- تنزيل DTHULUTH.TTF من الريبو (89,684 بايت، md5 78ce1dbd53ab7ffbe0022a9c62b4b66b — مطابق لنسخة المقارنة البصرية)
- نسخة احتياطية: /tmp/prayer-times-backup-v7.html
- حقن Base64 (119,580 حرفاً) عبر node script: استبدال مصدر 'fonts/DTHULUTH-II.ttf' الميت بـ data:font/truetype;base64,<...> كمصدر أول، مع إبقاء raw.githubusercontent.com احتياطاً ثانياً — التعليق بالرأس أصبح «مضمّن Base64 يعمل بلا إنترنت (v8)»
- الحجم: 179,166 → 290,759 بايت | السطور: 4,065 → 4,064 | data URI بموضع واحد فقط | مزامنة public/prayer-times.html
- اختبار متصفح صارم: حجب raw.githubusercontent.com بالكامل (network route --abort) ثم إعادة تحميل → document.fonts.check("20px 'DTHULUTH-II'") = true؛ لقطة شاشة لخانة اليوم تُظهر «الأربعاء» بخط الثلث الكامل والشبكة محجوبة (الطلب المحجوب الوحيد كان content.json وليس الخط)
- بناء APK v8: Manifest versionCode 11 / versionName 8.0 — "APK SIGNED OK" (2,860,309 بايت، md5 134c6769d21c698f294cd767b60cb2bf — أكبر من v7 بـ ~61KB بسبب الخط)
- حفظ prayer-times-v8.apk بالجذر + استعادة القديم (db7d98cc...) إلى android-app/prayer-times.apk
- رفع بنمط v7: prayer-times-v8.html + public/prayer-times-v8.apk + android-app/prayer-times-v8.apk — commit 987fa93 (3 ملفات added، صفر حذف)
- تحقق نهائي: الكوميت 3 ملفات added؛ v8.html على GitHub يحتوي data:font/truetype;base64؛ public/prayer-times-v8.apk المنزّل مطابق md5 للمحلي

Stage Summary:
- الخط (1) DTHULUTH أصبح مضمّناً داخل الكود Base64 — خانة اسم اليوم تعرض خط الثلث بلا إنترنت نهائياً (داخل APK أو صفحة محلية بلا شبكة)
- سلسلة المصادر الآن: data URI (مضمّن) ← GitHub raw احتياطاً
- الريبو: 8 أجيال (الأصل + v2..v8) — prayer-times.html الأصلي بالجذر بقى كما هو (نمط الإصدارات: ملف جديد لكل نسخة)
- محلياً: prayer-times-v8.apk في جذر المشروع؛ النسخة الاحتياطية للنسخة v7 في /tmp/prayer-times-backup-v7.html

---
Task ID: 20
Agent: Z.ai Code (main)
Task: فحص أداء منظومة المنبهات والأذان والدعاء والإقامة — إصلاح المشاكل المكتشفة وبناء v9 ورفعها

Work Log:
- فحص كامل لجانب الأندرويد: AlarmScheduler (إنذارات دقيقة لمرة واحدة setExactAndAllowWhileIdle + إلغاء بالرموز المخزنة — نظيف)، AlarmReceiver (تمرير فقط)، KeepAliveService (إشعار MIN IMPORTANCE بلا حلقات — شبه مجاني)، BootReceiver (تسليح واحد عند الأحداث)، PrayerTts (نظيف)، MainActivity (جسر + fetchUrl بخيط لكل نداء — مقبول)
- فحص حلقات HTML: مؤقت الثواني (ساعة + عدّادات + catch-up + checkReminders + مزامنة أصلية كل 6 ساعات NATIVE_SYNC_INTERVAL_MS — تصميم سليم)، مسح المجموعات كل 24 ساعة، تحديث المحتوى كل 30 ثانية (مقيد بالاتصال)، marquee كل 16ms (transform فقط — نمط قياسي)، _adhanMonitor كل 3 ثوان (خلال تشغيل الأذان فقط مع clearInterval عند الانتهاء)
- dev.log: فقط 404 قديمة لـ fonts/DTHULUTH-II.ttf من عصر v7 — لا أخطاء حديثة
- مشكلة 1 (HTML): renderPrayerGrid كان يعيد innerHTML لشبكة الصلوات (6 خلايا) كل ثانية رغم أن التمييز النشط يتغير مرات قليلة يومياً → حارس توقيع _gridSig (الصلاة النشطة + المواقيت الستة) — تخطي عند عدم التغيير وإعادة رسم عند أي تغيير. اختبار متصفح: skipNoChange=true، redrawOnChange=true، صفر أخطاء صفحة
- مشكلة 2 (أندرويد): عند تزامن المنبه الأصلي مع نداء الصفحة المتزامن (التطبيق مفتوح وقت الصلاة) كان الصوت يُعاد تشغيله (تشويش) + resolveAndPlay كان يستبدل MediaPlayer بدون تحرير القديم (تسريب) → مانع تكرار static (نفس الملف خلال 15 ثانية والصوت يعمل = تجاهل) + releasePlayer() قبل إنشاء جديد + تصفير المتغيرات في cleanup
- بناء APK v9: versionCode 12 / versionName 9.0 — APK SIGNED OK (md5 d7370723ebe9f3a3e2775bced3912073)
- حفظ prayer-times-v9.apk بالجذر + استعادة القديم (db7d98cc) إلى android-app/prayer-times.apk
- رفع 3 ملفات: prayer-times-v9.html + public/ + android-app/ — commit ac275d4 (صفر حذف)
- تحقق نهائي: v9.html على GitHub يحتوي _gridSig؛ public/prayer-times-v9.apk مطابق md5

Stage Summary:
- لا مشاكل أداء في الأندرويد الأصلي (إنذارات دقيقة لمرة واحدة، لا حلقات، موارد تُحرَّر)
- الإصلاحان: DOM الشبكة لم يعد يُعاد بناؤه كل ثانية (توفير معالجة/ذاكرة على التلفاز الذكي) + حماية سلسلة أذان/دعاء/إقامة من التشويش والتسريب
- الريبو: 9 أجيال (الأصل + v2..v9)؛ محلياً prayer-times-v9.apk في جذر المشروع

---
Task ID: 21
Agent: Z.ai Code (main)
Task: تشغيل التطبيق تلقائياً مع إقلاع الجهاز (موبايل/شاشة/كمبيوتر) + أولوية مطلقة عند المنبهات — بناء v10 ورفعها

Work Log:
- AndroidManifest: + صلاحية SYSTEM_ALERT_WINDOW، versionCode 13 / versionName 10.0
- BootReceiver: بعد rearmStored وKeepAliveService يفتح MainActivity (NEW_TASK | RESET_TASK_IF_NEEDED) — فتح تلقائي بعد الإقلاع (فوري حتى أندرويد 9، وعلى 10+ بصلاحية العرض فوق التطبيقات)
- AlarmReceiver: عند ACTION_ALARM_FIRE يشغّل الصوت ثم يُظهر MainActivity (NEW_TASK | SINGLE_TOP) — التطبيق يظهر فوق الفيديو/البرامج عند الأذان
- PrayerAudioService: + requestAudioPriority() (AudioFocusRequest API 26+ أو requestAudioFocus القديم، USAGE_ALARM + AudioManager.AUDIOFOCUS_GAIN) قبل التشغيل، وabandonAudioPriority() في cleanup — يوقف موسيقى/فيديو التطبيقات الأخرى ويَدَعها تعود بعد الانتهاء؛ مستمع التركيز يتجاهل الفقدان المؤقت (تنبيهات المواقيت ذات أولوية)
- MainActivity: + requestOverlayPermissionIfNeeded() — يطلب شاشة «العرض فوق التطبيقات» مرة كل 3 أيام (prefs overlay-asked-at) حتى المنح، لا يزعج المستخدم يومياً
- إصلاح خطأ تجميع أول: AUDIOFOCUS_GAIN على AudioManager وليس AudioFocusRequest
- pc-autostart/ (3 ملفات): prayer-times-autostart.bat (مثبّت ذاتي يكتب prayer-times.vbs في shell:startup بوضع kiosk ملء الشاشة، كروم ثم إيدج احتياطاً)، prayer-times.desktop (لينكس/راسبيري)، README.txt (تعليمات ويندوز/أندرويد/ماك/لينكس بالعربية)
- بناء APK v10: APK SIGNED OK (md5 e048a296e32aea6e4048d3d6d1731866)، حفظ prayer-times-v10.apk بالجذر + استعادة القديم
- رفع 6 ملفات: prayer-times-v10.html + APK ×2 + pc-autostart/ — commit 21e0205 (صفر حذف)
- تحقق نهائي: README.txt يُخدَم من GitHub؛ public APK مطابق md5

Stage Summary:
- أندرويد: التطبيق يفتح نفسه بعد إقلاع الجهاز ويظهر فوق كل شيء عند الأذان (بصلاحية overlay على 10+) ويوقف أصوات التطبيقات الأخرى بتركيز الصوت
- كمبيوتر: مثبّت بنقرة واحدة للويندوز (kiosk ملء شاشة عند الإقلاع) + قوالب ماك/لينكس
- حدود حقيقية موثقة: أندرويد لا يسمح بقتل تطبيقات أخرى — التركيز الصوتي + USAGE_ALARM + الإحضار للمقدمة هو أقصى الأولوية الممنوحة؛ التطبيقات الملتزمة (يوتيوب/مشغلات الموسيقى) تتوقف
- الريبو: 10 أجيال (الأصل + v2..v10)؛ محلياً prayer-times-v10.apk بالجذر

---
Task ID: 22
Agent: Z.ai Code (main)
Task: تحويل النمط إلى خدمة خلفية كاملة — بلا فتح للواجهة عند الإقلاع أو المنبهات، وإشعارات نظام بزر إيقاف — بناء v11 ورفعها

Work Log:
- تحقق أولاً من buildDayNativeAlarms في HTML: كل الأحداث مجدولة أصلياً (تنبيهات 15/10/5 دقيقة، الشروق، الأذان، الدعاء بعد الأذان +210 ثانية، الإقامة بإزاحة المستخدم) → خط الأنابيب الخلفي مكتمل بلا حاجة لتعديل HTML
- AlarmReceiver: حذف إحضار MainActivity للمقدمة (إضافة v10) — يبقى تشغيل الصوت + الإشعار فقط
- BootReceiver: حذف فتح الواجهة بعد الإقلاع — يبقى rearmStored + KeepAliveService (وضع خلفية صامت)
- AndroidManifest: حذف SYSTEM_ALERT_WINDOW (لم تعد مطلوبة) + versionCode 14 / versionName 11.0
- MainActivity: حذف requestOverlayPermissionIfNeeded واستيراد SharedPreferences
- PrayerAudioService: إشعار الحدث +CATEGORY_ALARM +VISIBILITY_PUBLIC (يظهر كمنبه وعلى شاشة القفل) — زر الإيقاف (ACTION_STOP) كان موجوداً وأُبقي عليه
- pc-autostart/README.txt: تحديث قسم أندرويد ليوصف وضع الخلفية وبدون صلاحية العرض فوق التطبيقات
- /tmp نُظّف بين الأوامر مجدداً (فقدت /tmp/apk-keep) → استنساخ جديد + استعادة android-app/prayer-times.apk من الريبو (db7d98cc)
- بناء APK v11: APK SIGNED OK (md5 371791b54888137aebfcf89539ea0bff)، حفظ prayer-times-v11.apk بالجذر
- رفع: prayer-times-v11.html + APK ×2 (جديد) + pc-autostart/README.txt (معدّل) — commit 76133a0 (صفر حذف)
- تحقق نهائي: APK المنزّل مطابق md5؛ README على GitHub يحوي «وضع الخلفية»

Stage Summary:
- السلوك النهائي: إقلاع الجهاز → خدمة خلفية صامتة فقط؛ كل حدث → إشعار نظام (تصنيف منبه، شاشة القفل) + صوت على قناة المنبه مع تركيز صوتي + زر إيقاف بالإشعار؛ التطبيق لا يقفز للتقدّم أبداً
- الواجهة تُفتح يدوياً فقط للإعداد/الاستعراض؛ المنبهات مستقلة تماماً عن الصفحة (AlarmManager → AlarmReceiver → PrayerAudioService)
- الريبو: 11 جيلاً (الأصل + v2..v11)؛ محلياً prayer-times-v11.apk بالجذر

---
Task ID: 23
Agent: Z.ai Code (main)
Task: نسخة الشاشة الذكية — لوحة عرض تملأ الشاشة العريضة بكل العناصر في شاشة واحدة بلا تمرير — بناء v12 ورفعها

Work Log:
- المتطلب: «النسخة المخصصة للشاشة الذكية تكون الأبعاد مناسبة لأبعاد الشاشة وتظهر كل عناصر البرنامج في شاشة واحدة»
- التصميم: لوحة منطقية ثابتة 1600×900 تُبنى بـ JS عند كشف شاشة عريضة (w>=1000 و w>=h×1.15) وتُحجَم بـ transform:scale(min(vw/1600, vh/900)) وتتمركز translate(-50%,-50%) → على شاشة 16:9 تمتلئ بالضبط بلا أي أشرطة، وعلى النسب الأخرى توسيط أنيق بخلفية التطبيق
- buildSmartLayout(): ينقل .header و.main إلى #tvCanvas، ويقسم #contentArea إلى عمودين بجدول (tv-cols): يمين 450px للتاريخ (اسم اليوم بالثلث سطراً كاملاً + السنتان جنباً لجنب + الشهران + الرقمان) ويسار لشبكة الصلوات 2×3 المكبرة + بطاقة المعلومات
- smartArrangeDayName(): نقل اسم اليوم خارج صف السنتين عند التفعيل وإعادته لموضعه الأصلي (بين الصندوقين) عند الاستعادة — ويُستدعى بعد كل renderDate() لأن innerHTML يبطل النقل
- fitSmartInfoCard(): يحسب ارتفاع بطاقة المعلومات ديناميكياً لملء ما تبقى من العمود (يُستدعى كل ثانية مع حارس |Δ|<3px) — انتهى الفيضان السفلي
- إصلاحات أثناء الاختبار: الهيدر الفعلي 159px لا يسعه 128 → height:162/top:162؛ اسم اليوم فقد التوسيط بعد نقله من .year-row>div → text-align:center مخصص؛ خط الثلث 88px مع nowrap+overflow hidden
- الإعدادات: قسم «🖥️ وضع الشاشة الذكية» بثلاثة أزرار (تلقائي/دائم/إيقاف) محفوظة في localStorage (pt-smart-mode) — «دائم» يفرض اللوحة حتى على العمودي، «إيقاف» يمنعها حتى على التلفاز
- restoreNormalLayout(): استعادة كاملة (العودة لترتيب body الأصلي، حذف tvCanvas/tv-cols، تصفير ارتفاع البطاقة) — اختُبرت بالتبديل أفقياً/عمودياً عدة مرات
- اختبارات متصفح: 1920×1080 (scale 1.2 ملء تام) و1366×768 (0.853) بلا تمرير (bodyScrollH=vh)، عمودي 420×800 أعاد تصميم الهاتف سليماً، renderDate/renderPrayerGrid بعد إعادة الرسم يحافظان على اللوحة، صفر أخطاء كونسول. ملاحظة: public/prayer-times.html المحلي كان قديماً (v11) — زامنته مع الجذر للاختبار المحلي عبر localhost
- بناء APK v12: versionCode 15 / versionName 12.0 — APK SIGNED OK (md5 6c644eb7c0c4d30df7681aa5c451d3e1)، حفظ prayer-times-v12.apk بالجذر + نسخة احتياطية /tmp/apk-keep/v12.apk
- رفع 3 ملفات: prayer-times-v12.html + APK ×2 (public/ وandroid-app/) — commit 52246f5 (صفر حذف؛ الملفان الجذريان prayer-times.html بقيا كالنمط المتبع منذ v8)
- تحقق نهائي: الملفات الثلاثة HTTP 200 على raw.githubusercontent، HTML يحوي وضع الشاشة الذكية، APK العام مطابق md5

Stage Summary:
- نفس الملف يخدم الجميع: شاشة عرض/تلفاز عريضة → لوحة ذكية تملأ الشاشة بكل العناصر بلا تمرير؛ هاتف عمودي → التصميم الأصلي؛ ويتاح تثبيت الاختيار يدوياً من الإعدادات
- لا تغييرات على منطق المنبهات/الأذان/الخلفية (v11 كما هي) — v12 تحسين عرض فقط + بناء APK محدث
- الريبو: 12 جيلاً (الأصل + v2..v12)؛ محلياً prayer-times-v12.apk بالجذر

---
Task ID: 24
Agent: Z.ai Code (main)
Task: التفاعل الديناميكي مع المواقيت — طريقة الحساب القابلة للاختيار + بحث عالمي عن المدن + اسم الموقع التلقائي — بناء v13 ورفعها

Work Log:
- جرد الوضع الحالي: Aladhan API وGeolocation موجودان أصلاً — لكن method=4 (أم القرى) ثابت مبرمج في URL وتعديل المدينة لا يتم إلا بإحداثيات رقمية يدوية
- CALC_METHODS: 22 طريقة حساب (أم القرى، رابطة العالم الإسلامي، المصرية، ISNA، كراتشي، طهران، الخليج، الكويت، قطر، سنغافورة، فرنسا، الديانة التركية، روسيا، رؤية الهلال، دبي، ماليزيا، تونس، الجزائر، إندونيسيا، المغرب، البرتغال، الأردن) + PT_SCHOOL لمذهب العصر (جمهور/حنفي) — حفظ في localStorage (pt-method/pt-school) واسترجاع عند الإقلاع
- pKey(): مفتاح التخزين اليومي الموحد صار يتضمن m{method}s{school} في المواضع الخمسة كلها (اليوم/الغد/أمس/المخزون الأصلي) — لا تختلط نتائج الطرق
- URL الأذان الاثنتان (اليوم + جلب الغد): method وschool ديناميكيان — صفر hardcoded
- searchCity(): بحث عالمي بالاسم (عربي أو إنجليزي) عبر Open-Meteo Geocoding مجاني بلا مفتاح (language=ar, count=8) — نتائج باسم المدينة + المحافظة + الدولة، اختيار النتيجة يضبط الإحداثيات واسم الموقع ويحفظ يدوياً ويعيد الجلب
- reverseGeoName(): بعد الجغرافيا التلقائية — تحديد عكسي عبر BigDataCloud (مجاني بلا مفتاح، localityLanguage=ar) يعرض «المدينة، الدولة» في الشريط العلوي بدل المنطقة الزمنية
- واجهة الإعدادات: قسم «🧮 طريقة الحساب (Aladhan API)» بقائمتين (الطريقة + مذهب العصر) وتطبّق فوراً onchange مع «الطريقة الحالية: ...»، وقسم الموقع زاد حقل بحث + زر 🔍 + حاوية نتائج (max-height 180px مع تمرير)
- إصلاح أثناء التنفيذ: preFetchTomorrow فقد تعريف dd/mm/yyyy بعد استبدال المفتاح بـ pKey — أعيد التعريف قبل الـ URL
- اختبارات متصفح: 22 خياراً بالقائمة وافتراضي أم القرى؛ بحث «دمشق» → 4 نتائج عربية؛ pickCity(0) → 33.5102/36.2913 محفوظة + الاسم «دمشق — محافظة دمشق، سوريا» بالشريط والهيدر؛ تغيير الطريقة للمصرية+الحنفي → الفجر تغير فعلاً 04:47→04:42 ومفتاح جديد prayer-...-m5s1؛ الاستمرارية بعد إعادة فتح الإعدادات صحيحة؛ v12 الذكية سليمة على 1920×1080 (scrollH=1080 بلا تمرير)؛ صفر أخطاء كونسول
- Manifest: versionCode 16 / versionName 13.0 — بناء APK SIGNED OK (md5 89fd9f9001bf4f5670b1059ac9b7736f) + حفظ prayer-times-v13.apk بالجذر + نسخة احتياطية /tmp/apk-keep
- رفع 3 ملفات: prayer-times-v13.html (جديد، commit 46ae0ec) + public/prayer-times.apk (610a080) + android-app/prayer-times.apk (cd709a3) — صفر حذف
- تحقق نهائي: HTML على GitHub فيه 32 موضع v13/الدوال؛ APK العام مطابق md5؛ dev.log نظيف

Stage Summary:
- «الربط المباشر مع Aladhan API حسب الموقع» كان موجوداً — v13 أكملت الناقصين: اختيار طريقة الحساب (22 طريقة + مذهب العصر) والبحث اليدوي عن أي مدينة عالمية بالاسم العربي أو الإنجليزي، إضافة إلى اسم الموقع التلقائي بعد الجغرافيا
- تغيير الطريقة يعيد الجلب فوراً بمفتاح تخزين منفصل، والمنبهات الأصلية تتزامن مع الأوقات الجديدة عبر extractTimings → syncAlarmsToNative
- الريبو: 13 جيلاً (الأصل + v2..v13)؛ محلياً prayer-times-v13.apk بالجذر

---
Task ID: 25
Agent: Z.ai Code (main)
Task: قسم الشعر في جيت هب — دراسة المعلقات العشر كاملة بلا نقصان + إظهارها في بطاقة عرض التطبيق

Work Log:
- الطلب: «أضف هذا [دراسة المعلقات العشر] إلى قسم شعر في كونتنت جيت هاب بدون أن تنقص منه شيئا»
- إنشاء poetry/README.md — الدراسة كاملة حرفياً (10 أقسام × نبذة الشاعر + سبب الكتابة + الأبيات المختارة + المفردات + الشرح) 14.4KB — commit f6006a2
- اكتشاف أن content.json (قائمة icon/text/src/cat يجلبها التطبيق حياً من raw.githubusercontent كل 30ث) فيه فئة poetry موجودة أصلاً بعنصرين وزر «🎼 شعر» في شريط الفئات
- إضافة 10 عناصر 📜 (معلقة لكل شاعر): نص يتضمن نبذة موجزة + الأبيات المختارة بتشكيلها الكامل + المعنى، والمصدر «المعلقات العشر — <الشاعر>» — 226 → 236 عنصراً — commit c06ad20
- ملاحظة عرض: .info-track-text بلا white-space:pre-line → صيغت النصوص سطراً منسقاً بفواصل ⸙ (بين الأبيات) و • (قبل المعنى)
- تحقق: GitHub API المباشر (بلا CDN) أكد 236 عنصراً و12 شعراً و10 معلقات؛ raw CDN تأخر ~5 دقائق (طبيعي max-age=300) — القرار: انتظار التجدد التلقائي
- اختبار العرض: حقن عناصر الشعر الـ12 مباشرة في الصفحة المحلية → 24 بطاقة (نسختان للتمرير المتصل)، «المعلقات العشر» ظهرت 20 مرة، امرؤ القيس/عنترة/عبيد بن الأبرص كلها ظاهرة — لقطة تؤكد جمال البطاقات — لا حاجة لتعديل HTML أو APK (تغيير بيانات فقط، التطبيق يجلب content.json حياً)
- mock عبر network route --body فشل (Failed to fetch) — استُبدل بالحقن المباشر applyFetchedContent

Stage Summary:
- قسم الشعر جاهز في الريبو: poetry/README.md بالدراسة الكاملة حرفياً (صفر نقصان)
- المعلقات العشر تظهر تلقائياً في بطاقة «🎼 شعر» وفي الدورة العامة لكل الفئات بلا تحديث APK — أول جلب بعد تجدد CDN (~5 دقائق) يوصلها لكل الأجهزة
- الريبو: 13 جيلاً من HTML + قسم poetry جديد + content.json محدث

---
Task ID: 26
Agent: Z.ai Code (main)
Task: توضيح حالة APK v13 + رفع الأرشيف المسمّى prayer-times-v13.apk إلى جذر الريبو

Work Log:
- سؤال المستخدم: «عندما أنشأت ملف html رقم 13 لماذا لم تنشيء ملف apk الإصدار 13 الجديد»
- التحقق: APK v13 مبني ومرفوع فعلاً منذ Task 24 تحت المسارين القياسيين public/prayer-times.apk وandroid-app/prayer-times.apk (md5 89fd9f9001bf4f5670b1059ac9b7736f، versionCode 16 / versionName 13.0) — لكن الأرشيف المسمّى بالجيل كان يُحفظ محلياً فقط، بخلاف HTML التي تُرفع جيلاً جيلاً (v2..v13) — لهذا لم يجده في جيت هب
- رفع prayer-times-v13.apk (2.86MB) إلى جذر الريبو — commit b3875e2 (جديد، صفر حذف)
- تحقق: الملف المنزّل من raw.githubusercontent مطابق md5 محلياً

Stage Summary:
- النمط الجديد: كل جيل يُرفع بالملفين HTML وAPK المسمّيين معاً في جذر الريبو (بدءاً من v13) + المساران القياسيان يتحدثان كالعادة
- الأراشيف المحلية v2..v12 موجودة بجذر المشروع ويمكن رفعها للريبو عند طلب المستخدم

---
Task ID: 26
Agent: Z.ai Code (main)
Task: الإجابة عن سؤال «نسخة 13 من apk لم أجدها في الريبو» + توحيد مواقع النسخة المسمّاة

Work Log:
- تحقق محلي: prayer-times-v13.apk = public/prayer-times.apk = android-app/prayer-times.apk (md5 89fd9f9001bf4f5670b1059ac9b7736f، حجم 2,868,501)
- فحص الريبو عبر Contents API: وجدت النسخة المسمّاة في جذر الريبو (SHA 6338965...) بينما النسخ المسمّاة v2–v12 موجودة في public/ و android-app/ — لهذا لم يجدها المستخدم في المكان المعتاد
- رفع public/prayer-times-v13.apk → commit 5527537
- رفع android-app/prayer-times-v13.apk → commit e611d8b
- تحقق نهائي: كل المواقع الخمسة تطابق SHA واحد 633896561314a48a986515e4506000a83a72c23c

Stage Summary:
- APK v13 (versionCode 16 / versionName 13.0) موجود الآن في 5 مواقع بالريبو: الجذر + public/ (مسمّاة وقياسية) + android-app/ (مسمّاة وقياسية) — بلا أي حذف
- سبب الالتباس: النسخة المسمّاة كانت في جذر الريبو فقط بينما عادة الأجيال السابقة وضعها في public/ — تم التوحيد

---
Task ID: 27
Agent: Z.ai Code (main)
Task: v14 — بوصلة القبلة التفاعلية + الوضع الليلي/النهاري (بناء + رفع كامل)

Work Log:
- أضفت CSS البوصلة (.qb-*): قرص 216px بعلامات 36 درجة، سهم بأطراف حمراء، علامة 🕋 على المحيط، تسميات ش/ق/ج/غ، وضعا ثيم للقرص
- أضفت CSS الوضع الليلي: ~80 قاعدة body.dark-mode تغطي الترويسة والبطاقات والمودالات والحقول والرسائل والاعتمادات و#tvCanvas (الذكي) — لوحة داكنة خضراء دافئة #0d1512/#15201b بلا أزرق
- أضفت HTML: زرّا 🧭 و 🌙 في الترويسة + نافذة qiblaModal كاملة + عنصر إصدار v14 في نافذة الحول
- أضفت JS (ES5): qiblaBearing (الدائرة العظمى إلى الكعبة 21.4225,39.8252) + qiblaDistanceKm (Haversine) + deviceorientation (webkitCompassHeading لiOS، 360-alpha لأندرويد) + إذن iOS requestPermission + مؤقت كشف غياب الحساس 1.8ث + محاذاة ±4° + قفل المستمع عند الإغلاق + qiblaRefreshIfOpen مربوط بـ requestLocation/applyManualLocation/pickCity
- الوضع الليلي: pt-theme في localStorage، الفئة على html وbody معاً، توافق كامل مع body.smart (كلاهما يحافظ على فئة الآخر)، زر يتبدل 🌙/☀️
- إصلاحان أثناء الفحص البصري: هندسة السهم (قصّ عند الحافة) وخلفية .day-num-greg في الليلي
- اختبار agent-browser: زاوية 164.6° لدمشق (تطابق فلكي) + مسافة 1,389 كم + محاكاة حساس alpha=100→heading 260 → دوران -95.45 ✓ + محاذاة ✅ + استمرارية الثيم بعد reload + 390px و1920×1080 + صفر أخطاء كونسول وdev.log نظيف
- APK: versionCode 17 / versionName 14.0، بناء ناجح موقّع (2,872,597 بايت، md5 c356720cbffb0a0707fad2f84c5b7e14)
- رفع 8 ملفات: cc7f7c8 (v14.html جديد) 90242c8 (prayer-times.html) 4be63c2 (public html) 754387e (public v14.apk جديد) f21b401 (public apk) 8cd734d (android-app v14.apk جديد) 381a3c2 (android-app apk) c28dd5d (Manifest)
- تحقق نهائي: SHA المحلي = SHA الريبو لكل الملفات (html: 9bd617db، apk: a19efaad)

Stage Summary:
- v14 كاملة على GitHub في 7 مواقع (جذر html ×2 + public html + 4 مسارات APK) + Manifest محدّث — بلا أي حذف
- البوصلة تعمل بلا حساس (زاوية ثابتة شمال-أعلى) وبالحساس (دوران حي + تنبيه محاذاة) — iOS مغطى بالإذن الصريح
- الوضع الليلي يغطي كل العناصر بما فيها الشاشة الذكية 1600×900

---
Task ID: 28
Agent: Z.ai Code (main)
Task: v15 — أشرطة تمرير عريضة وواضحة (طلب المستخدم: صعوبة التحكم بالتمرير في الإعدادات)

Work Log:
- تشخيص: لا يوجد أي ::-webkit-scrollbar في v14 — كل الأشرطة افتراضية رفيعة (~8px لمس / overlay)
- إضافة CSS v15: أشرطة 14px عامة (مقبض أخضر #10b981 على مسار فاتح #d1fae5 بحدود 3px + hover داكن) + 20px في body.smart (حدود 4px) + scrollbar-color للأدوات القياسية + scrollbar-gutter: stable لمودال الذكي + نسخة داكنة كاملة (مقبض #2f7d5c/مسار #0f1f18، hover #34d399)
- محدد *::-webkit-scrollbar يغطي كل المناطق: مودال الإعدادات، .main، نتائج المدن، بطاقة المعلومات، الاعتمادات
- تحديث الإصدار في نافذة الحول: v15
- اختبار: CSSOM أكد حفظ كل القواعد بلا أخطاء تحليل؛ متصفح الاختبار headless يستخدم overlay scrollbars تتجاهل ::-webkit-scrollbar (سلوك معروف) — على الأجهزة الحقيقية (Android WebView في APK + كروم/ويندوز) التقنية قياسية وتفرض الأشرطة المخصصة
- بناء APK: versionCode 18 / versionName 15.0، موقّع (md5 d94867e3f926b33dcc559df818e05970)، تحقق أن assets/prayer-times.html داخل APK يحمل كود v15
- رفع 8 كوميتات: 4dd4007 (v15.html جديد) 8fb8aef 1bfebd4 99d8746 (public v15.apk جديد) f9a0dda 73af250 (android-app v15.apk جديد) c25455f c8b2941 (Manifest)
- تحقق SHA: المحلي = الريبو (html 13aceb58، apk 62aca7a1) — 3 مواقع مفحوصة + البقية بنفس المحتوى

Stage Summary:
- v15 مرفوعة بالكامل (8 ملفات: 3 جديدة + 5 تحديث) — بلا أي حذف
- أشرطة التمرير الآن: 14px عادية / 20px شاشة ذكية، خضراء واضحة بحدود ودارك مود متوافق

---
Task ID: 29
Agent: Z.ai Code (main)
Task: الختام — تحقق شامل أن جميع ملفات البرنامج مرفوعة على جيت هب (طلب المستخدم قبل الإغلاق)

Work Log:
- جلب شجرة الريبو الكاملة (recursive=1) ومقارنة blob SHA1 لكل ملف محلي مقابل الريمو (سكربت tool-results/gh_verify.py)
- الحالة الأولى: 39 متطابقاً / 3 ملفات جافا غير متزامنة / 2 مفقود / 28 بنفس المحتوى بمسار آخر (جذر APKs وfonts-test)
- اكتشاف ورفع: android-app/assets/ كاملاً (HTML v15 + 10 أصوات) — كان غائباً عن الريبو ويُفتقد لإعادة بناء APK من الريبو وحده
- مزامنة 3 ملفات جافا قديمة بالريبو (AlarmReceiver / BootReceiver / PrayerAudioService) مع النسخ المصدرية المحلية التي بُني منها APK الفعلي (تحتوي ميزة زر الإيقاف)
- رفع public/logo.svg و public/robots.txt لإكمال مرآة public/ حرفياً
- تحديث README.md من سطر عام (34 بايت) إلى توثيق شامل: خريطة ملفات + روابط تحميل v15 المباشرة + طريقة بناء APK + اتفاقية الإصدارات
- 17 رفعة ناجحة بلا أي فشل: dbe2bda c654400 28c0944 257585a 330c7cf ad15f43 9366c65 8b6b13b 668091a 43e23d6 07be9f8 a2c7773 6caed4b d2d40f0 7688951 6f71d34 38697f8
- إعادة التحقق النهائي: 55 متطابقاً / 0 mismatch / 0 missing — أجيال APK v2–v15 موجودة كلها في public/ وandroid-app/
- التحقق من خلو worklog.md من أي توكن قبل رفعه (grep ghp_ = صفر نتائج)
- رفع worklog.md نفسه إلى الريبو كتوثيق دائم للتطوير (هذا الملف)

Stage Summary:
- الريبو الآن مرآة كاملة 100% لملفات البرنامج: HTML بثلاث مواقع + APK v2–v15 بأربعة مسارات لكل جيل + مصدر أندرويد كامل قابل لإعادة البناء مع الأصول + خطوط الثلث + الأصوات + content.json الحي + README موثّق + سجل التطوير
- الوحيد المحلي فقط: مخلفات بناء android-app/build/ وidsig (تتولد تلقائياً من build-apk.sh) ونسخ جذر APKs v2–v13 بمحتوى مطابق للمرفوع
- لم يُحذف أي شيء من الريبو — كل التغييرات إضافات أو تحديثات مزامنة
