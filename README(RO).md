<div align="center">
  <a href="README.md">中文</a> | <span>Română</span> | <a href="README(E).md">English</a>
</div>

<div align="center">
  <img src="https://img.shields.io/github/last-commit/Davinci198/fix-operit" alt="Last Commit">
  <img src="https://img.shields.io/badge/Platform-Android_8.0%2B-brightgreen.svg" alt="Platform">
  <a href="https://github.com/Davinci198/fix-operit/releases/latest"><img src="https://img.shields.io/github/v/release/Davinci198/fix-operit" alt="Latest Release"></a>
  <br>
  <a href="https://github.com/Davinci198/fix-operit/stargazers"><img src="https://img.shields.io/github/stars/Davinci198/fix-operit" alt="GitHub Stars"></a>
  <a href="https://github.com/Davinci198/fix-operit"><img src="https://img.shields.io/badge/📖-User_Guide-blue.svg" alt="User Guide"></a>
  <a href="docs/doc-src/dev-core/CONTRIBUTING.md"><img src="https://img.shields.io/badge/contributions-welcome-brightgreen.svg" alt="Contributions Welcome"></a>
  <br>
  <a href="https://github.com/Davinci198/fix-operit/issues"><img src="https://img.shields.io/badge/🐛-Issues-orange.svg" alt="Issues"></a>
</div>

<div align="center">
  <img src="app/src/main/assets/logo.svg" width="120" height="120" alt="Operit Logo">
  <h1>Operit AI - Asistent Inteligent</h1>
  <p>📱 <b>Primul asistent AI complet funcțional de pe mobil, care rulează în totalitate independent, cu puternice capacități de apelare a uneltelor (tool calling)</b> 📱</p>
</div>

<div align="center">
  <div style="padding: 10px 0; text-align: center;">
    <img src="docs/assets/README_examples/UI_terminal.jpg" width="22%" alt="Captură Operit 1" style="display: inline-block; border-radius: 8px; box-shadow: 0 5px 15px rgba(0,0,0,0.15); margin: 0 3px; max-width: 220px;">
    <img src="docs/assets/README_examples/UI_waifuMode.jpg" width="22%" alt="Captură Operit 2" style="display: inline-block; border-radius: 8px; box-shadow: 0 5px 15px rgba(0,0,0,0.15); margin: 0 3px; max-width: 220px;">
    <img src="docs/assets/README_examples/UI_DeepResearch.jpg" width="22%" alt="Captură Operit 3" style="display: inline-block; border-radius: 8px; box-shadow: 0 5px 15px rgba(0,0,0,0.15); margin: 0 3px; max-width: 220px;">
    <img src="docs/assets/README_examples/UI_beautified.jpg" width="22%" alt="Captură Operit 4" style="display: inline-block; border-radius: 8px; box-shadow: 0 5px 15px rgba(0,0,0,0.15); margin: 0 3px; max-width: 220px;">
  </div>
</div>

---

## 🌟 Prezentare

**Operit AI** este primul asistent AI complet funcțional de pe mobil: clientul, istoricul conversațiilor și configurările modelelor rulează și sunt salvate integral pe dispozitivul tău Android. Modelele din cloud sunt la alegerea ta — îți configurezi propriul API Key și endpoint, iar apelurile pleacă direct de pe dispozitiv; Operit nu oferă servicii de inferență LLM și nu face intermediere pentru cererile de chat. Aplicația are capacități puternice de **apelare a uneltelor**, **căutare profundă (Deep Search)**, **fluxuri de lucru și automatizări**, o **bibliotecă de memorie inteligentă**, plus funcții avansate de personalizare precum **personaje (persona)** și **carduri de personaj**, integrând **modele locale MNN/llama.cpp**, ecosistemul **MCP/Skill** și **interfață multilingvă**. Nu este doar o interfață de chat, ci un **asistent universal** fusionat adânc cu permisiunile și uneltele Android, cu un **mediu Ubuntu 24 integrat** — funcționalitate fără precedent.

---

## ⚡ Puncte forte

<table>
<tr>
<td width="50%">

### 🖥️ Mediu Ubuntu 24
Sistem Ubuntu 24 complet integrat, cu vim, MCP, Python și multe altele; rulează comenzi Linux complexe și sarcini automatizate direct pe telefon

