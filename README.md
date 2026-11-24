# Pipeline de CI/CD com GitHub Actions

Pipeline completo de integração e entrega contínua para um serviço Spring Boot de
feature flags. O serviço é pequeno de propósito: ele existe para dar ao pipeline algo
real para compilar, analisar, testar contra um Postgres de verdade, empacotar em
imagem, publicar e versionar. O assunto do projeto é o caminho do commit até o
release.

```
push na main ──► build ──────────────────────────► image ──────────────────────► GHCR
                 Checkstyle, SpotBugs              imagem em camadas             sha-<commit>
                 testes + Testcontainers           smoke test da imagem          main
                 portão de cobertura (JaCoCo)      push só se não for PR
                 jar reproduzível

tag vX.Y.Z ───► release ──► confere tag x versão ──► promove a imagem sha-<commit>
                            (mesmo digest)            para X.Y.Z, X.Y, latest
                                                      jar + sha256 no GitHub Release
```

## Tecnologias

- **Java 21** e **Spring Boot 3.5** (Web, Data JPA, Validation, Actuator)
- **PostgreSQL 16** com **Flyway**
- **GitHub Actions**: `setup-gradle` (cache), `build-push-action` (Buildx com cache
  `type=gha`), `metadata-action`, `login-action`, Dependabot
- **JaCoCo** (portão de cobertura), **Checkstyle**, **SpotBugs**
- **Docker** com imagem em camadas do Spring Boot (`-Djarmode=tools`)
- **GHCR** (GitHub Container Registry) e **GitHub Releases**
- **Testcontainers** nos testes de integração, rodando igual no laptop e no runner

## O serviço

Uma flag tem chave, descrição, estado (ligada/desligada) e percentual de rollout.
A avaliação por usuário é determinística: o bucket é `CRC32(chave:usuario) mod 100`
e o usuário entra se o bucket for menor que o percentual. Três propriedades saem
daí, e são testadas:

- o mesmo usuário sempre recebe a mesma resposta;
- subir de 10% para 20% só **adiciona** usuários, ninguém que tinha a feature perde;
- a chave da flag entra no hash, então duas flags a 10% sorteiam grupos diferentes
  (sobreposição medida de ~1%, não de 10%).

Atualizações usam versão otimista: o cliente manda a versão que leu, e uma cópia
defasada recebe 409 em vez de sobrescrever a alteração de outro.

## O pipeline, decisão por decisão

**Tudo que reprova o build está no Gradle, não no YAML.** Checkstyle, SpotBugs, testes
e o portão de cobertura estão ligados ao `check`. O passo do CI é só `./gradlew
build`, o mesmo comando do desenvolvedor. Se a regra vivesse num passo do workflow,
ela só falharia no CI, e qualquer um poderia desligá-la editando o YAML num PR.

**Portão de cobertura com mínimo de 90% de linhas e 80% de ramos.** O
`lombok.config` marca o código gerado pelo Lombok com `@Generated`, e o JaCoCo o
ignora: getters não inflam a conta. Cobertura mede o que *executou*, não o que foi
*verificado*. Um teste sem asserção cobre tanto quanto um bom teste. O portão
evita regressão grosseira, mas não prova qualidade.

**Cache do Gradle só é gravado pela main.** O `setup-gradle` grava na branch padrão
e apenas lê em PRs e tags (`cache-read-only` muda conforme a branch). Isso impede que
um PR envenene o cache usado pela main e evita estourar os 10 GB de cache por
repositório com uma entrada por branch. O provedor é o `basic`, de código aberto,
sobre o cache do GitHub Actions; o padrão da action é um serviço comercial com termos
próprios.

**Testcontainers em vez de `services:`.** O runner `ubuntu-24.04` tem Docker, então
os testes de integração sobem o próprio Postgres exatamente como no laptop. Um bloco
`services:` no workflow duplicaria a infraestrutura em dois lugares que divergem com
o tempo.

**O jar é reproduzível.** O `buildInfo` sai sem o campo `time`, e os arquivos do jar
não guardam timestamp e ficam em ordem estável. Dois builds do mesmo commit dão o
mesmo sha256, inclusive com JDKs de fornecedores diferentes. É isso que permite o
release reconstruir o jar e publicar um checksum que bate com o jar que o CI testou.
O commit fica gravado no próprio jar e aparece em `/actuator/info`.

