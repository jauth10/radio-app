# Radio-App – Startanleitung (Anhang H)

Das Projekt besteht aus drei Gradle-Modulen:

| Modul | Inhalt |
|---|---|
| `:app` | Die Android-App (Hörer- und Moderationsfunktionen) |
| `:contract` | Gemeinsame Datentypen und Pfade von App und Stub |
| `:stub-server` | Der Stub-Server, der die Schnittstellen S1 bis S4 der Sendersysteme nachbildet |

Die App spricht nur mit dem Stub. Ohne laufenden Stub zeigt sie „Keine Verbindung“.

## Voraussetzungen

- JDK 17 oder neuer (die mit Android Studio gelieferte JDK genügt)
- Android Studio mit Android SDK 37; Emulator oder Gerät ab Android 8 (API 26)
- `adb` im Pfad (liegt in `<Android-SDK>/platform-tools`)

Der Stub braucht kein Android SDK, er läuft auf jedem Rechner mit JDK.

## Schnellstart

Die Reihenfolge ist wichtig. Befehle im Projektverzeichnis; unter Windows `gradlew.bat` statt `./gradlew`.

1. **Stub starten:** `./gradlew :stub-server:run`
   Fertig, wenn `Responding at http://…:8080` erscheint. Der Stub hält seine Daten nur im Speicher; ein Neustart setzt alles zurück.
2. **Emulator starten** oder ein Gerät per USB anschließen (USB-Debugging an).
3. **Weiterleitung einrichten:** `adb reverse tcp:8080 tcp:8080`
   Prüfen mit `adb reverse --list`.
4. **App installieren und starten:** in Android Studio „Run ‚app‘“ oder `./gradlew :app:installDebug`.

**Wichtig:** Die Weiterleitung aus Schritt 3 geht bei jedem Neustart des Emulators oder des adb-Servers verloren und muss dann wiederholt werden. Ohne sie zeigt die App nur „Keine Verbindung“, ohne weiteren Hinweis.
Tipp: In Android Studio unter *Run → Edit Configurations → app → Before launch* eine Aufgabe „Run External Tool“ mit `adb reverse tcp:8080 tcp:8080` ergänzen, dann läuft der Schritt vor jedem Start.

### Warum Loopback und nicht 10.0.2.2

Ab Android 17 (API 37) erreicht die App den Stub unter der Emulator-Adresse `10.0.2.2` nur mit der Berechtigung `ACCESS_LOCAL_NETWORK`. Über Loopback (`127.0.0.1`) und `adb reverse` braucht es keine Berechtigung; so wurde die App abgenommen.

## Andere Adressen einstellen

Die Basis-URL der App steht beim Bauen fest (`API_BASE_URL`, Standard `http://127.0.0.1:8080/`). Anderer Wert beim Bauen:

```
./gradlew :app:installDebug -PapiBaseUrl=http://10.0.2.2:8080/
```

| Aufbau | Basis-URL | `adb reverse` nötig |
|---|---|---|
| Emulator oder Gerät mit adb (Standard) | `http://127.0.0.1:8080/` | ja |
| Emulator bis Android 16 ohne adb-Weiterleitung | `http://10.0.2.2:8080/` | nein |
| Gerät ohne adb im selben WLAN | `http://<Adresse des Rechners>:8080/` | nein |

Beim Gerät im WLAN muss die Firewall des Rechners Port 8080 zulassen. Debug-Builds erlauben unverschlüsseltes HTTP zu jedem Host, Release-Builds nur zu Loopback und `10.0.2.2`. Dieser Weg wurde auf Android 17 nicht geprüft; dort kann zusätzlich die Berechtigung `ACCESS_LOCAL_NETWORK` nötig sein (siehe oben).

### Port 8080 ist belegt

Meldet der Start `Address already in use`, nutzt ein anderes Programm den Port. Der Stub lässt sich verschieben, die App bleibt unverändert, weil `adb reverse` nur das Ziel ändert:

```
STUB_PORT=9090 ./gradlew :stub-server:run          # PowerShell: $env:STUB_PORT=9090; ./gradlew :stub-server:run
adb reverse tcp:8080 tcp:9090
```

Mit `netstat -ano | findstr :8080` lässt sich der Verursacher finden.

## Zugangsdaten und Regeln des Stubs

- **Moderationscodes:** `ALEX-2026` (Alex Host) und `SAM-2026` (Sam Host). Die Sitzung gilt 1 Stunde.
- **Sperre:** Nach 5 falschen Codes ist die Anmeldung auf diesem Gerät 5 Minuten gesperrt, auch mit richtigem Code.
- **Archiv:** drei Titel (`trk-1` Sample Song, `trk-2` Second Song, `trk-3` Third Song). `trk-3` ist nicht sendefähig. Eine Suche braucht mindestens 2 Zeichen.
- **Wünsche:** höchstens 3 pro Stunde und Hörer.
- **Bewertungen:** eine pro Ziel (Playlist oder Moderation) und Stunde.

Diese Regeln sind Annahmen des Stubs, keine Vorgaben der Sendersysteme.

