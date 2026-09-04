# Pipeline de Testes por Estágios

Este documento explica a nova estrutura de pipeline do GitHub Actions com testes organizados em estágios.

## 📋 Visão Geral

O novo workflow `maven-ci-staged.yml` executa testes em 6 estágios sequenciais:

```
Compilação → Unit Tests → Repository Tests → Integration Tests → Security Tests → E2E Tests → Coverage & Package
```

Cada estágio aguarda o sucesso do anterior antes de iniciar (usando `needs` no GitHub Actions).

## 🎯 Estágios Detalhados

### 1️⃣ **Compilação** (`compile`)
- Compila o código-fonte para ambos JDK 21 e 23
- Valida que não há erros de compilação
- **Padrão Maven:** `mvn clean compile`

### 2️⃣ **Testes Unitários** (`unit-tests`)
- Executa testes com sufixo `*Test.java`
- Usa o perfil Maven: `-Punit`
- **Padrão Maven:** `mvn test -Punit`
- **Arquivos testados:** `src/test/java/**/*Test.java`

### 3️⃣ **Testes de Repositório** (`repository-tests`)
- Executa testes de integração com repositórios: `*RepositoryIT.java`
- Usa o perfil Maven: `-Prepository`
- **Padrão Maven:** `mvn verify -Prepository`
- **Arquivos testados:** `src/test/java/**/*RepositoryIT.java`

### 4️⃣ **Testes de Integração** (`integration-tests`)
- Executa testes de controladoras e serviços
- Padrão: `**/controller/*IT.java` e `**/service/*IT.java`
- Exclui testes de segurança e repositório
- Usa o perfil Maven: `-Pintegration`
- **Padrão Maven:** `mvn verify -Pintegration`

### 5️⃣ **Testes de Segurança** (`security-tests`)
- Executa testes com sufixo `*SecurityIT.java`
- Valida autenticação, autorização e controle de acesso
- Usa o perfil Maven: `-Psecurity`
- **Padrão Maven:** `mvn verify -Psecurity`

### 6️⃣ **Testes E2E** (`e2e-tests`)
- Executa testes end-to-end: `*E2EIT.java`
- Usa o perfil Maven: `-Pe2e`
- **Padrão Maven:** `mvn verify -Pe2e`
- ⚠️ Configurado com `continue-on-error: true` pois ainda pode não ter testes E2E implementados

### 🎁 **Cobertura & Empacotamento** (`coverage-and-package`)
- Executa com o perfil `-Pall-integration` (todos os testes de integração)
- Gera relatório de cobertura (JaCoCo)
- Empacota o JAR para deploy
- **Apenas** executa em push para `main` branch

## 🚀 Como Usar Localmente

### Executar cada estágio individualmente:

```bash
# Compilação
mvn clean compile

# Unit Tests
mvn test -Punit

# Repository Tests
mvn verify -Prepository

# Integration Tests
mvn verify -Pintegration

# Security Tests
mvn verify -Psecurity

# E2E Tests
mvn verify -Pe2e

# Todos os testes de integração (Repository + Integration + Security)
mvn verify -Pall-integration
```

### Executar múltiplos estágios de uma vez:

```bash
# Unit + Repository + Integration + Security
mvn verify -Punit,repository,integration,security

# Todos (sem E2E)
mvn verify -Pall-integration

# Todos com E2E
mvn verify -Punit,repository,integration,security,e2e
```

## 📊 Convenção de Nomes de Testes

Para que o pipeline funcione corretamente, organize seus testes com os seguintes padrões:

| Tipo | Sufixo | Caminho | Exemplo |
|------|--------|--------|---------|
| Unitário | `Test` | `src/test/java/**` | `MotorcycleServiceTest.java` |
| Repositório | `RepositoryIT` | `src/test/java/**/repository/` | `CatalogStatsRepositoryIT.java` |
| Integração (Controladora) | `IT` | `src/test/java/**/controller/` | `MotorcycleControllerIT.java` |
| Integração (Serviço) | `IT` | `src/test/java/**/service/` | `MotorcycleServiceIT.java` |
| Segurança | `SecurityIT` | `src/test/java/**` | `AdminSecurityIT.java` |
| E2E | `E2EIT` | `src/test/java/**` | `MotorcycleComparisonE2EIT.java` |

## 🔧 Modificar os Estágios

Se precisar adicionar, remover ou modificar um estágio:

### No pom.xml:
Os perfis Maven estão em `<profiles>` no final do `pom.xml`. Cada perfil controla quais testes executar.

### No workflow:
O arquivo `.github/workflows/maven-ci-staged.yml` contém todos os jobs. Modifique o `needs` para ajustar dependências.

## 📈 Matriz de Versões Java

Todos os estágios executam com **JDK 21 e 23** para garantir compatibilidade:

- JDK 21: LTS (supportado)
- JDK 23: Versão alvo (mais recente)

Se precisar adicionar/remover uma versão, modifique a seção `matrix.java` em cada job.

## ⏱️ Tempo de Execução Esperado

Como os estágios executam sequencialmente, o tempo total é a soma de todos:

- **Compilação:** ~30s
- **Unit Tests:** ~20s
- **Repository Tests:** ~40s
- **Integration Tests:** ~1m
- **Security Tests:** ~30s
- **E2E Tests:** ~2m (quando implementado)
- **Coverage & Package:** ~1m

**Total estimado:** ~5-6 minutos por matriz (2 JDKs)

## ⚠️ Notas Importantes

1. **Continue-on-error para E2E:** O job `e2e-tests` tem `continue-on-error: true` porque os testes E2E ainda podem não estar implementados. Remova esta flag quando adicionar os testes.

2. **Testcontainers:** Os testes que precisam de PostgreSQL real usam Testcontainers. O Docker está disponível no GitHub Actions, então não há configuração especial necessária.

3. **Relatórios de Teste:** Cada estágio publica um relatório de teste separado com prefixo indicando o tipo:
   - "Unit Tests Report"
   - "Repository Tests Report"
   - "Integration Tests Report"
   - "Security Tests Report"
   - "E2E Tests Report"

4. **SonarQube:** O workflow `sonarqube.yml` executa independentemente. Você pode mantê-lo ou integrá-lo com este novo workflow.

## 🆘 Troubleshooting

### Teste não aparece no estágio correto
- Verificar se o sufixo do arquivo segue a convenção
- Verificar o caminho do arquivo (para integration vs service)
- Executar localmente com: `mvn test -P<perfil> -X` para debug

### Perfil não funciona localmente
```bash
# Verificar perfis disponíveis
mvn help:active-profiles

# Verificar configuração específica
mvn help:describe -Dcmd=verify
```

### Erro "No tests were run"
- Verificar que há testes com sufixo correto
- Verificar que as classes herdam de classes base de teste corretas
- Executar: `mvn test -Punit -Dtest=*Test` para verificar discovery

## 📝 Próximos Passos

1. Confirmar que todos os testes são descobertos corretamente em seus respectivos estágios
2. Adicionar testes E2E quando estiver pronto
3. Considerar adicionar mais estágios (Performance tests, Load tests, etc)
4. Integrar relatórios com ferramentas de análise (SonarQube, Code Coverage, etc)

---

**Última atualização:** 2026-09-04
**Versão:** 1.0
