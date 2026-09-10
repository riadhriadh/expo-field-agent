# expo-field-agent

[English](README.md) · [Français](README.fr.md) · **العربية**

> **يلزم development build. هذه الحزمة لا تعمل داخل Expo Go.**
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
| `notification.channelName` | `"Suivi en service"` | |
| `notification.title` | `"En service"` | |
| `notification.body` | `"Ta position est partagee pendant tes courses."` | |
| `notification.icon` | `null` | أيقونة التطبيق |
| `notification.color` | `"#FF6B2C"` | |
| `alert.titlePattern` | `".*"` | كل عنوان يُطلق التنبيه |
| `alert.sound` | `null` | نغمة المنبّه في النظام |
| `alert.channelName` | `"Nouvelles courses"` | |
| `alert.route` | `"field-agent-alert"` | |
| `alert.ttlSeconds` | `45` | |
| `alert.torch` | `false` | |
| `alert.channelVersion` | `1` | |
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
> تطبيق `example/` يتضمّنه لهذا السبب.

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

// الفقاعة ------------------------------------------------------------------
FieldAgent.showBubble(): Promise<boolean>;                             // false على iOS أو بدون الإذن
FieldAgent.hideBubble(): Promise<void>;
FieldAgent.setBubbleState(s: 'ok' | 'warn' | 'bad' | 'urgent', text?: string): Promise<void>;

// التنبيه ------------------------------------------------------------------
FieldAgent.triggerAlert({ title, body?, data?, tag?, channelId? }): Promise<void>;
FieldAgent.dismissAlert(): Promise<void>;
FieldAgent.setAlertSound(enabled: boolean): Promise<void>;             // كتم من جهة التطبيق
FieldAgent.getPendingAlert(): Promise<AlertPayload | null>;
FieldAgent.getPendingAlertSync(): AlertPayload | null;

