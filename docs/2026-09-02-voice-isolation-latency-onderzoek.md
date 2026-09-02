# Onderzoek: vertraging van voice isolation

2 september 2026. Alleen onderzoek; geen wijzigingen aan appcode, modellen of buildinstellingen. Onderzocht: huidige werkboom inclusief bestaande lokale wijzigingen, gepinde libDF-bron/modelarchieven en officiële Android/Oboe/modeldocumentatie. `adb devices -l` toonde geen verbonden toestel. Alle winstverwachtingen hieronder zijn dus voorstellen, geen toestelmetingen.

**Conclusie.** De huidige code heeft aantoonbare mogelijkheden om audioachterstand te laten oplopen en mist de metingen om dat vast te stellen. Daarnaast is er een officiële DeepFilterNet3-variant zonder lookahead die circa 20 ms intrinsieke modelvertraging kan besparen. Bij een grote hoorbare achterstand moeten route en wachtrijen eerst worden uitgesloten. Een sneller model verhelpt geen trage Bluetoothverbinding.

## Vastgesteld in de huidige code

| Onderdeel | Bevinding | Betekenis |
| --- | --- | --- |
| Wachtrijen | `AudioEngine.h:46` reserveert 48.000 mono-samples voor zowel input als output. Er is geen leeftijdslimiet, bezettingsmeting of inhaalbeleid. | Per queue is dit 1 seconde capaciteit bij 48 kHz, 3 seconden bij 16 kHz en 6 seconden bij 8 kHz. Capaciteit is geen bewijs van daadwerkelijke vertraging: lege queues voegen dit niet toe. |
| Deadlinebewaking | `DeadlineWatchdog.h:17` reset bij iedere geldige uitvoer; `VoiceWorker.cpp:21` meet geen begin/eindtijd rond inference. | Een geldige hop die te lang duurt wordt niet als deadlineoverschrijding gezien. De 250 ms in de watchdog begrenst uitsluitend ongeldige uitvoer. |
| Worker | `AudioEngine.cpp:124` gebruikt een gewone `std::thread` en slaapt na elke verwerkingsronde 1 ms, ook bij achterstand. | Extra wachttijd en mogelijke schedulingjitter. Geen bewijs dat deze sleep de dominante oorzaak is. |
| Framing | Modelhops zijn 480 samples op 48 kHz, dus 10 ms. `readBuffer_` kan 1.920 routesamples lezen maar wacht niet totdat hij vol is. | Niet ten onrechte 40 ms vaste wachttijd toeschrijven aan deze scratchbuffer. |
| Model | Werkelijke `config.ini` van het verpakte model: 48 kHz, FFT 960, hop 480, beide lookaheads 2. | Vaste vertraging naast rekentijd en audiobuffers. |
| Mixing | `VoiceWorker.cpp:26` mixt het huidige inputframe met vertraagde modeluitvoer. Natural heeft circa 25,1% droog signaal, Balanced 6,3%, Strong 0%. | De paden zijn niet in tijd uitgelijnd. Dit kan dubbele aanslagen/echo of kleuring geven. Dat is iets anders dan oplopende transportvertraging. |
| Route | `AudioRouteMonitor.kt:51` zet communication mode; de native engine gebruikt VoiceCommunication-attributen en inputpreset. | De app kiest een communicatiepad. Welk Bluetoothprofiel en welke werkelijke audiomodus worden gebruikt, moet op het toestel blijken. |
| Runtime/build | Inference loopt via Rust libDF/Tract, niet ONNX Runtime. Rust bouwt al met `--release`. | Alleen zeggen 'zet release aan' of 'zet NNAPI aan' is geen onderbouwde oplossing voor deze implementatie. |

De bestaande golden/resettest verwerkt telkens één hop en controleert uitvoercorrectheid. Hij meet geen langdurige rekensnelheid of microfoon-naar-koptelefoonvertraging.

## Kansrijke verbeteringen, in onderzoeksvolgorde

