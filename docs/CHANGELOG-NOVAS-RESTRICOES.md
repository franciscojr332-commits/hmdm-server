# Changelog: Novas restrições MDM (block airplane, power-off window, reopen app, block add user)

## Resumo

Foram adicionadas cinco novas opções de configuração MDM, enviadas ao dispositivo via sync e aplicadas pelo launcher Android (quando instalado com as alterações correspondentes no fork do hmdm-android).

## Alterações no servidor (hmdm-server)

### Banco de dados (Liquibase)

- **Ficheiro:** `server/src/main/resources/liquibase/db.changelog.xml`
- **ChangeSet:** `28.02.26-12:00`
- Novas colunas na tabela `configurations`:
  - `blockAirplaneMode` (BOOLEAN)
  - `blockPowerOffFrom` (VARCHAR(5), formato HH:mm)
  - `blockPowerOffTo` (VARCHAR(5), formato HH:mm)
  - `reopenAppPackage` (VARCHAR(512))
  - `blockAddUser` (BOOLEAN)

### Modelo e persistência

- **Configuration.java:** cinco novos campos com getters/setters e inclusão em `copy()`.
- **ConfigurationMapper.java:** cláusula UPDATE com os cinco campos.
- **ConfigurationMapper.xml:** INSERT com as cinco colunas e valores.

### Sync (resposta ao dispositivo)

- **SyncResponse.java:** propriedades e getters/setters para os cinco campos (`@JsonInclude(NON_NULL)`).
- **SyncResponseInt.java:** getters na interface.
- **SyncResource.java:** preenchimento do payload com `configuration.getBlockAirplaneMode()`, etc.

### UI e localização

- **configuration.html:** na aba Configurações MDM, novos controlos:
  - Bloquear modo avião (checkbox)
  - Impedir desligar entre [HH:mm] e [HH:mm]
  - App a reabrir quando fechado (pacote)
  - Bloquear adicionar/trocar utilizador (checkbox)
- **en_US.js, pt_PT.js:** chaves de localização para os novos labels e placeholders.

## Alterações no launcher Android (hmdm-android, fork)

### Leitura da configuração

- **ServerConfig.java:** cinco campos e getters/setters para o JSON do sync.

### Políticas aplicadas

- **Utils.java:**
  - `setAirplaneModeBlocked(Context, boolean)` – desativa modo avião quando ativado (Device Owner; em alguns aparelhos pode falhar).
  - `applyBlockAddUser(Context, boolean)` – aplica/remove `DISALLOW_ADD_USER` e `DISALLOW_SWITCH_USER`.
  - `isInsideBlockPowerOffWindow(ServerConfig)` – indica se a hora atual está dentro da janela blockPowerOffFrom–blockPowerOffTo.
- **Initializer.java:** em `applyEarlyNonInteractivePolicies`, chama `Utils.setAirplaneModeBlocked(context, true)` quando `blockAirplaneMode` é true.
- **ConfigUpdater.java:** em `lockRestrictions`, aplica `blockAddUser` e arranca/para `ReopenAppService` consoante `reopenAppPackage`.
- **AdminActivity.java:** em `clearRestrictions`, chama `Utils.applyBlockAddUser(this, false)` ao desbloquear.
- **MainActivity.java:** em `dispatchKeyEvent`, consome `KEYCODE_POWER` quando dentro da janela de bloqueio de desligar.
- **ReopenAppService (novo):** serviço em foreground que verifica periodicamente se o pacote em `reopenAppPackage` está a correr e reabre-o se tiver sido fechado.
- **AndroidManifest.xml:** declaração do `ReopenAppService` com `foregroundServiceType="specialUse|systemExempted"`.

## Comportamento esperado

- **Painel:** o administrador pode ativar/desativar as novas opções numa configuração; ao gravar, o sync passa a incluir os novos campos.
- **Dispositivo (com launcher alterado e Device Owner):**
  - Modo avião pode ser forçado a desligado (conforme suporte do fabricante).
  - Entre `blockPowerOffFrom` e `blockPowerOffTo`, o botão de energia não desliga o aparelho.
  - Se `reopenAppPackage` estiver definido, o app desse pacote é reaberto automaticamente se for fechado.
  - Com `blockAddUser` ativo, não é possível adicionar ou trocar de utilizador; ao desbloquear no AdminActivity, estas restrições são libertadas.

## Validação sugerida

1. **Servidor:** Editar uma configuração, ativar “Bloquear modo avião” e “Bloquear adicionar/trocar utilizador”, preencher janela de horário e pacote a reabrir; guardar. Fazer um pedido de sync (ou inspecionar o JSON de sync) e confirmar que os campos aparecem no payload.
2. **Launcher:** Instalar o APK do fork num dispositivo com Device Owner; aplicar a mesma configuração; confirmar no dispositivo que as restrições têm o efeito esperado (modo avião, janela de desligar, reabertura do app, bloqueio de utilizador).
