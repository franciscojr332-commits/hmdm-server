# Headwind MDM – O que o projeto faz hoje e o que adicionar

## Seu cenário: vários apps no trabalho (sem kiosk de app único)

O funcionário usa **Waze, WhatsApp, seu APK e outros aplicativos** durante o expediente – ou seja, **não** é kiosk de um app só. O Headwind cobre isso assim:

- **Não use modo Kiosk** (que prende em um app principal).
- Use **modo permissivo** (configuração com **“Modo permissivo (desbloqueado)”** / `permissive` ligado, ou **“Usar launcher padrão”** / `runDefaultLauncher`), assim o celular funciona com launcher normal e vários apps.
- Na mesma configuração, defina **quais apps podem**:
  - **Instalar** – Waze, WhatsApp, seu APK, etc.
  - **Permitir** – aparecem e podem ser usados.
  - **Bloquear** / **Não instalar** – o que não fizer parte do trabalho.
- Assim você tem “só os apps que eu permitir” (Waze, WhatsApp, seu APK, outros) **sem** travar o aparelho em um único app.

As restrições que você quer (bloquear modo avião, não desligar GPS, não desligar das 07 às 18, bloquear trocar/adicionar usuário) podem ser **acopladas a essa mesma configuração** – são opções adicionais, independentes de kiosk.

---

## 1. O que o projeto já faz hoje (Open Source)

### 1.1 Servidor (hmdm-server – painel web)

- **Dispositivos:** listar, buscar, filtrar, editar descrição, agrupar, atribuir configuração, ver informações detalhadas (plugin deviceinfo).
- **Configurações:** criar/editar múltiplas configurações por cliente, cada uma com suas regras.
- **Aplicativos:** upload de APK, versões, associar a configurações (Instalar / Bloquear / Não instalar / Permitir), parâmetros por app (keycode, exibir na última linha, etc.).
- **Grupos e clientes:** multi-tenant, grupos de dispositivos, papéis de usuário (admin, usuário), permissões.
- **Autenticação:** login/senha, 2FA (TOTP/Google Authenticator) – implementado no seu fork.
- **Design:** cores, imagem de fundo, tamanho de ícones, cabeçalho do desktop, exibir hora/bateria.
- **Idiomas:** vários locales (en, pt, es, fr, de, ru, ar, zh, ja, tr, vi, it).
- **APIs REST:** sync para o agente Android, arquivos, ícones, atualizações, etc.

### 1.2 Configurações enviadas ao dispositivo (SyncResponse)

O servidor envia ao app Android, por configuração, entre outros:

| Categoria | Opções |
|----------|--------|
| **Rede / Conectividade** | GPS (qualquer / desligado / ligado), Wi‑Fi, dados móveis, Bluetooth – valor fixo (on/off) ou “qualquer”. |
| **Localização** | requestUpdates: não rastrear / GPS / Wi‑Fi; disableLocation (não conceder permissão de local). |
| **Tela / hardware** | Brilho (auto/manual/valor), timeout de tela, volume (travar/ajustar), orientação, desativar screenshots. |
| **Modo Kiosk** | kioskMode, app principal, app de conteúdo, Home/Recents/notificações/status bar, **travar botão de energia** (kioskLockButtons), **tela sempre ligada** (kioskScreenOn), keyguard, botão de sair do kiosk. |
| **Restrições Android** | `restrictions` (texto livre): ex. `no_sms`, `no_outgoing_calls`, `no_usb_file_transfer` – dependem do que o **launcher Android** implementar. |
| **Segurança** | lockSafeSettings (travar “Configurações seguras”: Wi‑Fi, GPS, etc.), senha de desbloqueio do MDM, passwordMode. |
| **Outros** | runDefaultLauncher (modo permissivo), blockStatusBar (deprecated, usar kiosk), timezone, allowedClasses, adminExtras, etc. |

