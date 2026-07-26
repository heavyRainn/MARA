# Yasna

**Yasna** — русскоязычный голосовой ассистент для пожилых людей. Приложение работает локально на Android: распознаёт речь, отвечает голосом, запоминает важные сведения о пользователе, создаёт напоминания и учитывает медицинские ограничения при сохранении памяти.

Проект находится в активной разработке. Текущая версия включает **Memory v2** — атомарную долговременную память с подтверждением чувствительных фактов.

---

## Модули

| Модуль | Назначение |
|--------|------------|
| `:app` | Compose UI, `SpeakViewModel`, `ServiceLocator`, lifecycle |
| `:brain-core` | Чистый Kotlin/JVM: orchestrator, память, напоминания, LLM-контракты |
| `:platform-android` | Room, Retrofit/Groq, SpeechRecognizer, TTS, AlarmManager, WorkManager |

**Граница:** `:brain-core` не зависит от Android SDK, Room или Retrofit. Все платформенные детали — в `:platform-android`.

---

## Предметная область

### Пользователь
Пожилой человек, которому нужен простой голосовой собеседник: без сложного UI, с медленной речью, короткими ответами и запоминанием личных предпочтений.

### Основные возможности

1. **Голосовой диалог** — нажатие на микрофон, распознавание речи (ru-RU), ответ через TTS.
2. **Долговременная память** — факты о пользователе (имя, предпочтения, локация, лекарства и т.д.) с provenance и политиками безопасности.
3. **Напоминания** — «напомни завтра в 9 выпить таблетку» с подтверждением перед постановкой будильника.
4. **Команды памяти** — «что ты обо мне помнишь?», «забудь, что я люблю кофе», «не запоминай этот разговор».

### Принципы домена

- Текущее сообщение пользователя **важнее** сохранённой памяти.
- Медицинские факты (лекарства, аллергии, диагнозы) **не сохраняются без явного «да»**.
- Память из ответов ассистента, цитат и гипотез **не извлекается**.
- Удалённая пользователем память **не восстанавливается** автоматически (tombstones).
- Local-first: данные на устройстве, Groq используется только для генерации ответов и extraction.

---

## Архитектура

### Центральный orchestrator

`AssistantOrchestrator` (`:brain-core`) координирует все сценарии:

```
UserMessage / Confirmation
        │
        ▼
  SessionManager ──► sessionId (новая сессия: 8ч простоя / новый день)
        │
        ├── PendingActionRepository (Room) ── reminder / memory confirm
        ├── ReminderIntentResolver ──► ReminderCoordinator
        └── Chat flow:
              ContextBuilder + MemoryRetriever
              LanguageModel (Groq)
              ConversationRepository
              MemoryPipeline
              ConversationSummarizer
```

### Memory v2 pipeline

```
Extract → Validate → Resolve → Confirm → Store → Retrieve → Consolidate
```

| Этап | Компонент | Описание |
|------|-----------|----------|
| Extract | `GroqMemoryExtractor` | LLM возвращает JSON-кандидатов `MemoryCandidate` |
| Validate | `MemoryPolicy` | Rule-based: auto / confirm / reject |
| Resolve | `MemoryConflictResolver` | ADD / UPDATE / DELETE / NOOP, SUPERSEDED |
| Confirm | `PendingAction(CONFIRM_MEMORY)` | TTL 48ч, переживает process kill |
| Store | `MemoryRepository` + Room | Факты + sources в транзакции |
| Retrieve | `MemoryRetriever` | Topic + keyword ranking, лимиты в prompt |
| Consolidate | `DefaultMemoryConsolidator` + WorkManager | Expiry, дедупликация, rebuild `UserProfile` |

`UserProfile` — **проекция** над активными фактами (`UserProfileProjector`), не источник истины.

### Контекст для LLM

`ContextBuilder` формирует сообщения в порядке:

1. System prompt + правила безопасности памяти
2. Релевантные подтверждённые факты (до ~8 профильных + ~3 эпизодических)
3. Summary текущей сессии
4. Последние активные сообщения (tail = 8)
5. Текущая реплика пользователя

Полный профиль **не** отправляется при каждом запросе.

---

## Хранение данных (Room v1)

База `yasna.db`, **версия 1** — единая схема без миграций. При изменении схемы используется `fallbackToDestructiveMigration()` (данные сбрасываются). Подходит для ранней разработки; перед релизом потребуются явные migrations.

### Таблицы

| Таблица | Содержимое |
|---------|------------|
| `messages` | История чата: `message_uid`, `role`, `content`, `state` (ACTIVE/ARCHIVED/DELETED) |
| `conversation_sessions` | Сессии, `exclude_from_extraction` |
| `chat_summaries` | Резюме сессии |
| `user_profile` | Кэш-проекция профиля |
| `memory_facts` | Атомарные факты памяти |
| `memory_fact_sources` | Provenance (messageId, sourceType, excerpt) |
| `memory_tombstones` | Запрет восстановления забытых фактов |
| `pending_actions` | Ожидающие подтверждения (reminder, memory) |
| `reminders` | Запланированные напоминания |

### Сообщения

- В LLM попадают только **ACTIVE** сообщения (последние 8).
- После summary старые сообщения **архивируются**, не удаляются.
- Архив доступен для provenance и summarization.

---

## Сборка и запуск

### Требования

- Android Studio (AGP 8.4+, compileSdk 36)
- JDK 11+
- Groq API key

### Настройка

1. Скопируйте `local.properties.example` → `local.properties`
2. Укажите ключ: `groq.api.key=gsk_...`
3. Сборка:

```bash
./gradlew :app:assembleDebug
```

4. Тесты:

```bash
./gradlew :brain-core:test :platform-android:testDebugUnitTest
```

### Разрешения

- `RECORD_AUDIO` — распознавание речи
- `INTERNET` — Groq API

---

## Ключевые package names

```
com.care.voice                    — app (UI, ServiceLocator)
com.care.voice.brain              — brain-core (orchestrator, memory, reminder)
com.care.voice.brain.memory.*     — Memory v2 domain
com.care.voice.brain.pending.*    — Pending actions
com.care.voice.platform.android.* — Room, Groq, workers
com.care.voice.data.*             — entities, DAO, repositories
```

---

## Тестирование

Unit-тесты в `:brain-core`:

- `MemoryPolicyTest`, `MemoryCandidateParserTest`
- `MemoryConflictResolverTest`, `MemoryRetrieverTest`
- `MemoryPipelineTest`, `DefaultMemoryConsolidatorTest`
- `AssistantOrchestratorTest`, `ContextBuilderTest`

Unit-тесты в `:platform-android`:

- `MemoryMappersTest`, `ReminderTimeParserTest`

---

## Текущие ограничения

- Room v1 + destructive migration — **не для production** без migrations.
- Retrieval без embeddings (keyword + topic classification).
- `ProfileExtractor` устарел; используется `MemoryPipeline`.
- Groq model: `llama-3.1-8b-instant`.
- Нет экрана управления памятью для опекунов.
- Instrumented-тест миграции не написан (миграции пока отключены).

---

## Дорожная карта

1. Стабильные Room migrations при заморозке схемы.
2. Расширение unit-тестов до полного набора сценариев Memory v2.
3. Экран просмотра/удаления памяти.
4. Embeddings — только после накопления достаточного объёма фактов и метрик качества retrieval.
5. Release-safe logging (медицинские данные не в логах).

---

## Лицензия

Не указана. Уточните перед публикацией.
