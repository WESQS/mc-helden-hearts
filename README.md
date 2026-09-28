# MC Helden Hearts

Fabric-Server-Mod für Minecraft **1.21.1**.

- Jeder Spieler startet mit 2 Heldenherzen.
- Bei jedem Tod geht ein Herz verloren.
- Bei 0 Herzen folgt ein 24-Stunden-Bann.
- Beim nächsten Beitritt werden 20% des vorherigen Inventar-Loots zurückgegeben.
- Der aktuelle Stand wird dauerhaft über der Hotbar angezeigt.

## Bauen

Java 21 und Gradle verwenden:

```bash
./gradlew build
```

Die fertige JAR liegt danach in `build/libs/`. Kopiere sie zusammen mit Fabric Loader und Fabric API in den `mods`-Ordner deines Servers.
