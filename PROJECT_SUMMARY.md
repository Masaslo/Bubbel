# Project Summary — Bubbel

## Laatste update

- 31 augustus 2026: een lokaal Compose-prototype toegevoegd met een centrale aan/uitknop voor de luistermodus.
- 31 augustus 2026: de homescreen-UI uitgebreid met een radiale stress-naar-rust-bubbeltransitie, ademende actieve ring, bewegende geluidsiconen en een icon-only instellingen-dropdown met grote switches.
- 31 augustus 2026: de motion verfijnd: de emoji gebruikt nu een egale kleurfade, de buitenste bubble een radiale reveal, sounds starten off-screen en verdwijnen via een meegroeiend achtergrondmasker; de vierkante druk-indicator is verwijderd.
- 31 augustus 2026: het volledige pastelpalette gecentraliseerd in een semantisch Material 3-theme met primary-, secondary-, tertiary-, surface-, status- en on-colorrollen.
- Clean Architecture-basis toegevoegd: `domain` bevat status, repositorycontract en toggle-usecase; `data` bevat de lokale repository; `presentation` bevat de ViewModel.
- Oboe 1.10.0 (Prefab) en ONNX Runtime Android 1.29.0 zijn als afhankelijkheden voorbereid. Er is nog geen native audio-bridge, microfoonverwerking of DeepFilterNet3-modelintegratie.
- 31 augustus 2026: preallocated native DSP-primitives toegevoegd voor SPSC-buffering, generieke vaste-sampleframing, profielmixing met 2400-sample ramps en deadlinebewaking. De zelfstandige native assertions zijn op emulator-5554 uitgevoerd; er is nog geen audio-bridge of modelintegratie.
- 31 augustus 2026: voor modelprovenance gekozen voor het officiële, reproduceerbare DeepFilterNet3-model met 480-sample hops en upstream Rust `libDF`/Tract. De eerdere 512-sample ONNX-route is verlaten omdat alleen een niet-reproduceerbare derdepartij-export aan dat contract voldeed; ONNX Runtime wordt verwijderd zodra de officiële runtime succesvol is gekoppeld.
- `repo-opzet` is in `project.json` afgerond; `voice-isolation` blijft de volgende functionele mijlpaal.
