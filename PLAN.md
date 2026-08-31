# Plan voor Bubbel — persoonlijke Android-MVP

> **Laatst bijgewerkt: 2026-08-31** — filterkern gewijzigd naar DeepFilterNet3 (besluit Floris); AGC/limiter verplaatst naar na MVP; spike-validatie vervallen.

## Samenvatting

Bubbel wordt een eenvoudige, volledig lokale Android-app voor gebruik tijdens lessen. De app ontvangt live microfoongeluid, behoudt menselijke stemmen en dempt niet-spraakgeluiden die misofonie kunnen triggeren. Audio wordt nooit opgeslagen of verzonden.

De MVP is voor één gebruiker en één bestaand Android-toestel. Bedrade, USB- en Bluetooth-audio worden ondersteund; bij Bluetooth toont Bubbel dat extra vertraging mogelijk is.

## Implementatie

- Bouw de app native met Kotlin/Compose en een C++-audiokern.
- Gebruik Oboe/AAudio voor full-duplex microfoon-naar-output met lage latency, volgens de [Android low-latency-richtlijnen](https://developer.android.com/games/sdk/oboe/low-latency-audio). (Google, Apache-2.0, actief onderhouden — gekozen 2026-08-31.)
- Gebruik **DeepFilterNet3** (MIT/Apache-2.0) als lokale spraakfilter via ONNX Runtime Mobile/NNAPI, aangevuld met zachte voice-activity-demping. Verwerk mono-audio op 48 kHz. *(Vervangt RNNoise uit het oorspronkelijke plan; RNNoise dempt niet-spraak-triggers zoals kauwen, tikken en mondgeluiden onvoldoende.)*
- Voorzie drie filterstanden:
  - Natuurlijk: lichte demping, maximale verstaanbaarheid.
  - Gebalanceerd: standaardinstelling.
  - Sterk: maximale niet-spraakdemping, met kans op verlies van zachte spraak.
- Startscherm: één grote aan/uitknop, actieve status en huidige audioverbinding.
- Instellingen: filterstand, microfoonkeuze (Automatisch, Telefoon, Headset) en uitgangsvolume.
- Laat Android de audio-uitgang kiezen. Toon waarschuwingen bij Bluetooth-latency en mogelijk rondzingen via de telefoonspeaker, maar blokkeer deze routes niet.
- Draai tijdens gebruik als foreground service (met `foregroundServiceType="microphone"` + `FOREGROUND_SERVICE_MICROPHONE`, vereist op Android 14+), zodat een les van minimaal 90 minuten met uitgeschakeld scherm mogelijk is.
- Vraag alleen microfoon- en foreground-service-permissies. Voeg geen internetpermissie, accounts, analytics of cloudintegraties toe.
- Bewaar uitsluitend instellingen; nooit audio, tijdelijke audiofragmenten of transcripties.
- Behandel de voortijdig aangemaakte scaffold als niet-bindend: controleer of vervang deze tijdens implementatie op basis van dit plan.

## Test- en acceptatiecriteria

- Test eerst onbewerkte microfoon-doorvoer en daarna iedere filterstand op het bestaande referentietoestel.
- Bedraad/USB: streef naar maximaal 50 ms mediane round-trip latency en geen merkbare echo bij afgesloten oortjes of koptelefoon.
- Bluetooth: meet en toon de werkelijke situatie, maar hanteer geen harde latencygarantie.
- Laat Bubbel minimaal 90 minuten onafgebroken draaien zonder crash of audiostream die stilvalt.
- Test docentenspraak, zachte en verre spraak, meerdere stemmen en triggers zoals tikken, typen, schuiven, kauw-/mondgeluiden, ventilatie en verkeer.
- De persoonlijke proef slaagt wanneer:
  - gesproken uitleg verstaanbaar blijft;
  - triggerhinder in de gebalanceerde of sterke stand subjectief minimaal twee punten daalt op een schaal van 0–10;
  - geen filterstand onverwacht harde pieken produceert *(AGC/limiter hiervoor wordt pas na de MVP toegevoegd — besluit 2026-08-31)*;
  - microfoon- en audioapparaatwissels veilig herstellen of Bubbel duidelijk stoppen.
- Meet CPU-, batterij- en temperatuurimpact gedurende één les; richtwaarde is maximaal 20% batterijverbruik per 90 minuten.

## Aannames en roadmap

- Het exacte referentietoestel en de Android-versie worden bij aanvang van de technische test vastgelegd.
- Bubbel is een persoonlijke luisterhulp en wordt in v1 niet als medisch hulpmiddel gepositioneerd.
- Het filter kan niet garanderen dat ieder niet-spraakgeluid verdwijnt; vooral mondgeluiden kunnen op spraak lijken. Praktijktesten bepalen of later een gespecialiseerd model nodig is.
- DeepFilterNet3 is de gekozen filterkern voor de MVP (besluit 2026-08-31); RNNoise/rnnoise-nu is afgevallen. De extra rekenlast en latency (+20–50 ms) worden in de implementatietest gemeten tegen de acceptatiecriteria.
- Opnemen en transcriberen valt buiten v1. Een toekomstige versie kan dit als afzonderlijke, expliciet geactiveerde functie toevoegen, bij voorkeur volledig lokaal en met duidelijke opslag- en verwijderbediening.
- Na de MVP: AGC/limiter in de C++-kern tegen onverwacht harde pieken.
