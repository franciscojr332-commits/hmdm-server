# Análise: comando de reset de fábrica via push não chega ao dispositivo

**Objetivo:** Entender por que o reset de fábrica solicitado no painel não é executado no dispositivo, enquanto outros comandos via push funcionam.

---

## 1. Fluxo do reset via push (diferente dos outros comandos)

| Outros comandos (runApp, reboot, etc.) | Reset de fábrica |
|----------------------------------------|------------------|
| Push contém o comando direto (payload). | Push é só **configUpdated** (avisar que a configuração mudou). |
| Dispositivo processa o payload e executa. | Dispositivo deve **fazer sync**, obter **factoryReset: true** na resposta e **só então** executar o reset. |

Ou seja: o reset **não** vem no push; o push só dispara um **sync**. O reset vem na **resposta do sync**.

---

## 2. Fluxo completo (servidor → dispositivo)

```
1. Admin clica "Restaurar dispositivo de fábrica"
2. Backend: deviceDAO.requestDeviceReset(deviceId)  → UPDATE devices SET pending_factory_reset = true
3. Backend: pushService.sendSimpleMessage(deviceId, "configUpdated")
4. Dispositivo recebe push "configUpdated" (MQTT ou long polling)
5. PushNotificationProcessor.process() → ConfigUpdater.notifyConfigUpdate(context)
6. ConfigUpdater.updateConfig() → GetServerConfigTask.execute()
7. Dispositivo: GET (ou POST) .../rest/public/sync/configuration/{deviceNumber}
8. Servidor: getDeviceByNumber(number) → dbDevice.getPendingFactoryReset() == true
9. Servidor: data.setFactoryReset(true) na SyncResponse → JSON com "factoryReset": true
10. Dispositivo: settingsHelper.updateConfig(serverConfig) (config com factoryReset: true)
11. ConfigUpdater: updateRemoteLogConfig() → ... → setupPushService() → checkFactoryReset()
12. checkFactoryReset(): config.getFactoryReset() == true → ConfirmDeviceResetTask
13. ConfirmDeviceResetTask: POST .../rest/plugins/devicereset/public/{number} (confirmar no servidor)
14. Servidor: clearPendingFactoryResetByNumber(number)
15. Dispositivo: Utils.factoryReset(context) (execução do reset)
```

---

## 3. Pontos de falha possíveis

### A) Push configUpdated não está a ser enviado ou não chega

- **Verificar no servidor:** Log `"Device reset requested for device id X, configUpdated push sent"` em `DeviceResetResource.doRequestDeviceReset`. Se aparecer e `pushSent == true`, o envio está OK.
- **Verificar no servidor:** Se `pushSent == false`, o log é `"Device X not found for push"` → dispositivo não encontrado no canal de push (MQTT/polling).
- **Comparar com outros comandos:** Outros comandos podem usar outro tipo de mensagem ou outro canal. Confirmar que o **mesmo** dispositivo recebe **configUpdated** (ex.: testar alterar uma configuração no painel e ver se o dispositivo atualiza).

### B) Dispositivo recebe o push mas não faz sync

- **ConfigUpdater.notifyConfigUpdate:** Chama `updateConfig()`, que inicia `GetServerConfigTask`. Se houver **configInitializing == true** noutra thread, o `updateConfig()` sai logo (log: `"updateConfig(): configInitializing=true, exiting"`).
- **GetServerConfigTask:** Erro de rede, 404, ou resposta sem `data` → não chama `settingsHelper.updateConfig(serverConfig)`, e `checkFactoryReset()` corre com a **config antiga** (sem factoryReset).

### C) Sync é feito mas a resposta não traz factoryReset

- **Servidor:** Em `SyncResource.getDeviceSettingInternal()` (ou equivalente), o dispositivo é carregado com `unsecureDAO.getDeviceByNumber(number)`. O MyBatis deve incluir `pending_factory_reset` no `deviceResult` (DeviceMapper.xml: `pendingFactoryReset`).
- **Servidor:** A condição é `if (dbDevice != null && Boolean.TRUE.equals(dbDevice.getPendingFactoryReset())) { data.setFactoryReset(true); }`. Se o dispositivo for carregado por outro método que não preencha `pendingFactoryReset`, o flag não aparece na resposta.
- **Cliente:** O JSON da sync é deserializado para `ServerConfig`; o campo `factoryReset` existe (getter/setter). Se o servidor não enviar o campo ou enviar com outro nome, o cliente não vê `factoryReset: true`.

