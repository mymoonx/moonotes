# Moonotes 0.4.1

Основной клиентский аддон к отдельному моду Streamotes: предпросмотр эмотов в строке ввода и выбор Twitch-каналов текущего мира. Команда `/moonotes` открывает менеджер при установленном Moonotes Network.

Для общего режима уведомлений поддерживается Moonotes Network 0.5.2 и новее. Ответы на команды остаются в чате. Без Network сохраняется совместимый вывод сообщений.

Требования: Minecraft Java 26.3, Java 25, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3 и отдельный Streamotes 1.2.16+26.3.

## Сборка из этой папки

Windows:

```powershell
.\gradlew.bat clean build
```

Linux/macOS:

```bash
bash ./gradlew clean build
```

Результат: `build/libs/moonotes-0.4.1.jar`.

[Общая установка и возможности](../README.md) · [Moonotes Network](../network/README.md)