**Imagem em camadas.** O fat jar é desmontado em `dependencies`,
`spring-boot-loader`, `snapshot-dependencies` e `application`, em ordem crescente de
frequência de mudança. Um commit que só muda código troca uma camada de 42 kB, e não
uma de 57 MB. A imagem roda como usuário não-root, e o heap é dimensionado pelo
limite do container (`MaxRAMPercentage`), não pela RAM do host.

**O artefato entregue é o artefato testado.** O job `image` constrói a imagem no
daemon local, sobe um Postgres, espera a readiness, cria e avalia uma flag e confere
que o processo não roda como root. Só depois faz o push. O segundo `build-push` sai
inteiro do cache e não reconstrói nada. Em PR a imagem é construída e testada, mas
nunca publicada.

**Construir uma vez, promover.** O release por tag não reconstrói a imagem. Ele
procura a imagem `sha-<commit>` que o CI da main publicou e adiciona as tags `X.Y.Z`,
`X.Y` e `latest` ao mesmo digest. Se essa imagem não existe, o commit nunca passou
pelo CI da main e o release é recusado. Antes disso, o job confere que a tag bate
com a versão do `build.gradle.kts`, que é a fonte única da versão.

A promoção importa porque a imagem, diferente do jar, **não** é reproduzível como
está: o `metadata-action` grava `org.opencontainers.image.created` com a hora da
execução. Duas execuções do mesmo commit produzem camadas e config idênticas, mas
digests diferentes. Reconstruir no release entregaria um artefato que ninguém testou.

**Supply chain.**
- Toda action é fixada por SHA de commit, com a versão em comentário. Uma tag pode
  ser movida para código malicioso depois, como aconteceu com
  `tj-actions/changed-files` em 2025; um SHA não pode.
- O Dependabot atualiza os SHAs e as dependências do Gradle em um PR agrupado por
  semana.
- O workflow começa com `permissions: contents: read`, e só os jobs que publicam
  pedem `packages: write` ou `contents: write`.
- O checkout não deixa credencial no `.git` (`persist-credentials: false`).
- O `setup-gradle` valida o checksum do `gradle-wrapper.jar`.

**Concorrência e limites.** Em PR, um push novo cancela a execução anterior. Na main,
nada é cancelado: cancelar a meio caminho deixaria um commit sem imagem, justamente o
que o release promove. Todo job tem `timeout-minutes`.

## Números medidos

Medido numa única sessão, em 24/11/2026. O CI rodou em runners `ubuntu-24.04`
hospedados pelo GitHub (Docker 28.0.4, Temurin 21). A parte local rodou com o jar
empacotado, JDK 21 (Corretto 21.0.5), o daemon do Gradle parado e Postgres 16 via
Docker Compose.

### Execução fria x quente

Primeira execução sem nenhum cache, e a segunda no mesmo commit (`workflow_dispatch`),
com o cache do Gradle e o das camadas Docker já gravados pela main:

| etapa                          | fria   | quente |
|--------------------------------|-------:|-------:|
| pipeline inteiro               | 4m07s  | 2m11s  |
| job `build`                    | 118 s  | 74 s   |
| `./gradlew build`              | 94 s   | 59 s   |
| job `image`                    | 120 s  | 49 s   |
| build da imagem                | 46 s   | 11 s   |
| push para o GHCR               | 25 s   | 7 s    |
| smoke test (readiness da imagem) | 4,9 s | 6,0 s |

Com o cache quente, o que sobra no `./gradlew build` é trabalho de verdade:
compilação, análise estática e os 19 testes. O Postgres do Testcontainers é baixado a
cada execução, porque imagens Docker não entram no cache do Gradle.

### Release por tag

| passo                                     | duração |
|-------------------------------------------|--------:|
| job `release` inteiro                     | 52 s    |
| conferir tag `v1.0.0` x versão do projeto | 8 s     |
| promover a imagem (sem rebuild)           | 8 s     |
| reconstruir o jar                         | 12 s    |
| criar o GitHub Release com jar e sha256   | 5 s     |

