# Скиллы, память, хуки и MCP

Чем дольше вы работаете с агентом, тем яснее: повторять одни и те же объяснения в каждом запросе утомительно, а «пожалуйста, не делай X» агент иногда забывает. Для этого есть набор слоёв, которые превращают ваши знания и правила в то, что агент подхватывает сам. В этом уроке мы разберём их, посмотрим на реальный набор скиллов для тестировщиков и напишем свой.

После урока вы сможете:

- выбрать подходящий слой: инструкции, память, скилл, хук, MCP, плагин;
- написать `SKILL.md` с хорошим описанием, пошаговыми инструкциями и вынесенными справочниками;
- проверить скилл и найти слабые места;
- настроить хук, который гарантирует то, что нельзя оставить на усмотрение модели.

## Слои: что куда

| Слой | Когда подгружается | Для чего | Пример |
|---|---|---|---|
| **Инструкции проекта** (`CLAUDE.md`, `AGENTS.md`) | всегда | короткие правила и факты проекта | «сборка `./gradlew test`», «комментарии на английском» |
| **Память** | в начале сессии (индекс) | факты о людях, договорённостях и ходе работы | «публикация деплоит сайт: спрашивать» |
| **Скилл** | по необходимости: когда задача подходит под описание | процедура, которую используют не всегда | «как добавить язык», «как подготовить релиз» |
| **Слэш-команда** | когда вызвана человеком | сокращение для частого запроса | `/code-review` |
| **Подагент** | когда задача делегирована | исследование и независимые проверки с чистым контекстом | ревьюер, исследователь кода |
| **Хук** | на событие, **всегда**, без участия модели | то, что должно происходить гарантированно | блокировка `git push --force` |
| **MCP-сервер** | когда нужен инструмент | подключение внешних возможностей | браузер, база, трекер задач |
| **Плагин** | при установке | упаковка всего вышеперечисленного для распространения | набор инструментов для тестировщиков |

Выбор делается по двум вопросам. **Всегда ли это нужно?** Если да и это коротко, то в инструкции. Если нужно иногда, то в скилл (чтобы не занимать контекст постоянно). **Можно ли оставить это на усмотрение модели?** Если нет (безопасность, деньги, публичная история), то хук: правило, написанное в тексте, она может проигнорировать, а хук нет.

## Память: что записывать

Память хранит то, что нельзя вывести из кода. Полезная классификация:

| Тип | Что записывать | Пример |
|---|---|---|
| **Пользователь** | роль, опыт, предпочтения | «разработчик-новичок в Kotlin, любит скриншоты как доказательство» |
| **Обратная связь** | как работать, с причиной | «писать комментарии в коде на английском; причина: так принято в репозитории» |
| **Проект** | текущие цели и решения, которых нет в коде | «отклонили раздел FAQ; не предлагать повторно» |
| **Справка** | где искать внешнее | «ссылка на доску задач, дашборд мониторинга» |

Каждая запись: **что**, **почему**, **как применять**. Правило без причины невозможно применить разумно к краевому случаю. Не записывайте то, что видно в репозитории (структура, история), и то, что имеет смысл только в одной беседе. И помните, что память устаревает: запись об имени файла или флага нужно проверять перед использованием.

## Анатомия скилла

Скилл — это **папка**, главный файл которой называется `SKILL.md`. Посмотрим на реальный набор: репозиторий `qa-toolkit` (плагин для тестировщиков, лежит рядом с проектом):

```text
plugins/qa-toolkit/
  .claude-plugin/plugin.json        the manifest of the plugin
  hooks/
    hooks.json                      which hooks run on which events
    block-marketplace-force-push.sh a guard script
    protect-plugin-writes.sh        another guard
  scripts/
    jira_helper.py allure_helper.py ...   deterministic helpers called by skills
  skills/
    qa-copilot/SKILL.md
    daily-todo/SKILL.md
    write-testcase/
      SKILL.md
      references/                   long material, read only when needed
        create-case.md format.md automated-case.md
```

Заголовок `SKILL.md`, как у реального скилла `daily-todo`:

```markdown
---
name: daily-todo
description: Manages a personal `todo.md` backlog kept in the project repo root: reads it on request, syncs the session's TodoWrite state back into it. Use when the user says something like "plan for the day"/"todo", asks to add/update/close a todo item, or when the session ends having used TodoWrite against work tracked in todo.md.
---

# daily-todo
...instructions the agent follows once the skill is loaded...
```

