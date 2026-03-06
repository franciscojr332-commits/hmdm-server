# Análise completa e profunda: o APK no dispositivo recebe o comando de reset de fábrica via push?

**Conclusão direta:** O APK **não recebe** o comando de reset *dentro* do push. O push envia apenas **"configUpdated"**. O comando de reset vem na **resposta do sync** que o dispositivo faz *depois* de receber esse push. Desde que o push seja entregue e o sync devolva `factoryReset: true`, o APK **sim, recebe e pode executar** o reset. Abaixo está o fluxo completo e onde pode falhar.

---

## 1. O que é enviado “via push” no caso do reset

| O que o admin faz | O que o servidor envia no push | O que o APK recebe no push |
|------------------|--------------------------------|----------------------------|
| Clica "Restaurar dispositivo de fábrica" | Uma mensagem de tipo **configUpdated** (sem payload de “reset”) | Apenas `messageType: "configUpdated"` |

Ou seja: **via push o dispositivo só recebe o aviso de “configuração atualizada”**. O “comando de reset” em si não vai no payload do push; ele vem mais tarde, na resposta do sync.

---

## 2. Fluxo completo (servidor → push → APK → sync → reset)

### 2.1 Servidor: pedido de reset e envio do push

1. **DeviceResetResource**  
   - Recebe o pedido com `deviceId` (ID interno do dispositivo).  
   - Chama `deviceDAO.requestDeviceReset(deviceId)` → `UPDATE devices SET pending_factory_reset = true WHERE id = ?`.  
   - Chama `pushService.sendSimpleMessage(deviceId, PushMessage.TYPE_CONFIG_UPDATED)`.

2. **PushService.sendSimpleMessage(deviceId, "configUpdated")**  
   - Obtém o dispositivo com `deviceDAO.getDeviceById(deviceId)`.  
   - Se não existir → log "device id X not found, push not sent" e retorna `false`.  
   - Cria `PushMessage` com `deviceId` (integer) e `messageType = "configUpdated"` (sem payload).  
   - Chama `send(message)`.

3. **PushService.send(message)**  
   - Envia pelo **MQTT** e pelo **long polling**:
     - `pushSenderMqtt.send(message)`  
     - `pushSenderPolling.send(message)`  

Ou seja: o mesmo tipo de mensagem que outros comandos (por exemplo “config atualizada”) é enviado pelos mesmos canais. O conteúdo da mensagem é só `configUpdated`, não “reset”.

### 2.2 Entrega do push ao dispositivo

**Canal MQTT**

- **Servidor (PushSenderMqtt):**  
  - Obtém `Device` por `message.getDeviceId()` (ID interno).  
  - Publica no tópico MQTT = `device.getNumber()` (ou `device.getOldNumber()` se existir).  
  - Payload: `{ "messageType": "configUpdated" }` (sem campo “reset”).

- **APK:**  
  - Conecta com `settingsHelper.getDeviceId()` (número do dispositivo, string).  
  - Subscreve ao tópico com esse mesmo valor: `client.subscribe(deviceId, 2, ...)`.  
  - Quando chega uma mensagem, faz parse do JSON e chama `PushNotificationProcessor.process(msg, context)`.

Conclusão: **se o dispositivo estiver a usar MQTT e o número (device number) no servidor for o mesmo que no APK, o APK recebe o push `configUpdated` pelo MQTT.**

**Canal Long polling**

- **Servidor (LongPollingServlet):**  
  - O dispositivo faz GET `/rest/notification/polling/{deviceNumber}` (número em string).  
  - O servidor resolve `device = unsecureDAO.getDeviceByNumber(deviceNumber)` e usa `device.getId()` (integer).  
  - Regista o pedido em `pushSenderPolling.register(device.getId(), asyncContext)`.  
  - Quando chega uma mensagem com esse `message.getDeviceId()` (integer):  
    - Se o dispositivo estiver “online” (long poll em espera), a mensagem é entregue de imediato nesse contexto.  
    - Se não estiver, a mensagem é gravada em BD (`notificationDAO.send(message)` em `pushMessages` + `pendingPushes` com `deviceId` integer).  
  - Quando o dispositivo volta a fazer long poll, o servidor chama `getPendingMessagesForDelivery(device.getId())` e devolve as mensagens pendentes (por ID interno).

- **APK:**  
  - Chama `queryPushLongPolling(project, settingsHelper.getDeviceId(), signature)` → GET `.../rest/notification/polling/{number}`.  
  - O servidor associa esse `number` ao mesmo `device.getId()` usado ao enviar o push.  
  - A resposta é uma lista de mensagens (JSON); Retrofit/Jackson deserializa para `List<PushMessage>`.  
  - Para cada mensagem, o APK chama `PushNotificationProcessor.process(message, context)`.

Conclusão: **se o dispositivo estiver a usar long polling e o device number no pedido for o mesmo que o do dispositivo no servidor, o APK recebe o push `configUpdated` por long polling** (na próxima sondagem ou de imediato se o long poll estiver à espera).

Resumo: **em ambos os canais, o APK recebe apenas a mensagem de tipo `configUpdated`**. Não há “comando de reset” no payload do push.

