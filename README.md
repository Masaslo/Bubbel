# Bubbel 🫧

Bubbel is een volledig lokale Android-app voor mensen met misofonie of andere auditieve aandoeningen. De app ontvangt live microfoongeluid, behoudt menselijke stemmen en dempt niet-spraakgeluiden die misofonie kunnen triggeren. Primair bedoeld voor gebruik in klaslokalen met draadloze microfoons.

**Audio wordt nooit opgeslagen of verzonden.** Geen internetpermissie, geen accounts, geen analytics, geen cloud.

## Status

- **Fase:** planfase (MVP-ontwerp vastgelegd, nog geen app-code)
- **Filterkern:** DeepFilterNet3 (besluit 2026-08-31)
- **Plan:** [PLAN.md](PLAN.md) — volledige MVP-specificatie met test- en acceptatiecriteria

## Features (MVP)

- Voice isolation: filtert alle geluiden weg behalve de geselecteerde stem
- Drie filterstanden: Natuurlijk, Gebalanceerd (standaard), Sterk
- White noise generation
- Foreground service voor lessen van minimaal 90 minuten met uitgeschakeld scherm
- Bedrade, USB- en Bluetooth-audio ondersteund (Bluetooth: waarschuwing voor extra latency)

## Tech stack

- Kotlin + Jetpack Compose (UI)
- C++-audiokern
- Oboe/AAudio voor full-duplex microfoon-naar-output met lage latency
- DeepFilterNet3 via ONNX Runtime Mobile (NNAPI) voor spraakfiltering
- Mono-audio op 48 kHz

## Roadmap

- **MVP:** voice isolation + white noise, 90-min lessen, één gebruiker, één toestel
- **Na MVP:** AGC/limiter tegen onverwacht harde pieken; eventueel gespecialiseerd model als mondgeluiden onvoldoende worden gedempt
- **Buiten v1:** opnemen, transcriberen, cloudintegraties

## Bijdragen

Zie [PLAN.md](PLAN.md) voor de volledige scope. Issues en PR's zijn welkom.

**Branch naming:** gebruik voor wijzigingen altijd een branch met een van deze prefixes:

- `feature/` — nieuwe functionaliteit
- `docs/` — documentatie
- `fix/` — bugfixes

Wijzigingen gaan via een pull request naar `main`; direct pushen naar `main` is niet toegestaan.

## Licentie

Zie [LICENSE](LICENSE).