Две части: **frontmatter** (`name` и `description`) загружается агентом заранее, у всех скиллов сразу, а **тело** только когда агент решил, что скилл подходит. Поэтому `description` важнейшее поле: по нему модель решает, нужен ли скилл.

### Описание как триггер

Хорошее `description` отвечает на два вопроса: **что делает** и **когда использовать**, словами, которыми пользователь действительно выразит задачу.

| | Плохо | Хорошо |
|---|---|---|
| Расплывчато | «Помогает с тестами» | «Создаёт и обновляет ручные тест-кейсы в Allure TestOps через API. Использовать, когда просят написать, переписать или обновить тест-кейс» |
| Только «что» | «Работает с todo.md» | «…Use when the user says "plan for the day" / "todo" or asks to add or close an item» |
| Слишком широко | «Всё для разработки» | конкретная область и узкие признаки задачи |

Слишком узкое описание не срабатывает, слишком широкое срабатывает не вовремя и засоряет контекст. Хорошая проверка: придумайте пять запросов, где скилл **должен** сработать, и пять, где **не должен**.

### Прогрессивное раскрытие

Принцип: держать `SKILL.md` коротким и вести остальное «по ссылкам». В скилле `write-testcase` справочные материалы (`format.md`, `create-case.md`) лежат в `references/` и читаются только когда нужны. Так в контекст не попадает то, что не относится к текущему шагу. Рекомендации, которые стоит помнить: `SKILL.md` меньше примерно пятисот строк; ссылки на справочники прямо из него, на один уровень вглубь; длинные справочники с оглавлением.

### Скрипты внутри скиллов

Всё, что можно сделать **детерминированно**, лучше положить в скрипт: запрос к API, подстановка значений, разбор файла. Модель вызывает скрипт, а не пересказывает его логику, и результат воспроизводим. Правила репозитория `qa-toolkit` для таких скриптов стоит скопировать в любой проект:

- плоские функции над простыми данными, никаких абстракций «на вырост»;
- одна функция делает одно дело;
- без новых зависимостей: стандартная библиотека и системные утилиты;
- докстринги и комментарии на английском, у каждой функции описание «что и зачем»;
- сообщения об ошибках подсказывают, что делать: «Set env VAR», а не голый `None`;
- креды только из переменных окружения; ничего специфичного для продукта не зашито в код.

Заметьте, что это те же принципы, что в уроке про [качество кода](lesson:a4-refactoring): скрипт читают люди, которые не авторы, поэтому простота важнее универсальности.

### Шаблонные скиллы

Многие скиллы набора помечены «шаблон»: в них вместо конкретных значений стоят `<ПЛЕЙСХОЛДЕРЫ>` (адрес Jira, ключи проекта), список в файле `PLACEHOLDERS.md`, а скилл `bootstrap-toolkit` запускается один раз после копирования, собирает профиль проекта и подставляет значения. Это приём **«база для форка»**: знания о процессе общие, детали вашей среды подставляются при настройке. Продуктовых знаний в базовом наборе нет специально.

## Пишем скилл для этого проекта

Что дорого и повторяется? Добавление нового языка сайта. В коде язык размазан по нескольким местам, и агенту без подсказки легко пропустить часть. Вот скилл целиком.

```markdown
---
name: add-locale
description: Adds a new site language to the Pro Anima volunteer site (a fourth locale next to ru, en, sr). Use when the user asks to add, enable or translate the site into another language, or to check that a language is complete.
---

# add-locale

Adding a language touches code, messages, data and tests. Work in this order and run
the tests after every step.

## 1. Find every place a language is listed
Search the Kotlin sources for an existing code to find all touchpoints, do not trust a
fixed list: grep for `"sr"`, `AppLocale.SR` and `.sr` (not only in `model/`).
Expect at least: the `AppLocale` enum, the `LocalizedText` fields and its `valueFor`,
the admin form languages, the font subsets in `views/Layout.kt`, the typography rules in
`service/DisplayHelpers.kt`, the Java locale tag for dates in `service/SectionService.kt`.

## 2. Add the code
- Add the value to `AppLocale` with the code and the language's own name.
- Add the field to `LocalizedText` and extend `valueFor`.
- Extend the admin language lists.

## 3. Add the texts
- Create `src/main/resources/i18n/messages_<code>.properties` with EVERY key of
  `messages.properties`. Missing keys silently fall back to English: check with the
  missing-keys test, do not eyeball it.
- Create `content/volunteer-guide.<code>.md` (quotes in the language's own style, never
  the straight `"`).