### 2.3 Onde está o “comando de reset”: na resposta do sync

Depois de receber o push, o APK trata o tipo `configUpdated` assim:

1. **PushNotificationProcessor.process(message, context)**  
   - Se `message.getMessageType().equals(PushMessage.TYPE_CONFIG_UPDATED)` → chama `ConfigUpdater.notifyConfigUpdate(context)` e return.

2. **ConfigUpdater.notifyConfigUpdate / updateConfig**  
   - Inicia atualização da configuração: `GetServerConfigTask` (pedido de sync).

3. **GetServerConfigTask**  
   - Faz GET (ou POST) para `.../rest/public/sync/configuration/{number}` (número do dispositivo).  
   - O servidor em **SyncResource** monta a resposta com base no dispositivo (por número).  
   - Se `dbDevice.getPendingFactoryReset() == true`, faz `data.setFactoryReset(true)` na resposta.  
   - O dispositivo recebe JSON com `"factoryReset": true`.

4. **APK**  
   - Guarda a config: `settingsHelper.updateConfig(serverConfig)`.  
   - Mais à frente no fluxo chama `checkFactoryReset()`.  
   - Se `config.getFactoryReset() == true`, executa o fluxo de confirmação no servidor e depois `Utils.factoryReset(context)`.

Conclusão: **o APK só “recebe” o comando de reset quando faz o sync e a resposta inclui `factoryReset: true`.** O push só dispara esse sync.

---

## 3. Resposta à pergunta: “o APK recebe via push os comandos de reset de fábrica?”

- **Via push (estritamente):** o APK recebe **apenas** o aviso **configUpdated**. Esse aviso é entregue pelos mesmos mecanismos (MQTT e/ou long polling) que outros pushes; não há diferença de canal para o reset.

- **O “comando” de reset em si:** é recebido **na resposta do sync** (pedido GET/POST de configuração), não no corpo do push. O push é só o gatilho para “ir buscar a config”; o reset vem no campo `factoryReset` da config.

- **Em termos de resultado:** sim, o dispositivo pode receber o “comando” de reset e executá-lo, desde que:  
  (1) receba o push `configUpdated`,  
  (2) faça o sync com o mesmo `deviceNumber` que tem no servidor,  
  (3) o servidor devolva `factoryReset: true`,  
  (4) e as condições no APK (Device Owner, confirmação, etc.) forem satisfeitas.

---

## 4. Condições para o APK realmente “receber” e executar o reset

| # | Condição | Onde pode falhar |
|---|----------|-------------------|
| 1 | Servidor encontra o dispositivo por `deviceId` | `getDeviceById(deviceId)` null → push não enviado. |
| 2 | Mensagem gravada/entregue no canal correto | MQTT: tópico = `device.getNumber()`; polling: mensagem com `deviceId` (integer) e dispositivo a fazer poll com o mesmo `number` que corresponde a esse ID. |
| 3 | APK usa o mesmo identificador que o servidor | MQTT: subscribe ao tópico = `settingsHelper.getDeviceId()` = número do dispositivo; polling: GET `.../polling/{settingsHelper.getDeviceId()}`. Se o número no servidor e no APK não coincidirem, o push não chega a este dispositivo. |
| 4 | Sync usa o mesmo número | GET `.../sync/configuration/{number}` com o mesmo `number` que no servidor. O servidor carrega o dispositivo por esse número e inclui `pending_factory_reset` na resposta. |
| 5 | BD e mapper incluem `pending_factory_reset` | DeviceMapper: `devices.pending_factory_reset AS pendingFactoryReset` no SELECT e no resultMap. Sem isso, a resposta nunca teria `factoryReset: true`. |
| 6 | APK não sai do fluxo antes de `checkFactoryReset()` | Por exemplo, `configInitializing` noutra thread, ou falha no `GetServerConfigTask` (rede, 404, etc.). |
| 7 | Device Owner / permissões | `Utils.checkAdminMode(context)` tem de ser true para executar o reset; caso contrário o APK “recebe” o flag mas não executa. |

---

## 5. Conclusão da análise

- **O APK recebe via push** exatamente o que o servidor envia: uma mensagem **configUpdated**, pelos canais MQTT e long polling, com a mesma lógica de identificação (device number / device id) que os outros comandos.
- **O “comando” de reset de fábrica** não vem no push; vem na **resposta do sync** (`factoryReset: true`). O push só ordena ao APK que faça sync.
- **Em conjunto:** sim, o dispositivo pode receber e executar o reset desde que o push seja entregue, o sync seja feito com o número correto, a resposta traga `factoryReset: true` e as condições de permissão no APK sejam cumpridas. Se “os outros comandos via push funcionam”, o canal de push está a chegar ao dispositivo; o ponto a validar em caso de falha é o sync (resposta com `factoryReset`) e o fluxo até `checkFactoryReset()` (incluindo Device Owner), não o facto de o push em si ser “diferente” para o reset.

Este documento pode ser usado como referência técnica para suporte e debugging (logs no servidor e no APK já adicionados noutros ficheiros da análise).
