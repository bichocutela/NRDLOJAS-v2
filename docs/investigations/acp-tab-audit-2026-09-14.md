# ACP: inspeção das abas — 14/09/2026

## Escopo
Investigação somente leitura: Grupos, Produtos, Unidades, Categorias, Famílias, Campanhas, impressão Simples/Composto/Comunicado/Imagem, Templates, Dashboard, Histórico e Empresa. Não executar gravação, exclusão ou geração de novas impressões.

## Observações desta sessão
- A página inicial autenticada mostrou aviso explícito de que este acesso será descontinuado e orientou procurar o responsável indicado no próprio portal para obter novo acesso. Não há data ou URL de migração confirmada.
- Ao clicar no link visível Grupo de Produtos (/product-group), o navegador foi redirecionado à tela de login. Não foi possível inspecionar o conteúdo dos grupos nesta tentativa.
- O motivo do redirecionamento não está confirmado. Não atribuir a expiração, concorrência de sessões, migração ou bloqueio sem evidência adicional.
- A imagem enviada pelo usuário mostra grupos com títulos parcialmente ocultos e contagens 168, 186 e 504. Isso é pista para inspeção; não prova percentual, vigência ou vínculo com Tiroliro.

## Evidência bruta já recebida do usuário
Arquivo teste.zip: cinco JSONs válidos de 34.942 bytes cada, todos página zero de TemplatePrintLog/all. Dez registros de impressão únicos, 25 ocorrências de produtos. Total informado: 15.846–15.847 registros e 1.585 páginas.
Após JSON.parse(items[].dataLog), o caminho composedTemplates[].items[] contém produtos e configurações de impressão.
Registro 6306, criado em 2026-01-28T17:28:31: Trident, código 2010473004, EAN 7622210564337, value="2.99", clubValue="1.99", validOffer="03/02/2026", showOffersExpirationDate=true, dueDate=null.
Isso comprova a preservação da validade configurada no cartaz histórico, não a vigência atual no caixa.
Nos dez registros: Tiroliro ausente; secondUnitDiscount ausente; cashback nulo e cashbackValue zero.

## Próxima inspeção
1. Grupos: descrição completa, produtos vinculados, filtros e campos disponíveis. Nome do grupo não basta para confirmar elegibilidade comercial.
2. Histórico: encontrar registro vinculado ao código/EAN desejado e comparar dataLog com o cartaz; não confundir com Product/all.
3. Campanhas e templates: origem de período, percentual e cashback; distinguir campos gravados de padrões locais de renderização.
4. Demais abas: registrar utilidade e endpoints somente quando observados, sem presumir contratos.

## Continuação: evidências de código e limite da navegação

Na tentativa seguinte a página inicial mostrou identificação autenticada. O clique em Grupo de Produtos não retornou pela ferramenta de navegador, inclusive na tentativa de ler sua documentação de recuperação. Não houve resposta suficiente para confirmar conteúdo da aba, logout ou erro do servidor. Esta etapa não equivale a uma varredura concluída da interface.

### Mapa de utilidade por aba

| Aba | Evidência disponível | Utilidade e limite para NRD |
| --- | --- | --- |
| Grupo de Produtos | Código público usa GET ProductGroup/all, filtros pageSize=10, pageIndex=0 e description; lista `products`; Carregar Produtos passa `grupo.products` a selectProductsByGroup | Caminho prioritário para descobrir vínculos; nenhum percentual/validade de grupo real capturado nesta etapa |
| Produtos | JSON real Product/all já fornecido | Cadastro e preços; não transformar quantityTake isolado em percentual |
| Unidades | Serviço público GET Unit/all; criação/edição passam id/description | No serviço analisado são descrições de unidades de produto. Não há evidência de que representem filiais |
| Categoria de Produtos | Serviço público GET ProductCategory/all; id/description | Classificação/filtro. Categoria sozinha não fornece preço nem prazo |
| Família de Produtos | Campo productFamily presente em Product/all | Relação cadastral; detalhes da aba e endpoint de consulta ainda não revalidados nesta sessão |
| Campanhas | GET Campaign/all e GET Campaign/{id} no cliente; amostras recebidas com items vazio | Campo de associação campaignProducts deve ser verificado em resposta não vazia. Não há precedência global comprovada |
| Simples / Composto | Seleção de produto/grupo/campanha alimenta estado local. Renderer lê secondUnitDiscount, cashback, cashbackValue e validOffer | Produtos podem receber valores do editor que não estavam em Product/all. Historizar não equivale a validar vigência comercial |
| Dashboard / Histórico | Ambos consultam TemplatePrintLog/all e usam a mesma queryKey; ordenação solicitada difere. JSON real do ZIP prova dataLog serializado | Evidência de cartazes impressos. Hipótese de cache explica o caminho Dashboard→Histórico, mas não demonstra causa do erro de servidor |
| Templates | GET Template/all e Template/by-code/{code} no serviço público | Configuração define componentes e base do cálculo. Nome/modelo não atribui desconto automaticamente |
| Comunicado / Imagem / Comunicados | Rótulos/links observados, conteúdo específico não aberto nesta sessão | Não descartar imagens de cartazes como evidência visual, mas não presumir produtos/EAN estruturados |
| Empresa | Menu observado; conteúdo específico não aberto | Verificar escopo do estabelecimento e aviso de migração quando houver sessão estável |

### Campos de grupos no cliente
O serviço `updateProductGroup` recebe id, description e productsIds; `createProductGroup` recebe description e productsIds. Apenas o código foi lido; nenhuma escrita foi executada. Isso indica que o formulário/serviço examinado administra associação de produtos. Não prova que o servidor nunca retorne campos adicionais.

### Evidência histórica salva para testes futuros
`acp-history-trident-observed.json` conserva um recorte real de produto do dataLog do registro 6306, com origem identificada. Valor normal, Clube e validade de impressão são strings no histórico. O cadastro Product/all usa números em amostras anteriores; futuros parsers devem aceitar ambos sem trocar null por promoção ativa.

### Prioridade quando a navegação voltar
Abrir grupos de segunda unidade/cashback e conferir os produtos vinculados. Cruzar código/EAN do Tiroliro com o grupo e com o objeto de impressão. Não extrair a regra apenas do nome do grupo. Comparar registros históricos com data e configuração de template; não aplicar ofertas antigas ao catálogo atual.
