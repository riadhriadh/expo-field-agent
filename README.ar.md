# expo-field-agent

[English](README.md) · [Français](README.fr.md) · **العربية**

> **يلزم development build كي يحدث أي شيء فعليًا.**
>
> داخل Expo Go تتدهور الحزمة **بدل أن تنهار**: كل نداء يُرجع قيمة محايدة،
> و`isAvailable` يساوي `false`، ولا شيء يُتتبَّع أو يُعرَض أو يرنّ.
>
> ```bash
> npx expo prebuild && npx expo run:android
> ```

تتبّع GPS في الخلفية يصمد أمام إغلاق التطبيق، وإطفاء الشاشة، وإعادة تشغيل
الهاتف؛ وفقاعة عائمة فوق التطبيقات الأخرى؛ وتنبيه بملء الشاشة يعرض مكوّن
React Native **من تطبيقك أنت**، حتى والهاتف مقفل.

أندرويد: كامل. iOS: ما تسمح به المنصّة ولا شيء أكثر — جدول الحدود في الأسفل
يقول بالحرف ما هو غير متوفّر.

---

## تطبيق كامل مبنيّ عليها

**[riadhriadh/example-expo-field-agent](https://github.com/riadhriadh/example-expo-field-agent)** — تطبيق سائق على منوال Uber
Driver: الدخول في الخدمة، وتهيئة الأذونات، وفقاعة تعكس المرحلة، وعرض مهمّة بملء
الشاشة. كُتب انطلاقًا من [docs/prompt-rider-app.md](docs/prompt-rider-app.md)،
وهو كرّاس الشروط الموجود في هذا المستودع.

| الفقاعة فوق التطبيق | الفقاعة فوق شاشة البداية |
|---|---|
| <img src="https://raw.githubusercontent.com/riadhriadh/expo-field-agent/main/docs/screenshots/rider-online.png" width="240" alt="تطبيق السائق أثناء الخدمة والفقاعة تعرض En ligne" /> | <img src="https://raw.githubusercontent.com/riadhriadh/expo-field-agent/main/docs/screenshots/bubble-home.png" width="240" alt="الفقاعة تطفو فوق شاشة البداية في أندرويد" /> |

يسارًا: السائق في الخدمة، والخدمة ترسل المواقع، والفقاعة فوق التطبيق. يمينًا:
الفقاعة نفسها **فوق مشغّل النظام** والتطبيق في الخلفية — وهذا بالضبط ما يعجز عنه
JavaScript، وهو سبب كون هذه الحزمة أصلية.

الخريطة مطفأة في هاتين اللقطتين لأنّ مفتاح Google Maps غير مضبوط؛ أمّا التتبّع
تحتها فيعمل، ولذلك تستمرّ الإحداثيات في التحدّث.

---

## ‏Expo Go — تدهور، لا حجب

النصف الأصلي لا يمكن أن يوجد داخل Expo Go: فهو يحمل مجموعة ثابتة من الكود
الأصلي ليس كودك منها. هذه حقيقة في المنصّة، ولا حزمة تغيّرها.

وما تفعله هذه الحزمة حيالها: **تتدهور.** الاستيراد آمن، وكل نداء يُرجع قيمة
محايدة بدل أن يرمي استثناءً، فتستطيع بناء شاشاتك والتنقّل بينها داخل Expo Go،
وتحتفظ بـ development build لما يحتاج جهازًا فعليًا.

```ts
import * as FieldAgent from 'expo-field-agent';

if (!FieldAgent.isAvailable) {
  // Expo Go: قُلها في الواجهة بدل شحن مفتاح لا يفعل شيئًا.
}
```

| النداء | داخل Expo Go |
|---|---|
| `isAvailable` | `false` |
| `getPermissions()` | كل المفاتيح `'unsupported'` |
| `start()`، `stop()`، `setAuthHeader()`، `setInterval()`، `openSettings()` | تُحَلّ ولا تفعل شيئًا |
| `isRunning()` | `false` |
| `flush()` | `{ sent: 0, queued: 0 }` |
| `getState()` | `running:false`، و`queued:0`، و`provider:'none'`، و`locationEnabled:false`، و`lastError` يقول السبب |
| `getLog()` | `[]` |
| `exportLog()` | `null` |
| `getOdometer()` | `0` |
| `clearLog()`، `resetOdometer()` | تُحَلّ ولا تفعل شيئًا |
| `showBubble()` | `false` |
| `triggerAlert()`، `dismissAlert()`، `setAlertSound()`، `setStrings()`، `setBubbleImage()` | تُحَلّ ولا تفعل شيئًا |
| `getPendingAlert()` / `getPendingAlertSync()` | `null` |
| `addListener()` | اشتراك لا يُطلَق أبدًا، و`.remove()` آمن |
| `<AlertHost>` | لا يعرض شيئًا |

يُطلَق `console.warn` واحد عند أول نداء متدهور — مرّة واحدة لا عند كل نداء، كي
تبقى الشاشة مقروءة.

**أخطاء الوسائط تبقى ترمي استثناءً، في Expo Go كما في غيره.**
`triggerAlert({})` بلا عنوان، و`setInterval(0)`، و`setBubbleImage('')` الفارغ:
هذه عيوب في كودك لا قيود في المنصّة. وابتلاعها هنا كان سيُمرّرها إلى الإنتاج.

**ولا تخلط بين التدهور والدعم.** لا شيء يُتتبَّع، ولا فقاعة تُرسَم، ولا تنبيه
يرنّ. و`isAvailable` هو الإشارة الصادقة — اربط واجهتك بها، واجعل الاختبار
الحقيقي على development build.

---

## هل تحتاج هذه الحزمة أصلًا؟ اقرأ هذا أولًا

هذا الملحق وُجد لحالة واحدة: **شخص يعمل في الخارج، هاتفه في جيبه، وخادمك يجب
أن يظلّ يراه وأن يكون قادرًا على مقاطعته.** كل ما فيه ينبع من ذلك. إن لم تكن
هذه حالتك، فأداة أخفّ ستخدمك أفضل، والجداول التالية تسمّيها لك.

### استعملها حين

| الحالة | ما الذي ينكسر بدون وحدة أصلية (native) |
|---|---|
| **سائق توصيل أو سائق نقل ركّاب أثناء الخدمة** — موقع كل ١٥ ثانية إلى مركز التوزيع، بالإضافة إلى عرض مهمّة يجب أن يصله والشاشة مقفلة والهاتف في الجيب | السائق يُزيح التطبيق من قائمة التطبيقات الحديثة بحكم العادة. JavaScript يموت مع المهمّة؛ تتوقّف المواقع ويظنّ مركز التوزيع أنّ السائق عاد إلى بيته. |
| **فنّي في جولة ميدانية** — إثبات مرور على مدى يوم كامل، عبر أقبية وأنفاق بلا تغطية | النقاط المسجَّلة دون اتصال تعيش في الذاكرة وتزول مع العملية. تعود الجولة وفيها ثقوب. |
| **عامل منفرد أو دورية حراسة** — إثبات حضور ونداء عاجل | نبضة «ما زلت هنا» تتوقّف حين ينام المعالج تُبلّغ عن مفقود كل ليلة. |
| **مناوبة، إسعاف، مساعدة على الطريق** — نداء يجب أن يرنّ حتى في الوضع الصامت | إشعار عادي على هاتف صامت هو نداء لا يجيب عنه أحد. |
| **أسطول ولوجستيات** — دفعة يُعاد إرسالها بعد نفق يجب ألّا تُنشئ صفوفًا مكرّرة | حذف النقاط من الطابور بالعدد بدل الحذف بالمعرّف يُكرّرها أو يُضيّعها بمجرّد تداخل عمليتَي إرسال. |

### **لا** تستعملها حين — وهذا البديل

| ما تريده فعلًا | خذ هذا |
|---|---|
| الموقع فقط والتطبيق مفتوح على الشاشة | `expo-location` وحده. بلا سلّم أذونات، بلا إشعار دائم، بلا خدمة في المقدّمة. |
| موقع في الخلفية من حين لآخر، بأفضل جهد، بلا ضمان الصمود أمام إعادة التشغيل | `expo-location` مع `expo-task-manager` (‏`startLocationUpdatesAsync`). أبسط بكثير. الضمان هو سبب وجود هذا الملحق كلّه — إن لم تكن بحاجة إليه فلا تدفع ثمنه. |
| إشعار push عادي | `expo-notifications`. استعمال full-screen intent لمحتوى غير عاجل يُعرّض تطبيقك للإبلاغ عنه، ولهذا السبب بالضبط وضع أندرويد ١٤ هذه الميزة خلف إذن خاص. |
| النشر داخل Expo Go | مستحيل. هذه وحدة أصلية، وExpo Go يحمل مجموعة ثابتة من الكود الأصلي ليس كودك منها. |
| فقاعة عائمة على iOS | غير موجودة ولن توجد. لا هذه الحزمة ولا غيرها ستمنحك إيّاها. اقرأ جدول الحدود قبل أن تَعِد بها أحدًا. |

### لماذا وحدة أصلية من الأساس

التقاط GPS نفسه **لم** يُعَد كتابته — يبقى التقاط المنصّة
(`FusedLocationProviderClient`، `CLLocationManager`). ما يبرّر الكود الأصلي هو
قائمة ما يعجز JavaScript بنيويًا عن فعله:

| ما هو مطلوب | لماذا لا يستطيع JS |
|---|---|
| الصمود أمام إزاحة التطبيق من التطبيقات الحديثة | محرّك JS يُدمَّر مع المهمّة. وحدها خدمة أندرويد في المقدّمة المعلَنة بـ `stopWithTask="false"` تواصل العمل. |
| الصمود أمام موت العملية وإعادة تشغيل الهاتف | ‏`START_STICKY` ومستقبِل `BOOT_COMPLETED` ومراقب `AlarmManager` كلّها بُنى في الـ manifest وفي الكود الأصلي. بعد إعادة التشغيل لا يوجد أي مدخل JS إطلاقًا. |
| ‏`foregroundServiceType="location"` | أندرويد ١٤ يُسقط الخدمة حين يغيب النوع. إنّه خاصية في الـ manifest تُحسم وقت البناء. |
| الرسم فوق التطبيقات الأخرى | نافذة `TYPE_APPLICATION_OVERLAY`. ‏React Native يرسم داخل نشاطك (activity)، لا خارجه أبدًا. |
| فتح شاشة من الخلفية والهاتف مقفل | ‏full-screen intent، بالإضافة إلى استثناء إطلاق النشاط من الخلفية الذي يمنحه `SYSTEM_ALERT_WINDOW`. |
| الرنين والهاتف صامت | مسار الصوت `USAGE_ALARM`، بالإضافة إلى تركيز الصوت حتى لا يغطّي تطبيق الملاحة على الصوت. |
| طابور يصمد أمام القتل في منتصف الإرسال | يجب أن تكتبه على القرص العمليةُ نفسها التي تنفّذ الـ POST. |

**صفر ملف أصلي تلمسه من جهتك.** كل ما سبق يُثبّته ملحق الإعداد انطلاقًا من
`app.json`.

---

## التثبيت

```bash
npx expo install expo-field-agent
```

ثم في `app.json`:

```json
["expo-field-agent", {
  "tracking": {
    "url": "https://api.example.com/api/positions",
    "batchUrl": "https://api.example.com/api/positions/batch",
    "intervalSeconds": 15,
    "idleIntervalSeconds": 60,
    "distanceFilterMeters": 15,
    "batchSize": 50,
    "queueSize": 1000,
    "heartbeatSeconds": 120
  },
  "notification": {
    "channelName": "أثناء الخدمة",
    "title": "أثناء الخدمة",
    "body": "تتم مشاركة موقعك أثناء عملك.",
    "icon": "./assets/notif.png",
    "color": "#FF6B2C"
  },
  "alert": {
    "titlePattern": "مهمّة جديدة",
    "sound": "./assets/alert.wav",
    "channelName": "المهام الجديدة",
    "route": "field-agent-alert",
    "ttlSeconds": 45,
    "torch": true
  },
  "bubble": {
    "icon": "./assets/bubble.png",
    "label": "تتبّع",
    "colors": { "ok": "#1DB954", "warn": "#F5A623", "bad": "#E5484D" }
  }
}]
```

### كل المفاتيح اختيارية

لا يوجد مفتاح إجباري، ولكل مفتاح قيمة افتراضية معقولة. يكفي
`["expo-field-agent"]` بدون أي كائن خيارات: يُثبَّت الملحق بالكامل. وحده
`tracking.url` لا يمكن أن تكون له قيمة افتراضية — مرّرها هنا، أو أثناء التشغيل
عبر `start({ url })`. أي قيمة ناقصة أو غير صالحة **لا** تُفشل البناء أبدًا: يظهر
تحذير مقروء (`[expo-field-agent] …`) وتُطبَّق القيمة الافتراضية.

هذه القاعدة أهمّ ممّا تبدو. ملحق يُسقط الـ prebuild لأنّ أحدهم أخطأ في كتابة
لون هو ملحق يُحذَف من المشروع في المساء نفسه.

| المفتاح | القيمة الافتراضية | إذا أغفلته |
| --- | --- | --- |
| `tracking.url` | `null` | التتبّع يرفض أن يبدأ حتى يزوّده `start({ url })` بعنوان |
| `tracking.batchUrl` | `null` | تُرسَل النقاط واحدة تلو الأخرى إلى `url` |
| `tracking.intervalSeconds` | `15` | |
| `tracking.idleIntervalSeconds` | `60` | |
| `tracking.distanceFilterMeters` | `15` | |
| `tracking.batchSize` | `50` | |
| `tracking.queueSize` | `1000` | |
| `tracking.heartbeatSeconds` | `max(idleIntervalSeconds × 2, 120)` | أي `120` مع القيم الافتراضية |
| `tracking.exactAlarms` | `false` | يبقى `SCHEDULE_EXACT_ALARM` خارج الـ manifest، ويكتفي المراقب بمنبّه غير دقيق — راجع قسم المنبّهات الدقيقة |
| `tracking.wakeLock` | `true` | يُمسَك `PARTIAL_WAKE_LOCK` طوال جلسة التتبّع. الخدمة الأمامية لا تمنع تعليق المعالج: بدونه ينتظر النبض والإرسال أوّل استيقاظ يقرّره النظام ما إن تنطفئ الشاشة. اجعله `false` لتبادل انتظام الشاشة المقفلة ببطّارية |
| `tracking.maxAccuracyMeters` | `100` | فوق هذا الحدّ تكون القراءة ضجيجًا وتُسقَط قبل الطابور. الحدّ الأدنى `1` |
| `tracking.maxSpeedMps` | `60` | فوق هذا الحدّ تكون القفزة خللًا في GPS لا رحلة، فتُسقَط النقطة الثانية. الحدّ الأدنى `1` |
| `tracking.rejectMock` | `false` | تُحفَظ النقطة المزيّفة وتُعلَّم بـ `isMock` بدل أن تُرفَض |
| `notification.channelName` | `"Suivi en service"` | |
| `notification.title` | `"En service"` | |
| `notification.body` | `"Ta position est partagee pendant tes courses."` | |
| `notification.icon` | `null` | أيقونة التطبيق |
| `notification.color` | `"#FF6B2C"` | |
| `notification.resumeTitle` | `"Suivi interrompu"` | |
| `notification.resumeBody` | `"Android a refuse de relancer le suivi. Ouvre l'application pour reprendre."` | |
| `alert.titlePattern` | `".*"` | كل عنوان يُطلق التنبيه |
| `alert.sound` | `null` | نغمة المنبّه في النظام |
| `alert.channelName` | `"Nouvelles courses"` | |
| `alert.route` | `"field-agent-alert"` | |
| `alert.ttlSeconds` | `45` | |
| `alert.torch` | `false` | |
| `alert.forceVolume` | `true` | يُرفَع مسار المنبّه للتنبيه — وهو المسار الوحيد الذي يبقى أندرويد يشغّله في الوضع الصامت |
| `alert.volumeLevel` | `1` | يُرفَع إلى أقصى الجهاز. أرضية لا سقف: من ضبطه أعلى يحتفظ بمستواه |
| `alert.channelVersion` | `1` | |
| `alert.notificationBridge` | `false` | لا يُعلَن أي مستمع إشعارات — راجع قسم FCM لمعرفة متى تُفعّله |
| `bubble.icon` | `null` | نقطة بلون الحالة |
| `bubble.label` | `"Suivi"` | |
| `bubble.colors.ok` | `"#1DB954"` | |
| `bubble.colors.warn` | `"#F5A623"` | |
| `bubble.colors.bad` | `"#E5484D"` | |
| `bubble.colors.urgent` | `"#E5484D"` | |
| `ios.locationWhenInUsePermission` | `"Ta position sert a t'affecter les courses proches."` | |
| `ios.locationAlwaysPermission` | `"Ta position continue a etre partagee pendant tes courses, meme application fermee."` | |
| `ios.criticalAlerts` | `false` | يحتاج تصريح Apple ليكون له أي أثر |
| `rootComponent` | `"main"` | ما يسجّله `registerRootComponent` و expo-router |
| `logLevel` | `"error"` | الأخطاء وحدها تُكتَب في السجلّ؛ و`off` لا يكتب شيئًا إطلاقًا |
| `logMaxDays` | `7` | تُحذَف السطور الأقدم من ذلك عند الكتابة التالية. الحدّ الأدنى `1` |

النصوص الافتراضية الظاهرة للمستخدم بالفرنسية، لأنّها لغة المشروع الذي بُني من
أجله هذا الملحق. وهي إعداد عادي: اضبط `notification.title` و
`notification.body` و `alert.channelName` وجملتَي إذن `ios.*` بلغتك، ولن يراها
أحد أبدًا.

> **‏SDK 52 فقط، ولا علاقة له بهذا الملحق:** بعض إصدارات `expo-modules-core`
> تحمل Compose Compiler يرفض Kotlin 1.9.24 الافتراضي في Expo 52
> (`This version (1.5.15) of the Compose Compiler requires Kotlin version
> 1.9.25`). الإصلاح من جهة التطبيق:
>
> ```json
> ["expo-build-properties", { "android": { "kotlinVersion": "1.9.25" } }]
> ```
>
> وتطبيق `example/` لا يتضمّنه: حزمته المرجعية هي SDK 57، حيث لا لزوم للتثبيت.

> **‏iOS على أجهزة فيها Homebrew:** ‏CocoaPods 1.16 فوق Ruby 3.4 ينكسر إذا لم
> تكن اللغة المحلية UTF-8 (`Unicode Normalization not appropriate for
> ASCII-8BIT`). ولا علاقة لهذا أيضًا بالملحق:
>
> ```bash
> LANG=en_US.UTF-8 npx expo run:ios
> ```

الملحق يتكفّل بالجانب الأصلي كلّه: الأذونات، وخدمة المقدّمة
`type="location"`، ومستقبِلا الإقلاع والمراقبة، ونشاط التنبيه، ونسخ صوتك إلى
`res/raw` مع `noCompress`، و `UIBackgroundModes` ونصوص
`NSLocation*UsageDescription` على iOS. **لا ملف أصلي تلمسه.**

---

## من الصفر إلى تطبيق تتبّع

القسم السابق يقول ما تضعه في `app.json`. وهذا القسم يأخذك من جهاز فارغ إلى هاتف
يُرسل مواقعه فعلًا: إنشاء المشروع، والتثبيت، والـ prebuild، وسلّم الأذونات
بترتيبه الصحيح، و`App.tsx` كامل يعمل كما هو.

الدليل مكتوب على الحزمة التي **تُطوَّر عليها** الوحدة اليوم: ‏`expo@^57.0.24`، و
`react@19.2.3`، و`react-native@0.86.3`. ومع ذلك تبقى `peerDependencies` هي
`"expo": ">=52.0.0"`: ‏**SDK 57 ليس شرطًا**، والمضيفون الأقدم ابتداءً من 52 ما
زالوا مدعومين. إنّه فقط الإصدار الذي يُبنى ويُختبر عليه.

### ٠. الحقيقة التي تسبق كل شيء: ‏Expo Go لا يشغّل هذه الحزمة

`expo.modules.fieldagent.*` ليس ضمن الكود الأصلي الثابت الذي يحمله Expo Go، ولا
حزمة تغيّر ذلك. أنت بحاجة إلى **development build**: تطبيق أصلي تبنيه بنفسك.

وما يحدث داخل Expo Go تدهور لا انهيار: ‏`isAvailable` يساوي `false`، وكل مفاتيح
`getPermissions()` العشرة تساوي `'unsupported'`، و`start()` و`stop()` تُحَلّ ولا
تفعل شيئًا، و`<AlertHost>` لا يعرض شيئًا، ويُطلَق `console.warn` **واحد** عند أول
نداء متدهور لا عند كل نداء. الجدول الكامل في قسم «‏Expo Go — تدهور، لا حجب» في
الأعلى.

**واستثناء واحد مقصود:** أخطاء الوسائط تبقى ترمي في Expo Go كما في غيره
(`triggerAlert({})`، `setInterval(0)`، `setBubbleImage('')`)، لأنّها عيوب في
كودك لا قيود في المنصّة.

اربط واجهتك بـ `FieldAgent.isAvailable`. مفتاح ميّت أسوأ بكثير من شريط يقول
«التتبّع غير متاح».

### ١. أنشئ التطبيق

```bash
npx create-expo-app@latest my-field-app --template blank-typescript
cd my-field-app
```

### ٢. ثبّت الوحدة

```bash
npx expo install expo-field-agent
```

الحزمة المرجعية لهذا الدليل — إصدارات هذا المستودع نفسه، وهي ما يُبنى عليه الكود
الأصلي ويُختبر (‏`devDependencies` في الجذر، و‏`expo-status-bar` من
`example/package.json`):

```
expo                   ^57.0.24
react                  19.2.3
react-native           0.86.3
expo-status-bar        ~57.0.1
@types/react           ~19.2.0
typescript             ^5.9.3
```

و`expo-build-properties` **غائب عن قصد**: تثبيت Kotlin المذكور في قسم التثبيت
أعلاه حلّ خاص بـ SDK 52، ولا شيء في هذه الحزمة المرجعية يحتاجه. لا تضف الحزمة
إلّا إن كان لتطبيقك أنت سبب مستقلّ لها.

واختياريًا، إن أردت مشغّل قائمة المطوّر وحده:

```bash
npx expo install expo-dev-client
```

ملاحظة: ‏`example/package.json` لا يسرده أصلًا رغم أنّ أمر التشغيل فيه هو
`expo start --dev-client`؛ و`npx expo run:android` وحده ينتج بناء debug يعمل.

### ٣. اضبط `app.json`

هذه الكتلة الدنيا تعمل كما هي:

```json
{
  "expo": {
    "name": "تطبيق الميدان",
    "slug": "my-field-app",
    "scheme": "myfieldapp",
    "android": { "package": "com.example.myfieldapp" },
    "ios": { "bundleIdentifier": "com.example.myfieldapp" },
    "plugins": [
      [
        "expo-field-agent",
        {
          "tracking": {
            "url": "https://api.example.com/positions"
          },
          "notification": {
            "title": "أثناء الخدمة",
            "body": "تتم مشاركة موقعك أثناء عملك."
          },
          "ios": {
            "locationWhenInUsePermission": "يُستعمل موقعك لإسناد المهام القريبة إليك.",
            "locationAlwaysPermission": "تستمرّ مشاركة موقعك أثناء المهام، حتى والتطبيق مغلق."
          }
        }
      ]
    ]
  }
}
```

و`["expo-field-agent"]` بلا كائن خيارات إطلاقًا يُثبّت الملحق بالكامل هو أيضًا:
كل مفتاح اختياري وله قيمة افتراضية، وجدولها كامل في قسم التثبيت أعلاه. وحده
`tracking.url` بلا افتراضي — ضعه هنا أو مرّره بـ `start({ url })`، وبدون أحدهما
يرمي `start()`.

أمّا `notification.title` و`notification.body` وجملتا `ios.*` فموجودة في المثال
الأدنى لسبب واحد: **النصوص الافتراضية بالفرنسية** (`"En service"`،
`"Ta position est partagee pendant tes courses."`). اضبطها بلغتك، وإلّا قرأ
مستخدموك الفرنسية.

**ولا تنسخ `example/app.json` كما هو:** مفاتيحه كلّها من هذه الوحدة، لكنّها
مضبوطة لحاجة المثال لا لحاجتك — `tracking.url` على `https://httpbin.org/post`، و
`tracking.exactAlarms: true` (وهو ما يضيف `SCHEDULE_EXACT_ALARM` إلى بيانك)، و
`logLevel: "debug"`، ومسارات `./assets/*` لملفّات علامات مولَّدة. خذ منه البنية،
لا القيم.

### ٤. ‏prebuild — خطوة إلزامية لا اختيارية

```bash
npx expo prebuild --clean
```

لماذا هي إلزامية: ‏`expo-field-agent` هو **ملحق إعداد وكود أصلي معًا**، وكل ما
يحتاجه يعيش في ملفات المشروع الأصلية التي لا وجود لها قبل أن يولّدها الـ prebuild.

يحصل `android/app/src/main/AndroidManifest.xml` على:

- ١٤ إذن `<uses-permission>`: ‏`INTERNET`، `ACCESS_NETWORK_STATE`،
  `ACCESS_COARSE_LOCATION`، `ACCESS_FINE_LOCATION`، `ACCESS_BACKGROUND_LOCATION`،
  `FOREGROUND_SERVICE`، `FOREGROUND_SERVICE_LOCATION`، `POST_NOTIFICATIONS`،
  `SYSTEM_ALERT_WINDOW`، `USE_FULL_SCREEN_INTENT`، `ACCESS_NOTIFICATION_POLICY`،
  `RECEIVE_BOOT_COMPLETED`، `WAKE_LOCK`، `VIBRATE` — ويُضاف
  `SCHEDULE_EXACT_ALARM` وحده حين تضبط `tracking.exactAlarms: true`؛
- `<service android:name="expo.modules.fieldagent.TrackingService"`
  `android:exported="false" android:foregroundServiceType="location"`
  `android:stopWithTask="false"/>`؛
- المستقبِلات: ‏`BootReceiver` (‏`BOOT_COMPLETED` و`QUICKBOOT_POWERON` بصيغتيه و
  `MY_PACKAGE_REPLACED`)، و`WatchdogReceiver`، و`AlertActionReceiver`، و
  `ProvidersChangedReceiver` (‏`PROVIDERS_CHANGED`)؛
- نشاط `AlertActivity` مع `showWhenLocked` و`turnScreenOn` و`excludeFromRecents`
  و`launchMode="singleTask"` و`theme="@style/Theme.FieldAgent.Alert"`؛
- `<meta-data android:name="expo.modules.fieldagent.CONFIG">` واحدة تحمل الإعداد
  المحلول كلّه ككتلة JSON واحدة؛
- صوتك `alert.sound` منسوخًا إلى `res/raw/field_agent_alert.<ext>` مع إضافة
  `noCompress` إلى `app/build.gradle`.

ويحصل `ios/<Project>/Info.plist` على `UIBackgroundModes: ["location"]`، و
`NSLocationWhenInUseUsageDescription`، و`NSLocationAlwaysAndWhenInUseUsageDescription`،
و`NSLocationAlwaysUsageDescription`، وقاموس `EXFieldAgent` يقرؤه الجانب Swift؛
ويُنسَخ الصوت باسم `FieldAgentAlert.<ext>` ويُضاف إلى Copy Bundle Resources.

لا شيء من هذا كلّه يمكن بلوغه من JavaScript. وهذا وحده سبب كون الوحدة أصلية.

### ٥. شغّله على جهاز حقيقي

```bash
npx expo run:android          # يبني، ويثبّت، ويشغّل Metro
```

استعمل **هاتفًا فعليًا** لأي اختبار ذي معنى: المحاكي يزوّد مواقع مزيّفة (انظر
فخاخ أوّل مرّة أدناه)، ولا يعرف Doze ولا قاتلي المهام عند المصنّعين.

وعلى iOS:

```bash
npx expo run:ios
```

وعلى أجهزة فيها Homebrew وRuby 3.4 استعمل `LANG=en_US.UTF-8 npx expo run:ios` —
الملاحظة كاملة في قسم التثبيت أعلاه.

### ٦. الأذونات: المقدّمة أوّلًا، ثم الخلفية

يمشي `requestPermissions()` السلّم كلّه في نداء واحد، بهذا الترتيب بالضبط،
متجاوزًا كل ما هو ممنوح أصلًا:

| # | المفتاح | ما يراه المستخدم |
|---|---|---|
| ١ | `location` | حوار النظام لـ `ACCESS_FINE_LOCATION` و`ACCESS_COARSE_LOCATION` |
| ٢ | `notifications` | حوار `POST_NOTIFICATIONS` — أندرويد ١٣ فما فوق فقط |
| ٣ | `backgroundLocation` | فقط إن كان إذن المقدّمة ممنوحًا أصلًا. أندرويد ١٠ بالضبط: حوار حقيقي. أندرويد ١١ فما فوق: صفحة **معلومات التطبيق** في الإعدادات، إذ لا حوار موجود أصلًا |
| ٤ | `overlay` | شاشة `ACTION_MANAGE_OVERLAY_PERMISSION` |
| ٥ | `dndAccess` | شاشة `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` |
| ٦ | `fullScreenIntent` | شاشة `MANAGE_APP_USE_FULL_SCREEN_INTENT` — أندرويد ١٤ فما فوق فقط |
| ٧ | `batteryUnrestricted` | قائمة `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` في النظام، لا الحوار ذا النقرة الواحدة |
| ٨ | `autostart` | شاشة المصنّع، ثم `Power.markConfirmed()` بلا شرط |

كل شاشة إعدادات تُنتظَر عبر `startActivityForResult`، لأنّ لا API تنتظر صفحة
إعدادات: نتيجة النشاط — ولو كانت `RESULT_CANCELED` — هي وحدها ما يقول إنّ
المستخدم رجع. ويُحَلّ `requestPermissions()` بحزمة أذونات طازجة بعد السلّم كلّه.

**والجزء الذي يُخطئ فيه الجميع:** على أندرويد ١١ فما فوق **لا يمنح أي حوارٍ إذنَ
الموقع في الخلفية**. والنداء الواحد يأخذ المستخدم من حوار المقدّمة إلى صفحة
إعدادات لا يعرف لماذا فُتحت — وGoogle Play تشترط إفصاحًا صريحًا قبل ذلك الطلب.
لذلك اقسم السلّم بـ `skip`، كما يفعل `App.tsx` في الخطوة التالية.

وما يراه المستخدم هناك على أندرويد ١١ فما فوق هو صفحة **معلومات التطبيق**
(`ACTION_APPLICATION_DETAILS_SETTINGS`)، لا صفحة إذن الموقع مباشرةً: عليه أن
يضغط بنفسه **الأذونات ← الموقع ← السماح طوال الوقت**. ولهذا يجب أن يقول إفصاحك
هذه الكلمات بعينها.

**و`'undetermined'` ليست `'denied'`.** لا تُرجِع `runtimeState()` القيمة
`'denied'` إلّا بعد أن تُسجَّل علامة `asked_<permission>`؛ وقبل أول طلب تكون
`'undetermined'`. وحدها `'denied'` تعني «لا تسأل ثانية»: من يفرّع على
`!== 'granted'` يرمي مستخدمًا جديدًا في الإعدادات بدل أن يُريه الحوار.

### ٧. ‏`App.tsx` كامل

```tsx
import * as FieldAgent from 'expo-field-agent';
import { useEffect, useState } from 'react';
import { Button, Text, View } from 'react-native';

// شاشات البقاء: لا تُطلب عند التسجيل، بل في لحظة هادئة لاحقة.
const SURVIVAL = ['overlay', 'dndAccess', 'fullScreenIntent', 'batteryUnrestricted', 'autostart'] as const;

// شاشتك أنت: نصّ بملء الشاشة يُحَلّ حين يضغط المستخدم «متابعة».
const showDisclosure = async () => {};

export default function App() {
  const [points, setPoints] = useState(0);
  const [running, setRunning] = useState(false);

  useEffect(() => {
    const subs = [
      FieldAgent.addListener('position', () => setPoints((n) => n + 1)),
      FieldAgent.addListener('error', (e) => console.warn(e.code, e.message)),
    ];
    return () => subs.forEach((s) => s.remove());
  }, []);

  async function goOnDuty() {
    if (!FieldAgent.isAvailable) return;   // Expo Go: قُلها في الواجهة، لا تتظاهر

    // ١ — المقدّمة وحدها: حوار الموقع، ثم حوار الإشعارات على أندرويد ١٣ فما فوق.
    let perms = await FieldAgent.requestPermissions({ skip: ['backgroundLocation', ...SURVIVAL] });
    if (perms.location !== 'granted') return;   // لا شيء بعدها يستحقّ السؤال

    // ٢ — إفصاحك أنت، بكلماتك: «في الشاشة التالية اختر الموقع ← السماح طوال الوقت.»
    await showDisclosure();

    // ٣ — الخلفية وحدها: على أندرويد ١١ فما فوق تُفتح صفحة معلومات التطبيق،
    //     ويُحَلّ الوعد حين يعود المستخدم منها.
    perms = await FieldAgent.requestPermissions({ skip: ['location', 'notifications', ...SURVIVAL] });
    if (perms.backgroundLocation !== 'granted') {
      await FieldAgent.openSettings('backgroundLocation');   // الصفحة نفسها، اختصارًا
    }

    await FieldAgent.setAuthHeader('Bearer …');   // مشفَّر (Keystore / Keychain)
    try {
      await FieldAgent.start();                   // أو start({ url }) لتجاوز app.json
      setRunning(true);
    } catch (e) {
      // SERVICE_START: أندرويد رفض البدء من الخلفية. اعرضه، ولا تعتبره نهائيًا —
      // المراقب ومستقبِل الإقلاع وأوّل عودة إلى المقدّمة كلّها ستستأنف التتبّع.
      console.warn(e);
    }
    await FieldAgent.showBubble();                // false على iOS، عن قصد
  }

  async function goOffDuty() {
    await FieldAgent.stop();                      // لا يرمي أبدًا
    await FieldAgent.hideBubble();
    setRunning(false);
  }

  return (
    <View style={{ padding: 24, gap: 12 }}>
      <Text>{FieldAgent.isAvailable ? `النقاط المستلَمة: ${points}` : 'التتبّع غير متاح (Expo Go)'}</Text>
      <Button title={running ? 'إنهاء الخدمة' : 'بدء الخدمة'} onPress={running ? goOffDuty : goOnDuty} />
    </View>
  );
}
```

وشاشات البقاء تُطلب لاحقًا، في نداء رابع منفصل:

```ts
await FieldAgent.requestPermissions({ skip: ['location', 'backgroundLocation', 'notifications'] });
```

السلّم **idempotent**: ما هو ممنوح يُتجاوَز، فإعادة النداء بعد منح جزئي لا تُعيد
سؤال المستخدم إلّا عمّا ينقص.

### ٨. تحقّق أنّه يعمل فعلًا

```bash
adb shell dumpsys activity services <your.package> | grep isForeground   # المتوقّع isForeground=true
```

والوحدة **لا تكتب في logcat شيئًا** عن قصد: ‏logcat حلقة يعيد النظام تدويرها في
دقائق، والأعطال التي تستحقّ القراءة تقع قبل ساعات من وصل الهاتف بـ adb. سجلّها
على الجهاز نفسه: اقرأه بـ `getLog()`، وصدّره بـ `exportLog()` (وكلاهما أندرويد
فقط)، وارفع `logLevel` فوق قيمته الافتراضية `error` في `app.json` إن أردت أكثر
من الأخطاء.

وجدول «التحقّق» أدناه فيه اثنا عشر سيناريو، كلّها قابلة للتشغيل من تطبيق
`example/`.

### فخاخ أوّل مرّة

| الفخّ | ما يحدث فعلًا |
|---|---|
| `http://` في `tracking.url` | يعمل في بناء debug ويموت صامتًا في الإصدار: قالب Expo يضع `usesCleartextTraffic` في `src/debug` وحده، والوحدة لا تعلنه إطلاقًا. استعمل `https://`، أو أضف network security config لمضيف التطوير. و`10.0.2.2` في تطبيق `example/` هو جهازك كما يراه المحاكي، ولا يعمل إلّا لهذا السبب |
| محاكي أندرويد و`rejectMock: true` | كل قراءة من المحاكي مزيّفة: تُرفَض كل النقاط، ويُطلَق خطأ `MOCK_LOCATION` **مرّة واحدة** ثم صمت، فيبدو التطبيق يعمل والطابور فارغ ولا شيء يُرسَل. أبقِه `false` (وهو الافتراضي) خارج الآثار التعاقدية |
| تغيير `exactAlarms` أو `notificationBridge` بلا prebuild جديد | ‏`expo prebuild` يعيد استعمال `android/` الموجود، و**عدم كتابة** عقدة ليس مثل **حذفها**. الملحق يسحب فعليًا ما ألغيته، لكن فقط حين يعمل الـ prebuild — وإلّا بقيت تشحن ما أطفأته للتوّ |
| `notification.icon` ليست أحادية اللون بقناة ألفا | مربّع أبيض في شريط الحالة. يتحقّق الملحق من وجود الملفّ وكونه `.png` فقط، ولا يرى ما بداخله. المطلوب PNG أحادي اللون بـ 24dp مع ألفا |
| التتبّع يموت ليلًا | ابدأ بـ `batteryUnrestricted` و`autostart`، لا بـ GPS. ولا توجد API تُطفئ تحسين البطارية ولا قاتل المهام عند المصنّع؛ كل ما تستطيعه الوحدة فتح الشاشة الصحيحة على العلامة الصحيحة. و`autostart: 'granted'` تعني «أريناه الشاشة»، لا «الخيار مفعَّل» |
| قيمة خاطئة في `app.json` | **لا تُفشل البناء أبدًا**: سطر `[expo-field-agent] …` على stderr ثم القيمة الافتراضية — بما في ذلك المفاتيح المجهولة (`cle inconnue "tracking.intervalSecond"…`). خطأ مطبعي مُتجاهَل بصمت لا يُميَّز عن ميزة معطوبة، فاقرأ خرج الـ prebuild |
| `am force-stop` في الاختبار | ليس حالة اختبار: تطبيق أُوقف قسرًا لا يستقبل شيئًا إطلاقًا — لا بثًّا ولا منبّهًا ولا إقلاعًا. استعمل `am kill` أو الإزاحة من التطبيقات الحديثة |

### وعلى iOS — ما يتغيّر في هذا الدليل

كل ما سبق يُترجَم ويعمل على iOS، والقدرات الغائبة تُعيد قيمة صريحة (`false`،
`"unsupported"`) لا استثناءً، فيخدم مسار كود واحد المنصّتين. لكنّ وعد هذا الدليل
— تتبّعًا يصمد أمام كل شيء — وعدٌ أندرويديّ. وجدول الحدود الكامل أدناه؛ وهذا ما
يتغيّر في الخطوات نفسها:

| الخطوة | على iOS |
|---|---|
| ٤ — ‏prebuild | يكتب `UIBackgroundModes: ["location"]` وجمل `NSLocation*UsageDescription` الثلاث من `ios.locationWhenInUsePermission` و`ios.locationAlwaysPermission` — **وفقط إن لم تكن المفاتيح مضبوطة أصلًا**؛ القيمة الموجودة في إعدادك تفوز |
| ٦ — الأذونات | ‏`requestWhenInUseAuthorization()` أوّلًا، ثم `requestAlwaysAuthorization()` **بعد منح الأولى وحدها** — السلّم نفسه بآليّة أخرى — ثم إذن الإشعارات (مع `.criticalAlert` إن فُعّل `ios.criticalAlerts`). ولكل انتظار مهلة ٦٠ ثانية، لأنّ حوار النظام قد يُغلَق دون أن تتغيّر الحالة، ونداء إذن لا يُحَلّ أبدًا هو مضيف عالق على مؤشّر تحميل |
| ٦ — `getPermissions()` | **تسعة مفاتيح لا عشرة**: `exactAlarm` غائب تمامًا، فيقرأ `undefined` لا `'unsupported'`. أي واجهة تمرّ على قائمة العشرة الثابتة تعرض خانة فارغة هناك |
| ٦ — `openSettings(which)` | يتجاهل وسيطه: وجهة واحدة فقط (`UIApplication.openSettingsURLString`). لا تَعِد المستخدم برابط عميق إلى مفتاح بعينه |
| ٦ — «السماح أثناء استخدام التطبيق» | ‏`location: 'granted'` و`backgroundLocation: 'denied'` — لا `'undetermined'` |
| ٧ — `start()` | الحارسان نفسهما (‏URL ناقصة، إذن ناقص). والتشغيل على `authorizedWhenInUse` يبدأ فعلًا ثم يُطلق `BACKGROUND_LOCATION_LOST` فورًا: لا تحديثات في الخلفية ولا مراقبة للتغيّرات الكبيرة |
| ٧ — الإشعار الدائم «أثناء الخدمة» | **غير موجود**: لا خدمة مقدّمة ولا قناة. لا شيء يخبر المستخدم أنّه يُتتبَّع سوى مؤشّر الموقع في النظام |
| ٧ — `showBubble()` و`setStrings()` | ‏`false`، وبلا أثر: لا فقاعة ولا قناة ولا إشعار يُعاد تسميته. وجملتا الإذن تأتيان من `Info.plist` ويقرؤهما النظام بلغة الهاتف، لا بلغة تطبيقك |
| `setInterval(seconds)` | لا يضبط إيقاعًا: ‏iOS يسلّم عند الحركة، فيحرّك **مرشّح المسافة** بدلًا من ذلك |
| `tracking.rejectMock` | بلا أثر: لا يكشف `CLLocation` علَمًا كهذا، ولا يُرسَل `is_mock` |
| ٨ — الصمود | لا إعادة تشغيل بعد موت العملية ولا بعد إقلاع الهاتف. ما يبقى هو `startMonitoringSignificantLocationChanges()` أثناء `authorizedAlways` — الآلية الوحيدة القادرة على إعادة إطلاق تطبيق أُنهي — وبعد الإغلاق القسري لا شيء إطلاقًا |

السطر الذي يلخّص القسم: على أندرويد هذه الوحدة **ضمان بقاء**؛ وعلى iOS **تتبّع
خلفية بأفضل جهد** مع قائمة صريحة بما تحجبه المنصّة. عِد مستخدمي iOS بالثاني، ولا
تَعِدهم بالأوّل أبدًا.

---

## دمج كامل

```tsx
import * as FieldAgent from 'expo-field-agent';
import { AlertHost } from 'expo-field-agent';
import { useEffect } from 'react';

export default function App() {
  useEffect(() => {
    const sub = FieldAgent.addListener('error', (e) => console.warn(e.code, e.message));
    return () => sub.remove();
  }, []);

  async function goOnDuty(token: string) {
    await FieldAgent.requestPermissions();             // بالترتيب الذي يفرضه أندرويد
    await FieldAgent.setAuthHeader(`Bearer ${token}`); // مشفَّر (Keystore / Keychain)
    await FieldAgent.start();                          // idempotent
    await FieldAgent.showBubble();                     // false على iOS، عن قصد
  }

  return (
    <>
      <MyApp onGoOnDuty={goOnDuty} />
      <AlertHost
        render={(alert, actions) => (
          <MyOfferScreen alert={alert} onAccept={actions.dismiss} onDecline={actions.dismiss} />
        )}
      />
    </>
  );
}
```

يُركَّب `AlertHost` مرّة واحدة ويُعرَض فوق كل شيء. وهو يعمل في الحالتين اللتين
قد يصل بهما التنبيه:

- **التطبيق مفتوح أصلًا** — يُطلَق حدث `alert` وتظهر الشاشة؛
- **التطبيق مغلق أو الهاتف مقفل** — يفتح نشاط ملء الشاشة من تلقاء نفسه، ويشغّل
  محرّك React Native، ويقرأ `AlertHost` التنبيه **بشكل متزامن** عند أول رسم له
  (`getPendingAlertSync()`). لا سباق بين تحميل الحزمة وإطلاق الحدث: الجانب
  الأصلي **يحتفظ** بالتنبيه ويجعله قابلًا للقراءة، لا يكتفي ببثّه.

---

## واجهة البرمجة

```ts
// الأذونات -----------------------------------------------------------------
FieldAgent.getPermissions(): Promise<Permissions>;
FieldAgent.requestPermissions(opts?: { skip?: (keyof Permissions)[] }): Promise<Permissions>;
FieldAgent.openSettings(which: keyof Permissions): Promise<void>;

// التتبّع -------------------------------------------------------------------
FieldAgent.start(options?: Partial<TrackingOptions>): Promise<void>;   // idempotent
FieldAgent.stop(): Promise<void>;
FieldAgent.isRunning(): Promise<boolean>;
FieldAgent.setAuthHeader(value: string | null): Promise<void>;
FieldAgent.setInterval(seconds: number): Promise<void>;                // أثناء التشغيل
FieldAgent.flush(): Promise<{ sent: number; queued: number }>;
FieldAgent.getState(): Promise<TrackingState>;
FieldAgent.getOdometer(): Promise<number>;                             // بالأمتار، أندرويد فقط
FieldAgent.resetOdometer(): Promise<void>;                             // أندرويد فقط

// السجلّ (أندرويد فقط) ------------------------------------------------------
FieldAgent.getLog(opts?: { limit?: number; sinceMs?: number }): Promise<LogEntry[]>;  // الأحدث أولًا
FieldAgent.clearLog(): Promise<void>;
FieldAgent.exportLog(): Promise<string | null>;                        // مسار الملفّ المكتوب

// الفقاعة ------------------------------------------------------------------
FieldAgent.showBubble(): Promise<boolean>;                             // false على iOS أو بدون الإذن
FieldAgent.hideBubble(): Promise<void>;
FieldAgent.setBubbleState(s: 'ok' | 'warn' | 'bad' | 'urgent', text?: string): Promise<void>;
FieldAgent.setBubbleImage(source: string | number | null): Promise<void>;  // fichier local, null = bubble.icon

// Langue ------------------------------------------------------------------
FieldAgent.setStrings(values: FieldAgentStrings | null): Promise<void>;    // null = retour a app.json

// التنبيه ------------------------------------------------------------------
FieldAgent.triggerAlert({ title, body?, data?, tag?, channelId? }): Promise<void>;
FieldAgent.dismissAlert(): Promise<void>;
FieldAgent.setAlertSound(enabled: boolean): Promise<void>;             // كتم من جهة التطبيق
FieldAgent.getPendingAlert(): Promise<AlertPayload | null>;
FieldAgent.getPendingAlertSync(): AlertPayload | null;

// الأحداث ------------------------------------------------------------------
FieldAgent.addListener('position' | 'sent' | 'error' | 'alert' | 'bubblePress' | 'providerChange', cb): Subscription;
```

يحمل `Permissions` عشرة مفاتيح:

| المفتاح | ما هو |
|---|---|
| `location` | `ACCESS_FINE_LOCATION` |
| `backgroundLocation` | «السماح طوال الوقت» |
| `notifications` | `POST_NOTIFICATIONS` (أندرويد ١٣ فما فوق) |
| `overlay` | `SYSTEM_ALERT_WINDOW` — الفقاعة، **و** استثناء الإطلاق من الخلفية |
| `batteryUnrestricted` | قائمة تحسين البطارية في النظام |
| `dndAccess` | `ACCESS_NOTIFICATION_POLICY` |
| `fullScreenIntent` | **إضافة** — أندرويد ١٤ يضع `setFullScreenIntent` خلف إذن خاص. بدونه يتراجع تنبيه شاشة القفل بصمت إلى إشعار عادي، لذلك جُعلت الحالة ظاهرة بدل أن تُفترض. |
| `notificationAccess` | **إضافة** — شاشة الوصول إلى الإشعارات في النظام، ولا يلزم إلّا لـ `alert.notificationBridge` الاختياري. قيمتها `unsupported` ما لم تُفعّل الجسر. |
| `autostart` | **إضافة** — شاشة التشغيل التلقائي عند الشركة المصنّعة. لا توجد API تقرأها: `granted` بعد أن يُرسَل المستخدم إليها، و`undetermined` قبل ذلك، و`unsupported` على علامة تجارية بلا شاشة معروفة. |
| `exactAlarm` | **إضافة** — `SCHEDULE_EXACT_ALARM`، ولا يلزم إلّا لـ `tracking.exactAlarms` الاختياري. قيمته `unsupported` ما لم تُفعّل الخيار، لأنّ الإذن بدونه ليس في الـ manifest أصلًا، وإرسال المستخدم ليمنحه لن يمنح شيئًا. وبعد التفعيل: `granted` تحت أندرويد ١٢ حيث يكون المنبّه دقيقًا بلا طلب، ثم `granted` / `denied` بحسب `canScheduleExactAlarms()`. |

### حين يختفي إذن الموقع في الخلفية أثناء الخدمة

يمكن سحب `backgroundLocation` والخدمة تعمل أصلًا: مفتاح يُبدَّل يدويًا، أو
استعادة من نسخة احتياطية، أو إعادة الضبط التلقائية التي يطبّقها أندرويد على
تطبيق لم يُستعمل منذ أشهر، أو سائق يختار «أثناء استخدام التطبيق» على iOS.
والمنصّة لا تُبلّغ عن ذلك كإخفاق: يتوقّف مزوّد الموقع المدمج ببساطة عن تسليم
النقاط بمجرّد خروج التطبيق من المقدّمة، وiOS يمسح
`allowsBackgroundLocationUpdates` من تلقاء نفسه. لا استثناء، ولا نداء راجع، ولا
خطأ — يختفي السائق من الخريطة وحسب.

المنصّتان تُطلقان الآن حدث `error` برمز **`BACKGROUND_LOCATION_LOST`** لحظة
انتباههما، ومرّة واحدة فقط ما لم يعد الإذن. أندرويد يفحص عند كل بدء للخدمة وعند
كل نبضة؛ وiOS عند كل تغيّر في التصريح. تعامل معه بوصفه صاخبًا: لم يعد يوم العمل
مسجَّلًا، ولا أحد غير السائق يستطيع إصلاحه من إعدادات النظام.

مفتاحان زيادة على العقد الأصلي، لأنّ التنبيه والتتبّع بدونهما ينكسران على
أندرويد ١٤ فما فوق وعلى MIUI/EMUI/ColorOS **دون أن يقولا شيئًا**.

### المنبّهات الدقيقة — `SCHEDULE_EXACT_ALARM` وحده، وبطلب منك وحدك

في أندرويد إذنان للمنبّه الدقيق، ولن يُعلن هذا الملحق سوى واحد منهما أبدًا. وهذه
سياسة، لا سهو.

`USE_EXACT_ALARM` يُمنَح عند التثبيت ولا يسأل المستخدم شيئًا، ولذلك بالضبط
تحجزه Google Play للمنبّهات والمؤقّتات والتقاويم. وتطبيق توصيل يشحنه يرى إصداره
مرفوضًا. لا يُعلَن هنا **أبدًا**، فُعّل الخيار أو لم يُفعَّل: الرفض نفسه، وللسبب
نفسه، الذي يُرفَض به `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` الذي لم يطلبه هذا
الملحق يومًا ولن يطلبه.

أمّا `SCHEDULE_EXACT_ALARM` فهو الذي يمنحه المستخدم من شاشة إعدادات. ولا يدخل
الـ manifest **إلّا** حين تضبط `tracking.exactAlarms: true`، تمامًا كما يفعل
`alert.notificationBridge`: إذن لم يطلبه المضيف يعني مراجعة متجر لم يوقّع عليها.

```json
["expo-field-agent", { "tracking": { "exactAlarms": true } }]
```

**ما الذي يشتريه هذا الخيار فعلًا.** منبّه المراقب هو ما يوقظ العملية كل ١٥
دقيقة تقريبًا ليتأكّد أنّ التتبّع ما زال حيًّا ويعيد تشغيل الخدمة إن لم يكن.
ومنذ أندرويد ١٢ صار بدء خدمة مقدّمة من الخلفية ممنوعًا خارج قائمة قصيرة من
الاستثناءات — والمنبّه غير الدقيق `setAndAllowWhileIdle` **ليس** في تلك القائمة،
بينما الدقيق `setExactAndAllowWhileIdle` فيها. بدون الخيار يستيقظ المراقب ويحاول
كالعادة، لكنّه يُرفَض أكثر، فتنتظر الاستعادة أوّل عودة إلى المقدّمة. ومعه تُقبل
الاستعادة في حينها. هذا هو الفرق كلّه: ليس الدقّة، بل الإذن بالتصرّف.

اطلبه كأي وصول خاص آخر:

```ts
const { exactAlarm } = await FieldAgent.getPermissions();
if (exactAlarm === 'denied') await FieldAgent.openSettings('exactAlarm');
```

وهو قرار يُتَّخذ عند البناء. وخلافًا لمفاتيح الإيقاع، لا يُفعَّل من `start()`:
يجب أن يكون الإذن في الـ manifest قبل شحن التطبيق، ولا شيء أثناء التشغيل يضعه
هناك.

والمسار غير الدقيق تراجُع لا `else`: يمكن سحب الإذن بين نبضتين، لذلك يُعاد قراءة
`canScheduleExactAlarms()` عند كل تسليح، و`SecurityException` تسقط مباشرةً على
المنبّه غير الدقيق بدل أن نخسر المراقب كلّه.

### ما الذي يُعيده `getState()`

```ts
type TrackingState = {
  running: boolean;
  queued: number;                            // نقاط تنتظر على القرص
  lastFixAt: number | null;                  // ميلي ثانية Unix، من القراءة نفسها
  lastSentAt: number | null;                 // ميلي ثانية Unix لآخر إرسال مقبول
  lastError: string | null;                  // «CODE: message» — الأخير فقط، لا تاريخ كامل
  lastErrorAt: number | null;                // متى وقع ذلك الخطأ
  provider: 'fused' | 'manager' | 'none';    // ما يلتقط فعلًا
  locationEnabled: boolean;                  // مفتاح الموقع في النظام
};
```

المفاتيح الثلاثة الأخيرة إضافات، وكلٌّ منها يجيب عن سؤال لم تكن له إجابة من
الخارج:

- **`lastErrorAt`** — خطأ بلا تاريخ لا يمكن فرزه. «الطابور ممتلئ» قبل ثلاثة
  أيّام و«الطابور ممتلئ» قبل ثلاث ثوانٍ نصّ واحد، وواحد منهما فقط حادثة. والتحذير
  لا يدهس هذا الزوج أبدًا؛ الخطأ الحقيقي وحده يفعل.
- **`provider`** — `'fused'` يعني Google Play Services، و`'manager'` يعني
  التراجع إلى `LocationManager` على جهاز لا يملكها (أجهزة Huawei الحديثة، أنظمة
  مجرَّدة)، و`'none'` حين لا شيء يعمل. لم يكن الاثنان يومًا الشيء نفسه ولم يكن
  شيء يقول أيّهما لديك؛ صار للإيقاع الأخشن سبب معروف.
- **`locationEnabled`** — مفتاح النظام. قيمته `false` تفسّر غياب النقاط وحدها،
  وهي أوّل ما يُنظر فيه قبل اتّهام الخدمة.

**وعلى iOS تغيب هذه الثلاثة عن الحمولة وتقرأ `undefined`.** يُعيد متتبّع iOS
المفاتيح الخمسة الأصلية. عاملها كخاصّة بأندرويد إلى أن يتغيّر ذلك.

### `providerChange` — حين يُطفأ الموقع نفسه

أندرويد فقط. سائق يسحب شريط الإعدادات ويطفئ مربّع الموقع، أو يشغّل وضع الطيران،
يختفي من الخريطة والخدمة ما زالت تعمل وما زالت خضراء: يتوقّف المزوّد المدمج عن
التسليم ببساطة، بلا نداء راجع وبلا استثناء. وبثّ `PROVIDERS_CHANGED` هو الإشارة
الوحيدة الموجودة.

```ts
FieldAgent.addListener('providerChange', ({ enabled, gps, network }) => {
  if (!enabled) showBanner('الموقع مطفأ — لا يُسجَّل شيء.');
});
```

قيمة `enabled` هي `gps || network`؛ والعلَمان موجودان للحالة التي يختفي فيها
مزوّد واحد فقط. وحين يُطفأ الموقع والتتبّع مطلوب، يخرج كذلك حدث `error` برمز
**`LOCATION_OFF`**. وحين يعود، تعيد الخدمة طلب التحديثات من تلقاء نفسها — فالمزوّد
المدمج لا يستأنف طلبًا سقط أثناء الانقطاع — فلا شيء على المضيف أن يفعله.

ولا يوجد في iOS بثّ مكافئ، و**لا يُطلق هذا الحدث أبدًا**. المستمع هناك خامل، لا
خاطئ.

### السجلّ الأصلي — `getLog()` و`clearLog()` و`exportLog()`

أندرويد فقط. الأعطال التي تستحقّ القراءة تقع على هاتف داخل شاحنة، قبل ساعات من
وصله بـ adb، و logcat حلقة يعيد النظام تدويرها في دقائق. هذا السجلّ جدول SQLite
في تخزين التطبيق نفسه، تكتبه **الخدمة**، أي أنّه يظلّ يسجّل عبر موت العملية
وإعادة الإقلاع ويوم خدمة كامل بلا أي JavaScript في أي مكان.

```ts
const entries = await FieldAgent.getLog({ limit: 100 });
// [{ at: 1758546185123, level: 'error', code: 'FOREGROUND', message: '…' }, …]

const path = await FieldAgent.exportLog();   // ملفّ في الذاكرة المؤقّتة، الأقدم أولًا، أو null
await FieldAgent.clearLog();
```

- الأحدث أولًا، و`limit` افتراضه `500` و`sinceMs` افتراضه `0`. ويُتحقَّق منهما قبل
  أي شيء آخر: `limit` غير صحيح، أو أقل من ١، أو `sinceMs` سالب أو غير منتهٍ —
  كلّها **ترمي استثناءً**، في Expo Go كما في غيره، لأنّها عيوب في كودك لا قيود
  منصّة.
- يكتب `exportLog()` السجلّ كلّه في `cacheDir/field-agent/log-export.txt`، سطرًا
  لكل مدخلة، الأقدم أولًا — وهو الترتيب الذي تُقرأ به حادثة — ويُعيد المسار
  المطلق، أو `null` إن فشلت الكتابة. والتوقيت بـ UTC وبلغة محلّية ثابتة، حتى لا
  يسلّم هاتف مضبوط على العربية أرقامًا عربية شرقية، ولا يختم هاتف في تونس ‎+01‎
  بجانب خطّ زمني في الخادم بـ UTC.
- و`logLevel` هو الأرضية: `error` افتراضًا، و`off` لا يكتب شيئًا إطلاقًا. سجلّ
  يدوّن كل قراءة هو سجلّ لا يقرأه أحد وقاعدة تكبر من تلقاء نفسها.
- والاحتفاظ حدّان معًا: تُحذَف السطور الأقدم من `logMaxDays` عند الكتابة التالية،
  ويُقلَّم الجدول إلى سقف صلب قدره **١٠ ٠٠٠ سطر** غير قابل للضبط. وقرص ممتلئ أو
  قاعدة تالفة لا يُسقطان الخدمة أبدًا؛ تضيع الكتابة وحسب.
- و**لا تدخله أي نقطة موقع**، ولا أي ترويسة مصادقة. فالتصدير يغادر الجهاز لحظة
  يضغط أحدهم الزرّ، والموقع بيانات شخصية. ما يدخله رموز وأسباب:
  `FOREGROUND`، `LOCATION_OFF`، `NO_FIX`، `STALE`، `OFFLINE`، `PROVIDER`.

### عدّاد المسافة

أندرويد فقط. أمتار تُراكَم في الجانب الأصلي منذ آخر تصفير، عبر موت العمليات
وإعادة الإقلاع:

```ts
const metres = await FieldAgent.getOdometer();
await FieldAgent.resetOdometer();            // في بداية الخدمة مثلًا
```

لا يحسب إلّا الخطوات بين نقاط **أبقاها** مرشّح الجودة، ويتجاهل كل خطوة أقصر من
أسوأ الدقّتين: ضجيج GPS لدرّاجة مركونة ليلًا كان سيُحمّلها عشرات الكيلومترات حتى
الصباح. فهو إذًا أرضية لا عدّاد فوترة — النفق أو فقد الإشارة مسافة لا يدّعي أنّه
رآها.

### جودة النقاط — الدقّة والسرعة والمواقع المزيّفة

ثلاثة مفاتيح تقرّر ما يقبله المرشّح، وهي إعدادات لا ثوابت، لأنّ درّاجة في مدينة
مكتظّة وشاحنة على طريق سريع لا تتّفقان على معنى القفزة المستحيلة:

| المفتاح | الافتراضي | ما يرفضه |
|---|---|---|
| `tracking.maxAccuracyMeters` | `100` | قراءة تصفها المنصّة نفسها بأسوأ من ذلك: تخمين من برج اتّصال، لا موقع |
| `tracking.maxSpeedMps` | `60` | نقطتان تفترضان سرعة أعلى — ٦٠ م/ث تساوي ٢١٦ كم/س، وفوقها قفزة GPS لا مركبة |
| `tracking.rejectMock` | `false` | مع `true`: كل قراءة يعلّمها أندرويد بأنّها من مزوّد مزيّف |

وأمران يقعان مهما كان إعدادك. صارت كل نقطة تحمل `isMock` في حدث `position` و
`is_mock` في حمولة الإرسال، كي يحسم خادمٌ يفوتر بالكيلومتر بنفسه بدل أن يُحسم
القرار على الهاتف. ولم تعد حواجز المعقولية تقيس الزمن المنقضي بساعة الحائط حين
تحمل النقطتان ساعة تشغيل الجهاز: ساعة الحائط هي بالضبط ما يستطيع سائق تقديمه من
الإعدادات ليشتري استثناء «النفق» ويمرّر قفزة مستحيلة؛ أمّا ساعة التشغيل فلا.

ومع `rejectMock: true`، ترفع أوّل قراءة مزيّفة مرفوضة حدث `error` برمز
**`MOCK_LOCATION`** — مرّة واحدة لكل تشغيل، لأنّ الاحتيال يجب أن يُرى، لكنّ فيضًا
من السطور لا ينفع أحدًا.

### حمولة التنبيه

```ts
type AlertPayload = {
  id: string;            // لتغلق هذا التنبيه وحده
  title: string;
  body?: string;
  data?: Record<string, unknown>;
  receivedAt: number;    // ميلي ثانية Unix، مختومة في الجانب الأصلي
  route: string;         // ‏`alert.route` من app.json، كما هي
};
```

يُنقَل `route` نقلًا فحسب: لا يحتاجه `AlertHost`، لكن تطبيقًا يفضّل التنقّل
(expo-router) بدل تركيب شاشة فوق أخرى يجده جاهزًا دون أن يعيد قراءة إعداداته.

### ‏`data` والمواقع

يقبل `triggerAlert({ data })` أي كائن؛ يعبر إلى الجانب الأصلي على شكل JSON
ويعود محلَّلًا في `alert.data`. و`data.silent === true` يكتم صوت هذا التنبيه
وحده.

الموقع **لا يُكتب أبدًا** في السجلّات، لا على أندرويد ولا على iOS: إنّه بيانات
شخصية.

---

## اللغة، وصورة الفقاعة

### `setStrings()` — لغة التطبيق، لا لغة الهاتف

كل نصّ يعرضه الملحق للسائق قابل للاستبدال أثناء التشغيل، من الترجمات التي
يملكها تطبيقك أصلًا:

```ts
await FieldAgent.setStrings({
  serviceChannelName: 'التتبّع أثناء الخدمة',
  serviceTitle: 'أثناء الخدمة',
  serviceBody: 'تتم مشاركة موقعك أثناء عملك.',
  alertChannelName: 'المهام الجديدة',
  alertChannelNameSilent: 'المهام الجديدة (صامت)',
  dismiss: 'تجاهل',
  bubbleLabel: 'تتبّع',
  bubbleAccessibility: '%s — اضغط لفتح التطبيق',
  resumeTitle: 'توقّف التتبّع',
  resumeBody: 'رفض أندرويد إعادة تشغيل التتبّع. افتح التطبيق للمتابعة.',
});
```

كل مفتاح اختياري: ما تُغفله يحتفظ بقيمة `app.json`، و`setStrings(null)` يزيلها
كلها. نادِها مرّة عند الإقلاع، ثم عند كل تغيير للغة.

**لماذا أثناء التشغيل بدل `values-ar/strings.xml`.** المورد حسب اللغة يتبع
*الهاتف*. وتطبيق التوصيل يحمل غالبًا منتقي لغة خاصًّا به، وكون الهاتف بالفرنسية
لا يقول شيئًا عن سائق اختار العربية داخل التطبيق. هذه هي الآلية الوحيدة التي
تتبع التطبيق.

**وهو محفوظ عن قصد.** الخدمة تعود بعد إعادة التشغيل دون أن يعمل أي JavaScript؛
ولغة تعيش في الذاكرة كانت ستعود إلى الافتراضي، فيجد السائق إشعارًا بلغة لم
يخترها قط.

**وتُعاد تسمية القنوات فورًا.** قناة الإشعارات تُجمّد أهميّتها وصوتها واهتزازها
عند الإنشاء — لكن ليس اسمها، وإعادة إنشائها بالمعرّف نفسه تحدّث ذلك بالضبط.
بدون هذه المرحلة، السائق الذي ينتقل إلى العربية كان سيبقى أمام اسم قناة
بالفرنسية في إعدادات النظام حتى إلغاء التثبيت. وإشعار الخدمة الجاري يُعاد بناؤه
في النداء نفسه.

**ما لا يفعله.** حدث `error` يحمل `code` ثابتًا (`OFFLINE`، `VOLUME`،
`QUEUE_FULL`…) و`message` موجّهًا للمطوّر: ترجم انطلاقًا من الرمز، والرسالة
لسجلّاتك. وعلى iOS لا أثر له: لا إشعار خدمة، ولا قناة، ولا فقاعة، وجملتا إذن
الموقع تُقرآن من `Info.plist` بلغة الهاتف — ترجمهما عبر `InfoPlist.strings`، ولا
نداء أثناء التشغيل يغيّرهما.

### `setBubbleImage()` — تبديل الصورة أثناء الخدمة

`bubble.icon` في `app.json` هي الصورة المضمَّنة وقت البناء. وهذه تغيّرها والتطبيق
يعمل — نوع المهمّة، أو صورة نزّلها كودك للتو:

```ts
await FieldAgent.setBubbleImage('file:///data/user/0/…/client.jpg');
await FieldAgent.setBubbleImage(null);   // العودة إلى bubble.icon
```

تقبل uri بصيغة `file://`، أو مسارًا مطلقًا، أو uri بصيغة `content://`، أو ناتج
`require('./x.png')`. **مصادر محلّية فقط** — التنزيل من شأن التطبيق المضيف، فهو
صاحب المصادقة والتخزين المؤقّت وسياسة إعادة المحاولة، والفقاعة يجب أن تبقى نافذة
خفيفة. والمصدر `http(s)` يُرفض بحدث `error` برمز `BUBBLE_IMAGE`، لا يُتجاهل
بصمت.

تُقرأ الأبعاد قبل البكسلات: صورة باثني عشر ميغابكسل تُختزَل بدل أن تُفكّ كاملة
في حيّز مقداره 24dp. والصورة التي يفشل تحميلها تتراجع إلى نقطة الحالة، لا إلى
فراغ. والمسار محفوظ، فتستعيده الفقاعة حين تعود الخدمة بلا JS.

**في development build، ناتج `require()` يخدمه Metro عبر http** فيُرفض، مع رسالة
تقول ذلك. الصور المضمَّنة مكانها `bubble.icon`، وهي drawable حقيقي في كل أنواع
البناء.

---

## حدود كل منصّة — الحقيقة

| القدرة | أندرويد | iOS |
|---|---|---|
| التتبّع في الخلفية | ✅ خدمة مقدّمة `type="location"` | ✅ `UIBackgroundModes: location`، `allowsBackgroundLocationUpdates`، `pausesLocationUpdatesAutomatically = false` |
| الصمود أمام إزاحة التطبيق | ✅ `stopWithTask=false`، و`onTaskRemoved` لا يوقف شيئًا | ⚠️ نعم، ما دام التطبيق لم يُغلَق قسرًا (force quit) |
| الصمود أمام موت العملية | ✅ `START_STICKY` مع مراقب `AlarmManager` كل ١٥ دقيقة تقريبًا | ⚠️ **لا شيء يُعيد تشغيله**، إلّا تغيّرات الموقع الكبيرة (`startMonitoringSignificantLocationChanges`) |
| الصمود أمام إعادة تشغيل الهاتف | ✅ `BOOT_COMPLETED` و`QUICKBOOT_POWERON` | ❌ لا |
| الفقاعة العائمة | ✅ `TYPE_APPLICATION_OVERLAY` | ❌ **مستحيل، لا توجد API.** ‏`showBubble()` يُعيد `false`. أقرب مكافئ هو Live Activity (‏Dynamic Island / شاشة القفل)، وهو غير مقدَّم هنا. |
| الرنين في الوضع الصامت | ✅ مسار `USAGE_ALARM`، رفع الصوت إلى أقصاه ثم استعادته | ⚠️ فقط مع تصريح **Critical Alerts** الذي تمنحه Apple بناءً على طلب مبرَّر (`ios.criticalAlerts: true`). بدونه: إشعار عادي. |
| ملء الشاشة عند الوصول | ✅ `setFullScreenIntent` مع `CATEGORY_CALL` وقناة `IMPORTANCE_HIGH`، **و** استثناء `SYSTEM_ALERT_WINDOW` | ⚠️ لا مكافئ. إشعار `.timeSensitive` (‏`.critical` مع التصريح). كان CallKit ليعطي ملء شاشة حقيقيًا، لكنّ Apple ترفض إساءة استعماله: هذه ليست مكالمة، فلم يُستعمل. |
| تجاوز «عدم الإزعاج» | ⚠️ `setBypassDnd(true)` فقط إذا كان `ACCESS_NOTIFICATION_POLICY` ممنوحًا **لحظة إنشاء القناة** | ⚠️ `.timeSensitive` يخترق أوضاع التركيز؛ وما بعد ذلك يحتاج Critical Alerts |
| شاشات التشغيل التلقائي عند المصنّعين | ✅ جدول لكل علامة تجارية مع تراجع إلى صفحة معلومات التطبيق | ❌ لا ينطبق |
| مراقب بمنبّه دقيق | ⚠️ `SCHEDULE_EXACT_ALARM` اختياريًا فقط، ولا `USE_EXACT_ALARM` أبدًا؛ وبدونه تُرفَض الاستعادة أكثر | ❌ لا ينطبق، لا `AlarmManager` ولا شيء يُعاد تشغيله أصلًا |
| السجلّ على الجهاز (`getLog`، `clearLog`، `exportLog`) | ✅ SQLite تكتبه الخدمة، ويصمد أمام موت العملية وإعادة الإقلاع | ❌ **غير منفَّذ.** هذه الثلاث غير معلَنة على وحدة iOS إطلاقًا، فالنداء هناك **يُرفَض** بدل أن يُعيد قيمة محايدة. احتَط بـ `Platform.OS`. |
| عدّاد المسافة (`getOdometer`، `resetOdometer`) | ✅ يُراكَم على النقاط المقبولة ويُحفَظ | ❌ **غير منفَّذ**، ويُرفَض على iOS للسبب نفسه |
| حدث `providerChange` | ✅ مستقبِل `PROVIDERS_CHANGED`، مع خطأ `LOCATION_OFF` وإعادة طلب تلقائية عند العودة | ❌ لا بثّ مكافئ؛ ولا يُطلقه iOS أبدًا |
| `getState().provider` و`.locationEnabled` و`.lastErrorAt` | ✅ | ❌ غائبة عن حمولة iOS: تقرأ `undefined` |
| رفض المواقع المزيّفة (`tracking.rejectMock`) | ✅ `Location.isMock`، و`isMock` على كل نقطة | ❌ لا يكشف `CLLocation` علَمًا كهذا؛ فالمفتاح لا يفعل شيئًا ولا يُرسَل `is_mock` |

الملحق **يُترجَم ويعمل على iOS في كل الحالات**. القدرات الغائبة تُعيد قيمة
صريحة (`false`، `"unsupported"`)، لا استثناءً أبدًا.

**مع استثناء واحد معلَن، منذ الإصدار 1.6.0.** الدوالّ الخمس المضافة للسجلّ
الأصلي وعدّاد المسافة معلَنة على وحدة أندرويد وحدها. وعلى iOS ليست
«unsupported»، بل غائبة، والنداء يُرفَض. وإلى أن تُنفَّذ أو تُبطَّن، اختبر
`Platform.OS === 'android'` قبل نداء `getLog` أو `clearLog` أو `exportLog` أو
`getOdometer` أو `resetOdometer`. أمّا في Expo Go، حيث لا وحدة أصلية أصلًا،
فالقيم المحايدة في جدول التدهور تنطبق فعلًا.

---

## ما يتكفّل به الملحق نيابةً عنك

كل نقطة أدناه هي عطب حقيقي في الإنتاج، لا ممارسة نظرية جيّدة.

1. **خدمة المقدّمة** — `type="location"` معلَن في الـ manifest **و** ممرَّر إلى
   `startForeground()` (وإلّا أسقط أندرويد ١٤ التطبيق). `START_STICKY`.
   و`onTaskRemoved` لا يوقف شيئًا: تلك بالضبط اللحظة التي يُزيح فيها المستخدم
   التطبيق وهو يظنّ أنّ العمل مستمرّ.
2. **البقاء** — `BOOT_COMPLETED` **و** `QUICKBOOT_POWERON` (بعض الأنظمة المعدّلة
   ترسل الثاني فقط)؛ ومراقب `setAndAllowWhileIdle` كل ١٥ دقيقة تقريبًا؛ وحالة
   «أثناء الخدمة» تعيش في التفضيلات لا في الذاكرة. وحين يرفض أندرويد ١٢ فما فوق
   بدءًا من الخلفية، يكون الفشل ظاهرًا — حدث `error` برمز `SERVICE_START`، و**وعد
   `start()` مرفوض**، وإشعار «التتبّع متوقّف» على قناته الخاصّة، يقول إنّ التتبّع
   متوقّف لا العكس، ويمسح نفسه لحظة عودة الخدمة. و`tracking.exactAlarms` يجعل
   استعادة المراقب أقلّ عرضةً للرفض بكثير؛ وقسم المنبّهات الدقيقة يقول ما يكلّفه
   ذلك الإذن.
3. **قنوات إشعارات مرقَّمة بإصدار** — القناة الموجودة لا تعيد أبدًا قراءة
   أهميّتها ولا صوتها ولا اهتزازها ولا تجاوزها لوضع عدم الإزعاج. لذلك تحمل
   المعرّفات رقم الإصدار (`fa_alert_v1`)، مع نسخة لعدم الإزعاج ونسخة صامتة؛
   والقديمة تُحذف عند الإنشاء. ارفع `alert.channelVersion` لفرض إعادة الإنشاء
   على الأجهزة المثبَّتة.
4. **صوت مضمَّن** — ينسخ ملحق الإعداد ملفّك إلى `res/raw` ويضيف `noCompress` إلى
   `app/build.gradle` (بدونها يفشل `openRawResourceFd()` ولا يفتح `MediaPlayer`
   شيئًا). **انحراف مقصود:** الصوت الذي يزوّده التطبيق يُجرَّب **أوّلًا**، وسلسلة
   النظام (منبّه ← نغمة رنين ← إشعار) هي البديل — لا العكس. أنت ضمّنته عن قصد،
   وهو الوحيد الذي لا يعتمد على نظام معدَّل. وكل مرشَّح يُفتح قبل أن يُعتمد.
5. **الرنين في الوضع الصامت** — مسار `USAGE_ALARM`. **وهذا هو الجواب على «اجعله
   يرنّ حتى في الصامت»: مسار المنبّه هو المسار الوحيد الذي يواصل أندرويد تشغيله
   في وضعَي الصامت والاهتزاز.** ورفع مسار الإشعارات أو مسار الرنين لن يغيّر
   شيئًا، ولهذا لا يُمَسّ أيٌّ منهما. أمّا إلى أي حدّ يُرفَع فذلك
   `alert.volumeLevel` (من 0 إلى 1 من أقصى الجهاز، والافتراضي `1`)، و
   `alert.forceVolume: false` يلغي الرفع تمامًا لمن يفضّل احترام المستوى الذي
   اختاره المستخدم. والرفع **أرضية لا سقف** — من يبقي منبّهه أعلى أصلًا يحتفظ
   به، ولا تُسجَّل قيمة سابقة في تلك الحالة. والقيمة القديمة **تُحفَظ على
   القرص** (عملية تُقتل في منتصف تنبيه كانت ستترك
   منبّه الصباح عالقًا على أقصى مستوى) واستعادتها عند التشغيل التالي؛ ويُعاد
   قراءة مستوى الصوت بعد الكتابة، والرفع المرفوض بصمت يُنتج حدث `error` برمز
   `VOLUME`. وتركيز الصوت `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`، وإلّا غطّى
   تطبيق الملاحة على التنبيه وذهب الصوت إلى سمّاعة الأذن. وسقف مدّة إجباري
   (`ttlSeconds`).
6. **فتح شاشة من الخلفية** — الطريقان معًا لا واحد:
   `setFullScreenIntent` مع `CATEGORY_CALL` وقناة `IMPORTANCE_HIGH`، **و**
   الاستثناء الذي يمنحه `SYSTEM_ALERT_WINDOW`. والنشاط `showWhenLocked`
   و`turnScreenOn` و`excludeFromRecents` و`launchMode="singleTask"`، ويتكفّل
   بحوافّه بنفسه (أندرويد ١٥، من حافّة إلى حافّة).
7. **الفقاعة** — `TYPE_APPLICATION_OVERLAY` و`FLAG_NOT_FOCUSABLE`، قابلة للسحب،
   تلتصق بالحافّة عند الإفلات، وموضعها محفوظ. **النقر عليها يُحضر التطبيق إلى
   المقدّمة** (ويُطلق `bubblePress`): النقرة تأتي غالبًا من تطبيق آخر، حيث لا
   يعمل JavaScript ولا يستطيع فعل ذلك؛ و`SYSTEM_ALERT_WINDOW`، اللازم أصلًا
   للفقاعة، هو ما يجعل هذا الإطلاق مشروعًا. أمّا `ACTION_CANCEL` — أي انتزاع
   النظام للإيماءة — فليس نقرة: احتسابه كان يُنتج نقرات وهمية. **ولا يمكنها
   الظهور فوق شاشة القفل** — لا نافذة تراكب تستطيع ذلك. على شاشة القفل تكون
   السطح هو الإشعار بملء الشاشة؛ وتعود الفقاعة عند إلغاء القفل.
8. **الشركات المصنّعة** — يفتح `Power` الشاشة الصحيحة على MIUI و EMUI و ColorOS
   و FunTouch و One UI و OxygenOS و Realme و Meizu و Letv و Asus و Transsion و
   Nokia، بعدّة مكوّنات مرشَّحة لكل علامة وتراجع إلى صفحة معلومات التطبيق.
   و`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` **لا** تُطلَب: تمنعها Google Play عن
   معظم التطبيقات، ورفضها يؤدّي إلى سحب التطبيق من المتجر. قائمة النظام تكلّف
   نقرتين إضافيتين لا أكثر.
9. **البطارية** — مرشّح مسافة، وإيقاع مختلف بين النشاط والسكون، وتجميع
   (`setMaxUpdateDelayMillis`) أثناء السكون، و`WakeLock` جزئي أثناء الإرسال
   فقط، و`getCurrentLocation(PRIORITY_HIGH_ACCURACY)` فوري عند البدء (وإلّا وصلت
   أول نقطة بعد ثلاث دقائق)، ونبضة تتجاوز مرشّح المسافة.
10. **الطابور** — محدود الحجم، على القرص، والحذف **بالمعرّفات** لا بالعدد أبدًا.
    كل نقطة تحمل `client_id`: ضع فهرسًا فريدًا عليه في الخادم ولن تُنشئ دفعة
    مُعاد إرسالها أي تكرار. وعطل الشبكة (`OFFLINE`، `TIMEOUT`) لا يُفرغ شيئًا ولا
    يفصل أحدًا؛ ولا يُهمَل إلّا خطأ 4xx نهائي (ليس 401/403 ولا 408/429)، وإلّا
    عطّلت نقطة مسمومة كل ما بعدها.
11. **الخيط الرئيسي والتسريبات** — نبض الكشّاف يعمل على الخيط الرئيسي برمز
    هويّة، حتى لا تُعيد نبضة كانت في الطابور إشعال الكشّاف بعد التوقّف. ويُحرَّر
    `MediaPlayer` حتى حين يفشل `prepare()`. وتُزال المستمعات ونداءات
    `postDelayed` وطبقات العرض.
12. **أندرويد ١٣ و١٤ و١٥** — يُطلَب `POST_NOTIFICATIONS` قبل الاعتماد على أي
    إشعار؛ وموقع الخلفية على مرحلتين (`requestPermissions` يطلب «أثناء
    الاستخدام» ثم يُحيل إلى إعدادات النظام ابتداءً من أندرويد ١١، كما تفرض
    المنصّة وكما تشترط Google Play الإفصاح عنه)؛ و`FOREGROUND_SERVICE_LOCATION`
    في الـ manifest؛ ومعالجة العرض من حافّة إلى حافّة للـ API 35.

---

## ربط إشعار FCM بشاشة التنبيه

نعم، هذه هي حالة الاستعمال المقصودة. مرشّح `alert.titlePattern` يُختبر مقابل
**العنوان والوسم (tag) ومعرّف القناة** — مرّر ما يملؤه خطّ أنابيبك فعلًا، ولست
مضطرًّا إلى اختلاق عنوان وهمي.

```json
"alert": { "titlePattern": "^(new-job|مهمّة جديدة)$" }
```

**التطبيق مفتوح أو في الخلفية (‏JS حيّ)** — عبر `expo-notifications`:

```ts
import * as Notifications from 'expo-notifications';
import * as FieldAgent from 'expo-field-agent';

Notifications.addNotificationReceivedListener(({ request }) => {
  const { title, body, data } = request.content;
  FieldAgent.triggerAlert({
    title: title ?? '',
    body: body ?? undefined,
    data: data as Record<string, unknown>,
    tag: (data as any)?.tag,                        // وسم FCM عندك
    channelId: (request.trigger as any)?.channelId, // قناة أندرويد
  });
});
```

يعود `triggerAlert` دون أن يفعل شيئًا حين لا يطابق شيء النمط — فيمكنك ربطه
بـ**كل** إشعاراتك دون فرز يدوي.

**التطبيق مقتول** — النداء نفسه، لكن من مهمّة خلفية تعمل كـ headless JS على
أندرويد دون تشغيل التطبيق:

```ts
import * as TaskManager from 'expo-task-manager';
import * as Notifications from 'expo-notifications';
import * as FieldAgent from 'expo-field-agent';

const TASK = 'field-agent-push';

TaskManager.defineTask(TASK, ({ data, error }) => {
  if (error || !data) return;
  const notification = (data as any).notification?.data ?? (data as any);
  FieldAgent.triggerAlert({
    title: notification.title ?? '',
    body: notification.body,
    data: notification,
    tag: notification.tag,
    channelId: notification.channelId,
  });
});

Notifications.registerTaskAsync(TASK);
```

### كل مسارات الوصول، وما يحدث فعلًا

| كيف يصل التنبيه | شاشة كاملة؟ |
|---|---|
| ردّ خادمك على `POST /positions` | ✅ لا يحتاج أي push — الخدمة هي من قام بالطلب |
| ‏FCM **data-only**، التطبيق مفتوح أو في الخلفية | ✅ عبر `addNotificationReceivedListener` ثم `triggerAlert()` |
| ‏FCM **data-only**، التطبيق مقتول | ✅ عبر مهمّة headless من `expo-task-manager` ثم `triggerAlert()` |
| ‏FCM **مع كتلة `notification`**، التطبيق في الخلفية أو مقتول | ✅ **فقط** مع `alert.notificationBridge: true` — وإلّا ❌ |
| التطبيق موقوف قسرًا من الإعدادات | ❌ لا شيء يصله إطلاقًا. ولا تطبيق يستطيع إصلاح ذلك |
| ‏iOS، أيًّا كان المسار | ❌ الشاشة الكاملة غير موجودة. إشعار `.timeSensitive`، و`.critical` مع تصريح Apple |

### يُفضَّل أن تكون الرسالة data-only — ولماذا لا يصلحها أي كود عميل

حين تحمل رسالة FCM كتلة `notification` ولا يكون تطبيقك في المقدّمة، فإنّ SDK
الخاص بـ Firebase ينشر ذلك الإشعار في شريط النظام **بنفسه** ولا يستدعي كودك
إطلاقًا. لا `onMessageReceived`، ولا مهمّة خلفية، ولا `triggerAlert()`. وكتابة
`FirebaseMessagingService` خاص بك لا تنفع: الـ SDK يقصر الطريق قبل أي خدمة
يمكنك تسجيلها.

الإصلاح المجاني عند المُرسِل، وهو مفتاح واحد:

```json
{
  "message": {
    "token": "<رمز الجهاز>",
    "android": { "priority": "HIGH" },
    "data": {
      "title": "مهمّة جديدة",
      "body": "٣٫٢ كم · ١٢ د.ت",
      "jobId": "1234"
    }
  }
}
```

لا `notification` في أي موضع — لا `message.notification` ولا
`message.android.notification`. وكل قيم `data` يجب أن تكون نصوصًا: هذا قيد من
FCM لا منّا.

**إن كنت ترسل عبر خدمة push من Expo (‏`exp.host`) بدل FCM مباشرةً فالأمر
مُعالَج أصلًا:** ‏Expo يرسل data-only داخليًا و`expo-notifications` يعرض الإشعار
بنفسه، فتعمل المهمّة الخلفية. الفخّ لا يعضّ إلّا حين تخاطب FCM مباشرةً.

### `alert.notificationBridge` — حين لا تتحكّم بالمُرسِل

إذا كان الـ push يأتي من نظام لا تستطيع تغييره، يبقى طريق واحد:
`NotificationListenerService`. يرى الإشعار *بعد* أن ينشره أندرويد، وهو نقطة
المراقبة الوحيدة المتبقّية بعد أن يكون SDK الخاص بـ Firebase قد تجاوز تطبيقك.

```json
"alert": { "notificationBridge": true }
```

ما يفعله: يقرأ **إشعارات حزمتك أنت فقط**، ويقارنها بـ `alert.titlePattern` تمامًا
كأي مصدر آخر، ويُطلق التنبيه بملء الشاشة، ثم يلغي نسخة شريط النظام التي حلّ
محلّها كي لا يرى المستخدم الحدث نفسه مرّتين. وإشعار التنبيه الخاص به مستثنى
بالمعرّف، وإلّا لأعاد إطلاق نفسه بلا نهاية.

وما يكلّفه، وهذا ما يجب أن تزنه قبل تفعيله:

- يضيف `BIND_NOTIFICATION_LISTENER_SERVICE` إلى الـ manifest. **‏Google Play
  تراجع كل تطبيق يحمله** وتتوقّع أن يكون الوصول إلى الإشعارات وظيفة أساسية.
  والملحق يطبع تحذيرًا وقت البناء كي لا يكون هذا مفاجأة تُكتشَف عند النشر.
- على المستخدم منح الوصول يدويًا من شاشة إعدادات النظام —
  `openSettings('notificationAccess')` تفتحها، و`getPermissions()` يُرجع
  `notificationAccess`. وقيمتها `unsupported` ما لم تُفعّل الجسر، و`unsupported`
  دائمًا على iOS.
- **حمولة `data` في رسالة FCM لا تنجو.** الإشعار المنشور يحمل عنوانه ونصّه ووسمه
  وقناته — لا جدول `data`، الذي لا يصل التطبيق إلّا عبر intent الإطلاق عند النقر.
  يصل التنبيه ومعه `data.source === 'notificationBridge'` ولا شيء غير ذلك، فعلى
  التطبيق أن يجلب الباقي من واجهته (`GET /api/jobs/active` في تطبيق السائق). هذا
  قيد في المسار نفسه، لا في التنفيذ.

مُعطَّل افتراضيًا. أصلِح المُرسِل إن استطعت، واستعمل هذا حين لا تستطيع.

---

## من أين تأتي التنبيهات

الجانب الأصلي لا يستطيع اعتراض إشعارات التطبيقات الأخرى: هو يعرض مدخلًا واحدًا،
`triggerAlert()`، مُرشَّحًا بـ `alert.titlePattern` (لا يفرّق بين حالة الأحرف،
نصّ عادي أو تعبير نمطي). ثلاثة مصادر تصل إليه:

1. **ردّ خادمك.** إذا أعاد POST الموقع
   `{"alert": {"title": "...", "body": "...", "data": {...}}}`، أطلقت الخدمة
   التنبيه. وهذا المسار الوحيد الذي **لا يحتاج أي push** ويعمل والتطبيق مغلق،
   لأنّ الخدمة هي من قام بالطلب.
2. **‏push والتطبيق مفتوح** — اربط `expo-notifications`:
   ```ts
   Notifications.addNotificationReceivedListener(({ request }) => {
     FieldAgent.triggerAlert({
       title: request.content.title ?? '',
       body: request.content.body ?? undefined,
       data: request.content.data,
     });
   });
   ```
3. **‏push والتطبيق مغلق** — النداء نفسه من مهمّة خلفية
   `expo-notifications` + `expo-task-manager`
   (`Notifications.registerTaskAsync`)، تعمل كـ headless JS على أندرويد حتى
   والتطبيق مقتول.

---

## التحقّق — سيناريو واحد، أمر واحد

| # | السيناريو | أمر التحقّق | المتوقَّع |
|---|---|---|---|
| ١ | بدء بارد، الأذونات ممنوحة، `start()` | `adb shell dumpsys activity services tn.exemple.fieldagent \| grep isForeground` | `isForeground=true`، وأول POST في أقل من ١٠ ثوانٍ (`adb logcat -s OkHttp` من جهة الخادم، أو حدث `sent` في سجلّ التطبيق المثال) |
| ٢ | إزاحة التطبيق من التطبيقات الحديثة | الأمر نفسه بعد الإزاحة | الخدمة ما زالت قائمة، وأحداث `position` مستمرّة |
| ٣ | قتل العملية | `adb shell am crash tn.exemple.fieldagent` ثم `adb shell pidof tn.exemple.fieldagent` | معرّف عملية جديد، والخدمة عادت، و`getState().queued` يستأنف نزوله |
| ٤ | إعادة تشغيل الهاتف | `adb reboot` ثم، دون فتح التطبيق، `adb shell dumpsys activity services tn.exemple.fieldagent` | الخدمة أعاد تشغيلها `BootReceiver` |
| ٥ | وضع الطيران دقيقتين ثم العودة | `adb shell cmd connectivity airplane-mode enable` … `disable` | يرتفع `queued` ثم يعود إلى ٠؛ **بلا تكرار** في الخادم (فهرس فريد على `client_id`) |
| ٦ | شاشة مقفلة + تنبيه | `adb shell input keyevent 26` ثم `triggerAlert` من التطبيق المثال | استيقظت الشاشة، و`AlertActivity` في المقدّمة، مع صوت |
| ٧ | صامت + صوت المنبّه على ١ + تنبيه | `adb shell media volume --stream 4 --set 1` ثم تنبيه، و`adb shell dumpsys audio \| grep -A3 STREAM_ALARM` | ارتفع الصوت إلى أقصاه أثناء التنبيه واستُعيد بعده؛ و`dumpsys media.audio_flinger` يُظهر مسار `USAGE_ALARM` نشطًا |
| ٨ | «عدم الإزعاج» مفعَّل + تنبيه | فعّل عدم الإزعاج، ثم `getPermissions().dndAccess` | يرنّ إذا كان `granted`؛ وإلّا فالحالة تقولها بوضوح وهذا الملف يشرح ما العمل |
| ٩ | كتم من جهة التطبيق + تنبيه | `setAlertSound(false)` ثم تنبيه | لا مشغّل صوت، و**لا** رفع لمستوى الصوت (`dumpsys audio` دون تغيير)، والشاشة تُفتح رغم ذلك |
| ١٠ | تنبيه والتطبيق مغلق | أزِح التطبيق من التطبيقات الحديثة (أو `adb shell am kill tn.exemple.fieldagent`)، ثم تنبيه عبر ردّ الخادم أو مهمّة push. **وليس** `am force-stop`: التطبيق الموقوف قسرًا لا يستقبل شيئًا حتى يُشغَّل يدويًا | تُفتح الشاشة وتعرض مكوّن التطبيق بالبيانات الصحيحة |
| ١١ | الفقاعة: سحب، التصاق بالحافّة، نقر | يدويًا + `adb shell dumpsys window \| grep fieldagent` | وصل حدث `bubblePress`؛ والموضع محفوظ بعد `am crash` |
| ١٢ | ٨ ساعات تتبّع متواصل | `adb shell dumpsys meminfo tn.exemple.fieldagent` كل ساعة؛ و`adb shell dumpsys batterystats --charged tn.exemple.fieldagent` | `TOTAL PSS` مستقرّ؛ راجع «الحدود المقبولة» بخصوص الاستهلاك |

يُعيد تطبيق `example/` تشغيل كل سيناريو من هذه من شاشته الرئيسية. وملفّاته
الثلاثة (`assets/notif.png`، `assets/bulle.png`، `assets/alerte.wav`) علامات
مولَّدة: استبدلها بملفّاتك، فالملحق لا يفعل سوى نسخها.

هذه الاثنا عشر هي الجولة الوظيفية. وما لا تقيسه هو **المدّة** — الحالات الخمس
التي يقطع فيها أندرويد الإمداد فعلًا، والتي تبدو سليمة تمامًا في الدقائق الخمس
التي تراقبها فيها والشاشة مضاءة. ذلك الحزام منفصل: خادم استقبال بـ Node بلا أي
اعتماديات، يسجّل `recorded_at` لكل نقطة واردة ويطبع أكبر فجوة، مع سكربت adb لكل
سيناريو (‏Doze، و`am kill`، وخلفية مقيّدة عبر `appops`، وإعادة إقلاع، ودلو
الاستخدام النادر). والحكم رقم واحد — **لا فجوة تتجاوز ضِعف الفاصل المضبوط** —
والخادم يخرج برمز غير صفري عند تجاوزها، فبإمكان CI أن تتوقّف عليها. كل شيء في
[docs/ENDURANCE.md](docs/ENDURANCE.md) و`scripts/endurance/`، بما في ذلك لماذا
`am force-stop` حالة لا رجعة منها. وذلك المستند بالفرنسية.

---

## الاختبارات الآلية

```bash
npm test
```

```bash
cd example && npx expo prebuild --platform android && cd android && ./gradlew :expo-field-agent:test
```

- **`Geo`** — مرشّح المعقولية في JVM خالص بلا أندرويد: دقّة شاذّة، نقاط خارج
  الترتيب، قفزات مستحيلة، استثناء النفق، ومرشّح المسافة في مواجهة النبضة، وساعة
  التشغيل التي لا تخدعها ساعة حائط مقدَّمة، وحكم «موقع مزيّف»، وخطوة عدّاد
  المسافة، وحدّ قِدَم النبضة. ٣٥ حالة.
- **`Log`** — سُلّم الخطورة، وحساب مدّة الاحتفاظ، وصيغة السطر الواحد بـ UTC، بلا
  أندرويد إطلاقًا (١١ حالة)؛ ثم النصف الآخر فوق Robolectric: التدوير، وسقف
  ١٠ ٠٠٠ سطر، وترتيب التصدير، وقاعدة ترفض أن تُفتَح دون أن تُسقط الخدمة. ٩ حالات.
- **`Queue`** — الإضافة، والسقف، والحذف بالمعرّفات (بما في ذلك مع نقاط أُضيفت
  «أثناء الطيران»)، والبقاء بعد إعادة التشغيل، وملفّ بُتِر بفعل قتل العملية.
  ٩ حالات.
- **`Volume` و`Images`** — صوت المنبّه أرضيةً لا سقفًا، وفكّ ترميز الفقاعة
  «الحدود قبل البكسلات». ١٤ حالة.
- **`LocationSource`** — اختيار fused/manager من التوفّر وحده (حالتان)، ثم
  `ManagerSource` أمام `LocationManager` مزيّف: طلب المزوّدين معًا، وتخطّي
  المعطّل بدل رمي استثناء، والاحتفاظ بالأحدث من آخر موقعين معروفين. ٤ حالات.
- **`Bus`** — الخطأ يصل المستمع **و**القرص معًا، والتحذير لا يمحو آخر خطأ أبدًا،
  والخطأ المرفوع قبل `attach()` يخرج رغم ذلك. ٦ حالات.
- **`Config`** — المفاتيح الجديدة تُقرأ فعلًا من الـ manifest، والمضيف الصامت
  يأخذ الافتراضات المشحونة، وسقف بصفر يُرفَع إلى واحد، والقيمة العبثية تتراجع
  إلى «الأخطاء فقط»، وخيار `start()` يغلب الـ manifest — و`exactAlarms` عمدًا
  ليس ممّا يشغّله خيار `start()`، لأنّ الإذن يُقرَّر عند البناء. ٨ حالات.
- **`Watchdog`** — `exactAlarm` يقرأ `unsupported` ما لم يُفعّل المضيف الخيار،
  والمنبّه المرفوض يُكتَب بدل أن يُرمى، وتغيّر المزوّد يحمل ما بقي مفعَّلًا،
  وعودة الموقع لا تنهض بالخدمة إلّا إن كان أحد في الخدمة، والصمت لا يُؤرَّخ إلّا
  بعد تجاوز الحدّ. ٨ حالات.
- **ملحق الإعداد وسطح JS** — الـ manifest الناتج يحتوي فعلًا على الأذونات، وعلى
  الخدمة `type="location"` بـ `stopWithTask=false`، وعلى مستقبِل الإقلاع بنسخه
  quickboot، وعلى نشاط التنبيه `showWhenLocked`، وعلى مستقبِل
  `PROVIDERS_CHANGED`، وعلى `SCHEDULE_EXACT_ALARM` **فقط** إذا طلبه المضيف؛
  والإدراج في `build.gradle` idempotent؛ وكل قيمة غير صالحة تُحذّر وتتراجع إلى
  افتراضها بدل أن تُسقط البناء؛ و Expo Go يردّ بقيمته المحايدة على كل نداء بينما
  تبقى أخطاء الوسائط ترمي استثناءً. ٥٥ حالة.

ما تبقّى يدوي، ومقبول عن وعي، وموصوف في الجدول أعلاه.

### ما بُني فعلًا، لا ما كُتب فقط

| التحقّق | النتيجة |
|---|---|
| `expo prebuild` (أندرويد) ← manifest، `res/raw`، `res/drawable`، `build.gradle` | ✅ |
| `expo prebuild` (‏iOS) ← `Info.plist`، والصوت مُضاف إلى مشروع Xcode | ✅ |
| `:expo-field-agent:compileDebugKotlin` — ‏Expo SDK 52 | ✅ صفر تحذير في مصادر الوحدة |
| `:expo-field-agent:testDebugUnitTest` — ‏Geo، Log، Queue، Volume، Images، LocationSource، Bus، Config، Watchdog | ✅ ١٠٦/١٠٦ |
| `npm test` — ملحق الإعداد، والقيم، وتدهور Expo Go | ✅ ٥٥/٥٥ |
| `:app:assembleDebug` — ‏APK كامل، ‏manifest مدموج | ✅ |
| `xcodebuild -target ExpoFieldAgent` (محاكي iOS) | ✅ |
| `npm pack` ← تثبيت في تطبيق Expo **SDK 57** جديد، ثم `expo prebuild`، ثم بناء | ✅ بلا أي تعديل يدوي |
| `tsc` على الحزمة وعلى ملحق الإعداد وعلى المثال | ✅ |

وما **لم** يُتحقَّق منه هنا، وما لا يمكن التحقّق منه إلّا على هاتف حقيقي: اثنا
عشر سيناريو الجدول السابق. ولهذا وُضعت الأوامر.

---

## اختيارات وحدود مقبولة عن وعي

**لماذا خدمة أصلية بدل `expo-location` وحده.** التقاط GPS يبقى التقاط المنصّة
(`FusedLocationProviderClient`، `CLLocationManager`) — لم يُعَد كتابته. ما يعجز
عنه JavaScript، وما يبرّر هذا الملحق: إعلان
`foregroundServiceType="location"` لأندرويد ١٤، والاحتفاظ بطابور محدود على
القرص، والعودة بعد موت العملية أو إعادة التشغيل، ورسم طبقة فوق الشاشة، وفتح
نشاط بملء الشاشة من الخلفية، والرنين على مسار المنبّه.

**التبعيات المضافة.** واحدة فقط:
`com.google.android.gms:play-services-location`، وهي موجودة أصلًا في أي مشروع
Expo يستعمل `expo-location`. ‏`LocationManager` لا يكشف التجميع
(`maxUpdateDelay`) ولا `getCurrentLocation` إلّا ابتداءً من API 30/31؛ وهذا
الملحق يستهدف `minSdk 24`. وتشفير ترويسة المصادقة يمرّ عبر `AndroidKeyStore` مع
`javax.crypto` (من المنصّة) بدل `androidx.security:security-crypto`، وعبر
Keychain على iOS. والنقل يستعمل `HttpURLConnection` / `URLSession` — بضعة
كيلوبايتات من JSON لا تبرّر تثبيت إصدار من OkHttp داخل المشروع المضيف.

**نشاط التنبيه يركّب المكوّن الجذر للتطبيق نفسه.** لا مكوّن ثانٍ تسجّله:
`AlertActivity` يشغّل بالضبط الجذر الذي يسجّله `registerRootComponent` (و
expo-router) باسم `main`، فيُركَّب `<AlertHost>` كالمعتاد ويقرأ التنبيه عند أول
رسم. وإن سجّلت جذرك باسم آخر، فأخبر الملحق:
`["expo-field-agent", { "rootComponent": "اسمك" }]`.

**سطحان React للحظة قصيرة.** إذا فتح المستخدم التطبيق من أيقونته بينما تنبيه
معروض، أغلق نشاط التنبيه نفسه (`ActivityLifecycleCallbacks`) حتى لا تُركَّب
شجرتا React معًا بشكل دائم. والنافذة التي يتعايشان فيها تُقاس بالمللي ثانية.

**استهلاك البطارية: لا رقم منشور.** لم يُقَس، فلا يُعلَن. وهذا بروتوكول قياسه
على أسطولك:

```bash
adb shell dumpsys batterystats --reset
# ... ٨ ساعات خدمة، شاشة مطفأة، مسار حقيقي ...
adb shell dumpsys batterystats --charged tn.exemple.fieldagent > battery.txt
```

ورتبة المقدار تعتمد كلّيًا على `intervalSeconds` وجودة الإشارة وطراز الهاتف؛
رقم مقيس على Pixel لا يقول شيئًا عن Redmi.

**النبضة تقوم على wake lock لا على الحظّ.** يقودها `Handler` على الخيط
الرئيسي، أي `uptimeMillis`، الذي **يتوقّف عن التقدّم حين ينام المعالج** — والخدمة
الأمامية لا تمنع المعالج من النوم. هاتف ساكن، شاشة مطفأة، وضع Doze: بلا شيء يسند
المعالج، لم تكن النبضة تنطلق عند `heartbeatSeconds` بل عند الاستيقاظ التالي
للخدمة، أي على أبعد تقدير عند منبّه المراقب، وهو الحدّ الأدنى الذي يفرضه النظام
على منبّهات *while-idle*، **حوالي ١٥ دقيقة**. وبالنسبة لخادم يحكم على الطزاجة،
كان العميل قد اختفى. و`tracking.wakeLock`، وهو مفعّل افتراضيًا، يمسك
`PARTIAL_WAKE_LOCK` طوال الجلسة: يبقى المعالج مستيقظًا، وتنطلق النبضة في موعدها،
ويُفرَّغ الطابور فورًا، والشاشة مقفلة. ولا يُهمَل هذا القفل في Doze لأنّ الـ uid
يحمل خدمة أمامية. الثمن بطّارية؛ و`tracking.wakeLock: false` يردّه لمن لا يريده،
فيعود السلوك إلى ما وُصف أعلاه. أمّا `tracking.exactAlarms` فيبقى البابَ المجاور،
للمراقب وحده: `SCHEDULE_EXACT_ALARM` فقط، وهو ما يمنحه المستخدم ويستطيع سحبه، ولا
`USE_EXACT_ALARM` الممنوح عند التثبيت الذي تحجزه Google Play للمنبّهات والتقاويم.
أمّا على الطريق فالمشكلة لم تكن تُطرح أصلًا: كل قراءة GPS تُوقظ المعالج.

**والنبضة تصمت بدل أن تكذب.** إعادة إرسال آخر موقع معروف بختم زمني جديد هي كل
غرضها — لكن بعد `max(heartbeatSeconds × 4, ٥ دقائق)` محسوبةً من اللحظة التي
قبلت فيها هذه العملية تلك القراءة، لا ترسل شيئًا وتكتب سطر `STALE` في السجلّ
بدلًا من ذلك. هاتف فقد GPS في مرآب تحت الأرض قبل أربعين دقيقة كان ينشر مكانه
السابق على أنّه مكانه الحالي، ومنسّقٌ يوجّه بناءً على ذلك يرسل أحدهم إلى شارع
فارغ. الصمت هو الجواب الصادق، وفحص الطزاجة في الخادم يتكفّل بالباقي.

**‏`flush()` بلا خدمة.** يعمل: الطابور والنقل يعيشان في `Outbox` لا في الخدمة،
فـ `flush()` يدوي يُرسل حتى والتتبّع متوقّف.

**ما لا يفعله الملحق.** لا Live Activity على iOS، ولا CallKit، ولا
`NotificationListenerService` لاعتراض إشعارات التطبيقات الأخرى، ولا ترميز
جغرافي عكسي. ولا شيء من ذلك TODO: هذه أهداف غير مقصودة، مذكورة هنا كي لا
تُكتشَف أثناء عرض توضيحي.

---

## الرخصة

MIT.