## 4. Data
- Existing JSON records need no migration: a missing field reads as empty and falls back.
- Ask the user whether the translations will be added through the admin panel.

## 5. Verify
- Run `./gradlew test`.
- Open `/<code>`, `/<code>/cats`, `/<code>/guide` and check the header does not change height
  when the language is switched.
- Update `docs/smoke-checklist.md` and `docs/decisions.md` ("Language").

## Do not
- Do not machine-translate the guide without telling the user.
- Do not change existing routes or addresses.
```

Что в нём хорошего: описание называет и действие, и фразы пользователя; шаги упорядочены и проверяемы («запусти тесты после каждого шага»); есть **стратегия поиска** (искать код существующего языка), а не список файлов, который устареет; есть явные «чего не делать» и вопрос к человеку там, где решение его.

Заметьте и вывод об архитектуре: чтобы добавить язык, нужно изменить **много** файлов (а не просто значение `enum`). Такое наблюдение, сформулированное при написании скилла, само по себе вклад в проект: возможно, пора вынести список языков в одно место.

## Проверка скилла: линтер и тесты запросов

Скилл — это текст, который можно проверять автоматически и вручную.

**Автоматически:** структура (имя, описание, длина). Напишем простой линтер, чтобы увидеть правила.

::::live Линтер SKILL.md по тестам
Правила (рекомендации, на которые опирается курс): имя из строчных латинских букв, цифр и дефисов до 64 символов; описание не пустое и не длиннее 1024 символов и содержит слова про момент использования; тело не длиннее 500 строк.

:::step Шаг 1. Разбор frontmatter
Файл начинается с `---`, затем строки `ключ: значение`, затем `---`. Вернём пары и тело.

```kotlin
fun parseSkill(text: String): Pair<Map<String, String>, String>? {
    val lines = text.lines()
    if (lines.firstOrNull()?.trim() != "---") return null
    val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
    if (end < 0) return null
    val meta = lines.subList(1, end + 1).filter { ":" in it }
        .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
    return meta to lines.drop(end + 2).joinToString("\n")
}
```
:::

:::step Шаг 2. Правила как функция
Каждая проблема это строка-сообщение, которое подсказывает исправление.

```kotlin
fun lint(text: String): List<String> {
    val (meta, body) = parseSkill(text) ?: return listOf("the file must start with a --- frontmatter block")
    val problems = mutableListOf<String>()
    val name = meta["name"].orEmpty()
    if (!Regex("[a-z0-9-]{1,64}").matches(name)) problems += "name must be 1-64 chars of a-z, 0-9 and hyphens"
    ...
}
```
:::

:::step Шаг 3. Тесты: хороший и плохие скиллы
Запустите. Измените правила и посмотрите, какие тесты упадут.

```test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun parseSkill(text: String): Pair<Map<String, String>, String>? {
    val lines = text.lines()
    if (lines.firstOrNull()?.trim() != "---") return null
    val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
    if (end < 0) return null
    val meta = lines.subList(1, end + 1).filter { ":" in it }
        .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
    return meta to lines.drop(end + 2).joinToString("\n")
}

fun lint(text: String): List<String> {
    val (meta, body) = parseSkill(text) ?: return listOf("the file must start with a --- frontmatter block")
    val problems = mutableListOf<String>()

    val name = meta["name"].orEmpty()
    if (!Regex("[a-z0-9-]{1,64}").matches(name)) {
        problems += "name must be 1-64 characters: lowercase letters, digits and hyphens"
    }

    val description = meta["description"].orEmpty()
    if (description.isBlank()) {
        problems += "description is empty: it is how the agent decides to load the skill"
    } else {
        if (description.length > 1024) problems += "description is longer than 1024 characters"
        if (!Regex("\\bwhen\\b", RegexOption.IGNORE_CASE).containsMatchIn(description)) {
            problems += "description should say WHEN to use the skill (for example: Use when ...)"
        }
    }

    if (body.lines().size > 500) problems += "the body is longer than 500 lines: move detail into references/"
    return problems
}

