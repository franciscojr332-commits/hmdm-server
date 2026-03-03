# Resolver "algo deu errado" no provisionamento via QR

## Checklist de verificação

### 1. Base URL no painel HMDM
- **Configurações → Geral** → Base URL deve ser: `https://mdm.mikitos.com.br`
- O componente recebe `com.hmdm.BASE_URL` do QR; se for IP ou HTTP, pode falhar.

### 2. Componente DPC
- **Configurações → Configuração** → **Componente de receção de eventos**
- Deve ser: `com.hmdm.launcher.AdminReceiver` ou `.AdminReceiver`
- O package da aplicação principal deve ser: `com.hmdm.launcher`

### 3. URL do launcher
- Use **Substituir URL do launcher**: `https://mdm.mikitos.com.br/files/app-opensource-release-unsigned.apk`
- Ou garanta que a aplicação principal tenha exatamente esta URL.

### 4. APK assinado (recomendado)
O APK **unsigned** pode falhar em Android 14+. O build `assembleRelease` sem signingConfig gera APK unsigned.

**Opção rápida – build debug (já assinado com keystore debug):**
```bash
cd hmdm-android
./gradlew assembleDebug
# APK em: app/build/outputs/apk/opensource/debug/app-opensource-debug.apk
```

Depois:
1. Copiar o APK para o servidor (pasta files) ou fazê-lo upload no painel
2. Atualizar a URL na aplicação/launcher para o novo ficheiro
3. Gerar novo QR

### 5. Deploy do fix do hash
O servidor precisa da alteração em `QRCodeResource.java` para calcular o hash a partir da URL correta.
- Fazer push do código e **rebuild** no Coolify
- Ou build manual: `mvn clean install` + reconstruir a imagem Docker

### 6. Ver logs no dispositivo (erro real)
Com o dispositivo ligado por USB:
```bash
adb logcat | grep -iE "provisioning|hmdm|device.owner|checksum|error"
```
Digitalize o QR e observe a mensagem de erro exata.

### 7. Ver o JSON do QR nos logs do servidor
```bash
docker exec -it <container_mdm> sh
tail -f /usr/local/tomcat/logs/catalina.out
```
Gere um QR no painel e verifique se o JSON contém:
- URL correta do APK
- `PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM` preenchido
- `com.hmdm.BASE_URL` = `https://mdm.mikitos.com.br`

## Resumo das causas mais comuns

| Causa | Solução |
|-------|---------|
| Hash incorreto (launcherUrl ≠ URL usada para hash) | Deploy do fix em QRCodeResource.java |
| Base URL errada (IP em vez de domínio) | Configurações → Base URL = https://mdm.mikitos.com.br |
| APK unsigned em Android 14+ | Usar APK assinado (release ou debug) |
| Componente DPC errado | Verificar `com.hmdm.launcher.AdminReceiver` |
| URL do APK inacessível | Testar: `curl -I https://mdm.mikitos.com.br/files/xxx.apk` → 200 |
