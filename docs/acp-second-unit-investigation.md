# ACP: segunda unidade e validade comercial — 12/09/2026

## Resultado e limites da evidência

A análise dos bundles públicos confirma **campos e cálculo do editor de cartazes**,
mas não confirma que a promoção do Tiroliro esteja persistida no cadastro ou em
uma campanha. O navegador desta etapa voltou ao login; não foi capturado JSON
novo autenticado. Nenhuma alteração foi feita no ACP.

O responsável informou este recorte de Product/all: código 2012568001,
EAN 5604885098906, value=45.49, quantityTake=3, quantityPay=null, categoria Varejo.
A referência visual informa 50% na segunda unidade. São evidências distintas.
A fixture de regressão reproduz os campos relatados; não é uma captura integral
ou uma resposta nova do servidor. Metadados da página são apenas envelope de teste.

## Fluxo rastreado no frontend

1. Product/all é GET paginado por pageSize/pageIndex (base zero), code, barCode,
   description e productCategoryIds. A seleção copia o produto para estado local
   e aplica as configurações gerais de impressão e alterações gerais do editor.
2. Editar Produto mostra controles somente quando a configuração do template
   contém a opção correspondente. `secondUnitDiscount` é um percentual próprio;
   não é derivado de quantityTake. O controle aceita o valor do produto selecionado
   ou da edição geral. Seu onChange atualiza o estado local selecionado.
3. O renderer procura a base disponível na configuração do template nesta ordem:
   value, wholesaleValue, clubValue. Com secondUnitDiscount explícito, calcula
   a segunda unidade como base × (100 − percentual)/100 e a média das duas como
   (base + segunda unidade)/2, com toFixed(2) para apresentação.
   Para base 45.49 e percentual 50, a média matemática é 34.1175 → 34.12.
   **Isso explica o cartaz; não comprova qual template/base/percentual foi usado
   nem a elegibilidade vigente do Tiroliro.** Não foi adicionado cálculo ao NRD.
4. `Mostrar Validade da Oferta` controla showOffersExpirationDate. Sem endDate
   da campanha, o editor oferece um calendário para validOffer. Existe outro
   calendário para dueDate, rotulado Data de Vencimento. São dados diferentes.
5. No renderer de produto, validOffer ausente recebe hoje + 7 dias. A prévia
   pode portanto exibir uma data que não veio de uma oferta cadastrada.
   No renderer com campanha selecionada, a mensagem pode usar startDate/endDate;
   também há fallback de início para hoje e de final para validOffer. Essas
   datas de fallback não podem virar vigência confirmada no NRD.
6. Selecionar campanha usa Campaign/all e mapeia campaignProducts, aplicando
   campos da associação com fallback no product aninhado. A tela mantém uma
   selectedCampaign, não calcula precedência global entre campanhas. O mapeamento
   inspecionado não transporta secondUnitDiscount nem validOffer na lista de
   campos de campaignProducts; isso não prova ausência desses campos no servidor.
7. Salvar Tudo tem caminho separado de atualização Product/bulk. A existência
   desse caminho não confirma que um cartaz específico tenha sido salvo.
   Ele não foi executado. Também não foram acionadas impressão ou sincronização.

## Endpoints e escopo comprovado pelo código

| Endpoint relativo à API v1 | Uso observado no código | Limite |
| --- | --- | --- |
| GET Product/all | Lista/filtros de produtos | Recorte Tiroliro fornecido pelo responsável; sem JSON novo nesta etapa |
| GET ProductCategory/all | Categorias | Categoria não define percentual nem vigência |
| GET Product/integrationInfo | Método de leitura de informações da integração | Conteúdo comercial não confirmado; nenhuma regra inferida |
| GET Campaign/all | Campanhas paginadas e campaignProducts | Sem precedência global comprovada |
| GET Campaign/{id} | Método de serviço existente | Não observado como origem de segunda unidade no fluxo analisado |
| GET ProductGroup/all e ProductGroup/{id} | Seleção de grupos | Nome do grupo não é prova de regra vigente |
| GET TemplateGeneralSettings | Configurações gerais usadas na seleção | Configuração de impressão, não garantia de oferta |

O editor também contempla preço por unidade de conteúdo (contentQuantity /
contentUnit), embalagem, quantidade de embalagem, parcelamento, cashback e limite
por CPF. Esses controles não comprovam modalidades comerciais aplicáveis ao
produto; não foram transformados em novas ofertas no aplicativo.

## Compatibilidade com a main atual

A main 415f59d já contém consulta de campanhas, diagnóstico copiável, estoque,
vencimento, dados cadastrais e correção de De/Por baseada em categoria explícita.
Essas funcionalidades foram preservadas ao integrar este trabalho. O parser de
campanhas existente aceita aliases adicionais de campos; sua presença no código
NRD não constitui evidência de que a ACP realmente os forneça. Esta investigação
não certifica esses aliases nem acrescenta regras baseadas neles.

## O que falta obter antes de implementar novas regras

- Resposta autenticada mínima do Tiroliro contendo, se existentes,
  secondUnitDiscount e a origem/base de preço usada no cartaz.
- Configuração do template realmente utilizado e valores previamente preenchidos
  no editor, sem editar ou salvar para fabricar o resultado.
- Campanha vinculada com período explícito e resposta real de campaignProducts.
- Critério de precedência/combinação fornecido pela ACP, ou comportamento verificável
  que o demonstre. Seleção manual de uma campanha não atende esse requisito.
- Distinção entre limite por CPF e exigência de identificação/elegibilidade no Clube.

Sem esses dados, NRD mantém validade não confirmada, modalidades separadas e não
converte quantityTake=3 em segunda unidade ou em Leve/Pague sem quantityPay válido.
Não se incorpora a data padrão do cartaz como validade comercial.

## Referências técnicas reproduzíveis

Arquivos públicos já obtidos do frontend ACP; hashes abaixo identificam exatamente
as versões analisadas. São evidências de implementação do cliente, não de resposta
HTTP autenticada. Buscar os símbolos indicados evita depender de linhas minificadas.

- `9b8a39130812d76c.js` — SHA-256 `985e3ce87aec454d3e0df6559f180af761f4322f2bd620b46e579b1f9f5fa546`.
- `7255b1701036002f.js` — SHA-256 `7b14d6d2999808e7c75806695de23da9cc2cc0aadff16ded884205d74a70bd25`.
- `2055baa49f964fe4.js` — SHA-256 `d5d8935dd1402fd44b1ea11a08061cc2c272dda616adf94ab469ebd54fefdd19`.
- `b7d95081bd42b161.js` — SHA-256 `8612367cb9b4d0a4591815817057c783c6e53a12b7148ea5c85bbf99ca774a56`.

Símbolos: secondUnitDiscount, showOffersExpirationDate, validOffer,
campaignProducts.map, selectProduct, updateProduct, getAllCampaign.
