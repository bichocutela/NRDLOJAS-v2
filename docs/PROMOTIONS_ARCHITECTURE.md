# Promoções ACP: arquitetura e operação

## Fluxo de dados

```mermaid
flowchart TD
    A[ACP e Visual Mix] --> B[Gateway e repositório]
    B --> C[Delta e transação Room]
    C --> D[Flow e ViewModel]
    D --> E[Cards e detalhes Compose]
```

O gateway autentica identidade Firebase ou capacidade de sessão do NRD, verifica o acesso atual e restringe o escopo Promoções à fonte De/Por da Cidade Jardim. Credenciais ACP, cookies e chaves Gemini permanecem no servidor. Não há retorno ao login Nossa Gente.

## Componentes e blocos

| Bloco | Arquivos principais | Responsabilidade |
| --- | --- | --- |
| Detalhes | `ui/PromotionProductDetailsContent.kt`, `ui/AcpProductsPanel.kt`, `ui/PromotionsScreen.kt` | Cards clicáveis, EAN e mesmo componente comercial/barcode de Consultar Preços. Os detalhes usam o snapshot ACP local; nenhum GET adicional ao tocar. Campanhas e banners da consulta de preços permanecem disponíveis. |
| Imagens | `data/promotions/ProductImageRepository.kt` | Consulta desacoplada por GTIN válido, família Open Facts, cache persistente de URLs/ausência e limitação global de requisições. Apenas cards visíveis consultam imagens. ACP tem prioridade, placeholder em erros. Créditos CC BY-SA vinculados à fonte. |
| Categorias | `categories.mjs`, `server-cache.mjs`, `PromotionCategory.kt` | Gemini 2.5 Flash no servidor, descrições completas tratadas como dados, enum fechado e JSON validado. Cache por EAN/código/descrição/taxonomia com lease e precondições; sucesso nunca é reenviado. Regras locais corrigem termos ambíguos e permitem operação sem IA. |
| Persistência/sync | `promotion-sync.mjs`, `PromotionDelta.kt`, `PromotionDatabase.kt`, `AcpPromotionsRepository.kt` | Snapshot ACP compartilhado, páginas imutáveis e ponteiro atômico. Room guarda fonte bruta sanitizada, ofertas e metadados; delta, remoções e revisão são publicados numa transação. Documentos Visual Mix são reutilizados por revisão. |
| Estado/background | `PromotionSyncCoordinator.kt`, `PromotionsViewModel.kt`, `PromotionNotificationWorker.kt` | Um coordenador serializa tela, monitor e worker. StateFlow distingue requisição de dados/refresh explícito de checagem silenciosa. Room alimenta a UI independente da tela estar aberta. Outbox persistente e tags estáveis evitam duplicar notificações. |

## Sincronização incremental

O ACP não tem um `updated_at` comprovado para esta rota. Por isso, a comparação acontece no gateway: ele consulta a fonte completa **uma vez por versão de snapshot**, compartilhada entre aparelhos, com intervalo mínimo de 60 segundos e lease entre instâncias. Os aparelhos consultam `promotion_status`; quando a revisão difere, enviam IDs/hashes a `promotion_sync` e recebem apenas itens alterados e IDs removidos. Uma resposta incompleta não substitui o cache.

A verificação de um snapshot vencido inicia atualização assíncrona e pode retornar a revisão anterior naquele ciclo. O próximo ciclo encontra a nova revisão. O cache anterior segue visível durante rede lenta, falha ACP ou processamento. Páginas antigas têm retenção de 30 minutos antes da limpeza, permitindo leituras já em andamento.

O primeiro download é uma baseline, inclusive se tiver zero produtos; não gera centenas de notificações de produtos já existentes. A identidade da oferta é loja/código interno, então mudar preço, descrição, categoria, estoque ou validade não transforma o produto em novo. Produtos removidos e posteriormente recolocados são novos na reinserção.

## Monitor, novas ofertas e notificações

`ProcessLifecycleOwner` inicia um monitor de 60 segundos enquanto o aplicativo está em primeiro plano, inclusive em outras abas. Ele começa a consultar após o primeiro uso das ofertas. WorkManager executa o mesmo coordenador a cada 15 minutos, com conectividade e backoff, quando permitido pelo Android. Doze, economia de bateria e “Forçar parada” podem adiar ou impedir tarefas; isto não é push instantâneo.

O botão Ofertas novas mostra um badge e filtra os produtos adicionados no último ciclo de alteração das ofertas, por loja selecionada. Outra checagem sem alterações mantém a seleção anterior. Toque novamente em Todas as ofertas para remover o filtro. Preço/categoria/estoque alterados não contam como adição.

A transação de sincronização grava eventos numa outbox antes da entrega. Em primeiro plano, a novidade aparece na UI. Fora dele, a entrega respeita preferências locais, configuração remota e permissão de notificações Android. Se houver loja favorita, restringe a ela; sem favorita, abrange as lojas habilitadas. O clique abre Promoções. Tags Android e IDs de histórico estáveis tornam uma tentativa repetida idempotente.

## Configuração e limites reais

- `GEMINI_API_KEY` no ambiente da Edge Function; modelo opcional `PROMOTION_GEMINI_MODEL`, padrão `gemini-2.5-flash`. Nenhuma chave privada entra no APK.
- `server_promotion_cache` é coleção exclusiva do servidor, bloqueada pelas regras Firestore para clientes. Leases são cercados por `updateTime`, evitando sobrescrita por instâncias antigas.
- Classificação acontece em lotes limitados, com cooldown, cache permanente de sucesso e retry de falhas. Descrição alterada é um novo conteúdo a classificar. Categorias são sugestões validadas, não promessa de precisão absoluta.
- O Room também persiste a categoria no snapshot comercial; abertura offline não chama Gemini.
- Open Facts não cobre todos os produtos. EAN ausente/inválido, produto não encontrado e imagem indisponível mantêm placeholder. Ausência é guardada sete dias; erro temporário, cinco minutos; URL encontrada, um ano. Coil guarda os bytes das imagens.
- Cidade Jardim usa ACP. Outras lojas só ficam disponíveis após habilitação/publicação pelo Mestre; os dados de arquivo não inventam estoque ou campos que o documento não informa.
- Indicador visual gira na primeira carga, no refresh explícito ou no download de alterações. `finally` restaura o estado em sucesso, exceção e cancelamento; checagens silenciosas não giram continuamente.

## Verificação

Testes Node cobrem autorização, contratos ACP, sanitização, JSON/categorias controladas, lease com um vencedor, hashes estáveis, troca com contagem igual, deltas e preservação de snapshot em falhas. Testes Android cobrem GTIN/categoria, baseline, deltas, rollback da transação, reabertura do banco com EAN/estoque/validade e estado offline. O CI compila/testa Android, gera APK assinado e verifica a rota ACP e a sincronização implantada através de OIDC restrito do GitHub.
