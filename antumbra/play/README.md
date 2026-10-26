# Pubblicazione su Google Play

Guida operativa per pubblicare Antumbra. I testi della scheda sono in `fastlane/metadata/android/` (inglese e italiano), le grafiche in questa cartella.

## 1. Prima del primo caricamento (decisioni permanenti)

- **Application ID**: `appId` in `gradle.properties` (ora `app.antumbra.browser`). Dopo il primo upload non si può più cambiare.
- **Chiave di upload**: crea `upload.jks` come da README e conservane due copie offline. Con Play App Signing, Google firma l'app; se perdi la chiave di upload puoi chiederne il reset, ma serve tempo.
- **Endpoint segnalazioni**: imposta `reportEndpoint`. La policy Play sui contenuti generati dall'AI richiede che l'utente possa segnalare una risposta senza uscire dall'app. Va bene un form Formspree o un tuo server che accetti un POST JSON.
- **Informativa privacy**: pubblica `docs/privacy.html` (per esempio con GitHub Pages), sostituisci `CONTACT_EMAIL` e metti l'URL in `privacyPolicyUrl`.

## 2. Account sviluppatore

Gli account personali creati dopo novembre 2023 devono fare un **test chiuso con almeno 12 tester per 14 giorni consecutivi** prima di poter pubblicare in produzione. Pianificalo: carica la build nella traccia "Test chiuso" e invita i tester per email o con un gruppo Google.

## 3. Build

```
./gradlew bundleRelease
```

Carica `app/build/outputs/bundle/release/app-release.aab`. Prima di farlo, in Android Studio apri l'AAB con **Build › Analyze APK** e controlla che le librerie native risultino allineate a 16 KB (requisito Play per le app con target Android 15+). GeckoView e tor-android recenti lo sono; se una libreria segnala un problema, aggiorna la dipendenza.

## 4. Scheda dello store

| Campo | Valore |
|---|---|
| Nome | `title.txt` (28 caratteri, limite 30) |
| Descrizione breve | `short_description.txt` (limite 80) |
| Descrizione completa | `full_description.txt` |
| Icona | `play/icon-512.png` |
| Grafica in evidenza | `play/feature-graphic-en.png`, `play/feature-graphic-it.png` |
| Categoria | Comunicazione (o Strumenti) |
| Screenshot | Da fare sul telefono: avvio/connessione, pagina iniziale, menu, circuito, impostazioni connessione, chat AI. Almeno 4, formato 9:16. |

Non usare "Tor" nel nome: è un marchio del Tor Project. La descrizione lo cita solo in modo descrittivo, con la nota di non affiliazione già inclusa.

## 5. Contenuti dell'app (Play Console › Policy › Contenuti dell'app)

**Accesso all'app**: nessun login richiesto. Nota per i revisori:
> No account is needed. Tap Connect on the first screen. The AI assistant is optional and requires the user's own OpenRouter API key; reviewers can use "Open Duck.ai" on the assistant screen instead.

**Annunci**: No.

**Pubblico di destinazione**: 18+. Evita le regole Famiglie e copre la chat AI.

**Classificazione dei contenuti**: rispondi Sì ad "accesso illimitato a internet" e a "contenuti generati dall'AI"; No a tutto il resto.

**Sicurezza dei dati** (scelta prudente):
- Dati raccolti: **Messaggi › Altri contenuti generati dagli utenti** (le domande inviate al provider AI scelto dall'utente e le segnalazioni facoltative). Facoltativo, scopo "Funzionalità dell'app", non condiviso per pubblicità.
- Dati condivisi: nessuno.
- Dati cifrati in transito: Sì.
- L'utente può chiedere la cancellazione: non applicabile (nessun account, nulla conservato dallo sviluppatore).
- Nessun altro dato: niente posizione, identificativi, cronologia di navigazione, diagnostica.

**Servizio in primo piano (specialUse)**: dichiaralo così.
- Descrizione: *"When the user turns on 'Keep running in the background' in Settings › Apps and wallets, Antumbra keeps a local Tor SOCKS proxy running so that other apps the user has configured (for example cryptocurrency wallets) can connect through Tor. A persistent notification with a Stop action is shown while it runs."*
- Impatto se interrotto: *"The user's other apps immediately lose their Tor connection and fail or fall back to their own network settings."*
- Video: registra lo schermo mentre attivi l'opzione nelle impostazioni e mostri la notifica. Caricalo su YouTube come non in elenco.

**App finanziarie / crypto**: Antumbra non è un wallet e non gestisce fondi; se richiesto, dichiaralo così.

**Notizie, salute, governo**: No.

## 6. Dopo la pubblicazione

- GeckoView esce ogni quattro settimane e chiude falle di sicurezza: aggiorna e ripubblica a ogni release. Dependabot apre la PR da solo.
- Gli aggiornamenti dallo store si installano sopra la versione esistente: gli utenti non perdono le impostazioni.
- Aumenta `appVersionCode` a ogni caricamento.
