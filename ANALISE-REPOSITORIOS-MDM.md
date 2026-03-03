# Análise completa dos repositórios MDM (hmdm-server + hmdm-android)

**Data:** 2 de março de 2025  
**Repositórios:** [hmdm-server](https://github.com/franciscojr332-commits/hmdm-server) | [hmdm-android](https://github.com/franciscojr332-commits/hmdm-android)

---

## 1. Resumo executivo

- **Estado do repositório:** Ambos os repositórios estão **limpos** (`git status`: nothing to commit, working tree clean) e sincronizados com `origin/master`.
- **Últimos commits:** As funcionalidades de **novas restrições MDM** (bloquear modo avião, janela de bloqueio de desligar, reabrir app, bloquear adicionar utilizador) estão implementadas tanto no **servidor** quanto no **launcher Android**, com commits recentes e documentação em `docs/CHANGELOG-NOVAS-RESTRICOES.md`.
- **Conclusão:** As alterações estão **100% aplicadas e consistentes** entre servidor e Android. O fluxo BD → API → Sync → Launcher está completo.

---

## 2. Estrutura do projeto

### hmdm-server (painel web + API)

| Pasta/ficheiro      | Descrição |
|---------------------|-----------|
| `common/`           | Domínio, mappers MyBatis, interfaces (Configuration, SyncResponseInt, etc.) |
| `server/`           | REST (Jersey), recursos (SyncResource, QRCodeResource), webapp AngularJS |
| `jwt/`              | Autenticação JWT |
| `notification/`     | Notificações |
| `plugins/`          | Plugins MDM |
| `swagger/ui`        | Documentação API Swagger |
| `docs/`             | Documentação (CHANGELOG-NOVAS-RESTRICOES.md, etc.) |
| `pom.xml`           | Maven multi-módulo (Java 8, Tomcat) |

**Stack:** Java 8, Maven, PostgreSQL, Liquibase, MyBatis, Jersey (JAX-RS), AngularJS, Tomcat.

### hmdm-android (launcher MDM)

| Pasta/ficheiro | Descrição |
|----------------|-----------|
| `app/`         | Aplicação principal (launcher, activities, services, helpers) |
| `lib/`         | Biblioteca AAR para integração MDM (HeadwindMDM, MDMService, etc.) |
| `build.gradle`  | Gradle (Android) |

**Stack:** Android (Java), Gradle.

---

## 3. Últimos commits e funcionalidades

### hmdm-server (últimos 5 commits)

| Commit     | Mensagem |
|-----------|----------|
| `c2dc235` | **feat(mdm): add new restriction options** – block airplane, power-off window, reopen app, block add user (BD, Configuration, Mapper, SyncResponse, UI, i18n, docs) |
| `b767aef` | fix: do not logout on 403 for settings/hints/users/plugins (2FA race) |
| `6aa87df` | fix: plugin xtra i18n 404 – add tr_TR, it_IT, vi_VN; handle missing locale |
| `a0bf7c2` | fix(2FA): avoid logout on 403 during 2FA, clear session after verify/set |
| `c60ca89` | fix: use fully qualified JAX-RS Response; register TwoFactorResource |

### hmdm-android (últimos 5 commits)

| Commit     | Mensagem |
|-----------|----------|
| `b04e5cc` | **feat(mdm): apply new restrictions** – block airplane, power-off window, reopen app, block add user (ServerConfig, Utils, Initializer, ConfigUpdater, AdminActivity, MainActivity, ReopenAppService) |
| `a33acbe` | Certificates can be specified in the QR code |
| `6164ea4` | Library API call to send Push command; Admin can clear app data by Push |
| `4a5e727` | v6.30: Push broadcast, locked_packages |
| `7b54cea` | v6.29: D-Pad, file path, Managed Settings, etc. |

---

## 4. Verificação das novas restrições MDM (100% aplicadas)

### 4.1 Servidor (hmdm-server)

| Componente | Ficheiro | Estado |
|------------|----------|--------|
| **BD (Liquibase)** | `server/src/main/resources/liquibase/db.changelog.xml` | ChangeSet `28.02.26-12:00`: colunas `blockAirplaneMode`, `blockPowerOffFrom`, `blockPowerOffTo`, `reopenAppPackage`, `blockAddUser` – OK |
| **Domínio** | `common/.../Configuration.java` | 5 campos + getters/setters + **copy()** (linhas 962–966) – OK |
| **Mapper** | `common/.../ConfigurationMapper.java` e `.xml` | UPDATE e INSERT com os 5 campos – OK |
| **Sync** | `server/.../SyncResponse.java` | Propriedades + getters/setters – OK |
| **Sync** | `common/.../SyncResponseInt.java` | Interface com getters – OK |
| **API Sync** | `server/.../SyncResource.java` | `data.setBlockAirplaneMode(...)` … `data.setBlockAddUser(...)` (linhas 423–427) – OK |
| **UI** | `server/.../configuration.html` | Checkbox modo avião, inputs HH:mm, campo pacote reabrir, checkbox bloquear utilizador – OK |
| **i18n** | `localization/en_US.js`, `pt_PT.js` | Chaves `form.configuration.settings.mdm.block.airplane.mode`, etc. – OK |
| **Docs** | `docs/CHANGELOG-NOVAS-RESTRICOES.md` | Documentação das alterações – OK |

### 4.2 Launcher Android (hmdm-android)

| Componente | Ficheiro | Estado |
|------------|----------|--------|
| **Config** | `app/.../json/ServerConfig.java` | 5 campos + getters/setters – OK |
| **Utils** | `app/.../util/Utils.java` | `setAirplaneModeBlocked()`, `applyBlockAddUser()`, `isInsideBlockPowerOffWindow()` – OK |
| **Initializer** | `app/.../helper/Initializer.java` | `Utils.setAirplaneModeBlocked(context, true)` em `applyEarlyNonInteractivePolicies` – OK |
| **ConfigUpdater** | `app/.../helper/ConfigUpdater.java` | `blockAddUser` em lockRestrictions; start/stop `ReopenAppService` – OK |
| **AdminActivity** | `app/.../ui/AdminActivity.java` | `Utils.applyBlockAddUser(this, false)` em clearRestrictions – OK |
| **MainActivity** | `app/.../ui/MainActivity.java` | `dispatchKeyEvent` consome KEYCODE_POWER quando dentro da janela – OK |
| **ReopenAppService** | `app/.../service/ReopenAppService.java` | Serviço em foreground que reabre o pacote configurado – OK |
| **Manifest** | `app/src/main/AndroidManifest.xml` | Declaração de `ReopenAppService` com `foregroundServiceType="specialUse|systemExempted"` – OK |

---

## 5. Fluxo de dados (sync)

1. **Painel:** Admin edita configuração (checkbox modo avião, HH:mm, pacote reabrir, checkbox bloquear utilizador) e grava.
2. **Servidor:** `Configuration` é persistido via MyBatis (ConfigurationMapper) com os 5 novos campos.
3. **Sync:** Dispositivo chama a API de sync; `SyncResource` monta `SyncResponse` a partir de `Configuration` e envia JSON com `blockAirplaneMode`, `blockPowerOffFrom`, `blockPowerOffTo`, `reopenAppPackage`, `blockAddUser`.
4. **Launcher:** `ServerConfig` recebe o JSON; `ConfigUpdater`/`Initializer`/`MainActivity`/`AdminActivity`/`ReopenAppService` aplicam as políticas no dispositivo (Device Owner necessário para modo avião e bloqueio de utilizador).

---

## 6. Pontos de atenção (não são erros)

- **Build:** Não foi executado `mvn install` neste ambiente (Maven não estava no PATH). Recomenda-se rodar `mvn install` no servidor e `gradlew build` no Android para validar compilação.
- **Modo avião:** A API do Android para forçar desativação do modo avião pode variar por fabricante; o código está correto para Device Owner.
- **ReopenAppService:** Usa `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` em Android 14+; a string de declaração no manifest está adequada.

---

## 7. Onde estão os repositórios clonados

- **hmdm-server:** `C:\Users\Fjunior\hmdm-server`
- **hmdm-android:** `C:\Users\Fjunior\hmdm-android`

Para abrir no Cursor/VS Code, adicione estas pastas ao workspace ou abra cada uma separadamente.

---

## 8. Próximos passos sugeridos

1. **Build:** Executar `mvn install` em `hmdm-server` e `gradlew build` em `hmdm-android` para garantir que não há erros de compilação.
2. **Testes:** Validar no painel (editar configuração, guardar, inspecionar payload de sync) e no dispositivo (instalar APK como Device Owner e testar as 4 restrições).
3. **Ajustes:** Indicar que tipo de ajustes deseja (novas restrições, correções, melhorias de UI, etc.) para continuar a implementação.

---

*Análise gerada com base no clone dos repositórios e leitura do código e dos últimos commits.*