As tags `1.0.0`, `1.0`, `latest`, `main` e `sha-c22b390…` apontam para o mesmo digest.

### Reprodutibilidade do jar

| build do commit `c22b390`         | sha256 do jar     |
|-----------------------------------|-------------------|
| laptop, Corretto 21.0.5           | `e882b028c03e…`   |
| CI na main, Temurin 21            | `e882b028c03e…`   |
| job de release, Temurin 21        | `e882b028c03e…`   |
| laptop com `time` no buildInfo, 1º build | `e793a75edbe7…` |
| laptop com `time` no buildInfo, 2º build | `0b17e8b4c9e2…` |

### Camadas da imagem

Mudando uma linha de uma classe e reconstruindo:

| Dockerfile                | camadas novas | bytes novos |
|---------------------------|--------------:|------------:|
| em camadas (este projeto) | 1             | 42,4 kB     |
| copiando o fat jar        | 1             | 57,5 MB     |

A imagem final tem 272 MB, dos quais 57,7 MB são do serviço (dependências 57,3 MB,
loader 0,4 MB, aplicação 42 kB) e o resto é o JRE Alpine.

### Portão de cobertura

- Suíte completa (19 testes): 100% das linhas e 100% dos ramos.
- Rodando só o teste unitário do bucketer, o build falha com
  `lines covered ratio is 0.05, but expected minimum is 0.90` e
  `branches covered ratio is 0.20, but expected minimum is 0.80`.

## Testes

- `RolloutBucketerTest`: distribuição dentro de ±0,5 ponto percentual em 100 mil
  usuários, monotonicidade ao subir o percentual e independência entre flags.
- `FeatureFlagApiIT`: CRUD, chave duplicada, validação com todas as violações,
  versão defasada, 404 e as três razões da avaliação.
- `ConcurrentUpdateIT`: oito escritores atualizam a mesma versão ao mesmo tempo.
  Exatamente um vence, os outros recebem 409 e a versão final é 1.
- `ApplicationIT`: readiness e `/actuator/info` com versão e commit, sem `time`.

## Como rodar

```bash
docker compose up -d --wait
./gradlew bootRun
```

Build completo, com análise estática e portão de cobertura (precisa de Docker para o
Testcontainers):

```bash
./gradlew build
```

Imagem e smoke test locais:

```bash
./gradlew bootJar
docker build -t feature-flags:local .
.github/scripts/smoke-test.sh feature-flags:local
```

Imagem publicada:

```bash
docker pull ghcr.io/gkcontas/java-spring-github-actions-pipeline:1.0.0
```

## API

```bash
# criar
curl -s -X POST http://localhost:8080/api/flags -H 'Content-Type: application/json' \
  -d '{"key":"new-checkout","description":"New checkout flow","enabled":true,"rolloutPercentage":20}'

# listar e ler
curl -s http://localhost:8080/api/flags
curl -s http://localhost:8080/api/flags/new-checkout

# atualizar com a versão lida (versão defasada -> 409)
curl -s -X PUT http://localhost:8080/api/flags/new-checkout -H 'Content-Type: application/json' \
  -d '{"version":0,"description":"New checkout flow","enabled":true,"rolloutPercentage":50}'

# avaliar para um usuário
curl -s "http://localhost:8080/api/flags/new-checkout/evaluation?userId=user-1"
# {"key":"new-checkout","userId":"user-1","enabled":true,"reason":"ROLLOUT_INCLUDED"}

# versão e commit do build
curl -s http://localhost:8080/actuator/info
```

## Estrutura

```
.github/
  workflows/ci.yml          build, análise, testes, imagem, smoke test, push
  workflows/release.yml     tag -> confere versão, promove imagem, GitHub Release
  dependabot.yml            actions (SHAs) e Gradle, agrupados
  scripts/smoke-test.sh     sobe a imagem com um Postgres e exercita a API
  scripts/coverage-summary.py  cobertura no resumo do job
config/checkstyle/          regras que pegam erro, não formatação
config/spotbugs/            exclusões justificadas
Dockerfile                  imagem em camadas, usuário não-root
src/main/java/.../flag      domínio, bucketer, serviço
src/main/java/.../web       API REST e ProblemDetail
```
