# Como verificar se a migration `pending_factory_reset` rodou

A migration que cria a coluna e as permissões de device reset está no Liquibase:

- **Ficheiro:** `server/src/main/resources/liquibase/db.changelog.xml`
- **changeSet id:** `06.03.26-10:00`
- **Alterações:** coluna `devices.pending_factory_reset` + permissão `plugin_devicereset_access`

---

## 1. Verificar pela coluna na base de dados (recomendado)

Se a coluna existir na tabela `devices`, a migration foi aplicada.

### PostgreSQL

```sql
-- Ver se a coluna existe
SELECT column_name, data_type, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'devices'
  AND column_name = 'pending_factory_reset';
```

- **Resultado com 1 linha:** a migration rodou.
- **Resultado vazio:** a coluna não existe; a migration ainda não foi aplicada.

---

## 2. Verificar pela tabela do Liquibase

O Liquibase regista cada changeSet executado. A tabela padrão no PostgreSQL é `databasechangelog`.

```sql
-- Ver se este changeSet foi executado
SELECT id, author, filename, dateexecuted, description
FROM databasechangelog
WHERE id = '06.03.26-10:00';
```

- **Resultado com 1 linha:** este changeSet já foi executado.
- **Resultado vazio:** este changeSet ainda não foi executado.

(O nome da tabela pode ser `DATABASECHANGELOG` noutros motores; no PostgreSQL costuma ficar em minúsculas.)

---

## 3. Verificar a permissão (opcional)

A mesma migration insere a permissão `plugin_devicereset_access`:

```sql
SELECT id, name, description
FROM permissions
WHERE name = 'plugin_devicereset_access';
```

- **1 linha:** a parte da permissão da migration foi aplicada (e, em princípio, a coluna também).

---

## 4. Resumo rápido

| Método              | Comando / verificação |
|---------------------|------------------------|
| Coluna na tabela    | `information_schema.columns` em `devices` com `column_name = 'pending_factory_reset'` |
| Registo Liquibase   | `SELECT ... FROM databasechangelog WHERE id = '06.03.26-10:00'` |
| Permissão           | `SELECT ... FROM permissions WHERE name = 'plugin_devicereset_access'` |

Se a coluna existir **ou** o changeSet `06.03.26-10:00` aparecer em `databasechangelog`, a migration do `pending_factory_reset` rodou.
