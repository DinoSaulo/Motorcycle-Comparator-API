# Auditoria de Segurança — Motorcycle Comparison API

**Data:** 2026-08-27
**Âmbito:** código-fonte em `src/main/java`, configuração em `src/main/resources/application.yml`, migrações Flyway em `src/main/resources/db/migration`, e o comportamento observável através da suite de testes descrita em `src/test/java/com/motorcycle/comparison/security/`.
**Metodologia:** revisão manual de código orientada pela OWASP Testing Guide / OWASP Top 10:2021 / OWASP API Security Top 10:2023, com verificação empírica de cada hipótese através de testes JUnit 5 + MockMvc + Mockito executados contra o código tal como está hoje (`mvn test`).
**Stack:** Java 23, Spring Boot 3.4.1, Spring Security (stateless, JWT via JJWT 0.12.6), PostgreSQL 16 / H2 em testes, Flyway.

**Estado deste documento:** revisão 2. A primeira passagem levantou 8 achados que exigiam alteração de código; **7 estão corrigidos e cobertos por teste de regressão**, 1 permanece em aberto por ser uma decisão de processo. Uma segunda passagem acrescentou 3 achados novos, 2 corrigidos e 1 em aberto. Cada achado abaixo indica explicitamente o seu **estado** e o teste que hoje o fixa.

---

## Sumário executivo

| # | Achado | Severidade | Estado |
|---|--------|-----------|--------|
| 1 | Perfil `dev` como *default* silencioso, com segredos comprometidos embutidos | **Alto** | ✅ Corrigido — `DevDefaultsGuard` |
| 2 | `Content-Security-Policy` e `Referrer-Policy` ausentes | Médio | ✅ Corrigido |
| 3 | `imageUrl` sem validação de esquema/forma | Médio | ✅ Corrigido |
| 4 | `IllegalArgumentException` reenviada ao cliente sem lista de permissões; eco sem limite de tamanho | Baixo–Médio | ✅ Corrigido — `DomainValidationException` |
| 5 | `HEAD` no catálogo público devolvia 401 em vez de 200 | Baixo | ✅ Corrigido |
| 6 | Documentação OpenAPI sempre pública, mesmo em `prod` | Baixo | ✅ Corrigido |
| 7 | `ids` do endpoint de comparação sem limite antes da deduplicação | Baixo | ✅ Corrigido |
| 8 | Dependências desatualizadas (Spring Boot 3.4.1, dez/2024) | Informativo | ⚠️ **Em aberto** — decisão de processo |
| 9 | *(revisão 2)* `X-Forwarded-*` ignorados: HSTS nunca emitido, `Location` degrada para `http` | Médio | ✅ Corrigido |
| 10 | *(revisão 2)* Detalhes do `/actuator/health` visíveis a qualquer autenticado, não só a admins | Baixo | ✅ Corrigido |
| 11 | *(revisão 2)* Cabeçalho `Accept` malformado devolve 500 com corpo vazio | Baixo | ⚠️ **Em aberto** |
| 12 | Injeção SQL/JPQL via `q`, `brand`, `sort`, `ids` | — | ✅ Já defendido, com teste |
| 13 | Path traversal no endpoint de imagens | — | ✅ Já defendido, com teste |
| 14 | Forjadura de JWT (alg:none, chave estranha, claims alterados, expirado, issuer errado, cabeçalhos ambíguos) | — | ✅ Já defendido, com teste |
| 15 | Matriz de autorização (papel × método × recurso, incl. actuator e verbos não mapeados) | — | ✅ Já defendido, com teste |
| 16 | Validação de magic-bytes/polyglot no upload de imagens | — | ✅ Já defendido, com teste |
| 17 | Ausência de fuga de stack traces / nomes de classes / SQL / caminhos nas respostas de erro | — | ✅ Já defendido, com teste |
| 18 | `prod` sem `JWT_SECRET`/`ADMIN_PASSWORD` falha ao arrancar; segredo de `dev` não vaza | — | ✅ Já defendido, com teste |
| 19 | Utilizadores em memória, tokens não revogáveis, sem rate limiting, imagens em disco local | — | 📋 Fronteira deliberada, documentada no README |

---

## Achados corrigidos, com o teste que os fixa

### 1. Perfil `dev` como *default* silencioso, com segredos comprometidos embutidos