### 🧠 Sistem de memorie inteligentă
AI-ul clasifică și gestionează automat memoriile: interogări după timp, import/export, rezumare automată, căutare inteligentă în istoric; își amintește preferințele și obiceiurile tale pentru un serviciu personalizat

### 🗣️ Interacțiune vocală
Conversație naturală continuă, TTS local/cloud + STT local, voci personalizate, activare vocală sau prin audio specific, citire automată

</td>
<td width="50%">

### 🤖 Modele AI locale
Suport pentru modele locale MNN / llama.cpp (GGUF), rulare AI complet offline, confidențialitatea datelor protejată

### 🎭 Persona și carduri de personaj
Personalizează personalitatea și stilul AI-ului; carduri de personaj cu import/export (Tavern/JSON), backup, partajare prin cod QR; personajele pot conversa între ele și au istoric propriu

### 🔌 Ecosistem bogat de unelte
40+ unelte integrate + plugin-uri din piața MCP/Skill + pachete de unelte/fluxuri de lucru, cu Agent de auto-click, desenare/căutare de imagini, AI care conversiază reciproc/auto-cunoaștere, sarcini programate, piață de prompturi etc., acoperind operații pe fișiere, cereri de rețea, control de sistem și procesare media

</td>
</tr>
</table>

---

## 🛠️ Prezentare generală a funcțiilor

<details>
<summary><b>📦 Sistemul de unelte integrat (click pentru extindere)</b></summary>

| Tip unealtă | Descriere |
|---------|---------|
| 🐧 **Mediu Linux** | Ubuntu 24 complet, cu gestionare pachete apt, medii de rulare Python/Node.js, surse software personalizate |
| 📁 **Sistem de fișiere** | Citire/scriere fișiere, căutare, dezarhivare, conversie formate, integrare Git |
| 🌐 **Unelte de rețea** | Cereri HTTP, accesare pagini web, upload/download fișiere, dezvoltare și export web |
| ⚙️ **Operații de sistem** | Instalare aplicații, gestionare permisiuni, automatizare pe trei canale: Accesibilitate / ADB / Root (cu Agentul de auto-click AutoGLM, ecran virtual/multi-display pe adb root) |
| 🎬 **Procesare media** | Conversie video, extragere cadre, OCR/înțelegere imagini, fotografiere cu camera, citire audio/video |
| 🧑‍💻 **Dezvoltare și terminal** | Workspace/împachetare cu un click, editare cod/evidențiere sintaxă, terminal SSH/Chroot/vim, combinații Ctrl |
| 🎨 **Creație AI** | Pachete de desen (OpenAI/Qwen/NanoBanana/Zhipu), căutare/descărcare imagini |
| 🔍 **Motoare de căutare** | Căutare profundă, DuckDuckGo, Tavily, Google Scholar, Bing, Sogou, Quark, integrare Hărți Baidu |
| 🧩 **Pachete de unelte & fluxuri de lucru** | Ecosistem de ToolPkg/gestionare pachete, automatizare prin workflow, declanșare programată, declanșare prin activare vocală |

</details>

<details>
<summary><b>🎨 Personalizarea interfeței (click pentru extindere)</b></summary>

- ✨ **Sistem de teme**: culori, fonturi, spațieri, margini personalizate
- 🌍 **Suport multilingv**: acoperire chineză/engleză, comutare automată după limba sistemului
- 🔤 **Fonturi și tipografie**: dimensiune globală a fontului, margini conversație personalizate
- 🎭 **Companion de birou (desktop pet)**: animații WebP, expresii personalizate, afișare în fereastră flotantă
- 📱 **Optimizare aspect**: ascunderea barei de stare, bară de unelte personalizată, adaptare pentru tablete
- 🎨 **Randare Markdown**: formule LaTeX (cu derulare orizontală), evidențiere cod, tabele, diagrame Mermaid
- 🧾 **Afișarea informațiilor**: blocuri de gândire (chain of thought) pliabile, previzualizare blocuri HTML, limitare înălțime blocuri cod/gândire
- 🪟 **Experiența ferestrei flotante**: ascunderea avatarelor în modul fereastră/balon, selecție prin încercuire, previzualizare ecran complet
- 🧮 **Statistici de date**: statistici de utilizare a token-urilor, grafic circular pe modele

