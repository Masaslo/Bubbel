# Bubbel homescreen-ontwerp

> **Status:** akkoord door Floris op 31 augustus 2026; klaar voor een implementatieplan.

## Doel

Een prikkelarm en direct herkenbaar Android-homescreen bieden waarmee de gebruiker de luister-/filtermodus met een enkele tik aan- of uitzet.

## Richting

Het scherm neemt de aangeleverde schets als bron: een warme, egale achtergrond met een vriendelijke centrale bubbel en een losstaande instellingenknop rechtsboven. De interface gebruikt geen zichtbare tekst; betekenis komt van de iconen, plaatsing en actieve animatie.

## Visuele tokens

| Rol | Waarde | Gebruik |
| --- | --- | --- |
| Achtergrond | `#FFF3BD` | Volledig homescreen |
| Inactieve bubbel | `#E98D88` | Gestresste centrale aan/uitknop |
| Actieve bubbel | `#9AFA97` | Kalme centrale aan/uitknop |
| Actieve ring | `#8E8AE8` | Pulserende statusring |
| Instellingen | `#C8A96A` | Afgeronde tegel rechtsboven |
| Icoon | `#171713` | Gezicht en tandwiel |

De elementen krijgen sterk afgeronde hoeken, ruime padding en geen omlijningen. Schaduwen worden vermeden; de ring en kleur dragen de hiërarchie.

## Themaregel

Alle app-kleuren worden centraal en semantisch gedefinieerd in `ui/theme/Color.kt` en door `BubbelTheme` aangeboden. UI-composables gebruiken uitsluitend die themakleuren (bijvoorbeeld achtergrond, bubbel, actieve ring, instellingen en icoon) en bevatten geen losse hex-waarden of lokaal aangemaakte kleuren. Zo blijven toekomstige schermen visueel consistent en kan het palet op één plek worden aangepast.

## Interactie en status

De centrale bubbel is de enige primaire actie en schakelt de luister-/filtermodus aan en uit.

- **Inactief:** zachtrode, gestresste bubbel met schuine ogen en een golvende mond; geen ring.
- **Activeren:** de lavendelkleurige ring groeit in circa 600 ms vanuit de bubbel en wordt daarna actief.
- **Actief:** de ring herhaalt langzaam een rustige ademende cyclus van schaalvergroting en vervaging. De bubbel wordt subtiel frisser groen.
- **Deactiveren:** de ring vervaagt en krimpt terug; de bubbel keert terug naar de zachtrode, gestresste inactieve staat.
- **Instellingen:** de zandkleurige, afgeronde tegel rechtsboven opent de instellingenflow en toont alleen een tandwielicoon.

De status wordt dus uitsluitend visueel getoond: er zijn geen labels, knoppen met tekst, banners of andere status-copy op het homescreen.

## Lay-out

De knop blijft in het optische midden van het beschikbare scherm, ook met edge-to-edge weergave. De instellingenknop gebruikt een ruime touch-target en houdt afstand van de systeeminsets. De centrale bubbel krijgt eveneens een groot toegankelijk aanraakvlak.

```text
┌──────────────────────────┐
│                    [⚙]   │
│                          │
│          ◌ ◌ ◌           │
│            ☺             │
│                          │
└──────────────────────────┘
```

## Toegankelijkheid en betrouwbaarheid

- De centrale knop en instellingenknop krijgen toegankelijke contentbeschrijvingen voor TalkBack, ondanks het ontbreken van zichtbare tekst.
- De ademende animatie respecteert de Android-instelling om animaties te verminderen: bij verminderde animaties blijft een vaste actieve ring zichtbaar.
- De visuele status moet direct veranderen na een tik, onafhankelijk van de latere audio-engine-koppeling. Een volgende technische fase kan de status aan de werkelijke audiostatus koppelen.

## Testen

- Compose-tests verifiëren de standaard inactieve staat, activeren/deactiveren via de centrale knop en de instellingenactie.
- Handmatige controle op een Android-emulator of toestel verifieert de gecentreerde lay-out, systeeminsets, aanraakvlakken en de terughoudende actieve animatie.
- Handmatige toegankelijkheidscontrole verifieert TalkBack-beschrijvingen en de vaste ring wanneer systeemanimaties zijn uitgeschakeld.

## Buiten scope

- Audioverwerking, permissies, foreground service en echte audioverbindingstatus.
- Instellingeninhoud; alleen de navigatie-/interactiehaak hoort bij dit homescreen.
- Tekstuele status of onboarding op het homescreen.

## Ontwikkelkwaliteit en volgende ontwerpiteratie

De schets legt de informatiehiërarchie en statussen vast, maar is geen pixel-perfect eindontwerp. De eerste werkende versie wordt op een fysiek Android-toestel beoordeeld op optische centrering, gelaatsuitdrukking, kleurcontrast, ringdikte, animatiesnelheid en haptische feedback. Bevindingen worden vastgelegd als gerichte verbeterpunten voordat de UI als visueel afgerond geldt. Daarbij blijft de kern behouden: één primaire bubbel, icon-only navigatie en geen visuele ruis.