- **Severidade:** Alto · **Referência:** OWASP A05/A07; CWE-798
- **Cenário:** o guard "sem *fallback*" (`${JWT_SECRET}`, `${ADMIN_PASSWORD}`, sem valor por omissão) só protegia quem **já** tinha ativado explicitamente o perfil `prod`. Um operador que esquecesse `SPRING_PROFILES_ACTIVE` arrancava com sucesso no perfil `dev`, porque `spring.profiles.default: dev` intervém sempre que nenhum perfil está ativo — a servir com o segredo JWT público neste repositório e as palavras-passe `admin123`/`editor123`.
- **Correção:** `config/DevDefaultsGuard.java` — um `@PostConstruct` que rejeita o arranque quando `environment.getActiveProfiles()` está vazio, salvo opt-in explícito via `app.security.allow-dev-defaults` (`ALLOW_DEV_DEFAULTS`). `spring.profiles.default: dev` nunca popula os perfis *ativos*, pelo que este é o único ponto capaz de distinguir "esqueci-me de definir" de "estou deliberadamente em dev local". O `spring-boot-maven-plugin` ativa `dev` explicitamente, para que um `mvn spring-boot:run` local continue a funcionar.
- **Teste:** `SecretsAndProfileTest` — sem perfil ativo e sem opt-in falha a arrancar; com opt-in arranca; qualquer perfil explícito passa.

### 2. `Content-Security-Policy` e `Referrer-Policy` ausentes

- **Severidade:** Médio · **Referência:** OWASP A05; ASVS 14.4
- **Correção:** `SecurityConfig.securityFilterChain` passou a emitir uma CSP restritiva e `Referrer-Policy: no-referrer`. A política é `'self'` e não `'none'` porque o Swagger UI é servido da mesma origem; `style-src` inclui `'unsafe-inline'` pela CSS que o bundle injeta em runtime — uma concessão deliberada e estreita.
- **Teste:** `SecurityHeadersTest` — presença da CSP e do `Referrer-Policy`, e o Swagger UI continua servível. **Tripwire:** `cspScriptSrcConcessionStopsAtStyleSrc` isola o valor da diretiva `script-src` (em vez de procurar na string toda, que daria falso-negativo por causa do `style-src`) e falha se alguém alguma vez lá puser `'unsafe-inline'`/`'unsafe-eval'`, ou remover `object-src`/`base-uri`/`frame-ancestors`.

### 3. `imageUrl` sem validação de esquema ou forma

- **Severidade:** Médio · **Referência:** OWASP API8; CWE-918 (SSRF futuro), CWE-79
- **Cenário:** o valor só é aceite no `POST`, o que já exige `ROLE_ADMIN` — não era um vetor anónimo. Mas uma conta de admin comprometida (o alvo mais comum em incidentes reais) podia gravar `javascript:alert(1)`, um URL para `169.254.169.254`, ou um URL de phishing, devolvido tal e qual a qualquer leitor anónimo.
- **Correção:** `@Pattern` em `CreateMotorcycleRequest.imageUrl`, admitindo só as duas formas que o serviço honra: um URL `http(s)://` externo, ou um caminho `/api/v1/images/motorcycles/...` emitido por esta própria API.
- **Teste:** `MotorcycleControllerTest.malformedImageUrlSchemeBecomesBadRequest` — `javascript:alert(1)` é 400 nomeando o campo, e o serviço nunca é chamado.

### 4. Mensagens de exceção reenviadas ao cliente sem lista de permissões