</details>

<details>
<summary><b>🔗 Capacități de integrare (click pentru extindere)</b></summary>

- 🤖 **Integrare Tasker**: declanșează evenimente personalizate ale agentului AI, automatizare profundă
- 🌐 **Piață MCP/Skill**: instalare plugin-uri cu un click, MCP remote, descrieri automate, suport uvx/npx
- 🔌 **Suport mai multe modele**: OpenAI Responses API, Claude, Gemini, xAI, Novita, Ollama, NVIDIA, OpenRouter, LMStudio, 百灵 etc.
- 🧪 **Gestionare modele și prompturi**: configurații multiple/parametri personalizabili, piață de prompturi
- 🔐 **Sistem de permisiuni**: control al permisiunilor la nivel de unealtă și avertizări de securitate
- 🔑 **Pool de chei și statistici**: testare/import în masă, statistici de token-uri (grafic pe modele)
- 🗂️ **Legare workspace**: legare workspace și acces la fișiere prin SAF / SFTP / SSH
- 🖱️ **Agent de auto-click**: dublu canal AutoGLM + UI Tree, operații automatizate
- 📊 **Unelte în paralel**: executarea în paralel a uneltelor doar-citire, pentru răspunsuri mai rapide

</details>

<details>
<summary><b>💬 Conversații și gestionarea memoriei (click pentru extindere)</b></summary>

- 🧠 **Bibliotecă de memorie**: clasificare automată/căutare, interogări după timp, import/export, memorii atașate
- 💬 **Gestionarea conversațiilor**: rezumare automată și editare rezumate, grupare/ramificare/migrare istoric, blocare conversații, istoric independent pentru carduri de personaj
- ⚡ **Conversații paralele**: procesare paralelă a conversațiilor, mecanism de stare pentru pachetele de unelte
- 🤖 **Interacțiune personaje**: conversații între carduri de personaj, vizualizarea istoricului, blocuri de gândire pliabile
- 📦 **Istoric conversații**: import/export în mai multe formate, backup și restaurare istoric

</details>

<details>
<summary><b>💾 Date și backup (click pentru extindere)</b></summary>

- 🗂️ **Backup global/automat**: backup programat al bazei de date, cu recuperare în caz de corupere (excludere MCP/Skill/terminal/pachete)
- 🎭 **Carduri de personaj**: backup, export (Tavern/JSON), partajare prin cod QR
- 🧷 **Workspace**: legare SAF/SFTP/SSH, editare cod/evidențiere sintaxă, Git ignore
- 🧰 **Gestionare Skill**: comutatoare Skill, rezolvare repositorii și descărcare în cache

</details>

---

## 📸 Demonstrarea funcțiilor

<table>
<tr>
<td align="center" width="33%">
<img src="docs/assets/README_examples/function_webdev_MusicGame.png" width="100%"><br>
<b>Dezvoltare web</b><br>
Proiectează pagini web pe telefon și exportă-le ca aplicații independente
</td>
<td align="center" width="33%">
<img src="docs/assets/README_examples/function_floating_and_attach.jpg" height="200px"><br>
<b>Fereastră flotantă & atașamente</b><br>
Disponibile oricând, partajare convenabilă
</td>
<td align="center" width="33%">
<img src="docs/assets/README_examples/function_packageMarket.png" height="200px"><br>
<b>Piața de plugin-uri</b><br>
Un ecosistem MCP bogat
</td>
</tr>
</table>

---

## 🚀 Pornire rapidă

