# Bubbel homescreen-ontwerp

> **Status:** akkoord door Floris op 31 augustus 2026; klaar voor een implementatieplan.

## Doel

Een prikkelarm en direct herkenbaar Android-homescreen bieden waarmee de gebruiker de luister-/filtermodus met een enkele tik aan- of uitzet.

## Richting

Het scherm neemt de aangeleverde schets als bron: een warme, egale achtergrond met een vriendelijke centrale bubbel en een losstaande instellingenknop rechtsboven. De interface gebruikt geen zichtbare tekst; betekenis komt van de iconen, plaatsing en actieve animatie.

## Visuele tokens

| Rol | Waarde | Gebruik |
| --- | --- | --- |
| `primary` / actieve bubbel | `#C3FF8C` | Kalme centrale aan/uitknop |
| `primaryContainer` | `#D6FF8C` | Groene containeraccenten |
| `secondary` / actieve ring | `#8C9DFF` | Pulserende statusring en sounds |
| `secondaryContainer` | `#EAFF8C` | Secundaire zachte status |
| `tertiary` / instellingen | `#FFC88C` | Afgeronde tegel rechtsboven |
| `tertiaryContainer` | `#FFDB8C` | Instellingenpaneel |
| `background` | `#FFEE8C` | Volledig homescreen en soundmasker |
| `surface` | `#FDFF8C` | Neutrale oppervlakken |
| `error` / inactieve bubbel | `#FFB58C` | Gestresste centrale aan/uitknop |
| `outline` | `#8F8770` | Gedempte UI-elementen |
| `on*` | `#584B00` | Gezicht en iconen |

De elementen krijgen sterk afgeronde hoeken, ruime padding en geen omlijningen. Schaduwen worden vermeden; de ring en kleur dragen de hiërarchie.

## Themaregel

Alle app-kleuren worden centraal en semantisch gedefinieerd in `ui/theme/Color.kt` en door `BubbelTheme` aangeboden. UI-composables gebruiken uitsluitend die themakleuren (bijvoorbeeld achtergrond, bubbel, actieve ring, instellingen en icoon) en bevatten geen losse hex-waarden of lokaal aangemaakte kleuren. Zo blijven toekomstige schermen visueel consistent en kan het palet op één plek worden aangepast.

## Interactie en status

De centrale bubbel is de enige primaire actie en schakelt de luister-/filtermodus aan en uit.

- **Inactief:** zachtrode, gestresste bubbel met schuine ogen en een golvende mond; geen ring.
- **Activeren:** de emoji verandert egaal van zachtrood naar groen. De lavendelkleurige buitenste bubble wordt tegelijk vanaf 12 uur als een radiale cirkelboog zichtbaar.
- **Actief:** de ring herhaalt langzaam een rustige ademende cyclus. De gestresste wenkbrauwen, ogen en mond veranderen vloeiend naar een rustige gesloten-ogen-smile.
- **Deactiveren:** de radiale ring sluit achterwaarts en de emoji verkleurt egaal naar de zachtrode, gestresste inactieve staat.
- **Geluidsveld:** kleine muziek-, volume- en equalizericonen starten buiten de viewport en bewegen voortdurend naar het midden. Een achtergrondkleurig masker groeit met de centrale bubble mee en verbergt de iconen exact vanaf de buitenrand.
- **Drukfeedback:** de centrale knop gebruikt uitsluitend de eigen ronde animatie; de standaard vierkante ripple/indication is uitgeschakeld.
- **Instellingen:** de zandkleurige, afgeronde tegel rechtsboven opent onder zichzelf een compact dropdown-paneel; het bevat alleen duidelijke iconen en extra grote switches, geen apart scherm.

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
