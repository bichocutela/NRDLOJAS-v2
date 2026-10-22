# Validação ao vivo do ACP — 2026-09-12

## Escopo
Consulta somente leitura, diretamente aos endpoints oficiais do tenant ACP, para validar a disponibilidade de promoções de segunda unidade nos EANs definidos em `acp_second_unit_test.md`. Nenhum produto, campanha, template ou dado foi alterado.

## Resultado de `Product/all`

| EAN | HTTP | Resultado sanitizado |
|---|---:|---|
| `7891150069121` | 200 | `items: []`; nenhum produto localizado nesse tenant pelo filtro `barCode` |
| `7896098906750` | 200 | Um produto localizado: código ACP `2036618001`, descrição `Lava Roupas Pó Concentrado Primavera Tixan Ypê 2,2kg`, valor `37.99`; `clubValue: 0`; `previousValue: 0`; `quantityTake/quantityPay: null`; `cashback/cashbackValue: null`; `secondUnitDiscount` ausente/nulo |
| `07891150087118` | 200 | `items: []`; nenhum produto localizado nesse tenant pelo filtro `barCode` |

A resposta de `Product/all` usa os campos de paginação `pageIndex`, `totalPages`, `pageSize`, `totalCount`, `hasPrevious`, `hasNext` e `items`.

## Resultado de `Campaign/all`

Foram consultadas as páginas `pageIndex=0` e `pageIndex=1`, com `pageSize=100`. Ambas retornaram HTTP 200, `totalCount: 0`, `totalPages: 0` e `items: []`. Não apareceu nenhuma campanha com os produtos testados, nem texto contendo `40%`, `segunda unidade` ou equivalentes.

## Resultado de `Product/integrationInfo`

Resposta HTTP 200 com:

```json
{
  "id": 1,
  "lastCompleteRun": "2026-09-12T04:42:12",
  "lastRun": "2026-09-12T04:42:12",
  "message": null,
  "status": 2
}
```

O endpoint indica que existe uma sincronização concluída e não reporta mensagem de erro. Isso não prova que campanhas comerciais estejam disponíveis no tenant; apenas indica o estado global da integração de produtos.

## Endpoints adicionais observados no fluxo de Templates

A tela de Templates carregou, em modo GET, os endpoints `TemplateGeneralSettings` e `Template/all?pageSize=999999999&pageIndex=0&templateGroupId=1&hidden=false&ignoreFutureStartAt=true`. Esses endpoints são relevantes para descobrir como os campos visuais do cartaz são configurados, mas não devem ser tratados como fonte de preços ou campanhas sem uma resposta de produto associada. A sondagem posterior retornou HTTP 401 porque a sessão expirou; não foi repetida com credenciais expostas.

As tentativas de sondagem dos nomes presumidos `ProductCategory/all`, `ProductFamily/all`, `ProductGroup/all` e `Unit/all` também retornaram 401 nessa sessão expirada. Portanto, isso não comprova que as rotas não existam; apenas significa que precisam ser testadas depois de uma nova autenticação e, preferencialmente, observadas quando o menu correspondente for aberto.

## Conclusão técnica provisória

1. O EAN `7896098906750` está cadastrado e foi retornado sem uma regra de 40% na segunda unidade em `Product/all`.
2. O retorno não contém evidência de uma promoção de segunda unidade para esse produto.
3. `Campaign/all` está vazio no tenant consultado, portanto não foi possível confirmar a representação JSON de uma campanha de 40%.
4. Os EANs OMO testados não estão localizados pelo `barCode` nesse tenant, o que pode significar que o cadastro usa outro código, que o catálogo da loja é diferente ou que o produto não está sincronizado nesse ambiente.
5. Ainda não é seguro alterar o parser para assumir um novo campo: não há JSON ACP real com a regra de 40% para comparar. O parser atual já reconhece campos numéricos e variantes textuais de segunda unidade, mas o teste mostrou uma ausência de dados no ACP, não uma falha comprovada de interpretação do NRD.

## Próximo teste recomendado

Validar no ACP, pela tela de criação de cartaz, um produto que apareça visualmente com a campanha de 40%. Capturar apenas o corpo JSON sanitizado da chamada feita ao selecionar o produto/template. Esse teste é necessário para descobrir se a promoção fica em outro endpoint, em um campo de condição aninhado ou em uma chamada específica do fluxo de cartazes.