Ou seja: **travar Wi‑Fi/GPS** (evitar que o usuário desligue) hoje é feito via **lockSafeSettings** + política de GPS/Wi‑Fi na configuração. **Travar botão de energia** e **tela sempre ligada** já existem no kiosk (kioskLockButtons, kioskScreenOn).

### 1.3 O que depende do app Android (hmdm-android)

O repositório do launcher/agente é: **https://github.com/h-mdm/hmdm-android**

- Quem **aplica** as restrições no aparelho é o **launcher** (Device Owner ou Profile Owner).
- O servidor só **envia** parâmetros (ex.: gps, wifi, kioskMode, restrictions, lockSafeSettings).
- Novas restrições (ex.: “bloquear modo avião”, “bloquear desligar das 07 às 18”) exigem:
  - **Servidor:** novos campos ou valores na configuração (opcional, para você escolher no painel).
  - **Launcher Android:** código novo para ler essa config e chamar as APIs do Android (DevicePolicyManager, UserManager, etc.).

---

## 2. O que você quer e o que falta

Resumo do que você pediu e onde entra no projeto.

### 2.1 Bloquear modo avião

- **Hoje:** não existe opção específica “bloquear modo avião” no servidor nem no launcher open source.
- **Android:** desativar/forçar modo avião exige mexer em `Settings.Global.AIRPLANE_MODE_ON`. Em geral só é possível de forma confiável com app **sistema** ou **Device Owner** com permissões especiais; em muitos dispositivos não é possível bloquear 100% sem firmware customizado.
- **O que adicionar:**
  - **Servidor:** um campo na configuração, ex.: `blockAirplaneMode` (boolean), e enviar no sync (ex. em `SyncResponse` / payload que o app lê).
  - **Launcher (hmdm-android):** ao aplicar a configuração, se `blockAirplaneMode` for true, usar Device Policy / monitorar modo avião e reativar rede (conforme permitido pela API no Android da versão alvo).

### 2.2 Impedir desativar GPS (ou forçar GPS ligado)

- **Hoje:** já existe:
  - **GPS:** na configuração pode ser “Qualquer”, “Desligado” ou “Ligado”.
  - **lockSafeSettings:** “Travar configurações seguras (Wi‑Fi, GPS, etc.)” – ajuda a impedir o usuário de abrir e desligar GPS nas configurações.
- **O que pode faltar:** no launcher, garantir que quando a config diz “GPS ligado” + lockSafeSettings, o app realmente reaplica isso (ex. reativar GPS se o usuário desligar por outro meio). Isso é implementado no **hmdm-android**, não no servidor.

### 2.3 Seu APK em uso (vários apps: Waze, WhatsApp, seu APK – sem kiosk) + bloquear “forçar encerramento” / fechar app

No seu cenário (Waze, WhatsApp, seu APK, outros): use **modo permissivo** e defina só os apps permitidos; não use kiosk. autostartForeground e, no launcher, reabrir seu APK quando fechado ajudam, sem travar em um app só.

- **Hoje:**
  - **Modo kiosk** com um “app principal” faz o launcher abrir e manter esse app em foco.
  - **autostartForeground:** “manter apps auto-iniciados em primeiro plano” ajuda a trazer o app de volta.
  - Android **não permite** impedir 100% que o usuário use “Forçar parada” em Ajustes; o que dá para fazer é o launcher **reabrir** o app assim que possível (ex. ao voltar da tela de configurações ou por timer).
- **O que adicionar:**
  - No **launcher:** política mais agressiva de “reabrir app principal” quando ele for fechado (e, se possível, desabilitar “Forçar parada” na tela de apps – quando a API permitir para Device Owner).
  - No **servidor:** pode existir um flag do tipo “manter app principal sempre em primeiro plano” para deixar explícito esse comportamento na configuração.

### 2.4 Das 07 às 18 impedir desligar o aparelho