class SkillLintTest {
    private val good = """
        ---
        name: add-locale
        description: Adds a new site language. Use when the user asks to add or translate the site into another language.
        ---

        # add-locale
        1. Find every place a language is listed.
    """.trimIndent()

    @Test
    fun `a good skill has no problems`() {
        assertEquals(emptyList(), lint(good))
    }

    @Test
    fun `a missing frontmatter is reported`() {
        assertEquals(listOf("the file must start with a --- frontmatter block"), lint("# just a title"))
    }

    @Test
    fun `a bad name and an empty description are reported`() {
        val bad = "---\nname: Add Locale!\ndescription:\n---\nbody"
        val problems = lint(bad)

        assertTrue(problems.any { it.startsWith("name must be") })
        assertTrue(problems.any { it.startsWith("description is empty") })
    }

    @Test
    fun `a description that does not say when to use the skill is reported`() {
        val vague = "---\nname: helper\ndescription: Helps with various things.\n---\nbody"

        assertTrue(lint(vague).any { it.contains("WHEN") })
    }

    @Test
    fun `a very long body is reported`() {
        val long = "---\nname: big\ndescription: Does a lot. Use when asked.\n---\n" + "line\n".repeat(600)

        assertTrue(lint(long).any { it.contains("500 lines") })
    }
}
```
:::

:::step Шаг 4. Чего линтер не умеет
Он проверяет форму, но не **качество**: подходит ли описание под реальные запросы, понятны ли шаги. Это проверяется запросами. Составьте таблицу: пять формулировок, где скилл должен сработать (`«добавь сербский»`, «переведи сайт на немецкий», «проверь, что все языки полные»), и пять, где нет («переведи этот абзац», «исправь опечатку в гайде»). Запустите их с агентом и посмотрите, срабатывает ли скилл и делает ли то, что надо. Если срабатывает не там, сужайте описание; если не срабатывает, добавьте слова пользователей.
:::
::::

**Вручную:** прогоните скилл на реальной задаче и посмотрите, где агент отступил от инструкции: это место, где текст неясен или шаг пропущен. Исправьте текст и повторите. Скилл улучшается итеративно, как и код.

## Хуки: гарантии вместо просьб

Хук это команда, которую **среда** запускает на событие (до или после использования инструмента, при завершении ответа). Модель не может её «забыть». Конфигурация реального плагина:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          { "type": "command", "command": "\"${CLAUDE_PLUGIN_ROOT}\"/hooks/protect-plugin-writes.sh" }
        ]
      },
      {
        "matcher": "Bash",
        "hooks": [
          { "type": "command", "command": "\"${CLAUDE_PLUGIN_ROOT}\"/hooks/block-marketplace-force-push.sh" }
        ]
      }
    ]
  }
}
```

`PreToolUse` срабатывает **до** действия, `matcher` выбирает инструменты (`Bash`, `Write|Edit`), скрипт получает описание вызова на стандартный ввод в JSON. Сценарий, который защищает этот хук: репозиторий плагина публикуется для других людей, и переписывание истории (`git push --force`, `commit --amend`) ломает им обновление. Хук проверяет, что команда похожа на force-push, что репозиторий действительно «плагин с маркетплейсом», и блокирует. Правило «не делай force-push» существует и в тексте инструкций, но **хук исполняет его всегда**.

Хук читает вызов из стандартного ввода, а завершающий код сообщает решение: `0` пропустить, `2` заблокировать и вернуть сообщение с причиной модели (она увидит и исправит). Простой пример для нашего проекта: не давать писать русские комментарии в коде. Логика проверки, которую выполнял бы скрипт:

```run
fun hasCyrillicComment(line: String): Boolean {
    val withoutStrings = line.replace(Regex("\"(?:[^\"\\\\]|\\\\.)*\""), "\"\"")
    val comment = Regex("//(.*)$").find(withoutStrings)?.groupValues?.get(1) ?: return false
    return comment.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' }
}

fun main() {
    listOf(
        "val x = 1 // the answer",
        "val x = 1 // ответ",
        "val s = \"это строка\" // fine: the text is in a string",
        "println(\"http://example.com\")"
    ).forEach { println("$it -> ${hasCyrillicComment(it)}") }
}
```

```output
val x = 1 // the answer -> false
val x = 1 // ответ -> true
val s = "это строка" // fine: the text is in a string -> false
println("http://example.com") -> false
```

