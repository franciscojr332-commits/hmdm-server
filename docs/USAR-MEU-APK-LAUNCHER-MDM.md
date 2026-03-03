# Como garantir que o servidor usa o meu APK do launcher MDM

O servidor Headwind MDM pode usar **o teu** APK do launcher (o que compilas a partir do teu fork [hmdm-android](https://github.com/franciscojr332-commits/hmdm-android)) de duas formas: através da **aplicação principal** na configuração ou através do **override da URL do launcher**.

---

## Passo a passo: usar o meu APK agora (editar aplicação existente)

Se já tens a aplicação **Headwind MDM** (`com.hmdm.launcher`) no painel e queres apontá-la para o teu build:

- **Versão**: no `hmdm-android/app/build.gradle` o `versionName` é **6.31** — usa `6.31` no campo **Versão** do formulário.
- **Onde colocar o APK**:
  - **No servidor MDM**: copia o teu `.apk` para a pasta **files** do servidor (caminho `files.directory`, ex.: `_BASE_DIRECTORY_/files`). Nome sugerido: `hmdm-6.31-os.apk`. URL no formulário: `https://mdm.mikitos.com.br/files/hmdm-6.31-os.apk` (com o teu domínio).
  - **Noutro servidor**: coloca o APK num URL público e usa esse URL completo no formulário.
- **No formulário**: editar a aplicação Headwind MDM → **Versão** = `6.31` → **URL** = o URL do APK → **Salvar**.

---

## 1. Usar o teu APK como “Aplicação principal” (recomendado)

Assim o servidor guarda e serve o teu APK; o QR code e as instalações/atualizações usam sempre esse ficheiro.

### Passos

1. **Compilar o APK**  
   No projeto [hmdm-android](https://github.com/franciscojr332-commits/hmdm-android):  
   `./gradlew assembleRelease` (ou Build → Generate Signed Bundle/APK no Android Studio).  
   O APK fica em `app/build/outputs/apk/release/`.

2. **Criar uma aplicação no painel**  
   - **Aplicações** → **Adicionar aplicação**.  
   - Nome: por exemplo `Headwind MDM Launcher (meu build)`.  
   - **Enviar ficheiro**: escolhe o teu `.apk` (ou `.xapk`).  
   - O package deve ser `com.hmdm.launcher` (igual ao launcher oficial).  
   - Guardar.

3. **Definir como aplicação principal na configuração**  
   - **Configurações** → escolher a configuração (ou criar uma).  
   - Aba **Aplicações**: garantir que essa aplicação está na lista e com ação **Instalar**.  
   - Na secção **Configurações MDM** (ou onde está o seletor da aplicação principal), escolher **esta** aplicação como **aplicação principal** (main app / launcher).  
   - Preencher **Componente de receção de eventos** (ex.: `com.hmdm.launcher.AdminReceiver`).  
   - Guardar a configuração.

4. **Resultado**  
   - O **QR code** de inscrição passa a usar o URL do teu APK (servido pelo próprio servidor).  
   - Os dispositivos que fazem **sync** e têm essa configuração passam a receber o teu launcher na lista de aplicações e a instalá-lo/atualizá-lo a partir do servidor.  
   Ou seja: **o servidor passa a usar sempre o teu APK** para essa configuração.

---

## 2. Override da URL do launcher (rede fechada ou APK noutro servidor)

Se o teu APK estiver noutro sítio (outro servidor, CDN, rede interna), podes forçar o servidor a **apontar** para esse URL em vez do ficheiro da “aplicação principal”.

### Passos

1. **Configurações** → editar a configuração.  
2. Aba **Configurações MDM**.  
3. No campo **Substituir URL do inicializador** (Override launcher URL / `launcherUrl`), colocar o URL completo do teu APK, por exemplo:  
   - `https://meu-servidor.com/downloads/meu-launcher.apk`  
   - ou `https://painel-mdm.empresa.local/files/...` se o servidor MDM servir o ficheiro noutro path.  
4. Guardar.

### Comportamento

- A **aplicação principal** da configuração continua a definir o **package name** e o componente (ex.: `AdminReceiver`) usados no QR e no sync.  
- O **URL de download** do launcher (QR e atualizações) passa a ser o que meteste em **Substituir URL do inicializador**.  
Assim **garantes que o servidor “usa” o teu APK** no sentido de enviar os dispositivos a esse URL.

---

## Resumo

| Objetivo | O que fazer |
|----------|--------------|
| Servidor a **guardar e servir** o teu APK | Subir o APK como **Aplicação**, associá-lo à configuração e escolhê-lo como **Aplicação principal**. |
| Servidor a **apontar** para o teu APK noutro URL | Manter uma aplicação principal (pode ser a mesma ou outra) e preencher **Substituir URL do inicializador** com o URL do teu APK. |

Em ambos os casos, após guardar a configuração e usar o QR ou o sync, **o servidor passa a usar o teu APK do MDM** (direto do próprio servidor ou via URL override).
