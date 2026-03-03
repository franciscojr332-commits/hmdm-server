# Imagem de fundo não aparece no aparelho

Este guia explica por que a imagem de fundo enviada pelo painel pode não aparecer no launcher Android e qual formato/tamanho usar.

## Como funciona

1. No painel (Configuração → Design ou Configurações gerais → Design) você define a **URL da imagem de fundo**.
2. Ao fazer upload pelo botão "Upload", o servidor grava o arquivo e preenche a URL automaticamente (URL completa usando o **Base URL** do servidor).
3. O dispositivo recebe essa URL no sync da configuração e o app launcher baixa e exibe a imagem com **Picasso** (`.fit()` e `.centerCrop()`).

## Por que pode não aparecer

### 1. Base URL do servidor incorreta

A URL da imagem é montada com o parâmetro **`base.url`** do servidor (ex.: `https://mdm.mikitos.com.br`). Se no seu ambiente (Tomcat/Coolify) o `base.url` estiver como `http://localhost:8080` ou um IP interno, a URL salva será inacessível no celular.

**O que fazer:** Garanta que no contexto do servidor (ex.: `context.xml` ou variável de ambiente) o **base.url** seja a URL pública que o dispositivo usa, por exemplo:

- `https://mdm.mikitos.com.br`

Assim, a URL da imagem ficará no formato `https://mdm.mikitos.com.br/files/...` e o aparelho conseguirá baixá-la.

### 2. Dispositivo não sincronizou a configuração

A imagem de fundo é aplicada quando o dispositivo obtém a configuração atualizada. Se você alterou a URL e o aparelho não fez sync depois, ele continua com a config antiga (ou sem imagem).

**O que fazer:**

- Deixe o dispositivo online e espere o sync automático, ou
- Envie um push **"Requerer configuração"** (configUpdated) para forçar o sync, ou
- Abra o app launcher no dispositivo (ele pode sincronizar ao abrir).

### 3. Restrição de IP (device.allowed.address)

O endpoint `/files/*` usa o filtro de IP. Se **device.allowed.address** estiver configurado e o IP do dispositivo (ou da rede móvel) não estiver na lista, o servidor responde **403** e a imagem não carrega.

**O que fazer:** Use lista de IPs vazia para permitir qualquer origem ou inclua os IPs dos dispositivos/rede que precisam acessar os arquivos.

### 4. Enrollment seguro (secure.enrollment) e assinatura

Com **secure.enrollment** ativo, o launcher envia o header **X-Request-Signature** ao baixar arquivos (incluindo a imagem de fundo). Se o servidor rejeitar a requisição (assinatura inválida), a imagem não aparece.

**O que fazer:** Em cenários normais o app já envia a assinatura. Verifique se o **hash.secret** é o mesmo usado no provisionamento e se não há proxy/firewall alterando a URL ou os headers.

### 5. Formato e tamanho da imagem

O launcher não impõe formato na configuração; o Picasso no Android aceita JPEG, PNG, WebP etc. Imagens muito grandes (ex.: vários MB ou resolução altíssima) podem:

- Demorar para carregar ou dar timeout
- Causar uso excessivo de memória em aparelhos mais fracos

O código já usa `.fit().centerCrop()` para reduzir risco de crash, mas imagens pesadas ainda podem falhar.

## Formato e tamanho recomendados

| Item        | Recomendação |
|------------|------------------|
| **Formato** | JPEG ou PNG |
| **Tamanho em arquivo** | Até ~2 MB |
| **Resolução** | Até 1920×1080 (Full HD) é suficiente para tela de fundo |
| **Proporção** | Qualquer; o launcher usa `centerCrop`, então a imagem é ajustada à tela |

Dica: redimensione ou comprima a imagem no computador antes do upload para garantir carregamento rápido e estável em todos os dispositivos.

## Checklist rápido

- [ ] **base.url** do servidor é a URL pública acessível pelo dispositivo (ex.: `https://mdm.mikitos.com.br`).
- [ ] Após alterar a URL da imagem, a **configuração foi salva** no painel.
- [ ] O **dispositivo sincronizou** a configuração (sync automático ou push "Requerer configuração").
- [ ] Imagem em **JPEG ou PNG**, até ~2 MB e até 1920×1080.
- [ ] Se usar **device.allowed.address**, o IP do dispositivo (ou rede) está permitido.
- [ ] Com **secure.enrollment**, **hash.secret** está correto e a requisição do app não é alterada no caminho.

Se após conferir todos os itens a imagem ainda não aparecer, verifique no dispositivo (logcat) se há erros de rede ou de carregamento do Picasso ao abrir o launcher.
