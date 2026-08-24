# Postman — Motorcycle Comparator API

Collection para testes manuais da API, gerada a partir dos controllers (`AuthController`,
`MotorcycleController`), dos DTOs de request/response e do `SecurityConfig` /
`application.yml` do projeto — não do `/v3/api-docs` em runtime, para não depender da API
estar de pé no momento da geração.

## Ficheiros

| Ficheiro | O que é |
|---|---|
| `Motorcycle-Comparator-API.postman_collection.json` | A collection (43 pedidos, 5 pastas) |
| `Motorcycle-Comparator-API-Local.postman_environment.json` | Environment "Local" com `baseUrl=http://localhost:8080` |
| `Motorcycle-Comparator-API.zip` | Os dois ficheiros acima, prontos a importar |

## Importar

No Postman: **Import** → arrasta o zip (ou os dois `.json`) → confirma. Depois seleciona o
environment **Motorcycle Comparator API - Local** no seletor no canto superior direito.

## Antes de correr

```bash
mvn spring-boot:run   # perfil dev por omissão: porta 8080, seed de dados carregado
```

Com o perfil `dev`, os utilizadores de teste já vêm com password por omissão
(`admin` / `admin123` e `editor` / `editor123`), definidos como variáveis no environment.
Se estiveres a apontar a outro ambiente, atualiza `baseUrl`, `adminPassword` e
`editorPassword` no environment antes de correr qualquer pedido.

## Estrutura da collection

1. **Auth** — login público. Corre **Login - Admin** (e **Login - Editor**, para os
   cenários 403) primeiro: os tokens ficam guardados automaticamente em variáveis da
   collection (`adminToken` / `editorToken`) e são reutilizados nas pastas seguintes via
   Bearer Token — não precisas de copiar/colar nada.
2. **Catalog (public reads)** — pesquisa, filtros, listagem de marcas, obter por
   id/slug e comparação lado a lado (`/compare`). Sem autenticação.
3. **Motorcycles - Admin writes** — CRUD protegido por `ROLE_ADMIN`. Corre pela ordem em
   que os pedidos aparecem: **Create** guarda o id criado em `createdMotorcycleId`, que o
   **Update** e o **Delete** reutilizam; corre o **Delete** por último.
4. **Actuator** — `health`/`info` são públicos; `metrics` (e todo o resto do actuator)
   exige `ROLE_ADMIN`.
5. **API Docs** — `/v3/api-docs` e `/swagger-ui.html`, para confirmares a fonte de verdade
   sempre que a API mudar.

Cada pedido tem uma descrição com o comportamento esperado e testes automáticos (aba
*Test Results* depois de enviar) que confirmam o status code e, nos erros, a forma
uniforme `ApiError` (`timestamp`, `status`, `error`, `message`, `path`, `violations`).

## Cenários de erro cobertos

Além dos caminhos felizes, a collection inclui casos negativos para cada regra de negócio
visível no código: credenciais inválidas (401), corpo inválido (`@Valid`, 400), id/slug
desconhecido (404), pedido sem token (401), token sem `ROLE_ADMIN` (403), `compare` com
menos de 2 ou mais de 4 ids, ids duplicados, e um id inexistente na comparação.

## Manter a collection atualizada

Se adicionares ou mudares um endpoint, atualiza este ficheiro à mão — não há geração
automática ligada ao build. Alternativa: reimportar a partir do `/v3/api-docs` (Postman
consegue importar OpenAPI diretamente) e reaplicar as variáveis e os testes acima.