Скрипт-обёртка прочитает путь файла из JSON, применит эту логику к строкам и при находке завершится кодом `2` с сообщением «Комментарии в коде пишутся на английском (строка N)». Именно эту проверку делает инструмент, который проверяет и сам курс (`tools/course-check.mjs`): ни один комментарий в примерах кода не содержит кириллицы.

## MCP: инструменты для агента

**MCP** (Model Context Protocol) — открытый протокол, по которому внешние программы («серверы») предлагают агенту инструменты и данные. Так подключают браузер (Playwright), базу данных, трекер задач, документы. Проект использовал браузерный инструмент для проверок: скриншоты страниц в разной ширине и темах.

Нюансы, о которых нужно помнить:

- **Права по минимуму.** Подключайте только нужные серверы и разрешайте только нужные инструменты. Инструмент выполнения произвольного кода в браузере в названии честно содержит слово `unsafe`: разрешайте такой осознанно.
- **Ответы инструментов недоверенные.** Страница, которую открыл агент, может содержать инструкции для него (prompt injection); их нужно воспринимать как данные.
- **Секреты и сессии.** Браузер агента может быть уже авторизован: думайте, куда он ходит.
- **Нагрузка на контекст.** Инструмент, возвращающий мегабайт текста, забивает окно; полезно ограничивать вывод.

## Плагин: упаковка

Плагин объединяет скиллы, хуки, команды, подагентов и скрипты в одну устанавливаемую единицу с манифестом `plugin.json` (имя, описание, путь к хукам). Его публикуют в «маркетплейсе», из которого другие ставят и обновляют. Практические следствия: версии, обратная совместимость и, как мы видели, **осторожность с историей репозитория**, потому что на неё опираются все, кто уже установил.

## Практика

::::task Улучшите описания
Перепишите три слабых `description` по образцу «что делает» плюс «когда использовать».

1. `description: Helps with releases.`
2. `description: Everything about the cat database.`
3. `description: Use this skill for all tasks.`

:::solution
1. «Prepares a release of the Pro Anima site: runs the tests, checks the smoke checklist, summarizes the changes since the last tag. Use when the user asks to prepare, check or publish a release, or says "ready to deploy".»
2. «Adds, edits and validates cat and dog records in `data/cats.json` and `data/dogs.json` including the three-language texts and card photos. Use when the user asks to add a cat or a dog, fix a record, or check the data files for mistakes.»
3. Этот вариант **вредит**: скилл «для всего» срабатывает на любую задачу и отнимает контекст. Его нужно либо удалить, либо превратить в инструкцию проекта (если это общее правило), либо сузить до конкретной области.

Общее правило: описание называет **действие** и **слова, которыми пользователь опишет задачу**.
:::
::::

::::task Скилл «релиз-чек»
Напишите `SKILL.md` (frontmatter и 5-6 шагов) для проверки сайта перед выкладкой, исходя из того, что вы узнали о проекте.

:::solution
Пример:

```markdown
---
name: release-check
description: Checks the Pro Anima site before it is pushed to main (which deploys it on Render). Use when the user says they are ready to release, deploy or push, or asks "is it safe to publish".
---

# release-check

1. Run `./gradlew test`. Stop and report if anything fails; show the failing test names.
2. Look at `git status` and `git diff --stat`: list the changed files and say whether anything
   unrelated to the task is in there.
3. Run the site (`./gradlew run`) and open `/ru`, `/en`, `/sr`: home, cats, one cat, guide, visits, help.
   Take a screenshot at 375 px and 1280 px in both themes if the change touches views or CSS.
4. Check the content: every new message key exists in all three `messages_*.properties` files.
5. Read the diff for secrets (passwords, tokens, connection strings) and leftover debug output.
6. Report the result as a short checklist. Do NOT push: pushing to main deploys the site, ask the user.
```

Что здесь важно: последний шаг явно запрещает необратимое; шаги проверяемы; в описании есть слова, которыми человек действительно скажет («готов к релизу», «безопасно ли выкладывать»).
:::
::::

::::task Хук против пуша в main
Опишите (словами и псевдокодом/скриптом) хук `PreToolUse` для `Bash`, который **блокирует `git push` в ветку `main`**, если нет файла-разрешения `.allow-push`. Какой код завершения и какое сообщение вы вернёте?

