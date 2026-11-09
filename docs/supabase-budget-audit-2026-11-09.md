# Auditoria de consumo — Supabase NRD V2 — 09/11/2026

Projeto: `kkayksyzksexoarpfxyj`. Mudanças deste bloco são **somente no servidor** (`nrd-price-gateway/promotion-sync.mjs`); não mudam layout, credenciais, Supabase Storage, contrato público nem exigem novo APK.

## Evidências (contadores do projeto)

- `public.nrd_promotion_cache`: aproximadamente 37.544 linhas e 130 MiB (tabela + índices e TOAST).
- Contadores acumulados: aproximadamente 735.712 chamadas à função `nrd_cache_write`, 653.882 updates registrados em `pg_stat_user_tables` e 200.207 leituras na consulta por chave. Esses contadores **não são** os valores faturados e podem abranger várias semanas.
- Logs disponíveis para o período das últimas 24 horas consultadas: cerca de 16.953 chamadas `POST /rest/v1/rpc/nrd_cache_write`; cerca de 3.933 `GET /rest/v1/nrd_promotion_cache`; cerca de 1.814 execuções `/functions/v1/nrd-price-gateway`. Logs podem ter amostragem ou retenção diferente do faturamento.
- Distribuição das chaves presentes: 21.575 `catalog_*`, 15.872 `category_*`, 92 `snapshot_*`. Aproximadamente 19.945 registros `catalog_v2_*` ultrapassaram sua data de revalidação. **Não apagá-los indiscriminadamente**: os metadados antigos funcionam como fallback de fotos oficiais.
- `nrd-promotion-sync` e `nrd-promotion-control-import` rodam a cada cinco minutos; os cron jobs tiveram 288 execuções bem-sucedidas no período de 24 h.
- O gateway anterior fazia varreduras comerciais automáticas quando a última verificação ultrapassava **60 segundos** e, a cada verificação, escolhia até **80 produtos** para enriquecimento (fotos, departamentos e categorias). Isso repetia consultas e gravações mesmo sem alteração de preços.

## Mitigação

- Alinhar o **intervalo mínimo** de verificação comercial automática para 5 minutos, como o cron existente. O botão Atualizar (`promotion_refresh`) continua forçando verificação imediata.
- Limitador **compartilhado entre instâncias** da etapa opcional de enriquecimento: 80 produtos por hora, com cursor e próximo horário persistidos no ponteiro do snapshot. Isso não reduz a frequência de leitura dos preços ACP nas verificações comerciais.
- As diferenças de preço, ofertas removidas, campanhas e vigências continuam gerando novo snapshot; somente a busca complementar de imagem/categoria deixa de se repetir desnecessariamente. Metadados oficiais verificados são preservados pelo mecanismo já existente de `retainCatalogMetadata`.
- Não houve exclusão do cache nem migração de projeto, pois isso poderia provocar perda de fallback, sincronizações completas e mais consumo.

## Verificação

Os testes do `promotion-sync.test.mjs` passam a cobrir:
1. Limites de enriquecimento por hora e rotação entre lotes.
2. Atualização de preço mesmo dentro do período sem enriquecimento.
3. Intervalo automático de 5 minutos e atualização manual imediata.
4. Remoção de oferta e preservação de dados em falhas.

## Medição recomendada pós-deploy

Comparar por 24h `/rest/v1/rpc/nrd_cache_write`, `/rest/v1/nrd_promotion_cache`, `nrd-price-gateway`, e as métricas oficiais de uso (Edge Function invocations, egress, tamanho do banco). Não atribuir porcentagem final de economia sem observar tráfego após implantação. A etapa opcional tem redução **teórica** de até 12 lotes/hora para 1 lote/hora quando há demanda contínua, sem mudar a busca comercial a cada cinco minutos.


## Próxima etapa — aumentar a validade dos metadados oficiais

O cache persistente do catálogo contém 21.574 chaves `catalog_v2_*`; na auditoria anterior 19.945 estavam fora do TTL de 24 h, embora o NRD ainda pudesse usar as associações oficiais antigas para exibir imagens. O gateway recuperava no máximo 80 associações por ciclo de enriquecimento. Retestar diariamente as mesmas combinações de código/EAN/descrição gerava gravações e consultas dispensáveis.

**Mudança:** no `nrd-price-gateway`, novos registros verificados de imagem, seção e associação EAN terão TTL de 7 dias. O cliente de busca direta de imagens permanece com seu padrão independente. Resultados antigos só recebem o novo prazo quando forem revalidados naturalmente, sem varredura maciça ou exclusão de cache.

Preços, campanhas, estoque, validades e promoções **não** usam esse TTL de 7 dias; continuam consultados pelo gateway e sincronização comercial em seu ritmo próprio. Um botão de atualizar promoções continua funcionando independentemente desse prazo de metadados.

A economia exata precisa ser medida; o prazo de 7 dias reduz a frequência de revalidação de associações que já foram verificadas, mas não implica redução proporcional no egress total do projeto.
