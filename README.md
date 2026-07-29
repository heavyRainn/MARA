# Yasna

**Yasna** — русскоязычный голосовой ассистент для пожилых людей на Android: распознаёт речь, отвечает голосом, запоминает важные сведения о пользователе, создаёт напоминания и учитывает медицинские ограничения при сохранении памяти.

Проект в активной разработке. Текущая версия включает **Memory v2** (атомарная долговременная память) и **локальный neural TTS** (Piper / sherpa-onnx, голос Irina).

---

## Модули

| Модуль | Назначение |
|--------|------------|
| `:app` | Compose UI, `SpeakViewModel`, FSM голоса, `ServiceLocator` |
| `:brain-core` | Чистый Kotlin/JVM: orchestrator, память, напоминания, speech API, LLM-контракты |
| `:platform-android` | Room, Groq, SpeechRecognizer, Piper runtime, AlarmManager, WorkManager |
| `:piper-spike` | Native libs sherpa-onnx + vendored JNI bindings + assets голосовой модели |

**Граница:** `:brain-core` не зависит от Android SDK. Платформенные детали — в `:platform-android` и `:piper-spike`.

---

## Основные возможности

1. **Голосовой диалог** — микрофон, распознавание ru-RU, ответ через Groq LLM, озвучка ответа.
2. **Локальный TTS** — Piper (Irina, `ru_RU-irina-medium`) через sherpa-onnx; fallback на Android TTS.
3. **Долговременная память** — факты о пользователе с provenance и политиками безопасности.
4. **Напоминания** — «напомни завтра в 9…» с подтверждением; уведомление + голос при срабатывании.
5. **Команды памяти** — «что ты обо мне помнишь?», «забудь…», «не запоминай этот разговор».

### Принципы домена

- Текущее сообщение пользователя **важнее** сохранённой памяти.
- Медицинские факты **не сохраняются без явного «да»**.
- Память из ответов ассистента **не извлекается**.
- Удалённая память **не восстанавливается** автоматически (tombstones).
- Local-first: данные на устройстве; Groq — только для LLM (ответы, extraction, intent напоминаний).

---

## Архитектура

### Orchestrator

`AssistantOrchestrator` координирует диалог, pending actions (подтверждения), напоминания и memory pipeline.

### Speech pipeline

```
SpeakViewModel → TtsManager
  → AssistantSpeechCoordinator / ReminderSpeechCoordinator
  → SpeechPlaybackCoordinator (normalize, chunk)
  → FallbackSpeechSynthesisProvider
      → PiperSpeechProvider (sherpa-onnx)
      → AndroidSpeechProvider (fallback)
```

Модель Irina предзагружается в фоне при старте приложения.

### Memory v2

```
Extract → Validate → Resolve → Confirm → Store → Retrieve → Consolidate
```

| Этап | Компонент |
|------|-----------|
| Extract | `GroqMemoryExtractor` |
| Validate | `MemoryPolicy` |
| Resolve | `MemoryConflictResolver` |
| Confirm | `PendingAction(CONFIRM_MEMORY)` |
| Store | Room (`memory_facts`, sources) |
| Retrieve | `MemoryRetriever` |
| Consolidate | WorkManager + `DefaultMemoryConsolidator` |

### Напоминания

```
ReminderIntentResolver (LLM) → ReminderCoordinator → подтверждение
  → AlarmManager → ReminderReceiver → notification + voice
```

---

## Сборка и запуск

### Требования

- Android Studio (AGP 8.4+, compileSdk 36)
- JDK 11+
- Groq API key
- **Устройство arm64** (Piper JNI собран для `arm64-v8a`)
- Windows: PowerShell для автозагрузки Piper-assets при сборке

### Настройка

1. Скопируйте `local.properties.example` → `local.properties`
2. Укажите ключ: `groq.api.key=gsk_...`
3. Сборка (assets Piper скачаются автоматически):

```bash
./gradlew :app:assembleDebug
```

Ручная загрузка модели (опционально):

```powershell
powershell -File scripts/download-piper-spike.ps1
```

4. Тесты:

```bash
./gradlew :brain-core:test :platform-android:testDebugUnitTest :app:testDebugUnitTest
```

### Разрешения

| Разрешение | Зачем |
|------------|-------|
| `RECORD_AUDIO` | Распознавание речи |
| `INTERNET` | Groq API |
| `POST_NOTIFICATIONS` | Push-напоминания |
| `SCHEDULE_EXACT_ALARM` | Точные будильники |
| `RECEIVE_BOOT_COMPLETED` | Восстановление напоминаний после перезагрузки |

---

## Хранение данных

Room-база `yasna.db`. При изменении схемы — `fallbackToDestructiveMigration()` (данные сбрасываются). Перед релизом нужны явные migrations.

Основные таблицы: `messages`, `conversation_sessions`, `chat_summaries`, `memory_facts`, `memory_tombstones`, `pending_actions`, `reminders`.

---

## Структура пакетов

```
com.care.voice                    — app (UI, ServiceLocator)
com.care.voice.brain              — orchestrator, memory, reminder, speech
com.care.voice.platform.android.* — Room, Groq, Piper, workers, receivers
com.care.voice.data.*             — entities, DAO
com.k2fsa.sherpa.onnx             — vendored JNI bindings (piper-spike)
```

---

## Лицензии сторонних компонентов

См. [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) — sherpa-onnx, Piper, голосовые модели.

---

## Текущие ограничения

- Room + destructive migration — не для production без migrations.
- Retrieval без embeddings (keyword + topic).
- Groq model: `llama-3.1-8b-instant`; без интернета LLM недоступен.
- Piper только **arm64-v8a**; на x86-эмуляторе — fallback Android TTS.
- Нет экрана управления памятью для опекунов.
- Dataset license голоса Irina: Unknown (см. THIRD_PARTY_NOTICES).

---

## Дорожная карта

1. Стабильные Room migrations.
2. Экран просмотра/удаления памяти.
3. Embeddings для retrieval — после накопления метрик.
4. Release-safe logging (медицинские данные не в логах).

---

## Лицензия

Не указана. Уточните перед публикацией.
