# Push runApp: execução em foreground e em background

O tipo de mensagem **runApp** do plugin Push permite executar uma aplicação no dispositivo.

## Payload (JSON)

| Campo       | Obrigatório | Descrição |
|------------|-------------|-----------|
| `pkg`      | Sim         | Package name da aplicação (ex.: `com.example.app`) |
| `background` | Não       | Se `true`, inicia um **Service** em background em vez da activity principal. Requer `component`. |
| `component`  | Se background | Nome completo da classe do Service (ex.: `com.example.app.MyService`) |
| `action`   | Não        | Intent action (ex.: `android.intent.action.MAIN`) |
| `data`     | Não        | URI para `intent.setData()` |
| `extra`    | Não        | Objeto com extras a passar ao Intent |

## Exemplos

**Foreground (abrir a app no ecrã):**
```json
{"pkg": "com.example.myapp", "background": false}
```
Ou simplesmente:
```json
{"pkg": "com.example.myapp"}
```

**Background (iniciar um Service da app):**
```json
{"pkg": "com.example.myapp", "background": true, "component": "com.example.myapp.MyBackgroundService"}
```

Com action e extras para o Service:
```json
{
  "pkg": "com.example.myapp",
  "background": true,
  "component": "com.example.myapp.SyncService",
  "action": "com.example.myapp.START_SYNC",
  "extra": {"force": true}
}
```

## Notas

- Em **foreground**, o launcher usa a launcher activity da app (`MAIN`/`LAUNCHER`).
- Em **background**, o launcher chama `startService(Intent)` com o `ComponentName(pkg, component)`. A app alvo deve exportar o Service ou o launcher deve ter permissão para o iniciar.
- Em Android 8+ (API 26+), restrições de execução em background podem aplicar-se ao Service da app alvo; o comportamento depende da app e do fabricante.