:::solution
```bash
#!/bin/bash
# PreToolUse (Bash): refuse "git push" to main unless the user created .allow-push.
input=$(cat)
command=$(echo "$input" | jq -r '.tool_input.command // empty')

echo "$command" | grep -qE 'git[^&|;]*push' || exit 0
echo "$command" | grep -qE '(^|[[:space:]])main([[:space:]]|$)|HEAD' || exit 0
[ -f .allow-push ] && exit 0

echo "Push to main deploys the site. Ask the user, who creates .allow-push to allow it once." >&2
exit 2
```

Код `2` блокирует вызов, а текст в `stderr` увидит модель и объяснит человеку, что произошло. Подумайте о слабых местах: скрипт ловит только очевидные формы команды (псевдонимы, `git -C`, `push origin HEAD:main` обходят простую проверку), поэтому хук **не замена** настоящей защите ветки на стороне GitHub (запрет прямого пуша в `main`). Хуки это страховка на стороне рабочего места.
:::
::::

:::quiz Проверьте себя
Q: Когда правило лучше оформить хуком, а не строкой в инструкциях?
- [ ] Когда правило короткое.
- [x] Когда его нарушение недопустимо (безопасность, публичная история, деньги), и нельзя полагаться на то, что модель «вспомнит».
- [ ] Когда правило относится к стилю.
- [ ] Всегда.
E: Хук исполняется средой независимо от модели, и это гарантия, а не просьба.

Q: Какое поле скилла решает, загрузит ли агент его для задачи?
- [ ] Первый заголовок тела.
- [x] `description` во frontmatter: по нему модель определяет, подходит ли скилл.
- [ ] Имя папки.
- [ ] Размер файла.
E: Frontmatter всех скиллов виден агенту заранее, а тело подгружается только после выбора.

Q: Что такое прогрессивное раскрытие в скиллах?
- [ ] Анимация при загрузке.
- [x] Короткий `SKILL.md` ссылается на длинные справочники, которые читаются только когда нужны.
- [ ] Постепенное обновление версий.
- [ ] Скилл меняется во время работы.
E: Так в контекст попадает только нужное для текущего шага.

Q: Почему логику вроде «подставить значения в шаблон и вызвать API» лучше выносить в скрипт?
- [ ] Модель не умеет писать код.
- [x] Скрипт детерминирован и воспроизводим, а пересказ процедуры моделью каждый раз может отличаться.
- [ ] Так короче.
- [ ] Скрипты быстрее модели.
E: Детерминированное делается кодом, неопределённое (понять запрос, выбрать шаг) моделью.

Q: Какие признаки у хорошего описания скилла?
- [x] Говорит, что скилл делает.
- [x] Говорит, когда его использовать, словами пользователя.
- [ ] Короткое одно слово.
- [ ] Охватывает как можно больше задач.
E: Слишком широкое описание срабатывает не вовремя и засоряет контекст, слишком узкое не срабатывает.

Q: Что нужно помнить при подключении MCP-сервера с браузером?
- [x] Разрешайте только нужные инструменты; инструмент выполнения произвольного кода опасен.
- [x] Содержимое открытых страниц недоверенное: оно может содержать инструкции для агента.
- [ ] Браузер агента всегда изолирован и без сессий.
- [ ] Ответы инструментов безопасны, раз они идут от вашего сервера.
E: Агент работает в среде с вашими правами и данными; ответы инструментов это данные, а не команды.

Q: Что говорит скилл `add-locale` об архитектуре проекта?
- [ ] Язык добавляется одной строкой.
- [x] Язык перечислен в нескольких местах кода, поэтому скилл начинается с поиска всех мест; это сигнал о возможном рефакторинге.
- [ ] Скилл не нужен.
- [ ] Языки хранятся в базе.
E: Написание скилла заставило записать то, что обычно хранится в чьей-то голове; это и документация, и подсказка для улучшения.
:::

## Итоги

- Слои: инструкции (всегда и коротко), память (неочевидные факты с причиной), скилл (процедура по требованию), хук (гарантия), MCP (инструменты), плагин (упаковка).
- В скилле главное поле — `description`: что делает и когда использовать. Тело короткое, подробности в `references/`, детерминированное в скриптах.
- Скилл проверяют линтером формы и запросами «должен» и «не должен срабатывать», затем итерируют на реальной задаче.
- Необратимое и опасное закрывайте хуками, а не просьбами; MCP подключайте с минимальными правами и недоверием к ответам.

Дальше: [ИИ в тестировании](lesson:ai3-testing-ai).