### D) checkFactoryReset() não é chamado ou não vê factoryReset

- **Ordem de execução:** `checkFactoryReset()` é chamado depois de `updateRemoteLogConfig()` → `checkServerMigration()` → `setupPushService()`. Se alguma exceção ou return antecipado ocorrer antes, `checkFactoryReset()` pode não ser chamado.
- **Config em memória:** `checkFactoryReset()` usa `settingsHelper.getConfig()`. Se a config tiver sido guardada noutra instância de SettingsHelper ou antes do sync terminar, pode estar a ver config antiga.

### E) ConfirmDeviceResetTask falha

- **Rede / URL:** POST para `.../rest/plugins/devicereset/public/{number}`. Se o projeto usar prefixo (ex.: `/api`) ou o plugin estiver noutro path, o pedido pode falhar.
- **Servidor:** Resposta não 2xx → `ConfirmDeviceResetTask` devolve algo diferente de `TASK_SUCCESS` → o código não chama `Utils.factoryReset(context)` (log: "Failed to confirm device reset on server" ou "Device reset failed: no permissions").

### F) Utils.checkAdminMode(context) é false

- Em `checkFactoryReset()`, o reset só é executado se `Utils.checkAdminMode(context)` for true (Device Owner). Caso contrário: log "Device reset failed: no permissions".

---

## 4. O que verificar agora

### No servidor (logs e BD)

1. Ao clicar em "Restaurar dispositivo de fábrica":
   - Aparece no log: `"Device reset requested for device id X, configUpdated push sent"`?
   - Ou `"Device X not found for push"`?
2. Na base de dados: `SELECT id, number, pending_factory_reset FROM devices WHERE id = ?` — após o pedido de reset, `pending_factory_reset` deve ser `true` para esse dispositivo.
3. Quando o dispositivo faz sync (GET .../rest/public/sync/configuration/{number}): inspecionar a resposta JSON (ou log no servidor) e confirmar que existe `"factoryReset": true`.

### No dispositivo (logcat)

1. Filtrar por tag/Const.LOG_TAG e por "configUpdated", "Got Push Message", "updateConfig", "Configuration updated", "checkFactoryReset", "Device reset by server request", "Failed to confirm", "Device reset failed".
2. Confirmar que, após receber o push, há:
   - "Got Push Message, type configUpdated"
   - "Configuration updated" (GetServerConfigTask sucesso)
   - "checkFactoryReset() called"
   - "Device reset by server request" (se config.getFactoryReset() == true)
   - Ou uma das mensagens de falha (confirm, permissions, etc.).

### Comparação com outros comandos

- Outros comandos (ex.: runApp, reboot) são mensagens de push com tipo e payload próprios; o dispositivo executa a ação diretamente.
- O reset depende de: (1) push configUpdated, (2) sync, (3) campo factoryReset na resposta, (4) checkFactoryReset(), (5) confirm no servidor, (6) Device Owner. Se algum destes falhar, o reset não ocorre mesmo que o push tenha chegado.

---

## 5. Próximo passo sugerido: logs de diagnóstico

Adicionar logs temporários:

- **Servidor:** Em `SyncResource`, ao montar a resposta de configuração: log quando `dbDevice.getPendingFactoryReset()` for true e quando `data.setFactoryReset(true)` for chamado.
- **Android:** Em `ConfigUpdater.checkFactoryReset()`: log do valor de `config.getFactoryReset()` e de `Utils.checkAdminMode(context)`; em `GetServerConfigTask` após obter config: log se `serverConfig.getFactoryReset() != null && serverConfig.getFactoryReset()`.

Com isso consegues ver se o problema está em: push não enviado, sync não feito, resposta sem factoryReset, ou checkFactoryReset/confirm/admin mode.
