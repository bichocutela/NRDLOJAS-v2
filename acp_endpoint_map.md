# Mapa de endpoints ACP para regras comerciais

**Objetivo.** Identificar onde o ACP pode fornecer preço normal, preço promocional, Clube, cashback, Leve/Pague, atacado, segunda unidade, vigência e limites, mantendo todas as consultas em modo somente leitura. A análise foi comparada com o cliente Android em `AcpApi.kt`, `AcpProducts.kt` e `AcpCampaigns.kt`.

> **Regra de segurança:** nenhum endpoint de criação, edição, exclusão, sincronização manual ou geração de artefato deve ser chamado durante a investigação. Também não devem ser registrados cookies, tokens, senhas, cabeçalhos `Authorization` ou respostas que contenham esses valores.

## Endpoints já confirmados

| Prioridade | Endpoint | Situação observada | O que pode revelar | Próxima ação |
|---|---|---|---|---|
| 1 | `GET https://api.acp.app.br/api/v1/Product/all` | Confirmado no Android e testado com EANs | Valor, valor anterior, preço Clube, cashback, Leve/Pague, atacado, limite e possíveis campos promocionais diretamente no produto | Repetir com um produto que apareça no cartaz de 40%; comparar resposta antes/depois da seleção |
| 1 | `GET https://api.acp.app.br/api/v1/Campaign/all` | Confirmado no Android; retornou vazio no teste atual | Campanhas, regras por produto, vigência, condições, prioridade e segunda unidade | Testar durante uma sessão estável e abrir a tela Campanhas; observar parâmetros reais e paginação |
| 1 | `GET https://api.acp.app.br/api/v1/Product/integrationInfo` | Confirmado e retornou sincronização concluída | Estado global da integração de produtos | Usar apenas como diagnóstico; não interpretar como fonte de preço ou validade |
| 2 | `GET https://api.acp.app.br/api/v1/Template/all` | Observado ao abrir a tela de Templates | Templates disponíveis, grupo, tipo, validade de publicação, filtros e configurações visuais | Inspecionar a resposta sanitizada para descobrir se há placeholders de preço/oferta, sem confundir layout com regra comercial |
| 2 | `GET https://api.acp.app.br/api/v1/TemplateGeneralSettings` | Observado ao abrir a tela de Templates | Configurações gerais de impressão e campos exibidos | Verificar se existem nomes de campos comerciais ou apenas configurações de papel, escala e fundo |
| 3 | `GET https://api.acp.app.br/api/v1/File/Logo` | Observado na carga do ACP | Logos e imagens institucionais | Não é fonte de dados comerciais; não priorizar para o problema de desconto |

**Evidência coletada.** Para o EAN `7896098906750`, `Product/all` retornou um produto Tixan Ypê 2,2 kg com valor `37.99`, mas sem `secondUnitDiscount`, `quantityTake`, `quantityPay`, cashback ou preço Clube. `Campaign/all` retornou `totalCount: 0` no tenant consultado. Portanto, a ausência de 40% nesse teste não demonstra, por si só, erro do parser; demonstra que a regra não veio nesses retornos.[1] [2]

## Endpoints candidatos de catálogo

O ACP apresenta menus separados para **Grupo de Produtos**, **Unidades**, **Categoria de Produtos** e **Família de Produtos**. As rotas abaixo são hipóteses baseadas nesses módulos e devem ser confirmadas observando a chamada real ao abrir cada menu; uma sondagem com a sessão expirada retornou HTTP 401, portanto não é prova de inexistência.

| Endpoint candidato | Utilidade provável | Campos a procurar |
|---|---|---|
| `GET .../ProductCategory/all` | Relação de categorias e filtros de produto | `id`, `description`, vínculos de produto e eventualmente regras por categoria |
| `GET .../ProductFamily/all` | Famílias e agrupamentos comerciais | `id`, `description`, produto/família e regras de preço |
| `GET .../ProductGroup/all` | Grupos usados em campanhas ou cartazes compostos | `id`, `description`, produtos associados, quantidades e condições |
| `GET .../Unit/all` | Unidade de venda ou loja/unidade operacional | `id`, `description`, código de filial e escopo da campanha |

Esses endpoints são secundários. Eles podem explicar **por que** uma oferta se aplica, mas a fonte primária do valor ainda deve ser `Product/all`, `Campaign/all` ou uma chamada específica do editor de cartaz.

## Onde a regra de segunda unidade provavelmente está

A investigação deve procurar quatro formas de representação, nesta ordem:

