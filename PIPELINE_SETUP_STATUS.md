# Status da Configuração do Pipeline por Estágios

Data: 2026-09-04

## ✅ Concluído

### 1. **Estrutura de Pipeline no GitHub Actions**
- ✅ Criado novo workflow: `.github/workflows/maven-ci-staged.yml`
- ✅ 6 estágios sequenciais implementados:
  1. Compilação (JDK 21 e 23)
  2. Testes Unitários
  3. Testes de Repositório
  4. Testes de Integração
  5. Testes de Segurança
  6. Testes E2E + Coverage & Package

### 2. **Perfis Maven**
- ✅ Criados 7 perfis no `pom.xml`:
  - `unit` - testes unitários
  - `repository` - testes de repositório
  - `integration` - testes de integração
  - `security` - testes de segurança
  - `e2e` - testes end-to-end
  - `all-integration` - todos os testes de integração

### 3. **Documentação**
- ✅ `PIPELINE_STAGES.md` criado com instruções completas

### 4. **Correções de POM**
- ✅ Estrutura de `<profiles>` corrigida (estava dentro de `<build>`)

## ⚠️ Problemas Pendentes

### Testes de Repositório Falhando
Os testes `*RepositoryIT.java` estão com erros causados por:

1. **Conflito de Transações Hibernate**
   - `StaleObjectStateException` quando Flyway cria dados
   - Fixtures com IDs específicos causam conflito com dados migrados

2. **Contexto Spring em DataJpaTest**
   - Flyway está rodando durante inicialização dos testes
   - `@DataJpaTest` com `ddl-auto=create-drop` cria schema mas dados Flyway persistem

3. **Testes Unitários sem Problema**
   - Testes simples (Service tests) rodando OK
   - Problema isolado em testes de banco de dados

## 🔧 Soluções Recomendadas

### **Opção 1: Desabilitar Flyway em Testes (Recomendada)**
```properties
# No @TestPropertySource
spring.flyway.enabled=false  // Desabilitar completamente
```

**Pros:** Testes isolados, sem dados de produção
**Contras:** Precisa garantir que as migrations são válidas em outro contexto

### **Opção 2: Usar TestContainers com PostgreSQL Real**
```java
@Testcontainers
@DataJpaTest
class CatalogStatsRepositoryIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>();
    // Testes rodeiam com banco real
}
```

**Pros:** Testa com banco de verdade
**Contras:** Mais lento, precisa Docker rodando

### **Opção 3: Refatorar Fixtures para Não Usar IDs**
```java
// Atualmente
Motorcycle m = motorcycle(1L, "Yamaha", "MT-09", 889);

// Novo jeito
Motorcycle m = motorcycleWithoutId("Yamaha", "MT-09", 889);
// Deixar banco gerar IDs automaticamente
```

**Pros:** Evita conflito de IDs
**Contras:** Pode quebrar código que depende de IDs específicos

## 📊 Próximas Ações Sugeridas

1. **Curto Prazo (Agora)**
   - [ ] Escolher uma das 3 soluções acima
   - [ ] Implementar a solução escolhida
   - [ ] Testar `mvn verify -Prepository`

2. **Médio Prazo**
   - [ ] Documentar a estratégia de testes escolhida
   - [ ] Atualizar CI/CD se necessário
   - [ ] Testar pipeline completo no GitHub Actions

3. **Longo Prazo**
   - [ ] Adicionar testes E2E com Selenium/Cucumber
   - [ ] Integrar com SonarQube (já existe workflow separado)
   - [ ] Considerardashboard de testes

## 🚀 Como Testar o Pipeline Atual

```bash
# Compilação
mvn clean compile

# Todos os testes
mvn verify

# Apenas testes unitários  
mvn test -Punit

# Repositório (em desenvolvimento)
mvn verify -Prepository

# Integração
mvn verify -Pintegration

# Segurança
mvn verify -Psecurity
```

## 📝 Notas

- Os perfis estão configurados para usar Maven surefire/failsafe com includes/excludes
- O workflow GitHub Actions usa esses perfis para cada estágio
- A documentação está em `PIPELINE_STAGES.md`
- Todos os testes unitários estão passando com a configuração atual

## ⏭️ Recomendação Final

Sugiro implementar a **Opção 1** (Desabilitar Flyway) por ser:
- Mais simples de implementar
- Testes mais rápidos (sem Testcontainers)
- Isolamento real entre testes

Se a Opção 1 não funcionar, tentar a **Opção 3** (refatorar fixtures).

---

**Próximo Passo:** Qual solução você quer implementar?