- **Severidade:** Baixo–Médio · **Referência:** OWASP A05; CWE-209
- **Cenário:** `handleIllegalArgument` devolvia `ex.getMessage()` para **qualquer** `IllegalArgumentException` que chegasse ao `@RestControllerAdvice`. Todas as que efetivamente lá chegavam tinham mensagens seguras, mas não existia nenhuma barreira estrutural: uma nova dependência, ou uma alteração noutro ponto do código, que lançasse `IllegalArgumentException` com um caminho de sistema de ficheiros ou um detalhe de configuração seria reenviada automaticamente. Além disso, `handleTypeMismatch` e `messageOf(FieldError)` ecoavam o valor do cliente sem limite de tamanho.
- **Correção:** novo `exception/DomainValidationException` — deliberadamente subtipo de `IllegalArgumentException`, porque a resolução de `@ExceptionHandler` despacha pelo tipo mais específico, o que mantém os dois totalmente distinguidos na fronteira do *handler* sem partir os testes de serviço que assertam `IllegalArgumentException`. Os pontos de lançamento deliberados foram convertidos com o texto de mensagem inalterado (`MotorcycleService.validateSort`, `ComparisonService.validateSize`, as quatro rejeições de `FileStorageServiceImpl`, o teto `MAX_COMPARE_IDS` do controller). O `handleIllegalArgument` genérico regista a mensagem real em WARN e devolve `"The request contains an invalid value"`. A superfície de mensagens expostas passou a ser uma decisão explícita de tipo, não um comportamento acidental. Um `truncate(Object)` a 200 caracteres fecha o eco sem limite em ambos os pontos.
- **Teste:** `GlobalExceptionHandlerTest.handlesDomainValidation` / `handlesIllegalArgument` — o par é que é o ponto: um é reenviado verbatim, o outro nunca. `ErrorDisclosureTest.unreviewedIllegalArgumentIsGenericThroughRealDispatch` prova a mesma fronteira pelo caminho de dispatch real, através de um controller de apoio (`@Import`, exclusivo do teste) que representa "uma biblioteca no *call path*" — porque, empiricamente, já nenhum caminho de produção alcança aquele *handler*. `ErrorDisclosureTest.BoundedEcho` cobre a truncatura.

### 5. `HEAD` no catálogo público devolvia 401 em vez de 200

- **Severidade:** Baixo (falha fechada) · **Referência:** ASVS 4.3; CWE-284
- **Cenário:** ao contrário do `@GetMapping` do Spring MVC, o `requestMatchers(HttpMethod.GET, ...)` do Spring Security **não** inclui `HEAD` implicitamente, pelo que um `HEAD` caía em `anyRequest().authenticated()`. Não era explorável, mas quebrava CDNs e *health-checkers*.
- **Correção:** `.requestMatchers(HttpMethod.HEAD, PUBLIC_GET_PATHS).permitAll()`.
- **Teste:** `AuthorizationMatrixTest.HeadOnPublicPaths`.

### 6. Documentação OpenAPI sempre pública, mesmo em `prod`

- **Severidade:** Baixo · **Referência:** OWASP API8
- **Cenário:** a documentação expõe a forma exata dos endpoints de escrita, as regras de validação e os limites de `additionalSpecs` — reconhecimento que a maioria dos deployments prefere não publicar, independentemente de as leituras serem públicas.
- **Correção:** `springdoc.api-docs.enabled: false` e `swagger-ui.enabled: false` no documento `on-profile: prod`. `PUBLIC_PATHS` fica como está — sem springdoc, aqueles caminhos deixam de ter handler e caem num 404 limpo.
- **Teste:** `SecretsAndProfileTest.OpenApiExposureByProfile`. **Limitação declarada:** o teste asserta a *resolução da propriedade* (`prod` resolve para `false`, `dev` não), não um servidor `prod` a devolver 404 — arrancar um contexto web `prod` completo é impraticável, porque o seu datasource/Flyway é SQL exclusivo de PostgreSQL. O javadoc do teste diz isto explicitamente, para não reclamar uma garantia mais forte do que a que dá.

### 7. `ids` do endpoint de comparação sem limite antes da deduplicação

- **Severidade:** Baixo · **Referência:** OWASP API4
- **Cenário:** um chamador anónimo podia enviar `ids=1,1,1,...` arbitrariamente longo, forçando o *binding* para `List<Long>` e a construção de um `LinkedHashSet` **antes** de `validateSize` verificar o limite. Trabalho sem custo para o atacante.
- **Correção:** `MAX_COMPARE_IDS = 100` no controller, verificado antes da deduplicação. Deliberadamente generoso — não é a regra de negócio (essa é `app.comparison.max-items`, 4), é apenas o guarda de recursos.
- **Teste:** `MotorcycleControllerTest.oversizedIdsListBecomesBadRequestBeforeReachingTheService`.

### 9. `X-Forwarded-*` ignorados atrás de um proxy TLS *(revisão 2)*