| Forma | Exemplos de campos/textos | Como o Android deve tratar |
|---|---|---|
| Campo numérico direto no produto | `secondUnitDiscount`, `secondUnitPercentage`, `secondUnitPercent`, `discountSecondUnit` | Mapear para `AcpProduct.secondUnitDiscount` |
| Campo numérico dentro do vínculo campanha-produto | `promotion`, `offer`, `condition`, `rule`, `campaignProduct` com percentual | Mapear na regra específica do produto, preservando a campanha e a vigência |
| Regra textual | `40% na segunda unidade`, `segunda unidade com desconto` | Extrair percentual apenas quando o texto mencionar explicitamente segunda unidade; não inferir de qualquer percentual |
| Condição de template ou motor de impressão | Placeholder ou campo calculado retornado ao selecionar produto | Tratar como evidência de que o editor usa uma chamada adicional; não copiar o cálculo sem capturar o JSON da chamada |

O parser atual já contempla variantes de campos numéricos e texto de campanha em `AcpCampaigns.kt`, além das famílias `CLUB`, `CASHBACK`, `TAKE_PAY`, `WHOLESALE` e `SECOND_UNIT` em `AcpProducts.kt`. A próxima alteração só deve ocorrer depois de capturar um JSON real que contenha a regra e mostrar exatamente onde o campo está aninhado.[3] [4]

## Sequência recomendada de inspeção

### 1. Selecionar um produto que tenha a oferta visível

O produto precisa aparecer no ACP com o selo ou texto de 40% na segunda unidade. Os EANs testados até agora não são suficientes, porque dois não foram localizados e o Tixan não trouxe a campanha.

### 2. Registrar a carga inicial do editor

Ao abrir o template Simples, Composto ou outro template com produto, registrar somente método, URL, parâmetros, status e corpo sanitizado das chamadas `GET`. A carga inicial pode revelar filtros, grupos de template, unidades e configurações de validade.

### 3. Selecionar o produto

Comparar a lista de recursos antes e depois da seleção. Priorizar chamadas novas contendo `Product`, `Campaign`, `Offer`, `Promotion`, `Condition`, `Price`, `Template` ou `Print`. O objetivo é encontrar uma chamada que não seja apenas `Product/all`.

### 4. Ativar “Mostrar Validade da Oferta”

Comparar novamente as chamadas. Se o ACP buscar datas apenas quando essa opção é ativada, a vigência provavelmente não está no produto básico. Procurar campos como `startDate`, `endDate`, `initialDate`, `finalDate`, `validFrom`, `validTo`, `startAt`, `endAt` e objetos de validade aninhados.

### 5. Gerar apenas a pré-visualização

A pré-visualização deve ser observada sem salvar, imprimir, publicar ou criar qualquer registro. Se houver uma chamada de renderização, registrar apenas o endpoint e a resposta sanitizada; ela pode revelar o modelo de dados calculado para o cartaz, inclusive `De/Por`, `clubPrice`, `cashback`, `take`, `pay`, `secondUnitDiscount` e `limit`.

## Endpoints que não devem ser chamados

Não devem ser testados endpoints de `POST`, `PUT`, `PATCH` ou `DELETE`, nem rotas de criação/edição/exclusão de produtos, campanhas e templates. Também não se deve clicar em ações que salvem, gerem, publiquem, sincronizem manualmente ou excluam dados. A investigação deve permanecer limitada a GETs observados no fluxo normal.

## Conclusão

Os endpoints mais promissores são `Product/all`, `Campaign/all` e qualquer chamada adicional disparada **depois da seleção do produto no editor de cartaz**. `Product/integrationInfo` é apenas diagnóstico. `Template/all` e `TemplateGeneralSettings` ajudam a entender o mecanismo de apresentação, mas não devem ser considerados fonte de descontos sem uma resposta comercial associada. As rotas de categoria, família, grupo e unidade devem ser confirmadas pelo tráfego real dos respectivos menus, pois os testes fora de uma sessão válida retornaram 401.

Não há base técnica suficiente para alterar o parser agora. O próximo artefato necessário é um JSON sanitizado de um produto com promoção realmente visível no ACP, incluindo a chamada que fornece a regra e sua vigência.

## Referências

[1]: https://api.acp.app.br/api/v1/Product/all "ACP API — Product/all"

[2]: https://api.acp.app.br/api/v1/Campaign/all "ACP API — Campaign/all"

[3]: https://github.com/bichocutela/NRDLOJAS-v2/blob/main/app/src/main/java/com/example/data/acp/AcpProducts.kt "NRDLOJAS-v2 — AcpProducts.kt"

[4]: https://github.com/bichocutela/NRDLOJAS-v2/blob/main/app/src/main/java/com/example/data/acp/AcpCampaigns.kt "NRDLOJAS-v2 — AcpCampaigns.kt"