// الأحداث ------------------------------------------------------------------
FieldAgent.addListener('position' | 'sent' | 'error' | 'alert' | 'bubblePress', cb): Subscription;
```

يحمل `Permissions` ثمانية مفاتيح:

| المفتاح | ما هو |
|---|---|
| `location` | `ACCESS_FINE_LOCATION` |
| `backgroundLocation` | «السماح طوال الوقت» |
| `notifications` | `POST_NOTIFICATIONS` (أندرويد ١٣ فما فوق) |
| `overlay` | `SYSTEM_ALERT_WINDOW` — الفقاعة، **و** استثناء الإطلاق من الخلفية |
| `batteryUnrestricted` | قائمة تحسين البطارية في النظام |
| `dndAccess` | `ACCESS_NOTIFICATION_POLICY` |
| `fullScreenIntent` | **إضافة** — أندرويد ١٤ يضع `setFullScreenIntent` خلف إذن خاص. بدونه يتراجع تنبيه شاشة القفل بصمت إلى إشعار عادي، لذلك جُعلت الحالة ظاهرة بدل أن تُفترض. |
| `autostart` | **إضافة** — شاشة التشغيل التلقائي عند الشركة المصنّعة. لا توجد API تقرأها: `granted` بعد أن يُرسَل المستخدم إليها، و`undetermined` قبل ذلك، و`unsupported` على علامة تجارية بلا شاشة معروفة. |

مفتاحان زيادة على العقد الأصلي، لأنّ التنبيه والتتبّع بدونهما ينكسران على
أندرويد ١٤ فما فوق وعلى MIUI/EMUI/ColorOS **دون أن يقولا شيئًا**.

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

الملحق **يُترجَم ويعمل على iOS في كل الحالات**. القدرات الغائبة تُعيد قيمة
صريحة (`false`، `"unsupported"`)، لا استثناءً أبدًا.

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
   بدءًا من الخلفية، يكون الفشل ظاهرًا (حدث `error` برمز `SERVICE_START`) ويُنشر
   إشعار «استئناف» — لا يُبتلَع الخطأ.
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
5. **الرنين في الوضع الصامت** — مسار `USAGE_ALARM`؛ ورفع صوت المنبّه إلى أقصاه
   مع **حفظ القيمة القديمة على القرص** (عملية تُقتل في منتصف تنبيه كانت ستترك
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

### يجب أن تكون الرسالة data-only — وهذه ليست تفصيلة

حين تحمل رسالة FCM كتلة `notification` ولا يكون تطبيقك في المقدّمة، فإنّ SDK
الخاص بـ Firebase ينشر ذلك الإشعار في شريط النظام **بنفسه** ولا يستدعي كودك
إطلاقًا. لا `onMessageReceived`، ولا مهمّة خلفية، ولا `triggerAlert()`، ولا شاشة
كاملة. وكتابة `FirebaseMessagingService` خاص بك لا تغيّر شيئًا: الـ SDK يقصر
الطريق قبل أي خدمة يمكنك تسجيلها. هذه هي الحالة الوحيدة التي لا يصلحها أي كود
من جهة العميل — الإصلاح عند المُرسِل، وهو مفتاح واحد.

```json
{
  "message": {
    "token": "<device token>",
    "android": { "priority": "HIGH" },
    "data": {
      "title": "Nouvelle course",
      "body": "3,2 km - 12 DT",
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
بنفسه، فتعمل المهمّة الخلفية ويُبلَغ `triggerAlert()`. الفخّ لا يعضّ إلّا حين
تخاطب FCM مباشرةً.

**«الإيقاف القسري» ليس «التطبيق مغلق».** التطبيق الذي أوقفه المستخدم قسرًا من
إعدادات النظام لا يستقبل أي رسالة FCM ولا أي broadcast إطلاقًا حتى يُشغّله بيده
من جديد. تلك هي حالة *stopped* في أندرويد، ولا شيء يُخرجه منها — لا push، ولا
`BOOT_COMPLETED`، ولا المراقب. وفي كل هذا الملف، «التطبيق مغلق» تعني مُزاحًا من
التطبيقات الحديثة أو مقتولًا من النظام، لا موقوفًا قسرًا.

من هناك يتولّى الجانب الأصلي: قناة `IMPORTANCE_HIGH`، و`setFullScreenIntent`،
و`AlertActivity` فوق شاشة القفل، والرنين على مسار المنبّه، ومكوّن `AlertHost`
مرسومًا في أول إطار.

**ما لا يفعله الملحق:** لا يُثبّت `FirebaseMessagingService` خاصًّا به. خدمة
واحدة فقط تفوز بمرشّح `MESSAGING_EVENT`، وأخذه كان سيكسر `expo-notifications`
في تطبيقك. والربطان أعلاه يمرّان عبره، فلا شيء يُسلَب من أحد. وإن أردت رغم ذلك
مدخل FCM الأصلي (حالة واحدة: أنت لا تستعمل `expo-notifications` إطلاقًا)،
فاطلبه — يعني تبعية `firebase-messaging` إضافية وارتباطًا بالإصدارات، لا خيارًا
افتراضيًا يُتَّخذ نيابةً عنك.

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

---

## الاختبارات الآلية

```bash
npm test
```

```bash
cd example && npx expo prebuild --platform android && cd android && ./gradlew :expo-field-agent:test
```

- **`Geo`** — مرشّح المعقولية في JVM خالص بلا أندرويد: دقّة شاذّة، نقاط خارج
  الترتيب، قفزات مستحيلة، استثناء النفق، ومرشّح المسافة في مواجهة النبضة.
  ١٦ حالة.
- **`Queue`** — الإضافة، والسقف، والحذف بالمعرّفات (بما في ذلك مع نقاط أُضيفت
  «أثناء الطيران»)، والبقاء بعد إعادة التشغيل، وملفّ بُتِر بفعل قتل العملية.
  ٩ حالات.
- **ملحق الإعداد** — الـ manifest الناتج يحتوي فعلًا على الأذونات، وعلى الخدمة
  `type="location"` بـ `stopWithTask=false`، وعلى مستقبِل الإقلاع بنسخه
  quickboot، وعلى نشاط التنبيه `showWhenLocked`؛ والإدراج في `build.gradle`
  idempotent؛ وإعداد غير صالح يُحذّر بدل أن يُسقط البناء. ١٣ حالة.

ما تبقّى يدوي، ومقبول عن وعي، وموصوف في الجدول أعلاه.

### ما بُني فعلًا، لا ما كُتب فقط

| التحقّق | النتيجة |
|---|---|
| `expo prebuild` (أندرويد) ← manifest، `res/raw`، `res/drawable`، `build.gradle` | ✅ |
| `expo prebuild` (‏iOS) ← `Info.plist`، والصوت مُضاف إلى مشروع Xcode | ✅ |
| `:expo-field-agent:compileDebugKotlin` — ‏Expo SDK 52 | ✅ صفر تحذير في مصادر الوحدة |
| `:expo-field-agent:test` — ‏Geo و Queue | ✅ ٢٥/٢٥ |
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

**النبضة تتدهور في النوم العميق.** يقودها `Handler` على الخيط الرئيسي، أي
`uptimeMillis`، الذي **يتوقّف عن التقدّم حين ينام المعالج**. هاتف ساكن، شاشة
مطفأة، وضع Doze: لا تنطلق النبضة عند `heartbeatSeconds`، بل عند الاستيقاظ
التالي للخدمة — أي على أبعد تقدير عند منبّه المراقب، وهو الحدّ الأدنى الذي
يفرضه النظام على منبّهات *while-idle*، **حوالي ١٥ دقيقة**. والنزول تحت ذلك
يتطلّب منبّهًا دقيقًا ترفضه Google Play للتطبيقات التي ليست منبّهات ولا تقاويم.
أمّا على الطريق فالمشكلة لا تُطرح: كل قراءة GPS تُوقظ المعالج. مقيس على محاكٍ،
لا مستنتَج.

**‏`flush()` بلا خدمة.** يعمل: الطابور والنقل يعيشان في `Outbox` لا في الخدمة،
فـ `flush()` يدوي يُرسل حتى والتتبّع متوقّف.

**ما لا يفعله الملحق.** لا Live Activity على iOS، ولا CallKit، ولا
`NotificationListenerService` لاعتراض إشعارات التطبيقات الأخرى، ولا ترميز
جغرافي عكسي. ولا شيء من ذلك TODO: هذه أهداف غير مقصودة، مذكورة هنا كي لا
تُكتشَف أثناء عرض توضيحي.

---

## الرخصة

MIT.