- **Severidade:** Médio · **Referência:** OWASP A05; CWE-319 (Cleartext Transmission)
- **Cenário:** `server.forward-headers-strategy` não estava definido. Esta API destina-se a correr atrás de um proxy/ingress que termina o TLS, e sem esta definição o `request.isSecure()` é **sempre falso** para um pedido encaminhado. Duas consequências concretas: o *writer* de HSTS do Spring Security só emite `Strict-Transport-Security` num pedido seguro, pelo que o cabeçalho **nunca era enviado** — um browser que uma vez alcançasse a API por `http` nunca era instruído a parar de o fazer; e o `Location` devolvido por `POST /api/v1/motorcycles` era construído a partir do esquema/host internos, `http://host-interno:8080/...`, **degradando ativamente** o pedido seguinte do cliente.
- **Correção:** `server.forward-headers-strategy: ${FORWARD_HEADERS_STRATEGY:framework}`, sobreponível para `none` num deployment que **não** esteja atrás de um proxy de confiança — confiar em `X-Forwarded-*` quando nada os remove na fronteira é, em si, um vetor de *spoofing*, e o comentário no `application.yml` di-lo.
- **Teste:** `SecurityHeadersTest.ForwardedHeaderHandling` — o HSTS aparece com `X-Forwarded-Proto: https` e **não** aparece sem ele (a assimetria é que é o achado, por isso ambas as metades são assertadas), e o `Location` segue o esquema e o host encaminhados. A propriedade é fornecida explicitamente no teste porque `src/test/resources/application.yml` nunca define esta chave.

### 10. Detalhes do `/actuator/health` visíveis a qualquer autenticado *(revisão 2)*

- **Severidade:** Baixo · **Referência:** OWASP API8; CWE-200
- **Cenário:** `management.endpoint.health.show-details: when-authorized` sem `management.endpoint.health.roles` significa *qualquer principal autenticado*, incluindo a conta não-admin `editor`. E `/actuator/health` é o **único** endpoint do actuator que `SecurityConfig.PUBLIC_PATHS` deixa um não-admin alcançar — todos os outros exigem `ROLE_ADMIN`. O `editor` conseguia assim ler conetividade à base de dados, espaço em disco e detalhe ao nível dos componentes. O documento `prod` já sobrepunha `show-details: never`, pelo que a exposição estava limitada ao documento por omissão — que é precisamente o que um deployment sem perfil, ou tipo *staging*, herda.
- **Correção:** `roles: ADMIN` sob `management.endpoint.health`.
- **Teste:** `AuthorizationMatrixTest.HealthDetailVisibility` — anónimo e **editor** veem só o `status`; admin vê os `components`. O caso do editor é o que codifica a propriedade nova.

---

## Achados em aberto

### 8. Dependências desatualizadas

- **Severidade:** Informativo · **Referência:** OWASP A06
- **Localização:** `pom.xml` (`spring-boot-starter-parent` 3.4.1, de dezembro de 2024), `springdoc-openapi` 2.7.0, `jjwt` 0.12.6.
- **Estado:** deliberadamente não corrigido nesta revisão. Nenhuma CVE específica foi identificada e verificada contra as versões atuais — este ponto é a constatação de que, a partir de agosto de 2026, estas dependências estão bem atrás da linha de *patches*, o que significa que qualquer CVE divulgada desde então permanece por corrigir até uma atualização deliberada.
- **Recomendação:** um processo recorrente (Dependabot/Renovate, `mvn versions:display-dependency-updates`, ou um scan OWASP Dependency-Check/Snyk no CI) resolve isto de forma durável; um *bump* pontual não. Este documento não substitui esse processo.

### 11. Cabeçalho `Accept` malformado devolve 500 com corpo vazio *(revisão 2)*

- **Severidade:** Baixo (robustez e consistência, não fuga de informação)
- **Localização:** `exception/GlobalExceptionHandler.handleUnexpected`
- **Cenário:** `GET /api/v1/motorcycles` com `Accept: @@@not-a-media-type@@@` lança `HttpMediaTypeNotAcceptableException` — não durante o dispatch original, mas enquanto o `ResponseEntity<ApiError>` devolvido pelo próprio `handleUnexpected` está a ser escrito, porque a negociação de conteúdo da *resposta de erro* falha pelo mesmo cabeçalho malformado. Essa segunda falha não é apanhada, e o resultado é um 500 nu, sem corpo nenhum — nem sequer no formato uniforme `ApiError` que o resto da API garante.
- **Impacto:** não vaza informação (o corpo está vazio, precisamente). Quebra a garantia de formato uniforme e transforma um erro de cliente (deveria ser 406) num erro de servidor.
- **Recomendação:** um `@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)` que devolva 406 com corpo `ApiError` fixado em `MediaType.APPLICATION_JSON` (via `.contentType(...)` explícito), contornando a re-negociação. Nenhum teste foi escrito, por ficar fora do âmbito desta revisão.