| Element | Descriere |
|-----|------|
| 📋 **Cerințe de sistem** | Android 8.0+ (API 26+), recomandat 6GB+ RAM, 5GB+ spațiu liber |
| 📥 **Descărcare și instalare** | Descarcă cel mai nou APK din [pagina de Release](https://github.com/Davinci198/fix-operit/releases) |
| 📖 **Ghid de utilizare** | [Site-ul oficial](https://operit.app) conține tutoriale detaliate și exemple |

> **Avertisment de securitate:** Pentru siguranța datelor tale, descarcă aplicația exclusiv din [pagina de Release](https://github.com/Davinci198/fix-operit/releases) oficială sau de pe [site-ul oficial](https://operit.app). Pachetele de instalare descărcate din surse necunoscute pot fi modificate malițios, ducând la scurgeri de date private sau supravegherea dispozitivului.

**Pași de instalare:** descarcă APK-ul → instalează și pornește → configurează după ghid → începe să-l folosești ✨

---

## 🔐 Date, modele și servicii expuse extern

- **Modelele din cloud sunt configurate de utilizator:** atunci când folosești modele din cloud, alegi singur furnizorul și configurezi API Key, modelul și endpoint-ul. Cererile de chat pleacă direct de pe dispozitivul tău către furnizorul ales; Operit nu oferă inferență pentru chat, intermediere de cereri API și nu găzduiește istoricul conversațiilor în cloud. Citește și termenii de utilizare și politica de confidențialitate ai furnizorului ales.
- **Modelele locale pot face inferență offline:** modelele MNN și llama.cpp rulează inferența local, pe dispozitiv; după pregătirea fișierelor de model, le poți folosi fără conexiune la un furnizor de modele.
- **Celelalte funcții de rețea rulează independent:** piața, anunțurile, verificarea actualizărilor, autentificarea GitHub, precum și funcțiile MCP, căutare, voce, desen etc. pe care le activezi accesează resurse de rețea ale terților sau ale Operit. Aceste funcții nu reprezintă intermediere pentru cererile către modelul de chat.
- **Serviciile expuse extern sunt responsabilitatea celui care le desglobează:** serviciul HTTP extern este dezactivat implicit; odată activat, oferă chat web și API HTTP pe interfața de rețea a dispozitivului. Exemple precum QQ Bot pot fi folosite și pentru răspunsuri automate. Deschide accesul doar către persoane autorizate și ocupă-te singur de controlul accesului, protecția datelor și gestionarea conținutului. Operatorii care oferă în mod constant servicii de interacțiune emoțională antropomorfică către publicul din China trebuie să evalueze și să respecte singuri legislația și cerințele de reglementare aplicabile.

---

## 🔮 TODO / Plan de dezvoltare

- **Automatizare UI și pipeline de capturi de ecran**
  - ✅ Automatizare UI suportată în trei moduri de permisiuni: Accesibilitate / ADB / Root
  - ✅ Ecran virtual/multi-display în scenarii adb root (parametrul `display`)
  - ✅ UI Tree cu dublă soluție: AutoGLM + dump local uiautomator

---

## 📅 Istoricul versiunilor

<table>
<tr><th>Versiune</th><th>Data lansării</th><th>Actualizări principale</th></tr>

<tr>
<td><b>v1.12.0</b><br><sub>cea mai nouă</sub></td>
<td>2026-07-01</td>
<td>
• <b>Piața și ecosistemul de creație</b>: unificarea fluxului pieței, noua Piață de Artifacte, gestionarea și publicarea proiectelor, căutare îmbunătățită în piață, autentificare GitHub OAuth, repo exemplu și intrare în bara laterală<br>
• <b>ToolPkg și workspace</b>: separarea gestionării ToolPkg IPC / runtime, hook runner nou, IPC runtime pentru modul plan, urmărirea modificărilor workspace/atașamente/șabloane, parametrul zip include_root_directory<br>
• <b>Multilingv, voce și media</b>: localizări noi în coreeană, spaniolă și portugheză, MIMO/Mimo, VITS, ONNX, Doubao TTS, coadă de redare muzicală, optimizarea atașamentelor de imagini și deduplicarea numelor fișierelor atașate<br>
• <b>Stabilitate și securitate</b>: eliminarea API Key DeepSeek implicit hardcodată, proces nou de recuperare după crash și izolare la pornire, repararea afișării furnizorilor de modele, depășirii textului butoanelor, validării embedding-urilor cloud, recunoașterii audio DashScope etc.
</td>
</tr>

<tr>
<td><b>v1.11.0</b></td>
<td>2026-05-16</td>
<td>
• <b>Web Chat și Piața de Artifacte</b>: Web Chat și Piața de Artifacte noi, selecția modelului pentru chat web, navigarea istoricului, gestionarea proiectelor și publicarea lucrărilor, migrarea bazei de întrebări în sistemul de bibliotecă de memorie<br>
• <b>ToolPkg, Hook și Compose DSL</b>: ToolPkg AI provider nou, control pe o singură tură de conversație, interceptare hook și executore pentru unelte, extinderea Compose DSL cu WebView, slot, dump de debug și re-randare<br>
• <b>Memorie, context și istoric</b>: salvarea automată a memoriilor din mesajele alese de utilizator, salvarea automată a planurilor, căutare/reîmprospătare WorldBook, limitator de context, paginare fereastră istoric și previzualizarea poziției mesajelor<br>
• <b>Dezvoltare și automatizare</b>: instantanee/click iframe în browser, gestionarea notificărilor GitHub, exemplul QQ Bot, API de selecție a fișierelor, teste Java Bridge, pagini de setări remote/Windows și widget-uri desktop
</td>
</tr>

<tr>
<td><b>v1.10.1</b></td>
<td>2026-04-17</td>
<td>
• <b>Browser integrat și automatizare web</b>: browserul integrat mult îmbunătățit, cu tab-uri, istoric, favorite, permisiuni, ferestre multiple, minimizare și control viewport; import, instalare, pornire/oprire, stocare și meniu de pagină pentru scripturile de browser<br>
• <b>Avatar virtual și personalizarea interfeței</b>: suport avatare virtuale FBX și previzualizare MMD îmbunătățită, efect de temă „sticlă lichidă", personalizare extinsă pentru bara laterală, baloanele de chat și bara de introducere<br>
• <b>Plugin-uri, workspace și context</b>: depanarea și scrierea automată a plugin-urilor Operit din editorul de configurare, intrare locală de conversație HTTP, redenumirea workspace-ului și citirea automată a fișierelor de reguli, sărituri în istoric, paginare bidirecțională și completare automată a contextului<br>
• <b>Stabilitate și performanță</b>: reparații la permisiunile uneltelor, TTS HTTP, output lung SSH/tmux, sărituri în istoric, randare GIF/formule/Markdown, configurare MCP și statistici; optimizări continue pentru fluxul conversațiilor, căutarea profundă, sistemul de memorie, browserul și gestionarul de pachete
</td>
</tr>

<tr>
<td><b>v1.10.0</b></td>
<td>2026-03-18</td>
<td>
• <b>Chat de grup pentru carduri de personaj și auto-configurare AI</b>: chat de grup cu mai multe carduri de personaj și interacțiune @, capacitate nouă de auto-configurare AI care poate ajuta la configurarea MCP, Skill, STT, TTS și a parametrilor modelelor<br>
• <b>Upgrade teme și interacțiune</b>: mesaje pliabile pe grupuri, teme cu baloane și personalizare font/culori/fundal, baloane mai late, introducere cu sticlă lichidă, apăsare lungă pe pictograme pentru setări/mod voce, avatar asistent și suport avatare MP4<br>
• <b>Extinderea uneltelor și a platformei</b>: Ollama, NVIDIA, modul universal OpenAI Response, pachetul de unelte SSH separat, Java Bridge, plugin APKTool, descărcare automatizare web, randare audio/video Markdown, generare video xAI, anularea fluxurilor de lucru, taste personalizate terminal și coadă de mesaje<br>
• <b>Reparații și optimizări</b>: recunoaștere vocală, concurența memoriei, interacțiunea cu fereastra flotantă, afișarea terminalului, ecran complet automatizare web, MNN Tool Call etc.; optimizarea rechemării memoriei, a căutării în piață, șabloanelor de workspace, performanței uneltei grep și stabilității reîncercărilor agentului
</td>
</tr>

<tr>
<td><b>v1.9.1</b></td>
<td>2026-02-20</td>
<td>
• <b>Reparații de stabilitate</b>: repararea concentrată a mai multor probleme din 1.9.0, pentru o mai bună utilizabilitate și fluiditate<br>
• <b>Terminal și apelare unelte</b>: unelte terminal îmbunătățite, repararea blocării UI-ului interactiv, erorilor uneltelor din istoricul strict de tool call, execuției de comenzi raw în controllerul Windows<br>
• <b>MCP și biblioteca de memorie</b>: repararea MCP remote care nu se putea opri, rescrierea logicii de scriere în biblioteca de memorie, suport modele de vectori externe și unealtă nouă de modificare a conexiunii<br>
• <b>Funcții noi și reparații UI</b>: ștergerea istoricului de chat cu card de personaj nelegat, ștergerea în masă a fluxurilor de lucru și vizualizarea jurnalelor de execuție, reparații pentru tastatură/câmp de introducere întunecat/transparența temei/gestionarea pachetelor din trusă
</td>
</tr>

<tr>
<td><b>v1.9.0</b></td>
<td>2026-02-17</td>
<td>
• <b>Operații web automate pe mobil</b>: capabilități noi de operații web, cu ocolire CORS pentru proiectele web din workspace<br>
• <b>Operații terminal Windows</b>: suport pentru comenzi Windows, cu control al CLI-urilor precum Codex, plus modul strict de tool call pentru compatibilitate<br>
• <b>Extinderi de unelte și sistem</b>: vizualizator SQL, șabloane de workspace Android, furnizori compatibili OpenAI response, adăugarea directă de skill-uri, grafic circular de statistici<br>
• <b>Reparații și optimizări</b>: citirea imaginilor/rezumarea contextului/trunchierea caracterelor speciale/ffmpeg etc., output îmbunătățit la testarea conectivității modelelor și indicii de încărcare MCP
</td>
</tr>

<tr>
<td><b>v1.8.1</b></td>
<td>2026-02-03</td>
<td>
• <b>Inferență locală llama.cpp</b>: suport modele locale GGUF și uneltele aferente<br>
• <b>Unelte și interfață</b>: căutare/descărcare imagini, previzualizare blocuri HTML, limitare înălțime blocuri cod/gândire, ascunderea avatarelor în baloane, grafic circular token-uri, colapsarea lanțului de gândire<br>
• <b>Date și backup</b>: backup global (excludere MCP/skill/terminal/pachete) + backup/export/partajare carduri de personaj, comutatoare Skill, import/test în masă al pool-ului de chei, legare SAF pentru workspace<br>
• <b>Reparații</b>: ecoul înregistrării la citirea AI, statistici token în fereastra flotantă, acoperirea tastaturii la editarea personajelor, explozia de token-uri la căutarea profundă, pornirea MCP, ieșirea din fereastra flotantă a workflow-ului, trunchierea tabelelor, întreruperea vocii SiliconFlow
</td>
</tr>

<tr>
<td><b>v1.8.0</b></td>
<td>2026-01-13</td>
<td>
• <b>Sistemul de fluxuri de lucru</b>: capabilități de calcul/parametri de intrare-ieșire/execuție, cu declanșare prin activare vocală<br>
• <b>Activare vocală</b>: intrare directă în modul de conversație vocală, atașare rapidă de atașamente prin cuvinte-cheie vocale<br>
• <b>Conversații paralele</b>: procesare paralelă a conversațiilor, mecanismul de stare al pachetelor de unelte decide dinamic uneltele<br>
• <b>Noutăți și optimizări</b>: interogarea memoriei după timp, backup automat, furnizori OpenAI de desen/voce, pornire MCP optimizată, chroot în terminal, reparații multiple de bug-uri
</td>
</tr>

<tr>
<td><b>v1.7.1</b></td>
<td>2025-12-31</td>
<td>
• <b>Automatizare pe ecran virtual Root</b>: pornirea ecranelor virtuale pe root, sarcini AutoGLM concurente pe ferestre multiple<br>
• <b>Ecosistem Skill</b>: protocol Skill nou și piață de Skill-uri, cu urmărire BETA pentru versiunile nightly<br>
• <b>Interacțiune îmbunătățită</b>: editarea rezumelor, accesul web în mod fereastră flotantă, selecție prin încercuire, blocarea conversațiilor<br>
• <b>Reparații și optimizări</b>: crash la imagini mari, erori ToolCall, împachetarea blocurilor de cod, viteza de pornire și stabilitatea ecranelor virtuale
</td>
</tr>

<tr>
<td><b>v1.7.0</b></td>
<td>2025-12-19</td>
<td>
• <b>Jalon de automatizare GUI</b>: Autoglm + ecran virtual (activabil din setări)<br>
• <b>Automatizare extinsă</b>: configurare Autoglm cu un click și executor separat, logica de comutare a ecranului virtual și calitate personalizată a capturilor<br>
• <b>Optimizarea experienței</b>: afișarea cheilor neconcentrate ca asteriscuri, interzicerea Autoglm ca model principal<br>
• <b>Extinderi de unelte</b>: pachetul de desen NanoBanana, suport non-suprascriere apply file, MNN STT etc.
</td>
</tr>

<tr>
<td><b>v1.6.3</b></td>
<td>2025-12-08</td>
<td>
• <b>Suport nativ ToolCall</b>: apelare nativă de unelte pentru modele, unealta de gândire DeepSeek<br>
• <b>Workspace și terminal</b>: alegerea tipului de proiect la creare, conexiuni sistem de fișiere SSH, suport accesibilitate în terminal<br>
• <b>Modele și afișarea mesajelor</b>: selecție multiplă a configurațiilor de modele, afișarea numelui modelului și a furnizorului în mesaje<br>
• <b>Optimizări și reparații</b>: fereastra flotantă îmbunătățită, repararea blocajelor terminalului, migrarea workspace-ului în stocarea internă
</td>
</tr>

<tr>
<td><b>v1.6.2</b></td>
<td>2025-11-20</td>
<td>
• <b>Gestionarea conversațiilor</b>: apăsare lungă pentru ramificare, clasificarea istoricului, migrare în masă<br>
• <b>Configurarea modelelor</b>: redenumirea configurațiilor, legarea contextului, căutare nativă Google<br>
• <b>Reparații bug-uri</b>: comutarea interfeței, împachetarea bold-ului, modul balon etc.<br>
• Adăugat pachetul de căutare academică crossref, editor de cod actualizat
</td>
</tr>

<tr>
<td><b>v1.6.1</b></td>
<td>2025-11-05</td>
<td>
• <b>Optimizare majoră de performanță</b>: randarea UI refăcută, fluiditate mult mai bună<br>
• <b>Viziune AI extinsă</b>: recunoaștere directă a imaginilor, capabilități de recunoaștere indirectă<br>
• <b>Terminal SSH</b>: conexiuni SSH și montare inversă a sistemului de fișiere al telefonului<br>
• Mecanism de rezumare automată, căutare profundă, sistem nou de autorizare
</td>
</tr>

<tr>
<td><b>v1.6.0</b></td>
<td>2025-10-21</td>
<td>
• <b>Modele locale MNN</b><br>
• <b>Actualizare mare a bibliotecii de memorie</b>: clasificare automată de către AI, căutare inteligentă, import/export<br>
• <b>Terminal optimizat</b>: suport vim, bare de progres, surse software personalizate<br>
• Integrare Tasker, companion de birou, etichete de fir narativ
</td>
</tr>

<tr>
<td><b>v1.5.2</b></td>
<td>2025-10-05</td>
<td>
• MCP extins: suport uvx/npx, pornire accelerată<br>
• Git ignore pentru workspace<br>
• Fotografiere cu camera, randare HTML, filtrare regex
</td>
</tr>

<tr>
<td><b>v1.5.0</b></td>
<td>2025-09</td>
<td>
• <b>Terminal Ubuntu 24</b> complet integrat<br>
• Lansarea pieței MCP<br>
• Companion de birou, mod căutare profundă
</td>
</tr>

<tr>
<td><b>v1.4.0</b></td>
<td>2025-08</td>
<td>
• Execuție paralelă a mai multor unelte<br>
• Sistem de carduri de personaj, selector de personaje<br>
• Import carduri de personaj PNG
</td>
</tr>

<tr>
<td><b>v1.3.0</b></td>
<td>2025-08</td>
<td>
• Funcție de dezvoltare web<br>
• Selector de teme, UI personalizabil<br>
• Suport Anthropic Claude
</td>
</tr>

<tr>
<td><b>v1.2.x</b></td>
<td>2025-07</td>
<td>
• Sistem de conversație vocală<br>
• Funcție de bibliotecă de cunoștințe<br>
• Suport animații DragonBones
</td>
</tr>

<tr>
<td><b>v1.1.x</b></td>
<td>2025-06</td>
<td>
• Suport protocol MCP<br>
• Recunoaștere OCR, fereastră flotantă<br>
• Suport complet Gemini
</td>
</tr>

<tr>
<td><b>v1.0.0</b></td>
<td>2025-05</td>
<td>
• Prima versiune oficială<br>
• Conversație AI de bază, apelare unelte<br>
• Integrare Shizuku/Root
</td>
</tr>
</table>

> 📝 **Jurnal complet de modificări**: vizitează [pagina de Releases](https://github.com/Davinci198/fix-operit/releases) pentru detalii despre fiecare versiune

---

## 👨‍💻 Co-creare open source

Bine ai venit în ecosistemul open source Operit! Așteptăm contribuții de toate felurile: scripturi de la terți, plugin-uri MCP, dezvoltare de funcții de bază.

**Informații pentru dezvoltatori:**
- 📚 [Ghid de co-creare open source](docs/doc-src/dev-core/CONTRIBUTING.md) | [Ghid de dezvoltare a scripturilor](docs/SCRIPT_DEV_GUIDE.md)
- 📦 Construirea proiectului necesită descărcarea arhivelor cu dependențe non-model de pe [Google Drive](https://drive.google.com/drive/folders/1g-Q_i7cf6Ua4KX9ZM6V282EEZvTVVfF7?usp=sharing) (`subpack.zip`, `jniLibs.zip`, `libs.zip`); modelul local implicit STT este obținut și validat automat în faza de build Android, conform `app/config/stt-model-assets.properties`

### 💖 Contribuitori

Mulțumim tuturor celor care au contribuit la Operit AI!

<a href="https://github.com/Davinci198/fix-operit/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=Davinci198/fix-operit" />
</a>

## 💖 Susținerea dezvoltării

Dacă Operit AI îți este de folos, poți susține voluntar dezvoltarea continuă a proiectului și cheltuielile de bază:


- Sponsorizarea este complet voluntară și nu este legată de nicio funcție, cotă, actualizare, suport sau alt beneficiu
- Chiar dacă nu sponsorizezi, utilizarea normală, primirea actualizărilor și accesul la codul open source nu sunt afectate
- Poți folosi și butonul `Sponsor` din partea de sus a repo-ului GitHub pentru a ajunge la pagina de sponsorizare

---

## 📄 Licență

Acest proiect este licențiat sub [LGPL-3.0-only](https://spdx.org/licenses/LGPL-3.0-only.html).

Pe scurt, asta înseamnă:
- Poți folosi, modifica și distribui liber codul acestui proiect.
- Dacă modifici codul și îl distribui, trebuie să publici partea modificată tot sub licența LGPL-3.0-only.
- Pentru detalii, consultă fișierul [LICENSE](LICENSE).

---

## 📝 Raportarea problemelor

Ai întâmpinat o problemă sau ai sugestii? Deschide un [Issue](https://github.com/Davinci198/fix-operit/issues)!

**Ghid pentru raportare:**
- 📝 Descrie clar problema/sugestia, cu pași de reproducere
- 📱 Adaugă modelul dispozitivului, versiunea sistemului etc.
- 📸 Dacă e posibil, atașează capturi de ecran sau înregistrări

---

<div align="center">
  <h3>⭐ Dacă ți se pare util proiectul, lasă-ne un Star ⭐</h3>
  <p><b>🚀 Ajută-ne să-l facem cunoscut mai multor oameni — Operit AI 🚀</b></p>
  ## Star History

<a href="https://www.star-history.com/?repos=Davinci198%2Ffix-operit&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=Davinci198/fix-operit&type=date&theme=dark&legend=top-left&sealed_token=x2g4HD_vqrg9vWOmPW-1NFSSSJK2LImmWpVQBambbxIE2pHqGHAzid1rnimOClPo9Xjg6oLM4771kAIr_JgdboIOqdJuFVSozXRgW2w2HOOSCBtWbL1w9w" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=Davinci198/fix-operit&type=date&theme=dark&legend=top-left&sealed_token=x2g4HD_vqrg9vWOmPW-1NFSSSJK2LImmWpVQBambbxIE2pHqGHAzid1rnimOClPo9Xjg6oLM4771kAIr_JgdboIOqdJuFVSozXRgW2w2HOOSCBtWbL1w9w" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=Davinci198/fix-operit&type=date&legend=top-left&sealed_token=x2g4HD_vqrg9vWOmPW-1NFSSSJK2LImmWpVQBambbxIE2pHqGHAzid1rnimOClPo9Xjg6oLM4771kAIr_JgdboIOqdJuFVSozXRgW2w2HOOSCBtWbL1w9w" />
 </picture>
</a>
  <br>

  <sub>Made with ❤️ by the Operit Team</sub>
</div>
