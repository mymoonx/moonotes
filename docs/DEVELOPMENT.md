# 🛠 Moonotes — для разработчиков

Здесь собраны исходники, сборка, проверки и техническая документация Moonotes. **Для обычной установки эти шаги не нужны** — используйте [готовые JAR из Releases](https://github.com/mymoonx/moonotes/releases/latest).

[← Главная страница](../README.md) · [🖼 Галерея](SCREENSHOTS.md) · [📦 Релиз v0.6.0](https://github.com/mymoonx/moonotes/releases/tag/v0.6.0)

## Структура проекта

| Компонент | Каталог | Назначение |
| --- | --- | --- |
| **Moonotes 0.4.1** | [`moonotes/`](../moonotes/) | Клиентский аддон Streamotes: ввод, предпросмотр и каналы текущего мира |
| **Moonotes Network 0.6.0** | [`network/`](../network/) | Каталоги 7TV, библиотека, группы, загрузка, подсказки и HUD |
| **Cloudflare Worker 0.5.3** | [`worker/`](../worker/) | Резервное получение каталогов и изображений, кеш R2 |

Это **два самостоятельных Fabric-мода**, каждый со своим `fabric.mod.json` и JAR. Worker **не является модом Minecraft** и не распространяется в папку `mods`.

## Требования

- **Minecraft Java:** 26.3.
- **JDK:** 25; **Fabric Loader:** 0.20.1+; **Fabric API:** совместимая с 26.3 версия (для проверенного комплекта — 0.165.0+26.3).
- **Streamotes:** 1.2.16+26.3 для Fabric (внешняя зависимость обоих аддонов).
- **Node.js:** 24 для тестов Worker.
- **Gradle Wrapper** уже включён в каталоги модов.

Совместимость с другими версиями Minecraft, портами Streamotes и загрузчиками не подтверждается автоматически.

## Сборка модов

Запустите команды **из корня репозитория**.

**Windows (PowerShell):**
```powershell
.\moonotes\gradlew.bat -p .\moonotes clean build
.\network\gradlew.bat -p .\network clean build
```

**Linux/macOS:**
```bash
bash moonotes/gradlew -p moonotes clean build
bash network/gradlew -p network clean build
```

Готовые устанавливаемые JAR находятся в `moonotes/build/libs/` и `network/build/libs/`. Для установки выбирайте файлы **без** суффиксов `-sources`, `-dev` и `-javadoc`. Проверки Network выполняются в рамках `build`.

## Worker: тесты и развёртывание

**Только локальные проверки (не публикуют Worker):**
```bash
npm --prefix worker ci
npm --prefix worker test
```

Развёртывание Cloudflare — **отдельное действие владельца сервиса**. Оно не происходит при сборке модов или запуске GitHub Actions. Не публикуйте секреты Cloudflare, ключи R2 и приватные данные.

- [Техническая инструкция Worker](../worker/README.md)
- [Контракт API](../worker/API-CONTRACT.md)
- [Реализованные проверки и ограничения](VERIFICATION.md)

## Как устроены сборки и релизы

[GitHub Actions](https://github.com/mymoonx/moonotes/actions) собирает оба мода на Windows/Linux и проверяет Worker на Node.js 24. Результат конкретного запуска следует смотреть в его журнале.

Публикация релизов производится отдельно. Описание процедуры, тегов, артефактов и проверок находится в [PUBLISHING.md](PUBLISHING.md).

## Документация и участие

- [README Moonotes Network](../network/README.md) — особенности Network и обновления.
- [CHANGELOG](../CHANGELOG.md) — история изменений.
- [VERIFICATION](VERIFICATION.md) — историческая проверка v0.5.3; проверки 0.6.0 перечислены в релизе.
- [THIRD-PARTY-NOTICES](../THIRD-PARTY-NOTICES.md) — сторонние компоненты и уведомления о лицензиях.
- [Issues](https://github.com/mymoonx/moonotes/issues) — сообщения о проблемах и предложения.

**Лицензия на собственный код Moonotes пока не назначена.** Публичная доступность исходников сама по себе не означает разрешение на их свободное переиспользование. Учитывайте лицензии сторонних компонентов.