- **Hoje:** existe **kioskLockButtons** (travar botão de energia) e **kioskScreenOn** (tela sempre ligada), mas são **sempre ativos** quando o kiosk está ligado; não há “janela de horário” no servidor nem no launcher.
- **O que adicionar:**
  - **Servidor:** campos na configuração, por exemplo:
    - `blockPowerOffFrom` / `blockPowerOffTo` (horário início/fim, ex. 07:00 e 18:00), ou
    - um único “horário de trabalho” (workScheduleFrom / workScheduleTo) usado para “bloquear desligar” e, se quiser no futuro, outras regras.
  - Enviar esses campos no sync (ex. em `SyncResponse` ou objeto de “restrictions”).
  - **Launcher:** ler esses horários e, dentro da janela 07–18, aplicar a mesma lógica de “travar botão de energia” (e, se desejado, ignorar desligamento). Fora do horário, permitir desligar normalmente.

### 2.5 Bloquear trocar de usuário / adicionar usuário – só os apps que você permitir

- **Hoje:**
  - **Modo kiosk** + lista de aplicativos (Instalar / Permitir / Bloquear) já limitam quais apps aparecem e podem ser usados.
  - Não há, no open source, opção explícita “bloquear troca de usuário” ou “bloquear adicionar usuário”.
- **Android:** Device Owner pode usar restrições como:
  - `UserManager.DISALLOW_ADD_USER`
  - `UserManager.DISALLOW_SWITCH_USER` (em alguns cenários)
  para impedir adicionar usuário e, em certas versões, trocar de usuário.
- **O que adicionar:**
  - **Servidor:** flags na configuração, ex.: `blockAddUser`, `blockSwitchUser` (boolean).
  - Incluir no sync.
  - **Launcher:** ao aplicar a configuração do dispositivo, chamar `DevicePolicyManager.addUserRestriction()` com essas constantes quando o flag vier true (e remover a restrição quando false).

---

## 3. Onde mudar no código (resumo)

| Objetivo | Servidor (hmdm-server) | Launcher (hmdm-android) |
|----------|------------------------|--------------------------|
| Bloquear modo avião | Novo campo na configuração + sync | Ler config e aplicar/bloquear modo avião (onde a API permitir) |
| GPS sempre ligado / não desligar | Já existe (gps + lockSafeSettings) | Reforçar no launcher: reativar GPS se desligado |
| APK sempre aberto / dificultar “forçar parar” | Opcional: flag “manter app em foco” | Reabrir app principal ao ser fechado; desabilitar “Forçar parada” se a API permitir |
| Das 07 às 18 não desligar | Campos de horário (ex. blockPowerOffFrom/To) no Configuration + SyncResponse | Ler horário e aplicar “travar energia” só no intervalo |
| Bloquear trocar/adicionar usuário | Campos blockAddUser, blockSwitchUser + sync | addUserRestriction(DISALLOW_ADD_USER / DISALLOW_SWITCH_USER) |

Todas as políticas no dispositivo são aplicadas pelo **hmdm-android**; o servidor só armazena e envia a configuração.

---

## 4. Ordem sugerida de implementação

1. **Servidor:** adicionar os novos campos em `Configuration` (e no banco, via migration) e em `SyncResponse`, e expor no painel (configuração → restrições / MDM).
2. **Launcher:** no repositório **hmdm-android**, no código que processa o sync:
   - Implementar bloqueio de modo avião (se viável na API).
   - Reforçar “GPS ligado” quando lockSafeSettings estiver ativo.
   - Implementar janela de horário para travar botão de energia (07–18).
   - Aplicar restrições de usuário (DISALLOW_ADD_USER / DISALLOW_SWITCH_USER) quando os novos flags forem true.
   - Política de “reabrir app principal” para aproximar “sempre aberto” e dificultar uso após “forçar parar”.

Se quiser, no próximo passo podemos detalhar os nomes exatos dos campos no `Configuration` e no `SyncResponse` e um exemplo de migration Liquibase para você já deixar o servidor pronto para o launcher.