## Stub-Steuerung (Testwerkzeug)

`POST /stub/control` und `DELETE /stub/control` sind **ausdrücklich kein Bestandteil der angenommenen Senderlandschaft**, sondern ein Testwerkzeug. Sie machen das Offline- und Fehlerkonzept reproduzierbar und vorführbar. Der Stub lauscht auf allen Netzwerkschnittstellen; im selben Netz kann jeder die Steuerung nutzen.

Die Beispiele gelten für Git Bash, macOS und Linux. In PowerShell: `Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/stub/control -ContentType application/json -Body '<JSON>'`.

```
curl -X POST http://127.0.0.1:8080/stub/control -H "Content-Type: application/json" -d '<JSON>'
curl -X DELETE http://127.0.0.1:8080/stub/control        # nimmt alle Fehlerbilder zurück
```

| Aktion (`<JSON>`) | Wirkung |
|---|---|
| `{"action":"changeTrack"}` | nächster Titel läuft (rundherum); `"trackId":"trk-2"` wählt einen bestimmten |
| `{"action":"changeTrack","talkSegment":true}` | Moderationsbeitrag: `/playout/current` antwortet 204 |
| `{"action":"injectFailure","code":"503","area":"all","count":3}` | die nächsten 3 Anfragen scheitern |
| `{"action":"dropConnection"}` | trennt den Ereigniskanal; die App verbindet neu |
| `{"action":"dropConnection","reason":"token"}` | beendet die Moderationssitzung (App führt zur Anmeldung) |

Fehlerbilder (`code`): `503`, `422`, `409`, `401`, `timeout` (hält die Anfrage `durationMs` lang, Standard 30 s, dann 504) und `offline` (trennt die Verbindung sofort).
Bereiche (`area`): `playout`, `archive`, `requests`, `ratings`, `eventChannel`, `all`. `count` (1 bis 1000) zählt die betroffenen Anfragen. Ungültige Steuerungsanfragen antworten 400.

### Vorführung

- **Keine Verbindung zeigen:** `{"action":"injectFailure","code":"offline","area":"all","count":1000}`, danach `DELETE`.
  Der Flugmodus eignet sich in diesem Aufbau **nicht**: Die Verbindung läuft über die adb-Weiterleitung, nicht über das Netz des Geräts; die App liest im Flugmodus weiter vom Stub. Auch `adb reverse --remove-all` trennt eine offene Verbindung nicht zuverlässig. Alternativ den Stub beenden.
- **Veralteter Stand:** Programm mit Verbindung laden, auf „offline“ schalten und länger als 5 Minuten warten.
- **Ausstehende Zustellung:** auf „offline“ schalten, Wunsch oder Bewertung absenden (Status „offen“), dann `DELETE`. Der Hintergrundauftrag wiederholt mit wachsendem Abstand ab 30 Sekunden und stellt dann zu. Nach 5 erfolglosen Versuchen steht der Eintrag auf „fehlgeschlagen“ und lässt sich von Hand erneut senden.
- **Abfragebetrieb der Moderationssicht:** `{"action":"injectFailure","code":"503","area":"eventChannel","count":5}`, dann `{"action":"dropConnection"}`. Die Moderationssicht zeigt „verbindet neu“ mit Versuchszahl 1 bis 4 und schaltet auf Abfragen um; nach `DELETE` kehrt sie nach etwa einer Minute zum Ereigniskanal zurück.
- **Bewertung von außen:** als zweiter Hörer, zum Beispiel für die Moderationssicht (Schlüssel und `listenerId` bei jedem Aufruf ändern):

```
curl -X POST http://127.0.0.1:8080/ratings -H "Content-Type: application/json" -H "Idempotency-Key: demo-1" \
  -d '{"target":"PLAYLIST","referenceId":"show-1","value":5,"comment":"Super","listenerId":"demo-1","timestamp":"2026-10-14T12:00:00Z","displayName":"Demo"}'
```

## Tests ausführen

- Stub-Server (Routen S1 bis S4, Idempotenz, Ereigniskanal, Fehlerbilder): `./gradlew :stub-server:test`
- Unit-Tests der App: `./gradlew :app:testDebugUnitTest` (braucht das Android SDK)
- Oberflächentests der App: `./gradlew :app:connectedDebugAndroidTest` (braucht Emulator oder Gerät)

## Fehlersuche

| Symptom | Ursache und Abhilfe |
|---|---|
| App zeigt „Keine Verbindung“ | Läuft der Stub? `curl http://127.0.0.1:8080/playout/current` muss antworten. Steht die Weiterleitung? `adb reverse --list`. Ist ein Fehlerbild scharf? `DELETE /stub/control`. |
| Stub startet nicht, `Address already in use` | Port belegt, siehe oben (`STUB_PORT`). |
| Nach Neustart des Emulators wieder „Keine Verbindung“ | `adb reverse` erneut ausführen. |
| Anmeldung meldet „gesperrt“ | 5 Minuten warten oder den Stub neu starten. |