---

## Fronteiras deliberadas da iteração 1

Já documentadas em `README.md` §"Known limitations" — não são reportadas aqui como descobertas:

1. **Utilizadores em memória** (`application.yml`), substituíveis apenas trocando o *bean* `UserDetailsService`.
2. **Tokens não revogáveis** antes de expirarem — o TTL curto é a mitigação assumida.
3. **Dados semente indicativos**, não uma fonte de especificação autoritativa.
4. **Sem *rate limiting*** nos endpoints públicos, incluindo o login e a distribuição de imagens. Transformado deliberadamente num teste-*tripwire* (`TokenForgeryTest.loginHasNoRateLimiting`: dez tentativas erradas, todas 401, nenhuma 429) para que a introdução futura de um limitador seja uma alteração visível a este teste, não uma regressão silenciosa.
5. **Imagens no sistema de ficheiros local**, sob `IMAGE_STORAGE_LOCATION`.
6. ***Optimistic locking* parcial** — cobre apenas escritas genuinamente concorrentes em `motorcycles`.

---

## Suite de segurança

Em `src/test/java/com/motorcycle/comparison/security/` (agrupamento deliberado, documentado em `package-info.java`):

| Classe | O que fixa |
|--------|-----------|
| `AuthorizationMatrixTest` | Matriz papel × método × recurso; verbo não mapeado (`PATCH`); exposição do actuator; `HEAD` nos caminhos públicos; **`HealthDetailVisibility`** — detalhes do health só para admin |
| `TokenForgeryTest` | `alg:none`, chave estranha, *claims* alterados, expirado, *issuer* errado, esquemas `Bearer`/`bearer`/`Basic`, cabeçalhos duplicados, valor enorme, ausência de rate limiting no login |
| `InjectionAndTraversalTest` | Injeção SQL/JPQL via `q`/`brand`/`sort`/`ids`, escape de metacaracteres `LIKE`, path traversal na distribuição de imagens |
| `UploadSecurityTest` | Desacordo *content-type*/magic-bytes, SVG e HTML disfarçados de `.jpg`, JPEG *polyglot* com `<script>`, limite de tamanho, parte em falta, nome armazenado sempre um UUID novo |
| `ErrorDisclosureTest` | Ausência de stack traces, nomes de pacote, SQL e caminhos em 400/401/403/404/405/415; **`IllegalArgumentException` não revista é genérica pelo dispatch real**; **`BoundedEcho`** — eco truncado a 200 caracteres |
| `SecurityHeadersTest` | `nosniff`, `X-Frame-Options`, `Cache-Control: no-store` no login, override de cache nas imagens; **tripwire da CSP**; **`ForwardedHeaderHandling`** — HSTS e `Location` atrás de proxy TLS |
| `SecretsAndProfileTest` | `prod` sem segredos falha a arrancar; perfil não relacionado não herda o segredo de `dev`; `DevDefaultsGuard`; **`OpenApiExposureByProfile`** — springdoc desativado em `prod` |

Fora deste pacote, com relevância de segurança: `GlobalExceptionHandlerTest` (par `handleDomainValidation` / `handleIllegalArgument`), `MotorcycleControllerTest` (esquema do `imageUrl`, teto de `ids`), `JwtServiceTest`, `JwtAuthenticationFilterTest`, `FileStorageServiceImplTest`, `MotorcycleApiSecurityTest` (preflight CORS de origem permitida e não permitida).

### Nota técnica de implementação

Duas chaves — `server.forward-headers-strategy` e o bloco `management.endpoint.health` — estão **ausentes** de `src/test/resources/application.yml`, pelo que caem no valor por omissão do Spring Boot e não no do `application.yml` real. Os testes correspondentes fornecem-nas via `@TestPropertySource`, e dizem-no em comentário: sem isso não provariam nada.

Além disso, uma classe `@Nested` do JUnit não tem herança Java da classe que a envolve, pelo que o Spring só reinjeta campos **declarados na própria classe aninhada**. Reutilizar o `mockMvc` exterior faria a classe aninhada exercitar silenciosamente o contexto exterior, sem a sobreposição de propriedades — tornando o `@TestPropertySource` num *no-op*. As duas classes aninhadas afetadas sombreiam o campo localmente, com o motivo em comentário.

## Resultado de `mvn test`

```
Tests run: 378, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

(356 na revisão 1 → 378 nesta.)
