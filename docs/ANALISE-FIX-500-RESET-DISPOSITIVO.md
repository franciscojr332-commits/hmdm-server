# Análise profunda: correção do 500 em "Restaurar dispositivo de fábrica"

**Data:** 6 de março de 2026  
**Problema:** `PUT .../rest/plugins/devicereset/private/reset?deviceId=` → 500 Internal Server Error

**Atualização:** O 500 persistia porque o frontend em produção (ou em cache) continuava a enviar `?deviceId=` (URL antiga). Foi feita correção **no backend** para nunca devolver 500 por parâmetro inválido: ambos os endpoints passam a aceitar `deviceId` como **String**, fazer o parse manual e devolver **HTTP 400** quando for vazio ou inválido.

---

## 1. Origem do 500

### 1.1 O que o cliente enviava

- URL: `PUT https://mdm.mikitos.com.br/rest/plugins/devicereset/private/reset?deviceId=`
- Ou seja: endpoint **legacy** (`/private/reset` **sem** path param) com **query param `deviceId` vazio**.

### 1.2 Onde o 500 pode vir no backend

Há **duas** origens possíveis:

| Hipótese | Onde | Motivo |
|----------|------|--------|
| **A** | **Binding JAX-RS** | `@QueryParam("deviceId") Integer queryDeviceId`: se vier `?deviceId=` (vazio) ou `?deviceId=undefined`, a conversão String → Integer falha. Jersey pode responder **500** (ou 400) por falha de conversão **antes** de chamar o resource. |
| **B** | **Dentro do resource** | Se o binding passar (ex.: `null`), `doRequestDeviceReset(null)` devolve `Response.ERROR("error.bad.request")`. O projeto devolve um POJO `Response`; em muitos setups JAX-RS isso resulta em **HTTP 200** com body `{ status: "ERROR", message: "..." }`, não 500. |

Conclusão: o 500 observado é **muito provável** da **falha de conversão de parâmetro** (hipótese A), ou seja, o request **nem chega** à lógica de negócio com um `Integer` válido.

---

## 2. Por que a correção resolve

A correção atua em **três** frentes.

### 2.1 Guard no frontend (principal)

- **Antes:** Sempre chamava a API com `{ deviceId: device.id }`. Se `device.id` fosse `undefined`/`null`, a URL ficava `?deviceId=` ou `?deviceId=undefined` → backend tentava converter → 500.
- **Depois:** Só chama a API se existir um `device` e um `id` considerado válido:
  - `id !== 0` (aceita id numérico 0)
  - `id != null` e `id !== ''`
  - `!isNaN(Number(id))` (evita `"undefined"`, `"abc"`, etc.)
- **Efeito:** Com **deviceId vazio ou inválido a requisição não é enviada**. O cenário que levava ao 500 (query param vazio/inválido) deixa de ocorrer.

### 2.2 Uso do endpoint por path (redundância saudável)

- **Antes:** `rest/plugins/devicereset/private/reset?deviceId=:deviceId` → dependia 100% do query param.
- **Depois:** `rest/plugins/devicereset/private/reset/:deviceId` → usa o endpoint **por path** já existente no backend.
- **Efeito:** Quando a chamada é feita, o `deviceId` vai no **path** (ex.: `.../reset/123`). O backend usa `@PathParam("deviceId") Integer deviceId`, que recebe um segmento de path já “quebrado” pela rota; não há query param vazio a converter. Assim, mesmo que no futuro alguém quebre a guard, o uso do path reduz risco de 500 por **query** mal formado.

### 2.3 Mensagem ao utilizador quando dados são inválidos

- Se `device` ou `id` forem inválidos, em vez de chamar a API e receber 500, o utilizador vê a mensagem de erro já existente (`error.device.reset`) e a função termina sem fazer o PUT.

---

## 3. Fluxo após a correção