**1. Eerst route en vertraging zonder model vaststellen.** Vergelijk bedraad/USB en Bluetooth op hetzelfde toestel. Een toekomstige diagnostische bypass moet dezelfde input/outputroute behouden en alleen modelverwerking overslaan. Is die bypass al traag, dan ligt de grootste winst buiten het model. Oboe noemt Bluetooth expliciet als voorbeeld van een route die geen LowLatency-stream ondersteunt; een verzoek om die modus garandeert dus niets. [Oboe FAQ](https://github.com/google/oboe/blob/main/docs/FAQ.md)

**2. Achterstand begrenzen op audioleeftijd.** Meet input- en outputbezetting in milliseconden, inclusief het blok dat de worker al heeft uitgelezen. Ontwerp daarna een kleine doelbezetting en een apart herstelbeleid bij oude audio. Een verkennend queuebudget van enkele hops is een startpunt voor een experiment, geen vaste productinstelling. Alleen de huidige capaciteit verkleinen veroorzaakt eerder overflow/falen. Bij overslaan van audio moeten modeltoestand, discontinuïteiten en eventuele fade zorgvuldig worden afgehandeld; `clear()` mag volgens het huidige queuecontract alleen wanneer producer en consumer gestopt zijn.

Een hop vertegenwoordigt 10 ms audio. De gemiddelde totale verwerking per hop moet daar ruim onder blijven. Bij bijvoorbeeld 12 ms verwerking per 10 ms audio ontstaat na 1 seconde wandtijd theoretisch circa 167 ms extra achterstand, nog zonder schedulingoverhead. Dit is een rekenvoorbeeld, geen meting van Bubbel. Eén tijdelijke hapering kan bovendien blijvende vertraging achterlaten als de worker daarna oude audio in de outputqueue inhaalt en er geen beleid is om weer naar de doelbezetting terug te keren.

**3. Officieel model met lage vertraging vergelijken.** In de al gepinde upstreamcommit staat `models/DeepFilterNet3_ll_onnx.tar.gz`. Het lokaal gelezen archief gebruikt dezelfde 48 kHz/960 FFT/480 hop, maar beide lookaheads zijn 0. De huidige wrapper accepteert die geometrie; volledige runtimecompatibiliteit en kwaliteit zijn nog niet getest.

Upstream berekent de sampleverschuiving als `fft_size - hop_size + lookahead * hop_size`. Daarmee gaat deze van 1.440 samples (30 ms) naar 480 samples (10 ms): **20 ms minder intrinsieke vertraging**. Daar komen frameverzameling, rekentijd en I/O bij. De LADSPA-documentatie noemt 20 ms minimale STFT-latency voor zijn streamingcontext; dat is een andere afbakening dan alleen de sampleverschuiving. Encoder- en filterlookahead worden met `max()` gecombineerd, niet opgeteld. De winst moet nog worden bevestigd met een volledige streamingvergelijking. [Upstream delayberekening](https://github.com/Rikorose/DeepFilterNet/blob/d375b2d8309e0935d165700c91da9de862a99c31/libDF/src/bin/enhance_wav.rs), [LADSPA-model](https://github.com/Rikorose/DeepFilterNet/blob/main/ladspa/README.md)

Dit is een vervangend getraind model, geen uitnodiging om zomaar de lookahead in het bestaande configbestand op nul te zetten. Vergelijk spraakverstaanbaarheid en onderdrukking van dezelfde achtergrondgeluiden; werk bij een latere keuze ook provenance, hash en referentie-uitvoer bij.

**4. Android-audiopad en duplex-timing afstemmen.** Log de werkelijk verkregen performance/sharing modes, device-id's, frames-per-burst, buffersize, callbackgroottes en xruns. Het huidige log 'open output exclusive: OK' bevestigt alleen dat openen lukte, niet dat exclusive werkelijk is toegekend. Oboe zet volgens de officiële handleiding automatisch twee bursts als bufferdoel; het ontbreken van een expliciete setter bewijst daarom geen te grote systeemuitvoerbuffer. [Android low latency audio](https://developer.android.com/games/sdk/oboe/low-latency-audio)

Vergelijk op ondersteunde Android-versies het huidige VoiceCommunication-inputpreset met VoicePerformance voor live monitoring. Android beschrijft VoicePerformance vanaf API 29 expliciet als opname voor directe verwerking/weergave met minimale latency; VoiceCommunication kan echo cancellation en gain control toepassen. Dit is routeafhankelijk en kan akoestiek of headsetwerking veranderen. [Android AudioSource](https://developer.android.com/reference/android/media/MediaRecorder.AudioSource)

De streams hebben nu aparte callbacks; alleen output eerder starten maakt input niet outputgestuurd. Onderzoek een gemeenschappelijke uitvoerklok/duplexregeling, inclusief geleidelijke klokdrift tussen devices. De huidige converter gebruikt vaste nominale rates en corrigeert geen klokdrift. Dat is een hypothese bij langzaam oplopende achterstand. Houd zware inference buiten de callback.

**5. Rekentijd optimaliseren wanneer de meting dat rechtvaardigt.** Meet afzonderlijk inference en scheduling. Onderzoek workerprioriteit en wekken bij beschikbaar werk; vermijd onnodige sleep wanneer er werk klaarstaat. Vergelijk ook een geoptimaliseerde C++-build, want Rust release garandeert geen geoptimaliseerde native debugcode. Daarna zijn ThinLTO, allocatieprofiling en batching van queue-reads in de uitvoercallback kandidaten. Winst is onbekend. Kopieer niet blind upstream `panic=abort`: de app-wrapper gebruikt bewust `catch_unwind`.

De huidige drie filterprofielen voeren allemaal hetzelfde model uit: Natural selecteren vermindert de inferencebelasting niet. libDF heeft al conditionele verwerking op basis van lokale SNR; drempels aanpassen vereist een kwaliteitstest en verandert niet de vaste lookahead. [Gepinde libDF-implementatie](https://github.com/Rikorose/DeepFilterNet/blob/d375b2d8309e0935d165700c91da9de862a99c31/libDF/src/tract.rs)

**6. Andere runtime of ander model als vervolgstap.** RNNoise is een kandidaat voor een vergelijkende benchmark van snelheid en verstaanbaarheid, maar er is geen Bubbel/toestelbewijs dat een overstap beter uitpakt. [RNNoise](https://github.com/xiph/rnnoise)

ONNX Runtime/XNNPACK vereist hier een nieuwe integratie van modeltoestand, framing en DSP; het is geen bestaande schakelaar. De officiële ORT-gids adviseert CPU/XNNPACK als eerste vergelijking en waarschuwt dat gedeeltelijke acceleratorondersteuning de prestaties kan verslechteren. Daarom geen GPU/NPU- of quantisatiesnelheidsclaim zonder benchmark. [ORT mobile](https://onnxruntime.ai/docs/tutorials/mobile/)

## Meetplan voor een volgende implementatieronde

1. Noteer toestel, Android-versie, APK/buildtype en fysieke route. Meet akoestisch of met geschikte loopback de round-trip; softwaretimestamps dekken niet noodzakelijk de gehele Bluetoothketen.
2. Vergelijk dezelfde route met filter en diagnostische bypass. Meet direct na start en na 1, 5 en 15 minuten, ook tijdens spraak plus achtergrondgeluid. Stilte alleen is ongeschikt: libDF kan daarbij verwerking overslaan.
3. Verzamel p50/p95/p99 hoprekentijd, totale workerdoorlooptijd, queueleeftijd/bezetting, underruns/overruns en toegekende Oboe-instellingen. Verzamel data buiten de realtime callback; meetoverhead mag het resultaat niet bepalen.
4. Verander één factor tegelijk: backlogbeleid, audiopreset, duplex/scheduling of LL-model. Beoordeel latency, hoorbare uitval, verstaanbaarheid en warmte/batterij samen.
5. Vergelijk de dry/wet-paden met tijdsuitlijning of tijdelijk 100% wet om een interne echo te onderscheiden van transportvertraging. Uitlijning verbetert coherent mengen; ze maakt het wet-pad niet sneller.

Geen prestatie- of latencyfix is in deze onderzoeksronde uitgevoerd of gevalideerd.