```
Utilizador clica "Restaurar dispositivo de fábrica"
  → requestDeviceReset(device) é chamado
  → Guard: id = device.id
           se id !== 0 && (id == null || id === '' || isNaN(Number(id))) → alert + return (não há PUT)
  → Caso contrário: deviceService.requestDeviceReset({ deviceId: device.id })
  → Angular $resource monta: PUT .../rest/plugins/devicereset/private/reset/:deviceId
  → URL final: PUT .../rest/plugins/devicereset/private/reset/123 (ex.)
  → Backend: requestDeviceResetByPath(123) → doRequestDeviceReset(123) → 200 OK
```

- Com **id inválido**: nenhum PUT; sem 500.
- Com **id válido**: PUT no path com número; backend recebe `Integer` e responde 200 (ou erro de negócio mapeado em resposta JSON, não 500 de binding).

---

## 4. Edge cases considerados

| Cenário | Comportamento |
|---------|----------------|
| `device` null/undefined | Guard: `!device` → erro localizado, return. |
| `device.id` null/undefined | Guard: `id == null` → erro localizado, return. |
| `device.id === 0` | Permitido (`id !== 0` é false); chamada com path `.../reset/0` (se o backend aceitar). |
| `device.id === ""` | Guard: `id === ''` → erro localizado, return. |
| `device.id === "undefined"` (string) | Guard: `isNaN(Number("undefined"))` → true → erro localizado, return. |
| `device.id` string numérica (ex.: `"123"`) | `Number("123")` é 123; guard passa; URL `.../reset/123`; backend converte path "123" → 123 sem problema. |
| Cache do browser com URL antiga | Se o utilizador não clicar em “Restaurar” de um dispositivo sem id, não há request. Se ainda assim um request antigo for reenviado (ex.: F5 numa tab antiga), esse request pode continuar a dar 500 até o cache ser limpo; a **ação normal** (clicar no botão na lista) deixa de provocar 500. |

---

## 5. Conclusão

- **Sim, esta correção resolve o problema do 500** no uso normal do botão “Restaurar dispositivo de fábrica”:
  1. A **guard** impede que seja enviado qualquer request com `deviceId` vazio ou não numérico, eliminando a causa mais provável do 500 (falha de conversão do query param).
  2. O uso do endpoint **por path** garante que, quando o request é feito, o `deviceId` chega ao backend como path param, sem depender de query param vazio.
  3. Validação com `Number(id)` e `isNaN` cobre strings inválidas como `"undefined"`, evitando que um bug noutra parte da app provoque `.../reset/undefined` e um 500 no backend.

Recomendação: fazer deploy da alteração no frontend (e limpar cache do browser se necessário) e voltar a testar o botão; o 500 neste fluxo deve deixar de ocorrer.

---

## 6. Correção no backend (quando o 500 persistiu)

**Causa:** O frontend em produção/cache ainda chamava `PUT .../private/reset?deviceId=` (sem valor). O JAX-RS convertia `""` para `Integer` e lançava exceção **antes** de executar o resource → 500.

**Alterações em `DeviceResetResource.java`:**

1. **PUT /private/reset (legacy)**  
   - `@QueryParam("deviceId") Integer` → `@QueryParam("deviceId") String queryDeviceIdStr`  
   - Parse manual com `Integer.valueOf(queryDeviceIdStr.trim())` dentro de try/catch.  
   - Se vazio, null ou não numérico: `javax.ws.rs.core.Response.status(400).entity(Response.ERROR("error.bad.request")).build()`.  
   - Assim não há exceção de conversão e o cliente recebe **400** em vez de 500.

2. **PUT /private/reset/{deviceId} (path)**  
   - `@PathParam("deviceId") Integer` → `@PathParam("deviceId") String deviceIdStr`  
   - Parse manual; se inválido (ex.: "undefined"): resposta **400** com o mesmo body.  
   - Evita 500 quando o path contém valor não numérico.

**Resultado:** Para `?deviceId=` ou path inválido, o servidor responde **400 Bad Request** com `{"status":"ERROR","message":"error.bad.request"}`. O Angular trata como erro e o callback de erro mostra a mensagem ao utilizador; não há mais 500 por parâmetro inválido.
